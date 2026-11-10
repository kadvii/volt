package com.kadvii.vault.feature.authentication

class SetupMasterPinUseCase(
    private val authRepository: AuthRepository
) {
    operator fun invoke(masterPin: String, decoyPin: String? = null, enableBiometric: Boolean = false) {
        authRepository.saveMasterPin(masterPin)
        if (!decoyPin.isNullOrBlank() && decoyPin != masterPin) {
            authRepository.saveDecoyPin(decoyPin)
        }
        authRepository.setBiometricEnabled(enableBiometric)
        authRepository.resetFailedAttempts()
    }
}

