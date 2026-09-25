package com.example.dailytrack_mobile.presentation.screens.money.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind
import com.example.dailytrack_mobile.presentation.components.MonthYearPickerDialog
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.core.graphics.ColorUtils
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.dailytrack_mobile.presentation.screens.money.*
import com.example.dailytrack_mobile.presentation.theme.AppTheme
import com.example.dailytrack_mobile.presentation.theme.DtOgChartColors
import com.example.dailytrack_mobile.presentation.theme.LocalAppTheme
import com.example.dailytrack_mobile.presentation.util.Dimens

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────
internal fun formatExactCurrency(amount: Double): String {
    val isNegative = amount < 0
    val absAmount = Math.abs(amount)
    val integerPart = absAmount.toLong()
    val remainder = ((absAmount - integerPart) * 100).roundToLong()
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

    val sign = if (isNegative) "-" else ""
    return "${sign}₹$formattedInt$decimalStr"
}

internal fun formatShortened(amount: Double, withPrefix: Boolean = false): String {
    val abs = Math.abs(amount)
    val sign = if (amount < 0) "-" else ""
    val prefix = if (withPrefix) "₹" else ""
    val (num, suffix) = when {
        abs >= 1_00_00_000 -> (abs / 1_00_00_000) to "Cr"
        abs >= 1_00_000 -> (abs / 1_00_000) to "L"
        abs >= 1_000 -> (abs / 1_000) to "k"
        else -> abs to ""
    }
    val formattedNum = if (suffix.isEmpty()) {
        String.format(java.util.Locale.US, "%.0f", num)
    } else if (num >= 100) {
        String.format(java.util.Locale.US, "%.0f%s", num, suffix)
    } else if (num >= 10 || suffix == "k") {
        val s = String.format(java.util.Locale.US, "%.1f%s", num, suffix)
        s.replace(".0", "")
    } else {
        val s = String.format(java.util.Locale.US, "%.2f%s", num, suffix)
        s.replace(".00", "").replace(Regex("""(\.\d)0$"""), "$1")
    }
    return "$sign$prefix$formattedNum"
}

internal fun formatCompact(amount: Double): String {
    val abs = Math.abs(amount)
    return when {
        abs >= 1_00_000 -> String.format(java.util.Locale.US, "%.1fL", amount / 1_00_000)
        abs >= 1_000 -> String.format(java.util.Locale.US, "%.1fk", amount / 1_000)
        else -> String.format(java.util.Locale.US, "%.0f", amount)
    }
}

internal fun cleanDescriptionTitle(raw: String): String {
    var text = raw.trim()
    // Strip leading payment method tags like UPI/, UPI-, POS-, POS , IMPS-, NEFT-, etc.
    text = text.replace(Regex("^(UPI[-/]|POS[- ]|IMPS[-/]|NEFT[-/]|ACH[-/]|BILLDESK[- ]|PAYTM[-* ]|RAZORPAY[-* ]|BBPS[-/])", RegexOption.IGNORE_CASE), "")
    // Strip "Paid to " or "Transfer to " prefixes
    text = text.replace(Regex("^(Paid to |Transfer to |Payment to |To )", RegexOption.IGNORE_CASE), "")
    // If there's an internal slash separation (like 12345/Merchant/Bank), pick the most descriptive word
    if (text.contains("/")) {
        val parts = text.split("/").map { it.trim() }.filter { it.length > 2 && !it.all { ch -> ch.isDigit() } }
        if (parts.isNotEmpty()) {
            text = parts.firstOrNull { !it.equals("UPI", ignoreCase = true) && !it.equals("OK", ignoreCase = true) } ?: parts.first()
        }
    }
    // Remove trailing reference numbers/IDs like /1234567 or - 1234567
    text = text.replace(Regex("[-/]\\s*\\d{6,}.*$"), "")
    text = text.replace(Regex("\\s+"), " ").trim()
    return text.ifBlank { "Other" }
}

// ─────────────────────────────────────────────────────────────────────────────
// Analysis Tab
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AnalysisTab(
    state: MoneyState,
    onAction: (MoneyAction) -> Unit,
    onNavigateToTransactions: () -> Unit = {}
) {
    val dims = Dimens.current
    val categories = state.spendingAnalyzerData
    val filterState = state.analysisFilterState
    val isInitialLoading = state.isLoading && state.transactions.isEmpty()
    var drilldownCategories by remember { mutableStateOf<Set<String>?>(null) }
    val drilldownTransactions = drilldownCategories?.takeIf { categories.isNotEmpty() }?.let { selected ->
        state.filteredAnalysisTransactions.filter { tx -> selected.any { it.equals(tx.category, ignoreCase = true) } }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = dims.screenHorizontalPadding,
            end = dims.screenHorizontalPadding,
            top = dims.itemSpacingMedium,
            bottom = dims.screenBottomPadding
        ),
        // Tight enough that filters, chart, categories and totals share one screen.
        verticalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)
    ) {
        // Active Filter Bar / Trigger Header
        item {
            AnalysisFilterRow(
                filterState = filterState,
                onOpenFilterSheet = { onAction(MoneyAction.SetFilterSheetVisible(true)) },
                onAction = onAction
            )
        }

        // Cash Flow Breakdown Donut Card
        item {
            val periodTitle = filterState.financialYear ?: when (filterState.activeDatePreset) {
                QuickFilterPreset.THIS_MONTH -> "THIS MONTH"
                QuickFilterPreset.LAST_MONTH -> "LAST MONTH"
                else -> filterState.formattedDateRange() ?: "ALL TIME"
            }
            if (isInitialLoading && categories.isEmpty()) {
                CashFlowBreakdownCard(
                    categories = emptyList(),
                    transactions = emptyList(),
                    periodLabel = periodTitle,
                    hasActiveFilters = filterState.hasActiveFilters,
                    isLoading = true,
                    onViewTransactions = {}
                )
            } else if (categories.isNotEmpty()) {
                CashFlowBreakdownCard(
                    categories = categories,
                    transactions = state.filteredAnalysisTransactions,
                    periodLabel = periodTitle,
                    hasActiveFilters = filterState.hasActiveFilters,
                    isLoading = false,
                    onDrilldownChanged = { drilldownCategories = it },
                    onViewTransactions = { category ->
                        if (category != null) {
                            onAction(MoneyAction.ViewCategoryTransactions(category))
                        } else {
                            onAction(MoneyAction.SelectTab(1))
                        }
                        onNavigateToTransactions()
                    }
                )
            } else {
                EmptyFilterResultsCard(
                    onResetFilters = { onAction(MoneyAction.ResetAnalysisFilters) }
                )
            }
        }

        // Income & Expense Summary Row
        item {
            val periodSubtitle = filterState.financialYear ?: when (filterState.activeDatePreset) {
                QuickFilterPreset.THIS_MONTH -> "This month"
                QuickFilterPreset.LAST_MONTH -> "Last month"
                else -> filterState.formattedDateRange() ?: "All time"
            }
            IncomeExpenseRow(
                income = drilldownTransactions?.filter { it.type == TransactionType.CREDIT }?.sumOf { it.amount }
                    ?: state.filteredTotalIncome,
                expenses = drilldownTransactions?.filter { it.type == TransactionType.DEBIT }?.sumOf { it.amount }
                    ?: state.filteredTotalExpenses,
                periodLabel = when {
                    drilldownTransactions == null -> periodSubtitle
                    drilldownCategories?.size == 1 -> "${drilldownCategories!!.first()} · $periodSubtitle"
                    else -> "Other categories · $periodSubtitle"
                },
                isLoading = isInitialLoading
            )
        }
    }
}
