package com.example.gpstest.data.local

import com.example.gpstest.domain.model.AppSettings
import com.example.gpstest.domain.model.SatelliteHistorySnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class LegacySatelliteHistory(
    val snapshots: List<SatelliteHistorySnapshot>,
    val markerWritten: Boolean,
)

internal data class HistoryRetention(
    val cutoffTimestamp: Long,
    val maxSnapshots: Int,
)

internal interface LegacySatelliteHistoryStore {
    suspend fun readLegacyHistory(): LegacySatelliteHistory

    suspend fun markRoomMigrationComplete()

    suspend fun clearLegacyHistory()
}

internal interface SatelliteHistoryRoomStore {
    val snapshots: Flow<List<SatelliteHistorySnapshot>>

    suspend fun legacyImportCompleted(): Boolean

    suspend fun hasSnapshots(): Boolean

    suspend fun importLegacySnapshots(
        snapshots: List<SatelliteHistorySnapshot>,
        retention: HistoryRetention,
    )

    suspend fun markLegacyImportComplete()

    suspend fun saveSnapshot(snapshot: SatelliteHistorySnapshot, retention: HistoryRetention)

    suspend fun deleteSnapshot(timestamp: Long)

    suspend fun clearHistory()
}

class SatelliteHistoryPersistence internal constructor(
    private val roomStore: SatelliteHistoryRoomStore,
    private val legacyStore: LegacySatelliteHistoryStore,
    private val settings: Flow<AppSettings>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val readyMutex = Mutex()
    private var ready = false

    val snapshots: Flow<List<SatelliteHistorySnapshot>> =
        flow {
            ensureReady()
            emitAll(roomStore.snapshots)
        }

    suspend fun save(snapshot: SatelliteHistorySnapshot) {
        ensureReady()
        roomStore.saveSnapshot(snapshot, retention())
    }

    suspend fun delete(timestamp: Long) {
        ensureReady()
        roomStore.deleteSnapshot(timestamp)
    }

    suspend fun clear() {
        ensureReady()
        roomStore.clearHistory()
        legacyStore.clearLegacyHistory()
    }

    private suspend fun ensureReady() {
        readyMutex.withLock {
            if (ready) return

            if (roomStore.legacyImportCompleted()) {
                legacyStore.markRoomMigrationComplete()
            } else {
                val legacy = legacyStore.readLegacyHistory()
                val shouldImport =
                    legacy.snapshots.isNotEmpty() && (!legacy.markerWritten || !roomStore.hasSnapshots())

                if (shouldImport) {
                    roomStore.importLegacySnapshots(legacy.snapshots, retention())
                } else {
                    roomStore.markLegacyImportComplete()
                }
                legacyStore.markRoomMigrationComplete()
            }

            ready = true
        }
    }

    private suspend fun retention(): HistoryRetention {
        val appSettings = settings.first()
        return HistoryRetention(
            cutoffTimestamp = clock() - appSettings.retentionDays * MS_PER_DAY,
            maxSnapshots = appSettings.maxSnapshots,
        )
    }

    private companion object {
        const val MS_PER_DAY = 24L * 60 * 60 * 1000
    }
}
