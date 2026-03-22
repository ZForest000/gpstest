package com.example.gpstest.domain.repository

import com.example.gpstest.data.source.GnssDataSource
import com.example.gpstest.domain.model.GnssSatellite
import kotlinx.coroutines.flow.Flow

class GnssRepositoryImpl(
    private val dataSource: GnssDataSource
) : GnssRepository {

    override fun getSatellites(): Flow<List<GnssSatellite>> {
        return dataSource.startListening()
    }

    override suspend fun isGnssSupported(): Boolean {
        return dataSource.isSupported()
    }
}
