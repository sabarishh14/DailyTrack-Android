package com.example.dailytrack_mobile.data.local.routines

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.dailytrack_mobile.data.local.datastore.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-routine reminder times ("Gym at 6 PM"). Kept on this phone, like the
 * nightly check-in: a reminder is about this phone buzzing, not the routine.
 */
@Singleton
class RoutineReminders @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    /** Routine id → when to remind. */
    val reminders: Flow<Map<Long, LocalTime>> = context.dataStore.data.map { preferences ->
        preferences[KEY].orEmpty().mapNotNull { entry ->
            val id = entry.substringBefore('@').toLongOrNull()
            val time = runCatching { LocalTime.parse(entry.substringAfter('@')) }.getOrNull()
            if (id != null && time != null) id to time else null
        }.toMap()
    }

    suspend fun all(): Map<Long, LocalTime> = reminders.first()

    /** Routines whose reminder rings like an alarm rather than arriving as a notification. */
    val alarms: Flow<Set<Long>> = context.dataStore.data.map { preferences ->
        preferences[ALARMS_KEY].orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
    }

    suspend fun alarmIds(): Set<Long> = alarms.first()

    /** Sets or, with a null [time], removes the routine's reminder; [alarm] makes it ring. */
    suspend fun set(routineId: Long, time: LocalTime?, alarm: Boolean = false) {
        context.dataStore.edit { preferences ->
            val others = preferences[KEY].orEmpty().filterNot { it.substringBefore('@') == routineId.toString() }
            preferences[KEY] = if (time == null) others.toSet() else (others + "$routineId@$time").toSet()
            val otherAlarms = preferences[ALARMS_KEY].orEmpty() - routineId.toString()
            preferences[ALARMS_KEY] = if (time != null && alarm) otherAlarms + routineId.toString() else otherAlarms
        }
    }

    private companion object {
        val KEY = stringSetPreferencesKey("routine_reminders")
        val ALARMS_KEY = stringSetPreferencesKey("routine_reminder_alarms")
    }
}
