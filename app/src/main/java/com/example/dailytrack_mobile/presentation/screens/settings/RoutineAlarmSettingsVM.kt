package com.example.dailytrack_mobile.presentation.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailytrack_mobile.data.local.routines.RoutineAlarmSettings
import com.example.dailytrack_mobile.data.local.routines.RoutineAlarmSettings.AlarmKind
import com.example.dailytrack_mobile.notification.routines.RoutineAlarmService
import com.example.dailytrack_mobile.notification.routines.RoutineReminderNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Settings → Reminders & alarms: how Routines alarms ring. */
@HiltViewModel
class RoutineAlarmSettingsVM @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val alarmSettings: RoutineAlarmSettings
) : ViewModel() {

    val settings: StateFlow<RoutineAlarmSettings.Settings> =
        alarmSettings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, RoutineAlarmSettings.Settings())

    fun setSound(kind: AlarmKind, value: String?) = update {
        if (kind == AlarmKind.CHECK_IN) it.copy(checkInSound = value) else it.copy(reminderSound = value)
    }

    fun setVibrate(on: Boolean) = update { it.copy(vibrate = on) }

    fun setGentle(on: Boolean) = update { it.copy(gentle = on) }

    fun setSnooze(minutes: Int) = update { it.copy(snoozeMinutes = minutes) }

    /** Rings a routine alarm now, exactly as it would, to try the sound and the screen. */
    fun test() {
        viewModelScope.launch {
            val alarm = RoutineAlarmService.Alarm.of(
                context, alarmSettings.current(), AlarmKind.REMINDER,
                routineId = -1L, date = LocalDate.now(), emoji = "⏰", name = "Test alarm",
                text = "This is how your routine alarms ring. Touch anywhere to silence it.",
                test = true
            )
            if (!RoutineAlarmService.ring(context, alarm)) RoutineReminderNotifier.ringWithoutService(context, alarm)
        }
    }

    private fun update(transform: (RoutineAlarmSettings.Settings) -> RoutineAlarmSettings.Settings) {
        viewModelScope.launch { alarmSettings.update(transform) }
    }
}
