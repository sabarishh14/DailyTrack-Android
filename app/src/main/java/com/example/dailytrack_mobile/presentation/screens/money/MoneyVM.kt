package com.example.dailytrack_mobile.presentation.screens.money

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailytrack_mobile.data.local.datastore.DemoModeManager
import com.example.dailytrack_mobile.data.repository.MoneyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class MoneyVM @Inject constructor(
    private val repository: MoneyRepository,
    private val demoModeManager: DemoModeManager
) : ViewModel() {

    private val _state = MutableStateFlow(MoneyState())
    val state: StateFlow<MoneyState> = _state.asStateFlow()

    companion object {
        private const val PAGE_SIZE = 50
    }

    private var loadJob: kotlinx.coroutines.Job? = null
    private var progressiveFetchJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            demoModeManager.isDemoModeEnabledFlow.collect {
                loadInitialData()
            }
        }
        viewModelScope.launch {
            repository.dataUpdateFlow.collect {
                loadInitialData()
            }
        }
    }

    private fun loadInitialData(forceRefresh: Boolean = false) {
        loadJob?.cancel()
        progressiveFetchJob?.cancel()
        if (forceRefresh) {
            repository.clearCache()
            _state.update { it.copy(isRefreshing = true, errorMessage = null) }
        } else {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
        }
        loadJob = viewModelScope.launch {
            // Launch all of them in parallel
            val accountsDeferred = async { repository.getAccounts(forceRefresh = forceRefresh) }
            val transactionsDeferred = async { repository.getTransactions(limit = PAGE_SIZE, offset = 0, forceRefresh = forceRefresh) }
            val categoriesDeferred = async { repository.getCategories(forceRefresh = forceRefresh) }
            val budgetsDeferred = async { repository.getBudgets(forceRefresh = forceRefresh) }

            val accountsResult = accountsDeferred.await()
            val transactionsResult = transactionsDeferred.await()
            val categoriesResult = categoriesDeferred.await()
            val budgetsResult = budgetsDeferred.await()

            _state.update { current ->
                var updated = current.copy(isLoading = false, isRefreshing = false)

                // Process accounts
                accountsResult.onSuccess { accounts ->
                    updated = updated.copy(
                        accounts = accounts.map { dto ->
                            AccountInfo(
                                account = dto.account,
                                balance = dto.balance ?: 0.0,
                                realBalance = dto.realBalance,
                                balanceTracked = dto.balanceTracked
                            )
                        }
                    )
                }

                // Process transactions
                transactionsResult.onSuccess { response ->
                    updated = updated.copy(
                        transactions = response.transactions.map { it.toDomain() },
                        currentOffset = response.offset + response.limit,
                        hasMore = response.hasMore,
                        totalTransactionCount = response.total
                    )
                }.onFailure { error ->
                    updated = updated.copy(errorMessage = error.message ?: "Failed to load transactions")
                }

                // Process categories
                categoriesResult.onSuccess { categories ->
                    updated = updated.copy(apiCategories = categories)
                }

                // Budgets are optional garnish — a failure here must not blank the screen
                budgetsResult.onSuccess { budgets ->
                    updated = updated.copy(budgets = budgets.associate { it.category to it.monthlyLimit })
                }

                updated
            }

            // Fire off a background task to progressively fetch the REST of the transactions
            // for the spending analyser, exactly like the Web app does.
            if (_state.value.hasMore) {
                fetchAllTransactionsProgressively(forceRefresh = forceRefresh)
            }

            // Quietly warm the budget suggestions too, so by the time someone
            // actually opens the budget editor they're already there instead of
            // popping in — and reflowing the whole list — mid-look.
            prefetchBudgetSuggestions()
        }
    }

    private fun prefetchBudgetSuggestions() {
        if (_state.value.budgetSuggestions.isNotEmpty()) return
        viewModelScope.launch {
            repository.getBudgetSuggestions().onSuccess { suggestions ->
                _state.update { it.copy(budgetSuggestions = suggestions) }
            }
        }
    }

    private fun fetchAllTransactionsProgressively(forceRefresh: Boolean = false) {
        progressiveFetchJob?.cancel()
        progressiveFetchJob = viewModelScope.launch {
            var hasMore = _state.value.hasMore
            var offset = _state.value.currentOffset

            while (hasMore) {
                val result = repository.getTransactions(limit = 500, offset = offset, forceRefresh = forceRefresh).getOrNull()
                if (result == null || result.transactions.isEmpty()) break

                _state.update { current ->
                    // Make sure not to add duplicates
                    val existingIds = current.transactions.map { it.id }.toSet()
                    val newTxs = result.transactions.map { it.toDomain() }.filter { !existingIds.contains(it.id) }
                    
                    current.copy(
                        transactions = current.transactions + newTxs,
                        currentOffset = result.offset + result.limit,
                        hasMore = result.hasMore,
                        totalTransactionCount = result.total
                    )
                }
                hasMore = result.hasMore
                offset += 500
            }
        }
    }

    private fun loadMoreTransactions() {
        // We now fetch all transactions progressively in the background on init,
        // so manual pagination scrolling is no longer needed.
    }

    fun onAction(action: MoneyAction) {
        when (action) {
            is MoneyAction.SelectTab -> _state.update { current ->
                // Landing back on the Cash Flow tab (index 0) resolves any pending
                // drill-through: put the filters back exactly as they were before
                // "View all <category> transactions" temporarily overwrote them.
                if (action.index == 0 && current.preDrillDownFilterState != null) {
                    current.copy(
                        selectedTab = action.index,
                        analysisFilterState = current.preDrillDownFilterState,
                        preDrillDownFilterState = null
                    )
                } else {
                    current.copy(selectedTab = action.index)
                }
            }
            is MoneyAction.UpdateSearchQuery -> _state.update { it.copy(searchQuery = action.query) }
            is MoneyAction.SelectCategory -> _state.update { it.copy(selectedCategory = action.category) }

            is MoneyAction.Refresh -> loadInitialData(forceRefresh = true)
            is MoneyAction.LoadMore -> loadMoreTransactions()

            is MoneyAction.SetFilterSheetVisible -> _state.update {
                it.copy(isFilterSheetVisible = action.visible)
            }

            is MoneyAction.ApplyAnalysisFilters -> _state.update {
                it.copy(
                    analysisFilterState = action.filterState.copy(activeDatePreset = null),
                    isFilterSheetVisible = false
                )
            }

            is MoneyAction.UpdateAnalysisFilterState -> _state.update {
                it.copy(analysisFilterState = action.filterState)
            }

            is MoneyAction.ResetAnalysisFilters -> _state.update {
                it.copy(analysisFilterState = AnalysisFilterState())
            }

            is MoneyAction.RemoveCategoryFilter -> _state.update { current ->
                val updatedCats = current.analysisFilterState.categoryFilters.toMutableMap().apply {
                    remove(action.category)
                }
                current.copy(
                    analysisFilterState = current.analysisFilterState.copy(categoryFilters = updatedCats)
                )
            }

            is MoneyAction.RemoveAccountFilter -> _state.update { current ->
                val updatedAccs = current.analysisFilterState.accountFilters.toMutableMap().apply {
                    remove(action.account)
                }
                current.copy(
                    analysisFilterState = current.analysisFilterState.copy(accountFilters = updatedAccs)
                )
            }

            is MoneyAction.RemoveTypeFilter -> _state.update { current ->
                val updatedTypes = current.analysisFilterState.selectedTypes.toMutableSet().apply {
                    remove(action.type)
                }
                current.copy(
                    analysisFilterState = current.analysisFilterState.copy(selectedTypes = updatedTypes)
                )
            }

            is MoneyAction.ToggleQuickPreset -> _state.update { current ->
                val filters = current.analysisFilterState
                when (action.preset) {

                    QuickFilterPreset.THIS_MONTH -> {
                        if (filters.activeDatePreset == QuickFilterPreset.THIS_MONTH) {
                            current.copy(
                                analysisFilterState = filters.copy(
                                    customDateRange = null,
                                    activeDatePreset = null
                                )
                            )
                        } else {
                            val calendar = java.util.Calendar.getInstance().apply {
                                set(java.util.Calendar.DAY_OF_MONTH, 1)
                                set(java.util.Calendar.HOUR_OF_DAY, 0)
                                set(java.util.Calendar.MINUTE, 0)
                                set(java.util.Calendar.SECOND, 0)
                                set(java.util.Calendar.MILLISECOND, 0)
                            }
                            val startOfMonth = calendar.timeInMillis
                            val now = System.currentTimeMillis()
                            current.copy(
                                analysisFilterState = filters.copy(
                                    customDateRange = Pair(startOfMonth, now),
                                    financialYear = null,
                                    selectedMonth = null,
                                    selectedYear = null,
                                    activeDatePreset = QuickFilterPreset.THIS_MONTH
                                )
                            )
                        }
                    }
                    QuickFilterPreset.LAST_MONTH -> {
                        if (filters.activeDatePreset == QuickFilterPreset.LAST_MONTH) {
                            current.copy(
                                analysisFilterState = filters.copy(
                                    customDateRange = null,
                                    selectedMonth = null,
                                    selectedYear = null,
                                    activeDatePreset = null
                                )
                            )
                        } else {
                            val startOfLastMonth = java.util.Calendar.getInstance().apply {
                                add(java.util.Calendar.MONTH, -1)
                                set(java.util.Calendar.DAY_OF_MONTH, 1)
                                set(java.util.Calendar.HOUR_OF_DAY, 0)
                                set(java.util.Calendar.MINUTE, 0)
                                set(java.util.Calendar.SECOND, 0)
                                set(java.util.Calendar.MILLISECOND, 0)
                            }.timeInMillis

                            val endOfLastMonth = java.util.Calendar.getInstance().apply {
                                add(java.util.Calendar.MONTH, -1)
                                val maxDay = getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
                                set(java.util.Calendar.DAY_OF_MONTH, maxDay)
                                set(java.util.Calendar.HOUR_OF_DAY, 23)
                                set(java.util.Calendar.MINUTE, 59)
                                set(java.util.Calendar.SECOND, 59)
                                set(java.util.Calendar.MILLISECOND, 999)
                            }.timeInMillis

                            current.copy(
                                analysisFilterState = filters.copy(
                                    customDateRange = Pair(startOfLastMonth, endOfLastMonth),
                                    financialYear = null,
                                    selectedMonth = null,
                                    selectedYear = null,
                                    activeDatePreset = QuickFilterPreset.LAST_MONTH
                                )
                            )
                        }
                    }
                    QuickFilterPreset.EXPENSES_ONLY -> {
                        val isExpensesOnly = filters.selectedTypes == setOf(TransactionType.DEBIT)
                        current.copy(
                            analysisFilterState = filters.copy(
                                selectedTypes = if (isExpensesOnly) emptySet() else setOf(TransactionType.DEBIT)
                            )
                        )
                    }
                    QuickFilterPreset.INCOME_ONLY -> {
                        val isIncomeOnly = filters.selectedTypes == setOf(TransactionType.CREDIT)
                        current.copy(
                            analysisFilterState = filters.copy(
                                selectedTypes = if (isIncomeOnly) emptySet() else setOf(TransactionType.CREDIT)
                            )
                        )
                    }
                }
            }

            is MoneyAction.ClearFinancialYearFilter -> _state.update { current ->
                current.copy(
                    analysisFilterState = current.analysisFilterState.copy(financialYear = null)
                )
            }

            is MoneyAction.ClearDateRangeFilter, is MoneyAction.ClearMonthYearFilter -> _state.update { current ->
                current.copy(
                    analysisFilterState = current.analysisFilterState.copy(
                        customDateRange = null,
                        selectedMonth = null,
                        selectedYear = null,
                        activeDatePreset = null
                    )
                )
            }

            is MoneyAction.SelectMonthYearFilter -> _state.update { current ->
                val (start, end) = if (action.month != null) {
                    getMonthRangeMillis(action.month, action.year)
                } else {
                    getYearRangeMillis(action.year)
                }
                current.copy(
                    analysisFilterState = current.analysisFilterState.copy(
                        customDateRange = Pair(start, end),
                        selectedMonth = action.month,
                        selectedYear = action.year,
                        financialYear = null,
                        activeDatePreset = null
                    )
                )
            }

            is MoneyAction.ViewCategoryTransactions -> _state.update { current ->
                val newCategoryFilters = mutableMapOf<String, ItemFilterStatus>()
                if (action.category.equals("Others", ignoreCase = true)) {
                    val cats = if (action.otherCategories.isNotEmpty()) {
                        (action.otherCategories + listOf("Others", "Other")).distinct()
                    } else {
                        listOf("Others", "Other")
                    }
                    cats.forEach { cat ->
                        newCategoryFilters[cat] = ItemFilterStatus.INCLUDED
                    }
                } else {
                    newCategoryFilters[action.category] = ItemFilterStatus.INCLUDED
                }
                current.copy(
                    // Remember exactly what the donut was showing before this
                    // temporary single-category filter, so returning to the Cash
                    // Flow tab can put it back rather than leaving the donut
                    // stuck on just this one category.
                    preDrillDownFilterState = current.analysisFilterState,
                    analysisFilterState = current.analysisFilterState.copy(categoryFilters = newCategoryFilters),
                    selectedCategory = "All",
                    selectedTab = 1
                )
            }

            is MoneyAction.ShowTransactionDetail -> _state.update {
                it.copy(detailTransaction = action.transaction, editingTransaction = null, deletingTransaction = null)
            }

            is MoneyAction.ShowEditDialog -> _state.update {
                it.copy(editingTransaction = action.transaction, detailTransaction = null, deletingTransaction = null)
            }

            is MoneyAction.ShowDeleteConfirmation -> _state.update {
                it.copy(deletingTransaction = action.transaction, detailTransaction = null)
            }

            is MoneyAction.DismissDialogs -> _state.update {
                it.copy(detailTransaction = null, editingTransaction = null, deletingTransaction = null)
            }

            is MoneyAction.ClearActionMessage -> _state.update {
                it.copy(actionMessage = null)
            }

            is MoneyAction.UpdateTransaction -> {
                viewModelScope.launch {
                    _state.update { it.copy(isUpdating = true) }
                    val result = repository.updateTransaction(
                        id = action.id,
                        type = action.type,
                        category = action.category,
                        amount = action.amount,
                        note = action.note,
                        accountName = action.accountName,
                        date = action.date,
                        excludeAnalytics = action.excludeAnalytics
                    )

                    result.onSuccess {
                        _state.update {
                            it.copy(
                                isUpdating = false,
                                editingTransaction = null,
                                actionMessage = "Transaction updated successfully"
                            )
                        }
                        loadInitialData()
                    }.onFailure { error ->
                        _state.update {
                            it.copy(
                                isUpdating = false,
                                errorMessage = error.message ?: "Failed to update transaction"
                            )
                        }
                    }
                }
            }

            is MoneyAction.DeleteTransaction -> {
                viewModelScope.launch {
                    _state.update { it.copy(isDeleting = true) }
                    val result = repository.deleteTransaction(action.id)

                    result.onSuccess {
                        _state.update {
                            it.copy(
                                isDeleting = false,
                                deletingTransaction = null,
                                editingTransaction = null,
                                actionMessage = "Transaction deleted successfully"
                            )
                        }
                        loadInitialData()
                    }.onFailure { error ->
                        _state.update {
                            it.copy(
                                isDeleting = false,
                                errorMessage = error.message ?: "Failed to delete transaction"
                            )
                        }
                    }
                }
            }

            is MoneyAction.ToggleExcludeAnalytics -> {
                viewModelScope.launch {
                    val tx = _state.value.transactions.find { it.id == action.id } ?: return@launch
                    val newExcluded = !action.currentExcluded
                    
                    // Optimistic update
                    _state.update { current ->
                        current.copy(
                            transactions = current.transactions.map {
                                if (it.id == action.id) it.copy(isExcluded = newExcluded) else it
                            }
                        )
                    }

                    val result = repository.updateTransaction(
                        id = tx.id,
                        type = tx.rawType.ifEmpty { if (tx.type == TransactionType.CREDIT) "Credit" else "Debit" },
                        category = tx.category,
                        amount = tx.amount,
                        note = tx.note,
                        accountName = tx.bank,
                        date = tx.rawDate.ifEmpty { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(tx.timestampMillis)) },
                        excludeAnalytics = newExcluded
                    )

                    result.onFailure {
                        // Revert on failure
                        _state.update { current ->
                            current.copy(
                                transactions = current.transactions.map {
                                    if (it.id == action.id) it.copy(isExcluded = action.currentExcluded) else it
                                },
                                errorMessage = "Failed to update Spending Analyser setting"
                            )
                        }
                    }
                }
            }

            is MoneyAction.SetBudgetSheetVisible -> {
                _state.update { it.copy(isBudgetSheetVisible = action.visible) }
                // Normally already warmed by prefetchBudgetSuggestions() right after
                // load; this is just the fallback for whoever opens the sheet before
                // that finishes.
                if (action.visible) prefetchBudgetSuggestions()
            }

            is MoneyAction.SetBudgetSectionExpanded -> _state.update {
                it.copy(isBudgetSectionExpanded = action.expanded)
            }

            is MoneyAction.SaveBudgets -> {
                viewModelScope.launch {
                    _state.update { it.copy(isSavingBudgets = true) }
                    val payload = action.limits.map { (category, limit) ->
                        com.example.dailytrack_mobile.data.remote.dto.BudgetDto(
                            category = category,
                            monthlyLimit = limit
                        )
                    }
                    repository.saveBudgets(payload)
                        .onSuccess {
                            val saved = action.limits.filterValues { it > 0.0 }
                            _state.update {
                                it.copy(
                                    budgets = saved,
                                    isSavingBudgets = false,
                                    isBudgetSheetVisible = false,
                                    actionMessage = if (saved.isEmpty()) "Budgets cleared" else "Budgets updated"
                                )
                            }
                        }
                        .onFailure { error ->
                            _state.update {
                                it.copy(
                                    isSavingBudgets = false,
                                    actionMessage = error.message ?: "Failed to save budgets"
                                )
                            }
                        }
                }
            }

            is MoneyAction.ToggleTransactionSelection -> {
                _state.update { current ->
                    val newSelection = if (current.selectedTransactionIds.contains(action.id)) {
                        current.selectedTransactionIds - action.id
                    } else {
                        current.selectedTransactionIds + action.id
                    }
                    current.copy(selectedTransactionIds = newSelection)
                }
            }

            is MoneyAction.SelectAllTransactions -> {
                _state.update { current ->
                    val allIds = current.filteredTransactions.map { it.id }.toSet()
                    current.copy(selectedTransactionIds = allIds)
                }
            }

            is MoneyAction.ClearTransactionSelection -> {
                _state.update { it.copy(selectedTransactionIds = emptySet()) }
            }

            is MoneyAction.ShowBulkEditSheet -> {
                _state.update { it.copy(showBulkEditSheet = action.show) }
            }

            is MoneyAction.ShowBulkDeleteConfirmation -> {
                _state.update { it.copy(showBulkDeleteConfirm = action.show) }
            }

            is MoneyAction.ExecuteBulkDelete -> {
                val selectedIds = _state.value.selectedTransactionIds.toList()
                if (selectedIds.isNotEmpty()) {
                    viewModelScope.launch {
                        _state.update { it.copy(isBulkDeleting = true) }
                        val result = repository.bulkDeleteTransactions(selectedIds)
                        result.onSuccess {
                            _state.update {
                                it.copy(
                                    isBulkDeleting = false,
                                    showBulkDeleteConfirm = false,
                                    selectedTransactionIds = emptySet(),
                                    actionMessage = "Deleted ${selectedIds.size} transactions"
                                )
                            }
                            loadInitialData()
                        }.onFailure { error ->
                            _state.update {
                                it.copy(
                                    isBulkDeleting = false,
                                    errorMessage = error.message ?: "Failed to delete transactions"
                                )
                            }
                        }
                    }
                }
            }

            is MoneyAction.ExecuteBulkEdit -> {
                val updates = action.updates
                if (updates.isNotEmpty()) {
                    viewModelScope.launch {
                        _state.update { it.copy(isBulkUpdating = true) }
                        val result = repository.bulkEditTransactions(updates)
                        result.onSuccess {
                            _state.update {
                                it.copy(
                                    isBulkUpdating = false,
                                    showBulkEditSheet = false,
                                    selectedTransactionIds = emptySet(),
                                    actionMessage = "Updated ${updates.size} transactions"
                                )
                            }
                            loadInitialData()
                        }.onFailure { error ->
                            _state.update {
                                it.copy(
                                    isBulkUpdating = false,
                                    errorMessage = error.message ?: "Failed to update transactions"
                                )
                            }
                        }
                    }
                }
            }
        }

        // Any deliberate filter edit — wherever it happens from, Cash Flow or
        // Transactions — abandons the pending drill-through restore. Reverting a
        // choice the user just made on purpose would be a worse surprise than
        // leaving it in place; only an *unmodified* drill-through gets undone by
        // SelectTab(0) above.
        if (action.isManualFilterEdit) {
            _state.update {
                if (it.preDrillDownFilterState != null) it.copy(preDrillDownFilterState = null) else it
            }
        }
    }

    private val MoneyAction.isManualFilterEdit: Boolean
        get() = this is MoneyAction.ApplyAnalysisFilters ||
            this is MoneyAction.UpdateAnalysisFilterState ||
            this is MoneyAction.ResetAnalysisFilters ||
            this is MoneyAction.RemoveCategoryFilter ||
            this is MoneyAction.RemoveAccountFilter ||
            this is MoneyAction.RemoveTypeFilter ||
            this is MoneyAction.ToggleQuickPreset ||
            this is MoneyAction.ClearFinancialYearFilter ||
            this is MoneyAction.ClearDateRangeFilter ||
            this is MoneyAction.SelectMonthYearFilter ||
            this is MoneyAction.ClearMonthYearFilter
}

// ─────────────────────────────────────────────────────────────────────────────
// DTO → Domain mapping
// ─────────────────────────────────────────────────────────────────────────────

private val apiDateParser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private val displayDateFormat = SimpleDateFormat("MMM dd", Locale.US)

private fun com.example.dailytrack_mobile.data.remote.dto.TransactionDto.toDomain(): Transaction {
    val dateOnly = if (date.contains("T")) date.substringBefore("T") else date
    val parsedDate = try { apiDateParser.parse(dateOnly) } catch (_: Exception) { null }
    val displayDate = parsedDate?.let { displayDateFormat.format(it) } ?: dateOnly
    val timestampMs = parsedDate?.time ?: System.currentTimeMillis()

    val txType = when {
        type.equals("Credit", ignoreCase = true) || type.equals("Income", ignoreCase = true) -> TransactionType.CREDIT
        type.equals("Savings", ignoreCase = true) || type.equals("Saving", ignoreCase = true) -> TransactionType.SAVINGS
        type.equals("Investment", ignoreCase = true) || type.equals("Investments", ignoreCase = true) -> TransactionType.INVESTMENT
        else -> TransactionType.DEBIT
    }

    return Transaction(
        id = id,
        title = if (!description.isNullOrBlank()) description else heading,
        description = if (!description.isNullOrBlank()) heading else null,
        note = description,
        date = displayDate,
        bank = account,
        amount = amount,
        type = txType,
        category = heading,
        emoji = CategoryEmojis.forCategory(heading),
        isExcluded = excludeAnalytics,
        timestampMillis = timestampMs,
        monthStr = month,
        rawDate = dateOnly,
        rawType = type,
        split = split?.let { s ->
            SplitInfo(
                id = s.id,
                totalAmount = s.totalAmount,
                members = s.members.map { m ->
                    SplitMember(name = m.name, amount = m.amount, paid = m.paid)
                }
            )
        }
    )
}
