package com.example.dailytrack_mobile.presentation.screens.money.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailytrack_mobile.data.remote.dto.BulkEditTransactionItemDto
import com.example.dailytrack_mobile.presentation.screens.money.CategoryEmojis
import com.example.dailytrack_mobile.presentation.screens.money.DEFAULT_CANONICAL_ACCOUNTS
import com.example.dailytrack_mobile.presentation.screens.money.Transaction
import com.example.dailytrack_mobile.presentation.screens.money.TransactionType
import com.example.dailytrack_mobile.presentation.screens.money.sortAccountsCanonical
import com.example.dailytrack_mobile.presentation.util.Dimens
import com.example.dailytrack_mobile.presentation.components.transaction.EntryAmountCard
import com.example.dailytrack_mobile.presentation.components.transaction.EntryCategoryPills
import com.example.dailytrack_mobile.presentation.components.transaction.EntryDateAccountRow
import com.example.dailytrack_mobile.presentation.components.transaction.EntryDescriptionCard
import com.example.dailytrack_mobile.presentation.components.transaction.EntryEditorHeader
import com.example.dailytrack_mobile.presentation.components.transaction.EntryExcludeCard
import com.example.dailytrack_mobile.presentation.components.transaction.EntryHistory
import com.example.dailytrack_mobile.presentation.components.transaction.EntrySummaryCard
import com.example.dailytrack_mobile.presentation.components.transaction.EntryType
import com.example.dailytrack_mobile.presentation.components.transaction.EntryTypeSelector
import com.example.dailytrack_mobile.presentation.components.transaction.TransactionEntryState
import com.example.dailytrack_mobile.presentation.components.transaction.entryTypeAccent
import com.example.dailytrack_mobile.presentation.components.transaction.rankDescriptionSuggestions
import java.text.SimpleDateFormat
import java.util.*
import com.example.dailytrack_mobile.presentation.components.rememberSheetHeight

private val defaultCategories = listOf(
    "Food", "Transport", "Shopping", "Entertainment", "Bills",
    "Health", "Education", "Cinema", "Daily Need", "Salary",
    "Freelance", "Investment", "Gift", "Other"
)

internal val defaultAccounts = DEFAULT_CANONICAL_ACCOUNTS

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BulkEditTransactionsSheet(
    transactions: List<Transaction>,
    availableAccounts: List<String>,
    availableCategories: List<String>,
    history: EntryHistory = EntryHistory.EMPTY,
    isUpdating: Boolean,
    onSave: (List<BulkEditTransactionItemDto>) -> Unit,
    onDismiss: () -> Unit
) {
    val dims = Dimens.current
    val focusManager = LocalFocusManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val apiDateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val displayDateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }

    // Tab state: 0 = "Apply to All", 1 = "Per Transaction"
    var selectedTab by remember { mutableIntStateOf(0) }

    // Feedback badge for batch actions (e.g. "Applied 'Food' to all items")
    var batchFeedbackMessage by remember { mutableStateOf<String?>(null) }

    // Initialize list of editable items from selected transactions
    val items = remember(transactions) { transactions.map { it.toEntryState() } }
    // Per-transaction tab: one entry open at a time, the rest folded into cards.
    var expandedItemId by remember { mutableStateOf<Long?>(null) }

    val accountsList = remember(availableAccounts) {
        val list = if (availableAccounts.isNotEmpty()) availableAccounts else defaultAccounts
        sortAccountsCanonical(list)
    }
    val allCategories = remember(availableCategories) {
        if (availableCategories.isNotEmpty()) availableCategories else defaultCategories
    }
    // Batch changes cover every selected type, so offer what any of them has been used with.
    val selectedTypes = items.map { it.type }.distinct()
    val batchCategories = remember(selectedTypes, history, allCategories) {
        selectedTypes.flatMap { history.categoriesFor(it, allCategories) }.distinct()
    }
    val recentCategories = batchCategories.take(8)

    val totalAmount = items.sumOf { it.evaluatedAmount ?: 0.0 }

    // Category Picker Dialog states (Batch & Individual)
    var showBatchCategoryPicker by remember { mutableStateOf(false) }
    var categoryPickerTargetItem by remember { mutableStateOf<TransactionEntryState?>(null) }

    // Account Picker Dialog states (Batch & Individual)
    var showBatchAccountPicker by remember { mutableStateOf(false) }
    var accountPickerTargetItem by remember { mutableStateOf<TransactionEntryState?>(null) }

    // Description / Note Suggestions state
    var batchDescription by remember { mutableStateOf("") }
    var isBatchDescriptionFocused by remember { mutableStateOf(false) }
    var activeDescriptionTargetItem by remember { mutableStateOf<TransactionEntryState?>(null) }

    val isAnyDescriptionFocused = isBatchDescriptionFocused || activeDescriptionTargetItem != null

    // Intercept back gesture while Description is focused to dismiss keyboard cleanly
    BackHandler(enabled = isAnyDescriptionFocused) {
        focusManager.clearFocus()
        isBatchDescriptionFocused = false
        activeDescriptionTargetItem = null
    }

    val currentDescriptionText = when {
        isBatchDescriptionFocused -> batchDescription
        activeDescriptionTargetItem != null -> activeDescriptionTargetItem?.note ?: ""
        else -> ""
    }

    // One item's note follows its own type + category; the batch note follows every selected type,
    // narrowed to the category when they all share one.
    val descriptionTarget = activeDescriptionTargetItem
    val sharedCategory = items.map { it.category.trim() }.distinct().singleOrNull().orEmpty()
    val descriptionSuggestions = if (descriptionTarget != null) {
        rankDescriptionSuggestions(descriptionTarget.type, descriptionTarget.category, currentDescriptionText, history, limit = 40)
    } else {
        selectedTypes
            .flatMap { rankDescriptionSuggestions(it, sharedCategory, currentDescriptionText, history, limit = 40) }
            .distinct()
            .take(40)
    }

    // Global Date Picker for Batch Apply
    var showBatchDatePicker by remember { mutableStateOf(false) }
    if (showBatchDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showBatchDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { ms ->
                        items.forEach { it.dateMillis = ms }
                        batchFeedbackMessage = "Applied ${displayDateFormat.format(Date(ms))} to all items"
                    }
                    showBatchDatePicker = false
                }) {
                    Text("Apply to All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Single item date picker
    var datePickerTargetItem by remember { mutableStateOf<TransactionEntryState?>(null) }
    datePickerTargetItem?.let { target ->
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = target.dateMillis)
        DatePickerDialog(
            onDismissRequest = { datePickerTargetItem = null },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { ms ->
                        target.dateMillis = ms
                    }
                    datePickerTargetItem = null
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { datePickerTargetItem = null }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // ── Batch & Individual Category Picker Dialog ─────────────────────
    if (showBatchCategoryPicker) {
        CategoryPickerDialog(
            allCategories = batchCategories,
            recentCategories = recentCategories,
            onCategorySelected = { cat ->
                items.forEach { it.category = cat }
                batchFeedbackMessage = "Applied \"$cat\" to all ${items.size} items"
                showBatchCategoryPicker = false
            },
            onDismiss = { showBatchCategoryPicker = false }
        )
    }

    categoryPickerTargetItem?.let { target ->
        val targetCategories = history.categoriesFor(target.type, allCategories)
        CategoryPickerDialog(
            allCategories = targetCategories,
            recentCategories = editCategoryPills(target.category, targetCategories),
            onCategorySelected = { cat ->
                target.category = cat
                categoryPickerTargetItem = null
            },
            onDismiss = { categoryPickerTargetItem = null }
        )
    }

    // ── Batch & Individual Account Picker Dialog ──────────────────────
    if (showBatchAccountPicker) {
        AccountPickerDialog(
            accountsList = accountsList,
            currentAccount = items.firstOrNull()?.account.orEmpty(),
            onAccountSelected = { acc ->
                items.forEach { it.account = acc }
                batchFeedbackMessage = "Applied \"$acc\" to all ${items.size} items"
                showBatchAccountPicker = false
            },
            onDismiss = { showBatchAccountPicker = false }
        )
    }

    accountPickerTargetItem?.let { target ->
        AccountPickerDialog(
            accountsList = accountsList,
            currentAccount = target.account.orEmpty(),
            onAccountSelected = { acc ->
                target.account = acc
                accountPickerTargetItem = null
            },
            onDismiss = { accountPickerTargetItem = null }
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(rememberSheetHeight(0.79f))
                .imePadding()
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
            // ── Top Header ───────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dims.screenHorizontalPadding, vertical = dims.itemSpacingMedium),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Bulk Edit",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "${items.size} Selected",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Total: ₹${"%,.2f".format(totalAmount)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    enabled = !isUpdating
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Clean Mode Segmented Bar ─────────────────────────────────────
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(
                                "Apply to All",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        }
                    }
                )

                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(
                                "Per Transaction (${items.size})",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        }
                    }
                )
            }

            // ── Body based on Tab ────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (selectedTab == 0) {
                    // TAB 0: APPLY TO ALL
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = dims.screenHorizontalPadding,
                            vertical = dims.itemSpacingLarge
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Feedback banner
                        batchFeedbackMessage?.let { msg ->
                            item {
                                Surface(
                                    shape = RoundedCornerShape(dims.buttonCornerRadius),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = msg,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { batchFeedbackMessage = null },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Dismiss",
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 1. Batch Category
                        item {
                            Card(
                                shape = RoundedCornerShape(dims.cardCornerRadius),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Category",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Tap to apply to all ${items.size} items",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        recentCategories.forEach { cat ->
                                            val emoji = CategoryEmojis.forCategory(cat)
                                            FilterChip(
                                                selected = false,
                                                onClick = {
                                                    items.forEach { it.category = cat }
                                                    batchFeedbackMessage = "Applied \"$cat\" to all ${items.size} items"
                                                },
                                                label = { Text("$emoji $cat", style = MaterialTheme.typography.bodyMedium) },
                                                shape = RoundedCornerShape(dims.buttonCornerRadius - 2.dp)
                                            )
                                        }

                                        // + More categories chip with search popup
                                        FilterChip(
                                            selected = false,
                                            onClick = { showBatchCategoryPicker = true },
                                            label = {
                                                Text(
                                                    text = "+ More (${allCategories.size})",
                                                    style = MaterialTheme.typography.labelMedium.copy(
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "Search more categories",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                                labelColor = MaterialTheme.colorScheme.primary
                                            ),
                                            border = FilterChipDefaults.filterChipBorder(
                                                enabled = true,
                                                selected = false,
                                                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                            ),
                                            shape = RoundedCornerShape(dims.buttonCornerRadius - 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Batch Account
                        item {
                            Card(
                                shape = RoundedCornerShape(dims.cardCornerRadius),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Account",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Tap to apply to all ${items.size} items",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        accountsList.take(8).forEach { acc ->
                                            FilterChip(
                                                selected = false,
                                                onClick = {
                                                    items.forEach { it.account = acc }
                                                    batchFeedbackMessage = "Applied \"$acc\" to all ${items.size} items"
                                                },
                                                label = { Text(acc, style = MaterialTheme.typography.bodyMedium) },
                                                shape = RoundedCornerShape(dims.buttonCornerRadius - 2.dp)
                                            )
                                        }

                                        // + More accounts chip with search/picker popup
                                        FilterChip(
                                            selected = false,
                                            onClick = { showBatchAccountPicker = true },
                                            label = {
                                                Text(
                                                    text = "+ More (${accountsList.size})",
                                                    style = MaterialTheme.typography.labelMedium.copy(
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "Search more accounts",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                                labelColor = MaterialTheme.colorScheme.primary
                                            ),
                                            border = FilterChipDefaults.filterChipBorder(
                                                enabled = true,
                                                selected = false,
                                                borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                            ),
                                            shape = RoundedCornerShape(dims.buttonCornerRadius - 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Batch Type
                        item {
                            Card(
                                shape = RoundedCornerShape(dims.cardCornerRadius),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Transaction Type",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Tap to apply to all ${items.size} items",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(
                                            "Debit" to "Expense",
                                            "Credit" to "Income",
                                            "Savings" to "Savings",
                                            "Investment" to "Invest"
                                        ).forEach { (dbVal, label) ->
                                            Surface(
                                                shape = RoundedCornerShape(dims.buttonCornerRadius),
                                                color = MaterialTheme.colorScheme.surface,
                                                border = androidx.compose.foundation.BorderStroke(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                                ),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(dims.buttonCornerRadius))
                                                    .clickable {
                                                        items.forEach { it.type = EntryType.fromDb(dbVal) }
                                                        batchFeedbackMessage = "Applied \"$label\" type to all items"
                                                    }
                                            ) {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.padding(vertical = 10.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 4. Batch Date
                        item {
                            Card(
                                shape = RoundedCornerShape(dims.cardCornerRadius),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Date",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Tap to apply to all ${items.size} items",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        FilterChip(
                                            selected = false,
                                            onClick = {
                                                val now = System.currentTimeMillis()
                                                items.forEach { it.dateMillis = now }
                                                batchFeedbackMessage = "Applied Today to all items"
                                            },
                                            label = { Text("Today", style = MaterialTheme.typography.labelSmall) },
                                            shape = RoundedCornerShape(dims.buttonCornerRadius - 2.dp)
                                        )

                                        FilterChip(
                                            selected = false,
                                            onClick = {
                                                val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                                                items.forEach { it.dateMillis = cal.timeInMillis }
                                                batchFeedbackMessage = "Applied Yesterday to all items"
                                            },
                                            label = { Text("Yesterday", style = MaterialTheme.typography.labelSmall) },
                                            shape = RoundedCornerShape(dims.buttonCornerRadius - 2.dp)
                                        )

                                        OutlinedButton(
                                            onClick = { showBatchDatePicker = true },
                                            shape = RoundedCornerShape(dims.buttonCornerRadius - 2.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(imageVector = Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Choose Date...", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }

                        // 5. Batch Description / Note
                        item {
                            Card(
                                shape = RoundedCornerShape(dims.cardCornerRadius),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Description / Note",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Tap to apply note to all items",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = batchDescription,
                                            onValueChange = { batchDescription = it },
                                            placeholder = { Text("Note for all items...", style = MaterialTheme.typography.bodyMedium) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(dims.buttonCornerRadius - 2.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .onFocusChanged {
                                                    isBatchDescriptionFocused = it.isFocused
                                                    if (it.isFocused) activeDescriptionTargetItem = null
                                                }
                                        )

                                        Button(
                                            onClick = {
                                                items.forEach { it.note = batchDescription }
                                                batchFeedbackMessage = "Applied note to all ${items.size} items"
                                                focusManager.clearFocus()
                                            },
                                            shape = RoundedCornerShape(dims.buttonCornerRadius - 2.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                                        ) {
                                            Text("Apply", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                        }
                                    }
                                }
                            }
                        }

                        // 5. Batch Exclude Analytics
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(dims.cardCornerRadius),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Exclude from Spending Analyser",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Apply exclusion to all selected transactions",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        TextButton(
                                            onClick = {
                                                items.forEach { it.excludeAnalytics = true }
                                                batchFeedbackMessage = "Excluded all items from analyser"
                                            }
                                        ) {
                                            Text("Exclude All", style = MaterialTheme.typography.labelSmall)
                                        }
                                        TextButton(
                                            onClick = {
                                                items.forEach { it.excludeAnalytics = false }
                                                batchFeedbackMessage = "Included all items in analyser"
                                            }
                                        ) {
                                            Text("Include All", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }

                        // Prompt to switch to individual customize tab
                        item {
                            FilledTonalButton(
                                onClick = { selectedTab = 1 },
                                shape = RoundedCornerShape(dims.buttonCornerRadius),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Customize Separate Transactions (${items.size}) →", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                } else {
                    // TAB 1: PER TRANSACTION (Clean, fast, individual customizations)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = dims.screenHorizontalPadding,
                            vertical = dims.itemSpacingLarge
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                text = "Tap a transaction to change just that one",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        itemsIndexed(
                            items = items,
                            key = { _, itm -> itm.id }
                        ) { index, item ->
                            if (item.id == expandedItemId) {
                                // The same fields as Add Money and Edit, one entry at a time.
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    EntryEditorHeader(
                                        number = index + 1,
                                        total = items.size,
                                        onCollapse = {
                                            focusManager.clearFocus()
                                            expandedItemId = null
                                        }
                                    )
                                    EntryTypeSelector(
                                        selected = item.type,
                                        options = EntryType.entries,
                                        onSelect = { item.type = it }
                                    )
                                    EntryAmountCard(
                                        amount = item.amount,
                                        onAmountChange = { item.amount = it },
                                        accent = entryTypeAccent(item.type),
                                        compact = true
                                    )
                                    EntryDateAccountRow(
                                        dateMillis = item.dateMillis,
                                        account = item.account,
                                        onDateClick = { datePickerTargetItem = item },
                                        onAccountClick = { accountPickerTargetItem = item }
                                    )
                                    EntryCategoryPills(
                                        pills = editCategoryPills(item.category, history.categoriesFor(item.type, allCategories)),
                                        selected = item.category,
                                        onSelect = { item.category = it },
                                        onMore = { categoryPickerTargetItem = item }
                                    )
                                    EntryDescriptionCard(
                                        note = item.note,
                                        onNoteChange = { item.note = it },
                                        onFocusChanged = { focused ->
                                            if (focused) {
                                                activeDescriptionTargetItem = item
                                                isBatchDescriptionFocused = false
                                            } else if (activeDescriptionTargetItem == item) {
                                                activeDescriptionTargetItem = null
                                            }
                                        }
                                    )
                                    EntryExcludeCard(
                                        checked = item.excludeAnalytics,
                                        onCheckedChange = { item.excludeAnalytics = it }
                                    )
                                }
                            } else {
                                EntrySummaryCard(
                                    number = index + 1,
                                    entry = item,
                                    onClick = {
                                        focusManager.clearFocus()
                                        expandedItemId = item.id
                                    }
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // ── Sticky Bottom Footer or Docked Suggestion Accessory Bar ──────
            if (isAnyDescriptionFocused) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 6.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "SUGGESTIONS",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        letterSpacing = 1.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "• 1-tap to fill",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }

                            TextButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    isBatchDescriptionFocused = false
                                    activeDescriptionTargetItem = null
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = "Done",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            descriptionSuggestions.forEach { suggestion ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier.clickable {
                                        if (isBatchDescriptionFocused) {
                                            batchDescription = suggestion
                                            items.forEach { it.note = suggestion }
                                            batchFeedbackMessage = "Applied note to all ${items.size} items"
                                        } else if (activeDescriptionTargetItem != null) {
                                            activeDescriptionTargetItem?.note = suggestion
                                        }
                                        focusManager.clearFocus()
                                        isBatchDescriptionFocused = false
                                        activeDescriptionTargetItem = null
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = suggestion,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dims.screenHorizontalPadding, vertical = dims.itemSpacingLarge),
                    horizontalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isUpdating,
                        shape = RoundedCornerShape(dims.buttonCornerRadius),
                        modifier = Modifier.weight(0.35f)
                    ) {
                        Text("Cancel", style = MaterialTheme.typography.labelLarge)
                    }

                    val allAmountsValid = items.all { (it.evaluatedAmount ?: 0.0) > 0.0 && !it.account.isNullOrBlank() }

                    Button(
                        onClick = {
                            val updates = items.map { item ->
                                BulkEditTransactionItemDto(
                                    id = item.id,
                                    account = item.account.orEmpty(),
                                    date = item.apiDate,
                                    type = item.type.dbValue,
                                    heading = item.category.trim().ifEmpty { "Other" },
                                    description = item.note.trim(),
                                    amount = item.evaluatedAmount ?: 0.0,
                                    excludeAnalytics = item.excludeAnalytics
                                )
                            }
                            onSave(updates)
                        },
                        enabled = !isUpdating && allAmountsValid,
                        shape = RoundedCornerShape(dims.buttonCornerRadius),
                        modifier = Modifier
                            .weight(0.65f)
                            .height(dims.searchBarHeight)
                    ) {
                        if (isUpdating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Saving...", style = MaterialTheme.typography.labelLarge)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save All (${items.size})", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}
}
