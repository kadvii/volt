# Security Architecture Specification — Vault

## 1. Cryptographic Key Hierarchy

Vault implements a multi-tier key architecture separating root storage encryption keys from user-entered verification credentials.

```
+-----------------------------------------------------------------------------------+
|                        Hardware Security Module (TEE / StrongBox)                 |
+-----------------------------------------------------------------------------------+
       |                                                              |
       v                                                              v
+-----------------------------+                        +----------------------------+
| MasterVaultKey (AES-256)    |                        | DecoyVaultKey (AES-256)    |
| Keystore Alias:             |                        | Keystore Alias:            |
| "vault_master_key_alias"    |                        | "vault_decoy_key_alias"    |
+-----------------------------+                        +----------------------------+
       |                                                              |
       | Encrypts / Decrypts                                          | Encrypts / Decrypts
       v                                                              v
+-----------------------------+                        +----------------------------+
| Primary Files & Metadata    |                        | Decoy Files & Metadata     |
| - AES-256-GCM file stream   |                        | - AES-256-GCM file stream  |
| - vault_primary.db          |                        | - vault_decoy.db           |
+-----------------------------+                        +----------------------------+
```

---

## 2. Authentication & Key Derivation Protocol

User PINs are **NEVER** used directly as encryption keys and are **NEVER** stored in plaintext.

### 2.1 Master PIN Verification Algorithm
```
MasterPIN (4-8 digits) 
   + Random Salt (32 bytes via SecureRandom)
         |
         v
   PBKDF2WithHmacSHA256 (100,000 iterations)
         |
         v
   256-bit Candidate Verification Hash
         |
   Constant-Time Compare (MessageDigest.isEqual)
         |
   Stored Verification Hash (in EncryptedDataStore)
```

### 2.2 Decoy PIN Verification Algorithm
- The Decoy PIN utilizes its own separate 32-byte salt generated via `SecureRandom`.
- Generates its own 256-bit PBKDF2 hash.
- Verification occurs concurrently in constant time to prevent timing-based side-channel attacks that could determine whether an entered PIN was evaluated against the Master or Decoy hash.

---

## 3. Vault Session Lifecycle & In-Memory Key Management

### 3.1 Session State Machine
```kotlin
sealed interface VaultSessionState {
    object Locked : VaultSessionState
    data class AuthenticatedPrimary(
        val masterKey: SecretKey,
        val loginTimestamp: Long = System.currentTimeMillis()
    ) : VaultSessionState
    data class AuthenticatedDecoy(
        val decoyKey: SecretKey,
        val loginTimestamp: Long = System.currentTimeMillis()
    ) : VaultSessionState
}
```

### 3.2 In-Memory Key Zeroization Protocol
When a session terminates (user exits, screen turns off, timeout reached, or app backgrounded):
```kotlin
fun lockSession() {
    val current = _sessionState.value
    if (current is VaultSessionState.AuthenticatedPrimary) {
        // Zeroize any transient byte arrays
        MemoryScrubber.wipe(current.masterKey)
    } else if (current is VaultSessionState.AuthenticatedDecoy) {
        MemoryScrubber.wipe(current.decoyKey)
    }
    _sessionState.value = VaultSessionState.Locked
    // Explicit garbage collection hint for transient buffers
    System.gc()
}
```

---

## 4. Decoy Isolation Guarantee

The Decoy Vault is an independent subsystem:
1. **Independent Cryptographic Roots:** Decoy files are encrypted using `DecoyVaultKey` which is completely distinct from `MasterVaultKey`.
2. **Independent Databases:** Decoy database operations execute on `vault_decoy.db`. The primary database handle is never opened, ensuring that SQLite journal files or locks cannot reveal primary activity.
3. **Zero Primary Metadata Access:** The decoy session has no access to primary statistics, file counts, or logs.
4. **Settings Quarantine:** In Decoy mode, all Master PIN management and backup options are completely suppressed from the UI.

---

## 5. System Hardening & Privacy Controls

### 5.1 Dynamic `FLAG_SECURE` Management
`FLAG_SECURE` is applied dynamically to the Android `Window`:
- Enabled immediately when navigating to `stealth_auth` or any `vault_*` route.
- Blocks Android OS screenshots.
- Renders a black thumbnail card in the Android Recent Apps / Task Switcher.
- Blocks HDMI screen mirroring and third-party screen recorders.

### 5.2 Android Auto Backup Exclusion
The Android manifest declares `android:fullBackupContent="@xml/backup_rules"` and `android:dataExtractionRules="@xml/data_extraction_rules"`.
```xml
<!-- res/xml/data_extraction_rules.xml -->
<data-extraction-rules>
    <cloud-backup>
        <exclude path="vault_primary" />
        <exclude path="vault_decoy" />
        <exclude path="databases/vault_primary.db" />
        <exclude path="databases/vault_decoy.db" />
    </cloud-backup>
    <device-transfer>
        <exclude path="vault_primary" />
        <exclude path="vault_decoy" />
    </device-transfer>
</data-extraction-rules>
```

### 5.3 Secure Clipboard Handling
On Android 13+ (API 33+), any text copied from private notes or document viewer is marked as sensitive:
```kotlin
val clipData = ClipData.newPlainText("VaultText", text)
clipData.description.extras = PersistableBundle().apply {
    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
}
clipboardManager.setPrimaryClip(clipData)
```
The clipboard is automatically cleared via an asynchronous coroutine timer after 30 seconds.

### 5.4 Release Binary Obfuscation (R8 / ProGuard)
- Strips all `Log.v()`, `Log.d()`, `Log.i()`, and `Timber.*` calls in release builds.
- Obfuscates class names, field identifiers, and method signatures in `:core-security` and `:feature-vault`.
- Renames package paths to prevent automated static signature matching.

