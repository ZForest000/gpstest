# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew installDebug           # Install debug APK to connected device
./gradlew test                   # Run unit tests
./gradlew clean                  # Clean build artifacts
```

No lint configuration is currently set up. Tests use JUnit 4; there are no instrumented test runner customizations beyond the default `AndroidJUnitRunner`.

## Architecture

MVVM + Clean Architecture with three layers. Package: `com.example.gpstest`

**Data layer** (`data/`) — sources, local storage, validation:
- `source/GnssDataSource` → wraps Android `LocationManager` for GNSS satellite events
- `source/AGpsDataSource` → A-GPS injection via `LocationManager.sendExtraCommand`
- `source/AGpsDownloader` → OkHttp-based network download of A-GPS data
- `local/` → file I/O for A-GPS imports and history snapshots
- `validator/` → validates downloaded A-GPS data integrity

**Domain layer** (`domain/`) — models and repository interfaces:
- `model/` — data classes (`GnssData`, `SatelliteHistory`, `AGpsStatus`, `LocationInfo`)
- `repository/` — interfaces + implementations for Gnss, A-GPS, and history

**Presentation layer** (`ui/`, `viewmodel/`):
- `viewmodel/SatelliteViewModel` — manages GNSS data collection, signal history (60s per satellite), auto-snapshots
- `viewmodel/AGpsViewModel` — handles A-GPS download, injection, auto-update scheduling
- ViewModels are created via factory classes (no Hilt/Dagger — manual DI)
- UI state flows through `StateFlow` to Compose screens

## Navigation

Single-Activity (`MainActivity`) with Navigation Compose. Three bottom-tab screens:
- **SatelliteListScreen** — real-time satellite list with grouping (in-fix / visible / searching)
- **AGpsManagerScreen** — A-GPS download, import, injection, auto-update config
- **HistoryScreen** — browse saved satellite data snapshots

## Key Technical Details

- **Compose + Material3** for all UI; no XML layouts
- **Signal history tracking**: each satellite maintains a 60-second `SignalHistory` deque
- **Auto-snapshots**: WorkManager-backed periodic saves of satellite state
- **A-GPS flow**: download → validate → inject via `LocationManager.sendExtraCommand("delete_aiding_data" / "force_time_injection")`
- **Persistence**: DataStore (preferences), Kotlin Serialization (JSON snapshots)
- **Permissions**: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_LOCATION_EXTRA_COMMANDS`, `INTERNET`
- **Java 17** target; compileSdk/targetSdk 35; minSdk 24

## Conventions

- Repository pattern: interface in `domain/repository/`, implementation in same package
- Compose components are stateless; state hoisted to screen-level composables or ViewModels
- Coroutines for all async work; no RxJava
- Colors and theming centralized in `ui/theme/`
