package com.example.gpstest.domain.repository

import com.example.gpstest.domain.model.GnssData
import kotlinx.coroutines.flow.Flow

interface GnssRepository {
    fun getGnssData(): Flow<GnssData>
    suspend fun isGnssSupported(): Boolean
}
