package com.example.gpstest.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DumpsysParserTest {
    // 真实 dumpsys location 输出的 GNSS KPI 块典型片段（Qualcomm 风格）
    private val typicalOutput =
        listOf(
            "Location Output Service: GNSS",
            "GNSS_KPI_START",
            "KPI logging per GPS fix",
            "used in fix: 0",
            "KPI logging after GPS fix",
            "Number of GPS fixes: 1",
            "used in fix: 941",
            "Top 4 Avg Cn0: 41.2",
            "Avg Baseband Cn0: 38.5",
            "Used-in-fix constellation types: GPS GLONASS QZSS BEIDOU GALILEO",
            "GNSS_KPI_END",
        ).joinToString("\n")

    // --- 完整典型输出 ---

    @Test
    fun `parse extracts all fields from typical Qualcomm output`() {
        val result = DumpsysParser.parse(typicalOutput)
        assertEquals(941, result?.measurementCount)
        assertEquals(38.5f, result?.avgBasebandCn0)
        assertEquals(
            listOf("GPS", "GLONASS", "QZSS", "BEIDOU", "GALILEO"),
            result?.usedInFixConstellations,
        )
    }

    // --- measurementCount（取最后一个 used-in-fix）---

    @Test
    fun `parse uses last used-in-fix count when multiple matches present`() {
        // 典型 dumpsys 有 "used in fix: 0"（fix 前）和 "used in fix: 941"（fix 后）
        // 解析器应取最后出现的（实际测量计数）
        val result = DumpsysParser.parse(typicalOutput)
        assertEquals(941, result?.measurementCount)
    }

    @Test
    fun `parse tolerates irregular whitespace in used-in-fix`() {
        val output = "used  in   fix:    42"
        val result = DumpsysParser.parse(output)
        assertEquals(42, result?.measurementCount)
    }

    // --- constellations ---

    @Test
    fun `parse extracts single constellation`() {
        val output = "Used-in-fix constellation types: GPS"
        val result = DumpsysParser.parse(output)
        assertEquals(listOf("GPS"), result?.usedInFixConstellations)
    }

    @Test
    fun `parse returns empty list when constellations line absent`() {
        val output = "used in fix: 5\nAvg Baseband Cn0: 30.0"
        val result = DumpsysParser.parse(output)
        assertEquals(emptyList<String>(), result?.usedInFixConstellations)
    }

    // --- avgBasebandCn0 变体 ---

    @Test
    fun `parse matches Top 4 Avg Cn0 variant`() {
        val output = "used in fix: 1\nTop 4 Avg Cn0: 42.7"
        val result = DumpsysParser.parse(output)
        assertEquals(42.7f, result?.avgBasebandCn0)
    }

    @Test
    fun `parse matches lowercase cn0 label`() {
        val output = "used in fix: 1\navg baseband cn0: 35.1"
        val result = DumpsysParser.parse(output)
        assertEquals(35.1f, result?.avgBasebandCn0)
    }

    @Test
    fun `parse returns null cn0 when absent`() {
        val output = "used in fix: 1\nUsed-in-fix constellation types: GPS"
        val result = DumpsysParser.parse(output)
        assertNull(result?.avgBasebandCn0)
    }

    // --- 降级与边界 ---

    @Test
    fun `parse returns null for empty input`() {
        assertNull(DumpsysParser.parse(""))
    }

    @Test
    fun `parse returns null for blank input`() {
        assertNull(DumpsysParser.parse("   \n  \t "))
    }

    @Test
    fun `parse returns null when no KPI fields present`() {
        val output =
            listOf(
                "Some other service dump",
                "nothing relevant here",
                "LocationManager:",
                "  passive provider",
            ).joinToString("\n")
        assertNull(DumpsysParser.parse(output))
    }

    @Test
    fun `parse returns partial data when only some fields present`() {
        // 仅星座列表存在，count 和 cn0 缺失
        val output = "Used-in-fix constellation types: GPS GALILEO"
        val result = DumpsysParser.parse(output)
        assertEquals(0, result?.measurementCount)
        assertNull(result?.avgBasebandCn0)
        assertEquals(listOf("GPS", "GALILEO"), result?.usedInFixConstellations)
    }

    // --- OEM 差异（MTK 风格可能不同）---

    @Test
    fun `parse tolerates unknown constellations without crashing`() {
        val output = "used in fix: 3\nUsed-in-fix constellation types: GPS UNKNOWN_CONSTELLATION_X"
        val result = DumpsysParser.parse(output)
        assertEquals(listOf("GPS", "UNKNOWN_CONSTELLATION_X"), result?.usedInFixConstellations)
    }

    @Test
    fun `parse handles integer-only cn0 value`() {
        val output = "used in fix: 1\nAvg Baseband Cn0: 38"
        val result = DumpsysParser.parse(output)
        assertEquals(38f, result?.avgBasebandCn0)
    }
}
