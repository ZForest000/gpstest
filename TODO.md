# GNSS 调试工具 — 功能路线图

> **核实基准日期**：2026-07-15（基于 master 分支，含 G1 伪距推导与纯领域 WLS 解算核心）
> **目的**：经代码核实的功能增强清单与实施路线图，取代旧版 checkbox 列表
> **变更说明**：旧版 TODO.md 有 6 处标记错误，已在「第六章 已实现功能清单」中修正

---

## 📖 阅读说明

### 优先级标记

| 标记   | 含义                            | 触发时机 |
| ------ | ------------------------------- | -------- |
| **P0** | 紧急 — 死代码/数据丢失/严重误导 | 立即处理 |
| **P1** | 高 — 核心价值或差异化能力       | 当前迭代 |
| **P2** | 中 — 体验改善或工程稳健性       | 近期规划 |
| **P3** | 低 — 锦上添花或长线优化         | 滚动推进 |

### 工作量标记

- **小**：≤ 半天，单文件或浅改动
- **中**：1–3 天，跨 2–5 文件，可能引入新依赖
- **大**：≥ 1 周，涉及新模块/新数据流/新依赖

### 条目编号

- `B#`：Bug 与断路
- `G#`：专业 GNSS 能力增强
- `U#`：用户体验增强
- `E#`：工程化健康度

### 条目模板

```
### [ID] 标题（优先级 Pn，工作量：小/中/大）

**现状**：一句话 + 关键 file:line
**问题/缺口**：为什么需要、影响范围
**建议方案**：实施步骤（含技术要点、API 门槛）
**涉及文件**：新增/修改清单
**依赖与风险**：Android API、新库、兼容性、测试
**ROI**：工作量 vs 价值评估
```

### 📋 当前未实现功能速查

> 阶段一(止血)已全部完成(B1 ✅ / G5 ✅ / B2 ✅)。U2–U5、**B3 NMEA**、**Wave B 工程化**（E1/E2/E3 Timber/E5/E6/E8）均已实现。以下为**当前仍未实现**的功能,按优先级排列。详见各章节条目。

| 编号   | 功能                                               | 优先级 | 工作量 | 章节 |
| ------ | -------------------------------------------------- | ------ | ------ | ---- |
| **G1** | 本地定位解后续接线（导航电文、星历与实时卫星位置） | P1     | 大     | 二   |
| **G2** | RINEX 3.x 导出                                     | P1     | 大     | 二   |
| **G4** | GnssNavigationMessage 导航电文                     | P3     | 大     | 二   |
| **U6** | 信噪比柱状图 + DOP 实时曲线                        | P3     | 中     | 三   |
| **E7** | 历史存储迁移 Room                                  | P3     | 大     | 四   |

**下一步建议**：G3 已完成。优先 **G1 导航电文/星历接线**（为 G2 RINEX 提供观测数据），随后 **G2 RINEX 导出**（已可消费 G3 天线头部）；G1 不应按“直接读取伪距 API”实现。

---

## 一、🐛 已知 Bug 与断路

### B1. dumpsys 数据通路完全断路 ✅ 已修复（P0，工作量：中）

**现状**：`GnssDataSourceImpl.kt:53` 声明 `var currentDumpsysData: DumpsysGnssData? = null`，**全文件再无任何赋值语句**，永远为 null。`ShizukuHelper.kt:27-51` 只实现了 `isShizukuAvailable`/`isPermissionGranted`/`isRootMode` 三个探测方法，**完全没有执行 `dumpsys location` 命令和解析输出的代码**。

**问题/缺口**：下游 UI 链路已完整就绪——`ClockInfoCard.kt:60-63,124-145` 的 `DumpsysDataSection`、`SatelliteViewModel.kt:75` 已传递 `dumpsysData`——但数据源喂的是 null，**整段 UI 永远不会显示**。这是典型的死代码 bug，且 `.trae/documents/baseband-cn0-clock-bias-drift.md:79-91` 的设计文档里规划过但未落地。

**建议方案**：

1. 在 `ShizukuHelper` 中新增 `fetchDumpsysLocation(): DumpsysGnssData?`，通过 Shizuku/root 执行 `dumpsys location`。
2. 用正则解析输出关键字段：`avgBasebandCn0`、`measurementCount`、`usedInFixConstellations`、基带 C/N0 时钟偏差/漂移。
3. 在 `GnssDataSourceImpl` 的 callbackFlow 中周期性（如 5 秒）轮询 dumpsys 并赋值 `currentDumpsysData`。
4. 增加降级：无 Shizuku/root 权限时静默跳过，不影响主流程。

**涉及文件**：

- 修改 `data/source/ShizukuHelper.kt`（新增 `fetchDumpsysLocation`）
- 修改 `data/source/GnssDataSourceImpl.kt:53` 及回调注册区（`:328-353`）
- 新增 dumpsys 解析的单元测试（领域层）

**依赖与风险**：Shizuku/root 仅作为增强来源，非必需；需处理不同 OEM 的 dumpsys 输出格式差异（Qualcomm/MTK）；测试需用 mock 输入字符串。

**ROI**：中工作量 / 极高价值 — **优先级最高**，UI 已就绪只差数据通路，是专业调试工具（基带 C/N0、HAL 时钟）的差异化能力。

---

### B2. 载波相位完整周期标记错误 ✅ 已修复（P2，工作量：小）

**现状**：`GnssDataSourceImpl.kt:129` **硬编码 `fullCarrierPhaseCycleCount = null`**，从未调用 `measurement.fullCarrierPhaseCycleCount`（API 34+ 字段）。`GnssSatellite.kt:68` 字段定义存在，但 UI 层（`SatelliteDetailSheet`）无任何展示代码。

**问题/缺口**：旧版 TODO.md 将此项标记为 `[x]` 已完成，**实际是反方向的严重标记错误**——会误导后续工作以为已完成。该字段是 RTK/精密单点定位（PPP）整数模糊度解算的核心输入。

**建议方案**：

1. 在 `GnssDataSourceImpl.kt:129` 改为：API 34+ 时读取 `measurement.fullCarrierPhaseCycleCount`，否则保持 null。
2. 在 `SatelliteDetailSheet` 原始测量区增加展示行。
3. 更新 TODO 标记为 `[ ]` 未完成（本文档已修正）。

**涉及文件**：`data/source/GnssDataSourceImpl.kt:129`、`ui/components/SatelliteDetailSheet.kt`、`GnssSatellite.kt` 文档注释。

**依赖与风险**：API 34+ 才支持，老设备保持 null；需在 UI 提示"设备不支持"。

**ROI**：小工作量 / 中价值 — 标记纠错本身零成本，数据采集补充对未来 RTK 功能是前置依赖。

---

### B3. NMEA 监听完全未接线 ✅ 已实现（P2，工作量：中）

**现状（已实现）**：

1. **数据通路**：`GnssDataSourceImpl.getNmeaSentences()` 通过 `LocationManager.addNmeaListener` 注册，输出 `Flow<NmeaSentence>`；`GnssRepository` 透传且**不**参与 250ms 采样。
2. **领域层**：`NmeaSentence` / `NmeaParsedSnapshot` + `NmeaParser`（GGA/RMC 轻量解析）+ 单元测试。
3. **UI**：`NmeaScreen` + `NmeaViewModel`（环形缓冲、类型过滤、暂停、GGA/RMC 摘要）+ 抽屉导航 `Screen.Nmea`。
4. **导出**：`NmeaExportHelper` 写 cache + FileProvider `ACTION_SEND` 分享 `.nmea`。
5. **设置**：`SettingsStore.nmeaEnabled` + 设置页开关。

**涉及文件**：`GnssDataSource(Impl).kt`、`GnssRepository(Impl).kt`、`domain/model/Nmea*.kt`、`domain/util/NmeaParser.kt`、`viewmodel/NmeaViewModel.kt`、`ui/screens/nmea/NmeaScreen.kt`、`data/local/NmeaExportHelper.kt`、`MainActivity.kt`、`SettingsStore`/`SettingsScreen`、对应测试。

**提交**：`4d79b8d` 领域模型与解析、`6844633` 屏幕/设置/导出/导航、`67ec289` ktlint 校验。

**ROI**：已兑现 — 调试基础能力补全，可实时查看与导出 NMEA。

---

## 二、🚀 专业 GNSS 能力增强

### G1. 原始伪距推导 + 本地最小二乘定位解（P1，工作量：大，阶段 1/2 已完成）

**现状**：阶段 1 已完成伪距推导：`PseudorangeCalculator.kt` 根据 `GnssClockData`、卫星接收时间、测量状态和星座时间基准输出伪距与不确定度，结果已接入 `GnssSatellite` 与卫星详情。阶段 2 已完成纯领域 `PositionSolver.kt`：以调用方提供的卫星 ECEF、已修正伪距和不确定度执行迭代 WLS，含 4x4 部分主元 Gauss-Jordan、明确失败状态和黄金测试。Android `GnssMeasurement` **没有** `getPseudorangeMeters()` / `getPseudorangeUncertaintyMeters()` 直接 API。

**问题/缺口**：当前 WLS 核心刻意未接入真实数据流：尚无导航电文、星历下载/解析、实时卫星 ECEF 或卫星钟差与传播路径改正。因此应用仍不能产出本地实时位置解或与 Android 位置做残差对比。

**已完成（阶段 1/2）**：

1. `GnssSatellite` 已新增 `pseudorangeMeters`、`pseudorangeUncertaintyMeters` 及状态字段；字段来源是**推导值**，不是平台直接 getter。
2. `PseudorangeCalculator.kt` 已实现 GPS/Galileo GPS 兼容周内时间、北斗 BDT 转换、`timeOffsetNanos`、测量状态、不确定度和范围校验。
3. `PositionSolver.kt` 已实现状态 `(x, y, z, receiverClockBiasMeters)`、`w = 1 / sigma²` 等价相对权重、Gauss-Newton 正规方程、双重收敛条件与加权残差 RMS。
4. `PositionSolverTest.kt` 已覆盖 4/6 星黄金数据、高不确定度异常观测、无效输入、奇异几何、较差初值、迭代耗尽及极端有限不确定度。

**后续建议（阶段 3+）**：

1. 接入 `GnssNavigationMessage`，采集并保存可用于星历解析的导航电文。
2. 接入外部星历或解析后的本地星历，计算实时卫星 ECEF；随后处理卫星钟差、相对论效应、Sagnac、对流层/电离层改正和 ISB。
3. 将已完成前置改正的真实伪距与卫星 ECEF 送入 `PositionSolver`，再增加系统位置 vs 本地解算位置的残差展示。

**涉及文件**：已完成 `domain/model/Pseudorange.kt`、`domain/util/PseudorangeCalculator.kt`、`domain/model/PositionSolution.kt`、`domain/util/PositionSolver.kt` 及对应测试；后续涉及 `data/source/GnssDataSourceImpl.kt`、导航电文/星历模块和 UI 对比卡片。

**依赖与风险**：无直接伪距 API，算法复杂度高；卫星位置计算依赖星历/历书；最小二乘需足够卫星数（≥5）；必须用公开样例或录制原始观测做领域层黄金测试，避免“看似有数、实际错误”的误导性输出。

**ROI**：大工作量 / 极高价值 — 杀手级功能，将工具从"数据展示"提升到"诊断分析"；但当前不是单位投入收益最高项，适合在 G7/U2 后作为专项推进。

---

### G2. RINEX 3.x 导出（P1，工作量：大）

**现状**：全代码库 grep `export|toCsv|RINEX|FileProvider` 在 `.kt` 中**零命中**。已采集的 `accumulatedDeltaRangeMeters`、`receivedSvTimeNanos`、`GnssClockData` 是 RINEX 的输入要素，但没有导出通路。`AndroidManifest.xml:24` 声明了 FileProvider 但未被任何导出功能使用。

**问题/缺口**：RINEX 是 RTKLIB、Bernese、PPP 软件的通用输入格式。能导出 RINEX 意味着用户可把手机采集的原始数据喂给专业后处理软件做厘米级解算——**专业 GNSS 工具的差异化标志**。

**建议方案**：

1. 新增 `domain/export/RinexWriter.kt`：按 RINEX 3.04 规范生成 `.obs` 文件头（含天线信息 G3、近似位置、时钟）。
2. 采集循环中累积观测值（伪距、载波相位、多普勒），按历元写入。
3. 通过 FileProvider + `ACTION_SEND` 分享文件。
4. 配合 G1（伪距）和 G3（天线信息）数据更完整。

**涉及文件**：新增 `domain/export/RinexWriter.kt`、`data/local/RinexFileHandler.kt`、UI 导出按钮（HistoryScreen 或新增 ExportScreen）、`file_paths.xml` 配置。

**依赖与风险**：RINEX 格式严格，需单元测试覆盖；文件可能较大，需流式写入；需 `WRITE_EXTERNAL_STORAGE` 或 MediaStore 兼容。

**ROI**：大工作量 / 极高价值 — 专业用户的核心诉求，但依赖 G1/G3 数据完整性。

---

### G3. GnssAntennaInfo 接入 ✅ 已实现（P2，工作量：中）

**现状（已实现）**：`GnssDataSourceImpl.getAntennaInfos()` 通过 `LocationManager.registerAntennaInfoListener`（API 30+）独立 Flow 采集；`AntennaInfo` + `PhaseCenterVariationSummary` 领域模型 + 纯 `AntennaInfoMapper`；Repository 透传（不采样）；`SatelliteViewModel.antennaInfos: StateFlow<List<AntennaInfo>>`；`AntennaInfoCard` 紧随 `GnssCapabilitiesCard` 展示（空列表自动隐藏）；中英文资源齐备；ProGuard 已 keep `GnssAntennaInfo` 及相关 LocationManager 方法。单元测试覆盖 mapper。

**剩余缺口**：设备端真机冒烟（API 30+ 设备的 PCO/PCV 数值展示）；G2 RINEX 头部消费 `AntennaInfo` 字段时再扩展（如完整 PCV 网格、`SphericalCorrections` 信号增益表）。

---

### G4. GnssNavigationMessage 导航电文（P3，工作量：大）

**现状**：`proguard-rules.pro:11-12,18,26-27` 保留了 `GnssNavigationMessage` 及 `registerGnssNavigationMessageCallback`，但代码中从未调用。

**问题/缺口**：可解析星历/历书的原始字节，用于电文完整性监控、离线 PPP。价值偏专业研究，普通调试场景非必需。

**建议方案**：

1. `LocationManager.registerGnssNavigationMessageCallback`（API 24+）。
2. 解析各类型（L1CA、L5、CNAV 等）的电文数据。
3. 展示原始字节十六进制 + 关键字段解码。

**涉及文件**：`data/source/GnssDataSourceImpl.kt`、新增解析器、UI 屏幕。

**依赖与风险**：解析逻辑复杂，每星座电文格式不同；多数调试用户不需要。

**ROI**：大工作量 / 低价值 — 长线可选，除非有明确专业研究用户。

---

### G5. TDOP/GDOP 补全 ✅ 已实现（P0，工作量：小）

**现状**：`DopCalculator.kt:54` 已求逆 4×4 Q 矩阵，但 `:61-63` **只用了前 3 个空间分量**算 PDOP/HDOP/VDOP，`q[3][3]`（时间项）被丢弃。`DopInfo.kt:20-35` 模型无 `tdop`/`gdop` 字段。Grep `TDOP`/`GDOP` 零命中。

**问题/缺口**：Q 矩阵已经算出来了，补 TDOP/GDOP 是**零计算成本**，只需补公式和模型字段。DOP 全家族（P/H/V/T/G）是专业 GNSS 工具的标配。

**建议方案**：

1. `DopInfo.kt` 新增 `tdop: Double`、`gdop: Double` 字段。
2. `DopCalculator.kt:61` 后补：`val tdop = sqrt(q[3][3])`，`val gdop = sqrt(q[0][0]+q[1][1]+q[2][2]+q[3][3])`。
3. `DopCard.kt:60-71` 增加两行展示。
4. 补单元测试 `DopCalculatorTest.kt`、`DopInfoTest.kt`。

**涉及文件**：`domain/model/DopInfo.kt`、`domain/util/DopCalculator.kt:61-70`、`ui/components/DopCard.kt`、对应测试。

**依赖与风险**：无新依赖；需验证 `q[3][3] > 0`（已在 `:57-59` 对前 3 项做了，需补第 4 项的校验）。

**ROI**：小工作量 / 中价值 — **投入产出比最高**，应作为「止血」阶段首批落地。

---

### G6. GnssCapabilities 设备能力查询展示 ✅ 已实现（P2，工作量：小）

**现状**：设备 GNSS 能力（`hasMeasurementCorrelations`、`hasAntennaInfo`、测量率、`isGeofencingSupported` 等）从未查询展示。

**问题/缺口**：用户无法判断"某数据为空"是设备不支持还是 bug。能力查询能消除这类困惑。

**实现方案**：

1. 新增 `domain/model/GnssCapabilitiesInfo.kt`：
    - 核心字段：`hardwareModelName`、`yearOfHardware`（API 28+），`hasMeasurements`、`hasNavigationMessages`、`hasAntennaInfo`（API 31+），`hasAccumulatedDeltaRange`、`hasMeasurementCorrections`、`hasMeasurementCorrelationVectors`（API 34+）。
    - 增加 `CapabilityState` 枚举 + `Int.toCapabilityState()` 扩展，统一处理 `SUPPORTED/UNSUPPORTED/UNKNOWN` 三态。
2. `GnssDataSource` 接口与 `GnssDataSourceImpl` 实现新增 `getGnssCapabilities()`：
    - 通过 `LocationManager.gnssCapabilities`（API 31+）查询；API 28+ 设备仍返回硬件型号/年份。
    - API < 31 设备仅返回基础信息；API < 34 设备不查询扩展能力。
    - 捕获异常（部分 OEM `gnssCapabilities` 调用可能失败）并静默降级，不影响主数据流。
3. `GnssRepository` 接口与 `GnssRepositoryImpl` 实现新增 `getGnssCapabilities()`。
4. `SatelliteViewModel` 新增 `gnssCapabilities: StateFlow<GnssCapabilitiesInfo?>`，在 `init` 中异步查询（不依赖定位权限）。
5. `SatelliteListScreen` 顶部（StatBar 后）接入 `GnssCapabilitiesCard`，能力卡片为 null 时不显示。
6. 新增 `ui/components/GnssCapabilitiesCard.kt`，按中文文案展示各能力状态。
7. `strings.xml` 添加设备能力相关标题与标签。
8. 新增 `GnssCapabilitiesInfoTest.kt` 覆盖 `CapabilityState` 转换与 data class 行为。

**涉及文件**：

- 新增：`domain/model/GnssCapabilitiesInfo.kt`
- 新增：`ui/components/GnssCapabilitiesCard.kt`
- 新增：`app/src/test/java/com/example/gpstest/domain/model/GnssCapabilitiesInfoTest.kt`
- 修改：`data/source/GnssDataSource.kt`、`data/source/GnssDataSourceImpl.kt`
- 修改：`domain/repository/GnssRepository.kt`、`domain/repository/GnssRepositoryImpl.kt`
- 修改：`viewmodel/SatelliteViewModel.kt`
- 修改：`ui/screens/satellite/SatelliteListScreen.kt`
- 修改：`app/src/main/res/values/strings.xml`

**依赖与风险**：API 31+ 才支持完整能力；API < 31 降级为硬件型号/年份或 null。查询不需要定位权限，主数据流不依赖此功能。

**ROI**：小工作量 / 中价值 — 排障自助利器，提升用户对"为何没数据"的理解。

---

### G7. 闰秒 / Location 精度字段补全 ✅ 已实现（P2，工作量：小）

**现状（已实现）**：

- `LocationInfo`：`verticalAccuracyMeters` / `bearingAccuracyDegrees` / `speedAccuracyMetersPerSecond`（nullable，默认 null）
- `GnssDataSourceImpl`：API 26+ 守卫采集上述 Location 精度字段；`GnssClock.hasLeapSecond()` → `GnssClockData.leapSecond`
- UI：`LocationCard` 展示垂直/航向/速度精度；`ClockInfoCard` 展示闰秒
- 测试：`LocationInfoTest`（2）+ `GnssClockDataTest` 增补 leapSecond（2）
- **未采** `Location.extras` 卫星数（OEM 不稳定，按方案刻意跳过）

---

## 三、🎨 用户体验增强

### U1. 设置屏幕 ✅ 已实现（P1，工作量：中）

**现状（已核实）**：

- **设置屏**：`ui/screens/settings/SettingsScreen.kt` + 抽屉导航 `Screen.Settings`
- **深色模式三态**：`DarkModeConfig` {SYSTEM, ON, OFF}，`AppSettings.resolveDarkTheme()`，`MainActivity.setContent` 收集并传 `Theme(darkTheme=…)`
- **快照配置**：`SettingsStore`（DataStore `app_settings`）→ `SatelliteViewModel` 读 interval/autoSave；`SatelliteHistoryDataStore` 读 maxSnapshots + retentionDays
- **领域模型**：`domain/model/Settings.kt` + 7 个单元测试 `SettingsTest`

**剩余缺口**：清信号历史按钮、Material You dynamicColor 开关（可选）。

---

### U2. 天空图交互增强 ✅ 已实现（P1，第一阶段工作量：小；完整工作量：中）

**现状（已核实）**：一期 + 续均已落地：

- **SVID 标签**：`SkyChartView.kt` 绘制循环旁用 `drawText` 显示 `sat.svid`
- **星座过滤**：`SkyChartScreen` 维护 `visibleConstellations`，`SkyChartLegend` 可点击切换
- **双指缩放/平移**：`detectTransformGestures` + `SkyChartTransformState`
- **位置动画**：`rememberAnimatedSatellites`
- **指北旋转**：`CompassHeadingSource`（`TYPE_ROTATION_VECTOR`）+ `northUp` 开关
- **截图分享**：仍未做（可选，P3）

**剩余缺口**：截图分享（可选）。

**涉及文件**：`SkyChartView.kt`、`SkyChartLegend.kt`、`SkyChartScreen.kt`、`SkyChartMath.kt`、`SkyChartTransformState.kt`、`CompassHeadingSource.kt` + 对应单元测试。

**ROI**：已兑现 — 天空图从静态展示升级为可交互调试视图。

---

### U3. 历史趋势图 + 导出 + 详情钻取 ✅ 已完成（P1，工作量：大）

**现状（已实现）**：

1. **快照字段扩展**：`SatelliteHistorySnapshot` 增加 lat/lon/accuracy/pdop/hdop/vdop/ttffMs（可选，旧 JSON 兼容）
2. **详情钻取 + 删除**：`HistorySnapshotCard` 可展开卫星明细，支持单条删除/分享
3. **CSV 导出**：`HistoryCsvExporter`（摘要+明细）+ `HistoryExportHelper`（FileProvider + ACTION_SEND）
4. **趋势图**：`HistoryTrendChart` 绘制平均信号 / 定位星数 / 可见星数
5. **时间筛选**：`HistoryTimeFilter`（全部/1h/6h/24h/7d）
6. **保存链路**：`saveSnapshot` / `maybeSaveSnapshot` 写入定位质量字段

**涉及文件**：`SatelliteHistory.kt`、`HistoryCsvExporter.kt`、`HistoryExportHelper.kt`、`SatelliteHistoryDataStore.kt`、`SatelliteHistoryRepository*`、`SatelliteViewModel.kt`、`HistoryScreen.kt`、`HistorySnapshotCard.kt`、`HistoryTrendChart.kt`、`file_paths.xml`、`strings.xml` + 领域单测

**ROI**：已兑现 — 历史从“只存不用”升级为可分析、可导出、可钻取。

---

### U4. 卫星列表筛选/排序/冻结 ✅ 已实现（P2，工作量：中）

**现状（已实现）**：

1. **领域查询**：`SatelliteListQuery` + `SatelliteSortMode` 支持星座集合过滤、SVID 子串搜索、CN0/仰角/SVID 排序。
2. **UI 工具栏**：`SatelliteFilterBar`（FilterChip 星座、排序 Chip、SVID 搜索框、冻结/解冻按钮）。
3. **列表接线**：`SatelliteListScreen` 对三组卫星应用 query；冻结时快照列表，实时状态变化不覆盖冻结帧。
4. **单测**：`SatelliteListQueryTest` 覆盖过滤/排序组合。

**涉及文件**：`domain/util/SatelliteListQuery.kt`、`domain/model/SatelliteSortMode`、`ui/components/SatelliteFilterBar.kt`、`ui/screens/satellite/SatelliteListScreen.kt`、对应测试与 strings。

**ROI**：已兑现 — 多星座长列表可筛可排可冻帧观察。

---

### U5. A-GPS 补全 ✅ 已实现（P2，工作量：中）

**现状（已实现）**：

1. **文件导入**：`AGpsFileHandler` 读 Uri → 校验 → 缓存 → `importAndInject`；UI 导入按钮 + Activity Result。
2. **URL / 间隔**：`AGpsManagerScreen` 下载 URL 输入框 + 1/6/12/24h 间隔 Chip，写入 `AGpsSettingsStore`。
3. **注入历史持久化**：`AGpsInjectionHistoryStore`（DataStore，上限 50）+ 清除按钮；Worker 注入不再整表清空。
4. **ViewModel**：`importAndInject` / `clearInjectionHistory` 暴露；Snackbar 走标准 Host。

**涉及文件**：`AGpsFileHandler(Impl).kt`、`AGpsInjectionHistoryStore.kt`、`AGpsRepository(Impl).kt`、`AGpsViewModel.kt`、`AGpsManagerScreen.kt`、`AGpsSettingsStore.kt` + 单测。

**ROI**：已兑现 — 激活 import_file、可自定义源与间隔、历史可持久可清除。

---

### U6. 信噪比柱状图 + DOP 实时曲线（P3，工作量：中）

**现状**：

- 信噪比柱状图：grep `drawRect`/`drawRoundRect`/Bar 组件零命中，`SatelliteCard`/`StatBar` 都是文本数字。旧 TODO 标记 `[ ]` 未完成（正确）。
- DOP 实时曲线：grep `DopChart`/`dopHistory` 零命中，`SatelliteViewModel` 没有为 DopInfo 维护历史缓冲（只有 signalHistory）。旧 TODO 标记 `[ ]` 未完成（正确）。

**问题/缺口**：两个旧 TODO 项，价值中等但非紧急。柱状图提供信号强度直观对比；DOP 曲线展示几何精度随时间变化。

**建议方案**：

1. 新增 `ui/components/SignalBarChart.kt`：按卫星分组（或按星座）的 CN0 柱状图。
2. `SatelliteViewModel` 新增 `dopHistory: List<DopInfo>`（环形缓冲，60 点）。
3. 新增 `ui/components/DopTrendChart.kt` 或复用 SignalChart 画 PDOP/HDOP/VDOP 三线。

**涉及文件**：新增 `ui/components/SignalBarChart.kt`、`ui/components/DopTrendChart.kt`、`viewmodel/SatelliteViewModel.kt`（dopHistory）、`SatelliteListScreen.kt`（接入）。

**依赖与风险**：可选评估引入 Compose 图表库（Vico/YCharts）vs 自绘 Canvas。

**ROI**：中工作量 / 中价值 — 视觉化增强，优先级低于核心功能。

---

## 四、🔧 工程化健康度

### E1. Release minify 开启 + ProGuard 补全 ✅ 已实现（P2，工作量：中）

**现状（已实现）**：`isMinifyEnabled = true` + `isShrinkResources = true`；`proguard-rules.pro` 已补 kotlinx.serialization / OkHttp / WorkManager / Shizuku / GNSS keep；`assembleRelease` 通过。

**剩余缺口**：设备端 R8 冒烟（A-GPS/历史/Shizuku）；Release 签名配置仍可选。

---

### E2. CI 增加 lint / 覆盖率 ✅ 已实现（P2，工作量：小）

**现状（已实现）**：`push`/`pull_request`（master）+ `workflow_dispatch`；步骤含 `ktlintCheck`、`lintDebug`、`test`、`assembleDebug`/`assembleRelease`；`.github/dependabot.yml` weekly（gradle + github-actions）。

**剩余缺口**：JaCoCo/覆盖率上传、instrumented emulator job、Detekt（可选）。

---

### E3. Timber 日志 ✅ 已实现（P2，工作量：中；崩溃上报未做）

**现状（已实现）**：Timber 经 Version Catalog 接入；`GpsTestApplication` DebugTree / ReleaseTree；原 4 个 A-GPS 文件 `android.util.Log` 已替换。

**刻意未做**：Firebase Crashlytics / Sentry（Wave B 默认 Timber only）。

---

### E4. ViewModel/Repository 测试覆盖 ✅ 已实现（P1，工作量：中）

**现状**：已通过 commit `ef99db0 test(E4): 补全 ViewModel/Repository/DataSource 单元测试` 实现核心业务逻辑回归安全网。新增测试覆盖 `AGpsRepositoryImpl`、`AGpsViewModel`、`SatelliteViewModel`、`AGpsDownloaderImpl`、下载流程与 androidTest 骨架；提交时记录为新增 98 个用例、全量 246/0 失败。

**已覆盖**：

- `AGpsRepositoryImplTest`：多 URL 回退、注入验证阈值、历史上限、时间衰减状态机、清除辅助数据、时间注入。
- `AGpsViewModelTest`：状态机与自动更新调度入口。
- `SatelliteViewModelTest`：TTFF、信号历史 60 条环形缓冲、分组、错误态、history flow。
- `AGpsDownloaderImplTest` / `AGpsDownloaderDownloadTest`：URL 校验、默认 URL、MockWebServer 下载成功/失败路径。
- `ExampleInstrumentedTest`：androidTest 骨架，激活空置依赖。

**剩余缺口**：

- `AGpsUpdateWorker.doWork` 仍未覆盖，原因是 worker 内部硬 new 依赖链；需先引入 `WorkerFactory` 或显式依赖注入。
- `GnssDataSourceImpl` 仍未覆盖，原因是 100% Android framework callback 耦合；需 Robolectric 或抽出薄适配层。
- CI 目前仅 `workflow_dispatch` 手动触发，测试安全网的收益低于自动 push/PR 门禁场景。

**后续建议**：把 E4 从待办移出；后续测试工作拆到 E2（CI 门禁）和新增小项（WorkerFactory/Robolectric）中，不再作为当前 P1 待办。

**涉及文件**：`app/build.gradle.kts`、`app/src/test/java/com/example/gpstest/domain/repository/AGpsRepositoryImplTest.kt`、`app/src/test/java/com/example/gpstest/viewmodel/AGpsViewModelTest.kt`、`app/src/test/java/com/example/gpstest/viewmodel/SatelliteViewModelTest.kt`、`app/src/test/java/com/example/gpstest/data/source/AGpsDownloaderImplTest.kt`、`app/src/test/java/com/example/gpstest/data/source/AGpsDownloaderDownloadTest.kt`、`app/src/androidTest/java/com/example/gpstest/ExampleInstrumentedTest.kt`。

**依赖与风险**：已引入 `mockk`、`kotlinx-coroutines-test`、`turbine`、`mockwebserver`、`work-testing(androidTest)`；后续如覆盖 `GnssDataSourceImpl` 可能需要 Robolectric。

**ROI**：已兑现 — 核心逻辑回归保障已建立，是后续重构（E1/E7）的安全网。

---

### E5. 国际化（i18n）✅ 已实现基线（P2，工作量：中）

**现状（已实现）**：`values/` 中文默认 + `values-en/strings.xml` 英文；导航/天空图/统计卡/AGpsViewModel 用户可见文案已进资源；`app_name` 中英分离。

**剩余缺口**：部分内部错误串（Downloader/Validator）仍中文；帮助长文案可继续补全英文。

---

### E6. Version Catalog 迁移 ✅ 已实现（P3，工作量：小）

**现状（已实现）**：`gradle/libs.versions.toml` + 根/app `build.gradle.kts` 使用 `libs.*` 插件与依赖别名。

---

### E7. 历史存储迁移 Room（P3，工作量：大）

**现状**：`SatelliteHistoryDataStore.kt:23` 用 `stringPreferencesKey("snapshots_history")`，100 个快照塞进单个 JSON 字符串。每次写都要「读全量 JSON → 反序列化 → 改 → 序列化 → 写」（`:39-58`），数据增大后变慢且非原子。

**问题/缺口**：无法按时间范围查询、聚合统计、分页；写入性能随数据量下降。

**建议方案**：

1. 引入 `androidx.room`，定义 `SatelliteSnapshotEntity`、`SatelliteEntryEntity`（一对多）。
2. DAO 支持按时间范围查询、按星座过滤、分页（Paging 3）。
3. 迁移旧 JSON 数据到 Room。
4. 配合 U3 历史趋势图，支持高效查询。

**涉及文件**：`app/build.gradle.kts`（Room 依赖）、新建 `data/local/db/`（Entity/DAO/Database）、迁移逻辑、`SatelliteHistoryDataStore.kt` 废弃。

**依赖与风险**：Room 引入增加 APK 体积；需数据迁移；ksp 注解处理器。

**ROI**：大工作量 / 中价值 — 仅在历史功能（U3）做厚后才值得，否则 DataStore 够用。

---

### E8. 文档补全 ✅ 已实现基线（P3，工作量：小）

**现状（已实现）**：`CONTRIBUTING.md`、`CHANGELOG.md`、`docs/ARCHITECTURE.md` 已添加（PR/commit 规范、Keep a Changelog、Clean MVVM/DI/管道说明）。

**剩余缺口**：`SECURITY.md` / `CODE_OF_CONDUCT.md` / ADR 目录仍可选。

---

## 五、🗺️ 推荐实施路线图

按依赖关系和投入产出比分四阶段推进。每个阶段目标独立可交付，阶段间有明确的依赖链。

### 阶段一·止血（1–2 天，快速见效）

**目标**：修复死代码 bug，补零成本缺失项，消除误导标记。

| 顺序 | 条目                       | 预估工作量 | 关键产出                                                                                               |
| ---- | -------------------------- | ---------- | ------------------------------------------------------------------------------------------------------ |
| 1    | **B1** dumpsys 通路修复 ✅ | 中         | `ShizukuHelper.fetchDumpsysLocation` + 解析 + 接入数据源，ClockInfoCard 的 DumpsysDataSection 终于显示 |
| 2    | **G5** TDOP/GDOP 补全 ✅   | 小         | `DopCalculator` 补 2 行公式 + DopInfo 字段 + 测试，DOP 全家族                                          |
| 3    | **B2** 载波相位标记纠错 ✅ | 小         | `GnssDataSourceImpl.kt:129` 接入真实采集（API 34+），补 UI 展示                                        |

**依赖**：无；**风险**：B1 需测试不同 OEM dumpsys 格式。

---

### 阶段二·核心能力（3–7 天，差异化）

**目标**：建立工具的专业调试价值，从"数据展示"升级到"诊断分析"。

| 顺序 | 条目                                         | 预估工作量 | 关键产出                                               |
| ---- | -------------------------------------------- | ---------- | ------------------------------------------------------ |
| 1    | **U2** 天空图交互（一期+续） ✅              | 中         | SVID/过滤/缩放/动画/指北已落地                         |
| 2    | **G7** Location 精度字段 + 闰秒 ✅           | 小         | 垂直/航向/速度精度 + 闰秒采集与展示                    |
| 3    | **G1** 伪距 + WLS 核心 ✅；后续电文/星历接线 | 大         | 已完成纯领域解算；后续实现真实定位解与系统位置残差对比 |
| 4    | **B3** NMEA 监听 ✅                          | —          | 已实现：监听/解析/屏幕/导出/设置开关                   |

**依赖**：G6/U2/G7/B3 已完成；G1 阶段 1/2 已完成，后续依赖导航电文、星历与实时卫星位置。
**风险**：G1 后续卫星位置计算复杂，不可按直接伪距 API 实现。

---

### 阶段三·体验完善（5–10 天，用户留存）

**目标**：让已实现的功能真正好用，补全半成品。

| 顺序 | 条目                              | 预估工作量 | 关键产出                               |
| ---- | --------------------------------- | ---------- | -------------------------------------- |
| 1    | **U1** 设置屏幕 ✅                | —          | 已实现：主题三态 + 快照配置            |
| 2    | **U3** 历史趋势图 + CSV 导出 ✅   | —          | 已实现：趋势图/CSV/详情钻取/删除/筛选  |
| 3    | **U4** 卫星列表筛选/排序/冻结 ✅  | —          | 已实现：FilterChip/排序/SVID 搜索/冻结 |
| 4    | **U5** A-GPS 补全 ✅              | —          | 已实现：导入/URL/间隔/历史持久化       |
| 5    | **U2 续** 天空图缩放/动画/指北 ✅ | —          | 已实现；可选截图分享仍未做             |

**依赖**：U3 已完成（可选依赖 U1 快照配置）；其余独立。
**风险**：U3 快照字段扩展已用可选字段兼容旧 JSON。

---

### 阶段四·工程化（持续滚动）

**目标**：提升工程质量、可观测性、测试覆盖。可与前三阶段并行，或作为「无功能需求时的填充」。

| 顺序 | 条目                      | 预估工作量 | 关键产出                                  |
| ---- | ------------------------- | ---------- | ----------------------------------------- |
| 1    | **E2** CI ✅              | —          | push/PR + ktlint + lintDebug              |
| 2    | **E1** minify ✅          | —          | Release R8 + ProGuard 规则                |
| 3    | **E3** Timber ✅          | —          | 无 Crashlytics（刻意）                    |
| 4    | **E5** i18n 基线 ✅       | —          | values-en + 主要 UI 资源化                |
| 5    | **E8** 文档基线 ✅        | —          | CONTRIBUTING/CHANGELOG/ARCHITECTURE       |
| 6    | **E6** Version Catalog ✅ | —          | libs.versions.toml                        |
| 7    | **G3** GnssAntennaInfo ✅ | —          | API 30+ 独立 Flow + PCO/PCV 摘要卡片      |
| 8    | **G2** RINEX 导出         | 大         | 专业用户核心诉求（依赖 G1/G3，G3 已就绪） |
| 9    | **E7** Room 迁移          | 大         | 仅在 U3 做厚后                            |
| 10   | **G4** 导航电文           | 大         | 长线可选                                  |
| 11   | **U6** 柱状图/DOP 曲线    | 中         | 视觉化锦上添花                            |

**建议**：Wave B 工程化 + G3 已落地；下一优先 G1 接线（为 G2 RINEX 提供观测）。

---

### 路线图可视化

```
阶段一·止血(1-2天)         阶段二·核心(3-7天)        阶段三·体验(5-10天)       阶段四·工程化(持续)
┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐
│ B1 dumpsys修复 ★ │    │ U2 天空图 ★ ✅  │    │ U1 设置 ★ ✅   │    │ E2 CI ✅        │
│ G5 TDOP/GDOP ★   │ ──→│ G7 精度+闰秒 ✅ │ ──→│ U3 历史+导出 ✅ │ ──→│ E1 minify ✅    │
│ B2 载波相位纠错  │    │ G1 伪距推导+定位 │    │ U4 列表筛选 ✅ │    │ E3 Timber ✅    │
└──────────────────┘    │ B3 NMEA监听 ✅   │    │ U5 A-GPS补全 ✅│    │ E5/E6/E8 ✅     │
                         └──────────────────┘    └──────────────────┘    │ G3 天线 ✅       │
                                                                          │ G2 RINEX (剩)    │
                                                                          │ E7, G4, U6       │
                                                                          └──────────────────┘
★ = 阶段内最高优先级
```

---

## 六、✅ 已实现功能清单（里程碑记录）

> 旧版 TODO.md 标记经代码核实，以下功能均为**完整实现**或已形成明确里程碑记录，修正了旧版的多处标记错误。

### 高价值功能

| 功能                        | 旧标记   | 核实结论                        | 关键证据                                                                                                                |
| --------------------------- | -------- | ------------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| 卫星天空图（Sky View）      | `[*]`    | ✅ 完整实现                     | `SkyChartView.kt:64-209` 极坐标投影 + `SkyChartScreen.kt:139-146`                                                       |
| 多路径指示                  | `[*]`    | ✅ 完整实现                     | `GnssDataSourceImpl.kt:108` 采集 + `SatelliteDetailSheet.kt:106-112` 展示                                               |
| 自动增益控制（AGC）         | `[*]`    | ✅ 完整实现                     | `GnssDataSourceImpl.kt:100-107` 采集 + `SatelliteDetailSheet.kt:97-100` 展示                                            |
| HDOP/VDOP/PDOP              | `[ ]` ❌ | ✅ 完整实现                     | `DopCalculator.kt:18-71` 算法 + `DopCard.kt:60-71` 展示（含单元测试）                                                   |
| TTFF（首次定位时间）        | `[ ]` ❌ | ✅ 完整实现                     | `SatelliteViewModel.kt:44-45,92-102` 状态机 + `TtffCard.kt:25-100`                                                      |
| 信号历史曲线                | `[ ]` ❌ | ✅ 完整实现                     | `SignalChart.kt:132-176` 折线图 + `SatelliteDetailSheet.kt:150` 接入                                                    |
| **TDOP/GDOP 补全**          | —        | ✅ 已实现 (2026-07-12)          | `DopInfo.kt` + `DopCalculator.kt:62-63` 公式 + `DopCard.kt` 分组展示 + Help 解释 + 2 个新测试                           |
| **GnssCapabilities**        | —        | ✅ 已实现 (2026-07-12)          | `GnssCapabilitiesInfo.kt` + `GnssCapabilitiesCard.kt` + 数据源/仓库/ViewModel 接线 + 单元测试                           |
| **天空图交互 U2**           | —        | ✅ 已实现                       | SVID 标签 + 星座过滤 + 缩放/平移 + 位置动画 + 指北（`SkyChart*` / `CompassHeadingSource`）                              |
| **设置屏幕 U1**             | —        | ✅ 已实现                       | `SettingsScreen` + `SettingsStore` + 深色三态 + 快照 interval/max/retention 接线 + 7 测试                               |
| **Location 精度 + 闰秒 G7** | —        | ✅ 已实现 (2026-07-15)          | `LocationInfo` 垂直/航向/速度精度 + `GnssClockData.leapSecond` + LocationCard/ClockInfoCard 展示 + 测试                 |
| **G1 伪距 + WLS 核心**      | —        | ✅ 阶段 1/2 已实现 (2026-07-15) | `PseudorangeCalculator.kt` + `PositionSolver.kt`：伪距推导、纯领域迭代 WLS、黄金与边界测试；真实星历/卫星位置接线待后续 |
| **B3 NMEA 监听**            | —        | ✅ 已实现                       | `addNmeaListener` → `Flow<NmeaSentence>` + `NmeaParser` + `NmeaScreen`/导出/设置开关 + 领域单测                         |
| **U4 列表筛选/排序/冻结**   | —        | ✅ 已实现                       | `SatelliteListQuery` + `SatelliteFilterBar` + 冻结快照；星座/SVID/CN0·仰角排序 + 单测                                   |
| **U5 A-GPS 补全**           | —        | ✅ 已实现                       | 文件导入、URL 编辑、1/6/12/24h 间隔、注入历史 DataStore 持久化/清除 + ViewModel/UI 接线                                 |
| **Wave B 工程化**           | —        | ✅ 已实现 (2026-07-18)          | E2 CI push/PR+ktlint+lint；E8 文档；E6 Catalog；E1 minify；E3 Timber；E5 values-en 基线                                 |
| **G3 天线相位中心**         | —        | ✅ 已实现 (2026-07-18)          | `AntennaInfo` + `AntennaInfoMapper` + `GnssDataSourceImpl.getAntennaInfos()` (API 30+) + `AntennaInfoCard` + 中英资源 + 测试 |

### 专业/调试功能

| 功能             | 旧标记 | 核实结论    | 关键证据                                                                                    |
| ---------------- | ------ | ----------- | ------------------------------------------------------------------------------------------- |
| 伪距变化率       | `[x]`  | ✅ 完整实现 | `GnssDataSourceImpl.kt:126` + `SatelliteDetailSheet.kt:125-128`                             |
| 卫星时间不确定度 | `[x]`  | ✅ 完整实现 | `GnssDataSourceImpl.kt:124` + `SatelliteDetailSheet.kt:137-140`                             |
| 基带 C/N0        | `[*]`  | ✅ 完整实现 | `GnssDataSourceImpl.kt:223-228`（API 30+）+ `ClockInfoCard.kt:102-121` 全局均值             |
| 时钟偏差/漂移    | `[*]`  | ✅ 完整实现 | `GnssDataSourceImpl.kt:134-172` + `GnssClockData.kt:29-43` 派生 + `ClockInfoCard.kt:73-100` |
| 星座健康状态汇总 | `[*]`  | ✅ 完整实现 | `ConstellationHealthSummaryCard.kt:35-101` 进度条 + 百分比                                  |

### 工程化里程碑

| 功能                          | 旧标记 | 核实结论               | 关键证据                                                                                               |
| ----------------------------- | ------ | ---------------------- | ------------------------------------------------------------------------------------------------------ |
| ViewModel/Repository 测试覆盖 | —      | ✅ 已实现 (2026-07-12) | commit `ef99db0`：新增 98 个用例，覆盖 AGpsRepository/ViewModel/SatelliteViewModel/Downloader 核心路径 |

### 标记错误纠正记录

| 功能                 | 旧标记    | 实际状态      | 错误类型                                        |
| -------------------- | --------- | ------------- | ----------------------------------------------- |
| 信号历史曲线         | `[ ]`     | ✅ 已实现     | 标记偏低                                        |
| HDOP/VDOP/PDOP       | `[ ]`     | ✅ 已实现     | 标记偏低                                        |
| TTFF                 | `[ ]`     | ✅ 已实现     | 标记偏低                                        |
| **载波相位完整周期** | **`[x]`** | **✅ 已修复** | **原反方向错误，已于 2026-06-14 修复（见 B2）** |

> **载波相位完整周期**旧版标记 `[x]` 已完成，但 `GnssDataSourceImpl.kt:129` 实际硬编码 `fullCarrierPhaseCycleCount = null`，从未采集也未展示。**已于 2026-06-14 修复（B2）**：接入真实采集 + UI 展示。

---

## 附录：探索方法说明

本文档基于对以下维度的只读代码探索：

- **UI 层**：5 个屏幕 + 16 个组件的完整功能审查
- **领域/数据层**：所有数据模型、数据源、仓库、工具类的实现审查
- **工程化**：构建配置、测试覆盖（E4 后提交记录为 246 用例）、CI 流程、Manifest、ProGuard、i18n、文档
- **TODO 标记核实**：旧版 15 项逐一对照代码实现状态

所有 `file:line` 引用均来自 master 分支近期基线（含 `4b28455` 后的 G1 阶段 1/2 实现）。如代码后续变更，行号可能偏移，但文件名与逻辑位置应保持参考价值。
