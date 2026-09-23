package com.example.dailytrack_mobile.presentation.components.transaction

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.dailytrack_mobile.presentation.util.AmountExpression
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/** Maps to the DB `type` column. */
enum class EntryType(val label: String, val dbValue: String) {
    EXPENSE("Expense", "Debit"),
    INCOME("Income", "Credit"),
    SAVINGS("Savings", "Savings"),
    INVESTMENT("Invest", "Investment");

    companion object {
        fun fromDb(raw: String?): EntryType = when {
            raw == null -> EXPENSE
            raw.equals("Credit", ignoreCase = true) || raw.equals("Income", ignoreCase = true) -> INCOME
            raw.startsWith("Saving", ignoreCase = true) -> SAVINGS
            raw.startsWith("Invest", ignoreCase = true) -> INVESTMENT
            else -> EXPENSE
        }
    }
}

/**
 * One transaction being typed or edited. Fields are snapshot state so a card
 * can be edited in place and every summary of it stays current.
 *
 * Shared by Add Money (many entries), Edit Transaction (one) and Bulk Edit.
 */
class TransactionEntryState(
    val id: Long = nextLocalId(),
    type: EntryType = EntryType.EXPENSE,
    category: String = "",
    amount: String = "",
    note: String = "",
    account: String? = null,
    dateMillis: Long = System.currentTimeMillis(),
    excludeAnalytics: Boolean = false
) {
    var type by mutableStateOf(type)
    var category by mutableStateOf(category)
    var amount by mutableStateOf(amount)
    var note by mutableStateOf(note)
    var account by mutableStateOf(account)
    var dateMillis by mutableStateOf(dateMillis)
    var excludeAnalytics by mutableStateOf(excludeAnalytics)

    /** The amount the entry will save as, with any arithmetic worked out. */
    val evaluatedAmount: Double?
        get() = AmountExpression.evaluate(amount)

    /** Nothing typed yet — such an entry is skipped rather than blocking a save. */
    val isBlank: Boolean
        get() = amount.isBlank() && category.isBlank() && note.isBlank()

    /** What still has to be filled in, in the order the form asks for it. */
    val missingFields: List<String>
        get() = buildList {
            if ((evaluatedAmount ?: 0.0) <= 0.0) add("amount")
            if (category.isBlank()) add("category")
            if (account.isNullOrBlank()) add("account")
        }

    val isComplete: Boolean
        get() = missingFields.isEmpty()

    val apiDate: String
        get() = API_DATE.get()!!.format(Date(dateMillis))

    /** A copy for the next card: same account, date and type, fresh details. */
    fun nextFromThis(): TransactionEntryState = TransactionEntryState(
        type = type,
        account = account,
        dateMillis = dateMillis
    )

    fun duplicate(): TransactionEntryState = TransactionEntryState(
        type = type,
        category = category,
        amount = amount,
        note = note,
        account = account,
        dateMillis = dateMillis,
        excludeAnalytics = excludeAnalytics
    )

    companion object {
        private val idCounter = AtomicLong(System.currentTimeMillis())
        fun nextLocalId(): Long = idCounter.incrementAndGet()

        private val API_DATE = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        }

        fun parseApiDate(raw: String?): Long? = raw
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { API_DATE.get()!!.parse(it)?.time }.getOrNull() }

        fun formatApiDate(millis: Long): String = API_DATE.get()!!.format(Date(millis))
    }
}

/** Human list: "amount", "amount & category", "amount, category & account". */
fun List<String>.joinAsSentence(): String = when (size) {
    0 -> ""
    1 -> first()
    else -> dropLast(1).joinToString(", ") + " & " + last()
}
