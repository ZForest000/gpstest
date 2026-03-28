package com.example.gpstest.domain.model

data class GnssClockData(
    val timeNanos: Long,
    val biasNanos: Double?,
    val fullBiasNanos: Long?,
    val driftNanosPerSecond: Double?,
    val biasUncertaintyNanos: Double?,
    val driftUncertaintyNanosPerSecond: Double?,
    val hardwareClockDiscontinuityCount: Int
) {
    val totalBiasNanos: Double?
        get() {
            return if (fullBiasNanos != null && biasNanos != null) {
                fullBiasNanos.toDouble() + biasNanos
            } else null
        }

    val totalBiasMicroseconds: Double?
        get() = totalBiasNanos?.div(1000.0)

    val driftMicrosecondsPerSecond: Double?
        get() = driftNanosPerSecond?.div(1000.0)
}
