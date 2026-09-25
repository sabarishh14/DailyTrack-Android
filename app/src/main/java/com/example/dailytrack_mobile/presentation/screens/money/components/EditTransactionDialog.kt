package com.example.dailytrack_mobile.presentation.screens.money.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailytrack_mobile.presentation.components.rememberSheetHeight
import com.example.dailytrack_mobile.presentation.components.transaction.EntryAmountCard
import com.example.dailytrack_mobile.presentation.components.transaction.EntryCategoryPills
import com.example.dailytrack_mobile.presentation.components.transaction.EntryDateAccountRow
import com.example.dailytrack_mobile.presentation.components.transaction.EntryDescriptionCard
import com.example.dailytrack_mobile.presentation.components.transaction.EntryExcludeCard
import com.example.dailytrack_mobile.presentation.components.transaction.EntryHistory
import com.example.dailytrack_mobile.presentation.components.transaction.EntrySuggestionBar
import com.example.dailytrack_mobile.presentation.components.transaction.EntryType
import com.example.dailytrack_mobile.presentation.components.transaction.EntryTypeSelector
import com.example.dailytrack_mobile.presentation.components.transaction.TransactionEntryState
import com.example.dailytrack_mobile.presentation.components.transaction.entryTypeAccent
import com.example.dailytrack_mobile.presentation.components.transaction.rankDescriptionSuggestions
import com.example.dailytrack_mobile.presentation.screens.money.CategoryEmojis
import com.example.dailytrack_mobile.presentation.screens.money.Transaction
import com.example.dailytrack_mobile.presentation.screens.money.TransactionType
import com.example.dailytrack_mobile.presentation.screens.money.sortAccountsCanonical
import com.example.dailytrack_mobile.presentation.util.Dimens

private val defaultCategories = listOf(
    "Food", "Transport", "Shopping", "Entertainment", "Bills",
    "Health", "Education", "Cinema", "Daily Need", "Salary",
    "Freelance", "Investment", "Gift", "Other"
)

/** The DB type of a transaction as the shared entry form understands it. */
internal fun Transaction.entryType(): EntryType = when {
    type == TransactionType.CREDIT || rawType.equals("Credit", ignoreCase = true) -> EntryType.INCOME
    type == TransactionType.SAVINGS || isSavings -> EntryType.SAVINGS
    type == TransactionType.INVESTMENT || isInvestment -> EntryType.INVESTMENT
    else -> EntryType.EXPENSE
}

/** An editable copy of a saved transaction. */
internal fun Transaction.toEntryState(): TransactionEntryState = TransactionEntryState(
    id = id,
    type = entryType(),
    category = category,
    amount = if (amount % 1.0 == 0.0) "%.0f".format(amount) else "%.2f".format(amount),
    note = note.orEmpty(),
    account = bank,
    dateMillis = TransactionEntryState.parseApiDate(rawDate) ?: timestampMillis,
    excludeAnalytics = isExcluded
)

/** Top pills for the edit forms: the current category first, then the ones its type is most used with. */
internal fun editCategoryPills(current: String, typeCategories: List<String>): List<String> =
    (listOfNotNull(current.takeIf { it.isNotBlank() }) + typeCategories)
        .distinctBy { it.lowercase() }
        .take(6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionDialog(
    transaction: Transaction,
    availableAccounts: List<String>,
    availableCategories: List<String>,
    history: EntryHistory = EntryHistory.EMPTY,
    isUpdating: Boolean,
    onSave: (
        id: Long,
        type: String,
        category: String,
        amount: Double,
        note: String?,
        accountName: String,
        date: String,
        excludeAnalytics: Boolean
    ) -> Unit,
    onDelete: (Transaction) -> Unit,
    onDismiss: () -> Unit
) {
    val dims = Dimens.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current

    val entry = remember(transaction) { transaction.toEntryState() }
    var isNoteFocused by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showAccountPicker by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }

    val accountsList = remember(availableAccounts) {
        sortAccountsCanonical(availableAccounts.ifEmpty { defaultAccounts })
    }
    val allCategories = remember(availableCategories) { availableCategories.ifEmpty { defaultCategories } }
    val typeCategories = remember(entry.type, history, allCategories) { history.categoriesFor(entry.type, allCategories) }
    val categoryPills = remember(entry.category, typeCategories) { editCategoryPills(entry.category, typeCategories) }
    val noteSuggestions = remember(entry.type, entry.category, entry.note, history) {
        rankDescriptionSuggestions(entry.type, entry.category, entry.note, history)
    }

    BackHandler(enabled = isNoteFocused) { focusManager.clearFocus() }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = entry.dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { entry.dateMillis = it }
                    showDatePicker = false
                }) { Text("OK", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showCategoryPicker) {
        CategoryPickerDialog(
            allCategories = typeCategories,
            recentCategories = categoryPills,
            currentCategory = entry.category,
            onCategorySelected = {
                entry.category = it
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false }
        )
    }

    if (showAccountPicker) {
        AccountPickerDialog(
            accountsList = accountsList,
            currentAccount = entry.account.orEmpty(),
            onAccountSelected = {
                entry.account = it
                showAccountPicker = false
            },
            onDismiss = { showAccountPicker = false }
        )
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isUpdating) onDismiss() },
        contentWindowInsets = com.example.dailytrack_mobile.presentation.components.SheetContentInsets,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(rememberSheetHeight(0.85f))
                .imePadding()
        ) {
            // ── Header ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = dims.screenHorizontalPadding, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = entryTypeAccent(entry.type).copy(alpha = 0.14f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = CategoryEmojis.forCategory(entry.category), fontSize = 20.sp)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Edit Transaction",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = "#${transaction.id} · ${transaction.bank}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss, enabled = !isUpdating) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // ── Form — the same fields as Add Money ──────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = dims.screenHorizontalPadding, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                EntryTypeSelector(
                    selected = entry.type,
                    options = EntryType.entries,
                    onSelect = { entry.type = it }
                )
                EntryAmountCard(
                    amount = entry.amount,
                    onAmountChange = { entry.amount = it },
                    accent = entryTypeAccent(entry.type)
                )
                EntryDateAccountRow(
                    dateMillis = entry.dateMillis,
                    account = entry.account,
                    onDateClick = { showDatePicker = true },
                    onAccountClick = { showAccountPicker = true }
                )
                EntryCategoryPills(
                    pills = categoryPills,
                    selected = entry.category,
                    onSelect = { entry.category = it },
                    onMore = { showCategoryPicker = true }
                )
                EntryDescriptionCard(
                    note = entry.note,
                    onNoteChange = { entry.note = it },
                    onFocusChanged = { isNoteFocused = it }
                )
                EntryExcludeCard(
                    checked = entry.excludeAnalytics,
                    onCheckedChange = { entry.excludeAnalytics = it }
                )
            }

            // ── Footer: suggestions while typing a note, actions otherwise ──
            if (isNoteFocused && noteSuggestions.isNotEmpty()) {
                EntrySuggestionBar(
                    category = entry.category,
                    suggestions = noteSuggestions,
                    categorySuggestions = history.descriptionsFor(entry.type, entry.category),
                    onPick = {
                        entry.note = it
                        focusManager.clearFocus()
                    },
                    onDone = { focusManager.clearFocus() }
                )
            } else if (!isNoteFocused) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dims.screenHorizontalPadding, vertical = dims.itemSpacingLarge),
                    horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { onDelete(transaction) },
                        enabled = !isUpdating,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(dims.iconSizeSmall))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
                    }

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            onSave(
                                transaction.id,
                                entry.type.dbValue,
                                entry.category.trim().ifEmpty { "Other" },
                                entry.evaluatedAmount ?: 0.0,
                                entry.note.trim().takeIf { it.isNotBlank() },
                                entry.account.orEmpty(),
                                entry.apiDate,
                                entry.excludeAnalytics
                            )
                        },
                        enabled = !isUpdating && entry.isComplete,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        if (isUpdating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(dims.iconSizeMedium))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Changes", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }
    }
}
