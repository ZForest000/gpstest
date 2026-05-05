package com.example.gpstest.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DopInfoTest {
    @Test
    fun `quality is EXCELLENT when pdop less than 1`() {
        val info = DopInfo(pdop = 0.5, hdop = 0.3, vdop = 0.4, satelliteCount = 10)
        assertEquals(DopQuality.EXCELLENT, info.quality)
    }

    @Test
    fun `quality is EXCELLENT when pdop is zero`() {
        val info = DopInfo(pdop = 0.0, hdop = 0.0, vdop = 0.0, satelliteCount = 0)
        assertEquals(DopQuality.EXCELLENT, info.quality)
    }

    @Test
    fun `quality is GOOD when pdop is 1`() {
        val info = DopInfo(pdop = 1.0, hdop = 0.8, vdop = 0.6, satelliteCount = 8)
        assertEquals(DopQuality.GOOD, info.quality)
    }

    @Test
    fun `quality is GOOD when pdop is 1_9`() {
        val info = DopInfo(pdop = 1.9, hdop = 1.0, vdop = 1.5, satelliteCount = 8)
        assertEquals(DopQuality.GOOD, info.quality)
    }

    @Test
    fun `quality is MODERATE when pdop is 2`() {
        val info = DopInfo(pdop = 2.0, hdop = 1.5, vdop = 1.3, satelliteCount = 6)
        assertEquals(DopQuality.MODERATE, info.quality)
    }

    @Test
    fun `quality is MODERATE when pdop is 4_9`() {
        val info = DopInfo(pdop = 4.9, hdop = 3.0, vdop = 3.8, satelliteCount = 5)
        assertEquals(DopQuality.MODERATE, info.quality)
    }

    @Test
    fun `quality is FAIR when pdop is 5`() {
        val info = DopInfo(pdop = 5.0, hdop = 3.5, vdop = 3.5, satelliteCount = 4)
        assertEquals(DopQuality.FAIR, info.quality)
    }

    @Test
    fun `quality is FAIR when pdop is 9_9`() {
        val info = DopInfo(pdop = 9.9, hdop = 7.0, vdop = 7.0, satelliteCount = 4)
        assertEquals(DopQuality.FAIR, info.quality)
    }

    @Test
    fun `quality is POOR when pdop is 10`() {
        val info = DopInfo(pdop = 10.0, hdop = 7.0, vdop = 7.0, satelliteCount = 3)
        assertEquals(DopQuality.POOR, info.quality)
    }

    @Test
    fun `quality is POOR for very large pdop`() {
        val info = DopInfo(pdop = 50.0, hdop = 35.0, vdop = 35.0, satelliteCount = 2)
        assertEquals(DopQuality.POOR, info.quality)
    }
}
