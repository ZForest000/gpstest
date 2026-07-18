package com.example.gpstest.domain.ephemeris

import org.junit.Assert.assertEquals
import org.junit.Test

class GpsSatellitePropagatorTest {
    @Test
    fun `propagates a circular equatorial orbit at ephemeris reference time`() {
        val ephemeris =
            GpsBroadcastEphemeris(
                svid = 1,
                weekNumber = 2300,
                toeSeconds = 100_000.0,
                tocSeconds = 100_000.0,
                sqrtA = 5153.7954775,
                eccentricity = 0.0,
                inclinationRadians = 0.0,
                longitudeOfAscendingNodeRadians = GpsSatellitePropagator.EARTH_ROTATION_RATE * 100_000.0,
                argumentOfPerigeeRadians = 0.0,
                meanAnomalyRadians = 0.0,
                deltaN = 0.0,
                inclinationRate = 0.0,
                longitudeRate = GpsSatellitePropagator.EARTH_ROTATION_RATE,
                cuc = 0.0,
                cus = 0.0,
                cic = 0.0,
                cis = 0.0,
                crc = 0.0,
                crs = 0.0,
                af0Seconds = 1e-6,
                af1SecondsPerSecond = 0.0,
                af2SecondsPerSecondSquared = 0.0,
                groupDelaySeconds = 0.0,
            )

        val state = GpsSatellitePropagator.propagate(ephemeris, 100_000.0)

        assertEquals(ephemeris.sqrtA * ephemeris.sqrtA, state.position.xMeters, 0.1)
        assertEquals(0.0, state.position.yMeters, 0.1)
        assertEquals(0.0, state.position.zMeters, 0.1)
        assertEquals(1e-6, state.clockBiasSeconds, 1e-12)
    }
}
