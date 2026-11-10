package com.kadvii.vault.di

import android.content.Context
import com.kadvii.vault.core.security.AesGcmCipherService
import com.kadvii.vault.core.security.AndroidKeystoreManager
import com.kadvii.vault.core.security.KeystoreManager
import com.kadvii.vault.core.security.VaultSessionManager
import com.kadvii.vault.data.database.NotesDatabase
import com.kadvii.vault.data.database.VaultDatabase
import com.kadvii.vault.data.repository.NotesRepositoryImpl
import com.kadvii.vault.data.repository.VaultRepositoryImpl
import com.kadvii.vault.domain.repository.NotesRepository
import com.kadvii.vault.domain.repository.VaultRepository
import com.kadvii.vault.domain.usecase.DecryptVaultFileUseCase
import com.kadvii.vault.domain.usecase.DeleteNoteUseCase
import com.kadvii.vault.domain.usecase.DeleteVaultFileUseCase
import com.kadvii.vault.domain.usecase.EncryptAndStoreFileUseCase
import com.kadvii.vault.domain.usecase.GetNoteByIdUseCase
import com.kadvii.vault.domain.usecase.GetNotesUseCase
import com.kadvii.vault.domain.usecase.GetVaultFilesUseCase
import com.kadvii.vault.domain.usecase.SaveNoteUseCase
import com.kadvii.vault.feature.authentication.AuthRepository
import com.kadvii.vault.feature.authentication.AuthRepositoryImpl
import com.kadvii.vault.feature.authentication.AuthenticatePinUseCase
import com.kadvii.vault.feature.authentication.SetupMasterPinUseCase
import com.kadvii.vault.feature.backup.BackupManager

interface AppContainer {
    val notesDatabase: NotesDatabase
    val notesRepository: NotesRepository
    val getNotesUseCase: GetNotesUseCase
    val saveNoteUseCase: SaveNoteUseCase
    val deleteNoteUseCase: DeleteNoteUseCase
    val getNoteByIdUseCase: GetNoteByIdUseCase

    val authRepository: AuthRepository
    val vaultSessionManager: VaultSessionManager
    val setupMasterPinUseCase: SetupMasterPinUseCase
    val authenticatePinUseCase: AuthenticatePinUseCase

    val vaultDatabase: VaultDatabase
    val cipherService: AesGcmCipherService
    val keystoreManager: KeystoreManager
    val vaultRepository: VaultRepository
    val getVaultFilesUseCase: GetVaultFilesUseCase
    val encryptAndStoreFileUseCase: EncryptAndStoreFileUseCase
    val deleteVaultFileUseCase: DeleteVaultFileUseCase
    val decryptVaultFileUseCase: DecryptVaultFileUseCase
    val backupManager: BackupManager
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    override val notesDatabase: NotesDatabase by lazy {
        NotesDatabase.getInstance(context)
    }

    override val notesRepository: NotesRepository by lazy {
        NotesRepositoryImpl(notesDatabase.noteDao())
    }

    override val getNotesUseCase: GetNotesUseCase by lazy {
        GetNotesUseCase(notesRepository)
    }

    override val saveNoteUseCase: SaveNoteUseCase by lazy {
        SaveNoteUseCase(notesRepository)
    }

    override val deleteNoteUseCase: DeleteNoteUseCase by lazy {
        DeleteNoteUseCase(notesRepository)
    }

    override val getNoteByIdUseCase: GetNoteByIdUseCase by lazy {
        GetNoteByIdUseCase(notesRepository)
    }

    override val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl(context)
    }

    override val vaultSessionManager: VaultSessionManager by lazy {
        VaultSessionManager()
    }

    override val setupMasterPinUseCase: SetupMasterPinUseCase by lazy {
        SetupMasterPinUseCase(authRepository)
    }

    override val authenticatePinUseCase: AuthenticatePinUseCase by lazy {
        AuthenticatePinUseCase(authRepository)
    }

    override val vaultDatabase: VaultDatabase by lazy {
        VaultDatabase.getInstance(context)
    }

    override val cipherService: AesGcmCipherService by lazy {
        AesGcmCipherService()
    }

    override val keystoreManager: KeystoreManager by lazy {
        AndroidKeystoreManager()
    }

    override val vaultRepository: VaultRepository by lazy {
        VaultRepositoryImpl(
            vaultFileDao = vaultDatabase.vaultFileDao(),
            albumDao = vaultDatabase.albumDao(),
            cipherService = cipherService,
            keystoreManager = keystoreManager,
            filesDir = context.filesDir
        )
    }

    override val getVaultFilesUseCase: GetVaultFilesUseCase by lazy {
        GetVaultFilesUseCase(vaultRepository)
    }

    override val encryptAndStoreFileUseCase: EncryptAndStoreFileUseCase by lazy {
        EncryptAndStoreFileUseCase(vaultRepository)
    }

    override val deleteVaultFileUseCase: DeleteVaultFileUseCase by lazy {
        DeleteVaultFileUseCase(vaultRepository)
    }

    override val decryptVaultFileUseCase: DecryptVaultFileUseCase by lazy {
        DecryptVaultFileUseCase(vaultRepository)
    }

    override val backupManager: BackupManager by lazy {
        BackupManager(
            vaultFileDao = vaultDatabase.vaultFileDao(),
            cipherService = cipherService,
            keystoreManager = keystoreManager,
            filesDir = context.filesDir
        )
    }
}
