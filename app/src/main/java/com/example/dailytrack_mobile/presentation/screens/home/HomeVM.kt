package com.example.dailytrack_mobile.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailytrack_mobile.data.local.datastore.DemoModeManager
import com.example.dailytrack_mobile.data.local.datastore.InvestPreferencesManager
import com.example.dailytrack_mobile.data.remote.dto.PortfolioSnapshotDto
import com.example.dailytrack_mobile.data.repository.MoneyRepository
import com.example.dailytrack_mobile.data.repository.InvestmentsRepository
import com.example.dailytrack_mobile.presentation.screens.invest.InvestCategory
import com.example.dailytrack_mobile.presentation.screens.money.AccountInfo
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeVM @Inject constructor(
    private val repository: MoneyRepository,
    private val investmentsRepository: InvestmentsRepository,
    private val demoModeManager: DemoModeManager,
    private val investPreferencesManager: InvestPreferencesManager
) : ViewModel() {

    private val _state = MutableStateFlow(
        HomeState(
            hiddenInvestCategories = investPreferencesManager.getInitialHiddenCategories()
        )
    )
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private var cachedLatestSnapshot: PortfolioSnapshotDto? = null

    init {
        // So low-balance alerts reach this phone even for transactions added on the web.
        runCatching {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                viewModelScope.launch { repository.registerPushToken(token) }
            }
        }
        viewModelScope.launch {
            demoModeManager.isDemoModeEnabledFlow.collect {
                loadAccountsAndTransactions()
            }
        }
        viewModelScope.launch {
            repository.dataUpdateFlow.collect {
                loadAccountsAndTransactions()
            }
        }
        viewModelScope.launch {
            investPreferencesManager.hiddenCategoriesFlow.collect { hidden ->
                val (inv, curr) = calculateFilteredInvestments(cachedLatestSnapshot, hidden)
                _state.update {
                    it.copy(
                        hiddenInvestCategories = hidden,
                        investmentTotalInvested = inv,
                        investmentTotalCurrent = curr
                    )
                }
            }
        }
    }

    private fun calculateFilteredInvestments(
        snapshot: PortfolioSnapshotDto?,
        hidden: Set<InvestCategory>
    ): Pair<Double, Double> {
        if (snapshot == null) return Pair(0.0, 0.0)
        var inv = 0.0
        var curr = 0.0
        if (InvestCategory.STOCKS !in hidden) {
            inv += snapshot.invStocks ?: 0.0
            curr += snapshot.currStocks ?: 0.0
        }
        if (InvestCategory.MUTUAL_FUNDS !in hidden) {
            inv += snapshot.invMf ?: 0.0
            curr += snapshot.currMf ?: 0.0
        }
        if (InvestCategory.RETIREMENT !in hidden) {
            inv += snapshot.invProv ?: 0.0
            curr += snapshot.currProv ?: 0.0
        }
        if (InvestCategory.FD !in hidden) {
            inv += snapshot.invFixed ?: 0.0
            curr += snapshot.currFixed ?: 0.0
        }
        if (InvestCategory.GOLD !in hidden) {
            inv += snapshot.invGold ?: 0.0
            curr += snapshot.currGold ?: 0.0
        }
        return Pair(inv, curr)
    }

    private fun loadAccountsAndTransactions(forceRefresh: Boolean = false) {
        if (forceRefresh) {
            _state.update { it.copy(isRefreshing = true, errorMessage = null) }
        } else {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
        }
        viewModelScope.launch {
            val accountsResult = repository.getAccounts(forceRefresh = forceRefresh)
            val monthStr = String.format("%04d-%02d", _state.value.selectedYear, _state.value.selectedMonth.value)
            val transactionsResult = repository.getTransactions(limit = 1000, month = monthStr, forceRefresh = forceRefresh)
            val investmentsResult = investmentsRepository.getFullPortfolio(forceRefresh = forceRefresh)

            if (accountsResult.isSuccess && transactionsResult.isSuccess) {
                val accounts = accountsResult.getOrThrow()
                    .filter { it.balanceTracked }
                    .map { dto ->
                        AccountInfo(
                            account = dto.account,
                            balance = dto.balance ?: 0.0,
                            realBalance = dto.realBalance,
                            balanceTracked = dto.balanceTracked,
                            minBalance = dto.minBalance
                        )
                    }
                
                val transactions = transactionsResult.getOrThrow().transactions
                
                val income = mutableMapOf<String, Double>()
                val expense = mutableMapOf<String, Double>()
                
                transactions.forEach { t ->
                    if (t.type == "Credit") {
                        val key = if (t.account.isNotBlank()) t.account else "Uncategorized"
                        income[key] = (income[key] ?: 0.0) + t.amount
                    } else if (t.type == "Debit") {
                        val key = if (t.account.isNotBlank()) t.account else "Uncategorized"
                        expense[key] = (expense[key] ?: 0.0) + t.amount
                    }
                }
                
                val portfolioData = investmentsResult.getOrNull()
                val latestSnapshot = portfolioData?.snapshots?.firstOrNull()
                cachedLatestSnapshot = latestSnapshot
                val (totalInvested, totalCurrent) = calculateFilteredInvestments(latestSnapshot, _state.value.hiddenInvestCategories)
                
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        accounts = accounts,
                        incomeByCategory = income,
                        expenseByCategory = expense,
                        investmentTotalInvested = totalInvested,
                        investmentTotalCurrent = totalCurrent
                    )
                }
            } else {
                val errorMessage = accountsResult.exceptionOrNull()?.message 
                    ?: transactionsResult.exceptionOrNull()?.message 
                    ?: "Failed to load data"
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = errorMessage
                    )
                }
            }
        }
    }

    fun onAction(action: HomeAction) {
        when (action) {
            is HomeAction.Refresh -> loadAccountsAndTransactions(forceRefresh = action.forceRefresh)
            is HomeAction.DateSelected -> {
                _state.update { it.copy(selectedMonth = action.month, selectedYear = action.year) }
                loadAccountsAndTransactions()
            }
            is HomeAction.SetMinBalance -> setMinBalance(action.account, action.min)
            HomeAction.ClearNotice -> _state.update { it.copy(notice = null) }
        }
    }

    private fun setMinBalance(account: String, min: Double?) {
        viewModelScope.launch {
            repository.setMinBalance(account, min)
                .onSuccess {
                    _state.update { s ->
                        s.copy(
                            accounts = s.accounts.map { if (it.account == account) it.copy(minBalance = min) else it },
                            notice = if (min == null) "Minimum removed for $account" else "You'll be alerted when $account drops below ₹${"%,.0f".format(min)}"
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(notice = e.message ?: "Couldn't save the minimum") } }
        }
    }
}
