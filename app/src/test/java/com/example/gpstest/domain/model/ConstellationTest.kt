package com.example.gpstest.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ConstellationTest {
    @Test
    fun `maps type 1 to GPS`() {
        assertEquals(Constellation.GPS, Constellation.fromConstellationType(1))
    }

    @Test
    fun `maps type 2 to SBAS`() {
        assertEquals(Constellation.SBAS, Constellation.fromConstellationType(2))
    }

    @Test
    fun `maps type 3 to GLONASS`() {
        assertEquals(Constellation.GLONASS, Constellation.fromConstellationType(3))
    }

    @Test
    fun `maps type 4 to QZSS`() {
        assertEquals(Constellation.QZSS, Constellation.fromConstellationType(4))
    }

    @Test
    fun `maps type 5 to BEIDOU`() {
        assertEquals(Constellation.BEIDOU, Constellation.fromConstellationType(5))
    }

    @Test
    fun `maps type 6 to GALILEO`() {
        assertEquals(Constellation.GALILEO, Constellation.fromConstellationType(6))
    }
    @Test
    fun `maps type 7 to IRNSS`() {
        assertEquals(Constellation.IRNSS, Constellation.fromConstellationType(7))
    }


    @Test
    fun `maps unknown type 0 to UNKNOWN`() {
        assertEquals(Constellation.UNKNOWN, Constellation.fromConstellationType(0))
    }

    @Test
    fun `maps negative type to UNKNOWN`() {
        assertEquals(Constellation.UNKNOWN, Constellation.fromConstellationType(-1))
    }

    @Test
    fun `maps large type to UNKNOWN`() {
        assertEquals(Constellation.UNKNOWN, Constellation.fromConstellationType(99))
    }

    @Test
    fun `GPS shortName is GPS`() {
        assertEquals("GPS", Constellation.GPS.shortName)
    }

    @Test
    fun `SBAS shortName is SBAS`() {
        assertEquals("SBAS", Constellation.SBAS.shortName)
    }

    @Test
    fun `GLONASS shortName is GLO`() {
        assertEquals("GLO", Constellation.GLONASS.shortName)
    }

    @Test
    fun `GALILEO shortName is GAL`() {
        assertEquals("GAL", Constellation.GALILEO.shortName)
    }

    @Test
    fun `BEIDOU shortName is BDS`() {
        assertEquals("BDS", Constellation.BEIDOU.shortName)
    }

    @Test
    fun `QZSS shortName is QZS`() {
        assertEquals("QZS", Constellation.QZSS.shortName)
    }

    @Test
    fun `IRNSS shortName is IRN`() {
        assertEquals("IRN", Constellation.IRNSS.shortName)
    }

    @Test
    fun `UNKNOWN shortName is UNK`() {
        assertEquals("UNK", Constellation.UNKNOWN.shortName)
    }

    @Test
    fun `fromConstellationType round-trips for all known constellations`() {
        for (constellation in Constellation.entries) {
            if (constellation == Constellation.UNKNOWN) continue
            assertEquals(
                constellation,
                Constellation.fromConstellationType(constellation.constellationType),
            )
        }
    }
}
