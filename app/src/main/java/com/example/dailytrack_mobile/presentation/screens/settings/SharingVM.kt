package com.example.dailytrack_mobile.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailytrack_mobile.data.local.auth.AuthManager
import com.example.dailytrack_mobile.data.remote.dto.MyShareDto
import com.example.dailytrack_mobile.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Sharing: who can view your data (read-only), what's shared with you, and
 * switching between your data and theirs. Rules: DT-Web/ACCESS_CONTROL.md.
 */
@HiltViewModel
class SharingVM @Inject constructor(
    private val repo: AuthRepository
) : ViewModel() {

    data class State(
        val loading: Boolean = true,
        val mine: List<MyShareDto> = emptyList(),
        /** From the same call as [mine], so the screen fills in one go. */
        val withMe: List<AuthManager.SharedWithMe>? = null,
        val busy: Boolean = false,
        val error: String? = null,
        val message: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** Whose data is on screen: null = yours. */
    val viewAs: StateFlow<String?> = repo.viewAsFlow
    val sharedWithMe: StateFlow<List<AuthManager.SharedWithMe>> = repo.sharedWithMe

    fun load() {
        viewModelScope.launch {
            repo.getShares()
                .onSuccess { res ->
                    _state.update {
                        it.copy(loading = false, mine = res.mine, withMe = res.withMe.map { w -> AuthManager.SharedWithMe(w.owner, w.modules) })
                    }
                }
                .onFailure { _state.update { it.copy(loading = false) } }
        }
    }

    fun share(rawEmail: String, modules: List<String>) {
        val email = rawEmail.trim().lowercase()
        when {
            !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email) -> { _state.update { it.copy(error = "Enter a valid email") }; return }
            modules.isEmpty() -> { _state.update { it.copy(error = "Pick something to share") }; return }
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            repo.setShare(email, modules)
                .onSuccess {
                    _state.update { it.copy(busy = false, message = "Shared with $email") }
                    load()
                }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message ?: "Couldn't share") } }
        }
    }

    /** Turns one module on or off for someone; turning off the last stops sharing with them. */
    fun toggle(share: MyShareDto, module: String) {
        val modules = if (module in share.modules) share.modules - module else share.modules + module
        _state.update { s ->
            s.copy(mine = if (modules.isEmpty()) s.mine.filterNot { it.viewer == share.viewer }
            else s.mine.map { if (it.viewer == share.viewer) it.copy(modules = modules) else it })
        }
        viewModelScope.launch {
            repo.setShare(share.viewer, modules).onFailure { e ->
                _state.update { it.copy(message = e.message ?: "Couldn't save") }
                load()
            }
        }
    }

    fun stop(share: MyShareDto) {
        _state.update { s -> s.copy(mine = s.mine.filterNot { it.viewer == share.viewer }) }
        viewModelScope.launch {
            repo.setShare(share.viewer, emptyList())
                .onSuccess { _state.update { it.copy(message = "Stopped sharing with ${share.viewer}") } }
                .onFailure { e -> _state.update { it.copy(message = e.message ?: "Couldn't save") }; load() }
        }
    }

    /** Shows [owner]'s shared data, or your own with null. */
    fun switchView(owner: String?) {
        viewModelScope.launch { repo.switchView(owner) }
    }

    fun clearError() = _state.update { it.copy(error = null) }
    fun consumeMessage() = _state.update { it.copy(message = null) }
}
