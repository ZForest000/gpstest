package com.example.gpstest.data.local

import com.example.gpstest.domain.model.AppSettings
import com.example.gpstest.domain.model.SatelliteHistorySnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SatelliteHistoryPersistenceTest {
    @Test
    fun `first legacy import marks legacy only after room import and reopen does not duplicate`() =
        runTest {
            val snapshot = snapshot(timestamp = 1_000L)
            val room = FakeRoomStore()
            val legacy =
                FakeLegacyStore(
                    snapshots = listOf(snapshot),
                    beforeMarkerWrite = { assertTrue(room.metadataComplete) },
                )
            val settings = MutableStateFlow(AppSettings())

            SatelliteHistoryPersistence(room, legacy, settings).snapshots.first()

            assertEquals(1, room.importAttempts)
            assertTrue(room.metadataComplete)
            assertTrue(legacy.markerWritten)

            SatelliteHistoryPersistence(room, legacy, settings).snapshots.first()

            assertEquals(1, room.importAttempts)
        }

    @Test
    fun `failed room import keeps legacy json and marker for retry`() =
        runTest {
            val snapshot = snapshot(timestamp = 1_000L)
            val room = FakeRoomStore(failImportAttempts = 1)
            val legacy = FakeLegacyStore(snapshots = listOf(snapshot))
            val persistence = SatelliteHistoryPersistence(room, legacy, MutableStateFlow(AppSettings()))

            val error = runCatching { persistence.snapshots.first() }.exceptionOrNull()
            assertTrue(error is IllegalStateException)

            assertEquals(1, room.importAttempts)
            assertFalse(room.metadataComplete)
            assertFalse(legacy.markerWritten)
            assertEquals(listOf(snapshot), legacy.snapshots)

            persistence.snapshots.first()

            assertEquals(2, room.importAttempts)
            assertTrue(room.metadataComplete)
            assertTrue(legacy.markerWritten)
        }

    @Test
    fun `marked legacy json restores into an empty room`() =
        runTest {
            val snapshot = snapshot(timestamp = 1_000L)
            val room = FakeRoomStore()
            val legacy = FakeLegacyStore(snapshots = listOf(snapshot), markerWritten = true)

            SatelliteHistoryPersistence(room, legacy, MutableStateFlow(AppSettings())).snapshots.first()

            assertEquals(1, room.importAttempts)
            assertEquals(listOf(snapshot), room.rows.value)
            assertTrue(room.metadataComplete)
        }

    @Test
    fun `clear removes both layers and reopen cannot restore history`() =
        runTest {
            val snapshot = snapshot(timestamp = 1_000L)
            val room = FakeRoomStore(metadataComplete = true)
            val legacy = FakeLegacyStore(snapshots = listOf(snapshot), markerWritten = true)
            val settings = MutableStateFlow(AppSettings())

            SatelliteHistoryPersistence(room, legacy, settings).clear()

            assertTrue(room.rows.value.isEmpty())
            assertTrue(room.metadataComplete)
            assertTrue(legacy.snapshots.isEmpty())
            assertTrue(legacy.markerWritten)

            SatelliteHistoryPersistence(room, legacy, settings).snapshots.first()

            assertEquals(0, room.importAttempts)
        }

    @Test
    fun `save calculates retention exactly once in persistence`() =
        runTest {
            val room = FakeRoomStore()
            val legacy = FakeLegacyStore()
            val settings = MutableStateFlow(AppSettings(maxSnapshots = 3, retentionDays = 2))
            val persistence = SatelliteHistoryPersistence(
                roomStore = room,
                legacyStore = legacy,
                settings = settings,
                clock = { 10_000L },
            )

            persistence.save(snapshot(timestamp = 10_000L))

            assertEquals(
                listOf(HistoryRetention(cutoffTimestamp = -172_790_000L, maxSnapshots = 3)),
                room.saveRetentions,
            )
        }

    private fun snapshot(timestamp: Long): SatelliteHistorySnapshot =
        SatelliteHistorySnapshot(
            timestamp = timestamp,
            entriesJson = "[]",
            usedInFixCount = 0,
            visibleCount = 0,
            averageSignalStrength = 0f,
        )

    private class FakeLegacyStore(
        var snapshots: List<SatelliteHistorySnapshot> = emptyList(),
        var markerWritten: Boolean = false,
        private val beforeMarkerWrite: () -> Unit = {},
    ) : LegacySatelliteHistoryStore {
        override suspend fun readLegacyHistory(): LegacySatelliteHistory =
            LegacySatelliteHistory(snapshots = snapshots, markerWritten = markerWritten)

        override suspend fun markRoomMigrationComplete() {
            beforeMarkerWrite()
            markerWritten = true
        }

        override suspend fun clearLegacyHistory() {
            snapshots = emptyList()
            markerWritten = true
        }
    }

    private class FakeRoomStore(
        var metadataComplete: Boolean = false,
        private var failImportAttempts: Int = 0,
        initialSnapshots: List<SatelliteHistorySnapshot> = emptyList(),
    ) : SatelliteHistoryRoomStore {
        val rows = MutableStateFlow(initialSnapshots)
        var importAttempts = 0
            private set
        val saveRetentions = mutableListOf<HistoryRetention>()

        override val snapshots: Flow<List<SatelliteHistorySnapshot>> = rows

        override suspend fun legacyImportCompleted(): Boolean = metadataComplete

        override suspend fun hasSnapshots(): Boolean = rows.value.isNotEmpty()

        override suspend fun importLegacySnapshots(
            snapshots: List<SatelliteHistorySnapshot>,
            retention: HistoryRetention,
        ) {
            importAttempts++
            if (failImportAttempts > 0) {
                failImportAttempts--
                throw IllegalStateException("transaction rolled back")
            }
            rows.value = snapshots
            metadataComplete = true
        }

        override suspend fun markLegacyImportComplete() {
            metadataComplete = true
        }

        override suspend fun saveSnapshot(
            snapshot: SatelliteHistorySnapshot,
            retention: HistoryRetention,
        ) {
            saveRetentions += retention
            rows.value = listOf(snapshot) + rows.value
        }

        override suspend fun deleteSnapshot(timestamp: Long) {
            rows.value = rows.value.filterNot { it.timestamp == timestamp }
        }

        override suspend fun clearHistory() {
            rows.value = emptyList()
        }
    }
}
