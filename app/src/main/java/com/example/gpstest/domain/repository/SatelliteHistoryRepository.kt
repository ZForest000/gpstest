package com.example.gpstest.domain.repository

import com.example.gpstest.domain.model.DopInfo
import com.example.gpstest.domain.model.GnssSatellite
import com.example.gpstest.domain.model.LocationInfo
import com.example.gpstest.domain.model.SatelliteHistorySnapshot
import kotlinx.coroutines.flow.Flow

interface SatelliteHistoryRepository {
    val historySnapshots: Flow<List<SatelliteHistorySnapshot>>

    suspend fun saveSnapshot(
        satellites: List<GnssSatellite>,
        location: LocationInfo? = null,
        dopInfo: DopInfo? = null,
        ttffMs: Long? = null,
    )

    suspend fun deleteSnapshot(timestamp: Long)

    suspend fun clearHistory()
}
