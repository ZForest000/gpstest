# G3 GnssAntennaInfo 设计

**日期**: 2026-07-18  
**状态**: Ready for review  
**范围**: Wave C / TODO G3 — 范围 **B**（PCO + 载波频率 + PCV 简化摘要）  
**依赖**: 无（为 G2 RINEX 头部铺路）

## 1. 目标

采集并展示设备 GNSS 天线相位中心信息（Android `GnssAntennaInfo`，API 30+），供诊断与后续 RINEX 头部使用。

**成功标准**

- API 30+ 且设备提供数据时，卫星页显示天线信息卡片（频点、PCO、PCV 摘要）
- API < 30、不支持、或列表为空时，卡片隐藏，不崩溃
- 领域模型纯 Kotlin，字段名稳定，可供 G2 读取
- 不阻塞 250ms GNSS 主采样路径
- 单元测试覆盖 mapper 黄金值；`ktlintCheck` + `test` + `assembleDebug` 通过

## 2. 非目标

- 完整 PCV/信号增益二维表渲染或热力图
- 天线信息 CSV/JSON 导出（可后续做）
- 将天线数据并入 `GnssData` 250ms 采样流
- 修改 G6 能力查询语义（`hasAntennaInfo` 仍为能力三态）
- Crashlytics / 新第三方依赖

## 3. 架构与数据流

对齐 G6（能力）与 NMEA（独立 Flow）模式：

```
LocationManager (API 30+)
  ├─ getGnssAntennaInfos()          // 可选初值
  └─ registerAntennaInfoListener    // 更新
        │
        ▼
GnssDataSource.getAntennaInfos(): Flow<List<AntennaInfo>>
        │  callbackFlow + mapper
        ▼
GnssRepository.getAntennaInfos()    // 透传
        │
        ▼
SatelliteViewModel.antennaInfos: StateFlow<List<AntennaInfo>>
        │  startListening 时 collect；stop 时取消
        ▼
SatelliteListScreen → AntennaInfoCard (list 非空才显示)
```

**为何独立 Flow**：天线回调频率低、与卫星状态无关；并入 `GnssData` 会污染 250ms 采样路径且增加主 data class 体积。

## 4. 领域模型

文件：`domain/model/AntennaInfo.kt`（纯 Kotlin，无 `android.*`）

```kotlin
/**
 * 单条 GNSS 天线相位中心快照（对应平台 GnssAntennaInfo 一条）。
 * 字段名稳定，供 G2 RINEX ANTENNA: DELTA / 频点元数据使用。
 */
data class AntennaInfo(
    /** 载波频率，单位 MHz（平台 API 原生单位）。 */
    val carrierFrequencyMHz: Double,
    /** 相位中心偏移 PCO，毫米；设备坐标系 x/y/z。 */
    val pcoXMm: Double,
    val pcoYMm: Double,
    val pcoZMm: Double,
    val pcoXUncertaintyMm: Double,
    val pcoYUncertaintyMm: Double,
    val pcoZUncertaintyMm: Double,
    /**
     * PCV 简化摘要；无 spherical corrections 时为 null。
     * 完整网格留给后续诊断/导出，本波次不建模 double[][]。
     */
    val pcvSummary: PhaseCenterVariationSummary?,
)

/**
 * 相位中心变化（PCV）网格的轻量摘要。
 * 来自 GnssAntennaInfo.SphericalCorrections，不保留完整数组。
 */
data class PhaseCenterVariationSummary(
    /** 方位角步长（度），对应 getDeltaPhi()。 */
    val deltaPhiDeg: Double,
    /** 天顶角步长（度），对应 getDeltaTheta()。 */
    val deltaThetaDeg: Double,
    /** 网格点数 = rows * cols（corrections 数组维度乘积）。 */
    val sampleCount: Int,
    /** 校正值最小值（mm）。 */
    val minCorrectionMm: Double,
    /** 校正值最大值（mm）。 */
    val maxCorrectionMm: Double,
)
```

**映射规则**（`data` 层 `AntennaInfoMapper` 或 `GnssDataSourceImpl` 私有对象，可单测）：

| 平台 API                                            | 领域字段                      |
| --------------------------------------------------- | ----------------------------- |
| `getCarrierFrequencyMHz()`                          | `carrierFrequencyMHz`         |
| `PhaseCenterOffset.getX/Y/ZOffsetMm()`              | `pcoX/Y/ZMm`                  |
| `getX/Y/ZOffsetUncertaintyMm()`                     | `pco*UncertaintyMm`           |
| `getPhaseCenterVariationCorrections()` null         | `pcvSummary = null`           |
| 非 null 时 `getDeltaPhi/Theta` + 数组 min/max/count | `PhaseCenterVariationSummary` |
| `getSignalGainCorrections()`                        | **本波次忽略**                |

列表：平台 `List<GnssAntennaInfo>` → `List<AntennaInfo>`（可多频点多条）。空列表对 ViewModel 表示“无数据”，UI 隐藏卡片。

## 5. DataSource / Repository

### 5.1 `GnssDataSource`

新增：

```kotlin
/**
 * 天线相位中心信息流（API 30+）。
 * 独立于 [getGnssData]，不参与 250ms 采样。
 * API < 30、无 LocationManager、或不支持时：立即 `trySend(emptyList())` 并 close（不挂起等待）。
 */
fun getAntennaInfos(): Flow<List<AntennaInfo>>
```

### 5.2 `GnssDataSourceImpl`（API 30+ = `Build.VERSION_CODES.R`）

1. `locationManager == null` 或 `SDK_INT < R` → `callbackFlow { trySend(emptyList()); close() }`
2. 否则：
    - 创建 `GnssAntennaInfo.Listener { infos -> trySend(mapper.map(infos.orEmpty())) }`
    - 注册前若 `getGnssAntennaInfos()` 非 null：立即 `trySend(map(...))` 作为初值
    - `registerAntennaInfoListener(mainExecutor, listener)`
    - `SecurityException` / 其他异常：Timber.w → `trySend(emptyList())` → close
    - `awaitClose { unregisterAntennaInfoListener(listener) }`

**注意**：平台方法名为 `registerAntennaInfoListener` / `unregisterAntennaInfoListener`（非旧文档中的 Callback 命名）。ProGuard 需 keep 这些方法与 `GnssAntennaInfo*` 类型。

### 5.3 Repository

`GnssRepository` / `Impl` 透传 `getAntennaInfos()`。

## 6. ViewModel

`SatelliteViewModel`：

```kotlin
private val _antennaInfos = MutableStateFlow<List<AntennaInfo>>(emptyList())
val antennaInfos: StateFlow<List<AntennaInfo>> = _antennaInfos.asStateFlow()
```

- 在 `startListening()` 内启动独立 `Job` collect `repository.getAntennaInfos()`
- `stopListening()` / `onCleared` 取消该 Job，并将 `_antennaInfos` 置为 `emptyList()`（与监听生命周期对齐）
- 不依赖 `hasAntennaInfo` 能力位才能注册；能力位仅 UI 诊断。设备声称不支持时 listener 可能永不回调 → 卡片保持隐藏。

## 7. UI

### 7.1 `AntennaInfoCard`

- 入参：`infos: List<AntennaInfo>`（调用方保证非空）
- 标题：字符串资源「天线相位中心」/ en「Antenna phase center」
- 每条 `AntennaInfo` 一块子区域或 Divider 分隔：
    - 载波频率：`%.3f MHz`（或 `stringResource` format）
    - PCO：`X/Y/Z = … mm`，可选显示 uncertainty `±…`
    - 若 `pcvSummary != null`：一行摘要，如「PCV：Δφ=…° Δθ=…° 网格 N 点，范围 [min, max] mm」
- 样式对齐 `GnssCapabilitiesCard`（`surfaceVariant` Card）

### 7.2 挂载点

`SatelliteListScreen` / `SatelliteListContent`：在 `GnssCapabilitiesCard` **之后**（或之前）插入：

```kotlin
if (antennaInfos.isNotEmpty()) {
    item { AntennaInfoCard(infos = antennaInfos) }
}
```

从 ViewModel `collectAsState()` 传入。

### 7.3 文案

`values/strings.xml` + `values-en/strings.xml`：标题、频点、PCO 轴标签、PCV 摘要 format、不可用占位（若需要）。无硬编码中文。

## 8. ProGuard

在 `app/proguard-rules.pro` GNSS 段补充：

- `-keep class android.location.GnssAntennaInfo { *; }`
- `-keep class android.location.GnssAntennaInfo$* { *; }`
- `LocationManager` keep 块增加：
    - `registerAntennaInfoListener(...)`
    - `unregisterAntennaInfoListener(...)`
    - `getGnssAntennaInfos(...)`

## 9. 测试

| 测试                                            | 内容                                                                                                             |
| ----------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| `AntennaInfoMapperTest`（或 `AntennaInfoTest`） | 黄金值：给定假 PCO 数值构造领域对象，断言字段；PCV summary min/max/count 对合成网格正确；null PCV → null summary |
| 现有测试                                        | 不回归                                                                                                           |

平台类型无法在纯 JVM 单测直接构造时：将 mapper 定义为接受「中间 DTO / 原始 double 参数」的纯函数，测试该纯函数；Impl 仅做平台字段读取后调用。

## 10. 实现任务拆分（供 plan）

1. Domain 模型 + mapper 纯函数 + 单测
2. DataSource/Repository 注册与 Flow
3. ViewModel + AntennaInfoCard + 屏幕接线 + strings + ProGuard
4. `ktlintCheck test assembleDebug` + TODO.md 标记 G3 ✅

## 11. 风险与约束

| 风险                                             | 缓解                                                  |
| ------------------------------------------------ | ----------------------------------------------------- |
| 多数消费机 `hasAntennaInfo=UNSUPPORTED` 或空列表 | 卡片隐藏；能力卡仍显示是否支持                        |
| API 文档混用 Callback/Listener 命名              | 以 SDK 36 `javap` 为准：`registerAntennaInfoListener` |
| PCV 数组很大                                     | 只存摘要，不把 `double[][]` 放进 StateFlow            |
| minSdk 24                                        | 全部 API 30 调用包在 `VERSION.SDK_INT >= R`           |

## 12. 验收清单

- [ ] API 30+ 模拟/真机有数据时卡片可见且数值合理
- [ ] API < 30 或空数据无崩溃、无空白卡片
- [ ] 领域模型无 android 依赖
- [ ] ProGuard keep 已加
- [ ] 中英 strings 齐全
- [ ] 单测 + ktlint + assembleDebug 绿
- [ ] TODO.md G3 标记完成
