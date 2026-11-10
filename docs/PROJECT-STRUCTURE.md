# Project Structure Documentation — Vault (Phase 01)

## Directory Structure

```text
app/src/main/java/com/kadvii/vault/
├── core/
│   ├── common/
│   │   ├── AppResult.kt            # Functional Result wrapper (Success, Error, Loading)
│   │   └── Constants.kt            # Global application constants
│   ├── designsystem/
│   │   ├── Color.kt                # Light, Dark, and pastel color palette tokens
│   │   ├── Shape.kt                # Material 3 shape definitions
│   │   ├── Theme.kt                # VaultTheme composable with Dynamic Color
│   │   └── Type.kt                 # Material 3 typography scale
│   ├── navigation/
│   │   ├── NavRoutes.kt            # Route string constants
│   │   └── RootNavGraph.kt         # NavHost connecting screens
│   └── security/
│       └── SecurityConstants.kt    # Cryptographic algorithm & Keystore constants
│
├── data/
│   ├── database/
│   │   └── DatabaseConstants.kt    # Table and database metadata
│   ├── repository/
│   │   └── NotesRepositoryImpl.kt  # StateFlow backed Notes repository
│   └── storage/
│       └── StorageConstants.kt     # Scoped internal storage paths
│
├── domain/
│   ├── model/
│   │   └── Note.kt                 # Pure Kotlin Note data class
│   ├── repository/
│   │   └── NotesRepository.kt      # Domain repository contract
│   └── usecase/
│       └── GetNotesUseCase.kt      # Use case for querying notes
│
├── feature/
│   ├── notes/
│   │   ├── NotesHomeScreen.kt      # Jetpack Compose Notes Home Screen
│   │   ├── NotesUiState.kt         # Immutable UI state with search filtering
│   │   └── NotesViewModel.kt       # MVVM ViewModel with StateFlow
│   ├── vault/
│   │   └── VaultNavDestination.kt  # Route destination contract
│   ├── authentication/
│   │   └── AuthNavDestination.kt   # Route destination contract
│   ├── media/
│   │   └── MediaNavDestination.kt  # Route destination contract
│   ├── decoy/
│   │   └── DecoyNavDestination.kt  # Route destination contract
│   ├── backup/
│   │   └── BackupNavDestination.kt # Route destination contract
│   └── settings/
│       └── SettingsNavDestination.kt# Route destination contract
│
├── di/
│   └── AppContainer.kt             # Dependency Injection container
│
├── VaultApplication.kt             # Custom Application class
└── MainActivity.kt                 # Single ComponentActivity host
```

## Layer Separation Rules
1. **Domain Layer:** Pure Kotlin, independent of Android framework and UI libraries.
2. **Data Layer:** Implements repository contracts, handles persistence and storage.
3. **Presentation Layer (feature/):** Composables, ViewModels, and UI state models.
4. **Core Layer:** Cross-cutting concerns (design system, navigation, common utilities).

