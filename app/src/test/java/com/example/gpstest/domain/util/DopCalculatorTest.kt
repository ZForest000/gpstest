package com.example.gpstest.domain.util

import com.example.gpstest.domain.model.Constellation
import com.example.gpstest.domain.model.GnssSatellite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DopCalculatorTest {

    private fun makeSatellite(
        svid: Int,
        azimuth: Float,
        elevation: Float,
        usedInFix: Boolean = true
    ): GnssSatellite {
        return GnssSatellite(
            svid = svid,
            constellation = Constellation.GPS,
            cn0DbHz = 40f,
            azimuthDegrees = azimuth,
            elevationDegrees = elevation,
            hasAlmanac = true,
            hasEphemeris = true,
            usedInFix = usedInFix,
            carrierFrequencyHz = null,
            carrierCycles = null,
            dopplerShiftHz = null,
            timeNanos = System.nanoTime()
        )
    }

    @Test
    fun `returns null when fewer than 4 satellites`() {
        val sats = listOf(
            makeSatellite(1, 0f, 45f),
            makeSatellite(2, 90f, 30f),
            makeSatellite(3, 180f, 60f)
        )
        assertNull(DopCalculator.calculate(sats))
    }

    @Test
    fun `returns null when all satellites at default zero position`() {
        val sats = listOf(
            makeSatellite(1, 0f, 0f),
            makeSatellite(2, 0f, 0f),
            makeSatellite(3, 0f, 0f),
            makeSatellite(4, 0f, 0f)
        )
        assertNull(DopCalculator.calculate(sats))
    }

    @Test
    fun `returns null for negative elevation satellites`() {
        val sats = listOf(
            makeSatellite(1, 0f, -10f),
            makeSatellite(2, 90f, -5f),
            makeSatellite(3, 180f, -1f),
            makeSatellite(4, 270f, -20f)
        )
        assertNull(DopCalculator.calculate(sats))
    }

    @Test
    fun `calculates DOP with 4 well-distributed satellites`() {
        // Use varied elevations to avoid singular matrix (same elevation makes z colinear with time)
        val sats = listOf(
            makeSatellite(1, 0f, 30f),    // North, low
            makeSatellite(2, 90f, 60f),   // East, high
            makeSatellite(3, 180f, 45f),  // South, mid
            makeSatellite(4, 270f, 15f)   // West, very low
        )
        val result = DopCalculator.calculate(sats)
        assertNotNull(result)
        result!!
        assert(result.pdop > 0) { "PDOP should be positive" }
        assert(result.hdop > 0) { "HDOP should be positive" }
        assert(result.vdop > 0) { "VDOP should be positive" }
        assertEquals(4, result.satelliteCount)
        assert(result.pdop >= result.hdop)
        val pdopSquared = result.pdop * result.pdop
        val hdopSquared = result.hdop * result.hdop
        val vdopSquared = result.vdop * result.vdop
        assertEquals(pdopSquared, hdopSquared + vdopSquared, 0.01)
    }

    @Test
    fun `excludes satellites not used in fix`() {
        val sats = listOf(
            makeSatellite(1, 0f, 45f, usedInFix = true),
            makeSatellite(2, 90f, 45f, usedInFix = true),
            makeSatellite(3, 180f, 45f, usedInFix = true),
            makeSatellite(4, 270f, 45f, usedInFix = false)
        )
        assertNull(DopCalculator.calculate(sats))
    }

    @Test
    fun `quality is good for well-distributed satellites`() {
        // 12 satellites with varied elevations
        val sats = (0..11).map { i ->
            makeSatellite(
                svid = i + 1,
                azimuth = (i * 30).toFloat(),
                elevation = 20f + (i % 4) * 15f  // 20°, 35°, 50°, 65°
            )
        }
        val result = DopCalculator.calculate(sats)
        assertNotNull(result)
        assert(result!!.pdop < 3.0) { "PDOP=${result.pdop} should be small with 12 satellites" }
    }

    @Test
    fun `handles many satellites`() {
        val sats = (0..23).map { i ->
            makeSatellite(
                svid = i + 1,
                azimuth = (i * 15).toFloat(),
                elevation = 30f + (i % 3) * 20f
            )
        }
        val result = DopCalculator.calculate(sats)
        assertNotNull(result)
        assertEquals(24, result!!.satelliteCount)
        assert(result.pdop > 0)
        assert(result.hdop > 0)
        assert(result.vdop > 0)
    }
}
