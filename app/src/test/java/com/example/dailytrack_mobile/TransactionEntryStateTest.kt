package com.example.dailytrack_mobile

import com.example.dailytrack_mobile.presentation.components.transaction.EntryType
import com.example.dailytrack_mobile.presentation.components.transaction.TransactionEntryState
import com.example.dailytrack_mobile.presentation.components.transaction.joinAsSentence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionEntryStateTest {

    @Test
    fun `untouched entry is blank and skipped rather than incomplete`() {
        val entry = TransactionEntryState(account = "KOTAK")
        assertTrue(entry.isBlank)
        assertFalse(entry.isComplete)
    }

    @Test
    fun `missing fields are listed in form order`() {
        val entry = TransactionEntryState(note = "lunch")
        assertEquals(listOf("amount", "category", "account"), entry.missingFields)
        assertEquals("amount, category & account", entry.missingFields.joinAsSentence())
    }

    @Test
    fun `expression amounts count once evaluated`() {
        val entry = TransactionEntryState(amount = "120+40", category = "Food", account = "Cash")
        assertEquals(160.0, entry.evaluatedAmount!!, 0.0)
        assertTrue(entry.isComplete)
    }

    @Test
    fun `half-typed expression is not a valid amount`() {
        val entry = TransactionEntryState(amount = "120+", category = "Food", account = "Cash")
        assertEquals(listOf("amount"), entry.missingFields)
    }

    @Test
    fun `next entry keeps account date and type but not the details`() {
        val source = TransactionEntryState(
            type = EntryType.INCOME, category = "Salary", amount = "5000", note = "Sept",
            account = "HDFC", dateMillis = 1_700_000_000_000, excludeAnalytics = true
        )
        val next = source.nextFromThis()
        assertEquals(EntryType.INCOME, next.type)
        assertEquals("HDFC", next.account)
        assertEquals(source.dateMillis, next.dateMillis)
        assertTrue(next.isBlank)
        assertFalse(next.excludeAnalytics)
        assertNotEquals(source.id, next.id)
    }

    @Test
    fun `duplicate copies everything under a new id`() {
        val source = TransactionEntryState(category = "Food", amount = "99", note = "Tea", account = "Cash")
        val copy = source.duplicate()
        assertEquals(source.amount, copy.amount)
        assertEquals(source.note, copy.note)
        assertNotEquals(source.id, copy.id)
    }

    @Test
    fun `db types map both ways`() {
        EntryType.entries.forEach { assertEquals(it, EntryType.fromDb(it.dbValue)) }
        assertEquals(EntryType.INCOME, EntryType.fromDb("credit"))
        assertEquals(EntryType.EXPENSE, EntryType.fromDb(null))
    }

    @Test
    fun `api date round trips`() {
        val millis = TransactionEntryState.parseApiDate("2026-09-15")!!
        assertEquals("2026-09-15", TransactionEntryState.formatApiDate(millis))
    }
}
