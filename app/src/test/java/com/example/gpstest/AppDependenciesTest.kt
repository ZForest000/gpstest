package com.example.gpstest

import android.app.Application
import com.example.gpstest.data.local.AGpsFileHandler
import com.example.gpstest.data.local.AGpsInjectionHistoryStore
import com.example.gpstest.data.local.AGpsSettingsStore
import com.example.gpstest.data.local.ExternalGpsEphemerisProvider
import com.example.gpstest.data.local.RoomSatelliteHistoryStore
import com.example.gpstest.data.local.SatelliteHistoryDataStore
import com.example.gpstest.data.local.SatelliteHistoryPersistence
import com.example.gpstest.data.local.SettingsStore
import com.example.gpstest.data.source.AGpsDataSource
import com.example.gpstest.data.source.AGpsDownloader
import com.example.gpstest.data.source.GnssAcquisitionSession
import com.example.gpstest.data.source.GnssPlatformSource
import com.example.gpstest.domain.repository.AGpsRepository
import com.example.gpstest.domain.repository.GnssRepository
import com.example.gpstest.domain.repository.SatelliteHistoryRepository
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AppDependenciesTest {
    @Test
    fun `uses application context and reuses dependencies within one application container`() {
        val application = mockk<Application>()
        val appSettingsStore = mockk<SettingsStore>()
        val gnssPlatformSource = mockk<GnssPlatformSource>()
        val gnssAcquisitionSession = mockk<GnssAcquisitionSession>()
        val gnssRepository = mockk<GnssRepository>()
        val historyDataStore = mockk<SatelliteHistoryDataStore>()
        val roomSatelliteHistoryStore = mockk<RoomSatelliteHistoryStore>()
        val satelliteHistoryPersistence = mockk<SatelliteHistoryPersistence>()
        val satelliteHistoryRepository = mockk<SatelliteHistoryRepository>()
        val externalGpsEphemerisProvider = mockk<ExternalGpsEphemerisProvider>()
        val agpsDataSource = mockk<AGpsDataSource>()
        val agpsDownloader = mockk<AGpsDownloader>()
        val agpsFileHandler = mockk<AGpsFileHandler>()
        val agpsSettingsStore = mockk<AGpsSettingsStore>()
        val agpsInjectionHistoryStore = mockk<AGpsInjectionHistoryStore>()
        val agpsRepository = mockk<AGpsRepository>()
        val creationCounts = mutableMapOf<String, Int>()

        fun created(name: String) {
            creationCounts[name] = (creationCounts[name] ?: 0) + 1
        }

        val factory =
            AppDependencyFactory(
                createSettingsStore = { context ->
                    created("settingsStore")
                    assertSame(application, context)
                    appSettingsStore
                },
                createGnssPlatformSource = { context ->
                    created("gnssPlatformSource")
                    assertSame(application, context)
                    gnssPlatformSource
                },
                createGnssAcquisitionSession = { platformSource ->
                    created("gnssAcquisitionSession")
                    assertSame(gnssPlatformSource, platformSource)
                    gnssAcquisitionSession
                },
                createGnssRepository = { session ->
                    created("gnssRepository")
                    assertSame(gnssAcquisitionSession, session)
                    gnssRepository
                },
                createSatelliteHistoryDataStore = { context ->
                    created("historyDataStore")
                    assertSame(application, context)
                    historyDataStore
                },
                createRoomSatelliteHistoryStore = { context ->
                    created("roomHistoryStore")
                    assertSame(application, context)
                    roomSatelliteHistoryStore
                },
                createSatelliteHistoryPersistence = { roomStore, legacyStore, settingsStore ->
                    created("satelliteHistoryPersistence")
                    assertSame(roomSatelliteHistoryStore, roomStore)
                    assertSame(historyDataStore, legacyStore)
                    assertSame(appSettingsStore, settingsStore)
                    satelliteHistoryPersistence
                },
                createSatelliteHistoryRepository = { persistence ->
                    created("satelliteHistoryRepository")
                    assertSame(satelliteHistoryPersistence, persistence)
                    satelliteHistoryRepository
                },
                createExternalGpsEphemerisProvider = { context ->
                    created("externalGpsEphemerisProvider")
                    assertSame(application, context)
                    externalGpsEphemerisProvider
                },
                createAGpsDataSource = { context ->
                    created("agpsDataSource")
                    assertSame(application, context)
                    agpsDataSource
                },
                createAGpsDownloader = {
                    created("agpsDownloader")
                    agpsDownloader
                },
                createAGpsFileHandler = { context ->
                    created("agpsFileHandler")
                    assertSame(application, context)
                    agpsFileHandler
                },
                createAGpsSettingsStore = { context ->
                    created("agpsSettingsStore")
                    assertSame(application, context)
                    agpsSettingsStore
                },
                createAGpsInjectionHistoryStore = { context ->
                    created("agpsInjectionHistoryStore")
                    assertSame(application, context)
                    agpsInjectionHistoryStore
                },
                createAGpsRepository = {
                    context,
                    dataSource,
                    downloader,
                    fileHandler,
                    settingsStore,
                    historyStore,
                    ->
                    created("agpsRepository")
                    assertSame(application, context)
                    assertSame(agpsDataSource, dataSource)
                    assertSame(agpsDownloader, downloader)
                    assertSame(agpsFileHandler, fileHandler)
                    assertSame(agpsSettingsStore, settingsStore)
                    assertSame(agpsInjectionHistoryStore, historyStore)
                    agpsRepository
                },
            )

        val dependencies = AppDependencies(application, factory)

        assertSame(appSettingsStore, dependencies.appSettingsStore)
        assertSame(appSettingsStore, dependencies.appSettingsStore)
        assertSame(gnssRepository, dependencies.gnssRepository)
        assertSame(gnssRepository, dependencies.gnssRepository)
        assertSame(satelliteHistoryRepository, dependencies.satelliteHistoryRepository)
        assertSame(satelliteHistoryRepository, dependencies.satelliteHistoryRepository)
        assertSame(externalGpsEphemerisProvider, dependencies.externalGpsEphemerisProvider)
        assertSame(externalGpsEphemerisProvider, dependencies.externalGpsEphemerisProvider)
        assertSame(agpsSettingsStore, dependencies.agpsSettingsStore)
        assertSame(agpsSettingsStore, dependencies.agpsSettingsStore)
        assertSame(agpsRepository, dependencies.agpsRepository)
        assertSame(agpsRepository, dependencies.agpsRepository)

        assertEquals(1, creationCounts["settingsStore"])
        assertEquals(1, creationCounts["gnssPlatformSource"])
        assertEquals(1, creationCounts["gnssAcquisitionSession"])
        assertEquals(1, creationCounts["gnssRepository"])
        assertEquals(1, creationCounts["historyDataStore"])
        assertEquals(1, creationCounts["roomHistoryStore"])
        assertEquals(1, creationCounts["satelliteHistoryPersistence"])
        assertEquals(1, creationCounts["satelliteHistoryRepository"])
        assertEquals(1, creationCounts["externalGpsEphemerisProvider"])
        assertEquals(1, creationCounts["agpsDataSource"])
        assertEquals(1, creationCounts["agpsDownloader"])
        assertEquals(1, creationCounts["agpsFileHandler"])
        assertEquals(1, creationCounts["agpsSettingsStore"])
        assertEquals(1, creationCounts["agpsInjectionHistoryStore"])
        assertEquals(1, creationCounts["agpsRepository"])
    }
}
