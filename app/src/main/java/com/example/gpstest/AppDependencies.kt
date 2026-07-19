package com.example.gpstest

import android.app.Application
import com.example.gpstest.data.local.AGpsFileHandler
import com.example.gpstest.data.local.AGpsFileHandlerImpl
import com.example.gpstest.data.local.AGpsInjectionHistoryStore
import com.example.gpstest.data.local.AGpsSettingsStore
import com.example.gpstest.data.local.ExternalGpsEphemerisProvider
import com.example.gpstest.data.local.ExternalGpsEphemerisStore
import com.example.gpstest.data.local.RoomSatelliteHistoryStore
import com.example.gpstest.data.local.SatelliteHistoryDataStore
import com.example.gpstest.data.local.SettingsStore
import com.example.gpstest.data.source.AGpsDataSource
import com.example.gpstest.data.source.AGpsDataSourceImpl
import com.example.gpstest.data.source.AGpsDownloader
import com.example.gpstest.data.source.AGpsDownloaderImpl
import com.example.gpstest.data.source.GnssAcquisitionSession
import com.example.gpstest.data.source.GnssAcquisitionSessionImpl
import com.example.gpstest.data.source.GnssPlatformSource
import com.example.gpstest.data.source.GnssPlatformSourceImpl
import com.example.gpstest.domain.repository.AGpsRepository
import com.example.gpstest.domain.repository.AGpsRepositoryImpl
import com.example.gpstest.domain.repository.GnssRepository
import com.example.gpstest.domain.repository.GnssRepositoryImpl
import com.example.gpstest.domain.repository.SatelliteHistoryRepository
import com.example.gpstest.domain.repository.SatelliteHistoryRepositoryImpl

class AppDependencies private constructor(
    private val application: Application,
    factoryProvider: () -> AppDependencyFactory,
) {
    private val factory = factoryProvider()

    constructor(application: Application) : this(application, ::AppDependencyFactory)

    internal constructor(
        application: Application,
        factory: AppDependencyFactory,
    ) : this(application, { factory })

    val appSettingsStore: SettingsStore by lazy { factory.createSettingsStore(application) }

    private val gnssPlatformSource: GnssPlatformSource by lazy {
        factory.createGnssPlatformSource(application)
    }
    private val gnssAcquisitionSession: GnssAcquisitionSession by lazy {
        factory.createGnssAcquisitionSession(gnssPlatformSource)
    }
    val gnssRepository: GnssRepository by lazy {
        factory.createGnssRepository(gnssAcquisitionSession)
    }

    private val historyDataStore: SatelliteHistoryDataStore by lazy {
        factory.createSatelliteHistoryDataStore(application, appSettingsStore)
    }
    private val roomSatelliteHistoryStore: RoomSatelliteHistoryStore by lazy {
        factory.createRoomSatelliteHistoryStore(application, historyDataStore, appSettingsStore)
    }
    val satelliteHistoryRepository: SatelliteHistoryRepository by lazy {
        factory.createSatelliteHistoryRepository(roomSatelliteHistoryStore)
    }
    val externalGpsEphemerisProvider: ExternalGpsEphemerisProvider by lazy {
        factory.createExternalGpsEphemerisProvider(application)
    }

    private val agpsDataSource: AGpsDataSource by lazy {
        factory.createAGpsDataSource(application)
    }
    private val agpsDownloader: AGpsDownloader by lazy { factory.createAGpsDownloader() }
    private val agpsFileHandler: AGpsFileHandler by lazy {
        factory.createAGpsFileHandler(application)
    }
    val agpsSettingsStore: AGpsSettingsStore by lazy {
        factory.createAGpsSettingsStore(application)
    }
    private val agpsInjectionHistoryStore: AGpsInjectionHistoryStore by lazy {
        factory.createAGpsInjectionHistoryStore(application)
    }
    val agpsRepository: AGpsRepository by lazy {
        factory.createAGpsRepository(
            application,
            agpsDataSource,
            agpsDownloader,
            agpsFileHandler,
            agpsSettingsStore,
            agpsInjectionHistoryStore,
        )
    }
}

internal class AppDependencyFactory(
    val createSettingsStore: (Application) -> SettingsStore = { application ->
        SettingsStore(application)
    },
    val createGnssPlatformSource: (Application) -> GnssPlatformSource = { application ->
        GnssPlatformSourceImpl(application)
    },
    val createGnssAcquisitionSession: (GnssPlatformSource) -> GnssAcquisitionSession = { platformSource ->
        GnssAcquisitionSessionImpl(platformSource)
    },
    val createGnssRepository: (GnssAcquisitionSession) -> GnssRepository = { session ->
        GnssRepositoryImpl(session)
    },
    val createSatelliteHistoryDataStore: (Application, SettingsStore) -> SatelliteHistoryDataStore = {
        application,
        settingsStore,
        ->
        SatelliteHistoryDataStore(application, settingsStore)
    },
    val createRoomSatelliteHistoryStore: (
        Application,
        SatelliteHistoryDataStore,
        SettingsStore,
    ) -> RoomSatelliteHistoryStore = {
        application,
        dataStore,
        settingsStore,
        ->
        RoomSatelliteHistoryStore(application, dataStore, settingsStore)
    },
    val createSatelliteHistoryRepository: (RoomSatelliteHistoryStore) -> SatelliteHistoryRepository = { historyStore ->
        SatelliteHistoryRepositoryImpl(historyStore)
    },
    val createExternalGpsEphemerisProvider: (Application) -> ExternalGpsEphemerisProvider = { application ->
        ExternalGpsEphemerisStore(application)
    },
    val createAGpsDataSource: (Application) -> AGpsDataSource = { application ->
        AGpsDataSourceImpl(application)
    },
    val createAGpsDownloader: () -> AGpsDownloader = { AGpsDownloaderImpl() },
    val createAGpsFileHandler: (Application) -> AGpsFileHandler = { application ->
        AGpsFileHandlerImpl(application)
    },
    val createAGpsSettingsStore: (Application) -> AGpsSettingsStore = { application ->
        AGpsSettingsStore(application)
    },
    val createAGpsInjectionHistoryStore: (Application) -> AGpsInjectionHistoryStore = { application ->
        AGpsInjectionHistoryStore(application)
    },
    val createAGpsRepository: (
        Application,
        AGpsDataSource,
        AGpsDownloader,
        AGpsFileHandler,
        AGpsSettingsStore,
        AGpsInjectionHistoryStore,
    ) -> AGpsRepository = {
        application,
        dataSource,
        downloader,
        fileHandler,
        settingsStore,
        historyStore,
        ->
        AGpsRepositoryImpl(
            application,
            dataSource,
            downloader,
            fileHandler,
            settingsStore,
            historyStore,
        )
    },
)
