# 集中应用依赖组合设计

## 目标

将当前分散在 `MainActivity` 与 `AGpsUpdateWorker` 的手动依赖组合集中到 `GpsTestApplication`，使同一进程中的调用方复用同一组应用级依赖。

## 现状

`MainActivity` 与 `AGpsUpdateWorker` 曾各自组合所需依赖，分别维护 GNSS、历史、设置与 A-GPS 链的构造顺序和依赖不变式。

## 方案比较

1. 引入 Hilt：功能完整，但会扩大构建配置、注解处理和迁移范围，不适合本次收敛重复组合的目标。
2. 保留两个组合点并提取 A-GPS 工厂：能减少 Worker 重复，但 GNSS、历史和设置仍由 `MainActivity` 负责，应用组合仍是浅 Module。
3. 在 `GpsTestApplication` 中持有 `AppDependencies`：保留手动 DI 与现有 Factory，集中所有已有应用级依赖。调用方只知道组合根提供的稳定属性。选用此方案。

## 设计

新增 `AppDependencies` 容器，生产构造函数以 `Application` 为唯一构造参数。其公开属性使用 Kotlin `lazy` 缓存：

- `appSettingsStore`、`gnssRepository`、`satelliteHistoryRepository` 与 `externalGpsEphemerisProvider` 供 `MainActivity` 的 ViewModel Factory 使用。
- `agpsSettingsStore` 与 `agpsRepository` 供 `MainActivity` 和 `AGpsUpdateWorker` 共同使用。
- 默认生产路径继续使用既有 Adapter 和实现：`GnssDataSourceImpl`、`RoomSatelliteHistoryStore`、`AGpsDataSourceImpl`、`AGpsDownloaderImpl`、`AGpsFileHandlerImpl` 与存储类；不新增 DI 框架或领域接口。
- 内部 `AppDependencyFactory` 仅作为 JVM 作用域测试缝，用于替换生产创建器，避免测试触发 Room 或文件 I/O。

`GpsTestApplication` 通过惰性 `dependencies` 属性拥有该容器，是唯一应用级组合点。`MainActivity` 只从该属性取得 Factory 的输入。`AGpsUpdateWorker` 从其 `applicationContext` 取得同一应用实例的 `dependencies`，先读取现有设置，再调用同一个 A-GPS 仓库完成 hydrate、下载与注入。

## 保持的行为

- 权限、导航、ViewModel Factory、A-GPS 自动更新策略与重试语义不变。
- 依赖仍为进程级单例；首次访问前不创建平台对象或持久化存储。
- WorkManager 在应用进程中创建 Worker 时仍从 `Application` 获得应用 `Context`，不依赖 Activity 生命周期。

## 测试

在纯 JVM 单元测试中，以测试 `Application` 和内部 `AppDependencyFactory` 构造 `AppDependencies`。验证全部公开依赖（`appSettingsStore`、`gnssRepository`、`satelliteHistoryRepository`、`externalGpsEphemerisProvider`、`agpsSettingsStore`、`agpsRepository`）重复访问时返回同一对象；验证每个底层创建器仅执行一次；并验证传入创建器的始终是同一个 `Application` 实例。这将依赖作用域作为接口的测试面，防止组合根退回到每次访问新建实例，同时避免真实 Room 或文件 I/O。

随后运行定向测试、完整 `testDebugUnitTest` 和 `ktlintCheck`。
