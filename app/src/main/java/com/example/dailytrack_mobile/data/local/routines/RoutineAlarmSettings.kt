package com.example.dailytrack_mobile.data.local.routines

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.dailytrack_mobile.data.local.datastore.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How Routines alarms ring on this phone: the sound for a routine's alarm and
 * for the nightly check-in, vibration, a gentle start and the snooze length.
 */
@Singleton
class RoutineAlarmSettings @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    data class Settings(
        /** A sound's URI, null for the phone's default alarm, or [SILENT]. */
        val reminderSound: String? = null,
        val checkInSound: String? = null,
        val vibrate: Boolean = true,
        /** Starts quiet and gets louder over [GENTLE_RAMP_MS]. */
        val gentle: Boolean = true,
        val snoozeMinutes: Int = 10
    ) {
        fun soundFor(kind: AlarmKind): String? = if (kind == AlarmKind.CHECK_IN) checkInSound else reminderSound
    }

    enum class AlarmKind { REMINDER, CHECK_IN }

    val settings: Flow<Settings> = context.dataStore.data.map { preferences ->
        Settings(
            reminderSound = preferences[KEY_REMINDER_SOUND]?.takeIf { it.isNotEmpty() },
            checkInSound = preferences[KEY_CHECK_IN_SOUND]?.takeIf { it.isNotEmpty() },
            vibrate = preferences[KEY_VIBRATE] ?: true,
            gentle = preferences[KEY_GENTLE] ?: true,
            snoozeMinutes = (preferences[KEY_SNOOZE] ?: 10).coerceIn(1, 60)
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun update(transform: (Settings) -> Settings): Settings {
        val next = transform(current())
        context.dataStore.edit { preferences ->
            preferences[KEY_REMINDER_SOUND] = next.reminderSound.orEmpty()
            preferences[KEY_CHECK_IN_SOUND] = next.checkInSound.orEmpty()
            preferences[KEY_VIBRATE] = next.vibrate
            preferences[KEY_GENTLE] = next.gentle
            preferences[KEY_SNOOZE] = next.snoozeMinutes
        }
        return next
    }

    companion object {
        const val SILENT = "silent"
        const val GENTLE_RAMP_MS = 30_000L
        val SNOOZE_CHOICES = listOf(5, 10, 15, 20, 30)

        private val KEY_REMINDER_SOUND = stringPreferencesKey("routine_alarm_sound")
        private val KEY_CHECK_IN_SOUND = stringPreferencesKey("routine_checkin_alarm_sound")
        private val KEY_VIBRATE = booleanPreferencesKey("routine_alarm_vibrate")
        private val KEY_GENTLE = booleanPreferencesKey("routine_alarm_gentle")
        private val KEY_SNOOZE = intPreferencesKey("routine_alarm_snooze_minutes")
    }
}
