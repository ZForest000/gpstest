package com.example.gpstest.domain.util

import com.example.gpstest.domain.model.Constellation
import com.example.gpstest.domain.model.DopInfo
import com.example.gpstest.domain.model.GnssSatellite
import com.example.gpstest.domain.model.LocationInfo
import com.example.gpstest.domain.model.SatelliteHistorySnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryCsvExporterTest {
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

    @Test
    fun `toCsv includes summary and entry sections`() {
        val snapshot =
            SatelliteHistorySnapshot.fromSatellites(
                satellites =
                    listOf(
                        makeSatellite(svid = 1, cn0DbHz = 30f, usedInFix = true),
                        makeSatellite(svid = 2, constellation = Constellation.BEIDOU, cn0DbHz = 25f, usedInFix = false),
                    ),
                timestamp = 1_700_000_000_000L,
                location =
                    LocationInfo(
                        latitude = 31.23,
                        longitude = 121.47,
                        altitude = 0.0,
                        accuracy = 4f,
                        speed = 0f,
                        bearing = 0f,
                        timestamp = 1_700_000_000_000L,
                    ),
                dopInfo = DopInfo(pdop = 1.2, hdop = 0.9, vdop = 1.1, satelliteCount = 2),
                ttffMs = 1800L,
            )

        val csv = HistoryCsvExporter.toCsv(listOf(snapshot))

        assertTrue(csv.contains("# === Snapshot Summary ==="))
        assertTrue(csv.contains("# === Satellite Entries ==="))
        assertTrue(csv.contains("timestamp_iso,timestamp_ms,used_in_fix,visible"))
        assertTrue(csv.contains("snapshot_timestamp_ms,constellation,svid,cn0_dbhz,used_in_fix"))
        assertTrue(csv.contains("1700000000000"))
        assertTrue(csv.contains("31.230000"))
        assertTrue(csv.contains("121.470000"))
        assertTrue(csv.contains("1.200000"))
        assertTrue(csv.contains("1800"))
        assertTrue(csv.contains("GPS,1,30.00,true"))
        assertTrue(csv.contains("BEIDOU,2,25.00,false"))
    }

    @Test
    fun `toCsv orders snapshots by timestamp ascending`() {
        val older = SatelliteHistorySnapshot.fromSatellites(listOf(makeSatellite(svid = 1)), 1000L)
        val newer = SatelliteHistorySnapshot.fromSatellites(listOf(makeSatellite(svid = 2)), 2000L)
        val csv = HistoryCsvExporter.toCsv(listOf(newer, older))

        val summaryIdx = csv.indexOf("# === Snapshot Summary ===")
        val entriesIdx = csv.indexOf("# === Satellite Entries ===")
        val firstTs = csv.indexOf("1000", summaryIdx)
        val secondTs = csv.indexOf("2000", summaryIdx)
        assertTrue(firstTs in (summaryIdx + 1) until entriesIdx)
        assertTrue(secondTs in (firstTs + 1) until entriesIdx)
    }

    @Test
    fun `toCsv leaves blank cells for null quality fields`() {
        val snapshot = SatelliteHistorySnapshot.fromSatellites(listOf(makeSatellite()), 5000L)
        val csv = HistoryCsvExporter.toCsv(snapshot)
        val summaryLine =
            csv
                .lineSequence()
                .first { it.startsWith("5") || it.contains(",5000,") }
        // latitude..ttff are empty when null
        assertTrue(summaryLine.contains(",5000,"))
        assertFalse(summaryLine.contains("null"))
    }

    @Test
    fun `toCsv single snapshot overload includes same snapshot data as list form`() {
        val snapshot = SatelliteHistorySnapshot.fromSatellites(listOf(makeSatellite()), 9000L)
        val single = HistoryCsvExporter.toCsv(snapshot)
        val list = HistoryCsvExporter.toCsv(listOf(snapshot))
        assertTrue(single.contains("9000"))
        assertTrue(list.contains("9000"))
        assertTrue(single.contains("GPS,1,30.00,true"))
        assertTrue(list.contains("GPS,1,30.00,true"))
        assertTrue(single.contains("# === Snapshot Summary ==="))
        assertTrue(single.contains("# === Satellite Entries ==="))
    }

    @Test
    fun `toCsv handles empty list`() {
        val csv = HistoryCsvExporter.toCsv(emptyList())
        assertTrue(csv.contains("Snapshot count: 0"))
        assertTrue(csv.contains("# === Snapshot Summary ==="))
        assertTrue(csv.contains("# === Satellite Entries ==="))
    }
}
