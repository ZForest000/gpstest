package com.example.gpstest.domain.model

/**
 * 精度衰减因子（Dilution of Precision）等级。
 * DOP 是描述卫星几何分布对定位精度影响的无量纲数，数字越小越好。
 * - EXCELLENT: PDOP < 1，极佳几何分布
 * - GOOD: PDOP 1-2，良好
 * - MODERATE: PDOP 2-5，可接受
 * - FAIR: PDOP 5-10，精度下降明显
 * - POOR: PDOP >= 10，几何分布差，定位不可靠
 */
enum class DopQuality {
    EXCELLENT, // < 1
    GOOD, // 1 <= x < 2
    MODERATE, // 2 <= x < 5
    FAIR, // 5 <= x < 10
    POOR, // >= 10
}

data class DopInfo(
    val pdop: Double,
    val hdop: Double,
    val vdop: Double,
    val satelliteCount: Int,
) {
    val quality: DopQuality
        get() =
            when {
                pdop < 1 -> DopQuality.EXCELLENT
                pdop < 2 -> DopQuality.GOOD
                pdop < 5 -> DopQuality.MODERATE
                pdop < 10 -> DopQuality.FAIR
                else -> DopQuality.POOR
            }
}
