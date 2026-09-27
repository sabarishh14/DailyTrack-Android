package com.example.dailytrack_mobile.notification.routines

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.dailytrack_mobile.data.local.routines.RoutineCheckInSettings
import com.example.dailytrack_mobile.data.reminder.ReminderSchedulerImpl
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** The alarm behind the nightly Routines check-in. Separate from the daily reminder. */
@Singleton
class RoutineCheckInScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Sets the next alarm from [settings], or clears it when the check-in is off. */
    fun schedule(settings: RoutineCheckInSettings.Settings) {
        if (!settings.enabled || settings.days.isEmpty()) {
            cancel()
            return
        }
        val triggerAt = ReminderSchedulerImpl.calculateNextTriggerTime(settings.time, settings.days)
        val alarm = alarmIntent()
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        try {
            if (exactAllowed) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, alarm)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, alarm)
            }
        } catch (e: SecurityException) {
            // Exact alarms were revoked between the check and the call.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, alarm)
        }
    }

    fun cancel() {
        alarmManager.cancel(alarmIntent())
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, RoutineCheckInReceiver::class.java).setAction(RoutineCheckInReceiver.ACTION_CHECK_IN),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private companion object {
        const val REQUEST_CODE = 2_001
    }
}
