package com.example.gpstest.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * 历史快照中的单颗卫星记录。仅保存可跨时间对比的稳定字段，
 * 原始测量值（多普勒、多路径等）因波动过快，不适合历史分析而排除。
 */
@Serializable
data class SatelliteHistoryEntry(
    val timestamp: Long,
    val svid: Int,
    val constellationName: String,
    val rawConstellationType: Int? = null,
    val cn0DbHz: Float,
    val usedInFix: Boolean,
) {
    fun toStorageKey(): String = "${constellationName}_$svid"

    companion object {
        fun fromGnssSatellite(
            satellite: GnssSatellite,
            timestamp: Long,
        ): SatelliteHistoryEntry =
            SatelliteHistoryEntry(
                timestamp = timestamp,
                svid = satellite.svid,
                constellationName = satellite.constellation.name,
                rawConstellationType = satellite.rawConstellationType,
                cn0DbHz = satellite.cn0DbHz,
                usedInFix = satellite.usedInFix,
            )
    }
}

/**
 * 某一时刻所有可见卫星的快照。使用 JSON 编码的 entriesJson 存储卫星列表，
 * 而非多键存储，因为 DataStore 操作是单键原子性的。
 */
@Serializable
data class SatelliteHistorySnapshot(
    val timestamp: Long,
    val entriesJson: String,
    val usedInFixCount: Int,
    val visibleCount: Int,
    val averageSignalStrength: Float,
) {
    fun getEntries(): List<SatelliteHistoryEntry> =
        try {
            Json.decodeFromString(ListSerializer(SatelliteHistoryEntry.serializer()), entriesJson)
        } catch (e: Exception) {
            emptyList()
        }

    companion object {
        val EMPTY =
            SatelliteHistorySnapshot(
                timestamp = 0L,
                entriesJson = "[]",
                usedInFixCount = 0,
                visibleCount = 0,
                averageSignalStrength = 0f,
            )

        fun fromSatellites(
            satellites: List<GnssSatellite>,
            timestamp: Long,
        ): SatelliteHistorySnapshot {
            val entries = satellites.map { SatelliteHistoryEntry.fromGnssSatellite(it, timestamp) }
            val entriesJson = Json.encodeToString(ListSerializer(SatelliteHistoryEntry.serializer()), entries)
            val usedInFixCount = satellites.count { it.usedInFix }
            val visibleCount = satellites.count { it.cn0DbHz > 0 }
            val validSignals = satellites.filter { it.cn0DbHz > 0 }
            val avgSignal =
                if (validSignals.isNotEmpty()) {
                    validSignals.map { it.cn0DbHz }.average().toFloat()
                } else {
                    0f
                }

            return SatelliteHistorySnapshot(
                timestamp = timestamp,
                entriesJson = entriesJson,
                usedInFixCount = usedInFixCount,
                visibleCount = visibleCount,
                averageSignalStrength = avgSignal,
            )
        }
    }
}

/** 历史快照存储配置。100 个快照 × 60 秒间隔 ≈ 1.7 小时，保留 7 天。 */
data class SatelliteHistoryConfig(
    val maxSnapshots: Int = 100,
    val snapshotIntervalMs: Long = 60_000L,
    val retentionDays: Int = 7,
)
