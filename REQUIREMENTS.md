# Software Requirements Specification (SRS) — Vault

## 1. Introduction

### 1.1 Purpose
This document provides the definitive technical and functional requirements for the **Vault** Android application. It specifies behaviors, constraints, protocol formats, hardware requirements, and API interactions for the engineering, security, and QA teams.

### 1.2 System Scope
Vault operates as a dual-personality Android application:
1. **Public Persona:** A production-grade, fast, and feature-rich Notes utility.
2. **Hidden Persona:** An AES-256-GCM encrypted media vault with zero plaintext storage, plausible deniability decoy mode, and Android Keystore protection.

---

## 2. Functional Requirements

### FR-01: Public Notes Management
- **FR-01.1:** The application shall launch directly into the Notes Home Screen on cold start and warm start by default.
- **FR-01.2:** The user shall be able to create, read, update, and delete text notes containing:
  - Title (String, up to 200 characters)
  - Content (String, multi-line Markdown/plain text)
  - Category / Tag (e.g., Personal, Work, Ideas, or Custom)
  - Color Label (preset pastel color palette)
  - Pinned Status (Boolean: pinned notes sorted to top)
  - Timestamps (Created Date, Modified Date)
- **FR-01.3:** The user shall be able to filter notes by category and execute full-text search across titles and body contents with debounce (300ms).
- **FR-01.4:** The Notes database shall be stored in standard Room SQLite storage (`notes.db`), ensuring immediate access without triggering security prompts.

### FR-02: Secret Access Mechanism
- **FR-02.1 (Secret Gesture):** The entrance to the Vault shall be triggered by a specific, unadvertised gesture on the TopAppBar of the Notes screen:
  - Default: Long-press (duration: 2000ms ± 200ms) on the title text ("Notes").
  - Optional Alternative: Discrete double-tap with hold.
- **FR-02.2 (Feedback):** When the gesture threshold is reached, the device shall deliver a subtle, short haptic tick (`HapticFeedbackType.LongPress` or `VibrationEffect.createOneShot(25, 50)`) before navigating to the stealth PIN screen.
- **FR-02.3 (Zero Exposure):** The Notes UI shall display no lock icons, security badges, or visible hints suggesting the existence of the secret gesture.

### FR-03: Stealth Authentication Layer
- **FR-03.1 (Stealth Screen):** The authentication screen shall appear as a minimalist numeric pad titled neutrally (e.g., "Verification" or "Quick Code") without mentioning "Vault", "Encryption", or "Password".
- **FR-03.2 (Master PIN):**
  - Minimum length: 4 digits. Maximum length: 8 digits.
  - Verification: PIN is never stored in plaintext. Verification uses PBKDF2WithHmacSHA256 (100,000 iterations) with a 32-byte cryptographically secure random salt, producing a 256-bit hash compared in constant time (`MessageDigest.isEqual`).
- **FR-03.3 (Decoy PIN):**
  - The user can configure an optional Decoy PIN distinct from the Master PIN.
  - When the Decoy PIN is entered, the application authenticates into the Decoy Vault environment.
- **FR-03.4 (Disguised Invalid Attempt Handling):**
  - An invalid PIN shall not produce an error dialog stating "Incorrect Vault PIN".
  - The UI shall perform a subtle error shake animation and clear the digits, or show a neutral toast: "Note identifier not found".
- **FR-03.5 (Rate Limiting & Anti-Brute-Force):**
  - After 5 consecutive failed PIN attempts, enforce a 30-second lockout.
  - After 10 consecutive failed PIN attempts, enforce a 5-minute lockout with exponential backoff.
  - Lockout timestamps must be stored securely and persist across process kills.
- **FR-03.6 (Biometric Unlock):**
  - The user may enable Biometric Unlock (Fingerprint / Biometric Class 3) after establishing a Master PIN.
  - BiometricPrompt shall only unlock the Primary Vault; Decoy Vault access requires entering the Decoy PIN on the keypad.

### FR-04: Cryptographic Key Management
- **FR-04.1 (Android Keystore):** All root cryptographic keys shall be generated inside and managed by the Android Keystore system provider (`AndroidKeyStore`).
- **FR-04.2 (Hardware Backing):** Keys shall require hardware backing (TEE — Trusted Execution Environment or StrongBox) where available on the hardware (`KeyInfo.isInsideSecureHardware()`).
- **FR-04.3 (Key Separation):**
  - `MasterVaultKey`: Encrypts/decrypts the primary vault database and data encryption keys (DEK).
  - `DecoyVaultKey`: Encrypts/decrypts the decoy vault database and decoy data encryption keys.
  - `BackupKey`: Derived via PBKDF2/Argon2 from user-entered backup passphrase.
  - Under no circumstances shall `MasterVaultKey` and `DecoyVaultKey` share aliases, material, or initialization vectors.

### FR-05: Vault Storage & File Encryption
- **FR-05.1 (Cipher Algorithm):** All stored files shall be encrypted using AES-256 in Galois/Counter Mode (AES/GCM/NoPadding).
- **FR-05.2 (Nonce/IV Generation):** A unique, cryptographically secure 12-byte (96-bit) Initialization Vector (IV) must be generated for every encryption operation using `SecureRandom`. Reusing an IV with the same key is strictly prohibited.
- **FR-05.3 (Chunked Streaming):**
  - Large files (videos, high-res photos, large documents) shall be encrypted and decrypted using chunked streaming (chunk size: 64 KB).
  - Each chunk or the overall file stream shall include authentication tags to prevent truncated file replay attacks.
- **FR-05.4 (Storage Sandbox):**
  - Encrypted files shall be stored in the app's private internal storage directory:
    - Primary: `context.filesDir/vault_primary/media/`
    - Decoy: `context.filesDir/vault_decoy/media/`
  - Encrypted files shall be named with UUIDs (`<uuid>.enc`) and shall contain no plaintext file headers, original names, or EXIF metadata in the raw file system.
- **FR-05.5 (Atomic Commit):**
  - File encryption must write to a temporary file (`<uuid>.tmp`).
  - Only after successful completion and verification of the GCM authentication tag shall the file be atomically renamed to `<uuid>.enc` and its metadata inserted into Room.

### FR-06: Media Import Pipeline & Android Permissions
- **FR-06.1 (Zero Dangerous Permissions):** The application shall use the modern Android Photo Picker (`ActivityResultContracts.PickMultipleVisualMedia`) to import photos and videos, eliminating the need for `READ_EXTERNAL_STORAGE` or `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO`.
- **FR-06.2 (Document Import):** Document import shall use the Storage Access Framework (`ActivityResultContracts.OpenMultipleDocuments`) supporting mime types `*/*`.
- **FR-06.3 (Import Process Flow):**
  1. Open ContentResolver InputStream for the selected URI.
  2. Stream-encrypt content into `<uuid>.tmp` in private storage.
  3. Generate encrypted thumbnail (if image/video) and store encrypted thumbnail payload.
  4. Record file metadata (Original Name, MimeType, Size, DateAdded, AlbumId) in Vault Room DB.
  5. Atomically finalize file.
- **FR-06.4 (Original File Deletion Compliance):**
  - Never attempt silent background deletion of media (prohibited by Android OS).
  - On Android 11+ (API 30+), trigger `MediaStore.createDeleteRequest(contentResolver, listOf(mediaUri))` or `createTrashRequest`.
  - On Android 10 (API 29), trigger `RecoverableSecurityException` handling or prompt the user with system SAF delete confirmation.
  - If the user declines deletion, the encrypted file remains in the vault, and a non-critical notification informs the user that the original was preserved in gallery.

### FR-07: In-App Media Viewing & Zero-Disk Plaintext Policy
- **FR-07.1 (Zero Disk Caching):** At no point shall decrypted media (photos, videos, documents) be written to the physical storage disk (internal cache, external cache, or temp folders).
- **FR-07.2 (Image Rendering):**
  - High-res images shall be decrypted on-the-fly into an in-memory byte stream / `Bitmap` and fed directly to Coil's `ImageLoader`.
  - The decoded bitmap shall be held only in ephemeral memory and discarded when navigating away.
- **FR-07.3 (Video Playback Streaming):**
  - Video playback shall be implemented with Jetpack Media3 (ExoPlayer).
  - A custom `EncryptedFileDataSource : DataSource` shall stream decrypted byte segments on demand directly to ExoPlayer's decoder pipeline.
  - Seeking shall compute chunk/block offsets accurately and re-initialize GCM counter states without decrypting preceding blocks.
- **FR-07.4 (Document Viewer):**
  - Text documents shall be decrypted into memory strings.
  - PDFs shall be rendered via in-memory `PdfRenderer` using memory file descriptors (`MemoryFile` / `SharedMemory`).

### FR-08: Media Organization & Album Management
- **FR-08.1:** The user shall be able to create, rename, and delete custom Albums.
- **FR-08.2:** Deleting an album shall not delete the files inside it; files shall revert to "Uncategorized" unless explicitly chosen by user.
- **FR-08.3:** The user shall be able to move files between albums in batch or individually.
- **FR-08.4:** Media files can be sorted by Date Added, File Name, or File Size (Ascending / Descending).

### FR-09: Decoy Vault Isolation
- **FR-09.1:** The Decoy Vault must possess a fully separate SQLite database file (`vault_decoy.db`) and storage directory (`vault_decoy/`).
- **FR-09.2:** The Decoy Vault must NEVER query, inspect, count, or touch the primary vault database or files.
- **FR-09.3:** If Decoy mode is active, Settings screen must show Decoy storage statistics only (e.g. "Storage used: 12 MB") and must NOT show options to reset Master PIN.

### FR-10: Privacy & Anti-Surveillance Controls
- **FR-10.1 (FLAG_SECURE):**
  - `Window.setFlags(FLAG_SECURE, FLAG_SECURE)` shall be activated dynamically whenever the user enters the Secret PIN screen or any Vault screen.
  - This prevents OS screenshots, screen recording, and exposure in the Android Recent Apps / Multitasking preview thumbnail.
  - When returning to the normal Notes screen, `FLAG_SECURE` is released if user allows notes screenshots, or kept according to settings.
- **FR-10.2 (Auto-Lock on Lifecycle Events):**
  - In `Activity.onStop()` or `onPause()` after a configurable grace period (Default: 0 seconds / immediate), the active Vault session and in-memory keys must be destroyed.
  - Re-opening the app or returning from another app must return to the public Notes screen.
- **FR-10.3 (Secure Clipboard):**
  - Any text copied from Vault documents or notes must be marked with `ClipDescription.EXTRA_IS_SENSITIVE = true` (Android 13+) and cleared from clipboard after 30 seconds.
- **FR-10.4 (Backup Exclusion):**
  - Android Auto Backup and Device-to-Device transfer must explicitly exclude the Vault storage directories (`vault_primary`, `vault_decoy`) and database files via `dataExtractionRules.xml` and `backup_rules.xml`.

### FR-11: Encrypted Backup & Restore
- **FR-11.1:** The user can initiate a manual backup export to a local file (`.vlt`).
- **FR-11.2:** The backup must require a user-supplied Passphrase (min 8 chars) that derives an encryption key via PBKDF2/Argon2.
- **FR-11.3:** The `.vlt` file is a compressed tar/zip stream where every entry or the outer archive is encrypted with AES-256-GCM and verified with HMAC-SHA256 / GCM Tag.
- **FR-11.4:** Restoration validates the passphrase and archive integrity before writing any files into internal storage.

---

## 3. Non-Functional Requirements (NFR)

### NFR-01: Security & Cryptography
- No plaintext storage of keys, passwords, PINs, or media anywhere on the device.
- Cryptographic keys stored in Android Keystore with `KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT`.
- Zeroization: Memory buffers containing decrypted data or raw PIN digits must be explicitly wiped (`Arrays.fill(data, 0.toByte())`) immediately after processing.

### NFR-02: Performance & Resource Utilization
- Photo viewer thumbnail loading latency < 50ms from memory cache, < 150ms from disk decryption.
- Video playback initiation latency < 350ms for local 1080p/4K MP4 files.
- Memory consumption must never scale with file size (chunked streaming required).

### NFR-03: Reliability & Data Integrity
- Atomic filesystem and database operations: An unexpected battery death or OS process kill must leave the system in a consistent, non-corrupt state.
- Unfinished temporary files (`.tmp`) cleaned on startup.

### NFR-04: Compatibility
- Minimum OS: Android 10.0 (API level 29).
- Target OS: Android 15.0 (API level 35).
- Architectures: `arm64-v8a`, `armeabi-v7a`, `x86_64`.
- Screen sizes: Phone and Foldable layouts (portrait and landscape orientations).

