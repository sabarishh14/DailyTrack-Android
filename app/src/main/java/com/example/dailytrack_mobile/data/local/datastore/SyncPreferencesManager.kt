package com.example.dailytrack_mobile.data.local.datastore

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Small settings that belong to the manual sync actions rather than to any
 * screen — currently just the Letterboxd handle, remembered so the import
 * doesn't ask for it every time.
 */
@Singleton
class SyncPreferencesManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private companion object {
        const val PREFS_NAME = "sync_preferences"
        const val KEY_LETTERBOXD_USERNAME = "letterboxd_username"
    }

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    fun getLetterboxdUsername(): String = prefs.getString(KEY_LETTERBOXD_USERNAME, "").orEmpty()

    fun setLetterboxdUsername(username: String) {
        prefs.edit().putString(KEY_LETTERBOXD_USERNAME, username.trim()).apply()
    }
}
