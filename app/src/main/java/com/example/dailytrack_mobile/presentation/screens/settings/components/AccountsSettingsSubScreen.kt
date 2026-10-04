package com.example.dailytrack_mobile.presentation.screens.settings.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.dailytrack_mobile.data.remote.dto.AccountDto
import com.example.dailytrack_mobile.presentation.components.CardBudgetDialog
import com.example.dailytrack_mobile.presentation.components.topBarIconButtonColors
import com.example.dailytrack_mobile.presentation.components.transaction.bankColor
import com.example.dailytrack_mobile.presentation.components.transaction.formatRupees
import com.example.dailytrack_mobile.presentation.screens.settings.AccountsSettingsVM
import com.example.dailytrack_mobile.presentation.util.Dimens

private val CardColor = Color(0xFFEC4899)
private const val MAX_NAME = 50

/** Settings → Accounts: your savings accounts and credit cards, and adding more. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsSettingsSubScreen(
    onNavigateBack: () -> Unit,
    viewModel: AccountsSettingsVM = hiltViewModel()
) {
    BackHandler { onNavigateBack() }
    val state by viewModel.state.collectAsState()
    val dims = Dimens.current
    val snackbar = remember { SnackbarHostState() }
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingMin by remember { mutableStateOf<AccountDto?>(null) }
    var editingBudget by remember { mutableStateOf<AccountDto?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }
    // A new account: close the sheet (the snackbar says what was added).
    LaunchedEffect(state.addedCount) { if (state.addedCount > 0) adding = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "Accounts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    FilledIconButton(colors = topBarIconButtonColors(), onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(dims.iconSizeMedium))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    viewModel.clearAddError()
                    adding = true
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add account", fontWeight = FontWeight.SemiBold) }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when {
            state.loading && state.savings.isEmpty() && state.cards.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            state.loadError != null && state.savings.isEmpty() && state.cards.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(state.loadError ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { viewModel.load(force = true) }) { Text("Try again") }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = dims.screenHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                item { Summary(state.savingsTotal, state.savings.size, state.cards.size) }

                item {
                    Spacer(Modifier.height(6.dp))
                    SettingsSectionLabel("Savings accounts")
                }
                item {
                    SettingsCard {
                        if (state.savings.isEmpty()) {
                            EmptyRow("No savings accounts yet")
                        } else {
                            state.savings.forEachIndexed { index, account ->
                                if (index > 0) SettingsDivider()
                                AccountRow(
                                    account = account,
                                    creditCard = false,
                                    onClick = { editingMin = account }
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(6.dp))
                    SettingsSectionLabel("Credit cards")
                }
                item {
                    SettingsCard {
                        if (state.cards.isEmpty()) {
                            EmptyRow("No credit cards yet")
                        } else {
                            state.cards.forEachIndexed { index, account ->
                                if (index > 0) SettingsDivider()
                                AccountRow(account = account, creditCard = true, onClick = { editingBudget = account })
                            }
                        }
                    }
                }
                item {
                    Text(
                        text = "Tap a savings account to edit its balance or minimum, or a card to set its monthly budget.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }

    if (adding) {
        AddAccountSheet(
            existingNames = state.names,
            saving = state.adding,
            serverError = state.addError,
            onEdited = viewModel::clearAddError,
            onAdd = viewModel::add,
            onDismiss = { adding = false }
        )
    }
    editingMin?.let { account ->
        MinBalanceDialog(
            account = account,
            onSave = { balance, min ->
                viewModel.saveAccount(account, balance, min)
                editingMin = null
            },
            onDismiss = { editingMin = null }
        )
    }
    editingBudget?.let { card ->
        CardBudgetDialog(
            card = card.account,
            usedThisMonth = card.usedThisMonth ?: 0.0,
            budget = card.monthlyBudget,
            onSave = { budget ->
                viewModel.setCardBudget(card.account, budget)
                editingBudget = null
            },
            onDismiss = { editingBudget = null }
        )
    }
}

/**
 * Until someone has an account: one card that adds their first, with the same
 * sheet as Settings → Accounts. Home and Add money show it.
 */
@Composable
fun FirstAccountCard(
    onAdded: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountsSettingsVM = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val colors = MaterialTheme.colorScheme
    var adding by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.addedCount) {
        if (state.addedCount > 0) {
            adding = false
            onAdded()
        }
    }

    SettingsCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AccountBalance, contentDescription = null, tint = colors.primary, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Add your first account",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "A bank account or a credit card",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    viewModel.clearAddError()
                    adding = true
                },
                shape = RoundedCornerShape(26.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add account", fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (adding) {
        AddAccountSheet(
            existingNames = state.names,
            saving = state.adding,
            serverError = state.addError,
            onEdited = viewModel::clearAddError,
            onAdd = viewModel::add,
            onDismiss = { adding = false }
        )
    }
}

/** What's in savings altogether, and how many of each there are. */
@Composable
private fun Summary(total: Double, savings: Int, cards: Int) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(colors.primaryContainer, colors.tertiaryContainer)))
            .padding(18.dp)
    ) {
        Text(
            text = "IN YOUR SAVINGS",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = colors.onPrimaryContainer.copy(alpha = 0.7f)
        )
        Text(
            text = "₹${formatRupees(total)}",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
            color = colors.onPrimaryContainer
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CountChip(Icons.Default.AccountBalance, if (savings == 1) "1 account" else "$savings accounts")
            CountChip(Icons.Default.CreditCard, if (cards == 1) "1 card" else "$cards cards")
        }
    }
}

@Composable
private fun CountChip(icon: ImageVector, text: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.surface.copy(alpha = 0.45f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = colors.onPrimaryContainer)
    }
}

@Composable
private fun AccountRow(account: AccountDto, creditCard: Boolean, onClick: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    val accent = bankColor(account.account) ?: colors.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AccountBadge(creditCard, accent, 36.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.account,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = when {
                    creditCard && account.monthlyBudget != null -> "Budget ₹${formatRupees(account.monthlyBudget)} a month"
                    creditCard -> "No monthly budget set"
                    account.minBalance != null -> "Min ₹${formatRupees(account.minBalance)}"
                    else -> "No minimum set"
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
        // A card shows what it's been used for this month, as money gone out.
        val amount = if (creditCard) account.usedThisMonth?.let { -it } else account.balance
        if (amount != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (amount < 0) "-₹${formatRupees(-amount)}" else "₹${formatRupees(amount)}",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AccountBadge(creditCard: Boolean, accent: Color, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (creditCard) Icons.Default.CreditCard else Icons.Default.AccountBalance,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

@Composable
private fun EmptyRow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
    )
}

// ── Adding one ───────────────────────────────────────────────────────────────

/** "HDFC" for a savings account; "CC-AXIS REWARDS" for a card, whatever was typed before it. */
private fun finalName(typed: String, creditCard: Boolean): String {
    val name = typed.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
    if (!creditCard) return name
    val bare = name.replace(Regex("^CC[\\s-]*", RegexOption.IGNORE_CASE), "").trim()
    return if (bare.isEmpty()) "" else "CC-$bare"
}

private fun amount(text: String): Double? = text.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAccountSheet(
    existingNames: Set<String>,
    saving: Boolean,
    serverError: String?,
    onEdited: () -> Unit,
    onAdd: (name: String, creditCard: Boolean, balance: Double?, minBalance: Double?) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var creditCard by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var balance by rememberSaveable { mutableStateOf("") }
    var minimum by rememberSaveable { mutableStateOf("") }

    val finalName = finalName(name, creditCard)
    val nameProblem = when {
        name.isBlank() -> null
        !creditCard && name.trim().startsWith("CC", ignoreCase = true) -> "Names starting with CC are for credit cards"
        finalName.isEmpty() -> "Give the card a name"
        finalName.length > MAX_NAME -> "Keep it under $MAX_NAME characters"
        finalName.lowercase() in existingNames -> "You already have $finalName"
        else -> null
    }
    val balanceValue = amount(balance)
    val minValue = amount(minimum)
    val amountsOk = creditCard || ((balance.isBlank() || balanceValue != null) && (minimum.isBlank() || minValue != null))
    val canAdd = finalName.isNotEmpty() && nameProblem == null && amountsOk && !saving

    fun edit(block: () -> Unit) {
        block()
        onEdited()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Add an account",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TypeOption(
                    creditCard = false,
                    title = "Savings account",
                    subtitle = "Tracks its balance",
                    accent = colors.primary,
                    selected = !creditCard,
                    onClick = { edit { creditCard = false } },
                    modifier = Modifier.weight(1f)
                )
                TypeOption(
                    creditCard = true,
                    title = "Credit card",
                    subtitle = "Spends on credit",
                    accent = CardColor,
                    selected = creditCard,
                    onClick = { edit { creditCard = true } },
                    modifier = Modifier.weight(1f)
                )
            }

            Preview(
                name = finalName.ifEmpty { if (creditCard) "CC-YOUR CARD" else "YOUR BANK" },
                creditCard = creditCard,
                balance = if (creditCard) null else balanceValue ?: 0.0,
                minimum = if (creditCard) null else minValue,
                placeholder = finalName.isEmpty()
            )

            OutlinedTextField(
                value = name,
                onValueChange = { typed -> edit { name = typed.take(MAX_NAME + 3) } },
                label = { Text(if (creditCard) "Card name" else "Account name") },
                placeholder = { Text(if (creditCard) "AXIS REWARDS" else "HDFC") },
                prefix = if (creditCard) {
                    { Text("CC-") }
                } else null,
                singleLine = true,
                isError = nameProblem != null,
                supportingText = if (nameProblem != null) {
                    { Text(nameProblem) }
                } else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            AnimatedVisibility(
                visible = !creditCard,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MoneyField(
                        value = balance,
                        onChange = { edit { balance = it } },
                        label = "Current balance",
                        placeholder = "0",
                        help = "What's in it right now. Transactions move it from here."
                    )
                    MoneyField(
                        value = minimum,
                        onChange = { edit { minimum = it } },
                        label = "Minimum balance (optional)",
                        placeholder = "2000",
                        help = "You'll get an alert when a transaction takes it below this."
                    )
                }
            }
            AnimatedVisibility(visible = creditCard, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    text = "Spends are recorded against the card. Its balance stays at ₹0, so it doesn't change your totals.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }

            if (serverError != null) {
                Text(text = serverError, style = MaterialTheme.typography.bodyMedium, color = colors.error)
            }

            Button(
                onClick = { onAdd(finalName, creditCard, balanceValue, minValue) },
                enabled = canAdd,
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = colors.onPrimary)
                } else {
                    Text(if (creditCard) "Add card" else "Add account", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TypeOption(
    creditCard: Boolean,
    title: String,
    subtitle: String,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(
        if (selected) accent.copy(alpha = 0.12f) else colors.surfaceContainerHigh,
        label = "typeOptionBg"
    )
    val border by animateColorAsState(if (selected) accent else Color.Transparent, label = "typeOptionBorder")
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .border(BorderStroke(1.5.dp, border), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        AccountBadge(creditCard = creditCard, accent = accent, size = 34.dp)
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

/** How the new account will look in your lists. */
@Composable
private fun Preview(name: String, creditCard: Boolean, balance: Double?, minimum: Double?, placeholder: Boolean) {
    val colors = MaterialTheme.colorScheme
    val accent = if (creditCard) CardColor else colors.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceContainerHighest.copy(alpha = 0.6f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AccountBadge(creditCard, accent, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (placeholder) colors.onSurfaceVariant.copy(alpha = 0.6f) else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = when {
                    creditCard -> "Credit card"
                    minimum != null -> "Min ₹${formatRupees(minimum)}"
                    else -> "Savings account"
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
        if (balance != null) {
            Text(
                text = "₹${formatRupees(balance)}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface
            )
        }
    }
}

@Composable
private fun MoneyField(value: String, onChange: (String) -> Unit, label: String, placeholder: String, help: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { typed ->
            // Digits and one decimal point.
            val cleaned = typed.filter { it.isDigit() || it == '.' }
            if (cleaned.count { it == '.' } <= 1) onChange(cleaned.take(15))
        },
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        prefix = { Text("₹") },
        supportingText = { Text(help) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

// ── A savings account's minimum ──────────────────────────────────────────────

/** Set, change or remove the balance a savings account shouldn't drop below. */
/** A savings account's balance (set to what the bank shows) and its minimum. Blank minimum = none. */
@Composable
private fun MinBalanceDialog(account: AccountDto, onSave: (balance: Double, min: Double?) -> Unit, onDismiss: () -> Unit) {
    fun plain(v: Double?) = v?.let { if (it % 1.0 == 0.0) "%.0f".format(it) else "%.2f".format(it) } ?: ""
    var balanceText by remember { mutableStateOf(plain(account.balance)) }
    var minText by remember { mutableStateOf(plain(account.minBalance)) }
    val balance = amount(balanceText)
    val min = amount(minText)
    val minOk = minText.isBlank() || min != null
    val accent = bankColor(account.account) ?: MaterialTheme.colorScheme.primary
    val clean: (String) -> String = { new ->
        val c = new.filter { it.isDigit() || it == '.' }
        if (c.count { it == '.' } <= 1) c.take(15) else c.dropLast(1)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { AccountBadge(creditCard = false, accent = accent, size = 36.dp) },
        title = { Text(account.account) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = clean(it) },
                    label = { Text("Balance") },
                    prefix = { Text("₹") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = minText,
                    onValueChange = { minText = clean(it) },
                    label = { Text("Minimum (optional)") },
                    prefix = { Text("₹") },
                    placeholder = { Text("None") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { balance?.let { onSave(it, min) } },
                enabled = balance != null && minOk
            ) { Text("Save", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
