package com.kadvii.vault.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHashHelper {

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val DEFAULT_ITERATIONS = 100_000
    private const val DEFAULT_KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 32

    fun generateSalt(): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH_BYTES)
        random.nextBytes(salt)
        return salt
    }

    fun hashPin(
        pin: CharArray,
        salt: ByteArray,
        iterations: Int = DEFAULT_ITERATIONS,
        keyLengthBits: Int = DEFAULT_KEY_LENGTH_BITS
    ): ByteArray {
        val spec = PBEKeySpec(pin, salt, iterations, keyLengthBits)
        return try {
            val factory = SecretKeyFactory.getInstance(ALGORITHM)
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun hashPin(
        pin: String,
        salt: ByteArray,
        iterations: Int = DEFAULT_ITERATIONS,
        keyLengthBits: Int = DEFAULT_KEY_LENGTH_BITS
    ): ByteArray = hashPin(pin.toCharArray(), salt, iterations, keyLengthBits)

    fun verifyPin(
        candidatePin: CharArray,
        salt: ByteArray,
        expectedHash: ByteArray,
        iterations: Int = DEFAULT_ITERATIONS
    ): Boolean {
        val candidateHash = hashPin(candidatePin, salt, iterations, expectedHash.size * 8)
        return try {
            MessageDigest.isEqual(candidateHash, expectedHash)
        } finally {
            Arrays.fill(candidateHash, 0.toByte())
        }
    }

    fun verifyPin(
        candidatePin: String,
        salt: ByteArray,
        expectedHash: ByteArray,
        iterations: Int = DEFAULT_ITERATIONS
    ): Boolean = verifyPin(candidatePin.toCharArray(), salt, expectedHash, iterations)
}

