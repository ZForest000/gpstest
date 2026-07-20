# GNSS 调试工具：当前状态与待办

> **更新日期**：2026-07-20
>
> **用途**：本文件是剩余工作和验收状态的唯一入口。实现方案、已完成任务的过程记录保留在 `docs/superpowers/` 与 `CHANGELOG.md`，不在此重复。
> **维护规则**：功能开始前在此登记；完成后更新本文件、补充 `CHANGELOG.md`，并移除过时的方案描述。

---

## 当前结论

当前产品主功能已完成：卫星监控、天空图、A-GPS、NMEA、历史趋势与导出、GPS L1 C/A 本地定位、RINEX 导出、导航电文、天线信息、Room 历史存储，以及工程化基线均已落地。

当前没有阻塞发布的已知功能缺口。后续工作分为一个已设计但未实现的诊断增强、可选体验增强和真机/质量验证。

## 活跃功能待办

### G6 扩展：GNSS 能力声明与实测对照（P2，工作量：中）

**状态**：设计与实施计划已创建，尚未实现。

**目标**：在接收机诊断页对原始测量、导航电文和天线信息同时展示设备声明能力、30 秒观察窗内的实测结果与诊断说明，帮助判断数据为空是设备不支持、权限/省电限制、天空视野不足，还是数据链路问题。

**范围**：

- 领域层 `CapabilityProbeEvaluator` 根据声明能力和会话证据生成诊断行。
- `SatelliteViewModel` 在监听会话中积累测量、导航电文和天线信息的观察证据。
- `GnssCapabilitiesCard` 显示「声明 | 实测」与本地化诊断文案。
- 不新增平台回调、页面或导航入口；ADR、测量改正和相关向量保持仅展示声明状态。

**验收标准**：

- 三项能力均能显示声明状态、实测状态和诊断。
- 实测窗口固定为 30 秒；重新开始监听时重置证据。
- 规则由纯领域单元测试覆盖，中英文资源同步。
- `testDebugUnitTest` 与 `:app:compileDebugKotlin` 通过。

**参考**：[设计](docs/superpowers/specs/2026-07-19-gnss-capability-probe-design.md) 与 [实施计划](docs/superpowers/plans/2026-07-19-gnss-capability-probe.md)。

## 可选体验增强

| 项目 | 优先级 | 说明 |
| --- | --- | --- |
| 设置页补充 | P3 | 增加清除信号历史按钮与 Material You 动态配色开关。 |
| 天空图截图分享 | P3 | 将当前天空图导出为图片并通过系统分享。 |
| 天线信息扩展 | P3 | 在有明确专业需求时保留完整 PCV 网格与 `SphericalCorrections` 信号增益表，并供 RINEX 头部消费。 |

这些项目不影响现有诊断流程，不应阻塞版本发布。

## 验证与质量待办

| 项目 | 优先级 | 验收内容 |
| --- | --- | --- |
| GNSS 真机验证 | P1 | 在支持设备上验证 GPS L1 C/A 本地定位、导航电文、天线 PCO/PCV 与能力探测的实际输出。 |
| RINEX 兼容性 | P1 | 使用 RTKLIB、Bernese 或 PPP 工具验证导出的观测文件可被读取并给出可解释结果。 |
| 历史迁移回归 | P1 | 验证旧 JSON 数据迁移到 Room、重新打开应用、清理与保留策略。 |
| Release 冒烟 | P2 | 在开启 R8 的签名 Release 上验证 A-GPS、历史、Shizuku/root 增强链路；发布前补齐签名配置。 |
| 自动化测试边界 | P2 | 为 `AGpsUpdateWorker` 引入可测试的依赖注入方式，并以 Robolectric 或薄适配层覆盖 `GnssPlatformSourceImpl`。 |
| CI 质量增强 | P3 | 视项目需要增加覆盖率报告、instrumented emulator 任务与 Detekt。 |
| 国际化完善 | P3 | 补齐 Downloader/Validator 错误串和帮助长文案的英文资源。 |

## 已完成范围

| 类别 | 已完成能力 |
| --- | --- |
| 数据采集与修复 | Shizuku/root `dumpsys` 通路、完整载波相位周期、NMEA 监听/解析/导出。 |
| 专业 GNSS | 伪距推导与 GPS L1 C/A 本地定位、RINEX 3.x 导出、导航电文、天线相位中心、TDOP/GDOP、设备能力查询、闰秒与 Location 精度字段。 |
| 用户体验 | 设置、交互式天空图、历史趋势/CSV/详情、卫星筛选/排序/冻结、A-GPS 文件导入与配置、C/N0 柱状图与 DOP 趋势。 |
| 工程化 | Release R8/ProGuard、CI、Timber、ViewModel/Repository 测试、i18n 基线、Version Catalog、Room 历史迁移、贡献与架构文档。 |

## 明确不在当前范围

- Firebase Crashlytics、Sentry 等远端崩溃上报。当前仅使用 Timber；如需接入，单独评审隐私、成本和发布流程。
- Hilt/Dagger/Koin 迁移、产品风味与多模块拆分。现有手动 DI 满足当前规模。
- 不存在的 Android 平台伪距 getter。伪距必须由 `GnssClock` 与 `GnssMeasurement` 推导。

## 状态维护约定

1. 本文件只描述现在仍需决策、实现或验证的内容，不保留已完成项的旧问题、旧方案或历史路线图。
2. 详细设计和逐步实施清单放入 `docs/superpowers/specs/`、`docs/superpowers/plans/`；其中的复选框不替代本文件的状态。
3. 标记完成前必须对照当前代码和验证结果；完成后的用户可见变化写入 `CHANGELOG.md`。
