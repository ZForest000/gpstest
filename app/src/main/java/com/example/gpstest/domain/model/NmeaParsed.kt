package com.example.gpstest.domain.model

/**
 * GGA 报文解析结果（定位信息）。
 *
 * @param time 时间戳字符串 hhmmss.ss（原始字段，未做时区转换）
 * @param latitude 纬度（十进制度），南纬为负
 * @param longitude 经度（十进制度），西经为负
 * @param fixQuality 定位质量：0=无效 1=GPS 2=DGPS 等
 * @param numSatellites 参与定位的卫星数
 * @param hdop 水平精度因子
 * @param altitude 海拔（米）
 * @param geoidSep 大地水准面差距（米）
 */
data class GgaInfo(
    val time: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val fixQuality: Int = 0,
    val numSatellites: Int = 0,
    val hdop: Float? = null,
    val altitude: Float? = null,
    val geoidSep: Float? = null,
)

/**
 * RMC 报文解析结果（推荐最小定位信息）。
 *
 * @param time 时间戳字符串 hhmmss.ss
 * @param status 状态：A=有效 V=无效
 * @param latitude 纬度（十进制度）
 * @param longitude 经度（十进制度）
 * @param sogKnots 对地航速（节）
 * @param cogDegrees 对地航向（度）
 * @param date 日期字符串 ddmmyy
 * @param magVariation 磁偏角（度）
 */
data class RmcInfo(
    val time: String? = null,
    val status: Char? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val sogKnots: Float? = null,
    val cogDegrees: Float? = null,
    val date: String? = null,
    val magVariation: Float? = null,
)

/**
 * 最近一次 GGA / RMC 报文的合并快照，用于 UI 展示定位摘要。
 */
data class NmeaParsedSnapshot(
    val gga: GgaInfo? = null,
    val rmc: RmcInfo? = null,
)
