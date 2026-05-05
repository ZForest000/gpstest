package com.example.gpstest.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SatelliteHistoryTest {
    private fun makeSatellite(
        svid: Int = 1,
        constellation: Constellation = Constellation.GPS,
        cn0DbHz: Float = 30f,
        usedInFix: Boolean = true,
    ): GnssSatellite =
        GnssSatellite(
            svid = svid,
            constellation = constellation,
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
        )

    // --- SatelliteHistoryEntry ---

    @Test
    fun `toStorageKey combines constellation name and svid`() {
        val entry = SatelliteHistoryEntry(
            timestamp = 1000L,
            svid = 5,
            constellationName = "GPS",
            cn0DbHz = 30f,
            usedInFix = true,
        )
        assertEquals("GPS_5", entry.toStorageKey())
    }

    @Test
    fun `fromGnssSatellite creates entry from satellite`() {
        val sat = makeSatellite(svid = 10, constellation = Constellation.GALILEO, cn0DbHz = 25f, usedInFix = false)
        val entry = SatelliteHistoryEntry.fromGnssSatellite(sat, timestamp = 5000L)
        assertEquals(5000L, entry.timestamp)
        assertEquals(10, entry.svid)
        assertEquals("GALILEO", entry.constellationName)
        assertEquals(25f, entry.cn0DbHz, 0.01f)
        assertEquals(false, entry.usedInFix)
    }

    // --- SatelliteHistorySnapshot ---

    @Test
    fun `EMPTY snapshot has expected defaults`() {
        val empty = SatelliteHistorySnapshot.EMPTY
        assertEquals(0L, empty.timestamp)
        assertEquals("[]", empty.entriesJson)
        assertEquals(0, empty.usedInFixCount)
        assertEquals(0, empty.visibleCount)
        assertEquals(0f, empty.averageSignalStrength, 0.01f)
    }

    @Test
    fun `fromSatellites creates snapshot with correct counts`() {
        val satellites = listOf(
            makeSatellite(svid = 1, cn0DbHz = 30f, usedInFix = true),
            makeSatellite(svid = 2, cn0DbHz = 25f, usedInFix = false),
            makeSatellite(svid = 3, cn0DbHz = 0f, usedInFix = false),
        )
        val snapshot = SatelliteHistorySnapshot.fromSatellites(satellites, timestamp = 1000L)

        assertEquals(1000L, snapshot.timestamp)
        assertEquals(1, snapshot.usedInFixCount)
        assertEquals(2, snapshot.visibleCount)
    }

    @Test
    fun `fromSatellites calculates average signal strength of visible satellites`() {
        val satellites = listOf(
            makeSatellite(svid = 1, cn0DbHz = 20f),
            makeSatellite(svid = 2, cn0DbHz = 40f),
            makeSatellite(svid = 3, cn0DbHz = 0f),
        )
        val snapshot = SatelliteHistorySnapshot.fromSatellites(satellites, timestamp = 1000L)
        assertEquals(30f, snapshot.averageSignalStrength, 0.01f)
    }

    @Test
    fun `fromSatellites returns 0 average when all signals are zero`() {
        val satellites = listOf(
            makeSatellite(svid = 1, cn0DbHz = 0f),
            makeSatellite(svid = 2, cn0DbHz = 0f),
        )
        val snapshot = SatelliteHistorySnapshot.fromSatellites(satellites, timestamp = 1000L)
        assertEquals(0f, snapshot.averageSignalStrength, 0.01f)
        assertEquals(0, snapshot.visibleCount)
    }

    @Test
    fun `getEntries deserializes valid JSON`() {
        val satellites = listOf(
            makeSatellite(svid = 1, constellation = Constellation.GPS, cn0DbHz = 30f),
            makeSatellite(svid = 2, constellation = Constellation.BEIDOU, cn0DbHz = 25f),
        )
        val snapshot = SatelliteHistorySnapshot.fromSatellites(satellites, timestamp = 1000L)
        val entries = snapshot.getEntries()

        assertEquals(2, entries.size)
        assertEquals(1, entries[0].svid)
        assertEquals(2, entries[1].svid)
        assertEquals("GPS", entries[0].constellationName)
        assertEquals("BEIDOU", entries[1].constellationName)
    }

    @Test
    fun `getEntries returns empty list for invalid JSON`() {
        val snapshot = SatelliteHistorySnapshot(
            timestamp = 1000L,
            entriesJson = "not valid json",
            usedInFixCount = 0,
            visibleCount = 0,
            averageSignalStrength = 0f,
        )
        assertTrue(snapshot.getEntries().isEmpty())
    }

    @Test
    fun `fromSatellites with empty list creates empty snapshot`() {
        val snapshot = SatelliteHistorySnapshot.fromSatellites(emptyList(), timestamp = 2000L)
        assertEquals(2000L, snapshot.timestamp)
        assertEquals(0, snapshot.usedInFixCount)
        assertEquals(0, snapshot.visibleCount)
        assertEquals(0f, snapshot.averageSignalStrength, 0.01f)
        assertTrue(snapshot.getEntries().isEmpty())
    }
}
