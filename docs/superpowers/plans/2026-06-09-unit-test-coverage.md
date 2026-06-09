# 补充单元测试 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 为零覆盖的领域层文件编写 JUnit 4 单元测试，并补充现有测试的边界场景。

**架构：** 纯 JUnit 4 单元测试，无 mock 框架。每个测试文件与源文件 1:1 对应，包路径镜像源码包路径。测试使用反引号描述性方法名，通过 `private fun makeXxx()` 工厂函数构建测试数据。

**技术栈：** JUnit 4.13.2，Kotlin 2.1.0，kotlinx-serialization-json 1.7.3

---

## 文件结构

### 新建测试文件

| 文件 | 职责 |
|------|------|
| `app/src/test/java/com/example/gpstest/domain/model/GnssSatelliteTest.kt` | 测试 `GnssSatellite` 的 `group`、`signalStrength`、6 个 bitmask 属性，以及 `MultipathIndicator.fromInt()` |
| `app/src/test/java/com/example/gpstest/data/validator/XtraDataValidatorTest.kt` | 测试 `XtraDataValidator.validate()` 全流程、`getSizeStatistics()` |
| `app/src/test/java/com/example/gpstest/domain/model/SatelliteDisplayNameTest.kt` | 测试 `GnssSatellite.getDisplayName()` 和 `SatelliteHistoryEntry.getDisplayName()` 扩展函数 |

### 修改现有测试文件

| 文件 | 追加内容 |
|------|---------|
| `app/src/test/java/com/example/gpstest/domain/model/ConstellationTest.kt` | 补充 `shortName` 和 `constellationType` 属性断言 |
| `app/src/test/java/com/example/gpstest/domain/model/GnssDataTest.kt` | 补充负值 cn0 过滤、空列表 baseband 测试 |
| `app/src/test/java/com/example/gpstest/domain/model/GnssClockDataTest.kt` | 补充负值偏移、零漂移、双 null 测试 |
| `app/src/test/java/com/example/gpstest/domain/model/SatelliteHistoryTest.kt` | 补充 `SatelliteHistoryConfig` 默认值测试 |
| `app/src/test/java/com/example/gpstest/domain/util/DopCalculatorTest.kt` | 补充零仰角边界、同方位角退化测试 |

---

## 关键约定

- **Android `GnssMeasurement` 常量值**（在 `GnssSatelliteTest` 中直接使用 int 值）：
  - `ADR_STATE_VALID = 1`（bit 0）
  - `ADR_STATE_CYCLE_SLIP = 4`（bit 2）
  - `STATE_TOW_DECODED = 2`（bit 1）
  - `STATE_CODE_LOCK = 1`（bit 0）
  - `STATE_BIT_SYNC = 4`（bit 2）
  - `STATE_SUBFRAME_SYNC = 8`（bit 3）

- **`XtraDataValidator` 测试注意事项**：构造时必须显式传入 `strictMode` 参数（如 `XtraDataValidator(strictMode = true)`），避免依赖 `BuildConfig.DEBUG`。`android.util.Log` 调用在纯 JUnit 中会抛异常，但 `validate()` 方法中的 `Log.i()` 只在验证通过时执行，而 `validateMimeType()` 中的 `Log.w()` 只在非严格模式或未知 MIME 时执行。使用 `strictMode = true` + 合法数据可绕过所有 `Log` 调用。

- **`getDisplayName()` 扩展函数**：定义在 `ui.components` 包，但操作的是 `domain.model` 的 data class。测试文件放在 `domain.model` 包以保持 1:1 文件对应——只需在测试中 `import com.example.gpstest.ui.components.getDisplayName`。

- **运行测试命令**：
  ```bash
  $env:JAVA_HOME="C:\Program Files\Java\jdk-21"; .\gradlew test --tests "com.example.gpstest.domain.model.GnssSatelliteTest" -q
  ```
  全部测试：
  ```bash
  $env:JAVA_HOME="C:\Program Files\Java\jdk-21"; .\gradlew test -q
  ```

- **`GnssSatellite` 构造必填参数**（按声明顺序，无默认值的参数）：
  ```kotlin
  GnssSatellite(
      svid: Int,
      constellation: Constellation,
      rawConstellationType: Int,
      cn0DbHz: Float,
      azimuthDegrees: Float,
      elevationDegrees: Float,
      hasAlmanac: Boolean,
      hasEphemeris: Boolean,
      usedInFix: Boolean,
      carrierFrequencyHz: Float?,
      carrierCycles: Long?,
      dopplerShiftHz: Double?,
      timeNanos: Long,
      // 以下有默认值，可省略：
      // agcLevelDb: Double? = null,
      // multipathIndicator: MultipathIndicator? = null,
      // basebandCn0DbHz: Float? = null,
      // accumulatedDeltaRangeMeters: Double? = null,
      // accumulatedDeltaRangeState: Int? = null,
      // accumulatedDeltaRangeUncertaintyMeters: Double? = null,
      // receivedSvTimeNanos: Long? = null,
      // receivedSvTimeUncertaintyNanos: Double? = null,
      // pseudorangeRateMetersPerSecond: Double? = null,
      // measurementState: Int? = null,
      // measurementCn0DbHz: Double? = null,
      // fullCarrierPhaseCycleCount: Long? = null,
  )
  ```

---

## 任务 1：GnssSatellite.group 和 signalStrength

**文件：**
- 创建：`app/src/test/java/com/example/gpstest/domain/model/GnssSatelliteTest.kt`
- 测试源：`app/src/main/java/com/example/gpstest/domain/model/GnssSatellite.kt`

- [ ] **步骤 1：编写测试文件**

创建 `app/src/test/java/com/example/gpstest/domain/model/GnssSatelliteTest.kt`：

```kotlin
package com.example.gpstest.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GnssSatelliteTest {
    private fun makeSatellite(
        usedInFix: Boolean = false,
        cn0DbHz: Float = 30f,
        constellation: Constellation = Constellation.GPS,
        rawConstellationType: Int = constellation.constellationType,
        accumulatedDeltaRangeState: Int? = null,
        measurementState: Int? = null,
    ): GnssSatellite =
        GnssSatellite(
            svid = 1,
            constellation = constellation,
            rawConstellationType = rawConstellationType,
            cn0DbHz = cn0DbHz,
            azimuthDegrees = 45f,
            elevationDegrees = 30f,
            hasAlmanac = true,
            hasEphemeris = true,
            usedInFix = usedInFix,
            carrierFrequencyHz = null,
            carrierCycles = null,
            dopplerShiftHz = null,
            timeNanos = 0L,
            accumulatedDeltaRangeState = accumulatedDeltaRangeState,
            measurementState = measurementState,
        )

    // --- group ---

    @Test
    fun `group is USED_IN_FIX when usedInFix is true`() {
        val sat = makeSatellite(usedInFix = true, cn0DbHz = 0f)
        assertEquals(SatelliteGroup.USED_IN_FIX, sat.group)
    }

    @Test
    fun `group is USED_IN_FIX even when cn0 is positive`() {
        val sat = makeSatellite(usedInFix = true, cn0DbHz = 35f)
        assertEquals(SatelliteGroup.USED_IN_FIX, sat.group)
    }

    @Test
    fun `group is VISIBLE_ONLY when not used in fix but cn0 is positive`() {
        val sat = makeSatellite(usedInFix = false, cn0DbHz = 25f)
        assertEquals(SatelliteGroup.VISIBLE_ONLY, sat.group)
    }

    @Test
    fun `group is VISIBLE_ONLY when cn0 is very small positive`() {
        val sat = makeSatellite(usedInFix = false, cn0DbHz = 0.1f)
        assertEquals(SatelliteGroup.VISIBLE_ONLY, sat.group)
    }

    @Test
    fun `group is SEARCHING when not used in fix and cn0 is zero`() {
        val sat = makeSatellite(usedInFix = false, cn0DbHz = 0f)
        assertEquals(SatelliteGroup.SEARCHING, sat.group)
    }

    @Test
    fun `group is SEARCHING when not used in fix and cn0 is negative`() {
        val sat = makeSatellite(usedInFix = false, cn0DbHz = -1f)
        assertEquals(SatelliteGroup.SEARCHING, sat.group)
    }

    // --- signalStrength ---

    @Test
    fun `signalStrength is STRONG when cn0 is 35`() {
        val sat = makeSatellite(cn0DbHz = 35f)
        assertEquals(SignalStrength.STRONG, sat.signalStrength)
    }

    @Test
    fun `signalStrength is STRONG when cn0 is above 35`() {
        val sat = makeSatellite(cn0DbHz = 45f)
        assertEquals(SignalStrength.STRONG, sat.signalStrength)
    }

    @Test
    fun `signalStrength is MEDIUM when cn0 is 34`() {
        val sat = makeSatellite(cn0DbHz = 34f)
        assertEquals(SignalStrength.MEDIUM, sat.signalStrength)
    }

    @Test
    fun `signalStrength is MEDIUM when cn0 is 25`() {
        val sat = makeSatellite(cn0DbHz = 25f)
        assertEquals(SignalStrength.MEDIUM, sat.signalStrength)
    }

    @Test
    fun `signalStrength is WEAK when cn0 is 24`() {
        val sat = makeSatellite(cn0DbHz = 24f)
        assertEquals(SignalStrength.WEAK, sat.signalStrength)
    }

    @Test
    fun `signalStrength is WEAK when cn0 is zero`() {
        val sat = makeSatellite(cn0DbHz = 0f)
        assertEquals(SignalStrength.WEAK, sat.signalStrength)
    }

    @Test
    fun `signalStrength is WEAK when cn0 is negative`() {
        val sat = makeSatellite(cn0DbHz = -5f)
        assertEquals(SignalStrength.WEAK, sat.signalStrength)
    }

    // --- MultipathIndicator.fromInt ---

    @Test
    fun `MultipathIndicator fromInt 0 is UNKNOWN`() {
        assertEquals(MultipathIndicator.UNKNOWN, MultipathIndicator.fromInt(0))
    }

    @Test
    fun `MultipathIndicator fromInt 1 is DETECTED`() {
        assertEquals(MultipathIndicator.DETECTED, MultipathIndicator.fromInt(1))
    }

    @Test
    fun `MultipathIndicator fromInt 2 is NOT_DETECTED`() {
        assertEquals(MultipathIndicator.NOT_DETECTED, MultipathIndicator.fromInt(2))
    }

    @Test
    fun `MultipathIndicator fromInt negative is UNKNOWN`() {
        assertEquals(MultipathIndicator.UNKNOWN, MultipathIndicator.fromInt(-1))
    }

    @Test
    fun `MultipathIndicator fromInt 99 is UNKNOWN`() {
        assertEquals(MultipathIndicator.UNKNOWN, MultipathIndicator.fromInt(99))
    }
}
```

- [ ] **步骤 2：运行测试验证通过**

运行：
```bash
$env:JAVA_HOME="C:\Program Files\Java\jdk-21"; .\gradlew test --tests "com.example.gpstest.domain.model.GnssSatelliteTest" -q
```
预期：18 个测试全部 PASS（group 6 + signalStrength 7 + MultipathIndicator 4 + 测试类本身 1 = 18 tests）。这些测试测试的是已有的计算属性，不需要修改源码。

- [ ] **步骤 3：Commit**

```bash
git add app/src/test/java/com/example/gpstest/domain/model/GnssSatelliteTest.kt
git commit -m "test: add GnssSatellite group, signalStrength, and MultipathIndicator tests"
```

---

## 任务 2：GnssSatellite bitmask 属性

**文件：**
- 修改：`app/src/test/java/com/example/gpstest/domain/model/GnssSatelliteTest.kt`
- 测试源：`app/src/main/java/com/example/gpstest/domain/model/GnssSatellite.kt:89-128`

- [ ] **步骤 1：在 `GnssSatelliteTest.kt` 末尾追加 bitmask 测试**

在 `GnssSatelliteTest.kt` 的最后一个 `}` 之前追加：

```kotlin
    // --- bitmask: isAdrValid / hasCycleSlip ---

    @Test
    fun `isAdrValid is true when ADR_STATE_VALID bit is set`() {
        val sat = makeSatellite(accumulatedDeltaRangeState = 1)
        assertEquals(true, sat.isAdrValid)
    }

    @Test
    fun `isAdrValid is true when multiple bits including ADR_STATE_VALID are set`() {
        val sat = makeSatellite(accumulatedDeltaRangeState = 5)
        assertEquals(true, sat.isAdrValid)
    }

    @Test
    fun `isAdrValid is false when ADR_STATE_VALID bit is not set`() {
        val sat = makeSatellite(accumulatedDeltaRangeState = 4)
        assertEquals(false, sat.isAdrValid)
    }

    @Test
    fun `isAdrValid is false when accumulatedDeltaRangeState is null`() {
        val sat = makeSatellite(accumulatedDeltaRangeState = null)
        assertEquals(false, sat.isAdrValid)
    }

    @Test
    fun `hasCycleSlip is true when ADR_STATE_CYCLE_SLIP bit is set`() {
        val sat = makeSatellite(accumulatedDeltaRangeState = 4)
        assertEquals(true, sat.hasCycleSlip)
    }

    @Test
    fun `hasCycleSlip is false when ADR_STATE_CYCLE_SLIP bit is not set`() {
        val sat = makeSatellite(accumulatedDeltaRangeState = 1)
        assertEquals(false, sat.hasCycleSlip)
    }

    @Test
    fun `hasCycleSlip is false when accumulatedDeltaRangeState is null`() {
        val sat = makeSatellite(accumulatedDeltaRangeState = null)
        assertEquals(false, sat.hasCycleSlip)
    }

    // --- bitmask: measurementState properties ---

    @Test
    fun `hasCarrierPhaseLock is true when STATE_TOW_DECODED bit is set`() {
        val sat = makeSatellite(measurementState = 2)
        assertEquals(true, sat.hasCarrierPhaseLock)
    }

    @Test
    fun `hasCarrierPhaseLock is false when STATE_TOW_DECODED bit is not set`() {
        val sat = makeSatellite(measurementState = 1)
        assertEquals(false, sat.hasCarrierPhaseLock)
    }

    @Test
    fun `hasCarrierPhaseLock is false when measurementState is null`() {
        val sat = makeSatellite(measurementState = null)
        assertEquals(false, sat.hasCarrierPhaseLock)
    }

    @Test
    fun `hasCodeLock is true when STATE_CODE_LOCK bit is set`() {
        val sat = makeSatellite(measurementState = 1)
        assertEquals(true, sat.hasCodeLock)
    }

    @Test
    fun `hasCodeLock is false when STATE_CODE_LOCK bit is not set`() {
        val sat = makeSatellite(measurementState = 2)
        assertEquals(false, sat.hasCodeLock)
    }

    @Test
    fun `hasBitSync is true when STATE_BIT_SYNC bit is set`() {
        val sat = makeSatellite(measurementState = 4)
        assertEquals(true, sat.hasBitSync)
    }

    @Test
    fun `hasBitSync is false when STATE_BIT_SYNC bit is not set`() {
        val sat = makeSatellite(measurementState = 1)
        assertEquals(false, sat.hasBitSync)
    }

    @Test
    fun `hasSubframeSync is true when STATE_SUBFRAME_SYNC bit is set`() {
        val sat = makeSatellite(measurementState = 8)
        assertEquals(true, sat.hasSubframeSync)
    }

    @Test
    fun `hasSubframeSync is false when STATE_SUBFRAME_SYNC bit is not set`() {
        val sat = makeSatellite(measurementState = 4)
        assertEquals(false, sat.hasSubframeSync)
    }

    @Test
    fun `multiple measurementState bits can be set simultaneously`() {
        val sat = makeSatellite(measurementState = 15)
        assertEquals(true, sat.hasCarrierPhaseLock)
        assertEquals(true, sat.hasCodeLock)
        assertEquals(true, sat.hasBitSync)
        assertEquals(true, sat.hasSubframeSync)
    }

    @Test
    fun `all bitmask properties are false when measurementState is zero`() {
        val sat = makeSatellite(measurementState = 0)
        assertEquals(false, sat.hasCarrierPhaseLock)
        assertEquals(false, sat.hasCodeLock)
        assertEquals(false, sat.hasBitSync)
        assertEquals(false, sat.hasSubframeSync)
    }
```

- [ ] **步骤 2：运行测试验证通过**

运行：
```bash
$env:JAVA_HOME="C:\Program Files\Java\jdk-21"; .\gradlew test --tests "com.example.gpstest.domain.model.GnssSatelliteTest" -q
```
预期：全部 PASS（18 + 21 = 39 个测试）

- [ ] **步骤 3：Commit**

```bash
git add app/src/test/java/com/example/gpstest/domain/model/GnssSatelliteTest.kt
git commit -m "test: add GnssSatellite bitmask property tests (isAdrValid, hasCycleSlip, measurementState)"
```

---

## 任务 3：XtraDataValidator 基础验证

**文件：**
- 创建：`app/src/test/java/com/example/gpstest/data/validator/XtraDataValidatorTest.kt`
- 测试源：`app/src/main/java/com/example/gpstest/data/validator/XtraDataValidator.kt`

- [ ] **步骤 1：编写测试文件**

创建 `app/src/test/java/com/example/gpstest/data/validator/XtraDataValidatorTest.kt`：

```kotlin
package com.example.gpstest.data.validator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XtraDataValidatorTest {
    private fun makeValidator(
        minSizeBytes: Int = 1024,
        maxSizeBytes: Int = 2 * 1024 * 1024,
        strictMode: Boolean = true,
    ): XtraDataValidator = XtraDataValidator(
        minSizeBytes = minSizeBytes,
        maxSizeBytes = maxSizeBytes,
        strictMode = strictMode,
    )

    private fun makeValidData(size: Int = 2048): ByteArray = ByteArray(size) { (it % 256).toByte() }

    // --- empty data ---

    @Test
    fun `validate rejects empty data with EMPTY_DATA error`() {
        val validator = makeValidator()
        val result = validator.validate(ByteArray(0))
        assertFalse(result.isValid)
        assertEquals(ValidationErrorType.EMPTY_DATA, result.errorType)
        assertTrue(result.details.contains("空"))
    }

    // --- size bounds ---

    @Test
    fun `validate rejects data smaller than minSizeBytes`() {
        val validator = makeValidator(minSizeBytes = 1024)
        val result = validator.validate(ByteArray(512))
        assertFalse(result.isValid)
        assertEquals(ValidationErrorType.TOO_SMALL, result.errorType)
    }

    @Test
    fun `validate accepts data exactly at minSizeBytes`() {
        val validator = makeValidator(minSizeBytes = 1024, strictMode = false)
        val result = validator.validate(makeValidData(1024))
        assertTrue(result.isValid)
    }

    @Test
    fun `validate rejects data larger than maxSizeBytes`() {
        val validator = makeValidator(maxSizeBytes = 4096)
        val result = validator.validate(ByteArray(5000))
        assertFalse(result.isValid)
        assertEquals(ValidationErrorType.TOO_LARGE, result.errorType)
    }

    @Test
    fun `validate accepts data exactly at maxSizeBytes`() {
        val validator = makeValidator(maxSizeBytes = 4096, strictMode = false)
        val result = validator.validate(makeValidData(4096))
        assertTrue(result.isValid)
    }

    // --- HTML signature detection ---

    @Test
    fun `validate detects HTML lowercase error page`() {
        val validator = makeValidator()
        val htmlData = "<html><body>404</body></html>".toByteArray() + ByteArray(2000)
        val result = validator.validate(htmlData)
        assertFalse(result.isValid)
        assertEquals(ValidationErrorType.ERROR_PAGE_DETECTED, result.errorType)
        assertTrue(result.details.contains("HTML"))
    }

    @Test
    fun `validate detects HTML uppercase error page`() {
        val validator = makeValidator()
        val htmlData = "<HTML><BODY>ERROR</BODY></HTML>".toByteArray() + ByteArray(2000)
        val result = validator.validate(htmlData)
        assertFalse(result.isValid)
        assertEquals(ValidationErrorType.ERROR_PAGE_DETECTED, result.errorType)
    }

    @Test
    fun `validate detects DOCTYPE error page`() {
        val validator = makeValidator()
        val htmlData = "<!DOCTYPE html><html></html>".toByteArray() + ByteArray(2000)
        val result = validator.validate(htmlData)
        assertFalse(result.isValid)
        assertEquals(ValidationErrorType.ERROR_PAGE_DETECTED, result.errorType)
    }

    // --- JSON signature detection ---

    @Test
    fun `validate detects JSON error response with error key`() {
        val validator = makeValidator()
        val jsonData = "{\"error\":\"not found\"}".toByteArray() + ByteArray(2000)
        val result = validator.validate(jsonData)
        assertFalse(result.isValid)
        assertEquals(ValidationErrorType.ERROR_PAGE_DETECTED, result.errorType)
        assertTrue(result.details.contains("JSON"))
    }

    @Test
    fun `validate detects JSON error response with message key`() {
        val validator = makeValidator()
        val jsonData = "{\"message\":\"forbidden\"}".toByteArray() + ByteArray(2000)
        val result = validator.validate(jsonData)
        assertFalse(result.isValid)
        assertEquals(ValidationErrorType.ERROR_PAGE_DETECTED, result.errorType)
    }

    @Test
    fun `validate detects JSON error response with code key`() {
        val validator = makeValidator()
        val jsonData = "{\"code\":403}".toByteArray() + ByteArray(2000)
        val result = validator.validate(jsonData)
        assertFalse(result.isValid)
        assertEquals(ValidationErrorType.ERROR_PAGE_DETECTED, result.errorType)
    }

    // --- printable char ratio ---

    @Test
    fun `validate rejects data with high printable char ratio above 2KB`() {
        val validator = makeValidator()
        val textData = ByteArray(3000) { 'A'.code.toByte() }
        val result = validator.validate(textData)
        assertFalse(result.isValid)
        assertEquals(ValidationErrorType.INVALID_FORMAT, result.errorType)
        assertTrue(result.details.contains("文本"))
    }

    @Test
    fun `validate accepts high printable ratio data below 2KB threshold`() {
        val validator = makeValidator()
        val textData = ByteArray(1500) { 'A'.code.toByte() }
        val result = validator.validate(textData)
        assertTrue(result.isValid)
    }

    // --- valid data passes ---

    @Test
    fun `validate accepts valid binary data`() {
        val validator = makeValidator(strictMode = false)
        val binaryData = ByteArray(2048) { i -> ((i * 37) % 256).toByte() }
        val result = validator.validate(binaryData)
        assertTrue(result.isValid)
        assertNull(result.errorType)
    }

    @Test
    fun `validation result has empty details when valid`() {
        val validator = makeValidator(strictMode = false)
        val result = validator.validate(makeValidData(2048))
        assertTrue(result.isValid)
        assertEquals("", result.details)
    }
}
```

- [ ] **步骤 2：运行测试验证通过**

运行：
```bash
$env:JAVA_HOME="C:\Program Files\Java\jdk-21"; .\gradlew test --tests "com.example.gpstest.data.validator.XtraDataValidatorTest" -q
```
预期：15 个测试全部 PASS。

注意：`validate` 方法内部在验证通过时调用 `Log.i()`。在纯 JUnit 环境中 `android.util.Log` 是 stub 会抛异常。如果测试在 `validate accepts` 用例上失败并抛出 `RuntimeException("Stub!")`，需要在 `strictMode = false` 且数据不含 HTML/JSON 签名的用例中额外确认——如果 `Log` 仍然被调用，则需要将 `strictMode = false` 的用例改为 `strictMode = true` 并提供一个合法的 `mimeType`（如 `"application/octet-stream"`），使代码路径绕过 `Log.w` 但 `Log.i` 仍会被调用。**如果 `Log.i` 在所有成功路径都被调用**，则此测试需要 Robolectric 或需要重构源码将 `Log` 抽取为接口。先运行测试，根据实际结果决定。

- [ ] **步骤 3：处理 Log stub 问题（如果需要）**

如果步骤 2 中 `validate accepts` 相关测试因 `RuntimeException("Stub!")` 失败：

修改 `app/src/main/java/com/example/gpstest/data/validator/XtraDataValidator.kt`，将 `validate()` 方法中第 112-121 行的 `Log.i()` 调用替换为安全日志调用。添加一个 `logInfo` 私有方法：

```kotlin
    private fun logInfo(message: String) {
        try {
            Log.i(TAG, message)
        } catch (_: Exception) {
        }
    }
```

然后将 `validate()` 中的：
```kotlin
        Log.i(
            TAG,
            String.format(
                "数据验证通过 | 来源: %s | 大小: %d字节 | SHA-256: %s",
                sourceUrl ?: "unknown",
                dataSize,
                hash,
            ),
        )
```

替换为：
```kotlin
        logInfo(
            String.format(
                "数据验证通过 | 来源: %s | 大小: %d字节 | SHA-256: %s",
                sourceUrl ?: "unknown",
                dataSize,
                hash,
            )
        )
```

同样处理 `validateMimeType()` 中第 186 行的 `Log.w`：
```kotlin
    private fun logWarn(message: String) {
        try {
            Log.w(TAG, message)
        } catch (_: Exception) {
        }
    }
```

将 `Log.w(TAG, "未知的MIME类型: $mimeType (非严格模式下允许)")` 替换为 `logWarn("未知的MIME类型: $mimeType (非严格模式下允许)")`。

- [ ] **步骤 4：Commit**

```bash
git add app/src/test/java/com/example/gpstest/data/validator/XtraDataValidatorTest.kt
git add app/src/main/java/com/example/gpstest/data/validator/XtraDataValidator.kt
git commit -m "test: add XtraDataValidator basic validation tests"
```

---

## 任务 4：XtraDataValidator MIME 类型和 getSizeStatistics

**文件：**
- 修改：`app/src/test/java/com/example/gpstest/data/validator/XtraDataValidatorTest.kt`
- 测试源：`app/src/main/java/com/example/gpstest/data/validator/XtraDataValidator.kt:105-188,204-221`

- [ ] **步骤 1：在 `XtraDataValidatorTest.kt` 末尾 `}` 之前追加 MIME 和统计测试**

```kotlin
    // --- MIME type validation (strictMode) ---

    @Test
    fun `validate accepts valid MIME type application/octet-stream in strict mode`() {
        val validator = makeValidator(strictMode = true)
        val result = validator.validate(makeValidData(), mimeType = "application/octet-stream")
        assertTrue(result.isValid)
    }

    @Test
    fun `validate accepts valid MIME type application/vnd.qualcomm.xtra in strict mode`() {
        val validator = makeValidator(strictMode = true)
        val result = validator.validate(makeValidData(), mimeType = "application/vnd.qualcomm.xtra")
        assertTrue(result.isValid)
    }

    @Test
    fun `validate rejects text/html MIME type in strict mode`() {
        val validator = makeValidator(strictMode = true)
        val result = validator.validate(makeValidData(), mimeType = "text/html")
        assertFalse(result.isValid)
        assertEquals(ValidationErrorType.INVALID_MIME_TYPE, result.errorType)
    }

    @Test
    fun `validate rejects application/json MIME type in strict mode`() {
        val validator = makeValidator(strictMode = true)
        val result = validator.validate(makeValidData(), mimeType = "application/json")
        assertFalse(result.isValid)
        assertEquals(ValidationErrorType.INVALID_MIME_TYPE, result.errorType)
    }

    @Test
    fun `validate rejects application/html MIME type in strict mode`() {
        val validator = makeValidator(strictMode = true)
        val result = validator.validate(makeValidData(), mimeType = "application/html")
        assertFalse(result.isValid)
        assertEquals(ValidationErrorType.INVALID_MIME_TYPE, result.errorType)
    }

    @Test
    fun `validate skips MIME check when mimeType is null`() {
        val validator = makeValidator(strictMode = true)
        val result = validator.validate(makeValidData(), mimeType = null)
        assertTrue(result.isValid)
    }

    @Test
    fun `validate skips MIME check when strictMode is false`() {
        val validator = makeValidator(strictMode = false)
        val result = validator.validate(makeValidData(), mimeType = "text/html")
        assertTrue(result.isValid)
    }

    @Test
    fun `MIME type comparison is case insensitive`() {
        val validator = makeValidator(strictMode = true)
        val result = validator.validate(makeValidData(), mimeType = "Application/OCTET-Stream")
        assertTrue(result.isValid)
    }

    @Test
    fun `MIME type with whitespace is trimmed`() {
        val validator = makeValidator(strictMode = true)
        val result = validator.validate(makeValidData(), mimeType = "  application/octet-stream  ")
        assertTrue(result.isValid)
    }

    // --- getSizeStatistics ---

    @Test
    fun `getSizeStatistics includes size in KB`() {
        val data = ByteArray(2048)
        val stats = XtraDataValidator().getSizeStatistics(data)
        assertTrue(stats.contains("2.00 KB"))
    }

    @Test
    fun `getSizeStatistics includes first byte hex`() {
        val data = ByteArray(2048)
        data[0] = 0xAB.toByte()
        val stats = XtraDataValidator().getSizeStatistics(data)
        assertTrue(stats.contains("0xAB"))
    }

    @Test
    fun `getSizeStatistics includes magic bytes when data has 4 or more bytes`() {
        val data = ByteArray(2048)
        data[0] = 0x01
        data[1] = 0x02
        data[2] = 0x03
        data[3] = 0x04
        val stats = XtraDataValidator().getSizeStatistics(data)
        assertTrue(stats.contains("Magic:"))
        assertTrue(stats.contains("01 02 03 04"))
    }

    @Test
    fun `getSizeStatistics omits magic bytes when data has fewer than 4 bytes`() {
        val data = byteArrayOf(0x01.toByte(), 0x02.toByte())
        val stats = XtraDataValidator().getSizeStatistics(data)
        assertFalse(stats.contains("Magic:"))
    }
```

- [ ] **步骤 2：运行测试验证通过**

运行：
```bash
$env:JAVA_HOME="C:\Program Files\Java\jdk-21"; .\gradlew test --tests "com.example.gpstest.data.validator.XtraDataValidatorTest" -q
```
预期：全部 PASS（15 + 13 = 28 个测试）

- [ ] **步骤 3：Commit**

```bash
git add app/src/test/java/com/example/gpstest/data/validator/XtraDataValidatorTest.kt
git commit -m "test: add XtraDataValidator MIME type and getSizeStatistics tests"
```

---

## 任务 5：getDisplayName() 扩展函数测试

**文件：**
- 创建：`app/src/test/java/com/example/gpstest/domain/model/SatelliteDisplayNameTest.kt`
- 测试源：`app/src/main/java/com/example/gpstest/ui/components/ConstellationUiExt.kt:41-56`

- [ ] **步骤 1：编写测试文件**

创建 `app/src/test/java/com/example/gpstest/domain/model/SatelliteDisplayNameTest.kt`：

```kotlin
package com.example.gpstest.domain.model

import com.example.gpstest.ui.components.getDisplayName
import org.junit.Assert.assertEquals
import org.junit.Test

class SatelliteDisplayNameTest {
    private fun makeSatellite(
        constellation: Constellation = Constellation.GPS,
        rawConstellationType: Int = constellation.constellationType,
    ): GnssSatellite =
        GnssSatellite(
            svid = 1,
            constellation = constellation,
            rawConstellationType = rawConstellationType,
            cn0DbHz = 30f,
            azimuthDegrees = 45f,
            elevationDegrees = 30f,
            hasAlmanac = true,
            hasEphemeris = true,
            usedInFix = true,
            carrierFrequencyHz = null,
            carrierCycles = null,
            dopplerShiftHz = null,
            timeNanos = 0L,
        )

    // --- GnssSatellite.getDisplayName() ---

    @Test
    fun `GnssSatellite getDisplayName returns shortName for GPS`() {
        val sat = makeSatellite(constellation = Constellation.GPS)
        assertEquals("GPS", sat.getDisplayName())
    }

    @Test
    fun `GnssSatellite getDisplayName returns shortName for BEIDOU`() {
        val sat = makeSatellite(constellation = Constellation.BEIDOU)
        assertEquals("BDS", sat.getDisplayName())
    }

    @Test
    fun `GnssSatellite getDisplayName returns shortName for GLONASS`() {
        val sat = makeSatellite(constellation = Constellation.GLONASS)
        assertEquals("GLO", sat.getDisplayName())
    }

    @Test
    fun `GnssSatellite getDisplayName returns UNK with raw type for UNKNOWN with non-negative-1 raw type`() {
        val sat = makeSatellite(constellation = Constellation.UNKNOWN, rawConstellationType = 8)
        assertEquals("UNK(8)", sat.getDisplayName())
    }

    @Test
    fun `GnssSatellite getDisplayName returns UNK shortName for UNKNOWN with raw type -1`() {
        val sat = makeSatellite(constellation = Constellation.UNKNOWN, rawConstellationType = -1)
        assertEquals("UNK", sat.getDisplayName())
    }

    @Test
    fun `GnssSatellite getDisplayName returns UNK with raw type 0`() {
        val sat = makeSatellite(constellation = Constellation.UNKNOWN, rawConstellationType = 0)
        assertEquals("UNK(0)", sat.getDisplayName())
    }

    // --- SatelliteHistoryEntry.getDisplayName() ---

    @Test
    fun `SatelliteHistoryEntry getDisplayName returns shortName for GPS constellation`() {
        val entry = SatelliteHistoryEntry(
            timestamp = 1000L,
            svid = 1,
            constellationName = "GPS",
            cn0DbHz = 30f,
            usedInFix = true,
        )
        assertEquals("GPS", entry.getDisplayName())
    }

    @Test
    fun `SatelliteHistoryEntry getDisplayName returns shortName for BEIDOU`() {
        val entry = SatelliteHistoryEntry(
            timestamp = 1000L,
            svid = 1,
            constellationName = "BEIDOU",
            cn0DbHz = 30f,
            usedInFix = true,
        )
        assertEquals("BDS", entry.getDisplayName())
    }

    @Test
    fun `SatelliteHistoryEntry getDisplayName returns UNK with raw type for UNKNOWN with raw type`() {
        val entry = SatelliteHistoryEntry(
            timestamp = 1000L,
            svid = 1,
            constellationName = "UNKNOWN",
            rawConstellationType = 8,
            cn0DbHz = 30f,
            usedInFix = true,
        )
        assertEquals("UNK(8)", entry.getDisplayName())
    }

    @Test
    fun `SatelliteHistoryEntry getDisplayName returns UNK for UNKNOWN with null raw type`() {
        val entry = SatelliteHistoryEntry(
            timestamp = 1000L,
            svid = 1,
            constellationName = "UNKNOWN",
            rawConstellationType = null,
            cn0DbHz = 30f,
            usedInFix = true,
        )
        assertEquals("UNK", entry.getDisplayName())
    }

    @Test
    fun `SatelliteHistoryEntry getDisplayName returns UNK for UNKNOWN with raw type -1`() {
        val entry = SatelliteHistoryEntry(
            timestamp = 1000L,
            svid = 1,
            constellationName = "UNKNOWN",
            rawConstellationType = -1,
            cn0DbHz = 30f,
            usedInFix = true,
        )
        assertEquals("UNK", entry.getDisplayName())
    }

    @Test
    fun `SatelliteHistoryEntry getDisplayName returns raw name for invalid constellation name`() {
        val entry = SatelliteHistoryEntry(
            timestamp = 1000L,
            svid = 1,
            constellationName = "FUTURE_CONSTELLATION",
            cn0DbHz = 30f,
            usedInFix = true,
        )
        assertEquals("FUTURE_CONSTELLATION", entry.getDisplayName())
    }
}
```

- [ ] **步骤 2：运行测试验证通过**

运行：
```bash
$env:JAVA_HOME="C:\Program Files\Java\jdk-21"; .\gradlew test --tests "com.example.gpstest.domain.model.SatelliteDisplayNameTest" -q
```
预期：12 个测试全部 PASS。这些测试调用的是已有的扩展函数，不需要修改源码。

- [ ] **步骤 3：Commit**

```bash
git add app/src/test/java/com/example/gpstest/domain/model/SatelliteDisplayNameTest.kt
git commit -m "test: add getDisplayName() extension function tests for GnssSatellite and SatelliteHistoryEntry"
```

---

## 任务 6：补充 ConstellationTest — shortName 和 constellationType

**文件：**
- 修改：`app/src/test/java/com/example/gpstest/domain/model/ConstellationTest.kt`
- 测试源：`app/src/main/java/com/example/gpstest/domain/model/Constellation.kt`

- [ ] **步骤 1：在 `ConstellationTest.kt` 末尾 `}` 之前追加**

```kotlin
    // --- shortName property ---

    @Test
    fun `GPS shortName is GPS`() {
        assertEquals("GPS", Constellation.GPS.shortName)
    }

    @Test
    fun `SBAS shortName is SBAS`() {
        assertEquals("SBAS", Constellation.SBAS.shortName)
    }

    @Test
    fun `GLONASS shortName is GLO`() {
        assertEquals("GLO", Constellation.GLONASS.shortName)
    }

    @Test
    fun `GALILEO shortName is GAL`() {
        assertEquals("GAL", Constellation.GALILEO.shortName)
    }

    @Test
    fun `BEIDOU shortName is BDS`() {
        assertEquals("BDS", Constellation.BEIDOU.shortName)
    }

    @Test
    fun `QZSS shortName is QZS`() {
        assertEquals("QZS", Constellation.QZSS.shortName)
    }

    @Test
    fun `IRNSS shortName is IRN`() {
        assertEquals("IRN", Constellation.IRNSS.shortName)
    }

    @Test
    fun `UNKNOWN shortName is UNK`() {
        assertEquals("UNK", Constellation.UNKNOWN.shortName)
    }

    // --- constellationType property round-trip ---

    @Test
    fun `fromConstellationType round-trips for all known constellations`() {
        for (constellation in Constellation.entries) {
            if (constellation == Constellation.UNKNOWN) continue
            assertEquals(
                constellation,
                Constellation.fromConstellationType(constellation.constellationType),
            )
        }
    }
```

- [ ] **步骤 2：运行测试验证通过**

运行：
```bash
$env:JAVA_HOME="C:\Program Files\Java\jdk-21"; .\gradlew test --tests "com.example.gpstest.domain.model.ConstellationTest" -q
```
预期：全部 PASS（11 + 9 = 20 个测试）

- [ ] **步骤 3：Commit**

```bash
git add app/src/test/java/com/example/gpstest/domain/model/ConstellationTest.kt
git commit -m "test: add Constellation shortName and constellationType round-trip tests"
```

---

## 任务 7：补充 GnssDataTest — 负值和空列表边界

**文件：**
- 修改：`app/src/test/java/com/example/gpstest/domain/model/GnssDataTest.kt`
- 测试源：`app/src/main/java/com/example/gpstest/domain/model/GnssData.kt`

- [ ] **步骤 1：在 `GnssDataTest.kt` 末尾 `}` 之前追加**

```kotlin
    // --- edge cases ---

    @Test
    fun `avgCn0DbHz excludes negative values`() {
        val data = GnssData(
            satellites = listOf(
                makeSatellite(cn0DbHz = 30f),
                makeSatellite(cn0DbHz = -5f),
            ),
        )
        assertEquals(30f, data.avgCn0DbHz, 0.01f)
    }

    @Test
    fun `avgBasebandCn0DbHz returns 0 for empty satellite list`() {
        val data = GnssData(satellites = emptyList())
        assertEquals(0f, data.avgBasebandCn0DbHz, 0.01f)
    }

    @Test
    fun `avgBasebandCn0DbHz excludes negative baseband values`() {
        val data = GnssData(
            satellites = listOf(
                makeSatellite(basebandCn0DbHz = 20f),
                makeSatellite(basebandCn0DbHz = -10f),
            ),
        )
        assertEquals(20f, data.avgBasebandCn0DbHz, 0.01f)
    }
```

- [ ] **步骤 2：运行测试验证通过**

运行：
```bash
$env:JAVA_HOME="C:\Program Files\Java\jdk-21"; .\gradlew test --tests "com.example.gpstest.domain.model.GnssDataTest" -q
```
预期：全部 PASS（8 + 3 = 11 个测试）

- [ ] **步骤 3：Commit**

```bash
git add app/src/test/java/com/example/gpstest/domain/model/GnssDataTest.kt
git commit -m "test: add GnssData negative value and empty list edge cases"
```

---

## 任务 8：补充 GnssClockDataTest — 负值、零值、双 null

**文件：**
- 修改：`app/src/test/java/com/example/gpstest/domain/model/GnssClockDataTest.kt`
- 测试源：`app/src/main/java/com/example/gpstest/domain/model/GnssClockData.kt`

- [ ] **步骤 1：在 `GnssClockDataTest.kt` 末尾 `}` 之前追加**

```kotlin
    // --- edge cases ---

    @Test
    fun `totalBiasNanos handles negative bias values`() {
        val clock = GnssClockData(
            timeNanos = 1000L,
            biasNanos = -500.0,
            fullBiasNanos = -2000L,
            driftNanosPerSecond = null,
            biasUncertaintyNanos = null,
            driftUncertaintyNanosPerSecond = null,
            hardwareClockDiscontinuityCount = 0,
        )
        assertEquals(-2500.0, clock.totalBiasNanos!!, 0.001)
    }

    @Test
    fun `totalBiasNanos returns null when both are null`() {
        val clock = GnssClockData(
            timeNanos = 1000L,
            biasNanos = null,
            fullBiasNanos = null,
            driftNanosPerSecond = null,
            biasUncertaintyNanos = null,
            driftUncertaintyNanosPerSecond = null,
            hardwareClockDiscontinuityCount = 0,
        )
        assertNull(clock.totalBiasNanos)
    }

    @Test
    fun `driftMicrosecondsPerSecond handles zero drift`() {
        val clock = GnssClockData(
            timeNanos = 1000L,
            biasNanos = null,
            fullBiasNanos = null,
            driftNanosPerSecond = 0.0,
            biasUncertaintyNanos = null,
            driftUncertaintyNanosPerSecond = null,
            hardwareClockDiscontinuityCount = 0,
        )
        assertEquals(0.0, clock.driftMicrosecondsPerSecond!!, 0.001)
    }

    @Test
    fun `driftMicrosecondsPerSecond handles negative drift`() {
        val clock = GnssClockData(
            timeNanos = 1000L,
            biasNanos = null,
            fullBiasNanos = null,
            driftNanosPerSecond = -3000.0,
            biasUncertaintyNanos = null,
            driftUncertaintyNanosPerSecond = null,
            hardwareClockDiscontinuityCount = 0,
        )
        assertEquals(-3.0, clock.driftMicrosecondsPerSecond!!, 0.001)
    }
```

- [ ] **步骤 2：运行测试验证通过**

运行：
```bash
$env:JAVA_HOME="C:\Program Files\Java\jdk-21"; .\gradlew test --tests "com.example.gpstest.domain.model.GnssClockDataTest" -q
```
预期：全部 PASS（7 + 4 = 11 个测试）

- [ ] **步骤 3：Commit**

```bash
git add app/src/test/java/com/example/gpstest/domain/model/GnssClockDataTest.kt
git commit -m "test: add GnssClockData negative, zero, and dual-null edge cases"
```

---

## 任务 9：补充 SatelliteHistoryTest — SatelliteHistoryConfig 和 fromGnssSatellite rawConstellationType

**文件：**
- 修改：`app/src/test/java/com/example/gpstest/domain/model/SatelliteHistoryTest.kt`
- 测试源：`app/src/main/java/com/example/gpstest/domain/model/SatelliteHistory.kt`

- [ ] **步骤 1：在 `SatelliteHistoryTest.kt` 末尾 `}` 之前追加**

```kotlin
    // --- SatelliteHistoryConfig ---

    @Test
    fun `SatelliteHistoryConfig default maxSnapshots is 100`() {
        val config = SatelliteHistoryConfig()
        assertEquals(100, config.maxSnapshots)
    }

    @Test
    fun `SatelliteHistoryConfig default snapshotIntervalMs is 60000`() {
        val config = SatelliteHistoryConfig()
        assertEquals(60_000L, config.snapshotIntervalMs)
    }

    @Test
    fun `SatelliteHistoryConfig default retentionDays is 7`() {
        val config = SatelliteHistoryConfig()
        assertEquals(7, config.retentionDays)
    }

    // --- fromGnssSatellite preserves rawConstellationType ---

    @Test
    fun `fromGnssSatellite preserves rawConstellationType from source satellite`() {
        val sat = makeSatellite(constellation = Constellation.GPS)
        val entry = SatelliteHistoryEntry.fromGnssSatellite(sat, timestamp = 1000L)
        assertEquals(Constellation.GPS.constellationType, entry.rawConstellationType)
    }

    @Test
    fun `fromGnssSatellite preserves UNKNOWN rawConstellationType`() {
        val sat = GnssSatellite(
            svid = 1,
            constellation = Constellation.UNKNOWN,
            rawConstellationType = 99,
            cn0DbHz = 0f,
            azimuthDegrees = 0f,
            elevationDegrees = 0f,
            hasAlmanac = false,
            hasEphemeris = false,
            usedInFix = false,
            carrierFrequencyHz = null,
            carrierCycles = null,
            dopplerShiftHz = null,
            timeNanos = 0L,
        )
        val entry = SatelliteHistoryEntry.fromGnssSatellite(sat, timestamp = 1000L)
        assertEquals(99, entry.rawConstellationType)
    }
```

- [ ] **步骤 2：运行测试验证通过**

运行：
```bash
$env:JAVA_HOME="C:\Program Files\Java\jdk-21"; .\gradlew test --tests "com.example.gpstest.domain.model.SatelliteHistoryTest" -q
```
预期：全部 PASS（9 + 5 = 14 个测试）

- [ ] **步骤 3：Commit**

```bash
git add app/src/test/java/com/example/gpstest/domain/model/SatelliteHistoryTest.kt
git commit -m "test: add SatelliteHistoryConfig defaults and fromGnssSatellite rawConstellationType tests"
```

---

## 任务 10：补充 DopCalculatorTest — 零仰角和同方位角

**文件：**
- 修改：`app/src/test/java/com/example/gpstest/domain/util/DopCalculatorTest.kt`
- 测试源：`app/src/main/java/com/example/gpstest/domain/util/DopCalculator.kt`

- [ ] **步骤 1：在 `DopCalculatorTest.kt` 末尾 `}` 之前追加**

```kotlin
    @Test
    fun `returns null when all satellites have zero elevation`() {
        val sats = listOf(
            makeSatellite(1, 0f, 0f),
            makeSatellite(2, 90f, 0f),
            makeSatellite(3, 180f, 0f),
            makeSatellite(4, 270f, 0f),
        )
        assertNull(DopCalculator.calculate(sats))
    }

    @Test
    fun `returns null when all satellites share the same azimuth`() {
        val sats = listOf(
            makeSatellite(1, 90f, 30f),
            makeSatellite(2, 90f, 45f),
            makeSatellite(3, 90f, 60f),
            makeSatellite(4, 90f, 75f),
        )
        assertNull(DopCalculator.calculate(sats))
    }

    @Test
    fun `succeeds with exactly 4 used satellites`() {
        val sats = listOf(
            makeSatellite(1, 0f, 30f),
            makeSatellite(2, 90f, 60f),
            makeSatellite(3, 180f, 45f),
            makeSatellite(4, 270f, 15f),
        )
        val result = DopCalculator.calculate(sats)
        assertNotNull(result)
        assertEquals(4, result!!.satelliteCount)
    }
```

- [ ] **步骤 2：运行测试验证通过**

运行：
```bash
$env:JAVA_HOME="C:\Program Files\Java\jdk-21"; .\gradlew test --tests "com.example.gpstest.domain.util.DopCalculatorTest" -q
```
预期：全部 PASS（7 + 3 = 10 个测试）

- [ ] **步骤 3：Commit**

```bash
git add app/src/test/java/com/example/gpstest/domain/util/DopCalculatorTest.kt
git commit -m "test: add DopCalculator zero elevation, same azimuth, and exact-4 satellite tests"
```

---

## 任务 11：全量测试运行 + ktlint 检查

**文件：** 无文件变更

- [ ] **步骤 1：运行全部测试**

```bash
$env:JAVA_HOME="C:\Program Files\Java\jdk-21"; .\gradlew test -q
```
预期：全部测试 PASS（51 原有 + ~43 新增 = ~94 个测试）。汇总各文件测试数：

| 测试文件 | 任务 | 预期测试数 |
|---------|------|-----------|
| `GnssSatelliteTest` | 任务 1+2 | 39 |
| `XtraDataValidatorTest` | 任务 3+4 | 28 |
| `SatelliteDisplayNameTest` | 任务 5 | 12 |
| `ConstellationTest` | 任务 6 | 20 |
| `GnssDataTest` | 任务 7 | 11 |
| `GnssClockDataTest` | 任务 8 | 11 |
| `SatelliteHistoryTest` | 任务 9 | 14 |
| `DopCalculatorTest` | 任务 10 | 10 |
| `DopInfoTest` | 原有 | 10 |
| **总计** | | **~155** |

- [ ] **步骤 2：运行 ktlint 检查**

```bash
$env:JAVA_HOME="C:\Program Files\Java\jdk-21"; .\gradlew ktlintCheck -q
```
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：如有 ktlint 报错则修复并重新运行**

ktlint 常见问题：import 顺序、多余空行、行尾空格。根据报错信息修复后重新运行步骤 1 和 2。
