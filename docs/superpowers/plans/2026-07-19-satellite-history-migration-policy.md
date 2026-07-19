# 卫星历史 migration policy 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 `subagent-driven-development`（推荐）或 `executing-plans` 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将卫星历史的 legacy JSON 导入、Room v1→v2 schema migration、重开恢复、retention 和 clear 收敛到一个可测试的持久化 module，避免任何静默历史丢失。

**架构：** `SatelliteHistoryPersistence` 成为 repository 唯一的 storage dependency，并在一个 `Mutex` 中协调 Room adapter、legacy JSON adapter 与 retention 设置。Room v2 保存“legacy import 已完成”的 metadata；Room transaction 成功后才写 DataStore 的旧 marker。Room adapter 只执行表操作，legacy adapter 只读写旧 JSON/marker，计算 retention 截止点及所有迁移决策只留在 persistence module。

**技术栈：** Kotlin、Room 2.6.1、DataStore Preferences、Kotlin Flow/Mutex、JUnit 4、kotlinx-coroutines-test、AndroidX Room MigrationTestHelper、MockK、KSP、Gradle、ktlint。

---

## 文件结构

- 修改：`app/build.gradle.kts`：导出 Room schema、将 schema 暴露给 Android instrumentation test，并加入 `room-testing`。
- 修改：`gradle/libs.versions.toml`：声明 `androidx.room:room-testing` version catalog alias。
- 创建：`app/schemas/com.example.gpstest.data.local.db.SatelliteHistoryDatabase/1.json`：由 v1 KSP 生成并提交的 migration fixture。
- 创建：`app/schemas/com.example.gpstest.data.local.db.SatelliteHistoryDatabase/2.json`：由 v2 KSP 生成并提交的预期 schema。
- 修改：`app/src/main/java/com/example/gpstest/data/local/db/SatelliteHistoryDatabase.kt`：添加 v2 metadata entity、显式 `MIGRATION_1_2`，将数据库升级为 v2。
- 修改：`app/src/main/java/com/example/gpstest/data/local/db/SatelliteHistoryDao.kt`：提供 metadata、空库检查和单 transaction 的 import/save/prune/clear 原语。
- 修改：`app/src/main/java/com/example/gpstest/data/local/RoomSatelliteHistoryStore.kt`：移除 destructive fallback，作为 Room adapter 执行 DAO 原语。
- 修改：`app/src/main/java/com/example/gpstest/data/local/SatelliteHistoryDataStore.kt`：收窄为 legacy JSON/marker adapter，不再保存当前历史或执行 retention。
- 创建：`app/src/main/java/com/example/gpstest/data/local/SatelliteHistoryPersistence.kt`：唯一编排 legacy 导入、重开、retention 与 clear 的 deep module。
- 修改：`app/src/main/java/com/example/gpstest/domain/repository/SatelliteHistoryRepositoryImpl.kt`：改依赖 `SatelliteHistoryPersistence`，不暴露迁移细节。
- 修改：`app/src/main/java/com/example/gpstest/AppDependencies.kt`：组合两个 adapter 与 persistence，不在组合根作迁移判断。
- 创建：`app/src/test/java/com/example/gpstest/data/local/SatelliteHistoryPersistenceTest.kt`：用 in-memory adapter 覆盖导入、失败重试、恢复、clear 和 retention 决策。
- 修改：`app/src/test/java/com/example/gpstest/AppDependenciesTest.kt`：覆盖新的 persistence 组合及对象复用。
- 创建：`app/src/androidTest/java/com/example/gpstest/data/local/db/SatelliteHistoryDatabaseMigrationTest.kt`：从导出的 v1 schema 运行 `MigrationTestHelper` 并重开验证 v1 行仍可读。
- 修改：`docs/ARCHITECTURE.md`：记录 Room-backed persistence pipeline 和 legacy adapter 的边界。

### 任务 1：先固化可验证的 v1 Room schema fixture

**文件：**

- 修改：`app/build.gradle.kts`
- 修改：`app/src/main/java/com/example/gpstest/data/local/db/SatelliteHistoryDatabase.kt`
- 创建：`app/schemas/com.example.gpstest.data.local.db.SatelliteHistoryDatabase/1.json`

- [x] **步骤 1：配置 KSP 导出 schema，并把 schema 放入 Android test assets**

在 `app/build.gradle.kts` 的顶层加入 KSP 参数；在既有 `android {}` 中加入 Android test asset source set。此时数据库仍保持 v1，仅将 `exportSchema` 改为 `true`，以生成可回放的既有 schema。

```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    sourceSets {
        getByName("androidTest") {
            assets.srcDir("$projectDir/schemas")
        }
    }
}

@Database(
    entities = [HistorySnapshotEntity::class, HistorySatelliteEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class SatelliteHistoryDatabase : RoomDatabase() {
    abstract fun historyDao(): SatelliteHistoryDao
}
```

- [x] **步骤 2：运行 KSP 生成 v1 fixture**

运行：`$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; $env:ANDROID_HOME='D:\android_sdk'; .\gradlew.bat :app:kspDebugKotlin`

预期：`BUILD SUCCESSFUL`，并生成 `app/schemas/com.example.gpstest.data.local.db.SatelliteHistoryDatabase/1.json`。检查该 JSON 同时包含 `history_snapshots` 与带外键的 `history_satellites`，不要手写或修改其 identity hash。

- [x] **步骤 3：确认新增 fixture 可被 Git 追踪**

运行：`git status --short app/schemas`

预期：显示新的 `.../1.json`；它代表已经发布的 v1 安装数据库，后续 migration test 必须以它为输入。

- [x] **步骤 4：提交 v1 fixture 和配置**

运行：`git add app/build.gradle.kts app/src/main/java/com/example/gpstest/data/local/db/SatelliteHistoryDatabase.kt app/schemas/com.example.gpstest.data.local.db.SatelliteHistoryDatabase/1.json`，然后运行 `git commit -m "chore: export satellite history room schema"`。

### 任务 2：先写 v1→v2 的失败 migration/reopen instrumentation test

**文件：**

- 修改：`gradle/libs.versions.toml`
- 修改：`app/build.gradle.kts`
- 创建：`app/src/androidTest/java/com/example/gpstest/data/local/db/SatelliteHistoryDatabaseMigrationTest.kt`

- [x] **步骤 1：加入 Room migration test 依赖**

在 version catalog 的 `[libraries]` 增加 alias，并在既有 `androidTestImplementation` 依赖中使用它。

```toml
room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
```

```kotlin
androidTestImplementation(libs.room.testing)
```

- [x] **步骤 2：编写失败的 migration/reopen test**

新建以下测试。使用 `MigrationTestHelper` 读取任务 1 导出的 v1 schema，写入一个 snapshot 和一个 satellite row，先验证 v2 schema，再通过正常的 `Room.databaseBuilder` 重开并验证 relation 映射。

```kotlin
package com.example.gpstest.data.local.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SatelliteHistoryDatabaseMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            SatelliteHistoryDatabase::class.java.canonicalName,
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun migrate1To2KeepsExistingSnapshotAndSatelliteRowsAfterReopen() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                INSERT INTO history_snapshots
                    (timestamp, usedInFixCount, visibleCount, averageSignalStrength,
                     latitude, longitude, accuracy, pdop, hdop, vdop, ttffMs)
                VALUES (1000, 1, 1, 31.5, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO history_satellites
                    (snapshotTimestamp, svid, constellationName, rawConstellationType, cn0DbHz, usedInFix)
                VALUES (1000, 7, 'GPS', 1, 31.5, 1)
                """.trimIndent(),
            )
            close()
        }

        helper
            .runMigrationsAndValidate(
                TEST_DB,
                2,
                true,
                SatelliteHistoryDatabase.MIGRATION_1_2,
            ).close()

        val database =
            Room.databaseBuilder(
                InstrumentationRegistry.getInstrumentation().targetContext,
                SatelliteHistoryDatabase::class.java,
                TEST_DB,
            ).addMigrations(SatelliteHistoryDatabase.MIGRATION_1_2).build()

        val restored = runBlocking { database.historyDao().observeAll().first().single().toSnapshot() }
        database.close()

        assertEquals(1000L, restored.timestamp)
        assertEquals(1, restored.usedInFixCount)
        assertEquals(7, restored.getEntries().single().svid)
        assertEquals("GPS", restored.getEntries().single().constellationName)
    }

    private companion object {
        const val TEST_DB = "satellite-history-migration-test"
    }
}
```

- [x] **步骤 3：运行测试验证失败**

运行：`$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; $env:ANDROID_HOME='D:\android_sdk'; .\gradlew.bat :app:compileDebugAndroidTestKotlin`

预期：FAIL，Kotlin 编译错误包含 `Unresolved reference: MIGRATION_1_2`。这证明测试没有依赖 destructive fallback 或尚未实现的自动升级。

- [x] **步骤 4：提交红灯测试**

运行：`git add gradle/libs.versions.toml app/build.gradle.kts app/src/androidTest/java/com/example/gpstest/data/local/db/SatelliteHistoryDatabaseMigrationTest.kt`，然后运行 `git commit -m "test: cover satellite history room migration"`。

### 任务 3：实现显式 Room v2 schema migration，并移除 destructive fallback

**文件：**

- 修改：`app/src/main/java/com/example/gpstest/data/local/db/SatelliteHistoryDatabase.kt`
- 修改：`app/src/main/java/com/example/gpstest/data/local/db/SatelliteHistoryDao.kt`
- 修改：`app/src/main/java/com/example/gpstest/data/local/RoomSatelliteHistoryStore.kt`
- 创建：`app/schemas/com.example.gpstest.data.local.db.SatelliteHistoryDatabase/2.json`

- [x] **步骤 1：声明 metadata entity 与不删历史的 `Migration(1, 2)`**

将 schema 更新为 v2。migration 只能创建 metadata table，不能 `DROP`、`DELETE` 或重建两个已有 history table。公开 migration 常量供 production builder 和 test 复用。

```kotlin
@Entity(tableName = "history_migration_metadata")
data class HistoryMigrationMetadataEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val legacyImportCompleted: Boolean,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}

@Database(
    entities = [
        HistorySnapshotEntity::class,
        HistorySatelliteEntity::class,
        HistoryMigrationMetadataEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class SatelliteHistoryDatabase : RoomDatabase() {
    abstract fun historyDao(): SatelliteHistoryDao

    companion object {
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `history_migration_metadata` (
                            `id` INTEGER NOT NULL,
                            `legacyImportCompleted` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent(),
                    )
                }
            }
    }
}
```

- [x] **步骤 2：提供 DAO 所需的 metadata 与 transaction 原语**

保留既有 read/delete query；新增以下 API。DAO 仅把 persistence 传入的 cutoff/limit 应用在同一个 Room transaction 中，不计算 retention policy。

```kotlin
@Query("SELECT * FROM history_migration_metadata WHERE id = 0")
suspend fun migrationMetadata(): HistoryMigrationMetadataEntity?

@Query("SELECT EXISTS(SELECT 1 FROM history_snapshots)")
suspend fun hasSnapshots(): Boolean

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun upsertMigrationMetadata(metadata: HistoryMigrationMetadataEntity)

@Transaction
suspend fun importLegacySnapshots(
    snapshots: List<SatelliteHistorySnapshot>,
    cutoffTimestamp: Long,
    maxSnapshots: Int,
) {
    snapshots.forEach { snapshot ->
        insertSnapshotWithSatellites(
            HistorySnapshotEntity.fromSnapshot(snapshot),
            snapshot.getEntries().map(HistorySatelliteEntity::fromEntry),
        )
    }
    upsertMigrationMetadata(HistoryMigrationMetadataEntity(legacyImportCompleted = true))
    prune(cutoffTimestamp, maxSnapshots)
}

@Transaction
suspend fun saveSnapshotAndPrune(
    snapshot: SatelliteHistorySnapshot,
    cutoffTimestamp: Long,
    maxSnapshots: Int,
) {
    insertSnapshotWithSatellites(
        HistorySnapshotEntity.fromSnapshot(snapshot),
        snapshot.getEntries().map(HistorySatelliteEntity::fromEntry),
    )
    prune(cutoffTimestamp, maxSnapshots)
}

@Transaction
suspend fun clearAndMarkLegacyImportComplete() {
    clear()
    upsertMigrationMetadata(HistoryMigrationMetadataEntity(legacyImportCompleted = true))
}

private suspend fun prune(cutoffTimestamp: Long, maxSnapshots: Int) {
    deleteBefore(cutoffTimestamp)
    deleteTimestamps(timestampsAfterNewest(maxSnapshots))
}
```

在 `RoomSatelliteHistoryStore` 的 builder 中仅注册显式 migration，禁止重新加入 fallback：

```kotlin
Room.databaseBuilder(context, SatelliteHistoryDatabase::class.java, "satellite_history.db")
    .addMigrations(SatelliteHistoryDatabase.MIGRATION_1_2)
    .build()
```

- [x] **步骤 3：生成 v2 schema 并确认它是增量升级**

运行：`$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; $env:ANDROID_HOME='D:\android_sdk'; .\gradlew.bat :app:kspDebugKotlin`

预期：`BUILD SUCCESSFUL`，生成 `.../2.json`；检查 JSON 同时保留 v1 的两个 history table 并新增 `history_migration_metadata`，没有新的 destructive fallback。

- [x] **步骤 4：运行 migration test 验证通过**

运行：`$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; $env:ANDROID_HOME='D:\android_sdk'; .\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.gpstest.data.local.db.SatelliteHistoryDatabaseMigrationTest`

预期：连接设备/模拟器时 `BUILD SUCCESSFUL`，且 `migrate1To2KeepsExistingSnapshotAndSatelliteRowsAfterReopen` PASS。若当前环境没有设备，改运行 `:app:assembleDebugAndroidTest`，预期 `BUILD SUCCESSFUL`；保留该 instrumentation test 在 CI/设备环境执行。

- [x] **步骤 5：提交 Room migration 实现**

运行：`git add app/src/main/java/com/example/gpstest/data/local/db/SatelliteHistoryDatabase.kt app/src/main/java/com/example/gpstest/data/local/db/SatelliteHistoryDao.kt app/src/main/java/com/example/gpstest/data/local/RoomSatelliteHistoryStore.kt app/schemas/com.example.gpstest.data.local.db.SatelliteHistoryDatabase/2.json`，然后运行 `git commit -m "refactor: add explicit satellite history migration"`。

### 任务 4：以失败的 JVM 测试锁定集中 migration policy

**文件：**

- 创建：`app/src/test/java/com/example/gpstest/data/local/SatelliteHistoryPersistenceTest.kt`
- 创建：`app/src/main/java/com/example/gpstest/data/local/SatelliteHistoryPersistence.kt`
- 修改：`app/src/main/java/com/example/gpstest/data/local/RoomSatelliteHistoryStore.kt`
- 修改：`app/src/main/java/com/example/gpstest/data/local/SatelliteHistoryDataStore.kt`

- [x] **步骤 1：编写 persistence 的失败测试与两个 in-memory adapter**

测试与 production class 放在同一 `data.local` package，以访问 internal adapter interface。Fake Room 在 `importLegacySnapshots` 中先记录 attempt；`failNextImport=true` 时抛出异常且不改变 metadata/rows，模拟 Room transaction rollback。Fake legacy adapter 只在 `markRoomMigrationComplete()` 被调用后改变 marker。

```kotlin
@Test
fun `first legacy import marks legacy only after room import and reopen does not duplicate`() = runTest {
    val legacySnapshot = snapshot(timestamp = 1000L)
    val room = FakeRoomStore()
    val legacy = FakeLegacyStore(snapshots = listOf(legacySnapshot), markerWritten = false)
    val persistence = persistence(room, legacy)

    assertEquals(listOf(legacySnapshot), persistence.snapshots.first())
    assertEquals(1, room.importAttempts)
    assertTrue(room.legacyImportCompleted)
    assertTrue(legacy.markerWritten)

    persistence(room, legacy).snapshots.first()
    assertEquals(1, room.importAttempts)
}

@Test
fun `failed room import keeps legacy json and marker for retry`() = runTest {
    val legacySnapshot = snapshot(timestamp = 1000L)
    val room = FakeRoomStore(failNextImport = true)
    val legacy = FakeLegacyStore(snapshots = listOf(legacySnapshot), markerWritten = false)
    val persistence = persistence(room, legacy)

    assertNotNull(runCatching { persistence.snapshots.first() }.exceptionOrNull())
    assertFalse(legacy.markerWritten)
    assertEquals(listOf(legacySnapshot), legacy.snapshots)
    assertFalse(room.legacyImportCompleted)

    assertEquals(listOf(legacySnapshot), persistence.snapshots.first())
    assertTrue(legacy.markerWritten)
    assertEquals(2, room.importAttempts)
}

@Test
fun `marked legacy json restores into an empty room`() = runTest {
    val legacySnapshot = snapshot(timestamp = 1000L)
    val room = FakeRoomStore()
    val legacy = FakeLegacyStore(snapshots = listOf(legacySnapshot), markerWritten = true)

    assertEquals(listOf(legacySnapshot), persistence(room, legacy).snapshots.first())
    assertEquals(1, room.importAttempts)
}

@Test
fun `clear removes both layers and reopen cannot restore history`() = runTest {
    val legacySnapshot = snapshot(timestamp = 1000L)
    val room = FakeRoomStore(legacyImportCompleted = true)
    val legacy = FakeLegacyStore(snapshots = listOf(legacySnapshot), markerWritten = true)
    val persistence = persistence(room, legacy)

    persistence.clear()

    assertTrue(room.rows.isEmpty())
    assertTrue(room.legacyImportCompleted)
    assertTrue(legacy.snapshots.isEmpty())
    assertTrue(legacy.markerWritten)
    assertTrue(persistence(room, legacy).snapshots.first().isEmpty())
    assertEquals(0, room.importAttempts)
}

@Test
fun `save calculates retention exactly once in persistence`() = runTest {
    val room = FakeRoomStore()
    val persistence = persistence(room, FakeLegacyStore(), maxSnapshots = 3, retentionDays = 2)

    persistence.save(snapshot(timestamp = 10_000L))

    assertEquals(HistoryRetention(cutoffTimestamp = -172_790_000L, maxSnapshots = 3), room.saveRetentions.single())
}
```

将以下 test fixtures 放在同一测试类中；它们完整实现本任务步骤 3 定义的 adapter contract，不使用真实 Android Context、DataStore 或 Room。

```kotlin
private fun persistence(
    room: FakeRoomStore,
    legacy: FakeLegacyStore,
    maxSnapshots: Int = 100,
    retentionDays: Int = 7,
): SatelliteHistoryPersistence =
    SatelliteHistoryPersistence(
        room,
        legacy,
        MutableStateFlow(AppSettings(maxSnapshots = maxSnapshots, retentionDays = retentionDays)),
        clock = { 10_000L },
    )

private fun snapshot(timestamp: Long): SatelliteHistorySnapshot =
    SatelliteHistorySnapshot.EMPTY.copy(timestamp = timestamp)

private class FakeRoomStore(
    var legacyImportCompleted: Boolean = false,
    var failNextImport: Boolean = false,
) : SatelliteHistoryRoomStore {
    val rows = mutableListOf<SatelliteHistorySnapshot>()
    val saveRetentions = mutableListOf<HistoryRetention>()
    var importAttempts = 0
    private val emittedRows = MutableStateFlow<List<SatelliteHistorySnapshot>>(emptyList())

    override val snapshots: Flow<List<SatelliteHistorySnapshot>> = emittedRows

    override suspend fun legacyImportCompleted(): Boolean = legacyImportCompleted

    override suspend fun hasSnapshots(): Boolean = rows.isNotEmpty()

    override suspend fun importLegacySnapshots(
        snapshots: List<SatelliteHistorySnapshot>,
        retention: HistoryRetention,
    ) {
        importAttempts += 1
        if (failNextImport) {
            failNextImport = false
            throw IllegalStateException("transaction failed")
        }
        rows += snapshots
        legacyImportCompleted = true
        emittedRows.value = rows.toList()
    }

    override suspend fun markLegacyImportComplete() {
        legacyImportCompleted = true
    }

    override suspend fun saveSnapshot(snapshot: SatelliteHistorySnapshot, retention: HistoryRetention) {
        rows += snapshot
        saveRetentions += retention
        emittedRows.value = rows.toList()
    }

    override suspend fun deleteSnapshot(timestamp: Long) {
        rows.removeAll { it.timestamp == timestamp }
        emittedRows.value = rows.toList()
    }

    override suspend fun clearHistory() {
        rows.clear()
        legacyImportCompleted = true
        emittedRows.value = emptyList()
    }
}

private class FakeLegacyStore(
    var snapshots: List<SatelliteHistorySnapshot> = emptyList(),
    var markerWritten: Boolean = false,
) : LegacySatelliteHistoryStore {
    override suspend fun readLegacyHistory() = LegacySatelliteHistory(snapshots, markerWritten)

    override suspend fun markRoomMigrationComplete() {
        markerWritten = true
    }

    override suspend fun clearLegacyHistory() {
        snapshots = emptyList()
        markerWritten = true
    }
}
```

- [x] **步骤 2：运行 JVM 测试验证失败**

运行：`$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; $env:ANDROID_HOME='D:\android_sdk'; .\gradlew.bat testDebugUnitTest --tests com.example.gpstest.data.local.SatelliteHistoryPersistenceTest`

预期：FAIL，编译错误包含 `Unresolved reference: SatelliteHistoryPersistence`、`HistoryRetention` 或 adapter interface。这是 policy 测试先于协调器实现的红灯。

- [x] **步骤 3：实现 internal adapter contract 和唯一协调器**

新建 `SatelliteHistoryPersistence.kt`。此文件拥有迁移决策、clock、settings 读取和 `Mutex`；不让 repository 或 `AppDependencies` 判断 marker。定义的所有名称必须与步骤 1 一致。

```kotlin
internal data class LegacySatelliteHistory(
    val snapshots: List<SatelliteHistorySnapshot>,
    val markerWritten: Boolean,
)

internal data class HistoryRetention(
    val cutoffTimestamp: Long,
    val maxSnapshots: Int,
)

internal interface LegacySatelliteHistoryStore {
    suspend fun readLegacyHistory(): LegacySatelliteHistory
    suspend fun markRoomMigrationComplete()
    suspend fun clearLegacyHistory()
}

internal interface SatelliteHistoryRoomStore {
    val snapshots: Flow<List<SatelliteHistorySnapshot>>

    suspend fun legacyImportCompleted(): Boolean
    suspend fun hasSnapshots(): Boolean
    suspend fun importLegacySnapshots(
        snapshots: List<SatelliteHistorySnapshot>,
        retention: HistoryRetention,
    )
    suspend fun markLegacyImportComplete()
    suspend fun saveSnapshot(snapshot: SatelliteHistorySnapshot, retention: HistoryRetention)
    suspend fun deleteSnapshot(timestamp: Long)
    suspend fun clearHistory()
}

class SatelliteHistoryPersistence internal constructor(
    private val roomStore: SatelliteHistoryRoomStore,
    private val legacyStore: LegacySatelliteHistoryStore,
    private val settings: Flow<AppSettings>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val readinessMutex = Mutex()
    private var ready = false

    val snapshots: Flow<List<SatelliteHistorySnapshot>> =
        flow {
            ensureReady()
            emitAll(roomStore.snapshots)
        }

    suspend fun save(snapshot: SatelliteHistorySnapshot) {
        ensureReady()
        roomStore.saveSnapshot(snapshot, retention())
    }

    suspend fun delete(timestamp: Long) {
        ensureReady()
        roomStore.deleteSnapshot(timestamp)
    }

    suspend fun clear() {
        ensureReady()
        roomStore.clearHistory()
        legacyStore.clearLegacyHistory()
    }

    private suspend fun ensureReady() {
        readinessMutex.withLock {
            if (ready) return
            if (roomStore.legacyImportCompleted()) {
                legacyStore.markRoomMigrationComplete()
            } else {
                val legacy = legacyStore.readLegacyHistory()
                val shouldImport =
                    legacy.snapshots.isNotEmpty() &&
                        (!legacy.markerWritten || !roomStore.hasSnapshots())
                if (shouldImport) {
                    roomStore.importLegacySnapshots(legacy.snapshots, retention())
                } else {
                    roomStore.markLegacyImportComplete()
                }
                legacyStore.markRoomMigrationComplete()
            }
            ready = true
        }
    }

    private suspend fun retention(): HistoryRetention {
        val current = settings.first()
        return HistoryRetention(
            cutoffTimestamp = clock() - current.retentionDays * MS_PER_DAY,
            maxSnapshots = current.maxSnapshots,
        )
    }

    private companion object {
        const val MS_PER_DAY = 24L * 60 * 60 * 1000
    }
}
```

将 `RoomSatelliteHistoryStore` 改为 `SatelliteHistoryRoomStore` 的唯一 Room implementation。将其 `importLegacySnapshots`、`saveSnapshot`、`clearHistory` 直接委托给任务 3 的 DAO transaction helpers，并将 `HistoryRetention` 展开为 DAO 所需的 `cutoffTimestamp` 与 `maxSnapshots`；`legacyImportCompleted()` 查询 metadata，而不是 DataStore marker。保留 `observeAll().map { it.toSnapshot() }` 作为 `snapshots`。将两个 adapter class 标记为 `internal`；`SatelliteHistoryPersistence` 保持 public class 但使用上面的 internal constructor，避免把 adapter contract 暴露为 app 的外部 API。

将 `SatelliteHistoryDataStore` 改为 `LegacySatelliteHistoryStore` 的唯一 legacy implementation：构造器只接收 `Context`，`readLegacyHistory()` 无论 marker 状态都解码 `SNAPSHOTS_KEY`，`markRoomMigrationComplete()` 仅写 `ROOM_MIGRATED_KEY=true`，而 `clearLegacyHistory()` 必须在同一次 `edit` 中清空 `SNAPSHOTS_KEY` 并写 marker。删除 `snapshots`、`saveSnapshot`、`deleteSnapshot`、`clearHistory`、`SettingsStore` 和所有 retention 常量/算法。

- [x] **步骤 4：运行 policy 测试验证通过**

运行：`$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; $env:ANDROID_HOME='D:\android_sdk'; .\gradlew.bat testDebugUnitTest --tests com.example.gpstest.data.local.SatelliteHistoryPersistenceTest`

预期：`BUILD SUCCESSFUL`；五个测试均 PASS，特别是失败 import 后 marker 仍为 false、reopen 不增加 import attempts，以及 marker=true/空 Room 的保守恢复。

- [x] **步骤 5：提交集中 persistence module**

运行：`git add app/src/main/java/com/example/gpstest/data/local/SatelliteHistoryPersistence.kt app/src/main/java/com/example/gpstest/data/local/RoomSatelliteHistoryStore.kt app/src/main/java/com/example/gpstest/data/local/SatelliteHistoryDataStore.kt app/src/test/java/com/example/gpstest/data/local/SatelliteHistoryPersistenceTest.kt app/src/main/java/com/example/gpstest/data/local/db/SatelliteHistoryDao.kt`，然后运行 `git commit -m "refactor: centralize satellite history migration policy"`。

### 任务 5：将 repository 与应用组合根迁移到 persistence module

**文件：**

- 修改：`app/src/main/java/com/example/gpstest/domain/repository/SatelliteHistoryRepositoryImpl.kt`
- 修改：`app/src/main/java/com/example/gpstest/AppDependencies.kt`
- 修改：`app/src/test/java/com/example/gpstest/AppDependenciesTest.kt`

- [x] **步骤 1：先改 DI 单测为 persistence 组合契约**

删除测试里的 `RoomSatelliteHistoryStore` mock，新增 `SatelliteHistoryPersistence` mock，并要求 factory 只在组合根组装 adapter 和 settings，而 repository creator 只接收 persistence。

```kotlin
val historyDataStore = mockk<SatelliteHistoryDataStore>()
val roomSatelliteHistoryStore = mockk<RoomSatelliteHistoryStore>()
val satelliteHistoryPersistence = mockk<SatelliteHistoryPersistence>()

createSatelliteHistoryPersistence = { roomStore, legacyStore, settingsStore ->
    created("satelliteHistoryPersistence")
    assertSame(roomSatelliteHistoryStore, roomStore)
    assertSame(historyDataStore, legacyStore)
    assertSame(appSettingsStore, settingsStore)
    satelliteHistoryPersistence
},
createSatelliteHistoryRepository = { persistence ->
    created("satelliteHistoryRepository")
    assertSame(satelliteHistoryPersistence, persistence)
    satelliteHistoryRepository
},
```

断言 `creationCounts["satelliteHistoryPersistence"] == 1`，并删除旧的 `roomHistoryStore` factory/计数断言。保留现有应用实例、其他 12 个 dependency 和 lazy reuse 断言。

- [x] **步骤 2：运行 DI 测试验证失败**

运行：`$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; $env:ANDROID_HOME='D:\android_sdk'; .\gradlew.bat testDebugUnitTest --tests com.example.gpstest.AppDependenciesTest`

预期：FAIL，`AppDependencyFactory` 尚不含 `createSatelliteHistoryPersistence`，且 repository creator 仍接收 `RoomSatelliteHistoryStore`。

- [x] **步骤 3：最小化重接 repository 与 DI**

repository 只将 domain snapshot 映射和存储调用转发给 persistence；不在这里加入 metadata、legacy 或 retention 分支。

```kotlin
class SatelliteHistoryRepositoryImpl(
    private val persistence: SatelliteHistoryPersistence,
) : SatelliteHistoryRepository {
    override val historySnapshots: Flow<List<SatelliteHistorySnapshot>> = persistence.snapshots

    // 保留既有 SatelliteHistorySnapshot.fromSatellites(...) 构造。
    // 构造后调用 persistence.save(snapshot)。
    override suspend fun deleteSnapshot(timestamp: Long) = persistence.delete(timestamp)

    override suspend fun clearHistory() = persistence.clear()
}
```

在 `AppDependencies` 的 history chain 中保持三层 lazy object：`SatelliteHistoryDataStore(application)`、`RoomSatelliteHistoryStore(application)`、`SatelliteHistoryPersistence(roomStore, historyDataStore, appSettingsStore.settings)`。更新 `AppDependencyFactory` 的 function type 与默认 lambda，使 `createSatelliteHistoryRepository` 接收 `SatelliteHistoryPersistence`。组合根只能传递对象，不得调用 `readLegacyHistory()` 或检查 marker。

- [x] **步骤 4：运行 DI 与 persistence JVM 测试验证通过**

运行：`$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; $env:ANDROID_HOME='D:\android_sdk'; .\gradlew.bat testDebugUnitTest --tests com.example.gpstest.AppDependenciesTest --tests com.example.gpstest.data.local.SatelliteHistoryPersistenceTest`

预期：`BUILD SUCCESSFUL`；DI test 证明 persistence 单例被创建一次，policy test 仍证明每个迁移条件由 persistence 决定。

- [x] **步骤 5：提交调用方接线**

运行：`git add app/src/main/java/com/example/gpstest/domain/repository/SatelliteHistoryRepositoryImpl.kt app/src/main/java/com/example/gpstest/AppDependencies.kt app/src/test/java/com/example/gpstest/AppDependenciesTest.kt`，然后运行 `git commit -m "refactor: wire history repository to persistence module"`。

### 任务 6：同步架构文档并做分层验证

**文件：**

- 修改：`docs/ARCHITECTURE.md`

- [x] **步骤 1：用 Room-backed pipeline 替换过时的 DataStore JSON pipeline**

将“历史快照”章节替换为以下结构，并补充一段说明：`SatelliteHistoryPersistence` 是唯一负责 migration/reopen/retention/clear 的 module；`SatelliteHistoryDataStore` 只在 legacy JSON compatibility 中存在，UI、ViewModel 和 repository 不读取旧 marker 或 Room version。

```text
SatelliteViewModel.maybeSaveSnapshot()（约每 60 秒）
        ↓
SatelliteHistoryRepositoryImpl
        ↓
SatelliteHistoryPersistence
        ├─ RoomSatelliteHistoryStore（v1→v2 显式 migration、当前读写）
        └─ SatelliteHistoryDataStore（仅 legacy JSON / marker 兼容）
        ↓ Flow<List<SatelliteHistorySnapshot>>
HistoryScreen
```

- [x] **步骤 2：执行定向 JVM 回归测试**

运行：`$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; $env:ANDROID_HOME='D:\android_sdk'; .\gradlew.bat testDebugUnitTest --tests com.example.gpstest.data.local.SatelliteHistoryPersistenceTest --tests com.example.gpstest.AppDependenciesTest`

预期：`BUILD SUCCESSFUL`，所有 policy 和组合根测试 PASS。

- [x] **步骤 3：编译 release/debug 与 Android test APK**

运行：`$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; $env:ANDROID_HOME='D:\android_sdk'; .\gradlew.bat assembleDebug assembleRelease :app:assembleDebugAndroidTest`

预期：`BUILD SUCCESSFUL`。这确认显式 migration、KSP schema 与 instrumentation test 在 CI 可构建；若设备可用，另运行任务 3 的 `connectedDebugAndroidTest` 命令。

- [x] **步骤 4：执行完整单元测试和代码风格检查**

运行：`$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; $env:ANDROID_HOME='D:\android_sdk'; .\gradlew.bat testDebugUnitTest ktlintCheck`

预期：`BUILD SUCCESSFUL`，没有 ktlint violation；`rg -n "fallbackToDestructiveMigration|saveSnapshot\(|retentionDays|maxSnapshots" app/src/main/java/com/example/gpstest/data/local/SatelliteHistoryDataStore.kt` 无输出，证明 legacy adapter 不再拥有 destructive fallback、当前写入或 retention policy。

- [x] **步骤 5：提交文档与最终验证产物**

运行：`git add docs/ARCHITECTURE.md docs/superpowers/plans/2026-07-19-satellite-history-migration-policy.md`，然后运行 `git commit -m "docs: describe satellite history migration policy"`。

## 自检

- 规格覆盖：任务 1–3 覆盖 v1 fixture、显式 v1→v2 migration、无 destructive fallback 和 reopen 保留 row；任务 4 覆盖 legacy 首次导入、失败不写 marker、crash 后 metadata 防重、marker=true/空 Room 恢复、clear 不复活和唯一 retention policy；任务 5 保证 repository/UI 侧不知晓迁移；任务 6 同步架构文档和全量构建验证。没有遗漏规格中的行为或不变量。
- 占位符：无禁用的空泛指令；每个修改步骤给出精确类型、方法、输入和命令。
- 类型一致性：`SatelliteHistoryPersistence` 的公开存储 API 固定为 `snapshots/save/delete/clear`；adapter 统一使用 `legacyImportCompleted`、`HistoryRetention`、`LegacySatelliteHistory`；v2 metadata entity 与 `MIGRATION_1_2` 在 test、Room builder、DAO 和 persistence flow 中使用同一名称。

## 执行记录

- 主提交：`73c1c3d`、`d970283`、`4add7e5`、`33089cf`、`b1b211c`、`ceec6f3`。
- 格式验收提交：`0172fd6`、`97ecd86`。
- 已验证：显式 v1→v2 instrumentation test 可编译；persistence 的 5 个 JVM 测试、完整 JVM 测试套件、Debug 与 Release APK、Android test APK 和 ktlint 均通过。
