package com.example.dailytrack_mobile.data.local.security

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.dailytrack_mobile.data.local.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

enum class LockType {
    SYSTEM, // Same as device screen lock (Biometrics / Device PIN / Pattern)
    PIN     // Custom in-app PIN
}

enum class LockTimeout(val seconds: Long, val label: String, val description: String) {
    IMMEDIATELY(0L, "Immediately", "Locks immediately when closed"),
    THIRTY_SECONDS(30L, "After 30 seconds", "Locks after 30 seconds in background"),
    ONE_MINUTE(60L, "After 1 minute", "Locks after 1 minute in background"),
    FIVE_MINUTES(300L, "After 5 minutes", "Locks after 5 minutes in background"),
    FIFTEEN_MINUTES(900L, "After 15 minutes", "Locks after 15 minutes in background"),
    THIRTY_MINUTES(1800L, "After 30 minutes", "Locks after 30 minutes in background");

    companion object {
        fun fromString(value: String?): LockTimeout {
            return entries.firstOrNull { it.name == value } ?: IMMEDIATELY
        }
    }
}

class AppLockManager(private val context: Context) {

    companion object {
        val KEY_APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val KEY_LOCK_TYPE = stringPreferencesKey("app_lock_type")
        val KEY_PIN_HASH = stringPreferencesKey("app_lock_pin_hash")
        val KEY_BIOMETRIC_WITH_PIN = booleanPreferencesKey("app_lock_biometric_with_pin")
        val KEY_LOCK_TIMEOUT = stringPreferencesKey("app_lock_timeout")
        val KEY_LAST_BACKGROUND_TIMESTAMP = longPreferencesKey("app_lock_last_background_timestamp")

        private const val SALT = "DailyTrack_Secure_Salt_#2026"
    }

    // In-memory cache for fast lock verification without disk I/O latency
    @Volatile
    private var lastBackgroundTimestampMemory: Long = 0L

    val isAppLockEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_APP_LOCK_ENABLED] ?: false
    }

    val lockTimeoutFlow: Flow<LockTimeout> = context.dataStore.data.map { preferences ->
        val timeoutStr = preferences[KEY_LOCK_TIMEOUT] ?: LockTimeout.IMMEDIATELY.name
        LockTimeout.fromString(timeoutStr)
    }

    val lockTypeFlow: Flow<LockType> = context.dataStore.data.map { preferences ->
        val typeStr = preferences[KEY_LOCK_TYPE] ?: LockType.SYSTEM.name
        try {
            LockType.valueOf(typeStr)
        } catch (e: Exception) {
            LockType.SYSTEM
        }
    }

    val isBiometricWithPinEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_BIOMETRIC_WITH_PIN] ?: true
    }

    val hasCustomPinFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        !preferences[KEY_PIN_HASH].isNullOrEmpty()
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_APP_LOCK_ENABLED] = enabled
        }
    }

    suspend fun setLockTimeout(timeout: LockTimeout) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LOCK_TIMEOUT] = timeout.name
        }
    }

    suspend fun getLockTimeout(): LockTimeout {
        val preferences = context.dataStore.data.first()
        val timeoutStr = preferences[KEY_LOCK_TIMEOUT] ?: LockTimeout.IMMEDIATELY.name
        return LockTimeout.fromString(timeoutStr)
    }

    suspend fun setLastBackgroundTimestamp(timestamp: Long) {
        lastBackgroundTimestampMemory = timestamp
        context.dataStore.edit { preferences ->
            preferences[KEY_LAST_BACKGROUND_TIMESTAMP] = timestamp
        }
    }

    suspend fun getLastBackgroundTimestamp(): Long {
        if (lastBackgroundTimestampMemory != 0L) return lastBackgroundTimestampMemory
        val preferences = context.dataStore.data.first()
        val stored = preferences[KEY_LAST_BACKGROUND_TIMESTAMP] ?: 0L
        lastBackgroundTimestampMemory = stored
        return stored
    }

    suspend fun clearLastBackgroundTimestamp() {
        lastBackgroundTimestampMemory = 0L
        context.dataStore.edit { preferences ->
            preferences.remove(KEY_LAST_BACKGROUND_TIMESTAMP)
        }
    }

    suspend fun setLockType(lockType: LockType) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LOCK_TYPE] = lockType.name
        }
    }

    suspend fun setBiometricWithPinEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_BIOMETRIC_WITH_PIN] = enabled
        }
    }

    suspend fun savePin(pin: String) {
        val hash = hashPin(pin)
        context.dataStore.edit { preferences ->
            preferences[KEY_PIN_HASH] = hash
        }
    }

    suspend fun validatePin(pin: String): Boolean {
        val preferences = context.dataStore.data.first()
        val storedHash = preferences[KEY_PIN_HASH] ?: return false
        return storedHash == hashPin(pin)
    }

    suspend fun isAppLockEnabled(): Boolean {
        val preferences = context.dataStore.data.first()
        return preferences[KEY_APP_LOCK_ENABLED] ?: false
    }

    suspend fun getLockType(): LockType {
        val preferences = context.dataStore.data.first()
        val typeStr = preferences[KEY_LOCK_TYPE] ?: LockType.SYSTEM.name
        return try {
            LockType.valueOf(typeStr)
        } catch (e: Exception) {
            LockType.SYSTEM
        }
    }

    suspend fun hasPin(): Boolean {
        val preferences = context.dataStore.data.first()
        return !preferences[KEY_PIN_HASH].isNullOrEmpty()
    }

    suspend fun clearPin() {
        context.dataStore.edit { preferences ->
            preferences.remove(KEY_PIN_HASH)
        }
    }

    private fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest((pin + SALT).toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
