# 平均基带信噪比和时钟偏差/漂移功能实现计划

## 目标

实现 TODO.md 中中等价值功能：
- **基带 C/N0** — `GnssStatus.basebandCn0DbHz`，与信噪比互补的信号质量指标
- **时钟偏差/漂移** — `GnssClock` 接收机时钟状态

同时集成 **Shizuku** 以获取额外数据：
- 通过 `dumpsys location` 获取 `avgBasebandCn0`、`Used-in-fix constellation types` 等

## 技术背景

### 基带信噪比 (Baseband C/N0)
- 来源：`GnssStatus.getBasebandCn0DbHz(int)` (API 30+)
- 含义：基带载波噪声密度比，通常比 `getCn0DbHz()` 低几 dB
- 典型范围：10-50 dB-Hz

### 时钟数据 (GnssClock)
来自 `GnssMeasurementsEvent.Clock`：
| 字段 | 含义 | 单位 |
|-----|------|-----|
| `biasNanos` | 钟亚纳秒偏差 | 纳秒 |
| `fullBiasNanos` | 接收机钟与 GPS 时的总偏差 | 纳秒 |
| `driftNanosPerSecond` | 时钟漂移 | 纳秒/秒 |
| `biasUncertaintyNanos` | 偏差不确定度 (1σ) | 纳秒 |
| `driftUncertaintyNanosPerSecond` | 漂移不确定度 (1σ) | 纳秒/秒 |

### Shizuku 集成
- 通过 Shizuku 执行 `dumpsys location` 命令
- 解析输出获取额外数据
- 权限：ADB (UID 2000) 或 ROOT (UID 0)

## 实现步骤

### 步骤 1：集成 Shizuku 依赖

**1.1 添加 Gradle 依赖**
```groovy
// build.gradle.kts (app level)
def shizuku_version = "13.1.5"
implementation "dev.rikka.shizuku:api:$shizuku_version"
implementation "dev.rikka.shizuku:provider:$shizuku_version"
```

**1.2 添加 ShizukuProvider 到 AndroidManifest.xml**
```xml
<provider
    android:name="rikka.shizuku.ShizukuProvider"
    android:authorities="${applicationId}.shizuku"
    android:multiprocess="false"
    android:enabled="true"
    android:exported="true"
    android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />
```

### 步骤 2：创建 Shizuku 管理类

**2.1 创建 ShizukuHelper**
- 检查 Shizuku 是否运行
- 请求权限
- 执行 shell 命令
- 解析 `dumpsys location` 输出

### 步骤 3：扩展数据模型

**3.1 创建 GnssClockData 数据类**
```kotlin
data class GnssClockData(
    val timeNanos: Long,
    val biasNanos: Double?,
    val fullBiasNanos: Long?,
    val driftNanosPerSecond: Double?,
    val biasUncertaintyNanos: Double?,
    val driftUncertaintyNanosPerSecond: Double?
)
```

**3.2 创建 DumpsysGnssData 数据类**（Shizuku 获取的额外数据）
```kotlin
data class DumpsysGnssData(
    val avgBasebandCn0: Float?,
    val measurementCount: Int,
    val usedInFixConstellations: List<String>
)
```

**3.3 扩展 GnssSatellite**
- 添加 `basebandCn0DbHz: Float?` 字段

**3.4 扩展 GnssData**
- 添加 `clock: GnssClockData?` 字段
- 添加 `dumpsysData: DumpsysGnssData?` 字段
- 添加 `avgBasebandCn0DbHz: Float` 计算属性

### 步骤 4：更新数据源

**4.1 修改 GnssDataSourceImpl**
- 提取 `GnssClock` 数据
- 提取 `basebandCn0DbHz` (API 30+)
- 集成 Shizuku 获取 dumpsys 数据

### 步骤 5：更新 UI 显示

**5.1 卫星详情 (SatelliteDetailSheet)**
- 添加基带信噪比显示

**5.2 新建时钟信息卡片**
- 显示时钟偏差/漂移
- 显示平均基带信噪比
- 显示 Shizuku 状态

**5.3 添加字符串资源**

### 步骤 6：构建验证

- 运行 `./gradlew assembleDebug` 确保编译通过

## 文件变更清单

| 文件 | 变更类型 |
|-----|---------|
| `build.gradle.kts` | 修改 - 添加 Shizuku 依赖 |
| `AndroidManifest.xml` | 修改 - 添加 ShizukuProvider |
| `domain/model/GnssClockData.kt` | 新建 |
| `domain/model/DumpsysGnssData.kt` | 新建 |
| `domain/model/GnssSatellite.kt` | 修改 |
| `domain/model/GnssData.kt` | 修改 |
| `data/source/ShizukuHelper.kt` | 新建 |
| `data/source/GnssDataSourceImpl.kt` | 修改 |
| `ui/components/SatelliteDetailSheet.kt` | 修改 |
| `ui/components/ClockInfoCard.kt` | 新建 |
| `res/values/strings.xml` | 修改 |

## API 兼容性

| API | 最低版本 |
|-----|---------|
| `GnssStatus.getBasebandCn0DbHz()` | API 30 (Android 11) |
| `GnssClock` | API 24 (Android 7.0) |
| Shizuku | Android 6.0+ |

## Shizuku 权限说明

- 用户需要安装 Shizuku 应用
- 首次使用需要授权
- ADB 模式：权限有限但足够执行 dumpsys
- ROOT 模式：完全权限
