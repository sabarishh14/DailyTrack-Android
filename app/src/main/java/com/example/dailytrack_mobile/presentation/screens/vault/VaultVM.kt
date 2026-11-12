package com.example.dailytrack_mobile.presentation.screens.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailytrack_mobile.data.repository.InvestmentsRepository
import com.example.dailytrack_mobile.data.vault.VaultCrypto
import com.example.dailytrack_mobile.data.vault.VaultStore
import com.example.dailytrack_mobile.data.vault.VaultUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The private vault's screens (Settings › Privacy & Security › Private vault, and
 * the lock over Investments). The work is in data/vault; everything there blocks,
 * so it runs on the IO dispatcher here.
 */
@HiltViewModel
class VaultVM @Inject constructor(
    private val store: VaultStore,
    private val investments: InvestmentsRepository,
) : ViewModel() {

    val ui: StateFlow<VaultUi> = store.ui

    /** What's happening now ("Locking them away…"), or null. */
    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _friends = MutableStateFlow<List<VaultStore.Friend>>(emptyList())
    val friends: StateFlow<List<VaultStore.Friend>> = _friends.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { store.status() }
            runCatching { store.tryRemembered() }
            loadFriends()
        }
    }

    fun clearError() { _error.value = null }

    private fun run(busy: String, onDone: () -> Unit = {}, work: () -> Unit) {
        if (_busy.value != null) return
        _busy.value = busy
        _error.value = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching(work) }
            _busy.value = null
            result.onSuccess {
                investments.clearCache()
                onDone()
            }.onFailure { _error.value = it.message ?: "Something went wrong" }
        }
    }

    fun unlock(secret: String, recovery: Boolean, remember: Boolean, onDone: () -> Unit = {}) =
        run("Unlocking…", onDone) { store.unlock(secret, recovery, remember); runCatching { loadFriendsNow() } }

    fun turnOn(passphrase: String, recoveryKey: String, remember: Boolean, onDone: () -> Unit) =
        run("Starting…", onDone) {
            store.turnOn(passphrase, recoveryKey, remember) { _busy.value = it }
            runCatching { loadFriendsNow() }
        }

    fun turnOff(onDone: () -> Unit) = run("Putting your investments back…", onDone) { store.turnOff { _busy.value = it } }

    fun lock() = run("Locking…") { store.lockNow() }

    fun changePassphrase(passphrase: String, onDone: () -> Unit) = run("Saving…", onDone) { store.changePassphrase(passphrase) }

    fun newRecoveryKey(onCode: (String) -> Unit) {
        var code = ""
        run("Saving…", { onCode(code) }) { code = store.newRecoveryKey() }
    }

    fun startOver() = run("Starting over…") { store.startOver() }

    private fun loadFriendsNow() { _friends.value = store.syncGrants() }

    private fun loadFriends() { runCatching { if (store.isOpen && !store.readOnly) loadFriendsNow() } }

    companion object {
        const val MIN_PASSPHRASE = 12
        fun longEnough(p: String) = VaultCrypto.normalizePassphrase(p).length >= MIN_PASSPHRASE
        fun same(a: String, b: String) = VaultCrypto.normalizePassphrase(a) == VaultCrypto.normalizePassphrase(b)
    }
}
