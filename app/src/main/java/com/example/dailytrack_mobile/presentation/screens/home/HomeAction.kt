package com.example.dailytrack_mobile.presentation.screens.home

import java.time.Month

sealed class HomeAction {
    data class Refresh(val forceRefresh: Boolean = false) : HomeAction()
    data class DateSelected(val month: Month, val year: Int) : HomeAction()
    /** [min] null removes the account's floor. */
    data class SetMinBalance(val account: String, val min: Double?) : HomeAction()
    /** [budget] null removes the credit card's monthly budget. */
    data class SetCardBudget(val account: String, val budget: Double?) : HomeAction()
    data object ClearNotice : HomeAction()
}
