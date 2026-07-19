package com.example.gpstest.data.local

import android.content.Context
import androidx.room.Room
import com.example.gpstest.data.local.db.HistorySatelliteEntity
import com.example.gpstest.data.local.db.HistorySnapshotEntity
import com.example.gpstest.data.local.db.SatelliteHistoryDao
import com.example.gpstest.data.local.db.SatelliteHistoryDatabase
import com.example.gpstest.domain.model.AppSettings
import com.example.gpstest.domain.model.SatelliteHistorySnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Room 历史存储；首次访问时把旧 DataStore JSON 事务迁移到一对多表。 */
class RoomSatelliteHistoryStore(
    context: Context,
    private val legacyStore: SatelliteHistoryDataStore,
    private val settingsStore: SettingsStore,
) {
    private val dao: SatelliteHistoryDao =
        Room
            .databaseBuilder(context, SatelliteHistoryDatabase::class.java, "satellite_history.db")
            .addMigrations(SatelliteHistoryDatabase.MIGRATION_1_2)
            .build()
            .historyDao()
    private val migrationMutex = Mutex()
    private var migrationChecked = false

    val snapshots: Flow<List<SatelliteHistorySnapshot>> =
        flow {
            migrateIfNeeded()
            emitAll(dao.observeAll().map { rows -> rows.map { it.toSnapshot() } })
        }

    suspend fun saveSnapshot(snapshot: SatelliteHistorySnapshot) {
        migrateIfNeeded()
        insert(snapshot)
        prune(settingsStore.settings.first())
    }

    suspend fun deleteSnapshot(timestamp: Long) {
        migrateIfNeeded()
        dao.deleteSnapshot(timestamp)
    }

    suspend fun clearHistory() {
        migrateIfNeeded()
        dao.clear()
    }

    private suspend fun migrateIfNeeded() {
        migrationMutex.withLock {
            if (migrationChecked) return
            for (snapshot in legacyStore.readSnapshotsForMigration()) {
                insert(snapshot)
            }
            legacyStore.markRoomMigrationComplete()
            migrationChecked = true
        }
    }

    private suspend fun insert(snapshot: SatelliteHistorySnapshot) {
        dao.insertSnapshotWithSatellites(
            snapshot = HistorySnapshotEntity.fromSnapshot(snapshot),
            satellites = snapshot.getEntries().map(HistorySatelliteEntity::fromEntry),
        )
    }

    private suspend fun prune(settings: AppSettings) {
        val cutoff = System.currentTimeMillis() - settings.retentionDays * MS_PER_DAY
        dao.deleteBefore(cutoff)
        val excess = dao.timestampsAfterNewest(settings.maxSnapshots)
        if (excess.isNotEmpty()) dao.deleteTimestamps(excess)
    }

    private companion object {
        const val MS_PER_DAY = 24L * 60 * 60 * 1000
    }
}
