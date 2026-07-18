# 集中应用依赖组合实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 `subagent-driven-development`（推荐）或 `executing-plans` 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将 `MainActivity` 与 `AGpsUpdateWorker` 的重复手动依赖组合收敛至 `GpsTestApplication` 持有的应用级组合根。

**架构：** 新增 `AppDependencies` Module，以 `Application` 构造并惰性缓存现有 Adapter 与仓库。`GpsTestApplication` 唯一创建该 Module；Activity 的 Factory 和 Worker 从中消费依赖，不改变领域 Interface、Worker 的结果映射或 UI 行为。内部 `AppDependencyFactory` 仅作为 JVM 测试缝，不进入生产调用路径。

**技术栈：** Kotlin、Android Application、WorkManager、JUnit 4、MockK、Gradle、ktlint。

---

## 文件结构

- 创建：`app/src/main/java/com/example/gpstest/AppDependencies.kt`：应用级依赖组合 Module。
- 修改：`app/src/main/java/com/example/gpstest/GpsTestApplication.kt`：拥有 `AppDependencies`。
- 修改：`app/src/main/java/com/example/gpstest/MainActivity.kt:48-132`：删除本地构造，改为读取组合根。
- 修改：`app/src/main/java/com/example/gpstest/service/AGpsUpdateWorker.kt:9-55`：删除重建 A-GPS 链，改为读取组合根。
- 创建：`app/src/test/java/com/example/gpstest/AppDependenciesTest.kt`：覆盖应用级依赖复用。
- 修改：`docs/ARCHITECTURE.md`：记录新的手动 DI 组合点。

### 任务 1：实现并测试应用组合根

**文件：**

- 创建：`app/src/test/java/com/example/gpstest/AppDependenciesTest.kt`
- 创建：`app/src/main/java/com/example/gpstest/AppDependencies.kt`
- 修改：`app/src/main/java/com/example/gpstest/GpsTestApplication.kt`

- [x] **步骤 1：编写失败的测试**

```kotlin
@Test
fun `reuses GNSS and A-GPS dependencies within one application container`() {
    val dependencies = AppDependencies(application, factory)

    assertSame(dependencies.gnssRepository, dependencies.gnssRepository)
    assertSame(dependencies.agpsSettingsStore, dependencies.agpsSettingsStore)
    assertSame(dependencies.agpsRepository, dependencies.agpsRepository)
}
```

- [x] **步骤 2：运行测试验证失败**

运行：`$env:ANDROID_HOME='D:\\android_sdk'; .\\gradlew.bat testDebugUnitTest --tests com.example.gpstest.AppDependenciesTest`

预期：FAIL，编译错误提示 `AppDependencies` 未定义。

- [x] **步骤 3：编写最少实现代码**

```kotlin
class AppDependencies(application: Application) {
    private val appContext: Context = application

    val appSettingsStore: SettingsStore by lazy { SettingsStore(appContext) }

    private val gnssDataSource: GnssDataSource by lazy { GnssDataSourceImpl(appContext) }
    val gnssRepository: GnssRepository by lazy {
        GnssRepositoryImpl(gnssDataSource)
    }

    private val historyDataStore: SatelliteHistoryDataStore by lazy {
        SatelliteHistoryDataStore(appContext, appSettingsStore)
    }
    val satelliteHistoryRepository: SatelliteHistoryRepository by lazy {
        SatelliteHistoryRepositoryImpl(
            RoomSatelliteHistoryStore(appContext, historyDataStore, appSettingsStore),
        )
    }
    val externalGpsEphemerisProvider: ExternalGpsEphemerisProvider by lazy {
        ExternalGpsEphemerisStore(appContext)
    }

    private val agpsDataSource: AGpsDataSource by lazy { AGpsDataSourceImpl(appContext) }
    private val agpsDownloader: AGpsDownloader by lazy { AGpsDownloaderImpl() }
    private val agpsFileHandler: AGpsFileHandler by lazy { AGpsFileHandlerImpl(appContext) }
    val agpsSettingsStore: AGpsSettingsStore by lazy { AGpsSettingsStore(appContext) }
    private val agpsInjectionHistoryStore: AGpsInjectionHistoryStore by lazy {
        AGpsInjectionHistoryStore(appContext)
    }
    val agpsRepository: AGpsRepository by lazy {
        AGpsRepositoryImpl(
            appContext,
            agpsDataSource,
            agpsDownloader,
            agpsFileHandler,
            agpsSettingsStore,
            agpsInjectionHistoryStore,
        )
    }
}

class GpsTestApplication : Application() {
    val dependencies: AppDependencies by lazy { AppDependencies(this) }
}
```

实现完整的现有 GNSS、历史、设置、星历和 A-GPS 构造链，不新增 DI 框架或领域 Interface。

- [x] **步骤 4：运行测试验证通过**

运行：`$env:ANDROID_HOME='D:\\android_sdk'; .\\gradlew.bat testDebugUnitTest --tests com.example.gpstest.AppDependenciesTest`

实际：PASS。测试覆盖全部公开依赖的对象复用、13 个创建步骤各执行一次，以及所有创建器接收同一 `Application`。

- [x] **步骤 5：Commit**

提交：`git add app/src/main/java/com/example/gpstest/AppDependencies.kt app/src/main/java/com/example/gpstest/GpsTestApplication.kt app/src/test/java/com/example/gpstest/AppDependenciesTest.kt`，然后运行 `git commit -m "refactor: add application dependency container"`。

### 任务 2：将前台与后台调用方迁移到组合根

**文件：**

- 修改：`app/src/main/java/com/example/gpstest/MainActivity.kt:48-132`
- 修改：`app/src/main/java/com/example/gpstest/service/AGpsUpdateWorker.kt:9-55`

- [x] **步骤 1：确认组合根测试保持绿色**

运行：`$env:ANDROID_HOME='D:\\android_sdk'; .\\gradlew.bat testDebugUnitTest --tests com.example.gpstest.AppDependenciesTest`

预期：PASS。调用方迁移是重构步骤，不改变已由任务 1 锁定的应用级依赖作用域。

- [x] **步骤 2：迁移调用方**

```kotlin
private val dependencies: AppDependencies
    get() = (application as GpsTestApplication).dependencies

private val dependencies: AppDependencies
    get() = (applicationContext as GpsTestApplication).dependencies
```

Activity 的每个 Factory 使用既有 `dependencies` 属性。Worker 保留 `settings.first()`、`hydrateHistory()`、成功更新时间与失败重试逻辑，只移除 A-GPS Adapter 和仓库的本地构造。

- [x] **步骤 3：运行定向测试验证通过**

运行：`$env:ANDROID_HOME='D:\\android_sdk'; .\\gradlew.bat testDebugUnitTest --tests com.example.gpstest.AppDependenciesTest`

预期：PASS，前后台调用将通过同一个 `GpsTestApplication.dependencies` 取得缓存的 A-GPS 仓库。

- [x] **步骤 4：Commit**

提交：`git add app/src/main/java/com/example/gpstest/MainActivity.kt app/src/main/java/com/example/gpstest/service/AGpsUpdateWorker.kt`，然后运行 `git commit -m "refactor: centralize application dependency composition"`。

### 任务 3：同步文档与全量验证

**文件：**

- 修改：`docs/ARCHITECTURE.md`

- [x] **步骤 1：更新依赖组合说明**

```markdown
- `GpsTestApplication` 持有 `AppDependencies`，作为唯一的应用级手动 DI 组合点。
- `MainActivity` 和 `AGpsUpdateWorker` 从该组合根取得既有依赖，不再自行重建链路。
```

- [x] **步骤 2：运行全部单元测试**

运行：`$env:ANDROID_HOME='D:\\android_sdk'; .\\gradlew.bat testDebugUnitTest`

预期：BUILD SUCCESSFUL。

- [x] **步骤 3：运行代码风格检查**

运行：`$env:ANDROID_HOME='D:\\android_sdk'; .\\gradlew.bat ktlintCheck`

预期：BUILD SUCCESSFUL。

- [x] **步骤 4：Commit**

提交：`git add docs/ARCHITECTURE.md docs/superpowers/specs/2026-07-19-centralize-application-dependency-composition-design.md docs/superpowers/plans/2026-07-19-centralize-application-dependency-composition.md`，然后运行 `git commit -m "docs: describe application dependency composition"`。

## 自检

- 规格覆盖：3 个任务分别覆盖组合根、两个调用方迁移、文档和验证，没有遗漏已识别的重复组合位置。
- 占位符：计划中的类型均为现有类型；A-GPS 组合使用现有 Adapter，不引入新 Interface。
- 类型一致性：`AppDependencies` 是唯一的应用组合 Module；`GpsTestApplication.dependencies`、Activity 与 Worker 均使用同一名称。

## 执行记录

- 任务 1：先以 `AppDependencies` 未定义的编译失败确认红灯；代码审查后将生产构造器收窄为 `Application`，并增加 internal `AppDependencyFactory` 以隔离 Room 和文件 I/O，同时验证全部 13 个创建步骤的单次执行。
- 任务 2：`MainActivity` 的 5 个 ViewModel Factory 与 `AGpsUpdateWorker` 均已迁移为 `GpsTestApplication.dependencies` 的消费者；Worker 的关闭短路、hydrate、注入、成功更新时间与失败重试顺序未改变。
- 任务 3：已运行完整 `testDebugUnitTest` 与 `ktlintCheck`，均为 `BUILD SUCCESSFUL`；对应提交为 `26d597d` 和 `31b0524`。
