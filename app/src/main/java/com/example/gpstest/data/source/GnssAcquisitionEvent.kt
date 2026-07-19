package com.example.gpstest.data.source

import com.example.gpstest.domain.model.GnssClockData
import com.example.gpstest.domain.model.GnssSatellite
import com.example.gpstest.domain.model.LocationInfo
import com.example.gpstest.domain.model.MultipathIndicator
import com.example.gpstest.domain.model.PseudorangeResult
import com.example.gpstest.domain.model.PseudorangeStatus

/** Android constellation type 与 SVID 组成的稳定卫星键。 */
data class GnssSatelliteKey(
    val constellationType: Int,
    val svid: Int,
)

/** 从一次 [android.location.GnssMeasurementsEvent] 提取的单颗卫星附加信息。 */
data class GnssMeasurementExtras(
    val carrierCycles: Long? = null,
    val dopplerShiftHz: Double? = null,
    val agcLevelDb: Double? = null,
    val multipathIndicator: MultipathIndicator? = null,
    val accumulatedDeltaRangeMeters: Double? = null,
    val accumulatedDeltaRangeState: Int? = null,
    val accumulatedDeltaRangeUncertaintyMeters: Double? = null,
    val receivedSvTimeNanos: Long? = null,
    val receivedSvTimeUncertaintyNanos: Double? = null,
    val pseudorangeRateMetersPerSecond: Double? = null,
    val measurementState: Int? = null,
    val measurementCn0DbHz: Double? = null,
    val fullCarrierPhaseCycleCount: Long? = null,
    val pseudorangeResult: PseudorangeResult =
        PseudorangeResult(status = PseudorangeStatus.MISSING_MEASUREMENT),
)

/**
 * 平台 adapter 向 acquisition session 提供的归一化事件。
 *
 * 这些类型仅依赖领域模型，便于在纯 JVM 中测试事件融合规则。
 */
sealed interface GnssAcquisitionEvent {
    data class SatelliteStatus(
        val satellites: List<GnssSatellite>,
    ) : GnssAcquisitionEvent

    data class Measurements(
        val clock: GnssClockData,
        val extrasBySatellite: Map<GnssSatelliteKey, GnssMeasurementExtras>,
    ) : GnssAcquisitionEvent

    data class Location(
        val location: LocationInfo,
    ) : GnssAcquisitionEvent

    data class Pressure(
        val pressure: Float,
        val barometricAltitude: Double,
    ) : GnssAcquisitionEvent

    data class Dumpsys(
        val data: DumpsysGnssData,
    ) : GnssAcquisitionEvent
}
