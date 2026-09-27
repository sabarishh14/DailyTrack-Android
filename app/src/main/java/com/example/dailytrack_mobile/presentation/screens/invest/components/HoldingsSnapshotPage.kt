package com.example.dailytrack_mobile.presentation.screens.invest.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
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
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
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

// ─────────────────────────────────────────────────────────────────────────────
// Holdings on a date, optionally compared with another date.
//
// A full-screen page rather than a bottom sheet: it's a long list with its own
// filters, and a sheet closes whenever a scroll back to the top carries on into
// a downward drag. Here only Back or the arrow closes it.
//
// A summary card up top, then one compact row per holding: name, a line of
// context, its value and a single change pill. Tapping a row opens the detail —
// for a comparison, a small then / now / change table. Comparisons always read
// earlier → later, whichever date was picked first.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HoldingsSnapshotPage(
    state: InvestState,
    onAction: (InvestAction) -> Unit
) {
    val dims = Dimens.current
    val snapshotDate = state.snapshotDate ?: return
    val type = state.snapshotType
    val close = { onAction(InvestAction.CloseHoldingsSnapshot) }

    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val pageColor = MaterialTheme.colorScheme.surface
        MatchSystemBarsTo(pageColor)

        val shown = remember { MutableTransitionState(false) }.apply { targetState = true }
        AnimatedVisibility(
            visibleState = shown,
            enter = fadeIn(tween(180)) + slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it / 12 }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(pageColor)
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            ) {
                PageHeader(
                    subtitle = if (state.isComparing) {
                        "${formatShortDate(state.comparisonFromDate.orEmpty())} → ${formatShortDate(state.comparisonToDate.orEmpty())}"
                    } else {
                        formatPointDate(snapshotDate)
                    },
                    onBack = close
                )

                Spacer(Modifier.height(8.dp))

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

                Spacer(Modifier.height(14.dp))

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
}

/** The page draws behind the system bars, so their icons must suit its colour. */
@Composable
private fun MatchSystemBarsTo(pageColor: Color) {
    val view = LocalView.current
    val lightBars = pageColor.luminance() > 0.5f
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightBars
            isAppearanceLightNavigationBars = lightBars
        }
    }
}

/** Lists end clear of the gesture bar, which the page now draws behind. */
@Composable
private fun listBottomPadding(): Dp =
    28.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

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
            bottom = listBottomPadding()
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "summary") {
            HeroCard(tint = gainColor(state.snapshotTotalReturn)) {
                HeroLabel("Portfolio value")
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeroValue(rupees(state.snapshotTotalValue), Modifier.weight(1f))
                    ChangePill(state.snapshotTotalReturnPercent, large = true)
                }
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    HeroStat("Invested", rupees(state.snapshotTotalInvested), Modifier.weight(1f))
                    HeroDivider()
                    HeroStat(
                        label = "Returns",
                        value = signedRupees(state.snapshotTotalReturn),
                        valueColor = gainColor(state.snapshotTotalReturn),
                        lines = listOf(signedPercent(state.snapshotTotalReturnPercent) to gainColor(state.snapshotTotalReturn)),
                        modifier = Modifier.weight(1f)
                    )
                    HeroDivider()
                    HeroStat(
                        label = "Holdings",
                        value = "${holdings.size}",
                        lines = listOf(type.holdingNoun(holdings.size) to MaterialTheme.colorScheme.onSurfaceVariant),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item(key = "hint") { ListHint("Sorted by value · % is total return · tap for details") }

        items(holdings, key = { it.symbol }) { holding ->
            SnapshotRow(holding, type)
        }
    }
}

@Composable
private fun SnapshotRow(holding: HoldingSnapshotRow, type: HoldingsType) {
    var expanded by rememberSaveable(holding.symbol) { mutableStateOf(false) }
    HoldingCard(onClick = { expanded = !expanded }) {
        HoldingHeader(
            symbol = holding.symbol,
            subline = "${quantity(holding.quantity)} ${type.unitWord} · ${type.priceLabel} ${price(holding.unitPrice)}",
            value = rupees(holding.current),
            expanded = expanded
        ) {
            ChangePill(holding.pnlPercent)
        }
        ExpandedDetail(expanded) {
            Row {
                DetailStat("Avg price", price(holding.averagePrice), Modifier.weight(1f))
                DetailStat("Invested", rupees(holding.invested), Modifier.weight(1f))
                DetailStat("Returns", signedRupees(holding.pnl), Modifier.weight(1f), gainColor(holding.pnl))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Two dates
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

@Composable
private fun CompareFilter.accent(): Color = when (this) {
    CompareFilter.ALL -> MaterialTheme.colorScheme.primary
    CompareFilter.GAINERS -> InvestColors.GainGreen
    CompareFilter.LOSERS -> InvestColors.LossRed
    CompareFilter.NEW -> MaterialTheme.colorScheme.tertiary
    CompareFilter.EXITED -> MaterialTheme.colorScheme.onSurfaceVariant
}

@OptIn(ExperimentalFoundationApi::class)
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
    val toLabel = formatShortDate(state.comparisonToDate.orEmpty())
    val filters = remember(counts) {
        CompareFilter.entries.filter { it == CompareFilter.ALL || (counts[it] ?: 0) > 0 }
    }

    LazyColumn(
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(
            start = dims.screenHorizontalPadding,
            end = dims.screenHorizontalPadding,
            bottom = listBottomPadding()
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "summary") {
            ComparisonHero(state, counts, fromLabel, toLabel)
        }

        // Stays pinned while scrolling, so switching filter never means scrolling back up.
        stickyHeader(key = "filters") {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(top = 6.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filters, key = { it.name }) { f ->
                    FilterPill(f.label, counts[f] ?: 0, selected = filter == f, accent = f.accent()) { filter = f }
                }
            }
        }

        item(key = "hint") { ListHint("% is the ${type.priceLabel} change since $fromLabel · tap for details") }

        items(visible, key = { it.symbol }) { item ->
            ComparisonRow(item = item, type = type, fromLabel = fromLabel, toLabel = toLabel)
        }
    }
}

/** Where the whole portfolio went between the two dates. */
@Composable
private fun ComparisonHero(
    state: InvestState,
    counts: Map<CompareFilter, Int>,
    fromLabel: String,
    toLabel: String
) {
    val fromValue = state.comparisonFromValue
    val toValue = state.comparisonToValue
    val valueDelta = toValue - fromValue

    val fromInvested = state.comparisonFromInvested
    val toInvested = state.comparisonToInvested
    val fromReturns = fromValue - fromInvested
    val toReturns = toValue - toInvested
    val fromReturnPct = percentOf(fromReturns, fromInvested)
    val toReturnPct = percentOf(toReturns, toInvested)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    val newCount = counts[CompareFilter.NEW] ?: 0
    val exitedCount = counts[CompareFilter.EXITED] ?: 0
    val heldNow = (counts[CompareFilter.ALL] ?: 0) - exitedCount

    HeroCard(tint = gainColor(valueDelta)) {
        DateRange(fromLabel, toLabel)
        Spacer(Modifier.height(16.dp))
        HeroLabel("Portfolio value")
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeroValue(rupees(toValue), Modifier.weight(1f))
            ChangePill(percentOf(valueDelta, fromValue), large = true)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = "${signedRupees(valueDelta)} since $fromLabel · was ${rupees(fromValue)}",
            style = MaterialTheme.typography.bodySmall,
            color = muted
        )

        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            HeroStat(
                label = "Invested",
                value = rupees(toInvested),
                lines = listOf(
                    signedRupees(toInvested - fromInvested) to MaterialTheme.colorScheme.onSurface,
                    "was ${rupees(fromInvested)}" to muted
                ),
                modifier = Modifier.weight(1f)
            )
            HeroDivider()
            HeroStat(
                label = "Returns",
                value = signedRupees(toReturns),
                valueColor = gainColor(toReturns),
                lines = listOf(
                    "${signedPercent(toReturnPct)} · ${signedNumber(toReturnPct - fromReturnPct)} pts" to gainColor(toReturns - fromReturns),
                    "${signedRupees(toReturns - fromReturns)} change" to gainColor(toReturns - fromReturns)
                ),
                modifier = Modifier.weight(1f)
            )
            HeroDivider()
            HeroStat(
                label = "Positions",
                value = "$heldNow",
                lines = buildList {
                    if (newCount > 0) add("+$newCount new" to MaterialTheme.colorScheme.tertiary)
                    if (exitedCount > 0) add("-$exitedCount exited" to muted)
                    if (isEmpty()) add("no change" to muted)
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DateRange(fromLabel: String, toLabel: String) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        DatePill(fromLabel)
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            HorizontalDivider(color = colors.outlineVariant)
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "to",
                tint = colors.onSurfaceVariant,
                modifier = Modifier
                    .background(colors.surfaceContainerHigh, CircleShape)
                    .padding(4.dp)
                    .size(14.dp)
            )
        }
        DatePill(toLabel)
    }
}

@Composable
private fun DatePill(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

/**
 * One holding across the two dates: the later value with the price move, and
 * on tap a then / now / change table of the same numbers the web app shows.
 */
@Composable
private fun ComparisonRow(item: HoldingComparison, type: HoldingsType, fromLabel: String, toLabel: String) {
    var expanded by rememberSaveable(item.symbol) { mutableStateOf(false) }
    val isExited = item.change == HoldingChange.EXITED

    HoldingCard(onClick = { expanded = !expanded }) {
        HoldingHeader(
            symbol = item.symbol,
            subline = compareSubline(item, type),
            value = rupees(if (isExited) item.fromValue else item.current),
            expanded = expanded,
            muted = isExited
        ) {
            when (item.change) {
                HoldingChange.HELD -> ChangePill(item.pricePercent)
                HoldingChange.NEW -> StatusTag("New", MaterialTheme.colorScheme.tertiary)
                HoldingChange.EXITED -> StatusTag("Exited", InvestColors.LossRed)
            }
        }
        ExpandedDetail(expanded) {
            ChangeTable(item, type, fromLabel, toLabel)
        }
    }
}

private data class TableCell(val text: String, val sub: String? = null, val color: Color? = null)

@Composable
private fun ChangeTable(item: HoldingComparison, type: HoldingsType, fromLabel: String, toLabel: String) {
    val colors = MaterialTheme.colorScheme
    val held = item.change == HoldingChange.HELD
    val from = item.from
    val to = item.to
    val dash = TableCell("—", color = colors.onSurfaceVariant)

    Column {
        TableLine("", listOf(TableCell(fromLabel), TableCell(toLabel), TableCell("Change")), header = true)
        TableLine(
            "Qty",
            listOf(
                from?.let { TableCell(quantity(it.quantity)) } ?: dash,
                to?.let { TableCell(quantity(it.quantity)) } ?: dash,
                changeCell(item.quantityDelta, 0.001, signedQuantity(item.quantityDelta), colored = false)
            )
        )
        TableLine(
            type.priceLabel,
            listOf(
                from?.let { TableCell(price(it.unitPrice)) } ?: dash,
                to?.let { TableCell(price(it.unitPrice)) } ?: dash,
                if (held) changeCell(item.unitPriceDelta, 0.005, signedPrice(item.unitPriceDelta), percent = item.pricePercent)
                else dash
            )
        )
        TableLine(
            "Invested",
            listOf(
                from?.let { TableCell(rupees(it.invested)) } ?: dash,
                to?.let { TableCell(rupees(it.invested)) } ?: dash,
                changeCell(
                    item.investedDelta, 0.5, signedRupees(item.investedDelta),
                    percent = if (held) percentOf(item.investedDelta, from?.invested ?: 0.0) else null,
                    colored = false
                )
            )
        )
        TableLine(
            "Value",
            listOf(
                from?.let { TableCell(rupees(it.current)) } ?: dash,
                to?.let { TableCell(rupees(it.current)) } ?: dash,
                changeCell(
                    item.currentDelta, 0.5, signedRupees(item.currentDelta),
                    percent = if (held) item.currentPercent else null
                )
            )
        )
        TableLine(
            "Returns",
            listOf(
                from?.let { TableCell(signedRupees(it.pnl), signedPercent(it.pnlPercent), gainColor(it.pnl)) } ?: dash,
                to?.let { TableCell(signedRupees(it.pnl), signedPercent(it.pnlPercent), gainColor(it.pnl)) } ?: dash,
                changeCell(item.returnsDelta, 0.5, signedRupees(item.returnsDelta))
            )
        )
    }
}

/** A change value, or a quiet dash when nothing moved. */
@Composable
private fun changeCell(
    delta: Double,
    threshold: Double,
    text: String,
    percent: Double? = null,
    colored: Boolean = true
): TableCell {
    if (abs(delta) < threshold) return TableCell("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
    val color = when {
        !colored -> MaterialTheme.colorScheme.onSurface
        delta > 0 -> InvestColors.GainGreen
        else -> InvestColors.LossRed
    }
    return TableCell(text, percent?.let { signedPercent(it) }, color)
}

@Composable
private fun TableLine(label: String, cells: List<TableCell>, header: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (header) 2.dp else 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.width(60.dp)
        )
        cells.forEach { cell ->
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(
                    text = if (header) cell.text.uppercase() else cell.text,
                    style = if (header) {
                        MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    } else {
                        MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
                    },
                    color = cell.color ?: if (header) colors.onSurfaceVariant else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (cell.sub != null) {
                    Text(
                        text = cell.sub,
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = cell.color ?: colors.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun compareSubline(item: HoldingComparison, type: HoldingsType): String = when (item.change) {
    HoldingChange.NEW ->
        "Bought ${quantity(item.quantity)} ${type.unitWord} · ${type.priceLabel} ${price(item.unitPrice)}"
    HoldingChange.EXITED ->
        "Sold ${quantity(item.fromQuantity)} ${type.unitWord} · last ${price(item.fromPrice)}"
    HoldingChange.HELD -> {
        val moved = abs(item.quantityDelta) >= 0.001
        val qty = quantity(item.quantity) + if (moved) " (${signedQuantity(item.quantityDelta)})" else ""
        "$qty ${type.unitWord} · ${type.priceLabel} ${price(item.unitPrice)}"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Rows
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HoldingCard(onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick),
        content = content
    )
}

/** Monogram, name and one line of context on the left; value and a pill on the right. */
@Composable
private fun HoldingHeader(
    symbol: String,
    subline: String,
    value: String,
    expanded: Boolean,
    muted: Boolean = false,
    badge: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = monogram(symbol),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = if (muted) colors.onSurfaceVariant.copy(alpha = 0.6f) else colors.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = symbol,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = if (muted) colors.onSurfaceVariant else colors.onSurface,
                maxLines = if (expanded) 3 else 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subline,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                color = if (muted) colors.onSurfaceVariant else colors.onSurface,
                maxLines = 1
            )
            Spacer(Modifier.height(4.dp))
            badge()
        }
    }
}

@Composable
private fun ExpandedDetail(expanded: Boolean, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(
        visible = expanded,
        enter = fadeIn(tween(160)) + expandVertically(tween(220, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(120)) + shrinkVertically(tween(180))
    ) {
        Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun DetailStat(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color? = null) {
    Column(modifier = modifier) {
        StatLabel(label)
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

/** "▲ 4.2%" in a tinted capsule, coloured by direction; flat moves stay grey. */
@Composable
private fun ChangePill(percent: Double, large: Boolean = false) {
    val flat = abs(percent) < 0.05
    val color = when {
        flat -> MaterialTheme.colorScheme.onSurfaceVariant
        percent > 0 -> InvestColors.GainGreen
        else -> InvestColors.LossRed
    }
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(
                start = if (flat) 8.dp else if (large) 7.dp else 5.dp,
                end = if (large) 10.dp else 8.dp,
                top = if (large) 5.dp else 2.dp,
                bottom = if (large) 5.dp else 2.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!flat) {
            Icon(
                imageVector = if (percent > 0) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                contentDescription = if (percent > 0) "up" else "down",
                tint = color,
                modifier = Modifier.size(if (large) 14.dp else 12.dp)
            )
            Spacer(Modifier.width(2.dp))
        }
        Text(
            text = "${oneDecimal(abs(percent))}%",
            style = (if (large) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium)
                .copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
            color = color
        )
    }
}

@Composable
private fun StatusTag(text: String, color: Color) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.6.sp),
        color = color,
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
private fun FilterPill(label: String, count: Int, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) accent.copy(alpha = 0.16f) else colors.surfaceContainerHigh)
            .border(1.dp, if (selected) accent.copy(alpha = 0.55f) else Color.Transparent, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold),
            color = if (selected) accent else colors.onSurface
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = if (selected) accent else colors.onSurfaceVariant
        )
    }
}

@Composable
private fun ListHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Summary card
// ─────────────────────────────────────────────────────────────────────────────

/** Raised card with a faint wash of green or red from the corner, by direction. */
@Composable
private fun HeroCard(tint: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .background(
                Brush.linearGradient(
                    colors = listOf(tint.copy(alpha = 0.14f), Color.Transparent),
                    start = Offset.Zero,
                    end = Offset.Infinite
                )
            )
            .padding(18.dp),
        content = content
    )
}

@Composable
private fun HeroLabel(text: String) = StatLabel(text)

@Composable
private fun HeroValue(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium.copy(
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.5).sp,
            fontFeatureSettings = "tnum"
        ),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        modifier = modifier
    )
}

@Composable
private fun HeroStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    lines: List<Pair<String, Color>> = emptyList()
) {
    Column(modifier = modifier.padding(horizontal = 4.dp)) {
        StatLabel(label)
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        lines.forEach { (text, color) ->
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HeroDivider() {
    VerticalDivider(
        modifier = Modifier
            .fillMaxHeight()
            .padding(horizontal = 6.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Controls
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PageHeader(subtitle: String, onBack: () -> Unit) {
    val dims = Dimens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = dims.screenHorizontalPadding, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.width(4.dp))
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
    }
}

@Composable
private fun AssetTypeToggle(
    selected: HoldingsType,
    onSelect: (HoldingsType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        HoldingsType.entries.forEach { type ->
            val isSelected = type == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
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

    val colors = MaterialTheme.colorScheme
    val expanded = state.isComparePickerOpen
    val comparing = state.isComparing
    val content = if (comparing) colors.onPrimaryContainer else colors.onSurface

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (comparing) colors.primaryContainer else colors.surfaceContainerHigh)
                .clickable { onAction(InvestAction.SetComparePickerOpen(!expanded)) }
                .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.CompareArrows,
                contentDescription = null,
                tint = if (comparing) content else colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.compareDate?.let { "Comparing with ${formatShortDate(it)}" } ?: "Compare with another date",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = content
                )
                Text(
                    text = if (comparing) "Tap to pick a different date" else "See what changed between two snapshots",
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.7f)
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Hide dates" else "Show dates",
                tint = content.copy(alpha = 0.8f),
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(20.dp)
            )
            if (comparing) {
                IconButton(onClick = { onAction(InvestAction.SelectCompareDate(null)) }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Stop comparing",
                        tint = content,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(180)) + expandVertically(tween(220, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(180))
        ) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                val presets = remember(candidateDates, snapshotDate) { buildPresets(snapshotDate, candidateDates) }
                if (presets.isNotEmpty()) {
                    PickerLabel("Quick picks")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(presets, key = { it.first }) { (label, date) ->
                            DateChip(label, formatShortDate(date), state.compareDate == date) {
                                onAction(InvestAction.SelectCompareDate(date))
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                PickerLabel("All snapshots")
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
private fun PickerLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

@Composable
private fun DateChip(label: String, caption: String?, isSelected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) colors.primary else colors.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
            ),
            color = if (isSelected) colors.onPrimary else colors.onSurface
        )
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) colors.onPrimary.copy(alpha = 0.8f) else colors.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Building blocks
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1
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
                .height(168.dp)
                .clip(RoundedCornerShape(20.dp))
                .alpha(alpha)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
        Spacer(Modifier.height(12.dp))
        repeat(5) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(16.dp))
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

private val HoldingsType.unitWord: String
    get() = when (this) {
        HoldingsType.EQUITY -> "shares"
        HoldingsType.MUTUAL_FUNDS -> "units"
    }

private fun HoldingsType.holdingNoun(count: Int): String = when (this) {
    HoldingsType.EQUITY -> if (count == 1) "stock" else "stocks"
    HoldingsType.MUTUAL_FUNDS -> if (count == 1) "fund" else "funds"
}

/** "RELIANCE" → "RE", "Parag Parikh Flexi Cap" → "PP". */
private fun monogram(symbol: String): String {
    val words = symbol.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotBlank() }
    return when {
        words.size >= 2 -> "${words[0].first()}${words[1].first()}"
        words.size == 1 -> words[0].take(2)
        else -> "?"
    }.uppercase()
}

private val quantityFormat = DecimalFormat("#,##0.##")
private val priceFormat = DecimalFormat("#,##0.00")
private val oneDecimalFormat = DecimalFormat("0.0")

private fun quantity(value: Double): String = quantityFormat.format(value)

private fun signedQuantity(value: Double): String =
    (if (value >= 0) "+" else "-") + quantityFormat.format(abs(value))

/** Per-unit prices keep paise; they matter at that scale. */
private fun price(value: Double): String = "₹${priceFormat.format(value)}"

private fun signedPrice(value: Double): String = (if (value >= 0) "+" else "-") + price(abs(value))

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
