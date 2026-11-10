package com.kadvii.vault.feature.authentication.presentation

sealed interface AuthNavigationEvent {
    object None : AuthNavigationEvent
    object NavigateToPrimary : AuthNavigationEvent
    object NavigateToDecoy : AuthNavigationEvent
    object DismissToNotes : AuthNavigationEvent
}

data class StealthAuthUiState(
    val enteredPin: String = "",
    val maxDigits: Int = 8,
    val isErrorShake: Boolean = false,
    val errorMessage: String? = null,
    val isLockedOut: Boolean = false,
    val lockoutRemainingSeconds: Long = 0L,
    val isBiometricAvailable: Boolean = false,
    val navigationEvent: AuthNavigationEvent = AuthNavigationEvent.None
)
