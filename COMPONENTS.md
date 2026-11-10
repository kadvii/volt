# Reusable UI Components Specification — Vault

This document defines the reusable Jetpack Compose components designed for **Vault**, detailing their parameters, state models, interaction behavior, and accessibility hooks.

---

## 1. Public Notes Components

### 1.1 `SecretTriggerTopAppBar`
A custom `CenterAlignedTopAppBar` that encapsulates the stealth long-press gesture without altering standard visual rendering.

```kotlin
@Composable
fun SecretTriggerTopAppBar(
    title: String,
    onSearchClick: () -> Unit,
    onViewModeToggle: () -> Unit,
    onOverflowClick: () -> Unit,
    onSecretTriggerActivated: () -> Unit,
    modifier: Modifier = Modifier,
    holdDurationMs: Long = 2000L
)
```
- **Gesture Detection:** Applied directly to the `Text(text = title)` composable using `pointerInput`:
  ```kotlin
  Modifier.pointerInput(Unit) {
      detectTapGestures(
          onLongPress = {
              hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
              onSecretTriggerActivated()
          }
      )
  }
  ```
- **Visuals:** Zero icons or borders around the title; visually identical to a standard Material 3 TopAppBar.

---

### 1.2 `NoteCard`
A staggered grid card component displaying note summary information with authentic note aesthetics.

```kotlin
@Composable
fun NoteCard(
    note: NoteItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
)
```
- **Props:**
  - `note.title`: String (Bold, max 2 lines, ellipses overflow).
  - `note.content`: String (Regular, max 6 lines, ellipses overflow).
  - `note.color`: Color (Subtle pastel background tint).
  - `note.isPinned`: Boolean (Displays subtle push-pin icon in top-right if true).
  - `note.category`: String? (Displays small capsule chip if non-empty).
  - `note.updatedAt`: Formatted timestamp (e.g., "Yesterday", "Oct 14").
- **Card Shape:** Rounded corner (12dp).
- **Elevation:** Tonal elevation 1 (no sharp drop shadow).

---

### 1.3 `CategoryFilterRow`
A horizontally scrollable row of Material 3 `FilterChip` items.

```kotlin
@Composable
fun CategoryFilterRow(
    categories: List<String>,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit,
    onAddCategoryClick: () -> Unit,
    modifier: Modifier = Modifier
)
```
- **Visual Feedback:** Selected chip displays `LeadingIcon` checkmark with filled primary container. Unselected chips have outline borders.

---

### 1.4 `NoteColorPickerSheet`
A bottom sheet displaying selectable pastel color swatches for notes.

```kotlin
@Composable
fun NoteColorPickerSheet(
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
)
```
- **Palette Grid:** 6 pastel color circles (48dp diameter) with a selected checkmark overlay.

---

## 2. Stealth Authentication Components

### 2.1 `StealthKeypad`
A 3x4 numeric keypad designed for touch reliability, clean spacing, and tactile feedback.

```kotlin
@Composable
fun StealthKeypad(
    onDigitClick: (Char) -> Unit,
    onBackspaceClick: () -> Unit,
    onBiometricClick: (() -> Unit)?,
    isBiometricAvailable: Boolean,
    modifier: Modifier = Modifier
)
```
- **Button Specs:**
  - Diameter: 76dp circular button (`ButtonDefaults.filledTonalButtonColors`).
  - Font: `HeadlineMedium` (28sp, Medium weight).
  - Touch Feedback: Produces subtle 15ms haptic tick on every tap.
- **Layout:**
  - Row 1: `1`, `2`, `3`
  - Row 2: `4`, `5`, `6`
  - Row 3: `7`, `8`, `9`
  - Row 4: `Biometric Icon` (if enabled, else empty space), `0`, `Backspace Icon`

---

### 2.2 `PinIndicatorDots`
A row of dots reflecting the current entered PIN length with an animated error shake.

```kotlin
@Composable
fun PinIndicatorDots(
    pinLength: Int,
    maxDigits: Int = 4,
    isError: Boolean,
    onErrorAnimationFinished: () -> Unit,
    modifier: Modifier = Modifier
)
```
- **Dot Specs:**
  - Diameter: 14dp, Spacing: 16dp.
  - Inactive State: 1.5dp outline border (`outline` color).
  - Active/Filled State: Filled with Cyan Accent (`#4EA8DE`) with smooth scale bounce (1.0 -> 1.2 -> 1.0).
- **Error Animation:**
  - When `isError == true`, dots animate horizontally using an offset spring:
    `keyframes { durationMillis = 300; 0.dp at 0; (-12).dp at 50; 12.dp at 100; (-8).dp at 150; 8.dp at 200; 0.dp at 300 }`.
  - Triggers haptic error buzz and calls `onErrorAnimationFinished()`.

---

## 3. Vault & Media Management Components

### 3.1 `MediaThumbnailCard`
A 1:1 aspect ratio square card rendering an encrypted media item.

```kotlin
@Composable
fun MediaThumbnailCard(
    file: VaultFileItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
)
```
- **Rendering Pipeline:**
  - Utilizes Coil `AsyncImage` with custom `EncryptedThumbnailFetcher`.
  - Decrypts thumbnail payload directly into memory.
- **Overlays:**
  - If Video: Displays play icon + duration pill in bottom-right corner (`"03:12"`).
  - If Document: Displays PDF/DOC glyph and file size pill (`"1.4 MB"`).
  - If Selection Mode: Displays circular checkmark badge in top-right (checked or unchecked circle).

---

### 3.2 `SecureZoomableImageViewer`
A full-screen gesture-controlled image viewport rendering in-memory decrypted Bitmaps.

```kotlin
@Composable
fun SecureZoomableImageViewer(
    bitmap: ImageBitmap?,
    isLoading: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
)
```
- **Gesture Capabilities:**
  - **Pinch-to-Zoom:** Scales from 1.0x to 5.0x using `detectTransformGestures`.
  - **Double-Tap:** Toggles between 1.0x and 2.5x magnification centered on tap coordinates.
  - **Pan:** Translates coordinates when `scale > 1.0f`.
  - **Swipe-to-Dismiss:** Vertical drag downwards when `scale == 1.0f` triggers alpha fade and dismisses the viewer.

---

### 3.3 `VideoControlOverlay`
Translucent media player control overlay for video playback.

```kotlin
@Composable
fun VideoControlOverlay(
    isPlaying: Boolean,
    currentPositionMs: Long,
    totalDurationMs: Long,
    onPlayPauseToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onRewind10: () -> Unit,
    onForward10: () -> Unit,
    modifier: Modifier = Modifier
)
```
- **Components:**
  - Center: Rewind 10s button, Large circular Play/Pause button (64dp), Forward 10s button.
  - Bottom: Position text (`"01:23 / 04:50"`), Scrub slider with buffer indicators.
  - Auto-hide: Fades out after 3 seconds of user inactivity.

---

### 3.4 `MediaImportProgressDialog`
Modal progress indicator during AES-256-GCM chunked file import and thumbnail generation.

```kotlin
@Composable
fun MediaImportProgressDialog(
    currentFileIndex: Int,
    totalFiles: Int,
    currentFileName: String,
    currentFileProgress: Float,
    onCancel: () -> Unit
)
```
- **Content:**
  - Headline: *"Securing Files..."*
  - Subtitle: *"Encrypting item ${currentFileIndex} of ${totalFiles}: ${currentFileName}"*
  - LinearProgressIndicator for current file stream.
  - Informational notice: *"Encrypting with AES-256-GCM. Do not close the app."*

---

### 3.5 `DeleteOriginalConfirmationDialog`
Prompt displayed after successful encryption to request deletion of the original public file.

```kotlin
@Composable
fun DeleteOriginalConfirmationDialog(
    itemCount: Int,
    onConfirmDeleteOriginal: () -> Unit,
    onKeepOriginal: () -> Unit
)
```
- **Content:**
  - Title: *"Remove Original from Gallery?"*
  - Body: *"The ${itemCount} selected item(s) are now safely encrypted in Vault. Would you like to delete the unencrypted originals from your phone gallery?"*
  - Primary Action (Tonal): `"Delete Originals"`
  - Secondary Action (Text): `"Keep in Gallery"`

