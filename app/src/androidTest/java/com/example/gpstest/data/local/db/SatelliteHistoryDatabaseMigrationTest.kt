package com.example.gpstest.data.local.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SatelliteHistoryDatabaseMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            SatelliteHistoryDatabase::class.java.canonicalName,
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun migrate1To2KeepsExistingSnapshotAndSatelliteRowsAfterReopen() =
        runBlocking {
            helper.createDatabase(TEST_DB, 1).apply {
                execSQL(
                    """
                    INSERT INTO history_snapshots (
                        timestamp,
                        usedInFixCount,
                        visibleCount,
                        averageSignalStrength,
                        latitude,
                        longitude,
                        accuracy,
                        pdop,
                        hdop,
                        vdop,
                        ttffMs
                    ) VALUES (1000, 1, 1, 35.5, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
                    """.trimIndent(),
                )
                execSQL(
                    """
                    INSERT INTO history_satellites (
                        snapshotTimestamp,
                        svid,
                        constellationName,
                        rawConstellationType,
                        cn0DbHz,
                        usedInFix
                    ) VALUES (1000, 7, 'GPS', 1, 35.5, 1)
                    """.trimIndent(),
                )
                close()
            }

            helper.runMigrationsAndValidate(
                TEST_DB,
                2,
                true,
                SatelliteHistoryDatabase.MIGRATION_1_2,
            )

            val database: SatelliteHistoryDatabase = Room
                .databaseBuilder(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    SatelliteHistoryDatabase::class.java,
                    TEST_DB,
                )
                .addMigrations(SatelliteHistoryDatabase.MIGRATION_1_2)
                .build()
            try {
                val snapshot =
                    database
                        .historyDao()
                        .observeAll()
                        .first()
                        .single()
                        .toSnapshot()
                val satellite = snapshot.getEntries().single()

                assertEquals(1000L, snapshot.timestamp)
                assertEquals(1, snapshot.usedInFixCount)
                assertEquals(7, satellite.svid)
                assertEquals("GPS", satellite.constellationName)
            } finally {
                database.close()
            }
        }

    private companion object {
        const val TEST_DB = "satellite-history-migration-test"
    }
}
