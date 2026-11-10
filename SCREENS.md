# Screen Specifications & UI Blueprint — Vault

This document provides exhaustive screen-by-screen layout specifications, states, actions, transitions, and component compositions for both the public Notes surface and the hidden Vault surface.

---

## Part 1: Public Notes Application Screens

### Screen 1.1: Notes Home Screen (`NotesHomeScreen`)
- **Route:** `notes_home`
- **FLAG_SECURE:** Disabled (Standard behavior for normal note applications).
- **Layout Architecture:**
  - **TopAppBar (CenterAlignedTopAppBar):**
    - Navigation Icon: None (or drawer hamburger if multi-notebook enabled in future).
    - Title: `"Notes"` in `HeadlineLarge` font.
      > **Secret Trigger Attached:** Long-press detector attached to the title text `Text("Notes")`. Duration: 2000ms.
    - Actions:
      - Search icon (`Icons.Default.Search`) -> Expands search bar.
      - View Mode Toggle (`Icons.Default.GridView` / `Icons.Default.ViewAgenda`) -> Toggles between 2-column staggered grid and single-column list.
      - Overflow Menu (`Icons.Default.MoreVert`) -> *"Settings"*, *"Trash"*, *"About Notes"*.
  - **Sub-header Category Filter:** Horizontal scrolling `LazyRow` of `FilterChip` items:
    - `"All"`, `"Personal"`, `"Work"`, `"Ideas"`, `"+ Add Category"`.
  - **Body Content:**
    - `LazyVerticalStaggeredGrid` (2 columns, 8dp gutter) containing `NoteCard` components.
    - Pinned notes section placed at top with a subtle label: `"PINNED"`.
    - Other notes section below with label: `"OTHERS"`.
  - **Floating Action Button (FAB):**
    - Extended FAB: Icon `Icons.Default.Add` + Text `"New note"`. Tapping navigates to `note_editor/new`.
  - **States:**
    - *Empty State:* Friendly illustration + caption: *"No notes yet. Tap + to write your thoughts."*
    - *Populated State:* Staggered cards displaying Title (max 2 lines), Content preview (max 5 lines), timestamp, and category badge.

---

### Screen 1.2: Note Editor Screen (`NoteEditorScreen`)
- **Route:** `note_editor/{noteId}`
- **FLAG_SECURE:** Disabled.
- **Layout Architecture:**
  - **TopAppBar:**
    - Navigation Icon: Back arrow (`Icons.Default.ArrowBack`) -> Saves changes and pops backstack.
    - Actions:
      - Pin toggle (`Icons.Default.PushPin` / `Icons.Outlined.PushPin`).
      - Palette icon (`Icons.Outlined.Palette`) -> Opens bottom sheet with pastel color circles.
      - Delete icon (`Icons.Outlined.Delete`) -> Moves note to trash with undo snackbar.
  - **Body (Column with vertical scroll):**
    - Title Field: `BasicTextField` with placeholder `"Title"`, style `HeadlineMedium`, auto-capitalization words.
    - Category Tag Indicator: Small chip displaying current category, clickable to change.
    - Body Field: `BasicTextField` with placeholder `"Note..."`, style `BodyLarge`, expanding to bottom of viewport.
  - **Behavior:**
    - Auto-save on every character change with 500ms debounce.
    - Seamless keyboard management with IME padding (`WindowInsets.ime`).

---

### Screen 1.3: Notes Search Screen (`NotesSearchScreen`)
- **Route:** `notes_search`
- **Layout Architecture:**
  - **SearchBar (Material 3 SearchBar):**
    - Leading: Back icon to close search.
    - Placeholder: `"Search your notes..."`.
    - Trailing: Clear icon (`Icons.Default.Close`) when query is non-empty.
  - **Body:**
    - Live-filtered `LazyColumn` of matching notes with highlighted search keywords.
    - *Empty State:* *"No notes match '[query]'."*

---

### Screen 1.4: Public Notes Settings Screen (`NotesSettingsScreen`)
- **Route:** `notes_settings`
- **Layout Architecture:**
  - TopAppBar: Back arrow + Title `"Settings"`.
  - Preference Sections:
    - **Display:** Theme selection (System Default, Light, Dark), Dynamic Color toggle.
    - **Note Behavior:** Default note color, Show date in cards toggle.
    - **About:** App version (e.g., `"Notes v1.0.4"`), Open-source licenses.
  - **Stealth Requirement:** Absolutely no mention of Vault, Security, PIN, Encryption, or Decoy mode on this screen.

---

## Part 2: Stealth Access & Authentication Screens

### Screen 2.1: Stealth PIN Authentication Screen (`StealthAuthScreen`)
- **Route:** `stealth_auth`
- **FLAG_SECURE:** **ENFORCED** (Black screen in recents, screenshots blocked).
- **Visual Design:** Intentionally minimalist and ambiguous. Appears as a neutral verification keypad.
- **Layout Architecture:**
  - **Header Area (Top 25% of viewport):**
    - Minimal title: `"Verification"` or a subtle generic passcode glyph.
    - Row of 4–8 indicator dots (`Box(shape = CircleShape)`).
      - Empty: 12dp circle with outline.
      - Filled: 12dp circle filled with Accent Cyan (`#4EA8DE`).
  - **Numeric Keypad Area (Bottom 65% of viewport):**
    - 3x4 grid of circular buttons (72dp diameter):
      - Row 1: `1`, `2`, `3`
      - Row 2: `4`, `5`, `6`
      - Row 3: `7`, `8`, `9`
      - Row 4: `Biometric` (or Blank), `0`, `Backspace` (`Icons.Default.Backspace`)
    - Keypad buttons have subtle ripple and 15ms light haptic vibration on tap.
  - **Interactive Logic:**
    - On reaching target PIN length (e.g. 4 or 6 digits):
      - If input matches Master PIN hash: Transition with fade-through to `VaultDashboardScreen`.
      - If input matches Decoy PIN hash: Transition to `DecoyVaultDashboardScreen`.
      - If input fails: Indicator dots trigger horizontal error shake (±12dp, 300ms) and clear.
      - After 3 consecutive failed inputs: Pop immediately back to `NotesHomeScreen`.

---

### Screen 2.2: First-Time Vault Setup Flow
- **Route:** `vault_setup`
- **FLAG_SECURE:** **ENFORCED**.
- **Layout Architecture:**
  - Step 1: *"Set Your Master Code"* -> Enter 4–8 digits -> Re-enter to confirm.
  - Step 2: *"Set Decoy Code (Optional)"* -> Explains: *"Enter an alternative code to open a decoy space if forced to unlock."* -> Option to set or tap *"Skip for now"*.
  - Step 3: *"Enable Biometrics?"* -> System Biometric prompt test -> Completes setup.

---

## Part 3: Vault Interface Screens

### Screen 3.1: Primary Vault Dashboard (`VaultDashboardScreen`)
- **Route:** `vault_dashboard`
- **FLAG_SECURE:** **ENFORCED**.
- **Theme:** Slate Dark (`#0A0C0E`).
- **Layout Architecture:**
  - **TopAppBar:**
    - Navigation: Back icon (`Icons.Default.ArrowBack`) -> Immediately exits vault, purges session keys, returns to `NotesHomeScreen`.
    - Title: `"Vault"` in `HeadlineMedium` font.
    - Actions:
      - Selection Mode Toggle (`Icons.Default.SelectAll`).
      - Settings (`Icons.Default.Settings`) -> Opens `VaultSecuritySettingsScreen`.
  - **Sub-header Navigation Tabs (ScrollableTabRow):**
    - Tabs: `"All"`, `"Photos"`, `"Videos"`, `"Documents"`, `"Albums"`.
  - **Body (Media Grid):**
    - Responsive 3-to-4 column grid (`LazyVerticalGrid`).
    - Grid Item (`MediaThumbnailCard`):
      - Aspect ratio: 1:1.
      - Decrypted thumbnail loaded via Coil in-memory decoder.
      - Video duration badge (bottom-right) for video files (e.g. `"02:45"`).
      - Document icon + extension badge (e.g., `"PDF"`, `"DOC"`) for documents.
  - **Floating Action Button:**
    - Extended FAB: Icon `Icons.Default.Add` + Text `"Import"`. Tapping opens `MediaImportDialog`.
  - **States:**
    - *Empty State:* Minimalist lock illustration + text: *"Your private vault is empty. Tap Import to protect photos, videos, or documents."*

---

### Screen 3.2: Album Detail Screen (`AlbumDetailScreen`)
- **Route:** `vault_album/{albumId}`
- **FLAG_SECURE:** **ENFORCED**.
- **Layout Architecture:**
  - TopAppBar: Back arrow + Title with Album Name (e.g. `"Financial Records"`).
  - Actions: Edit Album Name, Delete Album, Import into this album.
  - Body: Filtered media grid containing only items assigned to `albumId`.

---

### Screen 3.3: In-Memory Secure Image Viewer (`SecureImageViewerScreen`)
- **Route:** `secure_image_viewer/{fileId}`
- **FLAG_SECURE:** **ENFORCED**.
- **Layout Architecture:**
  - Viewport: Pure Black (`#000000`).
  - Immersive Overlay: Tapping anywhere toggles TopAppBar and BottomActionBar visibility.
  - **TopBar Overlay (Translucent):**
    - Back button, File Name (`TitleMedium`), File Date.
  - **Center Viewport:**
    - Custom Compose gesture-detecting container supporting:
      - Pinch-to-zoom (up to 5.0x scale).
      - Double-tap to toggle 2.5x zoom.
      - Pan / drag when zoomed in.
      - Swipe down to dismiss and pop back to dashboard.
    - Image rendered directly from in-memory decrypted bitmap.
  - **BottomBar Overlay:**
    - Details (`Icons.Default.Info`) -> Opens `FileDetailsBottomSheet`.
    - Move to Album (`Icons.Default.DriveFileMove`).
    - Export / Restore (`Icons.Default.FileDownload`) -> Exports decrypted copy to gallery.
    - Delete (`Icons.Default.Delete`) -> Prompts deletion from vault.

---

### Screen 3.4: Streaming Secure Video Player (`SecureVideoPlayerScreen`)
- **Route:** `secure_video_player/{fileId}`
- **FLAG_SECURE:** **ENFORCED**.
- **Layout Architecture:**
  - Viewport: Pure Black.
  - Surface: AndroidView wrapping Media3 `PlayerView` linked to ExoPlayer instance.
  - Custom Player Controls (fade out after 3 seconds of inactivity):
    - Center: Play/Pause button, 10s Rewind, 10s Fast-Forward.
    - Bottom: Scrub bar (`Slider`), Current Time / Total Duration, Fullscreen toggle.
  - Streaming Behavior: Decrypts only 64 KB blocks on demand via `EncryptedFileDataSource`. Zero unencrypted video caching.

---

### Screen 3.5: Secure Document Viewer (`SecureDocumentViewerScreen`)
- **Route:** `secure_doc_viewer/{fileId}`
- **FLAG_SECURE:** **ENFORCED**.
- **Layout Architecture:**
  - For PDF: Multi-page vertical scrolling `LazyColumn` rendering in-memory rendered PDF page bitmaps using Android `PdfRenderer`.
  - For Plaintext/Markdown: Scrollable typography view with monospace option.

---

### Screen 3.6: Media Import Dialog / BottomSheet (`MediaImportDialog`)
- **Options Presented:**
  1. **Photos & Videos:** Triggers Android Photo Picker (`PickMultipleVisualMedia`).
  2. **Documents & Files:** Triggers Storage Access Framework (`OpenMultipleDocuments`).
- **Post-Import Sub-Dialog:**
  - When encryption completes:
    - *"Imported 4 items successfully into Vault."*
    - Prompt: *"Would you like to delete the originals from your phone gallery to save space and maintain privacy?"*
    - Actions: `[Keep Originals]` and `[Delete Originals]`.

---

### Screen 3.7: File Details Bottom Sheet (`FileDetailsBottomSheet`)
- **Content:**
  - Original File Name
  - Media Type / MIME type
  - File Size (formatted in KB / MB)
  - Date Imported & Original Date Taken
  - Encryption Specs: *"AES-256-GCM (Hardware Keystore)"*
  - Assigned Album

---

### Screen 3.8: Vault Security Settings Screen (`VaultSecuritySettingsScreen`)
- **Route:** `vault_settings`
- **FLAG_SECURE:** **ENFORCED**.
- **Content:**
  - **Authentication:**
    - Change Master PIN.
    - Biometric Unlock toggle.
    - Setup / Change Decoy PIN.
  - **Auto-Lock Rules:**
    - Lock Immediately on App Exit (Default: Enabled).
    - Lock on Screen Off (Enabled).
    - Auto-Lock Inactivity Timer (Immediate, 30s, 1m, 5m).
  - **Disguise Options:**
    - Secret Trigger Gesture (Long Press Title / Double Tap Title).
    - Title Hold Duration (1.5s, 2.0s, 3.0s).
  - **Backup & Recovery:**
    - Encrypted Backup Export & Restore navigation.
  - **Decoy Mode Guard:** If opened in Decoy Session, Master PIN settings and Primary Backup options are completely hidden!

---

### Screen 3.9: Encrypted Backup & Restore Screen (`BackupRestoreScreen`)
- **Route:** `vault_backup_restore`
- **FLAG_SECURE:** **ENFORCED**.
- **Content:**
  - Section 1: **Create Encrypted Backup**
    - Passphrase input + Confirm Passphrase.
    - Export button -> opens SAF `CreateDocument`.
  - Section 2: **Restore from Backup**
    - File picker button (`.vlt` files).
    - Passphrase prompt.
    - Restore button with progress indicator.

---

### Screen 3.10: Decoy Vault Dashboard (`DecoyVaultDashboardScreen`)
- **Route:** `decoy_dashboard`
- **FLAG_SECURE:** **ENFORCED**.
- **Appearance:** Visually identical to `VaultDashboardScreen`, but connected exclusively to `vault_decoy.db` and `vault_decoy/` folder.
- **Data Display:** Displays only decoy media items. Settings menu shows only Decoy preferences.

