package com.example.gpstest.data.source

import com.example.gpstest.domain.model.GnssData
import kotlinx.coroutines.flow.Flow

/**
 * GNSS 数据源接口，抽象 Android 平台 GNSS API。
 *
 * [getGnssData] 将 4 个独立的平台回调（卫星状态、原始测量、位置、气压传感器）
 * 合并为统一的 [Flow]，实现见 [GnssDataSourceImpl]。
 */
interface GnssDataSource {

    /**
     * 返回 GNSS 数据流，包含卫星列表、位置、时钟和气压信息。
     * 调用方应在生命周期感知的协程作用域中收集此 Flow。
     */
    fun getGnssData(): Flow<GnssData>

    /** 设备是否支持 GPS（存在 GPS provider）。仿真器或无 GPS 设备返回 false。 */
    fun isSupported(): Boolean
}
