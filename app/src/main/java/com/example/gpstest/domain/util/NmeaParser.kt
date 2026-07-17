package com.example.gpstest.domain.util

import com.example.gpstest.domain.model.GgaInfo
import com.example.gpstest.domain.model.NmeaParsedSnapshot
import com.example.gpstest.domain.model.NmeaSentence
import com.example.gpstest.domain.model.RmcInfo

/**
 * 轻量级 NMEA 报文解析器，仅提取 UI 展示所需的 GGA / RMC 字段。
 *
 * 设计目标：纯函数、无状态、易测试；不做完整 NMEA 解析（不做校验和、不做单位换算之外的处理）。
 * 字段索引约定参见 [parseGga] 和 [parseRmc] 的注释。
 */
object NmeaParser {
    private const val GGA = "GGA"
    private const val RMC = "RMC"

    /**
     * 用新到达的报文更新当前快照。非 GGA/RMC 报文直接返回原快照。
     */
    fun updateSnapshot(
        snapshot: NmeaParsedSnapshot,
        sentence: NmeaSentence,
    ): NmeaParsedSnapshot {
        val parts = splitFields(sentence.message)
        return when (sentence.type) {
            GGA -> parseGga(parts)?.let { snapshot.copy(gga = it) } ?: snapshot
            RMC -> parseRmc(parts)?.let { snapshot.copy(rmc = it) } ?: snapshot
            else -> snapshot
        }
    }

    /**
     * 解析 GGA 报文字段。
     *
     * 字段索引（按逗号切分，含 `$--GGA` 前缀）：
     * - 0: `$--GGA`
     * - 1: UTC 时间 hhmmss.ss
     * - 2: 纬度 ddmm.mmmm
     * - 3: N / S
     * - 4: 经度 dddmm.mmmm
     * - 5: E / W
     * - 6: 定位质量
     * - 7: 卫星数
     * - 8: HDOP
     * - 9: 海拔
     * - 11: 大地水准面差距
     */
    fun parseGga(message: String): GgaInfo? = parseGga(splitFields(message))

    private fun parseGga(parts: List<String>): GgaInfo? {
        if (parts.size < 10) return null
        return GgaInfo(
            time = parts.getOrNull(1)?.takeIf { it.isNotBlank() },
            latitude = parseLatLon(parts.getOrNull(2), parts.getOrNull(3), isLongitude = false),
            longitude = parseLatLon(parts.getOrNull(4), parts.getOrNull(5), isLongitude = true),
            fixQuality = parts.getOrNull(6)?.toIntOrNull() ?: 0,
            numSatellites = parts.getOrNull(7)?.toIntOrNull() ?: 0,
            hdop = parts.getOrNull(8)?.toFloatOrNull(),
            altitude = parts.getOrNull(9)?.toFloatOrNull(),
            geoidSep = parts.getOrNull(11)?.toFloatOrNull(),
        )
    }

    /**
     * 解析 RMC 报文字段。
     *
     * 字段索引：
     * - 0: `$--RMC`
     * - 1: UTC 时间 hhmmss.ss
     * - 2: 状态 A/V
     * - 3: 纬度 ddmm.mmmm
     * - 4: N / S
     * - 5: 经度 dddmm.mmmm
     * - 6: E / W
     * - 7: 对地航速（节）
     * - 8: 对地航向（度）
     * - 9: 日期 ddmmyy
     * - 10: 磁偏角（度）
     */
    fun parseRmc(message: String): RmcInfo? = parseRmc(splitFields(message))

    private fun parseRmc(parts: List<String>): RmcInfo? {
        if (parts.size < 10) return null
        return RmcInfo(
            time = parts.getOrNull(1)?.takeIf { it.isNotBlank() },
            status = parts.getOrNull(2)?.firstOrNull(),
            latitude = parseLatLon(parts.getOrNull(3), parts.getOrNull(4), isLongitude = false),
            longitude = parseLatLon(parts.getOrNull(5), parts.getOrNull(6), isLongitude = true),
            sogKnots = parts.getOrNull(7)?.toFloatOrNull(),
            cogDegrees = parts.getOrNull(8)?.toFloatOrNull(),
            date = parts.getOrNull(9)?.takeIf { it.isNotBlank() },
            magVariation = parts.getOrNull(10)?.toFloatOrNull(),
        )
    }

    /**
     * 将 ddmm.mmmm / dddmm.mmmm 转换为十进制度。
     *
     * - 纬度：度 = 前 2 位，分 = 余下部分
     * - 经度：度 = 前 3 位，分 = 余下部分
     * - S / W 取负
     *
     * 输入为空或非法时返回 null。
     */
    fun parseLatLon(
        value: String?,
        direction: String?,
        isLongitude: Boolean,
    ): Double? {
        if (value.isNullOrBlank()) return null
        val degLen = if (isLongitude) 3 else 2
        if (value.length <= degLen) return null
        val degrees = value.substring(0, degLen).toDoubleOrNull() ?: return null
        val minutes = value.substring(degLen).toDoubleOrNull() ?: return null
        val decimal = degrees + minutes / 60.0
        return when (direction?.trim()?.uppercase()?.firstOrNull()) {
            'S', 'W' -> -decimal
            'N', 'E' -> decimal
            null -> decimal
            else -> null
        }
    }

    /** 按逗号切分报文，剥离行尾校验和 `*XX`。 */
    private fun splitFields(message: String): List<String> {
        val noChecksum =
            message.substringBefore('*').let {
                if (it.contains('\r') || it.contains('\n')) it.trim() else it
            }
        return noChecksum.split(",")
    }
}
