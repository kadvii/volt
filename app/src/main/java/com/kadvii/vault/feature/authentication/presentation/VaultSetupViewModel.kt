package com.kadvii.vault.feature.authentication.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kadvii.vault.core.security.VaultSessionManager
import com.kadvii.vault.feature.authentication.SetupMasterPinUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class SetupStep {
    CREATE_PIN,
    CONFIRM_PIN,
    DECOY_PIN,
    CONFIRM_DECOY_PIN,
    BIOMETRIC_OPTION,
    COMPLETE
}

data class VaultSetupUiState(
    val currentStep: SetupStep = SetupStep.CREATE_PIN,
    val enteredPin: String = "",
    val masterPin: String = "",
    val decoyPin: String? = null,
    val isBiometricEnabled: Boolean = false,
    val errorMessage: String? = null,
    val isErrorShake: Boolean = false,
    val isFinished: Boolean = false
)

class VaultSetupViewModel(
    private val setupMasterPinUseCase: SetupMasterPinUseCase,
    private val vaultSessionManager: VaultSessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaultSetupUiState())
    val uiState: StateFlow<VaultSetupUiState> = _uiState.asStateFlow()

    fun onDigitEntered(digit: Char) {
        if (_uiState.value.enteredPin.length >= 8) return
        val newPin = _uiState.value.enteredPin + digit
        _uiState.update { it.copy(enteredPin = newPin, errorMessage = null, isErrorShake = false) }
    }

    fun onBackspace() {
        val current = _uiState.value.enteredPin
        if (current.isNotEmpty()) {
            _uiState.update { it.copy(enteredPin = current.dropLast(1), errorMessage = null, isErrorShake = false) }
        }
    }

    fun onClear() {
        _uiState.update { it.copy(enteredPin = "", errorMessage = null, isErrorShake = false) }
    }

    fun onNextStep() {
        val pin = _uiState.value.enteredPin

        when (_uiState.value.currentStep) {
            SetupStep.CREATE_PIN -> {
                if (pin.length < 4) {
                    _uiState.update { it.copy(errorMessage = "PIN must be at least 4 digits", isErrorShake = true) }
                    return
                }
                _uiState.update {
                    it.copy(
                        currentStep = SetupStep.CONFIRM_PIN,
                        masterPin = pin,
                        enteredPin = "",
                        errorMessage = null
                    )
                }
            }

            SetupStep.CONFIRM_PIN -> {
                if (pin != _uiState.value.masterPin) {
                    _uiState.update {
                        it.copy(
                            enteredPin = "",
                            errorMessage = "PINs do not match. Try again.",
                            isErrorShake = true
                        )
                    }
                    return
                }
                // PIN confirmed, advance to Decoy PIN step
                _uiState.update {
                    it.copy(
                        currentStep = SetupStep.DECOY_PIN,
                        enteredPin = "",
                        errorMessage = null
                    )
                }
            }

            SetupStep.DECOY_PIN -> {
                if (pin.isNotEmpty()) {
                    if (pin.length < 4) {
                        _uiState.update { it.copy(errorMessage = "Decoy PIN must be at least 4 digits", isErrorShake = true) }
                        return
                    }
                    if (pin == _uiState.value.masterPin) {
                        _uiState.update { it.copy(errorMessage = "Decoy PIN cannot match Master PIN", isErrorShake = true) }
                        return
                    }
                    _uiState.update {
                        it.copy(
                            currentStep = SetupStep.BIOMETRIC_OPTION,
                            decoyPin = pin,
                            enteredPin = "",
                            errorMessage = null
                        )
                    }
                } else {
                    // Skipped decoy PIN
                    _uiState.update {
                        it.copy(
                            currentStep = SetupStep.BIOMETRIC_OPTION,
                            decoyPin = null,
                            enteredPin = "",
                            errorMessage = null
                        )
                    }
                }
            }

            SetupStep.CONFIRM_DECOY_PIN -> {
                // If needed
            }

            SetupStep.BIOMETRIC_OPTION -> {
                completeSetup()
            }

            SetupStep.COMPLETE -> Unit
        }
    }

    fun onSkipDecoyPin() {
        _uiState.update {
            it.copy(
                currentStep = SetupStep.BIOMETRIC_OPTION,
                decoyPin = null,
                enteredPin = "",
                errorMessage = null
            )
        }
    }

    fun onToggleBiometrics(enabled: Boolean) {
        _uiState.update { it.copy(isBiometricEnabled = enabled) }
    }

    fun completeSetup() {
        val state = _uiState.value
        setupMasterPinUseCase(
            masterPin = state.masterPin,
            decoyPin = state.decoyPin,
            enableBiometric = state.isBiometricEnabled
        )
        vaultSessionManager.setAuthenticated(isDecoy = false)
        _uiState.update { it.copy(isFinished = true) }
    }

    companion object {
        fun provideFactory(
            setupMasterPinUseCase: SetupMasterPinUseCase,
            vaultSessionManager: VaultSessionManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return VaultSetupViewModel(
                    setupMasterPinUseCase = setupMasterPinUseCase,
                    vaultSessionManager = vaultSessionManager
                ) as T
            }
        }
    }
}

