package com.example.gpstest.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GnssDataTest {
    private fun makeSatellite(
        cn0DbHz: Float = 30f,
        basebandCn0DbHz: Float? = null,
        usedInFix: Boolean = true,
    ): GnssSatellite =
        GnssSatellite(
            svid = 1,
            constellation = Constellation.GPS,
            cn0DbHz = cn0DbHz,
            azimuthDegrees = 0f,
            elevationDegrees = 45f,
            hasAlmanac = true,
            hasEphemeris = true,
            usedInFix = usedInFix,
            carrierFrequencyHz = null,
            carrierCycles = null,
            dopplerShiftHz = null,
            timeNanos = 0L,
            basebandCn0DbHz = basebandCn0DbHz,
        )

    // --- avgCn0DbHz ---

    @Test
    fun `avgCn0DbHz returns average of positive cn0 values`() {
        val data = GnssData(
            satellites = listOf(
                makeSatellite(cn0DbHz = 20f),
                makeSatellite(cn0DbHz = 40f),
                makeSatellite(cn0DbHz = 60f),
            ),
        )
        assertEquals(40f, data.avgCn0DbHz, 0.01f)
    }

    @Test
    fun `avgCn0DbHz excludes zero values`() {
        val data = GnssData(
            satellites = listOf(
                makeSatellite(cn0DbHz = 30f),
                makeSatellite(cn0DbHz = 0f),
            ),
        )
        assertEquals(30f, data.avgCn0DbHz, 0.01f)
    }

    @Test
    fun `avgCn0DbHz returns 0 for empty satellite list`() {
        val data = GnssData(satellites = emptyList())
        assertEquals(0f, data.avgCn0DbHz, 0.01f)
    }

    @Test
    fun `avgCn0DbHz returns 0 when all values are zero`() {
        val data = GnssData(
            satellites = listOf(
                makeSatellite(cn0DbHz = 0f),
                makeSatellite(cn0DbHz = 0f),
            ),
        )
        assertEquals(0f, data.avgCn0DbHz, 0.01f)
    }

    // --- avgBasebandCn0DbHz ---

    @Test
    fun `avgBasebandCn0DbHz returns average of positive baseband values`() {
        val data = GnssData(
            satellites = listOf(
                makeSatellite(basebandCn0DbHz = 15f),
                makeSatellite(basebandCn0DbHz = 25f),
            ),
        )
        assertEquals(20f, data.avgBasebandCn0DbHz, 0.01f)
    }

    @Test
    fun `avgBasebandCn0DbHz excludes null baseband values`() {
        val data = GnssData(
            satellites = listOf(
                makeSatellite(basebandCn0DbHz = 20f),
                makeSatellite(basebandCn0DbHz = null),
            ),
        )
        assertEquals(20f, data.avgBasebandCn0DbHz, 0.01f)
    }

    @Test
    fun `avgBasebandCn0DbHz excludes zero baseband values`() {
        val data = GnssData(
            satellites = listOf(
                makeSatellite(basebandCn0DbHz = 25f),
                makeSatellite(basebandCn0DbHz = 0f),
            ),
        )
        assertEquals(25f, data.avgBasebandCn0DbHz, 0.01f)
    }

    @Test
    fun `avgBasebandCn0DbHz returns 0 when all baseband are null`() {
        val data = GnssData(
            satellites = listOf(
                makeSatellite(basebandCn0DbHz = null),
                makeSatellite(basebandCn0DbHz = null),
            ),
        )
        assertEquals(0f, data.avgBasebandCn0DbHz, 0.01f)
    }
}
