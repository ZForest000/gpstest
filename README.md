# GPS Debug Tool

一个功能强大的 Android GPS 调试工具，用于实时监测和分析 GNSS（全球导航卫星系统）数据。支持多星座卫星追踪、信号质量分析、精度因子评估、A-GPS 数据管理和历史记录功能。

## 功能特性

### 卫星监测

- **实时卫星追踪**：显示当前可见的所有 GNSS 卫星，按「已定位」「可见」「搜索中」分组展示
- **多星座支持**：GPS、GLONASS、Galileo、BeiDou、QZSS、SBAS
- **星座健康摘要**：以进度条形式展示各星座卫星可用比例
- **星座统计**：各星座卫星数量统计卡片
- **信号强度图表**：60 秒滚动历史信号强度（C/N0）可视化
- **卫星详情**：底部弹窗查看每颗卫星的方位角、高度角、信噪比等详细信息
- **星历/历书状态**：指示卫星是否拥有有效星历和历书数据
- **高级测量数据**：载波频率、载波周期、多普勒频移、AGC（自动增益控制）、多路径指示、基带信噪比（API 30+）

### 卫星天空图

- **极坐标天空图**：以雷达图形式可视化卫星方位角和高度角
- **交互选择**：点击天空图上的卫星标记查看详细数据
- **星座颜色标识**：不同星座使用不同颜色区分

### 精度因子 (DOP)

- **PDOP**：位置精度衰减因子，综合评估定位精度
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

### A-GPS 管理

- **XTRA 数据下载**：自动下载 Qualcomm XTRA 辅助定位数据
- **数据注入**：下载后自动验证并注入到定位引擎
- **定时更新**：支持设置自动更新间隔（WorkManager 后台任务）
- **数据验证**：验证下载数据的完整性和格式
- **状态监控**：显示 A-GPS 数据的有效期和状态
- **文件导入**：支持从文件导入 A-GPS 数据

### 历史记录

- **卫星历史快照**：自动定期保存卫星状态快照
- **数据持久化**：使用 DataStore 保存历史数据
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

- **UI 框架**：Jetpack Compose + Material Design 3
- **架构模式**：MVVM（Model-View-ViewModel）
- **异步处理**：Kotlin Coroutines + Flow
- **依赖注入**：手动注入（Factory 模式）
- **数据存储**：DataStore Preferences
- **后台任务**：WorkManager
- **网络请求**：OkHttp
- **权限增强**：Shizuku API（可选，用于系统级权限和 dumpsys 数据）
- **最低 SDK**：API 24 (Android 7.0)
- **目标 SDK**：API 35 (Android 15)

## 项目结构

```
app/src/main/java/com/example/gpstest/
├── data/                          # 数据层
│   ├── local/                     # 本地数据源
│   │   ├── AGpsFileHandler.kt     # A-GPS 文件处理
│   │   ├── AGpsSettingsStore.kt   # A-GPS 设置存储
│   │   └── SatelliteHistoryDataStore.kt  # 卫星历史数据存储
│   ├── source/                    # 远程/设备数据源
│   │   ├── AGpsDataSource.kt      # A-GPS 数据源接口
│   │   ├── AGpsDataSourceImpl.kt  # A-GPS 数据源实现
│   │   ├── AGpsDownloader.kt      # A-GPS 下载器
│   │   ├── GnssDataSource.kt      # GNSS 数据源接口
│   │   ├── GnssDataSourceImpl.kt  # GNSS 数据源实现
│   │   └── ShizukuHelper.kt       # Shizuku 权限辅助类
│   └── validator/                 # 数据验证
│       └── XtraDataValidator.kt   # XTRA 数据验证
├── domain/                        # 领域层
│   ├── model/                     # 领域模型
│   │   ├── AGpsStatus.kt          # A-GPS 状态
│   │   ├── Constellation.kt       # 卫星星座枚举
│   │   ├── DopInfo.kt             # 精度衰减因子数据
│   │   ├── GnssClockData.kt       # GNSS 时钟数据
│   │   ├── GnssData.kt            # GNSS 数据聚合
│   │   ├── GnssSatellite.kt       # 卫星信息（含 AGC、多路径、基带信噪比等）
│   │   ├── LocationInfo.kt        # 定位信息
│   │   ├── SatelliteGroup.kt      # 卫星分组枚举（已定位/可见/搜索中）
│   │   └── SatelliteHistory.kt    # 卫星历史
│   ├── repository/                # 仓库接口和实现
│   │   ├── AGpsRepository.kt      # A-GPS 仓库接口
│   │   ├── AGpsRepositoryImpl.kt
│   │   ├── GnssRepository.kt      # GNSS 仓库接口
│   │   ├── GnssRepositoryImpl.kt
│   │   ├── SatelliteHistoryRepository.kt
│   │   └── SatelliteHistoryRepositoryImpl.kt
│   └── util/                      # 工具类
│       └── DopCalculator.kt       # 精度因子计算
├── service/                       # 后台服务
│   └── AGpsUpdateWorker.kt        # A-GPS 更新工作器
├── ui/                            # UI 层
│   ├── components/                # 可复用组件
│   │   ├── AGpsStatusCard.kt      # A-GPS 状态卡片
│   │   ├── ClockInfoCard.kt       # 时钟信息卡片
│   │   ├── ConstellationHealthSummaryCard.kt  # 星座健康摘要
│   │   ├── ConstellationStatCard.kt           # 星座统计卡片
│   │   ├── ConstellationUiExt.kt  # 星座 UI 扩展
│   │   ├── DopCard.kt             # 精度因子卡片
│   │   ├── HistorySnapshotCard.kt # 历史快照卡片
│   │   ├── LocationCard.kt        # 位置信息卡片
│   │   ├── SatelliteCard.kt       # 卫星信息卡片
│   │   ├── SatelliteDetailSheet.kt # 卫星详情底部弹窗
│   │   ├── SharedComponents.kt    # 共享 UI 组件
│   │   ├── SignalChart.kt         # 信号强度图表
│   │   ├── StatBar.kt             # 统计信息栏
│   │   ├── TtffCard.kt            # 首次定位时间卡片
│   │   └── UiUtils.kt             # UI 工具函数
│   ├── screens/                   # 页面
│   │   ├── agps/                  # A-GPS 管理页面
│   │   ├── help/                  # 帮助页面
│   │   ├── history/               # 历史记录页面
│   │   ├── satellite/             # 卫星列表页面
│   │   └── skychart/              # 卫星天空图页面
│   └── theme/                     # 主题配置
├── viewmodel/                     # ViewModel
│   ├── AGpsViewModel.kt           # A-GPS ViewModel
│   └── SatelliteViewModel.kt      # 卫星 ViewModel
├── MainActivity.kt                # 主 Activity（导航、权限、DI）
└── GpstestApplication.kt          # Application 类
```

## 权限要求

应用需要以下权限：

- `ACCESS_FINE_LOCATION` - 精确定位权限
- `ACCESS_COARSE_LOCATION` - 粗略定位权限
- `ACCESS_LOCATION_EXTRA_COMMANDS` - 发送定位相关命令（用于注入 A-GPS 数据）
- `INTERNET` - 网络访问权限（下载 A-GPS 数据）

## 安装说明

### 环境要求

- Android Studio Ladybug 或更高版本
- JDK 17 或更高版本
- Android SDK API 24-35

### 构建步骤

1. 克隆仓库

```bash
git clone <repository-url>
cd gpstest
```

2. 使用 Android Studio 打开项目
3. 同步 Gradle 依赖

```bash
./gradlew sync
```

4. 构建 APK

```bash
./gradlew assembleDebug
```

或直接在 Android Studio 中点击 "Run" 按钮安装到设备。

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

1. 进入「历史」页面
2. 查看已保存的卫星状态快照
3. 点击快照查看详细信息

## 技术亮点

### 响应式 UI

- 使用 Kotlin Flow 实现数据流的响应式更新
- Compose 的 remember 和 derivedStateOf 优化性能
- 自动处理配置变更（屏幕旋转等）

### 模块化架构

- 清晰的分层架构（Data/Domain/UI）
- 依赖反转原则，便于测试和维护
- Repository 模式统一管理数据来源

### 后台任务

- WorkManager 实现可靠的定时 A-GPS 更新
- 支持设备重启后继续任务
- 智能的重试机制和退避策略

### 数据验证

- XTRA 数据完整性校验
- 文件大小和格式验证
- 下载异常处理

### Shizuku 增强

- 通过 Shizuku 获取系统级 GNSS dumpsys 数据
- 支持 ROOT 和 ADB 两种运行模式
- 非侵入式设计：Shizuku 不可用时不影响基本功能

## 注意事项

1. **GPS 信号**：在室内或遮挡严重的地方可能无法获取卫星信号
2. **A-GPS 数据**：需要网络连接下载辅助定位数据
3. **电池消耗**：持续 GPS 定位会增加电量消耗
4. **Android 版本**：部分高级 GNSS 功能需要 Android 7.0+ 和硬件支持
5. **Shizuku**：高级系统数据功能需要安装 Shizuku 应用并授权

## 开源协议

本项目采用 MIT 协议开源 - 详见 [LICENSE](LICENSE) 文件

## 贡献指南

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

***

**免责声明**：本应用仅供开发和调试使用，不保证定位数据的绝对准确性。
