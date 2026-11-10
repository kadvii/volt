package com.kadvii.vault.core.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

sealed interface SessionState {
    object Locked : SessionState
    data class AuthenticatedPrimary(val sessionStartTimestamp: Long = System.currentTimeMillis()) : SessionState
    data class AuthenticatedDecoy(val sessionStartTimestamp: Long = System.currentTimeMillis()) : SessionState
}

class VaultSessionManager {

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Locked)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    @Volatile
    var isAwaitingSystemPicker: Boolean = false

    @Volatile
    var isImportInProgress: Boolean = false

    private val lockListeners = CopyOnWriteArrayList<() -> Unit>()

    val isAuthenticated: Boolean
        get() = _sessionState.value !is SessionState.Locked

    val isPrimarySession: Boolean
        get() = _sessionState.value is SessionState.AuthenticatedPrimary

    val isDecoySession: Boolean
        get() = _sessionState.value is SessionState.AuthenticatedDecoy

    fun registerLockListener(listener: () -> Unit) {
        lockListeners.add(listener)
    }

    fun unregisterLockListener(listener: () -> Unit) {
        lockListeners.remove(listener)
    }

    fun authenticatePrimary() {
        _sessionState.value = SessionState.AuthenticatedPrimary()
    }

    fun authenticateDecoy() {
        _sessionState.value = SessionState.AuthenticatedDecoy()
    }

    fun setAuthenticated(isDecoy: Boolean) {
        if (isDecoy) {
            authenticateDecoy()
        } else {
            authenticatePrimary()
        }
    }

    fun lock() {
        _sessionState.value = SessionState.Locked
        isAwaitingSystemPicker = false
        isImportInProgress = false
        lockListeners.forEach { 
            try {
                it()
            } catch (_: Exception) { }
        }
        System.gc()
    }
}
