package com.example.gpstest.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Serializable
data class SatelliteHistoryEntry(
    val timestamp: Long,
    val svid: Int,
    val constellationName: String,
    val cn0DbHz: Float,
    val usedInFix: Boolean
) {
    fun toStorageKey(): String = "${constellationName}_$svid"
    
    companion object {
        fun fromGnssSatellite(satellite: GnssSatellite, timestamp: Long): SatelliteHistoryEntry {
            return SatelliteHistoryEntry(
                timestamp = timestamp,
                svid = satellite.svid,
                constellationName = satellite.constellation.name,
                cn0DbHz = satellite.cn0DbHz,
                usedInFix = satellite.usedInFix
            )
        }
    }
}

@Serializable
data class SatelliteHistorySnapshot(
    val timestamp: Long,
    val entriesJson: String,
    val usedInFixCount: Int,
    val visibleCount: Int,
    val averageSignalStrength: Float
) {
    fun getEntries(): List<SatelliteHistoryEntry> {
        return try {
            Json.decodeFromString(ListSerializer(SatelliteHistoryEntry.serializer()), entriesJson)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    companion object {
        val EMPTY = SatelliteHistorySnapshot(
            timestamp = 0L,
            entriesJson = "[]",
            usedInFixCount = 0,
            visibleCount = 0,
            averageSignalStrength = 0f
        )
        
        fun fromSatellites(satellites: List<GnssSatellite>, timestamp: Long): SatelliteHistorySnapshot {
            val entries = satellites.map { SatelliteHistoryEntry.fromGnssSatellite(it, timestamp) }
            val entriesJson = Json.encodeToString(ListSerializer(SatelliteHistoryEntry.serializer()), entries)
            val usedInFixCount = satellites.count { it.usedInFix }
            val visibleCount = satellites.count { it.cn0DbHz > 0 }
            val validSignals = satellites.filter { it.cn0DbHz > 0 }
            val avgSignal = if (validSignals.isNotEmpty()) {
                validSignals.map { it.cn0DbHz }.average().toFloat()
            } else 0f
            
            return SatelliteHistorySnapshot(
                timestamp = timestamp,
                entriesJson = entriesJson,
                usedInFixCount = usedInFixCount,
                visibleCount = visibleCount,
                averageSignalStrength = avgSignal
            )
        }
    }
}

data class SatelliteHistoryConfig(
    val maxSnapshots: Int = 100,
    val snapshotIntervalMs: Long = 60_000L,
    val retentionDays: Int = 7
)
