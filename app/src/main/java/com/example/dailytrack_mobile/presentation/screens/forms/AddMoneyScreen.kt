package com.example.dailytrack_mobile.presentation.screens.forms

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.dailytrack_mobile.data.local.datastore.TransactionDraft
import com.example.dailytrack_mobile.presentation.screens.forms.components.AccountSearchDialog
import com.example.dailytrack_mobile.presentation.screens.forms.components.CategorySearchDialog
import com.example.dailytrack_mobile.presentation.theme.AppTheme
import com.example.dailytrack_mobile.presentation.theme.LocalAppTheme
import com.example.dailytrack_mobile.presentation.util.AmountExpression
import com.example.dailytrack_mobile.presentation.util.Dimens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Add Money (Transaction) Form — Modern Redesign with Theme Integration
// ─────────────────────────────────────────────────────────────────────────────

/** Maps to DB `type` column: "Debit" / "Credit" */
private enum class TransactionType(val label: String, val dbValue: String) {
    EXPENSE("Expense", "Debit"),
    INCOME("Income", "Credit")
}

private val defaultExpenseCategories = listOf(
    "Food", "Transport", "Shopping", "Health", "Housing",
    "Entertainment", "Education", "Travel", "Utilities", "Other"
)

private val defaultIncomeCategories = listOf(
    "Salary", "Freelance", "Investment", "Gift", "Other"
)

private val defaultAccounts = listOf(
    "Cash", "KOTAK", "IDBI", "FEDERAL", "CUB", "INDIAN", "ICICI", "HDFC", "SBI", "Axis", "CC-PINNACLE 6360"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddMoneyScreen(
    formsVM: FormsVM = hiltViewModel(),
    onDirtyStateChanged: (Boolean) -> Unit = {},
    onSaveSuccess: () -> Unit = {}
) {
    val formState by formsVM.addMoneyState.collectAsState()

    // Anything left half-typed last time seeds the form, so backing out of the
    // screen — or the process being killed behind a banking app — costs nothing.
    val restoredDraft = remember { formsVM.consumeSavedDraft() }
    var showDraftBanner by remember { mutableStateOf(restoredDraft != null) }

    var selectedType by remember {
        mutableStateOf(
            restoredDraft?.type
                ?.let { saved -> TransactionType.entries.firstOrNull { it.name == saved } }
                ?: TransactionType.EXPENSE
        )
    }
    var categoryInput by remember { mutableStateOf(restoredDraft?.category.orEmpty()) }
    var categorySearchDialogOpen by remember { mutableStateOf(false) }
    var categorySearchQuery by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf(restoredDraft?.amount.orEmpty()) }
    var note by remember { mutableStateOf(restoredDraft?.note.orEmpty()) }
    var isDescriptionFocused by remember { mutableStateOf(false) }
    var showSuggestions by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val dims = Dimens.current
    val amountFocusRequester = remember { FocusRequester() }
    var isAmountFocused by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // Smoothly scroll and manage suggestions when description focus changes
    LaunchedEffect(isDescriptionFocused) {
        if (isDescriptionFocused) {
            showSuggestions = true
            // Allow keyboard opening animation to start, then smoothly scroll Description into comfortable view
            kotlinx.coroutines.delay(120)
            scrollState.animateScrollTo(scrollState.maxValue)
        } else {
            kotlinx.coroutines.delay(200)
            showSuggestions = false
        }
    }

    // Intercept back gesture while Description is focused to dismiss keyboard cleanly
    BackHandler(enabled = isDescriptionFocused) {
        focusManager.clearFocus()
    }

    var selectedAccount by remember { mutableStateOf(restoredDraft?.account) }
    // Date is deliberately not restored: a draft picked up the next day should
    // file under today, not the day the typing started.
    var selectedDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var excludeAnalytics by remember { mutableStateOf(restoredDraft?.excludeAnalytics ?: false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var accountSearchDialogOpen by remember { mutableStateOf(false) }
    var accountSearchQuery by remember { mutableStateOf("") }

    val accountsList = remember(formState.accounts) {
        val list = if (formState.accounts.isNotEmpty()) formState.accounts else defaultAccounts
        list.sortedBy { account ->
            val index = defaultAccounts.indexOfFirst { it.equals(account, ignoreCase = true) }
            if (index == -1) Int.MAX_VALUE else index
        }
    }

    // Auto-select first account if not yet selected
    LaunchedEffect(accountsList) {
        if (selectedAccount == null && accountsList.isNotEmpty()) {
            selectedAccount = accountsList.first()
        }
    }

    val displayDateFormat = remember { SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()) }
    val apiDateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    // The amount field doubles as a calculator; everything downstream works off
    // the evaluated value, never the raw text.
    val evaluatedAmount = remember(amount) { AmountExpression.evaluate(amount) }
    val amountPreview = remember(amount) { AmountExpression.previewFor(amount) }

    val isDirty = remember(selectedType, categoryInput, amount, note, selectedAccount, excludeAnalytics) {
        amount.isNotBlank() || categoryInput.isNotBlank() || note.isNotBlank() ||
            selectedType != TransactionType.EXPENSE || excludeAnalytics
    }

    LaunchedEffect(isDirty) {
        onDirtyStateChanged(isDirty)
    }

    // Mirror the form into the draft store. Debounced so a fast typist doesn't
    // write on every keystroke; an emptied form clears its own draft.
    LaunchedEffect(selectedType, categoryInput, amount, note, selectedAccount, excludeAnalytics) {
        kotlinx.coroutines.delay(400)
        formsVM.persistDraft(
            TransactionDraft(
                type = selectedType.name,
                category = categoryInput,
                amount = amount,
                note = note,
                account = selectedAccount,
                excludeAnalytics = excludeAnalytics
            )
        )
    }

    // Category lists based on type, most-used transactions, and DB
    val allCategories = remember(formState.categories) {
        if (formState.categories.isNotEmpty()) formState.categories else (defaultExpenseCategories + defaultIncomeCategories).distinct()
    }

    val currentCategoryList = remember(selectedType, allCategories, formState.mostUsedExpenseCategories, formState.mostUsedIncomeCategories) {
        val mostUsed = if (selectedType == TransactionType.EXPENSE) {
            formState.mostUsedExpenseCategories
        } else {
            formState.mostUsedIncomeCategories
        }
        val defaultBase = if (selectedType == TransactionType.EXPENSE) defaultExpenseCategories else defaultIncomeCategories
        val combined = (mostUsed + defaultBase + allCategories).distinct()
        if (selectedType == TransactionType.EXPENSE) {
            combined.filter { c ->
                !c.equals("Salary", ignoreCase = true) && !c.equals("Freelance", ignoreCase = true)
            }
        } else {
            combined.filter { c ->
                c.equals("Salary", ignoreCase = true) || c.equals("Freelance", ignoreCase = true) ||
                c.equals("Investment", ignoreCase = true) || c.equals("Gift", ignoreCase = true) ||
                c.equals("Other", ignoreCase = true) || mostUsed.contains(c)
            }
        }
    }

    // Display top 6 pills: top 6 most-used/relevant categories, plus selected category if outside the top 6
    val visibleCategoryPills = remember(selectedType, currentCategoryList, categoryInput) {
        val top6 = currentCategoryList.take(6)
        if (categoryInput.isNotBlank() && !top6.any { it.equals(categoryInput, ignoreCase = true) }) {
            top6 + categoryInput
        } else {
            top6
        }
    }

    // Contextual Description Suggestions:
    // Prioritize past descriptions under the currently selected category,
    // followed by other historical descriptions across all transactions.
    val descriptionSuggestions = remember(
        categoryInput,
        note,
        formState.descriptionsByCategory,
        formState.recentDescriptions
    ) {
        val categoryList = if (categoryInput.isNotBlank()) {
            formState.descriptionsByCategory[categoryInput.trim()].orEmpty()
        } else {
            emptyList()
        }
        val combined = (categoryList + formState.recentDescriptions).distinct()
        val query = note.trim()
        if (query.isBlank()) {
            combined.take(50)
        } else {
            val (startsWith, contains) = combined
                .filter { !it.equals(query, ignoreCase = true) }
                .partition { it.startsWith(query, ignoreCase = true) }
            (startsWith + contains.filter { it.contains(query, ignoreCase = true) }).take(50)
        }
    }

    // Reset or validate category when type switches
    LaunchedEffect(selectedType) {
        if (categoryInput.isNotBlank() && !currentCategoryList.any { it.equals(categoryInput, ignoreCase = true) }) {
            categoryInput = ""
        }
    }

    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selectedDate = it }
                    showDatePicker = false
                }) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Category Search & Custom Creation Dialog
    if (categorySearchDialogOpen) {
        CategorySearchDialog(
            searchQuery = categorySearchQuery,
            onSearchQueryChange = { categorySearchQuery = it },
            categoryList = currentCategoryList,
            selectedCategory = categoryInput,
            onCategorySelected = { selected ->
                categoryInput = selected
                categorySearchDialogOpen = false
                categorySearchQuery = ""
            },
            onDismiss = {
                categorySearchDialogOpen = false
                categorySearchQuery = ""
            }
        )
    }

    val cardBg = MaterialTheme.colorScheme.surfaceContainer
    val cardBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

    // Theme-harmonious colors for expense and income
    val currentTheme = LocalAppTheme.current
    val isDtOg = currentTheme == AppTheme.DT_OG

    val expenseBgColor = MaterialTheme.colorScheme.errorContainer
    val expenseTextColor = MaterialTheme.colorScheme.onErrorContainer
    val expenseAccentColor = MaterialTheme.colorScheme.error

    val incomeBgColor = if (isDtOg) Color(0xFF10B981).copy(alpha = 0.18f) else MaterialTheme.colorScheme.tertiaryContainer
    val incomeTextColor = if (isDtOg) Color(0xFF10B981) else MaterialTheme.colorScheme.onTertiaryContainer
    val incomeAccentColor = if (isDtOg) Color(0xFF10B981) else MaterialTheme.colorScheme.tertiary

    val activeAmountColor = if (selectedType == TransactionType.EXPENSE) expenseAccentColor else incomeAccentColor

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = dims.screenHorizontalPadding, vertical = 12.dp)
                .padding(bottom = if (showSuggestions && descriptionSuggestions.isNotEmpty()) 76.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        // ── Error Banner if any ──────────────────────────────────────
        formState.errorMessage?.let { errorMsg ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { formsVM.clearAddMoneyError() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // ── Restored Draft Notice ────────────────────────────────────
        // Says plainly why the form came back pre-filled, and offers the one
        // action that matters if it wasn't wanted.
        AnimatedVisibility(
            visible = showDraftBanner,
            enter = fadeIn(tween(200)) + expandVertically(tween(200)),
            exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.HistoryEdu,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Restored your unsaved entry",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            categoryInput = ""
                            amount = ""
                            note = ""
                            excludeAnalytics = false
                            selectedType = TransactionType.EXPENSE
                            selectedAccount = accountsList.firstOrNull()
                            formsVM.clearDraft()
                            showDraftBanner = false
                        }
                    ) {
                        Text(
                            text = "Discard",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    IconButton(
                        onClick = { showDraftBanner = false },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }

        // ── Expense / Income Segmented Switcher ───────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(16.dp)
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Expense Option
                val isExpense = selectedType == TransactionType.EXPENSE
                val expenseBg by animateColorAsState(
                    targetValue = if (isExpense) expenseBgColor else Color.Transparent,
                    animationSpec = tween(200),
                    label = "expenseBg"
                )
                val expenseText by animateColorAsState(
                    targetValue = if (isExpense) expenseTextColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(200),
                    label = "expenseText"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(expenseBg)
                        .clickable { selectedType = TransactionType.EXPENSE }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "↑ Expense",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (isExpense) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = expenseText
                    )
                }

                // Income Option
                val isIncome = selectedType == TransactionType.INCOME
                val incomeBg by animateColorAsState(
                    targetValue = if (isIncome) incomeBgColor else Color.Transparent,
                    animationSpec = tween(200),
                    label = "incomeBg"
                )
                val incomeText by animateColorAsState(
                    targetValue = if (isIncome) incomeTextColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(200),
                    label = "incomeText"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(incomeBg)
                        .clickable { selectedType = TransactionType.INCOME }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "↓ Income",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (isIncome) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = incomeText
                    )
                }
            }
        }

        // ── Large Amount Input Box (Tapping opens soft numpad) ─────────
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = cardBorder,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    amountFocusRequester.requestFocus()
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Text(
                    text = "AMOUNT",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "₹",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = activeAmountColor.copy(alpha = 0.85f),
                        modifier = Modifier.padding(end = 6.dp)
                    )

                    BasicTextField(
                        value = amount,
                        onValueChange = { newValue ->
                            // Digits, one decimal point, and arithmetic — a bill split
                            // across three items can be typed as "120+40+15".
                            if (AmountExpression.isAllowedInput(newValue)) {
                                amount = newValue
                            }
                        },
                        textStyle = TextStyle(
                            fontSize = if (amount.length > 12) 30.sp else 42.sp,
                            fontWeight = FontWeight.Bold,
                            color = activeAmountColor
                        ),
                        cursorBrush = SolidColor(activeAmountColor),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                // Collapse the expression on Done so the field then
                                // shows exactly the number that will be saved.
                                if (!AmountExpression.isPlainNumber(amount)) {
                                    AmountExpression.evaluate(amount)?.let { value ->
                                        amount = AmountExpression.formatAmount(value)
                                    }
                                }
                                focusManager.clearFocus()
                            }
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(amountFocusRequester)
                            .onFocusChanged { isAmountFocused = it.isFocused },
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (amount.isEmpty()) {
                                    Text(
                                        text = "0",
                                        fontSize = 42.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = activeAmountColor.copy(alpha = 0.35f)
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }

                // Operator row — the decimal keypad offers no minus, times or
                // divide, so the field supplies them rather than asking the user
                // to hunt through keyboard layouts.
                AnimatedVisibility(
                    visible = isAmountFocused,
                    enter = fadeIn(tween(160)) + expandVertically(tween(180)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(140))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AMOUNT_OPERATORS.forEach { (label, token) ->
                            AmountOperatorKey(
                                label = label,
                                accent = activeAmountColor,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    val next = amount + token
                                    if (AmountExpression.isAllowedInput(next)) amount = next
                                }
                            )
                        }
                        AmountOperatorKey(
                            label = "⌫",
                            accent = activeAmountColor,
                            modifier = Modifier.weight(1f),
                            onClick = { amount = amount.dropLast(1) }
                        )
                    }
                }

                // Only appears while the field holds an expression, so plain
                // number entry looks exactly as it did before.
                AnimatedVisibility(
                    visible = amountPreview != null,
                    enter = fadeIn(tween(150)) + expandVertically(tween(150)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(120))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Functions,
                            contentDescription = null,
                            tint = activeAmountColor.copy(alpha = 0.8f),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "= ₹${amountPreview.orEmpty()}",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = activeAmountColor.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }

        // ── Date & Account Selection Cards (Side-by-Side) ─────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // DATE Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = cardBorder,
                onClick = { showDatePicker = true },
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Text(
                        text = "DATE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = displayDateFormat.format(Date(selectedDate)),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Select Date",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // ACCOUNT Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = cardBorder,
                onClick = { accountSearchDialogOpen = true },
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Text(
                        text = "ACCOUNT",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = selectedAccount ?: "Select",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Select Account",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // ── Category Flow Chips (4 Pills + Compact "+" Pill) ───────────
        val isCategoriesLoading = formState.isLoadingData &&
            (if (selectedType == TransactionType.EXPENSE) formState.mostUsedExpenseCategories.isEmpty() else formState.mostUsedIncomeCategories.isEmpty())

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "CATEGORY",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )

            if (isCategoriesLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(72.dp, 84.dp, 68.dp, 80.dp).forEach { w ->
                        Box(
                            modifier = Modifier
                                .width(w)
                                .height(36.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f))
                        )
                    }
                }
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    visibleCategoryPills.forEach { cat ->
                        val isSelected = categoryInput.equals(cat, ignoreCase = true)
                        val chipBg = if (isSelected) MaterialTheme.colorScheme.primaryContainer else cardBg
                        val chipTextColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        val chipBorderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(chipBg)
                                .border(1.dp, chipBorderColor, RoundedCornerShape(20.dp))
                                .clickable {
                                    categoryInput = if (isSelected) "" else cat
                                }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = cat,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.5.sp
                                ),
                                color = chipTextColor
                            )
                        }
                    }

                    // Compact "+" icon-only pill to search and add categories
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(cardBg)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                        .clickable {
                            categorySearchDialogOpen = true
                        }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Search / Add Category",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        }

        // ── Description Box ──────────────────────────────────────────
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = cardBorder,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DESCRIPTION",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    if (note.isNotBlank()) {
                        Text(
                            text = "Clear",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { note = "" }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                BasicTextField(
                    value = note,
                    onValueChange = { note = it },
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isDescriptionFocused = it.isFocused },
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.TopStart) {
                            if (note.isEmpty()) {
                                Text(
                                    text = "Optional note (suggestions appear above keyboard)...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
        }

        // ── Exclude from Analytics Card ──────────────────────────────
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = cardBorder,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Exclude from Analytics",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Won't count in reports or charts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                Switch(
                    checked = excludeAnalytics,
                    onCheckedChange = { excludeAnalytics = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ── Save Transaction Button ──────────────────────────────────
        val isValidAmount = (evaluatedAmount ?: 0.0) > 0
        val isFormValid = !formState.isSaving && isValidAmount && categoryInput.isNotBlank() && selectedAccount != null

        Button(
            onClick = {
                val amt = evaluatedAmount ?: 0.0
                val dateStr = apiDateFormat.format(Date(selectedDate))
                formsVM.saveTransaction(
                    type = selectedType.dbValue,
                    category = categoryInput.trim().ifEmpty { "Other" },
                    amount = amt,
                    note = note.trim().takeIf { it.isNotBlank() },
                    accountName = selectedAccount ?: accountsList.firstOrNull() ?: "Cash",
                    date = dateStr,
                    excludeAnalytics = excludeAnalytics,
                    onSuccess = onSaveSuccess
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            ),
            enabled = isFormValid
        ) {
            if (formState.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Saving Transaction...",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                )
            } else {
                Text(
                    text = "Save Transaction",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // ── Docked Keyboard Suggestion Accessory Bar ─────────────────────
    // Floats directly attached above the soft keyboard keys
    AnimatedVisibility(
        visible = showSuggestions && descriptionSuggestions.isNotEmpty(),
        enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it },
        exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it },
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (categoryInput.isNotBlank()) "SUGGESTIONS • ${categoryInput.uppercase()}" else "SUGGESTIONS • ALL PAST NOTES",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "1-tap to fill",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    descriptionSuggestions.forEach { suggestion ->
                        val isCategoryMatch = categoryInput.isNotBlank() &&
                            formState.descriptionsByCategory[categoryInput.trim()]?.contains(suggestion) == true
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCategoryMatch) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isCategoryMatch) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                }
                            ),
                            onClick = {
                                note = suggestion
                                focusManager.clearFocus()
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (isCategoryMatch) Icons.Default.Check else Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = suggestion,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 13.sp,
                                        fontWeight = if (isCategoryMatch) FontWeight.Bold else FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

    // Account Search & Selection Dialog (FlowRow pills matching Category dialog)
    if (accountSearchDialogOpen) {
        AccountSearchDialog(
            searchQuery = accountSearchQuery,
            onSearchQueryChange = { accountSearchQuery = it },
            accountList = accountsList,
            selectedAccount = selectedAccount,
            onAccountSelected = { selected ->
                selectedAccount = selected
                accountSearchDialogOpen = false
                accountSearchQuery = ""
            },
            onDismiss = {
                accountSearchDialogOpen = false
                accountSearchQuery = ""
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Amount operator keys
//
// Displayed with the conventional × and ÷ glyphs but inserting the * and /
// the expression parser actually reads.
// ─────────────────────────────────────────────────────────────────────────────

private val AMOUNT_OPERATORS = listOf(
    "+" to "+",
    "−" to "-",
    "×" to "*",
    "÷" to "/",
    "(" to "(",
    ")" to ")"
)

@Composable
private fun AmountOperatorKey(
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = accent.copy(alpha = 0.10f),
        modifier = modifier
            .height(38.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = accent.copy(alpha = 0.9f)
            )
        }
    }
}
