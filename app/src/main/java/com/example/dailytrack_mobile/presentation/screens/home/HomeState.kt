package com.example.dailytrack_mobile.presentation.screens.home

import com.example.dailytrack_mobile.presentation.screens.money.AccountInfo
import java.time.Month
import java.time.LocalDate

data class HomeState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    /** One-off feedback (e.g. a minimum saved or refused), shown once then cleared. */
    val notice: String? = null,
    val accounts: List<AccountInfo> = emptyList(),
    val selectedMonth: Month = LocalDate.now().month,
    val selectedYear: Int = LocalDate.now().year,
    val incomeByCategory: Map<String, Double> = emptyMap(),
    val expenseByCategory: Map<String, Double> = emptyMap(),
    val investmentTotalInvested: Double = 0.0,
    val investmentTotalCurrent: Double = 0.0,
    val hiddenInvestCategories: Set<com.example.dailytrack_mobile.presentation.screens.invest.InvestCategory> = emptySet(),
    /** Accounts loaded and there are none yet: Home offers to add the first instead of empty sections. */
    val noAccounts: Boolean = false,
    /** The portfolio loaded and has nothing in it yet. */
    val noInvestments: Boolean = false
) {
    val isInvestFiltered: Boolean get() = hiddenInvestCategories.isNotEmpty()
    val visibleInvestCategoriesCount: Int get() = com.example.dailytrack_mobile.presentation.screens.invest.InvestCategory.entries.size - hiddenInvestCategories.size
    val totalInvestCategoriesCount: Int get() = com.example.dailytrack_mobile.presentation.screens.invest.InvestCategory.entries.size

    val totalBankBalance: Double
        get() = accounts
            .filter { it.balanceTracked }
            .sumOf { it.balance }
}
