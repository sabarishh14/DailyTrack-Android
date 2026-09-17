package com.example.dailytrack_mobile.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.dailytrack_mobile.presentation.theme.AppTheme
import com.example.dailytrack_mobile.presentation.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Extension property to create the DataStore instance
val Context.dataStore by preferencesDataStore(name = "settings")

class ThemeManager(private val context: Context) {
    companion object {
        private const val SYNC_PREFS_NAME = "theme_sync_cache"
        private const val KEY_THEME = "app_theme"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_AMOLED = "with_amoled"

        val THEME_KEY = stringPreferencesKey(KEY_THEME)
        val THEME_MODE_KEY = stringPreferencesKey(KEY_THEME_MODE)
        val AMOLED_KEY = booleanPreferencesKey(KEY_AMOLED)
    }

    private val syncPrefs by lazy {
        context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun hasSyncCache(): Boolean {
        return syncPrefs.contains(KEY_THEME)
    }

    fun getInitialTheme(): AppTheme {
        val name = syncPrefs.getString(KEY_THEME, null) ?: return AppTheme.YELLOW
        return resolveTheme(name)
    }

    fun getInitialThemeMode(): ThemeMode {
        val name = syncPrefs.getString(KEY_THEME_MODE, null) ?: return ThemeMode.SYSTEM
        return try {
            ThemeMode.valueOf(name)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    fun getInitialAmoled(): Boolean {
        return syncPrefs.getBoolean(KEY_AMOLED, false)
    }

    /**
     * Resolves a persisted theme name to an [AppTheme], remapping themes that
     * have since been retired so an existing pick lands on its closest
     * replacement instead of silently resetting to the default.
     */
    fun resolveTheme(name: String): AppTheme = when (name) {
        "DRACULA" -> AppTheme.SYNTHWAVE
        "MONOKAI" -> AppTheme.MILES_MORALES
        else -> try {
            AppTheme.valueOf(name)
        } catch (e: IllegalArgumentException) {
            AppTheme.YELLOW
        }
    }

    private fun cacheTheme(themeName: String) {
        syncPrefs.edit().putString(KEY_THEME, themeName).apply()
    }

    private fun cacheThemeMode(themeMode: String) {
        syncPrefs.edit().putString(KEY_THEME_MODE, themeMode).apply()
    }

    private fun cacheAmoled(enabled: Boolean) {
        syncPrefs.edit().putBoolean(KEY_AMOLED, enabled).apply()
    }

    // Get the theme from local storage (Defaults to YELLOW)
    val themeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        val theme = preferences[THEME_KEY] ?: "YELLOW"
        cacheTheme(theme)
        theme
    }

    // Get the theme mode from local storage (Defaults to SYSTEM)
    val themeModeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        val mode = preferences[THEME_MODE_KEY] ?: "SYSTEM"
        cacheThemeMode(mode)
        mode
    }

    // Get the amoled / true black state from local storage (Defaults to false)
    val amoledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        val amoled = preferences[AMOLED_KEY] ?: false
        cacheAmoled(amoled)
        amoled
    }

    // Save the new theme to local storage
    suspend fun saveTheme(themeName: String) {
        cacheTheme(themeName)
        context.dataStore.edit { preferences ->
            preferences[THEME_KEY] = themeName
        }
    }

    // Save the theme mode (SYSTEM, LIGHT, DARK)
    suspend fun saveThemeMode(themeMode: String) {
        cacheThemeMode(themeMode)
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = themeMode
        }
    }

    // Save the amoled / true black state
    suspend fun saveAmoled(enabled: Boolean) {
        cacheAmoled(enabled)
        context.dataStore.edit { preferences ->
            preferences[AMOLED_KEY] = enabled
        }
    }
}

