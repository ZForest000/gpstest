# 统一卡片视觉语言设计

**日期：** 2026-07-19
**状态：** 已获设计批准，待书面规格审查

## 目标

为 GPS Test 的信息卡与紧凑列表卡建立一套低调的色调层级。所有卡片使用相同的圆角、背景层级和阴影策略；定位、信号与 A-GPS 等业务状态只影响局部文字、图标或指示点，不再改变整张普通信息卡的底色。

## 非目标

- 不改变 GNSS、A-GPS、历史或导航的业务逻辑与状态机。
- 不重写页面布局、卡片中的字段顺序或既有的点击行为。
- 不更换全局色板、字体或 Material 3 主题配置。
- 不将图表、筛选控件、底部弹窗和按钮纳入本次卡片规范。

## 设计原则

选择已确认的 A 方案「低调色调层级」：

1. 卡片通过同一层级的 `surfaceVariant` 背景与留白组织信息，而不是依赖明显描边或阴影。
2. 一般信息卡的所有视觉参数由一个可复用组件提供，避免各文件自行指定 `CardDefaults`、`RoundedCornerShape` 和 `padding`。
3. 紧凑的卫星行与常规信息卡属于同一设计系统，只在内容密度上不同。
4. 成功、警告、错误、信号质量和星座身份仍保留现有颜色语义，但颜色只出现于图标、状态文字、进度条或小圆点。

## 视觉规范

| 类型 | 容器色 | 圆角 | 阴影 | 内容内边距 | 适用场景 |
| --- | --- | --- | --- | --- | --- |
| 标准信息卡 | `MaterialTheme.colorScheme.surfaceVariant` | 12 dp | 0 dp | 16 dp | 定位、DOP、A-GPS、诊断、设置、历史详情 |
| 紧凑列表卡 | `MaterialTheme.colorScheme.surfaceVariant` | 12 dp | 0 dp | 12 dp | 单颗卫星、列表内的短摘要 |

卡片内部的标题与主体信息保持当前的排版层级。标题行与内容区的默认垂直间距为 8 dp；已有为密集数据服务的较小间距可保留，但不能单独改用其他圆角、底色或阴影。

### 状态表达

- `LocationCard` 不再因已定位使用 `primaryContainer`；「已定位」状态继续使用主色文字。
- `AGpsStatusCard`、`DopCard`、诊断卡和设置卡统一使用标准信息卡容器；各状态行继续使用既有的成功、警告、错误色。
- `SatelliteCard` 的已用于定位、信号强度和多路径状态继续通过指示符与文字表达，不再作为与其余卡片不同的矩形背景实现。
- 明确的错误结果仍可保留 `errorContainer` 语义；这种异常反馈不是普通信息卡样式的例外。

## 组件边界

在 `ui/components/` 新增 `GpsCard`，作为普通卡片的唯一入口。它封装 Material 3 的 shape、颜色、elevation 与两档 content padding；调用方只选择标准或紧凑密度，并提供内容。需要点击的卫星行使用同一组件提供的可点击变体，以保留当前的 `onClick` 行为与无障碍语义。

`GpsCard` 不负责标题、状态或业务配色。现有业务组件继续拥有自己的数据展示与状态语义，避免把与样式无关的逻辑集中到基础卡片中。

## 迁移范围

- 新建：`app/src/main/java/com/example/gpstest/ui/components/GpsCard.kt`
- 修改：`SatelliteCard.kt`、`LocationCard.kt`、`LocalPositionCard.kt`、`DopCard.kt`、`TtffCard.kt`
- 修改：`AGpsStatusCard.kt`、`ClockInfoCard.kt`、`GnssCapabilitiesCard.kt`、`AntennaInfoCard.kt`、`HistorySnapshotCard.kt`
- 修改：`app/src/main/java/com/example/gpstest/ui/screens/agps/AGpsManagerScreen.kt`
- 修改：`app/src/main/java/com/example/gpstest/ui/screens/settings/SettingsScreen.kt`
- 修改：`app/src/main/java/com/example/gpstest/ui/screens/nmea/NmeaScreen.kt`
- 修改：`app/src/main/java/com/example/gpstest/ui/screens/navigation/NavigationMessageScreen.kt`
- 修改：`app/src/main/java/com/example/gpstest/ui/screens/help/HelpScreen.kt`

星座统计、健康摘要、历史趋势和信号柱图内嵌的小型视觉块不属于常规信息卡，本次不强制改造它们。

## 验收与测试

- 新增 Compose UI 测试：标准和紧凑 `GpsCard` 都能渲染内容；可点击变体触发一次传入的回调。
- 对所有迁移页面进行人工浅色与深色主题检查：卡片圆角为 12 dp、无可见投影、信息卡内边距为 16 dp、紧凑卫星行内边距为 12 dp。
- 已定位、缺失 A-GPS、低信号、多路径和 DOP 质量状态仍通过局部元素清楚区分。
- 运行 `./gradlew test`、`./gradlew ktlintCheck` 与 `./gradlew assembleDebug`，确保样式迁移未影响构建或既有单元测试。

## 不变量

1. 普通信息卡不得直接设置 `primaryContainer`、自定义 elevation 或非 12 dp 圆角。
2. 普通卡片的容器视觉配置只能在 `GpsCard` 中定义。
3. 业务状态不会改变普通卡片的整体背景色。
4. 卫星卡点击、A-GPS 操作、设置开关和历史交互的行为保持不变。
