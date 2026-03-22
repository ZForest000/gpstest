# Android GPS调试工具 - 设计文档

**日期**: 2025-03-22
**状态**: 设计审查中
**作者**: Claude

---

## 1. 项目概述

### 1.1 目标
创建一个Android原生应用，用于开发和调试GPS相关功能。该应用将展示完整的GNSS卫星原始测量数据，并提供A-GPS数据管理功能。

### 1.2 目标用户
- 需要调试GPS功能的Android应用开发者
- 需要验证GNSS硬件性能的测试人员

### 1.3 核心功能
1. **GNSS卫星数据展示**：显示所有可见卫星的完整原始测量数据
2. **A-GPS数据管理**：支持手动导入、管理查看和自动更新A-GPS辅助数据

### 1.4 技术栈
- **语言**: Kotlin
- **UI框架**: Jetpack Compose + Material3
- **架构**: MVVM + Clean Architecture
- **最低SDK**: Android 7.0 (API 24)
- **目标SDK**: Android 15 (API 35)

---

## 2. 应用架构

### 2.1 架构分层

```
┌─────────────────────────────────────────────────────────────┐
│                      UI Layer (Compose)                      │
├─────────────────────────────────────────────────────────────┤
│  SatelliteListScreen  │  AGpsManagerScreen  │  MainScreen   │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                   ViewModel Layer                            │
├─────────────────────────────────────────────────────────────┤
│  SatelliteViewModel  │  AGpsViewModel  │  MainViewModel     │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                   Repository Layer                           │
├─────────────────────────────────────────────────────────────┤
│  GnssRepository      │  AGpsRepository     │  SettingsRepo   │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                   Data Layer                                 │
├─────────────────────────────────────────────────────────────┤
│  GnssDataSource      │  AGpsDataSource      │  FileHandler  │
│  (LocationManager)   │  (Download/Inject)   │  (.xml/.bin)  │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 分层职责

| 层级 | 职责 | 技术选型 |
|------|------|----------|
| **UI层** | 渲染界面，处理用户交互，无业务逻辑 | Jetpack Compose |
| **ViewModel层** | 持有UI状态，处理用户事件，协调Repository | ViewModel + StateFlow |
| **Repository层** | 协调数据源，封装业务逻辑，提供单一数据源 | Kotlin Interface |
| **数据层** | 与系统API、文件系统、网络交互 | LocationManager, OkHttp |

### 2.3 数据流

```
用户操作 → UI事件 → ViewModel → Repository → DataSource → 系统API
                                                   ↓
原始数据 ← DataSource ← Repository ← ViewModel ← UI更新
```

---

## 3. 核心数据模型

### 3.1 GNSS卫星数据模型

```kotlin
/**
 * 表示单个GNSS卫星的测量数据
 */
data class GnssSatellite(
    val svid: Int,                    // 卫星ID (PRN)
    val constellation: Constellation, // 星座类型
    val cn0DbHz: Float,               // 载噪比 (信号强度 dB-Hz)
    val azimuthDegrees: Float,        // 方位角 (0-360°)
    val elevationDegrees: Float,      // 仰角 (0-90°)
    val hasAlmanac: Boolean,          // 是否有历书数据
    val hasEphemeris: Boolean,        // 是否有星历数据
    val usedInFix: Boolean,           // 是否用于当前定位解算
    val carrierFrequencyHz: Float?,   // 载波频率 (Hz)
    val carrierCycles: Float?,        // 载波相位周期数
    val dopplerShiftHz: Float?,       // 多普勒频移 (Hz)
    val timeNanos: Long               // 测量时间戳 (纳秒)
)

/**
 * GNSS星座类型枚举
 */
enum class Constellation {
    GPS,        // 美国GPS (32颗卫星)
    GLONASS,    // 俄罗斯GLONASS (24颗卫星)
    GALILEO,    // 欧洲Galileo (30颗卫星)
    BEIDOU,     // 中国北斗 (45颗卫星)
    QZSS,       // 日本QZSS (4颗卫星)
    UNKNOWN     // 未知类型
}

/**
 * 卫星分组类型，用于UI展示
 */
enum class SatelliteGroup {
    USED_IN_FIX,    // 用于定位
    VISIBLE_ONLY,   // 可见但未用于定位
    SEARCHING       // 正在搜索
}
```

### 3.2 A-GPS数据模型

```kotlin
/**
 * A-GPS数据状态
 */
data class AGpsStatus(
    val timeStatus: DataStatus,       // 时间数据状态
    val ephemerisStatus: DataStatus,  // 星历数据状态
    val almanacStatus: DataStatus,    // 历书数据状态
    val lastUpdateTime: Long? = null  // 最后更新时间戳
)

/**
 * 数据有效期状态
 */
enum class DataStatus {
    VALID,       // 有效且在有效期内
    EXPIRED,     // 已过期
    PARTIAL,     // 部分有效（如部分卫星星历过期）
    MISSING      // 缺失
}

/**
 * A-GPS注入记录
 */
data class AGpsInjectionRecord(
    val id: String,
    val type: InjectionType,
    val source: InjectionSource,
    val timestamp: Long,
    val success: Boolean,
    val errorMessage: String? = null
)

enum class InjectionType { TIME, EPHEMERIS, ALMANAC }
enum class InjectionSource { MANUAL, AUTO_DOWNLOAD, NETWORK }
```

---

## 4. 核心组件设计

### 4.1 GnssDataSource

负责与Android LocationManager交互，获取原始GNSS测量数据。

```kotlin
interface GnssDataSource {
    /**
     * 开始监听GNSS测量数据
     * @return Flow<GnssMeasurement> 持续发射测量数据
     */
    fun startListening(): Flow<List<GnssSatellite>>

    /**
     * 停止监听
     */
    fun stopListening()

    /**
     * 检查设备是否支持GNSS测量
     */
    fun isSupported(): Boolean
}
```

**实现要点**:
- 使用 `LocationManager.registerGnssMeasurementCallback()`
- 将 `GnssMeasurement` 对象转换为领域模型 `GnssSatellite`
- 处理不同Android版本的API差异
- 使用 `Flow` 实现响应式数据流

### 4.2 AGpsDataSource

封装A-GPS数据注入API。

```kotlin
interface AGpsDataSource {
    /**
     * 注入时间数据
     */
    suspend fun injectTime(timeMillis: Long): Result<Unit>

    /**
     * 注入星历数据
     */
    suspend fun injectEphemeris(data: ByteArray): Result<Unit>

    /**
     * 注入历书数据
     */
    suspend fun injectAlmanac(data: ByteArray): Result<Unit>

    /**
     * 检查当前A-GPS数据状态
     */
    suspend fun checkStatus(): AGpsStatus
}
```

**实现要点**:
- 使用 `LocationManager.sendNiCommand()` 注入数据
- 支持不同格式的A-GPS数据（XTRA .bin, IENGSS .xml）
- 处理注入失败的情况，返回详细错误信息

### 4.3 AGpsDownloader

从网络下载A-GPS数据。

```kotlin
interface AGpsDownloader {
    /**
     * 下载A-GPS数据
     * @param url 数据源URL
     * @return Result<ByteArray> 下载的二进制数据
     */
    suspend fun download(url: String): Result<ByteArray>

    /**
     * 获取默认的A-GPS数据源URL
     */
    fun getDefaultUrls(): List<String>
}
```

**默认数据源**:
- Qualcomm XTRA: `https://xtrapath1.izatcloud.net/xtra3grc.bin`
- 备用源可配置

### 4.4 AGpsFileHandler

处理本地A-GPS文件导入。

```kotlin
interface AGpsFileHandler {
    /**
     * 解析A-GPS文件
     * @param uri 文件URI
     * @return Result<AGpsData> 解析后的数据
     */
    suspend fun parseFile(uri: Uri): Result<AGpsData>

    /**
     * 支持的文件类型
     */
    fun getSupportedTypes(): List<String>
}
```

**支持的格式**:
- `.bin` - Qualcomm XTRA格式
- `.xml` - IENGSS格式（包含星历/历书）
- `.txt` - 纯文本格式

---

## 5. UI设计

### 5.1 导航结构

```
MainActivity (单Activity架构)
    ├── SatelliteListScreen (主入口，卫星列表)
    │   ├── 顶部统计栏
    │   ├── 卫星卡片列表（分组显示）
    │   └── 底部详情抽屉
    ├── AGpsManagerScreen (A-GPS管理)
    └── SettingsScreen (设置)
```

### 5.2 主界面布局

```
┌────────────────────────────────────────────────────────┐
│  GPS Debug Tool                        ⚙️              │
├────────────────────────────────────────────────────────┤
│                                                          │
│  ┌────────────────────────────────────────────────┐   │
│  │ 📊 信号统计                                    │   │
│  │ 在用: 8  |  可见: 18  |  总计: 23              │   │
│  └────────────────────────────────────────────────┘   │
│                                                          │
│  ┌────────────────────────────────────────────────┐   │
│  │ 用于定位 (8) ▼                               │   │
│  ├────────────────────────────────────────────────┤   │
│  │ 🟢 GPS-1    45 dBHz   ⬆️ 45° ➡️ 120°          │   │
│  │ 🟢 GPS-8    42 dBHz   ⬆️ 30° ➡️ 200°          │   │
│  │ 🟢 GAL-12   38 dBHz   ⬆️ 15° ➡️ 80°           │   │
│  └────────────────────────────────────────────────┘   │
│                                                          │
│  ┌────────────────────────────────────────────────┐   │
│  │ 可见未用 (10) ▼                              │   │
│  ├────────────────────────────────────────────────┤   │
│  │ ⚪ GPS-3    25 dBHz   ⬆️ 10° ➡️ 300°          │   │
│  └────────────────────────────────────────────────┘   │
│                                                          │
│  ─────────────────────────────────────────────────────  │
│  📱 A-GPS管理                        │
└────────────────────────────────────────────────────────┘
```

### 5.3 A-GPS管理界面

```
┌────────────────────────────────────────────────────────┐
│  ← A-GPS 管理                                           │
├────────────────────────────────────────────────────────┤
│                                                          │
│  ┌────────────────────────────────────────────────┐   │
│  │ 📅 数据状态                                    │   │
│  │ ├─ 时间: ✅ 已同步 (2025-03-22 14:30:25)       │   │
│  │ ├─ 星历: ⚠️  部分过期 (18/32 有效)             │   │
│  │ └─ 历书: ✅ 有效 (更新于 2小时前)              │   │
│  └────────────────────────────────────────────────┘   │
│                                                          │
│  ┌────────────────────────────────────────────────┐   │
│  │ ⚙️ 自动更新                                    │   │
│  │ [开启]  每 24 小时自动下载更新                 │   │
│  └────────────────────────────────────────────────┘   │
│                                                          │
│  ┌────────────────────────────────────────────────┐   │
│  │ 📥 手动导入                                    │   │
│  │ [选择文件导入]  支持 .xml, .bin, .txt          │   │
│  └────────────────────────────────────────────────┘   │
│                                                          │
│  ┌────────────────────────────────────────────────┐   │
│  │ 📋 注入历史                                    │   │
│  │ • 2025-03-22 14:30 - 星历注入成功             │   │
│  │ • 2025-03-22 12:15 - 自动更新完成             │   │
│  └────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────┘
```

### 5.4 卫星详情抽屉

```
┌────────────────────────────────────────────────────────┐
│  卫星详情: GPS-1                           [关闭]       │
├────────────────────────────────────────────────────────┤
│  基本信息                                              │
│  • 星座类型: GPS                                       │
│  • 卫星ID (PRN): 1                                      │
│  • 信号强度: 45 dBHz                                   │
│  • 方位角: 120°  仰角: 45°                              │
│  • 状态: 用于定位 ✓                                     │
│                                                         │
│  原始测量数据                                          │
│  • 载波频率: 1575.42 MHz (L1)                          │
│  • 载波相位周期: 12345.67                              │
│  • 多普勒频移: -850 Hz                                  │
│  • 时间戳: 1856234123456 ns                            │
│  • 有星历: ✓  有历书: ✓                                │
│                                                         │
│  信号图表                                              │
│  [最近60秒信号强度趋势图]                              │
└────────────────────────────────────────────────────────┘
```

---

## 6. 权限与依赖

### 6.1 Android权限

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

### 6.2 核心依赖

```kotlin
dependencies {
    // Jetpack Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")

    // ViewModel & Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.3")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
```

---

## 7. 项目目录结构

```
app/src/main/
├── AndroidManifest.xml
├── java/com/example/gpstest/
│   ├── GpstestApplication.kt
│   ├── MainActivity.kt
│   │
│   ├── ui/
│   │   ├── navigation/
│   │   │   └── NavGraph.kt
│   │   ├── screens/
│   │   │   ├── satellite/
│   │   │   │   ├── SatelliteListScreen.kt
│   │   │   │   ├── SatelliteItem.kt
│   │   │   │   └── SatelliteDetailSheet.kt
│   │   │   ├── agps/
│   │   │   │   └── AGpsManagerScreen.kt
│   │   │   └── settings/
│   │   │       └── SettingsScreen.kt
│   │   ├── components/
│   │   │   ├── SatelliteCard.kt
│   │   │   ├── SignalChart.kt
│   │   │   ├── StatBar.kt
│   │   │   └── AGpsStatusCard.kt
│   │   └── theme/
│   │       ├── Color.kt
│   │       ├── Theme.kt
│   │       └── Type.kt
│   │
│   ├── viewmodel/
│   │   ├── SatelliteViewModel.kt
│   │   ├── AGpsViewModel.kt
│   │   └── MainViewModel.kt
│   │
│   ├── domain/
│   │   ├── model/
│   │   │   ├── GnssSatellite.kt
│   │   │   ├── AGpsStatus.kt
│   │   │   └── Constellation.kt
│   │   └── repository/
│   │       ├── GnssRepository.kt
│   │       ├── AGpsRepository.kt
│   │       └── SettingsRepository.kt
│   │
│   ├── data/
│   │   ├── source/
│   │   │   ├── GnssDataSource.kt
│   │   │   ├── AGpsDataSource.kt
│   │   │   └── AGpsDownloader.kt
│   │   ├── local/
│   │   │   └── AGpsFileHandler.kt
│   │   └── mapper/
│   │       └── GnssMeasurementMapper.kt
│   │
│   ├── service/
│   │   └── AGpsUpdateWorker.kt
│   │
│   └── util/
│       ├── ConstellationExt.kt
│       └── PermissionHelper.kt
│
└── res/
    ├── values/
    │   ├── strings.xml
    │   └── colors.xml
    └── drawable/
```

---

## 8. 关键功能实现细节

### 8.1 GNSS测量数据获取

使用 `GnssMeasurementsEvent` API获取原始测量数据：

```kotlin
class GnssDataSourceImpl(
    private val context: Context
) : GnssDataSource {

    private val locationManager = context.getSystemService<LocationManager>()
    private val _measurements = MutableSharedFlow<List<GnssSatellite>>()
    private var callback: GnssMeasurementsEvent.Callback? = null

    override fun startListening(): Flow<List<GnssSatellite>> {
        callback = object : GnssMeasurementsEvent.Callback() {
            override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent) {
                val satellites = event.measurements.map { it.toGnssSatellite() }
                // 发射到Flow
            }
        }
        locationManager?.registerGnssMeasurementCallback(callback, null)
        return _measurements.asSharedFlow()
    }

    override fun stopListening() {
        locationManager?.unregisterGnssMeasurementCallback(callback)
    }
}
```

### 8.2 A-GPS自动更新

使用WorkManager实现定期后台更新：

```kotlin
class AGpsUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // 1. 检查数据是否过期
        // 2. 下载新的A-GPS数据
        // 3. 注入到LocationManager
        return Result.success()
    }
}

// 调度定期任务
val constraints = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .setRequiresBatteryNotLow(true)
    .build()

val updateRequest = PeriodicWorkRequestBuilder<AGpsUpdateWorker>(24, TimeUnit.HOURS)
    .setConstraints(constraints)
    .build()

WorkManager.getInstance(context).enqueue(updateRequest)
```

### 8.3 信号图表绘制

使用Compose Canvas绘制信号强度趋势：

```kotlin
@Composable
fun SignalChart(
    readings: List<Float>,  // 最近N个信号强度读数
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // 绘制网格线
        // 绘制信号曲线
        val path = Path().apply {
            readings.forEachIndexed { index, value ->
                val x = canvasWidth * index / readings.size
                val y = canvasHeight * (1 - value / 60) // 60 dBHz为满格
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        drawPath(path, color = Color.Green, style = Stroke(2f))
    }
}
```

---

## 9. 开发路线图

### Phase 1: 基础框架（优先级最高）
- 创建Android项目，配置Compose和Material3
- 实现单Activity架构和导航系统
- 实现权限请求流程（精确位置权限）
- 创建基础主题和UI组件

### Phase 2: GNSS数据核心
- 实现GnssDataSource（注册监听、接收测量回调）
- 定义GnssSatellite等数据模型
- 实现GnssRepository和SatelliteViewModel
- 创建卫星列表UI展示

### Phase 3: 卫星详情与可视化
- 实现卫星详情底部抽屉
- 使用Canvas绘制信号强度趋势图
- 实现数据分组和过滤功能
- 优化列表性能（LazyColumn）

### Phase 4: A-GPS管理
- 实现AGpsDataSource（注入API封装）
- 实现AGpsFileHandler（文件解析）
- 实现AGpsRepository和AGpsViewModel
- 创建A-GPS管理界面

### Phase 5: 自动更新
- 实现AGpsDownloader（网络下载）
- 使用WorkManager实现后台更新任务
- 实现定期检查和更新逻辑
- 使用DataStore持久化设置

### Phase 6: 优化与测试
- 性能优化（列表虚拟化、数据缓存）
- 完善异常处理和用户提示
- 真机测试（不同厂商设备）
- UI细节打磨

---

## 10. 非功能需求

### 10.1 性能要求
- 卫星列表更新频率：最高每秒1次
- UI帧率：保持60fps流畅滚动
- 内存占用：不超过150MB
- APK大小：不超过10MB

### 10.2 兼容性要求
- 最低支持Android 7.0 (API 24)
- 适配Android 12+的位置权限新规
- 适配不同屏幕尺寸（手机/平板）

### 10.3 可用性要求
- 关键操作有明确反馈
- 异常情况有友好提示
- 支持深色模式

---

## 11. 风险与缓解措施

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 某些设备不支持原始GNSS测量 | 核心功能不可用 | 运行时检测API可用性，提供降级方案 |
| A-GPS数据格式兼容性问题 | 导入功能异常 | 支持多种格式，提供详细错误提示 |
| 后台更新被系统限制 | 自动更新失败 | 使用WorkManager，处理任务失败重试 |
| 不同厂商GPS实现差异 | 数据显示不一致 | 测试多品牌设备，统一数据处理逻辑 |

---

## 12. 附录

### 12.1 参考资料
- [Android GNSS原生测量API](https://developer.android.com/reference/android/location/GnssMeasurement)
- [Android LocationManager文档](https://developer.android.com/reference/android/location/LocationManager)
- [Qualcomm XTRA格式规范](https://developer.qualcomm.com/software/qualcomm-xtm-assist-now)

### 12.2 术语表
- **GNSS**: 全球导航卫星系统（Global Navigation Satellite System）
- **A-GPS**: 辅助GPS（Assisted GPS），通过网络下载辅助数据加速定位
- **PRN**: 伪随机噪声码（Pseudo-Random Noise），卫星标识号
- **C/N0**: 载噪比（Carrier-to-Noise density ratio），信号强度指标
- **星历**: 描述卫星轨道的精确参数，有效期约2-4小时
- **历书**: 描述所有卫星轨道的粗略参数，有效期约1-3个月
