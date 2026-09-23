package com.example.dailytrack_mobile.data.local.auth

import org.json.JSONArray
import org.json.JSONObject

/**
 * What the signed-in user may see and change. Mirrors backend/access.py; see
 * ACCESS_CONTROL.md in the DT-Web repo. The backend is the real gate, so this
 * only decides what the UI offers.
 */
enum class AccessModule(val key: String) {
    MONEY("money"), GYM("gym"), INVEST("invest"), SABDEKHO("sabdekho")
}

enum class AccessLevel(val key: String, val rank: Int) {
    NONE("none", 0), VIEW("view", 1), EDIT("edit", 2);

    companion object {
        fun from(key: String?): AccessLevel = entries.firstOrNull { it.key == key } ?: NONE
    }
}

data class AccessInfo(
    val email: String = "",
    val role: String = "member",
    val isOwner: Boolean = false,
    val isAdmin: Boolean = false,
    val modules: Map<AccessModule, AccessLevel> = emptyMap(),
    /** null = every category */
    val categories: List<String>? = null,
    /** null = every account */
    val accounts: List<String>? = null,
    val moneyRestricted: Boolean = false,
    /** Account balances / net worth may be shown (false while categories are limited). */
    val balancesVisible: Boolean = false,
    /** Whole-ledger actions: Sheets sync, reconcile, balance overrides. */
    val fullMoneyAccess: Boolean = false
) {
    fun level(module: AccessModule): AccessLevel = modules[module] ?: AccessLevel.NONE
    fun canView(module: AccessModule) = level(module).rank >= AccessLevel.VIEW.rank
    fun canEdit(module: AccessModule) = level(module).rank >= AccessLevel.EDIT.rank

    fun toJson(): String = JSONObject().apply {
        put("email", email)
        put("role", role)
        put("isOwner", isOwner)
        put("isAdmin", isAdmin)
        put("modules", JSONObject().apply { modules.forEach { (m, l) -> put(m.key, l.key) } })
        put("money", JSONObject().apply {
            put("categories", categories?.let { JSONArray(it) } ?: JSONObject.NULL)
            put("accounts", accounts?.let { JSONArray(it) } ?: JSONObject.NULL)
            put("restricted", moneyRestricted)
            put("balancesVisible", balancesVisible)
            put("fullAccess", fullMoneyAccess)
        })
    }.toString()

    companion object {
        /** Everything; used for owners/admins before the server answers and in demo mode. */
        val FULL = AccessInfo(
            role = "owner",
            isOwner = true,
            isAdmin = true,
            modules = AccessModule.entries.associateWith { AccessLevel.EDIT },
            balancesVisible = true,
            fullMoneyAccess = true
        )

        /** Nothing but Home; used for members until their access is known. */
        val NONE = AccessInfo()

        fun fromJson(json: String?): AccessInfo? {
            if (json.isNullOrBlank()) return null
            return try {
                val o = JSONObject(json)
                val mods = o.optJSONObject("modules")
                val money = o.optJSONObject("money")
                AccessInfo(
                    email = o.optString("email", ""),
                    role = o.optString("role", "member"),
                    isOwner = o.optBoolean("isOwner", false),
                    isAdmin = o.optBoolean("isAdmin", false),
                    modules = AccessModule.entries.associateWith { AccessLevel.from(mods?.optString(it.key, "none")) },
                    categories = money?.optStringListOrNull("categories"),
                    accounts = money?.optStringListOrNull("accounts"),
                    moneyRestricted = money?.optBoolean("restricted", false) ?: false,
                    balancesVisible = money?.optBoolean("balancesVisible", false) ?: false,
                    fullMoneyAccess = money?.optBoolean("fullAccess", false) ?: false
                )
            } catch (_: Exception) {
                null
            }
        }

        private fun JSONObject.optStringListOrNull(key: String): List<String>? {
            if (isNull(key)) return null
            val arr = optJSONArray(key) ?: return null
            return List(arr.length()) { arr.getString(it) }
        }
    }
}
