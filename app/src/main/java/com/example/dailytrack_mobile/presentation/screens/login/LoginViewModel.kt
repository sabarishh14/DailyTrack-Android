package com.example.dailytrack_mobile.presentation.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailytrack_mobile.data.local.datastore.DemoModeManager
import com.example.dailytrack_mobile.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val demoModeManager: DemoModeManager
) : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    val state = _state.asStateFlow()

    init {
        // Explain forced sign-outs (revoked access, expired session) on the login screen.
        viewModelScope.launch {
            authRepository.sessionNotice.collect { notice ->
                if (notice != null) {
                    _state.update { it.copy(isLoading = false, errorMessage = notice) }
                    authRepository.consumeSessionNotice()
                }
            }
        }
    }

    fun onAction(action: LoginAction) {
        when (action) {
            is LoginAction.OnGoogleTokenReceived -> {
                viewModelScope.launch {
                    _state.update { it.copy(isLoading = true, errorMessage = null) }
                    val result = authRepository.loginWithFirebaseToken(
                        idToken = action.firebaseIdToken,
                        email = action.email,
                        name = action.displayName,
                        photoUrl = action.photoUrl
                    )
                    if (result.isSuccess) {
                        _state.update { it.copy(isLoading = false, isLoginSuccess = true) }
                    } else {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = result.exceptionOrNull()?.message ?: "Sign-in failed"
                            )
                        }
                    }
                }
            }
            is LoginAction.OnLoginFailed -> {
                _state.update { it.copy(isLoading = false, errorMessage = action.message) }
            }
            is LoginAction.OnClearError -> {
                _state.update { it.copy(errorMessage = null) }
            }
            is LoginAction.OnContinueDemoMode -> {
                viewModelScope.launch {
                    demoModeManager.setDemoModeEnabled(true)
                }
            }
        }
    }
}
