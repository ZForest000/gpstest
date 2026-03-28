# GPS Debug Tool

一个功能强大的 Android GPS 调试工具，用于实时监测和分析 GNSS（全球导航卫星系统）数据。支持多星座卫星追踪、信号质量分析、A-GPS 数据管理和历史记录功能。

## 更新日志

- **2026-03-28**: 新增 AGC（自动增益控制）、多路径指示、基带信噪比、时钟偏差/漂移功能；集成 Shizuku 支持
- **2026-03-28**: 为项目生成并集成了全新的 Material 3 风格应用图标。

## 功能特性

### 卫星监测

- **实时卫星追踪**：显示当前可见的所有 GNSS 卫星
- **多星座支持**：GPS、GLONASS、Galileo、BeiDou、QZSS、SBAS、NavIC、Unknown
- **信号强度图表**：可视化展示各卫星的载噪比（CN0）
- **卫星详情**：查看每颗卫星的方位角、高度角、信噪比等详细信息
- **多路径指示**：检测信号是否经反射到达（Multipath Indicator）
- **AGC 数据**：自动增益控制电平，反映信号干扰环境
- **基带信噪比**：基带 C/N0，与信噪比互补的信号质量指标（API 30+）

### 时钟信息

- **时钟偏差**：接收机时钟与 GPS 时的偏差
- **时钟漂移**：时钟频率漂移率
- **不确定度**：偏差和漂移的测量不确定度

### Shizuku 支持

- **状态检测**：检测 Shizuku 是否运行及权限状态
- **模式识别**：识别 ROOT 或 ADB 模式

### 定位信息

- **实时位置数据**：纬度、经度、海拔、精度
- **速度信息**：当前移动速度和方向
- **定位精度**：水平精度和垂直精度
- **气压辅助**：使用气压计提高海拔测量精度

### A-GPS 管理

- **XTRA 数据下载**：自动下载 Qualcomm XTRA 辅助定位数据
- **定时更新**：支持设置自动更新间隔
- **数据验证**：验证下载数据的完整性
- **状态监控**：显示 A-GPS 数据的有效期和状态

### 历史记录

- **卫星历史快照**：保存和查看历史卫星状态
- **数据持久化**：使用 DataStore 保存历史数据
- **对比分析**：对比不同时间点的卫星分布

## 技术栈

- **UI 框架**：Jetpack Compose + Material Design 3
- **架构模式**：MVVM（Model-View-ViewModel）
- **异步处理**：Kotlin Coroutines + Flow
- **依赖注入**：手动注入（Factory 模式）
- **数据存储**：DataStore Preferences
- **后台任务**：WorkManager
- **网络请求**：OkHttp
- **权限增强**：Shizuku API（可选，用于系统级权限）
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
│   ├── model/                     # 数据模型
│   ├── source/                    # 远程/设备数据源
│   │   ├── AGpsDataSource.kt      # A-GPS 数据源接口
│   │   ├── AGpsDownloader.kt      # A-GPS 下载器
│   │   ├── BarometerDataSource.kt # 气压计数据源
│   │   ├── GnssDataSource.kt      # GNSS 数据源
│   │   └── ShizukuHelper.kt       # Shizuku 权限辅助类
│   └── validator/                 # 数据验证
│       └── XtraDataValidator.kt   # XTRA 数据验证
├── domain/                        # 领域层
│   ├── model/                     # 领域模型
│   │   ├── AGpsStatus.kt          # A-GPS 状态
│   │   ├── Constellation.kt       # 卫星星座枚举
│   │   ├── GnssClockData.kt       # GNSS 时钟数据
│   │   ├── GnssData.kt            # GNSS 数据
│   │   ├── GnssSatellite.kt       # 卫星信息（含 AGC、多路径、基带信噪比）
│   │   ├── LocationInfo.kt        # 定位信息
│   │   └── SatelliteHistory.kt    # 卫星历史
│   └── repository/                # 仓库接口和实现
├── service/                       # 后台服务
│   └── AGpsUpdateWorker.kt        # A-GPS 更新工作器
├── ui/                            # UI 层
│   ├── components/                # 可复用组件
│   │   ├── AGpsStatusCard.kt      # A-GPS 状态卡片
│   │   ├── HistorySnapshotCard.kt # 历史快照卡片
│   │   ├── LocationCard.kt        # 位置信息卡片
│   │   ├── SatelliteCard.kt       # 卫星信息卡片
│   │   ├── SatelliteDetailSheet.kt # 卫星详情底部弹窗
│   │   ├── SignalChart.kt         # 信号强度图表
│   │   └── StatBar.kt             # 统计信息栏
│   ├── screens/                   # 页面
│   │   ├── agps/                  # A-GPS 管理页面
│   │   ├── history/               # 历史记录页面
│   │   └── satellite/             # 卫星列表页面
│   └── theme/                     # 主题配置
├── viewmodel/                     # ViewModel
│   ├── AGpsViewModel.kt           # A-GPS ViewModel
│   └── SatelliteViewModel.kt      # 卫星 ViewModel
├── MainActivity.kt                # 主 Activity
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

1. 使用 Android Studio 打开项目
2. 同步 Gradle 依赖

```bash
./gradlew sync
```

1. 构建 APK

```bash
./gradlew assembleDebug
```

或直接在 Android Studio 中点击 "Run" 按钮安装到设备。

## 使用指南

### 首次使用

1. 安装应用后打开，授予定位权限
2. 应用会自动开始搜索卫星
3. 在户外或靠近窗户的位置可获得更好的信号

### A-GPS 数据更新

1. 进入 "A-GPS" 标签页
2. 点击 "立即更新" 手动下载最新数据
3. 或开启"自动更新"设置更新间隔

### 查看历史记录

1. 进入 "历史" 标签页
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

## 注意事项

1. **GPS 信号**：在室内或遮挡严重的地方可能无法获取卫星信号
2. **A-GPS 数据**：需要网络连接下载辅助定位数据
3. **电池消耗**：持续 GPS 定位会增加电量消耗
4. **Android 版本**：部分高级 GNSS 功能需要 Android 7.0+ 和硬件支持

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
