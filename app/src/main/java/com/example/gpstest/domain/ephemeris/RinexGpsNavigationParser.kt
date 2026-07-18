package com.example.gpstest.domain.ephemeris

/** RINEX 3/4 导航文本中的 GPS 广播星历解析器。 */
object RinexGpsNavigationParser {
    fun parse(text: String): List<GpsBroadcastEphemeris> {
        val lines = text.lineSequence().toList()
        val headerEnd = lines.indexOfFirst { it.contains("END OF HEADER") }
        if (headerEnd < 0) return emptyList()
        val result = mutableListOf<GpsBroadcastEphemeris>()
        var index = headerEnd + 1
        while (index + GPS_RECORD_LINE_COUNT <= lines.size) {
            val firstLine = lines[index]
            if (!firstLine.startsWith("G")) {
                index++
                continue
            }
            val record = lines.subList(index, index + GPS_RECORD_LINE_COUNT)
            parseRecord(record)?.let(result::add)
            index += GPS_RECORD_LINE_COUNT
        }
        return result
    }

    private fun parseRecord(lines: List<String>): GpsBroadcastEphemeris? =
        runCatching {
            val svid =
                lines[0]
                    .take(3)
                    .trim()
                    .removePrefix("G")
                    .toInt()
            val clock = fixedValues(lines[0], FIRST_CLOCK_START, CLOCK_FIELD_COUNT)
            val line2 = values(lines[1])
            val line3 = values(lines[2])
            val line4 = values(lines[3])
            val line5 = values(lines[4])
            val line6 = values(lines[5])
            val line7 = values(lines[6])
            GpsBroadcastEphemeris(
                svid = svid,
                weekNumber = line6[2].toInt(),
                toeSeconds = line4[0],
                tocSeconds = line4[0],
                sqrtA = line3[3],
                eccentricity = line3[1],
                inclinationRadians = line5[0],
                longitudeOfAscendingNodeRadians = line4[2],
                argumentOfPerigeeRadians = line5[2],
                meanAnomalyRadians = line2[3],
                deltaN = line2[2],
                inclinationRate = line6[0],
                longitudeRate = line5[3],
                cuc = line3[0],
                cus = line3[2],
                cic = line4[1],
                cis = line4[3],
                crc = line5[1],
                crs = line2[1],
                af0Seconds = clock[0],
                af1SecondsPerSecond = clock[1],
                af2SecondsPerSecondSquared = clock[2],
                groupDelaySeconds = line7[2],
            )
        }.getOrNull()

    private fun values(line: String): List<Double> {
        val values = fixedValues(line, CONTINUATION_VALUE_START, FIELD_COUNT)
        require(values.size >= 4)
        return values
    }

    private fun fixedValues(
        line: String,
        start: Int,
        count: Int,
    ): List<Double> {
        if (line.length >= start + FIELD_WIDTH) {
            val fixed =
                runCatching {
                    (0 until count).map { index ->
                        number(
                            line.substring(start + index * FIELD_WIDTH, minOf(line.length, start + (index + 1) * FIELD_WIDTH)).trim(),
                        )
                    }
                }.getOrNull()
            if (fixed?.size == count) return fixed
        }
        return line.trim().split(WHITESPACE).map(::number)
    }

    private fun number(value: String): Double = value.replace('D', 'E').replace('d', 'E').toDouble()

    private const val GPS_RECORD_LINE_COUNT = 8
    private const val FIRST_CLOCK_START = 23
    private const val CONTINUATION_VALUE_START = 4
    private const val FIELD_WIDTH = 19
    private const val FIELD_COUNT = 4
    private const val CLOCK_FIELD_COUNT = 3
    private val WHITESPACE = Regex("\\s+")
}
