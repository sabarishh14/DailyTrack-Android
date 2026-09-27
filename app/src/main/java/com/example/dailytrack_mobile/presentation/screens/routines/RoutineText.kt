package com.example.dailytrack_mobile.presentation.screens.routines

import com.example.dailytrack_mobile.domain.routines.CheckInStatus
import com.example.dailytrack_mobile.domain.routines.DayItem
import com.example.dailytrack_mobile.domain.routines.IntervalUnit
import com.example.dailytrack_mobile.domain.routines.PeriodUnit
import com.example.dailytrack_mobile.domain.routines.Routine
import com.example.dailytrack_mobile.domain.routines.RoutineKind
import com.example.dailytrack_mobile.domain.routines.RoutineSchedule
import com.example.dailytrack_mobile.domain.routines.Streak
import com.example.dailytrack_mobile.domain.routines.StreakUnit
import com.example.dailytrack_mobile.domain.routines.bit
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/** Wording shared by the Routines screens. */
internal object RoutineText {

    private val weekdayDate = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
    private val longDate = DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.ENGLISH)
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val clock = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    const val WEEKDAYS = 0b0011111
    const val WEEKENDS = 0b1100000
    const val ALL_DAYS = 0b1111111

    fun weekdays(mask: Int?): String = when (val days = mask ?: 0) {
        ALL_DAYS -> "Every day"
        WEEKDAYS -> "Weekdays"
        WEEKENDS -> "Weekends"
        0 -> "No days picked"
        else -> DayOfWeek.entries.filter { days and it.bit() != 0 }.joinToString(", ") { shortDay(it) }
    }

    fun shortDay(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

    fun narrowDay(day: DayOfWeek): String = day.getDisplayName(TextStyle.NARROW, Locale.ENGLISH)

    fun interval(every: Int, unit: IntervalUnit): String {
        val word = when (unit) {
            IntervalUnit.DAY -> "day"
            IntervalUnit.WEEK -> "week"
            IntervalUnit.MONTH -> "month"
        }
        return if (every == 1) "Every $word" else "Every $every ${word}s"
    }

    /** "Every day", "Mon, Wed, Fri", "4× a week", "Every 3 months". */
    fun schedule(routine: Routine): String = when (routine.schedule) {
        RoutineSchedule.DAILY -> "Every day"
        RoutineSchedule.DAYS -> weekdays(routine.days)
        RoutineSchedule.WEEKLY -> "${routine.target ?: 1}× a week"
        RoutineSchedule.MONTHLY -> "${routine.target ?: 1}× a month"
        RoutineSchedule.INTERVAL -> interval(routine.every ?: 1, routine.unit ?: IntervalUnit.DAY)
    }

    /** The line under a routine in "Your routines". */
    fun summary(routine: Routine, today: LocalDate, nextDue: LocalDate?): String {
        val parts = mutableListOf<String>()
        if (routine.kind == RoutineKind.AVOID) parts += "Quitting"
        val end = routine.endDate
        when {
            end != null && today > end -> parts += "Challenge finished"
            end != null && today < routine.startDate -> parts += "${routine.challengeLength}-day challenge"
            end != null -> parts += "Day ${routine.challengeDay(today)} of ${routine.challengeLength}"
            else -> parts += schedule(routine)
        }
        if (nextDue != null) parts += "next ${dueIn(nextDue, today, sentenceStart = false)}"
        return parts.joinToString(" · ")
    }

    /** The line under a routine on a day's list. */
    fun itemLine(item: DayItem, streak: Streak?): String {
        val routine = item.routine
        val parts = mutableListOf<String>()
        routine.challengeDay(item.date)?.let { parts += "Day $it of ${routine.challengeLength}" }
        when (routine.schedule) {
            RoutineSchedule.WEEKLY, RoutineSchedule.MONTHLY -> item.progress?.let { progress ->
                val period = if (progress.unit == PeriodUnit.WEEK) "week" else "month"
                parts += if (progress.met && progress.needed > 0) {
                    "Done for this $period"
                } else {
                    "${progress.done} of ${progress.needed} this $period"
                }
            }
            RoutineSchedule.INTERVAL -> item.dueDate?.let { due ->
                val late = ChronoUnit.DAYS.between(due, item.date)
                parts += when {
                    item.status == CheckInStatus.DONE && late <= 0 -> "Done early"
                    late < 0 -> "Due ${due.format(dayMonth)}"
                    late == 0L -> "Due today"
                    late == 1L -> "Overdue by a day"
                    else -> "Overdue by $late days"
                }
            }
            else -> if (parts.isEmpty()) parts += schedule(routine)
        }
        streakBadge(streak)?.let { parts += it }
        return parts.joinToString(" · ")
    }

    fun streakBadge(streak: Streak?): String? =
        streak?.takeIf { it.current >= 2 }?.let { "🔥 ${it.current}" }

    fun streakLength(count: Int, unit: StreakUnit): String = "$count ${
        when (unit) {
            StreakUnit.DAY -> if (count == 1) "day" else "days"
            StreakUnit.WEEK -> if (count == 1) "week" else "weeks"
            StreakUnit.MONTH -> if (count == 1) "month" else "months"
            StreakUnit.TIME -> "on time"
        }
    }"

    fun percent(fraction: Double?): String = fraction?.let { "${(it * 100).roundToInt()}%" } ?: "—"

    fun percent(fraction: Float?): String = percent(fraction?.toDouble())

    fun weekdayDate(date: LocalDate): String = date.format(weekdayDate)

    fun longDate(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(longDate)
    }

    fun time(time: LocalTime): String = time.format(clock)

    /** "Today", "Tomorrow", "In 3 days" or "12 Oct"; lower case mid-sentence. */
    fun dueIn(date: LocalDate, today: LocalDate, sentenceStart: Boolean = true): String {
        val days = ChronoUnit.DAYS.between(today, date)
        val phrase = when {
            days <= 0L -> "Today"
            days == 1L -> "Tomorrow"
            days < 7L -> "In $days days"
            else -> return date.format(dayMonth)
        }
        return if (sentenceStart) phrase else phrase.lowercase()
    }
}
