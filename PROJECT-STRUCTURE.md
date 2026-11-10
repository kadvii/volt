# Project Structure & Source Layout — Vault

This document outlines the directory structure, package conventions, and module boundaries for the **Vault** Android codebase.

---

## 1. Top-Level Directory Layout

```
vault/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── gradle.properties
├── app/                              # Application bootstrap module
│   ├── build.gradle.kts
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── java/com/vault/
│           │   ├── VaultApplication.kt
│           │   ├── MainActivity.kt
│           │   └── navigation/
│           │       ├── RootNavGraph.kt
│           │       └── NavRoutes.kt
│           └── res/
│               ├── drawable/
│               ├── mipmap/
│               ├── values/
│               │   ├── strings.xml
│               │   └── themes.xml
│               └── xml/
│                   ├── backup_rules.xml
│                   └── data_extraction_rules.xml
│
├── core/
│   ├── core-model/                   # Pure domain models & contracts
│   │   ├── build.gradle.kts
│   │   └── src/main/java/com/vault/core/model/
│   │       ├── Note.kt
│   │       ├── VaultFile.kt
│   │       ├── Album.kt
│   │       ├── SessionState.kt
│   │       └── AppResult.kt
│   │
│   ├── core-database/                # Room entities, DAOs & databases
│   │   ├── build.gradle.kts
│   │   └── src/main/java/com/vault/core/database/
│   │       ├── entity/
│   │       │   ├── NoteEntity.kt
│   │       │   ├── VaultFileEntity.kt
│   │       │   └── AlbumEntity.kt
│   │       ├── dao/
│   │       │   ├── NoteDao.kt
│   │       │   ├── VaultFileDao.kt
│   │       │   └── AlbumDao.kt
│   │       ├── NotesDatabase.kt
│   │       ├── VaultDatabase.kt
│   │       └── di/
│   │           └── DatabaseModule.kt
│   │
│   ├── core-security/                # Cryptography, Keystore & Key derivation
│   │   ├── build.gradle.kts
│   │   └── src/main/java/com/vault/core/security/
│   │       ├── crypto/
│   │       │   ├── AesGcmCipherService.kt
│   │       │   ├── KeystoreManager.kt
│   │       │   └── KeyDerivationService.kt
│   │       ├── session/
│   │       │   ├── VaultSessionManager.kt
│   │       │   └── SecurityLifecycleObserver.kt
│   │       ├── util/
│   │       │   ├── MemoryScrubber.kt
│   │       │   └── SecureRandomGenerator.kt
│   │       └── di/
│   │           └── SecurityModule.kt
│   │
│   ├── core-media/                   # Streaming decoders & ExoPlayer sources
│   │   ├── build.gradle.kts
│   │   └── src/main/java/com/vault/core/media/
│   │       ├── exoplayer/
│   │       │   └── EncryptedFileDataSource.kt
│   │       ├── coil/
│   │       │   ├── EncryptedThumbnailFetcher.kt
│   │       │   └── InMemoryBitmapDecoder.kt
│   │       └── importer/
│   │           ├── MediaImportPipeline.kt
│   │           └── MediaStoreDeletionHelper.kt
│   │
│   └── core-design/                  # Theme, colors, typography & shared UI
│       ├── build.gradle.kts
│       └── src/main/java/com/vault/core/design/
│           ├── theme/
│           │   ├── Color.kt
│           │   ├── Theme.kt
│           │   ├── Type.kt
│           │   └── Shape.kt
│           └── component/
│               ├── SecretTriggerTopAppBar.kt
│               ├── StealthKeypad.kt
│               ├── PinIndicatorDots.kt
│               ├── NoteCard.kt
│               └── MediaThumbnailCard.kt
│
└── feature/
    ├── feature-notes/                # Authentic Notes app UI & ViewModels
    │   ├── build.gradle.kts
    │   └── src/main/java/com/vault/feature/notes/
    │       ├── presentation/
    │       │   ├── NotesHomeScreen.kt
    │       │   ├── NoteEditorScreen.kt
    │       │   ├── NotesSearchScreen.kt
    │       │   └── NotesViewModel.kt
    │       ├── domain/
    │       │   ├── GetNotesUseCase.kt
    │       │   ├── SaveNoteUseCase.kt
    │       │   └── DeleteNoteUseCase.kt
    │       └── data/
    │           └── NotesRepositoryImpl.kt
    │
    ├── feature-auth/                 # Stealth PIN pad & Biometrics
    │   ├── build.gradle.kts
    │   └── src/main/java/com/vault/feature/auth/
    │       ├── presentation/
    │       │   ├── StealthAuthScreen.kt
    │       │   ├── VaultSetupScreen.kt
    │       │   └── AuthViewModel.kt
    │       ├── domain/
    │       │   ├── AuthenticatePinUseCase.kt
    │       │   └── SetupMasterPinUseCase.kt
    │       └── data/
    │           └── AuthRepositoryImpl.kt
    │
    ├── feature-vault/                # Vault dashboard, viewers & album manager
    │   ├── build.gradle.kts
    │   └── src/main/java/com/vault/feature/vault/
    │       ├── presentation/
    │       │   ├── VaultDashboardScreen.kt
    │       │   ├── AlbumDetailScreen.kt
    │       │   ├── SecureImageViewerScreen.kt
    │       │   ├── SecureVideoPlayerScreen.kt
    │       │   ├── SecureDocViewerScreen.kt
    │       │   └── VaultViewModel.kt
    │       ├── domain/
    │       │   ├── ImportMediaUseCase.kt
    │       │   ├── GetVaultFilesUseCase.kt
    │       │   └── DeleteVaultFileUseCase.kt
    │       └── data/
    │           └── VaultRepositoryImpl.kt
    │
    ├── feature-settings/             # Notes settings & Vault security settings
    │   ├── build.gradle.kts
    │   └── src/main/java/com/vault/feature/settings/
    │       ├── NotesSettingsScreen.kt
    │       ├── VaultSecuritySettingsScreen.kt
    │       └── SettingsViewModel.kt
    │
    └── feature-backup/               # Encrypted export & restore
        ├── build.gradle.kts
        └── src/main/java/com/vault/feature/backup/
            ├── BackupRestoreScreen.kt
            ├── BackupManager.kt
            └── BackupViewModel.kt
```

---

## 2. Package Naming Conventions
- Root Package: `com.vault`
- Architecture Layer Separation:
  - `com.vault.core.<subsystem>`
  - `com.vault.feature.<feature-name>.presentation`
  - `com.vault.feature.<feature-name>.domain`
  - `com.vault.feature.<feature-name>.data`

---

## 3. Dependency Rules Across Boundaries
1. **Domain Isolation:** `domain` packages must never import Android UI classes (`androidx.compose.*`, `android.view.*`) or database drivers (`androidx.room.*`).
2. **Feature Isolation:** Feature modules (`feature-notes`, `feature-vault`, `feature-auth`) must not depend on each other directly; navigation is routed via the `:app` root graph using contract routes.
3. **Security Encapsulation:** Only `:core-security` and `:core-media` hold direct cryptographic algorithm references; feature modules interact through high-level use cases.

