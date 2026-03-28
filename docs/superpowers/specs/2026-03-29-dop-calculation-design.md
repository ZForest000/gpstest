# HDOP/VDOP/PDOP 精度因子功能设计

## Context

Android 原生 API 不提供 DOP（Dilution of Precision）值。当前 `GnssSatellite` 已包含方位角（azimuth）和仰角（elevation）数据，可以基于卫星几何矩阵计算 DOP。用户希望在卫星列表页面新增独立的 DOP 精度卡片，显示完整信息（数值 + 精度颜色 + 参与卫星数），仅基于 usedInFix 卫星计算。

## 方案

新建独立 `DopCalculator` 工具类进行纯几何计算，通过 ViewModel 传递结果到新建的 `DopCard` UI 组件。

## 涉及文件

| 文件 | 操作 | 说明 |
|------|------|------|
| `domain/model/DopInfo.kt` | 新建 | DOP 数据类 + 精度等级枚举 |
| `domain/util/DopCalculator.kt` | 新建 | 纯函数式 DOP 计算器 |
| `viewmodel/SatelliteViewModel.kt` | 修改 | UI State 增加 dopInfo 字段 |
| `ui/components/DopCard.kt` | 新建 | DOP 展示卡片组件 |
| `ui/screens/satellite/SatelliteListScreen.kt` | 修改 | 插入 DopCard |
| `res/values/strings.xml` | 修改 | 新增字符串资源 |

## 详细设计

### 1. DopInfo 数据类

文件：`app/src/main/java/com/example/gpstest/domain/model/DopInfo.kt`

```kotlin
enum class DopQuality {
    EXCELLENT,  // PDOP < 1
    GOOD,       // 1 <= PDOP < 2
    MODERATE,   // 2 <= PDOP < 5
    FAIR,       // 5 <= PDOP < 10
    POOR        // PDOP >= 10
}

data class DopInfo(
    val pdop: Double,
    val hdop: Double,
    val vdop: Double,
    val satelliteCount: Int
) {
    val quality: DopQuality
        get() = when {
            pdop < 1 -> DopQuality.EXCELLENT
            pdop < 2 -> DopQuality.GOOD
            pdop < 5 -> DopQuality.MODERATE
            pdop < 10 -> DopQuality.FAIR
            else -> DopQuality.POOR
        }
}
```

### 2. DopCalculator

文件：`app/src/main/java/com/example/gpstest/domain/util/DopCalculator.kt`

纯 Kotlin 对象，无 Android 依赖。放在 `domain/util/` 下而非 `domain/model/`，因为它是计算工具而非数据模型。

**算法**：
1. 过滤卫星：
   - 排除 `elevationDegrees < 0` 的卫星（地平线以下）
   - 排除 `elevationDegrees == 0f && azimuthDegrees == 0f` 的卫星（Android 默认值，表示无有效几何数据）
2. 需要至少 4 颗有效卫星
3. 构建 N×4 几何矩阵 H，每行：
   - `x = cos(el) * sin(az)`
   - `y = cos(el) * cos(az)`
   - `z = sin(el)`
   - `w = 1`（时间项）
4. 计算 `Q = (H^T · H)^{-1}`
5. **矩阵有效性检查**：验证 Q 对角线元素 Q[0,0]、Q[1,1]、Q[2,2] 均为正数、有限值（非 NaN/Infinity）。若任一不满足，返回 `null`（卫星几何退化场景）
6. PDOP = √(Q[0,0] + Q[1,1] + Q[2,2])
7. HDOP = √(Q[0,0] + Q[1,1])
8. VDOP = √(Q[2,2])

**矩阵运算**：使用 4×4 手动矩阵求逆（Gauss-Jordan 消元），不引入外部依赖。对于 4×4 矩阵足够高效。

以下情况返回 `null`：
- 有效卫星不足 4 颗
- 矩阵求逆失败（奇异矩阵）
- 对角线元素非正/非有限值

### 3. ViewModel 修改

文件：`app/src/main/java/com/example/gpstest/viewmodel/SatelliteViewModel.kt`

- `SatelliteUiState.Success` 新增字段 `val dopInfo: DopInfo? = null`
- 在 `startListening()` 的 collect 回调中，group by 之后计算：

```kotlin
val usedInFixList = grouped[SatelliteGroup.USED_IN_FIX].orEmpty()
val dopInfo = DopCalculator.calculate(usedInFixList)
```

### 4. DopCard 组件

文件：`app/src/main/java/com/example/gpstest/ui/components/DopCard.kt`

风格采用与 `ConstellationStatCard` 一致的模式（`Column` + `.background()` 而非 `Card` composable）：
- `surfaceVariant` 背景 + `RoundedCornerShape(12.dp)`
- `padding(16.dp)`

布局：
```
┌─────────────────────────────────┐
│ DOP 精度因子        卫星: N 颗   │
│                                 │
│ ● PDOP  1.2                     │
│ ● HDOP  0.8                     │
│ ● VDOP  0.9                     │
└─────────────────────────────────┘
```

- 每行前的圆点颜色独立反映该 DOP 值的精度等级（使用与 PDOP 相同阈值分别评估 HDOP/VDOP）：
  - 值 < 2 → 绿色（优）
  - 2 <= 值 < 5 → 黄色（中）
  - 5 <= 值 < 10 → 橙色（良）
  - 值 >= 10 → 红色（差）
- `dopInfo == null` 时显示 "等待定位..." 占位文本
- 使用 `Row` + `Spacer` 排列，标签用 `bodySmall`，数值用 `bodyMedium` + `FontWeight.Bold`

### 5. 屏幕集成

文件：`app/src/main/java/com/example/gpstest/ui/screens/satellite/SatelliteListScreen.kt`

- `SatelliteListContent` 新增参数 `dopInfo: DopInfo?`
- 在 `ConstellationStatCard` 之后、`ClockInfoCard` 之前插入 `DopCard(dopInfo = dopInfo)`
- 从 `SatelliteUiState.Success` 传递 `dopInfo`

### 6. 字符串资源

在 `res/values/strings.xml` 新增：
- `dop_title`：DOP 精度因子
- `dop_satellites`：卫星: %d 颗
- `dop_waiting`：等待定位...
- `dop_pdop`、`dop_hdop`、`dop_vdop`：对应标签

## 验证

1. 构建项目：`./gradlew assembleDebug`
2. 安装到设备，检查：
   - 无定位时 DOPCard 显示等待状态
   - 有定位后显示 PDOP/HDOP/VDOP 数值和颜色
   - 卫星数少于 4 颗时显示等待状态
   - 精度颜色随 DOP 值变化正确
