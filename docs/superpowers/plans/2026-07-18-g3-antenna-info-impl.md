# G3 GnssAntennaInfo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Collect and display GNSS antenna phase-center info (PCO + carrier MHz + PCV summary) on the satellite screen for API 30+ devices, with stable domain fields for future RINEX.

**Architecture:** Pure domain models + pure mapper functions; platform `registerAntennaInfoListener` wrapped in a separate `callbackFlow` (not merged into 250ms `GnssData`); Repository pass-through; `SatelliteViewModel` StateFlow; Compose `AntennaInfoCard` after capabilities card.

**Tech Stack:** Kotlin 2.1, Android `LocationManager` / `GnssAntennaInfo` (API 30+), Kotlin Flow `callbackFlow`, Jetpack Compose Material3, JUnit 4 unit tests, no new dependencies.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-07-18-g3-antenna-info-design.md` (scope B).
- Conventional emoji commits: `feat: ✨` / `test: ✅` / `docs: 📝` / `chore: 🔧`.
- JAVA_HOME: `C:\Program Files\Java\jdk-21` before Gradle.
- Domain layer: no `android.*` imports.
- Platform API names (SDK 36): `registerAntennaInfoListener`, `unregisterAntennaInfoListener`, `getGnssAntennaInfos` — not “Callback”.
- API gate: `Build.VERSION.SDK_INT >= Build.VERSION_CODES.R` (30).
- i18n: Chinese in `values/`, English in `values-en/`; no hard-coded UI strings.
- Do not commit untracked `docs/superpowers/plans/*` other than this impl plan if user prefers; this plan file may be committed when asked.
- ProGuard minify already on; must keep antenna types/methods.

---

## File Map

| Path                                                                                | Action                                         |
| ----------------------------------------------------------------------------------- | ---------------------------------------------- |
| `app/src/main/java/com/example/gpstest/domain/model/AntennaInfo.kt`                 | Create domain models + pure PCV summary helper |
| `app/src/test/java/com/example/gpstest/domain/model/AntennaInfoTest.kt`             | Create unit tests                              |
| `app/src/main/java/com/example/gpstest/data/source/AntennaInfoMapper.kt`            | Create pure mapping from primitive inputs      |
| `app/src/main/java/com/example/gpstest/data/source/GnssDataSource.kt`               | Add `getAntennaInfos()`                        |
| `app/src/main/java/com/example/gpstest/data/source/GnssDataSourceImpl.kt`           | Register listener + map                        |
| `app/src/main/java/com/example/gpstest/domain/repository/GnssRepository.kt`         | Add pass-through                               |
| `app/src/main/java/com/example/gpstest/domain/repository/GnssRepositoryImpl.kt`     | Pass-through (no sample)                       |
| `app/src/main/java/com/example/gpstest/viewmodel/SatelliteViewModel.kt`             | StateFlow + collect job                        |
| `app/src/main/java/com/example/gpstest/ui/components/AntennaInfoCard.kt`            | Create card                                    |
| `app/src/main/java/com/example/gpstest/ui/screens/satellite/SatelliteListScreen.kt` | Wire card                                      |
| `app/src/main/res/values/strings.xml`                                               | Chinese labels                                 |
| `app/src/main/res/values-en/strings.xml`                                            | English labels                                 |
| `app/proguard-rules.pro`                                                            | Keep antenna API                               |
| `TODO.md`                                                                           | Mark G3 complete                               |

---

### Task 1: Domain model + pure mapper tests (TDD)

**Files:**

- Create: `app/src/main/java/com/example/gpstest/domain/model/AntennaInfo.kt`
- Create: `app/src/main/java/com/example/gpstest/data/source/AntennaInfoMapper.kt`
- Test: `app/src/test/java/com/example/gpstest/domain/model/AntennaInfoTest.kt`

**Interfaces:**

- Produces:
    - `data class AntennaInfo(...)`
    - `data class PhaseCenterVariationSummary(...)`
    - `object AntennaInfoMapper` with:
        - `fun summarizePcv(corrections: Array<DoubleArray>?, deltaPhiDeg: Double, deltaThetaDeg: Double): PhaseCenterVariationSummary?`
        - `fun fromPrimitives(carrierFrequencyMHz: Double, pcoXMm: Double, pcoYMm: Double, pcoZMm: Double, pcoXUncertaintyMm: Double, pcoYUncertaintyMm: Double, pcoZUncertaintyMm: Double, pcvSummary: PhaseCenterVariationSummary?): AntennaInfo`

- [ ] **Step 1: Write failing tests**

Create `AntennaInfoTest.kt`:

```kotlin
package com.example.gpstest.domain.model

import com.example.gpstest.data.source.AntennaInfoMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AntennaInfoTest {
    @Test
    fun `fromPrimitives preserves PCO and carrier`() {
        val info =
            AntennaInfoMapper.fromPrimitives(
                carrierFrequencyMHz = 1575.42,
                pcoXMm = 1.0,
                pcoYMm = 2.0,
                pcoZMm = 3.0,
                pcoXUncertaintyMm = 0.1,
                pcoYUncertaintyMm = 0.2,
                pcoZUncertaintyMm = 0.3,
                pcvSummary = null,
            )
        assertEquals(1575.42, info.carrierFrequencyMHz, 0.0)
        assertEquals(1.0, info.pcoXMm, 0.0)
        assertEquals(2.0, info.pcoYMm, 0.0)
        assertEquals(3.0, info.pcoZMm, 0.0)
        assertEquals(0.1, info.pcoXUncertaintyMm, 0.0)
        assertEquals(0.2, info.pcoYUncertaintyMm, 0.0)
        assertEquals(0.3, info.pcoZUncertaintyMm, 0.0)
        assertNull(info.pcvSummary)
    }

    @Test
    fun `summarizePcv returns null when corrections null`() {
        assertNull(AntennaInfoMapper.summarizePcv(null, 30.0, 5.0))
    }

    @Test
    fun `summarizePcv returns null when corrections empty`() {
        assertNull(AntennaInfoMapper.summarizePcv(emptyArray(), 30.0, 5.0))
    }

    @Test
    fun `summarizePcv computes min max count and deltas`() {
        val grid =
            arrayOf(
                doubleArrayOf(-1.5, 0.0, 2.5),
                doubleArrayOf(1.0, -0.5, 0.25),
            )
        val summary = AntennaInfoMapper.summarizePcv(grid, 30.0, 5.0)!!
        assertEquals(30.0, summary.deltaPhiDeg, 0.0)
        assertEquals(5.0, summary.deltaThetaDeg, 0.0)
        assertEquals(6, summary.sampleCount)
        assertEquals(-1.5, summary.minCorrectionMm, 0.0)
        assertEquals(2.5, summary.maxCorrectionMm, 0.0)
    }

    @Test
    fun `data class equality holds`() {
        val a =
            AntennaInfo(
                carrierFrequencyMHz = 1176.45,
                pcoXMm = 0.0,
                pcoYMm = 0.0,
                pcoZMm = 10.0,
                pcoXUncertaintyMm = 0.0,
                pcoYUncertaintyMm = 0.0,
                pcoZUncertaintyMm = 1.0,
                pcvSummary = null,
            )
        val b = a.copy()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
```

- [ ] **Step 2: Run tests — expect FAIL (classes missing)**

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
.\gradlew test --tests com.example.gpstest.domain.model.AntennaInfoTest
```

Expected: compile error / class not found for `AntennaInfo` or `AntennaInfoMapper`.

- [ ] **Step 3: Implement domain models**

Create `AntennaInfo.kt`:

```kotlin
package com.example.gpstest.domain.model

/**
 * 单条 GNSS 天线相位中心快照（对应平台 GnssAntennaInfo 一条）。
 * 字段名稳定，供 G2 RINEX ANTENNA: DELTA / 频点元数据使用。
 */
data class AntennaInfo(
    val carrierFrequencyMHz: Double,
    val pcoXMm: Double,
    val pcoYMm: Double,
    val pcoZMm: Double,
    val pcoXUncertaintyMm: Double,
    val pcoYUncertaintyMm: Double,
    val pcoZUncertaintyMm: Double,
    val pcvSummary: PhaseCenterVariationSummary?,
)

/**
 * 相位中心变化（PCV）网格的轻量摘要。
 * 不保留完整 double[][]，避免 StateFlow 体积膨胀。
 */
data class PhaseCenterVariationSummary(
    val deltaPhiDeg: Double,
    val deltaThetaDeg: Double,
    val sampleCount: Int,
    val minCorrectionMm: Double,
    val maxCorrectionMm: Double,
)
```

Create `AntennaInfoMapper.kt`:

```kotlin
package com.example.gpstest.data.source

import com.example.gpstest.domain.model.AntennaInfo
import com.example.gpstest.domain.model.PhaseCenterVariationSummary

/**
 * 将平台天线字段映射为领域模型的纯函数集合（可 JVM 单测）。
 */
object AntennaInfoMapper {
    fun fromPrimitives(
        carrierFrequencyMHz: Double,
        pcoXMm: Double,
        pcoYMm: Double,
        pcoZMm: Double,
        pcoXUncertaintyMm: Double,
        pcoYUncertaintyMm: Double,
        pcoZUncertaintyMm: Double,
        pcvSummary: PhaseCenterVariationSummary?,
    ): AntennaInfo =
        AntennaInfo(
            carrierFrequencyMHz = carrierFrequencyMHz,
            pcoXMm = pcoXMm,
            pcoYMm = pcoYMm,
            pcoZMm = pcoZMm,
            pcoXUncertaintyMm = pcoXUncertaintyMm,
            pcoYUncertaintyMm = pcoYUncertaintyMm,
            pcoZUncertaintyMm = pcoZUncertaintyMm,
            pcvSummary = pcvSummary,
        )

    fun summarizePcv(
        corrections: Array<DoubleArray>?,
        deltaPhiDeg: Double,
        deltaThetaDeg: Double,
    ): PhaseCenterVariationSummary? {
        if (corrections == null || corrections.isEmpty()) return null
        var min = Double.POSITIVE_INFINITY
        var max = Double.NEGATIVE_INFINITY
        var count = 0
        for (row in corrections) {
            for (value in row) {
                if (value < min) min = value
                if (value > max) max = value
                count++
            }
        }
        if (count == 0) return null
        return PhaseCenterVariationSummary(
            deltaPhiDeg = deltaPhiDeg,
            deltaThetaDeg = deltaThetaDeg,
            sampleCount = count,
            minCorrectionMm = min,
            maxCorrectionMm = max,
        )
    }
}
```

- [ ] **Step 4: Run tests — expect PASS**

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
.\gradlew test --tests com.example.gpstest.domain.model.AntennaInfoTest
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/example/gpstest/domain/model/AntennaInfo.kt `
  app/src/main/java/com/example/gpstest/data/source/AntennaInfoMapper.kt `
  app/src/test/java/com/example/gpstest/domain/model/AntennaInfoTest.kt
git commit -m "feat: ✨ add AntennaInfo domain model and mapper"
```

---

### Task 2: DataSource + Repository antenna Flow

**Files:**

- Modify: `app/src/main/java/com/example/gpstest/data/source/GnssDataSource.kt`
- Modify: `app/src/main/java/com/example/gpstest/data/source/GnssDataSourceImpl.kt`
- Modify: `app/src/main/java/com/example/gpstest/domain/repository/GnssRepository.kt`
- Modify: `app/src/main/java/com/example/gpstest/domain/repository/GnssRepositoryImpl.kt`
- Modify: `app/proguard-rules.pro`

**Interfaces:**

- Consumes: `AntennaInfoMapper`, `AntennaInfo`
- Produces: `fun getAntennaInfos(): Flow<List<AntennaInfo>>` on DataSource + Repository

- [ ] **Step 1: Extend interfaces**

In `GnssDataSource.kt` add import for `AntennaInfo` and:

```kotlin
/**
 * 天线相位中心信息流（API 30+）。
 * 独立于 [getGnssData]，不参与 250ms 采样。
 * API < 30 或不支持时立即发出 emptyList 并关闭。
 */
fun getAntennaInfos(): Flow<List<AntennaInfo>>
```

In `GnssRepository.kt` add import + same method:

```kotlin
fun getAntennaInfos(): Flow<List<AntennaInfo>>
```

In `GnssRepositoryImpl.kt`:

```kotlin
override fun getAntennaInfos(): Flow<List<AntennaInfo>> = dataSource.getAntennaInfos()
```

(Do **not** apply `.sample(250)`.)

- [ ] **Step 2: Implement `getAntennaInfos` in `GnssDataSourceImpl`**

Add imports:

```kotlin
import android.location.GnssAntennaInfo
import com.example.gpstest.domain.model.AntennaInfo
import timber.log.Timber
```

Append method (after `getNmeaSentences` / near end of class, before helpers):

```kotlin
override fun getAntennaInfos(): Flow<List<AntennaInfo>> =
    callbackFlow {
        val lm = locationManager
        if (lm == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener =
            GnssAntennaInfo.Listener { infos ->
                if (!isClosedForSend) {
                    trySend(mapAntennaInfos(infos))
                }
            }

        try {
            lm.getGnssAntennaInfos()?.let { initial ->
                trySend(mapAntennaInfos(initial))
            }
            lm.registerAntennaInfoListener(context.mainExecutor, listener)
        } catch (e: SecurityException) {
            Timber.w(e, "Antenna info listener registration denied")
            trySend(emptyList())
            close(e)
            return@callbackFlow
        } catch (e: Exception) {
            Timber.w(e, "Antenna info listener registration failed")
            trySend(emptyList())
            close(e)
            return@callbackFlow
        }

        awaitClose {
            try {
                lm.unregisterAntennaInfoListener(listener)
            } catch (_: Exception) {
                // ignore cleanup
            }
        }
    }

@androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
private fun mapAntennaInfos(infos: List<GnssAntennaInfo>?): List<AntennaInfo> {
    if (infos.isNullOrEmpty()) return emptyList()
    return infos.map { platform ->
        val pco = platform.phaseCenterOffset
        val pcv = platform.phaseCenterVariationCorrections
        val summary =
            if (pcv != null) {
                AntennaInfoMapper.summarizePcv(
                    corrections = pcv.correctionsArray,
                    deltaPhiDeg = pcv.deltaPhi,
                    deltaThetaDeg = pcv.deltaTheta,
                )
            } else {
                null
            }
        AntennaInfoMapper.fromPrimitives(
            carrierFrequencyMHz = platform.carrierFrequencyMHz,
            pcoXMm = pco.xOffsetMm,
            pcoYMm = pco.yOffsetMm,
            pcoZMm = pco.zOffsetMm,
            pcoXUncertaintyMm = pco.xOffsetUncertaintyMm,
            pcoYUncertaintyMm = pco.yOffsetUncertaintyMm,
            pcoZUncertaintyMm = pco.zOffsetUncertaintyMm,
            pcvSummary = summary,
        )
    }
}
```

Notes:

- Use property accessors as Kotlin exposes them (`carrierFrequencyMHz`, `phaseCenterOffset`, etc.).
- If compile fails on `getGnssAntennaInfos()` vs property `gnssAntennaInfos`, use whichever the stub provides.

- [ ] **Step 3: ProGuard keep rules**

In `app/proguard-rules.pro` GNSS section, after `GnssCapabilities` keep, add:

```proguard
-keep class android.location.GnssAntennaInfo { *; }
-keep class android.location.GnssAntennaInfo$* { *; }
```

Inside existing `-keep class android.location.LocationManager { ... }` block, add:

```proguard
    public *** registerAntennaInfoListener(...);
    public *** unregisterAntennaInfoListener(...);
    public *** getGnssAntennaInfos(...);
```

- [ ] **Step 4: Compile check**

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
.\gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/example/gpstest/data/source/GnssDataSource.kt `
  app/src/main/java/com/example/gpstest/data/source/GnssDataSourceImpl.kt `
  app/src/main/java/com/example/gpstest/domain/repository/GnssRepository.kt `
  app/src/main/java/com/example/gpstest/domain/repository/GnssRepositoryImpl.kt `
  app/proguard-rules.pro
git commit -m "feat: ✨ register GnssAntennaInfo listener flow"
```

---

### Task 3: ViewModel + UI card + i18n

**Files:**

- Modify: `app/src/main/java/com/example/gpstest/viewmodel/SatelliteViewModel.kt`
- Create: `app/src/main/java/com/example/gpstest/ui/components/AntennaInfoCard.kt`
- Modify: `app/src/main/java/com/example/gpstest/ui/screens/satellite/SatelliteListScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`

**Interfaces:**

- Consumes: `repository.getAntennaInfos(): Flow<List<AntennaInfo>>`
- Produces: `val antennaInfos: StateFlow<List<AntennaInfo>>`

- [ ] **Step 1: Strings**

`values/strings.xml` after GNSS Capabilities block:

```xml
    <!-- Antenna phase center (G3) -->
    <string name="antenna_info_title">天线相位中心</string>
    <string name="antenna_carrier_format">载波频率：%1$.3f MHz</string>
    <string name="antenna_pco_format">PCO X/Y/Z：%1$.2f / %2$.2f / %3$.2f mm</string>
    <string name="antenna_pco_uncertainty_format">不确定度 ±%1$.2f / %2$.2f / %3$.2f mm</string>
    <string name="antenna_pcv_summary_format">PCV：Δφ=%1$.1f° Δθ=%2$.1f°，网格 %3$d 点，范围 [%4$.2f, %5$.2f] mm</string>
```

`values-en/strings.xml` before `</resources>`:

```xml
    <!-- Antenna phase center (G3) -->
    <string name="antenna_info_title">Antenna phase center</string>
    <string name="antenna_carrier_format">Carrier: %1$.3f MHz</string>
    <string name="antenna_pco_format">PCO X/Y/Z: %1$.2f / %2$.2f / %3$.2f mm</string>
    <string name="antenna_pco_uncertainty_format">Uncertainty ±%1$.2f / %2$.2f / %3$.2f mm</string>
    <string name="antenna_pcv_summary_format">PCV: Δφ=%1$.1f° Δθ=%2$.1f°, %3$d samples, range [%4$.2f, %5$.2f] mm</string>
```

- [ ] **Step 2: ViewModel**

In `SatelliteViewModel.kt`:

```kotlin
import com.example.gpstest.domain.model.AntennaInfo
```

After `_gnssCapabilities`:

```kotlin
private val _antennaInfos = MutableStateFlow<List<AntennaInfo>>(emptyList())
val antennaInfos: StateFlow<List<AntennaInfo>> = _antennaInfos.asStateFlow()

private var antennaJob: Job? = null
```

Update `startListening()` to also start antenna collection (cancel previous antennaJob first):

```kotlin
fun startListening() {
    collectionJob?.cancel()
    antennaJob?.cancel()
    _antennaInfos.value = emptyList()

    antennaJob =
        viewModelScope.launch {
            try {
                repository.getAntennaInfos().collect { list ->
                    _antennaInfos.value = list
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                _antennaInfos.value = emptyList()
            }
        }

    collectionJob =
        viewModelScope.launch {
            // existing getGnssData collect body unchanged
            ...
        }
}
```

In `onCleared()` (existing), cancel antenna job and clear:

```kotlin
override fun onCleared() {
    collectionJob?.cancel()
    antennaJob?.cancel()
    _antennaInfos.value = emptyList()
    super.onCleared()
}
```

(If `onCleared` already cancels `collectionJob`, only add antenna lines.)

- [ ] **Step 3: Create `AntennaInfoCard.kt`**

```kotlin
package com.example.gpstest.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gpstest.R
import com.example.gpstest.domain.model.AntennaInfo

@Composable
fun AntennaInfoCard(
    infos: List<AntennaInfo>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.antenna_info_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            infos.forEachIndexed { index, info ->
                if (index > 0) {
                    HorizontalDivider()
                }
                AntennaInfoEntry(info = info)
            }
        }
    }
}

@Composable
private fun AntennaInfoEntry(info: AntennaInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text =
                stringResource(
                    R.string.antenna_carrier_format,
                    info.carrierFrequencyMHz,
                ),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text =
                stringResource(
                    R.string.antenna_pco_format,
                    info.pcoXMm,
                    info.pcoYMm,
                    info.pcoZMm,
                ),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text =
                stringResource(
                    R.string.antenna_pco_uncertainty_format,
                    info.pcoXUncertaintyMm,
                    info.pcoYUncertaintyMm,
                    info.pcoZUncertaintyMm,
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        info.pcvSummary?.let { pcv ->
            Text(
                text =
                    stringResource(
                        R.string.antenna_pcv_summary_format,
                        pcv.deltaPhiDeg,
                        pcv.deltaThetaDeg,
                        pcv.sampleCount,
                        pcv.minCorrectionMm,
                        pcv.maxCorrectionMm,
                    ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
```

- [ ] **Step 4: Wire `SatelliteListScreen`**

1. Import `AntennaInfo` and `AntennaInfoCard`.
2. In `SatelliteListScreen` composable:

```kotlin
val antennaInfos by viewModel.antennaInfos.collectAsState()
```

3. Pass `antennaInfos` into `SatelliteListContent`.
4. Add parameter:

```kotlin
antennaInfos: List<com.example.gpstest.domain.model.AntennaInfo>,
```

5. After `GnssCapabilitiesCard` block:

```kotlin
if (antennaInfos.isNotEmpty()) {
    item {
        AntennaInfoCard(infos = antennaInfos)
    }
}
```

- [ ] **Step 5: Build + unit tests**

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
.\gradlew ktlintCheck test assembleDebug
```

Expected: all green.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/example/gpstest/viewmodel/SatelliteViewModel.kt `
  app/src/main/java/com/example/gpstest/ui/components/AntennaInfoCard.kt `
  app/src/main/java/com/example/gpstest/ui/screens/satellite/SatelliteListScreen.kt `
  app/src/main/res/values/strings.xml `
  app/src/main/res/values-en/strings.xml
git commit -m "feat: ✨ show antenna info card on satellite screen"
```

---

### Task 4: Verify + TODO mark

**Files:**

- Modify: `TODO.md`

- [ ] **Step 1: Full verify**

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
.\gradlew ktlintCheck test assembleDebug
```

Expected: `BUILD SUCCESSFUL`. Optionally `assembleRelease` if time allows.

- [ ] **Step 2: Mark G3 complete in TODO.md**

1. Top remaining table: remove G3 row or mark ✅.
2. Section `### G3. GnssAntennaInfo 接入` → rename to `### G3. ... ✅ 已实现` and replace body with short “现状（已实现）” + remaining gaps (device smoke / RINEX use in G2).
3. Priority order table: G3 as ✅.
4. Roadmap ASCII: mark G3 if listed.
5. Status table row for G3 if present.

- [ ] **Step 3: Commit**

```powershell
git add TODO.md
git commit -m "docs: 📝 mark G3 antenna info complete"
```

- [ ] **Step 4: Optional branch finish**

If work was on `feat/g3-antenna-info`, report commits and ask user to merge/push (do not merge unless asked).

---

## Spec coverage checklist

| Spec requirement                                       | Task                      |
| ------------------------------------------------------ | ------------------------- |
| Domain `AntennaInfo` + PCV summary                     | Task 1                    |
| Pure mapper / no android in domain                     | Task 1                    |
| Separate Flow, not in `GnssData`                       | Task 2                    |
| `registerAntennaInfoListener` + unregister             | Task 2                    |
| Initial `getGnssAntennaInfos`                          | Task 2                    |
| API < 30 → empty, no crash                             | Task 2                    |
| Repository pass-through                                | Task 2                    |
| ProGuard keeps                                         | Task 2                    |
| ViewModel StateFlow                                    | Task 3                    |
| AntennaInfoCard + hide if empty                        | Task 3                    |
| zh + en strings                                        | Task 3                    |
| Unit tests + ktlint + assembleDebug                    | Task 1/3/4                |
| TODO mark                                              | Task 4                    |
| Non-goals: full PCV table, export, merge into GnssData | Not implemented (correct) |

## Self-review notes

- No TBD/placeholder steps; platform method names match SDK 36 `javap`.
- Type names consistent: `AntennaInfo`, `PhaseCenterVariationSummary`, `AntennaInfoMapper`.
- Mapper lives in `data.source` so domain stays pure; tests may import data mapper (acceptable; alternative is moving pure helpers next to domain — keep as planned for platform adjacency).
