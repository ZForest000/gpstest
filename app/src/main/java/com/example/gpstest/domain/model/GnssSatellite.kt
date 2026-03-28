package com.example.gpstest.domain.model

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
    val basebandCn0DbHz: Float? = null
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
}

enum class SignalStrength {
    STRONG,
    MEDIUM,
    WEAK
}
