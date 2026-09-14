package com.example.dailytrack_mobile.data.local.datastore

import android.content.SharedPreferences
import org.json.JSONArray

/**
 * JSON-array backed string-list cache over a [SharedPreferences] instance.
 *
 * Replaces ad-hoc "|||"-delimited string joining, which silently corrupts data
 * whenever a stored value itself contains the delimiter sequence.
 */
class PrefsStringListCache(private val prefs: SharedPreferences) {

    fun read(key: String): List<String> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            List(array.length()) { array.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun write(key: String, values: List<String>) {
        val array = JSONArray()
        values.forEach { array.put(it) }
        prefs.edit().putString(key, array.toString()).apply()
    }
}
