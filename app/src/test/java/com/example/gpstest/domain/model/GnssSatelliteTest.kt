package com.example.gpstest.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GnssSatelliteTest {
    private fun makeSatellite(
        usedInFix: Boolean = false,
        cn0DbHz: Float = 30f,
        constellation: Constellation = Constellation.GPS,
        rawConstellationType: Int = constellation.constellationType,
        carrierFrequencyHz: Float? = null,
        carrierCycles: Long? = null,
        fullCarrierPhaseCycleCount: Long? = null,
        accumulatedDeltaRangeMeters: Double? = null,
        accumulatedDeltaRangeState: Int? = null,
        accumulatedDeltaRangeUncertaintyMeters: Double? = null,
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
            carrierFrequencyHz = carrierFrequencyHz,
            carrierCycles = carrierCycles,
            dopplerShiftHz = null,
            timeNanos = 0L,
            accumulatedDeltaRangeMeters = accumulatedDeltaRangeMeters,
            accumulatedDeltaRangeState = accumulatedDeltaRangeState,
            accumulatedDeltaRangeUncertaintyMeters = accumulatedDeltaRangeUncertaintyMeters,
            measurementState = measurementState,
            fullCarrierPhaseCycleCount = fullCarrierPhaseCycleCount,
        )

    // --- group ---

    @Test
    fun `pseudorange defaults to missing measurement`() {
        val satellite = makeSatellite()

        assertNull(satellite.pseudorangeMeters)
        assertNull(satellite.pseudorangeUncertaintyMeters)
        assertEquals(PseudorangeStatus.MISSING_MEASUREMENT, satellite.pseudorangeStatus)
    }

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

    // --- bitmask: isAdrValid / hasCycleSlip ---

    @Test
    fun `isAdrValid is true when ADR_STATE_VALID bit is set`() {
        val sat = makeSatellite(accumulatedDeltaRangeState = 1)
        assertEquals(true, sat.isAdrValid)
    }

    @Test
    fun `isAdrValid is true when multiple bits including ADR_STATE_VALID are set`() {
        val sat = makeSatellite(accumulatedDeltaRangeState = 5)
        assertEquals(true, sat.isAdrValid)
    }

    @Test
    fun `isAdrValid is false when ADR_STATE_VALID bit is not set`() {
        val sat = makeSatellite(accumulatedDeltaRangeState = 4)
        assertEquals(false, sat.isAdrValid)
    }

    @Test
    fun `isAdrValid is false when accumulatedDeltaRangeState is null`() {
        val sat = makeSatellite(accumulatedDeltaRangeState = null)
        assertEquals(false, sat.isAdrValid)
    }

    @Test
    fun `hasCycleSlip is true when ADR_STATE_CYCLE_SLIP bit is set`() {
        val sat = makeSatellite(accumulatedDeltaRangeState = 4)
        assertEquals(true, sat.hasCycleSlip)
    }

    @Test
    fun `hasCycleSlip is false when ADR_STATE_CYCLE_SLIP bit is not set`() {
        val sat = makeSatellite(accumulatedDeltaRangeState = 1)
        assertEquals(false, sat.hasCycleSlip)
    }

    @Test
    fun `hasCycleSlip is false when accumulatedDeltaRangeState is null`() {
        val sat = makeSatellite(accumulatedDeltaRangeState = null)
        assertEquals(false, sat.hasCycleSlip)
    }

    // --- effectiveCarrierPhaseCycles / effectiveAdrMeters ---

    @Test
    fun `effectiveCarrierPhaseCycles prefers carrierCycles when present`() {
        val sat =
            makeSatellite(
                carrierCycles = 100L,
                carrierFrequencyHz = 1_575_420_000f,
                accumulatedDeltaRangeMeters = 19.0,
                accumulatedDeltaRangeState = 1,
            )
        assertEquals(100.0, sat.effectiveCarrierPhaseCycles!!, 1e-9)
    }

    @Test
    fun `effectiveCarrierPhaseCycles falls back to fullCarrierPhaseCycleCount`() {
        val sat = makeSatellite(fullCarrierPhaseCycleCount = 42L)
        assertEquals(42.0, sat.effectiveCarrierPhaseCycles!!, 1e-9)
    }

    @Test
    fun `effectiveCarrierPhaseCycles derives from valid ADR and frequency`() {
        // GPS L1: 1575.42 MHz, ADR 19.029367 m → cycles = ADR * f / c
        val frequencyHz = 1_575_420_000f
        val adrMeters = 19.029367
        val expected = adrMeters * frequencyHz / 299_792_458.0
        val sat =
            makeSatellite(
                carrierFrequencyHz = frequencyHz,
                accumulatedDeltaRangeMeters = adrMeters,
                accumulatedDeltaRangeState = 1,
            )
        assertEquals(expected, sat.effectiveCarrierPhaseCycles!!, 1e-6)
    }

    @Test
    fun `effectiveCarrierPhaseCycles is null when ADR invalid`() {
        val sat =
            makeSatellite(
                carrierFrequencyHz = 1_575_420_000f,
                accumulatedDeltaRangeMeters = 19.0,
                accumulatedDeltaRangeState = 0,
            )
        assertNull(sat.effectiveCarrierPhaseCycles)
    }

    @Test
    fun `effectiveCarrierPhaseCycles is null when cycle slip`() {
        val sat =
            makeSatellite(
                carrierFrequencyHz = 1_575_420_000f,
                accumulatedDeltaRangeMeters = 19.0,
                accumulatedDeltaRangeState = 1 or 4,
            )
        assertNull(sat.effectiveCarrierPhaseCycles)
    }

    @Test
    fun `effectiveCarrierPhaseCycles is null when frequency missing`() {
        val sat =
            makeSatellite(
                accumulatedDeltaRangeMeters = 19.0,
                accumulatedDeltaRangeState = 1,
            )
        assertNull(sat.effectiveCarrierPhaseCycles)
    }

    @Test
    fun `effectiveAdrMeters returns value only when valid and no cycle slip`() {
        val sat =
            makeSatellite(
                accumulatedDeltaRangeMeters = 12.5,
                accumulatedDeltaRangeState = 1,
                accumulatedDeltaRangeUncertaintyMeters = 0.02,
            )
        assertEquals(12.5, sat.effectiveAdrMeters!!, 1e-9)
        assertEquals(0.02, sat.effectiveAdrUncertaintyMeters!!, 1e-9)
    }

    @Test
    fun `effectiveAdrMeters is null when cycle slip`() {
        val sat =
            makeSatellite(
                accumulatedDeltaRangeMeters = 12.5,
                accumulatedDeltaRangeState = 1 or 4,
                accumulatedDeltaRangeUncertaintyMeters = 0.02,
            )
        assertNull(sat.effectiveAdrMeters)
        assertNull(sat.effectiveAdrUncertaintyMeters)
    }

    // --- bitmask: measurementState properties ---

    @Test
    fun `hasCarrierPhaseLock is true when STATE_TOW_DECODED bit is set`() {
        val sat = makeSatellite(measurementState = 8)
        assertEquals(true, sat.hasCarrierPhaseLock)
    }

    @Test
    fun `hasCarrierPhaseLock is false when STATE_TOW_DECODED bit is not set`() {
        val sat = makeSatellite(measurementState = 1)
        assertEquals(false, sat.hasCarrierPhaseLock)
    }

    @Test
    fun `hasCarrierPhaseLock is false when measurementState is null`() {
        val sat = makeSatellite(measurementState = null)
        assertEquals(false, sat.hasCarrierPhaseLock)
    }

    @Test
    fun `hasCodeLock is true when STATE_CODE_LOCK bit is set`() {
        val sat = makeSatellite(measurementState = 1)
        assertEquals(true, sat.hasCodeLock)
    }

    @Test
    fun `hasCodeLock is false when STATE_CODE_LOCK bit is not set`() {
        val sat = makeSatellite(measurementState = 2)
        assertEquals(false, sat.hasCodeLock)
    }

    @Test
    fun `hasBitSync is true when STATE_BIT_SYNC bit is set`() {
        val sat = makeSatellite(measurementState = 2)
        assertEquals(true, sat.hasBitSync)
    }

    @Test
    fun `hasBitSync is false when STATE_BIT_SYNC bit is not set`() {
        val sat = makeSatellite(measurementState = 1)
        assertEquals(false, sat.hasBitSync)
    }

    @Test
    fun `hasSubframeSync is true when STATE_SUBFRAME_SYNC bit is set`() {
        val sat = makeSatellite(measurementState = 4)
        assertEquals(true, sat.hasSubframeSync)
    }

    @Test
    fun `hasSubframeSync is false when STATE_SUBFRAME_SYNC bit is not set`() {
        val sat = makeSatellite(measurementState = 8)
        assertEquals(false, sat.hasSubframeSync)
    }

    @Test
    fun `multiple measurementState bits can be set simultaneously`() {
        val sat = makeSatellite(measurementState = 15)
        assertEquals(true, sat.hasCarrierPhaseLock)
        assertEquals(true, sat.hasCodeLock)
        assertEquals(true, sat.hasBitSync)
        assertEquals(true, sat.hasSubframeSync)
    }

    @Test
    fun `all bitmask properties are false when measurementState is zero`() {
        val sat = makeSatellite(measurementState = 0)
        assertEquals(false, sat.hasCarrierPhaseLock)
        assertEquals(false, sat.hasCodeLock)
        assertEquals(false, sat.hasBitSync)
        assertEquals(false, sat.hasSubframeSync)
    }
}
