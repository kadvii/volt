package com.kadvii.vault

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kadvii.vault.core.designsystem.VaultTheme
import com.kadvii.vault.core.navigation.NavRoutes
import com.kadvii.vault.core.navigation.RootNavGraph
import com.kadvii.vault.feature.authentication.AuthNavDestination
import com.kadvii.vault.feature.decoy.DecoyNavDestination
import com.kadvii.vault.feature.media.MediaNavDestination
import com.kadvii.vault.feature.vault.VaultNavDestination

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            VaultTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route

                    // Dynamic FLAG_SECURE enforcement: protect Vault and Auth screens against screenshots and recents previews
                    LaunchedEffect(currentRoute) {
                        val isSecureRoute = when {
                            currentRoute == null -> false
                            currentRoute.startsWith("secure_") -> true
                            currentRoute == AuthNavDestination.STEALTH_AUTH_ROUTE -> true
                            currentRoute == AuthNavDestination.SETUP_ROUTE -> true
                            currentRoute == VaultNavDestination.ROUTE -> true
                            currentRoute == DecoyNavDestination.DECOY_DASHBOARD_ROUTE -> true
                            currentRoute == com.kadvii.vault.feature.backup.BackupNavDestination.BACKUP_RESTORE_ROUTE -> true
                            else -> false
                        }

                        if (isSecureRoute) {
                            window.setFlags(
                                WindowManager.LayoutParams.FLAG_SECURE,
                                WindowManager.LayoutParams.FLAG_SECURE
                            )
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        }
                    }

                    RootNavGraph(navController = navController)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Auto-lock vault session when app moves to background
        (applicationContext as? VaultApplication)?.container?.vaultSessionManager?.lock()
    }
}
