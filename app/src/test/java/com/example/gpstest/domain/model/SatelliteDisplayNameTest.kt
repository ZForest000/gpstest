package com.example.gpstest.domain.model

import com.example.gpstest.ui.components.getDisplayName
import org.junit.Assert.assertEquals
import org.junit.Test

class SatelliteDisplayNameTest {
    private fun makeSatellite(
        constellation: Constellation = Constellation.GPS,
        rawConstellationType: Int = constellation.constellationType,
    ): GnssSatellite =
        GnssSatellite(
            svid = 1,
            constellation = constellation,
            rawConstellationType = rawConstellationType,
            cn0DbHz = 30f,
            azimuthDegrees = 45f,
            elevationDegrees = 30f,
            hasAlmanac = true,
            hasEphemeris = true,
            usedInFix = true,
            carrierFrequencyHz = null,
            carrierCycles = null,
            dopplerShiftHz = null,
            timeNanos = 0L,
        )

    // --- GnssSatellite.getDisplayName() ---

    @Test
    fun `GnssSatellite getDisplayName returns shortName for GPS`() {
        val sat = makeSatellite(constellation = Constellation.GPS)
        assertEquals("GPS", sat.getDisplayName())
    }

    @Test
    fun `GnssSatellite getDisplayName returns shortName for BEIDOU`() {
        val sat = makeSatellite(constellation = Constellation.BEIDOU)
        assertEquals("BDS", sat.getDisplayName())
    }

    @Test
    fun `GnssSatellite getDisplayName returns shortName for GLONASS`() {
        val sat = makeSatellite(constellation = Constellation.GLONASS)
        assertEquals("GLO", sat.getDisplayName())
    }

    @Test
    fun `GnssSatellite getDisplayName returns UNK with raw type for UNKNOWN with non-negative-1 raw type`() {
        val sat = makeSatellite(constellation = Constellation.UNKNOWN, rawConstellationType = 8)
        assertEquals("UNK(8)", sat.getDisplayName())
    }

    @Test
    fun `GnssSatellite getDisplayName returns UNK shortName for UNKNOWN with raw type -1`() {
        val sat = makeSatellite(constellation = Constellation.UNKNOWN, rawConstellationType = -1)
        assertEquals("UNK", sat.getDisplayName())
    }

    @Test
    fun `GnssSatellite getDisplayName returns UNK with raw type 0`() {
        val sat = makeSatellite(constellation = Constellation.UNKNOWN, rawConstellationType = 0)
        assertEquals("UNK(0)", sat.getDisplayName())
    }

    // --- SatelliteHistoryEntry.getDisplayName() ---

    @Test
    fun `SatelliteHistoryEntry getDisplayName returns shortName for GPS constellation`() {
        val entry =
            SatelliteHistoryEntry(
                timestamp = 1000L,
                svid = 1,
                constellationName = "GPS",
                cn0DbHz = 30f,
                usedInFix = true,
            )
        assertEquals("GPS", entry.getDisplayName())
    }

    @Test
    fun `SatelliteHistoryEntry getDisplayName returns shortName for BEIDOU`() {
        val entry =
            SatelliteHistoryEntry(
                timestamp = 1000L,
                svid = 1,
                constellationName = "BEIDOU",
                cn0DbHz = 30f,
                usedInFix = true,
            )
        assertEquals("BDS", entry.getDisplayName())
    }

    @Test
    fun `SatelliteHistoryEntry getDisplayName returns UNK with raw type for UNKNOWN with raw type`() {
        val entry =
            SatelliteHistoryEntry(
                timestamp = 1000L,
                svid = 1,
                constellationName = "UNKNOWN",
                rawConstellationType = 8,
                cn0DbHz = 30f,
                usedInFix = true,
            )
        assertEquals("UNK(8)", entry.getDisplayName())
    }

    @Test
    fun `SatelliteHistoryEntry getDisplayName returns UNK for UNKNOWN with null raw type`() {
        val entry =
            SatelliteHistoryEntry(
                timestamp = 1000L,
                svid = 1,
                constellationName = "UNKNOWN",
                rawConstellationType = null,
                cn0DbHz = 30f,
                usedInFix = true,
            )
        assertEquals("UNK", entry.getDisplayName())
    }

    @Test
    fun `SatelliteHistoryEntry getDisplayName returns UNK for UNKNOWN with raw type -1`() {
        val entry =
            SatelliteHistoryEntry(
                timestamp = 1000L,
                svid = 1,
                constellationName = "UNKNOWN",
                rawConstellationType = -1,
                cn0DbHz = 30f,
                usedInFix = true,
            )
        assertEquals("UNK", entry.getDisplayName())
    }

    @Test
    fun `SatelliteHistoryEntry getDisplayName returns raw name for invalid constellation name`() {
        val entry =
            SatelliteHistoryEntry(
                timestamp = 1000L,
                svid = 1,
                constellationName = "FUTURE_CONSTELLATION",
                cn0DbHz = 30f,
                usedInFix = true,
            )
        assertEquals("FUTURE_CONSTELLATION", entry.getDisplayName())
    }
}
