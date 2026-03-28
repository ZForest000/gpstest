package com.example.gpstest.domain.repository

import com.example.gpstest.data.local.SatelliteHistoryDataStore
import com.example.gpstest.domain.model.SatelliteHistorySnapshot
import com.example.gpstest.domain.model.GnssSatellite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SatelliteHistoryRepositoryImpl(
    private val dataStore: SatelliteHistoryDataStore
) : SatelliteHistoryRepository {
    
    override val historySnapshots: Flow<List<SatelliteHistorySnapshot>> = dataStore.snapshots
    
    override suspend fun saveSnapshot(satellites: List<GnssSatellite>) {
        val snapshot = SatelliteHistorySnapshot.fromSatellites(
            satellites = satellites,
            timestamp = System.currentTimeMillis()
        )
        dataStore.saveSnapshot(snapshot)
    }
    
    override suspend fun getSnapshotsSince(timestamp: Long): List<SatelliteHistorySnapshot> {
        return dataStore.snapshots.map { snapshots ->
            snapshots.filter { it.timestamp >= timestamp }
        }.first()
    }
    
    override suspend fun getLatestSnapshot(): SatelliteHistorySnapshot? {
        return dataStore.snapshots.map { snapshots ->
            snapshots.firstOrNull()
        }.first()
    }
    
    override suspend fun clearHistory() {
        dataStore.clearHistory()
    }
    
    override suspend fun clearOldSnapshots(olderThanTimestamp: Long) {
        dataStore.clearOldSnapshots(olderThanTimestamp)
    }
}

private suspend inline fun <T> Flow<T>.first(): T {
    var result: T? = null
    collect { 
        result = it
        return@collect
    }
    return result ?: throw NoSuchElementException("Flow is empty")
}
