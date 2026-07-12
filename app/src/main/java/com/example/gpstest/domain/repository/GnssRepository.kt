package com.example.gpstest.domain.repository

import com.example.gpstest.domain.model.GnssCapabilitiesInfo
import com.example.gpstest.domain.model.GnssData
import kotlinx.coroutines.flow.Flow

interface GnssRepository {
    fun getGnssData(): Flow<GnssData>

    suspend fun isGnssSupported(): Boolean

    /** 查询设备 GNSS 能力（静态信息，不需要位置权限）。 */
    suspend fun getGnssCapabilities(): GnssCapabilitiesInfo?
}
