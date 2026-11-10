# MVP Roadmap & Execution Plan — Vault

## 1. Overview & Strategy

The development of **Vault** is structured into 8 distinct engineering phases following strict Clean Architecture and security-by-design principles. Each phase represents a verifiable, testable deliverable milestone. Progress through phases is sequential: no phase begins until the preceding phase is tested, reviewed, and verified.

---

## 2. Phase Breakdown & Milestones

| Phase | Milestone Name | Scope & Deliverables | Estimated Effort | Gate Criteria |
| :--- | :--- | :--- | :--- | :--- |
| **Phase 1** | **Project Foundation** | Gradle multi-module/package setup, Material 3 theme, Hilt DI, base navigation graph, core utils. | 2-3 Days | App compiles, launches clean scaffold, DI graph passes unit tests. |
| **Phase 2** | **Notes Application** | Full CRUD Notes, Search with debounce, Category filtering, Color palettes, Room DB (`notes.db`). | 3-4 Days | Notes app is 100% operational as a standalone utility with automated unit/UI tests. |
| **Phase 3** | **Hidden Access & Auth** | Secret title gesture, stealth PIN keypad, PBKDF2 key derivation, BiometricPrompt, Decoy PIN routing, auto-lock lifecycle. | 3-4 Days | Stealth gesture navigates reliably; Master, Decoy, and wrong PIN paths verified. |
| **Phase 4** | **Vault Storage & Crypto** | AES-256-GCM chunked stream cipher, Android Keystore manager, atomic write protocol, `vault_primary.db`. | 4-5 Days | Encryption/decryption benchmarked (>40 MB/s); zero data corruption on crash tests. |
| **Phase 5** | **Media Management** | Photo Picker & SAF document import, safe MediaStore delete requests, albums, in-memory Coil image viewer, ExoPlayer streaming. | 5-6 Days | Import, display, and video streaming functional without any disk plaintext writes. |
| **Phase 6** | **Privacy Hardening** | `FLAG_SECURE` lifecycle enforcement, Recents preview scrubbing, secure clipboard, Android Auto-Backup exclusion. | 2-3 Days | Zero screenshot capability in Vault; zero backup leakages confirmed. |
| **Phase 7** | **Encrypted Backup** | Password-based encrypted archive creation (`.vlt`), checksums, atomic restore engine, decryption verification. | 3-4 Days | Full export and restore verified across clean reinstall. |
| **Phase 8** | **Finalization & QA** | End-to-end integration tests, memory leak checks (LeakCanary), ProGuard/R8 obfuscation, release signing. | 3-4 Days | All functional and security tests pass with 0 warnings. |

---

## 3. Detailed Phase Scope & Technical Deliverables

### Phase 1: Project Foundation & Architecture
- **Activities:**
  - Initialize project with Gradle Kotlin DSL (`build.gradle.kts`) targeting Android 15 (API 35) with Min SDK 29 (Android 10).
  - Configure Jetpack Compose BOM, Material 3, Navigation Compose.
  - Setup Hilt Dependency Injection (`@HiltAndroidApp`, application module, dispatchers module).
  - Configure Material 3 Theme with Dynamic Color (Material You), explicit Light and Dark palettes.
  - Establish base directory/package architecture: `core-model`, `core-database`, `core-security`, `core-design`, `feature-notes`, `feature-vault`, `feature-auth`.

### Phase 2: Authentic Notes Application
- **Activities:**
  - Build Room database entities and DAOs: `NoteEntity`, `NoteDao`, `NotesDatabase`.
  - Implement repository and use cases: `GetNotesUseCase`, `SaveNoteUseCase`, `DeleteNoteUseCase`, `SearchNotesUseCase`.
  - Design Compose screens: `NotesHomeScreen` (staggered grid, search bar, category chips) and `NoteEditorScreen` (title, markdown body, color picker).
  - Write Robolectric and Compose unit tests for note search, filtering, and storage.

### Phase 3: Hidden Access & Stealth Authentication
- **Activities:**
  - Implement custom Compose pointer input gesture on `TopAppBar` title for 2.0s long-press detection with 25ms haptic tick.
  - Create `StealthAuthScreen`: custom numeric keypad with haptic responses and neutral styling.
  - Implement `AuthRepository` with PBKDF2WithHmacSHA256 (100,000 iterations) salt + hash generator.
  - Integrate Android `BiometricPrompt` with `BiometricManager.Authenticators.BIOMETRIC_STRONG`.
  - Implement Decoy PIN detection: route to `DecoySession` vs `PrimarySession`.
  - Implement anti-brute-force rate limiting (failed attempt lockout backoff).

### Phase 4: Vault Storage & Cryptographic Core
- **Activities:**
  - Implement `KeystoreKeyManager`: Hardware-backed AES-256 key generation inside `AndroidKeyStore`.
  - Implement `AesGcmCipherService`: AES-256-GCM chunked stream encryption with unique 12-byte IV per file.
  - Implement atomic file writer (`.tmp` write -> GCM verify -> atomic rename to `.enc`).
  - Create Room `VaultDatabase` (`vault_primary.db` and `vault_decoy.db`) with `VaultFileEntity` and `AlbumEntity`.
  - Implement test suite for cryptographic integrity, IV randomness, and recovery from abrupt termination.

### Phase 5: Media Management & Zero-Disk Viewing
- **Activities:**
  - Integrate `ActivityResultContracts.PickMultipleVisualMedia` for modern Android Photo Picker.
  - Integrate `ActivityResultContracts.OpenMultipleDocuments` for PDF and document imports.
  - Implement MediaStore original file deletion flow via `MediaStore.createDeleteRequest(contentResolver, uris)`.
  - Build Compose `VaultDashboardScreen` with tabbed media categories (Photos, Videos, Documents, Albums).
  - Implement `EncryptedImageDecoder` for Coil: decrypts directly into memory stream.
  - Build `SecureImageViewerScreen` with pinch-to-zoom and pan gestures.
  - Implement `EncryptedFileDataSource` for Media3 ExoPlayer: stream-decrypts video blocks on the fly without staging unencrypted files.
  - Build `SecureVideoPlayerScreen` with custom playback controls.

### Phase 6: Privacy Hardening & System Integration
- **Activities:**
  - Implement `SecurityLifecycleObserver`: dynamically attaches/detaches `FLAG_SECURE` based on current route.
  - Implement auto-lock on `onPause` / `onStop` / `ACTION_SCREEN_OFF`.
  - Purge session keys and invoke `System.gc()` after zeroing memory buffers upon vault exit.
  - Configure `res/xml/data_extraction_rules.xml` and `res/xml/backup_rules.xml` to strictly exclude vault files.
  - Implement secure clipboard manager with auto-expiration (30 seconds) and `EXTRA_IS_SENSITIVE`.

### Phase 7: Encrypted Backup & Restore
- **Activities:**
  - Build `BackupManager`: bundles vault files and database metadata into a compressed archive.
  - Derive backup key from user password via PBKDF2 + 32-byte salt; encrypt archive via AES-256-GCM.
  - Build restore engine: verifies header, salt, and authentication tag before extracting to internal storage.
  - Support exporting/importing via Storage Access Framework (`CreateDocument` / `OpenDocument`).

### Phase 8: Hardening, QA, & Release Packaging
- **Activities:**
  - Execute end-to-end integration and security test suites.
  - Run LeakCanary to verify zero memory leaks across viewer transitions.
  - Configure ProGuard/R8 rules: strip all `Log.*` calls, obfuscate crypto and internal classes.
  - Profile startup latency, encryption throughput, and memory consumption during 4K video playback.
  - Prepare release APK/AAB with release signing configuration.

---

## 4. Risk Matrix & Mitigations

| Risk | Impact | Probability | Mitigation Strategy |
| :--- | :--- | :--- | :--- |
| **Android 11+ MediaStore Delete Denial** | Medium | Medium | User can refuse system deletion. The encrypted file is retained in Vault, and the user is clearly informed that the original was not removed. |
| **Out-Of-Memory (OOM) on Large Video Playback** | Critical | High | Strict chunk-based streaming in `EncryptedFileDataSource`. Decrypt only 64 KB buffers as requested by ExoPlayer; never read full video into RAM. |
| **Keystore Key Loss on Lockscreen Change** | High | Low | Handle `KeyPermanentlyInvalidatedException` gracefully; prompt re-authentication via Master Passphrase. |
| **Decoy Vault Leakage** | Critical | Low | Complete structural segregation: distinct database file, distinct directory, distinct Keystore alias. Zero shared queries. |
| **Play Store Rejection for Disguise** | Medium | Low | Ensure the public Notes application is 100% genuine, useful, fully functional, and completely compliant with Google Play developer policies. |

