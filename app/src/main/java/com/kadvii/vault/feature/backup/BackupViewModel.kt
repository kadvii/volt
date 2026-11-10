package com.kadvii.vault.feature.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream

data class BackupUiState(
    val backupPassphrase: String = "",
    val confirmBackupPassphrase: String = "",
    val restorePassphrase: String = "",
    val isProcessing: Boolean = false,
    val statusMessage: String? = null,
    val isSuccess: Boolean = false,
    val isError: Boolean = false
)

class BackupViewModel(
    private val backupManager: BackupManager,
    private val isDecoy: Boolean
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    fun onBackupPassphraseChange(pass: String) {
        _uiState.update { it.copy(backupPassphrase = pass, statusMessage = null) }
    }

    fun onConfirmBackupPassphraseChange(pass: String) {
        _uiState.update { it.copy(confirmBackupPassphrase = pass, statusMessage = null) }
    }

    fun onRestorePassphraseChange(pass: String) {
        _uiState.update { it.copy(restorePassphrase = pass, statusMessage = null) }
    }

    fun createBackup(outputStream: OutputStream) {
        val state = _uiState.value
        if (state.backupPassphrase.length < 6) {
            _uiState.update { it.copy(statusMessage = "Passphrase must be at least 6 characters", isError = true) }
            return
        }
        if (state.backupPassphrase != state.confirmBackupPassphrase) {
            _uiState.update { it.copy(statusMessage = "Passphrases do not match", isError = true) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, statusMessage = "Creating encrypted backup...") }
            try {
                outputStream.use { out ->
                    val result = backupManager.createBackup(state.backupPassphrase, out, isDecoy)
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            statusMessage = "Backup created successfully (${result.fileCount} files)",
                            isSuccess = true,
                            isError = false,
                            backupPassphrase = "",
                            confirmBackupPassphrase = ""
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        statusMessage = "Backup failed: ${e.message}",
                        isError = true,
                        isSuccess = false
                    )
                }
            }
        }
    }

    fun restoreBackup(inputStream: InputStream) {
        val state = _uiState.value
        if (state.restorePassphrase.isBlank()) {
            _uiState.update { it.copy(statusMessage = "Please enter your backup passphrase", isError = true) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, statusMessage = "Verifying and restoring backup...") }
            try {
                inputStream.use { stream ->
                    val result = backupManager.restoreBackup(state.restorePassphrase, stream, isDecoy)
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            statusMessage = "Restored ${result.restoredFilesCount} files successfully!",
                            isSuccess = true,
                            isError = false,
                            restorePassphrase = ""
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        statusMessage = "Restore failed: Invalid passphrase or corrupted backup",
                        isError = true,
                        isSuccess = false
                    )
                }
            }
        }
    }

    companion object {
        fun provideFactory(
            backupManager: BackupManager,
            isDecoy: Boolean
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return BackupViewModel(backupManager, isDecoy) as T
            }
        }
    }
}

