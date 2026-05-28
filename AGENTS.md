# Repository Guidelines

## Project Overview

Android GPS debug tool for real-time GNSS satellite monitoring and A-GPS management. Supports GPS, GLONASS, Galileo, BeiDou, QZSS, and SBAS constellations. Features include a sky chart, DOP calculation, TTFF tracking, A-GPS XTRA download/injection, satellite history snapshots, and optional Shizuku/root diagnostics.

**Language**: Kotlin 2.1.0 | **UI**: Jetpack Compose (BOM 2024.10.01) + Material 3 | **Min SDK**: 24 | **Target SDK**: 35

## Architecture & Data Flow

**Pattern**: Clean MVVM with unidirectional data flow. Single Activity, no Fragments.

```
data layer ──→ domain layer ──→ presentation layer
(sources,       (repo interfaces,   (ViewModels +
 persistence,     models, utils)      Compose UI)
 validators)
```

**Key data pipelines**:

- **GNSS**: Android platform callbacks (GnssStatus + GnssMeasurements + Location + barometer) → `callbackFlow` merge in `GnssDataSourceImpl` → `Flow<GnssData>` → `GnssRepositoryImpl` → `SatelliteViewModel` → `StateFlow<SatelliteUiState>` → `collectAsState()` → UI
- **A-GPS**: User action / WorkManager trigger → `AGpsViewModel` → `AGpsRepositoryImpl` orchestrates download (OkHttp) → validate (`XtraDataValidator`) → inject (`LocationManager.sendExtraCommand`). Multi-URL fallback: user URL → 3 Qualcomm izatcloud defaults.
- **History**: `SatelliteViewModel.maybeSaveSnapshot()` (every 60s) → `SatelliteHistoryRepositoryImpl` → `SatelliteHistoryDataStore` (DataStore Preferences + kotlinx-serialization JSON) → `StateFlow` → `HistoryScreen`

**State management**: `MutableStateFlow` in ViewModels, exposed as read-only `StateFlow`. UI sealed states:
- `SatelliteUiState`: Loading → PermissionRequired → Success(...) → Error(message)
- `AGpsUiState`: Idle → Downloading → Injecting → Success(message) → Error(message)

**Dependency injection**: Manual DI via `ViewModelProvider.Factory`. No Hilt/Dagger/Koin. Dependencies constructed in `MainActivity`, passed to factory classes (`SatelliteViewModelFactory`, `AGpsViewModelFactory`). `AGpsUpdateWorker` rebuilds its own dependency chain.

**Error handling**: `Result<T>` throughout repositories and data sources. `try/catch` in ViewModel coroutine scopes with sealed error states. Multi-URL fallback in A-GPS downloads. WorkManager exponential backoff retry.

## Key Directories

```
app/src/main/java/com/example/gpstest/
├── MainActivity.kt              # Sole Activity, Compose entry, DI wiring, permissions, navigation
├── viewmodel/                   # SatelliteViewModel, AGpsViewModel
├── domain/
│   ├── model/                   # GnssData, GnssSatellite, Constellation, LocationInfo, GnssClockData, DopInfo, AGpsStatus, AGpsSettings, SatelliteHistory, SatelliteGroup
│   ├── repository/              # Interface + Impl pairs: GnssRepository, AGpsRepository, SatelliteHistoryRepository
│   └── util/                    # DopCalculator (4×4 Gauss-Jordan matrix inversion)
├── data/
│   ├── source/                  # GnssDataSource, AGpsDataSource, AGpsDownloader, ShizukuHelper (interface + Impl pairs)
│   ├── local/                   # SatelliteHistoryDataStore, AGpsSettingsStore, AGpsFileHandler/Impl
│   └── validator/               # XtraDataValidator
├── service/                     # AGpsUpdateWorker (WorkManager CoroutineWorker)
└── ui/
    ├── screens/                 # SatelliteListScreen, SkyChartScreen, AGpsManagerScreen, HistoryScreen, HelpScreen
    ├── components/              # 15 composables (SatelliteCard, SignalChart, LocationCard, DopCard, etc.)
    └── theme/                   # Color.kt, Type.kt, Theme.kt
```

## Development Commands

```bash
# Build
./gradlew assembleDebug          # Debug APK
./gradlew assembleRelease        # Release APK (no signing config — needs manual setup)

# Test
./gradlew test                   # Unit tests only (50 tests, domain layer only)
./gradlew testDebugUnitTest      # Explicit debug unit tests

# Lint
./gradlew ktlintCheck            # Run ktlint (1.5.0, android mode)

# Install
./gradlew installDebug           # Install debug APK on device
```

**CI** (`.github/workflows/ci.yml`): Runs on push/PR to `master` — JDK 21, `./gradlew test`, `assembleDebug`, `assembleRelease`, uploads APKs and test results.

## Code Conventions & Common Patterns

### Naming
- **Files/Classes**: PascalCase. Interfaces unsuffixed (`GnssDataSource`), implementations suffixed with `Impl` (`GnssDataSourceImpl`)
- **Methods**: camelCase, verb-first (`startListening()`, `downloadAndInject()`, `maybeSaveSnapshot()`)
- **Variables**: camelCase. Private backing fields prefixed `_` (`_uiState`, `_ttffState`). Constants `SCREAMING_SNAKE_CASE`
- **Composables**: PascalCase functions (`SatelliteCard`, `DopCard`). State via `remember { mutableStateOf() }`
- **Sealed interfaces**: `SatelliteUiState`, `AGpsUiState`, `TtffState` with nested data class/object variants

### Architecture patterns
- **Interface + Impl** throughout: every DataSource, Repository, Downloader, FileHandler follows this pattern
- **callbackFlow** to convert Android platform callbacks into Kotlin Flow
- **viewModelScope.launch** for all ViewModel async work (auto-cancelled in `onCleared()`)
- **Dispatchers.IO** for file I/O and network operations
- **60s ring buffer** per satellite for signal history (`Map<String, List<SignalReading>>`)

### Language
- Comments and documentation: mixed Chinese (中文) and English. Chinese for domain knowledge, architecture decisions, and user-facing docs
- User-facing strings: Chinese via Android string resources

## Important Files

| File | Purpose |
|------|---------|
| `app/src/main/java/com/example/gpstest/MainActivity.kt` | Entry point, navigation host, DI wiring, permission handling |
| `app/src/main/java/com/example/gpstest/viewmodel/SatelliteViewModel.kt` | GNSS data collection, signal history, TTFF, auto-snapshots, DOP |
| `app/src/main/java/com/example/gpstest/viewmodel/AGpsViewModel.kt` | A-GPS download/inject lifecycle, WorkManager scheduling |
| `app/src/main/java/com/example/gpstest/data/source/GnssDataSourceImpl.kt` | Core sensor fusion — merges 4 Android callbacks into single Flow |
| `app/src/main/java/com/example/gpstest/domain/util/DopCalculator.kt` | DOP matrix math (PDOP/HDOP/VDOP) |
| `app/src/main/java/com/example/gpstest/domain/repository/AGpsRepositoryImpl.kt` | A-GPS orchestrator with multi-URL fallback and time-decay status |
| `app/build.gradle.kts` | App module build config, all dependencies |
| `app/proguard-rules.pro` | Keeps GNSS reflection APIs |

## Runtime/Tooling Preferences

- **JDK**: 17 (source/target compatibility). JDK 21 used as build JDK locally and on CI
- **Gradle**: 8.9 with configuration cache, parallel builds, build caching enabled
- **Kotlin code style**: `official`
- **Linting**: ktlint 1.5.0 via `org.jlleitschuh.gradle.ktlint` plugin (12.1.2), android mode
- **Gradle properties**: `org.gradle.java.home=C:/Program Files/Java/jdk-21` (local only; CI removes this via `sed`)
- **No product flavors** — only `debug` and `release` build types
- **Release minification disabled** (`isMinifyEnabled = false`)

## Testing & QA

**Framework**: JUnit 4.13.2. No mocking libraries, no Robolectric, no Truth.

**Scope**: 50 unit tests covering domain layer only:
- `domain/model/`: DopInfo (10 tests), SatelliteHistory (9), Constellation (9), GnssData (8), GnssClockData (7)
- `domain/util/`: DopCalculator (7 tests)

**Test conventions**:
- File naming: `<SourceClass>Test.kt` — 1:1 with source file
- Package mirroring: test packages match source packages exactly
- Method naming: backtick descriptive names — `` `quality is EXCELLENT when pdop less than 1` ``
- Test style: direct instantiation of data classes, assert computed properties. No test base classes, no `@Before`/`@After`
- Fixtures: private `makeSatellite()` helper functions per test class

**Not tested**: ViewModels, DataSources, Repositories (except domain logic), UI/screens, services, AGpsUpdateWorker, XtraDataValidator.
