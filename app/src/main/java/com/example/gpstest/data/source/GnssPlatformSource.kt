package com.example.gpstest.data.source

import com.example.gpstest.domain.model.AntennaInfo
import com.example.gpstest.domain.model.GnssCapabilitiesInfo
import com.example.gpstest.domain.model.NavigationMessageFrame
import com.example.gpstest.domain.model.NmeaSentence
import kotlinx.coroutines.flow.Flow

/**
 * Android GNSS API adapter。
 *
 * 每个 Flow 都是 cold 的平台注册入口；共享、订阅计数与事件融合由
 * [GnssAcquisitionSession] 统一负责。
 */
interface GnssPlatformSource {
    fun getAcquisitionEvents(): Flow<GnssAcquisitionEvent>

    fun getNmeaSentences(): Flow<NmeaSentence>

    fun getNavigationMessages(): Flow<NavigationMessageFrame>

    fun getAntennaInfos(): Flow<List<AntennaInfo>>

    fun isSupported(): Boolean

    fun getGnssCapabilities(): GnssCapabilitiesInfo?
}
