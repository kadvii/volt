package com.kadvii.vault.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kadvii.vault.VaultApplication
import com.kadvii.vault.domain.model.VaultFileType
import com.kadvii.vault.feature.authentication.AuthNavDestination
import com.kadvii.vault.feature.authentication.presentation.StealthAuthScreen
import com.kadvii.vault.feature.authentication.presentation.StealthAuthViewModel
import com.kadvii.vault.feature.authentication.presentation.VaultSetupScreen
import com.kadvii.vault.feature.authentication.presentation.VaultSetupViewModel
import com.kadvii.vault.feature.backup.BackupNavDestination
import com.kadvii.vault.feature.backup.BackupScreen
import com.kadvii.vault.feature.backup.BackupViewModel
import com.kadvii.vault.feature.decoy.DecoyDashboardScreen
import com.kadvii.vault.feature.decoy.DecoyNavDestination
import com.kadvii.vault.feature.media.MediaNavDestination
import com.kadvii.vault.feature.media.SecureDocumentViewerScreen
import com.kadvii.vault.feature.media.SecureImageViewerScreen
import com.kadvii.vault.feature.media.SecureVideoPlayerScreen
import com.kadvii.vault.feature.notes.NoteEditorScreen
import com.kadvii.vault.feature.notes.NoteEditorViewModel
import com.kadvii.vault.feature.notes.NotesHomeScreen
import com.kadvii.vault.feature.notes.NotesViewModel
import com.kadvii.vault.feature.vault.VaultDashboardScreen
import com.kadvii.vault.feature.vault.VaultNavDestination
import com.kadvii.vault.feature.vault.VaultViewModel

@Composable
fun RootNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current.applicationContext as VaultApplication
    val appContainer = context.container
    val coroutineScope = rememberCoroutineScope()

    NavHost(
        navController = navController,
        startDestination = NavRoutes.NOTES_HOME,
        modifier = modifier
    ) {
        composable(NavRoutes.NOTES_HOME) {
            val notesViewModel: NotesViewModel = viewModel(
                factory = NotesViewModel.provideFactory(
                    getNotesUseCase = appContainer.getNotesUseCase,
                    saveNoteUseCase = appContainer.saveNoteUseCase,
                    deleteNoteUseCase = appContainer.deleteNoteUseCase,
                    notesRepository = appContainer.notesRepository
                )
            )

            NotesHomeScreen(
                viewModel = notesViewModel,
                onNoteClick = { noteId ->
                    navController.navigate(NavRoutes.noteEditorRoute(noteId))
                },
                onCreateNoteClick = {
                    navController.navigate(NavRoutes.noteEditorRoute(0L))
                },
                onSecretTrigger = {
                    if (appContainer.authRepository.isVaultSetup()) {
                        navController.navigate(AuthNavDestination.STEALTH_AUTH_ROUTE)
                    } else {
                        navController.navigate(AuthNavDestination.SETUP_ROUTE)
                    }
                }
            )
        }

        composable(
            route = NavRoutes.NOTE_EDITOR,
            arguments = listOf(
                navArgument("noteId") {
                    type = NavType.LongType
                    defaultValue = 0L
                }
            )
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getLong("noteId") ?: 0L
            val editorViewModel: NoteEditorViewModel = viewModel(
                factory = NoteEditorViewModel.provideFactory(
                    noteId = noteId,
                    getNoteByIdUseCase = appContainer.getNoteByIdUseCase,
                    saveNoteUseCase = appContainer.saveNoteUseCase,
                    deleteNoteUseCase = appContainer.deleteNoteUseCase
                )
            )

            NoteEditorScreen(
                viewModel = editorViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(AuthNavDestination.SETUP_ROUTE) {
            val setupViewModel: VaultSetupViewModel = viewModel(
                factory = VaultSetupViewModel.provideFactory(
                    setupMasterPinUseCase = appContainer.setupMasterPinUseCase,
                    vaultSessionManager = appContainer.vaultSessionManager
                )
            )

            VaultSetupScreen(
                viewModel = setupViewModel,
                onSetupComplete = {
                    navController.navigate(VaultNavDestination.ROUTE) {
                        popUpTo(NavRoutes.NOTES_HOME) { inclusive = false }
                    }
                },
                onDismiss = {
                    navController.popBackStack()
                }
            )
        }

        composable(AuthNavDestination.STEALTH_AUTH_ROUTE) {
            val authViewModel: StealthAuthViewModel = viewModel(
                factory = StealthAuthViewModel.provideFactory(
                    authenticatePinUseCase = appContainer.authenticatePinUseCase,
                    authRepository = appContainer.authRepository,
                    vaultSessionManager = appContainer.vaultSessionManager
                )
            )

            StealthAuthScreen(
                viewModel = authViewModel,
                onAuthenticated = { isDecoy ->
                    val destination = if (isDecoy) {
                        DecoyNavDestination.DECOY_DASHBOARD_ROUTE
                    } else {
                        VaultNavDestination.ROUTE
                    }
                    navController.navigate(destination) {
                        popUpTo(NavRoutes.NOTES_HOME) { inclusive = false }
                    }
                },
                onDismiss = {
                    navController.popBackStack()
                }
            )
        }

        composable(VaultNavDestination.ROUTE) {
            val vaultViewModel: VaultViewModel = viewModel(
                key = "primary_vault",
                factory = VaultViewModel.provideFactory(
                    isDecoy = false,
                    getVaultFilesUseCase = appContainer.getVaultFilesUseCase,
                    encryptAndStoreFileUseCase = appContainer.encryptAndStoreFileUseCase,
                    deleteVaultFileUseCase = appContainer.deleteVaultFileUseCase,
                    vaultRepository = appContainer.vaultRepository,
                    sessionManager = appContainer.vaultSessionManager
                )
            )

            VaultDashboardScreen(
                viewModel = vaultViewModel,
                decryptVaultFileUseCase = appContainer.decryptVaultFileUseCase,
                onFileClick = { file ->
                    val route = when (file.fileType) {
                        VaultFileType.PHOTO -> MediaNavDestination.imageViewerRoute(file.id)
                        VaultFileType.VIDEO -> MediaNavDestination.videoPlayerRoute(file.id)
                        VaultFileType.DOCUMENT -> MediaNavDestination.docViewerRoute(file.id)
                    }
                    navController.navigate(route)
                },
                onBackupClick = {
                    navController.navigate(BackupNavDestination.BACKUP_RESTORE_ROUTE)
                },
                onLockClick = {
                    appContainer.vaultSessionManager.lock()
                    navController.navigate(NavRoutes.NOTES_HOME) {
                        popUpTo(NavRoutes.NOTES_HOME) { inclusive = true }
                    }
                }
            )
        }

        composable(DecoyNavDestination.DECOY_DASHBOARD_ROUTE) {
            val decoyViewModel: VaultViewModel = viewModel(
                key = "decoy_vault",
                factory = VaultViewModel.provideFactory(
                    isDecoy = true,
                    getVaultFilesUseCase = appContainer.getVaultFilesUseCase,
                    encryptAndStoreFileUseCase = appContainer.encryptAndStoreFileUseCase,
                    deleteVaultFileUseCase = appContainer.deleteVaultFileUseCase,
                    vaultRepository = appContainer.vaultRepository,
                    sessionManager = appContainer.vaultSessionManager
                )
            )

            DecoyDashboardScreen(
                viewModel = decoyViewModel,
                decryptVaultFileUseCase = appContainer.decryptVaultFileUseCase,
                onFileClick = { file ->
                    val route = when (file.fileType) {
                        VaultFileType.PHOTO -> MediaNavDestination.imageViewerRoute(file.id)
                        VaultFileType.VIDEO -> MediaNavDestination.videoPlayerRoute(file.id)
                        VaultFileType.DOCUMENT -> MediaNavDestination.docViewerRoute(file.id)
                    }
                    navController.navigate(route)
                },
                onBackupClick = {
                    navController.navigate(BackupNavDestination.BACKUP_RESTORE_ROUTE)
                },
                onLockClick = {
                    appContainer.vaultSessionManager.lock()
                    navController.navigate(NavRoutes.NOTES_HOME) {
                        popUpTo(NavRoutes.NOTES_HOME) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = MediaNavDestination.MEDIA_VIEWER_ROUTE,
            arguments = listOf(navArgument("fileId") { type = NavType.LongType })
        ) { backStackEntry ->
            val fileId = backStackEntry.arguments?.getLong("fileId") ?: 0L
            val isDecoy = appContainer.vaultSessionManager.isDecoySession
            val fileState by appContainer.vaultRepository.getFileById(fileId, isDecoy).collectAsStateWithLifecycle(null)

            fileState?.let { file ->
                SecureImageViewerScreen(
                    file = file,
                    decryptVaultFileUseCase = appContainer.decryptVaultFileUseCase,
                    onNavigateBack = { navController.popBackStack() },
                    onDeleteClick = {
                        coroutineScope.launch {
                            appContainer.vaultRepository.deleteFile(file)
                            navController.popBackStack()
                        }
                    },
                    onToggleFavorite = {
                        coroutineScope.launch {
                            appContainer.vaultRepository.toggleFavorite(file.id, !file.isFavorite)
                        }
                    }
                )
            }
        }

        composable(
            route = MediaNavDestination.VIDEO_PLAYER_ROUTE,
            arguments = listOf(navArgument("fileId") { type = NavType.LongType })
        ) { backStackEntry ->
            val fileId = backStackEntry.arguments?.getLong("fileId") ?: 0L
            val isDecoy = appContainer.vaultSessionManager.isDecoySession
            val fileState by appContainer.vaultRepository.getFileById(fileId, isDecoy).collectAsStateWithLifecycle(null)

            fileState?.let { file ->
                SecureVideoPlayerScreen(
                    file = file,
                    decryptVaultFileUseCase = appContainer.decryptVaultFileUseCase,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        composable(
            route = MediaNavDestination.DOC_VIEWER_ROUTE,
            arguments = listOf(navArgument("fileId") { type = NavType.LongType })
        ) { backStackEntry ->
            val fileId = backStackEntry.arguments?.getLong("fileId") ?: 0L
            val isDecoy = appContainer.vaultSessionManager.isDecoySession
            val fileState by appContainer.vaultRepository.getFileById(fileId, isDecoy).collectAsStateWithLifecycle(null)

            fileState?.let { file ->
                SecureDocumentViewerScreen(
                    file = file,
                    decryptVaultFileUseCase = appContainer.decryptVaultFileUseCase,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        composable(BackupNavDestination.BACKUP_RESTORE_ROUTE) {
            val backupViewModel: BackupViewModel = viewModel(
                factory = BackupViewModel.provideFactory(
                    backupManager = appContainer.backupManager,
                    isDecoy = appContainer.vaultSessionManager.isDecoySession
                )
            )

            BackupScreen(
                viewModel = backupViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
