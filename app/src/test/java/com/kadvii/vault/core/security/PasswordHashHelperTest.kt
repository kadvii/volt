package com.kadvii.vault.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHashHelperTest {

    @Test
    fun generateSalt_returnsExpectedByteLength() {
        val salt = PasswordHashHelper.generateSalt()
        assertNotNull(salt)
        assertEquals(32, salt.size)
    }

    @Test
    fun hashPin_isDeterministicWithSameSalt() {
        val salt = PasswordHashHelper.generateSalt()
        val hash1 = PasswordHashHelper.hashPin("1234", salt)
        val hash2 = PasswordHashHelper.hashPin("1234", salt)

        assertEquals(hash1.toList(), hash2.toList())
    }

    @Test
    fun hashPin_producesDifferentHashesForDifferentPins() {
        val salt = PasswordHashHelper.generateSalt()
        val hash1 = PasswordHashHelper.hashPin("1234", salt)
        val hash2 = PasswordHashHelper.hashPin("5678", salt)

        assertNotEquals(hash1.toList(), hash2.toList())
    }

    @Test
    fun verifyPin_matchesCorrectPinAndRejectsIncorrectPin() {
        val salt = PasswordHashHelper.generateSalt()
        val expectedHash = PasswordHashHelper.hashPin("9876", salt)

        assertTrue(PasswordHashHelper.verifyPin("9876", salt, expectedHash))
        assertFalse(PasswordHashHelper.verifyPin("1234", salt, expectedHash))
        assertFalse(PasswordHashHelper.verifyPin("98760", salt, expectedHash))
    }
}
