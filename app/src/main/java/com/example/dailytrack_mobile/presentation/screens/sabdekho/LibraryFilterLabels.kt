package com.example.dailytrack_mobile.presentation.screens.sabdekho

import java.time.LocalDate
import kotlin.math.roundToInt

// Labels shared by the Stats charts and the Library filters they jump to.

/** Monday first, like the server's weekday numbers and the "By day" chart. */
val WeekdayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
val MonthNames = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)
private val MonthShortNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** The half-star ratings a log can have, best first. */
val RatingSteps = listOf("5.0", "4.5", "4.0", "3.5", "3.0", "2.5", "2.0", "1.5", "1.0", "0.5")

/** Which Library filter a removable chip stands for. */
enum class LibraryFacet { YEAR, MONTH, WEEK, WEEKDAY, RATING, LANGUAGE }

/**
 * The dates "week N" covers in a year, as the stats charts count weeks: ISO
 * weeks, with early-January days of last year's final week folded into week 1
 * and late-December days of next year's week 1 folded into week 52. Null for
 * "all" years, where a week number spans many date ranges.
 */
fun weekDates(year: String, week: Int): Pair<LocalDate, LocalDate>? {
    val y = year.toIntOrNull() ?: return null
    if (week !in 1..52) return null
    val jan4 = LocalDate.of(y, 1, 4)
    val monday = jan4.minusDays((jan4.dayOfWeek.value - 1).toLong()).plusWeeks((week - 1).toLong())
    val start = if (week == 1) LocalDate.of(y, 1, 1) else monday
    val end = if (week == 52) LocalDate.of(y, 12, 31) else monday.plusDays(6)
    return start to end
}

/** "16–22 Mar", or "28 Apr – 4 May" across a month; null when there's no single range. */
fun weekRangeLabel(year: String, week: Int): String? {
    val (start, end) = weekDates(year, week) ?: return null
    return if (start.month == end.month) {
        "${start.dayOfMonth}–${end.dayOfMonth} ${MonthShortNames[end.monthValue - 1]}"
    } else {
        "${start.dayOfMonth} ${MonthShortNames[start.monthValue - 1]} – ${end.dayOfMonth} ${MonthShortNames[end.monthValue - 1]}"
    }
}

/** "Week 12 · 16–22 Mar" for a year, "Week 12" across all years. */
fun weekLabel(year: String, week: Int): String =
    weekRangeLabel(year, week)?.let { "Week $week · $it" } ?: "Week $week"

/** "★★★★½" — rounded to the nearest half star. */
fun starsLabel(rating: Double): String {
    val halves = (rating * 2).roundToInt().coerceIn(0, 10)
    return "★".repeat(halves / 2) + if (halves % 2 == 1) "½" else ""
}
