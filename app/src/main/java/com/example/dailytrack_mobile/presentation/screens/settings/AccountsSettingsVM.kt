package com.example.dailytrack_mobile.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailytrack_mobile.data.remote.dto.AccountDto
import com.example.dailytrack_mobile.data.repository.MoneyRepository
import com.example.dailytrack_mobile.data.repository.isCcAccount
import com.example.dailytrack_mobile.presentation.components.transaction.formatRupees
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Settings → Accounts: every account, and adding a savings account or a credit card. */
@HiltViewModel
class AccountsSettingsVM @Inject constructor(
    private val repository: MoneyRepository
) : ViewModel() {

    data class State(
        val loading: Boolean = true,
        val loadError: String? = null,
        val savings: List<AccountDto> = emptyList(),
        val cards: List<AccountDto> = emptyList(),
        /** Adding one: the request is out. */
        val adding: Boolean = false,
        /** Why adding failed, shown in the sheet. */
        val addError: String? = null,
        /** Bumped each time one is added, so the sheet knows to close. */
        val addedCount: Int = 0,
        val message: String? = null
    ) {
        val savingsTotal: Double get() = savings.sumOf { it.balance ?: 0.0 }
        val names: Set<String> get() = (savings + cards).map { it.account.lowercase() }.toSet()
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        load(force = true)
    }

    fun load(force: Boolean = false) {
        viewModelScope.launch {
            repository.getAccounts(forceRefresh = force)
                .onSuccess { accounts -> _state.update { it.withAccounts(accounts).copy(loading = false, loadError = null) } }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, loadError = e.message ?: "Couldn't load your accounts") }
                }
        }
    }

    fun add(name: String, creditCard: Boolean, balance: Double?, minBalance: Double?) {
        if (_state.value.adding) return
        _state.update { it.copy(adding = true, addError = null) }
        viewModelScope.launch {
            repository.createAccount(name, creditCard, balance, minBalance)
                .onSuccess { account ->
                    val all = repository.getCachedAccountDetails().ifEmpty { current() + account }
                    _state.update {
                        it.withAccounts(all).copy(
                            adding = false,
                            addedCount = it.addedCount + 1,
                            message = if (creditCard) "${account.account} added" else "${account.account} added with ₹${formatRupees(account.balance ?: 0.0)}"
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(adding = false, addError = e.message ?: "Couldn't add the account") } }
        }
    }

    /** Saves a savings account's balance and minimum (null = none) from the edit dialog. */
    fun saveAccount(account: AccountDto, balance: Double, min: Double?) {
        viewModelScope.launch {
            repository.updateBalance(account.account, balance, min)
                .onSuccess {
                    _state.update { s ->
                        s.copy(
                            savings = s.savings.map { if (it.account == account.account) it.copy(balance = balance, minBalance = min) else it },
                            message = "${account.account} saved"
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(message = e.message ?: "Couldn't save") } }
        }
    }

    /** Sets or, with null, removes a savings account's minimum balance. */
    fun setMinBalance(account: String, min: Double?) {
        viewModelScope.launch {
            repository.setMinBalance(account, min)
                .onSuccess {
                    _state.update { s ->
                        s.copy(
                            savings = s.savings.map { if (it.account == account) it.copy(minBalance = min) else it },
                            message = if (min == null) "Minimum removed for $account"
                            else "You'll be alerted when $account drops below ₹${formatRupees(min)}"
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(message = e.message ?: "Couldn't save the minimum") } }
        }
    }

    /** Sets or, with null, removes a credit card's monthly budget. */
    fun setCardBudget(card: String, budget: Double?) {
        viewModelScope.launch {
            repository.setCardBudget(card, budget)
                .onSuccess {
                    _state.update { s ->
                        s.copy(
                            cards = s.cards.map { if (it.account == card) it.copy(monthlyBudget = budget) else it },
                            message = if (budget == null) "Budget removed for $card"
                            else "You'll be alerted when $card goes over ₹${formatRupees(budget)} in a month"
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(message = e.message ?: "Couldn't save the budget") } }
        }
    }

    fun clearAddError() = _state.update { it.copy(addError = null) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun current(): List<AccountDto> = _state.value.savings + _state.value.cards

    private fun State.withAccounts(accounts: List<AccountDto>): State {
        val (cards, savings) = accounts.partition { isCcAccount(it.account) }
        return copy(
            savings = savings.sortedBy { it.account.lowercase() },
            cards = cards.sortedBy { it.account.lowercase() }
        )
    }
}
