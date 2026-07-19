# 设计：GNSS 能力声明 vs 实测对照（方案 B）

**日期**: 2026-07-19  
**状态**: 待用户审查  
**范围**: 接收机诊断页能力卡增强；不新增页面/导航

## 背景与目标

G6 已实现静态 `GnssCapabilities` 查询（硬件型号/年份 + 若干能力开关）。用户仍无法区分：

- 设备**声明不支持** → UI 为空属正常
- 设备**声明支持**但运行时无数据 → OEM 虚标 / 权限·省电 / 无天空视野 / 链路故障

本设计在现有能力卡上叠加 **声明 vs 实测** 对照，解释「为何某类数据为空」。

### 成功标准

1. 对 **Measurements / Navigation Messages / Antenna Info** 三项，同时展示声明状态、实测状态、一句中文诊断。
2. 实测基于 `startListening()` 后的 **固定 30 秒观察窗**。
3. 规则可单测（纯领域函数）；不新增平台 GNSS 回调。
4. API < 31 或能力查询失败时仍可据实测给出有意义提示。

### 非目标

- ADR / Measurement Corrections / Correlation Vectors 的实测
- 完整接收机健康评分、可分享诊断报告
- 新页面、新底部导航或抽屉入口
- 30s 窗口用户可配置（首版固定常量）

## 已确认决策

| 决策 | 选择 |
|------|------|
| 总体方案 | 方案 B：声明 vs 实测对照 |
| 覆盖能力 | 核心三项：Measurements / Nav Messages / Antenna |
| 观察窗 | 固定 30s，自 `startListening()` 起算 |
| UI | 扩展现有 `GnssCapabilitiesCard`（声明 \| 实测 + 诊断行） |
| 实现结构 | 领域纯函数 + ViewModel 聚合证据（方案 A） |

## 架构

```
声明 ── loadCapabilities() ──► GnssCapabilitiesInfo
                                  │
证据 ── startListening() 后 ──────┤
  · GnssData 卫星 measurement 字段 │
  · getNavigationMessages() 帧    ├──► CapabilityProbeEvaluator
  · antennaInfos 非空曾出现        │         │
  · elapsedMs / listeningStarted  │         ▼
                                  └── List<CapabilityProbeRow>
                                              │
                                              ▼
                                    GnssCapabilitiesCard（扩展）
```

- **不**新增 `registerGnss*` 回调；证据复用现有数据流。
- 评估逻辑在 `domain/util/CapabilityProbeEvaluator`，无 Android 依赖。
- ViewModel 只负责累计证据与计时，不内联矩阵规则。

## 领域模型

### 新增类型（建议文件）

`domain/model/CapabilityProbe.kt`（或拆分为同包多文件，保持领域层无 android 依赖）：

```kotlin
enum class CapabilityKey {
    MEASUREMENTS,
    NAVIGATION_MESSAGES,
    ANTENNA_INFO,
}

enum class ProbeOutcome {
    /** 观察窗内（或任意时刻）已见过有效数据 */
    OBSERVED,
    /** 窗已满且未见数据 */
    NOT_OBSERVED,
    /** 窗未满且未见数据 */
    OBSERVING,
    /** 声明不支持，实测不适用 */
    NOT_APPLICABLE,
    /** 未开始监听 / 声明与证据均不足以判断 */
    UNKNOWN,
}

/** 映射到 strings.xml 的诊断原因，避免领域层硬编码中文 */
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
    /** 观察中时剩余毫秒；其它状态为 null */
    val remainingMs: Long? = null,
)
```

`CapabilityState` 继续复用 `GnssCapabilitiesInfo.kt` 中已有枚举与 `Int.toCapabilityState()`。

### 评估器

`domain/util/CapabilityProbeEvaluator.kt`：

```kotlin
object CapabilityProbeEvaluator {
    const val DEFAULT_WINDOW_MS: Long = 30_000L

    fun evaluate(
        capabilities: GnssCapabilitiesInfo?,
        evidence: CapabilityEvidence,
        windowMs: Long = DEFAULT_WINDOW_MS,
    ): List<CapabilityProbeRow>
}
```

对三项各调用一次内部 `evaluateOne(declared, seen, evidence, windowMs)`。

#### 声明字段映射

| CapabilityKey | GnssCapabilitiesInfo 字段 |
|---------------|---------------------------|
| MEASUREMENTS | `hasMeasurements` |
| NAVIGATION_MESSAGES | `hasNavigationMessages` |
| ANTENNA_INFO | `hasAntennaInfo` |

字段为 `null`（API 不足或未查询）→ `CapabilityState.UNKNOWN`。

#### 单行矩阵

令 `seen` = 该项证据布尔；`listening` = `evidence.listeningStarted`；`elapsed` = `evidence.elapsedMs`。

1. 若 `!listening` → `observed = UNKNOWN`，`diagnostic = NOT_LISTENING`
2. 若 `seen` → `observed = OBSERVED`  
   - 声明 SUPPORTED → `DECLARED_SUPPORTED_OBSERVED`  
   - 声明 UNSUPPORTED → 仍显示 OBSERVED（罕见：声明不支持却见到数据），诊断可用 `DECLARED_UNKNOWN_OBSERVED` 或单独枚举；**首版**：UNSUPPORTED + seen → `observed = OBSERVED`，`diagnostic = DECLARED_UNKNOWN_OBSERVED`（文案强调「已观测到数据」）  
   - 声明 UNKNOWN → `DECLARED_UNKNOWN_OBSERVED`
3. 若 `!seen && elapsed < windowMs` → `observed = OBSERVING`，`remainingMs = windowMs - elapsed`  
   - SUPPORTED → `DECLARED_SUPPORTED_OBSERVING`  
   - UNSUPPORTED → `observed = NOT_APPLICABLE`，`diagnostic = DECLARED_UNSUPPORTED`（不支持则不必「观察中」）  
   - UNKNOWN → `DECLARED_UNKNOWN_OBSERVING`
4. 若 `!seen && elapsed >= windowMs` →  
   - SUPPORTED → `NOT_OBSERVED` + `DECLARED_SUPPORTED_NOT_OBSERVED`  
   - UNSUPPORTED → `NOT_APPLICABLE` + `DECLARED_UNSUPPORTED`  
   - UNKNOWN → `NOT_OBSERVED` + `DECLARED_UNKNOWN_NOT_OBSERVED`

**要点**：声明 `UNSUPPORTED` 时，无论窗是否满，实测列均为 `NOT_APPLICABLE`（除非实际 `seen`，见步骤 2），避免对不支持能力显示「未观测到」造成误导。

## 实测证据规则

会话范围：单次 `startListening()` → 再次 `startListening()` / `onCleared` 时**重置**所有 seen 与计时。

| 能力 | 证据条件（会话内 sticky OR） |
|------|------------------------------|
| Measurements | 任一卫星：`measurementState != null` **或** `measurementCn0DbHz != null` **或** `pseudorangeRateMetersPerSecond != null` **或** `accumulatedDeltaRangeMeters != null` |
| Navigation Messages | `repository.getNavigationMessages()` 收到 ≥1 帧 |
| Antenna Info | `antennaInfos` 曾出现过 `isNotEmpty()`；之后变空仍算见过 |

辅助纯函数建议：

```kotlin
fun GnssSatellite.hasMeasurementEvidence(): Boolean
fun List<GnssSatellite>.anyMeasurementEvidence(): Boolean
```

放在领域层（`GnssSatellite` 扩展或 Evaluator 同文件），便于单测。

## ViewModel 行为

`SatelliteViewModel`：

1. **保留**现有 `gnssCapabilities: StateFlow<GnssCapabilitiesInfo?>`（init 加载，不依赖定位权限）。
2. **新增**证据状态（私有）与对外：
   - `capabilityProbes: StateFlow<List<CapabilityProbeRow>>`
   - 或暴露 `capabilityEvidence` + 在 UI 侧 evaluate——**推荐 VM 内 evaluate 后只暴露 rows**，UI 无逻辑。
3. `startListening()`：
   - 重置 `measurementsSeen / navSeen / antennaSeen = false`
   - `probeStartElapsedRealtime = SystemClock.elapsedRealtime()`（实现层可用 `android.os.SystemClock`；测试可注入时钟接口，首版允许直接用系统时钟 + 单测只测 Evaluator）
   - `listeningStarted = true`
4. 主 GNSS `collect`：若 `!measurementsSeen && satellites.anyMeasurementEvidence()` → 置 true 并 recompute
5. 现有 navigation collect：首帧 → `navSeen = true`
6. 现有 antenna collect：`list.isNotEmpty()` → `antennaSeen = true`
7. **计时刷新**：`viewModelScope` 内每 1s `delay`，在 `listeningStarted && 存在仍为 OBSERVING 的项` 时 recompute；全部离开 OBSERVING 后可停 ticker 以省电
8. 权限拒绝导致无法监听：保持 `listeningStarted = false` 或不调用 start 时的默认 `NOT_LISTENING`；与现有 `SatelliteUiState.PermissionRequired` 共存，能力卡在诊断页 Success 路径展示时通常已在监听——若卡片在非 Success 也展示 capabilities，则实测为 NOT_LISTENING

`ReceiverDiagnosticsScreen`：在已有 `gnssCapabilities` 旁 collect `capabilityProbes`，传入扩展后的 Card。

## UI

扩展 `GnssCapabilitiesCard`：

```
参数:
  capabilities: GnssCapabilitiesInfo
  probes: List<CapabilityProbeRow> = emptyList()
```

布局：

- 硬件型号 / 年份：不变
- **核心三项**：若 `probes` 含对应 key，渲染：
  - 标签
  - 行：`声明: {支持|不支持|未知}` · `实测: {已观测|未观测|观察中 (Ns)|不适用|未知}`
  - 次要样式诊断一句（`ProbeDiagnostic` → `stringResource`）
- **ADR / Corrections / Correlation Vectors**：仍仅声明状态（现有逻辑），不接 probes
- `probes` 为空：核心三项退化为仅声明（兼容旧调用）

文案键（`values/strings.xml` + 如有 `values-en` 则同步）：

- `cap_declared` / `cap_observed` 标签
- 实测状态：`cap_probe_observed` / `cap_probe_not_observed` / `cap_probe_observing` / `cap_probe_not_applicable` / `cap_probe_unknown`
- 诊断：`cap_diag_*` 对应每个 `ProbeDiagnostic`
- 观察中可带格式：`cap_probe_observing_seconds` → `观察中 (%1$d秒)`

诊断语气约束：

- `DECLARED_SUPPORTED_NOT_OBSERVED` **不得**写死「芯片不支持」；使用「30 秒内未观测到，可能原因：OEM 声明虚高、省电限制、无天空视野等」。

## 测试

新增 `CapabilityProbeEvaluatorTest`（JUnit4，领域层惯例）：

- SUPPORTED + seen → OBSERVED
- SUPPORTED + !seen + elapsed < 30s → OBSERVING + remainingMs
- SUPPORTED + !seen + elapsed >= 30s → NOT_OBSERVED
- UNSUPPORTED + !seen → NOT_APPLICABLE（窗前/窗后均是）
- UNSUPPORTED + seen → OBSERVED（边界）
- UNKNOWN + seen / observing / not observed
- `!listeningStarted` → NOT_LISTENING
- `capabilities == null` 时三项声明均为 UNKNOWN
- `anyMeasurementEvidence` 真假用例（仅 measurement 字段 vs 仅 cn0 状态字段）

不强制 ViewModel 仪器测试。

## 涉及文件（预期）

| 操作 | 路径 |
|------|------|
| 新增 | `domain/model/CapabilityProbe.kt`（或等价） |
| 新增 | `domain/util/CapabilityProbeEvaluator.kt` |
| 新增 | `test/.../CapabilityProbeEvaluatorTest.kt` |
| 修改 | `viewmodel/SatelliteViewModel.kt` |
| 修改 | `ui/components/GnssCapabilitiesCard.kt` |
| 修改 | `ui/screens/diagnostics/ReceiverDiagnosticsScreen.kt` |
| 修改 | `res/values/strings.xml`（及 en 若存在） |

可选：`GnssSatellite` 上增加 `hasMeasurementEvidence` 扩展。

## 风险与降级

| 风险 | 处理 |
|------|------|
| 室内/冷启动 30s 误报 NOT_OBSERVED | 文案列可能原因，不断言硬件缺陷 |
| 导航电文极低频 | 30s 可能不够；接受首版假阴性，文案已覆盖「未观测到」 |
| OEM 能力查询抛异常 | 现有 `getGnssCapabilities` 已 catch → null；探针按 UNKNOWN 声明处理 |
| 重复 startListening | 重置证据，避免跨会话污染 |

## 实现顺序建议

1. 领域模型 + Evaluator + 单测（红绿）
2. ViewModel 证据累计与 `capabilityProbes`
3. Card + strings + 诊断页接线
4. `./gradlew test` / 必要 UI 冒烟

## 规格自检

- [x] 无 TBD/占位章节
- [x] 三项能力、30s 窗、扩展卡片与决策表一致
- [x] 范围排除健康报告/ADR 实测
- [x] UNSUPPORTED 与 seen 边界已写明
- [x] 诊断文案禁止武断「芯片不支持」
