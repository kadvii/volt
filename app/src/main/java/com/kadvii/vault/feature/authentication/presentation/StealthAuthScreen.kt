package com.kadvii.vault.feature.authentication.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kadvii.vault.core.security.BiometricPromptHelper
import com.kadvii.vault.feature.authentication.presentation.components.PinIndicatorDots
import com.kadvii.vault.feature.authentication.presentation.components.StealthKeypad

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StealthAuthScreen(
    viewModel: StealthAuthViewModel,
    onAuthenticated: (isDecoy: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    // Handle navigation events
    LaunchedEffect(uiState.navigationEvent) {
        when (uiState.navigationEvent) {
            is AuthNavigationEvent.NavigateToPrimary -> {
                viewModel.onNavigationHandled()
                onAuthenticated(false)
            }
            is AuthNavigationEvent.NavigateToDecoy -> {
                viewModel.onNavigationHandled()
                onAuthenticated(true)
            }
            is AuthNavigationEvent.DismissToNotes -> {
                viewModel.onNavigationHandled()
                onDismiss()
            }
            is AuthNavigationEvent.None -> Unit
        }
    }

    // Trigger biometric automatically if available and not locked out
    LaunchedEffect(uiState.isBiometricAvailable, uiState.isLockedOut) {
        if (uiState.isBiometricAvailable && !uiState.isLockedOut && activity != null) {
            BiometricPromptHelper.showBiometricPrompt(
                activity = activity,
                onSuccess = viewModel::onBiometricSuccess
            )
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
                            contentDescription = "Back to Notes"
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
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Security Verification",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (uiState.isLockedOut) {
                    Text(
                        text = "Too many attempts. Try again in ${uiState.lockoutRemainingSeconds}s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (uiState.errorMessage != null) {
                    Text(
                        text = uiState.errorMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        text = "Enter your passcode to continue",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(36.dp))

                PinIndicatorDots(
                    pinLength = uiState.enteredPin.length,
                    maxDots = 6,
                    isError = uiState.isErrorShake
                )
            }

            StealthKeypad(
                onDigitClick = viewModel::onDigitEntered,
                onBackspaceClick = viewModel::onBackspace,
                onSubmitClick = viewModel::onSubmitPin,
                onBiometricClick = {
                    if (activity != null) {
                        BiometricPromptHelper.showBiometricPrompt(
                            activity = activity,
                            onSuccess = viewModel::onBiometricSuccess
                        )
                    }
                },
                onClearClick = viewModel::onClear,
                isBiometricAvailable = uiState.isBiometricAvailable && !uiState.isLockedOut,
                canSubmit = uiState.enteredPin.length >= 4 && !uiState.isLockedOut,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }
}
