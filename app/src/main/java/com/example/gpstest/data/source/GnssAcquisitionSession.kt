package com.example.gpstest.data.source

import com.example.gpstest.domain.model.AntennaInfo
import com.example.gpstest.domain.model.GnssCapabilitiesInfo
import com.example.gpstest.domain.model.GnssData
import com.example.gpstest.domain.model.NavigationMessageFrame
import com.example.gpstest.domain.model.NmeaSentence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn

/** 所有 GNSS consumer 共享的平台 acquisition session。 */
interface GnssAcquisitionSession {
    fun getGnssData(): Flow<GnssData>

    fun getNmeaSentences(): Flow<NmeaSentence>

    fun getNavigationMessages(): Flow<NavigationMessageFrame>

    fun getAntennaInfos(): Flow<List<AntennaInfo>>

    fun isSupported(): Boolean

    fun getGnssCapabilities(): GnssCapabilitiesInfo?
}

class GnssAcquisitionSessionImpl(
    private val platformSource: GnssPlatformSource,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : GnssAcquisitionSession {
    private val gnssData: SharedFlow<Result<GnssData>> =
        shareResults(
            flow {
                val fusion = GnssEventFusion()
                platformSource.getAcquisitionEvents().collect { event ->
                    fusion.onEvent(event)?.let { data -> emit(data) }
                }
            },
            scope,
        )

    private val nmeaSentences: SharedFlow<Result<NmeaSentence>> =
        shareResults(platformSource.getNmeaSentences(), scope)

    private val navigationMessages: SharedFlow<Result<NavigationMessageFrame>> =
        shareResults(platformSource.getNavigationMessages(), scope)

    private val antennaInfos: SharedFlow<Result<List<AntennaInfo>>> =
        shareResults(platformSource.getAntennaInfos(), scope, replay = 1)

    override fun getGnssData(): Flow<GnssData> = gnssData.values()

    override fun getNmeaSentences(): Flow<NmeaSentence> = nmeaSentences.values()

    override fun getNavigationMessages(): Flow<NavigationMessageFrame> = navigationMessages.values()

    override fun getAntennaInfos(): Flow<List<AntennaInfo>> = antennaInfos.values()

    override fun isSupported(): Boolean = platformSource.isSupported()

    override fun getGnssCapabilities(): GnssCapabilitiesInfo? = platformSource.getGnssCapabilities()

    private fun <T> shareResults(
        source: Flow<T>,
        scope: CoroutineScope,
        replay: Int = 0,
    ): SharedFlow<Result<T>> =
        source
            .map(Result.Companion::success)
            .catch { error -> emit(Result.failure(error)) }
            .shareIn(
                scope = scope,
                started =
                    SharingStarted.WhileSubscribed(
                        stopTimeoutMillis = 0,
                        replayExpirationMillis = 0,
                    ),
                replay = replay,
            )

    private fun <T> Flow<Result<T>>.values(): Flow<T> = map(Result<T>::getOrThrow)
}
