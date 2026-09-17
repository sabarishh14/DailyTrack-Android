package com.example.dailytrack_mobile.presentation.screens.invest.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailytrack_mobile.presentation.screens.invest.HoldingChange
import com.example.dailytrack_mobile.presentation.screens.invest.HoldingComparison
import com.example.dailytrack_mobile.presentation.screens.invest.HoldingSnapshotRow
import com.example.dailytrack_mobile.presentation.screens.invest.HoldingsType
import com.example.dailytrack_mobile.presentation.screens.invest.InvestAction
import com.example.dailytrack_mobile.presentation.screens.invest.InvestColors
import com.example.dailytrack_mobile.presentation.screens.invest.InvestState
import com.example.dailytrack_mobile.presentation.screens.invest.formatExactCurrency
import com.example.dailytrack_mobile.presentation.screens.invest.formatPointDate
import com.example.dailytrack_mobile.presentation.util.Dimens
import java.text.DecimalFormat
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.round
import com.example.dailytrack_mobile.presentation.components.rememberSheetHeight

// ─────────────────────────────────────────────────────────────────────────────
// Holdings on a date, optionally compared with another date.
//
// Mirrors the web app's snapshot view: every number sits under a short label
// (Invested / Current / Returns, Qty / LTP), and comparisons show the later
// value with a ▲/▼ badge for the change. Comparisons always read earlier →
// later, whichever date was picked first.
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoldingsSnapshotSheet(
    state: InvestState,
    onAction: (InvestAction) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dims = Dimens.current
    val snapshotDate = state.snapshotDate ?: return
    val type = state.snapshotType

    ModalBottomSheet(
        onDismissRequest = { onAction(InvestAction.CloseHoldingsSnapshot) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(rememberSheetHeight(0.9f))
        ) {
            SheetHeader(
                subtitle = if (state.isComparing) {
                    "${formatShortDate(state.comparisonFromDate.orEmpty())} → ${formatShortDate(state.comparisonToDate.orEmpty())}"
                } else {
                    formatPointDate(snapshotDate)
                },
                onClose = { onAction(InvestAction.CloseHoldingsSnapshot) }
            )

            Spacer(Modifier.height(10.dp))

            AssetTypeToggle(
                selected = type,
                onSelect = { onAction(InvestAction.SelectSnapshotType(it)) },
                modifier = Modifier.padding(horizontal = dims.screenHorizontalPadding)
            )

            Spacer(Modifier.height(10.dp))

            CompareControl(
                state = state,
                onAction = onAction,
                modifier = Modifier.padding(horizontal = dims.screenHorizontalPadding)
            )

            Spacer(Modifier.height(12.dp))

            val isBusy = state.isSnapshotLoading || (state.isComparing && state.isCompareLoading)

            when {
                isBusy -> SkeletonContent(Modifier.padding(horizontal = dims.screenHorizontalPadding))

                state.snapshotError != null -> SheetMessage(
                    title = "Couldn't load holdings",
                    detail = state.snapshotError
                )

                state.isComparing -> ComparisonContent(state, type)

                state.snapshotHoldings.isEmpty() -> SheetMessage(
                    title = "No ${type.label.lowercase()} on this date",
                    detail = "Try another date or asset type."
                )

                else -> SnapshotContent(state, type)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Single date
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.SnapshotContent(state: InvestState, type: HoldingsType) {
    val dims = Dimens.current
    val holdings = remember(state.snapshotHoldings) {
        state.snapshotHoldings.sortedByDescending { it.current }
    }

    LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(
            start = dims.screenHorizontalPadding,
            end = dims.screenHorizontalPadding,
            bottom = 28.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "summary") {
            CardSurface(highlight = true) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Stat("Invested", rupees(state.snapshotTotalInvested), Modifier.weight(1f))
                    Stat("Current", rupees(state.snapshotTotalValue), Modifier.weight(1f), bold = true)
                    Stat(
                        label = "Returns",
                        value = signedRupees(state.snapshotTotalReturn),
                        modifier = Modifier.weight(1f),
                        valueColor = gainColor(state.snapshotTotalReturn),
                        detail = signedPercent(state.snapshotTotalReturnPercent)
                    )
                }
            }
        }

        item(key = "count") {
            CountLine("${holdings.size} ${type.holdingNoun(holdings.size)}")
        }

        items(holdings, key = { it.symbol }) { holding ->
            SnapshotRow(holding, type)
        }
    }
}

@Composable
private fun SnapshotRow(holding: HoldingSnapshotRow, type: HoldingsType) {
    CardSurface {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                SymbolText(holding.symbol)
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${quantity(holding.quantity)} units · Avg ${price(holding.averagePrice)} · " +
                        "${type.priceLabel} ${price(holding.unitPrice)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(10.dp))
            TintedBadge(signedPercent(holding.pnlPercent), gainColor(holding.pnl))
        }

        RowDivider()

        Row(modifier = Modifier.fillMaxWidth()) {
            Stat("Invested", rupees(holding.invested), Modifier.weight(1f))
            Stat("Current", rupees(holding.current), Modifier.weight(1f), bold = true)
            Stat("Returns", signedRupees(holding.pnl), Modifier.weight(1f), valueColor = gainColor(holding.pnl))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Two dates
//
// Summary card, filter chips, then one card per holding: a header with name,
// tag and returns over Quantity / LTP / Invested / Current tiles, each with its
// change and % — the same data as the web app's mobile cards.
// ─────────────────────────────────────────────────────────────────────────────

private enum class CompareFilter(val label: String) {
    ALL("All"), GAINERS("Gainers"), LOSERS("Losers"), NEW("New"), EXITED("Exited")
}

private fun HoldingComparison.matches(filter: CompareFilter): Boolean = when (filter) {
    CompareFilter.ALL -> true
    CompareFilter.GAINERS -> change == HoldingChange.HELD && pricePercent > 0.0001
    CompareFilter.LOSERS -> change == HoldingChange.HELD && pricePercent < -0.0001
    CompareFilter.NEW -> change == HoldingChange.NEW
    CompareFilter.EXITED -> change == HoldingChange.EXITED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.ComparisonContent(state: InvestState, type: HoldingsType) {
    val dims = Dimens.current
    val comparison = state.holdingsComparison

    if (comparison.isEmpty()) {
        SheetMessage(title = "Nothing to compare", detail = "Neither date has any ${type.label.lowercase()}.")
        return
    }

    var filter by remember(state.compareDate, state.snapshotType) { mutableStateOf(CompareFilter.ALL) }
    val counts = remember(comparison) {
        CompareFilter.entries.associateWith { f -> comparison.count { it.matches(f) } }
    }
    val visible = remember(comparison, filter) { comparison.filter { it.matches(filter) } }
    val fromLabel = formatShortDate(state.comparisonFromDate.orEmpty())
    val filters = remember(counts) {
        CompareFilter.entries.filter { it == CompareFilter.ALL || (counts[it] ?: 0) > 0 }
    }

    LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(
            start = dims.screenHorizontalPadding,
            end = dims.screenHorizontalPadding,
            bottom = 28.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "summary") {
            ComparisonSummary(state, fromLabel)
            Spacer(Modifier.height(4.dp))
        }

        item(key = "filters") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filters, key = { it.name }) { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text("${f.label} ${counts[f] ?: 0}") },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        }

        items(visible, key = { it.symbol }) { item ->
            ComparisonCard(item = item, type = type)
        }
    }
}

@Composable
private fun ComparisonSummary(state: InvestState, fromLabel: String) {
    val fromValue = state.comparisonFromValue
    val toValue = state.comparisonToValue
    val valueDelta = toValue - fromValue

    val fromInvested = state.comparisonFromInvested
    val toInvested = state.comparisonToInvested
    val fromReturns = fromValue - fromInvested
    val toReturns = toValue - toInvested
    val fromReturnPct = percentOf(fromReturns, fromInvested)
    val toReturnPct = percentOf(toReturns, toInvested)

    CardSurface(highlight = true) {
        StatLabel("Current value")
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = rupees(toValue),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            ChangePill(valueDelta, percentOf(valueDelta, fromValue))
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = "${signedRupees(valueDelta)} since $fromLabel · was ${rupees(fromValue)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        RowDivider(top = 14.dp, bottom = 12.dp)

        MetricLine(
            label = "Invested",
            then = rupees(fromInvested),
            now = rupees(toInvested),
            change = "${signedRupees(toInvested - fromInvested)} · ${signedPercent(percentOf(toInvested - fromInvested, fromInvested))}",
            changeColor = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(12.dp))
        MetricLine(
            label = "Returns",
            then = "${signedRupees(fromReturns)} (${signedPercent(fromReturnPct)})",
            now = "${signedRupees(toReturns)} (${signedPercent(toReturnPct)})",
            change = "${signedRupees(toReturns - fromReturns)} · ${signedNumber(toReturnPct - fromReturnPct)} pts",
            changeColor = gainColor(toReturns - fromReturns)
        )
    }
}

/** Label and change on one line; the then → now values quietly beneath. */
@Composable
private fun MetricLine(label: String, then: String, now: String, change: String, changeColor: Color) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = change,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = changeColor
            )
        }
        Spacer(Modifier.height(1.dp))
        Text(
            text = "$then → $now",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * One holding across the two dates — the web app's mobile card, laid out as a
 * header (name, tag, returns) over a 2×2 grid of tiles. Each tile shows the
 * later value with its change, and a % wherever a % means something.
 */
@Composable
private fun ComparisonCard(item: HoldingComparison, type: HoldingsType) {
    val isExited = item.change == HoldingChange.EXITED
    val unitWord = if (type == HoldingsType.EQUITY) "shares" else "units"

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── Header ──────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    SymbolText(item.symbol)
                    if (item.change != HoldingChange.HELD) {
                        Spacer(Modifier.height(6.dp))
                        when (item.change) {
                            HoldingChange.NEW -> SmallTag("New position", InvestColors.GainGreen)
                            HoldingChange.EXITED -> SmallTag("Exited", InvestColors.LossRed)
                            HoldingChange.HELD -> Unit
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    StatLabel("Returns")
                    Spacer(Modifier.height(2.dp))
                    if (isExited) {
                        Text(
                            text = "—",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = signedRupees(item.returns),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = gainColor(item.returns)
                        )
                        Text(
                            text = signedPercent(item.returnPercent),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = gainColor(item.returns)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── 2×2 tiles ───────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile(
                    label = "Quantity",
                    value = quantity(item.quantity),
                    change = changeText(item.quantityDelta, isCurrency = false, suffix = " $unitWord"),
                    changeColor = deltaColor(item.quantityDelta, 0.001),
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = type.priceLabel,
                    value = if (isExited) price(item.fromPrice) else price(item.unitPrice),
                    change = if (item.change == HoldingChange.HELD) {
                        changeText(item.unitPriceDelta, isCurrency = true, percent = item.pricePercent, pricePrecision = true)
                    } else null,
                    changeColor = deltaColor(item.unitPriceDelta, 0.005),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile(
                    label = "Invested",
                    value = rupees(item.invested),
                    change = changeText(
                        item.investedDelta,
                        isCurrency = true,
                        percent = if (item.change == HoldingChange.HELD) percentOf(item.investedDelta, item.from?.invested ?: 0.0) else null
                    ),
                    changeColor = deltaColor(item.investedDelta, 0.5),
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "Current",
                    value = rupees(item.current),
                    change = changeText(
                        item.currentDelta,
                        isCurrency = true,
                        percent = if (item.change == HoldingChange.HELD) item.currentPercent else null
                    ),
                    changeColor = deltaColor(item.currentDelta, 0.5),
                    emphasised = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    change: String?,
    changeColor: Color,
    modifier: Modifier = Modifier,
    emphasised: Boolean = false
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        StatLabel(label)
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (emphasised) FontWeight.Bold else FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = change ?: " ",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = changeColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** "▲ ₹250 · +11.9%", "▼ 3 shares", or "No change". */
private fun changeText(
    delta: Double,
    isCurrency: Boolean,
    percent: Double? = null,
    suffix: String = "",
    pricePrecision: Boolean = false
): String {
    val threshold = if (isCurrency) (if (pricePrecision) 0.005 else 0.5) else 0.001
    if (abs(delta) < threshold) return "No change"
    val arrow = if (delta > 0) "▲" else "▼"
    val amount = when {
        !isCurrency -> quantity(abs(delta)) + suffix
        pricePrecision -> price(abs(delta))
        else -> rupees(abs(delta))
    }
    return if (percent != null) "$arrow $amount · ${signedPercent(percent)}" else "$arrow $amount"
}

@Composable
private fun deltaColor(delta: Double, threshold: Double): Color = when {
    abs(delta) < threshold -> MaterialTheme.colorScheme.onSurfaceVariant
    delta > 0 -> InvestColors.GainGreen
    else -> InvestColors.LossRed
}

@Composable
private fun ChangePill(delta: Double, percent: Double, compact: Boolean = false) {
    val color = gainColor(delta)
    val arrow = when {
        abs(percent) < 0.05 -> ""
        delta > 0 -> "▲ "
        else -> "▼ "
    }
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.13f)) {
        Text(
            text = "$arrow${oneDecimal(abs(percent))}%",
            style = (if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge)
                .copy(fontWeight = FontWeight.Bold),
            color = color,
            modifier = Modifier.padding(
                horizontal = if (compact) 6.dp else 9.dp,
                vertical = if (compact) 2.dp else 4.dp
            )
        )
    }
}

@Composable
private fun SmallTag(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(5.dp), color = color.copy(alpha = 0.14f)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 10.sp),
            color = color,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Controls
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SheetHeader(subtitle: String, onClose: () -> Unit) {
    val dims = Dimens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dims.screenHorizontalPadding, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Holdings",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AssetTypeToggle(
    selected: HoldingsType,
    onSelect: (HoldingsType) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            HoldingsType.entries.forEach { type ->
                val isSelected = type == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(type) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = type.label,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CompareControl(
    state: InvestState,
    onAction: (InvestAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val snapshotDate = state.snapshotDate ?: return
    val candidateDates = remember(state.availableSnapshotDates, snapshotDate) {
        state.availableSnapshotDates.filter { it != snapshotDate }
    }
    if (candidateDates.isEmpty()) return

    val expanded = state.isComparePickerOpen

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (state.isComparing) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                )
                .clickable { onAction(InvestAction.SetComparePickerOpen(!expanded)) }
                .padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                contentDescription = null,
                tint = if (state.isComparing) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = state.compareDate?.let { "Compared with ${formatShortDate(it)}" } ?: "Compare with…",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (state.isComparing) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (state.isComparing) {
                TextButton(onClick = { onAction(InvestAction.SelectCompareDate(null)) }) {
                    Text("Clear", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Hide dates" else "Show dates",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(20.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(180)) + expandVertically(tween(220, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(180))
        ) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                val presets = remember(candidateDates, snapshotDate) { buildPresets(snapshotDate, candidateDates) }
                if (presets.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(presets, key = { it.first }) { (label, date) ->
                            DateChip(label, formatShortDate(date), state.compareDate == date) {
                                onAction(InvestAction.SelectCompareDate(date))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(candidateDates, key = { it }) { date ->
                        DateChip(formatShortDate(date), null, state.compareDate == date) {
                            onAction(InvestAction.SelectCompareDate(date))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateChip(label: String, caption: String?, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                ),
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
            if (caption != null) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Building blocks
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CardSurface(highlight: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (highlight) MaterialTheme.colorScheme.surfaceContainerHigh
                else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), content = content)
    }
}

@Composable
private fun RowDivider(top: Dp = 12.dp, bottom: Dp = 12.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(top = top, bottom = bottom),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )
}

@Composable
private fun StatLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun Stat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    bold: Boolean = false,
    detail: String? = null
) {
    Column(modifier = modifier) {
        StatLabel(label)
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold
            ),
            color = valueColor,
            maxLines = 1
        )
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = valueColor
            )
        }
    }
}



@Composable
private fun TintedBadge(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.13f)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = color,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun SymbolText(symbol: String, modifier: Modifier = Modifier) {
    Text(
        text = symbol,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

@Composable
private fun CountLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp)
    )
}

/** Placeholders shaped like the real content, so nothing jumps when data lands. */
@Composable
private fun SkeletonContent(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "HoldingsSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "HoldingsSkeletonAlpha"
    )
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(14.dp))
                .alpha(alpha)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
        Spacer(Modifier.height(20.dp))
        repeat(4) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .alpha(alpha)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
            )
        }
    }
}

@Composable
private fun SheetMessage(title: String, detail: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Inbox,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(34.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun gainColor(amount: Double): Color = when {
    abs(amount) < 0.5 -> MaterialTheme.colorScheme.onSurfaceVariant
    amount > 0 -> InvestColors.GainGreen
    else -> InvestColors.LossRed
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private val HoldingsType.priceLabel: String
    get() = when (this) {
        HoldingsType.EQUITY -> "LTP"
        HoldingsType.MUTUAL_FUNDS -> "NAV"
    }

private fun HoldingsType.holdingNoun(count: Int): String = when (this) {
    HoldingsType.EQUITY -> if (count == 1) "stock" else "stocks"
    HoldingsType.MUTUAL_FUNDS -> if (count == 1) "fund" else "funds"
}

private val quantityFormat = DecimalFormat("#,##0.##")
private val priceFormat = DecimalFormat("#,##0.00")
private val oneDecimalFormat = DecimalFormat("0.0")

private fun quantity(value: Double): String = quantityFormat.format(value)

/** Per-unit prices keep paise; they matter at that scale. */
private fun price(value: Double): String = "₹${priceFormat.format(value)}"

/** Whole rupees with Indian grouping for position totals. */
private fun rupees(amount: Double): String = formatExactCurrency(round(amount))

private fun signedRupees(amount: Double): String =
    if (round(amount) >= 0) "+${rupees(amount)}" else rupees(amount)

private fun oneDecimal(value: Double): String = oneDecimalFormat.format(value)

private fun signedNumber(value: Double): String =
    (if (value >= 0) "+" else "") + oneDecimalFormat.format(value)

private fun percentOf(part: Double, whole: Double): Double =
    if (whole == 0.0) 0.0 else part / whole * 100.0

private fun signedPercent(percent: Double): String =
    "${if (percent >= 0) "+" else ""}${oneDecimalFormat.format(percent)}%"

/** "12 Sep" for chips; the year only when it isn't this one. */
private fun formatShortDate(dateStr: String): String = try {
    val date = LocalDate.parse(dateStr)
    val month = date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    if (date.year == LocalDate.now().year) "${date.dayOfMonth} $month"
    else "${date.dayOfMonth} $month ${date.year % 100}"
} catch (e: Exception) {
    dateStr
}

/**
 * Quick comparison points, each resolved to the newest snapshot at or before
 * that offset, since snapshots aren't daily. The chip shows the real date too.
 */
private fun buildPresets(baseDate: String, candidates: List<String>): List<Pair<String, String>> {
    val base = try {
        LocalDate.parse(baseDate)
    } catch (e: Exception) {
        return emptyList()
    }
    val parsed = candidates.mapNotNull { raw ->
        try {
            LocalDate.parse(raw) to raw
        } catch (e: Exception) {
            null
        }
    }
    if (parsed.isEmpty()) return emptyList()

    val offsets = listOf(
        "1W ago" to base.minusWeeks(1),
        "1M ago" to base.minusMonths(1),
        "3M ago" to base.minusMonths(3),
        "6M ago" to base.minusMonths(6),
        "1Y ago" to base.minusYears(1)
    )
    return offsets.mapNotNull { (label, target) ->
        parsed.filter { !it.first.isAfter(target) }.maxByOrNull { it.first }?.let { label to it.second }
    }.distinctBy { it.second }
}
