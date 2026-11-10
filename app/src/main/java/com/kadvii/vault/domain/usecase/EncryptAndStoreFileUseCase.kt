package com.kadvii.vault.domain.usecase

import com.kadvii.vault.domain.model.VaultFileType
import com.kadvii.vault.domain.repository.VaultRepository
import java.io.InputStream

class EncryptAndStoreFileUseCase(
    private val vaultRepository: VaultRepository
) {
    suspend operator fun invoke(
        sourceInputStream: InputStream,
        originalName: String,
        mimeType: String,
        fileType: VaultFileType,
        albumId: Long? = null,
        isDecoy: Boolean = false
    ): Long {
        return vaultRepository.saveEncryptedFile(
            sourceInputStream = sourceInputStream,
            originalName = originalName,
            mimeType = mimeType,
            fileType = fileType,
            albumId = albumId,
            isDecoy = isDecoy
        )
    }
}

