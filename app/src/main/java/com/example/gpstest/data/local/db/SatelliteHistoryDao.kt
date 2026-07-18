package com.example.gpstest.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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
}
