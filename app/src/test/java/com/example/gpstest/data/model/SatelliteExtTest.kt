package com.example.gpstest.data.model

import com.example.gpstest.domain.model.Constellation
import com.example.gpstest.domain.model.GnssSatellite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SatelliteExtTest {
    private fun makeSatellite(
        constellation: Constellation = Constellation.GPS,
        cn0DbHz: Float = 30f,
        carrierFrequencyHz: Float? = null,
        svid: Int = 1,
    ): GnssSatellite =
        GnssSatellite(
            svid = svid,
            constellation = constellation,
            cn0DbHz = cn0DbHz,
            azimuthDegrees = 45f,
            elevationDegrees = 30f,
            hasAlmanac = true,
            hasEphemeris = true,
            usedInFix = true,
            carrierFrequencyHz = carrierFrequencyHz,
            carrierCycles = null,
            dopplerShiftHz = null,
            timeNanos = 0L,
        )

    // --- constellationType ---

    @Test
    fun `constellationType maps GPS to 1`() {
        assertEquals(1, makeSatellite(constellation = Constellation.GPS).constellationType)
    }

    @Test
    fun `constellationType maps SBAS to 2`() {
        assertEquals(2, makeSatellite(constellation = Constellation.SBAS).constellationType)
    }

    @Test
    fun `constellationType maps GLONASS to 3`() {
        assertEquals(3, makeSatellite(constellation = Constellation.GLONASS).constellationType)
    }

    @Test
    fun `constellationType maps QZSS to 4`() {
        assertEquals(4, makeSatellite(constellation = Constellation.QZSS).constellationType)
    }

    @Test
    fun `constellationType maps BEIDOU to 5`() {
        assertEquals(5, makeSatellite(constellation = Constellation.BEIDOU).constellationType)
    }

    @Test
    fun `constellationType maps GALILEO to 6`() {
        assertEquals(6, makeSatellite(constellation = Constellation.GALILEO).constellationType)
    }

    @Test
    fun `constellationType maps UNKNOWN to 0`() {
        assertEquals(0, makeSatellite(constellation = Constellation.UNKNOWN).constellationType)
    }

    // --- snrCn0 ---

    @Test
    fun `snrCn0 returns cn0DbHz value`() {
        assertEquals(30f, makeSatellite(cn0DbHz = 30f).snrCn0, 0.01f)
    }

    // --- carrierFrequencyMhz ---

    @Test
    fun `carrierFrequencyMhz converts Hz to MHz`() {
        val sat = makeSatellite(carrierFrequencyHz = 1_575_420_000f)
        assertEquals(1575.42f, sat.carrierFrequencyMhz!!, 0.01f)
    }

    @Test
    fun `carrierFrequencyMhz returns null when frequency is null`() {
        assertNull(makeSatellite(carrierFrequencyHz = null).carrierFrequencyMhz)
    }

    // --- hasCarrierFrequency ---

    @Test
    fun `hasCarrierFrequency is true for positive frequency`() {
        assertTrue(makeSatellite(carrierFrequencyHz = 1_575_420_000f).hasCarrierFrequency)
    }

    @Test
    fun `hasCarrierFrequency is false for null frequency`() {
        assertFalse(makeSatellite(carrierFrequencyHz = null).hasCarrierFrequency)
    }

    @Test
    fun `hasCarrierFrequency is false for zero frequency`() {
        assertFalse(makeSatellite(carrierFrequencyHz = 0f).hasCarrierFrequency)
    }

    @Test
    fun `hasCarrierFrequency is false for negative frequency`() {
        assertFalse(makeSatellite(carrierFrequencyHz = -1f).hasCarrierFrequency)
    }

    // --- svidWithConstellation ---

    @Test
    fun `svidWithConstellation combines constellation and svid`() {
        val sat = makeSatellite(constellation = Constellation.GPS, svid = 12)
        assertEquals("GPS-12", sat.svidWithConstellation)
    }

    @Test
    fun `svidWithConstellation works with BEIDOU`() {
        val sat = makeSatellite(constellation = Constellation.BEIDOU, svid = 5)
        assertEquals("BEIDOU-5", sat.svidWithConstellation)
    }

    // --- alias properties ---

    @Test
    fun `almanac returns hasAlmanac`() {
        assertTrue(makeSatellite().almanac)
    }

    @Test
    fun `ephemeris returns hasEphemeris`() {
        assertTrue(makeSatellite().ephemeris)
    }

    @Test
    fun `pseudoRandomNumber returns svid`() {
        assertEquals(7, makeSatellite(svid = 7).pseudoRandomNumber)
    }
}
