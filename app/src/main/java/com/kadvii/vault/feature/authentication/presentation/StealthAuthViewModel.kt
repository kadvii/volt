package com.kadvii.vault.feature.authentication.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kadvii.vault.core.security.VaultSessionManager
import com.kadvii.vault.feature.authentication.AuthRepository
import com.kadvii.vault.feature.authentication.AuthResult
import com.kadvii.vault.feature.authentication.AuthenticatePinUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class StealthAuthViewModel(
    private val authenticatePinUseCase: AuthenticatePinUseCase,
    private val authRepository: AuthRepository,
    private val vaultSessionManager: VaultSessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(StealthAuthUiState())
    val uiState: StateFlow<StealthAuthUiState> = _uiState.asStateFlow()

    private var lockoutJob: Job? = null

    init {
        checkLockoutAndBiometrics()
    }

    private fun checkLockoutAndBiometrics() {
        val remaining = authRepository.getLockoutRemainingSeconds()
        val isBioEnabled = authRepository.isBiometricEnabled()

        if (remaining > 0) {
            startLockoutCountdown(remaining)
        } else {
            _uiState.update {
                it.copy(
                    isLockedOut = false,
                    lockoutRemainingSeconds = 0L,
                    isBiometricAvailable = isBioEnabled
                )
            }
        }
    }

    fun onDigitEntered(digit: Char) {
        if (_uiState.value.isLockedOut) return
        if (_uiState.value.enteredPin.length >= _uiState.value.maxDigits) return

        val newPin = _uiState.value.enteredPin + digit
        _uiState.update { it.copy(enteredPin = newPin, isErrorShake = false, errorMessage = null) }
    }

    fun onBackspace() {
        if (_uiState.value.isLockedOut) return
        val currentPin = _uiState.value.enteredPin
        if (currentPin.isNotEmpty()) {
            _uiState.update { it.copy(enteredPin = currentPin.dropLast(1), isErrorShake = false, errorMessage = null) }
        }
    }

    fun onClear() {
        if (_uiState.value.isLockedOut) return
        _uiState.update { it.copy(enteredPin = "", isErrorShake = false, errorMessage = null) }
    }

    fun onSubmitPin() {
        val pin = _uiState.value.enteredPin
        if (pin.length < 4 || _uiState.value.isLockedOut) return

        when (val result = authenticatePinUseCase(pin)) {
            is AuthResult.MasterSuccess -> {
                vaultSessionManager.setAuthenticated(isDecoy = false)
                _uiState.update { it.copy(navigationEvent = AuthNavigationEvent.NavigateToPrimary) }
            }
            is AuthResult.DecoySuccess -> {
                vaultSessionManager.setAuthenticated(isDecoy = true)
                _uiState.update { it.copy(navigationEvent = AuthNavigationEvent.NavigateToDecoy) }
            }
            is AuthResult.InvalidPin -> {
                // After 5 consecutive failures, dismiss back to notes to protect secrecy
                if (result.consecutiveFailedAttempts >= 5) {
                    _uiState.update {
                        it.copy(
                            enteredPin = "",
                            isErrorShake = true,
                            errorMessage = "Incorrect passcode",
                            navigationEvent = AuthNavigationEvent.DismissToNotes
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            enteredPin = "",
                            isErrorShake = true,
                            errorMessage = "Incorrect passcode. Try again."
                        )
                    }
                }
            }
            is AuthResult.LockedOut -> {
                _uiState.update {
                    it.copy(
                        enteredPin = "",
                        isErrorShake = true,
                        errorMessage = "Locked out",
                        navigationEvent = AuthNavigationEvent.DismissToNotes
                    )
                }
                startLockoutCountdown(result.remainingSeconds)
            }
        }
    }

    fun onBiometricSuccess() {
        vaultSessionManager.setAuthenticated(isDecoy = false)
        _uiState.update { it.copy(navigationEvent = AuthNavigationEvent.NavigateToPrimary) }
    }

    fun onNavigationHandled() {
        _uiState.update { it.copy(navigationEvent = AuthNavigationEvent.None) }
    }

    private fun startLockoutCountdown(seconds: Long) {
        lockoutJob?.cancel()
        lockoutJob = viewModelScope.launch {
            var currentSeconds = seconds
            _uiState.update {
                it.copy(isLockedOut = true, lockoutRemainingSeconds = currentSeconds)
            }
            while (currentSeconds > 0) {
                delay(1000L)
                currentSeconds--
                _uiState.update {
                    it.copy(
                        lockoutRemainingSeconds = currentSeconds,
                        isLockedOut = currentSeconds > 0
                    )
                }
            }
        }
    }

    companion object {
        fun provideFactory(
            authenticatePinUseCase: AuthenticatePinUseCase,
            authRepository: AuthRepository,
            vaultSessionManager: VaultSessionManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return StealthAuthViewModel(
                    authenticatePinUseCase = authenticatePinUseCase,
                    authRepository = authRepository,
                    vaultSessionManager = vaultSessionManager
                ) as T
            }
        }
    }
}

