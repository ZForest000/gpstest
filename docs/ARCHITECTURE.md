# 架构说明

GPS Debug Tool 采用 **Clean MVVM** 与 **单向数据流**。单 Activity（`MainActivity`），无 Fragment；UI 为 Jetpack Compose + Material 3。

## 分层

```
数据层 ──→ 领域层 ──→ 表现层
(数据源、     (仓库接口、    (ViewModel +
 持久化、      模型、工具)     Compose UI)
 验证器)
```

| 层     | 包路径                                             | 职责                                              |
| ------ | -------------------------------------------------- | ------------------------------------------------- |
| 表现层 | `ui/`、`viewmodel/`                                | Compose 屏幕/组件、密封 UI 状态、`StateFlow` 收集 |
| 领域层 | `domain/model`、`domain/repository`、`domain/util` | 模型、仓库接口与实现、DOP 等纯逻辑                |
| 数据层 | `data/source`、`data/local`、`data/validator`      | 平台回调、下载、DataStore、文件、校验             |
| 服务   | `service/`                                         | WorkManager（如 `AGpsUpdateWorker`）              |

依赖方向：表现层 → 领域层 → 数据抽象；平台细节留在 data 实现中。

## 依赖注入

**手动 DI**，无 Hilt / Dagger / Koin。

- `GpsTestApplication` 持有惰性创建的 `AppDependencies`，是唯一的应用级手动 DI 组合点。
- `AppDependencies` 惰性缓存现有的 GNSS、卫星历史、应用设置、外部 GPS 星历和 A-GPS 依赖链。
- 通过 `ViewModelProvider.Factory` 注入：
    - `SatelliteViewModelFactory`
    - `AGpsViewModelFactory`
    - `SettingsViewModelFactory`
    - `NmeaViewModelFactory`
    - `NavigationMessageViewModelFactory`
- `MainActivity` 的 Factory 与 `AGpsUpdateWorker` 都从同一个应用组合根读取依赖，不再自行构造依赖链。

约定：接口无后缀（如 `GnssAcquisitionSession`），实现类加 `Impl`。

## 状态管理

ViewModel 内 `MutableStateFlow`，对外只读 `StateFlow`。典型密封状态：

- `SatelliteUiState`：`Loading` → `PermissionRequired` → `Success(...)` → `Error(message)`
- `AGpsUiState`：`Idle` → `Downloading` → `Injecting` → `Success(message)` → `Error(message)`

错误：数据源/仓库多用 `Result<T>`；ViewModel 在协程中 `try/catch` 并映射到密封错误态。

## 核心数据管道

### GNSS（实时卫星与定位）

```
Android 回调
  GnssStatus + GnssMeasurements + Location + 气压计
        ↓ callbackFlow 归一化（GnssPlatformSourceImpl）
  Flow<GnssAcquisitionEvent>
        ↓ 事件融合 + WhileSubscribed(0) 共享（GnssAcquisitionSessionImpl）
  Flow<GnssData>
        ↓
  GnssRepositoryImpl
        ↓
  SatelliteViewModel  →  StateFlow<SatelliteUiState>
        ↓ collectAsState()
  Compose UI（列表 / 天空图 / DOP / TTFF 等）
```

NMEA、导航电文和天线信息也由同一个 acquisition session 按需共享；同类平台 listener
仅在首个 consumer 到来时注册，并在最后一个 consumer 取消时立即注销。

### A-GPS（XTRA 下载与注入）

```
用户操作 或 WorkManager
        ↓
  AGpsViewModel
        ↓
  AGpsRepositoryImpl
        ├─ 下载（OkHttp，AGpsDownloader）
        ├─ 验证（XtraDataValidator）
        └─ 注入（LocationManager.sendExtraCommand）
```

多 URL 回退顺序：用户配置 URL → 多个 Qualcomm izatcloud 默认地址。  
WorkManager 使用指数退避重试。

### 历史快照

```text
SatelliteViewModel.maybeSaveSnapshot()（约每 60 秒）
        ↓
SatelliteHistoryRepositoryImpl
        ↓
SatelliteHistoryPersistence
        ├─ RoomSatelliteHistoryStore（v1→v2 显式 migration、当前读写）
        └─ SatelliteHistoryDataStore（仅 legacy JSON / marker 兼容）
        ↓ Flow<List<SatelliteHistorySnapshot>>
HistoryScreen
```

`SatelliteHistoryPersistence` 唯一拥有 legacy JSON 导入、重开恢复、retention 和 clear 的协调职责。`RoomSatelliteHistoryStore` 使用显式且非 destructive 的 Room v1→v2 migration，并承担当前历史的读写；`SatelliteHistoryDataStore` 仅保留旧 JSON 与 marker 的兼容职责。UI、ViewModel 和 repository 均不读取 marker 或 Room version。

## 导航

`MainActivity` + Navigation Compose。主要入口包括：

- **SatelliteListScreen** — 实时卫星列表（定位中 / 可见 / 搜索中）
- **AGpsManagerScreen** — 下载、导入、注入、自动更新
- **HistoryScreen** — 历史快照
- 另有天空图、帮助等屏幕（见 `ui/screens/`）

## 关键目录（精简）

```
app/src/main/java/com/example/gpstest/
├── GpsTestApplication.kt     # Application 入口，持有应用级组合根
├── AppDependencies.kt        # 惰性缓存的应用级依赖组合
├── MainActivity.kt           # Activity 入口、权限、导航；消费应用组合根
├── viewmodel/                # SatelliteViewModel, AGpsViewModel, …
├── domain/
│   ├── model/
│   ├── repository/           # 接口 + Impl 同包
│   └── util/                 # 如 DopCalculator
├── data/
│   ├── source/               # GNSS / A-GPS / Shizuku 等
│   ├── local/                # DataStore、文件
│   └── validator/            # XtraDataValidator
├── service/                  # AGpsUpdateWorker
└── ui/
    ├── screens/
    ├── components/
    └── theme/
```

## 测试范围

| 优先覆盖         | 说明                                                 |
| ---------------- | ---------------------------------------------------- |
| **domain**       | 模型计算属性、`DopCalculator`、解析/导出等纯逻辑     |
| **ViewModel**    | 状态机、TTFF、信号历史缓冲、调度入口等可测路径       |
| 部分 data 纯逻辑 | 如校验器、codec、无完整 Android 运行时依赖的下载逻辑 |

| 通常不单测                                  | 说明                            |
| ------------------------------------------- | ------------------------------- |
| 完整 UI / Compose 屏幕                      | 无强制 UI 测试基线              |
| 强依赖 LocationManager 的 DataSource 全链路 | 平台回调集成                    |
| `AGpsUpdateWorker` 端到端                   | 由编排逻辑与 VM/Repo 测间接覆盖 |

测试框架：JUnit 4；命令：`./gradlew test`。命名与包镜像约定见 [CONTRIBUTING.md](../CONTRIBUTING.md)。

## 设计原则（摘要）

1. **单向数据流**：平台 → Flow → Repository → ViewModel → UI
2. **接口 + 实现**：便于替换数据源与单测
3. **无状态 Compose**：状态提升到 Screen / ViewModel
4. **Result + 密封状态**：可预期的加载/成功/失败
5. **后台可靠任务**：A-GPS 定时更新走 WorkManager

更细的命令、权限与文件职责见根目录 [AGENTS.md](../AGENTS.md) 与 [README.md](../README.md)。
