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
// Filter Row with Quick Presets and Active Removable Chips
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AnalysisFilterRow(
    filterState: AnalysisFilterState,
    onOpenFilterSheet: () -> Unit,
    onAction: (MoneyAction) -> Unit
) {
    val dims = Dimens.current

    val scrollState = rememberScrollState()
    var showMonthYearPicker by remember { mutableStateOf(false) }

    if (showMonthYearPicker) {
        MonthYearPickerDialog(
            selectedMonth = filterState.selectedMonth,
            selectedYear = filterState.selectedYear ?: LocalDate.now().year,
            onSelected = { month, year ->
                onAction(MoneyAction.SelectMonthYearFilter(month, year))
                showMonthYearPicker = false
            },
            onDismiss = { showMonthYearPicker = false }
        )
    }

    LaunchedEffect(filterState.hasActiveFilters) {
        if (!filterState.hasActiveFilters) {
            scrollState.animateScrollTo(0)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Filters: icon only, with the active count beside it.
            Surface(
                onClick = onOpenFilterSheet,
                shape = RoundedCornerShape(dims.buttonCornerRadius - 2.dp),
                color = if (filterState.hasActiveFilters) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.height(32.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Filters",
                        tint = if (filterState.hasActiveFilters) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp)
                    )
                    if (filterState.hasActiveFilters) {
                        Text(
                            text = "${filterState.activeFilterCount}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Clear: icon only, while anything is filtered.
            if (filterState.hasActiveFilters) {
                Surface(
                    onClick = { onAction(MoneyAction.ResetAnalysisFilters) },
                    shape = RoundedCornerShape(dims.buttonCornerRadius - 2.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear all filters",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Vertical divider separating main filter button from quick pills
            VerticalDivider(
                modifier = Modifier.height(20.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // ─── 1. ACTIVE / SELECTED FILTERS FIRST ──────────────────────────

            // A. Active Categories (Included / Excluded)
            filterState.categoryFilters.forEach { (cat, status) ->
                when (status) {
                    ItemFilterStatus.INCLUDED -> {
                        ActiveFilterRemovableChip(
                            text = "+ $cat",
                            isIncluded = true,
                            onRemove = { onAction(MoneyAction.RemoveCategoryFilter(cat)) }
                        )
                    }
                    ItemFilterStatus.EXCLUDED -> {
                        ActiveFilterRemovableChip(
                            text = "- $cat",
                            isExcluded = true,
                            onRemove = { onAction(MoneyAction.RemoveCategoryFilter(cat)) }
                        )
                    }
                    ItemFilterStatus.NEUTRAL -> Unit
                }
            }

            // B. Active Accounts (Included / Excluded)
            val sortedAccountFilters = remember(filterState.accountFilters) {
                sortAccountsCanonical(filterState.accountFilters.keys.toList()).mapNotNull { acc ->
                    filterState.accountFilters[acc]?.let { status -> acc to status }
                }
            }
            sortedAccountFilters.forEach { (acc, status) ->
                when (status) {
                    ItemFilterStatus.INCLUDED -> {
                        ActiveFilterRemovableChip(
                            text = "+ $acc",
                            isIncluded = true,
                            onRemove = { onAction(MoneyAction.RemoveAccountFilter(acc)) }
                        )
                    }
                    ItemFilterStatus.EXCLUDED -> {
                        ActiveFilterRemovableChip(
                            text = "- $acc",
                            isExcluded = true,
                            onRemove = { onAction(MoneyAction.RemoveAccountFilter(acc)) }
                        )
                    }
                    ItemFilterStatus.NEUTRAL -> Unit
                }
            }

            // C. Active Date Filter (Month/Year, Preset, Financial Year, or Custom Range)
            val isMonthYearActive = filterState.selectedYear != null
            if (isMonthYearActive) {
                val monthYearActiveText = when {
                    filterState.selectedMonth != null ->
                        "${filterState.selectedMonth.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${filterState.selectedYear}"
                    else ->
                        "Year ${filterState.selectedYear}"
                }
                ActiveFilterRemovableChip(
                    text = monthYearActiveText,
                    onRemove = { onAction(MoneyAction.ClearDateRangeFilter) }
                )
            } else if (filterState.activeDatePreset == QuickFilterPreset.THIS_MONTH) {
                QuickPresetChip(
                    text = "This Month",
                    isSelected = true,
                    onClick = { onAction(MoneyAction.ToggleQuickPreset(QuickFilterPreset.THIS_MONTH)) }
                )
            } else if (filterState.activeDatePreset == QuickFilterPreset.LAST_MONTH) {
                QuickPresetChip(
                    text = "Last Month",
                    isSelected = true,
                    onClick = { onAction(MoneyAction.ToggleQuickPreset(QuickFilterPreset.LAST_MONTH)) }
                )
            } else if (!filterState.financialYear.isNullOrBlank() && filterState.financialYear != "All Time") {
                ActiveFilterRemovableChip(
                    text = filterState.financialYear,
                    onRemove = { onAction(MoneyAction.ClearFinancialYearFilter) }
                )
            } else if (filterState.customDateRange != null) {
                filterState.formattedDateRange()?.let { rangeText ->
                    ActiveFilterRemovableChip(
                        text = rangeText,
                        onRemove = { onAction(MoneyAction.ClearDateRangeFilter) }
                    )
                }
            }

            // D. Active Type Filter
            val isExpensesOnly = filterState.selectedTypes == setOf(TransactionType.DEBIT)
            val isIncomeOnly = filterState.selectedTypes == setOf(TransactionType.CREDIT)
            if (isExpensesOnly) {
                QuickPresetChip(
                    text = "Expenses Only",
                    isSelected = true,
                    onClick = { onAction(MoneyAction.ToggleQuickPreset(QuickFilterPreset.EXPENSES_ONLY)) }
                )
            } else if (isIncomeOnly) {
                QuickPresetChip(
                    text = "Income Only",
                    isSelected = true,
                    onClick = { onAction(MoneyAction.ToggleQuickPreset(QuickFilterPreset.INCOME_ONLY)) }
                )
            } else if (filterState.selectedTypes.isNotEmpty() && filterState.selectedTypes.size != TransactionType.values().size) {
                filterState.selectedTypes.forEach { type ->
                    ActiveFilterRemovableChip(
                        text = type.displayName,
                        onRemove = { onAction(MoneyAction.RemoveTypeFilter(type)) }
                    )
                }
            }

            // Subtle divider between active filters and available quick presets
            if (filterState.hasActiveFilters) {
                VerticalDivider(
                    modifier = Modifier.height(16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
            }

            // ─── 2. AVAILABLE / UNSELECTED PRESETS ───────────────────────────
            if (filterState.activeDatePreset != QuickFilterPreset.THIS_MONTH) {
                QuickPresetChip(
                    text = "This Month",
                    isSelected = false,
                    onClick = { onAction(MoneyAction.ToggleQuickPreset(QuickFilterPreset.THIS_MONTH)) }
                )
            }

            if (!isExpensesOnly) {
                QuickPresetChip(
                    text = "Expenses Only",
                    isSelected = false,
                    onClick = { onAction(MoneyAction.ToggleQuickPreset(QuickFilterPreset.EXPENSES_ONLY)) }
                )
            }

            if (filterState.activeDatePreset != QuickFilterPreset.LAST_MONTH) {
                QuickPresetChip(
                    text = "Last Month",
                    isSelected = false,
                    onClick = { onAction(MoneyAction.ToggleQuickPreset(QuickFilterPreset.LAST_MONTH)) }
                )
            }

            if (!isMonthYearActive) {
                QuickPresetChip(
                    text = "Month / Year",
                    isSelected = false,
                    onClick = { showMonthYearPicker = true }
                )
            }

            if (!isIncomeOnly) {
                QuickPresetChip(
                    text = "Income Only",
                    isSelected = false,
                    onClick = { onAction(MoneyAction.ToggleQuickPreset(QuickFilterPreset.INCOME_ONLY)) }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dotted Clear Button Component
// ─────────────────────────────────────────────────────────────────────────────
@Composable
internal fun DottedClearButton(onClick: () -> Unit) {
    val dims = Dimens.current
    val color = ChartColors.ExpenseRed
    
    Box(
        modifier = Modifier
            .width(80.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(dims.buttonCornerRadius - 2.dp))
            .clickable { onClick() }
            .drawBehind {
                drawRoundRect(
                    color = color.copy(alpha = 0.8f),
                    size = size,
                    style = Stroke(
                        width = 4f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                    ),
                    cornerRadius = CornerRadius((dims.buttonCornerRadius - 2.dp).toPx())
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Clear",
            color = color,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Quick Preset Chip Component
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickPresetChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val dims = Dimens.current
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            )
        },
        leadingIcon = if (isSelected) {
            {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
            }
        } else null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.primary
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isSelected,
            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(dims.buttonCornerRadius - 2.dp)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Active Filter Removable Chip Component
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActiveFilterRemovableChip(
    text: String,
    onRemove: () -> Unit,
    isIncluded: Boolean = false,
    isExcluded: Boolean = false
) {
    val dims = Dimens.current

    val containerColor = when {
        isExcluded -> MaterialTheme.colorScheme.errorContainer
        isIncluded -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }

    val contentColor = when {
        isExcluded -> MaterialTheme.colorScheme.onErrorContainer
        isIncluded -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        shape = RoundedCornerShape(dims.buttonCornerRadius - 2.dp),
        color = containerColor,
        modifier = Modifier.height(32.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 10.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = contentColor
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(22.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove filter",
                    modifier = Modifier.size(14.dp),
                    tint = contentColor
                )
            }
        }
    }
}

