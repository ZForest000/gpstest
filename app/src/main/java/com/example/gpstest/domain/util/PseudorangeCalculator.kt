package com.example.gpstest.domain.util

import com.example.gpstest.domain.model.Constellation
import com.example.gpstest.domain.model.GnssClockData
import com.example.gpstest.domain.model.PseudorangeMeasurement
import com.example.gpstest.domain.model.PseudorangeResult
import com.example.gpstest.domain.model.PseudorangeStatus
import kotlin.math.sqrt

/** Derives a code pseudorange from Android GNSS raw-measurement clock values. */
object PseudorangeCalculator {
    private const val SPEED_OF_LIGHT_METERS_PER_SECOND = 299_792_458.0
    private const val NANOS_PER_SECOND = 1_000_000_000.0
    private const val WEEK_NANOS = 604_800_000_000_000L
    private const val BDS_GPS_OFFSET_NANOS = 14_000_000_000L
    private const val MIN_PSEUDORANGE_METERS = 15_000_000.0
    private const val MAX_PSEUDORANGE_METERS = 50_000_000.0

    fun calculate(
        clock: GnssClockData?,
        measurement: PseudorangeMeasurement,
    ): PseudorangeResult {
        if (measurement.constellation !in SUPPORTED_CONSTELLATIONS) {
            return unavailable(PseudorangeStatus.UNSUPPORTED_CONSTELLATION)
        }
        if (clock?.fullBiasNanos == null) {
            return unavailable(PseudorangeStatus.MISSING_FULL_BIAS)
        }
        if (measurement.receivedSvTimeNanos == null) {
            return unavailable(PseudorangeStatus.MISSING_RECEIVED_SV_TIME)
        }
        if (!measurement.hasCodeLock) {
            return unavailable(PseudorangeStatus.MISSING_CODE_LOCK)
        }
        if (!measurement.hasTowDecoded) {
            return unavailable(PseudorangeStatus.MISSING_TOW_DECODED)
        }

        val biasNanos = clock.biasNanos ?: 0.0
        val receivedSvTimeUncertaintyNanos = measurement.receivedSvTimeUncertaintyNanos
        val biasUncertaintyNanos = clock.biasUncertaintyNanos ?: 0.0
        if (
            !measurement.timeOffsetNanos.isFinite() ||
            !biasNanos.isFinite() ||
            !biasUncertaintyNanos.isFinite() ||
            (
                receivedSvTimeUncertaintyNanos != null &&
                    (!receivedSvTimeUncertaintyNanos.isFinite() || receivedSvTimeUncertaintyNanos < 0.0)
            )
        ) {
            return unavailable(PseudorangeStatus.INVALID_INPUT)
        }

        val constellationOffsetNanos =
            if (measurement.constellation == Constellation.BEIDOU) BDS_GPS_OFFSET_NANOS else 0L
        val receiverTowNanos =
            floorMod(
                clock.timeNanos - clock.fullBiasNanos - constellationOffsetNanos,
                WEEK_NANOS,
            ).toDouble() + measurement.timeOffsetNanos - biasNanos
        val travelTimeNanos = floorMod(receiverTowNanos - measurement.receivedSvTimeNanos, WEEK_NANOS.toDouble())
        val pseudorangeMeters = travelTimeNanos / NANOS_PER_SECOND * SPEED_OF_LIGHT_METERS_PER_SECOND

        if (!pseudorangeMeters.isFinite()) {
            return unavailable(PseudorangeStatus.INVALID_INPUT)
        }
        if (pseudorangeMeters !in MIN_PSEUDORANGE_METERS..MAX_PSEUDORANGE_METERS) {
            return unavailable(PseudorangeStatus.OUT_OF_RANGE)
        }

        val uncertaintyMeters =
            receivedSvTimeUncertaintyNanos?.let {
                sqrt(it * it + biasUncertaintyNanos * biasUncertaintyNanos) /
                    NANOS_PER_SECOND *
                    SPEED_OF_LIGHT_METERS_PER_SECOND
            }
        return PseudorangeResult(
            meters = pseudorangeMeters,
            uncertaintyMeters = uncertaintyMeters,
            status = PseudorangeStatus.AVAILABLE,
        )
    }

    private fun unavailable(status: PseudorangeStatus) = PseudorangeResult(status = status)

    private fun floorMod(
        value: Long,
        modulus: Long,
    ): Long = ((value % modulus) + modulus) % modulus

    private fun floorMod(
        value: Double,
        modulus: Double,
    ): Double = ((value % modulus) + modulus) % modulus

    private val SUPPORTED_CONSTELLATIONS =
        setOf(Constellation.GPS, Constellation.GALILEO, Constellation.BEIDOU)
}
