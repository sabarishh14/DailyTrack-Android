package com.example.dailytrack_mobile.presentation.screens.invest.stocks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.dailytrack_mobile.data.remote.dto.StockDto
import com.example.dailytrack_mobile.data.remote.dto.StockSearchResultDto
import com.example.dailytrack_mobile.presentation.components.FullScreenPage
import com.example.dailytrack_mobile.presentation.components.navigationBarPadding
import com.example.dailytrack_mobile.presentation.screens.invest.InvestColors
import com.example.dailytrack_mobile.presentation.screens.invest.components.PageHeader
import com.example.dailytrack_mobile.presentation.screens.invest.formatExactCurrency
import com.example.dailytrack_mobile.presentation.util.Dimens
import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

// The web's StocksModal on the phone: stocks and ETFs added by hand, their buys
// and sales. Valued through the day at the market's price (stocks_service.py).

private val dateText = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private val fieldDate = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
private val quotedText = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)
private val qtyText = DecimalFormat("#,##0.####")

private fun money(amount: Double): String = formatExactCurrency(amount.roundToLong().toDouble())
private fun price(amount: Double): String = "₹" + String.format(Locale.ENGLISH, "%,.2f", amount)
private fun signedMoney(amount: Double): String = (if (amount >= 0) "+" else "-") + money(abs(amount))
private fun percent(value: Double): String = String.format(Locale.ENGLISH, "%+.1f%%", value)
private fun gainColor(value: Double): Color = if (value >= 0) InvestColors.GainGreen else InvestColors.LossRed
private fun gainOf(s: StockDto): Double? = s.value?.takeIf { s.invested > 0 }?.let { (it - s.invested) / s.invested * 100 }

/** On the Stocks tab: opens [StocksPage]. */
@Composable
fun StocksEntryCard(onClick: () -> Unit) {
    val dims = Dimens.current
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = dims.screenHorizontalPadding, vertical = 4.dp),
        shape = RoundedCornerShape(dims.buttonCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile()
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Manage stocks", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
                Muted("Add any NSE stock or ETF by hand")
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A date to pick, up to today, and what to do with it. */
private class DateRequest(val initial: LocalDate, val onPick: (LocalDate) -> Unit)

@Composable
fun StocksPage(viewModel: StocksVM, onDismiss: () -> Unit) {
    val state by viewModel.state.collectAsState()
    var pickDate by remember { mutableStateOf<DateRequest?>(null) }
    LaunchedEffect(Unit) { viewModel.load() }

    FullScreenPage(onDismiss = onDismiss) {
        BackHandler(enabled = state.adding) { viewModel.stopAdding() }
        PageHeader(
            title = if (state.adding) "Add a stock" else "Stocks",
            subtitle = if (state.adding) "Any NSE stock or ETF" else state.quotedAt?.let { "Prices as of " + quoted(it) } ?: "Valued through the day",
            onBack = { if (state.adding) viewModel.stopAdding() else onDismiss() }
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.current.screenHorizontalPadding + 4.dp)
                .padding(top = 4.dp, bottom = navigationBarPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.adding) AddView(state, viewModel) { pickDate = it }
            else ListView(state, viewModel) { pickDate = it }
        }
    }
    pickDate?.let { request -> PastDatePicker(request) { pickDate = null } }
}

private fun quoted(stamp: String): String =
    runCatching { LocalDateTime.parse(stamp.take(19)).format(quotedText).replace("AM", "am").replace("PM", "pm") }.getOrDefault(stamp)

@Composable
private fun ListView(state: StocksUiState, vm: StocksVM, onPickDate: (DateRequest) -> Unit) {
    var showSold by remember { mutableStateOf(false) }
    when {
        state.loading -> Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
        }
        state.loadError != null && state.stocks.isEmpty() -> Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Muted(state.loadError, center = true)
            TextButton(onClick = vm::load) { Text("Try again") }
        }
        state.stocks.isEmpty() -> Welcome(state.readOnly, vm::startAdding)
        else -> {
            Summary(state, vm::startAdding)
            state.note?.let { note ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(InvestColors.GainGreen.copy(alpha = 0.12f))
                        .clickable(onClick = vm::dismissNote)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = InvestColors.GainGreen, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(note, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = InvestColors.GainGreen)
                }
            }
            state.held.forEach { StockCard(it, state, vm, onPickDate) }
            if (state.sold.isNotEmpty()) {
                TextButton(onClick = { showSold = !showSold }) {
                    Text("Sold (${state.sold.size})")
                    Icon(if (showSold) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                }
                if (showSold) state.sold.forEach { StockCard(it, state, vm, onPickDate) }
            }
        }
    }
}

@Composable
private fun Summary(state: StocksUiState, onAdd: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val gain = state.invested.takeIf { it > 0 }?.let { (state.value - it) / it * 100 }
    Surface(shape = RoundedCornerShape(20.dp), color = colors.primaryContainer.copy(alpha = 0.45f)) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text("Current value", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                Text(money(state.value), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = colors.onSurface)
            }
            Row {
                Stat("Invested", money(state.invested), colors.onSurface, Modifier.weight(1f))
                if (gain != null) Stat("Returns", "${signedMoney(state.value - state.invested)}  ${percent(gain)}", gainColor(gain), Modifier.weight(1f))
            }
            if (!state.readOnly) {
                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add")
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = color)
    }
}

private sealed interface Confirm {
    data object Remove : Confirm
    data class DeleteTrade(val id: Long) : Confirm
}

/** One stock: two lines, opening on a tap for its trades, Buy, Sell and Remove. */
@Composable
private fun StockCard(stock: StockDto, state: StocksUiState, vm: StocksVM, onPickDate: (DateRequest) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val held = stock.quantity > 0
    val open = state.open == stock.symbol && !state.readOnly
    var trading by remember(stock.symbol, open) { mutableStateOf<String?>(null) }
    var confirm by remember(stock.symbol, open) { mutableStateOf<Confirm?>(null) }
    var form by remember(stock.symbol, open, trading) { mutableStateOf(TradeForm()) }
    val gain = gainOf(stock)

    Card(
        onClick = { vm.toggle(stock.symbol) },
        enabled = !state.readOnly,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (open) colors.surfaceContainerHigh else colors.surfaceContainer,
            disabledContainerColor = colors.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f).alpha(if (held) 1f else 0.6f)) {
                    Text(stock.symbol, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface)
                    Text(
                        if (held) "${qtyText.format(stock.quantity)} · avg ${stock.avgPrice?.let(::price) ?: "—"}" else "Sold",
                        style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (held) {
                    Spacer(Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(stock.value?.let(::money) ?: "—", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = colors.onSurface, maxLines = 1)
                        if (gain != null) Text(percent(gain), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = gainColor(gain))
                    }
                }
            }

            if (open) {
                Spacer(Modifier.height(8.dp))
                stock.trades.forEachIndexed { i, t ->
                    if (i > 0) HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.4f))
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (t.kind == "BUY") "Bought" else "Sold",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (t.kind == "BUY") InvestColors.GainGreen else colors.primary,
                            modifier = Modifier.widthIn(min = 56.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text("${qtyText.format(t.quantity)} at ${price(t.price)}", style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                            Text(runCatching { LocalDate.parse(t.date).format(dateText) }.getOrDefault(t.date), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                        }
                        if (confirm == Confirm.DeleteTrade(t.id)) {
                            TextButton(onClick = { confirm = null }) { Text("Keep") }
                            TextButton(onClick = { vm.removeTrade(stock.symbol, t.id) { confirm = null } }, enabled = !state.busy) {
                                Text("Delete", color = colors.error)
                            }
                        } else {
                            IconButton(onClick = { confirm = Confirm.DeleteTrade(t.id) }) {
                                Icon(Icons.Default.Close, contentDescription = "Delete this trade", tint = colors.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                val kind = trading
                when {
                    kind != null -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        TradeFields(form, sell = kind == "SELL", onChange = { form = it }, onPickDate = onPickDate)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                            TextButton(onClick = { trading = null }) { Text("Cancel") }
                            Button(onClick = { vm.trade(stock.symbol, kind, form) { trading = null } }, enabled = form.ready && !state.busy) {
                                Text(if (kind == "BUY") "Buy" else "Sell")
                            }
                        }
                    }
                    confirm == Confirm.Remove -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Remove it and its history?", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                        TextButton(onClick = { confirm = null }) { Text("Cancel") }
                        TextButton(onClick = { vm.remove(stock.symbol) }, enabled = !state.busy) { Text("Remove", color = colors.error) }
                    }
                    else -> Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { trading = "BUY" }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Buy")
                        }
                        if (held) TextButton(onClick = { trading = "SELL" }) {
                            Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Sell")
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { confirm = Confirm.Remove }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Remove", tint = colors.onSurfaceVariant)
                        }
                    }
                }
                if (state.error != null) ErrorText(state.error)
            }
        }
    }
}

@Composable
private fun Welcome(readOnly: Boolean, onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(if (readOnly) "No stocks here yet" else "Track your stocks", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
        if (!readOnly) Muted("Any NSE stock or ETF, valued through the day at the market's price.", center = true)
    }
    if (readOnly) return
    Card(
        onClick = onAdd,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            IconTile()
            Spacer(Modifier.height(8.dp))
            Text("Add a stock", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
            Muted("Search for it and add how many you hold, at what price.")
        }
    }
}

@Composable
private fun IconTile() {
    Box(
        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

// ── Adding one ──────────────────────────────────────────────────────────────

@Composable
private fun AddView(state: StocksUiState, vm: StocksVM, onPickDate: (DateRequest) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val picked = state.picked
    if (picked == null) {
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            placeholder = { Text("Search by name or symbol, e.g. infosys") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        val results = state.results
        when {
            results == null -> Muted("Any NSE stock or ETF.", center = true, modifier = Modifier.padding(vertical = 20.dp))
            results.isEmpty() -> Muted("Nothing on NSE by that name", center = true, modifier = Modifier.padding(vertical = 20.dp))
            else -> results.forEach { r -> SearchResult(r) { vm.pick(r) } }
        }
        state.error?.let { ErrorText(it) }
        return
    }

    Surface(shape = RoundedCornerShape(16.dp), color = colors.primary.copy(alpha = 0.10f)) {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(picked.symbol, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface)
                Muted(picked.name)
            }
            TextButton(onClick = { vm.pick(null) }) { Text("Change") }
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surfaceContainer).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("What you hold", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface)
        TradeFields(state.form, sell = false, onChange = vm::setForm, onPickDate = onPickDate)
        Muted("Bought more than once? Add the total and its average price, or each buy later.")
    }
    state.error?.let { ErrorText(it) }
    Button(
        onClick = vm::add, enabled = state.form.ready && !state.busy,
        shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
    ) {
        if (state.busy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.onPrimary)
            Spacer(Modifier.width(10.dp))
        }
        Text(if (state.busy) "Adding…" else "Add stock", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun SearchResult(result: StockSearchResultDto, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(result.symbol, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
            Muted(result.name)
        }
    }
}

/** Quantity and price side by side, then the day. */
@Composable
private fun TradeFields(form: TradeForm, sell: Boolean, onChange: (TradeForm) -> Unit, onPickDate: (DateRequest) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField(if (sell) "Sold" else "Quantity", form.quantity, null, Modifier.weight(1f)) { onChange(form.copy(quantity = it)) }
            NumberField(if (sell) "At" else "Average price", form.price, "₹ ", Modifier.weight(1.2f)) { onChange(form.copy(price = it)) }
        }
        val colors = MaterialTheme.colorScheme
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surfaceContainerHighest)
                .clickable { onPickDate(DateRequest(form.date) { onChange(form.copy(date = it)) }) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("On", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(form.date.format(fieldDate), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface)
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, prefix: String?, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        prefix = prefix?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    )
}

@Composable
private fun Muted(text: String, center: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
        modifier = if (center) modifier.fillMaxWidth() else modifier
    )
}

@Composable
private fun ErrorText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PastDatePicker(request: DateRequest, onDismiss: () -> Unit) {
    val today = LocalDate.now()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = request.initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() <= today

            override fun isSelectableYear(year: Int): Boolean = year <= today.year
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { request.onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("Select", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state = state)
    }
}
