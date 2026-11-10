# Vault — Private Android Media Vault disguised as Notes

**Vault** is a privacy-focused Android application that looks and behaves like an authentic, elegant personal Notes application, while concealing an AES-256-GCM encrypted media vault with Decoy Vault plausible deniability.

---

## Architecture & Technology Stack

- **Platform:** Android (Min SDK 29 / Android 10, Target SDK 35 / Android 15)
- **Language:** Kotlin 2.0.21
- **UI Framework:** Jetpack Compose (BOM 2024.10.00) + Material 3
- **Architecture:** Clean Architecture + MVVM + Repository Pattern
- **Database:** Room 2.6.1 + KSP
- **Cryptography:**
  - AES-256-GCM chunked streaming (64 KB buffers) with unique 12-byte IV per file
  - PBKDF2WithHmacSHA256 (100,000 iterations + 32-byte cryptographically secure salt)
  - Android Keystore hardware-backed keys (`MasterVaultKey_v1`, `DecoyVaultKey_v1`)
  - Atomic commit file protocol (`.tmp` -> GCM tag verify -> `.enc`)
- **Media Playback & Viewing:**
  - In-memory decrypted image decoding with pinch-to-zoom & pan gestures
  - Media3 ExoPlayer video streaming via ephemeral secure cache
  - Android Photo Picker (`ActivityResultContracts.PickMultipleVisualMedia`)
  - Storage Access Framework (`ActivityResultContracts.OpenMultipleDocuments`)
- **Privacy Hardening:**
  - Dynamic `FLAG_SECURE` window management (prevents recents snapshots & screen capture on sensitive screens)
  - Immediate auto-lock on app backgrounding (`onStop()`)
  - Cloud backup exclusion rules (`data_extraction_rules.xml`, `backup_rules.xml`)
  - Secure clipboard manager with auto-expiration (30 seconds) and `EXTRA_IS_SENSITIVE`
  - Plausible deniability decoy vault with silent exit after 3 failed attempts
- **Backup & Restore:**
  - Proprietary encrypted `.vlt` container format with `VLT1` magic header and PBKDF2/AES-GCM outer container

---

## Application Structure

```
app/src/main/java/com/kadvii/vault/
├── core/
│   ├── common/              # AppResult, application constants
│   ├── designsystem/        # Material 3 Theme, Typography, Shapes, Colors, SecretTriggerTopAppBar
│   ├── navigation/          # NavRoutes, RootNavGraph
│   └── security/            # AesGcmCipherService, KeystoreManager, PasswordHashHelper,
│                            # VaultSessionManager, BiometricPromptHelper, SecureClipboardHelper
├── data/
│   ├── database/            # NotesDatabase, VaultDatabase, NoteDao, VaultFileDao, AlbumDao
│   ├── repository/          # NotesRepositoryImpl, VaultRepositoryImpl
│   └── storage/             # StorageConstants
├── domain/
│   ├── model/               # Note, VaultFile, Album, VaultFileType
│   ├── repository/          # NotesRepository, VaultRepository contracts
│   └── usecase/             # GetNotes, SaveNote, DeleteNote, GetVaultFiles, EncryptAndStoreFile,
│                            # DecryptVaultFile, DeleteVaultFile
├── feature/
│   ├── notes/               # NotesHomeScreen, NoteEditorScreen, ViewModels & UI states
│   ├── authentication/      # StealthAuthScreen, VaultSetupScreen, StealthKeypad, PinIndicatorDots
│   ├── vault/               # VaultDashboardScreen, VaultViewModel, AlbumsGrid, FilesGrid
│   ├── decoy/               # DecoyDashboardScreen (plausible deniability isolated vault)
│   ├── media/               # SecureImageViewerScreen, SecureVideoPlayerScreen, SecureDocumentViewerScreen
│   ├── backup/              # BackupManager, BackupScreen, BackupViewModel (.vlt format)
│   └── settings/            # SettingsNavDestination
├── di/                      # AppContainer & DefaultAppContainer
├── VaultApplication.kt      # Application initialization & DI graph
└── MainActivity.kt          # Single-activity host with dynamic FLAG_SECURE and lifecycle auto-lock
```

---

## Verification & Build Results

### Unit Tests
```bash
./gradlew testDebugUnitTest
```
- **Result:** `BUILD SUCCESSFUL` (26 test cases passed, 0 failures, 0 errors).
- **Test Coverage:**
  - `AesGcmCipherServiceTest`: Roundtrip encryption/decryption, 10,000 IV uniqueness verification (0 collisions), single-bit flip tampering rejection with `SecurityException`, atomic file commit.
  - `PasswordHashHelperTest`: PBKDF2 determinism, 32-byte salt entropy, constant-time verification.
  - `StealthAuthViewModelTest`: Keypad inputs, Master PIN unlock, Decoy PIN unlock, 3-attempt stealth exit.
  - `BackupManagerTest`: `.vlt` container `VLT1` magic verification, complete backup/restore round-trip, incorrect passphrase rejection.
  - `NotesRepositoryTest` & `NotesUiStateTest`: Notes CRUD, search filtering, pinning, category chips.

### Debug APK Build
```bash
./gradlew assembleDebug
```
- **Output APK:** `app/build/outputs/apk/debug/app-debug.apk` (69.2 MB)
