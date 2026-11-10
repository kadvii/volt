# Acceptance Criteria & Verification Matrix — Vault

This document establishes the official Gherkin (Given-When-Then) acceptance criteria for every functional and security feature in **Vault**. All tests must pass before any phase or release candidate is approved.

---

## 1. Public Notes Application

### AC-01: Cold Start into Authentic Notes
```gherkin
Scenario: Cold start opens Notes Home without security prompts
  Given the app is not running in memory
  When the user taps the app icon labeled "Notes"
  Then the app launches directly to NotesHomeScreen within 500ms
  And displays existing notes in a staggered grid or empty state
  And no PIN, password, or security dialog is presented to the user
```

### AC-02: Create and Persist Note
```gherkin
Scenario: Creating and editing a note
  Given the user is on NotesHomeScreen
  When the user taps the Floating Action Button ("+ Note")
  And enters title "Grocery List" and body "- Milk\n- Apples\n- Almonds"
  And selects the pastel Green color chip and toggles "Pin"
  And taps the back navigation button
  Then the note is saved to the local Room database (notes.db)
  And appears pinned at the top of NotesHomeScreen with green background
```

### AC-03: Real-Time Note Search
```gherkin
Scenario: Searching notes by keyword
  Given 20 notes exist in the notes database
  When the user types "Grocery" in the search bar
  Then after 300ms debounce, only notes containing "Grocery" in title or body are displayed
  And clearing the search bar immediately restores the full notes list
```

---

## 2. Secret Access Mechanism & Stealth Authentication

### AC-04: Secret Gesture Trigger
```gherkin
Scenario: Long-pressing the Notes title triggers stealth auth
  Given the user is on NotesHomeScreen
  When the user presses and holds the "Notes" title text for 2.0 seconds
  Then the device emits a single subtle 25ms haptic tick
  And the window sets FLAG_SECURE
  And navigates to the StealthAuthScreen
```

### AC-05: Tap or Short Press on Notes Title
```gherkin
Scenario: Short pressing or tapping the Notes title does not trigger auth
  Given the user is on NotesHomeScreen
  When the user taps or holds the "Notes" title for less than 1.8 seconds
  Then no haptic feedback is triggered
  And the app remains on NotesHomeScreen without any visual change
```

### AC-06: Master PIN Authentication
```gherkin
Scenario: Entering valid Master PIN unlocks Primary Vault
  Given the user is on StealthAuthScreen with Master PIN set to "5914"
  When the user enters digits "5914"
  Then the app verifies the hash against PBKDF2 salt in constant time
  And loads the MasterVaultKey from Android Keystore
  And opens vault_primary.db
  And navigates to Primary Vault Dashboard
```

### AC-07: Decoy PIN Authentication
```gherkin
Scenario: Entering valid Decoy PIN unlocks Decoy Vault
  Given Master PIN is "5914" and Decoy PIN is "1234"
  And the user is on StealthAuthScreen
  When the user enters digits "1234"
  Then the app loads the DecoyVaultKey from Android Keystore
  And opens vault_decoy.db
  And navigates to Decoy Vault Dashboard
  And vault_primary.db is never opened or queried
```

### AC-08: Disguised Feedback on Invalid PIN
```gherkin
Scenario: Entering incorrect PIN provides no confirmation of Vault existence
  Given Master PIN is "5914" and Decoy PIN is "1234"
  And the user is on StealthAuthScreen
  When the user enters digits "9999"
  Then no error dialog stating "Wrong Vault PIN" is shown
  And the keypad performs a subtle shake animation
  And the entered digits clear
  And after 3 invalid attempts, the app navigates back to NotesHomeScreen
```

### AC-09: Anti-Brute-Force Rate Limiting
```gherkin
Scenario: Enforcing lockout after repeated failed attempts
  Given 5 consecutive incorrect PIN attempts have occurred
  When the user attempts to enter another PIN
  Then the keypad is disabled with a neutral cooldown message ("Please wait 30s")
  And killing and restarting the app does not bypass the remaining lockout duration
```

### AC-10: Biometric Unlock
```gherkin
Scenario: Unlocking Primary Vault with Biometrics
  Given the user has enabled Biometric Unlock in Primary Vault Settings
  When the StealthAuthScreen is presented
  Then the system BiometricPrompt is automatically displayed
  When the user provides a valid fingerprint
  Then the app unlocks and navigates directly to Primary Vault Dashboard
```

---

## 3. Cryptographic Storage & Media Import

### AC-11: AES-256-GCM File Encryption
```gherkin
Scenario: Encrypted file storage contains zero plaintext headers
  Given the user selects an image "vacation.jpg" (3.2 MB) to import
  When the encryption engine processes the file
  Then a unique 12-byte random IV is generated
  And the file is encrypted using AES-256-GCM into a temporary file
  And the final file is saved as <UUID>.enc in private app storage
  And inspecting the raw bytes of <UUID>.enc reveals no JPEG magic bytes (FF D8 FF)
  And reveals no EXIF metadata or plaintext strings
```

### AC-12: Media Import via Android Photo Picker
```gherkin
Scenario: Importing photos without broad storage permissions
  Given the user is on VaultDashboard
  When the user taps "Import Photos"
  Then the system Photo Picker (ActivityResultContracts.PickMultipleVisualMedia) opens
  And the app requires no READ_EXTERNAL_STORAGE or READ_MEDIA_IMAGES permission
  When the user selects 3 photos and confirms
  Then each photo is encrypted and imported into Vault
```

### AC-13: Safe Original File Deletion Request
```gherkin
Scenario: Requesting deletion of original media after successful encryption
  Given 2 photos have been successfully encrypted and verified in Vault
  When the import process finishes
  Then the app prompts: "Remove original files from gallery?"
  When the user selects "Yes"
  Then on Android 11+, MediaStore.createDeleteRequest is called with the original URIs
  And the system confirmation dialog is displayed
  When the user accepts the system prompt
  Then the OS deletes the original files from public MediaStore
```

### AC-14: User Declines Original Deletion
```gherkin
Scenario: User chooses to keep original media
  Given a photo has been successfully imported into Vault
  When the app prompts: "Remove original files from gallery?"
  And the user selects "Keep Original"
  Then the encrypted copy remains in Vault
  And no MediaStore delete request is issued
```

---

## 4. In-App Media Viewing & Zero-Disk Plaintext Policy

### AC-15: Zero-Disk Decryption for Image Viewer
```gherkin
Scenario: Viewing an encrypted photo without writing plaintext to disk
  Given an encrypted photo exists in Vault
  When the user taps the photo thumbnail
  Then the image is decrypted directly into an in-memory byte buffer
  And decoded into a Bitmap for display
  And no unencrypted files exist in context.cacheDir or context.filesDir
  When the user exits the viewer
  Then the in-memory buffer is zeroed out and reclaimed
```

### AC-16: Video Streaming with ExoPlayer
```gherkin
Scenario: Streaming encrypted video without full file decryption
  Given an encrypted 4K video (1.5 GB) exists in Vault
  When the user taps the video to play
  Then Media3 ExoPlayer initializes with EncryptedFileDataSource
  And video playback begins within 400ms
  And memory consumption stays below 150 MB throughout playback
  And seeking forward jumps to the correct byte chunk without decrypting the entire file
```

---

## 5. Decoy Vault Complete Isolation

### AC-17: Strict Segregation of Decoy Data
```gherkin
Scenario: Decoy vault has zero access or visibility to Primary Vault
  Given Primary Vault contains 25 private photos
  And Decoy Vault contains 2 benign photos
  When the user unlocks the app with the Decoy PIN
  Then the UI displays only the 2 benign photos
  And the primary database file (vault_primary.db) remains locked and unread
  And storage statistics in Settings show only the size of Decoy files
  And the Master PIN cannot be modified or reset from Decoy mode
```

---

## 6. Privacy & Anti-Surveillance Lifecycle

### AC-18: Screenshot Protection via FLAG_SECURE
```gherkin
Scenario: Preventing screenshots and recent app previews
  Given the user is on any Vault screen or the StealthAuthScreen
  When the user attempts to take a device screenshot
  Then Android blocks the screenshot with message: "Can't take screenshot due to security policy"
  When the user opens the Android Recents task switcher
  Then the app preview card displays a blank/black surface
```

### AC-19: Auto-Lock on Backgrounding
```gherkin
Scenario: Vault auto-locks immediately when app is backgrounded
  Given the user is actively viewing a private photo in Vault
  When the user presses the Home button or turns off the device screen
  Then the active session keys are cleared from memory
  And the Vault database connection is closed
  When the user re-opens the app from the launcher
  Then the app displays the public NotesHomeScreen
  And no private media is visible without re-executing the secret gesture and PIN
```

### AC-20: Cloud Backup Exclusion
```gherkin
Scenario: Vault files excluded from Google Drive Android Auto-Backup
  Given the app is installed on a device with Google Auto Backup enabled
  When an OS backup cycle runs
  Then vault_primary, vault_decoy, and database files are ignored
  And only public notes and app preferences are included in backup
```

---

## 7. Encrypted Backup & Restore

### AC-21: Encrypted Backup Generation
```gherkin
Scenario: Creating an encrypted backup file
  Given the user is in Primary Vault Settings
  When the user initiates "Create Encrypted Backup" and inputs passphrase "SecretP@ss2026"
  Then the app bundles encrypted media and database metadata into a .vlt file
  And the .vlt container is encrypted with AES-256-GCM using a key derived via PBKDF2
  And the user exports the file via SAF
```

### AC-22: Backup Restoration Verification
```gherkin
Scenario: Restoring from encrypted backup
  Given a valid .vlt backup file
  When the user initiates "Restore Backup" and inputs the correct passphrase
  Then the app verifies the container header and GCM authentication tag
  And extracts the encrypted media into vault_primary/
  And reconstructs the database records
  When the user browses the vault
  Then all restored photos, videos, and albums are fully accessible
```

### AC-23: Corrupted Backup Rejection
```gherkin
Scenario: Attempting restore with corrupted backup or wrong passphrase
  Given a .vlt backup file with modified ciphertext bytes or an incorrect passphrase
  When the user attempts to restore the backup
  Then the GCM tag verification fails (AEADBadTagException)
  And the app displays: "Invalid passphrase or corrupted backup file"
  And zero files are written to internal vault storage
```

