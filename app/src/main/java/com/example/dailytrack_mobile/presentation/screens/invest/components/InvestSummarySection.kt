package com.example.dailytrack_mobile.presentation.screens.invest.components

import com.example.dailytrack_mobile.presentation.screens.invest.InvestCategory
import com.example.dailytrack_mobile.presentation.screens.invest.InvestColors
import com.example.dailytrack_mobile.presentation.screens.invest.InvestState
import com.example.dailytrack_mobile.presentation.screens.invest.InvestTab
import com.example.dailytrack_mobile.presentation.screens.invest.InvestmentHolding
import com.example.dailytrack_mobile.presentation.screens.invest.ChartTimeRange
import com.example.dailytrack_mobile.presentation.screens.invest.ChartPoint
import com.example.dailytrack_mobile.presentation.screens.invest.formatCompact
import com.example.dailytrack_mobile.presentation.screens.invest.formatExactCurrency
import com.example.dailytrack_mobile.presentation.screens.invest.formatPnl
import com.example.dailytrack_mobile.presentation.screens.invest.formatPnlExact

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
// Summary Row
// ─────────────────────────────────────────────────────────────────────────────
@Composable
internal fun SummaryRow(
    state: InvestState,
    selectedPoint: ChartPoint? = null
) {
    val dims = Dimens.current
    val isSelected = selectedPoint != null
    val displayCurrent = selectedPoint?.current?.toDouble() ?: state.periodCurrent
    val displayInvested = selectedPoint?.invested?.toDouble() ?: state.periodInvested
    val displayPnl = selectedPoint?.let { (it.current - it.invested).toDouble() } ?: state.periodPnl
    val displayPnlPercent = selectedPoint?.pnlPercent?.toDouble() ?: state.periodPnlPercent
    val isGain = displayPnl >= 0.0

    var showAbsoluteAmounts by rememberSaveable { mutableStateOf(false) }

    val pnlLabel = when {
        isSelected -> "POINT P&L"
        state.selectedTimeRange == ChartTimeRange.ALL -> "TOTAL P&L"
        else -> "${state.selectedTimeRange.label} RETURN"
    }

    val investedValue = if (showAbsoluteAmounts) formatExactCurrency(displayInvested) else formatCompact(displayInvested)
    val currentValue = if (showAbsoluteAmounts) formatExactCurrency(displayCurrent) else formatCompact(displayCurrent)
    val pnlValue = if (showAbsoluteAmounts) formatPnlExact(displayPnl) else formatPnl(displayPnl)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = dims.screenHorizontalPadding, vertical = dims.itemSpacingMedium),
        horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)
    ) {
        SummaryMiniCard(
            label = "INVESTED",
            value = investedValue,
            subValue = "Cost basis",
            isExact = showAbsoluteAmounts,
            onClick = { showAbsoluteAmounts = !showAbsoluteAmounts },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
        SummaryMiniCard(
            label = "CURRENT",
            value = currentValue,
            subValue = "Market value",
            isExact = showAbsoluteAmounts,
            onClick = { showAbsoluteAmounts = !showAbsoluteAmounts },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
        SummaryMiniCard(
            label = pnlLabel,
            value = pnlValue,
            subValue = "${if (isGain) "+" else ""}${String.format(java.util.Locale.US, "%.1f", displayPnlPercent)}%",
            valueColor = if (isGain) InvestColors.GainGreen else InvestColors.LossRed,
            isExact = showAbsoluteAmounts,
            onClick = { showAbsoluteAmounts = !showAbsoluteAmounts },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
    }
}

@Composable
internal fun SummaryMiniCard(
    label: String,
    value: String,
    subValue: String? = null,
    valueColor: Color? = null,
    isExact: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val dims = Dimens.current
    val valueFontSize = remember(value.length, isExact) {
        if (isExact) {
            when {
                value.length <= 8 -> 14.sp
                value.length <= 11 -> 12.sp
                value.length <= 14 -> 10.5.sp
                else -> 9.5.sp
            }
        } else {
            15.sp
        }
    }

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(dims.buttonCornerRadius))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(dims.buttonCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = dims.miniCardPaddingHorizontal,
                    vertical = dims.miniCardPaddingVertical
                ),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp,
                        fontSize = 10.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(dims.itemSpacingSmall))
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = valueFontSize
                    ),
                    color = valueColor ?: MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            if (subValue != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subValue,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.5.sp
                    ),
                    color = valueColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

