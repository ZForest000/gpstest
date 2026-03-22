package com.example.gpstest.util

import android.location.GnssStatus
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
        GnssStatus.CONSTELLATION_GPS -> "GPS"
        GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
        GnssStatus.CONSTELLATION_BEIDOU -> "BeiDou"
        GnssStatus.CONSTELLATION_QZSS -> "QZSS"
        GnssStatus.CONSTELLATION_GALILEO -> "Galileo"
        GnssStatus.CONSTELLATION_NAVIC -> "NavIC"
        GnssStatus.CONSTELLATION_IRNSS -> "IRNSS"
        GnssStatus.CONSTELLATION_SBAS -> "SBAS"
        GnssStatus.CONSTELLATION_UNKNOWN -> "Unknown"
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
        GnssStatus.CONSTELLATION_GPS -> Blue40
        GnssStatus.CONSTELLATION_GLONASS -> Red40
        GnssStatus.CONSTELLATION_BEIDOU -> Yellow40
        GnssStatus.CONSTELLATION_QZSS -> Purple40
        GnssStatus.CONSTELLATION_GALILEO -> Teal40
        GnssStatus.CONSTELLATION_NAVIC -> Orange40
        GnssStatus.CONSTELLATION_IRNSS -> Pink40
        GnssStatus.CONSTELLATION_SBAS -> Color.Gray
        GnssStatus.CONSTELLATION_UNKNOWN -> Color.LightGray
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
        GnssStatus.CONSTELLATION_GPS -> "Global Positioning System (USA)"
        GnssStatus.CONSTELLATION_GLONASS -> "Global Navigation Satellite System (Russia)"
        GnssStatus.CONSTELLATION_BEIDOU -> "BeiDou Navigation Satellite System (China)"
        GnssStatus.CONSTELLATION_QZSS -> "Quasi-Zenith Satellite System (Japan)"
        GnssStatus.CONSTELLATION_GALILEO -> "Galileo (European Union)"
        GnssStatus.CONSTELLATION_NAVIC -> "Navigation with Indian Constellation (India)"
        GnssStatus.CONSTELLATION_IRNSS -> "Indian Regional Navigation Satellite System (India)"
        GnssStatus.CONSTELLATION_SBAS -> "Satellite-Based Augmentation System"
        GnssStatus.CONSTELLATION_UNKNOWN -> "Unknown Constellation"
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
        GnssStatus.CONSTELLATION_GPS -> 32
        GnssStatus.CONSTELLATION_GLONASS -> 24
        GnssStatus.CONSTELLATION_BEIDOU -> 45
        GnssStatus.CONSTELLATION_QZSS -> 4
        GnssStatus.CONSTELLATION_GALILEO -> 30
        GnssStatus.CONSTELLATION_NAVIC -> 7
        GnssStatus.CONSTELLATION_IRNSS -> 7
        GnssStatus.CONSTELLATION_SBAS -> 0 // 变化的
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
        GnssStatus.CONSTELLATION_GPS -> listOf("L1", "L2", "L5")
        GnssStatus.CONSTELLATION_GLONASS -> listOf("L1", "L2", "L3")
        GnssStatus.CONSTELLATION_BEIDOU -> listOf("B1", "B2", "B3")
        GnssStatus.CONSTELLATION_QZSS -> listOf("L1", "L2", "L5", "L6")
        GnssStatus.CONSTELLATION_GALILEO -> listOf("E1", "E5", "E6")
        GnssStatus.CONSTELLATION_NAVIC -> listOf("L5", "S")
        GnssStatus.CONSTELLATION_IRNSS -> listOf("L5", "S")
        else -> emptyList()
    }
}
