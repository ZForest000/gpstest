package com.example.gpstest.domain.model

enum class DopQuality {
    EXCELLENT,  // < 1
    GOOD,       // 1 <= x < 2
    MODERATE,   // 2 <= x < 5
    FAIR,       // 5 <= x < 10
    POOR        // >= 10
}

data class DopInfo(
    val pdop: Double,
    val hdop: Double,
    val vdop: Double,
    val satelliteCount: Int
) {
    val quality: DopQuality
        get() = when {
            pdop < 1 -> DopQuality.EXCELLENT
            pdop < 2 -> DopQuality.GOOD
            pdop < 5 -> DopQuality.MODERATE
            pdop < 10 -> DopQuality.FAIR
            else -> DopQuality.POOR
        }
}
