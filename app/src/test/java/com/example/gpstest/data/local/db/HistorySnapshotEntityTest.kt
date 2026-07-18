package com.example.gpstest.data.local.db

import com.example.gpstest.domain.model.SatelliteHistorySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistorySnapshotEntityTest {
    @Test
    fun `entity round trip preserves optional navigation and DOP fields`() {
        val snapshot =
            SatelliteHistorySnapshot(
                timestamp = 1234L,
                entriesJson = "[]",
                usedInFixCount = 4,
                visibleCount = 9,
                averageSignalStrength = 35.5f,
                latitude = 39.9,
                longitude = 116.4,
                accuracy = 3.2f,
                pdop = 1.1,
                hdop = 0.8,
                vdop = 0.9,
                ttffMs = 4500L,
            )

        val restored = HistorySnapshotEntity.fromSnapshot(snapshot).toSnapshot()

        assertEquals(snapshot, restored)
    }

    @Test
    fun `entity round trip keeps missing optional fields null`() {
        val restored = HistorySnapshotEntity.fromSnapshot(SatelliteHistorySnapshot.EMPTY).toSnapshot()

        assertNull(restored.latitude)
        assertNull(restored.pdop)
        assertNull(restored.ttffMs)
    }
}
