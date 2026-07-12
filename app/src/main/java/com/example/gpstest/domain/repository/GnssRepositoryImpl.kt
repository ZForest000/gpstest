package com.example.gpstest.domain.repository

import com.example.gpstest.data.source.GnssDataSource
import com.example.gpstest.domain.model.GnssData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.sample

// 卫星状态、测量值、位置、气压等多个回调都经 trySend 发射（气压计可达数十 Hz），
// 直接驱动 UI 会导致每个分组卡片每帧重算、卫星列表频繁重组。sample 把发射限制在
// ~4Hz，对实时监控足够流畅，又能大幅减少下游重算与重组次数。
private const val UI_SAMPLE_INTERVAL_MS = 250L

@OptIn(kotlinx.coroutines.FlowPreview::class)
class GnssRepositoryImpl(
    private val dataSource: GnssDataSource,
) : GnssRepository {
    override fun getGnssData(): Flow<GnssData> = dataSource.getGnssData().sample(UI_SAMPLE_INTERVAL_MS)

    override suspend fun isGnssSupported(): Boolean = dataSource.isSupported()
}
