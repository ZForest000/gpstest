package com.example.gpstest.data.source

/**
 * 解析 `dumpsys location` 命令输出的 GNSS KPI 块。
 *
 * dumpsys 输出格式因 Android 版本和 OEM（Qualcomm/MTK 等）而异，本解析器采用
 * 宽松正则 + 多变体匹配策略：任一字段未命中即返回 null，不抛异常。
 *
 * 目标字段（典型出现在 GNSS_KPI_START / GNSS_KPI_END 之间）：
 * - `Used-in-fix constellation types: GPS GLONASS ...` → [DumpsysGnssData.usedInFixConstellations]
 * - `used in fix: <N>` → [DumpsysGnssData.measurementCount]
 * - `Avg Baseband Cn0: <f>` 或 `Top 4 Avg Cn0: <f>` 等 OEM 变体 → [DumpsysGnssData.avgBasebandCn0]
 */
internal object DumpsysParser {
    // "used in fix: 12" —— 出现多次取最后一次（GNSS KPI 块内）
    private val USED_IN_FIX_COUNT_REGEX = Regex("""used\s+in\s+fix\s*:\s*(\d+)""", RegexOption.IGNORE_CASE)

    // "Used-in-fix constellation types: GPS GLONASS BEIDOU"
    private val CONSTELLATIONS_REGEX =
        Regex("""used-in-fix\s+constellation\s+types\s*:\s*(.+)""", RegexOption.IGNORE_CASE)

    // 基带 C/N0 变体：Qualcomm 常见 "Avg Baseband Cn0"，部分 OEM 输出 "Top 4 Avg Cn0" 等
    // 优先匹配 "Baseband Cn0"（语义最准确），回退到 "Avg Cn0"（前缀可能有 "Top N"）
    private val BASEBAND_CN0_REGEX =
        Regex("""baseband\s*cn0\s*:\s*([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)

    private val AVG_CN0_REGEX =
        Regex("""avg\s*cn0\s*:\s*([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)

    /**
     * 解析 dumpsys location 输出。任一字段缺失返回 null；整体无可用数据返回 null。
     */
    fun parse(output: String): DumpsysGnssData? {
        if (output.isBlank()) return null

        val measurementCount = parseMeasurementCount(output)
        val constellations = parseConstellations(output)
        val avgBasebandCn0 = parseAvgBasebandCn0(output)

        // 三者全空说明输出不含 GNSS KPI 信息（如非 location 服务或 OEM 不支持）
        if (measurementCount == 0 && constellations.isEmpty() && avgBasebandCn0 == null) {
            return null
        }

        return DumpsysGnssData(
            avgBasebandCn0 = avgBasebandCn0,
            measurementCount = measurementCount,
            usedInFixConstellations = constellations,
        )
    }

    private fun parseMeasurementCount(output: String): Int =
        USED_IN_FIX_COUNT_REGEX
            .findAll(output)
            .lastOrNull()
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 0

    private fun parseConstellations(output: String): List<String> =
        CONSTELLATIONS_REGEX
            .find(output)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.split(Regex("\\s+"))
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    private fun parseAvgBasebandCn0(output: String): Float? {
        // 优先 baseband（语义最准确），回退 avg（兼容 "Top N Avg Cn0" 变体）
        return BASEBAND_CN0_REGEX
            .find(output)
            ?.groupValues
            ?.getOrNull(1)
            ?.toFloatOrNull()
            ?: AVG_CN0_REGEX
                .find(output)
                ?.groupValues
                ?.getOrNull(1)
                ?.toFloatOrNull()
    }
}
