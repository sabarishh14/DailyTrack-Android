package com.example.dailytrack_mobile.notification.routines

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.dailytrack_mobile.MainActivity
import com.example.dailytrack_mobile.data.local.routines.RoutineCheckInSettings
import com.example.dailytrack_mobile.data.reminder.ReminderSchedulerImpl
import com.example.dailytrack_mobile.presentation.navigation.Routes
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The alarms behind Routines: the nightly check-in, and each routine's own
 * reminder. Separate from the app's daily reminder.
 */
@Singleton
class RoutineCheckInScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Sets the next check-in from [settings], or clears it when the check-in is off.
     * One that rings is set as an alarm clock, like a routine's alarm.
     */
    fun schedule(settings: RoutineCheckInSettings.Settings) {
        if (!settings.enabled || settings.days.isEmpty()) {
            cancel()
            return
        }
        val triggerAt = ReminderSchedulerImpl.calculateNextTriggerTime(settings.time, settings.days)
        if (settings.alarm) setAlarmClock(triggerAt, alarmIntent()) else setAlarm(triggerAt, alarmIntent())
    }

    fun cancel() {
        alarmManager.cancel(alarmIntent())
        alarmManager.cancel(checkInSnoozeIntent())
    }

    /** Rings tonight's check-in again in [minutes], leaving the nightly one as it is. */
    fun snoozeCheckIn(minutes: Long) {
        setAlarmClock(System.currentTimeMillis() + minutes * 60_000, checkInSnoozeIntent())
    }

    /**
     * A routine's reminder at [time]: the next one today if it's still ahead, else tomorrow.
     * An [alarm] is set as an alarm clock, so it's exact and shows as the phone's next alarm.
     */
    fun scheduleReminder(routineId: Long, time: LocalTime, alarm: Boolean = false) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(time)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (alarm) setAlarmClock(triggerAt, reminderIntent(routineId)) else setAlarm(triggerAt, reminderIntent(routineId))
    }

    /** Rings a routine's alarm again in [minutes], on top of its usual one. */
    fun snooze(routineId: Long, minutes: Long) {
        setAlarmClock(System.currentTimeMillis() + minutes * 60_000, snoozeIntent(routineId))
    }

    fun cancelReminder(routineId: Long) {
        alarmManager.cancel(reminderIntent(routineId))
        alarmManager.cancel(snoozeIntent(routineId))
    }

    private fun setAlarmClock(triggerAt: Long, alarm: PendingIntent) {
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (!exactAllowed) {
            setAlarm(triggerAt, alarm)
            return
        }
        try {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, openRoutines()), alarm)
        } catch (e: SecurityException) {
            setAlarm(triggerAt, alarm)
        }
    }

    /** What the system's "next alarm" opens: the Routines page. */
    private fun openRoutines(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_CODE,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("route", Routes.Routines.route),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun setAlarm(triggerAt: Long, alarm: PendingIntent) {
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

    /** One per routine, told apart by its data URI. */
    private fun reminderIntent(routineId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, RoutineCheckInReceiver::class.java)
            .setAction(RoutineCheckInReceiver.ACTION_REMIND)
            .setData(Uri.parse("dailytrack://routine-reminder/$routineId"))
            .putExtra(RoutineCheckInReceiver.EXTRA_ROUTINE_ID, routineId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** A snoozed alarm: its own URI, so it neither replaces nor is replaced by the daily one. */
    private fun snoozeIntent(routineId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, RoutineCheckInReceiver::class.java)
            .setAction(RoutineCheckInReceiver.ACTION_REMIND)
            .setData(Uri.parse("dailytrack://routine-snooze/$routineId"))
            .putExtra(RoutineCheckInReceiver.EXTRA_ROUTINE_ID, routineId)
            .putExtra(RoutineCheckInReceiver.EXTRA_SNOOZED, true),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun checkInSnoozeIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, RoutineCheckInReceiver::class.java)
            .setAction(RoutineCheckInReceiver.ACTION_CHECK_IN)
            .setData(Uri.parse("dailytrack://routine-checkin-snooze"))
            .putExtra(RoutineCheckInReceiver.EXTRA_SNOOZED, true),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

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
