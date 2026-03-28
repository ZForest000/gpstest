package com.example.gpstest.data.source

import com.example.gpstest.domain.model.GnssData
import kotlinx.coroutines.flow.Flow

interface GnssDataSource {
    fun getGnssData(): Flow<GnssData>
    fun isSupported(): Boolean
}
