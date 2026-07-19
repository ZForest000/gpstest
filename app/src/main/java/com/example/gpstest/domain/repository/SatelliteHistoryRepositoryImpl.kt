package com.example.gpstest.domain.repository

import com.example.gpstest.data.local.RoomSatelliteHistoryStore
import com.example.gpstest.domain.model.DopInfo
import com.example.gpstest.domain.model.GnssSatellite
import com.example.gpstest.domain.model.LocationInfo
import com.example.gpstest.domain.model.SatelliteHistorySnapshot
import kotlinx.coroutines.flow.Flow

class SatelliteHistoryRepositoryImpl internal constructor(
    private val dataStore: RoomSatelliteHistoryStore,
) : SatelliteHistoryRepository {
    override val historySnapshots: Flow<List<SatelliteHistorySnapshot>> = dataStore.snapshots

    override suspend fun saveSnapshot(
        satellites: List<GnssSatellite>,
        location: LocationInfo?,
        dopInfo: DopInfo?,
        ttffMs: Long?,
    ) {
        val snapshot =
            SatelliteHistorySnapshot.fromSatellites(
                satellites = satellites,
                timestamp = System.currentTimeMillis(),
                location = location,
                dopInfo = dopInfo,
                ttffMs = ttffMs,
            )
        dataStore.saveSnapshot(snapshot)
    }

    override suspend fun deleteSnapshot(timestamp: Long) {
        dataStore.deleteSnapshot(timestamp)
    }

    override suspend fun clearHistory() {
        dataStore.clearHistory()
    }
}
