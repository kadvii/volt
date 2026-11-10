package com.kadvii.vault.feature.decoy

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kadvii.vault.domain.model.VaultFile
import com.kadvii.vault.domain.usecase.DecryptVaultFileUseCase
import com.kadvii.vault.feature.vault.VaultDashboardScreen
import com.kadvii.vault.feature.vault.VaultViewModel

@Composable
fun DecoyDashboardScreen(
    viewModel: VaultViewModel,
    decryptVaultFileUseCase: DecryptVaultFileUseCase,
    onFileClick: (VaultFile) -> Unit,
    onLockClick: () -> Unit,
    onBackupClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    VaultDashboardScreen(
        viewModel = viewModel,
        decryptVaultFileUseCase = decryptVaultFileUseCase,
        onFileClick = onFileClick,
        onLockClick = onLockClick,
        onBackupClick = onBackupClick,
        title = "Private Vault",
        modifier = modifier
    )
}
