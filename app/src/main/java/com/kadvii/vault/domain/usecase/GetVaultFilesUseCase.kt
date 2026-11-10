package com.kadvii.vault.domain.usecase

import com.kadvii.vault.domain.model.VaultFile
import com.kadvii.vault.domain.model.VaultFileType
import com.kadvii.vault.domain.repository.VaultRepository
import kotlinx.coroutines.flow.Flow

class GetVaultFilesUseCase(
    private val vaultRepository: VaultRepository
) {
    operator fun invoke(
        fileType: VaultFileType? = null,
        albumId: Long? = null,
        isDecoy: Boolean = false
    ): Flow<List<VaultFile>> {
        return vaultRepository.getFiles(fileType, albumId, isDecoy)
    }
}

