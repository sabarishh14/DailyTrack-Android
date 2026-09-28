package com.example.dailytrack_mobile.data.local.routines

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.dailytrack_mobile.data.local.datastore.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

private val DEFAULT_TIME: LocalTime = LocalTime.of(22, 0)

/** When the nightly Routines check-in comes, if at all, and whether it rings. Stored on this phone. */
@Singleton
class RoutineCheckInSettings @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    data class Settings(
        val enabled: Boolean = false,
        val time: LocalTime = DEFAULT_TIME,
        val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
        /** Ring like an alarm, with a screen to answer everything, rather than a notification. */
        val alarm: Boolean = false
    )

    val settings: Flow<Settings> = context.dataStore.data.map { preferences ->
        Settings(
            enabled = preferences[KEY_ENABLED] ?: false,
            time = preferences[KEY_TIME]?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: DEFAULT_TIME,
            days = preferences[KEY_DAYS]
                ?.mapNotNull { name -> runCatching { DayOfWeek.valueOf(name) }.getOrNull() }
                ?.toSet()
                ?: DayOfWeek.entries.toSet(),
            alarm = preferences[KEY_ALARM] ?: false
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun update(transform: (Settings) -> Settings): Settings {
        val next = transform(current())
        context.dataStore.edit { preferences ->
            preferences[KEY_ENABLED] = next.enabled
            preferences[KEY_TIME] = next.time.toString()
            preferences[KEY_DAYS] = next.days.map { it.name }.toSet()
            preferences[KEY_ALARM] = next.alarm
        }
        return next
    }

    private companion object {
        val KEY_ENABLED = booleanPreferencesKey("routine_checkin_enabled")
        val KEY_TIME = stringPreferencesKey("routine_checkin_time")
        val KEY_DAYS = stringSetPreferencesKey("routine_checkin_days")
        val KEY_ALARM = booleanPreferencesKey("routine_checkin_alarm")
    }
}
