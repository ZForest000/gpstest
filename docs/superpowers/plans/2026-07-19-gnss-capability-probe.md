# GNSS 能力声明 vs 实测对照 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在接收机诊断页的 `GnssCapabilitiesCard` 上，对 Measurements / Navigation Messages / Antenna Info 展示「声明 | 实测」对照与一句诊断，解释为何某类数据为空。

**架构：** 领域层纯函数 `CapabilityProbeEvaluator` 根据 `GnssCapabilitiesInfo`（声明）与 `CapabilityEvidence`（30s 窗内 sticky 证据）合成 `List<CapabilityProbeRow>`；`SatelliteViewModel` 在 `startListening()` 会话内累计证据并每秒刷新；UI 仅渲染 rows，不内联规则。不新增平台 GNSS 回调。

**技术栈：** Kotlin 2.1、JUnit 4、Jetpack Compose Material3、`StateFlow` + coroutines、现有 MVVM 手动 DI。

**规格：** `docs/superpowers/specs/2026-07-19-gnss-capability-probe-design.md`（用户已确认）

## 全局约束

- Domain 层禁止 `android.*` 导入（`GnssSatellite` 已有 `android.location.GnssMeasurement` 历史依赖，**新文件** `CapabilityProbe.kt` / `CapabilityProbeEvaluator.kt` 不得新增 android 依赖）。
- TDD：先写失败测试，再写最少实现。
- JAVA_HOME：`C:\Program Files\Java\jdk-21`；Android SDK：`D:\android_sdk`。
- 中文 `values/strings.xml` + 英文 `values-en/strings.xml` 同步；UI 无硬编码文案。
- Commit 风格与仓库一致（可用 `feat:` / `test:` / `docs:`）。
- 不修改 ADR / Corrections / Correlation Vectors 的声明-only 展示逻辑（除布局顺序外）。
- 不新增页面/导航。

---

## 文件结构

| 路径 | 职责 |
|------|------|
| `app/src/main/java/com/example/gpstest/domain/model/CapabilityProbe.kt` | 新建：`CapabilityKey`、`ProbeOutcome`、`ProbeDiagnostic`、`CapabilityEvidence`、`CapabilityProbeRow` |
| `app/src/main/java/com/example/gpstest/domain/util/CapabilityProbeEvaluator.kt` | 新建：评估矩阵 + `hasMeasurementEvidence` 扩展 |
| `app/src/test/java/com/example/gpstest/domain/util/CapabilityProbeEvaluatorTest.kt` | 新建：矩阵与证据规则单测 |
| `app/src/main/java/com/example/gpstest/viewmodel/SatelliteViewModel.kt` | 修改：证据累计、`capabilityProbes`、1s ticker |
| `app/src/main/java/com/example/gpstest/ui/components/GnssCapabilitiesCard.kt` | 修改：声明\|实测行 + 诊断 |
| `app/src/main/java/com/example/gpstest/ui/screens/diagnostics/ReceiverDiagnosticsScreen.kt` | 修改：collect probes 并传入 Card |
| `app/src/main/res/values/strings.xml` | 修改：中文探针文案 |
| `app/src/main/res/values-en/strings.xml` | 修改：英文探针文案 |

---

### 任务 1：领域模型 + Evaluator 单测与实现（TDD）

**文件：**

- 创建：`app/src/main/java/com/example/gpstest/domain/model/CapabilityProbe.kt`
- 创建：`app/src/main/java/com/example/gpstest/domain/util/CapabilityProbeEvaluator.kt`
- 测试：`app/src/test/java/com/example/gpstest/domain/util/CapabilityProbeEvaluatorTest.kt`

- [ ] **步骤 1：编写失败的测试**

创建 `CapabilityProbeEvaluatorTest.kt`：

```kotlin
package com.example.gpstest.domain.util

import com.example.gpstest.domain.model.CapabilityEvidence
import com.example.gpstest.domain.model.CapabilityKey
import com.example.gpstest.domain.model.CapabilityState
import com.example.gpstest.domain.model.Constellation
import com.example.gpstest.domain.model.GnssCapabilitiesInfo
import com.example.gpstest.domain.model.GnssSatellite
import com.example.gpstest.domain.model.ProbeDiagnostic
import com.example.gpstest.domain.model.ProbeOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityProbeEvaluatorTest {
    private fun caps(
        measurements: Int? = 1,
        nav: Int? = 1,
        antenna: Int? = 1,
    ) = GnssCapabilitiesInfo(
        hardwareModelName = "Test",
        yearOfHardware = "2024",
        hasMeasurements = measurements,
        hasNavigationMessages = nav,
        hasAntennaInfo = antenna,
        hasAccumulatedDeltaRange = null,
        hasMeasurementCorrections = null,
        hasMeasurementCorrelationVectors = null,
    )

    private fun evidence(
        listening: Boolean = true,
        elapsedMs: Long = 0L,
        measurementsSeen: Boolean = false,
        navSeen: Boolean = false,
        antennaSeen: Boolean = false,
    ) = CapabilityEvidence(
        listeningStarted = listening,
        elapsedMs = elapsedMs,
        measurementsSeen = measurementsSeen,
        navigationMessagesSeen = navSeen,
        antennaInfoSeen = antennaSeen,
    )

    private fun row(
        rows: List<com.example.gpstest.domain.model.CapabilityProbeRow>,
        key: CapabilityKey,
    ) = rows.first { it.key == key }

    private fun makeSatellite(
        measurementState: Int? = null,
        measurementCn0DbHz: Double? = null,
        pseudorangeRateMetersPerSecond: Double? = null,
        accumulatedDeltaRangeMeters: Double? = null,
    ) = GnssSatellite(
        svid = 1,
        constellation = Constellation.GPS,
        rawConstellationType = 1,
        cn0DbHz = 30f,
        azimuthDegrees = 45f,
        elevationDegrees = 30f,
        hasAlmanac = true,
        hasEphemeris = true,
        usedInFix = false,
        carrierFrequencyHz = null,
        carrierCycles = null,
        dopplerShiftHz = null,
        timeNanos = 0L,
        measurementState = measurementState,
        measurementCn0DbHz = measurementCn0DbHz,
        pseudorangeRateMetersPerSecond = pseudorangeRateMetersPerSecond,
        accumulatedDeltaRangeMeters = accumulatedDeltaRangeMeters,
    )

    @Test
    fun `not listening yields NOT_LISTENING for all keys`() {
        val rows =
            CapabilityProbeEvaluator.evaluate(
                capabilities = caps(),
                evidence = evidence(listening = false),
            )
        assertEquals(3, rows.size)
        rows.forEach { r ->
            assertEquals(ProbeOutcome.UNKNOWN, r.observed)
            assertEquals(ProbeDiagnostic.NOT_LISTENING, r.diagnostic)
            assertNull(r.remainingMs)
        }
    }

    @Test
    fun `supported and seen is OBSERVED`() {
        val r =
            row(
                CapabilityProbeEvaluator.evaluate(
                    caps(measurements = 1),
                    evidence(measurementsSeen = true, elapsedMs = 5_000),
                ),
                CapabilityKey.MEASUREMENTS,
            )
        assertEquals(CapabilityState.SUPPORTED, r.declared)
        assertEquals(ProbeOutcome.OBSERVED, r.observed)
        assertEquals(ProbeDiagnostic.DECLARED_SUPPORTED_OBSERVED, r.diagnostic)
        assertNull(r.remainingMs)
    }

    @Test
    fun `supported not seen within window is OBSERVING with remaining`() {
        val r =
            row(
                CapabilityProbeEvaluator.evaluate(
                    caps(measurements = 1),
                    evidence(elapsedMs = 10_000),
                ),
                CapabilityKey.MEASUREMENTS,
            )
        assertEquals(ProbeOutcome.OBSERVING, r.observed)
        assertEquals(ProbeDiagnostic.DECLARED_SUPPORTED_OBSERVING, r.diagnostic)
        assertEquals(20_000L, r.remainingMs)
    }

    @Test
    fun `supported not seen after window is NOT_OBSERVED`() {
        val r =
            row(
                CapabilityProbeEvaluator.evaluate(
                    caps(measurements = 1),
                    evidence(elapsedMs = 30_000),
                ),
                CapabilityKey.MEASUREMENTS,
            )
        assertEquals(ProbeOutcome.NOT_OBSERVED, r.observed)
        assertEquals(ProbeDiagnostic.DECLARED_SUPPORTED_NOT_OBSERVED, r.diagnostic)
        assertNull(r.remainingMs)
    }

    @Test
    fun `unsupported without seen is NOT_APPLICABLE before and after window`() {
        listOf(0L, 30_000L).forEach { elapsed ->
            val r =
                row(
                    CapabilityProbeEvaluator.evaluate(
                        caps(measurements = 0),
                        evidence(elapsedMs = elapsed),
                    ),
                    CapabilityKey.MEASUREMENTS,
                )
            assertEquals(CapabilityState.UNSUPPORTED, r.declared)
            assertEquals(ProbeOutcome.NOT_APPLICABLE, r.observed)
            assertEquals(ProbeDiagnostic.DECLARED_UNSUPPORTED, r.diagnostic)
        }
    }

    @Test
    fun `unsupported but seen is OBSERVED with unknown-observed diagnostic`() {
        val r =
            row(
                CapabilityProbeEvaluator.evaluate(
                    caps(measurements = 0),
                    evidence(measurementsSeen = true),
                ),
                CapabilityKey.MEASUREMENTS,
            )
        assertEquals(ProbeOutcome.OBSERVED, r.observed)
        assertEquals(ProbeDiagnostic.DECLARED_UNKNOWN_OBSERVED, r.diagnostic)
    }

    @Test
    fun `null capabilities treat declared as UNKNOWN`() {
        val r =
            row(
                CapabilityProbeEvaluator.evaluate(
                    capabilities = null,
                    evidence = evidence(measurementsSeen = true),
                ),
                CapabilityKey.MEASUREMENTS,
            )
        assertEquals(CapabilityState.UNKNOWN, r.declared)
        assertEquals(ProbeOutcome.OBSERVED, r.observed)
        assertEquals(ProbeDiagnostic.DECLARED_UNKNOWN_OBSERVED, r.diagnostic)
    }

    @Test
    fun `unknown declared observing and not observed`() {
        val observing =
            row(
                CapabilityProbeEvaluator.evaluate(
                    caps(measurements = null),
                    evidence(elapsedMs = 1_000),
                ),
                CapabilityKey.MEASUREMENTS,
            )
        assertEquals(ProbeOutcome.OBSERVING, observing.observed)
        assertEquals(ProbeDiagnostic.DECLARED_UNKNOWN_OBSERVING, observing.diagnostic)

        val notObs =
            row(
                CapabilityProbeEvaluator.evaluate(
                    caps(measurements = -1),
                    evidence(elapsedMs = 30_000),
                ),
                CapabilityKey.MEASUREMENTS,
            )
        assertEquals(ProbeOutcome.NOT_OBSERVED, notObs.observed)
        assertEquals(ProbeDiagnostic.DECLARED_UNKNOWN_NOT_OBSERVED, notObs.diagnostic)
    }

    @Test
    fun `nav and antenna keys use their own seen flags`() {
        val rows =
            CapabilityProbeEvaluator.evaluate(
                caps(),
                evidence(navSeen = true, antennaSeen = false, elapsedMs = 30_000),
            )
        assertEquals(ProbeOutcome.OBSERVED, row(rows, CapabilityKey.NAVIGATION_MESSAGES).observed)
        assertEquals(ProbeOutcome.NOT_OBSERVED, row(rows, CapabilityKey.ANTENNA_INFO).observed)
    }

    @Test
    fun `hasMeasurementEvidence true when any measurement field present`() {
        assertTrue(makeSatellite(measurementState = 1).hasMeasurementEvidence())
        assertTrue(makeSatellite(measurementCn0DbHz = 35.0).hasMeasurementEvidence())
        assertTrue(makeSatellite(pseudorangeRateMetersPerSecond = 100.0).hasMeasurementEvidence())
        assertTrue(makeSatellite(accumulatedDeltaRangeMeters = 1.0).hasMeasurementEvidence())
    }

    @Test
    fun `hasMeasurementEvidence false when only status cn0 present`() {
        assertFalse(makeSatellite().hasMeasurementEvidence())
        assertFalse(listOf(makeSatellite()).anyMeasurementEvidence())
        assertTrue(listOf(makeSatellite(), makeSatellite(measurementState = 1)).anyMeasurementEvidence())
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
.\gradlew.bat testDebugUnitTest --tests com.example.gpstest.domain.util.CapabilityProbeEvaluatorTest
```

预期：FAIL，编译错误（`CapabilityProbeEvaluator` / `CapabilityEvidence` 等未定义）。

- [ ] **步骤 3：实现领域模型**

创建 `CapabilityProbe.kt`：

```kotlin
package com.example.gpstest.domain.model

enum class CapabilityKey {
    MEASUREMENTS,
    NAVIGATION_MESSAGES,
    ANTENNA_INFO,
}

enum class ProbeOutcome {
    OBSERVED,
    NOT_OBSERVED,
    OBSERVING,
    NOT_APPLICABLE,
    UNKNOWN,
}

enum class ProbeDiagnostic {
    DECLARED_SUPPORTED_OBSERVED,
    DECLARED_SUPPORTED_OBSERVING,
    DECLARED_SUPPORTED_NOT_OBSERVED,
    DECLARED_UNSUPPORTED,
    DECLARED_UNKNOWN_OBSERVED,
    DECLARED_UNKNOWN_OBSERVING,
    DECLARED_UNKNOWN_NOT_OBSERVED,
    NOT_LISTENING,
}

data class CapabilityEvidence(
    val listeningStarted: Boolean,
    val elapsedMs: Long,
    val measurementsSeen: Boolean,
    val navigationMessagesSeen: Boolean,
    val antennaInfoSeen: Boolean,
)

data class CapabilityProbeRow(
    val key: CapabilityKey,
    val declared: CapabilityState,
    val observed: ProbeOutcome,
    val diagnostic: ProbeDiagnostic,
    val remainingMs: Long? = null,
)
```

- [ ] **步骤 4：实现 Evaluator**

创建 `CapabilityProbeEvaluator.kt`：

```kotlin
package com.example.gpstest.domain.util

import com.example.gpstest.domain.model.CapabilityEvidence
import com.example.gpstest.domain.model.CapabilityKey
import com.example.gpstest.domain.model.CapabilityProbeRow
import com.example.gpstest.domain.model.CapabilityState
import com.example.gpstest.domain.model.GnssCapabilitiesInfo
import com.example.gpstest.domain.model.GnssSatellite
import com.example.gpstest.domain.model.ProbeDiagnostic
import com.example.gpstest.domain.model.ProbeOutcome
import com.example.gpstest.domain.model.toCapabilityState

object CapabilityProbeEvaluator {
    const val DEFAULT_WINDOW_MS: Long = 30_000L

    fun evaluate(
        capabilities: GnssCapabilitiesInfo?,
        evidence: CapabilityEvidence,
        windowMs: Long = DEFAULT_WINDOW_MS,
    ): List<CapabilityProbeRow> =
        listOf(
            evaluateOne(
                key = CapabilityKey.MEASUREMENTS,
                declaredCode = capabilities?.hasMeasurements,
                seen = evidence.measurementsSeen,
                evidence = evidence,
                windowMs = windowMs,
            ),
            evaluateOne(
                key = CapabilityKey.NAVIGATION_MESSAGES,
                declaredCode = capabilities?.hasNavigationMessages,
                seen = evidence.navigationMessagesSeen,
                evidence = evidence,
                windowMs = windowMs,
            ),
            evaluateOne(
                key = CapabilityKey.ANTENNA_INFO,
                declaredCode = capabilities?.hasAntennaInfo,
                seen = evidence.antennaInfoSeen,
                evidence = evidence,
                windowMs = windowMs,
            ),
        )

    private fun evaluateOne(
        key: CapabilityKey,
        declaredCode: Int?,
        seen: Boolean,
        evidence: CapabilityEvidence,
        windowMs: Long,
    ): CapabilityProbeRow {
        val declared =
            when (declaredCode) {
                null -> CapabilityState.UNKNOWN
                else -> declaredCode.toCapabilityState()
            }

        if (!evidence.listeningStarted) {
            return CapabilityProbeRow(
                key = key,
                declared = declared,
                observed = ProbeOutcome.UNKNOWN,
                diagnostic = ProbeDiagnostic.NOT_LISTENING,
                remainingMs = null,
            )
        }

        if (seen) {
            val diagnostic =
                when (declared) {
                    CapabilityState.SUPPORTED -> ProbeDiagnostic.DECLARED_SUPPORTED_OBSERVED
                    CapabilityState.UNSUPPORTED,
                    CapabilityState.UNKNOWN,
                    -> ProbeDiagnostic.DECLARED_UNKNOWN_OBSERVED
                }
            return CapabilityProbeRow(
                key = key,
                declared = declared,
                observed = ProbeOutcome.OBSERVED,
                diagnostic = diagnostic,
                remainingMs = null,
            )
        }

        if (declared == CapabilityState.UNSUPPORTED) {
            return CapabilityProbeRow(
                key = key,
                declared = declared,
                observed = ProbeOutcome.NOT_APPLICABLE,
                diagnostic = ProbeDiagnostic.DECLARED_UNSUPPORTED,
                remainingMs = null,
            )
        }

        val elapsed = evidence.elapsedMs.coerceAtLeast(0L)
        if (elapsed < windowMs) {
            val diagnostic =
                when (declared) {
                    CapabilityState.SUPPORTED -> ProbeDiagnostic.DECLARED_SUPPORTED_OBSERVING
                    CapabilityState.UNKNOWN -> ProbeDiagnostic.DECLARED_UNKNOWN_OBSERVING
                    CapabilityState.UNSUPPORTED -> ProbeDiagnostic.DECLARED_UNSUPPORTED
                }
            return CapabilityProbeRow(
                key = key,
                declared = declared,
                observed = ProbeOutcome.OBSERVING,
                diagnostic = diagnostic,
                remainingMs = windowMs - elapsed,
            )
        }

        val diagnostic =
            when (declared) {
                CapabilityState.SUPPORTED -> ProbeDiagnostic.DECLARED_SUPPORTED_NOT_OBSERVED
                CapabilityState.UNKNOWN -> ProbeDiagnostic.DECLARED_UNKNOWN_NOT_OBSERVED
                CapabilityState.UNSUPPORTED -> ProbeDiagnostic.DECLARED_UNSUPPORTED
            }
        return CapabilityProbeRow(
            key = key,
            declared = declared,
            observed = ProbeOutcome.NOT_OBSERVED,
            diagnostic = diagnostic,
            remainingMs = null,
        )
    }
}

fun GnssSatellite.hasMeasurementEvidence(): Boolean =
    measurementState != null ||
        measurementCn0DbHz != null ||
        pseudorangeRateMetersPerSecond != null ||
        accumulatedDeltaRangeMeters != null

fun List<GnssSatellite>.anyMeasurementEvidence(): Boolean = any { it.hasMeasurementEvidence() }
```

- [ ] **步骤 5：运行测试验证通过**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
.\gradlew.bat testDebugUnitTest --tests com.example.gpstest.domain.util.CapabilityProbeEvaluatorTest
```

预期：全部 PASS。

- [ ] **步骤 6：Commit**

```powershell
git add app/src/main/java/com/example/gpstest/domain/model/CapabilityProbe.kt `
  app/src/main/java/com/example/gpstest/domain/util/CapabilityProbeEvaluator.kt `
  app/src/test/java/com/example/gpstest/domain/util/CapabilityProbeEvaluatorTest.kt
git commit -m "feat: add capability probe evaluator for declare-vs-observe"
```

---

### 任务 2：SatelliteViewModel 累计证据并暴露 capabilityProbes

**文件：**

- 修改：`app/src/main/java/com/example/gpstest/viewmodel/SatelliteViewModel.kt`

- [ ] **步骤 1：增加导入与状态字段**

在现有 import 区增加：

```kotlin
import android.os.SystemClock
import com.example.gpstest.domain.model.CapabilityEvidence
import com.example.gpstest.domain.model.CapabilityProbeRow
import com.example.gpstest.domain.util.CapabilityProbeEvaluator
import com.example.gpstest.domain.util.anyMeasurementEvidence
```

在 `_gnssCapabilities` 附近增加：

```kotlin
private val _capabilityProbes = MutableStateFlow<List<CapabilityProbeRow>>(emptyList())
val capabilityProbes: StateFlow<List<CapabilityProbeRow>> = _capabilityProbes.asStateFlow()

private var probeListeningStarted: Boolean = false
private var probeStartElapsedRealtimeMs: Long = 0L
private var measurementsSeen: Boolean = false
private var navigationMessagesSeen: Boolean = false
private var antennaInfoSeen: Boolean = false
private var probeTickerJob: Job? = null
```

- [ ] **步骤 2：实现 recompute / reset / ticker 私有方法**

在 `loadCapabilities()` 之后添加：

```kotlin
private fun resetCapabilityProbeSession() {
    probeListeningStarted = true
    probeStartElapsedRealtimeMs = SystemClock.elapsedRealtime()
    measurementsSeen = false
    navigationMessagesSeen = false
    antennaInfoSeen = false
    recomputeCapabilityProbes()
    startProbeTicker()
}

private fun recomputeCapabilityProbes() {
    val elapsed =
        if (probeListeningStarted) {
            (SystemClock.elapsedRealtime() - probeStartElapsedRealtimeMs).coerceAtLeast(0L)
        } else {
            0L
        }
    _capabilityProbes.value =
        CapabilityProbeEvaluator.evaluate(
            capabilities = _gnssCapabilities.value,
            evidence =
                CapabilityEvidence(
                    listeningStarted = probeListeningStarted,
                    elapsedMs = elapsed,
                    measurementsSeen = measurementsSeen,
                    navigationMessagesSeen = navigationMessagesSeen,
                    antennaInfoSeen = antennaInfoSeen,
                ),
        )
}

private fun startProbeTicker() {
    probeTickerJob?.cancel()
    probeTickerJob =
        viewModelScope.launch {
            while (isActive && probeListeningStarted) {
                recomputeCapabilityProbes()
                val stillObserving =
                    _capabilityProbes.value.any { it.observed == com.example.gpstest.domain.model.ProbeOutcome.OBSERVING }
                if (!stillObserving) break
                delay(1_000L)
            }
        }
}
```

同时：`loadCapabilities()` 成功写入 `_gnssCapabilities` 后调用 `recomputeCapabilityProbes()`，使声明变化时 rows 更新。

```kotlin
private fun loadCapabilities() {
    viewModelScope.launch {
        try {
            _gnssCapabilities.value = repository.getGnssCapabilities()
        } catch (e: Exception) {
            _gnssCapabilities.value = null
        }
        recomputeCapabilityProbes()
    }
}
```

- [ ] **步骤 3：在 startListening / 数据回调中接线**

在 `startListening()` 开头（取消 jobs 之后、启动新 jobs 之前）调用：

```kotlin
resetCapabilityProbeSession()
```

`navigationJob` 的 collect 内，在处理帧时：

```kotlin
repository.getNavigationMessages().collect { frame ->
    if (!navigationMessagesSeen) {
        navigationMessagesSeen = true
        recomputeCapabilityProbes()
    }
    ephemerisStore.add(frame)?.let { ephemeris -> ephemerides[ephemeris.svid] = ephemeris }
}
```

`antennaJob` 的 collect 内：

```kotlin
repository.getAntennaInfos().collect { list ->
    _antennaInfos.value = list
    if (!antennaInfoSeen && list.isNotEmpty()) {
        antennaInfoSeen = true
        recomputeCapabilityProbes()
    }
}
```

主 `collectionJob` 的 `collect` 内，在拿到 `satellites` 后：

```kotlin
if (!measurementsSeen && satellites.anyMeasurementEvidence()) {
    measurementsSeen = true
    recomputeCapabilityProbes()
}
```

`onCleared()` 中增加：

```kotlin
probeTickerJob?.cancel()
probeListeningStarted = false
```

（可选）`onCleared` 后 `recomputeCapabilityProbes()` 非必须，ViewModel 已销毁。

- [ ] **步骤 4：编译检查**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
.\gradlew.bat :app:compileDebugKotlin
```

预期：BUILD SUCCESSFUL。

- [ ] **步骤 5：Commit**

```powershell
git add app/src/main/java/com/example/gpstest/viewmodel/SatelliteViewModel.kt
git commit -m "feat: accumulate GNSS capability probe evidence in ViewModel"
```

---

### 任务 3：字符串资源（中/英）

**文件：**

- 修改：`app/src/main/res/values/strings.xml`（在现有 `cap_unknown` 附近追加）
- 修改：`app/src/main/res/values-en/strings.xml`（追加对应英文；若文件中尚无 cap_* 段，在文件末尾 `</resources>` 前追加完整探针相关字符串，并补齐与中文卡共用的 `cap_*` 标签键以免 en locale 缺资源）

- [ ] **步骤 1：中文资源**

在 `values/strings.xml` 的 `cap_unknown` 后追加：

```xml
    <string name="cap_declared">声明</string>
    <string name="cap_observed_label">实测</string>
    <string name="cap_probe_observed">已观测</string>
    <string name="cap_probe_not_observed">未观测</string>
    <string name="cap_probe_observing_seconds">观察中 (%1$d秒)</string>
    <string name="cap_probe_not_applicable">不适用</string>
    <string name="cap_probe_unknown">未知</string>
    <string name="cap_diag_supported_observed">声明支持，已观测到数据</string>
    <string name="cap_diag_supported_observing">声明支持，正在等待数据…</string>
    <string name="cap_diag_supported_not_observed">声明支持，但 30 秒内未观测到。可能原因：OEM 声明虚高、省电限制、无天空视野等</string>
    <string name="cap_diag_unsupported">设备不支持，对应区域为空属正常</string>
    <string name="cap_diag_unknown_observed">能力查询不可用或与声明不一致，但已观测到数据</string>
    <string name="cap_diag_unknown_observing">无法确认声明支持情况，正在观察…</string>
    <string name="cap_diag_unknown_not_observed">无法确认声明支持情况，30 秒内未观测到数据</string>
    <string name="cap_diag_not_listening">开始 GNSS 监听后进行实测</string>
```

- [ ] **步骤 2：英文资源**

在 `values-en/strings.xml` 追加（若 en 文件缺少基础 cap 标签，一并补上）：

```xml
    <string name="gnss_capabilities_title">Device GNSS capabilities</string>
    <string name="cap_hardware_model">Hardware model</string>
    <string name="cap_hardware_year">Hardware year</string>
    <string name="cap_measurements">Raw measurements</string>
    <string name="cap_navigation_messages">Navigation messages</string>
    <string name="cap_antenna_info">Antenna info</string>
    <string name="cap_accumulated_delta_range">Accumulated delta range</string>
    <string name="cap_measurement_corrections">Measurement corrections</string>
    <string name="cap_measurement_correlation_vectors">Measurement correlation vectors</string>
    <string name="cap_supported">Supported</string>
    <string name="cap_unsupported">Unsupported</string>
    <string name="cap_unknown">Unknown</string>
    <string name="cap_declared">Declared</string>
    <string name="cap_observed_label">Observed</string>
    <string name="cap_probe_observed">Seen</string>
    <string name="cap_probe_not_observed">Not seen</string>
    <string name="cap_probe_observing_seconds">Observing (%1$ds)</string>
    <string name="cap_probe_not_applicable">N/A</string>
    <string name="cap_probe_unknown">Unknown</string>
    <string name="cap_diag_supported_observed">Declared supported; data observed</string>
    <string name="cap_diag_supported_observing">Declared supported; waiting for data…</string>
    <string name="cap_diag_supported_not_observed">Declared supported, but nothing observed in 30s. Possible causes: OEM over-claim, power saving, poor sky view</string>
    <string name="cap_diag_unsupported">Device does not support this; empty UI is expected</string>
    <string name="cap_diag_unknown_observed">Capability query unavailable or inconsistent; data was observed</string>
    <string name="cap_diag_unknown_observing">Cannot confirm declared support; observing…</string>
    <string name="cap_diag_unknown_not_observed">Cannot confirm declared support; nothing observed in 30s</string>
    <string name="cap_diag_not_listening">Start GNSS listening to run observation</string>
```

注意：若 en 文件已有部分 `gnss_capabilities_*` / `cap_*`，只追加缺失项，避免 duplicate resource。

- [ ] **步骤 3：Commit**

```powershell
git add app/src/main/res/values/strings.xml app/src/main/res/values-en/strings.xml
git commit -m "feat: add strings for GNSS capability probe diagnostics"
```

---

### 任务 4：扩展 GnssCapabilitiesCard + 诊断页接线

**文件：**

- 修改：`app/src/main/java/com/example/gpstest/ui/components/GnssCapabilitiesCard.kt`
- 修改：`app/src/main/java/com/example/gpstest/ui/screens/diagnostics/ReceiverDiagnosticsScreen.kt`

- [ ] **步骤 1：重写能力卡以支持 probes**

将 `GnssCapabilitiesCard` 替换为以下结构（保留 ADR 等声明-only 行）：

```kotlin
package com.example.gpstest.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gpstest.R
import com.example.gpstest.domain.model.CapabilityKey
import com.example.gpstest.domain.model.CapabilityProbeRow
import com.example.gpstest.domain.model.CapabilityState
import com.example.gpstest.domain.model.GnssCapabilitiesInfo
import com.example.gpstest.domain.model.ProbeDiagnostic
import com.example.gpstest.domain.model.ProbeOutcome
import com.example.gpstest.domain.model.toCapabilityState
import kotlin.math.ceil

@Composable
fun GnssCapabilitiesCard(
    capabilities: GnssCapabilitiesInfo,
    probes: List<CapabilityProbeRow> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val probeByKey = probes.associateBy { it.key }

    GpsCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GpsCardTitle(text = stringResource(R.string.gnss_capabilities_title))

            CapabilityRow(
                label = stringResource(R.string.cap_hardware_model),
                value = capabilities.hardwareModelName ?: stringResource(R.string.value_not_available),
            )
            CapabilityRow(
                label = stringResource(R.string.cap_hardware_year),
                value = capabilities.yearOfHardware ?: stringResource(R.string.value_not_available),
            )

            ProbeOrStateRow(
                label = stringResource(R.string.cap_measurements),
                declaredCode = capabilities.hasMeasurements,
                probe = probeByKey[CapabilityKey.MEASUREMENTS],
            )
            ProbeOrStateRow(
                label = stringResource(R.string.cap_navigation_messages),
                declaredCode = capabilities.hasNavigationMessages,
                probe = probeByKey[CapabilityKey.NAVIGATION_MESSAGES],
            )
            ProbeOrStateRow(
                label = stringResource(R.string.cap_antenna_info),
                declaredCode = capabilities.hasAntennaInfo,
                probe = probeByKey[CapabilityKey.ANTENNA_INFO],
            )

            capabilities.hasAccumulatedDeltaRange?.let {
                CapabilityStateRow(
                    label = stringResource(R.string.cap_accumulated_delta_range),
                    state = it.toCapabilityState(),
                )
            }
            capabilities.hasMeasurementCorrections?.let {
                CapabilityStateRow(
                    label = stringResource(R.string.cap_measurement_corrections),
                    state = it.toCapabilityState(),
                )
            }
            capabilities.hasMeasurementCorrelationVectors?.let {
                CapabilityStateRow(
                    label = stringResource(R.string.cap_measurement_correlation_vectors),
                    state = it.toCapabilityState(),
                )
            }
        }
    }
}

@Composable
private fun ProbeOrStateRow(
    label: String,
    declaredCode: Int?,
    probe: CapabilityProbeRow?,
) {
    if (probe != null) {
        CapabilityProbeBlock(label = label, probe = probe)
        return
    }
    declaredCode?.let {
        CapabilityStateRow(label = label, state = it.toCapabilityState())
    }
}

@Composable
private fun CapabilityProbeBlock(
    label: String,
    probe: CapabilityProbeRow,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        CapabilityRow(
            label = stringResource(R.string.cap_declared),
            value = capabilityStateLabel(probe.declared),
        )
        CapabilityRow(
            label = stringResource(R.string.cap_observed_label),
            value = probeOutcomeLabel(probe),
        )
        Text(
            text = stringResource(probeDiagnosticRes(probe.diagnostic)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun capabilityStateLabel(state: CapabilityState): String =
    stringResource(
        when (state) {
            CapabilityState.SUPPORTED -> R.string.cap_supported
            CapabilityState.UNSUPPORTED -> R.string.cap_unsupported
            CapabilityState.UNKNOWN -> R.string.cap_unknown
        },
    )

@Composable
private fun probeOutcomeLabel(probe: CapabilityProbeRow): String =
    when (probe.observed) {
        ProbeOutcome.OBSERVED -> stringResource(R.string.cap_probe_observed)
        ProbeOutcome.NOT_OBSERVED -> stringResource(R.string.cap_probe_not_observed)
        ProbeOutcome.OBSERVING -> {
            val seconds = ceil((probe.remainingMs ?: 0L) / 1000.0).toInt().coerceAtLeast(0)
            stringResource(R.string.cap_probe_observing_seconds, seconds)
        }
        ProbeOutcome.NOT_APPLICABLE -> stringResource(R.string.cap_probe_not_applicable)
        ProbeOutcome.UNKNOWN -> stringResource(R.string.cap_probe_unknown)
    }

private fun probeDiagnosticRes(diagnostic: ProbeDiagnostic): Int =
    when (diagnostic) {
        ProbeDiagnostic.DECLARED_SUPPORTED_OBSERVED -> R.string.cap_diag_supported_observed
        ProbeDiagnostic.DECLARED_SUPPORTED_OBSERVING -> R.string.cap_diag_supported_observing
        ProbeDiagnostic.DECLARED_SUPPORTED_NOT_OBSERVED -> R.string.cap_diag_supported_not_observed
        ProbeDiagnostic.DECLARED_UNSUPPORTED -> R.string.cap_diag_unsupported
        ProbeDiagnostic.DECLARED_UNKNOWN_OBSERVED -> R.string.cap_diag_unknown_observed
        ProbeDiagnostic.DECLARED_UNKNOWN_OBSERVING -> R.string.cap_diag_unknown_observing
        ProbeDiagnostic.DECLARED_UNKNOWN_NOT_OBSERVED -> R.string.cap_diag_unknown_not_observed
        ProbeDiagnostic.NOT_LISTENING -> R.string.cap_diag_not_listening
    }

@Composable
private fun CapabilityStateRow(
    label: String,
    state: CapabilityState,
) {
    CapabilityRow(label = label, value = capabilityStateLabel(state))
}

@Composable
private fun CapabilityRow(
    label: String,
    value: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
    }
}
```

- [ ] **步骤 2：诊断页接线**

在 `ReceiverDiagnosticsScreen` 中：

```kotlin
val capabilityProbes by viewModel.capabilityProbes.collectAsState()
```

并将：

```kotlin
GnssCapabilitiesCard(capabilities = capabilities)
```

改为：

```kotlin
GnssCapabilitiesCard(
    capabilities = capabilities,
    probes = capabilityProbes,
)
```

- [ ] **步骤 3：编译 + 领域单测**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
.\gradlew.bat testDebugUnitTest --tests com.example.gpstest.domain.util.CapabilityProbeEvaluatorTest
.\gradlew.bat :app:compileDebugKotlin
```

预期：测试 PASS，编译成功。

- [ ] **步骤 4：Commit**

```powershell
git add app/src/main/java/com/example/gpstest/ui/components/GnssCapabilitiesCard.kt `
  app/src/main/java/com/example/gpstest/ui/screens/diagnostics/ReceiverDiagnosticsScreen.kt
git commit -m "feat: show declare-vs-observe probes on capabilities card"
```

---

### 任务 5：全量单元测试与规格状态收尾

**文件：**

- 修改：`docs/superpowers/specs/2026-07-19-gnss-capability-probe-design.md`（状态改为「已实现」）
- 可选：`TODO.md` 增加一行完成记录（若仓库习惯维护 TODO）

- [ ] **步骤 1：运行相关/全量单元测试**

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
.\gradlew.bat testDebugUnitTest
```

预期：BUILD SUCCESSFUL，无失败测试。

- [ ] **步骤 2：更新规格状态**

将设计文档头部 `状态: 待用户审查` 改为 `状态: 已实现`。

- [ ] **步骤 3：Commit**

```powershell
git add docs/superpowers/specs/2026-07-19-gnss-capability-probe-design.md
git commit -m "docs: mark capability probe design implemented"
```

---

## 规格覆盖自检

| 规格需求 | 任务 |
|----------|------|
| 核心三项声明 vs 实测 | 任务 1–4 |
| 30s 固定观察窗 | 任务 1（Evaluator）+ 任务 2（ticker） |
| 领域纯函数可单测 | 任务 1 |
| 扩展现有能力卡 | 任务 4 |
| 不测 ADR/Corrections 实测 | 任务 4 保持声明-only |
| 诊断文案非武断 | 任务 3 `cap_diag_supported_not_observed` |
| sticky 证据 + startListening 重置 | 任务 2 |
| measurement 证据字段规则 | 任务 1 扩展函数 + 测试 |
| 中英文字符串 | 任务 3 |

## 类型一致性自检

- `CapabilityProbeRow` / `CapabilityEvidence` / `ProbeOutcome` / `ProbeDiagnostic` / `CapabilityKey` 在任务 1 定义，任务 2–4 仅使用这些名字。
- `CapabilityProbeEvaluator.DEFAULT_WINDOW_MS = 30_000L` 与文案「30 秒」一致。
- ViewModel 对外仅 `capabilityProbes: StateFlow<List<CapabilityProbeRow>>`。
- Card 参数：`capabilities` + `probes`（默认 empty → 兼容无探针调用）。

## 占位符扫描

无 TBD /「适当处理」类步骤；测试代码与实现代码均已写出。
