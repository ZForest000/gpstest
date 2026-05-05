package com.example.gpstest.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GnssClockDataTest {
    @Test
    fun `totalBiasNanos sums fullBiasNanos and biasNanos`() {
        val clock = GnssClockData(
            timeNanos = 1000L,
            biasNanos = 0.5,
            fullBiasNanos = -1000L,
            driftNanosPerSecond = null,
            biasUncertaintyNanos = null,
            driftUncertaintyNanosPerSecond = null,
            hardwareClockDiscontinuityCount = 0,
        )
        assertEquals(-999.5, clock.totalBiasNanos!!, 0.001)
    }

    @Test
    fun `totalBiasNanos returns null when fullBiasNanos is null`() {
        val clock = GnssClockData(
            timeNanos = 1000L,
            biasNanos = 0.5,
            fullBiasNanos = null,
            driftNanosPerSecond = null,
            biasUncertaintyNanos = null,
            driftUncertaintyNanosPerSecond = null,
            hardwareClockDiscontinuityCount = 0,
        )
        assertNull(clock.totalBiasNanos)
    }

    @Test
    fun `totalBiasNanos returns null when biasNanos is null`() {
        val clock = GnssClockData(
            timeNanos = 1000L,
            biasNanos = null,
            fullBiasNanos = -1000L,
            driftNanosPerSecond = null,
            biasUncertaintyNanos = null,
            driftUncertaintyNanosPerSecond = null,
            hardwareClockDiscontinuityCount = 0,
        )
        assertNull(clock.totalBiasNanos)
    }

    @Test
    fun `totalBiasMicroseconds converts nanos to microseconds`() {
        val clock = GnssClockData(
            timeNanos = 1000L,
            biasNanos = 500.0,
            fullBiasNanos = 0L,
            driftNanosPerSecond = null,
            biasUncertaintyNanos = null,
            driftUncertaintyNanosPerSecond = null,
            hardwareClockDiscontinuityCount = 0,
        )
        assertEquals(0.5, clock.totalBiasMicroseconds!!, 0.001)
    }

    @Test
    fun `totalBiasMicroseconds returns null when totalBiasNanos is null`() {
        val clock = GnssClockData(
            timeNanos = 1000L,
            biasNanos = null,
            fullBiasNanos = null,
            driftNanosPerSecond = null,
            biasUncertaintyNanos = null,
            driftUncertaintyNanosPerSecond = null,
            hardwareClockDiscontinuityCount = 0,
        )
        assertNull(clock.totalBiasMicroseconds)
    }

    @Test
    fun `driftMicrosecondsPerSecond converts drift from nanos to micros`() {
        val clock = GnssClockData(
            timeNanos = 1000L,
            biasNanos = null,
            fullBiasNanos = null,
            driftNanosPerSecond = 2000.0,
            biasUncertaintyNanos = null,
            driftUncertaintyNanosPerSecond = null,
            hardwareClockDiscontinuityCount = 0,
        )
        assertEquals(2.0, clock.driftMicrosecondsPerSecond!!, 0.001)
    }

    @Test
    fun `driftMicrosecondsPerSecond returns null when driftNanosPerSecond is null`() {
        val clock = GnssClockData(
            timeNanos = 1000L,
            biasNanos = null,
            fullBiasNanos = null,
            driftNanosPerSecond = null,
            biasUncertaintyNanos = null,
            driftUncertaintyNanosPerSecond = null,
            hardwareClockDiscontinuityCount = 0,
        )
        assertNull(clock.driftMicrosecondsPerSecond)
    }
}
