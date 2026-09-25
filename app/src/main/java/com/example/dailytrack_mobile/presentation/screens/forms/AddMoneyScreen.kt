package com.example.dailytrack_mobile.presentation.screens.forms

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.dailytrack_mobile.data.local.datastore.TransactionDraft
import com.example.dailytrack_mobile.data.remote.dto.BalanceChangeDto
import com.example.dailytrack_mobile.data.repository.NewTransaction
import com.example.dailytrack_mobile.presentation.components.transaction.BalanceProjection
import com.example.dailytrack_mobile.presentation.components.transaction.EntryBalancePreview
import com.example.dailytrack_mobile.presentation.components.transaction.projectBalances
import com.example.dailytrack_mobile.presentation.components.transaction.EntryAmountCard
import com.example.dailytrack_mobile.presentation.components.transaction.EntryCategoryPills
import com.example.dailytrack_mobile.presentation.components.transaction.EntryDateAccountRow
import com.example.dailytrack_mobile.presentation.components.transaction.EntryDescriptionCard
import com.example.dailytrack_mobile.presentation.components.transaction.EntryEditorHeader
import com.example.dailytrack_mobile.presentation.components.transaction.EntryExcludeCard
import com.example.dailytrack_mobile.presentation.components.transaction.EntrySuggestionBar
import com.example.dailytrack_mobile.presentation.components.transaction.EntrySummaryCard
import com.example.dailytrack_mobile.presentation.components.transaction.EntryType
import com.example.dailytrack_mobile.presentation.components.transaction.EntryTypeSelector
import com.example.dailytrack_mobile.presentation.components.transaction.TransactionEntryState
import com.example.dailytrack_mobile.presentation.components.transaction.entryTypeAccent
import com.example.dailytrack_mobile.presentation.components.transaction.formatRupees
import com.example.dailytrack_mobile.presentation.components.transaction.joinAsSentence
import com.example.dailytrack_mobile.presentation.components.transaction.rankDescriptionSuggestions
import com.example.dailytrack_mobile.presentation.screens.forms.components.AccountSearchDialog
import com.example.dailytrack_mobile.presentation.screens.forms.components.CategorySearchDialog
import com.example.dailytrack_mobile.presentation.util.Dimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

// ─────────────────────────────────────────────────────────────────────────────
// Add Money
//
// One entry looks exactly like a single-transaction form. "Add another" folds
// the current entry into a one-line card and opens a fresh one below, carrying
// over the account, date and type — so a day's worth of spending is typed as a
// stack of cards rather than one enormous page. Everything saves in one go.
// ─────────────────────────────────────────────────────────────────────────────

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

private val addTypes = EntryType.entries

private fun TransactionDraft.toEntry(): TransactionEntryState = TransactionEntryState(
    type = EntryType.entries.firstOrNull { it.name == type } ?: EntryType.EXPENSE,
    category = category,
    amount = amount,
    note = note,
    account = account,
    dateMillis = TransactionEntryState.parseApiDate(date) ?: System.currentTimeMillis(),
    excludeAnalytics = excludeAnalytics
)

private fun TransactionEntryState.toDraft(today: String): TransactionDraft = TransactionDraft(
    type = type.name,
    category = category,
    amount = amount,
    note = note,
    account = account,
    excludeAnalytics = excludeAnalytics,
    date = apiDate.takeIf { it != today }
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AddMoneyScreen(
    formsVM: FormsVM = hiltViewModel(),
    onDirtyStateChanged: (Boolean) -> Unit = {},
    onSaveSuccess: (count: Int, balances: List<BalanceChangeDto>) -> Unit = { _, _ -> }
) {
    val formState by formsVM.addMoneyState.collectAsState()
    val focusManager = LocalFocusManager.current
    val dims = Dimens.current
    val scrollState = rememberScrollState()

    // Anything left half-typed last time seeds the form, so backing out of the
    // screen — or the process being killed behind a banking app — costs nothing.
    val restoredDrafts = remember { formsVM.consumeSavedDrafts() }

    // The balance preview must start from today's numbers, not the last visit's.
    LaunchedEffect(Unit) { formsVM.refreshAccounts() }
    var showDraftBanner by remember { mutableStateOf(restoredDrafts.isNotEmpty()) }

    val entries = remember {
        mutableStateListOf<TransactionEntryState>().apply {
            if (restoredDrafts.isEmpty()) add(TransactionEntryState())
            else restoredDrafts.forEach { add(it.toEntry()) }
        }
    }
    var activeId by remember { mutableStateOf<Long?>(entries.last().id) }
    val active = entries.firstOrNull { it.id == activeId }

    // A removed entry can be brought back for a few seconds.
    var lastRemoved by remember { mutableStateOf<Pair<Int, TransactionEntryState>?>(null) }
    LaunchedEffect(lastRemoved) {
        if (lastRemoved != null) {
            delay(5000)
            lastRemoved = null
        }
    }

    val amountFocusRequester = remember { FocusRequester() }
    val editorTopRequester = remember { BringIntoViewRequester() }
    val descriptionRequester = remember { BringIntoViewRequester() }
    var focusAmountOnOpen by remember { mutableStateOf(false) }
    var isDescriptionFocused by remember { mutableStateOf(false) }
    var showSuggestions by remember { mutableStateOf(false) }

    var showDatePicker by remember { mutableStateOf(false) }
    var categorySearchDialogOpen by remember { mutableStateOf(false) }
    var categorySearchQuery by remember { mutableStateOf("") }
    var accountSearchDialogOpen by remember { mutableStateOf(false) }
    var accountSearchQuery by remember { mutableStateOf("") }

    val accountsList = remember(formState.accounts) {
        val list = if (formState.accounts.isNotEmpty()) formState.accounts else defaultAccounts
        list.sortedBy { account ->
            val index = defaultAccounts.indexOfFirst { it.equals(account, ignoreCase = true) }
            if (index == -1) Int.MAX_VALUE else index
        }
    }

    // Entries without an account yet start on the first one.
    LaunchedEffect(accountsList, entries.size) {
        accountsList.firstOrNull()?.let { first ->
            entries.filter { it.account == null }.forEach { it.account = first }
        }
    }

    // A single entry is always open — a lone folded card would just be in the way.
    LaunchedEffect(entries.size) {
        if (entries.size == 1) activeId = entries.first().id
    }

    // ── Keep the draft in step with the cards ────────────────────────────────
    LaunchedEffect(Unit) {
        snapshotFlow {
            val today = TransactionEntryState.formatApiDate(System.currentTimeMillis())
            entries.map { it.toDraft(today) }
        }.collectLatest { drafts ->
            delay(400)  // a fast typist shouldn't write on every keystroke
            formsVM.persistDrafts(drafts)
        }
    }

    val isDirty = entries.size > 1 || entries.any { !it.isBlank }
    LaunchedEffect(isDirty) { onDirtyStateChanged(isDirty) }

    // ── Categories ───────────────────────────────────────────────────────────
    val allCategories = remember(formState.categories) {
        formState.categories.ifEmpty { (defaultExpenseCategories + defaultIncomeCategories).distinct() }
    }

    // Only what this type has been used with before; new ones can still be typed in the search dialog.
    fun categoriesFor(type: EntryType): List<String> = formState.history.categoriesFor(type, allCategories)

    // ── Entry actions ────────────────────────────────────────────────────────

    fun open(entry: TransactionEntryState, focusAmount: Boolean = false) {
        focusManager.clearFocus()
        // Folding away an entry nobody typed into just removes it.
        active?.takeIf { it.id != entry.id && it.isBlank && entries.size > 1 }?.let { entries.remove(it) }
        activeId = entry.id
        focusAmountOnOpen = focusAmount
    }

    fun collapse() {
        focusManager.clearFocus()
        val current = active ?: return
        if (entries.size == 1) return
        if (current.isBlank) entries.remove(current)
        activeId = null
    }

    fun addAnother() {
        val current = active
        if (current != null && current.isBlank) {
            // Already on an empty card — just put the cursor in it.
            runCatching { amountFocusRequester.requestFocus() }
            return
        }
        val next = (current ?: entries.last()).nextFromThis()
        entries.add(next)
        open(next, focusAmount = true)
    }

    fun duplicate(entry: TransactionEntryState) {
        val copy = entry.duplicate()
        entries.add(entries.indexOf(entry) + 1, copy)
        open(copy)
    }

    fun remove(entry: TransactionEntryState) {
        focusManager.clearFocus()
        val index = entries.indexOf(entry)
        if (index < 0) return
        entries.removeAt(index)
        if (!entry.isBlank) lastRemoved = index to entry
        if (entries.isEmpty()) {
            val fresh = entry.nextFromThis()
            entries.add(fresh)
        }
        if (activeId == entry.id) {
            activeId = if (entries.size == 1) entries.first().id else null
        }
    }

    // Opening a card scrolls its top into view, and a brand-new one gets the cursor.
    LaunchedEffect(activeId) {
        if (activeId == null) return@LaunchedEffect
        delay(120)
        if (entries.size > 1) runCatching { editorTopRequester.bringIntoView() }
        if (focusAmountOnOpen) {
            focusAmountOnOpen = false
            runCatching { amountFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(isDescriptionFocused) {
        if (isDescriptionFocused) {
            showSuggestions = true
            delay(250)  // let the keyboard start opening first
            runCatching { descriptionRequester.bringIntoView() }
        } else {
            delay(200)
            showSuggestions = false
        }
    }

    BackHandler(enabled = isDescriptionFocused) { focusManager.clearFocus() }

    // ── Pickers (always act on the open entry) ───────────────────────────────

    if (showDatePicker && active != null) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = active.dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { active.dateMillis = it }
                    showDatePicker = false
                }) { Text("OK", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (categorySearchDialogOpen && active != null) {
        CategorySearchDialog(
            searchQuery = categorySearchQuery,
            onSearchQueryChange = { categorySearchQuery = it },
            categoryList = categoriesFor(active.type),
            selectedCategory = active.category,
            onCategorySelected = { selected ->
                active.category = selected
                categorySearchDialogOpen = false
                categorySearchQuery = ""
            },
            onDismiss = {
                categorySearchDialogOpen = false
                categorySearchQuery = ""
            }
        )
    }

    if (accountSearchDialogOpen && active != null) {
        AccountSearchDialog(
            searchQuery = accountSearchQuery,
            onSearchQueryChange = { accountSearchQuery = it },
            accountList = accountsList,
            selectedAccount = active.account,
            onAccountSelected = { selected ->
                active.account = selected
                accountSearchDialogOpen = false
                accountSearchQuery = ""
            },
            onDismiss = {
                accountSearchDialogOpen = false
                accountSearchQuery = ""
            }
        )
    }

    val descriptionSuggestions = active?.let {
        rankDescriptionSuggestions(
            type = it.type,
            category = it.category,
            query = it.note,
            history = formState.history
        )
    }.orEmpty()

    // What each entry leaves in its account, stacking entries on the same one.
    val projections = projectBalances(entries, formState.accountDetails)

    val toSave = entries.filterNot { it.isBlank }
    val firstIncomplete = toSave.firstOrNull { !it.isComplete }
    val canSave = !formState.isSaving && toSave.isNotEmpty() && firstIncomplete == null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                // Save is the last thing on the page: just a small margin under it
                // (the gesture-bar inset is added by the scaffold).
                .padding(start = dims.screenHorizontalPadding, end = dims.screenHorizontalPadding, top = 12.dp, bottom = 8.dp)
                .padding(bottom = if (showSuggestions && descriptionSuggestions.isNotEmpty()) 84.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            formState.errorMessage?.let { errorMsg ->
                InlineBanner(
                    text = errorMsg,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    leading = {
                        Icon(Icons.Default.ErrorOutline, contentDescription = "Error", tint = MaterialTheme.colorScheme.error)
                    },
                    onDismiss = { formsVM.clearAddMoneyError() }
                )
            }

            // Says plainly why the form came back pre-filled, and offers the one
            // action that matters if it wasn't wanted.
            AnimatedVisibility(
                visible = showDraftBanner,
                enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
            ) {
                InlineBanner(
                    text = if (restoredDrafts.size > 1) "Restored ${restoredDrafts.size} unsaved entries" else "Restored your unsaved entry",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    leading = {
                        Icon(
                            Icons.Default.HistoryEdu,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    actionLabel = "Discard",
                    onAction = {
                        focusManager.clearFocus()
                        entries.clear()
                        val fresh = TransactionEntryState(account = accountsList.firstOrNull())
                        entries.add(fresh)
                        activeId = fresh.id
                        lastRemoved = null
                        formsVM.clearDraft()
                        showDraftBanner = false
                    },
                    onDismiss = { showDraftBanner = false }
                )
            }

            if (entries.size > 1) {
                BatchSummaryRow(entries = toSave)
            }

            AnimatedVisibility(
                visible = lastRemoved != null,
                enter = fadeIn(tween(150)) + expandVertically(tween(150)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
            ) {
                InlineBanner(
                    text = "Entry removed",
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionLabel = "Undo",
                    onAction = {
                        lastRemoved?.let { (index, entry) ->
                            // Drop a placeholder that was only there to keep the form non-empty.
                            if (entries.size == 1 && entries.first().isBlank) entries.clear()
                            entries.add(index.coerceAtMost(entries.size), entry)
                            open(entry)
                        }
                        lastRemoved = null
                    }
                )
            }

            // ── The entries ──────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(tween(220)),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                entries.forEachIndexed { index, entry ->
                    key(entry.id) {
                        if (entry.id == activeId) {
                            EntryEditor(
                                entry = entry,
                                number = index + 1,
                                total = entries.size,
                                balanceProjection = projections[entry.id],
                                categoryPills = remember(entry.type, entry.category, formState.categories, formState.history) {
                                    val top = categoriesFor(entry.type).take(6)
                                    if (entry.category.isNotBlank() && top.none { it.equals(entry.category, ignoreCase = true) }) top + entry.category else top
                                },
                                isCategoriesLoading = formState.isLoadingData && !formState.history.hasCategories(entry.type),
                                amountFocusRequester = amountFocusRequester,
                                topRequester = editorTopRequester,
                                descriptionRequester = descriptionRequester,
                                onTypeSelect = { type ->
                                    if (type != entry.type) {
                                        entry.type = type
                                        if (entry.category.isNotBlank() &&
                                            categoriesFor(type).none { it.equals(entry.category, ignoreCase = true) }
                                        ) {
                                            entry.category = ""
                                        }
                                    }
                                },
                                onDateClick = { showDatePicker = true },
                                onAccountClick = { accountSearchDialogOpen = true },
                                onMoreCategories = { categorySearchDialogOpen = true },
                                onDescriptionFocusChanged = { isDescriptionFocused = it },
                                onCollapse = { collapse() },
                                onDuplicate = { duplicate(entry) },
                                onRemove = { remove(entry) }
                            )
                        } else {
                            EntrySummaryCard(
                                number = index + 1,
                                entry = entry,
                                onClick = { open(entry) },
                                onDuplicate = { duplicate(entry) },
                                onRemove = { remove(entry) }
                            )
                        }
                    }
                }
            }

            // ── Add another ──────────────────────────────────────────────────
            OutlinedButton(
                onClick = { addAnother() },
                enabled = !formState.isSaving,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Add another transaction",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }

            // Points at what's holding the save up, one tap from fixing it.
            if (firstIncomplete != null && toSave.size > 1) {
                val number = entries.indexOf(firstIncomplete) + 1
                Surface(
                    onClick = { open(firstIncomplete) },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Entry $number needs ${firstIncomplete.missingFields.joinAsSentence()}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Fix",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // ── Save ─────────────────────────────────────────────────────────
            Button(
                onClick = {
                    focusManager.clearFocus()
                    formsVM.saveTransactions(
                        entries = toSave.map { e ->
                            NewTransaction(
                                type = e.type.dbValue,
                                category = e.category.trim().ifEmpty { "Other" },
                                amount = e.evaluatedAmount ?: 0.0,
                                note = e.note.trim().takeIf { it.isNotBlank() },
                                accountName = e.account ?: accountsList.firstOrNull() ?: "Cash",
                                date = e.apiDate,
                                excludeAnalytics = e.excludeAnalytics
                            )
                        },
                        onSuccess = onSaveSuccess
                    )
                },
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                val count = toSave.size
                if (formState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = when {
                        formState.isSaving && count > 1 -> "Saving $count transactions…"
                        formState.isSaving -> "Saving transaction…"
                        count > 1 -> "Save $count transactions"
                        else -> "Save Transaction"
                    },
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp)
                )
            }
        }

        // Docked directly above the soft keyboard while a description is typed.
        AnimatedVisibility(
            visible = showSuggestions && descriptionSuggestions.isNotEmpty() && active != null,
            enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it },
            exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val category = active?.category.orEmpty()
            EntrySuggestionBar(
                category = category,
                suggestions = descriptionSuggestions,
                categorySuggestions = active?.let { formState.history.descriptionsFor(it.type, category) }.orEmpty(),
                onPick = { suggestion ->
                    active?.note = suggestion
                    focusManager.clearFocus()
                },
                onDone = { focusManager.clearFocus() }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// The open entry
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryEditor(
    entry: TransactionEntryState,
    number: Int,
    total: Int,
    balanceProjection: BalanceProjection?,
    categoryPills: List<String>,
    isCategoriesLoading: Boolean,
    amountFocusRequester: FocusRequester,
    topRequester: BringIntoViewRequester,
    descriptionRequester: BringIntoViewRequester,
    onTypeSelect: (EntryType) -> Unit,
    onDateClick: () -> Unit,
    onAccountClick: () -> Unit,
    onMoreCategories: () -> Unit,
    onDescriptionFocusChanged: (Boolean) -> Unit,
    onCollapse: () -> Unit,
    onDuplicate: () -> Unit,
    onRemove: () -> Unit
) {
    val accent = entryTypeAccent(entry.type)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (total > 1) 6.dp else 0.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.bringIntoViewRequester(topRequester),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (total > 1) {
                EntryEditorHeader(
                    number = number,
                    total = total,
                    onCollapse = onCollapse,
                    onDuplicate = onDuplicate,
                    onRemove = onRemove
                )
            }
            EntryTypeSelector(selected = entry.type, options = addTypes, onSelect = onTypeSelect)
        }

        EntryAmountCard(
            amount = entry.amount,
            onAmountChange = { entry.amount = it },
            accent = accent,
            focusRequester = amountFocusRequester
        )

        EntryDateAccountRow(
            dateMillis = entry.dateMillis,
            account = entry.account,
            onDateClick = onDateClick,
            onAccountClick = onAccountClick
        )

        AnimatedVisibility(
            visible = balanceProjection != null,
            enter = fadeIn(tween(180)) + expandVertically(tween(180)),
            exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
        ) {
            // Keeps showing the last projection while it animates away.
            var shown by remember { mutableStateOf(balanceProjection) }
            if (balanceProjection != null) shown = balanceProjection
            shown?.let { EntryBalancePreview(it) }
        }

        EntryCategoryPills(
            pills = categoryPills,
            selected = entry.category,
            onSelect = { entry.category = it },
            onMore = onMoreCategories,
            isLoading = isCategoriesLoading
        )

        Column(
            modifier = Modifier.bringIntoViewRequester(descriptionRequester),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            EntryDescriptionCard(
                note = entry.note,
                onNoteChange = { entry.note = it },
                onFocusChanged = onDescriptionFocusChanged
            )
            EntryExcludeCard(
                checked = entry.excludeAnalytics,
                onCheckedChange = { entry.excludeAnalytics = it }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Small pieces
// ─────────────────────────────────────────────────────────────────────────────

/** "3 TRANSACTIONS · ₹1,240 out · ₹500 in" */
@Composable
private fun BatchSummaryRow(entries: List<TransactionEntryState>) {
    val out = entries.filter { it.type != EntryType.INCOME }.sumOf { it.evaluatedAmount ?: 0.0 }
    val income = entries.filter { it.type == EntryType.INCOME }.sumOf { it.evaluatedAmount ?: 0.0 }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (entries.size == 1) "1 TRANSACTION" else "${entries.size} TRANSACTIONS",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.weight(1f)
        )
        if (out > 0) {
            Text(
                text = "₹${formatRupees(out)} out",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = entryTypeAccent(EntryType.EXPENSE)
            )
        }
        if (out > 0 && income > 0) {
            Text(
                text = "  ·  ",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (income > 0) {
            Text(
                text = "₹${formatRupees(income)} in",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = entryTypeAccent(EntryType.INCOME)
            )
        }
    }
}

@Composable
private fun InlineBanner(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    leading: (@Composable () -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            leading?.invoke()
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = contentColor,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 6.dp)
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = contentColor
                    )
                }
            }
            if (onDismiss != null) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = contentColor,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}
