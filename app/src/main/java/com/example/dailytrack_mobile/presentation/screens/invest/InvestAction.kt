package com.example.dailytrack_mobile.presentation.screens.invest

sealed class InvestAction {
    data class SelectTab(val tab: InvestTab) : InvestAction()
    data class SelectTimeRange(val range: ChartTimeRange) : InvestAction()
    object Refresh : InvestAction()
    data class ToggleCategoryVisibility(val category: InvestCategory) : InvestAction()
    data class SetAllCategoriesVisibility(val showAll: Boolean) : InvestAction()
    data class SetCategorySettingsOpen(val isOpen: Boolean) : InvestAction()

    // Holdings snapshot sheet
    data class OpenHoldingsSnapshot(val date: String) : InvestAction()
    object CloseHoldingsSnapshot : InvestAction()
    data class SelectSnapshotType(val type: HoldingsType) : InvestAction()
    /** Passing null drops back to the single-date view. */
    data class SelectCompareDate(val date: String?) : InvestAction()
    data class SetComparePickerOpen(val isOpen: Boolean) : InvestAction()

    // One stock's or fund's page
    data class OpenHoldingDetail(val holding: InvestmentHolding) : InvestAction()
    object CloseHoldingDetail : InvestAction()
    data class SelectDetailRange(val range: ChartTimeRange) : InvestAction()
}
