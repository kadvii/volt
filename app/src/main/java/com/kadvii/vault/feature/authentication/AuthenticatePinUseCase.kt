package com.kadvii.vault.feature.authentication

sealed interface AuthResult {
    object MasterSuccess : AuthResult
    object DecoySuccess : AuthResult
    data class InvalidPin(val consecutiveFailedAttempts: Int) : AuthResult
    data class LockedOut(val remainingSeconds: Long) : AuthResult
}

class AuthenticatePinUseCase(
    private val authRepository: AuthRepository
) {
    operator fun invoke(candidatePin: String): AuthResult {
        val lockoutRemaining = authRepository.getLockoutRemainingSeconds()
        if (lockoutRemaining > 0) {
            return AuthResult.LockedOut(lockoutRemaining)
        }

        // Check Master PIN
        if (authRepository.verifyMasterPin(candidatePin)) {
            authRepository.resetFailedAttempts()
            return AuthResult.MasterSuccess
        }

        // Check Decoy PIN
        if (authRepository.verifyDecoyPin(candidatePin)) {
            authRepository.resetFailedAttempts()
            return AuthResult.DecoySuccess
        }

        // Failed attempt
        val failedCount = authRepository.recordFailedAttempt()
        val postLockout = authRepository.getLockoutRemainingSeconds()
        return if (postLockout > 0) {
            AuthResult.LockedOut(postLockout)
        } else {
            AuthResult.InvalidPin(failedCount)
        }
    }
}

