package com.example.dailytrack_mobile.notification.routines

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.dailytrack_mobile.data.local.routines.RoutineAlarmSettings
import com.example.dailytrack_mobile.data.local.routines.RoutineAlarmSettings.AlarmKind
import com.example.dailytrack_mobile.data.local.routines.RoutineCheckInSettings
import com.example.dailytrack_mobile.data.local.routines.RoutineReminders
import com.example.dailytrack_mobile.data.repository.RoutinesRepository
import com.example.dailytrack_mobile.data.repository.engine
import com.example.dailytrack_mobile.domain.routines.CheckInStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import javax.inject.Inject

/**
 * The nightly alarm (start the check-in), a routine's own reminder, the
 * notifications' and alarms' buttons, and boot / app updates (set the alarms
 * again). An answer is saved on the phone and the notification moves on straight
 * away; sending it to the server comes after and may wait for a connection.
 */
@AndroidEntryPoint
class RoutineCheckInReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: RoutinesRepository
    @Inject lateinit var settings: RoutineCheckInSettings
    @Inject lateinit var alarmSettings: RoutineAlarmSettings
    @Inject lateinit var scheduler: RoutineCheckInScheduler
    @Inject lateinit var reminders: RoutineReminders

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val result = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_CHECK_IN -> checkIn(appContext, snoozed = intent.getBooleanExtra(EXTRA_SNOOZED, false))
                    ACTION_REMIND -> remind(
                        appContext,
                        intent.getLongExtra(EXTRA_ROUTINE_ID, -1L),
                        snoozed = intent.getBooleanExtra(EXTRA_SNOOZED, false)
                    )
                    ACTION_ANSWER -> answer(appContext, intent)
                    ACTION_SNOOZE -> {
                        val routineId = intent.getLongExtra(EXTRA_ROUTINE_ID, -1L)
                        RoutineAlarmService.stop(appContext)
                        RoutineReminderNotifier.cancel(appContext, routineId)
                        if (routineId >= 0) scheduler.snooze(routineId, alarmSettings.current().snoozeMinutes.toLong())
                    }
                    ACTION_DISMISS -> {
                        RoutineAlarmService.stop(appContext)
                        RoutineReminderNotifier.cancel(appContext, intent.getLongExtra(EXTRA_ROUTINE_ID, -1L))
                    }
                    ACTION_SNOOZE_CHECK_IN -> {
                        RoutineAlarmService.stop(appContext)
                        RoutineCheckInNotifier.cancel(appContext)
                        scheduler.snoozeCheckIn(alarmSettings.current().snoozeMinutes.toLong())
                    }
                    ACTION_LATER_CHECK_IN -> {
                        // Stops the ringing; tonight's questions stay in the shade to answer later.
                        RoutineAlarmService.stop(appContext)
                        RoutineCheckInNotifier.ask(appContext, repository.load().engine(), LocalDate.now(), quiet = true)
                    }
                    Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                        scheduler.schedule(settings.current())
                        val alarms = reminders.alarmIds()
                        reminders.all().forEach { (id, time) -> scheduler.scheduleReminder(id, time, alarm = id in alarms) }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Routine check-in step failed", e)
            } finally {
                result.finish()
            }
        }
    }

    /** Tomorrow's is set first (a snooze leaves it be); tonight's asks, or rings, if anything's open. */
    private suspend fun checkIn(context: Context, snoozed: Boolean) {
        val current = settings.current()
        if (!snoozed) scheduler.schedule(current)
        // Best effort: an offline phone asks from its own copy.
        withTimeoutOrNull(if (current.alarm) ALARM_SYNC_TIMEOUT_MS else SYNC_TIMEOUT_MS) { repository.refresh() }
        val engine = repository.load().engine()
        val today = LocalDate.now()
        if (!current.alarm) {
            RoutineCheckInNotifier.ask(context, engine, today)
            return
        }
        val open = engine.dayItems(today).count { it.needsAnswer }
        if (open == 0) return
        if (!ringCheckIn(context, alarmSettings.current(), today, open)) {
            RoutineCheckInNotifier.ask(context, engine, today)
        }
    }

    /**
     * Tomorrow's is set first (a snooze leaves it be); today's only shows, or
     * rings, if the routine is due and still open.
     */
    private suspend fun remind(context: Context, routineId: Long, snoozed: Boolean) {
        if (routineId < 0) return
        val time = reminders.all()[routineId]
        if (time == null) {
            scheduler.cancelReminder(routineId)
            return
        }
        val alarm = routineId in reminders.alarmIds()
        if (!snoozed) scheduler.scheduleReminder(routineId, time, alarm)
        // An alarm shouldn't wait long on a slow connection to find out it's already done.
        withTimeoutOrNull(if (alarm) ALARM_SYNC_TIMEOUT_MS else SYNC_TIMEOUT_MS) { repository.refresh() }
        val snapshot = repository.load()
        if (snapshot.owner != null && snapshot.routines.none { it.id == routineId }) {
            // Deleted (maybe on another device): nothing left to remind about.
            reminders.set(routineId, null)
            scheduler.cancelReminder(routineId)
            return
        }
        val engine = snapshot.engine()
        val routine = engine.routines.firstOrNull { it.id == routineId } ?: return // archived
        val item = engine.itemOn(routine, LocalDate.now())
        if (item == null || !item.needsAnswer) return
        if (!alarm) {
            RoutineReminderNotifier.show(context, item)
            return
        }
        val ring = RoutineAlarmService.Alarm.of(
            context, alarmSettings.current(), AlarmKind.REMINDER,
            routineId = routine.id, date = item.date,
            emoji = routine.emoji, name = routine.name, text = RoutineReminderNotifier.textFor(item)
        )
        if (!RoutineAlarmService.ring(context, ring)) RoutineReminderNotifier.ringWithoutService(context, ring)
    }

    private suspend fun answer(context: Context, intent: Intent) {
        val routineId = intent.getLongExtra(EXTRA_ROUTINE_ID, -1L)
        val date = intent.getStringExtra(EXTRA_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val status = CheckInStatus.from(intent.getStringExtra(EXTRA_STATUS))
        if (routineId < 0 || date == null || status == null) return
        repository.setCheckIn(routineId, date, status)
        val engine = repository.load().engine()
        if (intent.getBooleanExtra(EXTRA_FROM_REMINDER, false)) {
            // A reminder's ❤️: just that one. A check-in on screen skips past it.
            RoutineAlarmService.stopIfFor(context, routineId)
            RoutineReminderNotifier.cancel(context, routineId)
            RoutineCheckInNotifier.syncIfShowing(context, engine, date)
        } else {
            RoutineCheckInNotifier.afterAnswer(context, engine, date)
        }
        withTimeoutOrNull(SYNC_TIMEOUT_MS) { repository.flush() }
    }

    companion object {
        const val ACTION_CHECK_IN = "com.example.dailytrack_mobile.ACTION_ROUTINE_CHECK_IN"
        const val ACTION_ANSWER = "com.example.dailytrack_mobile.ACTION_ROUTINE_ANSWER"
        const val ACTION_REMIND = "com.example.dailytrack_mobile.ACTION_ROUTINE_REMIND"
        const val ACTION_SNOOZE = "com.example.dailytrack_mobile.ACTION_ROUTINE_SNOOZE"
        const val ACTION_DISMISS = "com.example.dailytrack_mobile.ACTION_ROUTINE_DISMISS"
        const val ACTION_SNOOZE_CHECK_IN = "com.example.dailytrack_mobile.ACTION_ROUTINE_SNOOZE_CHECK_IN"
        const val ACTION_LATER_CHECK_IN = "com.example.dailytrack_mobile.ACTION_ROUTINE_LATER_CHECK_IN"
        const val EXTRA_FROM_REMINDER = "from_reminder"
        const val EXTRA_SNOOZED = "snoozed"
        const val EXTRA_ROUTINE_ID = "routine_id"
        const val EXTRA_DATE = "date"
        const val EXTRA_STATUS = "status"

        private const val TAG = "RoutineCheckIn"
        private const val SYNC_TIMEOUT_MS = 8_000L
        private const val ALARM_SYNC_TIMEOUT_MS = 4_000L
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Rings tonight's check-in. False when it couldn't ring, so the caller can ask instead. */
        fun ringCheckIn(context: Context, settings: RoutineAlarmSettings.Settings, date: LocalDate, open: Int): Boolean =
            RoutineAlarmService.ring(
                context,
                RoutineAlarmService.Alarm.of(
                    context, settings, AlarmKind.CHECK_IN,
                    routineId = -1L, date = date, emoji = "🌙", name = "Nightly check-in",
                    text = if (open == 1) "1 routine to check in" else "$open routines to check in"
                )
            )
    }
}
