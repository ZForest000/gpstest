# 卫星天空图设计规格

## 概述

为 GNSS 测试应用新增卫星天空图（Sky Chart）页面，以极坐标图形式实时展示卫星在天穹上的空间分布。同时将应用导航从顶部图标改为侧拉菜单（Navigation Drawer）。

## 1. 导航结构改造

### 当前状态
- 顶部 TopAppBar 图标导航到 A-GPS 和历史页面
- `Screen` sealed class 定义 3 个路由：`SatelliteList`、`History`、`AGps`（均为 `object` 子类）

### 目标状态
- 引入 `ModalNavigationDrawer`，菜单项：
  - 卫星列表 → `Icons.Default.SatelliteAlt`（默认页）
  - 天空图 → `Icons.Default.Explore`
  - A-GPS 管理 → `Icons.Default.CloudDownload`
  - 历史记录 → `Icons.Default.History`
- 新增路由 `object SkyChart : Screen("sky_chart")`
- SatelliteListScreen 顶部栏简化为汉堡菜单图标（`Icons.Default.Menu`），点击触发 Drawer 打开
- Drawer 菜单项带图标和文字，当前页面高亮
- 选择菜单项后自动关闭 Drawer 并导航到对应路由
- 注：当前应用并无底部导航栏，Drawer 直接替代顶部图标导航

## 2. 天空图页面布局

### TopAppBar
- 标题"天空图"
- 左侧汉堡菜单图标（触发 Drawer）

### UI 状态处理
天空图页面需处理 `SatelliteUiState` 的所有变体：
- `Loading` → 居中显示加载指示器（CircularProgressIndicator）
- `PermissionRequired` → 显示权限请求提示（与卫星列表页一致）
- `Error` → 显示错误信息
- `Success` → 显示天空图
  - 卫星列表为空 → 显示空状态圆环 + 提示文字"等待卫星信号..."

### 天空图主体（Canvas 绘制）
- **尺寸**：正方形，宽度填满屏幕可用空间（减去水平 padding 32dp），保持 1:1 宽高比
- **背景**：浅灰（Light Theme: `#F0F0F0`，Dark Theme: `#2A2A2A`），圆形裁剪
- 4 个同心虚线圆环，代表仰角 0°、30°、60°、90°（外圈=地平线，圆心=头顶正上方）
- 圆环旁标注仰角值（30°、60°，外圈和圆心可省略）
- 十字线标示方位，N 在顶部，顺时针为 E/S/W，方位字母标注在圆环外侧

#### 卫星点绘制规则
- **仅绘制有有效方位角/仰角的卫星**：`azimuthDegrees > 0 || elevationDegrees > 0`。SEARCHING 状态卫星（`cn0DbHz <= 0`）若方位角和仰角均为 0 则不绘制，避免在 N 方位外圈堆积无意义点
- 按星座颜色填充：
  - GPS → 蓝 (#1976D2)
  - BEIDOU → 黄 (#FBC02D)（显示名"BDS"）
  - GLONASS → 红 (#D32F2F)
  - GALILEO → 青 (#00796B)
  - QZSS → 紫 (#7B1FA2)
  - SBAS → 灰
  - UNKNOWN → 浅灰 (#BDBDBD)
- 参与定位的卫星（`usedInFix = true`）：实心圆 + 加粗边框（2dp）
- 未定位的卫星：颜色 alpha 降为 0.5 + 细边框（1dp）
- **点大小**：基础半径 6dp，根据 C/N0 线性映射到 5dp~10dp（映射范围 C/N0 0~50 dB-Hz，`radius = 5 + (cn0DbHz / 50) * 5`）
- **点击检测半径**：20dp（大于视觉半径，确保可用性），遍历卫星点计算点击距离，取最近且在检测半径内的卫星

### 底部图例栏
- 水平排列各星座色块 + 名称，使用 `FlowRow` 避免窄屏溢出
- 实心圆 = 参与定位，空心圆 = 仅可见
- 每个图例项高度 24dp

### 点击交互
- 点击卫星点弹出底部 `ModalBottomSheet`
- **复用现有** `ui/components/SatelliteDetailSheet.kt` 组件，不新建
- 传入被点击的 `GnssSatellite` 对象

### 暗色主题适配
- 图表背景：`#2A2A2A`
- 圆环和十字线颜色：`rgba(255,255,255,0.2)`
- 方位文字：`rgba(255,255,255,0.6)`
- 卫星点颜色不变，未定位卫星 alpha 降为 0.35

### 无障碍
- Canvas 组件设置 `contentDescription = "卫星天空图，显示 ${卫星数} 颗卫星的位置分布"`
- 卫星点点击区域 ≥ 48dp（通过 20dp 检测半径满足）

## 3. 数据流与组件划分

### 数据复用
- 天空图共享 `SatelliteViewModel`（scoped to Activity），不新建 ViewModel
- 从 `SatelliteUiState.Success` 读取卫星列表
- 所需字段：`azimuthDegrees`、`elevationDegrees`、`constellation`、`usedInFix`、`cn0DbHz`
- 由于 NavHost 同一时间只组合一个页面，两个屏幕不会同时操作 ViewModel 的选中状态

### 新增文件
| 文件 | 职责 |
|------|------|
| `ui/screens/skychart/SkyChartScreen.kt` | 页面入口：Scaffold + TopAppBar + 天空图 + 图例 + 状态处理 |
| `ui/screens/skychart/SkyChartView.kt` | Canvas 天空图核心绘制组件（纯绘制，接收卫星列表参数） |
| `ui/screens/skychart/SkyChartLegend.kt` | 底部星座图例栏（FlowRow 布局） |

### 修改文件
| 文件 | 改动 |
|------|------|
| `MainActivity.kt` | 添加 Drawer 导航、`Screen.SkyChart` 路由、传入 SatelliteViewModel |
| `SatelliteListScreen.kt` | 顶部图标简化为汉堡菜单按钮（删除原有 A-GPS/历史图标） |

### 坐标映射算法
- 方位角 → Canvas 角度：N=上方（0° azimuth → canvas 角度 270° → 顶部），公式：
  ```
  canvasAngleRadians = Math.toRadians(azimuthDegrees - 90.0)
  x = centerX + radius * cos(canvasAngleRadians)
  y = centerY + radius * sin(canvasAngleRadians)
  ```
- 仰角 → 半径：90°=圆心(0半径)，0°=外圈(最大半径)
  ```
  radius = (1.0 - elevationDegrees / 90.0) * maxRadius
  ```
- 边界处理：若 `elevationDegrees < 0`，clamp 到 0（放在外圈）

## 4. 技术选型

- 绘制：Jetpack Compose `Canvas` API（项目已有使用经验 — SignalChart）
- 导航：`ModalNavigationDrawer`（Material3）
- 布局：`FlowRow`（图例栏自适应换行）
- 无需引入第三方图表库
