package com.example.dailytrack_mobile.domain.routines

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class PeriodUnit { WEEK, MONTH }

/** How a times-per-week/month routine is doing in one period. */
data class PeriodProgress(val done: Int, val needed: Int, val unit: PeriodUnit, val answered: Int = 0) {
    val met: Boolean get() = done >= needed
}

/** A routine as it stands on one day's list. */
data class DayItem(
    val routine: Routine,
    val date: LocalDate,
    val status: CheckInStatus?,
    val note: String?,
    /**
     * Counts against the day when not done: fixed-day routines and chores that
     * are due. Times-per-week/month routines are optional on any single day.
     */
    val required: Boolean,
    val progress: PeriodProgress? = null,
    /** Chores: the day this one fell due. */
    val dueDate: LocalDate? = null
) {
    /** Still worth asking about in the nightly check-in. */
    val needsAnswer: Boolean
        get() = status == null && (required || progress?.met == false)
}

/**
 * How a day went: [done] out of [total], where total is everything that counted
 * ([done] + [missed] + [unanswered]). [skipped] ones are excused and not in the
 * total. [unanswered] are still open today, and count as missed once it's over.
 */
data class DayStats(
    val date: LocalDate,
    val done: Int,
    val total: Int,
    val missed: Int = 0,
    val skipped: Int = 0,
    val unanswered: Int = 0
) {
    val fraction: Float? get() = if (total > 0) done.toFloat() / total else null
}

/** Successes out of chances; partial credit comes from times-per-period targets. */
data class Score(val successes: Double, val chances: Double) {
    val fraction: Double? get() = if (chances > 0) successes / chances else null
}

enum class StreakUnit { DAY, WEEK, MONTH, TIME }

data class Streak(val current: Int, val best: Int, val unit: StreakUnit)

/** A chore not yet due, or a routine that hasn't started. */
data class UpcomingItem(val routine: Routine, val date: LocalDate)

/**
 * Everything derived from routines and their check-ins: each day's list,
 * streaks and the consistency score. Pure and synchronous, so the page, the
 * nightly check-in notification and the tests all run the same rules.
 *
 *  - Fixed days (every day, or chosen weekdays): each due day is one chance.
 *    Done counts, Missed counts against, Skipped is excused. A past day left
 *    unanswered counts as missed; today's waits until it's answered.
 *  - Times per week / month: each period is one chance, judged when it ends,
 *    or as soon as the target is hit. Skipped days lower the target only once
 *    too few days are left to reach it. Missing partway earns partial credit.
 *  - Chores (every N days/weeks/months): due from the due date until done, and
 *    on time if done by then. A Skip on the deadline moves it a day. The next
 *    one falls due N after it was done.
 *  - Days before a routine was added count only once filled in: a blank one
 *    isn't a miss, and a week or chore deadline that passed before then isn't
 *    a failure.
 *  - Archived routines are left out of everything.
 */
class RoutineEngine(routines: List<Routine>, checkIns: List<CheckIn>) {

    val routines: List<Routine> = routines
        .filter { !it.archived }
        .sortedWith(compareBy({ it.sortOrder }, { it.id }))

    private val byRoutine: Map<Long, Map<LocalDate, CheckIn>> = checkIns
        .groupBy { it.routineId }
        .mapValues { (_, list) -> list.associateBy { it.date } }

    private val cycleCache = HashMap<Long, List<Cycle>>()

    fun checkIn(routineId: Long, date: LocalDate): CheckIn? = byRoutine[routineId]?.get(date)

    // ── Day lists ────────────────────────────────────────────────────────────

    fun dayItems(date: LocalDate): List<DayItem> = routines.mapNotNull { itemFor(it, date) }

    /** Done out of what counted: every due item not skipped, plus optional ones that got done. */
    fun dayStats(date: LocalDate): DayStats = statsOf(date, dayItems(date))

    /** Monday to Sunday of [today]'s week; days still ahead are null. */
    fun week(today: LocalDate): List<DayStats?> {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (0L..6L).map { offset ->
            val day = monday.plusDays(offset)
            if (day > today) null else dayStats(day)
        }
    }

    fun upcoming(today: LocalDate): List<UpcomingItem> = routines.mapNotNull { routine ->
        when {
            routine.schedule == RoutineSchedule.INTERVAL -> cycles(routine).lastOrNull()
                ?.takeIf { it.completed == null && it.due > today }
                ?.let { UpcomingItem(routine, it.due) }
            routine.startDate > today -> UpcomingItem(routine, routine.startDate)
            else -> null
        }
    }.sortedBy { it.date }

    // ── Score ────────────────────────────────────────────────────────────────

    fun consistency(today: LocalDate, days: Int = 30): Score =
        score(today.minusDays(days - 1L), today, today)

    /** The [days] before the last [days]: what [consistency] is compared with. */
    fun previousConsistency(today: LocalDate, days: Int = 30): Score =
        score(today.minusDays(2L * days - 1), today.minusDays(days.toLong()), today)

    fun routineConsistency(routine: Routine, today: LocalDate, days: Int = 30): Score =
        score(today.minusDays(days - 1L), today, today, listOf(routine))

    /** Chances judged between [from] and [to] (and not after [today]). */
    fun score(
        from: LocalDate,
        to: LocalDate,
        today: LocalDate,
        only: List<Routine> = routines
    ): Score {
        var successes = 0.0
        var chances = 0.0
        val last = minOf(to, today)
        for (routine in only) {
            when (routine.schedule) {
                RoutineSchedule.DAILY, RoutineSchedule.DAYS -> {
                    var day = maxOf(from, routine.startDate)
                    val end = minOf(last, routine.endDate ?: last)
                    while (day <= end) {
                        if (dueOnFixedDay(routine, day)) {
                            when (checkIn(routine.id, day)?.status) {
                                CheckInStatus.DONE -> { successes++; chances++ }
                                CheckInStatus.MISSED -> chances++
                                CheckInStatus.SKIPPED -> Unit
                                null -> if (day < today && routine.countsIfUnanswered(day)) chances++
                            }
                        }
                        day = day.plusDays(1)
                    }
                }
                RoutineSchedule.WEEKLY, RoutineSchedule.MONTHLY -> {
                    var period = periodOf(routine, maxOf(from, routine.startDate))
                    while (period.start <= last) {
                        val progress = progress(routine, period)
                        if (progress.needed > 0 && !beforeItWasAdded(routine, period, progress)) {
                            val ended = period.endInclusive < today
                            if (ended && period.endInclusive in from..last) {
                                chances += progress.needed
                                successes += minOf(progress.done, progress.needed)
                            } else if (!ended && progress.met && today in from..last) {
                                chances += progress.needed
                                successes += progress.needed
                            }
                        }
                        period = periodOf(routine, period.endInclusive.plusDays(1))
                    }
                }
                RoutineSchedule.INTERVAL -> for (cycle in cycles(routine)) {
                    val judged = judge(routine, cycle, today) ?: continue
                    if (judged.first in from..last) {
                        chances++
                        if (judged.second) successes++
                    }
                }
            }
        }
        return Score(successes, chances)
    }

    // ── Streaks ──────────────────────────────────────────────────────────────

    fun streak(routine: Routine, today: LocalDate): Streak {
        var run = 0
        var best = 0
        fun hit() {
            run++
            best = maxOf(best, run)
        }
        val last = minOf(today, routine.endDate ?: today)
        return when (routine.schedule) {
            RoutineSchedule.DAILY, RoutineSchedule.DAYS -> {
                var day = routine.startDate
                while (day <= last) {
                    if (dueOnFixedDay(routine, day)) {
                        when (checkIn(routine.id, day)?.status) {
                            CheckInStatus.DONE -> hit()
                            CheckInStatus.SKIPPED -> Unit
                            CheckInStatus.MISSED -> run = 0
                            null -> if (day < today && routine.countsIfUnanswered(day)) run = 0
                        }
                    }
                    day = day.plusDays(1)
                }
                Streak(run, best, StreakUnit.DAY)
            }
            RoutineSchedule.WEEKLY, RoutineSchedule.MONTHLY -> {
                var period = periodOf(routine, routine.startDate)
                while (period.start <= last) {
                    val progress = progress(routine, period)
                    if (progress.needed > 0 && !beforeItWasAdded(routine, period, progress)) {
                        if (progress.met) hit() else if (period.endInclusive < today) run = 0
                    }
                    period = periodOf(routine, period.endInclusive.plusDays(1))
                }
                Streak(run, best, if (routine.schedule == RoutineSchedule.WEEKLY) StreakUnit.WEEK else StreakUnit.MONTH)
            }
            RoutineSchedule.INTERVAL -> {
                for (cycle in cycles(routine)) {
                    val judged = judge(routine, cycle, today) ?: continue
                    if (judged.second) hit() else run = 0
                }
                Streak(run, best, StreakUnit.TIME)
            }
        }
    }

    /**
     * Days where everything due was done (skips excused). Days with nothing due
     * don't count either way, and today only counts once it's all done; an
     * explicit miss today breaks the streak straight away.
     */
    fun perfectDays(today: LocalDate): Streak {
        val first = routines.minOfOrNull { it.startDate } ?: return Streak(0, 0, StreakUnit.DAY)
        var run = 0
        var best = 0
        var day = first
        while (day <= today) {
            val counted = requiredItems(day).filter { counts(it) }
            when {
                counted.isEmpty() -> Unit
                counted.all { it.status == CheckInStatus.DONE } -> {
                    run++
                    best = maxOf(best, run)
                }
                day == today && counted.none { it.status == CheckInStatus.MISSED } -> Unit
                else -> run = 0
            }
            day = day.plusDays(1)
        }
        return Streak(run, best, StreakUnit.DAY)
    }

    /** The routine as it stood on [date]: null when it wasn't due or active. */
    fun itemOn(routine: Routine, date: LocalDate): DayItem? = itemFor(routine, date)

    /** Everything judged since the routine started. */
    fun allTime(routine: Routine, today: LocalDate): Score =
        score(routine.startDate, today, today, listOf(routine))

    /** Every answer given for the routine, newest first. */
    fun answersFor(routine: Routine): List<CheckIn> =
        byRoutine[routine.id].orEmpty().values.sortedByDescending { it.date }

    fun lastDone(routine: Routine): LocalDate? =
        byRoutine[routine.id].orEmpty().values.filter { it.status == CheckInStatus.DONE }.maxOfOrNull { it.date }

    /** A chore's open due date (past, if it's overdue); null for other routines or once finished. */
    fun nextDue(routine: Routine): LocalDate? =
        if (routine.schedule == RoutineSchedule.INTERVAL) cycles(routine).lastOrNull()?.takeIf { it.completed == null }?.due
        else null

    /**
     * Due days before [today] with no answer at all, oldest first: what "mark
     * them all done" fills in. Only for every-day and chosen-day routines; the
     * others don't have a fixed list of days.
     */
    fun unansweredPastDays(routine: Routine, today: LocalDate): List<LocalDate> {
        if (routine.schedule != RoutineSchedule.DAILY && routine.schedule != RoutineSchedule.DAYS) return emptyList()
        val last = minOf(today.minusDays(1), routine.endDate ?: today)
        val days = ArrayList<LocalDate>()
        var day = routine.startDate
        while (day <= last) {
            if (dueOnFixedDay(routine, day) && checkIn(routine.id, day) == null) days += day
            day = day.plusDays(1)
        }
        return days
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private data class Cycle(val due: LocalDate, val deadline: LocalDate, val completed: LocalDate?)

    private fun itemFor(routine: Routine, date: LocalDate): DayItem? {
        val checkIn = checkIn(routine.id, date)
        return when (routine.schedule) {
            RoutineSchedule.DAILY, RoutineSchedule.DAYS ->
                if (dueOnFixedDay(routine, date)) {
                    DayItem(routine, date, checkIn?.status, checkIn?.note, required = true)
                } else null
            RoutineSchedule.WEEKLY, RoutineSchedule.MONTHLY ->
                if (routine.activeOn(date)) {
                    DayItem(
                        routine, date, checkIn?.status, checkIn?.note,
                        required = false,
                        progress = progress(routine, periodOf(routine, date))
                    )
                } else null
            RoutineSchedule.INTERVAL -> choreItem(routine, date, checkIn)
        }
    }

    /** A chore is on the list from its due date until the day it's done, or on the day it's done early. */
    private fun choreItem(routine: Routine, date: LocalDate, checkIn: CheckIn?): DayItem? {
        val cycle = cycles(routine).firstOrNull { cycle ->
            val completed = cycle.completed
            (cycle.due <= date && (completed == null || completed >= date)) || completed == date
        } ?: return null
        return DayItem(
            routine, date, checkIn?.status, checkIn?.note,
            required = cycle.due <= date,
            dueDate = cycle.due
        )
    }

    private fun requiredItems(date: LocalDate): List<DayItem> = routines.mapNotNull { routine ->
        when (routine.schedule) {
            RoutineSchedule.WEEKLY, RoutineSchedule.MONTHLY -> null
            else -> itemFor(routine, date)?.takeIf { it.required }
        }
    }

    private fun statsOf(date: LocalDate, items: List<DayItem>): DayStats {
        val done = items.count { it.status == CheckInStatus.DONE }
        val total = items.count { it.required && counts(it) } +
            items.count { !it.required && it.status == CheckInStatus.DONE }
        return DayStats(
            date = date,
            done = done,
            total = total,
            missed = items.count { it.required && it.status == CheckInStatus.MISSED },
            skipped = items.count { it.required && it.status == CheckInStatus.SKIPPED },
            unanswered = items.count { it.required && it.status == null && counts(it) }
        )
    }

    private fun dueOnFixedDay(routine: Routine, date: LocalDate): Boolean =
        routine.activeOn(date) && when (routine.schedule) {
            RoutineSchedule.DAILY -> true
            RoutineSchedule.DAYS -> (routine.days ?: 0) and date.dayOfWeek.bit() != 0
            else -> false
        }

    private fun periodOf(routine: Routine, date: LocalDate): ClosedRange<LocalDate> =
        if (routine.schedule == RoutineSchedule.WEEKLY) {
            val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            monday..monday.plusDays(6)
        } else {
            val first = date.withDayOfMonth(1)
            first..first.plusMonths(1).minusDays(1)
        }

    private fun progress(routine: Routine, period: ClosedRange<LocalDate>): PeriodProgress {
        var active = 0
        var skipped = 0
        var done = 0
        var answered = 0
        var day = period.start
        while (day <= period.endInclusive) {
            if (routine.activeOn(day)) {
                active++
                val status = checkIn(routine.id, day)?.status
                if (status != null) answered++
                when (status) {
                    CheckInStatus.DONE -> done++
                    CheckInStatus.SKIPPED -> skipped++
                    else -> Unit
                }
            }
            day = day.plusDays(1)
        }
        val needed = minOf(routine.target ?: 1, active - skipped).coerceAtLeast(0)
        val unit = if (routine.schedule == RoutineSchedule.WEEKLY) PeriodUnit.WEEK else PeriodUnit.MONTH
        return PeriodProgress(done, needed, unit, answered)
    }

    /**
     * A chore's history as due → deadline → done, oldest first. Each done
     * check-in completes the open one (early completions included), and the
     * next falls due one interval later. The last entry stays open until done.
     */
    private fun cycles(routine: Routine): List<Cycle> = cycleCache.getOrPut(routine.id) {
        val doneDates = byRoutine[routine.id].orEmpty().values
            .filter { it.status == CheckInStatus.DONE }
            .map { it.date }
            .sorted()
        val cycles = ArrayList<Cycle>()
        var due = routine.startDate
        var next = 0
        while (cycles.size < MAX_CYCLES) {
            if (routine.endDate != null && due > routine.endDate) break
            val completed = doneDates.getOrNull(next)
            cycles += Cycle(due, deadlineFor(routine, due), completed)
            if (completed == null) break
            next++
            due = routine.afterInterval(completed)
        }
        cycles
    }

    /** The due date, pushed a day for each Skip answered on the deadline itself. */
    private fun deadlineFor(routine: Routine, due: LocalDate): LocalDate {
        var deadline = due
        var guard = 0
        while (checkIn(routine.id, deadline)?.status == CheckInStatus.SKIPPED && guard++ < 366) {
            deadline = deadline.plusDays(1)
        }
        return deadline
    }

    /**
     * (when it was decided, on time?), or null while it can still be done in
     * time, or when its deadline passed before the routine was even added.
     */
    private fun judge(routine: Routine, cycle: Cycle, today: LocalDate): Pair<LocalDate, Boolean>? {
        val completed = cycle.completed
        val judged = when {
            completed != null && completed <= cycle.deadline -> completed to true
            completed != null -> cycle.deadline to false
            today > cycle.deadline -> cycle.deadline to false
            else -> null
        }
        return judged?.takeIf { (date, onTime) -> onTime || routine.countsIfUnanswered(date) }
    }

    /** A due item that counts for its day: answered (skips excused), or blank but held against it. */
    private fun counts(item: DayItem): Boolean = when (item.status) {
        CheckInStatus.SKIPPED -> false
        null -> item.routine.countsIfUnanswered(item.date)
        else -> true
    }

    /** A period that ended before the routine was added and was never filled in. */
    private fun beforeItWasAdded(routine: Routine, period: ClosedRange<LocalDate>, progress: PeriodProgress): Boolean {
        val added = routine.createdOn ?: return false
        return period.endInclusive < added && progress.answered == 0
    }

    private companion object {
        const val MAX_CYCLES = 5_000
    }
}
