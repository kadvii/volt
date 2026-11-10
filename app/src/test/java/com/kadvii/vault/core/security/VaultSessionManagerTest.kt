package com.kadvii.vault.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VaultSessionManagerTest {

    private lateinit var sessionManager: VaultSessionManager

    @Before
    fun setUp() {
        sessionManager = VaultSessionManager()
    }

    @Test
    fun initialState_isLocked() {
        assertFalse(sessionManager.isAuthenticated)
        assertFalse(sessionManager.isPrimarySession)
        assertFalse(sessionManager.isDecoySession)
        assertEquals(SessionState.Locked, sessionManager.sessionState.value)
    }

    @Test
    fun authenticatePrimary_setsAuthenticatedPrimary() {
        sessionManager.authenticatePrimary()

        assertTrue(sessionManager.isAuthenticated)
        assertTrue(sessionManager.isPrimarySession)
        assertFalse(sessionManager.isDecoySession)
    }

    @Test
    fun authenticateDecoy_setsAuthenticatedDecoy() {
        sessionManager.authenticateDecoy()

        assertTrue(sessionManager.isAuthenticated)
        assertFalse(sessionManager.isPrimarySession)
        assertTrue(sessionManager.isDecoySession)
    }

    @Test
    fun lock_resetsSessionAndCallsListeners() {
        var listenerCalled = false
        sessionManager.registerLockListener { listenerCalled = true }

        sessionManager.authenticatePrimary()
        sessionManager.isAwaitingSystemPicker = true
        sessionManager.isImportInProgress = true

        sessionManager.lock()

        assertFalse(sessionManager.isAuthenticated)
        assertFalse(sessionManager.isAwaitingSystemPicker)
        assertFalse(sessionManager.isImportInProgress)
        assertTrue(listenerCalled)
    }
}
