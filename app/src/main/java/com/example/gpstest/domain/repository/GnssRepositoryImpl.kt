package com.example.gpstest.domain.repository

import com.example.gpstest.data.source.GnssDataSource
import com.example.gpstest.domain.model.GnssData
import kotlinx.coroutines.flow.Flow

class GnssRepositoryImpl(
    private val dataSource: GnssDataSource
) : GnssRepository {

    override fun getGnssData(): Flow<GnssData> {
        return dataSource.getGnssData()
    }

    override suspend fun isGnssSupported(): Boolean {
        return dataSource.isSupported()
    }
}
