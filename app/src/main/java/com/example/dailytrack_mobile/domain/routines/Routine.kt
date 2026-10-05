package com.example.dailytrack_mobile.domain.routines

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class RoutineKind(val key: String) {
    /** Something to do: read, gym, brush at night. */
    BUILD("build"),

    /** Something to stay away from: no sugar, no phone in bed. */
    AVOID("avoid");

    companion object {
        fun from(key: String?): RoutineKind = entries.firstOrNull { it.key == key } ?: BUILD
    }
}

enum class RoutineSchedule(val key: String) {
    DAILY("daily"),

    /** On chosen weekdays ([Routine.days]). */
    DAYS("days"),

    /** [Routine.target] times each Monday–Sunday week, on any days. */
    WEEKLY("weekly"),

    /** [Routine.target] times each calendar month, on any days. */
    MONTHLY("monthly"),

    /** A chore every [Routine.every] [Routine.unit]s, counted from when it was last done. */
    INTERVAL("interval");

    companion object {
        fun from(key: String?): RoutineSchedule = entries.firstOrNull { it.key == key } ?: DAILY
    }
}

enum class IntervalUnit(val key: String) {
    DAY("day"), WEEK("week"), MONTH("month");

    companion object {
        fun from(key: String?): IntervalUnit? = entries.firstOrNull { it.key == key }
    }
}

enum class CheckInStatus(val key: String) {
    DONE("done"),
    MISSED("missed"),

    /** Excused: a genuine reason. Doesn't count for or against, and doesn't break streaks. */
    SKIPPED("skipped");

    companion object {
        fun from(key: String?): CheckInStatus? = entries.firstOrNull { it.key == key }
    }
}

/** The weekday bitmask the server stores: Monday = 1 … Sunday = 64. */
fun DayOfWeek.bit(): Int = 1 shl (value - 1)

data class Routine(
    val id: Long,
    val name: String,
    val emoji: String? = null,
    val kind: RoutineKind = RoutineKind.BUILD,
    val schedule: RoutineSchedule = RoutineSchedule.DAILY,
    val days: Int? = null,
    val target: Int? = null,
    val every: Int? = null,
    val unit: IntervalUnit? = null,
    /** First day it counts; for [RoutineSchedule.INTERVAL], the first due date. */
    val startDate: LocalDate,
    /** A challenge's last day, inclusive. */
    val endDate: LocalDate? = null,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    /** The day it was added in the app; null counts it as added on [startDate]. */
    val createdOn: LocalDate? = null
) {
    val isChallenge: Boolean get() = endDate != null

    /**
     * Whether leaving [date] blank counts as a miss: from the start date on, it
     * does. That's when the routine began, whenever it was added here, so its
     * blank days are days to fill in.
     */
    fun countsIfUnanswered(date: LocalDate): Boolean = date >= startDate

    /** [date] is on or after the day it was added here. A chore already overdue when added isn't late. */
    fun addedBy(date: LocalDate): Boolean = date >= (createdOn ?: startDate)

    fun activeOn(date: LocalDate): Boolean =
        !archived && date >= startDate && (endDate == null || date <= endDate)

    /** For a challenge: "day 12" of [challengeLength]; null outside it or for other routines. */
    fun challengeDay(date: LocalDate): Int? =
        if (endDate != null && date >= startDate && date <= endDate) {
            ChronoUnit.DAYS.between(startDate, date).toInt() + 1
        } else null

    val challengeLength: Int?
        get() = endDate?.let { ChronoUnit.DAYS.between(startDate, it).toInt() + 1 }

    /** A chore's next due date after being done on [date]. */
    fun afterInterval(date: LocalDate): LocalDate {
        val count = (every ?: 1).coerceAtLeast(1).toLong()
        return when (unit ?: IntervalUnit.DAY) {
            IntervalUnit.DAY -> date.plusDays(count)
            IntervalUnit.WEEK -> date.plusWeeks(count)
            IntervalUnit.MONTH -> date.plusMonths(count)
        }
    }
}

data class CheckIn(
    val routineId: Long,
    val date: LocalDate,
    val status: CheckInStatus,
    val note: String? = null
)
