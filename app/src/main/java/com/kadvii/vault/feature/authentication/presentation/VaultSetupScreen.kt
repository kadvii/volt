package com.kadvii.vault.feature.authentication.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kadvii.vault.feature.authentication.presentation.components.PinIndicatorDots
import com.kadvii.vault.feature.authentication.presentation.components.StealthKeypad

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultSetupScreen(
    viewModel: VaultSetupViewModel,
    onSetupComplete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isFinished) {
        if (uiState.isFinished) {
            onSetupComplete()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Cancel setup"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                val (title, subtitle) = when (uiState.currentStep) {
                    SetupStep.CREATE_PIN ->
                        Pair("Create Vault PIN", "Enter a 4 to 8 digit PIN for your private vault")
                    SetupStep.CONFIRM_PIN ->
                        Pair("Confirm Vault PIN", "Re-enter your master PIN to confirm")
                    SetupStep.DECOY_PIN ->
                        Pair("Decoy Vault PIN", "Optional secondary PIN that opens a decoy vault under coercion")
                    SetupStep.CONFIRM_DECOY_PIN ->
                        Pair("Confirm Decoy PIN", "Re-enter your decoy PIN")
                    SetupStep.BIOMETRIC_OPTION ->
                        Pair("Biometric Security", "Quickly unlock your primary vault with fingerprint or face")
                    SetupStep.COMPLETE ->
                        Pair("Setup Complete", "Your vault is configured and secured")
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = uiState.errorMessage ?: subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (uiState.errorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                if (uiState.currentStep != SetupStep.BIOMETRIC_OPTION && uiState.currentStep != SetupStep.COMPLETE) {
                    PinIndicatorDots(
                        pinLength = uiState.enteredPin.length,
                        maxDots = 6,
                        isError = uiState.isErrorShake
                    )
                }
            }

            if (uiState.currentStep == SetupStep.BIOMETRIC_OPTION) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Enable Biometric Unlock",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Switch(
                            checked = uiState.isBiometricEnabled,
                            onCheckedChange = viewModel::onToggleBiometrics
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = viewModel::completeSetup,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = "Finish Setup")
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (uiState.currentStep == SetupStep.DECOY_PIN) {
                        OutlinedButton(
                            onClick = viewModel::onSkipDecoyPin,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(text = "Skip Decoy Vault")
                        }
                    }

                    StealthKeypad(
                        onDigitClick = viewModel::onDigitEntered,
                        onBackspaceClick = viewModel::onBackspace,
                        onSubmitClick = viewModel::onNextStep,
                        onBiometricClick = {},
                        onClearClick = viewModel::onClear,
                        isBiometricAvailable = false,
                        canSubmit = uiState.enteredPin.length >= 4,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                }
            }
        }
    }
}

