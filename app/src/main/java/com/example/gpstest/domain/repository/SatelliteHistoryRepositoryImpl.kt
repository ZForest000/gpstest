package com.example.gpstest.domain.repository

import com.example.gpstest.data.local.SatelliteHistoryDataStore
import com.example.gpstest.domain.model.GnssSatellite
import com.example.gpstest.domain.model.SatelliteHistorySnapshot
import kotlinx.coroutines.flow.Flow

class SatelliteHistoryRepositoryImpl(
    private val dataStore: SatelliteHistoryDataStore,
) : SatelliteHistoryRepository {
    override val historySnapshots: Flow<List<SatelliteHistorySnapshot>> = dataStore.snapshots

    override suspend fun saveSnapshot(satellites: List<GnssSatellite>) {
        val snapshot =
            SatelliteHistorySnapshot.fromSatellites(
                satellites = satellites,
                timestamp = System.currentTimeMillis(),
            )
        dataStore.saveSnapshot(snapshot)
    }

    override suspend fun clearHistory() {
        dataStore.clearHistory()
    }
}
