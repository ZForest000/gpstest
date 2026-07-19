package com.example.gpstest.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.gpstest.domain.model.SatelliteHistorySnapshot
import kotlinx.coroutines.flow.Flow

@Dao
interface SatelliteHistoryDao {
    @Transaction
    @Query("SELECT * FROM history_snapshots ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<SnapshotWithSatellites>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: HistorySnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSatellites(satellites: List<HistorySatelliteEntity>)

    @Query("DELETE FROM history_snapshots WHERE timestamp = :timestamp")
    suspend fun deleteSnapshot(timestamp: Long)

    @Query("DELETE FROM history_snapshots")
    suspend fun clear()

    @Query("SELECT * FROM history_migration_metadata WHERE id = 0")
    suspend fun migrationMetadata(): HistoryMigrationMetadataEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM history_snapshots)")
    suspend fun hasSnapshots(): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMigrationMetadata(metadata: HistoryMigrationMetadataEntity)

    @Query("DELETE FROM history_snapshots WHERE timestamp < :cutoff")
    suspend fun deleteBefore(cutoff: Long)

    @Query("SELECT timestamp FROM history_snapshots ORDER BY timestamp DESC LIMIT -1 OFFSET :keepCount")
    suspend fun timestampsAfterNewest(keepCount: Int): List<Long>

    @Query("DELETE FROM history_snapshots WHERE timestamp IN (:timestamps)")
    suspend fun deleteTimestamps(timestamps: List<Long>)

    @Transaction
    suspend fun insertSnapshotWithSatellites(
        snapshot: HistorySnapshotEntity,
        satellites: List<HistorySatelliteEntity>,
    ) {
        insertSnapshot(snapshot)
        if (satellites.isNotEmpty()) insertSatellites(satellites)
    }

    @Transaction
    suspend fun importLegacySnapshots(
        snapshots: List<SatelliteHistorySnapshot>,
        cutoffTimestamp: Long,
        maxSnapshots: Int,
    ) {
        snapshots.forEach { snapshot ->
            insertSnapshotWithSatellites(
                snapshot = HistorySnapshotEntity.fromSnapshot(snapshot),
                satellites = snapshot.getEntries().map(HistorySatelliteEntity::fromEntry),
            )
        }
        upsertMigrationMetadata(
            HistoryMigrationMetadataEntity(legacyImportCompleted = true),
        )
        prune(cutoffTimestamp, maxSnapshots)
    }

    @Transaction
    suspend fun saveSnapshotAndPrune(
        snapshot: SatelliteHistorySnapshot,
        cutoffTimestamp: Long,
        maxSnapshots: Int,
    ) {
        insertSnapshotWithSatellites(
            snapshot = HistorySnapshotEntity.fromSnapshot(snapshot),
            satellites = snapshot.getEntries().map(HistorySatelliteEntity::fromEntry),
        )
        prune(cutoffTimestamp, maxSnapshots)
    }

    @Transaction
    suspend fun clearAndMarkLegacyImportComplete() {
        clear()
        upsertMigrationMetadata(
            HistoryMigrationMetadataEntity(legacyImportCompleted = true),
        )
    }

    private suspend fun prune(
        cutoffTimestamp: Long,
        maxSnapshots: Int,
    ) {
        deleteBefore(cutoffTimestamp)
        val timestamps = timestampsAfterNewest(maxSnapshots)
        if (timestamps.isNotEmpty()) deleteTimestamps(timestamps)
    }
}
