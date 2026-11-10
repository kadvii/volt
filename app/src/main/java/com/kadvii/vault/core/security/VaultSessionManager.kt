package com.kadvii.vault.core.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SessionState {
    object Locked : SessionState
    data class AuthenticatedPrimary(val sessionStartTimestamp: Long = System.currentTimeMillis()) : SessionState
    data class AuthenticatedDecoy(val sessionStartTimestamp: Long = System.currentTimeMillis()) : SessionState
}

class VaultSessionManager {

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Locked)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    val isAuthenticated: Boolean
        get() = _sessionState.value !is SessionState.Locked

    val isPrimarySession: Boolean
        get() = _sessionState.value is SessionState.AuthenticatedPrimary

    val isDecoySession: Boolean
        get() = _sessionState.value is SessionState.AuthenticatedDecoy

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
        System.gc()
    }
}

