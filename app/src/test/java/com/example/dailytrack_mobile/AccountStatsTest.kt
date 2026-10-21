package com.example.dailytrack_mobile

import com.example.dailytrack_mobile.domain.money.AccountSpend
import com.example.dailytrack_mobile.domain.money.AccountStatsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** The same cases as the server's tests/test_account_stats.py. */
class AccountStatsTest {
    private val today = LocalDate.of(2026, 10, 19)
    private fun at(account: String, month: Int, amount: Double) = AccountSpend(account, YearMonth.of(2026, month), amount)
    private val all = listOf(at("KOTAK", 7, 300.0), at("KOTAK", 9, 600.0), at("HDFC", 8, 200.0), at("HDFC", 9, 100.0))

    @Test
    fun `the latest month against the year before it`() {
        val stats = AccountStatsCalculator.of(all, all, today)
        assertEquals(YearMonth.of(2026, 9), stats.latest)
        assertFalse(stats.inProgress)
        assertEquals(listOf("KOTAK" to 900.0, "HDFC" to 300.0), stats.shown)
        val (kotak, hdfc) = stats.rows
        // KOTAK since July: 300 then 0, so 150 a month; September's 600 is 4x that.
        assertEquals("KOTAK", kotak.account)
        assertEquals(150.0, kotak.average!!, 0.01)
        assertEquals(2, kotak.months)
        assertEquals(600.0, kotak.latest, 0.01)
        assertEquals(300.0, kotak.change!!, 0.01)
        assertEquals(75.0, kotak.share, 0.01)
        assertEquals(listOf(0.0, 0.0, 0.0, 300.0, 0.0, 600.0), kotak.trend)
        assertEquals(200.0, hdfc.average!!, 0.01)
        assertEquals(-50.0, hdfc.change!!, 0.01)
    }

    @Test
    fun `this month so far is measured against the months before`() {
        val withToday = all + at("KOTAK", 10, 150.0)
        val stats = AccountStatsCalculator.of(withToday, withToday, today)
        val kotak = stats.rows.first { it.account == "KOTAK" }
        assertTrue(stats.inProgress)
        assertEquals(300.0, kotak.average!!, 0.01)
        assertEquals(-50.0, kotak.change!!, 0.01)
    }

    @Test
    fun `the date filters only pick the month`() {
        val august = all.filter { it.month == YearMonth.of(2026, 8) }
        val stats = AccountStatsCalculator.of(august, all, today)
        assertEquals(YearMonth.of(2026, 8), stats.latest)
        val (hdfc, kotak) = stats.rows
        assertEquals("HDFC", hdfc.account)
        assertNull(hdfc.average)
        assertEquals(100.0, hdfc.share, 0.01)
        assertEquals(300.0, kotak.average!!, 0.01)
        assertEquals(-100.0, kotak.change!!, 0.01)
    }

    @Test
    fun `nothing in range is empty`() {
        assertTrue(AccountStatsCalculator.of(emptyList(), all, today).rows.isEmpty())
    }
}
