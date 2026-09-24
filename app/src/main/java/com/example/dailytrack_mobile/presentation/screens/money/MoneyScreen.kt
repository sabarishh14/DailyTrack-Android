package com.example.dailytrack_mobile.presentation.screens.money

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.dailytrack_mobile.presentation.components.DailyTrackPullToRefreshBox
import com.example.dailytrack_mobile.presentation.components.transaction.EntryHistory
import com.example.dailytrack_mobile.presentation.components.transaction.HistoryRow
import com.example.dailytrack_mobile.presentation.screens.money.components.AnalysisTab
import com.example.dailytrack_mobile.presentation.screens.money.components.BulkDeleteConfirmationDialog
import com.example.dailytrack_mobile.presentation.screens.money.components.BudgetManagerSheet
import com.example.dailytrack_mobile.presentation.screens.money.components.BulkEditTransactionsSheet
import com.example.dailytrack_mobile.presentation.screens.money.components.DeleteConfirmationDialog
import com.example.dailytrack_mobile.presentation.screens.money.components.EditTransactionDialog
import com.example.dailytrack_mobile.presentation.screens.money.components.FilterBottomSheet
import com.example.dailytrack_mobile.presentation.screens.money.components.TransactionDetailBottomSheet
import com.example.dailytrack_mobile.presentation.screens.money.components.TransactionsTab
import com.example.dailytrack_mobile.presentation.util.Dimens
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// Sub-tabs & Dialogs for unified horizontal paging
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun MoneyCashFlowTab(
    state: MoneyState,
    onAction: (MoneyAction) -> Unit,
    onNavigateToTransactions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dims = Dimens.current
    DailyTrackPullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { onAction(MoneyAction.Refresh) },
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            PillTabBar(
                selectedIndex = 0,
                tabs = listOf("Cash Flow", "Transactions"),
                onTabSelected = { targetIndex ->
                    if (targetIndex == 1) {
                        onNavigateToTransactions()
                    }
                },
                modifier = Modifier.padding(
                    start = dims.screenHorizontalPadding,
                    end = dims.screenHorizontalPadding,
                    top = dims.itemSpacingMedium,
                    bottom = dims.itemSpacingSmall
                )
            )

            AnalysisTab(
                state = state,
                onAction = onAction,
                onNavigateToTransactions = onNavigateToTransactions
            )
        }
    }
}

@Composable
fun MoneyTransactionsTab(
    state: MoneyState,
    onAction: (MoneyAction) -> Unit,
    onNavigateToCashFlow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dims = Dimens.current
    DailyTrackPullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { onAction(MoneyAction.Refresh) },
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            PillTabBar(
                selectedIndex = 1,
                tabs = listOf("Cash Flow", "Transactions"),
                onTabSelected = { targetIndex ->
                    if (targetIndex == 0) {
                        onNavigateToCashFlow()
                    }
                },
                modifier = Modifier.padding(
                    start = dims.screenHorizontalPadding,
                    end = dims.screenHorizontalPadding,
                    top = dims.itemSpacingMedium,
                    bottom = dims.itemSpacingSmall
                )
            )

            TransactionsTab(
                state = state,
                onAction = onAction
            )
        }
    }
}

@Composable
fun MoneyDialogsAndSheets(
    state: MoneyState,
    onAction: (MoneyAction) -> Unit
) {
    // Transaction Detail Bottom Sheet
    state.detailTransaction?.let { tx ->
        TransactionDetailBottomSheet(
            transaction = tx,
            onEdit = { transactionToEdit ->
                onAction(MoneyAction.ShowEditDialog(transactionToEdit))
            },
            onDelete = { transactionToDelete ->
                onAction(MoneyAction.ShowDeleteConfirmation(transactionToDelete))
            },
            onDismiss = {
                onAction(MoneyAction.DismissDialogs)
            },
            canEdit = com.example.dailytrack_mobile.presentation.access.LocalAccess.current.canEdit(com.example.dailytrack_mobile.data.local.auth.AccessModule.MONEY)
        )
    }

    // Categories and descriptions per type, from the whole history as it streams in
    val entryHistory = remember(state.transactions) {
        EntryHistory.from(state.transactions.map { HistoryRow(it.rawType, it.category, it.note, it.rawDate) })
    }

    // Edit Transaction Dialog
    state.editingTransaction?.let { tx ->
        EditTransactionDialog(
            transaction = tx,
            availableAccounts = state.allAvailableAccounts,
            availableCategories = state.allAvailableCategories,
            history = entryHistory,
            isUpdating = state.isUpdating,
            onSave = { id, type, category, amount, note, accountName, date, excludeAnalytics ->
                onAction(
                    MoneyAction.UpdateTransaction(
                        id = id,
                        type = type,
                        category = category,
                        amount = amount,
                        note = note,
                        accountName = accountName,
                        date = date,
                        excludeAnalytics = excludeAnalytics
                    )
                )
            },
            onDelete = { transactionToDelete ->
                onAction(MoneyAction.ShowDeleteConfirmation(transactionToDelete))
            },
            onDismiss = {
                onAction(MoneyAction.DismissDialogs)
            }
        )
    }

    // Delete Confirmation Dialog
    state.deletingTransaction?.let { tx ->
        DeleteConfirmationDialog(
            transaction = tx,
            isDeleting = state.isDeleting,
            onConfirm = {
                onAction(MoneyAction.DeleteTransaction(tx.id))
            },
            onDismiss = {
                onAction(MoneyAction.DismissDialogs)
            }
        )
    }

    // Filter Bottom Sheet for Spending Analyzer
    if (state.isFilterSheetVisible) {
        FilterBottomSheet(
            filterState = state.analysisFilterState,
            allCategories = state.mostUsedCategories,
            allAccounts = state.allAvailableAccounts,
            onApply = { updatedFilters ->
                onAction(MoneyAction.ApplyAnalysisFilters(updatedFilters))
            },
            onDismiss = {
                onAction(MoneyAction.SetFilterSheetVisible(false))
            }
        )
    }

    // Budget Editor Bottom Sheet
    if (state.isBudgetSheetVisible) {
        BudgetManagerSheet(
            categories = state.mostUsedCategories,
            currentBudgets = state.budgets,
            suggestions = state.budgetSuggestions,
            isSaving = state.isSavingBudgets,
            onSave = { limits ->
                onAction(MoneyAction.SaveBudgets(limits))
            },
            onDismiss = {
                onAction(MoneyAction.SetBudgetSheetVisible(false))
            }
        )
    }

    // Bulk Edit Transactions Bottom Sheet
    if (state.showBulkEditSheet && state.selectedTransactions.isNotEmpty()) {
        BulkEditTransactionsSheet(
            transactions = state.selectedTransactions,
            availableAccounts = state.allAvailableAccounts,
            availableCategories = state.allAvailableCategories,
            history = entryHistory,
            isUpdating = state.isBulkUpdating,
            onSave = { updates ->
                onAction(MoneyAction.ExecuteBulkEdit(updates))
            },
            onDismiss = {
                onAction(MoneyAction.ShowBulkEditSheet(false))
            }
        )
    }

    // Bulk Delete Confirmation Dialog
    if (state.showBulkDeleteConfirm && state.selectedTransactionIds.isNotEmpty()) {
        BulkDeleteConfirmationDialog(
            selectedCount = state.selectedTransactionIds.size,
            isDeleting = state.isBulkDeleting,
            onConfirm = {
                onAction(MoneyAction.ExecuteBulkDelete)
            },
            onDismiss = {
                onAction(MoneyAction.ShowBulkDeleteConfirmation(false))
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Standalone Money composable (backward compatibility & standalone screens)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun MoneyScreen(
    viewModel: MoneyVM = hiltViewModel(),
    initialTab: Int? = null,
    onTabConsumed: () -> Unit = {},
    onSelectionModeChange: (Boolean) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(state.selectedTab.coerceIn(0, 1)) }

    androidx.activity.compose.BackHandler(enabled = state.isSelectionMode) {
        viewModel.onAction(MoneyAction.ClearTransactionSelection)
    }

    LaunchedEffect(state.isSelectionMode) {
        onSelectionModeChange(state.isSelectionMode)
    }

    LaunchedEffect(initialTab) {
        if (initialTab != null) {
            selectedTab = initialTab.coerceIn(0, 1)
            viewModel.onAction(MoneyAction.SelectTab(selectedTab))
            onTabConsumed()
        }
    }

    LaunchedEffect(state.actionMessage) {
        state.actionMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onAction(MoneyAction.ClearActionMessage)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (selectedTab == 0) {
            MoneyCashFlowTab(
                state = state,
                onAction = viewModel::onAction,
                onNavigateToTransactions = {
                    selectedTab = 1
                    viewModel.onAction(MoneyAction.SelectTab(1))
                }
            )
        } else {
            MoneyTransactionsTab(
                state = state,
                onAction = viewModel::onAction,
                onNavigateToCashFlow = {
                    selectedTab = 0
                    viewModel.onAction(MoneyAction.SelectTab(0))
                }
            )
        }

        MoneyDialogsAndSheets(
            state = state,
            onAction = viewModel::onAction
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pill Tab Bar
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PillTabBar(
    selectedIndex: Int,
    tabs: List<String>,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val dims = Dimens.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dims.buttonCornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            tabs.forEachIndexed { index, title ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(dims.buttonCornerRadius - 2.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(index) }
                        .padding(vertical = dims.itemSpacingMedium),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
