# 仓库指南

## 项目概述

Android GPS 调试工具，用于实时 GNSS 卫星监控和 A-GPS 管理。支持 GPS、GLONASS、Galileo、北斗、QZSS 和 SBAS 星座。功能包括天空图、DOP 计算、TTFF 追踪、A-GPS XTRA 下载/注入、卫星历史快照，以及可选的 Shizuku/root 诊断。

**语言**: Kotlin 2.1.0 | **UI**: Jetpack Compose (BOM 2024.10.01) + Material 3 | **Min SDK**: 24 | **Target SDK**: 35

## 架构与数据流

**模式**: Clean MVVM，单向数据流。单 Activity，无 Fragment。

```
数据层 ──→ 领域层 ──→ 表现层
(数据源、     (仓库接口、    (ViewModel +
 持久化、      模型、工具)     Compose UI)
 验证器)
```

**核心数据管道**:

- **GNSS**: Android 平台回调 (GnssStatus + GnssMeasurements + Location + 气压计) → `GnssDataSourceImpl` 中的 `callbackFlow` 合并 → `Flow<GnssData>` → `GnssRepositoryImpl` → `SatelliteViewModel` → `StateFlow<SatelliteUiState>` → `collectAsState()` → UI
- **A-GPS**: 用户操作 / WorkManager 触发 → `AGpsViewModel` → `AGpsRepositoryImpl` 编排下载 (OkHttp) → 验证 (`XtraDataValidator`) → 注入 (`LocationManager.sendExtraCommand`)。多 URL 回退：用户 URL → 3 个 Qualcomm izatcloud 默认地址。
- **历史**: `SatelliteViewModel.maybeSaveSnapshot()` (每 60 秒) → `SatelliteHistoryRepositoryImpl` → `SatelliteHistoryDataStore` (DataStore Preferences + kotlinx-serialization JSON) → `StateFlow` → `HistoryScreen`

**状态管理**: ViewModel 中使用 `MutableStateFlow`，对外暴露为只读 `StateFlow`。UI 使用密封状态：
- `SatelliteUiState`: Loading → PermissionRequired → Success(...) → Error(message)
- `AGpsUiState`: Idle → Downloading → Injecting → Success(message) → Error(message)

**依赖注入**: 通过 `ViewModelProvider.Factory` 手动 DI。无 Hilt/Dagger/Koin。`GpsTestApplication` 持有应用级 `AppDependencies`，集中惰性创建依赖；`MainActivity` 的 Factory 和 `AGpsUpdateWorker` 都从该组合根取得既有依赖。

**错误处理**: 仓库和数据源中全面使用 `Result<T>`。ViewModel 协程作用域内 `try/catch` 配合密封错误状态。A-GPS 下载支持多 URL 回退。WorkManager 指数退避重试。

## 导航

单 Activity (`MainActivity`) + Navigation Compose。三个底部标签页：
- **SatelliteListScreen** — 实时卫星列表，按状态分组（定位中 / 可见 / 搜索中）
- **AGpsManagerScreen** — A-GPS 下载、导入、注入、自动更新配置
- **HistoryScreen** — 浏览保存的卫星数据快照

## 关键目录

```
app/src/main/java/com/example/gpstest/
├── MainActivity.kt              # 唯一 Activity，Compose 入口，DI 连线，权限，导航
├── viewmodel/                   # SatelliteViewModel, AGpsViewModel
├── domain/
│   ├── model/                   # GnssData, GnssSatellite, Constellation, LocationInfo, GnssClockData, DopInfo, AGpsStatus, AGpsSettings, SatelliteHistory, SatelliteGroup
│   ├── repository/              # 接口 + 实现配对：GnssRepository, AGpsRepository, SatelliteHistoryRepository
│   └── util/                    # DopCalculator (4×4 Gauss-Jordan 矩阵求逆)
├── data/
│   ├── source/                  # GnssDataSource, AGpsDataSource, AGpsDownloader, ShizukuHelper (接口 + 实现配对)
│   ├── local/                   # SatelliteHistoryDataStore, AGpsSettingsStore, AGpsFileHandler/Impl
│   └── validator/               # XtraDataValidator
├── service/                     # AGpsUpdateWorker (WorkManager CoroutineWorker)
└── ui/
    ├── screens/                 # SatelliteListScreen, SkyChartScreen, AGpsManagerScreen, HistoryScreen, HelpScreen
    ├── components/              # 15 个可组合组件 (SatelliteCard, SignalChart, LocationCard, DopCard 等)
    └── theme/                   # Color.kt, Type.kt, Theme.kt
```

## 开发命令

```bash
# 构建
./gradlew assembleDebug          # Debug APK
./gradlew assembleRelease        # Release APK (无签名配置 — 需手动设置)

# 测试
./gradlew test                   # 仅单元测试 (50 个测试，仅领域层)
./gradlew testDebugUnitTest      # 显式 debug 单元测试

# 代码检查
./gradlew ktlintCheck            # 运行 ktlint (1.5.0, android 模式)

# 安装
./gradlew installDebug           # 安装 debug APK 到设备

# 清理
./gradlew clean                  # 清理构建产物
```

**JDK 路径**: `C:\Program Files\Java\jdk-21` — 构建前需设置 `JAVA_HOME`：
```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21"
```

**Android SDK 路径**: `D:\android_sdk` — ADB 等工具路径：
```bash
export PATH="$PATH:/d/android_sdk/platform-tools"
```

**CI** (`.github/workflows/ci.yml`): push/PR 到 `master` 时触发 — JDK 21，`./gradlew test`、`assembleDebug`、`assembleRelease`，上传 APK 和测试结果。

## 关键技术细节

- **Compose + Material3** 用于所有 UI；无 XML 布局
- **信号历史追踪**: 每颗卫星维护 60 秒的 `SignalHistory` 环形缓冲区
- **自动快照**: WorkManager 驱动的周期性卫星状态保存
- **A-GPS 流程**: 下载 → 验证 → 通过 `LocationManager.sendExtraCommand("delete_aiding_data" / "force_time_injection")` 注入
- **持久化**: DataStore (preferences)，Kotlin Serialization (JSON 快照)
- **权限**: `ACCESS_FINE_LOCATION`、`ACCESS_COARSE_LOCATION`、`ACCESS_LOCATION_EXTRA_COMMANDS`、`INTERNET`
- **Java 17** 目标；compileSdk/targetSdk 35；minSdk 24

## 代码规范与常用模式

### 命名
- **文件/类**: PascalCase。接口无后缀 (`GnssDataSource`)，实现类加 `Impl` 后缀 (`GnssDataSourceImpl`)
- **方法**: camelCase，动词开头 (`startListening()`, `downloadAndInject()`, `maybeSaveSnapshot()`)
- **变量**: camelCase。私有后备字段加 `_` 前缀 (`_uiState`, `_ttffState`)。常量使用 `SCREAMING_SNAKE_CASE`
- **Composable 函数**: PascalCase (`SatelliteCard`, `DopCard`)。状态通过 `remember { mutableStateOf() }`
- **密封接口**: `SatelliteUiState`、`AGpsUiState`、`TtffState` 使用嵌套 data class/object 变体

### 架构模式
- **接口 + 实现** 贯穿全项目：所有 DataSource、Repository、Downloader、FileHandler 均遵循此模式
- **callbackFlow** 将 Android 平台回调转换为 Kotlin Flow
- **viewModelScope.launch** 用于所有 ViewModel 异步工作 (在 `onCleared()` 中自动取消)
- **Dispatchers.IO** 用于文件 I/O 和网络操作
- **60 秒环形缓冲区** 每颗卫星的信号历史 (`Map<String, List<SignalReading>>`)

### 通用约定
- Repository 模式：接口在 `domain/repository/`，实现在同包中
- Compose 组件无状态；状态提升至屏幕级可组合组件或 ViewModel
- 所有异步工作使用协程；无 RxJava
- 颜色和主题集中在 `ui/theme/`

### 语言
- 注释和文档：中英混合。领域知识、架构决策和面向用户的文档使用中文
- 面向用户的字符串：通过 Android 字符串资源使用中文

## 重要文件

| 文件 | 用途 |
|------|------|
| `app/src/main/java/com/example/gpstest/MainActivity.kt` | 入口点，导航宿主，DI 连线，权限处理 |
| `app/src/main/java/com/example/gpstest/viewmodel/SatelliteViewModel.kt` | GNSS 数据采集，信号历史，TTFF，自动快照，DOP |
| `app/src/main/java/com/example/gpstest/viewmodel/AGpsViewModel.kt` | A-GPS 下载/注入生命周期，WorkManager 调度 |
| `app/src/main/java/com/example/gpstest/data/source/GnssDataSourceImpl.kt` | 核心传感器融合 — 将 4 个 Android 回调合并为单个 Flow |
| `app/src/main/java/com/example/gpstest/domain/util/DopCalculator.kt` | DOP 矩阵运算 (PDOP/HDOP/VDOP) |
| `app/src/main/java/com/example/gpstest/domain/repository/AGpsRepositoryImpl.kt` | A-GPS 编排器，多 URL 回退和时间衰减状态 |
| `app/build.gradle.kts` | 应用模块构建配置，所有依赖 |
| `app/proguard-rules.pro` | 保留 GNSS 反射 API |

## 运行时/工具配置

- **JDK**: 17 (源码/目标兼容性)。本地和 CI 使用 JDK 21 作为构建 JDK
- **Gradle**: 8.9，启用配置缓存、并行构建、构建缓存
- **Kotlin 代码风格**: `official`
- **代码检查**: ktlint 1.5.0，通过 `org.jlleitschuh.gradle.ktlint` 插件 (12.1.2)，android 模式
- **Gradle 属性**: `org.gradle.java.home=C:/Program Files/Java/jdk-21` (仅本地；CI 通过 `sed` 移除)
- **无 product flavors** — 仅 `debug` 和 `release` 构建类型
- **Release 代码混淆已禁用** (`isMinifyEnabled = false`)

## 测试与质量保证

**框架**: JUnit 4.13.2。无 mock 库，无 Robolectric，无 Truth。

**范围**: 50 个单元测试，仅覆盖领域层：
- `domain/model/`: DopInfo (10), SatelliteHistory (9), Constellation (9), GnssData (8), GnssClockData (7)
- `domain/util/`: DopCalculator (7)

**测试约定**:
- 文件命名: `<源类名>Test.kt` — 与源文件 1:1 对应
- 包镜像: 测试包与源码包完全一致
- 方法命名: 反引号描述性名称 — `` `quality is EXCELLENT when pdop less than 1` ``
- 测试风格: 直接实例化 data class，断言计算属性。无测试基类，无 `@Before`/`@After`
- 测试数据: 每个测试类的私有 `makeSatellite()` 辅助函数

**未测试**: ViewModel、DataSource、Repository (领域逻辑除外)、UI/屏幕、Service、AGpsUpdateWorker、XtraDataValidator。
