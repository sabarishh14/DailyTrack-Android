package com.example.dailytrack_mobile.presentation.screens.money.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.dailytrack_mobile.presentation.screens.money.*
import com.example.dailytrack_mobile.presentation.util.Dimens

// ─────────────────────────────────────────────────────────────────────────────
// Transactions Tab
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun TransactionsTab(
    state: MoneyState,
    onAction: (MoneyAction) -> Unit
) {
    val canEdit = com.example.dailytrack_mobile.presentation.access.LocalAccess.current.canEdit(com.example.dailytrack_mobile.data.local.auth.AccessModule.MONEY)
    val dims = Dimens.current
    val filterState = state.analysisFilterState
    val balancesAllowed = com.example.dailytrack_mobile.presentation.access.LocalAccess.current.balancesVisible

    // "Show balances": each row's account balance after it, like a bank statement.
    // Remembered on this phone only.
    val context = LocalContext.current
    val uiPrefs = remember { context.getSharedPreferences("money_ui", android.content.Context.MODE_PRIVATE) }
    var showBalances by remember { mutableStateOf(uiPrefs.getBoolean("show_tx_balances", false)) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Search bar + Filter button + active filter chips (non-scrollable header)
        Column(
            modifier = Modifier.padding(
                start = dims.screenHorizontalPadding,
                end = dims.screenHorizontalPadding,
                top = dims.itemSpacingMedium,
                bottom = dims.itemSpacingSmall
            ),
            verticalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)
        ) {
            // Search Bar & Filter Button Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchBar(
                    query = state.searchQuery,
                    onQueryChange = { onAction(MoneyAction.UpdateSearchQuery(it)) },
                    modifier = Modifier.weight(1f)
                )

                if (balancesAllowed) {
                    BalancesToggleButton(
                        active = showBalances,
                        onClick = {
                            showBalances = !showBalances
                            uiPrefs.edit().putBoolean("show_tx_balances", showBalances).apply()
                        }
                    )
                }

                FilterButtonWithBadge(
                    activeCount = filterState.activeFilterCount,
                    onClick = { onAction(MoneyAction.SetFilterSheetVisible(true)) }
                )
            }

            // Active Filter Chips or Category Selector
            if (filterState.hasActiveFilters) {
                ActiveFiltersChipRow(
                    filterState = filterState,
                    onAction = onAction
                )
            } else {
                CategoryFilterRow(
                    categories = state.categoryFilters,
                    selected = state.selectedCategory,
                    onSelect = { onAction(MoneyAction.SelectCategory(it)) }
                )
            }

            // Result summary status row when filtered or searching
            if (filterState.hasActiveFilters || state.searchQuery.isNotBlank() || state.selectedCategory != "All") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (state.filteredTransactions.size == 1) "1 transaction found"
                               else "${state.filteredTransactions.size} transactions found",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Content: Loading, Error, Empty, or Transaction list
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                state.isLoading && state.transactions.isEmpty() -> {
                    // Initial loading state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(dims.itemSpacingLarge)
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp),
                                strokeCap = StrokeCap.Round
                            )
                            Text(
                                text = "Loading transactions...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                state.errorMessage != null && state.transactions.isEmpty() -> {
                    // Error state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(dims.itemSpacingLarge),
                            modifier = Modifier.padding(horizontal = dims.screenHorizontalPadding)
                        ) {
                            Text(
                                text = "😕",
                                style = MaterialTheme.typography.displayMedium
                            )
                            Text(
                                text = "Couldn't load transactions",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = state.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = { onAction(MoneyAction.Refresh) },
                                shape = RoundedCornerShape(dims.buttonCornerRadius)
                            ) {
                                Text("Retry", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }

                state.filteredTransactions.isEmpty() && !state.isLoading -> {
                    // Filtered or Empty state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium),
                            modifier = Modifier.padding(horizontal = dims.screenHorizontalPadding)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FilterAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = if (filterState.hasActiveFilters || state.searchQuery.isNotBlank() || state.selectedCategory != "All")
                                    "No Matching Transactions"
                                else "No transactions found",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (filterState.hasActiveFilters || state.searchQuery.isNotBlank() || state.selectedCategory != "All")
                                    "No transactions match your current search or active filters."
                                else "No transactions are available in your account.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            if (filterState.hasActiveFilters || state.searchQuery.isNotBlank() || state.selectedCategory != "All") {
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedButton(
                                    onClick = {
                                        onAction(MoneyAction.ResetAnalysisFilters)
                                        onAction(MoneyAction.UpdateSearchQuery(""))
                                        onAction(MoneyAction.SelectCategory("All"))
                                    },
                                    shape = RoundedCornerShape(dims.buttonCornerRadius)
                                ) {
                                    Text("Reset Filters & Search")
                                }
                            }
                        }
                    }
                }

                else -> {
                    // Transaction list with pagination
                    val listState = rememberLazyListState()

                    // Trigger load more when reaching near bottom
                    val shouldLoadMore = remember {
                        derivedStateOf {
                            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            val totalItems = listState.layoutInfo.totalItemsCount
                            lastVisibleItem >= totalItems - 5 // Load 5 items before end
                        }
                    }

                    LaunchedEffect(shouldLoadMore.value) {
                        if (shouldLoadMore.value && state.hasMore && !state.isLoadingMore) {
                            onAction(MoneyAction.LoadMore)
                        }
                    }

                    // Track single swiped item state
                    var swipedTransactionId by remember { mutableStateOf<Long?>(null) }

                    // Auto-close swiped item when scrolling
                    LaunchedEffect(listState.isScrollInProgress) {
                        if (listState.isScrollInProgress && swipedTransactionId != null) {
                            swipedTransactionId = null
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = dims.screenHorizontalPadding,
                            end = dims.screenHorizontalPadding,
                            bottom = if (state.isSelectionMode) dims.screenBottomPadding + 84.dp else dims.screenBottomPadding
                        ),
                        verticalArrangement = Arrangement.spacedBy(dims.itemSpacingMedium)
                    ) {
                        items(
                            items = state.filteredTransactions,
                            key = { it.id }
                        ) { transaction ->
                            val isSelected = transaction.id in state.selectedTransactionIds
                            SwipeableTransactionItem(
                                // View-only users: no swipe actions and no bulk selection.
                                swipeEnabled = canEdit,
                                showBalance = balancesAllowed && showBalances,
                                transaction = transaction,
                                isSwiped = swipedTransactionId == transaction.id,
                                onSwipeStateChanged = { isSwiped ->
                                    swipedTransactionId = if (isSwiped) transaction.id else if (swipedTransactionId == transaction.id) null else swipedTransactionId
                                },
                                isSelected = isSelected,
                                isSelectionMode = state.isSelectionMode,
                                onLongClick = {
                                    if (canEdit) onAction(MoneyAction.ToggleTransactionSelection(transaction.id))
                                },
                                onClick = {
                                    if (state.isSelectionMode) {
                                        onAction(MoneyAction.ToggleTransactionSelection(transaction.id))
                                    } else {
                                        onAction(MoneyAction.ShowTransactionDetail(transaction))
                                    }
                                },
                                onEdit = { onAction(MoneyAction.ShowEditDialog(transaction)) },
                                onDelete = { onAction(MoneyAction.ShowDeleteConfirmation(transaction)) }
                            )
                        }

                        // Loading more indicator
                        if (state.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = dims.itemSpacingLarge),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeCap = StrokeCap.Round
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Docked Selection Action Bar extending flush to navbar
            androidx.compose.animation.AnimatedVisibility(
                visible = state.isSelectionMode,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                BulkSelectionActionBar(
                    selectedCount = state.selectedTransactionIds.size,
                    isAllSelected = state.selectedTransactionIds.size == state.filteredTransactions.size && state.filteredTransactions.isNotEmpty(),
                    onSelectAllToggle = {
                        if (state.selectedTransactionIds.size == state.filteredTransactions.size) {
                            onAction(MoneyAction.ClearTransactionSelection)
                        } else {
                            onAction(MoneyAction.SelectAllTransactions)
                        }
                    },
                    onClearSelection = { onAction(MoneyAction.ClearTransactionSelection) },
                    onBulkEdit = { onAction(MoneyAction.ShowBulkEditSheet(true)) },
                    onBulkDelete = { onAction(MoneyAction.ShowBulkDeleteConfirmation(true)) }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Docked Bulk Selection Action Bar (Flush to bottom navbar)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun BulkSelectionActionBar(
    selectedCount: Int,
    isAllSelected: Boolean,
    onSelectAllToggle: () -> Unit,
    onClearSelection: () -> Unit,
    onBulkEdit: () -> Unit,
    onBulkDelete: () -> Unit
) {
    val dims = Dimens.current
    Surface(
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 12.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Close Button + Selection Count + Select All
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onClearSelection,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear Selection",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "$selectedCount",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                TextButton(
                    onClick = onSelectAllToggle,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isAllSelected) "Deselect" else "All",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Right: Edit and Delete Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onBulkEdit,
                    shape = RoundedCornerShape(dims.buttonCornerRadius),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Edit",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Button(
                    onClick = onBulkDelete,
                    shape = RoundedCornerShape(dims.buttonCornerRadius),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Delete",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Filter Button With Active Badge
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun FilterButtonWithBadge(
    activeCount: Int,
    onClick: () -> Unit
) {
    val dims = Dimens.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(dims.cardCornerRadius - 4.dp),
        color = if (activeCount > 0) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.height(dims.searchBarHeight)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = "Filters",
                tint = if (activeCount > 0) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            if (activeCount > 0) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Text(
                        text = "$activeCount",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

/** Same shape as the filter button beside it; lit while balances show. */
@Composable
private fun BalancesToggleButton(active: Boolean, onClick: () -> Unit) {
    val dims = Dimens.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(dims.cardCornerRadius - 4.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.height(dims.searchBarHeight)
    ) {
        Box(modifier = Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.AccountBalanceWallet,
                contentDescription = if (active) "Hide balances" else "Show balances",
                tint = if (active) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Active Filters Chip Row
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ActiveFiltersChipRow(
    filterState: AnalysisFilterState,
    onAction: (MoneyAction) -> Unit
) {
    val dims = Dimens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Clear button (matching Cash Flow style)
        Surface(
            onClick = {
                onAction(MoneyAction.ResetAnalysisFilters)
                onAction(MoneyAction.SelectCategory("All"))
                onAction(MoneyAction.UpdateSearchQuery(""))
            },
            shape = RoundedCornerShape(dims.buttonCornerRadius - 2.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
            modifier = Modifier.height(32.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear all",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Clear",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // Active Categories (Moved to FRONT right after Clear All!)
        filterState.categoryFilters.forEach { (cat, status) ->
            when (status) {
                ItemFilterStatus.INCLUDED -> {
                    ActiveFilterChipItem(
                        text = "+ $cat",
                        isIncluded = true,
                        onRemove = { onAction(MoneyAction.RemoveCategoryFilter(cat)) }
                    )
                }
                ItemFilterStatus.EXCLUDED -> {
                    ActiveFilterChipItem(
                        text = "- $cat",
                        isExcluded = true,
                        onRemove = { onAction(MoneyAction.RemoveCategoryFilter(cat)) }
                    )
                }
                ItemFilterStatus.NEUTRAL -> Unit
            }
        }

        // Active Quick Date Preset
        if (filterState.activeDatePreset == QuickFilterPreset.THIS_MONTH) {
            ActiveFilterChipItem(
                text = "This Month",
                onRemove = { onAction(MoneyAction.ToggleQuickPreset(QuickFilterPreset.THIS_MONTH)) }
            )
        }
        if (filterState.activeDatePreset == QuickFilterPreset.LAST_MONTH) {
            ActiveFilterChipItem(
                text = "Last Month",
                onRemove = { onAction(MoneyAction.ToggleQuickPreset(QuickFilterPreset.LAST_MONTH)) }
            )
        }

        // Active Financial Year
        if (!filterState.financialYear.isNullOrBlank() && filterState.financialYear != "All Time") {
            ActiveFilterChipItem(
                text = filterState.financialYear,
                onRemove = { onAction(MoneyAction.ClearFinancialYearFilter) }
            )
        }

        // Active Custom Date Range
        if (filterState.activeDatePreset == null) {
            filterState.formattedDateRange()?.let { rangeText ->
                ActiveFilterChipItem(
                    text = rangeText,
                    onRemove = { onAction(MoneyAction.ClearDateRangeFilter) }
                )
            }
        }

        // Active Types
        if (filterState.selectedTypes.isNotEmpty()) {
            filterState.selectedTypes.forEach { type ->
                ActiveFilterChipItem(
                    text = type.displayName,
                    onRemove = { onAction(MoneyAction.RemoveTypeFilter(type)) }
                )
            }
        }

        // Active Accounts
        val sortedAccountFilters = remember(filterState.accountFilters) {
            sortAccountsCanonical(filterState.accountFilters.keys.toList()).mapNotNull { acc ->
                filterState.accountFilters[acc]?.let { status -> acc to status }
            }
        }
        sortedAccountFilters.forEach { (acc, status) ->
            when (status) {
                ItemFilterStatus.INCLUDED -> {
                    ActiveFilterChipItem(
                        text = "+ $acc",
                        isIncluded = true,
                        onRemove = { onAction(MoneyAction.RemoveAccountFilter(acc)) }
                    )
                }
                ItemFilterStatus.EXCLUDED -> {
                    ActiveFilterChipItem(
                        text = "- $acc",
                        isExcluded = true,
                        onRemove = { onAction(MoneyAction.RemoveAccountFilter(acc)) }
                    )
                }
                ItemFilterStatus.NEUTRAL -> Unit
            }
        }
    }
}

@Composable
private fun ActiveFilterChipItem(
    text: String,
    onRemove: () -> Unit,
    isIncluded: Boolean = false,
    isExcluded: Boolean = false
) {
    val dims = Dimens.current
    val containerColor = when {
        isExcluded -> MaterialTheme.colorScheme.errorContainer
        isIncluded -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = when {
        isExcluded -> MaterialTheme.colorScheme.onErrorContainer
        isIncluded -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        shape = RoundedCornerShape(dims.buttonCornerRadius - 2.dp),
        color = containerColor,
        modifier = Modifier.height(32.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 10.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = contentColor
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(22.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove filter",
                    modifier = Modifier.size(14.dp),
                    tint = contentColor
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Search Bar
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val dims = Dimens.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .height(dims.searchBarHeight),
        placeholder = {
            Text(
                text = "Search transactions...",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = if (query.isNotBlank()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        } else null,
        shape = RoundedCornerShape(dims.cardCornerRadius - 4.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            cursorColor = MaterialTheme.colorScheme.primary
        ),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface
        )
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Category Filter Chips
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilterRow(
    categories: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    val dims = Dimens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { category ->
            val isSelected = category == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(category) },
                label = {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    )
                },
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(dims.buttonCornerRadius - 2.dp)
            )
        }
    }
}
