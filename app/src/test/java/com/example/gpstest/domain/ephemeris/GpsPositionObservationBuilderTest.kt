package com.example.gpstest.domain.ephemeris

import com.example.gpstest.domain.model.Constellation
import com.example.gpstest.domain.model.GnssClockData
import com.example.gpstest.domain.model.GnssSatellite
import com.example.gpstest.domain.model.PseudorangeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GpsPositionObservationBuilderTest {
    @Test
    fun `builds corrected GPS observation from valid pseudorange and ephemeris`() {
        val ephemeris = testEphemeris()
        val clock = GnssClockData(100_000_000_000_000L, 0.0, 0L, null, null, null, 0)
        val satellite = testSatellite().copy(pseudorangeMeters = 20_000_000.0, pseudorangeUncertaintyMeters = 3.0)

        val observation = GpsPositionObservationBuilder.build(clock, listOf(satellite), mapOf(1 to ephemeris)).single()

        assertEquals(20_000_000.0, observation.pseudorangeMeters, 10.0)
        assertEquals(3.0, observation.uncertaintyMeters, 0.0)
        assertNotNull(observation.satellitePosition)
    }

    @Test
    fun `reports missing ephemeris SVIDs separately from valid pseudoranges`() {
        val clock = GnssClockData(100_000_000_000_000L, 0.0, 0L, null, null, null, 0)
        val satellite = testSatellite().copy(pseudorangeMeters = 20_000_000.0, pseudorangeUncertaintyMeters = 3.0)

        val result = GpsPositionObservationBuilder.analyze(clock, listOf(satellite), emptyMap())

        assertEquals(1, result.gpsSatelliteCount)
        assertEquals(1, result.validPseudorangeCount)
        assertEquals(listOf(1), result.missingEphemerisSvids)
        assertEquals(0, result.observations.size)
    }

    private fun testEphemeris() = GpsBroadcastEphemeris(1, 0, 0.0, 0.0, 5153.7954775, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, GpsSatellitePropagator.EARTH_ROTATION_RATE, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)

    private fun testSatellite() = GnssSatellite(1, Constellation.GPS, 1, 40f, 0f, 45f, true, true, true, 1_575_420_000f, null, null, 0L, pseudorangeStatus = PseudorangeStatus.AVAILABLE)
}
