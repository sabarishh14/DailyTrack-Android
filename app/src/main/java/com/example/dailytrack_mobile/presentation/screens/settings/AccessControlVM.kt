package com.example.dailytrack_mobile.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailytrack_mobile.data.local.auth.AccessLevel
import com.example.dailytrack_mobile.data.local.auth.AccessModule
import com.example.dailytrack_mobile.data.remote.dto.AccessUserDto
import com.example.dailytrack_mobile.data.remote.dto.AccessUserRequestDto
import com.example.dailytrack_mobile.data.remote.dto.MoneyScopeDto
import com.example.dailytrack_mobile.data.remote.dto.PermissionsDto
import com.example.dailytrack_mobile.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Admin-only people & permissions editor. Rules: DT-Web/ACCESS_CONTROL.md. */
data class AccessDraft(
    val email: String,
    val role: String = "member",
    val modules: Map<AccessModule, AccessLevel> = AccessModule.entries.associateWith { AccessLevel.VIEW },
    /** null = all categories */
    val categories: List<String>? = null,
    /** null = all accounts */
    val accounts: List<String>? = null,
    val isNew: Boolean = false,
    val legacy: Boolean = false,
    val confirmRemove: Boolean = false
)

data class AccessControlState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val owners: List<String> = emptyList(),
    val users: List<AccessUserDto> = emptyList(),
    val allCategories: List<String> = emptyList(),
    val allAccounts: List<String> = emptyList(),
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
            val users = repo.getAccessUsers()
            val options = repo.getAccessOptions()
            users.onSuccess { res ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        owners = res.owners,
                        users = res.users,
                        allCategories = options.getOrNull()?.categories.orEmpty(),
                        allAccounts = options.getOrNull()?.accounts.orEmpty()
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.message ?: "Couldn't load people") }
            }
        }
    }

    fun startAdd(rawEmail: String) {
        val email = rawEmail.trim().lowercase()
        if (!Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email)) {
            _state.update { it.copy(message = "Enter a valid email address") }
            return
        }
        val current = _state.value
        current.users.firstOrNull { it.email.equals(email, ignoreCase = true) }?.let { edit(it); return }
        if (email in current.owners) {
            _state.update { it.copy(message = "$email is an owner and already has full access") }
            return
        }
        _state.update { it.copy(draft = AccessDraft(email = email, isNew = true)) }
    }

    fun edit(user: AccessUserDto) {
        _state.update {
            it.copy(
                draft = AccessDraft(
                    email = user.email,
                    role = user.role,
                    modules = AccessModule.entries.associateWith { m -> AccessLevel.from(user.permissions.modules[m.key]) },
                    categories = user.permissions.moneyScope.categories,
                    accounts = user.permissions.moneyScope.accounts,
                    legacy = user.legacy
                )
            )
        }
    }

    fun updateDraft(transform: (AccessDraft) -> AccessDraft) {
        _state.update { s -> s.copy(draft = s.draft?.let(transform)?.copy(confirmRemove = false)) }
    }

    fun closeEditor() = _state.update { it.copy(draft = null) }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun save() {
        val draft = _state.value.draft ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val request = AccessUserRequestDto(
                email = draft.email,
                role = draft.role,
                permissions = PermissionsDto(
                    modules = draft.modules.entries.associate { (m, l) -> m.key to l.key },
                    moneyScope = MoneyScopeDto(categories = draft.categories, accounts = draft.accounts)
                )
            )
            repo.saveAccessUser(request, draft.isNew)
                .onSuccess {
                    _state.update {
                        it.copy(
                            isSaving = false,
                            draft = null,
                            message = if (draft.isNew) "${draft.email} can now sign in"
                            else "Saved. ${draft.email} gets the new access within a minute."
                        )
                    }
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
                    _state.update {
                        it.copy(isSaving = false, draft = null, message = "${draft.email} was removed and will be signed out shortly.")
                    }
                    load()
                }
                .onFailure { e -> _state.update { it.copy(isSaving = false, message = e.message ?: "Could not remove") } }
        }
    }
}
