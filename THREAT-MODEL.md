# Threat Model & Security Risk Assessment — Vault

## 1. Executive Summary & Security Philosophy

Security in **Vault** is constructed under the principles of **Defense in Depth**, **Zero-Trust Storage**, and **Honest Security Boundaries**. No security system is impenetrable; rather, Vault is engineered to withstand defined threat vectors, minimize the blast radius of compromises, and deliver plausible deniability against coercion.

This threat model identifies potential adversaries, analyzes attack surfaces using the STRIDE methodology, and details what the system can and cannot defend against.

---

## 2. Adversary Profiles & Attacker Capabilities

| Adversary Profile | Motivation | Capabilities | Access Level |
| :--- | :--- | :--- | :--- |
| **Adversary 1: The Casual Snooper** | Curiosity, jealousy, gossip | Hand-held device access for 1–5 minutes while user is in another room or device is borrowed. | Unlocked device, no technical tools. |
| **Adversary 2: The Coercive Interrogator** | Control, inspection, domestic surveillance | Compels user to open the device and show photo vaults or security apps under duress. | Physical duress, observation of user entry. |
| **Adversary 3: The Common Device Thief** | Financial resale | Steals locked or unlocked phone. Reboots device, attempts PIN guesses, connects USB. | Physical control of hardware, no hardware labs. |
| **Adversary 4: The Forensic Examiner** | Legal discovery, corporate audit, border inspection | Uses commercial forensic extraction tools (Cellebrite UFED, Oxygen, ADB root dumps). | Physical access, offline disk imaging, chip extraction. |
| **Adversary 5: On-Device Malicious App** | Credential theft, data exfiltration | Malicious app installed alongside Vault; attempts IPC exploitation, accessibility scraping, or file sniffing. | Android sandbox, accessibility permissions. |

---

## 3. STRIDE Threat Analysis Matrix

| Threat Category | Specific Threat Scenario | Risk Level | Architectural Mitigation | Residual Risk |
| :--- | :--- | :--- | :--- | :--- |
| **Spoofing** | Adversary attempts to bypass BiometricPrompt with spoofed fingerprint. | Medium | Enforce `BIOMETRIC_STRONG` (Class 3 hardware biometrics) backed by Android Keystore. | 2D fake fingerprints on low-tier sensors. |
| **Tampering** | Adversary flips ciphertext bits in raw `.enc` file to corrupt or alter payload. | High | AES-256-GCM 128-bit authentication tag. Decryption throws `AEADBadTagException` and rejects tampered blocks immediately. | Tampered file becomes unreadable (loss of data). |
| **Repudiation** | User denies storing a particular file in the primary vault during duress. | High | Decoy Vault provides plausible deniability: user presents innocent decoy container under duress. | Plausibility depends on realistic user seeding of decoy data. |
| **Information Disclosure** | Unlocked vault leaks preview in Android Recent Apps (Multitasking carousel). | Critical | `Window.setFlags(FLAG_SECURE, FLAG_SECURE)` enforced on all vault and authentication screens. Android renders black screen. | None (OS-enforced). |
| **Information Disclosure** | Plaintext video fragments left in app cache after playback. | Critical | Video stream decrypted strictly into volatile RAM buffers via `EncryptedFileDataSource`. Zero disk cache writes. | Root access with active RAM inspection. |
| **Information Disclosure** | Vault media included in Google Drive automatic cloud backup. | Critical | `backup_rules.xml` and `data_extraction_rules.xml` strictly exclude `vault_primary/` and `vault_decoy/`. | None. |
| **Denial of Service** | Adversary repeatedly inputs wrong PINs to lock user out indefinitely. | Medium | Exponential backoff (30s, 1m, 5m). Key material is never deleted or nuked on wrong PIN. | Temporary operational inconvenience for user. |
| **Elevation of Privilege** | Other Android apps access private files via shared storage. | Critical | Files stored exclusively in sandboxed internal storage (`context.filesDir`). Android UID separation blocks all external apps. | Broken on rooted devices. |

---

## 4. Realistic Security Guarantees: What We CAN and CANNOT Protect Against

### 4.1 What Vault CAN Protect Against
1. **Physical Inspection (Casual & Curious):** An observer holding the unlocked phone sees only a functional Notes app. The secret long-press trigger cannot be discovered without prior knowledge.
2. **Coercive Duress:** When forced to open the vault, entering the Decoy PIN opens an authentic decoy gallery. The primary vault database and keys are completely unreferenced.
3. **Offline Flash Storage Forensics:** If an attacker extracts an image of the device NAND flash, all vault media files are encrypted with AES-256-GCM. Without the Keystore key (anchored in the TEE/StrongBox hardware chip), brute-forcing AES-256 is computationally impossible.
4. **Thumbnail & Cache Carving:** Zero decrypted images or video chunks are written to flash storage. File-carving forensic tools cannot recover deleted plaintext media from internal storage.
5. **Screen Recording & Shoulder Surfing:** `FLAG_SECURE` prevents OS screen capture, HDMI mirroring, and screen recording utilities.
6. **Online / Cloud Backup Leakage:** Android OS Cloud Backup ignores all vault directories and databases.

### 4.2 What Vault CANNOT Protect Against (Honest Security Boundaries)
1. **Compromised OS / Kernel-Level Root Exploit:** If an attacker installs a kernel-level root exploit (e.g., Magisk with Frida/Xposed) and attaches a live memory debugger to the Vault process **while the user has the vault unlocked**, the attacker can read decrypted bitmaps directly from process RAM.
2. **Hardware Keyloggers / Malicious Accessibility Services:** If malware has been granted Android Accessibility Services permission, it can monitor touch coordinates and capture the Master PIN as the user types it on the screen.
3. **Hardware Chip-Off with Microprobing on TEE:** Nation-state adversaries with cleanrooms and electron microscopes capable of decapping the SoC and probing TEE hardware registers can theoretically extract hardware master keys.
4. **Physical Torture / Coercive Compulsion (Rubber-Hose Cryptanalysis):** If an adversary refuses to accept the Decoy Vault and forces the user to reveal the Master PIN through physical harm, no software mechanism can protect the data.

