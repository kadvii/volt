# Database Schema & Entity Specification — Vault

## 1. Multi-Database Architecture

To preserve plausible deniability and prevent catastrophic cross-contamination, **Vault** employs three isolated SQLite databases managed via Android Jetpack Room:

1. **`notes.db` (Public Notes Database):** Contains all notes, categories, and tags. Always accessible, zero encryption required (or optional standard SQLite cipher).
2. **`vault_primary.db` (Primary Vault Database):** Contains metadata for files, albums, and settings in the primary secure vault. Only opened after successful Master PIN / Biometric authentication.
3. **`vault_decoy.db` (Decoy Vault Database):** Physically separated SQLite database located in `context.filesDir/vault_decoy/`. Opened only when the Decoy PIN is entered.

```
/data/data/com.vault.notes/
├── databases/
│   ├── notes.db               <-- Public notes database
│   └── notes.db-wal
├── files/
│   ├── vault_primary/
│   │   ├── vault_primary.db   <-- Primary encrypted metadata
│   │   └── media/             <-- <UUID>.enc ciphertext files
│   └── vault_decoy/
│       ├── vault_decoy.db     <-- Isolated decoy metadata
│       └── media/             <-- Decoy ciphertext files
```

---

## 2. Public Notes Database (`notes.db`)

### 2.1 Table: `notes`
```sql
CREATE TABLE IF NOT EXISTS notes (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    category TEXT,
    color_hex TEXT NOT NULL DEFAULT '#F4F4F0',
    is_pinned INTEGER NOT NULL DEFAULT 0,
    is_archived INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_notes_updated_at ON notes(updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_notes_pinned ON notes(is_pinned DESC);
CREATE INDEX IF NOT EXISTS idx_notes_category ON notes(category);
```

### 2.2 Table: `categories`
```sql
CREATE TABLE IF NOT EXISTS categories (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    name TEXT NOT NULL UNIQUE,
    color_hex TEXT NOT NULL DEFAULT '#2D6A4F',
    created_at INTEGER NOT NULL
);
```

---

## 3. Vault Databases (`vault_primary.db` and `vault_decoy.db`)

### 3.1 Table: `vault_files`
Stores file metadata for encrypted media assets. Plaintext media binaries are NEVER stored in this table; only disk file references, initialization vectors, and encrypted thumbnails are retained.

```sql
CREATE TABLE IF NOT EXISTS vault_files (
    id TEXT PRIMARY KEY NOT NULL, -- UUIDv4 string
    encrypted_file_name TEXT NOT NULL, -- E.g. "a9f8b2c1-....enc"
    original_file_name TEXT NOT NULL,
    mime_type TEXT NOT NULL,
    file_size_bytes INTEGER NOT NULL,
    iv_base64 TEXT NOT NULL, -- 12-byte random nonce in Base64
    thumbnail_encrypted_blob BLOB, -- Small AES-GCM encrypted thumbnail byte array
    thumbnail_iv_base64 TEXT, -- 12-byte nonce for thumbnail
    media_duration_ms INTEGER DEFAULT 0, -- For video/audio
    width INTEGER DEFAULT 0, -- Resolution width
    height INTEGER DEFAULT 0, -- Resolution height
    album_id INTEGER, -- Nullable foreign key to albums
    date_added INTEGER NOT NULL,
    date_taken INTEGER DEFAULT 0,
    sha256_ciphertext TEXT NOT NULL, -- Integrity checksum
    FOREIGN KEY(album_id) REFERENCES albums(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_vault_files_date_added ON vault_files(date_added DESC);
CREATE INDEX IF NOT EXISTS idx_vault_files_album_id ON vault_files(album_id);
CREATE INDEX IF NOT EXISTS idx_vault_files_mime_type ON vault_files(mime_type);
```

### 3.2 Table: `albums`
```sql
CREATE TABLE IF NOT EXISTS albums (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    name TEXT NOT NULL,
    cover_file_id TEXT,
    created_at INTEGER NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY(cover_file_id) REFERENCES vault_files(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_albums_sort_order ON albums(sort_order ASC);
```

### 3.3 Table: `vault_configuration`
Key-value store for internal vault configuration.
```sql
CREATE TABLE IF NOT EXISTS vault_configuration (
    config_key TEXT PRIMARY KEY NOT NULL,
    config_value TEXT NOT NULL
);
```

---

## 4. Authentication & Security Storage (Encrypted DataStore)

PIN verification hashes and authentication configuration are stored in an Android Keystore-encrypted Jetpack DataStore preferences container:

| Preference Key | Type | Description |
| :--- | :--- | :--- |
| `master_pin_salt_b64` | String | 32-byte cryptographically secure random salt (Base64). |
| `master_pin_hash_b64` | String | 256-bit PBKDF2WithHmacSHA256 verification hash (Base64). |
| `decoy_pin_salt_b64` | String | Independent 32-byte salt for decoy PIN. |
| `decoy_pin_hash_b64` | String | 256-bit PBKDF2 hash for decoy PIN. |
| `biometric_enabled` | Boolean | True if user opted into BiometricPrompt unlock. |
| `auto_lock_timeout_ms` | Long | Auto-lock duration (0 = Immediate, 30000 = 30s, etc.). |
| `failed_pin_attempts` | Int | Counter of consecutive failed PIN submissions. |
| `lockout_until_timestamp` | Long | Epoch millis until which PIN attempts are blocked. |
| `secret_gesture_type` | String | `"LONG_PRESS_TITLE"` or `"DOUBLE_TAP_TITLE"`. |

---

## 5. Room Entity Data Models (Kotlin)

```kotlin
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val category: String?,
    @ColumnInfo(name = "color_hex") val colorHex: String = "#F4F4F0",
    @ColumnInfo(name = "is_pinned") val isPinned: Boolean = false,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "vault_files",
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["album_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("date_added"),
        Index("album_id"),
        Index("mime_type")
    ]
)
data class VaultFileEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "encrypted_file_name") val encryptedFileName: String,
    @ColumnInfo(name = "original_file_name") val originalFileName: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "file_size_bytes") val fileSizeBytes: Long,
    @ColumnInfo(name = "iv_base64") val ivBase64: String,
    @ColumnInfo(name = "thumbnail_encrypted_blob") val thumbnailEncryptedBlob: ByteArray?,
    @ColumnInfo(name = "thumbnail_iv_base64") val thumbnailIvBase64: String?,
    @ColumnInfo(name = "media_duration_ms") val mediaDurationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    @ColumnInfo(name = "album_id") val albumId: Long? = null,
    @ColumnInfo(name = "date_added") val dateAdded: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "date_taken") val dateTaken: Long = 0L,
    @ColumnInfo(name = "sha256_ciphertext") val sha256Ciphertext: String
)
```

---

## 6. Migration Strategy

1. **Strict Versioning:** Room databases will start at `version = 1`.
2. **Automated Migration Tests:** Room migration tests will run via `MigrationTestHelper` before any schema changes are deployed.
3. **Decoy Consistency:** Any schema migration applied to `vault_primary.db` must simultaneously be applied to `vault_decoy.db`.

