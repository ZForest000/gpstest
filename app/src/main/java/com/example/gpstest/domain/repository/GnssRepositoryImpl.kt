package com.example.gpstest.domain.repository

import com.example.gpstest.data.source.GnssAcquisitionSession
import com.example.gpstest.domain.model.AntennaInfo
import com.example.gpstest.domain.model.GnssCapabilitiesInfo
import com.example.gpstest.domain.model.GnssData
import com.example.gpstest.domain.model.NavigationMessageFrame
import com.example.gpstest.domain.model.NmeaSentence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.sample

// 卫星状态、测量值、位置、气压等多个回调都经 trySend 发射（气压计可达数十 Hz），
// 直接驱动 UI 会导致每个分组卡片每帧重算、卫星列表频繁重组。sample 把发射限制在
// ~4Hz，对实时监控足够流畅，又能大幅减少下游重算与重组次数。
private const val UI_SAMPLE_INTERVAL_MS = 250L

@OptIn(kotlinx.coroutines.FlowPreview::class)
class GnssRepositoryImpl(
    private val acquisitionSession: GnssAcquisitionSession,
) : GnssRepository {
    override fun getGnssData(): Flow<GnssData> = acquisitionSession.getGnssData().sample(UI_SAMPLE_INTERVAL_MS)

    // NMEA 报文不采样：原始数据按行展示，采样会丢失报文。
    override fun getNmeaSentences(): Flow<NmeaSentence> = acquisitionSession.getNmeaSentences()

    override fun getNavigationMessages(): Flow<NavigationMessageFrame> = acquisitionSession.getNavigationMessages()

    // 天线信息不采样：更新频率低，直接透传。
    override fun getAntennaInfos(): Flow<List<AntennaInfo>> = acquisitionSession.getAntennaInfos()

    override suspend fun isGnssSupported(): Boolean = acquisitionSession.isSupported()

    override suspend fun getGnssCapabilities(): GnssCapabilitiesInfo? = acquisitionSession.getGnssCapabilities()
}
