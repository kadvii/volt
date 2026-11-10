package com.kadvii.vault.domain.usecase

import com.kadvii.vault.domain.model.VaultFile
import com.kadvii.vault.domain.repository.VaultRepository

class DeleteVaultFileUseCase(
    private val vaultRepository: VaultRepository
) {
    suspend operator fun invoke(file: VaultFile): Boolean {
        return vaultRepository.deleteFile(file)
    }
}

