package com.kadvii.vault.domain.usecase

import com.kadvii.vault.domain.model.VaultFile
import com.kadvii.vault.domain.repository.VaultRepository
import java.io.InputStream

class DecryptVaultFileUseCase(
    private val vaultRepository: VaultRepository
) {
    fun getBytes(file: VaultFile): ByteArray {
        return vaultRepository.getDecryptedBytes(file)
    }

    fun getStream(file: VaultFile): InputStream {
        return vaultRepository.getDecryptedStream(file)
    }
}

