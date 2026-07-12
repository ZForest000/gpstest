package com.example.gpstest.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class GnssCapabilitiesInfoTest {
    private fun makeInfo(
        hasMeasurements: Int? = null,
        hasNavigationMessages: Int? = null,
        hasAntennaInfo: Int? = null,
    ) = GnssCapabilitiesInfo(
        hardwareModelName = "Test GNSS",
        yearOfHardware = "2024",
        hasMeasurements = hasMeasurements,
        hasNavigationMessages = hasNavigationMessages,
        hasAntennaInfo = hasAntennaInfo,
        hasAccumulatedDeltaRange = null,
        hasMeasurementCorrections = null,
        hasMeasurementCorrelationVectors = null,
    )

    @Test
    fun `toCapabilityState returns SUPPORTED for supported code`() {
        assertEquals(CapabilityState.SUPPORTED, 1.toCapabilityState())
    }

    @Test
    fun `toCapabilityState returns UNSUPPORTED for unsupported code`() {
        assertEquals(CapabilityState.UNSUPPORTED, 0.toCapabilityState())
    }

    @Test
    fun `toCapabilityState returns UNKNOWN for unknown code`() {
        assertEquals(CapabilityState.UNKNOWN, (-1).toCapabilityState())
    }

    @Test
    fun `toCapabilityState returns UNKNOWN for invalid code`() {
        assertEquals(CapabilityState.UNKNOWN, 99.toCapabilityState())
    }

    @Test
    fun `data class equality holds for same values`() {
        val a = makeInfo(hasMeasurements = 1)
        val b = makeInfo(hasMeasurements = 1)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `hardware model and year are preserved`() {
        val info = makeInfo()
        assertEquals("Test GNSS", info.hardwareModelName)
        assertEquals("2024", info.yearOfHardware)
    }

    @Test
    fun `null capability fields are allowed`() {
        val info = makeInfo()
        assertEquals(null, info.hasMeasurements)
        assertEquals(null, info.hasNavigationMessages)
        assertEquals(null, info.hasAntennaInfo)
    }
}
