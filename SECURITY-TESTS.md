# Security Test Plan & Verification Suite — Vault

This document defines automated security test suites, cryptographic verification benchmarks, and forensic penetration testing methodologies for **Vault**.

---

## 1. Automated Cryptographic Test Suite (JUnit 5 + Robolectric)

### Test SEC-01: Initialization Vector (IV) Uniqueness
- **Objective:** Verify that every encryption operation generates a unique 12-byte IV.
- **Method:**
  - Execute 10,000 simulated file encryptions in a tight loop.
  - Collect all 12-byte IV arrays into a `HashSet<ByteBuffer>`.
  - Assert `set.size == 10000`.
- **Pass Criteria:** Zero duplicate IVs across all iterations.

### Test SEC-02: AES-GCM Tamper Detection (Bit-Flipping Attack)
- **Objective:** Verify that any modification to ciphertext bytes is immediately detected.
- **Method:**
  - Encrypt a test image payload.
  - Flip a single bit in the ciphertext stream at random byte positions (byte 10, middle byte, tag byte).
  - Attempt decryption with the original key and IV.
  - Assert that `javax.crypto.AEADBadTagException` is thrown.
- **Pass Criteria:** 100% of tampered payloads throw `AEADBadTagException`; zero tampered bytes are returned as plaintext.

### Test SEC-03: Truncation Detection
- **Objective:** Verify that truncated ciphertext files are rejected.
- **Method:**
  - Encrypt a 500 KB test file.
  - Truncate the last 16 bytes (the GCM authentication tag).
  - Attempt decryption.
  - Assert that `AEADBadTagException` or `IllegalBlockSizeException` is thrown.
- **Pass Criteria:** Truncated file cannot be decrypted.

### Test SEC-04: Memory Zeroization Verification
- **Objective:** Verify that `MemoryScrubber` overwrites sensitive buffers.
- **Method:**
  - Allocate a `ByteArray` with random non-zero bytes.
  - Call `MemoryScrubber.wipe(byteArray)`.
  - Assert that all elements in `byteArray` equal `0.toByte()`.
- **Pass Criteria:** 100% of buffer indices are zeroed out.

---

## 2. Decoy Vault Forensic Isolation Tests

### Test SEC-05: Decoy Session Physical Isolation
- **Objective:** Ensure no access to `vault_primary.db` occurs when the Decoy PIN is authenticated.
- **Method:**
  - Seed primary vault with 10 records and decoy vault with 2 records.
  - Lock session.
  - Authenticate using Decoy PIN.
  - Inspect SQLite connection pool: assert `vault_primary.db` connection is `CLOSED`.
  - Assert that executing `vaultFileDao.getAllFiles()` returns exactly the 2 decoy records.
  - Query Android Keystore: assert `MasterVaultKey` alias is not queried or loaded into memory.
- **Pass Criteria:** Zero disk I/O, zero memory references, and zero queries to primary assets during decoy session.

### Test SEC-06: Cross-Key Decryption Invalidation
- **Objective:** Ensure `DecoyVaultKey` cannot decrypt a primary vault file.
- **Method:**
  - Encrypt a payload using `MasterVaultKey`.
  - Attempt decryption using `DecoyVaultKey` and original IV.
  - Assert `AEADBadTagException` is thrown.
- **Pass Criteria:** Cryptographic cross-isolation holds.

---

## 3. Privacy & Leakage Forensic Penetration Tests

### Test SEC-07: Plaintext File Disk Carving Audit
- **Objective:** Verify zero unencrypted media files or fragments are written to disk during image viewing or video playback.
- **Method:**
  - Import a unique test image containing a distinct canary byte sequence (`"CANARY_TOKEN_987654321"`).
  - Open and view the image in `SecureImageViewerScreen`.
  - While viewer is active, recursively scan `context.cacheDir`, `context.filesDir`, and external storage partitions using raw byte pattern matching.
  - Assert the canary string is found **ONLY** within the encrypted `<UUID>.enc` file in internal storage and nowhere in cache or temp folders.
- **Pass Criteria:** Zero plaintext canary occurrences on physical disk outside the ciphertext file.

### Test SEC-08: Window Screenshot & Recents Prevention (`FLAG_SECURE`)
- **Objective:** Verify that Android blocks screen capture and recents thumbnails.
- **Method:**
  - Navigate to `StealthAuthScreen` and `VaultDashboardScreen`.
  - In an instrumentation test, invoke `UiDevice.takeScreenshot(file)`.
  - Assert the screenshot operation fails or outputs a completely black bitmap.
  - Assert `(window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0`.
- **Pass Criteria:** `FLAG_SECURE` strictly enforced.

### Test SEC-09: Android Auto-Backup Exclusion Verification
- **Objective:** Verify that private vault files are excluded from Android OS cloud backups.
- **Method:**
  - Parse `res/xml/data_extraction_rules.xml` and `res/xml/backup_rules.xml`.
  - Execute `bmgr` backup simulation via ADB shell: `adb shell bmgr backupnow <package_name>`.
  - Extract and inspect the resulting backup archive package.
  - Assert that `vault_primary/`, `vault_decoy/`, `vault_primary.db`, and `vault_decoy.db` are absent from the archive.
- **Pass Criteria:** Zero vault directories or databases present in backup container.

### Test SEC-10: Rate Limiting & Anti-Brute-Force Lockout
- **Objective:** Verify exponential lockout on repeated invalid PIN submissions.
- **Method:**
  - Submit 5 invalid PINs in sequence.
  - Assert that `AuthViewModel` transitions to `LockoutState(remainingSeconds = 30)`.
  - Assert that keypad input is rejected during the 30-second window.
  - Simulate process kill and restart; assert lockout remains enforced based on persistent timestamp.
- **Pass Criteria:** Brute-force throttling persists across process kills.

