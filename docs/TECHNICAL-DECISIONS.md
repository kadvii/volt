# Technical Decisions Log — Vault (Phase 01)

## ADR-01: Kotlin 2.0 with Jetpack Compose & Material 3
- **Decision:** Use Kotlin 2.0.21 with the modern Compose compiler Gradle plugin (`org.jetbrains.kotlin.plugin.compose`) and Material 3 BOM.
- **Rationale:** Delivers compile-time stability, strong skipping mode optimizations, dynamic color theming, and edge-to-edge support out of the box.

## ADR-02: Local / Offline Storage Strategy
- **Decision:** The application operates strictly offline without requiring user accounts or external network sync.
- **Rationale:** Zero network footprint guarantees zero remote data leakage, zero telemetry tracking, and total privacy for the user.

## ADR-03: Minimal Permission Architecture
- **Decision:** `AndroidManifest.xml` requests zero storage, media, camera, or network permissions.
- **Rationale:** An authentic Notes application has no reason to request storage permissions on startup. Media import in Phase 05 will leverage the Android Photo Picker (`PickMultipleVisualMedia`), which does not require runtime permissions.

## ADR-04: Non-ASCII Windows Path Handling
- **Decision:** Enable `android.overridePathCheck=true` in `gradle.properties` and resolve short 8.3 paths in `gradlew.bat`.
- **Rationale:** Ensures reliable compilation on Windows workstations where the workspace directory path contains Unicode/Arabic characters (`فولت`).

