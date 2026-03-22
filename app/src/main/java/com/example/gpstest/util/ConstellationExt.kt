package com.example.gpstest.util

import androidx.compose.ui.graphics.Color
import com.example.gpstest.ui.theme.Blue40
import com.example.gpstest.ui.theme.Orange40
import com.example.gpstest.ui.theme.Pink40
import com.example.gpstest.ui.theme.Purple40
import com.example.gpstest.ui.theme.Red40
import com.example.gpstest.ui.theme.Teal40
import com.example.gpstest.ui.theme.Yellow40

/**
 * GNSS星座类型扩展工具
 */

/**
 * 获取星座名称
 *
 * @param constellationType 星座类型常量
 * @return 星座名称
 */
fun getConstellationName(constellationType: Int): String {
    return when (constellationType) {
        1 -> "GPS"
        3 -> "GLONASS"
        5 -> "BeiDou"
        4 -> "QZSS"
        6 -> "Galileo"
        7 -> "NavIC"
        // Note: IRNSS (old name for NavIC) uses same value
        2 -> "SBAS"
        0 -> "Unknown"
        else -> "Constellation $constellationType"
    }
}

/**
 * 获取星座颜色
 *
 * @param constellationType 星座类型常量
 * @return 星座对应的颜色
 */
fun getConstellationColor(constellationType: Int): Color {
    return when (constellationType) {
        1 -> Blue40
        3 -> Red40
        5 -> Yellow40
        4 -> Purple40
        6 -> Teal40
        7 -> Orange40
        2 -> Color.Gray
        0 -> Color.LightGray
        else -> Color.Gray
    }
}

/**
 * 获取星座描述
 *
 * @param constellationType 星座类型常量
 * @return 星座的详细描述
 */
fun getConstellationDescription(constellationType: Int): String {
    return when (constellationType) {
        1 -> "Global Positioning System (USA)"
        3 -> "Global Navigation Satellite System (Russia)"
        5 -> "BeiDou Navigation Satellite System (China)"
        4 -> "Quasi-Zenith Satellite System (Japan)"
        6 -> "Galileo (European Union)"
        7 -> "Navigation with Indian Constellation (India)"
        2 -> "Satellite-Based Augmentation System"
        0 -> "Unknown Constellation"
        else -> "Unknown Constellation Type: $constellationType"
    }
}

/**
 * 获取星座的卫星数量
 *
 * @param constellationType 星座类型常量
 * @return 该星座的理论卫星数量
 */
fun getConstellationSatelliteCount(constellationType: Int): Int {
    return when (constellationType) {
        1 -> 32  // GPS
        3 -> 24  // GLONASS
        5 -> 45  // BeiDou
        4 -> 4   // QZSS
        6 -> 30  // Galileo
        7 -> 7   // NavIC/IRNSS
        2 -> 0   // SBAS (varies)
        else -> 0
    }
}

/**
 * 检查是否为全球导航卫星系统
 *
 * @param constellationType 星座类型常量
 * @return 是否为全球系统
 */
fun isGlobalConstellation(constellationType: Int): Boolean {
    return when (constellationType) {
        GnssStatus.CONSTELLATION_GPS,
        GnssStatus.CONSTELLATION_GLONASS,
        GnssStatus.CONSTELLATION_BEIDOU,
        GnssStatus.CONSTELLATION_GALILEO -> true
        else -> false
    }
}

/**
 * 检查是否为区域导航卫星系统
 *
 * @param constellationType 星座类型常量
 * @return 是否为区域系统
 */
fun isRegionalConstellation(constellationType: Int): Boolean {
    return when (constellationType) {
        GnssStatus.CONSTELLATION_QZSS,
        GnssStatus.CONSTELLATION_NAVIC,
        GnssStatus.CONSTELLATION_IRNSS -> true
        else -> false
    }
}

/**
 * 检查是否为增强系统
 *
 * @param constellationType 星座类型常量
 * @return 是否为增强系统
 */
fun isAugmentationSystem(constellationType: Int): Boolean {
    return constellationType == GnssStatus.CONSTELLATION_SBAS
}

/**
 * 获取星座的频率类型
 *
 * @param constellationType 星座类型常量
 * @return 频率类型列表
 */
fun getConstellationFrequencies(constellationType: Int): List<String> {
    return when (constellationType) {
        1 -> listOf("L1", "L2", "L5")  // GPS
        3 -> listOf("L1", "L2", "L3")  // GLONASS
        5 -> listOf("B1", "B2", "B3")  // BeiDou
        4 -> listOf("L1", "L2", "L5", "L6")  // QZSS
        6 -> listOf("E1", "E5", "E6")  // Galileo
        7 -> listOf("L5", "S")  // NavIC
        else -> emptyList()
    }
}
