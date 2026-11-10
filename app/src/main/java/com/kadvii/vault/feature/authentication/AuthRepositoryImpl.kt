package com.kadvii.vault.feature.authentication

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.kadvii.vault.core.security.PasswordHashHelper
import java.util.Arrays

class AuthRepositoryImpl(
    context: Context,
    private val prefsName: String = "vault_auth_prefs"
) : AuthRepository {

    private val prefs: SharedPreferences = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    companion object {
        private const val KEY_MASTER_SALT = "auth_master_salt_b64"
        private const val KEY_MASTER_HASH = "auth_master_hash_b64"
        private const val KEY_DECOY_SALT = "auth_decoy_salt_b64"
        private const val KEY_DECOY_HASH = "auth_decoy_hash_b64"
        private const val KEY_BIOMETRIC_ENABLED = "auth_biometric_enabled"
        private const val KEY_FAILED_ATTEMPTS = "auth_failed_attempts"
        private const val KEY_LOCKOUT_TIMESTAMP = "auth_lockout_until"

        private const val MAX_ATTEMPTS_TIER_1 = 5
        private const val LOCKOUT_DURATION_TIER_1_MS = 30_000L // 30 seconds
        private const val MAX_ATTEMPTS_TIER_2 = 10
        private const val LOCKOUT_DURATION_TIER_2_MS = 300_000L // 5 minutes
    }

    override fun isVaultSetup(): Boolean {
        return prefs.contains(KEY_MASTER_HASH) && prefs.contains(KEY_MASTER_SALT)
    }

    override fun hasDecoyPin(): Boolean {
        return prefs.contains(KEY_DECOY_HASH) && prefs.contains(KEY_DECOY_SALT)
    }

    override fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    override fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    override fun saveMasterPin(pin: String) {
        val salt = PasswordHashHelper.generateSalt()
        val pinChars = pin.toCharArray()
        try {
            val hash = PasswordHashHelper.hashPin(pinChars, salt)
            prefs.edit()
                .putString(KEY_MASTER_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(KEY_MASTER_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
                .apply()
        } finally {
            Arrays.fill(pinChars, '0')
        }
    }

    override fun saveDecoyPin(pin: String) {
        val salt = PasswordHashHelper.generateSalt()
        val pinChars = pin.toCharArray()
        try {
            val hash = PasswordHashHelper.hashPin(pinChars, salt)
            prefs.edit()
                .putString(KEY_DECOY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(KEY_DECOY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
                .apply()
        } finally {
            Arrays.fill(pinChars, '0')
        }
    }

    override fun verifyMasterPin(pin: String): Boolean {
        val saltB64 = prefs.getString(KEY_MASTER_SALT, null) ?: return false
        val hashB64 = prefs.getString(KEY_MASTER_HASH, null) ?: return false

        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val expectedHash = Base64.decode(hashB64, Base64.NO_WRAP)
        val pinChars = pin.toCharArray()
        return try {
            PasswordHashHelper.verifyPin(pinChars, salt, expectedHash)
        } finally {
            Arrays.fill(pinChars, '0')
        }
    }

    override fun verifyDecoyPin(pin: String): Boolean {
        val saltB64 = prefs.getString(KEY_DECOY_SALT, null) ?: return false
        val hashB64 = prefs.getString(KEY_DECOY_HASH, null) ?: return false

        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val expectedHash = Base64.decode(hashB64, Base64.NO_WRAP)
        val pinChars = pin.toCharArray()
        return try {
            PasswordHashHelper.verifyPin(pinChars, salt, expectedHash)
        } finally {
            Arrays.fill(pinChars, '0')
        }
    }

    override fun getFailedAttempts(): Int {
        return prefs.getInt(KEY_FAILED_ATTEMPTS, 0)
    }

    override fun recordFailedAttempt(): Int {
        val current = getFailedAttempts() + 1
        val editor = prefs.edit().putInt(KEY_FAILED_ATTEMPTS, current)

        if (current >= MAX_ATTEMPTS_TIER_2) {
            val lockoutUntil = System.currentTimeMillis() + LOCKOUT_DURATION_TIER_2_MS
            editor.putLong(KEY_LOCKOUT_TIMESTAMP, lockoutUntil)
        } else if (current >= MAX_ATTEMPTS_TIER_1) {
            val lockoutUntil = System.currentTimeMillis() + LOCKOUT_DURATION_TIER_1_MS
            editor.putLong(KEY_LOCKOUT_TIMESTAMP, lockoutUntil)
        }

        editor.apply()
        return current
    }

    override fun resetFailedAttempts() {
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_TIMESTAMP, 0L)
            .apply()
    }

    override fun getLockoutRemainingSeconds(): Long {
        val lockoutUntil = prefs.getLong(KEY_LOCKOUT_TIMESTAMP, 0L)
        val now = System.currentTimeMillis()
        return if (lockoutUntil > now) {
            (lockoutUntil - now) / 1000L
        } else {
            0L
        }
    }
}

