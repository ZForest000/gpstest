package com.example.gpstest.domain.repository

import com.example.gpstest.domain.model.AntennaInfo
import com.example.gpstest.domain.model.GnssCapabilitiesInfo
import com.example.gpstest.domain.model.GnssData
import com.example.gpstest.domain.model.NmeaSentence
import kotlinx.coroutines.flow.Flow

interface GnssRepository {
    fun getGnssData(): Flow<GnssData>

    /**
     * 原始 NMEA 报文流，直接透传数据源，**不**参与 [getGnssData] 的 250ms 采样。
     * NMEA 速率远低于卫星状态，且 UI 需要逐行展示，采样会丢失报文。
     */
    fun getNmeaSentences(): Flow<NmeaSentence>

    /**
     * 天线相位中心信息流，直接透传数据源，**不**参与 250ms 采样。
     * API < 30 时为空列表。
     */
    fun getAntennaInfos(): Flow<List<AntennaInfo>>

    suspend fun isGnssSupported(): Boolean

    /** 查询设备 GNSS 能力（静态信息，不需要位置权限）。 */
    suspend fun getGnssCapabilities(): GnssCapabilitiesInfo?
}
