package com.example.gpstest.data.local

import android.content.Context
import androidx.room.Room
import com.example.gpstest.data.local.db.HistoryMigrationMetadataEntity
import com.example.gpstest.data.local.db.SatelliteHistoryDao
import com.example.gpstest.data.local.db.SatelliteHistoryDatabase
import com.example.gpstest.domain.model.SatelliteHistorySnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room 历史存储，仅负责对 DAO 的事务原语进行适配。 */
internal class RoomSatelliteHistoryStore(
    context: Context,
) : SatelliteHistoryRoomStore {
    private val dao: SatelliteHistoryDao =
        Room
            .databaseBuilder(context, SatelliteHistoryDatabase::class.java, "satellite_history.db")
            .addMigrations(SatelliteHistoryDatabase.MIGRATION_1_2)
            .build()
            .historyDao()

    override val snapshots: Flow<List<SatelliteHistorySnapshot>> =
        dao.observeAll().map { rows -> rows.map { it.toSnapshot() } }

    override suspend fun legacyImportCompleted(): Boolean =
        dao.migrationMetadata()?.legacyImportCompleted ?: false

    override suspend fun hasSnapshots(): Boolean = dao.hasSnapshots()

    override suspend fun importLegacySnapshots(
        snapshots: List<SatelliteHistorySnapshot>,
        retention: HistoryRetention,
    ) {
        dao.importLegacySnapshots(
            snapshots = snapshots,
            cutoffTimestamp = retention.cutoffTimestamp,
            maxSnapshots = retention.maxSnapshots,
        )
    }

    override suspend fun markLegacyImportComplete() {
        dao.upsertMigrationMetadata(HistoryMigrationMetadataEntity(legacyImportCompleted = true))
    }

    override suspend fun saveSnapshot(
        snapshot: SatelliteHistorySnapshot,
        retention: HistoryRetention,
    ) {
        dao.saveSnapshotAndPrune(
            snapshot = snapshot,
            cutoffTimestamp = retention.cutoffTimestamp,
            maxSnapshots = retention.maxSnapshots,
        )
    }

    override suspend fun deleteSnapshot(timestamp: Long) {
        dao.deleteSnapshot(timestamp)
    }

    override suspend fun clearHistory() {
        dao.clearAndMarkLegacyImportComplete()
    }
}
