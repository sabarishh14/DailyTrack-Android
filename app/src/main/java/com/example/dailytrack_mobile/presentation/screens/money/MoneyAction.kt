package com.example.dailytrack_mobile.presentation.screens.money

sealed class MoneyAction {
    data class SelectTab(val index: Int) : MoneyAction()
    data class UpdateSearchQuery(val query: String) : MoneyAction()
    data class SelectCategory(val category: String) : MoneyAction()

    // Data loading actions
    object Refresh : MoneyAction()
    object LoadMore : MoneyAction()

    // Filter Sheet actions
    data class SetFilterSheetVisible(val visible: Boolean) : MoneyAction()
    data class ApplyAnalysisFilters(val filterState: AnalysisFilterState) : MoneyAction()
    object ResetAnalysisFilters : MoneyAction()
    data class RemoveCategoryFilter(val category: String) : MoneyAction()
    data class RemoveAccountFilter(val account: String) : MoneyAction()
    data class RemoveTypeFilter(val type: TransactionType) : MoneyAction()
    data class ToggleQuickPreset(val preset: QuickFilterPreset) : MoneyAction()
    object ClearFinancialYearFilter : MoneyAction()
    object ClearDateRangeFilter : MoneyAction()
    data class SelectMonthYearFilter(val month: java.time.Month?, val year: Int) : MoneyAction()
    object ClearMonthYearFilter : MoneyAction()
    data class UpdateAnalysisFilterState(val filterState: AnalysisFilterState) : MoneyAction()
    data class ViewCategoryTransactions(
        val category: String,
        val otherCategories: List<String> = emptyList()
    ) : MoneyAction()

    // Transaction Dialog & Modification actions
    data class ShowTransactionDetail(val transaction: Transaction?) : MoneyAction()
    data class ShowEditDialog(val transaction: Transaction?) : MoneyAction()
    data class ShowDeleteConfirmation(val transaction: Transaction?) : MoneyAction()
    object DismissDialogs : MoneyAction()
    data class UpdateTransaction(
        val id: Long,
        val type: String,
        val category: String,
        val amount: Double,
        val note: String?,
        val accountName: String,
        val date: String,
        val excludeAnalytics: Boolean
    ) : MoneyAction()
    data class DeleteTransaction(val id: Long) : MoneyAction()
    data class ToggleExcludeAnalytics(val id: Long, val currentExcluded: Boolean) : MoneyAction()
    object ClearActionMessage : MoneyAction()

    // Budgets
    data class SetBudgetSheetVisible(val visible: Boolean) : MoneyAction()
    data class SetBudgetSectionExpanded(val expanded: Boolean) : MoneyAction()
    /** Saves the whole set at once; a limit of 0 removes that category's budget. */
    data class SaveBudgets(val limits: Map<String, Double>) : MoneyAction()

    // Multi-Selection & Bulk Actions
    data class ToggleTransactionSelection(val id: Long) : MoneyAction()
    object SelectAllTransactions : MoneyAction()
    object ClearTransactionSelection : MoneyAction()
    data class ShowBulkEditSheet(val show: Boolean) : MoneyAction()
    data class ShowBulkDeleteConfirmation(val show: Boolean) : MoneyAction()
    object ExecuteBulkDelete : MoneyAction()
    data class ExecuteBulkEdit(val updates: List<com.example.dailytrack_mobile.data.remote.dto.BulkEditTransactionItemDto>) : MoneyAction()
}
