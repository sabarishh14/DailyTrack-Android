package com.example.dailytrack_mobile.notification.routines

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.dailytrack_mobile.MainActivity
import com.example.dailytrack_mobile.R
import com.example.dailytrack_mobile.data.local.routines.RoutineAlarmSettings.AlarmKind
import com.example.dailytrack_mobile.domain.routines.CheckInStatus
import com.example.dailytrack_mobile.domain.routines.DayItem
import com.example.dailytrack_mobile.domain.routines.PeriodUnit
import com.example.dailytrack_mobile.domain.routines.RoutineKind
import com.example.dailytrack_mobile.presentation.navigation.Routes
import java.time.LocalDate

/**
 * A routine's own reminder ("Gym at 6 PM"): one notification per routine, only
 * on days it's due and still open, with a ❤️ Done button. A reminder set as an
 * alarm is rung by [RoutineAlarmService]; what it leaves behind once silenced is
 * [showQuiet].
 */
object RoutineReminderNotifier {

    private const val CHANNEL_ID = "routine_reminders"
    private const val FALLBACK_ALARM_CHANNEL_ID = "routine_alarms"
    private const val ID_BASE = 40_000

    fun show(context: Context, item: DayItem) {
        val routine = item.routine
        post(context, routine.id, builder(context, routine.id, item.date, titleFor(item), textFor(item)).build())
    }

    /** The same reminder without a sound: what's left once an alarm has been silenced. */
    fun showQuiet(context: Context, routineId: Long, date: LocalDate, title: String, text: String) {
        post(context, routineId, builder(context, routineId, date, title, text).setSilent(true).build())
    }

    /**
     * Rings with a notification alone, for when Android won't let [RoutineAlarmService]
     * start: its channel's alarm sound on repeat until it's answered or the shade opens.
     */
    fun ringWithoutService(context: Context, alarm: RoutineAlarmService.Alarm) {
        ensureFallbackAlarmChannel(context)
        val screen = PendingIntent.getActivity(
            context, idFor(alarm.routineId),
            RoutineAlarmActivity.intent(context, alarm, opened = true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, FALLBACK_ALARM_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(alarm.title)
            .setContentText(alarm.text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(screen, true)
            .setContentIntent(screen)
            .setTimeoutAfter(RoutineAlarmService.RING_FOR_MS)
        if (alarm.kind == AlarmKind.REMINDER && !alarm.test) {
            builder.addAction(0, "❤️ Done", doneIntent(context, alarm.routineId, alarm.localDate))
        }
        val notification = builder.build()
        notification.flags = notification.flags or Notification.FLAG_INSISTENT
        post(context, alarm.routineId, notification)
    }

    fun cancel(context: Context, routineId: Long) {
        NotificationManagerCompat.from(context).cancel(idFor(routineId))
    }

    fun titleFor(item: DayItem): String = listOfNotNull(item.routine.emoji, item.routine.name).joinToString(" ")

    fun textFor(item: DayItem): String {
        val progress = item.progress?.let {
            "${it.done} of ${it.needed} this ${if (it.unit == PeriodUnit.WEEK) "week" else "month"}. "
        }.orEmpty()
        val due = item.dueDate?.let { if (item.date > it) "It's overdue. " else "It's due today. " }.orEmpty()
        val nudge = if (item.routine.kind == RoutineKind.AVOID) "Stay strong today." else "Time for it. Tap ❤️ once it's done."
        return progress + due + nudge
    }

    private fun builder(context: Context, routineId: Long, date: LocalDate, title: String, text: String): NotificationCompat.Builder {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openRoutines(context, routineId))
            .addAction(0, "❤️ Done", doneIntent(context, routineId, date))
    }

    private fun post(context: Context, routineId: Long, notification: Notification) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        try {
            NotificationManagerCompat.from(context).notify(idFor(routineId), notification)
        } catch (e: SecurityException) {
            // Permission withdrawn in the meantime.
        }
    }

    private fun idFor(routineId: Long): Int = ID_BASE + (routineId % 1_000_000).toInt()

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Routine reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "The reminder you set on a routine, on days it's due"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun ensureFallbackAlarmChannel(context: Context) {
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val channel = NotificationChannel(FALLBACK_ALARM_CHANNEL_ID, "Routine alarms", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Used only if an alarm can't ring the usual way"
            setSound(
                sound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 600, 800, 600)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun openRoutines(context: Context, routineId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("route", Routes.Routines.route)
        }
        return PendingIntent.getActivity(
            context, idFor(routineId), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun doneIntent(context: Context, routineId: Long, date: LocalDate): PendingIntent {
        // Its own data URI: pending intents match on action and target, not extras,
        // so this can never be confused with a check-in answer button.
        val intent = Intent(context, RoutineCheckInReceiver::class.java)
            .setAction(RoutineCheckInReceiver.ACTION_ANSWER)
            .setData(Uri.parse("dailytrack://routine-reminder/$routineId"))
            .putExtra(RoutineCheckInReceiver.EXTRA_ROUTINE_ID, routineId)
            .putExtra(RoutineCheckInReceiver.EXTRA_DATE, date.toString())
            .putExtra(RoutineCheckInReceiver.EXTRA_STATUS, CheckInStatus.DONE.key)
            .putExtra(RoutineCheckInReceiver.EXTRA_FROM_REMINDER, true)
        return PendingIntent.getBroadcast(
            context, idFor(routineId), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
