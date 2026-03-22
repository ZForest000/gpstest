package com.example.gpstest.domain.repository

import com.example.gpstest.domain.model.GnssSatellite
import kotlinx.coroutines.flow.Flow

interface GnssRepository {
    fun getSatellites(): Flow<List<GnssSatellite>>
    suspend fun isGnssSupported(): Boolean
}
