package com.example.gpstest.domain.repository

import com.example.gpstest.domain.model.GnssSatellite
import com.example.gpstest.domain.model.SatelliteHistorySnapshot
import kotlinx.coroutines.flow.Flow

interface SatelliteHistoryRepository {
    val historySnapshots: Flow<List<SatelliteHistorySnapshot>>

    suspend fun saveSnapshot(satellites: List<GnssSatellite>)

    suspend fun clearHistory()
}
