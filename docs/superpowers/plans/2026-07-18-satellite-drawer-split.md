# 卫星监控抽屉拆分实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将卫星监控功能拆成以总览为根的抽屉页面，降低卫星列表的首屏信息密度。

**架构：** `SatelliteViewModel` 继续是唯一的实时状态源。页面只负责读取并展示各自的状态切片；共享壳层统一处理 Loading、权限和错误。`MainActivity` 持有所有新路由及按组排列的抽屉条目。

**技术栈：** Kotlin、Jetpack Compose Material 3、Navigation Compose、JUnit 4。

---

## 文件结构

- 创建：`app/src/main/java/com/example/gpstest/ui/components/FixStatusSummary.kt`，总览的紧凑定位状态摘要。
- 创建：`app/src/test/java/com/example/gpstest/ui/components/FixStatusSummaryStateTest.kt`，总览摘要状态映射的纯 JVM 单测。
- 创建：`app/src/main/java/com/example/gpstest/ui/screens/satellite/SatelliteScreenScaffold.kt`，公共顶栏和 Loading/权限/错误壳层。
- 创建：`app/src/main/java/com/example/gpstest/ui/screens/overview/SatelliteOverviewScreen.kt`，总览页。
- 创建：`app/src/main/java/com/example/gpstest/ui/screens/positioning/PositioningScreen.kt`，定位与精度页。
- 创建：`app/src/main/java/com/example/gpstest/ui/screens/diagnostics/ReceiverDiagnosticsScreen.kt`，接收机诊断页。
- 修改：`app/src/main/java/com/example/gpstest/ui/screens/satellite/SatelliteListScreen.kt`，收缩为纯列表页。
- 修改：`app/src/main/java/com/example/gpstest/MainActivity.kt`，新增路由、总览根导航与抽屉分组。
- 修改：`app/src/main/res/values/strings.xml`、`app/src/main/res/values-en/strings.xml`，补齐标题、分组与摘要文本。

### 任务 1：总览摘要组件与文案

**文件：**
- 创建：`app/src/main/java/com/example/gpstest/ui/components/FixStatusSummary.kt`
- 修改：`app/src/main/res/values/strings.xml`
- 修改：`app/src/main/res/values-en/strings.xml`

- [ ] 创建 `FixStatusSummary(location, dopInfo, ttffState)`，用一个紧凑 Surface 呈现定位状态、TTFF 和 PDOP；空位置、DOP 或未完成 TTFF 使用资源文本而不显示伪数值。
- [ ] 先编写 `FixStatusSummaryStateTest`，断言无位置/无 DOP 时输出“无定位、无 TTFF、无 PDOP”，以及完成定位时保留 TTFF 秒数和 PDOP。
- [ ] 运行 `./gradlew testDebugUnitTest --tests com.example.gpstest.ui.components.FixStatusSummaryStateTest`，预期：生产映射尚不存在导致编译失败。
- [ ] 添加 `FixStatusSummaryState` 与 `buildFixStatusSummaryState`，再运行同一测试，预期：两条断言通过。
- [ ] 为总览、定位、接收机诊断、实时监控、数据与工具、定位状态、TTFF 与 PDOP 添加中英文资源。
- [ ] 运行 `./gradlew ktlintCheck`，预期：新增 Kotlin 文件符合格式。

### 任务 2：共享卫星页面壳层

**文件：**
- 创建：`app/src/main/java/com/example/gpstest/ui/screens/satellite/SatelliteScreenScaffold.kt`

- [ ] 创建 `SatelliteScreenScaffold(title, onOpenDrawer, actions, content)`，统一带菜单按钮的 `TopAppBar` 和 `Scaffold`。
- [ ] 创建 `SatelliteStateContent(uiState, permissionState, onRequestPermission, onOpenAppSettings, onRetry, content)`，仅在 `SatelliteUiState.Success` 时调用内容 lambda；其他状态复用已有 Loading、`PermissionRequiredContent`、`ErrorContent`。
- [ ] 运行 `./gradlew compileDebugKotlin`，预期：共享 Compose API 可被后续页面调用。

### 任务 3：实现总览、定位与接收机诊断页面

**文件：**
- 创建：`app/src/main/java/com/example/gpstest/ui/screens/overview/SatelliteOverviewScreen.kt`
- 创建：`app/src/main/java/com/example/gpstest/ui/screens/positioning/PositioningScreen.kt`
- 创建：`app/src/main/java/com/example/gpstest/ui/screens/diagnostics/ReceiverDiagnosticsScreen.kt`

- [ ] 总览按顺序展示 `StatBar`、`FixStatusSummary`、`ConstellationStatCard`、`SignalBarChart` 和 `ConstellationHealthSummaryCard`。
- [ ] 定位页按顺序展示 `TtffCard`、`LocationCard`、`DopCard`、`DopTrendChart` 和 `LocalPositionCard`；TTFF 重置仍调用 `SatelliteViewModel.resetTtff()`。
- [ ] 接收机诊断页展示 `ClockInfoCard`、`GnssCapabilitiesCard` 和非空时的 `AntennaInfoCard`；顶部分享按钮调用既有 `RinexExportHelper.share` 并沿用失败 Toast。
- [ ] 运行 `./gradlew compileDebugKotlin`，预期：三页均从同一 ViewModel 读取状态。

### 任务 4：收缩卫星列表

**文件：**
- 修改：`app/src/main/java/com/example/gpstest/ui/screens/satellite/SatelliteListScreen.kt`

- [ ] 删除 TTFF、位置、DOP、信号图、趋势图、时钟、健康、能力、天线和本地解算状态的收集与渲染。
- [ ] 保留 `SatelliteFilterBar`、三组 `SatelliteCard`、`SatelliteDetailSheet` 和列表冻结快照；将冻结资源文案限定为“冻结列表”。
- [ ] 将顶栏标题改为卫星列表，移除 RINEX 分享按钮。
- [ ] 运行 `./gradlew compileDebugKotlin`，预期：列表页只依赖列表与详情所需状态。

### 任务 5：接线抽屉与验证

**文件：**
- 修改：`app/src/main/java/com/example/gpstest/MainActivity.kt`
- 修改：`app/src/main/res/values/strings.xml`
- 修改：`app/src/main/res/values-en/strings.xml`

- [ ] 新增 `Screen.Overview`、`Screen.Positioning`、`Screen.ReceiverDiagnostics`，并将 `Overview` 设为 `startDestination` 与 `navigateAndCloseDrawer` 的 `popUpTo` 根。
- [ ] 在抽屉中按“实时监控”“数据与工具”分组，顺序为总览、卫星列表、天空图、定位与精度、接收机诊断、A-GPS、历史、NMEA、导航电文、帮助、设置。
- [ ] 新增三个 `composable` 目的地，均传入共享 `satelliteViewModel`、权限回调与抽屉回调。
- [ ] 运行 `./gradlew test`、`./gradlew ktlintCheck`、`./gradlew assembleDebug`，预期：全部通过并生成 Debug APK。
- [ ] 手动验证：启动落在总览；抽屉首项高亮；列表首屏无诊断卡；RINEX 可在接收机诊断页触发；权限缺失和 GNSS 错误可在四个卫星页面正确显示。
