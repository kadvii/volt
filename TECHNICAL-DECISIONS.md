# Technical Decisions & Architectural Rationale — Vault

## 1. Overview
This document records technical architecture decisions, cryptographic algorithms, platform API choices, and performance trade-offs made for the **Vault** Android project.

---

## 2. Technical Decision Log

### ADR-01: Cryptographic Algorithm: AES-256-GCM
- **Context:** The application requires symmetric encryption for photos, 4K videos, and documents with confidentiality and integrity verification.
- **Alternatives Considered:**
  - *AES-256-CBC with HMAC-SHA256:* Traditional, but susceptible to padding oracle attacks if improperly implemented, requires two passes (encrypt then MAC), and is slower.
  - *ChaCha20-Poly1305:* Fast in software on older 32-bit CPUs, but lacks hardware acceleration on mid-to-high tier 64-bit ARM chips.
  - *AES-256-GCM (Galois/Counter Mode):* Authenticated encryption with associated data (AEAD). Supported directly by ARMv8 Cryptographic Extensions in hardware.
- **Decision:** **AES-256-GCM**.
- **Rationale:**
  - Modern Android devices (API 29+) universally feature 64-bit ARMv8 processors with dedicated AES instructions (`PMULL`, `AESE`), yielding encryption throughput exceeding 120 MB/s.
  - GCM natively integrates authentication tags (128-bit), immediately detecting any file truncation, bit-flipping, or disk tampering without separate MAC logic.
  - Enforced Requirement: A unique, cryptographically random 12-byte IV generated via `SecureRandom` for every single file encryption operation. Reusing an IV with GCM is catastrophic and strictly prohibited.

---

### ADR-02: Key Anchoring: Android Keystore with Hardware Backing (TEE / StrongBox)
- **Context:** Encryption keys must be generated and stored securely against forensic extraction.
- **Alternatives Considered:**
  - *User PIN as direct AES Key:* If user PIN is short (4–6 digits), an offline brute-force attack takes seconds.
  - *Hardcoded or Obfuscated Application Key:* Trivial to extract via decompilation (JADX/Ghidra).
  - *Android Keystore System:* Cryptographic master keys are generated inside the Trusted Execution Environment (TEE) or dedicated hardware security module (StrongBox).
- **Decision:** **Android Keystore (Hardware Backed)**.
- **Rationale:**
  - Keystore keys are non-exportable: key material never leaves the secure hardware boundary. Cryptographic operations occur inside the hardware processor.
  - Master keys are wrapped by the Android OS and can be bound to user authentication (Biometrics).
  - Multi-Key Hierarchy: Separate hardware keys are created for Primary Vault (`MasterVaultKey`) and Decoy Vault (`DecoyVaultKey`).

---

### ADR-03: Key Derivation for PIN: PBKDF2WithHmacSHA256
- **Context:** How to verify user PINs securely without storing them in plaintext or risking offline dictionary attacks.
- **Alternatives Considered:**
  - *Plain SHA-256:* Unsalted or single-round hash can be cracked against 4-digit PIN tables in milliseconds.
  - *Argon2id:* Superior memory-hard function, but requires external C/C++ native NDK libraries (JNI overhead, ABI size increase, potential native crashes).
  - *PBKDF2WithHmacSHA256 (100,000 iterations):* Built-in to Android's `javax.crypto.SecretKeyFactory` since API 1, highly optimized, zero external binary bloat.
- **Decision:** **PBKDF2WithHmacSHA256 with 32-byte SecureRandom Salt and 100,000 Iterations**.
- **Rationale:**
  - Native support ensures 100% stability across all Android devices without JNI build complexities.
  - 100,000 iterations creates an intentional ~120ms computational delay per verification attempt, effectively rendering brute-force attacks on the device or extracted hashes computationally intractable when paired with rate-limiting.

---

### ADR-04: Media Import: Modern Android Photo Picker over Legacy Storage Permissions
- **Context:** How to import user photos and videos into Vault across Android 10 to 15.
- **Alternatives Considered:**
  - *Requesting `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` / `READ_EXTERNAL_STORAGE`:* Requires runtime permission dialogues that immediately alert observers that the "Notes" app is scanning the user's gallery. Also violates Google Play permission policies for simple note apps.
  - *Android Photo Picker (`PickMultipleVisualMedia`):* Standardized system component backported to Android 10+ via Google Play System Updates.
- **Decision:** **Android Photo Picker & Storage Access Framework (SAF)**.
- **Rationale:**
  - Zero permissions requested in `AndroidManifest.xml`! The Notes app never prompts the user for storage or gallery permissions.
  - The OS Photo Picker runs in an isolated system process: the user selects specific media items, and only those item URIs are granted to the application.
  - Completely satisfies Google Play Store privacy guidelines and eliminates suspicion.

---

### ADR-05: In-Memory Video Streaming with ExoPlayer `EncryptedFileDataSource`
- **Context:** Playing encrypted 1080p and 4K videos inside the app without disk writes.
- **Alternatives Considered:**
  - *Decrypt Entire Video to Cache File:* High forensic risk. Plaintext file remains on disk during playback and can be recovered even after deletion.
  - *Local HTTP Server (NanoHTTPD):* Complex, exposes video on local TCP port (`localhost:8080`), vulnerable to other on-device apps sniffing local traffic.
  - *Custom ExoPlayer `DataSource`:* Media3 ExoPlayer reads byte ranges (`DataSpec.position`, `DataSpec.length`) directly from our custom `EncryptedFileDataSource`.
- **Decision:** **Custom ExoPlayer `EncryptedFileDataSource`**.
- **Rationale:**
  - Video chunks are decrypted strictly in memory as requested by ExoPlayer's rendering buffer.
  - Memory footprint remains constant (~30–80 MB) regardless of video file size (supports 10 GB+ videos without OOM).
  - Zero bytes of unencrypted media ever touch internal flash storage.

---

### ADR-06: Database Architecture: Three Separate SQLite Databases
- **Context:** Managing data for Public Notes, Primary Vault, and Decoy Vault.
- **Alternatives Considered:**
  - *Single SQLite Database with `is_hidden` column:* Catastrophic risk. A simple SQL injection or database file dump reveals all primary and decoy records simultaneously.
  - *Single SQLite with SQLCipher:* Cannot selectively isolate Decoy from Primary metadata without decrypting both.
  - *Physical Multi-Database Segregation:* Three distinct `.db` files in separate directories.
- **Decision:** **Three Physically Segregated Databases (`notes.db`, `vault_primary.db`, `vault_decoy.db`)**.
- **Rationale:**
  - Total isolation: When the Decoy PIN is entered, `vault_primary.db` is never opened, queried, or referenced. A forensic inspect of an unlocked decoy session cannot find any pointer to the primary vault.
  - Public notes remain fast and accessible without any cryptographic key latency.

