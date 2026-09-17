package com.example.dailytrack_mobile.presentation.screens.invest

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// Investment accent palette
// ─────────────────────────────────────────────────────────────────────────────

object InvestColors {
    val Stocks       = Color(0xFF2ECC71)  // green
    val MutualFunds  = Color(0xFFA78BFA)  // lavender
    val Retirement   = Color(0xFF3B82F6)  // blue
    val FD           = Color(0xFFF59E0B)  // amber
    val Gold         = Color(0xFFFBBF24)  // gold
    val RealEstate   = Color(0xFFEC4899)  // pink
    val GainGreen    = Color(0xFF2ECC71)
    val LossRed      = Color(0xFFEF4444)
}

// ─────────────────────────────────────────────────────────────────────────────
// Data models
// ─────────────────────────────────────────────────────────────────────────────

data class InvestmentHolding(
    val name: String,
    val invested: Double,
    val current: Double,
    val category: InvestCategory
) {
    val pnl: Double get() = current - invested
    val pnlPercent: Double get() = if (invested == 0.0) 0.0 else (pnl / invested) * 100.0
    val isGain: Boolean get() = pnl >= 0
}

enum class InvestCategory(val label: String, val color: Color) {
    STOCKS("Stocks", InvestColors.Stocks),
    MUTUAL_FUNDS("Mutual Funds", InvestColors.MutualFunds),
    RETIREMENT("Retirement", InvestColors.Retirement),
    FD("FD", InvestColors.FD),
    GOLD("Gold", InvestColors.Gold),
    REAL_ESTATE("Real Estate", InvestColors.RealEstate)
}

enum class InvestTab(val label: String) {
    OVERVIEW("Overview"),
    STOCKS("Stocks"),
    MUTUAL_FUNDS("Mutual Funds"),
    RETIREMENT("Retirement"),
    FD("FD"),
    GOLD("Gold"),
    REAL_ESTATE("Real Estate")
}

// ─────────────────────────────────────────────────────────────────────────────
// State
// ─────────────────────────────────────────────────────────────────────────────

enum class ChartTimeRange(val label: String) {
    ONE_MONTH("1M"),
    THREE_MONTHS("3M"),
    SIX_MONTHS("6M"),
    ONE_YEAR("1Y"),
    YTD("YTD"),
    ALL("ALL")
}

data class ChartPoint(
    val date: String,
    val invested: Float,
    val current: Float
) {
    val pnl: Float get() = current - invested
    val pnlPercent: Float get() = if (invested == 0f) 0f else (pnl / invested) * 100f
}

// -----------------------------------------------------------------------------
// Holdings snapshots
//
// Every portfolio snapshot keeps the individual holdings that made it up, so a
// point on the chart can be opened to see what was actually held that day — and
// diffed against any other day the app has a snapshot for.
// -----------------------------------------------------------------------------

enum class HoldingsType(val label: String) {
    EQUITY("Stocks"),
    MUTUAL_FUNDS("Mutual Funds")
}

data class HoldingSnapshotRow(
    val symbol: String,
    val quantity: Double,
    val averagePrice: Double,
    /** LTP for equity, NAV for mutual funds — the same slot either way. */
    val unitPrice: Double,
    val invested: Double,
    val current: Double
) {
    val pnl: Double get() = current - invested
    val pnlPercent: Double get() = if (invested == 0.0) 0.0 else (pnl / invested) * 100.0
}

enum class HoldingChange { NEW, EXITED, HELD }

/**
 * One symbol across two dates, always ordered chronologically: [from] is the
 * earlier date and [to] the later one, so every difference reads "later minus
 * earlier" regardless of which date was picked on the chart first.
 */
data class HoldingComparison(
    val symbol: String,
    val from: HoldingSnapshotRow?,
    val to: HoldingSnapshotRow?
) {
    val change: HoldingChange
        get() = when {
            from == null -> HoldingChange.NEW
            to == null -> HoldingChange.EXITED
            else -> HoldingChange.HELD
        }

    val quantity: Double get() = to?.quantity ?: 0.0
    val quantityDelta: Double get() = quantity - (from?.quantity ?: 0.0)

    val unitPrice: Double get() = to?.unitPrice ?: 0.0
    val unitPriceDelta: Double get() = if (from != null && to != null) to.unitPrice - from.unitPrice else 0.0

    val invested: Double get() = to?.invested ?: 0.0
    val investedDelta: Double get() = invested - (from?.invested ?: 0.0)

    val current: Double get() = to?.current ?: 0.0
    val currentDelta: Double get() = current - (from?.current ?: 0.0)

    val fromQuantity: Double get() = from?.quantity ?: 0.0
    val fromPrice: Double get() = from?.unitPrice ?: 0.0
    val fromValue: Double get() = from?.current ?: 0.0

    /** Price move per unit — the cleanest read on performance, unaffected by buying or selling. */
    val pricePercent: Double
        get() = if (from == null || to == null || from.unitPrice == 0.0) 0.0 else unitPriceDelta / from.unitPrice * 100.0

    /** Change in position value, which also includes any units added or removed. */
    val currentPercent: Double get() = if (fromValue == 0.0) 0.0 else currentDelta / fromValue * 100.0

    val returns: Double get() = current - invested
    val returnPercent: Double get() = if (invested == 0.0) 0.0 else returns / invested * 100.0
    val returnsDelta: Double get() = returns - ((from?.current ?: 0.0) - (from?.invested ?: 0.0))

    /** Largest value on either date, so exited positions still sort sensibly. */
    val sortValue: Double get() = maxOf(current, from?.current ?: 0.0)
}

data class InvestState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val selectedTab: InvestTab = InvestTab.OVERVIEW,
    val holdings: List<InvestmentHolding> = emptyList(),
    val historicalSnapshots: List<com.example.dailytrack_mobile.data.remote.dto.PortfolioSnapshotDto> = emptyList(),
    val selectedTimeRange: ChartTimeRange = ChartTimeRange.THREE_MONTHS,
    val chartPoints: List<ChartPoint> = emptyList(),
    val hiddenCategories: Set<InvestCategory> = emptySet(),
    val isCategorySettingsOpen: Boolean = false,

    // Holdings snapshot sheet
    val isHoldingsSheetOpen: Boolean = false,
    val snapshotDate: String? = null,
    val snapshotType: HoldingsType = HoldingsType.EQUITY,
    val isSnapshotLoading: Boolean = false,
    val snapshotHoldings: List<HoldingSnapshotRow> = emptyList(),
    val snapshotError: String? = null,
    val compareDate: String? = null,
    val isCompareLoading: Boolean = false,
    val compareHoldings: List<HoldingSnapshotRow> = emptyList(),
    val isComparePickerOpen: Boolean = false
) {
    // ── Visibility helpers ──────────────────────────────────────────────
    val visibleCategoriesCount: Int get() = InvestCategory.entries.size - hiddenCategories.size
    val totalCategoriesCount: Int get() = InvestCategory.entries.size
    val isAnyCategoryHidden: Boolean get() = hiddenCategories.isNotEmpty()

    // ── Derived portfolio totals (reflecting visible categories) ────────
    val totalInvested: Double get() = holdings.filter { it.category !in hiddenCategories }.sumOf { it.invested }
    val totalCurrent: Double get() = holdings.filter { it.category !in hiddenCategories }.sumOf { it.current }
    val totalPnl: Double get() = totalCurrent - totalInvested
    val totalPnlPercent: Double get() = if (totalInvested == 0.0) 0.0 else (totalPnl / totalInvested) * 100.0
    val isOverallGain: Boolean get() = totalPnl >= 0

    // ── Per-category aggregation ────────────────────────────────────────
    data class CategorySummary(
        val category: InvestCategory,
        val invested: Double,
        val current: Double,
        val isVisible: Boolean = true
    ) {
        val pnl: Double get() = current - invested
        val pnlPercent: Double get() = if (invested == 0.0) 0.0 else (pnl / invested) * 100.0
        val isGain: Boolean get() = pnl >= 0
    }

    val categorySummaries: List<CategorySummary>
        get() = InvestCategory.entries.map { cat ->
            val catHoldings = holdings.filter { it.category == cat }
            CategorySummary(
                category = cat,
                invested = catHoldings.sumOf { it.invested },
                current = catHoldings.sumOf { it.current },
                isVisible = cat !in hiddenCategories
            )
        }.filter { it.invested > 0 || it.current > 0 }

    val visibleCategorySummaries: List<CategorySummary>
        get() = categorySummaries.filter { it.isVisible }

    // ── Filtered holdings for a tab ─────────────────────────────────────
    val filteredHoldings: List<InvestmentHolding>
        get() = when (selectedTab) {
            InvestTab.OVERVIEW -> holdings.filter { it.category !in hiddenCategories }
            InvestTab.STOCKS -> holdings.filter { it.category == InvestCategory.STOCKS }
            InvestTab.MUTUAL_FUNDS -> holdings.filter { it.category == InvestCategory.MUTUAL_FUNDS }
            InvestTab.RETIREMENT -> holdings.filter { it.category == InvestCategory.RETIREMENT }
            InvestTab.FD -> holdings.filter { it.category == InvestCategory.FD }
            InvestTab.GOLD -> holdings.filter { it.category == InvestCategory.GOLD }
            InvestTab.REAL_ESTATE -> holdings.filter { it.category == InvestCategory.REAL_ESTATE }
        }

    val filteredInvested: Double get() = filteredHoldings.sumOf { it.invested }
    val filteredCurrent: Double get() = filteredHoldings.sumOf { it.current }
    val filteredPnl: Double get() = filteredCurrent - filteredInvested
    val filteredPnlPercent: Double get() = if (filteredInvested == 0.0) 0.0 else (filteredPnl / filteredInvested) * 100.0
    val isFilteredGain: Boolean get() = filteredPnl >= 0

    // ── Timeframe period metrics ─────────────────────────────────────────
    val periodStartPoint: ChartPoint? get() = chartPoints.firstOrNull()
    val periodEndPoint: ChartPoint? get() = chartPoints.lastOrNull()

    val periodCurrent: Double
        get() = periodEndPoint?.current?.toDouble() ?: filteredCurrent

    val periodInvested: Double
        get() = periodEndPoint?.invested?.toDouble() ?: filteredInvested

    val periodPnl: Double
        get() {
            if (selectedTimeRange == ChartTimeRange.ALL || chartPoints.size < 2) {
                return filteredPnl
            }
            val start = periodStartPoint ?: return filteredPnl
            val end = periodEndPoint ?: return filteredPnl
            val netInflow = (end.invested - start.invested).toDouble()
            val valueDiff = (end.current - start.current).toDouble()
            return valueDiff - netInflow
        }

    val periodPnlPercent: Double
        get() {
            if (selectedTimeRange == ChartTimeRange.ALL || chartPoints.size < 2) {
                return filteredPnlPercent
            }
            val start = periodStartPoint ?: return filteredPnlPercent
            val base = start.current.toDouble()
            return if (base > 0.0) (periodPnl / base) * 100.0 else 0.0
        }

    val isPeriodGain: Boolean get() = periodPnl >= 0.0

    val periodLabel: String
        get() = when (selectedTimeRange) {
            ChartTimeRange.ALL -> "overall"
            ChartTimeRange.YTD -> "YTD"
            else -> "past ${selectedTimeRange.label}"
        }

    // -- Holdings snapshot ------------------------------------------------

    val isComparing: Boolean get() = compareDate != null

    /** Every date the app holds a snapshot for, newest first. */
    val availableSnapshotDates: List<String>
        get() = historicalSnapshots
            .map { it.date.substringBefore("T") }
            .distinct()
            .sortedDescending()

    val snapshotTotalValue: Double get() = snapshotHoldings.sumOf { it.current }
    val snapshotTotalInvested: Double get() = snapshotHoldings.sumOf { it.invested }
    val snapshotTotalReturn: Double get() = snapshotTotalValue - snapshotTotalInvested
    val snapshotTotalReturnPercent: Double
        get() = if (snapshotTotalInvested == 0.0) 0.0 else (snapshotTotalReturn / snapshotTotalInvested) * 100.0

    // Dates are ISO yyyy-MM-dd, so string order is chronological order.
    private val snapshotIsLater: Boolean
        get() = compareDate != null && snapshotDate != null && snapshotDate > compareDate

    /** The earlier of the two dates being compared. */
    val comparisonFromDate: String? get() = if (snapshotIsLater) compareDate else snapshotDate

    /** The later of the two dates being compared. */
    val comparisonToDate: String? get() = if (snapshotIsLater) snapshotDate else compareDate

    private val fromHoldings: List<HoldingSnapshotRow>
        get() = if (snapshotIsLater) compareHoldings else snapshotHoldings

    private val toHoldings: List<HoldingSnapshotRow>
        get() = if (snapshotIsLater) snapshotHoldings else compareHoldings

    /** Both dates merged by symbol, largest positions first. */
    val holdingsComparison: List<HoldingComparison>
        get() {
            if (!isComparing) return emptyList()
            val fromBySymbol = fromHoldings.associateBy { it.symbol }
            val toBySymbol = toHoldings.associateBy { it.symbol }
            return (fromBySymbol.keys + toBySymbol.keys)
                .map { symbol ->
                    HoldingComparison(symbol = symbol, from = fromBySymbol[symbol], to = toBySymbol[symbol])
                }
                .sortedByDescending { it.sortValue }
        }

    val comparisonFromInvested: Double get() = fromHoldings.sumOf { it.invested }
    val comparisonToInvested: Double get() = toHoldings.sumOf { it.invested }
    val comparisonFromValue: Double get() = fromHoldings.sumOf { it.current }
    val comparisonToValue: Double get() = toHoldings.sumOf { it.current }

}
