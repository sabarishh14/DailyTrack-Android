package com.example.dailytrack_mobile.presentation.screens.invest.funds

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.dailytrack_mobile.data.remote.dto.FundDto
import com.example.dailytrack_mobile.data.remote.dto.FundSearchResultDto
import com.example.dailytrack_mobile.presentation.components.SheetContentInsets
import com.example.dailytrack_mobile.presentation.components.rememberSheetHeight
import com.example.dailytrack_mobile.presentation.screens.invest.InvestColors
import com.example.dailytrack_mobile.presentation.screens.invest.formatExactCurrency
import com.example.dailytrack_mobile.presentation.util.Dimens
import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

// The web's FundsModal on the phone: what's held outside Kite, importing a CAS,
// adding a fund by hand, and SIPs. The server does the valuing (funds_service.py).

private const val CAMS_CAS = "https://www.camsonline.com/Investors/Statements/Consolidated-Account-Statement"

private val CamsSettings = listOf(
    "Statement type" to "Detailed",
    "Period" to "Specific · from 01-Jan-1990",
    "Folio listing" to "With zero balance folios",
    "Password" to "Set your own; you'll need it below"
)

private val dateText = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
private val unitsText = DecimalFormat("#,##0.###")

private fun money(amount: Double): String = formatExactCurrency(amount.roundToLong().toDouble())

private fun signedMoney(amount: Double): String = (if (amount >= 0) "+" else "-") + money(kotlin.math.abs(amount))

private fun percent(value: Double): String = String.format(Locale.ENGLISH, "%+.1f%%", value)

private fun ordinal(n: Int): String =
    "$n" + if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }

private fun gainOf(fund: FundDto): Double? =
    fund.value?.takeIf { fund.invested > 0 }?.let { (it - fund.invested) / fund.invested * 100 }

private fun gainColor(value: Double): Color = if (value >= 0) InvestColors.GainGreen else InvestColors.LossRed

/** A date to pick, up to today, and what to do with it. */
private class DateRequest(val initial: LocalDate, val onPick: (LocalDate) -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FundsSheet(viewModel: FundsVM, onDismiss: () -> Unit) {
    val state by viewModel.state.collectAsState()
    var pickDate by remember { mutableStateOf<DateRequest?>(null) }
    LaunchedEffect(Unit) { viewModel.load() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        contentWindowInsets = SheetContentInsets,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) }
    ) {
        BackHandler(enabled = state.view != FundsView.LIST) { viewModel.show(FundsView.LIST) }
        Column(modifier = Modifier.fillMaxWidth().heightIn(max = rememberSheetHeight(0.92f))) {
            Header(
                title = when (state.view) {
                    FundsView.LIST -> "Mutual funds"
                    FundsView.IMPORT -> "Import statement"
                    FundsView.ADD -> "Add a fund"
                },
                onBack = if (state.view != FundsView.LIST) ({ viewModel.show(FundsView.LIST) }) else null,
                onClose = onDismiss
            )
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.current.screenHorizontalPadding + 4.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (state.view) {
                    FundsView.LIST -> ListView(state, viewModel) { pickDate = it }
                    FundsView.IMPORT -> ImportView(state, viewModel)
                    FundsView.ADD -> AddView(state, viewModel) { pickDate = it }
                }
            }
        }
    }

    pickDate?.let { request -> PastDatePicker(request) { pickDate = null } }
}

/** On the Mutual funds tab: opens [FundsSheet]. */
@Composable
fun FundsEntryCard(onClick: () -> Unit) {
    val dims = Dimens.current
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = dims.screenHorizontalPadding, vertical = 4.dp),
        shape = RoundedCornerShape(dims.buttonCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconTile(Icons.Default.Description)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Manage mutual funds",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Muted("Import a statement, add funds and SIPs")
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Header(title: String, onBack: (() -> Unit)?, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = if (onBack == null) 20.dp else 4.dp, end = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close") }
    }
}

// ── Your funds ──────────────────────────────────────────────────────────────

@Composable
private fun ListView(state: FundsUiState, vm: FundsVM, onPickDate: (DateRequest) -> Unit) {
    var showSold by remember { mutableStateOf(false) }
    when {
        state.loading -> Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
        }
        state.loadError != null && state.funds.isEmpty() -> Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Muted(state.loadError, center = true)
            TextButton(onClick = vm::load) { Text("Try again") }
        }
        state.funds.isEmpty() -> Welcome(
            readOnly = state.readOnly,
            onImport = { vm.show(FundsView.IMPORT) },
            onAdd = { vm.show(FundsView.ADD) }
        )
        else -> {
            Summary(state, onImport = { vm.show(FundsView.IMPORT) }, onAdd = { vm.show(FundsView.ADD) })
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
            state.held.forEach { fund -> FundCard(fund, state, vm, onPickDate) }
            if (state.sold.isNotEmpty()) {
                TextButton(onClick = { showSold = !showSold }) {
                    Text("Sold (${state.sold.size})")
                    Icon(if (showSold) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                }
                if (showSold) state.sold.forEach { fund -> FundCard(fund, state, vm, onPickDate) }
            }
            state.rowError?.let { ErrorText(it) }
        }
    }
}

@Composable
private fun Summary(state: FundsUiState, onImport: () -> Unit, onAdd: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val gain = state.invested.takeIf { it > 0 }?.let { (state.value - it) / it * 100 }
    Surface(shape = RoundedCornerShape(20.dp), color = colors.primaryContainer.copy(alpha = 0.45f)) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text("Current value", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                Text(
                    money(state.value),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurface
                )
            }
            Row {
                Stat("Invested", money(state.invested), colors.onSurface, Modifier.weight(1f))
                if (gain != null) {
                    Stat("Returns", "${signedMoney(state.value - state.invested)}  ${percent(gain)}", gainColor(gain), Modifier.weight(1f))
                }
            }
            if (!state.readOnly) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import")
                    }
                    Button(onClick = onAdd, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add")
                    }
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
    data class StopSip(val id: Long) : Confirm
}

/** One fund: two lines, opening on a tap for its SIPs and Remove. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FundCard(fund: FundDto, state: FundsUiState, vm: FundsVM, onPickDate: (DateRequest) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val held = fund.units > 0
    val open = state.open == fund.code && !state.readOnly
    var addingSip by remember(fund.code, open) { mutableStateOf(false) }
    var confirm by remember(fund.code, open) { mutableStateOf<Confirm?>(null) }
    var sip by remember(fund.code, open) { mutableStateOf(SipForm()) }
    val gain = gainOf(fund)

    Card(
        onClick = { vm.toggle(fund.code) },
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
                    Text(
                        fund.name,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val folios = fund.folios.takeIf { it.isNotEmpty() }?.joinToString(", ") { "••$it" }?.let { " · folio $it" }.orEmpty()
                    val sipTag = when {
                        open || fund.sips.isEmpty() -> ""
                        fund.sips.size > 1 -> " · ${fund.sips.size} SIPs"
                        else -> " · SIP ${money(fund.sips.first().amount)}"
                    }
                    Text(
                        (if (held) "${unitsText.format(fund.units)} units" else "Sold") + folios + sipTag,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant
                    )
                }
                if (held) {
                    Spacer(Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            fund.value?.let(::money) ?: "—",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = colors.onSurface,
                            maxLines = 1
                        )
                        if (gain != null) {
                            Text(percent(gain), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = gainColor(gain))
                        }
                    }
                }
            }

            if (open) {
                Spacer(Modifier.height(8.dp))
                val current = confirm
                when {
                    addingSip -> SipFields(
                        sip = sip,
                        onChange = { sip = it },
                        onPickDate = onPickDate,
                        footer = {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                                TextButton(onClick = { addingSip = false }) { Text("Cancel") }
                                Button(
                                    onClick = { vm.startSip(fund.code, sip) { addingSip = false } },
                                    enabled = sip.amountValue != null && !state.rowBusy
                                ) { Text("Start SIP") }
                            }
                        }
                    )
                    current != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (current is Confirm.StopSip) "Stop this SIP?" else "Remove it and its history?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { confirm = null }) { Text("Cancel") }
                        TextButton(
                            enabled = !state.rowBusy,
                            onClick = {
                                when (current) {
                                    is Confirm.StopSip -> vm.stopSip(current.id) { confirm = null }
                                    Confirm.Remove -> vm.remove(fund.code)
                                }
                            }
                        ) { Text(if (current is Confirm.StopSip) "Stop" else "Remove", color = colors.error) }
                    }
                    else -> Row(verticalAlignment = Alignment.CenterVertically) {
                        FlowRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            fund.sips.forEach { s ->
                                Row(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(colors.primary.copy(alpha = 0.12f))
                                        .clickable { confirm = Confirm.StopSip(s.id) }
                                        .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "SIP ${money(s.amount)} · ${ordinal(s.day)}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = colors.primary
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Default.Close, contentDescription = "Stop SIP", tint = colors.primary, modifier = Modifier.size(14.dp))
                                }
                            }
                            if (held) {
                                TextButton(onClick = { addingSip = true }) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("SIP")
                                }
                            }
                        }
                        IconButton(onClick = { confirm = Confirm.Remove }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Remove", tint = colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Welcome(readOnly: Boolean, onImport: () -> Unit, onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            if (readOnly) "No mutual funds here yet" else "Track your mutual funds",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        if (!readOnly) Muted("Valued every day at AMFI's NAV, with SIPs that add themselves.", center = true)
    }
    if (readOnly) return
    Option(Icons.Default.Description, "Import statement", "Every fund and transaction at once, from your CAS.", recommended = true, onClick = onImport)
    Option(Icons.Default.Add, "Add a fund", "Search for one and add what you put in, or its SIP.", recommended = false, onClick = onAdd)
}

@Composable
private fun Option(icon: ImageVector, title: String, text: String, recommended: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(icon)
                Spacer(Modifier.weight(1f))
                if (recommended) {
                    Text(
                        "RECOMMENDED",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = InvestColors.GainGreen,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(InvestColors.GainGreen.copy(alpha = 0.14f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = colors.onSurface)
            Muted(text)
        }
    }
}

@Composable
private fun IconTile(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

// ── Import ──────────────────────────────────────────────────────────────────

@Composable
private fun ImportView(state: FundsUiState, vm: FundsVM) {
    val colors = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    var showPassword by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::pickFile) }

    Step(1, "Get your statement from CAMS") {
        Muted("Tick the disclaimer, enter your email, then choose:")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surfaceContainer)
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CamsSettings.forEach { (label, value) ->
                    Row {
                        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.weight(0.42f))
                        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface, modifier = Modifier.weight(0.58f))
                    }
                }
            }
        }
        FilledTonalButton(onClick = { uriHandler.openUri(CAMS_CAS) }) {
            Text("Open CAMS")
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
        }
        Muted("Arrives by email in a few minutes.")
    }

    Step(2, "Upload it") {
        val file = state.file
        val outline = if (file != null) colors.primary else colors.outlineVariant
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (file != null) colors.primary.copy(alpha = 0.06f) else colors.surfaceContainer)
                .drawBehind {
                    drawRoundRect(
                        color = outline,
                        cornerRadius = CornerRadius(16.dp.toPx()),
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = if (file != null) null else PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()))
                        )
                    )
                }
                .clickable { picker.launch(arrayOf("application/pdf")) }
                .padding(vertical = 22.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconTile(if (file != null) Icons.Default.Description else Icons.Default.UploadFile)
            Spacer(Modifier.height(8.dp))
            Text(
                file?.name ?: "Choose your CAS PDF",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Muted(
                if (file != null) listOfNotNull(file.bytes?.let { "${it / 1024} KB" }, "tap to change").joinToString(" · ")
                else "From your email or Downloads"
            )
        }
        OutlinedTextField(
            value = state.password,
            onValueChange = vm::setPassword,
            label = { Text("PDF password") },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.import() }),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password"
                    )
                }
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        state.importError?.let { ErrorText(it) }
        WideButton(
            text = if (state.importing) "Reading your statement…" else "Import",
            busy = state.importing,
            enabled = file != null && state.password.isNotEmpty() && !state.importing,
            onClick = vm::import
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Read once and never stored: not the PDF, not the password.", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun Step(number: Int, title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text("$number", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
        }
        content()
    }
}

// ── Add ─────────────────────────────────────────────────────────────────────

@Composable
private fun AddView(state: FundsUiState, vm: FundsVM, onPickDate: (DateRequest) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val picked = state.picked
    if (picked == null) {
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            placeholder = { Text("Search by fund name, e.g. parag flexi") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        val results = state.results
        when {
            results == null -> Muted("Any fund with a daily NAV, Direct or Regular.", center = true, modifier = Modifier.padding(vertical = 20.dp))
            results.isEmpty() -> Muted("No fund by that name", center = true, modifier = Modifier.padding(vertical = 20.dp))
            else -> results.forEach { result -> SearchResult(result) { vm.pick(result) } }
        }
        state.addError?.let { ErrorText(it) }
        return
    }

    Surface(shape = RoundedCornerShape(16.dp), color = colors.primary.copy(alpha = 0.10f)) {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(picked.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface)
                Muted(fundLine(picked))
            }
            TextButton(onClick = { vm.pick(null) }) { Text("Change") }
        }
    }

    Panel {
        Text("One-time", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface)
        AmountField("Amount", state.amount, vm::setAmount)
        DateField("On", state.date) { onPickDate(DateRequest(state.date, vm::setDate)) }
    }

    Panel {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { vm.setWithSip(!state.withSip) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Monthly SIP", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface, modifier = Modifier.weight(1f))
            Switch(checked = state.withSip, onCheckedChange = vm::setWithSip)
        }
        if (state.withSip) {
            SipFields(sip = state.sip, onChange = vm::setSip, onPickDate = onPickDate) {
                Muted("Past instalments are filled in at each day's NAV; new ones add themselves.")
            }
        }
    }

    state.addError?.let { ErrorText(it) }
    WideButton(text = if (state.adding) "Adding…" else "Add fund", busy = state.adding, enabled = state.canAdd, onClick = vm::add)
}

private fun fundLine(fund: FundSearchResultDto): String =
    listOfNotNull(fund.amc, "NAV ₹" + String.format(Locale.ENGLISH, "%,.2f", fund.nav)).joinToString(" · ")

@Composable
private fun SearchResult(result: FundSearchResultDto, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(result.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
            Muted(fundLine(result))
        }
    }
}

/** A monthly SIP's amount, day and first month, then [footer]. */
@Composable
private fun SipFields(sip: SipForm, onChange: (SipForm) -> Unit, onPickDate: (DateRequest) -> Unit, footer: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AmountField("Every month", sip.amount) { onChange(sip.copy(amount = it.filter { c -> c.isDigit() || c == '.' })) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surfaceContainerHighest)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("On the ${ordinal(sip.day)}", style = MaterialTheme.typography.bodyLarge, color = colors.onSurface, modifier = Modifier.weight(1f))
            IconButton(onClick = { onChange(sip.copy(day = if (sip.day <= 1) 31 else sip.day - 1)) }) { Text("−", style = MaterialTheme.typography.titleLarge) }
            IconButton(onClick = { onChange(sip.copy(day = if (sip.day >= 31) 1 else sip.day + 1)) }) { Text("+", style = MaterialTheme.typography.titleLarge) }
        }
        DateField("Since", sip.since) { onPickDate(DateRequest(sip.since) { onChange(sip.copy(since = it)) }) }
        footer()
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) { content() }
}

@Composable
private fun AmountField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        prefix = { Text("₹ ") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun DateField(label: String, date: LocalDate, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(date.format(dateText), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface)
        Spacer(Modifier.width(10.dp))
        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

// ── Bits ────────────────────────────────────────────────────────────────────

@Composable
private fun WideButton(text: String, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
    }
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
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
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
