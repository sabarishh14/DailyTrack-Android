package com.example.dailytrack_mobile.notification.routines

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import com.example.dailytrack_mobile.MainActivity
import com.example.dailytrack_mobile.R
import com.example.dailytrack_mobile.domain.routines.CheckInStatus
import com.example.dailytrack_mobile.domain.routines.DayItem
import com.example.dailytrack_mobile.domain.routines.PeriodUnit
import com.example.dailytrack_mobile.domain.routines.RoutineEngine
import com.example.dailytrack_mobile.domain.routines.RoutineKind
import com.example.dailytrack_mobile.presentation.navigation.Routes
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The nightly check-in: one notification that asks about each routine still
 * open, one at a time, with ❤️ Done / 😭 Missed / Skip buttons, and ends with
 * the day's result. Answers go to [RoutineCheckInReceiver].
 */
object RoutineCheckInNotifier {

    private const val CHANNEL_ID = "routine_checkins"
    private const val NOTIFICATION_ID = 3_001
    private const val EXTRA_SHOWN_DATE = "routine_checkin_date"
    private const val EXTRA_IS_QUESTION = "routine_checkin_question"
    private const val WRAP_UP_TIMEOUT_MS = 90 * 60 * 1000L

    /** Starts the check-in with the first open routine. False when there's nothing to ask. */
    fun ask(context: Context, engine: RoutineEngine, date: LocalDate): Boolean {
        val items = engine.dayItems(date)
        val next = items.firstOrNull { it.needsAnswer } ?: return false
        showQuestion(context, engine, items, next, date)
        return true
    }

    /** After an answer: the next routine, or the day's result once all are answered. */
    fun afterAnswer(context: Context, engine: RoutineEngine, date: LocalDate) {
        val items = engine.dayItems(date)
        val next = items.firstOrNull { it.needsAnswer }
        if (next != null) showQuestion(context, engine, items, next, date) else showWrapUp(context, engine, date)
    }

    /**
     * Keeps a question that's on screen in step with answers given in the app:
     * moves it on to what's still open, or removes it once nothing is. The
     * day's result, once shown, is left alone.
     */
    fun syncIfShowing(context: Context, engine: RoutineEngine, date: LocalDate) {
        val showing = runCatching {
            context.getSystemService(NotificationManager::class.java).activeNotifications
                .firstOrNull { it.id == NOTIFICATION_ID }
        }.getOrNull() ?: return
        val extras = showing.notification.extras
        if (!extras.getBoolean(EXTRA_IS_QUESTION) || extras.getString(EXTRA_SHOWN_DATE) != date.toString()) return
        val items = engine.dayItems(date)
        val next = items.firstOrNull { it.needsAnswer }
        if (next != null) showQuestion(context, engine, items, next, date) else cancel(context)
    }

    fun showWrapUp(context: Context, engine: RoutineEngine, date: LocalDate) {
        val stats = engine.dayStats(date)
        val percent = stats.fraction?.let { (it * 100).roundToInt() }
        val title = when {
            percent == 100 -> "🎉 Perfect day!"
            percent != null && percent >= 70 -> "✨ Nicely done"
            else -> "🌙 All checked in"
        }
        val text = if (percent != null) {
            "${stats.done} of ${stats.total} done · $percent% ${dayWord(date)}"
        } else {
            "Nothing was due ${dayWord(date)}."
        }
        val notification = builder(context, date, isQuestion = false)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setTimeoutAfter(WRAP_UP_TIMEOUT_MS)
            .build()
        post(context, notification)
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    // ── Building ─────────────────────────────────────────────────────────────

    private fun showQuestion(context: Context, engine: RoutineEngine, items: List<DayItem>, item: DayItem, date: LocalDate) {
        // "2 of 5": what tonight is about, whether answered already or still open.
        val counted = items.filter { it.required || it.status != null || it.progress?.met == false }
        val position = counted.count { it.status != null } + 1
        val routine = item.routine
        val question = questionFor(item, date)
        val stats = engine.dayStats(date)
        val soFar = if (stats.total > 0) "So far: ${stats.done} of ${stats.total} done" else null

        val notification = builder(context, date, isQuestion = true)
            .setContentTitle(listOfNotNull(routine.emoji, routine.name).joinToString(" "))
            .setContentText(question)
            .setStyle(NotificationCompat.BigTextStyle().bigText(listOfNotNull(question, soFar).joinToString("\n")))
            .setSubText("Check-in · $position of ${counted.size.coerceAtLeast(position)}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .addAction(0, "❤️ Done", answerIntent(context, routine.id, date, CheckInStatus.DONE))
            .addAction(0, "😭 Missed", answerIntent(context, routine.id, date, CheckInStatus.MISSED))
            .addAction(0, "⏭ Skip", answerIntent(context, routine.id, date, CheckInStatus.SKIPPED))
            .build()
        post(context, notification)
    }

    private fun questionFor(item: DayItem, date: LocalDate): String {
        val due = item.dueDate?.let { due ->
            if (date > due) "Overdue since ${due.format(SHORT_DATE)}. " else "Due today. "
        }.orEmpty()
        val progress = item.progress?.let {
            "${it.done} of ${it.needed} this ${if (it.unit == PeriodUnit.WEEK) "week" else "month"}. "
        }.orEmpty()
        val ask = if (item.routine.kind == RoutineKind.AVOID) {
            "Did you stick to it ${dayWord(date)}?"
        } else {
            "Did you do it ${dayWord(date)}?"
        }
        return due + progress + ask
    }

    /** "today", or "on Sunday" for a check-in answered after midnight. */
    private fun dayWord(date: LocalDate): String =
        if (date == LocalDate.now()) "today" else "on ${date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}"

    private fun builder(context: Context, date: LocalDate, isQuestion: Boolean): NotificationCompat.Builder {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOnlyAlertOnce(true)
            .setContentIntent(openRoutines(context))
            .addExtras(bundleOf(EXTRA_SHOWN_DATE to date.toString(), EXTRA_IS_QUESTION to isQuestion))
    }

    private fun post(context: Context, notification: android.app.Notification) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission withdrawn in the meantime; nothing to show.
        }
    }

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Routine check-ins", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "The nightly check-in that asks about each of your routines"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun openRoutines(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("route", Routes.Routines.route)
        }
        return PendingIntent.getActivity(
            context, NOTIFICATION_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun answerIntent(context: Context, routineId: Long, date: LocalDate, status: CheckInStatus): PendingIntent {
        val intent = Intent(context, RoutineCheckInReceiver::class.java)
            .setAction(RoutineCheckInReceiver.ACTION_ANSWER)
            .putExtra(RoutineCheckInReceiver.EXTRA_ROUTINE_ID, routineId)
            .putExtra(RoutineCheckInReceiver.EXTRA_DATE, date.toString())
            .putExtra(RoutineCheckInReceiver.EXTRA_STATUS, status.key)
        // One request code per routine and answer, so the three buttons never share extras.
        val requestCode = (routineId * 4 + status.ordinal).toInt()
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private val SHORT_DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
}
