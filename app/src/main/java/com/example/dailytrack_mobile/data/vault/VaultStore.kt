package com.example.dailytrack_mobile.data.vault

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.example.dailytrack_mobile.data.local.auth.AuthManager
import com.example.dailytrack_mobile.data.remote.api.ApiConfig
import com.example.dailytrack_mobile.data.remote.dto.VAssetsItem
import com.example.dailytrack_mobile.data.remote.dto.VDayItem
import com.example.dailytrack_mobile.data.remote.dto.VFundsItem
import com.example.dailytrack_mobile.data.remote.dto.VState
import com.example.dailytrack_mobile.data.remote.dto.VTables
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** What the screens show about the vault on screen. */
data class VaultUi(
    /** unknown | off | moving | on */
    val state: String = "unknown",
    val open: Boolean = false,
    /** Why it's shut: locked | no-keys (a friend's, and no vault of your own) | no-grant (not opened for you yet) */
    val reason: String? = null,
    val owner: String? = null,
    val email: String? = null,
    val viewing: Boolean = false,
    val hasKeys: Boolean = false,
    val moving: Boolean = false,
) {
    val closed get() = state == "on" && !open
}

class VaultHttpError(val status: Int, val code: String?, message: String) : Exception(message)

/**
 * The private vault on this phone, as the web's vault/store.js: whether it's on,
 * unlocking it, keeping its key on this device (wrapped by a key in the Android
 * Keystore that never leaves it), reading and saving the locked items, and
 * moving investments in and out. VaultEngine works on what's opened here, and
 * VaultInterceptor puts the two in front of the Investments API.
 *
 * Everything here blocks: call it off the main thread (the interceptor already is).
 */
@Singleton
class VaultStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val authManager: AuthManager,
    moshi: Moshi,
    private val client: dagger.Lazy<OkHttpClient>,
) {
    private val tablesAdapter = moshi.adapter(VTables::class.java).serializeNulls()
    private val assetsAdapter = moshi.adapter(VAssetsItem::class.java).serializeNulls()
    private val fundsAdapter = moshi.adapter(VFundsItem::class.java).serializeNulls()
    private val dayAdapter = moshi.adapter(VDayItem::class.java).serializeNulls()
    private val stateAdapter = moshi.adapter(VState::class.java).serializeNulls()
    private val prefs by lazy { context.getSharedPreferences("dt_vault", Context.MODE_PRIVATE) }

    private class Session(
        val owner: String,
        val dataKey: ByteArray,
        val myDataKey: ByteArray,
        val readOnly: Boolean,
        @Volatile var tables: VTables,
        var saved: MutableMap<String, Pair<Int, String>>,
    )

    private val _ui = MutableStateFlow(VaultUi())
    val ui: StateFlow<VaultUi> = _ui.asStateFlow()

    @Volatile private var info: JSONObject? = null
    @Volatile private var infoFor: String? = UNREAD
    @Volatile private var session: Session? = null
    @Volatile private var moving = false
    private val statusLock = Any()
    private val writeLock = Any()

    // ---- talking to the server ----

    fun call(method: String, path: String, body: Any? = null): JSONObject {
        val requestBody: RequestBody? = when (body) {
            null -> if (method == "GET" || method == "DELETE") null else "{}".toRequestBody(JSON)
            is RequestBody -> body
            is JSONObject -> body.toString().toRequestBody(JSON)
            is Map<*, *> -> JSONObject(body).toString().toRequestBody(JSON)
            else -> body.toString().toRequestBody(JSON)
        }
        val request = Request.Builder().url(ApiConfig.BASE_URL.trimEnd('/') + path).method(method, requestBody).build()
        client.get().newCall(request).execute().use { res ->
            val text = res.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()
            if (!res.isSuccessful || json?.optBoolean("success", true) == false) {
                throw VaultHttpError(res.code, json?.optString("code")?.takeIf { it.isNotEmpty() },
                    json?.optString("message")?.takeIf { it.isNotEmpty() } ?: "Server returned ${res.code}")
            }
            return json ?: JSONObject()
        }
    }

    // ---- this phone's copy of the key ----
    // The data key, sealed under an AES key made in the Android Keystore: it can't
    // be read out of the phone, and "Lock" deletes what it sealed.

    private fun deviceKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(DEVICE_KEY, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(DEVICE_KEY, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build())
        }.generateKey()
    }

    private fun rememberKey(email: String, dataKey: ByteArray) {
        runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, deviceKey()) }
            cipher.updateAAD("dt-vault|v1|$email|device".toByteArray())
            prefs.edit().putString("key:$email", VaultCrypto.toB64(cipher.iv + cipher.doFinal(dataKey))).apply()
        }
    }

    private fun recallKey(email: String): ByteArray? = runCatching {
        val all = VaultCrypto.fromB64(prefs.getString("key:$email", null) ?: return null)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deviceKey(), GCMParameterSpec(128, all, 0, 12))
        cipher.updateAAD("dt-vault|v1|$email|device".toByteArray())
        cipher.doFinal(all, 12, all.size - 12)
    }.getOrNull()

    private fun forgetKey(email: String?) {
        if (email != null) prefs.edit().remove("key:$email").apply()
    }

    // ---- the state ----

    private fun emit() {
        val v = info
        if (v == null) {
            _ui.value = VaultUi(moving = moving)
            return
        }
        val owner = v.optString("owner")
        val email = v.optString("email")
        val viewing = owner != email
        val state = v.optString("state", "off")
        val hasKeys = !v.isNull("me")
        val open = session != null
        val reason = when {
            state != "on" || open -> null
            !hasKeys -> "no-keys"
            viewing && v.isNull("grant") -> "no-grant"
            else -> "locked"
        }
        _ui.value = VaultUi(state, open, reason, owner, email, viewing, hasKeys, moving)
    }

    /** GET /api/vault for what's on screen, read once per view (or again with [force]). */
    fun status(force: Boolean = false): JSONObject {
        synchronized(statusLock) {
            val view = authManager.getViewAs()
            if (!force && info != null && infoFor == view) return info!!
            if (infoFor != view) session = null
            val fresh = call("GET", "/api/vault")
            info = fresh
            infoFor = view
            emit()
            return fresh
        }
    }

    val isOpen get() = session != null
    val readOnly get() = session?.readOnly ?: true
    val tables: VTables get() = session?.tables ?: throw VaultHttpError(423, null, "Unlock your private vault first")

    private fun decryptAll(owner: String, dataKey: ByteArray): Pair<VTables, MutableMap<String, Pair<Int, String>>> {
        val items = call("GET", "/api/vault/items").getJSONArray("items")
        val t = VTables()
        val saved = mutableMapOf<String, Pair<Int, String>>()
        val ids = (0 until items.length()).map { items.getJSONObject(it) }.sortedBy { it.getString("id") }
        for (item in ids) {
            val id = item.getString("id")
            val json = VaultCrypto.openItem(dataKey, owner, id, item.getString("payload"))
            val normal = when {
                id == "state" -> stateAdapter.fromJson(json)!!.also { t.state = it }.let(stateAdapter::toJson)
                id == "assets" -> assetsAdapter.fromJson(json)!!.also { t.assets.addAll(it.assets); t.tasks.addAll(it.tasks) }.let(assetsAdapter::toJson)
                id == "funds" -> fundsAdapter.fromJson(json)!!.also { t.funds.addAll(it.funds); t.fund_tx.addAll(it.fund_tx); t.sips.addAll(it.sips) }.let(fundsAdapter::toJson)
                id.startsWith("day:") -> dayAdapter.fromJson(json)!!.also {
                    t.snapshots.addAll(it.snapshots); t.equity.addAll(it.equity); t.mf.addAll(it.mf); t.fund_values.addAll(it.fund_values)
                }.let(dayAdapter::toJson)
                else -> json   // something newer than this app: kept as it is
            }
            saved[id] = item.getInt("rev") to normal
        }
        return t to saved
    }

    /** {item id: JSON}, as the web splits them. */
    private fun toItems(t: VTables): Map<String, String> {
        val items = linkedMapOf(
            "assets" to assetsAdapter.toJson(VAssetsItem(t.assets, t.tasks)),
            "funds" to fundsAdapter.toJson(VFundsItem(t.funds, t.fund_tx, t.sips)),
            "state" to stateAdapter.toJson(t.state),
        )
        val months = sortedSetOf<String>()
        listOf(t.snapshots.map { it.date }, t.equity.map { it.date }, t.mf.map { it.date }, t.fund_values.map { it.date })
            .flatten().forEach { months.add(it.take(7)) }
        for (m in months) {
            items["day:$m"] = dayAdapter.toJson(VDayItem(
                t.snapshots.filter { it.date.startsWith(m) }, t.equity.filter { it.date.startsWith(m) },
                t.mf.filter { it.date.startsWith(m) }, t.fund_values.filter { it.date.startsWith(m) }))
        }
        return items
    }

    private fun openWith(myDataKey: ByteArray, remember: Boolean) {
        val v = status()
        val owner = v.getString("owner")
        val email = v.getString("email")
        val viewing = owner != email
        val dataKey = if (viewing) {
            val grant = v.optJSONObject("grant") ?: throw IllegalStateException("$owner hasn't opened their vault for you yet")
            VaultCrypto.openGrant(owner, email, grant.getString("ephemeral_key"), grant.getString("wrapped_key"),
                VaultKeys.from(v.getJSONObject("me"))!!, myDataKey)
        } else myDataKey
        val (t, saved) = decryptAll(owner, dataKey)
        session = Session(owner, dataKey, myDataKey, viewing, t, saved)
        if (remember) rememberKey(email, myDataKey)
        emit()
        if (!viewing) runCatching { syncGrants() }
    }

    /** Opens it with the key this phone kept, if it kept one. */
    fun tryRemembered(): Boolean {
        if (session != null) return true
        val v = runCatching { status() }.getOrNull() ?: return false
        if (v.optString("state") != "on" || v.isNull("me")) return false
        if (v.optString("owner") != v.optString("email") && v.isNull("grant")) return false
        val key = recallKey(v.getString("email")) ?: return false
        return runCatching { openWith(key, remember = false); true }.getOrDefault(false)
    }

    /** Unlock with the passphrase (or the recovery key). */
    fun unlock(secret: String, recovery: Boolean = false, remember: Boolean = true) {
        val v = status()
        val keys = VaultKeys.from(v.optJSONObject("me")) ?: throw IllegalStateException("Set up your vault first")
        val email = v.getString("email")
        val key = try {
            if (recovery) VaultCrypto.unwrapWithRecoveryKey(email, keys, secret) else VaultCrypto.unwrapWithPassphrase(email, keys, secret)
        } catch (_: Exception) {
            throw IllegalStateException(if (recovery) "That recovery key doesn't open it" else "That passphrase doesn't open it")
        }
        openWith(key, remember)
    }

    /** Locks it on this phone: the next open needs the passphrase. */
    fun lockNow() {
        forgetKey(info?.optString("email"))
        session = null
        emit()
    }

    // ---- reading and saving ----

    private fun copy(t: VTables): VTables = tablesAdapter.fromJson(tablesAdapter.toJson(t))!!

    /** Runs [fn] on a copy, one at a time, and saves what changed. When another
     *  device saved meanwhile, reads again and runs it again. */
    fun <T> transact(fn: (VTables) -> T): T {
        synchronized(writeLock) {
            var attempt = 0
            while (true) {
                val s = session ?: throw VaultHttpError(423, null, "Unlock your private vault first")
                val t = copy(s.tables)
                val result = fn(t)
                if (s.readOnly) return result      // a friend's: worked out, never saved
                try {
                    save(s, t)
                    return result
                } catch (e: VaultHttpError) {
                    if (e.code != "VAULT_STALE" || attempt >= 2) throw e
                    attempt++
                    reload()
                }
            }
        }
    }

    private fun save(s: Session, t: VTables) {
        val items = toItems(t)
        val put = JSONArray()
        val del = JSONArray()
        for ((id, json) in items) {
            if (s.saved[id]?.second != json) {
                put.put(JSONObject().put("id", id).put("payload", VaultCrypto.sealItem(s.dataKey, s.owner, id, json)).put("rev", s.saved[id]?.first ?: 0))
            }
        }
        for ((id, have) in s.saved) if (id !in items && (id == "state" || id == "assets" || id == "funds" || id.startsWith("day:"))) {
            del.put(JSONObject().put("id", id).put("rev", have.first))
        }
        if (put.length() > 0 || del.length() > 0) {
            val revs = call("PUT", "/api/vault/items", JSONObject().put("put", put).put("delete", del)).getJSONObject("revs")
            for (id in revs.keys()) {
                val rev = revs.getInt(id)
                if (rev > 0) s.saved[id] = rev to items.getValue(id) else s.saved.remove(id)
            }
        }
        s.tables = t
    }

    fun reload() {
        val s = session ?: return
        val (t, saved) = decryptAll(s.owner, s.dataKey)
        s.tables = t
        s.saved = saved
    }

    // ---- moving in and out ----

    private fun fingerprints(t: VTables): JSONObject {
        val out = JSONObject()
        val tables = mapOf("snapshots" to t.snapshots.map { it.id }, "equity" to t.equity.map { it.id }, "mf" to t.mf.map { it.id },
            "assets" to t.assets.map { it.id }, "tasks" to t.tasks.map { it.id }, "funds" to t.funds.map { it.id },
            "fund_tx" to t.fund_tx.map { it.id }, "sips" to t.sips.map { it.id }, "fund_values" to t.fund_values.map { it.id })
        for ((name, ids) in tables) {
            val sorted = ids.map { it.toString() }.sorted()
            out.put(name, JSONObject().put("count", sorted.size).put("ids", VaultCrypto.sha256Hex(sorted.joinToString(","))))
        }
        return out
    }

    private fun same(a: Any?, b: Any?): Boolean = when {
        (a == null || a == JSONObject.NULL) && (b == null || b == JSONObject.NULL) -> true
        a is Number && b is Number -> a.toDouble() == b.toDouble()
        else -> a == b
    }

    /** Every row the server sent, field for field, in what this app read: nothing it doesn't know is dropped. */
    private fun readsAllOf(raw: JSONObject, t: VTables): Boolean {
        val typed = JSONObject(tablesAdapter.toJson(t))
        for (name in raw.keys()) {
            val theirs = raw.getJSONArray(name)
            val ours = typed.optJSONArray(name) ?: return false
            if (theirs.length() != ours.length()) return false
            val byId = (0 until ours.length()).associate { ours.getJSONObject(it).let { r -> r.get("id").toString() to r } }
            for (i in 0 until theirs.length()) {
                val row = theirs.getJSONObject(i)
                val mine = byId[row.get("id").toString()] ?: return false
                if (row.keys().asSequence().toSet() != mine.keys().asSequence().toSet()) return false
                if (row.keys().asSequence().any { !same(row.opt(it), mine.opt(it)) }) return false
            }
        }
        return true
    }

    private fun sameTables(a: VTables, b: VTables): Boolean {
        fun norm(t: VTables) = copy(t).apply {
            snapshots.sortBy { it.id }; equity.sortBy { it.id }; mf.sortBy { it.id }; assets.sortBy { it.id }; tasks.sortBy { it.id }
            funds.sortBy { it.id }; fund_tx.sortBy { it.id }; sips.sortBy { it.id }; fund_values.sortBy { it.id }
        }
        return norm(a) == norm(b)
    }

    private fun setMoving(on: Boolean) {
        moving = on
        emit()
    }

    /**
     * Turns the vault on: keys made here (the server only keeps them wrapped),
     * then everything readable today moved in. Nothing readable is deleted until
     * the locked copy has been read back, opened and checked, here and on the server.
     */
    fun turnOn(passphrase: String, recoveryKey: String, remember: Boolean, progress: (String) -> Unit) {
        var v = status(force = true)
        if (v.optString("state") == "on") throw IllegalStateException("Your vault is already on")
        setMoving(true)
        try {
            val email = v.getString("email")
            val dataKey = if (v.isNull("me")) {
                progress("Making your keys…")
                val made = VaultCrypto.createKeys(email, passphrase, recoveryKey)
                call("POST", "/api/vault/keys", JSONObject(made.keys))
                made.dataKey
            } else {
                try {
                    VaultCrypto.unwrapWithPassphrase(email, VaultKeys.from(v.getJSONObject("me"))!!, passphrase)
                } catch (_: Exception) {
                    throw IllegalStateException("That passphrase doesn't open the vault you set up before")
                }
            }
            v = status(force = true)

            progress("Reading your investments…")
            val plain = call("GET", "/api/vault/plain")
            val raw = plain.getJSONObject("tables")
            val tables = tablesAdapter.fromJson(raw.toString())!!
            tables.state = stateAdapter.fromJson(plain.optJSONObject("state")?.toString() ?: "{}") ?: VState()
            if (!readsAllOf(raw, tables)) throw IllegalStateException("This version of the app can't move your investments safely. Update it and try again.")

            progress("Locking them away…")
            val items = toItems(tables)
            val leftover = call("GET", "/api/vault/items").getJSONArray("items")
            val revs = (0 until leftover.length()).associate { leftover.getJSONObject(it).let { i -> i.getString("id") to i.getInt("rev") } }
            val put = JSONArray()
            for ((id, json) in items) put.put(JSONObject().put("id", id).put("payload", VaultCrypto.sealItem(dataKey, email, id, json)).put("rev", revs[id] ?: 0))
            val del = JSONArray()
            for ((id, rev) in revs) if (id !in items) del.put(JSONObject().put("id", id).put("rev", rev))
            call("PUT", "/api/vault/items", JSONObject().put("put", put).put("delete", del))

            progress("Checking the locked copy…")
            val (back, saved) = decryptAll(email, dataKey)
            val prints = fingerprints(back)
            val theirs = plain.getJSONObject("fingerprints")
            val matches = theirs.keys().asSequence().all { name ->
                prints.getJSONObject(name).getInt("count") == theirs.getJSONObject(name).getInt("count") &&
                    prints.getJSONObject(name).getString("ids") == theirs.getJSONObject(name).getString("ids")
            }
            if (!matches || !sameTables(back, tables)) throw IllegalStateException("The locked copy didn't match, so nothing was removed. Try again.")

            progress("Removing the readable copy…")
            call("POST", "/api/vault/finish", JSONObject().put("fingerprints", prints))

            session = Session(email, dataKey, dataKey, false, back, saved)
            if (remember) rememberKey(email, dataKey)
            status(force = true)
            runCatching { syncGrants() }
        } catch (e: Exception) {
            runCatching { call("POST", "/api/vault/cancel") }
            runCatching { status(force = true) }
            throw e
        } finally {
            setMoving(false)
        }
    }

    /** Turns it off: everything stored readable again, checked, and only then are the locked items deleted. */
    fun turnOff(progress: (String) -> Unit) {
        val s = session ?: throw IllegalStateException("Unlock your vault first")
        if (s.readOnly) throw IllegalStateException("Unlock your vault first")
        setMoving(true)
        try {
            synchronized(writeLock) {
                reload()
                progress("Putting your investments back…")
                val all = JSONObject(tablesAdapter.toJson(s.tables))
                val state = all.remove("state")
                call("POST", "/api/vault/restore", JSONObject().put("tables", all).put("state", state).put("fingerprints", fingerprints(s.tables)))
            }
            val v = status(force = true)
            forgetKey(v.optString("email"))
            session = null
        } finally {
            setMoving(false)
        }
    }

    fun changePassphrase(passphrase: String) {
        val s = session ?: throw IllegalStateException("Unlock your vault first")
        call("PUT", "/api/vault/keys", JSONObject(VaultCrypto.rewrapPassphrase(status().getString("email"), s.myDataKey, passphrase)))
        status(force = true)
    }

    /** A new recovery key (the old one stops working). Returns it, to show once. */
    fun newRecoveryKey(): String {
        val s = session ?: throw IllegalStateException("Unlock your vault first")
        val code = VaultCrypto.newRecoveryKey()
        call("PUT", "/api/vault/keys", JSONObject(VaultCrypto.rewrapRecovery(status().getString("email"), s.myDataKey, code)))
        status(force = true)
        return code
    }

    /** Keys set up before but never used (the vault is off): forgotten, to start over. */
    fun startOver() {
        call("DELETE", "/api/vault/keys")
        forgetKey(status(force = true).optString("email"))
    }

    data class Friend(val viewer: String, val granted: Boolean)

    /** Friends Investments is shared with, given the key once they have vault keys. */
    fun syncGrants(): List<Friend> {
        val s = session ?: return emptyList()
        if (s.readOnly) return emptyList()
        val grants = call("GET", "/api/vault/grants").getJSONArray("grants")
        return (0 until grants.length()).map { i ->
            val g = grants.getJSONObject(i)
            val viewer = g.getString("viewer")
            var granted = g.optBoolean("granted")
            val publicKey = g.optString("public_key").takeIf { !g.isNull("public_key") && it.isNotEmpty() }
            if (publicKey != null && !granted) {
                val grant = VaultCrypto.grantFor(s.owner, viewer, publicKey, s.dataKey)
                call("PUT", "/api/vault/grants/" + java.net.URLEncoder.encode(viewer, "UTF-8"),
                    JSONObject(grant).put("viewer_key", publicKey))
                granted = true
            }
            Friend(viewer, granted)
        }
    }

    /** Forget the vault's state (switching whose data is shown); signing out also forgets this phone's key. */
    fun reset(signedOut: Boolean = false) {
        if (signedOut) prefs.edit().clear().apply()
        info = null
        infoFor = UNREAD
        session = null
        emit()
    }

    private companion object {
        const val UNREAD = "\u0000unread"
        const val DEVICE_KEY = "dt_vault_device"
        val JSON = "application/json".toMediaType()
    }
}
