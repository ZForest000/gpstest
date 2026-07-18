package com.example.gpstest.domain.ephemeris

import com.example.gpstest.domain.model.Constellation
import com.example.gpstest.domain.model.GnssClockData
import com.example.gpstest.domain.model.GnssSatellite
import com.example.gpstest.domain.model.PseudorangeObservation
import com.example.gpstest.domain.model.PseudorangeStatus

data class GpsObservationBuildResult(
    val observations: List<PseudorangeObservation>,
    val gpsSatelliteCount: Int,
    val validPseudorangeCount: Int,
    val loadedEphemerisCount: Int,
    val missingEphemerisSvids: List<Int>,
)

/** 将有效 GPS 原始观测和广播星历转换为 PositionSolver 输入。 */
object GpsPositionObservationBuilder {
    private const val SPEED_OF_LIGHT = 299_792_458.0
    private const val WEEK_NANOS = 604_800_000_000_000.0

    fun build(
        clock: GnssClockData?,
        satellites: List<GnssSatellite>,
        ephemerides: Map<Int, GpsBroadcastEphemeris>,
    ): List<PseudorangeObservation> = analyze(clock, satellites, ephemerides).observations

    fun analyze(
        clock: GnssClockData?,
        satellites: List<GnssSatellite>,
        ephemerides: Map<Int, GpsBroadcastEphemeris>,
    ): GpsObservationBuildResult {
        val gpsSatellites = satellites.filter { it.constellation == Constellation.GPS }
        val validPseudorangeSatellites =
            gpsSatellites.filter {
                it.pseudorangeStatus == PseudorangeStatus.AVAILABLE && it.pseudorangeMeters?.isFinite() == true
            }
        val missingEphemerisSvids =
            validPseudorangeSatellites
                .filter { it.svid !in ephemerides }
                .map { it.svid }
                .distinct()
                .sorted()
        val receiverTowSeconds = receiverTowSeconds(clock)
        if (receiverTowSeconds == null) {
            return GpsObservationBuildResult(
                observations = emptyList(),
                gpsSatelliteCount = gpsSatellites.size,
                validPseudorangeCount = validPseudorangeSatellites.size,
                loadedEphemerisCount = ephemerides.size,
                missingEphemerisSvids = missingEphemerisSvids,
            )
        }
        val observations =
            validPseudorangeSatellites.mapNotNull { satellite ->
                val pseudorange = satellite.pseudorangeMeters
                val ephemeris = ephemerides[satellite.svid]
                if (pseudorange == null || ephemeris == null) {
                    return@mapNotNull null
                }
                val transmitTow = wrapWeek(receiverTowSeconds - pseudorange / SPEED_OF_LIGHT)
                val satelliteState = GpsSatellitePropagator.propagate(ephemeris, transmitTow)
                PseudorangeObservation(
                    satellitePosition = satelliteState.position,
                    pseudorangeMeters = pseudorange + SPEED_OF_LIGHT * satelliteState.clockBiasSeconds,
                    uncertaintyMeters = satellite.pseudorangeUncertaintyMeters?.takeIf { it.isFinite() && it > 0.0 } ?: DEFAULT_UNCERTAINTY_METERS,
                )
            }
        return GpsObservationBuildResult(
            observations = observations,
            gpsSatelliteCount = gpsSatellites.size,
            validPseudorangeCount = validPseudorangeSatellites.size,
            loadedEphemerisCount = ephemerides.size,
            missingEphemerisSvids = missingEphemerisSvids,
        )
    }

    private fun receiverTowSeconds(clock: GnssClockData?): Double? {
        val fullBias = clock?.fullBiasNanos ?: return null
        val bias = clock.biasNanos ?: 0.0
        return wrapWeek((clock.timeNanos - fullBias - bias) / 1_000_000_000.0)
    }

    private fun wrapWeek(seconds: Double): Double = ((seconds % WEEK_SECONDS) + WEEK_SECONDS) % WEEK_SECONDS

    private const val WEEK_SECONDS = WEEK_NANOS / 1_000_000_000.0
    private const val DEFAULT_UNCERTAINTY_METERS = 10.0
}
