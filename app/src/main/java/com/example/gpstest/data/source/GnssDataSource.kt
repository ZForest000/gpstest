package com.example.gpstest.data.source

import com.example.gpstest.domain.model.GnssSatellite
import kotlinx.coroutines.flow.Flow

interface GnssDataSource {
    fun startListening(): Flow<List<GnssSatellite>>
    fun stopListening()
    fun isSupported(): Boolean
}
