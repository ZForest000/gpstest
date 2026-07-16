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
            rawConstellationType = constellation.constellationType,
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
        val entry =
            SatelliteHistoryEntry(
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
        val satellites =
            listOf(
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
        val satellites =
            listOf(
                makeSatellite(svid = 1, cn0DbHz = 20f),
                makeSatellite(svid = 2, cn0DbHz = 40f),
                makeSatellite(svid = 3, cn0DbHz = 0f),
            )
        val snapshot = SatelliteHistorySnapshot.fromSatellites(satellites, timestamp = 1000L)
        assertEquals(30f, snapshot.averageSignalStrength, 0.01f)
    }

    @Test
    fun `fromSatellites returns 0 average when all signals are zero`() {
        val satellites =
            listOf(
                makeSatellite(svid = 1, cn0DbHz = 0f),
                makeSatellite(svid = 2, cn0DbHz = 0f),
            )
        val snapshot = SatelliteHistorySnapshot.fromSatellites(satellites, timestamp = 1000L)
        assertEquals(0f, snapshot.averageSignalStrength, 0.01f)
        assertEquals(0, snapshot.visibleCount)
    }

    @Test
    fun `getEntries deserializes valid JSON`() {
        val satellites =
            listOf(
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
        val snapshot =
            SatelliteHistorySnapshot(
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

    @Test
    fun `SatelliteHistoryConfig default maxSnapshots is 100`() {
        val config = SatelliteHistoryConfig()
        assertEquals(100, config.maxSnapshots)
    }

    @Test
    fun `SatelliteHistoryConfig default snapshotIntervalMs is 60000`() {
        val config = SatelliteHistoryConfig()
        assertEquals(60_000L, config.snapshotIntervalMs)
    }

    @Test
    fun `SatelliteHistoryConfig default retentionDays is 7`() {
        val config = SatelliteHistoryConfig()
        assertEquals(7, config.retentionDays)
    }

    @Test
    fun `fromGnssSatellite preserves rawConstellationType from source satellite`() {
        val sat = makeSatellite(constellation = Constellation.GPS)
        val entry = SatelliteHistoryEntry.fromGnssSatellite(sat, timestamp = 1000L)
        assertEquals(Constellation.GPS.constellationType, entry.rawConstellationType)
    }

    @Test
    fun `fromGnssSatellite preserves UNKNOWN rawConstellationType`() {
        val sat =
            GnssSatellite(
                svid = 1,
                constellation = Constellation.UNKNOWN,
                rawConstellationType = 99,
                cn0DbHz = 0f,
                azimuthDegrees = 0f,
                elevationDegrees = 0f,
                hasAlmanac = false,
                hasEphemeris = false,
                usedInFix = false,
                carrierFrequencyHz = null,
                carrierCycles = null,
                dopplerShiftHz = null,
                timeNanos = 0L,
            )
        val entry = SatelliteHistoryEntry.fromGnssSatellite(sat, timestamp = 1000L)
        assertEquals(99, entry.rawConstellationType)
    }

    @Test
    fun `fromSatellites stores location dop and ttff when provided`() {
        val location =
            LocationInfo(
                latitude = 31.2304,
                longitude = 121.4737,
                altitude = 10.0,
                accuracy = 5.5f,
                speed = 0f,
                bearing = 0f,
                timestamp = 1000L,
            )
        val dop = DopInfo(pdop = 1.5, hdop = 1.0, vdop = 1.2, satelliteCount = 8)
        val snapshot =
            SatelliteHistorySnapshot.fromSatellites(
                satellites = listOf(makeSatellite()),
                timestamp = 1000L,
                location = location,
                dopInfo = dop,
                ttffMs = 2500L,
            )
        assertEquals(31.2304, snapshot.latitude!!, 1e-6)
        assertEquals(121.4737, snapshot.longitude!!, 1e-6)
        assertEquals(5.5f, snapshot.accuracy!!, 0.01f)
        assertEquals(1.5, snapshot.pdop!!, 1e-6)
        assertEquals(1.0, snapshot.hdop!!, 1e-6)
        assertEquals(1.2, snapshot.vdop!!, 1e-6)
        assertEquals(2500L, snapshot.ttffMs)
        assertTrue(snapshot.hasLocation)
    }

    @Test
    fun `fromSatellites leaves quality fields null when omitted`() {
        val snapshot = SatelliteHistorySnapshot.fromSatellites(listOf(makeSatellite()), 1000L)
        assertEquals(null, snapshot.latitude)
        assertEquals(null, snapshot.longitude)
        assertEquals(null, snapshot.accuracy)
        assertEquals(null, snapshot.pdop)
        assertEquals(null, snapshot.ttffMs)
        assertEquals(false, snapshot.hasLocation)
    }

    @Test
    fun `HistoryTimeFilter ALL returns all snapshots`() {
        val now = 1_000_000L
        val snapshots =
            listOf(
                SatelliteHistorySnapshot.EMPTY.copy(timestamp = now - 1000),
                SatelliteHistorySnapshot.EMPTY.copy(timestamp = now - 100_000),
            )
        assertEquals(2, HistoryTimeFilter.ALL.apply(snapshots, now).size)
    }

    @Test
    fun `HistoryTimeFilter HOUR_1 keeps only last hour`() {
        val now = 10_000_000L
        val snapshots =
            listOf(
                SatelliteHistorySnapshot.EMPTY.copy(timestamp = now - 30 * 60 * 1000L),
                SatelliteHistorySnapshot.EMPTY.copy(timestamp = now - 2 * 60 * 60 * 1000L),
            )
        val filtered = HistoryTimeFilter.HOUR_1.apply(snapshots, now)
        assertEquals(1, filtered.size)
        assertEquals(now - 30 * 60 * 1000L, filtered[0].timestamp)
    }

    @Test
    fun `HistoryTimeFilter DAY_7 excludes older than seven days`() {
        val now = 100_000_000L
        val dayMs = 24 * 60 * 60 * 1000L
        val snapshots =
            listOf(
                SatelliteHistorySnapshot.EMPTY.copy(timestamp = now - 3 * dayMs),
                SatelliteHistorySnapshot.EMPTY.copy(timestamp = now - 8 * dayMs),
            )
        val filtered = HistoryTimeFilter.DAY_7.apply(snapshots, now)
        assertEquals(1, filtered.size)
        assertEquals(now - 3 * dayMs, filtered[0].timestamp)
    }
}
