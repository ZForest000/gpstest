# 卫星监控抽屉拆分设计

**日期**: 2026-07-18
**状态**: 已确认，开始实现

## 目标

降低实时卫星监控的首屏信息密度。将原本集中在 `SatelliteListScreen` 的总览、列表、定位精度和接收机诊断拆成抽屉目的地，同时继续使用同一个 `SatelliteViewModel` 提供实时数据。

## 非目标

- 不改变 GNSS 数据采集、DOP 计算、历史保存或 RINEX 录制。
- 不拆分 `SatelliteViewModel`，避免多个页面重复订阅与状态不一致。
- 不修改既有的天空图、A-GPS、历史、NMEA 和导航电文的业务行为。

## 抽屉信息架构

抽屉按以下顺序分组，其中“总览”是启动页和导航根路由。

### 实时监控

1. **总览**：已用/可见/总数、定位状态、TTFF、PDOP、星座统计、信号柱图、星座健康摘要。定位与 DOP 仅以紧凑摘要展示。
2. **卫星列表**：筛选、排序、冻结列表、三种卫星分组与卫星详情抽屉；不再显示总览或诊断卡片。
3. **天空图**：保留既有独立屏幕。
4. **定位与精度**：完整位置、TTFF、完整 DOP、DOP 趋势和本地 GPS 解算结果。
5. **接收机诊断**：GNSS 时钟、能力、天线相位中心和 RINEX 导出操作。

### 数据与工具

保留 A-GPS、历史、NMEA 和导航电文。帮助与设置置于抽屉底部。分组标题与分隔线仅组织导航，不改变路由语义。

## 页面与状态边界

- `SatelliteOverviewScreen` 只读取总览所需的 `uiState`、TTFF；PDOP 空值与未定位状态必须清楚显示。
- `SatelliteListScreen` 保留筛选、排序、冻结和卫星详情。冻结限定为列表快照，其他页面持续读取实时流。
- `PositioningScreen` 使用现有位置、DOP、DOP 历史和本地解算状态。
- `ReceiverDiagnosticsScreen` 使用时钟、dumpsys、能力、天线和 RINEX recorder；导出入口从列表顶栏移入此页。
- 共享的 `SatelliteStateContent` 处理 Loading、权限缺失和错误，所有新页面的提示与重试行为一致。

## 导航与返回

- 新增 `Screen.Overview`，将 `NavHost.startDestination` 设为该路由。
- 抽屉导航的 `popUpTo` 根路由同步改为 `Overview`，保留 `saveState`、`restoreState` 和 `launchSingleTop`。
- 抽屉条目按当前路由高亮；总览、卫星列表、天空图、定位与精度、接收机诊断均可直接打开抽屉。

## 验收标准

- 启动应用首先显示总览，抽屉第一项为总览。
- 卫星列表首屏仅包含筛选控件和列表，不再需要滚过诊断卡片才能查看卫星。
- 原有总览与诊断能力均能在对应页面找到，RINEX 能从接收机诊断页导出。
- 权限拒绝、加载中、GNSS 错误和空天线信息的行为与改造前一致。
- `test`、`ktlintCheck` 与 `assembleDebug` 通过。
