# 确立 GNSS 重构方向

状态：已接受

本项目继续采用手动 DI、Clean MVVM 和单向数据流。本次决定优先收敛已经造成重复平台注册、状态顺序泄漏或数据迁移风险的实现；不以替换框架、按文件拆分或增加无真实变化轴的 interface 作为重构目标。

## 背景

架构审查发现，GNSS 导航电文存在多个 cold `Flow` collector，可能重复注册 Android callback；`SatelliteViewModel` 同时持有采集、定位解算、TTFF、趋势、RINEX 和历史快照职责；A-GPS 的初始化顺序由前台与后台 caller 共同承担；历史迁移策略分散在 Room、旧 DataStore 和组合根中。这些问题降低了 locality，也让 interface 难以成为稳定的测试面。

## 决策

### P0：建立单次注册的 GNSS acquisition session

GNSS 平台 callback 的注册、注销、事件融合和共享语义必须由一个 deep module 集中持有。所有卫星、导航电文、NMEA 和天线信息的 consumer 都通过同一个 session 获取数据；不得因新增 screen 或 ViewModel 再次注册同类平台 callback。

该 module 的 implementation 可以保留 Android adapter 与内部纯事件融合逻辑，但调用方不应了解 measurement 与 status 的配对顺序、API 分支、listener 生命周期或 freshness 规则。

验收条件：

- 同一应用进程中，导航电文只存在一次有效的平台注册。
- 取消最后一个 consumer 后，相关 listener 被注销。
- 事件融合规则可在不依赖 Android runtime 的测试中覆盖。

### P1：收窄 SatelliteViewModel 的 ownership

`SatelliteViewModel` 保留卫星监控的 presentation projection，但不再直接拥有本地定位解算、星历来源合并、TTFF、信号/DOP 缓冲、RINEX 记录和快照节流的全部 implementation。

本地定位与星历流程应形成独立的定位 session；TTFF、趋势、RINEX 与历史快照应形成独立的监控与 recording module。它们可以共享 GNSS acquisition session，但各自拥有 clock、生命周期、失败降级和持久化顺序。

验收条件：

- `startListening()` 或等价重启动作会建立新的 TTFF 起点。
- 定位解算可用 frame、clock 和星历 fixture 做端到端单元测试。
- 自动快照、clock jump 与重启后的记录行为有专门测试。
- 各 screen 仅消费其需要的 projection 和 action。

### P1：将 A-GPS 操作视为 transaction

A-GPS 的 source selection、下载/文件验证、注入、注入记录、状态更新和 readiness 必须在一个 transaction module 内保持一致。`hydrateHistory()` 不再是 UI 或 Worker 必须记住的前置调用；前台和后台 caller 只触发高层操作并接收 durable outcome。

WorkManager 的调度策略与 operation state 归属同一 workflow；UI 不再把持久 `StateFlow` 当作一次性 message 后再调用清理方法。

验收条件：

- 前台操作和 Worker 不依赖调用 `hydrateHistory()` 的先后顺序。
- 未预期异常不会使操作永久停留在下载或注入状态。
- 回退下载、验证失败、注入失败、并发操作和历史写入竞争均有测试。

### P1：集中卫星历史的 migration policy

卫星历史的 snapshot materialization、retention、Room schema migration 和旧 DataStore 导入必须由同一个历史持久化 module 决定。Room 与 legacy JSON 仅作为内部 adapter；组合根不承担 migration 顺序。

不得继续使用会清空 Room 数据、同时又受持久化“已迁移”标记约束的 destructive migration 组合。下一次 schema 演进前必须明确保留数据、重导入或显式弃置数据的策略。

验收条件：

- schema 变更后的 reopen 行为有测试。
- legacy marker 与 Room 数据状态不会产生不可恢复的不一致。
- retention 和最大快照数只在一个 implementation 中定义。

### P2：随后收敛 export 与 navigation shell

CSV、NMEA 与 RINEX 的 cache、FileProvider、URI 权限、share intent 和失败分类应收敛为 export module；各格式 writer 保留自己的领域语义。导航的 destination metadata、drawer、NavHost 和 back/drawer policy 应从 `MainActivity` 的多处维护收敛为一个 navigation shell policy。

这两项不阻塞 P0/P1，因为当前没有已确认的数据一致性或重复平台注册风险。

## 明确不做

- 不引入 Hilt、Dagger 或 Koin。`AppDependencies` 仍是应用级组合根，已有真实的测试 seam。
- 不按 NMEA、导航电文、天线信息继续拆分 `GnssRepository`。当前拆分只会增加 single-adapter 的 shallow seam。
- 不创建全局 Formatting module。现有时间、定位和状态文案具有不同产品语义。
- 不把本 ADR 当作 interface 设计。每个 P0/P1 项在实现前应单独记录不可逆的 module、seam 或存储决策。

## 考虑过的选项

1. 仅拆分大文件：拒绝。文件变小不会自动提高 locality，也无法消除跨 caller 的生命周期规则。
2. 立即引入 DI 框架：拒绝。当前问题在 GNSS ownership、事务顺序与 migration policy，而不在组合根是否自动生成。
3. 将所有候选一次实现：拒绝。P0/P1 需要逐项形成独立设计和测试面，避免在共享数据管道中同时改变多个不变量。

## 后果

- 后续实现会新增少量 deep module，但目标是收窄调用方需要了解的 interface，而不是增加抽象层数。
- `docs/ARCHITECTURE.md` 应在第一个 P0/P1 实施完成后更新，替换仍描述旧 DataStore 历史路径的内容。
- 需要为 GNSS lifecycle、TTFF/recording、A-GPS transaction 和历史迁移补充针对性测试；不以全量 Compose 测试作为前置条件。
