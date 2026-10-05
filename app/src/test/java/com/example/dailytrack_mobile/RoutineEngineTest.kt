package com.example.dailytrack_mobile

import com.example.dailytrack_mobile.domain.routines.CheckIn
import com.example.dailytrack_mobile.domain.routines.CheckInStatus
import com.example.dailytrack_mobile.domain.routines.CheckInStatus.DONE
import com.example.dailytrack_mobile.domain.routines.CheckInStatus.MISSED
import com.example.dailytrack_mobile.domain.routines.CheckInStatus.SKIPPED
import com.example.dailytrack_mobile.domain.routines.IntervalUnit
import com.example.dailytrack_mobile.domain.routines.Routine
import com.example.dailytrack_mobile.domain.routines.RoutineEngine
import com.example.dailytrack_mobile.domain.routines.RoutineSchedule
import com.example.dailytrack_mobile.domain.routines.StreakUnit
import com.example.dailytrack_mobile.domain.routines.bit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** The Routines rules, on a fixed calendar: MON is Monday 28 Sep 2026. */
class RoutineEngineTest {

    private val mon: LocalDate = LocalDate.of(2026, 9, 28)
    private fun day(offset: Long): LocalDate = mon.plusDays(offset)

    private fun daily(id: Long, start: LocalDate = mon, end: LocalDate? = null) =
        Routine(id = id, name = "R$id", startDate = start, endDate = end)

    private fun weekly(id: Long, target: Int, start: LocalDate = mon) =
        Routine(id = id, name = "W$id", schedule = RoutineSchedule.WEEKLY, target = target, startDate = start)

    private fun chore(id: Long, firstDue: LocalDate, every: Int = 7, unit: IntervalUnit = IntervalUnit.DAY) =
        Routine(id = id, name = "C$id", schedule = RoutineSchedule.INTERVAL, every = every, unit = unit, startDate = firstDue)

    private fun at(routine: Routine, date: LocalDate, status: CheckInStatus) = CheckIn(routine.id, date, status)

    private fun engine(routines: List<Routine>, vararg checkIns: CheckIn) = RoutineEngine(routines, checkIns.toList())

    private fun fraction(successes: Double, chances: Double) = successes / chances

    // ── What's on a day's list ──────────────────────────────────────────────

    @Test
    fun `fixed days are due on their days and inside a challenge`() {
        val challenge = daily(1, start = mon, end = day(2))
        val mwf = Routine(
            id = 2, name = "Gym", schedule = RoutineSchedule.DAYS,
            days = DayOfWeek.MONDAY.bit() or DayOfWeek.WEDNESDAY.bit() or DayOfWeek.FRIDAY.bit(),
            startDate = mon
        )
        val e = engine(listOf(challenge, mwf))

        assertEquals(emptyList<Long>(), e.dayItems(day(-1)).map { it.routine.id })
        assertEquals(listOf(1L, 2L), e.dayItems(mon).map { it.routine.id })
        assertEquals(listOf(1L), e.dayItems(day(1)).map { it.routine.id })
        assertEquals(listOf(1L, 2L), e.dayItems(day(2)).map { it.routine.id })
        assertEquals(emptyList<Long>(), e.dayItems(day(3)).map { it.routine.id })
        assertEquals(listOf(2L), e.dayItems(day(4)).map { it.routine.id })

        assertEquals(2, challenge.challengeDay(day(1)))
        assertEquals(3, challenge.challengeLength)
    }

    @Test
    fun `a day counts due items and optional ones only when done`() {
        val a = daily(1)
        val b = daily(2)
        val gym = weekly(3, target = 3)
        val filter = chore(4, firstDue = day(2))
        val today = day(2)
        val e = engine(
            listOf(a, b, gym, filter),
            at(a, today, DONE), at(b, today, SKIPPED), at(gym, today, DONE)
        )
        val stats = e.dayStats(today)
        // a done, gym done as a bonus; b excused; the due chore still counts.
        assertEquals(2, stats.done)
        assertEquals(3, stats.total)
        // The mix behind it: nothing missed yet, one skip, the chore still blank.
        assertEquals(0, stats.missed)
        assertEquals(1, stats.skipped)
        assertEquals(1, stats.unanswered)
    }

    @Test
    fun `a day's mix separates answered misses from blanks`() {
        val a = daily(1)
        val b = daily(2)
        val c = daily(3)
        val e = engine(listOf(a, b, c), at(a, mon, DONE), at(b, mon, MISSED))
        val stats = e.dayStats(mon)
        assertEquals(1, stats.done)
        assertEquals(1, stats.missed)
        assertEquals(1, stats.unanswered)
        assertEquals(0, stats.skipped)
        assertEquals(3, stats.total)
    }

    @Test
    fun `the week runs Monday to Sunday with days ahead left empty`() {
        val week = engine(listOf(daily(1))).week(day(2))
        assertEquals(7, week.size)
        assertEquals(listOf(mon, day(1), day(2)), week.take(3).map { it!!.date })
        assertTrue(week.drop(3).all { it == null })
    }

    @Test
    fun `only unanswered things still worth asking are asked tonight`() {
        val read = daily(1)
        val answered = daily(2)
        val metGym = weekly(3, target = 1)
        val openGym = weekly(4, target = 2)
        val filter = chore(5, firstDue = day(1))
        val today = day(1)
        val e = engine(
            listOf(read, answered, metGym, openGym, filter),
            at(answered, today, MISSED), at(metGym, mon, DONE)
        )
        val asked = e.dayItems(today).filter { it.needsAnswer }.map { it.routine.id }
        assertEquals(listOf(1L, 4L, 5L), asked)
    }

    @Test
    fun `archived routines are left out of everything`() {
        val gone = daily(1).copy(archived = true)
        val e = engine(listOf(gone), at(gone, mon, DONE))
        assertTrue(e.dayItems(mon).isEmpty())
        assertNull(e.consistency(day(3)).fraction)
        assertEquals(0, e.perfectDays(day(3)).best)
    }

    // ── The score ───────────────────────────────────────────────────────────

    @Test
    fun `a past day left unanswered counts as missed while today waits`() {
        val read = daily(1)
        val today = day(2)
        val e = engine(listOf(read), at(read, mon, DONE))
        // Monday done, Tuesday never answered, today not yet answered.
        assertEquals(fraction(1.0, 2.0), e.consistency(today).fraction!!, 1e-9)

        val answered = engine(listOf(read), at(read, mon, DONE), at(read, today, MISSED))
        assertEquals(fraction(1.0, 3.0), answered.consistency(today).fraction!!, 1e-9)
    }

    @Test
    fun `skips are excused`() {
        val read = daily(1)
        val e = engine(listOf(read), at(read, mon, DONE), at(read, day(1), SKIPPED), at(read, day(2), MISSED))
        assertEquals(fraction(1.0, 2.0), e.consistency(day(3)).fraction!!, 1e-9)
    }

    @Test
    fun `consistency is compared with the same stretch just before it`() {
        val read = daily(1)
        val e = engine(
            listOf(read),
            at(read, mon, DONE),
            at(read, day(1), DONE), at(read, day(2), DONE), at(read, day(3), MISSED), at(read, day(4), DONE), at(read, day(5), DONE),
            at(read, day(6), DONE), at(read, day(7), DONE), at(read, day(8), DONE), at(read, day(9), DONE)
        )
        // Days 6 to 10, with today (day 10) still open: 4 of 4.
        assertEquals(1.0, e.consistency(day(10), days = 5).fraction!!, 1e-9)
        // Days 1 to 5: 4 of 5. Monday is in neither.
        assertEquals(0.8, e.previousConsistency(day(10), days = 5).fraction!!, 1e-9)
    }

    @Test
    fun `weekly targets are judged when the week ends, with partial credit`() {
        val gym = weekly(1, target = 3)
        val firstWeek = engine(listOf(gym), at(gym, mon, DONE), at(gym, day(2), DONE))
        // Mid-week nothing is judged yet.
        assertNull(firstWeek.consistency(day(4)).fraction)
        // Once the week is over: 2 of 3.
        assertEquals(fraction(2.0, 3.0), firstWeek.consistency(day(7)).fraction!!, 1e-9)

        // Hitting the next week's target early counts straight away.
        val secondWeek = engine(
            listOf(gym),
            at(gym, mon, DONE), at(gym, day(2), DONE),
            at(gym, day(7), DONE), at(gym, day(8), DONE), at(gym, day(9), DONE)
        )
        assertEquals(fraction(5.0, 6.0), secondWeek.consistency(day(9)).fraction!!, 1e-9)
    }

    @Test
    fun `skips lower a weekly target only once too few days are left`() {
        // Starts on a Thursday: 4 days left, so a target of 4 needs all of them...
        val late = weekly(1, target = 4, start = day(3))
        val e = engine(
            listOf(late),
            at(late, day(3), DONE), at(late, day(4), SKIPPED), at(late, day(5), DONE), at(late, day(6), DONE)
        )
        // ...and skipping Friday brings it down to 3, which were all done.
        assertEquals(1.0, e.consistency(day(7)).fraction!!, 1e-9)

        // A full week with two skips still needs all 4.
        val full = weekly(2, target = 4)
        val twoSkips = engine(
            listOf(full),
            at(full, mon, SKIPPED), at(full, day(1), SKIPPED), at(full, day(2), DONE), at(full, day(3), DONE)
        )
        assertEquals(fraction(2.0, 4.0), twoSkips.consistency(day(7)).fraction!!, 1e-9)
    }

    @Test
    fun `monthly targets count calendar months`() {
        val calls = Routine(
            id = 1, name = "Call home", schedule = RoutineSchedule.MONTHLY, target = 2,
            startDate = LocalDate.of(2026, 9, 1)
        )
        val e = engine(listOf(calls), at(calls, LocalDate.of(2026, 9, 10), DONE))
        val today = LocalDate.of(2026, 10, 2)
        assertEquals(0.5, e.consistency(today).fraction!!, 1e-9)
        assertEquals(0, e.streak(calls, today).current)
        assertEquals(StreakUnit.MONTH, e.streak(calls, today).unit)
    }

    // ── Chores ──────────────────────────────────────────────────────────────

    @Test
    fun `a chore is due from its date until done and judged against it`() {
        val filter = chore(1, firstDue = day(2))
        val before = engine(listOf(filter))
        assertTrue(before.dayItems(day(1)).isEmpty())
        assertEquals(listOf(day(2)), before.upcoming(day(1)).map { it.date })
        val overdue = before.dayItems(day(4)).single()
        assertTrue(overdue.required)
        assertEquals(day(2), overdue.dueDate)

        // Done two days late: that one missed, and the next is due a week after it was done.
        val late = engine(listOf(filter), at(filter, day(4), DONE))
        assertTrue(late.dayItems(day(5)).isEmpty())
        assertEquals(listOf(day(11)), late.upcoming(day(5)).map { it.date })
        assertEquals(0, late.streak(filter, day(5)).best)

        // The next one on time.
        val onTime = engine(listOf(filter), at(filter, day(4), DONE), at(filter, day(11), DONE))
        assertEquals(1, onTime.streak(filter, day(12)).current)
        assertEquals(StreakUnit.TIME, onTime.streak(filter, day(12)).unit)
        assertEquals(fraction(1.0, 2.0), onTime.consistency(day(12)).fraction!!, 1e-9)
    }

    @Test
    fun `a skip on the deadline moves it by a day, an earlier one doesn't`() {
        val filter = chore(1, firstDue = day(2))
        val snoozed = engine(listOf(filter), at(filter, day(2), SKIPPED), at(filter, day(3), DONE))
        assertEquals(1, snoozed.streak(filter, day(4)).current)

        val tooEarly = engine(listOf(filter), at(filter, day(1), SKIPPED), at(filter, day(3), DONE))
        assertEquals(0, tooEarly.streak(filter, day(4)).current)
    }

    @Test
    fun `doing a chore early counts and restarts the clock`() {
        val sheets = chore(1, firstDue = LocalDate.of(2026, 10, 15), every = 1, unit = IntervalUnit.MONTH)
        val early = LocalDate.of(2026, 10, 10)
        val e = engine(listOf(sheets), at(sheets, early, DONE))

        val item = e.dayItems(early).single()
        assertFalse(item.required)
        assertEquals(1, e.dayStats(early).done)
        assertEquals(1, e.dayStats(early).total)
        assertEquals(listOf(LocalDate.of(2026, 11, 10)), e.upcoming(early.plusDays(1)).map { it.date })
        assertEquals(1.0, e.consistency(early.plusDays(1)).fraction!!, 1e-9)
    }

    @Test
    fun `routines that haven't started are coming up`() {
        val later = daily(1, start = day(5))
        assertEquals(listOf(day(5)), engine(listOf(later)).upcoming(mon).map { it.date })
    }

    // ── Streaks ─────────────────────────────────────────────────────────────

    @Test
    fun `skips and today's pending answer don't break a streak, a miss does`() {
        val read = daily(1)
        val checkIns = arrayOf(at(read, mon, DONE), at(read, day(1), SKIPPED), at(read, day(2), DONE))
        val pending = engine(listOf(read), *checkIns).streak(read, day(3))
        assertEquals(2, pending.current)
        assertEquals(2, pending.best)

        val missed = engine(listOf(read), *checkIns, at(read, day(3), MISSED)).streak(read, day(3))
        assertEquals(0, missed.current)
        assertEquals(2, missed.best)
    }

    @Test
    fun `a weekly streak counts weeks that hit the target`() {
        val gym = weekly(1, target = 1)
        val e = engine(listOf(gym), at(gym, mon, DONE), at(gym, day(8), DONE))
        // Two good weeks; the third week hasn't hit it yet but isn't over.
        assertEquals(2, e.streak(gym, day(15)).current)
        // Once the third week ends without it, the streak is gone.
        assertEquals(0, e.streak(gym, day(21)).current)
        assertEquals(2, e.streak(gym, day(21)).best)
    }

    @Test
    fun `perfect days need everything due done, with skips excused`() {
        val a = daily(1)
        val b = daily(2)
        val base = arrayOf(
            at(a, mon, DONE), at(b, mon, DONE),
            at(a, day(1), DONE), at(b, day(1), SKIPPED),
            at(a, day(2), DONE), at(b, day(2), DONE),
            at(a, day(3), DONE)
        )
        // Today (day 3) is still in progress, so the three before it stand.
        val inProgress = engine(listOf(a, b), *base).perfectDays(day(3))
        assertEquals(3, inProgress.current)
        assertEquals(3, inProgress.best)

        val broken = engine(listOf(a, b), *base, at(b, day(3), MISSED)).perfectDays(day(3))
        assertEquals(0, broken.current)
        assertEquals(3, broken.best)
    }

    // ── Days before a routine was added ─────────────────────────────────────

    @Test
    fun `blank days from the start date count however late it was added`() {
        // Started Monday, but only added on Thursday (day 3): Mon–Wed are days to fill in.
        val sugar = daily(1).copy(createdOn = day(3))
        val blank = engine(listOf(sugar))
        assertEquals(0.0, blank.consistency(day(4)).fraction!!, 1e-9)
        assertEquals(1, blank.dayStats(mon).total)
        assertEquals(1, blank.dayStats(mon).unanswered)

        // A gap left in those days still counts against it.
        val gap = engine(listOf(sugar), at(sugar, mon, DONE), at(sugar, day(1), DONE), at(sugar, day(3), DONE))
        assertEquals(0.75, gap.consistency(day(4)).fraction!!, 1e-9)

        // Filled in, they count like any other.
        val filled = engine(listOf(sugar), *(0L..3L).map { at(sugar, day(it), DONE) }.toTypedArray())
        assertEquals(1.0, filled.consistency(day(4)).fraction!!, 1e-9)
        assertEquals(4, filled.streak(sugar, day(4)).current)
        assertEquals(4, filled.perfectDays(day(4)).current)
    }

    @Test
    fun `weeks that ended before a routine was added count only once filled in`() {
        val gym = weekly(1, target = 3).copy(createdOn = day(8))
        val e = engine(listOf(gym), at(gym, day(9), DONE), at(gym, day(10), DONE))
        // Week one ended before it was added and is blank; week two gets 2 of 3.
        assertEquals(fraction(2.0, 3.0), e.consistency(day(14)).fraction!!, 1e-9)

        val backfilled = engine(listOf(gym), at(gym, day(1), DONE), at(gym, day(9), DONE), at(gym, day(10), DONE))
        assertEquals(fraction(3.0, 6.0), backfilled.consistency(day(14)).fraction!!, 1e-9)
    }

    @Test
    fun `a chore already overdue when added isn't a failure`() {
        val filter = chore(1, firstDue = mon).copy(createdOn = day(5))
        val e = engine(listOf(filter))
        assertNull(e.consistency(day(6)).fraction)
        assertTrue(e.dayItems(day(6)).single().required)
    }

    @Test
    fun `unanswered past days are the due days before today with no answer`() {
        val read = daily(1)
        val e = engine(listOf(read), at(read, day(1), DONE))
        assertEquals(listOf(mon, day(2)), e.unansweredPastDays(read, day(3)))

        val mondays = Routine(id = 2, name = "Review", schedule = RoutineSchedule.DAYS, days = DayOfWeek.MONDAY.bit(), startDate = mon)
        assertEquals(listOf(mon, day(7)), engine(listOf(mondays)).unansweredPastDays(mondays, day(10)))

        // Only fixed-day routines can be filled in wholesale.
        val gym = weekly(3, target = 2)
        assertTrue(engine(listOf(gym)).unansweredPastDays(gym, day(10)).isEmpty())
    }

    // ── One routine's page ──────────────────────────────────────────────────

    @Test
    fun `a routine's own page has its days, all-time score and answers`() {
        val read = daily(1)
        val other = daily(2)
        val e = engine(
            listOf(read, other),
            at(read, mon, DONE), CheckIn(read.id, day(1), SKIPPED, "Unwell"), at(read, day(2), MISSED),
            at(other, mon, DONE)
        )
        assertEquals(SKIPPED, e.itemOn(read, day(1))!!.status)
        assertNull(e.itemOn(read, day(-1)))
        // Mon done, Tue excused, Wed missed, Thu (today) still open.
        assertEquals(fraction(1.0, 2.0), e.allTime(read, day(3)).fraction!!, 1e-9)
        assertEquals(listOf(day(2), day(1), mon), e.answersFor(read).map { it.date })
        assertEquals("Unwell", e.answersFor(read).first { it.status == SKIPPED }.note)
        assertEquals(mon, e.lastDone(read))
    }

    @Test
    fun `a chore's next due date is the open one, even if overdue`() {
        val filter = chore(1, firstDue = day(2))
        assertEquals(day(2), engine(listOf(filter)).nextDue(filter))
        assertEquals(day(11), engine(listOf(filter), at(filter, day(4), DONE)).nextDue(filter))
        assertNull(engine(listOf(daily(2))).nextDue(daily(2)))
    }

    @Test
    fun `days with nothing due don't count either way`() {
        val mondays = Routine(id = 1, name = "Weekly review", schedule = RoutineSchedule.DAYS, days = DayOfWeek.MONDAY.bit(), startDate = mon)
        val e = engine(listOf(mondays), at(mondays, mon, DONE), at(mondays, day(7), DONE))
        assertEquals(2, e.perfectDays(day(10)).current)
    }
}
