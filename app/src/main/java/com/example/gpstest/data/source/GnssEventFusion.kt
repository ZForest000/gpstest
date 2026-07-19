package com.example.gpstest.data.source

import com.example.gpstest.domain.model.GnssClockData
import com.example.gpstest.domain.model.GnssData
import com.example.gpstest.domain.model.GnssSatellite
import com.example.gpstest.domain.model.LocationInfo

/**
 * 在 acquisition session 内合并 GNSS 平台事件。
 *
 * 该类不依赖 Android runtime，因此事件配对和 freshness 规则可通过 JVM 单测覆盖。
 */
class GnssEventFusion {
    private var baseSatellites: List<GnssSatellite> = emptyList()
    private var currentLocation: LocationInfo? = null
    private var currentClock: GnssClockData? = null
    private var currentDumpsysData: DumpsysGnssData? = null
    private var currentPressure: Float? = null
    private var currentBarometricAltitude: Double? = null
    private var measurementExtras: Map<GnssSatelliteKey, GnssMeasurementExtras> = emptyMap()

    /** 处理一个平台事件；气压更新仅缓存，其他更新返回可发射的最新快照。 */
    fun onEvent(event: GnssAcquisitionEvent): GnssData? =
        when (event) {
            is GnssAcquisitionEvent.SatelliteStatus -> {
                baseSatellites = event.satellites
                snapshot()
            }

            is GnssAcquisitionEvent.Measurements -> {
                currentClock = event.clock
                measurementExtras = event.extrasBySatellite
                if (baseSatellites.isEmpty()) null else snapshot()
            }

            is GnssAcquisitionEvent.Location -> {
                currentLocation =
                    event.location.copy(
                        pressure = currentPressure,
                        barometricAltitude = currentBarometricAltitude,
                    )
                snapshot()
            }

            is GnssAcquisitionEvent.Pressure -> {
                currentPressure = event.pressure
                currentBarometricAltitude = event.barometricAltitude
                null
            }

            is GnssAcquisitionEvent.Dumpsys -> {
                currentDumpsysData = event.data
                snapshot()
            }
        }

    private fun snapshot(): GnssData =
        GnssData(
            satellites = baseSatellites.map(::mergeMeasurementExtras),
            location = currentLocation,
            clock = currentClock,
            dumpsysData = currentDumpsysData,
        )

    private fun mergeMeasurementExtras(satellite: GnssSatellite): GnssSatellite {
        val extras =
            measurementExtras[GnssSatelliteKey(satellite.rawConstellationType, satellite.svid)]
                ?: return satellite
        return satellite.copy(
            carrierCycles = extras.carrierCycles ?: satellite.carrierCycles,
            dopplerShiftHz = extras.dopplerShiftHz ?: satellite.dopplerShiftHz,
            agcLevelDb = extras.agcLevelDb ?: satellite.agcLevelDb,
            multipathIndicator = extras.multipathIndicator ?: satellite.multipathIndicator,
            accumulatedDeltaRangeMeters =
                extras.accumulatedDeltaRangeMeters ?: satellite.accumulatedDeltaRangeMeters,
            accumulatedDeltaRangeState = extras.accumulatedDeltaRangeState ?: satellite.accumulatedDeltaRangeState,
            accumulatedDeltaRangeUncertaintyMeters =
                extras.accumulatedDeltaRangeUncertaintyMeters
                    ?: satellite.accumulatedDeltaRangeUncertaintyMeters,
            receivedSvTimeNanos = extras.receivedSvTimeNanos ?: satellite.receivedSvTimeNanos,
            receivedSvTimeUncertaintyNanos =
                extras.receivedSvTimeUncertaintyNanos ?: satellite.receivedSvTimeUncertaintyNanos,
            pseudorangeRateMetersPerSecond =
                extras.pseudorangeRateMetersPerSecond ?: satellite.pseudorangeRateMetersPerSecond,
            measurementState = extras.measurementState ?: satellite.measurementState,
            measurementCn0DbHz = extras.measurementCn0DbHz ?: satellite.measurementCn0DbHz,
            fullCarrierPhaseCycleCount =
                extras.fullCarrierPhaseCycleCount ?: satellite.fullCarrierPhaseCycleCount,
            pseudorangeMeters = extras.pseudorangeResult.meters ?: satellite.pseudorangeMeters,
            pseudorangeUncertaintyMeters =
                extras.pseudorangeResult.uncertaintyMeters ?: satellite.pseudorangeUncertaintyMeters,
            pseudorangeStatus = extras.pseudorangeResult.status,
        )
    }
}
