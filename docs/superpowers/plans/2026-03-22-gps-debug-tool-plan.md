# Android GPS调试工具 - 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-step. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标:** 创建一个Android原生应用，用于调试GPS功能，展示完整GNSS卫星原始测量数据，并支持A-GPS数据管理。

**架构:** 采用MVVM + Clean Architecture，分层为UI层、ViewModel层、Repository层和数据层。使用Jetpack Compose构建UI，LocationManager获取GNSS数据，WorkManager处理后台任务。

**技术栈:** Kotlin, Jetpack Compose, Material3, Coroutines, Flow, WorkManager, DataStore, JUnit

---

## 文件结构概览

在开始任务前，了解将要创建的文件结构：

```
app/
├── build.gradle.kts                    # Gradle构建配置
├── src/main/
│   ├── AndroidManifest.xml             # 权限声明
│   ├── java/com/example/gpstest/
│   │   ├── GpstestApplication.kt       # Application类
│   │   ├── MainActivity.kt             # 主Activity
│   │   ├── domain/model/               # 数据模型
│   │   │   ├── GnssSatellite.kt
│   │   │   ├── Constellation.kt
│   │   │   ├── AGpsStatus.kt
│   │   │   └── SatelliteGroup.kt
│   │   ├── data/source/               # 数据源
│   │   │   ├── GnssDataSource.kt      # 接口
│   │   │   └── GnssDataSourceImpl.kt  # 实现
│   │   ├── data/mapper/               # 数据映射
│   │   │   └── GnssMeasurementMapper.kt
│   │   ├── domain/repository/         # 仓库接口
│   │   │   ├── GnssRepository.kt
│   │   │   └── GnssRepositoryImpl.kt
│   │   ├── viewmodel/                 # ViewModel
│   │   │   └── SatelliteViewModel.kt
│   │   ├── ui/                        # UI层
│   │   │   ├── theme/
│   │   │   │   ├── Color.kt
│   │   │   │   ├── Theme.kt
│   │   │   │   └── Type.kt
│   │   │   ├── components/
│   │   │   │   ├── StatBar.kt
│   │   │   │   ├── SatelliteCard.kt
│   │   │   │   └── SatelliteDetailSheet.kt
│   │   │   └── screens/
│   │   │       └── satellite/
│   │   │           └── SatelliteListScreen.kt
│   │   └── util/
│   │       └── ConstellationExt.kt
│   └── res/
│       └── values/
│           └── strings.xml
settings.gradle.kts                     # Gradle设置
build.gradle.kts (project)              # 项目级Gradle配置
```

---

## Phase 1: 基础框架

### Task 1.1: 创建项目结构和Gradle配置

**文件:**
- 创建: `settings.gradle.kts`
- 创建: `build.gradle.kts` (project)
- 创建: `app/build.gradle.kts`

- [ ] **Step 1: 创建项目级settings.gradle.kts**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "GpsTest"
include(":app")
```

- [ ] **Step 2: 创建项目级build.gradle.kts**

```kotlin
// Top-level build file
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
}
```

- [ ] **Step 3: 创建app/build.gradle.kts**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.gpstest"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.gpstest"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Jetpack Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.9.3")

    // ViewModel & Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
```

- [ ] **Step 4: 创建proguard-rules.pro**

```proguard
# Keep GNSS APIs
-keep class android.location.GnssMeasurement { *; }
-keep class android.location.GnssMeasurementsEvent { *; }
```

- [ ] **Step 5: 提交**

```bash
git add settings.gradle.kts build.gradle.kts app/build.gradle.kts app/proguard-rules.pro
git commit -m "feat: set up project structure and Gradle configuration"
```

---

### Task 1.2: 配置AndroidManifest和权限

**文件:**
- 创建: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: 创建AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- 位置权限 -->
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:name=".GpstestApplication"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Gpstest">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.Gpstest">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "feat: add AndroidManifest with location permissions"
```

---

### Task 1.3: 创建Application类和MainActivity

**文件:**
- 创建: `app/src/main/java/com/example/gpstest/GpstestApplication.kt`
- 创建: `app/src/main/java/com/example/gpstest/MainActivity.kt`

- [ ] **Step 1: 创建GpstestApplication.kt**

```kotlin
package com.example.gpstest

import android.app.Application

class GpstestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
```

- [ ] **Step 2: 创建MainActivity.kt**

```kotlin
package com.example.gpstest

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.example.gpstest.ui.theme.Theme

class MainActivity : ComponentActivity() {

    private val viewModel: SatelliteViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            viewModel.startListening()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkLocationPermission()

        setContent {
            Theme {
                Surface {
                    SatelliteListScreen(
                        viewModel = viewModel,
                        onRequestPermission = { requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
                    )
                }
            }
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.startListening()
        }
    }
}
```

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/example/gpstest/GpstestApplication.kt app/src/main/java/com/example/gpstest/MainActivity.kt
git commit -m "feat: add Application class and MainActivity"
```

---

### Task 1.4: 创建UI主题

**文件:**
- 创建: `app/src/main/java/com/example/gpstest/ui/theme/Color.kt`
- 创建: `app/src/main/java/com/example/gpstest/ui/theme/Theme.kt`
- 创建: `app/src/main/java/com/example/gpstest/ui/theme/Type.kt`

- [ ] **Step 1: 创建Color.kt**

```kotlin
package com.example.gpstest.ui.theme

import androidx.compose.ui.graphics.Color

val Green80 = Color(0xFF4CAF50)
val GreenGrey80 = Color(0xFFBCBDB4)
val Green40 = Color(0xFF388E3C)
val GreenGrey40 = Color(0xFF7D7D7D)

// Signal strength colors
val SignalStrong = Color(0xFF4CAF50)   // > 35 dB-Hz
val SignalMedium = Color(0xFFFFC107)   // 25-35 dB-Hz
val SignalWeak = Color(0xFFF44336)     // < 25 dB-Hz

// Constellation colors
val GpsColor = Color(0xFF2196F3)
val GlonassColor = Color(0xFFFF9800)
val GalileoColor = Color(0xFF9C27B0)
val BeidouColor = Color(0xFFE91E63)
```

- [ ] **Step 2: 创建Type.kt**

```kotlin
package com.example.gpstest.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
```

- [ ] **Step 3: 创建Theme.kt**

```kotlin
package com.example.gpstest.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Green80,
    secondary = GreenGrey80,
    tertiary = Green40
)

private val LightColorScheme = lightColorScheme(
    primary = Green40,
    secondary = GreenGrey40,
    tertiary = Green80
)

@Composable
fun Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/example/gpstest/ui/theme/
git commit -m "feat: add UI theme with colors and typography"
```

---

### Task 1.5: 创建字符串资源

**文件:**
- 创建: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: 创建strings.xml**

```xml
<resources>
    <string name="app_name">GPS Debug Tool</string>

    <!-- Satellite Screen -->
    <string name="satellite_list_title">GNSS Satellites</string>
    <string name="signal_stats">Signal Statistics</string>
    <string name="used_in_fix">Used in Fix</string>
    <string name="visible_only">Visible Only</string>
    <string name="searching">Searching</string>
    <string name="signal_strength_format">%d dB-Hz</string>
    <string name="azimuth_elevation_format">↑ %d° → %d°</string>
    <string name="permission_required">Location permission required</string>
    <string name="grant_permission">Grant Permission</string>

    <!-- Satellite Details -->
    <string name="satellite_details">Satellite Details</string>
    <string name="basic_info">Basic Information</string>
    <string name="constellation_type">Constellation Type</string>
    <string name="satellite_id">Satellite ID (PRN)</string>
    <string name="signal_strength">Signal Strength</string>
    <string name="azimuth">Azimuth</string>
    <string name="elevation">Elevation</string>
    <string name="status">Status</string>
    <string name="used_in_fix_yes">Used in fix ✓</string>
    <string name="used_in_fix_no">Not used in fix</string>
    <string name="raw_measurement">Raw Measurement Data</string>
    <string name="carrier_frequency">Carrier Frequency</string>
    <string name="carrier_cycles">Carrier Cycles</string>
    <string name="doppler_shift">Doppler Shift</string>
    <string name="timestamp">Timestamp</string>
    <string name="has_ephemeris">Has Ephemeris</string>
    <string name="has_almanac">Has Almanac</string>
    <string name="yes">Yes</string>
    <string name="no">No</string>
    <string name="signal_chart">Signal Chart (Last 60 seconds)</string>

    <!-- Constellation Names -->
    <string name="constellation_gps">GPS</string>
    <string name="constellation_glonass">GLONASS</string>
    <string name="constellation_galileo">Galileo</string>
    <string name="constellation_beidou">BeiDou</string>
    <string name="constellation_qzss">QZSS</string>
    <string name="constellation_unknown">Unknown</string>
</resources>
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "feat: add string resources"
```

---

## Phase 2: GNSS数据核心

### Task 2.1: 创建数据模型

**文件:**
- 创建: `app/src/main/java/com/example/gpstest/domain/model/Constellation.kt`
- 创建: `app/src/main/java/com/example/gpstest/domain/model/SatelliteGroup.kt`
- 创建: `app/src/main/java/com/example/gpstest/domain/model/GnssSatellite.kt`

- [ ] **Step 1: 创建Constellation.kt**

```kotlin
package com.example.gpstest.domain.model

enum class Constellation {
    GPS,
    GLONASS,
    GALILEO,
    BEIDOU,
    QZSS,
    UNKNOWN;

    companion object {
        fun fromConstellationType(type: Int): Constellation {
            return when (type) {
                1 -> GPS
                2 -> SBAS
                3 -> GLONASS
                4 -> QZSS
                5 -> BEIDOU
                6 -> GALILEO
                else -> UNKNOWN
            }
        }
    }
}

// Note: SBAS is a special case, mapped to UNKNOWN for now
private const val SBAS = UNKNOWN
```

- [ ] **Step 2: 创建SatelliteGroup.kt**

```kotlin
package com.example.gpstest.domain.model

enum class SatelliteGroup {
    USED_IN_FIX,
    VISIBLE_ONLY,
    SEARCHING
}
```

- [ ] **Step 3: 创建GnssSatellite.kt**

```kotlin
package com.example.gpstest.domain.model

data class GnssSatellite(
    val svid: Int,
    val constellation: Constellation,
    val cn0DbHz: Float,
    val azimuthDegrees: Float,
    val elevationDegrees: Float,
    val hasAlmanac: Boolean,
    val hasEphemeris: Boolean,
    val usedInFix: Boolean,
    val carrierFrequencyHz: Float?,
    val carrierCycles: Float?,
    val dopplerShiftHz: Float?,
    val timeNanos: Long
) {
    val group: SatelliteGroup
        get() = when {
            usedInFix -> SatelliteGroup.USED_IN_FIX
            cn0DbHz > 0 -> SatelliteGroup.VISIBLE_ONLY
            else -> SatelliteGroup.SEARCHING
        }

    val signalStrength: SignalStrength
        get() = when {
            cn0DbHz >= 35f -> SignalStrength.STRONG
            cn0DbHz >= 25f -> SignalStrength.MEDIUM
            else -> SignalStrength.WEAK
        }
}

enum class SignalStrength {
    STRONG,
    MEDIUM,
    WEAK
}
```

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/example/gpstest/domain/model/
git commit -m "feat: add domain models for GNSS satellite data"
```

---

### Task 2.2: 创建GnssDataSource接口和实现

**文件:**
- 创建: `app/src/main/java/com/example/gpstest/data/source/GnssDataSource.kt`
- 创建: `app/src/main/java/com/example/gpstest/data/source/GnssDataSourceImpl.kt`

- [ ] **Step 1: 创建GnssDataSource.kt接口**

```kotlin
package com.example.gpstest.data.source

import com.example.gpstest.domain.model.GnssSatellite
import kotlinx.coroutines.flow.Flow

interface GnssDataSource {
    fun startListening(): Flow<List<GnssSatellite>>
    fun stopListening()
    fun isSupported(): Boolean
}
```

- [ ] **Step 2: 创建GnssDataSourceImpl.kt**

```kotlin
package com.example.gpstest.data.source

import android.content.Context
import android.location.GnssMeasurementsEvent
import android.location.LocationManager
import com.example.gpstest.domain.model.Constellation
import com.example.gpstest.domain.model.GnssSatellite
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class GnssDataSourceImpl(
    private val context: Context
) : GnssDataSource {

    private val locationManager: LocationManager?
        get() = context.getSystemService(LocationManager::class.java)

    override fun startListening(): Flow<List<GnssSatellite>> = callbackFlow {
        val callback = object : GnssMeasurementsEvent.Callback() {
            override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent) {
                val satellites = event.measurements.map { measurement ->
                    GnssSatellite(
                        svid = measurement.svid,
                        constellation = Constellation.fromConstellationType(
                            measurement.constellationType
                        ),
                        cn0DbHz = measurement.cn0DbHz,
                        azimuthDegrees = measurement.azimuthDegrees,
                        elevationDegrees = measurement.elevationDegrees,
                        hasAlmanac = measurement.hasAlmanac(),
                        hasEphemeris = measurement.hasEphemeris(),
                        usedInFix = measurement.usedInFix(),
                        carrierFrequencyHz = measurement.carrierFrequencyHz,
                        carrierCycles = measurement.carrierCycles,
                        dopplerShiftHz = measurement.dopplerShiftHz,
                        timeNanos = measurement.timeNanos
                    )
                }
                trySend(satellites)
            }

            override fun onStatusChanged(status: Int) {
                // Status can be: STATUS_NOT_SUPPORTED (0), STATUS_READY (1), STATUS_LOCATION_DISABLED (2)
                if (status == GnssMeasurementsEvent.Callback.STATUS_NOT_SUPPORTED) {
                    close()
                }
            }
        }

        val registered = locationManager?.registerGnssMeasurementsCallback(
            callback,
            context.mainExecutor
        ) ?: false

        if (!registered) {
            close()
            awaitClose()
        }

        awaitClose {
            locationManager?.unregisterGnssMeasurementsCallback(callback)
        }
    }

    override fun stopListening() {
        // Flow is automatically stopped when collector is cancelled
    }

    override fun isSupported(): Boolean {
        return locationManager?.getGnssHardwareCapabilities()?.let { true } ?: false
    }
}
```

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/example/gpstest/data/source/
git commit -m "feat: implement GnssDataSource with LocationManager callback"
```

---

### Task 2.3: 创建Repository

**文件:**
- 创建: `app/src/main/java/com/example/gpstest/domain/repository/GnssRepository.kt`
- 创建: `app/src/main/java/com/example/gpstest/domain/repository/GnssRepositoryImpl.kt`

- [ ] **Step 1: 创建GnssRepository.kt接口**

```kotlin
package com.example.gpstest.domain.repository

import com.example.gpstest.domain.model.GnssSatellite
import kotlinx.coroutines.flow.Flow

interface GnssRepository {
    fun getSatellites(): Flow<List<GnssSatellite>>
    suspend fun isGnssSupported(): Boolean
}
```

- [ ] **Step 2: 创建GnssRepositoryImpl.kt**

```kotlin
package com.example.gpstest.domain.repository

import com.example.gpstest.data.source.GnssDataSource
import com.example.gpstest.domain.model.GnssSatellite
import kotlinx.coroutines.flow.Flow

class GnssRepositoryImpl(
    private val dataSource: GnssDataSource
) : GnssRepository {

    override fun getSatellites(): Flow<List<GnssSatellite>> {
        return dataSource.startListening()
    }

    override suspend fun isGnssSupported(): Boolean {
        return dataSource.isSupported()
    }
}
```

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/example/gpstest/domain/repository/
git commit -m "feat: add GnssRepository layer"
```

---

### Task 2.4: 创建SatelliteViewModel

**文件:**
- 创建: `app/src/main/java/com/example/gpstest/viewmodel/SatelliteViewModel.kt`

- [ ] **Step 1: 创建SatelliteViewModel.kt**

```kotlin
package com.example.gpstest.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gpstest.domain.model.GnssSatellite
import com.example.gpstest.domain.model.SatelliteGroup
import com.example.gpstest.domain.repository.GnssRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SatelliteViewModel(
    application: Application,
    private val repository: GnssRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<SatelliteUiState>(SatelliteUiState.Loading)
    val uiState: StateFlow<SatelliteUiState> = _uiState.asStateFlow()

    private val _selectedSatellite = MutableStateFlow<GnssSatellite?>(null)
    val selectedSatellite: StateFlow<GnssSatellite?> = _selectedSatellite.asStateFlow()

    fun startListening() {
        viewModelScope.launch {
            try {
                repository.getSatellites().collect { satellites ->
                    val grouped = satellites.groupBy { it.group }
                    _uiState.value = SatelliteUiState.Success(
                        usedInFix = grouped[SatelliteGroup.USED_IN_FIX].orEmpty(),
                        visibleOnly = grouped[SatelliteGroup.VISIBLE_ONLY].orEmpty(),
                        searching = grouped[SatelliteGroup.SEARCHING].orEmpty(),
                        totalCount = satellites.size
                    )
                }
            } catch (e: Exception) {
                _uiState.value = SatelliteUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun selectSatellite(satellite: GnssSatellite) {
        _selectedSatellite.value = satellite
    }

    fun clearSelection() {
        _selectedSatellite.value = null
    }
}

sealed interface SatelliteUiState {
    data object Loading : SatelliteUiState
    data class Success(
        val usedInFix: List<GnssSatellite>,
        val visibleOnly: List<GnssSatellite>,
        val searching: List<GnssSatellite>,
        val totalCount: Int
    ) : SatelliteUiState
    data class Error(val message: String) : SatelliteUiState
}
```

- [ ] **Step 2: 更新MainActivity以使用ViewModelFactory**

编辑 `app/src/main/java/com/example/gpstest/MainActivity.kt`，添加ViewModelFactory：

```kotlin
// 在MainActivity中添加factory
private val viewModel: SatelliteViewModel by viewModels {
    val application = application as GpstestApplication
    val dataSource = GnssDataSourceImpl(application)
    val repository = GnssRepositoryImpl(dataSource)
    SatelliteViewModelFactory(application, repository)
}

// 创建Factory类（在文件底部添加）
class SatelliteViewModelFactory(
    private val application: Application,
    private val repository: GnssRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SatelliteViewModel::class.java)) {
            return SatelliteViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
```

更新imports：
```kotlin
import androidx.lifecycle.ViewModelProvider
import com.example.gpstest.data.source.GnssDataSourceImpl
import com.example.gpstest.domain.repository.GnssRepositoryImpl
```

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/example/gpstest/viewmodel/SatelliteViewModel.kt
git add app/src/main/java/com/example/gpstest/MainActivity.kt
git commit -m "feat: implement SatelliteViewModel with StateFlow"
```

---

## Phase 3: UI实现

### Task 3.1: 创建StatBar组件

**文件:**
- 创建: `app/src/main/java/com/example/gpstest/ui/components/StatBar.kt`

- [ ] **Step 1: 创建StatBar.kt**

```kotlin
package com.example.gpstest.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.gpstest.R

@Composable
fun StatBar(
    usedInFixCount: Int,
    visibleCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.signal_stats),
            style = MaterialTheme.typography.titleMedium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatItem(stringResource(R.string.used_in_fix), usedInFixCount)
            Spacer(modifier = Modifier.weight(1f))
            StatItem("Visible", visibleCount)
            Spacer(modifier = Modifier.weight(1f))
            StatItem("Total", totalCount)
        }
    }
}

@Composable
private fun StatItem(label: String, count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "$label: $count",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/example/gpstest/ui/components/StatBar.kt
git commit -m "feat: add StatBar component for satellite statistics"
```

---

### Task 3.2: 创建SatelliteCard组件

**文件:**
- 创建: `app/src/main/java/com/example/gpstest/ui/components/SatelliteCard.kt`

- [ ] **Step 1: 创建SatelliteCard.kt**

```kotlin
package com.example.gpstest.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gpstest.R
import com.example.gpstest.domain.model.Constellation
import com.example.gpstest.domain.model.GnssSatellite
import com.example.gpstest.ui.theme.BeidouColor
import com.example.gpstest.ui.theme.GalileoColor
import com.example.gpstest.ui.theme.GlonassColor
import com.example.gpstest.ui.theme.GpsColor
import com.example.gpstest.ui.theme.SignalMedium
import com.example.gpstest.ui.theme.SignalStrong
import com.example.gpstest.ui.theme.SignalWeak

@Composable
fun SatelliteCard(
    satellite: GnssSatellite,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left side: Constellation indicator and ID
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConstellationIndicator(
                constellation = satellite.constellation,
                usedInFix = satellite.usedInFix
            )
            Column {
                Text(
                    text = "${getConstellationName(satellite.constellation)}-${satellite.svid}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(
                        R.string.signal_strength_format,
                        satellite.cn0DbHz.toInt()
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = getSignalColor(satellite.cn0DbHz)
                )
            }
        }

        // Right side: Azimuth and Elevation
        Text(
            text = stringResource(
                R.string.azimuth_elevation_format,
                satellite.elevationDegrees.toInt(),
                satellite.azimuthDegrees.toInt()
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ConstellationIndicator(
    constellation: Constellation,
    usedInFix: Boolean
) {
    val color = when (constellation) {
        Constellation.GPS -> GpsColor
        Constellation.GLONASS -> GlonassColor
        Constellation.GALILEO -> GalileoColor
        Constellation.BEIDOU -> BeidouColor
        else -> Color.Gray
    }

    val indicator = if (usedInFix) "🟢" else "⚪"

    Text(
        text = indicator,
        style = MaterialTheme.typography.titleMedium
    )
}

private fun getConstellationName(constellation: Constellation): String {
    return when (constellation) {
        Constellation.GPS -> "GPS"
        Constellation.GLONASS -> "GLN"
        Constellation.GALILEO -> "GAL"
        Constellation.BEIDOU -> "BDS"
        Constellation.QZSS -> "QZS"
        Constellation.UNKNOWN -> "UNK"
    }
}

private fun getSignalColor(cn0: Float): Color {
    return when {
        cn0 >= 35f -> SignalStrong
        cn0 >= 25f -> SignalMedium
        else -> SignalWeak
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/example/gpstest/ui/components/SatelliteCard.kt
git commit -m "feat: add SatelliteCard component"
```

---

### Task 3.3: 创建SatelliteDetailSheet组件

**文件:**
- 创建: `app/src/main/java/com/example/gpstest/ui/components/SatelliteDetailSheet.kt`

- [ ] **Step 1: 创建SatelliteDetailSheet.kt**

```kotlin
package com.example.gpstest.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.gpstest.R
import com.example.gpstest.domain.model.GnssSatellite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SatelliteDetailSheet(
    satellite: GnssSatellite,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(
                R.string.satellite_details,
                "${getConstellationName(satellite.constellation)}-${satellite.svid}"
            ),
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Basic Info Section
        DetailSection(stringResource(R.string.basic_info)) {
            DetailRow(
                stringResource(R.string.constellation_type),
                getConstellationFullName(satellite.constellation)
            )
            DetailRow(
                stringResource(R.string.satellite_id),
                "${satellite.svid}"
            )
            DetailRow(
                stringResource(R.string.signal_strength),
                "${satellite.cn0DbHz.toInt()} dB-Hz"
            )
            DetailRow(
                stringResource(R.string.azimuth),
                "${satellite.azimuthDegrees.toInt()}°"
            )
            DetailRow(
                stringResource(R.string.elevation),
                "${satellite.elevationDegrees.toInt()}°"
            )
            DetailRow(
                stringResource(R.string.status),
                if (satellite.usedInFix) stringResource(R.string.used_in_fix_yes)
                else stringResource(R.string.used_in_fix_no)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Raw Measurement Section
        DetailSection(stringResource(R.string.raw_measurement)) {
            DetailRow(
                stringResource(R.string.carrier_frequency),
                satellite.carrierFrequencyHz?.let { "%.2f MHz".format(it / 1_000_000) }
                    ?: "N/A"
            )
            DetailRow(
                stringResource(R.string.carrier_cycles),
                satellite.carrierCycles?.toString() ?: "N/A"
            )
            DetailRow(
                stringResource(R.string.doppler_shift),
                satellite.dopplerShiftHz?.let { "%d Hz".format(it.toInt()) } ?: "N/A"
            )
            DetailRow(
                stringResource(R.string.timestamp),
                formatTimestamp(satellite.timeNanos)
            )
            DetailRow(
                stringResource(R.string.has_ephemeris),
                if (satellite.hasEphemeris) stringResource(R.string.yes) else stringResource(R.string.no)
            )
            DetailRow(
                stringResource(R.string.has_almanac),
                if (satellite.hasAlmanac) stringResource(R.string.yes) else stringResource(R.string.no)
            )
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun getConstellationName(constellation: com.example.gpstest.domain.model.Constellation): String {
    return when (constellation) {
        com.example.gpstest.domain.model.Constellation.GPS -> "GPS"
        com.example.gpstest.domain.model.Constellation.GLONASS -> "GLN"
        com.example.gpstest.domain.model.Constellation.GALILEO -> "GAL"
        com.example.gpstest.domain.model.Constellation.BEIDOU -> "BDS"
        com.example.gpstest.domain.model.Constellation.QZSS -> "QZS"
        com.example.gpstest.domain.model.Constellation.UNKNOWN -> "UNK"
    }
}

private fun getConstellationFullName(constellation: com.example.gpstest.domain.model.Constellation): String {
    return when (constellation) {
        com.example.gpstest.domain.model.Constellation.GPS -> stringResource(R.string.constellation_gps)
        com.example.gpstest.domain.model.Constellation.GLONASS -> stringResource(R.string.constellation_glonass)
        com.example.gpstest.domain.model.Constellation.GALILEO -> stringResource(R.string.constellation_galileo)
        com.example.gpstest.domain.model.Constellation.BEIDOU -> stringResource(R.string.constellation_beidou)
        com.example.gpstest.domain.model.Constellation.QZSS -> stringResource(R.string.constellation_qzss)
        com.example.gpstest.domain.model.Constellation.UNKNOWN -> stringResource(R.string.constellation_unknown)
    }
}

private fun formatTimestamp(nanos: Long): String {
    val millis = nanos / 1_000_000
    val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    return sdf.format(Date(millis))
}
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/example/gpstest/ui/components/SatelliteDetailSheet.kt
git commit -m "feat: add SatelliteDetailSheet component"
```

---

### Task 3.4: 创建SatelliteListScreen

**文件:**
- 创建: `app/src/main/java/com/example/gpstest/ui/screens/satellite/SatelliteListScreen.kt`

- [ ] **Step 1: 创建SatelliteListScreen.kt**

```kotlin
package com.example.gpstest.ui.screens.satellite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.gpstest.R
import com.example.gpstest.ui.components.SatelliteCard
import com.example.gpstest.ui.components.SatelliteDetailSheet
import com.example.gpstest.ui.components.StatBar
import com.example.gpstest.viewmodel.SatelliteUiState
import com.example.gpstest.viewmodel.SatelliteViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SatelliteListScreen(
    viewModel: SatelliteViewModel,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState
    val selectedSatellite by viewModel.selectedSatellite
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    var showPermissionRequest by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.satellite_list_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is SatelliteUiState.Loading -> {
                    LoadingContent(
                        onRequestPermission = { showPermissionRequest = true }
                    )
                }

                is SatelliteUiState.Success -> {
                    SatelliteContent(
                        usedInFix = state.usedInFix,
                        visibleOnly = state.visibleOnly,
                        searching = state.searching,
                        totalCount = state.totalCount,
                        onSatelliteClick = { viewModel.selectSatellite(it) }
                    )
                }

                is SatelliteUiState.Error -> {
                    ErrorContent(
                        message = state.message,
                        onRequestPermission = { showPermissionRequest = true }
                    )
                }
            }
        }

        // Permission request dialog
        if (showPermissionRequest) {
            PermissionRequestDialog(
                onGrant = {
                    showPermissionRequest = false
                    onRequestPermission()
                },
                onDismiss = { showPermissionRequest = false }
            )
        }

        // Bottom sheet for satellite details
        if (selectedSatellite != null) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.clearSelection() },
                sheetState = sheetState
            ) {
                SatelliteDetailSheet(
                    satellite = selectedSatellite!!,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun LoadingContent(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Initializing GNSS...")
    }
}

@Composable
private fun SatelliteContent(
    usedInFix: List<com.example.gpstest.domain.model.GnssSatellite>,
    visibleOnly: List<com.example.gpstest.domain.model.GnssSatellite>,
    searching: List<com.example.gpstest.domain.model.GnssSatellite>,
    totalCount: Int,
    onSatelliteClick: (com.example.gpstest.domain.model.GnssSatellite) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        // Statistics bar
        item {
            StatBar(
                usedInFixCount = usedInFix.size,
                visibleCount = visibleOnly.size,
                totalCount = totalCount
            )
        }

        // Used in fix satellites
        if (usedInFix.isNotEmpty()) {
            item {
                androidx.compose.material.Text(
                    text = "${stringResource(R.string.used_in_fix)} (${usedInFix.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            items(usedInFix) { satellite ->
                SatelliteCard(
                    satellite = satellite,
                    onClick = { onSatelliteClick(satellite) }
                )
            }
        }

        // Visible only satellites
        if (visibleOnly.isNotEmpty()) {
            item {
                androidx.compose.material.Text(
                    text = "${stringResource(R.string.visible_only)} (${visibleOnly.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            items(visibleOnly) { satellite ->
                SatelliteCard(
                    satellite = satellite,
                    onClick = { onSatelliteClick(satellite) }
                )
            }
        }

        // Searching satellites
        if (searching.isNotEmpty()) {
            item {
                androidx.compose.material.Text(
                    text = "${stringResource(R.string.searching)} (${searching.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            items(searching) { satellite ->
                SatelliteCard(
                    satellite = satellite,
                    onClick = { onSatelliteClick(satellite) }
                )
            }
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun PermissionRequestDialog(
    onGrant: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.permission_required)) },
        text = { Text("Location permission is required to access GNSS data.") },
        confirmButton = {
            Button(onClick = onGrant) {
                Text(stringResource(R.string.grant_permission))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
```

- [ ] **Step 2: 添加缺少的imports和AlertDialog**

在文件顶部添加：
```kotlin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
```

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/example/gpstest/ui/screens/satellite/SatelliteListScreen.kt
git commit -m "feat: implement SatelliteListScreen with lazy column and bottom sheet"
```

---

### Task 3.5: 创建ConstellationExt工具类

**文件:**
- 创建: `app/src/main/java/com/example/gpstest/util/ConstellationExt.kt`

- [ ] **Step 1: 创建ConstellationExt.kt**

```kotlin
package com.example.gpstest.util

import com.example.gpstest.domain.model.Constellation

fun Constellation.getDisplayName(): String {
    return when (this) {
        Constellation.GPS -> "GPS"
        Constellation.GLONASS -> "GLONASS"
        Constellation.GALILEO -> "Galileo"
        Constellation.BEIDOU -> "BeiDou"
        Constellation.QZSS -> "QZSS"
        Constellation.UNKNOWN -> "Unknown"
    }
}

fun Constellation.getColor(): androidx.compose.ui.graphics.Color {
    return when (this) {
        Constellation.GPS -> androidx.compose.ui.graphics.Color(0xFF2196F3)
        Constellation.GLONASS -> androidx.compose.ui.graphics.Color(0xFFFF9800)
        Constellation.GALILEO -> androidx.compose.ui.graphics.Color(0xFF9C27B0)
        Constellation.BEIDOU -> androidx.compose.ui.graphics.Color(0xFFE91E63)
        Constellation.QZSS -> androidx.compose.ui.graphics.Color(0xFF00BCD4)
        Constellation.UNKNOWN -> androidx.compose.ui.graphics.Color.Gray
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/example/gpstest/util/ConstellationExt.kt
git commit -m "feat: add Constellation extension utilities"
```

---

## 测试和验证

### Task 4.1: 构建和运行

- [ ] **Step 1: 构建项目**

```bash
./gradlew assembleDebug
```

预期输出: BUILD SUCCESSFUL

- [ ] **Step 2: 连接Android设备或启动模拟器**

```bash
adb devices
```

预期输出: 显示设备列表

- [ ] **Step 3: 安装应用**

```bash
./gradlew installDebug
```

预期输出: INSTALL_SUCCESSFUL

- [ ] **Step 4: 启动应用并检查日志**

```bash
adb logcat -s GpsTest GnssMeasurement
```

预期输出: 看到GNSS测量数据日志

---

## 完成清单

在提交最终实现之前，确认以下内容：

- [ ] 所有文件已创建并包含完整代码
- [ ] 项目成功构建（./gradlew assembleDebug）
- [ ] 应用可以在真机/模拟器上运行
- [ ] 位置权限请求正常工作
- [ ] 卫星列表正确显示GNSS数据
- [ ] 点击卫星卡片显示详情抽屉
- [ ] 信号统计数据正确显示
- [ ] 代码已提交到git

---

## 已知限制和未来改进

1. **信号图表**: 当前未实现，可在Phase 3添加
2. **A-GPS功能**: 未在此计划中实现，可在后续Phase添加
3. **数据持久化**: 当前不保存历史数据
4. **深色模式**: 主题已支持但可能需要微调
5. **性能优化**: 列表性能可能需要针对大量卫星进行优化

---

## 参考资料

- [Android GNSS测量API](https://developer.android.com/guide/topics/sensors/gnss-measurements)
- [Jetpack Compose文档](https://developer.android.com/jetpack/compose)
- [Kotlin Flow文档](https://developer.android.com/kotlin/flow)
- [Material 3指南](https://m3.material.io/)
