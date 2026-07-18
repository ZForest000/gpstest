package com.example.gpstest.data.source

import com.example.gpstest.domain.model.AntennaInfo
import com.example.gpstest.domain.model.GnssCapabilitiesInfo
import com.example.gpstest.domain.model.GnssData
import com.example.gpstest.domain.model.NmeaSentence
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

    /**
     * 返回原始 NMEA 报文流（独立于 [getGnssData]，不参与 250ms 采样）。
     *
     * 底层通过 [android.location.LocationManager.addNmeaListener] 注册监听器，
     * 在 [callbackFlow] 的 awaitClose 中注销。需要位置权限才能收到报文。
     */
    fun getNmeaSentences(): Flow<NmeaSentence>

    /**
     * 天线相位中心信息流（API 30+）。
     * 独立于 [getGnssData]，不参与 250ms 采样。
     * API < 30 或不支持时立即发出 emptyList 并关闭。
     */
    fun getAntennaInfos(): Flow<List<AntennaInfo>>

    /** 设备是否支持 GPS（存在 GPS provider）。仿真器或无 GPS 设备返回 false。 */
    fun isSupported(): Boolean

    /**
     * 查询设备 GNSS 能力（API 31+）。
     *
     * 结果是静态的设备能力快照，不需要位置权限即可获取。
     * API < 31 的设备可能仅返回硬件型号/年份（API 28+），能力字段全部为 null。
     */
    fun getGnssCapabilities(): GnssCapabilitiesInfo?
}
