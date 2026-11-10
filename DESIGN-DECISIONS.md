# Design Decisions & UX Rationale — Vault

## 1. Overview
This document records the foundational product design decisions, trade-offs, and behavioral UX principles established for **Vault**.

---

## 2. Decision Log & Rationale

### Decision 01: Why a Notes Disguise Instead of a Calculator or Clock?
- **Options Evaluated:**
  - Option A: Calculator disguise with passcode formula (e.g., `PIN + =`).
  - Option B: System Utility disguise (Audio Manager / Battery Saver).
  - Option C: Fully functional, modern Notes application.
- **Decision:** **Option C (Notes Application)**.
- **Rationale:**
  - Calculators with equals-sign triggers are universally recognized today. Modern users, suspicious spouses, school administrators, and border agents immediately test calculators for passcode behavior.
  - A Notes app is ubiquitous on every mobile device. It is expected to hold personal data, but its appearance arouses zero curiosity.
  - Unlike fake calculators that fail when performing scientific formulas, Vault provides a genuinely useful, complete notes platform with categories, search, and markdown styling. If someone inspects the app, it works flawlessly.

---

### Decision 02: Why Secret Long-Press on Title Text?
- **Options Evaluated:**
  - Option A: Multi-finger tap gesture (e.g. 3-finger triple tap).
  - Option B: Hardware key combination (Volume Up + Volume Down).
  - Option C: Unmarked title long-press (2.0s hold on "Notes").
- **Decision:** **Option C (Title Long-Press)**.
- **Rationale:**
  - Hardware key combos interfere with media volume and system accessibility shortcuts.
  - Multi-finger tap gestures are awkward on one-handed mobile devices and easily misfire.
  - Holding the title of an app is an intuitive gesture for power users, completely invisible to observers, and virtually impossible to trigger during normal note reading or editing.
  - The 2.0-second threshold prevents accidental triggers during screen cleaning or scrolling, and the 25ms haptic tick provides discrete tactile confirmation.

---

### Decision 03: Why Neutral Feedback for Incorrect Passcodes?
- **Options Evaluated:**
  - Option A: Display standard error dialog: *"Incorrect PIN. Try again."*
  - Option B: Display neutral error: *"Note not found"*.
  - Option C: Subtle horizontal shake animation + automatic field clear, followed by silent return to Notes.
- **Decision:** **Option C**.
- **Rationale:**
  - Displaying any message referencing a "PIN" or "Vault" proves to an adversary or observer that a secret compartment exists.
  - A gentle shake followed by returning to the notes list leaves an intruder confused: they cannot determine whether the screen was a note lock, an internal debug screen, or a cancelled action.

---

### Decision 04: Why Plausible Deniability Decoy Vault?
- **Options Evaluated:**
  - Option A: Single Master PIN with emergency "Nuke / Wipe" PIN.
  - Option B: Decoy Vault with independent credential and isolated data.
- **Decision:** **Option B (Decoy Vault)**.
- **Rationale:**
  - A "Wipe" PIN causes irreversible data loss and immediately escalates suspicion during coercive interrogation (the inquisitor realizes the data was intentionally destroyed).
  - A Decoy Vault allows the user to unlock the app under duress, revealing an authentic, innocent gallery containing benign photos (e.g. family pets, landscape wallpapers, receipts). The inquisitor is satisfied, and the real vault remains completely hidden.

---

### Decision 05: Visual Differentiation: Light/Pastel Notes vs. Deep Slate Vault
- **Rationale:**
  - The public Notes app uses Material You dynamic colors and warm pastel accents to convey friendliness, approachability, and everyday utility.
  - The hidden Vault uses a deep slate/OLED dark palette (`#0A0C0E`). This serves two critical purposes:
    1. **Cognitive Mode Awareness:** The distinct visual atmosphere ensures the user is immediately aware they are inside the secure compartment, preventing accidental confusion with public screens.
    2. **Media Fidelity:** Media (photos and videos) looks superior against pure dark backgrounds with zero ambient light glare.

---

### Decision 06: Zero-Disk Decryption UX
- **Rationale:**
  - Staging decrypted files into temporary cache directories makes thumbnail loading fast and easy to program, but creates severe forensic vulnerabilities (unencrypted deleted inodes can be recovered with carving tools).
  - We decided on direct in-memory decryption streams for Coil and ExoPlayer. While technically complex, this delivers instant playback while guaranteeing zero unencrypted disk footprints.

