package com.example.gpstest.domain.model

enum class PseudorangeStatus {
    AVAILABLE,
    MISSING_MEASUREMENT,
    UNSUPPORTED_CONSTELLATION,
    MISSING_FULL_BIAS,
    MISSING_RECEIVED_SV_TIME,
    MISSING_CODE_LOCK,
    MISSING_TOW_DECODED,
    INVALID_INPUT,
    OUT_OF_RANGE,
}

data class PseudorangeMeasurement(
    val constellation: Constellation,
    val timeOffsetNanos: Double,
    val receivedSvTimeNanos: Long?,
    val receivedSvTimeUncertaintyNanos: Double?,
    val hasCodeLock: Boolean,
    val hasTowDecoded: Boolean,
)

data class PseudorangeResult(
    val meters: Double? = null,
    val uncertaintyMeters: Double? = null,
    val status: PseudorangeStatus,
)
