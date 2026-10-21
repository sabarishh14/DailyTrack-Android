package com.example.dailytrack_mobile.presentation.screens.invest.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailytrack_mobile.presentation.components.FitText
import com.example.dailytrack_mobile.presentation.components.FullScreenPage
import com.example.dailytrack_mobile.presentation.screens.invest.ChartPoint
import com.example.dailytrack_mobile.presentation.screens.invest.HoldingsType
import com.example.dailytrack_mobile.presentation.screens.invest.InvestAction
import com.example.dailytrack_mobile.presentation.screens.invest.InvestColors
import com.example.dailytrack_mobile.presentation.screens.invest.InvestState
import com.example.dailytrack_mobile.presentation.screens.invest.InvestmentHolding
import com.example.dailytrack_mobile.presentation.screens.invest.formatCompact
import com.example.dailytrack_mobile.presentation.screens.invest.formatFull
import com.example.dailytrack_mobile.presentation.screens.invest.formatPnl
import com.example.dailytrack_mobile.presentation.screens.invest.formatPointDate
import com.example.dailytrack_mobile.presentation.screens.invest.periodLabel
import com.example.dailytrack_mobile.presentation.screens.invest.periodReturn
import com.example.dailytrack_mobile.presentation.util.Dimens
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// One stock or fund over time, opened from its row in the holdings list.
//
// The portfolio's own pieces at the size of one position: its value (following
// the chart while scrubbing), the chart with Value / Return, the time range,
// then what's held. A full-screen page like Holdings, so dragging along the
// chart can never close it.
// ─────────────────────────────────────────────────────────────────────────────

private val ChartHeight = 205.dp

@Composable
fun HoldingDetailPage(
    state: InvestState,
    onAction: (InvestAction) -> Unit
) {
    val holding = state.detailHolding ?: return
    val type = holding.historyType ?: return
    val dims = Dimens.current
    val close = { onAction(InvestAction.CloseHoldingDetail) }

    var isValueMode by rememberSaveable { mutableStateOf(true) }
    var selectedPoint by remember { mutableStateOf<ChartPoint?>(null) }
    LaunchedEffect(state.detailPoints) { selectedPoint = null }

    val points = state.detailPoints
    val (periodPnl, periodPercent) = remember(points, state.detailRange, holding) {
        periodReturn(points, state.detailRange, holding.invested, holding.current)
    }

    FullScreenPage(onDismiss = close) {
        PageHeader(
            title = holding.name,
            subtitle = "${if (type == HoldingsType.EQUITY) "Stock" else "Mutual fund"} · " +
                "${quantity(holding.quantity ?: 0.0)} ${type.unitWord}",
            onBack = close
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = listBottomPadding())
        ) {
            item(key = "value") {
                ValueHeader(
                    holding = holding,
                    selectedPoint = selectedPoint,
                    periodPnl = periodPnl,
                    periodPercent = periodPercent,
                    periodLabel = state.detailRange.periodLabel,
                    isValueMode = isValueMode,
                    onValueModeChanged = { isValueMode = it },
                    onClearSelection = { selectedPoint = null }
                )
            }

            item(key = "chart") {
                val chartModifier = Modifier
                    .fillMaxWidth()
                    .height(ChartHeight)
                    .padding(horizontal = dims.screenHorizontalPadding)
                when {
                    state.isDetailLoading -> ChartSkeleton(chartModifier)
                    state.detailError != null -> ChartMessage("Couldn't load its history", state.detailError, chartModifier)
                    state.detailHistory.size < 2 -> ChartMessage("Not enough history yet", "It fills in with each sync.", chartModifier)
                    else -> Column {
                        AdvancedChart(
                            points = points,
                            isValueMode = isValueMode,
                            isGain = periodPnl >= 0.0,
                            showXAxis = true,
                            selectedPoint = selectedPoint,
                            onPointSelected = { selectedPoint = it },
                            modifier = chartModifier
                        )
                        ChartLegend(isValueMode = isValueMode, isGain = periodPnl >= 0.0)
                    }
                }
            }

            item(key = "range") {
                Spacer(Modifier.height(dims.itemSpacingSmall))
                TimeRangeToggle(
                    selectedRange = state.detailRange,
                    onRangeSelected = { onAction(InvestAction.SelectDetailRange(it)) },
                    modifier = Modifier.padding(horizontal = dims.screenHorizontalPadding)
                )
            }

            item(key = "position") {
                PositionCard(
                    holding = holding,
                    type = type,
                    modifier = Modifier.padding(
                        start = dims.screenHorizontalPadding,
                        end = dims.screenHorizontalPadding,
                        top = 20.dp
                    )
                )
            }
        }
    }
}

/**
 * Its value and gain, as the portfolio header shows them: the period's gain at
 * rest, a day's value and total return while a point on the chart is held.
 */
@Composable
private fun ValueHeader(
    holding: InvestmentHolding,
    selectedPoint: ChartPoint?,
    periodPnl: Double,
    periodPercent: Double,
    periodLabel: String,
    isValueMode: Boolean,
    onValueModeChanged: (Boolean) -> Unit,
    onClearSelection: () -> Unit
) {
    val dims = Dimens.current
    val current = selectedPoint?.current?.toDouble() ?: holding.current
    val pnl = selectedPoint?.pnl?.toDouble() ?: periodPnl
    val percent = selectedPoint?.pnlPercent?.toDouble() ?: periodPercent
    val isGain = pnl >= 0.0
    val trendColor = if (isGain) InvestColors.GainGreen else InvestColors.LossRed

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = dims.screenHorizontalPadding + 4.dp,
                end = dims.screenHorizontalPadding + 4.dp,
                top = dims.itemSpacingSmall,
                bottom = dims.itemSpacingMedium
            )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = selectedPoint?.let { "VALUE ON ${formatPointDate(it.date).uppercase()}" } ?: "CURRENT VALUE",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = if (selectedPoint != null) 1.2.sp else 1.8.sp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (selectedPoint != null) ResetChip(onClearSelection)
            }
            Spacer(Modifier.width(8.dp))
            ValueReturnToggle(isValueMode = isValueMode, onModeChanged = onValueModeChanged)
        }

        Spacer(Modifier.height(4.dp))

        FitText(
            text = if (isValueMode) formatFull(current)
                else "${if (isGain) "+" else ""}${String.format(Locale.US, "%.2f", percent)}%",
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
            color = if (isValueMode) MaterialTheme.colorScheme.onBackground else trendColor
        )

        Spacer(Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = if (isGain) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                contentDescription = null,
                tint = trendColor,
                modifier = Modifier.size(dims.iconSizeSmall)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (isValueMode) "${formatPnl(pnl)} (${String.format(Locale.US, "%.1f", percent)}%)"
                    else "Val: ${formatCompact(current)} • Net: ${formatPnl(pnl)}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (isValueMode) trendColor else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(dims.itemSpacingMedium))
            Text(
                text = selectedPoint?.let { "Inv: ${formatCompact(it.invested.toDouble())}" } ?: periodLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun ResetChip(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
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
}

/** Units, cost and price today, then what it's made overall, whatever the range. */
@Composable
private fun PositionCard(holding: InvestmentHolding, type: HoldingsType, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        StatLabel("Position")
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(14.dp)
        ) {
            Row {
                DetailStat(type.unitWord.replaceFirstChar { it.uppercase() }, quantity(holding.quantity ?: 0.0), Modifier.weight(1f))
                DetailStat("Avg price", price(holding.averagePrice ?: 0.0), Modifier.weight(1f))
                DetailStat(type.priceLabel, price(holding.unitPrice ?: 0.0), Modifier.weight(1f))
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
            Row {
                DetailStat("Invested", rupees(holding.invested), Modifier.weight(1f))
                DetailStat("Returns", signedRupees(holding.pnl), Modifier.weight(1f), gainColor(holding.pnl))
                DetailStat("Return", signedPercent(holding.pnlPercent), Modifier.weight(1f), gainColor(holding.pnl))
            }
        }
    }
}

/** The chart's shape while its history loads, so nothing jumps when it lands. */
@Composable
private fun ChartSkeleton(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "HoldingChartSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "HoldingChartSkeletonAlpha"
    )
    Box(modifier = modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = 24.dp)
                .clip(RoundedCornerShape(16.dp))
                .alpha(alpha)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
    }
}

/** In the chart's place, at its size, so the page keeps its shape. */
@Composable
private fun ChartMessage(title: String, detail: String, modifier: Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
