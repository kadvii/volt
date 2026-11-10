package com.kadvii.vault.feature.authentication

interface AuthRepository {
    fun isVaultSetup(): Boolean
    fun hasDecoyPin(): Boolean
    fun isBiometricEnabled(): Boolean
    fun setBiometricEnabled(enabled: Boolean)

    fun saveMasterPin(pin: String)
    fun saveDecoyPin(pin: String)

    fun verifyMasterPin(pin: String): Boolean
    fun verifyDecoyPin(pin: String): Boolean

    fun getFailedAttempts(): Int
    fun recordFailedAttempt(): Int
    fun resetFailedAttempts()
    fun getLockoutRemainingSeconds(): Long
}

