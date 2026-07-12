# 天空图交互 Phase 2 设计

## 目标

补齐天空图 U2 剩余交互能力：双指缩放/平移、卫星位置动画、可选的北向上（north-up）罗盘旋转。Phase 1（SVID 标注 + 星座筛选）已上线，不在本文范围。

## 范围

### 范围内

1. **双指缩放 + 单指平移 + 双击重置**（作用于天空图画布）
2. **卫星位置动画**（方位角/高度角插值 + 出现/消失淡入淡出）
3. **北向上开关**（默认关闭），航向来自 `TYPE_ROTATION_VECTOR`

### 范围外

- 截图 / 分享
- 标注碰撞避让
- 惯性滑动（fling）、双指旋转手势
- ViewModel / Repository / 领域层 / 数据层改动
- 新增 Android 权限或第三方依赖
- 进程被杀后仍持久化变换/北向上/筛选（仅 `remember` / 配置变更级别）
- 航向上模式（设备朝向在顶部）——仅支持真北向上

## 架构

状态与传感器逻辑留在 Compose UI 层。`SkyChartView` 保持为纯绘制 + 命中检测组件。

### 新增文件

| 文件                                          | 职责                                                                      |
| --------------------------------------------- | ------------------------------------------------------------------------- |
| `ui/components/SkyChartTransformState.kt`     | scale、offset、northUp；钳位/重置辅助；命中检测用逆变换                   |
| `ui/components/CompassHeadingSource.kt`       | 仅在 northUp 开启时注册/注销 `TYPE_ROTATION_VECTOR`；对外暴露平滑后的航向 |
| `ui/components/AnimatedSatellitePositions.kt` | 以 constellation+svid 为键；插值 az/el；出现/消失淡入淡出                 |

### 修改文件

| 文件                            | 变更                                                 |
| ------------------------------- | ---------------------------------------------------- |
| `ui/components/SkyChartView.kt` | 应用画布变换；按 alpha 绘制动画位置；逆变换命中检测  |
| `ui/screens/SkyChartScreen.kt`  | 持有变换状态、罗盘生命周期、动画位置；北向上开关浮层 |

### 不改动

- `SkyChartLegend.kt` — 星座开关保持 Phase 1 行为
- `SatelliteViewModel`、仓库、数据源 — 无改动
- `AndroidManifest` — 无新权限（`TYPE_ROTATION_VECTOR` 不需要权限）

### 数据流

```
GNSS 卫星（已按星座筛选）
        │
        ▼
AnimatedSatellitePositions  ──►  动画后的 (az, el, alpha) 列表
        │
        ▼
SkyChartView
  ◄── SkyChartTransformState（scale、offset、northUp）
  ◄── heading（northUp 开启时来自 CompassHeadingSource）

手势 ──► SkyChartTransformState
北向上开关 ──► northUp ──► CompassHeadingSource 开/关
```

## 变换与手势

### 状态模型（`SkyChartTransformState`）

| 字段      | 类型      | 默认值        | 说明                       |
| --------- | --------- | ------------- | -------------------------- |
| `scale`   | `Float`   | `1f`          | 钳位到 `[1f, 4f]`          |
| `offset`  | `Offset`  | `Offset.Zero` | 旋转后的画布坐标系中的平移 |
| `northUp` | `Boolean` | `false`       | 与 scale/offset 相互独立   |

### 手势规则

- **双指缩放**：1×–4×；以捏合中心为锚点
- **单指平移**：仅当 `scale > 1` 时允许；平移需钳位以保证内容可用；`scale == 1` 时强制 `offset = Offset.Zero`
- **双击**：只重置 `scale` 与 `offset`；**不**改变 `northUp`
- **不支持**：惯性滑动、双指旋转、多指旋转图表

### 画布变换顺序

相对中心，按以下顺序应用：

1. `translate(center)`
2. 若 `northUp`：`rotate(-heading)`，使真北始终在屏幕顶部
3. `translate(offset)`
4. `scale(scale)`
5. 绘制圆环、标注、卫星（沿用现有极坐标：方位角 0° = 图表空间顶部为北）

### 坐标系

- **图表空间**：现有极坐标图；N 在图表顶部，E 右，S 下，W 左
- **屏幕空间**：完整变换（含北向上旋转）之后的坐标
- 手势在**旋转后的屏幕坐标**中解释
- 命中检测：点击点经**同一变换链的逆变换**从屏幕映射到图表空间
- N 标注始终表示图表空间中的**真北**（开启北向上时随画布一起旋转）

## 位置动画

### 键

卫星身份键：`constellation` + `svid`（跨 GNSS 更新保持稳定）。

### 运动

| 事件               | 行为                                            | 时长       |
| ------------------ | ----------------------------------------------- | ---------- |
| 位置更新（同一键） | 方位角与高度角线性插值                          | **400 ms** |
| 方位角跨越 0°      | 最短弧插值（如 350° → 10° 走 +20°，而非 −340°） | 同 400 ms  |
| 出现（新键）       | alpha 0 → 1 淡入                                | **300 ms** |
| 消失（键移除）     | 保留最后位置为残影；alpha 1 → 0 淡出后移除      | **300 ms** |
| 整表清空再恢复     | 全部淡出，再按新键淡入；**无**跨键连续性        | 同上       |

### 实现约束

- 仅在 Compose UI 层驱动（`withFrameMillis` 和/或 `Animatable`）
- 仅线性缓动 — 无弹簧曲线
- 无运动拖尾
- 仅对 **alpha > 0.5** 的卫星做命中检测
- 被筛选掉的星座不进入动画器（筛选在上游 `SkyChartScreen` 完成）

## 北向上罗盘

### 语义

当 `northUp == true` 时，图表旋转使**真北始终在屏幕顶部**，与现有 N 标注一致。这**不是**航向上（设备正前方在顶部）。

### 传感器

- 数据源：仅 `Sensor.TYPE_ROTATION_VECTOR`
- **仅**在 `northUp == true` **且**天空图处于前台/已 resume 时注册
- 以下情况立即注销：关闭北向上、离开该屏、应用进入后台
- 恢复时：仅当 `northUp` 仍为 true 时重新注册
- 航向范围：设备方位相对真北，`0f..360f` 度
- 平滑：最短弧更新 + 低通，时间常数约 **100–150 ms**
- 画布使用 `rotate(-heading)`

### 传感器不可用

- 保持 `northUp` 为开启
- 航向固定为 `0f`（表现为假定设备已对齐真北的北向上）
- 无 toast、snackbar 或崩溃

### 开关 UI

- 叠加在天空图画布区域的左上角图标
- 默认：关闭
- 双击重置不影响该开关

## 错误与生命周期边界

| 情况                   | 行为                                                                                |
| ---------------------- | ----------------------------------------------------------------------------------- |
| 无旋转向量传感器       | heading = 0°，northUp 可保持开启，无错误 UI                                         |
| 离开天空图 / 进入后台  | 立即注销传感器                                                                      |
| 恢复时 northUp 仍开    | 重新注册传感器                                                                      |
| scale 回到 1×          | 强制 offset = Zero                                                                  |
| 配置变更（旋转屏幕等） | 通过 `remember` / 已实现的 saved state 保留变换与 northUp；动画可从当前卫星列表重建 |
| 无新权限               | 已确认 — 旋转向量无需权限                                                           |
| 无 ViewModel 改动      | 已确认                                                                              |

## 测试

### 单元测试（JUnit 4，无 mock，领域风格纯函数）

1. **`SkyChartTransformState`**
    - scale 钳位到 `[1, 4]`
    - scale 为 1 时平移强制为零
    - `resetScaleAndOffset()` 清除 scale/offset，但不动 `northUp`
2. **方位角最短弧**辅助函数（动画使用）
3. **逆变换命中检测**往返（屏幕点 → 图表 → 屏幕）在浮点误差内一致

### 不单测

- Compose 多点触控手势
- 硬件 `TYPE_ROTATION_VECTOR` 读数
- 逐帧 `withFrameMillis` 动画
- 截图 / 视觉回归

### 手工验证

1. 双指缩放 1×–4×；双击回到 1×；1× 时不可平移
2. 卫星移动平滑；出现/消失有淡入淡出
3. 北向上开启：旋转设备时真北保持在顶部；关闭：图表固定
4. 组合：缩放/平移 + 选中卫星 + 北向上
5. 离开页面 / 后台：传感器停止（无持续占用）

### 构建检查

```bash
./gradlew ktlintCheck
./gradlew test
./gradlew assembleDebug
```

## 自审

- **占位符扫描**：无 TBD/TODO。
- **一致性**：画布顺序、手势坐标、逆变换命中、N 标注语义在各节一致。
- **范围**：仅缩放/平移 + 动画 + 北向上；截图与 ViewModel 改动排除。
- **歧义**：北向上 = 真北固定在顶部（非航向上）；双击不重置 northUp；无传感器 → heading 0，无错误 UI。

## 与既有规格的关系

- 基于 `2026-03-28-sky-chart-design.md`（基础极坐标天空图）
- 基于 `2026-07-12-sky-chart-interaction-design.md`（Phase 1：SVID 标注 + 星座筛选）
- 不重开 Phase 1 已决事项
