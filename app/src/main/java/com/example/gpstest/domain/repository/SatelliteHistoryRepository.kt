package com.example.gpstest.domain.repository

import com.example.gpstest.domain.model.SatelliteHistorySnapshot
import com.example.gpstest.domain.model.GnssSatellite
import kotlinx.coroutines.flow.Flow

interface SatelliteHistoryRepository {
    val historySnapshots: Flow<List<SatelliteHistorySnapshot>>
    
    suspend fun saveSnapshot(satellites: List<GnssSatellite>)
    
    suspend fun getSnapshotsSince(timestamp: Long): List<SatelliteHistorySnapshot>
    
    suspend fun getLatestSnapshot(): SatelliteHistorySnapshot?
    
    suspend fun clearHistory()
    
    suspend fun clearOldSnapshots(olderThanTimestamp: Long)
}
