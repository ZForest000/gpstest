# GPS Debug Tool

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.10.01-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![Platform](https://img.shields.io/badge/Android-7.0%2B%20(API%2024)-3DDC84.svg)](https://www.android.com)
[![Target](https://img.shields.io/badge/Target-Android%2015%20(API%2035)-3DDC84.svg)](https://www.android.com)
[![CI](https://img.shields.io/badge/CI-GitHub%20Actions-2088FF.svg)](.github/workflows/ci.yml)

一个功能强大的 Android GPS 调试工具，用于实时监测和分析 GNSS（全球导航卫星系统）数据。支持多星座卫星追踪、信号质量分析、精度因子评估、A-GPS 数据管理和历史记录功能。

面向 Android 开发者与 GNSS 爱好者，可在单机上完成卫星可见性排查、信号质量评估与 A-GPS 辅助数据注入。

## 目录

- [功能特性](#功能特性)
- [导航](#导航)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [架构与数据流](#架构与数据流)
- [开发命令](#开发命令)
- [测试](#测试)
- [CI/CD](#cicd)
- [权限要求](#权限要求)
- [安装说明](#安装说明)
- [使用指南](#使用指南)
- [技术亮点](#技术亮点)
- [注意事项](#注意事项)
- [开源协议](#开源协议)
- [贡献指南](#贡献指南)

## 功能特性

### 卫星监测

- **实时卫星追踪**：显示当前可见的所有 GNSS 卫星，按「已定位」「可见」「搜索中」分组展示
- **多星座支持**：GPS、GLONASS、Galileo、BeiDou、QZSS、SBAS
- **星座健康摘要**：以进度条形式展示各星座卫星可用比例
- **信号强度图表**：60 秒滚动历史信号强度（C/N0）可视化
- **卫星详情**：底部弹窗查看每颗卫星的方位角、高度角、信噪比等详细信息
- **星历/历书状态**：指示卫星是否拥有有效星历和历书数据
- **高级测量数据**：载波频率、载波周期、多普勒频移、AGC（自动增益控制）、多路径指示、基带信噪比（API 30+）

### 卫星天空图

- **极坐标天空图**：以雷达图形式可视化卫星方位角和高度角
- **交互选择**：点击天空图上的卫星标记查看详细数据
- **星座颜色标识**：不同星座使用不同颜色区分

### 精度因子 (DOP)

- **PDOP**：位置精度衰减因子，综合评估定位精度（4×4 Gauss-Jordan 矩阵求逆）
- **HDOP**：水平精度衰减因子
- **VDOP**：垂直精度衰减因子
- **质量等级**：根据 DOP 值自动评估定位质量（优秀/良好/中等/较差/差）

### 首次定位时间 (TTFF)

- **定位计时**：追踪从启动到首次获得有效定位的时间
- **质量分级**：彩色标识定位速度（< 10s 优秀 / 10-30s 良好 / 30-60s 中等 / > 60s 较差）
- **重置功能**：支持手动重置计时

### 时钟信息

- **时钟偏差**：接收机时钟与 GPS 时的偏差
- **时钟漂移**：时钟频率漂移率
- **不确定度**：偏差和漂移的测量不确定度

### 定位信息

- **实时位置数据**：纬度、经度、海拔、精度
- **速度信息**：当前移动速度和方向
- **定位精度**：水平精度和垂直精度
- **气压辅助**：使用气压计提高海拔测量精度

### Shizuku 支持

- **状态检测**：检测 Shizuku 是否运行及权限状态
- **模式识别**：识别 ROOT 或 ADB 模式
- **dumpsys 数据**：通过 Shizuku 获取 GNSS dumpsys 系统级测量数据（基带 C/N0、测量计数、参与定位的星座列表）
- **非侵入式设计**：Shizuku 不可用时不影响基本功能

### A-GPS 管理

- **XTRA 数据下载**：自动下载 Qualcomm XTRA 辅助定位数据（OkHttp + 多 URL 回退）
- **数据注入**：下载后自动验证并注入到定位引擎
- **定时更新**：支持设置自动更新间隔（WorkManager 后台任务，指数退避重试）
- **数据验证**：验证下载数据的完整性和格式
- **状态监控**：显示 A-GPS 数据的有效期和状态
- **文件导入**：支持从文件导入 A-GPS 数据

### 历史记录

- **卫星历史快照**：自动定期保存卫星状态快照（每 60 秒）
- **数据持久化**：使用 DataStore Preferences + kotlinx-serialization JSON
- **历史浏览**：查看历史卫星分布和信号强度

### 帮助系统

- **内置帮助页面**：涵盖所有功能模块的详细说明
- **TTFF 标准**：首次定位时间参考标准
- **DOP 指南**：精度因子解读
- **C/N0 参考**：信号强度范围参考
- **星座信息**：各 GNSS 星座简介
- **高级测量说明**：原始测量数据字段解释

## 导航

应用采用 **导航抽屉（Navigation Drawer）**，包含五个页面：

- **卫星列表** — 实时卫星数据、定位信息、TTFF、DOP、时钟、星座健康概览
- **天空图** — 卫星位置极坐标可视化
- **A-GPS 管理** — A-GPS 下载、注入、定时更新配置
- **历史记录** — 查看已保存的卫星状态快照
- **帮助** — 功能说明和参考指南

## 技术栈

| 分类 | 技术 | 版本 |
|------|------|------|
| **语言** | Kotlin | 2.1.0 |
| **UI** | Jetpack Compose + Material 3 | BOM 2024.10.01 |
| **构建** | Android Gradle Plugin | 8.7.3 |
| **构建** | Gradle | 8.9 |
| **JDK（源码兼容）** | JDK | 17 |
| **JDK（构建运行时）** | JDK | 21 |
| **最低 SDK** | Android | API 24 (Android 7.0) |
| **目标 SDK** | Android | API 35 (Android 15) |
| **架构** | Clean MVVM | — |
| **异步** | Kotlin Coroutines + Flow | 1.9.0 |
| **序列化** | kotlinx-serialization-json | 1.7.3 |
| **DI** | 手动注入（Factory 模式） | — |
| **网络** | OkHttp | 4.12.0 |
| **存储** | DataStore Preferences | 1.1.1 |
| **后台任务** | WorkManager | 2.10.0 |
| **权限增强** | Shizuku API | 13.1.5 |
| **代码风格** | Kotlin official | — |
| **代码检查** | ktlint（android 模式） | 1.5.0 |

## 项目结构

```
app/src/main/java/com/example/gpstest/
├── MainActivity.kt                # 唯一 Activity，Compose 入口，DI 装配，权限，导航
├── viewmodel/                     # ViewModel
│   ├── SatelliteViewModel.kt      # GNSS 数据收集、信号历史、TTFF、自动快照、DOP
│   └── AGpsViewModel.kt           # A-GPS 下载/注入生命周期，WorkManager 调度
├── domain/                        # 领域层
│   ├── model/                     # GnssData, GnssSatellite, Constellation, LocationInfo,
│   │                              #   GnssClockData, DopInfo, AGpsStatus, AGpsSettings,
│   │                              #   SatelliteHistory, SatelliteGroup
│   ├── repository/                # 仓库接口 + Impl 实现
│   │   ├── GnssRepository.kt / GnssRepositoryImpl.kt
│   │   ├── AGpsRepository.kt / AGpsRepositoryImpl.kt
│   │   └── SatelliteHistoryRepository.kt / SatelliteHistoryRepositoryImpl.kt
│   └── util/                      # DopCalculator（4×4 Gauss-Jordan 矩阵求逆）
├── data/                          # 数据层
│   ├── source/                    # GnssDataSource, AGpsDataSource, ShizukuHelper
│   │                              #   (Interface + Impl)
│   ├── local/                     # SatelliteHistoryDataStore, AGpsSettingsStore,
│   │                              #   AGpsFileHandler (Interface + Impl)
│   └── validator/                 # XtraDataValidator
├── service/                       # AGpsUpdateWorker (WorkManager CoroutineWorker)
└── ui/                            # 表现层
    ├── screens/                   # 页面（各页面独立子包）
    │   ├── satellite/             # SatelliteListScreen
    │   ├── skychart/              # SkyChartScreen + SkyChartView + SkyChartLegend
    │   ├── agps/                  # AGpsManagerScreen
    │   ├── history/               # HistoryScreen
    │   └── help/                  # HelpScreen
    ├── components/                # 可复用组件（SatelliteCard, SignalChart,
    │                              #   LocationCard, DopCard, ClockInfoCard,
    │                              #   TtffCard, ConstellationHealthSummaryCard 等 15 个）
    └── theme/                     # Color, Type, Theme
```

## 架构与数据流

### Clean MVVM + 单向数据流

```
data layer ──→ domain layer ──→ presentation layer
(sources,       (repo interfaces,   (ViewModels +
 persistence,     models, utils)      Compose UI)
 validators)
```

**关键数据管线**：

- **GNSS**：Android 平台回调 (GnssStatus + GnssMeasurements + Location + barometer) → `callbackFlow` merge → `Flow<GnssData>` → `GnssRepositoryImpl` → `SatelliteViewModel` → `StateFlow<SatelliteUiState>` → `collectAsState()` → UI
- **A-GPS**：用户操作 / WorkManager 触发 → `AGpsViewModel` → `AGpsRepositoryImpl` 协调下载 (OkHttp) → 验证 (`XtraDataValidator`) → 注入 (`LocationManager.sendExtraCommand`)。多 URL 回退：用户 URL → 3 个 Qualcomm izatcloud 默认地址。
- **历史**：`SatelliteViewModel.maybeSaveSnapshot()` (每 60s) → `SatelliteHistoryRepositoryImpl` → `SatelliteHistoryDataStore` (DataStore + JSON) → `StateFlow` → `HistoryScreen`

**状态管理**：`MutableStateFlow` 在 ViewModel 中，暴露为只读 `StateFlow`。UI sealed states：
- `SatelliteUiState`：Loading → PermissionRequired → Success(...) → Error(message)
- `AGpsUiState`：Idle → Downloading → Injecting → Success(message) → Error(message)

**错误处理**：全链路 `Result<T>`。ViewModel 层 `try/catch` + sealed error states。A-GPS 多 URL 回退。WorkManager 指数退避重试。

## 开发命令

```bash
# 构建
./gradlew assembleDebug          # Debug APK
./gradlew assembleRelease        # Release APK（无签名配置，需自行设置）

# 测试
./gradlew test                   # 运行全部单元测试
./gradlew testDebugUnitTest      # 显式 Debug 单元测试

# 代码检查
./gradlew ktlintCheck            # ktlint 代码风格检查

# 安装
./gradlew installDebug           # 安装 Debug APK 到设备
```

## 测试

**框架**：JUnit 4.13.2。无 mocking 库，无 Robolectric，无 Truth。

**范围**：9 个测试文件，共 151 个单元测试，覆盖领域层与数据校验层：

| 测试文件 | 用例数 | 说明 |
|----------|--------|------|
| `domain/model/GnssSatelliteTest` | 36 | 卫星属性、星座判定、显示名称 |
| `domain/model/SatelliteHistoryTest` | 14 | 历史快照数据模型 |
| `domain/model/ConstellationTest` | 19 | 星座类型映射与短名 |
| `domain/model/SatelliteDisplayNameTest` | 12 | 卫星显示名称生成 |
| `domain/model/GnssClockDataTest` | 11 | 时钟偏差与漂移计算 |
| `domain/model/GnssDataTest` | 11 | GNSS 聚合数据模型 |
| `domain/model/DopInfoTest` | 10 | DOP 质量等级判定 |
| `domain/util/DopCalculatorTest` | 10 | DOP 矩阵运算（PDOP/HDOP/VDOP） |
| `data/validator/XtraDataValidatorTest` | 28 | XTRA 数据完整性与格式校验 |

**测试命名**：反引号描述性名称，如 `` `quality is EXCELLENT when pdop less than 1` ``。直接实例化 data class，断言计算属性，无测试基类，无 `@Before`/`@After`。

**未测试**：ViewModels、DataSources、Repositories（除领域逻辑外）、UI/屏幕、Services、`AGpsUpdateWorker`、`ShizukuHelper`、`AGpsDownloader`。

## CI/CD

GitHub Actions（`.github/workflows/ci.yml`）：push / PR 到 `master` 分支时触发：
- JDK 21（Temurin）+ Android SDK 构建环境
- 自动移除本地 `gradle.properties` 中的 `org.gradle.java.home` 覆盖（避免路径在 CI 上失效）
- `./gradlew test` 运行所有单元测试
- `assembleDebug` + `assembleRelease` 构建
- 上传产物：`debug-apk`、`release-apk`、`test-results`（`app/build/reports/tests/`）

**下载构建产物**：在对应 commit 的 GitHub Actions 运行页面，滚动至页面底部「Artifacts」区域，点击 `debug-apk` / `release-apk` / `test-results` 即可下载。注意：产物在运行记录保留一段时间后会过期。

> 提示：CI 仅运行单元测试与构建，**不包含** `ktlintCheck` 步骤；代码风格检查需在本地手动执行 `./gradlew ktlintCheck`。

## 权限要求

### 运行时权限

- `ACCESS_FINE_LOCATION` — 精确定位权限
- `ACCESS_COARSE_LOCATION` — 粗略定位权限
- `ACCESS_LOCATION_EXTRA_COMMANDS` — 发送定位相关命令（注入 A-GPS 数据）
- `INTERNET` — 网络访问（下载 A-GPS 数据）

### Shizuku 组件声明

`AndroidManifest.xml` 中注册了 `rikka.shizuku.ShizukuProvider`（authorities 为 `${applicationId}.shizuku`），并由系统权限 `android.permission.INTERACT_ACROSS_USERS_FULL` 守护。这是获取系统级 GNSS dumpsys 数据所必需的组件声明；Shizuku 未安装/未授权时，该功能自动降级，不影响基础定位与卫星显示。

## 安装说明

### 环境要求

- Android Studio Ladybug 或更高版本
- JDK 21（构建运行时）；源码兼容 JDK 17（`sourceCompatibility`/`targetCompatibility = 17`）
- Android SDK Platform 35、Build-Tools 对应版本

> **本地构建注意**：仓库的 `gradle.properties` 包含一行机器相关的本地路径 `org.gradle.java.home=C:/Program Files/Java/jdk-21`。若你的 JDK 安装路径不同，请按本机情况修改或删除该行（CI 会自动移除它，故不影响云端构建）。

### 构建步骤

```bash
git clone <repository-url>
cd gpstest
./gradlew assembleDebug
```

或使用 Android Studio 打开项目，同步 Gradle 后点击 "Run"。

## 使用指南

### 首次使用

1. 安装应用后打开，授予定位权限
2. 应用会自动开始搜索卫星
3. 在户外或靠近窗户的位置可获得更好的信号
4. 通过左侧抽屉菜单切换不同功能页面

### 卫星列表

- 实时查看所有卫星列表，按「已定位」「可见」「搜索中」分组
- 查看定位信息（经纬度、海拔、速度、精度）
- 监控 TTFF（首次定位时间）和 DOP 精度因子
- 查看时钟偏差和星座健康摘要
- 点击卫星查看详情和信号历史图表

### 天空图

- 以极坐标雷达图形式查看卫星在天空中的位置
- 中心代表天顶，外圈代表地平线
- 不同星座以不同颜色标识
- 点击卫星标记查看详细信息

### A-GPS 数据更新

1. 进入「A-GPS 管理」页面
2. 点击「立即更新」手动下载最新数据
3. 或开启「自动更新」设置更新间隔

### 查看历史记录

1. 进入「历史记录」页面
2. 查看已保存的卫星状态快照
3. 点击快照查看详细信息

## 技术亮点

### 响应式 UI

- Kotlin Flow 实现数据流响应式更新
- Compose `remember` 和 `derivedStateOf` 优化性能
- 自动处理配置变更（屏幕旋转等）

### 模块化架构

- 三层分离（Data / Domain / UI）
- 依赖反转原则（Interface + Impl），便于测试和维护
- Repository 模式统一管理数据来源

### 后台任务

- WorkManager 实现可靠的定时 A-GPS 更新
- 设备重启后继续任务
- 指数退避重试策略

### 数据验证

- XTRA 数据完整性校验
- 文件大小和格式验证
- 下载异常处理

### Shizuku 增强

- 通过 Shizuku 获取系统级 GNSS dumpsys 数据
- 支持 ROOT 和 ADB 两种运行模式
- 非侵入式：Shizuku 不可用时不影响基本功能

## 注意事项

1. **GPS 信号**：室内或遮挡严重的地方可能无法获取卫星信号
2. **A-GPS 数据**：需要网络连接下载辅助定位数据
3. **电池消耗**：持续 GPS 定位会增加电量消耗
4. **Android 版本**：部分高级 GNSS 功能需要 Android 7.0+ 和硬件支持
5. **Shizuku**：高级系统数据功能需要安装 Shizuku 应用并授权

## 开源协议

本项目采用 MIT 协议开源 — 详见 [LICENSE](LICENSE) 文件

## 贡献指南

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

***

**免责声明**：本应用仅供开发和调试使用，不保证定位数据的绝对准确性。
