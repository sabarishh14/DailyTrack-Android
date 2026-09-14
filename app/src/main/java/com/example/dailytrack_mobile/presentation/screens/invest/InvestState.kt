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

data class InvestState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val selectedTab: InvestTab = InvestTab.OVERVIEW,
    val holdings: List<InvestmentHolding> = emptyList(),
    val historicalSnapshots: List<com.example.dailytrack_mobile.data.remote.dto.PortfolioSnapshotDto> = emptyList(),
    val selectedTimeRange: ChartTimeRange = ChartTimeRange.THREE_MONTHS,
    val chartPoints: List<ChartPoint> = emptyList(),
    val hiddenCategories: Set<InvestCategory> = emptySet(),
    val isCategorySettingsOpen: Boolean = false
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

}
