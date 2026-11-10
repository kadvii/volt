# Design System Specification — Vault

## 1. Design Philosophy & Dual-Surface Concept

The visual design of **Vault** is anchored on the principle of **Inconspicuous Elegance**. The application must exhibit zero "security telltales"—no padlocks, fingerprint glyphs, shields, or biometric badges anywhere on the public surface. To an observer or casual user, the application is indistinguishable from a modern, premium Android notes app (such as Google Keep or Apple Notes).

When transitioned into the hidden Vault layer, the UI retains the same refined Material 3 aesthetic, but introduces specialized media-centric dark surfaces, high-density media grids, and full-screen immersive media viewports.

---

## 2. Color System & Palettes (Material 3)

The design system uses standard Material 3 color roles with full dynamic theming (Material You) on Android 12+ (API 31+) and curated fallback tonal palettes for Android 10–11.

### 2.1 Public Notes Surface — Color Palette

#### Light Theme (Default Daylight)
| Token | Hex Value | Role / Usage |
| :--- | :--- | :--- |
| `md_theme_light_primary` | `#2D6A4F` | Accent actions, active tabs, FAB |
| `md_theme_light_onPrimary` | `#FFFFFF` | Text/icons on primary |
| `md_theme_light_surface` | `#FCFDF7` | App background, top app bar |
| `md_theme_light_onSurface` | `#1A1C19` | High-contrast note titles and body |
| `md_theme_light_surfaceVariant` | `#DFE4D7` | Note card borders, search bar surface |
| `md_theme_light_onSurfaceVariant` | `#43483F` | Secondary timestamps, note preview body |
| `md_theme_light_outline` | `#73796E` | Dividers, subtle borders |

#### Dark Theme (Stealth & Night)
| Token | Hex Value | Role / Usage |
| :--- | :--- | :--- |
| `md_theme_dark_primary` | `#95D5B2` | Accent actions, active category chip |
| `md_theme_dark_onPrimary` | `#003923` | High-contrast text on primary |
| `md_theme_dark_surface` | `#111411` | Deep background |
| `md_theme_dark_onSurface` | `#E2E3DD` | Primary text |
| `md_theme_dark_surfaceVariant` | `#2B312A` | Card backgrounds, search input |
| `md_theme_dark_onSurfaceVariant` | `#C3C8BC` | Secondary labels |
| `md_theme_dark_outline` | `#8D9387` | Dividers |

### 2.2 Pastel Note Card Accents (Public Surface)
To reinforce authenticity, notes can be assigned subtle pastel background tints:
- **Default (Neutral):** Surface Variant (`#F4F4F0` Light / `#1C1F1B` Dark)
- **Amber / Ideas:** `#FFF3C4` Light / `#362F18` Dark
- **Mint / Personal:** `#D8F3DC` Light / `#183324` Dark
- **Sky / Work:** `#D0E8FF` Light / `#172A3A` Dark
- **Rose / Important:** `#FFD6E0` Light / `#381A22` Dark
- **Lavender / Misc:** `#E8DFF5` Light / `#281E38` Dark

### 2.3 Hidden Vault Surface — Media-First Surface
When inside the Vault, media presentation requires deep contrast:
- **Vault Surface (OLED Black / Slate):** `#0A0C0E`
- **Vault Card Surface:** `#14181D`
- **Vault Highlight Accent:** `#4EA8DE` (Calm Cyan/Blue — distinct from security red/amber)
- **Media Viewer Viewport:** `#000000` (Pure black for full immersion)

---

## 3. Typography Scale

Vault adopts the standard Material 3 type hierarchy, utilizing system sans-serif (Google Sans / Roboto) with strict weight discipline.

| Style Role | Font Size | Line Height | Weight | Tracking | Usage |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Display Large** | 57sp | 64sp | Regular (400) | -0.25sp | Large counters, splash |
| **Headline Large** | 32sp | 40sp | SemiBold (600) | 0sp | Screen titles ("Notes", "Albums") |
| **Headline Medium** | 28sp | 36sp | Medium (500) | 0sp | Dialog headings, section titles |
| **Title Medium** | 16sp | 24sp | SemiBold (600) | +0.15sp | Note titles, media card titles |
| **Body Large** | 16sp | 24sp | Regular (400) | +0.5sp | Note body editor, document viewer |
| **Body Medium** | 14sp | 20sp | Regular (400) | +0.25sp | Search results preview, descriptions |
| **Label Large** | 14sp | 20sp | Medium (500) | +0.1sp | Buttons, category chips, tabs |
| **Label Small** | 11sp | 16sp | Regular (400) | +0.5sp | File sizes, timestamps, durations |

---

## 4. Spacing, Elevation & Corner Radii

### 4.1 Base Spacing Grid (8dp Base, 4dp Sub-grid)
- `space_xxs`: 2dp (Micro alignments)
- `space_xs`: 4dp (Badge padding, chip gap)
- `space_s`: 8dp (Inner element padding)
- `space_m`: 16dp (Standard screen margin, card padding)
- `space_l`: 24dp (Section separation)
- `space_xl`: 32dp (Hero spacing)
- `space_xxl`: 48dp (Top of authentication screens)

### 4.2 Corner Radii Tokens
- `radius_xs`: 4dp (Thumbnails, badges)
- `radius_s`: 8dp (Media grid items, text field containers)
- `radius_m`: 12dp (Note cards, dialogs)
- `radius_l`: 16dp (Category chips, bottom sheet top corners)
- `radius_full`: 999dp (Circular action buttons, stealth keypad buttons)

### 4.3 Surface Elevations
In accordance with Material 3 specifications, elevations are depicted via tonal color mapping rather than harsh drop shadows:
- `Elevation 0`: Pure surface (Background)
- `Elevation 1`: Search bar, note cards (+5% primary tonal overlay)
- `Elevation 2`: Bottom App Bar, Top App Bar on scroll (+8% overlay)
- `Elevation 3`: Dialogs, Bottom Sheets (+11% overlay)

---

## 5. Iconography & Visual Language

- **Icon Family:** Material Symbols (Rounded) or Lucide Android vectors.
- **Stroke Width:** Regular (2dp stroke) for clarity at standard 24dp size.
- **Rule of Disguise:**
  - On the Notes surface: only standard note icons are used (`search`, `edit`, `folder`, `push_pin`, `more_vert`, `palette`, `delete`).
  - Absolute exclusion on public screens: No lock (`lock`), key (`vpn_key`), shield (`security`), or eyeball (`visibility_off`) icons.

---

## 6. Motion & Animation Tokens

### 6.1 Easing Curves
- **Emphasized Decelerate (`CubicBezier(0.05f, 0.7f, 0.1f, 1.0f)`):** Used for incoming elements, bottom sheets, and full-screen viewer entry.
- **Emphasized Accelerate (`CubicBezier(0.3f, 0.0f, 0.8f, 0.15f)`):** Used for exiting dialogs and dismiss gestures.
- **Standard (`CubicBezier(0.2f, 0.0f, 0.0f, 1.0f)`):** Smooth color transitions and note card expansions.

### 6.2 Durations
- `duration_short`: 150ms (Button press, chip select)
- `duration_medium`: 250ms (Search expansion, dialog fade)
- `duration_long`: 350ms (Screen transitions, sheet expansions)
- `stealth_hold_time`: 2000ms (Secret gesture detection duration)

### 6.3 Specialized Stealth Animations
- **Long-Press Tick:** At exactly 2000ms of holding the "Notes" title, a subtle 1.05x pulse scale on the title text occurs concurrently with a 25ms haptic tick, providing discrete confirmation to the user without drawing outside attention.
- **Keypad Error Shake:** On entering an incorrect PIN, the 4 PIN dots oscillate horizontally by ±12dp across 3 cycles over 300ms, followed by automatic digit clearing.

