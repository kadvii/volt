# Product Requirements Document (PRD) — Vault

## 1. Executive Summary

**Product Name:** Vault (working title)  
**Platform:** Android (Min SDK: 29 / Android 10, Target SDK: 35 / Android 15)  
**Application Category:** Productivity / Personal Privacy  
**Architectural Model:** Disguised Dual-State Architecture (Legitimate Notes App + Hidden Encrypted Vault)  

Vault is a privacy-first Android application designed to protect sensitive personal media (photos, videos, confidential documents) from unauthorized physical and forensic inspection. Unlike traditional vault applications that overtly advertise their protective nature with lock icons and security branding—which immediately arouses suspicion—Vault appears, feels, and operates as an authentic, fully functional modern note-taking application. 

Behind a discrete, configurable secret gesture and a stealth authentication layer lies an encrypted compartment powered by AES-256-GCM, hardware-backed Android Keystore, and segregated SQLite/Room databases. Vault also incorporates a plausible deniability "Decoy Vault" mechanism, safeguarding users against coerced access.

---

## 2. Core Problem & Market Opportunity

### 2.1 The Problem
1. **The "Curious Friend & Shoulder Surfer" Threat:** When handing an unlocked device to friends, family members, or colleagues to view a photo or show an app, users risk inadvertent exposure of private photos, identification records, or financial documents.
2. **The "Suspicion Paradox" of Existing Vault Apps:** Traditional vault apps (e.g., "Secret Calculator", "Photo Locker", "AppLock") look obvious in app drawer listings, notification centers, and recent tasks. Their presence signals that the user is hiding something, provoking interrogation or forced unlock in intimate partner surveillance, workplace audits, or border inspections.
3. **Pervasive Plaintext Leaks:** Many privacy tools cache decrypted thumbnails in temporary external storage, leak file names in MediaStore, allow screenshotting in the Android Recents task switcher, or fail to scrub encryption keys from memory upon backgrounding.
4. **Coercion & Compulsion Vulnerability:** In situations where a user is forced to enter a PIN (duress), existing apps lack an authentic alternative storage environment, immediately exposing all private media.

### 2.2 The Solution: Disguised Dual-State Architecture
Vault solves these problems by providing:
- **Zero Suspicion:** A genuine, feature-rich Notes application (search, tags, markdown formatting, note colors, pinned notes) that is completely functional even if the user never opens the vault.
- **Stealth Trigger:** An unannounced secret interaction on the UI (e.g., long-press on top app bar title) that invokes an unobtrusive authentication layer.
- **Hardware-Backed AES-256-GCM:** Authenticated cryptographic isolation for photos, 4K videos, PDFs, and notes.
- **Decoy Chamber:** A separate authentication credential that unlocks an authentic secondary vault with separate keys and independent data, preserving plausible deniability.
- **Zero-Disk Decryption:** In-memory, streaming decryption pipeline for media viewing and video playback without temporary plaintext file writes.

---

## 3. Target Audience & User Personas

### Persona 1: Sarah — The Privacy-Conscious Professional
- **Age:** 31
- **Occupation:** Corporate Legal Counsel
- **Context:** Carries personal and confidential client identity documents, lease agreements, and medical scans on her primary personal phone.
- **Pain Point:** Frequently lends her phone during meetings to present presentations or let family use the camera. Terrified of confidential documents appearing in standard Google Photos or Gallery sweeps.
- **Needs:** Fast import of sensitive PDFs and photos, zero-trace storage, immediate auto-lock when switching apps.

### Persona 2: Alex — The Personal Autonomy Advocate
- **Age:** 24
- **Occupation:** Software Engineer / Activist
- **Context:** Lives in an environment with high interpersonal scrutiny where roommates or family members routinely inspect device apps.
- **Pain Point:** Any app named "Vault", "Locker", or "Hide Photos" triggers invasive questions.
- **Needs:** Complete disguise as a mundane daily notes utility, no notifications that betray privacy features, and a decoy PIN that presents innocent everyday photos under duress.

### Persona 3: Marcus — The Creative Freelancer
- **Age:** 28
- **Occupation:** Photographer & Content Creator
- **Context:** Stores unreleased client shoots and private personal media on device.
- **Pain Point:** Existing vault apps compress photos, strip EXIF data destructively, or buffer full 4K video into RAM, causing out-of-memory (OOM) crashes.
- **Needs:** High-performance viewing of full-resolution images, smooth chunked streaming playback of high-bitrate video, organized private albums.

---

## 4. Product Vision & Value Proposition

### 4.1 Vision Statement
*"Provide individuals with absolute control over their personal digital privacy on Android through undetectable disguise, uncompromising cryptographic engineering, and intuitive daily productivity."*

### 4.2 Value Propositions
| Pillar | Proposition |
| :--- | :--- |
| **Plausible Disguise** | Looks, launches, and operates as an authentic Notes application with full note management. |
| **Cryptographic Integrity** | AES-256-GCM with keys anchored in hardware Keystore; no plaintext caching on disk. |
| **Coercion Resistance** | Dual-credential architecture separating Primary and Decoy compartments at the database, key, and filesystem layers. |
| **Seamless Android Integration** | Full compliance with Android 10–15 Scoped Storage, Photo Picker, and BiometricPrompt without asking for dangerous broad permissions. |

---

## 5. System Modes & Functional Scope

```
+-------------------------------------------------------------+
|                         Vault App                           |
+-------------------------------------------------------------+
                               |
            +------------------+------------------+
            |                                     |
            v                                     v
+-----------------------+             +-----------------------+
|  Normal Notes Mode    |             |  Secret Entry Layer   |
| (Always Default Entry)|             | (Title Long-Press)    |
+-----------------------+             +-----------------------+
                                                  |
                                                  v
                                      +-----------------------+
                                      | Stealth PIN / Bio     |
                                      +-----------------------+
                                                  |
                       +--------------------------+--------------------------+
                       |                                                     |
                       v                                                     v
          +-------------------------+                           +-------------------------+
          |   Primary Vault Mode    |                           |    Decoy Vault Mode     |
          |  (Master Key & DB)      |                           |  (Decoy Key & DB)       |
          +-------------------------+                           +-------------------------+
          | - Private Photos        |                           | - Decoy Photos          |
          | - Private Videos        |                           | - Decoy Notes           |
          | - Private Documents     |                           | - Innocent Structure    |
          | - Albums & Management   |                           | - Zero Primary Trace    |
          | - Encrypted Backup      |                           +-------------------------+
          +-------------------------+
```

---

## 6. Functional Requirements Overview

### 6.1 Notes Application (Public Surface)
1. **Note Management:** Create, read, update, delete (CRUD) text notes.
2. **Organization:** Categories/tags (Work, Personal, Ideas), pinned notes, color labeling.
3. **Search:** Real-time search across titles and body contents.
4. **Theme Support:** Dynamic Material You, explicit Light and Dark mode.
5. **Persistence:** Fully persistent local Room database for notes, completely unencrypted or encrypted with standard SQLite cipher, accessible instantly without any PIN.

### 6.2 Secret Entry & Authentication
1. **Discreet Trigger:** Configurable stealth gesture (Default: 2.0-second long-press on top app bar title "Notes" with optional subtle haptic tick).
2. **Stealth Authentication Screen:** Minimal numeric PIN pad (4–8 digits) styled to look like an internal note lock or diagnostic screen.
3. **Biometric Unlock:** Optional Android `BiometricPrompt` (Class 3 strong biometrics) enabled only after Master PIN creation.
4. **Disguised Feedback for Invalid Attempts:** Entering an invalid PIN does NOT display "Wrong Vault PIN"; instead it triggers a subtle button vibration or a neutral notification (e.g. "Note note found", "Index refreshed") to avoid revealing the existence of a vault.
5. **Rate Limiting & Anti-Brute-Force:** Exponential backoff after 5 consecutive failed attempts (30s, 1m, 5m lockouts).

### 6.3 Vault Storage & Media Management
1. **Photo & Video Import:** Modern Android Photo Picker (`PickMultipleVisualMedia`) integration.
2. **Document Import:** Storage Access Framework (`OpenMultipleDocuments`) for PDF, TXT, DOCX, etc.
3. **Encryption Pipeline:** Files encrypted using AES-256-GCM chunked streaming into internal sandboxed directory (`context.filesDir/vault_primary/`).
4. **Original File Deletion Assistance:** After cryptographic verification, request system deletion of original file via `MediaStore.createDeleteRequest()` on Android 11+ or SAF delete.
5. **In-App Media Viewer:** High-performance Coil/Compose image viewer with pinch-to-zoom; decrypted directly into memory buffers without disk writes.
6. **In-App Video Player:** Media3/ExoPlayer integration streaming decrypted byte chunks via custom `DataSource` without staging full decrypted files.
7. **Albums:** Create custom albums, assign media items, move files between albums.
8. **Export / Restore:** Re-encrypt or write decrypted file back to user-selected destination in public storage with explicit intent.

### 6.4 Decoy Vault
1. **Independent Credential:** Separate Decoy PIN configured in Vault Security Settings.
2. **Complete Data Segregation:** Decoy session mounts `vault_decoy/` directory and `decoy_vault.db`.
3. **Zero Primary Traces:** Primary encryption keys are never initialized or loaded when the Decoy PIN is matched.

### 6.5 Privacy & Security Controls
1. **Screen Capture Protection:** Enforce `FLAG_SECURE` whenever the Vault or Authentication screen is active.
2. **Auto-Lock:** Instant locking upon `onPause` or configurable timeout (Immediate, 30s, 1m, 5m).
3. **Backup Exclusion:** Exclude `vault_primary/` and `vault_decoy/` from Android Cloud Backups via `res/xml/backup_rules.xml` and `dataExtractionRules.xml`.
4. **Secure Wipe & Memory Scrubbing:** Explicit zeroing of byte and char arrays containing keys or unencrypted buffers.

### 6.6 Encrypted Backup & Restore
1. **Encrypted Archive:** Export full vault content into a single encrypted `.vlt` archive protected by user-defined Backup Passphrase (PBKDF2/Argon2 + AES-256-GCM).
2. **Selective / Isolated Restore:** Primary backup contains only primary data; Decoy backup contains only decoy data.

---

## 7. Non-Functional Requirements

| Metric | Target | Rationale |
| :--- | :--- | :--- |
| **Startup Latency** | Notes app cold start < 450ms | Must feel indistinguishable from standard note apps. |
| **Memory Footprint** | Peak RAM < 180MB during 4K video playback | Streaming decryption chunks; no full-file memory allocations. |
| **Encryption Throughput** | > 40 MB/s on mid-tier hardware | AES-256-GCM hardware acceleration (ARMv8 Cryptography Extensions). |
| **Battery Impact** | Negligible background consumption (< 0.1%/hr) | No background sync daemons or unneeded polling workers. |
| **Reliability** | Zero data corruption on crash/kill | Atomic write operations: write to `.tmp`, flush to disk, verify tag, atomic rename. |
| **Compatibility** | API 29 (Android 10) through API 35 (Android 15) | Covers >94% of active worldwide Android devices. |

---

## 8. MVP Scope vs. Future Roadmap

### MVP Scope (Current Release)
- Genuine Notes Application (CRUD, Search, Categories, Persistence).
- Secret Title Long-Press Gesture entry.
- Master PIN & Decoy PIN authentication.
- BiometricPrompt integration.
- Hardware-backed AES-256-GCM key management via Android Keystore.
- Scoped Storage Photo Picker & SAF document import.
- Secure in-memory image viewing and album management.
- ExoPlayer chunked video playback.
- `FLAG_SECURE` and auto-lock on app backgrounding.
- Safe original media deletion handling via MediaStore API.

### Future Roadmap (Post-MVP)
- **v1.1:** Encrypted audio recordings & secret audio recorder.
- **v1.2:** Encrypted cloud backup to user's personal WebDAV / Nextcloud / Google Drive (client-side encrypted).
- **v1.3:** Emergency panic gesture (quick phone flip down immediately locks and switches to note screen).
- **v1.4:** Fake note calculator disguise option (alternate skin for users who prefer calculator disguise).

---

## 9. Error Handling & Edge Cases

| Edge Case | Failure Mode | Mitigation Strategy |
| :--- | :--- | :--- |
| **Process Kill during Import** | Half-written ciphertext file left on disk. | Write to temporary staging file (`.import_tmp`); verify complete GCM tag before committing to database and renaming. Cleanup orphaned `.import_tmp` on app startup. |
| **Original File Delete Denied** | User denies MediaStore delete prompt. | Retain encrypted vault file; notify user: "Encrypted copy saved to Vault. Original remains in your gallery." |
| **Low Device Storage (< 200MB)** | `IOException: No space left on device` during encryption. | Pre-check available disk space before starting import: require `freeSpace >= (fileSize * 1.1)`. Abort gracefully if insufficient. |
| **Corrupted Encrypted Block** | `AEADBadTagException` during decryption. | Catch exception, log sanitized error, show "File damaged or authentication failed" without crashing app. |
| **Keystore Key Invalidation** | User changes or removes lockscreen credentials. | Catch `KeyPermanentlyInvalidatedException`. Prompt user to enter Master Passphrase to re-wrap or re-initialize keystore keys. |
| **Decoy PIN Duplication** | User attempts to set Decoy PIN identical to Master PIN. | Reject at validation: "Decoy code must be different from master code." |

---

## 10. Success Metrics & KPIs
1. **Zero Exposure Incidents:** 100% test coverage against plaintext disk leakage and background task previews.
2. **Import Integrity:** 99.99% successful import and verification rate across 10,000+ automated test cycles.
3. **Disguise Plausibility:** 0% mention of security or encryption keywords in public UI components, strings, or app manifests visible to casual inspection.
4. **Crash-Free Sessions:** > 99.8% crash-free user sessions.

