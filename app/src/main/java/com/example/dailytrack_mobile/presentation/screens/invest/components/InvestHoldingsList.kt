package com.example.dailytrack_mobile.presentation.screens.invest.components

import com.example.dailytrack_mobile.presentation.screens.invest.InvestCategory
import com.example.dailytrack_mobile.presentation.screens.invest.InvestColors
import com.example.dailytrack_mobile.presentation.screens.invest.InvestState
import com.example.dailytrack_mobile.presentation.screens.invest.InvestTab
import com.example.dailytrack_mobile.presentation.screens.invest.InvestmentHolding
import com.example.dailytrack_mobile.presentation.screens.invest.ChartTimeRange
import com.example.dailytrack_mobile.presentation.screens.invest.ChartPoint
import com.example.dailytrack_mobile.presentation.screens.invest.formatCompact
import com.example.dailytrack_mobile.presentation.screens.invest.formatPnl

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
import com.example.dailytrack_mobile.presentation.screens.invest.components.CategoryVisibilitySheet
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
// Filter Pills (horizontally scrollable)
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterPills(
    selectedTab: InvestTab,
    onTabSelected: (InvestTab) -> Unit
) {
    val dims = Dimens.current
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = dims.screenHorizontalPadding, vertical = dims.itemSpacingMedium),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InvestTab.entries.forEach { tab ->
            val isSelected = tab == selectedTab
            FilterChip(
                selected = isSelected,
                onClick = { onTabSelected(tab) },
                label = {
                    Text(
                        text = tab.label,
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
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Category Summary Card (for Overview tab)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
internal fun CategorySummaryCard(
    summary: InvestState.CategorySummary,
    onClick: () -> Unit
) {
    val dims = Dimens.current
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dims.screenHorizontalPadding, vertical = dims.itemSpacingSmall),
        shape = RoundedCornerShape(dims.cardCornerRadius - 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dims.cardInnerPadding - 4.dp)
                .alpha(if (summary.isVisible) 1f else 0.65f)
        ) {
            // Header row: category name + P&L badge + chevron
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Color dot
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(summary.category.color)
                    )
                    Text(
                        text = summary.category.label,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (!summary.isVisible) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.25f))
                        ) {
                            Text(
                                text = "Excluded",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // P&L percentage badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (summary.isGain) InvestColors.GainGreen.copy(alpha = 0.15f)
                                else InvestColors.LossRed.copy(alpha = 0.15f)
                            )
                            .padding(horizontal = dims.itemSpacingMedium, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${if (summary.isGain) "+" else ""}${String.format("%.1f", summary.pnlPercent)}%",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (summary.isGain) InvestColors.GainGreen else InvestColors.LossRed
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "View holdings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(dims.itemSpacingLarge))

            // Values row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ValueColumn(label = "INVESTED", value = formatCompact(summary.invested))
                ValueColumn(label = "CURRENT", value = formatCompact(summary.current))
                ValueColumn(
                    label = "P&L",
                    value = formatPnl(summary.pnl),
                    valueColor = if (summary.isGain) InvestColors.GainGreen else InvestColors.LossRed
                )
            }

            Spacer(modifier = Modifier.height(dims.itemSpacingLarge))

            // Accent bar
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.3f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(summary.category.color)
            )
        }
    }
}

@Composable
internal fun ValueColumn(
    label: String,
    value: String,
    valueColor: Color? = null
) {
    val dims = Dimens.current
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = valueColor ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty Holdings State (for filtered tabs with 0 items)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
internal fun EmptyHoldingsState(
    tab: InvestTab,
    modifier: Modifier = Modifier
) {
    val dims = Dimens.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dims.screenHorizontalPadding, vertical = dims.itemSpacingMedium),
        shape = RoundedCornerShape(dims.cardCornerRadius - 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dims.cardInnerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PieChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(dims.itemSpacingMedium))
            Text(
                text = "No ${tab.label} holdings yet",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Holdings categorized under ${tab.label} will be displayed here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Individual Holding Item
// ─────────────────────────────────────────────────────────────────────────────
@Composable
internal fun HoldingItem(holding: InvestmentHolding) {
    val dims = Dimens.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dims.screenHorizontalPadding, vertical = 4.dp),
        shape = RoundedCornerShape(dims.buttonCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dims.itemSpacingLarge, vertical = dims.itemSpacingLarge),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(holding.category.color)
            )

            Spacer(modifier = Modifier.width(dims.itemSpacingLarge))

            // Name + invested
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = holding.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Invested: ${formatCompact(holding.invested)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.width(dims.itemSpacingMedium))

            // Current value + P&L
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatCompact(holding.current),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${formatPnl(holding.pnl)} (${String.format("%.1f", holding.pnlPercent)}%)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = if (holding.isGain) InvestColors.GainGreen else InvestColors.LossRed,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

