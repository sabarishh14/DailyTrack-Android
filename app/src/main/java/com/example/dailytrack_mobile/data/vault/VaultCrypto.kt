package com.example.dailytrack_mobile.data.vault

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The private vault's cryptography: byte for byte what the web's vault/crypto.js
 * does with WebCrypto, so either opens what the other locked.
 *
 *   passphrase ──PBKDF2-SHA256──▶ key ──AES-GCM──▶ wraps the data key
 *   recovery key ─PBKDF2-SHA256─▶ key ──AES-GCM──▶ wraps the data key too
 *   data key ──AES-256-GCM──▶ every item, and this person's ECDH private key (JWK)
 *   a friend's public key + a one-off ECDH key ──HKDF──▶ wraps the data key for them
 *
 * Every sealed value says what it is (AAD "dt-vault|v1|owner|purpose"), so one
 * can't be swapped for another. The server only ever sees what these return.
 */
object VaultCrypto {
    const val ITERATIONS = 600_000
    private val random = SecureRandom()
    private val b64 = Base64.getEncoder()
    private val unb64 = Base64.getDecoder()
    private val urlB64 = Base64.getUrlEncoder().withoutPadding()
    private val urlUnb64 = Base64.getUrlDecoder()

    fun random(n: Int): ByteArray = ByteArray(n).also { random.nextBytes(it) }
    fun toB64(bytes: ByteArray): String = b64.encodeToString(bytes)
    fun fromB64(text: String): ByteArray = unb64.decode(text)

    private fun aad(owner: String, purpose: String) = "dt-vault|v1|$owner|$purpose".toByteArray(Charsets.UTF_8)

    // ---- what people type ----

    /** A passphrase as it's turned into a key: case, extra spaces and the ends don't matter. */
    fun normalizePassphrase(p: String): String = p.trim().lowercase().replace(Regex("\\s+"), " ")

    private const val B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /** A new recovery key: 160 random bits as 8 groups of 4 letters and digits. */
    fun newRecoveryKey(): String {
        val out = StringBuilder()
        var bits = 0
        var value = 0
        for (b in random(20)) {
            value = (value shl 8) or (b.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                out.append(B32[(value ushr (bits - 5)) and 31])
                bits -= 5
            }
            value = value and ((1 shl bits) - 1)
        }
        return out.chunked(4).joinToString("-")
    }

    /** A recovery key as typed: any case, with or without dashes and spaces. */
    fun normalizeRecoveryKey(k: String): String = k.uppercase().replace(Regex("[^A-Z2-7]"), "")

    // ---- keys ----

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(key, "HmacSHA256")); doFinal(data) }

    /** PBKDF2-HMAC-SHA256, one 32-byte block, over the UTF-8 bytes (as WebCrypto). */
    fun pbkdf2(secret: String, salt: ByteArray, iterations: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        var u = mac.doFinal(salt + byteArrayOf(0, 0, 0, 1))
        val t = u.copyOf()
        for (i in 1 until iterations) {
            u = mac.doFinal(u)
            for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
        }
        return t
    }

    fun passphraseKey(passphrase: String, salt: String, iterations: Int) = pbkdf2(normalizePassphrase(passphrase), fromB64(salt), iterations)
    fun recoveryKeyKey(code: String, salt: String, iterations: Int) = pbkdf2(normalizeRecoveryKey(code), fromB64(salt), iterations)

    /** nonce ‖ ciphertext ‖ tag, base64. */
    fun seal(key: ByteArray, bytes: ByteArray, owner: String, purpose: String): String {
        val iv = random(12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(aad(owner, purpose))
        return toB64(iv + cipher.doFinal(bytes))
    }

    /** The bytes sealed; throws when the key (or what it's for) is wrong. */
    fun open(key: ByteArray, sealed: String, owner: String, purpose: String): ByteArray {
        val all = fromB64(sealed)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, all, 0, 12))
        cipher.updateAAD(aad(owner, purpose))
        return cipher.doFinal(all, 12, all.size - 12)
    }

    class Made(val dataKey: ByteArray, val keys: Map<String, Any>)

    /** A new vault's keys: what the server keeps, and the data key that stays here. */
    fun createKeys(owner: String, passphrase: String, recoveryKey: String): Made {
        val dataKey = random(32)
        val salt = toB64(random(16))
        val recoverySalt = toB64(random(16))
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val pub = pair.public as ECPublicKey
        val priv = pair.private as ECPrivateKey
        val jwk = JSONObject()
            .put("kty", "EC").put("crv", "P-256")
            .put("d", urlB64.encodeToString(fixed(priv.s)))
            .put("x", urlB64.encodeToString(fixed(pub.w.affineX)))
            .put("y", urlB64.encodeToString(fixed(pub.w.affineY)))
        return Made(dataKey, mapOf(
            "salt" to salt,
            "iterations" to ITERATIONS,
            "wrapped_key" to seal(passphraseKey(passphrase, salt, ITERATIONS), dataKey, owner, "key"),
            "recovery_salt" to recoverySalt,
            "recovery_wrapped_key" to seal(recoveryKeyKey(recoveryKey, recoverySalt, ITERATIONS), dataKey, owner, "recovery"),
            "public_key" to toB64(rawPublic(pub)),
            "wrapped_private_key" to seal(dataKey, jwk.toString().toByteArray(Charsets.UTF_8), owner, "private"),
        ))
    }

    fun unwrapWithPassphrase(owner: String, keys: VaultKeys, passphrase: String): ByteArray =
        open(passphraseKey(passphrase, keys.salt, keys.iterations), keys.wrappedKey, owner, "key")

    fun unwrapWithRecoveryKey(owner: String, keys: VaultKeys, code: String): ByteArray =
        open(recoveryKeyKey(code, keys.recoverySalt, keys.iterations), keys.recoveryWrappedKey, owner, "recovery")

    fun rewrapPassphrase(owner: String, dataKey: ByteArray, passphrase: String): Map<String, Any> {
        val salt = toB64(random(16))
        return mapOf("salt" to salt, "iterations" to ITERATIONS,
            "wrapped_key" to seal(passphraseKey(passphrase, salt, ITERATIONS), dataKey, owner, "key"))
    }

    fun rewrapRecovery(owner: String, dataKey: ByteArray, code: String): Map<String, Any> {
        val salt = toB64(random(16))
        return mapOf("recovery_salt" to salt,
            "recovery_wrapped_key" to seal(recoveryKeyKey(code, salt, ITERATIONS), dataKey, owner, "recovery"))
    }

    // ---- friends (ECDH P-256 + HKDF-SHA256) ----

    private val curve: ECParameterSpec by lazy {
        AlgorithmParameters.getInstance("EC").apply { init(ECGenParameterSpec("secp256r1")) }
            .getParameterSpec(ECParameterSpec::class.java)
    }

    /** A number as the fixed 32 bytes P-256 uses. */
    private fun fixed(n: BigInteger): ByteArray {
        val raw = n.toByteArray()
        return when {
            raw.size == 32 -> raw
            raw.size > 32 -> raw.copyOfRange(raw.size - 32, raw.size)
            else -> ByteArray(32 - raw.size) + raw
        }
    }

    /** 0x04 ‖ x ‖ y: WebCrypto's "raw" public key. */
    private fun rawPublic(pub: ECPublicKey) = byteArrayOf(4) + fixed(pub.w.affineX) + fixed(pub.w.affineY)

    private fun publicFromRaw(raw: ByteArray): PublicKey {
        require(raw.size == 65 && raw[0] == 4.toByte()) { "Not a P-256 public key" }
        val point = ECPoint(BigInteger(1, raw.copyOfRange(1, 33)), BigInteger(1, raw.copyOfRange(33, 65)))
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, curve))
    }

    private fun privateFromD(d: ByteArray): PrivateKey =
        KeyFactory.getInstance("EC").generatePrivate(ECPrivateKeySpec(BigInteger(1, d), curve))

    private fun grantKey(private: PrivateKey, public: PublicKey, owner: String, viewer: String): ByteArray {
        val shared = KeyAgreement.getInstance("ECDH").run { init(private); doPhase(public, true); generateSecret() }
        val secret = fixed(BigInteger(1, shared))
        // HKDF-SHA256: extract with a zero salt, then one 32-byte block of expand.
        val prk = hmac(ByteArray(32), secret)
        return hmac(prk, "dt-vault|v1|grant|$owner|$viewer".toByteArray(Charsets.UTF_8) + byteArrayOf(1))
    }

    /** The data key wrapped for a friend's public key: {ephemeral_key, wrapped_key}. */
    fun grantFor(owner: String, viewer: String, viewerPublic: String, dataKey: ByteArray): Map<String, String> {
        val eph = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val key = grantKey(eph.private, publicFromRaw(fromB64(viewerPublic)), owner, viewer)
        return mapOf(
            "ephemeral_key" to toB64(rawPublic(eph.public as ECPublicKey)),
            "wrapped_key" to seal(key, dataKey, owner, "grant|$viewer"),
        )
    }

    /** A friend's data key, from their grant and this person's own keys. */
    fun openGrant(owner: String, viewer: String, ephemeralKey: String, wrappedKey: String, myKeys: VaultKeys, myDataKey: ByteArray): ByteArray {
        val jwk = JSONObject(String(open(myDataKey, myKeys.wrappedPrivateKey, viewer, "private"), Charsets.UTF_8))
        val private = privateFromD(urlUnb64.decode(jwk.getString("d")))
        val key = grantKey(private, publicFromRaw(fromB64(ephemeralKey)), owner, viewer)
        return open(key, wrappedKey, owner, "grant|$viewer")
    }

    // ---- items ----

    fun sealItem(dataKey: ByteArray, owner: String, id: String, json: String): String {
        val buf = ByteArrayOutputStream()
        GZIPOutputStream(buf).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        return seal(dataKey, buf.toByteArray(), owner, "item|$id")
    }

    fun openItem(dataKey: ByteArray, owner: String, id: String, payload: String): String {
        val zipped = open(dataKey, payload, owner, "item|$id")
        return GZIPInputStream(ByteArrayInputStream(zipped)).use { String(it.readBytes(), Charsets.UTF_8) }
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    fun sha256Hex(text: String): String = hex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)))
    fun sha1Hex(text: String): String = hex(MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8)))
}

/** One person's vault keys, as the server keeps them (all of them wrapped). */
data class VaultKeys(
    val salt: String,
    val iterations: Int,
    val wrappedKey: String,
    val recoverySalt: String,
    val recoveryWrappedKey: String,
    val publicKey: String,
    val wrappedPrivateKey: String,
) {
    companion object {
        fun from(o: JSONObject?): VaultKeys? = o?.let {
            VaultKeys(it.getString("salt"), it.getInt("iterations"), it.getString("wrapped_key"),
                it.getString("recovery_salt"), it.getString("recovery_wrapped_key"),
                it.getString("public_key"), it.getString("wrapped_private_key"))
        }
    }
}
