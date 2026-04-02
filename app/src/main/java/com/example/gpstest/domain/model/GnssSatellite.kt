package com.example.gpstest.domain.model

import android.location.GnssMeasurement

enum class MultipathIndicator {
    UNKNOWN,
    DETECTED,
    NOT_DETECTED;

    companion object {
        fun fromInt(value: Int): MultipathIndicator {
            return when (value) {
                1 -> DETECTED
                2 -> NOT_DETECTED
                else -> UNKNOWN
            }
        }
    }
}

data class GnssSatellite(
    val svid: Int,
    val constellation: Constellation,
    val cn0DbHz: Float,
    val azimuthDegrees: Float,
    val elevationDegrees: Float,
    val hasAlmanac: Boolean,
    val hasEphemeris: Boolean,
    val usedInFix: Boolean,
    val carrierFrequencyHz: Float?,
    val carrierCycles: Long?,
    val dopplerShiftHz: Double?,
    val timeNanos: Long,
    val agcLevelDb: Double? = null,
    val multipathIndicator: MultipathIndicator? = null,
    val basebandCn0DbHz: Float? = null,
    val accumulatedDeltaRangeMeters: Double? = null,
    val accumulatedDeltaRangeState: Int? = null,
    val accumulatedDeltaRangeUncertaintyMeters: Double? = null,
    val receivedSvTimeNanos: Long? = null,
    val receivedSvTimeUncertaintyNanos: Double? = null,
    val pseudorangeRateMetersPerSecond: Double? = null,
    val measurementState: Int? = null,
    val measurementCn0DbHz: Double? = null,
    val fullCarrierPhaseCycleCount: Long? = null
) {
    val group: SatelliteGroup
        get() = when {
            usedInFix -> SatelliteGroup.USED_IN_FIX
            cn0DbHz > 0 -> SatelliteGroup.VISIBLE_ONLY
            else -> SatelliteGroup.SEARCHING
        }

    val signalStrength: SignalStrength
        get() = when {
            cn0DbHz >= 35f -> SignalStrength.STRONG
            cn0DbHz >= 25f -> SignalStrength.MEDIUM
            else -> SignalStrength.WEAK
        }

    val isAdrValid: Boolean
        get() = accumulatedDeltaRangeState?.let { state ->
            (state and GnssMeasurement.ADR_STATE_VALID) != 0
        } ?: false

    val hasCycleSlip: Boolean
        get() = accumulatedDeltaRangeState?.let { state ->
            (state and GnssMeasurement.ADR_STATE_CYCLE_SLIP) != 0
        } ?: false

    val hasCarrierPhaseLock: Boolean
        get() = measurementState?.let { state ->
            (state and GnssMeasurement.STATE_TOW_DECODED) != 0
        } ?: false

    val hasCodeLock: Boolean
        get() = measurementState?.let { state ->
            (state and GnssMeasurement.STATE_CODE_LOCK) != 0
        } ?: false

    val hasBitSync: Boolean
        get() = measurementState?.let { state ->
            (state and GnssMeasurement.STATE_BIT_SYNC) != 0
        } ?: false

    val hasSubframeSync: Boolean
        get() = measurementState?.let { state ->
            (state and GnssMeasurement.STATE_SUBFRAME_SYNC) != 0
        } ?: false
}

enum class SignalStrength {
    STRONG,
    MEDIUM,
    WEAK
}
