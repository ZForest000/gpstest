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
}
