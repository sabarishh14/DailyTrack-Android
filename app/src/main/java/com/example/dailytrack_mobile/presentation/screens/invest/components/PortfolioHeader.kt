package com.example.dailytrack_mobile.presentation.screens.invest.components

import com.example.dailytrack_mobile.presentation.screens.invest.InvestCategory
import com.example.dailytrack_mobile.presentation.screens.invest.InvestColors
import com.example.dailytrack_mobile.presentation.screens.invest.InvestState
import com.example.dailytrack_mobile.presentation.screens.invest.InvestTab
import com.example.dailytrack_mobile.presentation.screens.invest.InvestmentHolding
import com.example.dailytrack_mobile.presentation.screens.invest.ChartTimeRange
import com.example.dailytrack_mobile.presentation.screens.invest.ChartPoint
import com.example.dailytrack_mobile.presentation.screens.invest.formatCompact
import com.example.dailytrack_mobile.presentation.screens.invest.formatFull
import com.example.dailytrack_mobile.presentation.screens.invest.formatPnl
import com.example.dailytrack_mobile.presentation.screens.invest.formatPointDate

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
// Portfolio Header
// ─────────────────────────────────────────────────────────────────────────────
@Composable
internal fun PortfolioHeader(
    state: InvestState,
    isValueMode: Boolean,
    onValueModeChanged: (Boolean) -> Unit,
    selectedPoint: ChartPoint? = null,
    onClearSelection: () -> Unit = {},
    onOpenCategorySettings: () -> Unit = {}
) {
    val dims = Dimens.current
    val isSelected = selectedPoint != null
    val displayCurrent = selectedPoint?.current?.toDouble() ?: state.periodCurrent
    val displayInvested = selectedPoint?.invested?.toDouble() ?: state.periodInvested
    val displayPnl = selectedPoint?.let { (it.current - it.invested).toDouble() } ?: state.periodPnl
    val displayPnlPercent = selectedPoint?.pnlPercent?.toDouble() ?: state.periodPnlPercent
    val isGain = displayPnl >= 0.0

    val headerTitle = when {
        isSelected -> "VALUE ON ${formatPointDate(selectedPoint!!.date).uppercase()}"
        state.selectedTab == InvestTab.OVERVIEW -> "PORTFOLIO VALUE"
        else -> "${state.selectedTab.label.uppercase()} VALUE"
    }

    val periodSubtext = when {
        isSelected -> "Inv: ${formatCompact(displayInvested)}"
        else -> state.periodLabel
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = dims.screenHorizontalPadding + 4.dp,
                end = dims.screenHorizontalPadding + 4.dp,
                top = dims.screenTopPadding,
                bottom = dims.itemSpacingMedium
            )
    ) {
        // Top Row: Title + Badges on Left, Action Controls on Right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = headerTitle,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = if (isSelected) 1.2.sp else 1.8.sp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (isSelected) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.clickable { onClearSelection() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "Reset",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Reset",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = state.selectedTimeRange.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp,
                                letterSpacing = 0.5.sp
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }

                    if (state.selectedTab == InvestTab.OVERVIEW && state.isAnyCategoryHidden) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            modifier = Modifier.clickable { onOpenCategorySettings() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "${state.visibleCategoriesCount}/${state.totalCategoriesCount}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 10.sp,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action Controls: Category Settings + Value/Return Toggle
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(dims.buttonCornerRadius),
                    color = if (state.isAnyCategoryHidden) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
                    border = BorderStroke(
                        0.5.dp,
                        if (state.isAnyCategoryHidden) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(dims.buttonCornerRadius))
                        .clickable { onOpenCategorySettings() }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Configure Net Worth Categories",
                            tint = if (state.isAnyCategoryHidden) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        if (state.isAnyCategoryHidden) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 3.dp, end = 3.dp)
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }

                ValueReturnToggle(
                    isValueMode = isValueMode,
                    onModeChanged = onValueModeChanged
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Hero value (full width)
        val heroValue = if (isValueMode) {
            formatFull(displayCurrent)
        } else {
            "${if (isGain) "+" else ""}${String.format(java.util.Locale.US, "%.2f", displayPnlPercent)}%"
        }

        Text(
            text = heroValue,
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.ExtraBold
            ),
            color = if (isValueMode) MaterialTheme.colorScheme.onBackground else if (isGain) InvestColors.GainGreen else InvestColors.LossRed
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Subline row (full width, responsive)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = if (isGain) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                contentDescription = null,
                tint = if (isGain) InvestColors.GainGreen else InvestColors.LossRed,
                modifier = Modifier.size(dims.iconSizeSmall)
            )
            Spacer(modifier = Modifier.width(4.dp))
            val sublineMetrics = if (isValueMode) {
                "${formatPnl(displayPnl)} (${String.format(java.util.Locale.US, "%.1f", displayPnlPercent)}%)"
            } else {
                "Val: ${formatCompact(displayCurrent)} • Net: ${formatPnl(displayPnl)}"
            }
            Text(
                text = sublineMetrics,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (isValueMode) (if (isGain) InvestColors.GainGreen else InvestColors.LossRed) else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(dims.itemSpacingMedium))
            Text(
                text = periodSubtext,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
