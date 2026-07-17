package com.example.gpstest.domain.util

import com.example.gpstest.domain.model.NmeaParsedSnapshot
import com.example.gpstest.domain.model.NmeaSentence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NmeaParserTest {
    private val gga = "\$GPGGA,092750.000,5321.6802,N,00606.7072,W,1,08,1.03,61.7,M,55.2,M,,"
    private val rmc = "\$GPRMC,092750.0,A,5321.68,N,00606.71,W,0.123,45.6,010120,,,A"

    // --- parseGga ---

    @Test
    fun `parseGga extracts time and quality`() {
        val info = NmeaParser.parseGga(gga)!!
        assertEquals("092750.000", info.time)
        assertEquals(1, info.fixQuality)
        assertEquals(8, info.numSatellites)
    }

    @Test
    fun `parseGga extracts north latitude`() {
        val info = NmeaParser.parseGga(gga)!!
        // 53 度 21.6802 分 → 53 + 21.6802/60 ≈ 53.36134
        assertEquals(53.3613, info.latitude!!, 0.0001)
    }

    @Test
    fun `parseGga extracts west longitude as negative`() {
        val info = NmeaParser.parseGga(gga)!!
        // 6 度 06.7072 分 → 6 + 06.7072/60 ≈ 6.11179，西经取负
        assertEquals(-6.1118, info.longitude!!, 0.0001)
    }

    @Test
    fun `parseGga extracts hdop altitude and geoid`() {
        val info = NmeaParser.parseGga(gga)!!
        assertEquals(1.03f, info.hdop!!, 0.001f)
        assertEquals(61.7f, info.altitude!!, 0.001f)
        assertEquals(55.2f, info.geoidSep!!, 0.001f)
    }

    @Test
    fun `parseGga returns null for truncated message`() {
        assertNull(NmeaParser.parseGga("\$GPGGA,092750"))
    }

    @Test
    fun `parseGga handles missing latitude`() {
        val info = NmeaParser.parseGga("\$GPGGA,,,,,,0,,,M,,M,,")!!
        assertNull(info.latitude)
        assertNull(info.longitude)
        assertEquals(0, info.fixQuality)
    }

    // --- parseRmc ---

    @Test
    fun `parseRmc extracts status time and date`() {
        val info = NmeaParser.parseRmc(rmc)!!
        assertEquals('A', info.status)
        assertEquals("092750.0", info.time)
        assertEquals("010120", info.date)
    }

    @Test
    fun `parseRmc extracts sog and cog`() {
        val info = NmeaParser.parseRmc(rmc)!!
        assertEquals(0.123f, info.sogKnots!!, 0.0001f)
        assertEquals(45.6f, info.cogDegrees!!, 0.0001f)
    }

    @Test
    fun `parseRmc extracts south latitude as negative`() {
        val info = NmeaParser.parseRmc("\$GPRMC,0,A,5321.68,S,00606.71,E,0,0,010120,,,A")!!
        assertEquals(-53.3613, info.latitude!!, 0.0001)
    }

    @Test
    fun `parseRmc returns null for truncated message`() {
        assertNull(NmeaParser.parseRmc("\$GPRMC,092750"))
    }

    // --- parseLatLon ---

    @Test
    fun `parseLatLon returns null for blank value`() {
        assertNull(NmeaParser.parseLatLon("", "N", isLongitude = false))
        assertNull(NmeaParser.parseLatLon(null, "N", isLongitude = false))
    }

    @Test
    fun `parseLatLon returns null for invalid degrees`() {
        assertNull(NmeaParser.parseLatLon("AB21.68", "N", isLongitude = false))
    }

    @Test
    fun `parseLatLon returns null for unknown direction`() {
        assertNull(NmeaParser.parseLatLon("5321.68", "Q", isLongitude = false))
    }

    @Test
    fun `parseLatLon accepts value without direction`() {
        // 无方向时按正值返回
        assertEquals(53.3613, NmeaParser.parseLatLon("5321.68", null, isLongitude = false)!!, 0.0001)
    }

    @Test
    fun `parseLatLon returns null when latitude too short`() {
        assertNull(NmeaParser.parseLatLon("53", "N", isLongitude = false))
    }

    // --- updateSnapshot ---

    @Test
    fun `updateSnapshot sets gga and keeps null rmc`() {
        val snapshot = NmeaParser.updateSnapshot(NmeaParsedSnapshot(), NmeaSentence(0L, gga))
        assertEquals("092750.000", snapshot.gga?.time)
        assertEquals(null, snapshot.rmc)
    }

    @Test
    fun `updateSnapshot sets rmc and preserves existing gga`() {
        val withGga = NmeaParser.updateSnapshot(NmeaParsedSnapshot(), NmeaSentence(0L, gga))
        val withRmc = NmeaParser.updateSnapshot(withGga, NmeaSentence(0L, rmc))
        assertEquals("092750.000", withRmc.gga?.time)
        assertEquals("010120", withRmc.rmc?.date)
    }

    @Test
    fun `updateSnapshot ignores non gga rmc sentences`() {
        val initial = NmeaParser.updateSnapshot(NmeaParsedSnapshot(), NmeaSentence(0L, gga))
        val result = NmeaParser.updateSnapshot(initial, NmeaSentence(0L, "\$GPGSV,1,1,01"))
        assertEquals(initial, result)
    }

    @Test
    fun `updateSnapshot returns same instance reference for ignored types`() {
        val initial = NmeaParsedSnapshot()
        val result = NmeaParser.updateSnapshot(initial, NmeaSentence(0L, "\$GPGSV,1,1,01"))
        assertEquals(initial, result)
    }
}
