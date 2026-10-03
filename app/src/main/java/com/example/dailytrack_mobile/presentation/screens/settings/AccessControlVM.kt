package com.example.dailytrack_mobile.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailytrack_mobile.data.remote.dto.AccessUserDto
import com.example.dailytrack_mobile.data.remote.dto.AccessUserRequestDto
import com.example.dailytrack_mobile.data.remote.dto.PermissionsDto
import com.example.dailytrack_mobile.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Admin-only: who can sign in. Everyone who can has their own, separate data, so
 * there's nothing to share here, only a role. Rules: DT-Web/ACCESS_CONTROL.md.
 */
data class AccessDraft(
    val email: String,
    val role: String = "member",
    val confirmRemove: Boolean = false
)

data class AccessControlState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val owners: List<String> = emptyList(),
    val users: List<AccessUserDto> = emptyList(),
    /** People who tried to sign in and are waiting to be let in. */
    val requests: List<com.example.dailytrack_mobile.data.remote.dto.AccessRequestDto> = emptyList(),
    val draft: AccessDraft? = null,
    val isSaving: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class AccessControlVM @Inject constructor(
    private val repo: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AccessControlState())
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            repo.getAccessUsers()
                .onSuccess { res -> _state.update { it.copy(isLoading = false, owners = res.owners, users = res.users, requests = res.requests) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message ?: "Couldn't load people") } }
        }
    }

    /** Adding someone is one step: they can sign in straight away, as a member. */
    fun add(rawEmail: String) {
        val email = rawEmail.trim().lowercase()
        if (!Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email)) {
            _state.update { it.copy(message = "Enter a valid email address") }
            return
        }
        val current = _state.value
        current.users.firstOrNull { it.email.equals(email, ignoreCase = true) }?.let { edit(it); return }
        if (email in current.owners) {
            _state.update { it.copy(message = "$email is an owner") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            repo.saveAccessUser(AccessUserRequestDto(email = email, role = "member", permissions = PermissionsDto()), isNew = true)
                .onSuccess {
                    _state.update { it.copy(isSaving = false, message = "$email can now sign in") }
                    load()
                }
                .onFailure { e -> _state.update { it.copy(isSaving = false, message = e.message ?: "Could not add") } }
        }
    }

    /** Approve lets them sign in as a member with their own data; decline clears the request. */
    fun answer(email: String, approve: Boolean) {
        _state.update { s -> s.copy(requests = s.requests.filterNot { it.email == email }) }
        viewModelScope.launch {
            (if (approve) repo.approveRequest(email) else repo.declineRequest(email))
                .onSuccess { _state.update { it.copy(message = if (approve) "$email can now sign in" else "Request declined") }; load() }
                .onFailure { e -> _state.update { it.copy(message = e.message ?: "Couldn't save") }; load() }
        }
    }

    fun edit(user: AccessUserDto) {
        _state.update { it.copy(draft = AccessDraft(email = user.email, role = if (user.role == "admin") "admin" else "member")) }
    }

    fun setRole(role: String) {
        _state.update { s -> s.copy(draft = s.draft?.copy(role = role, confirmRemove = false)) }
    }

    fun closeEditor() = _state.update { it.copy(draft = null) }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun save() {
        val draft = _state.value.draft ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            repo.saveAccessUser(AccessUserRequestDto(email = draft.email, role = draft.role, permissions = PermissionsDto()), isNew = false)
                .onSuccess {
                    _state.update { it.copy(isSaving = false, draft = null, message = "Saved") }
                    load()
                }
                .onFailure { e -> _state.update { it.copy(isSaving = false, message = e.message ?: "Could not save") } }
        }
    }

    /** First call arms the button, second call removes. */
    fun remove() {
        val draft = _state.value.draft ?: return
        if (!draft.confirmRemove) {
            _state.update { it.copy(draft = draft.copy(confirmRemove = true)) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            repo.deleteAccessUser(draft.email)
                .onSuccess {
                    _state.update { it.copy(isSaving = false, draft = null, message = "${draft.email} was removed") }
                    load()
                }
                .onFailure { e -> _state.update { it.copy(isSaving = false, message = e.message ?: "Could not remove") } }
        }
    }
}
