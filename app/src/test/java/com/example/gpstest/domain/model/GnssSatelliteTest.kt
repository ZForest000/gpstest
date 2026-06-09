package com.example.gpstest.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GnssSatelliteTest {
    private fun makeSatellite(
        usedInFix: Boolean = false,
        cn0DbHz: Float = 30f,
        constellation: Constellation = Constellation.GPS,
        rawConstellationType: Int = constellation.constellationType,
        accumulatedDeltaRangeState: Int? = null,
        measurementState: Int? = null,
    ): GnssSatellite =
        GnssSatellite(
            svid = 1,
            constellation = constellation,
            rawConstellationType = rawConstellationType,
            cn0DbHz = cn0DbHz,
            azimuthDegrees = 45f,
            elevationDegrees = 30f,
            hasAlmanac = true,
            hasEphemeris = true,
            usedInFix = usedInFix,
            carrierFrequencyHz = null,
            carrierCycles = null,
            dopplerShiftHz = null,
            timeNanos = 0L,
            accumulatedDeltaRangeState = accumulatedDeltaRangeState,
            measurementState = measurementState,
        )

    // --- group ---

    @Test
    fun `group is USED_IN_FIX when usedInFix is true`() {
        val sat = makeSatellite(usedInFix = true, cn0DbHz = 0f)
        assertEquals(SatelliteGroup.USED_IN_FIX, sat.group)
    }

    @Test
    fun `group is USED_IN_FIX even when cn0 is positive`() {
        val sat = makeSatellite(usedInFix = true, cn0DbHz = 35f)
        assertEquals(SatelliteGroup.USED_IN_FIX, sat.group)
    }

    @Test
    fun `group is VISIBLE_ONLY when not used in fix but cn0 is positive`() {
        val sat = makeSatellite(usedInFix = false, cn0DbHz = 25f)
        assertEquals(SatelliteGroup.VISIBLE_ONLY, sat.group)
    }

    @Test
    fun `group is VISIBLE_ONLY when cn0 is very small positive`() {
        val sat = makeSatellite(usedInFix = false, cn0DbHz = 0.1f)
        assertEquals(SatelliteGroup.VISIBLE_ONLY, sat.group)
    }

    @Test
    fun `group is SEARCHING when not used in fix and cn0 is zero`() {
        val sat = makeSatellite(usedInFix = false, cn0DbHz = 0f)
        assertEquals(SatelliteGroup.SEARCHING, sat.group)
    }

    @Test
    fun `group is SEARCHING when not used in fix and cn0 is negative`() {
        val sat = makeSatellite(usedInFix = false, cn0DbHz = -1f)
        assertEquals(SatelliteGroup.SEARCHING, sat.group)
    }

    // --- signalStrength ---

    @Test
    fun `signalStrength is STRONG when cn0 is 35`() {
        val sat = makeSatellite(cn0DbHz = 35f)
        assertEquals(SignalStrength.STRONG, sat.signalStrength)
    }

    @Test
    fun `signalStrength is STRONG when cn0 is above 35`() {
        val sat = makeSatellite(cn0DbHz = 45f)
        assertEquals(SignalStrength.STRONG, sat.signalStrength)
    }

    @Test
    fun `signalStrength is MEDIUM when cn0 is 34`() {
        val sat = makeSatellite(cn0DbHz = 34f)
        assertEquals(SignalStrength.MEDIUM, sat.signalStrength)
    }

    @Test
    fun `signalStrength is MEDIUM when cn0 is 25`() {
        val sat = makeSatellite(cn0DbHz = 25f)
        assertEquals(SignalStrength.MEDIUM, sat.signalStrength)
    }

    @Test
    fun `signalStrength is WEAK when cn0 is 24`() {
        val sat = makeSatellite(cn0DbHz = 24f)
        assertEquals(SignalStrength.WEAK, sat.signalStrength)
    }

    @Test
    fun `signalStrength is WEAK when cn0 is zero`() {
        val sat = makeSatellite(cn0DbHz = 0f)
        assertEquals(SignalStrength.WEAK, sat.signalStrength)
    }

    @Test
    fun `signalStrength is WEAK when cn0 is negative`() {
        val sat = makeSatellite(cn0DbHz = -5f)
        assertEquals(SignalStrength.WEAK, sat.signalStrength)
    }

    // --- MultipathIndicator.fromInt ---

    @Test
    fun `MultipathIndicator fromInt 0 is UNKNOWN`() {
        assertEquals(MultipathIndicator.UNKNOWN, MultipathIndicator.fromInt(0))
    }

    @Test
    fun `MultipathIndicator fromInt 1 is DETECTED`() {
        assertEquals(MultipathIndicator.DETECTED, MultipathIndicator.fromInt(1))
    }

    @Test
    fun `MultipathIndicator fromInt 2 is NOT_DETECTED`() {
        assertEquals(MultipathIndicator.NOT_DETECTED, MultipathIndicator.fromInt(2))
    }

    @Test
    fun `MultipathIndicator fromInt negative is UNKNOWN`() {
        assertEquals(MultipathIndicator.UNKNOWN, MultipathIndicator.fromInt(-1))
    }

    @Test
    fun `MultipathIndicator fromInt 99 is UNKNOWN`() {
        assertEquals(MultipathIndicator.UNKNOWN, MultipathIndicator.fromInt(99))
    }
}
