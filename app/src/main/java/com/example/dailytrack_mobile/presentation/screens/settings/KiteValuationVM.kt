package com.example.dailytrack_mobile.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailytrack_mobile.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * How Kite's holdings are valued (the server's kite_daily.py): "manual", their
 * worth changes with a Kite sync; "daily", the last sync's holdings are repriced
 * every day. Kept on the server, so it's the same on the web.
 */
@HiltViewModel
class KiteValuationVM @Inject constructor(
    private val repo: AuthRepository
) : ViewModel() {

    /** "manual" or "daily"; null until the server answers. */
    private val _mode = MutableStateFlow<String?>(null)
    val mode: StateFlow<String?> = _mode.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch {
            repo.getKiteValuation()
                .onSuccess { _mode.value = it }
                .onFailure { _error.value = "Couldn't load" }
        }
    }

    fun pick(next: String) {
        val before = _mode.value
        if (next == before) return
        _mode.value = next
        _error.value = null
        viewModelScope.launch {
            repo.setKiteValuation(next).onFailure { e ->
                _mode.value = before
                _error.value = e.message ?: "Couldn't save"
            }
        }
    }
}
