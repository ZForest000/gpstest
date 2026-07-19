package com.example.gpstest.data.local.db

import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.gpstest.domain.model.SatelliteHistoryEntry
import com.example.gpstest.domain.model.SatelliteHistorySnapshot
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Entity(tableName = "history_snapshots")
data class HistorySnapshotEntity(
    @PrimaryKey val timestamp: Long,
    val usedInFixCount: Int,
    val visibleCount: Int,
    val averageSignalStrength: Float,
    val latitude: Double?,
    val longitude: Double?,
    val accuracy: Float?,
    val pdop: Double?,
    val hdop: Double?,
    val vdop: Double?,
    val ttffMs: Long?,
) {
    fun toSnapshot(entries: List<SatelliteHistoryEntry> = emptyList()): SatelliteHistorySnapshot =
        SatelliteHistorySnapshot(
            timestamp = timestamp,
            entriesJson = Json.encodeToString(ListSerializer(SatelliteHistoryEntry.serializer()), entries),
            usedInFixCount = usedInFixCount,
            visibleCount = visibleCount,
            averageSignalStrength = averageSignalStrength,
            latitude = latitude,
            longitude = longitude,
            accuracy = accuracy,
            pdop = pdop,
            hdop = hdop,
            vdop = vdop,
            ttffMs = ttffMs,
        )

    companion object {
        fun fromSnapshot(snapshot: SatelliteHistorySnapshot): HistorySnapshotEntity =
            HistorySnapshotEntity(
                timestamp = snapshot.timestamp,
                usedInFixCount = snapshot.usedInFixCount,
                visibleCount = snapshot.visibleCount,
                averageSignalStrength = snapshot.averageSignalStrength,
                latitude = snapshot.latitude,
                longitude = snapshot.longitude,
                accuracy = snapshot.accuracy,
                pdop = snapshot.pdop,
                hdop = snapshot.hdop,
                vdop = snapshot.vdop,
                ttffMs = snapshot.ttffMs,
            )
    }
}

@Entity(
    tableName = "history_satellites",
    primaryKeys = ["snapshotTimestamp", "constellationName", "svid"],
    foreignKeys = [
        ForeignKey(
            entity = HistorySnapshotEntity::class,
            parentColumns = ["timestamp"],
            childColumns = ["snapshotTimestamp"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("snapshotTimestamp")],
)
data class HistorySatelliteEntity(
    val snapshotTimestamp: Long,
    val svid: Int,
    val constellationName: String,
    val rawConstellationType: Int?,
    val cn0DbHz: Float,
    val usedInFix: Boolean,
) {
    fun toEntry(): SatelliteHistoryEntry =
        SatelliteHistoryEntry(
            timestamp = snapshotTimestamp,
            svid = svid,
            constellationName = constellationName,
            rawConstellationType = rawConstellationType,
            cn0DbHz = cn0DbHz,
            usedInFix = usedInFix,
        )

    companion object {
        fun fromEntry(entry: SatelliteHistoryEntry): HistorySatelliteEntity =
            HistorySatelliteEntity(
                snapshotTimestamp = entry.timestamp,
                svid = entry.svid,
                constellationName = entry.constellationName,
                rawConstellationType = entry.rawConstellationType,
                cn0DbHz = entry.cn0DbHz,
                usedInFix = entry.usedInFix,
            )
    }
}

@Entity(tableName = "history_migration_metadata")
data class HistoryMigrationMetadataEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val legacyImportCompleted: Boolean,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}

data class SnapshotWithSatellites(
    @androidx.room.Embedded val snapshot: HistorySnapshotEntity,
    @Relation(parentColumn = "timestamp", entityColumn = "snapshotTimestamp")
    val satellites: List<HistorySatelliteEntity>,
) {
    fun toSnapshot(): SatelliteHistorySnapshot = snapshot.toSnapshot(satellites.map { it.toEntry() })
}

@Database(
    entities = [
        HistorySnapshotEntity::class,
        HistorySatelliteEntity::class,
        HistoryMigrationMetadataEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class SatelliteHistoryDatabase : RoomDatabase() {
    abstract fun historyDao(): SatelliteHistoryDao

    companion object {
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `history_migration_metadata` (
                            `id` INTEGER NOT NULL,
                            `legacyImportCompleted` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent(),
                    )
                }
            }
    }
}
