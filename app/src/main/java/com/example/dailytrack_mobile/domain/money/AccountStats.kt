package com.example.dailytrack_mobile.domain.money

import java.time.LocalDate
import java.time.YearMonth

/** One transaction's part in the analyser's by-account view. */
data class AccountSpend(val account: String, val month: YearMonth, val amount: Double)

data class AccountRow(
    val account: String,
    /** A month over the 12 before [AccountStats.latest], since its first month there; null when it's new. */
    val average: Double?,
    val months: Int,
    /** The latest month (this month so far, when it's still going). */
    val latest: Double,
    /** [latest] against [average], in %; null without one to compare with. */
    val change: Double?,
    /** Its share of what the filters show (the donut). */
    val share: Double,
    /** The 6 months up to the latest, oldest first. */
    val trend: List<Double>
)

data class AccountStats(
    /** What the filters show, per account, biggest first: the donut. */
    val shown: List<Pair<String, Double>>,
    val months: List<YearMonth>,
    val latest: YearMonth?,
    /** The latest month is this one, still going. */
    val inProgress: Boolean,
    val rows: List<AccountRow>
) {
    companion object {
        val EMPTY = AccountStats(emptyList(), emptyList(), null, false, emptyList())
    }
}

/**
 * Each account's latest month against its usual, worked out as the server does
 * (backend money_query._account_months, which the web shows). The latest month
 * is the last one the filters cover; its usual is the average of the 12 months
 * before it, from the account's first month there, a month with nothing on it
 * counting as 0. Date filters only pick the latest month: [history] has every
 * other filter applied, but not the dates.
 */
object AccountStatsCalculator {
    private const val HISTORY_MONTHS = 12

    fun of(shown: List<AccountSpend>, history: List<AccountSpend>, today: LocalDate): AccountStats {
        val anchor = shown.maxOfOrNull { it.month } ?: return AccountStats.EMPTY
        val first = anchor.minusMonths(HISTORY_MONTHS.toLong())
        val byAccount = history.filter { it.month in first..anchor }
            .groupBy { it.account }
            .mapValues { (_, list) -> list.groupBy { it.month }.mapValues { (_, inMonth) -> inMonth.sumOf { it.amount } } }
        val shownTotals = shown.groupBy { it.account }.mapValues { (_, list) -> list.sumOf { it.amount } }
        val shownTotal = shownTotals.values.sum()
        val before = (HISTORY_MONTHS downTo 1).map { anchor.minusMonths(it.toLong()) }
        val trendMonths = (5 downTo 0).map { anchor.minusMonths(it.toLong()) }
        val rows = byAccount.map { (account, perMonth) ->
            val firstMonth = perMonth.keys.min()
            val span = before.filter { it >= firstMonth }
            val average = if (span.isNotEmpty()) span.sumOf { perMonth[it] ?: 0.0 } / span.size else null
            val latest = perMonth[anchor] ?: 0.0
            AccountRow(
                account = account,
                average = average,
                months = span.size,
                latest = latest,
                change = average?.takeIf { it != 0.0 }?.let { (latest - it) / it * 100 },
                share = if (shownTotal > 0) (shownTotals[account] ?: 0.0) / shownTotal * 100 else 0.0,
                trend = trendMonths.map { perMonth[it] ?: 0.0 }
            )
        }.sortedWith(compareByDescending<AccountRow> { it.latest }.thenByDescending { it.average ?: 0.0 })
        return AccountStats(
            shown = shownTotals.toList().sortedByDescending { it.second },
            months = trendMonths,
            latest = anchor,
            inProgress = anchor == YearMonth.from(today),
            rows = rows
        )
    }
}
