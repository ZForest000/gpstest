# 卫星历史 migration policy 设计

**状态：** 已获设计批准，待书面规格审查

## 目标

将卫星历史的 legacy JSON 导入、Room schema migration、reopen、retention 与清除行为集中到一个持久化 module。任何 schema 变化都不得静默丢弃历史；失败必须保留可恢复的数据并允许下一次启动重试。

## 范围

- 保留现有 `SatelliteHistoryRepository` 作为调用方的 interface；screen 和 `SatelliteViewModel` 不了解 legacy marker、Room version 或迁移顺序。
- 让历史持久化 module 成为唯一决定 snapshot materialization、legacy 导入、retention、Room migration 与 clear policy 的地方。
- 将 Room schema 从 v1 演进到 v2，注册显式 `Migration(1, 2)`，并删除 destructive migration fallback。
- 为 v2 新增 Room metadata table，保存 legacy import 的完成状态；它是跨启动的权威记录。
- 将 `SatelliteHistoryDataStore` 缩为 legacy JSON adapter：读取旧快照、读取/写入旧 marker、清除旧 JSON。它不再执行 retention、snapshot 保存或作为当前历史的读模型。
- 更新架构文档，使历史数据流指向 Room-backed persistence module。

不在本次范围内：变更历史 screen、导出历史、迁移到新的 DI 框架，或清理与历史无关的 ViewModel 职责。

## 方案与理由

采用“数据保留 + 显式 migration”而非 destructive migration 或自动弃置：

1. Room 以 v1 → v2 的显式 migration 打开旧数据库；该 migration 只创建 metadata table，不触碰已有 snapshot / satellite rows。
2. 历史持久化 module 在 Room transaction 内写入 legacy snapshots、metadata 与 retention 结果；transaction 成功后才更新 DataStore 的旧 marker。
3. 如果 transaction 失败，metadata 和 Room snapshot 都回滚，旧 JSON 与旧 marker 保持不变；下次 open 会重试。
4. 如果 app 在 Room transaction 成功后、写旧 marker 前中断，Room metadata 已说明导入完成；下次 open 不重复插入，并补写旧 marker。
5. 对旧版本遗留的“marker=true、Room 为空、legacy JSON 仍有数据”组合，优先恢复 legacy JSON 至空 Room。该一次性保守恢复可能复活旧版本已清除的历史，但不会静默丢失数据；从 v2 起，clear 同时清除 Room 与 legacy adapter，组合不再出现。

## Module 设计

### `SatelliteHistoryPersistence`（deep module）

它是 `SatelliteHistoryRepositoryImpl` 的唯一 storage dependency，interface 只暴露：

```kotlin
val snapshots: Flow<List<SatelliteHistorySnapshot>>
suspend fun save(snapshot: SatelliteHistorySnapshot)
suspend fun delete(timestamp: Long)
suspend fun clear()
```

在第一次读或写前，module 执行 `ensureReady()`：打开已注册 migration 的 Room database、检查 metadata、读取 legacy adapter、以 transaction 导入或恢复、写 metadata、应用 retention，并在 Room 成功后同步旧 marker。调用方不管理 migration 顺序，也不接触 migration state。

内部 seam：

- `RoomSatelliteHistoryStore` 是 Room adapter，持有 DAO、`Migration(1, 2)` 和 metadata DAO。
- `SatelliteHistoryDataStore` 是 legacy JSON adapter，只保留兼容旧安装所需的读取、marker 与 purge 操作。
- `SatelliteHistoryPersistence` 协调这两个 adapter 和 `SettingsStore`；它拥有 `Mutex`，确保首次导入、save、delete、clear 的顺序与失败语义集中。

`AppDependencies` 只构造 `SatelliteHistoryPersistence`，不承担任何 migration 判断。

## 数据与错误语义

| 场景 | 结果 |
| --- | --- |
| 首次运行，legacy JSON 有快照 | transaction 导入、记录 metadata、再写旧 marker |
| 正常 reopen | metadata 已完成，不读取/重复导入 legacy JSON |
| v1 Room 升级到 v2 | 显式 migration 保留所有 v1 row，再按 metadata 继续准备 |
| Room transaction 失败 | 不更新 marker，不清旧 JSON；下次 open 重试 |
| 已迁移 marker=true、Room 为空、legacy JSON 非空 | 一次性恢复 legacy JSON，避免 destructive migration 遗留的数据丢失 |
| 用户清空历史 | 清 Room、legacy JSON 与 metadata 的可恢复来源；不会在下次 open 复活数据 |
| retention / maxSnapshots | 仅 `SatelliteHistoryPersistence` 在成功写入后应用一次 |

公开 repository 的失败模型暂不扩展为新的 UI state：Room open 或 transaction 异常会保留数据并向当前 coroutine 传播。现有 caller 的错误处理不变；后续若要展示恢复提示，另立 UI 规格。

## 验收与测试

### JVM 测试

- legacy 首次导入后，reopen 不会重复插入。
- Room transaction 失败后，legacy marker 未写入，legacy JSON 保留，后续调用可重试。
- marker=true、空 Room、legacy JSON 非空时恢复 legacy snapshots。
- clear 后 reopen 不会恢复已清除的历史。
- retentionDays 与 maxSnapshots 仅由 persistence module 应用。

### Room migration/reopen 测试

- 使用 `room-testing` 的 `MigrationTestHelper` 建立 v1 数据库，运行 `Migration(1, 2)` 后 reopen，验证 snapshot 与 satellite rows 都仍可读。
- 测试仅创建 metadata table 的 v1 → v2 migration，不调用 destructive fallback。
- Android instrumentation 环境可用时运行该测试；至少将 Android test APK 编译进 CI 可验证的构建产物。

## 文件影响

- 修改：`app/src/main/java/com/example/gpstest/data/local/RoomSatelliteHistoryStore.kt`
- 修改：`app/src/main/java/com/example/gpstest/data/local/SatelliteHistoryDataStore.kt`
- 新建：`app/src/main/java/com/example/gpstest/data/local/SatelliteHistoryPersistence.kt`
- 修改：`app/src/main/java/com/example/gpstest/data/local/db/SatelliteHistoryDatabase.kt`
- 修改：`app/src/main/java/com/example/gpstest/data/local/db/SatelliteHistoryDao.kt`
- 修改：`app/src/main/java/com/example/gpstest/domain/repository/SatelliteHistoryRepositoryImpl.kt`
- 修改：`app/src/main/java/com/example/gpstest/AppDependencies.kt`
- 修改：`app/build.gradle.kts`、`gradle/libs.versions.toml`
- 新建/修改：对应 JVM 与 Android instrumentation tests
- 修改：`docs/ARCHITECTURE.md`

## 不变量

1. 不使用 `fallbackToDestructiveMigration()`。
2. marker 绝不先于成功的 Room transaction 写入。
3. 一次 schema migration 不删除已有 Room 历史。
4. retention 与最大快照数只由一个 implementation 定义。
5. caller 不需要知道 legacy import、Room version 或 reopen 顺序。
