package com.example.dailytrack_mobile.presentation.screens.invest.stocks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailytrack_mobile.data.local.auth.AuthManager
import com.example.dailytrack_mobile.data.local.datastore.DemoModeManager
import com.example.dailytrack_mobile.data.remote.dto.StockDto
import com.example.dailytrack_mobile.data.remote.dto.StockSearchResultDto
import com.example.dailytrack_mobile.data.remote.dto.StocksResponseDto
import com.example.dailytrack_mobile.data.repository.StocksRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** How many, at what price, on which day: a buy or a sale being typed. */
data class TradeForm(
    val quantity: String = "",
    val price: String = "",
    val date: LocalDate = LocalDate.now()
) {
    val quantityValue: Double? get() = quantity.toDoubleOrNull()?.takeIf { it > 0 }
    val priceValue: Double? get() = price.toDoubleOrNull()?.takeIf { it > 0 }
    val ready: Boolean get() = quantityValue != null && priceValue != null
}

data class StocksUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val stocks: List<StockDto> = emptyList(),
    val quotedAt: String? = null,
    val adding: Boolean = false,
    /** Someone else's, shared to look at: nothing can change. */
    val readOnly: Boolean = false,
    val note: String? = null,
    /** The stock whose trades and actions are showing. */
    val open: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
    // Adding one
    val query: String = "",
    val results: List<StockSearchResultDto>? = null,
    val picked: StockSearchResultDto? = null,
    val form: TradeForm = TradeForm(),
) {
    val held: List<StockDto> get() = stocks.filter { it.quantity > 0 }
    val sold: List<StockDto> get() = stocks.filter { it.quantity <= 0 }
    val value: Double get() = held.sumOf { it.value ?: 0.0 }
    val invested: Double get() = held.sumOf { it.invested }
}

/** The Stocks page (StocksPage): stocks and ETFs added by hand, buying and selling them. */
@HiltViewModel
class StocksVM @Inject constructor(
    private val repository: StocksRepository,
    demoMode: DemoModeManager,
    authManager: AuthManager
) : ViewModel() {

    private val _state = MutableStateFlow(StocksUiState())
    val state: StateFlow<StocksUiState> = _state.asStateFlow()

    /** Shown on the Stocks tab: not in demo mode, nor while viewing someone. */
    private val _available = MutableStateFlow(false)
    val available: StateFlow<Boolean> = _available.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            combine(authManager.viewAsFlow, demoMode.isDemoModeEnabledFlow) { viewAs, demo -> viewAs to demo }
                .collect { (viewAs, demo) ->
                    _state.update { it.copy(readOnly = viewAs != null) }
                    _available.value = viewAs == null && !demo
                }
        }
    }

    private fun show(r: StocksResponseDto, note: String? = null) = _state.update {
        it.copy(loading = false, busy = false, stocks = r.stocks, quotedAt = r.quotedAt, note = note ?: it.note)
    }

    fun load() {
        _state.update { it.copy(loading = it.stocks.isEmpty(), loadError = null, adding = false, open = null, error = null) }
        viewModelScope.launch {
            repository.stocks()
                .onSuccess { show(it) }
                .onFailure { e -> _state.update { it.copy(loading = false, loadError = e.message) } }
        }
    }

    fun dismissNote() = _state.update { it.copy(note = null) }

    fun startAdding() = _state.update {
        it.copy(adding = true, query = "", results = null, picked = null, form = TradeForm(), error = null)
    }

    fun stopAdding() = _state.update { it.copy(adding = false, error = null) }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query, error = null) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _state.update { it.copy(results = null) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(200)
            repository.search(query.trim())
                .onSuccess { results -> _state.update { it.copy(results = results.take(8)) } }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun pick(result: StockSearchResultDto?) = _state.update {
        if (result == null) it.copy(picked = null, query = "", results = null) else it.copy(picked = result)
    }

    fun setForm(form: TradeForm) = _state.update {
        it.copy(form = form.copy(
            quantity = form.quantity.filter { c -> c.isDigit() || c == '.' },
            price = form.price.filter { c -> c.isDigit() || c == '.' }
        ), error = null)
    }

    fun add() {
        val current = _state.value
        val stock = current.picked ?: return
        val f = current.form
        if (!f.ready || current.busy) return
        change(onDone = { _state.update { it.copy(adding = false) } }, note = "${stock.symbol} added") {
            repository.add(stock.symbol, f.quantityValue!!, f.priceValue!!, f.date.toString())
        }
    }

    fun toggle(symbol: String) = _state.update { it.copy(open = if (it.open == symbol) null else symbol, error = null) }

    fun trade(symbol: String, kind: String, form: TradeForm, onDone: () -> Unit) {
        if (!form.ready) return
        change(onDone) { repository.trade(symbol, kind, form.quantityValue!!, form.priceValue!!, form.date.toString()) }
    }

    fun removeTrade(symbol: String, id: Long, onDone: () -> Unit) = change(onDone) { repository.removeTrade(symbol, id) }

    fun remove(symbol: String) = change({ _state.update { it.copy(open = null) } }) { repository.remove(symbol) }

    private fun change(onDone: () -> Unit, note: String? = null, work: suspend () -> Result<StocksResponseDto>) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            work()
                .onSuccess { r ->
                    show(r, note)
                    onDone()
                }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message) } }
        }
    }
}
