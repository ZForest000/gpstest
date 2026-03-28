# 卫星天空图设计规格

## 概述

为 GNSS 测试应用新增卫星天空图（Sky Chart）页面，以极坐标图形式实时展示卫星在天穹上的空间分布。同时将应用导航从顶部图标改为侧拉菜单（Navigation Drawer）。

## 1. 导航结构改造

### 当前状态
- 顶部 TopAppBar 图标导航到 A-GPS 和历史页面
- `Screen` sealed class 定义 3 个路由：`SatelliteList`、`History`、`AGps`

### 目标状态
- 引入 `ModalNavigationDrawer`，菜单项：
  - 卫星列表（默认页）
  - 天空图
  - A-GPS 管理
  - 历史记录
- 新增路由 `Screen.SkyChart("sky_chart")`
- SatelliteListScreen 顶部栏简化为汉堡菜单图标，点击触发 Drawer 打开
- Drawer 菜单项带图标和文字，当前页面高亮
- 选择菜单项后自动关闭 Drawer 并导航到对应路由

## 2. 天空图页面布局

### TopAppBar
- 标题"天空图"
- 左侧汉堡菜单图标（触发 Drawer）

### 天空图主体（Canvas 绘制）
- 浅灰背景圆形区域
- 4 个同心虚线圆环，代表仰角 0°、30°、60°、90°（外圈=地平线，圆心=头顶正上方）
- 十字线标示方位，N 在顶部，顺时针为 E/S/W
- 卫星点按星座颜色填充：
  - GPS → 蓝 (#1976D2)
  - BDS → 黄 (#FBC02D)
  - GLONASS → 红 (#D32F2F)
  - Galileo → 青 (#00796B)
  - QZSS → 紫 (#7B1FA2)
  - SBAS → 灰
- 参与定位的卫星：实心圆 + 加粗边框
- 未定位的卫星：较淡颜色 + 细边框
- 卫星点大小根据信号强度(C/N0)微调，信号越强点越大

### 底部图例栏
- 水平排列各星座色块 + 名称
- 实心圆 = 参与定位，空心圆 = 仅可见

### 点击交互
- 点击卫星点弹出 `ModalBottomSheet`
- 显示：星座类型、卫星ID、信号强度(C/N0)、方位角、仰角、定位状态、星历/历书数据状态

## 3. 数据流与组件划分

### 数据复用
- 天空图共享 `SatelliteViewModel`，不新建 ViewModel
- 从 `SatelliteUiState.Success` 读取卫星列表
- 所需字段：`azimuthDegrees`、`elevationDegrees`、`constellation`、`usedInFix`、`cn0DbHz`

### 新增文件
| 文件 | 职责 |
|------|------|
| `ui/screens/skychart/SkyChartScreen.kt` | 页面入口：Scaffold + TopAppBar + 天空图 + 图例 |
| `ui/screens/skychart/SkyChartView.kt` | Canvas 天空图核心绘制组件 |
| `ui/screens/skychart/SkyChartLegend.kt` | 底部星座图例栏 |
| `ui/screens/skychart/SatelliteDetailSheet.kt` | 点击卫星弹出的详情底部 Sheet |

### 修改文件
| 文件 | 改动 |
|------|------|
| `MainActivity.kt` | 添加 Drawer 导航、`Screen.SkyChart` 路由、传入 SatelliteViewModel |
| `SatelliteListScreen.kt` | 顶部图标简化为汉堡菜单按钮 |

### 坐标映射算法
- 方位角 → Canvas 角度：N=上方，`canvasAngle = azimuthDegrees - 90°`，再转为 `(x, y)`
- 仰角 → 半径：90°=圆心(0半径)，0°=外圈(最大半径)，`radius = (1 - elevationDegrees / 90) * maxRadius`

## 4. 技术选型

- 绘制：Jetpack Compose `Canvas` API（项目已有使用经验 — SignalChart）
- 导航：`ModalNavigationDrawer`（Material3）
- 无需引入第三方图表库
