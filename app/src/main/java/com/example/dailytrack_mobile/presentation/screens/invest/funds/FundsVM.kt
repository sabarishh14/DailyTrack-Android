package com.example.dailytrack_mobile.presentation.screens.invest.funds

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailytrack_mobile.data.local.auth.AuthManager
import com.example.dailytrack_mobile.data.local.datastore.DemoModeManager
import com.example.dailytrack_mobile.data.remote.dto.AddFundRequestDto
import com.example.dailytrack_mobile.data.remote.dto.FundDto
import com.example.dailytrack_mobile.data.remote.dto.FundSearchResultDto
import com.example.dailytrack_mobile.data.remote.dto.FundSipRequestDto
import com.example.dailytrack_mobile.data.repository.FundsRepository
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

enum class FundsView { LIST, IMPORT, ADD }

/** A monthly SIP being set up: on the add screen, or for a fund already held. */
data class SipForm(
    val amount: String = "",
    val day: Int = LocalDate.now().dayOfMonth,
    val since: LocalDate = LocalDate.now()
) {
    val amountValue: Double? get() = amount.toDoubleOrNull()?.takeIf { it > 0 }

    fun request() = FundSipRequestDto(amountValue ?: 0.0, day, since.toString())
}

data class FundsUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val funds: List<FundDto> = emptyList(),
    val view: FundsView = FundsView.LIST,
    /** Someone else's, shared to look at: nothing can change. */
    val readOnly: Boolean = false,
    /** What the last import or add did, until dismissed. */
    val note: String? = null,
    /** The fund whose SIPs and Remove are showing. */
    val open: String? = null,
    val rowBusy: Boolean = false,
    val rowError: String? = null,
    // Import
    val file: FundsRepository.PickedFile? = null,
    val password: String = "",
    val importing: Boolean = false,
    val importError: String? = null,
    // Add
    val query: String = "",
    val results: List<FundSearchResultDto>? = null,
    val picked: FundSearchResultDto? = null,
    val amount: String = "",
    val date: LocalDate = LocalDate.now(),
    val withSip: Boolean = false,
    val sip: SipForm = SipForm(),
    val adding: Boolean = false,
    val addError: String? = null
) {
    val held: List<FundDto> get() = funds.filter { it.units > 0 }
    val sold: List<FundDto> get() = funds.filter { it.units <= 0 }
    val value: Double get() = held.sumOf { it.value ?: 0.0 }
    val invested: Double get() = held.sumOf { it.invested }

    val canAdd: Boolean
        get() = picked != null && !adding &&
            ((amount.toDoubleOrNull() ?: 0.0) > 0 || (withSip && sip.amountValue != null))
}

/** The mutual funds sheet (FundsSheet): what's held outside Kite, importing a statement, adding one. */
@HiltViewModel
class FundsVM @Inject constructor(
    private val repository: FundsRepository,
    demoMode: DemoModeManager,
    authManager: AuthManager
) : ViewModel() {

    private val _state = MutableStateFlow(FundsUiState())
    val state: StateFlow<FundsUiState> = _state.asStateFlow()

    /** Shown on the Mutual funds tab: not in demo mode, nor while viewing someone. */
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

    fun load() {
        _state.update { it.copy(loading = it.funds.isEmpty(), loadError = null, view = FundsView.LIST, open = null) }
        viewModelScope.launch {
            repository.funds()
                .onSuccess { funds -> _state.update { it.copy(loading = false, funds = funds) } }
                .onFailure { e -> _state.update { it.copy(loading = false, loadError = e.message) } }
        }
    }

    fun show(view: FundsView) = _state.update {
        when (view) {
            FundsView.IMPORT -> it.copy(view = view, importError = null)
            FundsView.ADD -> it.copy(
                view = view, query = "", results = null, picked = null, amount = "", date = LocalDate.now(),
                withSip = false, sip = SipForm(), addError = null
            )
            FundsView.LIST -> it.copy(view = view)
        }
    }

    fun dismissNote() = _state.update { it.copy(note = null) }

    // ── Import ──────────────────────────────────────────────────────────────

    fun pickFile(uri: Uri) = _state.update { it.copy(file = repository.describe(uri), importError = null) }

    fun setPassword(password: String) = _state.update { it.copy(password = password, importError = null) }

    fun import() {
        val current = _state.value
        val file = current.file ?: return
        if (current.password.isEmpty() || current.importing) return
        _state.update { it.copy(importing = true, importError = null) }
        viewModelScope.launch {
            repository.import(file.uri, current.password)
                .onSuccess { r ->
                    val parts = listOfNotNull(
                        r.added.size.takeIf { it > 0 }?.let { "$it added" },
                        r.updated.size.takeIf { it > 0 }?.let { "$it updated" },
                        r.skippedKite.size.takeIf { it > 0 }?.let { "$it already in Kite" },
                        r.unknown.size.takeIf { it > 0 }?.let { "$it not found" }
                    )
                    _state.update {
                        it.copy(
                            importing = false, password = "", file = null, funds = r.funds, view = FundsView.LIST,
                            note = parts.joinToString(" · ").ifEmpty { "Nothing new in that statement" }
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(importing = false, importError = e.message) } }
        }
    }

    // ── Add ─────────────────────────────────────────────────────────────────

    fun setQuery(query: String) {
        _state.update { it.copy(query = query, addError = null) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _state.update { it.copy(results = null) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(250)
            repository.search(query.trim())
                .onSuccess { results -> _state.update { it.copy(results = results.take(8)) } }
                .onFailure { e -> _state.update { it.copy(addError = e.message) } }
        }
    }

    fun pick(result: FundSearchResultDto?) = _state.update {
        if (result == null) it.copy(picked = null, query = "", results = null) else it.copy(picked = result)
    }

    fun setAmount(amount: String) = _state.update { it.copy(amount = amount.filter { c -> c.isDigit() || c == '.' }) }

    fun setDate(date: LocalDate) = _state.update { it.copy(date = date) }

    fun setWithSip(on: Boolean) = _state.update { it.copy(withSip = on) }

    fun setSip(sip: SipForm) = _state.update { it.copy(sip = sip) }

    fun add() {
        val current = _state.value
        val fund = current.picked ?: return
        if (!current.canAdd) return
        val amount = current.amount.toDoubleOrNull()?.takeIf { it > 0 }
        val request = AddFundRequestDto(
            code = fund.code,
            amount = amount,
            date = amount?.let { current.date.toString() },
            sip = current.sip.takeIf { current.withSip && it.amountValue != null }?.request()
        )
        _state.update { it.copy(adding = true, addError = null) }
        viewModelScope.launch {
            repository.add(request)
                .onSuccess { funds ->
                    _state.update { it.copy(adding = false, funds = funds, view = FundsView.LIST, note = "${fund.name} added") }
                }
                .onFailure { e -> _state.update { it.copy(adding = false, addError = e.message) } }
        }
    }

    // ── One fund ────────────────────────────────────────────────────────────

    fun toggle(code: String) = _state.update { it.copy(open = if (it.open == code) null else code, rowError = null) }

    fun startSip(code: String, sip: SipForm, onDone: () -> Unit) = rowChange(onDone) { repository.startSip(code, sip.request()) }

    fun stopSip(id: Long, onDone: () -> Unit) = rowChange(onDone) { repository.stopSip(id) }

    fun remove(code: String) = rowChange({}) { repository.remove(code) }

    private fun rowChange(onDone: () -> Unit, work: suspend () -> Result<List<FundDto>>) {
        if (_state.value.rowBusy) return
        _state.update { it.copy(rowBusy = true, rowError = null) }
        viewModelScope.launch {
            work()
                .onSuccess { funds ->
                    _state.update { it.copy(rowBusy = false, funds = funds) }
                    onDone()
                }
                .onFailure { e -> _state.update { it.copy(rowBusy = false, rowError = e.message) } }
        }
    }
}
