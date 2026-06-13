# GNSS 调试工具 — 功能路线图

> **核实基准日期**：2026-06-14（基于 master 分支 `d22baec`）
> **目的**：经代码核实的功能增强清单与实施路线图，取代旧版 checkbox 列表
> **变更说明**：旧版 TODO.md 有 6 处标记错误，已在「第六章 已实现功能清单」中修正

---

## 📖 阅读说明

### 优先级标记

| 标记 | 含义 | 触发时机 |
|------|------|----------|
| **P0** | 紧急 — 死代码/数据丢失/严重误导 | 立即处理 |
| **P1** | 高 — 核心价值或差异化能力 | 当前迭代 |
| **P2** | 中 — 体验改善或工程稳健性 | 近期规划 |
| **P3** | 低 — 锦上添花或长线优化 | 滚动推进 |

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

### B3. NMEA 监听完全未接线（P2，工作量：中）

**现状**：`strings.xml:49-51` 已预留 `nmea_*` 文案和格式化字符串，但全代码库 grep `onNmeaMessage`/`addNmeaListener`/`registerNmea` **零命中**。文案是死代码。

**问题/缺口**：NMEA 是 GNSS 数据交换的事实标准（`$GPGGA`、`$GPGSV`、`$GPRMC` 等），许多用户期望日志记录与导出；也能交叉验证内部回调数据。当前完全没有 NMEA 能力。

**建议方案**：
1. 在 `GnssDataSourceImpl` 中用 `LocationManager.addNmeaListener(executor, callback)`（API 24+）注册监听。
2. 将 NMEA 句子收集到 `Flow<String>` 或缓冲列表。
3. 新增 `NmeaScreen` 屏幕展示实时语句流，或并入 SatelliteList 的新 Tab。
4. 后续可配合 G2（RINEX 导出）做 NMEA 日志文件导出。

**涉及文件**：`data/source/GnssDataSourceImpl.kt`、`domain/model/`（新增 NMEA 模型）、新建 `ui/screens/nmea/`。

**依赖与风险**：高频 NMEA 流可能影响性能，需做节流；部分 OEM 设备不输出完整语句。

**ROI**：中工作量 / 中价值 — 文案已就绪，是调试工具的基础能力补全，但优先级低于 B1。

---

## 二、🚀 专业 GNSS 能力增强

### G1. 原始伪距采集 + 本地最小二乘定位解（P1，工作量：大）

**现状**：`GnssDataSourceImpl.kt:126` 只读取了 `pseudorangeRateMetersPerSecond`（速率），**`GnssMeasurement.getPseudorangeMeters()` / `getPseudorangeUncertaintyMeters()`（API 31+）完全未采**。`GnssSatellite.kt:41-69` 模型中无 `pseudorangeMeters` 字段。`GnssClockData.totalBiasNanos`（`GnssClockData.kt:29-36`）已采集时钟偏移。

**问题/缺口**：有了原始伪距 + 接收机时钟偏移 + 卫星位置（可从星历算），可**本地计算定位解**（加权最小二乘），与 Android 报告位置做残差对比——这是 GNSS 调试工具的核心价值。当前用户只能看 Android 给的"成品"位置，无法诊断"为什么定位偏了"。

**建议方案**：
1. `GnssSatellite` 新增 `pseudorangeMeters`、`pseudorangeUncertaintyMeters` 字段（API 31+ 守卫）。
2. `GnssDataSourceImpl.kt:126` 附近补充采集。
3. 新增 `domain/util/PositionSolver.kt`：从伪距 + 时钟偏移 + 卫星位置解算位置（迭代最小二乘）。
4. 卫星位置计算：接入 G3（GnssAntennaInfo）+ 星历，或先用简化模型（如广播星历解析）。
5. UI 增加对比卡片：系统位置 vs 本地解算位置，显示差异（米）。

**涉及文件**：`domain/model/GnssSatellite.kt`、`data/source/GnssDataSourceImpl.kt`、新增 `domain/util/PositionSolver.kt`、新增 UI 卡片。

**依赖与风险**：API 31+；卫星位置计算复杂（需星历/历书），可作为二期；最小二乘需足够卫星数（≥5）；需新增领域层测试。

**ROI**：大工作量 / 极高价值 — 杀手级功能，将工具从"数据展示"提升到"诊断分析"。

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

### G3. GnssAntennaInfo 接入（P2，工作量：中）

**现状**：`proguard-rules.pro:11-38` 明确保留了 `GnssAntennaInfo`/`PhaseCenterOffset` 类，但全代码库 grep `registerAntennaInfoListener`、`PhaseCenterOffset` **零命中**，从未调用。

**问题/缺口**：天线相位中心偏移（PCO）和相位中心变化（PCV）对高精度测量至关重要。proguard 配置说明设计时考虑过，但未实现。

**建议方案**：
1. 在 `GnssDataSourceImpl` 中 `LocationManager.registerAntennaInfoCallback`（API 31+）。
2. 新增 `domain/model/GnssAntennaInfo.kt` 数据类。
3. 新增「天线信息」卡片或在 ClockInfoCard 旁扩展，展示 PCO/PCV。
4. 数据可用于 G2（RINEX 头部的天线信息段）。

**涉及文件**：`data/source/GnssDataSourceImpl.kt`、新增 `domain/model/GnssAntennaInfo.kt`、UI 卡片。

**依赖与风险**：API 31+，部分设备无天线信息；需在 UI 提示"设备不支持"。

**ROI**：中工作量 / 中价值 — G2 RINEX 的前置依赖之一，独立价值偏低但组合价值高。

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

### G5. TDOP/GDOP 补全（P0，工作量：小）

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

### G6. GnssCapabilities 设备能力查询展示（P2，工作量：小）

**现状**：设备 GNSS 能力（`hasMeasurementCorrelations`、`hasAntennaInfo`、测量率、`isGeofencingSupported` 等）从未查询展示。

**问题/缺口**：用户无法判断"某数据为空"是设备不支持还是 bug。能力查询能消除这类困惑。

**建议方案**：
1. 启动时 `LocationManager.getGnssCapabilities()`（API 31+）。
2. 新增 `domain/model/GnssCapabilitiesInfo.kt`。
3. 在 HelpScreen 或 SatelliteListScreen 增加设备能力卡片。

**涉及文件**：`data/source/GnssDataSourceImpl.kt`、新增模型、UI 卡片。

**依赖与风险**：API 31+，老设备用 Build.VERSION 降级。

**ROI**：小工作量 / 中价值 — 排障自助利器，提升用户对"为何没数据"的理解。

---

### G7. 闰秒 / Location 精度字段补全（P2，工作量：小）

**现状**：
- `GnssClock`/`GnssMeasurement` 的 `hasLeapSecond`/`leapSecond`（闰秒，用于 GPS 时↔UTC 换算）未采集。
- `GnssDataSourceImpl.kt:278-293` 从 `Location` 只取了 lat/lon/alt/accuracy/speed/bearing/time，**未采** `verticalAccuracyMeters`、`bearingAccuracyDegrees`、`speedAccuracyMetersPerSecond`（API 26+）、`location.extras` 中的卫星数。Grep `verticalAccuracyMeters` 零命中。
- `LocationInfo.kt:3-13` 模型无对应字段。

**问题/缺口**：闰秒影响所有时间戳换算精度；垂直精度（实测 VDOP 对照）对 GPS 调试有意义。

**建议方案**：
1. `LocationInfo` 新增 `verticalAccuracyMeters`、`bearingAccuracyDegrees`、`speedAccuracyMetersPerSecond`、`satelliteCountInFix`（extras）字段。
2. `GnssDataSourceImpl.kt:278-293` 补充采集（API 26+ 守卫）。
3. `LocationCard.kt` 增加展示。
4. `GnssClockData` 新增 `leapSecond: Int?`，采集时读取。

**涉及文件**：`domain/model/LocationInfo.kt`、`domain/model/GnssClockData.kt`、`data/source/GnssDataSourceImpl.kt:278-293`、`ui/components/LocationCard.kt`。

**依赖与风险**：API 26+ 守卫；测试需更新现有 `GnssClockDataTest`。

**ROI**：小工作量 / 中价值 — 数据完整性补全，调试细节更丰富。

---

## 三、🎨 用户体验增强

### U1. 设置屏幕（P1，工作量：中）

**现状**：项目**无 Settings 屏幕**。`Theme.kt:103-108` 的 `Theme()` 别名不接受 `dynamicColor` 参数，`MainActivity.kt:116` 调用未传主题选项；`strings.xml:61-62` 预留 `settings_dark_mode` 文案但无 UI 入口（死代码）。`SatelliteHistoryConfig`（`SatelliteHistory.kt:96-100`）定义了快照配置但**实际未被读取**（`SatelliteHistoryDataStore.kt:24` 硬编码 `MAX_SNAPSHOTS = 100`）。

**问题/缺口**：用户无法切换日夜模式、无法开关 Material You、无法调整快照频率/上限、无法清缓存。配置能力是基础体验缺口。

**建议方案**：
1. 新建 `ui/screens/settings/SettingsScreen.kt`，加入导航（`MainActivity.kt:169-181`）。
2. 新增 `data/local/ThemePreferencesStore.kt`（DataStore）存主题偏好。
3. `Theme.kt` 改造为从 DataStore 读取偏好，支持「跟随系统/亮/暗」三态 + Material You 开关。
4. 快照配置项：间隔（30/60/120 秒）、上限（100/500/1000）、自动保存开关，写入实际生效的 config。
5. 清缓存按钮：清除历史快照、信号历史。

**涉及文件**：新建 `ui/screens/settings/`、`data/local/ThemePreferencesStore.kt`、`ui/theme/Theme.kt:103`、`MainActivity.kt:116,169-181`、`data/local/SatelliteHistoryDataStore.kt`（读取 config）。

**依赖与风险**：需引入 `androidx.datastore`（已有）；主题切换需 recompose 整个应用。

**ROI**：中工作量 / 高价值 — 解决多个死代码（strings、config），用户基础控制能力。

---

### U2. 天空图交互增强（P1，工作量：中）

**现状**：`SkyChartView.kt`（196 行 Canvas）实现了基础极坐标投影和点击卫星弹详情，但**交互最单薄**：
- 无缩放/平移（grep `transformable`/`detectTransformGestures` 零命中）
- 图例点击不能切换星座显示（`SkyChartLegend.kt` 是纯展示）
- 卫星点无 SVID 标签（`SkyChartView.kt:172-190` 绘制循环只画圆）
- 无位置动画过渡（卫星跳变，无 `animateFloatAsState`）
- 无指北旋转（未接入指南针传感器）

**问题/缺口**：天空图是调试工具的视觉核心，当前像静态图。卫星密集时无法放大、无法快速识别哪颗是哪颗。

**建议方案**（可分阶段）：
1. **SVID 标签**（小）：在卫星点旁绘制 SVID 数字，避免必须点开才知道。
2. **星座过滤**（小）：图例变可点击 `FilterChip`，切换某星座显示/隐藏。
3. **双指缩放**（中）：用 `Modifier.pointerInput { detectTransformGestures }` 包裹 Canvas，缩放/平移坐标系。
4. **位置动画**（中）：用 `Animatable`/`animateFloatAsState` 平滑过渡卫星位置。
5. **指北旋转**（中，可选）：接入 `Sensor.TYPE_ORIENTATION` 或 `TYPE_ROTATION_VECTOR`，旋转画布使前进方向朝上。
6. **截图分享**（小，可选）：`Canvas` 转 Bitmap，通过 FileProvider 分享。

**涉及文件**：`ui/screens/skychart/SkyChartView.kt`、`SkyChartLegend.kt`、`SkyChartScreen.kt`；指北旋转需 `data/source/` 新增传感器接入。

**依赖与风险**：动画可能增加耗电；缩放后命中检测需同步调整坐标变换。

**ROI**：中工作量 / 高价值 — 提升核心屏幕体验，SVID 标签 + 星座过滤应优先做。

---

### U3. 历史趋势图 + 导出 + 详情钻取（P1，工作量：大）

**现状**：`HistoryScreen.kt` 只是卡片列表。grep 全代码库无 `CSV`/`export`/`ACTION_SEND`/`FileWriter`（仅 `AndroidManifest.xml:24` 有 FileProvider 声明但历史功能没用）。点快照卡片无反应，无法展开明细（`SatelliteHistorySnapshot.getEntries()` 数据已存却未展示）。无时间筛选/搜索、无单条删除。

更深层：`SatelliteHistoryEntry`（`SatelliteHistory.kt:13-37`）**只存卫星信号**，未保存经纬度/精度/DOP/TTFF，历史无法回溯定位质量轨迹。

**问题/缺口**：用户保存了快照却无法分析趋势、无法导出给他人、无法看某时刻明细。历史功能停留在"存了但没用起来"。

**建议方案**：
1. **趋势图**（中）：用 SignalChart 组件或新图表，画"平均信号/定位卫星数/可见卫星数随时间"曲线。
2. **详情钻取**（小）：点快照卡片展开，调用 `getEntries()` 展示该时刻每颗卫星明细。
3. **CSV 导出**（中）：`HistorySnapshot.toCsv()`，通过已有 FileProvider + `ACTION_SEND` 分享。
4. **单条删除**（小）：滑动或长按菜单删除单条快照。
5. **快照字段扩展**（中）：`SatelliteHistoryEntry` 增加 lat/lon/accuracy/dop/ttff，让历史可回溯定位质量（需数据迁移）。
6. **时间筛选**（小）：按日期范围、按星座筛选。

**涉及文件**：`ui/screens/history/HistoryScreen.kt`、`HistorySnapshotCard.kt`、`domain/model/SatelliteHistory.kt`、`data/local/SatelliteHistoryDataStore.kt`、新增 CSV 导出工具。

**依赖与风险**：字段扩展需数据迁移（旧 JSON 反序列化兼容）；图表性能需注意（100 点 × 多序列）。

**ROI**：大工作量 / 高价值 — 让历史功能真正可用，是用户长期使用的留存点。

---

### U4. 卫星列表筛选/排序/冻结（P2，工作量：中）

**现状**：`SatelliteListScreen.kt` 卫星分三组（usedInFix/visibleOnly/searching）固定渲染，无任何筛选。grep UI 目录无 `FilterChip`/`OutlinedTextField`/search 相关代码。数据实时刷新，无法"冻结"画面观察。

**问题/缺口**：多星座时列表很长，无法按 GPS/北斗/Galileo 过滤、按信号强度排序、按 SVID 搜索。无法暂停数据流仔细看一帧。

**建议方案**：
1. 顶部加 `FilterChip` 行：按星座（GPS/GLONASS/Galileo/BeiDou/QZSS/SBAS）切换显示。
2. 排序下拉：信号强度（强→弱）、仰角（高→低）、SVID。
3. SVID 搜索框：`OutlinedTextField` 输入 SVID 即时过滤。
4. 冻结按钮：暂停 `collectAsState` 的更新（用 `mutableStateOf<Boolean>` 控制）。

**涉及文件**：`ui/screens/satellite/SatelliteListScreen.kt`、可能新增 `ui/components/FilterBar.kt`。

**依赖与风险**：筛选状态需在配置改变（旋转）时保留；冻结期间需明确视觉提示。

**ROI**：中工作量 / 中价值 — 多星座场景下的实用增强。

---

### U5. A-GPS 补全（P2，工作量：中）

**现状**：
- `strings.xml:179` 定义了 `import_file`（导入文件）文案，但 `AGpsManagerScreen.kt:207-274` 的 `ManualActionsCard` **没有"导入"按钮**——文案是死的。
- `AGpsSettingsStore.kt:25` 存了 `DOWNLOAD_URL`，但 UI 上无输入框让用户改源地址（只能用默认 XTRA URL）。
- `AutoUpdateCard`（`:196-201`）只有开关，无滑块/输入改 `updateIntervalHours`。
- 注入历史列表（`AGpsRepositoryImpl.kt:55-56` 的 `_injectionHistory`）只在内存，进程被杀即丢失；无上限/清除。

**问题/缺口**：A-GPS 管理功能半成品，多个文案/字段已定义但无 UI 入口。用户无法自定义源、无法调间隔、无法持久化注入历史。

**建议方案**：
1. 补「导入文件」按钮，调用已有的 `AGpsFileHandler`（30 行）。
2. 下载 URL 输入框（`OutlinedTextField`），写入 `AGpsSettingsStore.DOWNLOAD_URL`。
3. 更新间隔滑块（`Slider`）或下拉，1/6/12/24 小时可选。
4. 注入历史持久化到 DataStore，加上限（如 50 条）和清除按钮。
5. 修复 Snackbar 用法（`:135-160` 改用标准 `SnackbarHostState`）。

**涉及文件**：`ui/screens/agps/AGpsManagerScreen.kt`、`data/local/AGpsSettingsStore.kt`、`domain/repository/AGpsRepositoryImpl.kt:55-56`。

**依赖与风险**：URL 输入需校验格式；历史持久化需定义数据模型。

**ROI**：中工作量 / 中价值 — 补全已有设计，激活多个死代码。

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

### E1. Release minify 开启 + ProGuard 补全（P2，工作量：中）

**现状**：`app/build.gradle.kts:26` `isMinifyEnabled = false`，Release 构建**未开启 R8/混淆/压缩**。`:27-30` 声明了 proguardFiles 但因 minify 关闭而**完全不生效**。`proguard-rules.pro` 缺 kotlinx-serialization、OkHttp、Shizuku、WorkManager 的 keep 规则。

**问题/缺口**：APK 体积未优化、代码无防护、混淆规则未验证。Release 包发出去等于半成品。

**建议方案**：
1. `app/build.gradle.kts:26` 改 `isMinifyEnabled = true`，启用 `isShrinkResources = true`。
2. 补 ProGuard 规则：
   - `-keepclassmembers @kotlinx.serialization.Serializable class **`
   - OkHttp/Shizuku/WorkManager 官方推荐规则
3. 本地 `assembleRelease` 实测，验证混淆后功能正常。
4. 配合 E8 设置 Release 签名配置。

**涉及文件**：`app/build.gradle.kts:26`、`app/proguard-rules.pro`。

**依赖与风险**：混淆可能破坏反射调用（GNSS API、DataStore 序列化），需充分回归测试；无签名配置（见 E8）。

**ROI**：中工作量 / 高价值 — 工程化基础，APK 瘦身 + 代码防护。

---

### E2. CI 增加 lint / 覆盖率（P2，工作量：小）

**现状**：`.github/workflows/ci.yml`（61 行）当前步骤：JDK 21 → Android SDK → `test` → `assembleDebug` → `assembleRelease` → 上传 artifact。README 第 251 行自述「缺 ktlintCheck 步骤」。无 Detekt、无 Android Lint、无 JaCoCo 覆盖率、无 instrumented 测试 job、无 dependabot。

另外：`:25-26` 用 `sed` 删除 `gradle.properties:18` 的机器相关路径（`org.gradle.java.home`）——是 workaround，应改用环境变量。无 Release 签名配置。

**问题/缺口**：CI 不检查代码风格、不收集覆盖率、不跑 Lint，质量门禁缺失。

**建议方案**：
1. CI 增加 `./gradlew ktlintCheck`、`./gradlew lintDebug` 步骤。
2. 可选 Detekt（静态分析）。
3. 接入 JaCoCo / Kover 收集覆盖率，上传 Codecov。
4. 增加 Android emulator job 跑 instrumented 测试（需补 `app/src/androidTest/`，目前为空）。
5. 移除 `gradle.properties:18` 机器路径，改用 `JAVA_HOME` 环境变量，消除 sed workaround。
6. 增加 `dependabot.yml`。

**涉及文件**：`.github/workflows/ci.yml`、`gradle.properties:18`、新建 `.github/dependabot.yml`。

**依赖与风险**：CI 时间变长；emulator job 配置复杂。

**ROI**：小工作量 / 高价值 — 质量门禁，防止回归。

---

### E3. Timber + 崩溃上报（P2，工作量：中）

**现状**：全仓 grep `timber|crashlytics|firebase` **零命中**。4 个文件共 54 处裸用 `android.util.Log`（`AGpsDataSourceImpl.kt` 13、`AGpsDownloader.kt` 12、`AGpsRepositoryImpl.kt` 27、`XtraDataValidator.kt` 2），用硬编码 TAG。**完全无崩溃上报**。

**问题/缺口**：Release 包的线上崩溃不可观测；日志不可控（Release 也会打印）；无统一日志框架。

**建议方案**：
1. 引入 `com.jakewharton.timber:timber`，在 `Application.onCreate` 种树（Debug 用 `DebugTree`，Release 用自定义 release tree 或不种）。
2. 全局替换 54 处 `android.util.Log` 为 `Timber.x()`（自动 TAG）。
3. 评估接入 Firebase Crashlytics（需 Google 服务配置）或自托管 Sentry。
4. 在 Release 构建关闭 debug 日志。

**涉及文件**：`app/build.gradle.kts`（加依赖）、新建 `GpsTestApplication.kt`、4 个 Log 使用文件、`AndroidManifest.xml`（注册 Application）。

**依赖与风险**：Crashlytics 需 Firebase 项目；Timber 需全量替换 Log 调用。

**ROI**：中工作量 / 高价值 — 线上可观测性从零到有。

---

### E4. ViewModel/Repository 测试覆盖（P1，工作量：中）

**现状**：48 个 Kotlin 源文件，9 个测试文件，151 个用例，**全部集中在 `domain/` 和 `data/validator/`**。完全没测试的类（按风险）：
- `SatelliteViewModel.kt`（184 行）— TTFF、信号历史环形缓冲、自动快照逻辑全无测试
- `AGpsViewModel.kt`（146 行）— A-GPS 状态机无测试
- `AGpsRepositoryImpl.kt`（352 行，最大文件）— 多 URL 回退、验证、注入逻辑无测试（bug 高发区）
- `GnssDataSourceImpl.kt`（370 行）— callbackFlow 合并无测试
- `AGpsDownloader.kt`（106 行）— URL 校验（含中文错误信息）无测试
- `AGpsUpdateWorker.kt`（69 行）— 退避策略无测试
- 整个 `app/src/androidTest/` 目录缺失（`build.gradle.kts:97-98` 的 androidTestImplementation 依赖未被使用）

**问题/缺口**：核心业务逻辑（ViewModel 状态机、Repository 回退）零测试，回归风险高。

**建议方案**：
1. 补测试栈依赖：`mockk`、`kotlinx-coroutines-test`、`turbine`（Flow 测试）、`androidx.arch.core:core-testing`、（可选）`robolectric`。
2. 优先测 `SatelliteViewModel`、`AGpsViewModel`（纯状态机，ROI 最高）。
3. 测 `AGpsRepositoryImpl` 多 URL 回退（用 mockk 模拟 downloader/dataSource）。
4. 测 `AGpsDownloader` URL 校验、`AGpsUpdateWorker` 退避。
5. 测 `SkyChartView` 坐标投影（纯函数，易测易出 bug）。
6. 建 `app/src/androidTest/`，写关键 UI 流程的 instrumented 测试。

**涉及文件**：`app/build.gradle.kts`（加测试依赖）、新建多个 `*Test.kt`、新建 `app/src/androidTest/`。

**依赖与风险**：引入 mock 库（项目原本无 mock）；ViewModel 测试需 `InstantTaskExecutorRule`。

**ROI**：中工作量 / 高价值 — 核心逻辑回归保障，是后续重构（E1/E7）的安全网。

---

### E5. 国际化（i18n）（P2，工作量：中）

**现状**：`app/src/main/res/` 下**只有 `values/`**，无 `values-en/`、`values-zh-rCN/`、`values-night/`。`strings.xml`（320 行）默认中文，但 `app_name = "GPS Debug Tool"`（英文）命名不一致。

更严重：UI 代码存在**硬编码中文字符串**绕过资源系统：
- `AGpsManagerScreen.kt:251` `Text("验证下载源")`
- `AGpsManagerScreen.kt:311` `Text("关闭")`
- `SkyChartScreen.kt:61` `title = { Text("天空图") }`
- `AGpsDownloader.kt:40` `"URL为空"`（错误信息硬编码）

**问题/缺口**：海外用户无法使用；硬编码字符串无法随系统语言切换；维护混乱。

**建议方案**：
1. 抽取所有硬编码中文字符串到 `strings.xml`。
2. 建立 `values-en/strings.xml` 英文版（或把当前中文移到 `values-zh-rCN/`，`values/` 改英文默认）。
3. `app/build.gradle.kts` 用 `resourceConfigurations` 限定支持的语言。
4. 统一 `app_name` 命名。

**涉及文件**：4 个硬编码文件、`app/src/main/res/values/strings.xml`、新建 `values-en/strings.xml`、`app/build.gradle.kts`。

**依赖与风险**：翻译工作量；需审查所有 UI 文案。

**ROI**：中工作量 / 中价值 — 扩大用户群，规范字符串管理。

---

### E6. Version Catalog 迁移（P3，工作量：小）

**现状**：所有依赖散写在 `app/build.gradle.kts` 的 `dependencies {}` 中，无 `gradle/libs.versions.toml`。

**问题/缺口**：版本管理分散、跨模块复用难、依赖升级易遗漏。

**建议方案**：迁移到 Gradle Version Catalog（`gradle/libs.versions.toml`），统一管理版本号、库坐标、插件。

**涉及文件**：新建 `gradle/libs.versions.toml`、`build.gradle.kts`（根和 app）、`settings.gradle.kts`。

**依赖与风险**：AGP 8.7.3 原生支持，无兼容问题；需全量替换 dependencies 块。

**ROI**：小工作量 / 低价值 — 长线维护便利，单模块项目短期收益小。

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

### E8. 文档补全（P3，工作量：小）

**现状**：`README.md`（378 行）很完整。但缺 `CONTRIBUTING.md`（README 第 366-374 行只写了 5 步，无 code style/commit convention/PR 模板）、缺 `CHANGELOG.md`（versionCode=1, versionName="1.0"，无版本历史）、缺 `ARCHITECTURE.md` / ADR、缺 `SECURITY.md`、`CODE_OF_CONDUCT.md`。`docs/` 只有 `superpowers/` 子目录（AI 规划文档）。`TODO.md`（本文档已重构）与旧版 README 功能宣称存在偏差。

**问题/缺口**：新贡献者无 onboarding 指南；版本演进无记录；架构决策无沉淀。

**建议方案**：
1. 补 `CONTRIBUTING.md`（PR 流程、commit 规范、代码风格、测试要求）。
2. 补 `CHANGELOG.md`（遵循 Keep a Changelog 格式）。
3. 补 `docs/ARCHITECTURE.md` 或在 README 内深化（ADR 记录关键决策，如手动 DI vs Hilt）。
4. 对齐 README 与本文档的功能宣称。

**涉及文件**：新建 `CONTRIBUTING.md`、`CHANGELOG.md`、可选 `docs/adr/`。

**依赖与风险**：无技术风险；需持续维护。

**ROI**：小工作量 / 中价值 — 项目健康度，开源协作基础。

---

## 五、🗺️ 推荐实施路线图

按依赖关系和投入产出比分四阶段推进。每个阶段目标独立可交付，阶段间有明确的依赖链。

### 阶段一·止血（1–2 天，快速见效）

**目标**：修复死代码 bug，补零成本缺失项，消除误导标记。

| 顺序 | 条目 | 预估工作量 | 关键产出 |
|------|------|-----------|----------|
| 1 | **B1** dumpsys 通路修复 | 中 | `ShizukuHelper.fetchDumpsysLocation` + 解析 + 接入数据源，ClockInfoCard 的 DumpsysDataSection 终于显示 |
| 2 | **G5** TDOP/GDOP 补全 | 小 | `DopCalculator` 补 2 行公式 + DopInfo 字段 + 测试，DOP 全家族 |
| 3 | **B2** 载波相位标记纠错 | 小 | `GnssDataSourceImpl.kt:129` 接入真实采集（API 34+），补 UI 展示 |

**依赖**：无；**风险**：B1 需测试不同 OEM dumpsys 格式。

---

### 阶段二·核心能力（3–7 天，差异化）

**目标**：建立工具的专业调试价值，从"数据展示"升级到"诊断分析"。

| 顺序 | 条目 | 预估工作量 | 关键产出 |
|------|------|-----------|----------|
| 1 | **G6** GnssCapabilities 查询 | 小 | 设备能力卡片，排障自助 |
| 2 | **G7** Location 精度字段 + 闰秒 | 小 | 数据完整性补全 |
| 3 | **G1** 原始伪距 + 本地定位解 | 大 | 杀手级：本地最小二乘解算 vs 系统位置残差对比 |
| 4 | **U2** 天空图增强（SVID 标签 + 星座过滤优先） | 中 | 核心屏幕体验提升 |
| 5 | **B3** NMEA 监听 | 中 | 补全调试基础能力 |

**依赖**：G1 依赖 G6/G7 的数据完整性；U2 独立。
**风险**：G1 卫星位置计算复杂，可分二期（先用简化模型）；NMEA 高频流需节流。

---

### 阶段三·体验完善（5–10 天，用户留存）

**目标**：让已实现的功能真正好用，补全半成品。

| 顺序 | 条目 | 预估工作量 | 关键产出 |
|------|------|-----------|----------|
| 1 | **U1** 设置屏幕 | 中 | 主题切换 + 快照配置，激活多个死代码 |
| 2 | **U3** 历史趋势图 + CSV 导出 | 大 | 历史功能真正可用 |
| 3 | **U4** 卫星列表筛选/排序/冻结 | 中 | 多星座场景实用增强 |
| 4 | **U5** A-GPS 补全 | 中 | 激活 import_file 文案、URL 编辑、间隔调节 |
| 5 | **U2 续** 天空图缩放/动画/指北 | 中 | 完成天空图全功能 |

**依赖**：U3 可选依赖 U1 的快照配置；其余独立。
**风险**：U3 快照字段扩展需数据迁移。

---

### 阶段四·工程化（持续滚动）

**目标**：提升工程质量、可观测性、测试覆盖。可与前三阶段并行，或作为「无功能需求时的填充」。

| 顺序 | 条目 | 预估工作量 | 关键产出 |
|------|------|-----------|----------|
| 1 | **E4** ViewModel/Repository 测试 | 中 | 核心逻辑回归保障（建议尽早，作为其他重构的安全网） |
| 2 | **E2** CI 增加 lint/覆盖率 | 小 | 质量门禁 |
| 3 | **E1** Release minify + ProGuard | 中 | APK 瘦身 + 代码防护 |
| 4 | **E3** Timber + 崩溃上报 | 中 | 线上可观测性 |
| 5 | **E5** i18n | 中 | 国际化 |
| 6 | **E8** 文档补全 | 小 | 协作基础 |
| 7 | **G2** RINEX 导出 | 大 | 专业用户核心诉求（依赖 G1/G3 数据） |
| 8 | **G3** GnssAntennaInfo | 中 | RINEX 头部依赖 |
| 9 | **E6** Version Catalog | 小 | 维护便利 |
| 10 | **E7** Room 迁移 | 大 | 仅在 U3 做厚后 |
| 11 | **G4** 导航电文 | 大 | 长线可选 |
| 12 | **U6** 柱状图/DOP 曲线 | 中 | 视觉化锦上添花 |

**建议**：**E4 测试覆盖应优先于其他工程化项**——它是 E1（混淆回归）、E7（Room 迁移）等重构的安全网。

---

### 路线图可视化

```
阶段一·止血(1-2天)         阶段二·核心(3-7天)        阶段三·体验(5-10天)       阶段四·工程化(持续)
┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐
│ B1 dumpsys修复 ★ │    │ G6 设备能力查询  │    │ U1 设置屏幕 ★   │    │ E4 测试覆盖 ★   │
│ G5 TDOP/GDOP ★   │ ──→│ G7 精度字段+闰秒 │ ──→│ U3 历史+导出 ★  │ ──→│ E2 CI lint      │
│ B2 载波相位纠错  │    │ G1 伪距+定位解 ★ │    │ U4 列表筛选     │    │ E1 minify       │
└──────────────────┘    │ U2 天空图增强 ★  │    │ U5 A-GPS补全    │    │ E3 Timber+崩溃   │
                        │ B3 NMEA监听      │    │ U2续 缩放/动画  │    │ E5 i18n          │
                        └──────────────────┘    └──────────────────┘    │ G2/G3 RINEX      │
                                                                         │ E6-E8, G4, U6    │
                                                                         └──────────────────┘
★ = 阶段内最高优先级
```

---

## 六、✅ 已实现功能清单（里程碑记录）

> 旧版 TODO.md 标记经代码核实，以下 11 项均为**完整实现**（采集 + 展示链路齐全），修正了旧版的 6 处标记错误。

### 高价值功能

| 功能 | 旧标记 | 核实结论 | 关键证据 |
|------|--------|----------|----------|
| 卫星天空图（Sky View） | `[*]` | ✅ 完整实现 | `SkyChartView.kt:64-209` 极坐标投影 + `SkyChartScreen.kt:139-146` |
| 多路径指示 | `[*]` | ✅ 完整实现 | `GnssDataSourceImpl.kt:108` 采集 + `SatelliteDetailSheet.kt:106-112` 展示 |
| 自动增益控制（AGC） | `[*]` | ✅ 完整实现 | `GnssDataSourceImpl.kt:100-107` 采集 + `SatelliteDetailSheet.kt:97-100` 展示 |
| HDOP/VDOP/PDOP | `[ ]` ❌ | ✅ 完整实现 | `DopCalculator.kt:18-71` 算法 + `DopCard.kt:60-71` 展示（含单元测试） |
| TTFF（首次定位时间） | `[ ]` ❌ | ✅ 完整实现 | `SatelliteViewModel.kt:44-45,92-102` 状态机 + `TtffCard.kt:25-100` |
| 信号历史曲线 | `[ ]` ❌ | ✅ 完整实现 | `SignalChart.kt:132-176` 折线图 + `SatelliteDetailSheet.kt:150` 接入 |

### 专业/调试功能

| 功能 | 旧标记 | 核实结论 | 关键证据 |
|------|--------|----------|----------|
| 伪距变化率 | `[x]` | ✅ 完整实现 | `GnssDataSourceImpl.kt:126` + `SatelliteDetailSheet.kt:125-128` |
| 卫星时间不确定度 | `[x]` | ✅ 完整实现 | `GnssDataSourceImpl.kt:124` + `SatelliteDetailSheet.kt:137-140` |
| 基带 C/N0 | `[*]` | ✅ 完整实现 | `GnssDataSourceImpl.kt:223-228`（API 30+）+ `ClockInfoCard.kt:102-121` 全局均值 |
| 时钟偏差/漂移 | `[*]` | ✅ 完整实现 | `GnssDataSourceImpl.kt:134-172` + `GnssClockData.kt:29-43` 派生 + `ClockInfoCard.kt:73-100` |
| 星座健康状态汇总 | `[*]` | ✅ 完整实现 | `ConstellationHealthSummaryCard.kt:35-101` 进度条 + 百分比 |

### 标记错误纠正记录

| 功能 | 旧标记 | 实际状态 | 错误类型 |
|------|--------|----------|----------|
| 信号历史曲线 | `[ ]` | ✅ 已实现 | 标记偏低 |
| HDOP/VDOP/PDOP | `[ ]` | ✅ 已实现 | 标记偏低 |
| TTFF | `[ ]` | ✅ 已实现 | 标记偏低 |
| **载波相位完整周期** | **`[x]`** | **✅ 已修复** | **原反方向错误，已于 2026-06-14 修复（见 B2）** |

> **载波相位完整周期**旧版标记 `[x]` 已完成，但 `GnssDataSourceImpl.kt:129` 实际硬编码 `fullCarrierPhaseCycleCount = null`，从未采集也未展示。**已于 2026-06-14 修复（B2）**：接入真实采集 + UI 展示。

---

## 附录：探索方法说明

本文档基于对以下维度的只读代码探索：
- **UI 层**：5 个屏幕 + 16 个组件的完整功能审查
- **领域/数据层**：所有数据模型、数据源、仓库、工具类的实现审查
- **工程化**：构建配置、测试覆盖（151 用例分布）、CI 流程、Manifest、ProGuard、i18n、文档
- **TODO 标记核实**：旧版 15 项逐一对照代码实现状态

所有 `file:line` 引用均来自 master 分支 `d22baec`。如代码后续变更，行号可能偏移，但文件名与逻辑位置应保持参考价值。
