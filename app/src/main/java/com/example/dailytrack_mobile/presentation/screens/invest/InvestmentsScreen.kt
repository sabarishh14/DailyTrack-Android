package com.example.dailytrack_mobile.presentation.screens.invest

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import com.example.dailytrack_mobile.presentation.screens.invest.components.AssetAllocationCard
import com.example.dailytrack_mobile.presentation.screens.invest.components.CategorySummaryCard
import com.example.dailytrack_mobile.presentation.screens.invest.components.CategoryVisibilitySheet
import com.example.dailytrack_mobile.presentation.screens.invest.components.ChartLegend
import com.example.dailytrack_mobile.presentation.screens.invest.components.EmptyHoldingsState
import com.example.dailytrack_mobile.presentation.screens.invest.components.FilterPills
import com.example.dailytrack_mobile.presentation.screens.invest.components.HoldingItem
import com.example.dailytrack_mobile.presentation.screens.invest.components.PortfolioHeader
import com.example.dailytrack_mobile.presentation.screens.invest.components.AdvancedChart
import com.example.dailytrack_mobile.presentation.screens.invest.components.SummaryRow
import com.example.dailytrack_mobile.presentation.screens.invest.components.TimeRangeToggle
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.dailytrack_mobile.presentation.components.DailyTrackPullToRefreshBox
import com.example.dailytrack_mobile.presentation.util.Dimens
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────
internal fun formatCompact(amount: Double): String {
    val abs = Math.abs(amount)
    return when {
        abs >= 1_00_00_000 -> "₹%.2fCr".format(abs / 1_00_00_000)
        abs >= 1_00_000    -> "₹%.2fL".format(abs / 1_00_000)
        abs >= 1_000       -> "₹%.1fK".format(abs / 1_000)
        else               -> "₹%.0f".format(abs)
    }
}

internal fun formatFull(amount: Double): String {
    val prefix = if (amount < 0) "-" else ""
    return "$prefix₹%,.2f".format(Math.abs(amount))
}

internal fun formatExactCurrency(amount: Double): String {
    val isNegative = amount < 0
    val absAmount = Math.abs(amount)
    val integerPart = absAmount.toLong()
    val remainder = ((absAmount - integerPart) * 100).roundToInt()
    val decimalStr = if (remainder > 0) String.format(java.util.Locale.US, ".%02d", remainder) else ""

    val str = integerPart.toString()
    val formattedInt = if (str.length <= 3) {
        str
    } else {
        val last3 = str.substring(str.length - 3)
        val rest = str.substring(0, str.length - 3)
        val sb = StringBuilder()
        var count = 0
        for (i in rest.length - 1 downTo 0) {
            sb.append(rest[i])
            count++
            if (count == 2 && i > 0) {
                sb.append(',')
                count = 0
            }
        }
        sb.reverse().toString() + "," + last3
    }
    val prefix = if (isNegative) "-₹" else "₹"
    return "$prefix$formattedInt$decimalStr"
}

internal fun formatPnl(amount: Double): String {
    val prefix = if (amount >= 0) "+" else "-"
    return "$prefix${formatCompact(amount)}"
}

internal fun formatPnlExact(amount: Double): String {
    val prefix = if (amount >= 0) "+" else ""
    return "$prefix${formatExactCurrency(amount)}"
}

internal fun formatPointDate(dateStr: String): String {
    return try {
        val date = java.time.LocalDate.parse(dateStr)
        val month = date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        "${date.dayOfMonth} $month ${date.year}"
    } catch (e: Exception) {
        dateStr
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Main Composable
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun InvestmentsScreen(
    viewModel: InvestVM = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val dims = Dimens.current

    var isValueMode by remember { mutableStateOf(true) }
    var selectedChartPoint by remember { mutableStateOf<ChartPoint?>(null) }

    LaunchedEffect(state.chartPoints) {
        selectedChartPoint = null
    }

    DailyTrackPullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { viewModel.onAction(InvestAction.Refresh) },
        modifier = Modifier.fillMaxSize()
    ) {
        if (state.isLoading && state.holdings.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(dims.itemSpacingLarge)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                        strokeCap = StrokeCap.Round
                    )
                    Text(
                        text = "Loading portfolio...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(bottom = dims.screenBottomPadding)
            ) {
                // ── Portfolio header ────────────────────────────────────────────
                item {
                    PortfolioHeader(
                        state = state,
                        isValueMode = isValueMode,
                        onValueModeChanged = { isValueMode = it },
                        selectedPoint = selectedChartPoint,
                        onClearSelection = { selectedChartPoint = null },
                        onOpenCategorySettings = { viewModel.onAction(InvestAction.SetCategorySettingsOpen(true)) }
                    )
                }

                // ── Advanced chart ─────────────────────────────────────────────
                item {
                    AdvancedChart(
                        points = state.chartPoints,
                        isValueMode = isValueMode,
                        isGain = state.isPeriodGain,
                        showXAxis = true,
                        selectedPoint = selectedChartPoint,
                        onPointSelected = { selectedChartPoint = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(205.dp)
                            .padding(horizontal = dims.screenHorizontalPadding)
                    )
                }

                // ── Chart Legend (Current vs Invested or Return vs Baseline) ───
                item {
                    ChartLegend(
                        isValueMode = isValueMode,
                        isGain = state.isPeriodGain
                    )
                }

                // ── Time range toggle (1M | 3M | 6M | 1Y | YTD | ALL) ───────────
                item {
                    Spacer(modifier = Modifier.height(dims.itemSpacingSmall))
                    TimeRangeToggle(
                        selectedRange = state.selectedTimeRange,
                        onRangeSelected = { viewModel.onAction(InvestAction.SelectTimeRange(it)) },
                        modifier = Modifier.padding(horizontal = dims.screenHorizontalPadding)
                    )
                }

                // ── Summary row (Invested / Current / P&L) ─────────────────────
                item {
                    Spacer(modifier = Modifier.height(dims.itemSpacingSmall))
                    SummaryRow(
                        state = state,
                        selectedPoint = selectedChartPoint
                    )
                }

                // ── Filter pill tabs ────────────────────────────────────────────
                item {
                    FilterPills(
                        selectedTab = state.selectedTab,
                        onTabSelected = { viewModel.onAction(InvestAction.SelectTab(it)) }
                    )
                }

                // ── Content based on selected tab ───────────────────────
                when (state.selectedTab) {
                    InvestTab.OVERVIEW -> {
                        // Show asset allocation breakdown card
                        if (state.visibleCategorySummaries.isNotEmpty()) {
                            item {
                                AssetAllocationCard(
                                    summaries = state.visibleCategorySummaries,
                                    totalCurrent = state.totalCurrent,
                                    isFiltered = state.isAnyCategoryHidden,
                                    totalCategoriesCount = state.totalCategoriesCount
                                )
                            }
                        }
                        // Show category summary cards
                        items(state.categorySummaries) { summary ->
                            CategorySummaryCard(
                                summary = summary,
                                onClick = {
                                    val targetTab = when (summary.category) {
                                        InvestCategory.STOCKS -> InvestTab.STOCKS
                                        InvestCategory.MUTUAL_FUNDS -> InvestTab.MUTUAL_FUNDS
                                        InvestCategory.RETIREMENT -> InvestTab.RETIREMENT
                                        InvestCategory.FD -> InvestTab.FD
                                        InvestCategory.GOLD -> InvestTab.GOLD
                                        InvestCategory.REAL_ESTATE -> InvestTab.REAL_ESTATE
                                    }
                                    viewModel.onAction(InvestAction.SelectTab(targetTab))
                                }
                            )
                        }
                    }
                    else -> {
                        // Show individual holdings directly (TabSummaryHeader is removed as cards below graph show it)
                        val filtered = state.filteredHoldings
                        if (filtered.isEmpty()) {
                            item {
                                EmptyHoldingsState(tab = state.selectedTab)
                            }
                        } else {
                            items(filtered) { holding ->
                                HoldingItem(holding = holding)
                            }
                        }
                    }
                }
            }
        }

        if (state.isCategorySettingsOpen) {
            val categoryTotals = remember(state.holdings) {
                InvestCategory.entries.associateWith { cat ->
                    state.holdings.filter { it.category == cat }.sumOf { it.current }
                }
            }
            CategoryVisibilitySheet(
                hiddenCategories = state.hiddenCategories,
                categoryTotals = categoryTotals,
                onToggleCategory = { viewModel.onAction(InvestAction.ToggleCategoryVisibility(it)) },
                onSetAllVisibility = { viewModel.onAction(InvestAction.SetAllCategoriesVisibility(it)) },
                onDismiss = { viewModel.onAction(InvestAction.SetCategorySettingsOpen(false)) }
            )
        }
    }
}
