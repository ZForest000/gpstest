package com.example.gpstest.data.source

import com.example.gpstest.domain.model.AntennaInfo
import com.example.gpstest.domain.model.GnssCapabilitiesInfo
import com.example.gpstest.domain.model.NavigationMessageFrame
import com.example.gpstest.domain.model.NmeaSentence
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GnssAcquisitionSessionTest {
    @Test
    fun `session module is available for platform source consumers`() {
        val sessionClass = Class.forName("com.example.gpstest.data.source.GnssAcquisitionSessionImpl")

        assertNotNull(sessionClass)
    }

    @Test
    fun `main acquisition has one upstream registration until its final consumer leaves`() =
        runTest {
            assertSharedUntilFinalConsumer(
                stream = GnssAcquisitionSession::getGnssData,
                counterOf = { it.acquisitionCounter },
                sessionFactory = { source -> GnssAcquisitionSessionImpl(source, backgroundScope) },
            )
        }

    @Test
    fun `NMEA has one upstream registration until its final consumer leaves`() =
        runTest {
            assertSharedUntilFinalConsumer(
                stream = GnssAcquisitionSession::getNmeaSentences,
                counterOf = { it.nmeaCounter },
                sessionFactory = { source -> GnssAcquisitionSessionImpl(source, backgroundScope) },
            )
        }

    @Test
    fun `navigation messages have one upstream registration until their final consumer leaves`() =
        runTest {
            assertSharedUntilFinalConsumer(
                stream = GnssAcquisitionSession::getNavigationMessages,
                counterOf = { it.navigationCounter },
                sessionFactory = { source -> GnssAcquisitionSessionImpl(source, backgroundScope) },
            )
        }

    @Test
    fun `antenna information has one upstream registration until its final consumer leaves`() =
        runTest {
            assertSharedUntilFinalConsumer(
                stream = GnssAcquisitionSession::getAntennaInfos,
                counterOf = { it.antennaCounter },
                sessionFactory = { source -> GnssAcquisitionSessionImpl(source, backgroundScope) },
            )
        }

    @Test
    fun `platform failure is delivered to active consumer`() =
        runTest {
            val failure = IllegalStateException("NMEA unavailable")
            val source = FakePlatformSource().apply { nmeaFailure = failure }
            val session = GnssAcquisitionSessionImpl(source, backgroundScope)
            var received: Throwable? = null

            backgroundScope.launch {
                try {
                    session.getNmeaSentences().collect()
                } catch (error: Throwable) {
                    received = error
                }
            }
            runCurrent()

            assertSame(failure, received)
        }

    @Test
    fun `late antenna consumer receives the current antenna snapshot`() =
        runTest {
            val source = AntennaReplaySource()
            val session = GnssAcquisitionSessionImpl(source, backgroundScope)
            val firstValues = mutableListOf<List<AntennaInfo>>()
            val secondValues = mutableListOf<List<AntennaInfo>>()

            val first = backgroundScope.launch { session.getAntennaInfos().collect(firstValues::add) }
            runCurrent()
            val second = backgroundScope.launch { session.getAntennaInfos().collect(secondValues::add) }
            runCurrent()

            assertEquals(1, source.counter.starts)
            assertEquals(1, firstValues.size)
            assertEquals(1, secondValues.size)

            first.cancel()
            second.cancel()
        }

    @Test
    fun `antenna failure is cleared before a later consumer starts a new session`() =
        runTest {
            val source = AntennaRecoverySource()
            val session = GnssAcquisitionSessionImpl(source, backgroundScope)
            var firstFailure: Throwable? = null

            backgroundScope.launch {
                try {
                    session.getAntennaInfos().collect()
                } catch (error: Throwable) {
                    firstFailure = error
                }
            }
            runCurrent()
            assertSame(source.failure, firstFailure)

            source.shouldFail = false
            var secondFailure: Throwable? = null
            val values = mutableListOf<List<AntennaInfo>>()
            backgroundScope.launch {
                try {
                    session.getAntennaInfos().collect(values::add)
                } catch (error: Throwable) {
                    secondFailure = error
                }
            }
            runCurrent()

            assertEquals(2, source.starts)
            assertNull(secondFailure)
            assertEquals(1, values.size)
        }

    private suspend fun <T> TestScope.assertSharedUntilFinalConsumer(
        stream: (GnssAcquisitionSession) -> Flow<T>,
        counterOf: (FakePlatformSource) -> SubscriptionCounter,
        sessionFactory: (FakePlatformSource) -> GnssAcquisitionSession,
    ) {
        val source = FakePlatformSource()
        val counter = counterOf(source)
        val session = sessionFactory(source)

        val first = backgroundScope.launch { stream(session).collect() }
        runCurrent()
        assertEquals(1, counter.starts)

        val second = backgroundScope.launch { stream(session).collect() }
        runCurrent()
        assertEquals(1, counter.starts)

        first.cancel()
        runCurrent()
        assertEquals(0, counter.stops)

        second.cancel()
        runCurrent()
        assertEquals(1, counter.stops)

        val restarted = backgroundScope.launch { stream(session).collect() }
        runCurrent()
        assertEquals(2, counter.starts)

        restarted.cancel()
        runCurrent()
        assertEquals(2, counter.stops)
    }

    private class FakePlatformSource : GnssPlatformSource {
        val acquisitionCounter = SubscriptionCounter()
        val nmeaCounter = SubscriptionCounter()
        val navigationCounter = SubscriptionCounter()
        val antennaCounter = SubscriptionCounter()
        var nmeaFailure: Throwable? = null

        override fun getAcquisitionEvents(): Flow<GnssAcquisitionEvent> = acquisitionCounter.flow()

        override fun getNmeaSentences(): Flow<NmeaSentence> =
            nmeaFailure?.let { failure ->
                kotlinx.coroutines.flow.flow { throw failure }
            } ?: nmeaCounter.flow()

        override fun getNavigationMessages(): Flow<NavigationMessageFrame> = navigationCounter.flow()

        override fun getAntennaInfos(): Flow<List<AntennaInfo>> = antennaCounter.flow()

        override fun isSupported(): Boolean = true

        override fun getGnssCapabilities(): GnssCapabilitiesInfo? = null
    }

    private class SubscriptionCounter {
        var starts = 0
        var stops = 0

        fun <T> flow(): Flow<T> =
            flow {
                starts += 1
                try {
                    awaitCancellation()
                } finally {
                    stops += 1
                }
            }
    }

    private class AntennaReplaySource : GnssPlatformSource {
        val counter = SubscriptionCounter()

        override fun getAcquisitionEvents(): Flow<GnssAcquisitionEvent> = emptyFlow()

        override fun getNmeaSentences(): Flow<NmeaSentence> = emptyFlow()

        override fun getNavigationMessages(): Flow<NavigationMessageFrame> = emptyFlow()

        override fun getAntennaInfos(): Flow<List<AntennaInfo>> =
            flow {
                counter.starts += 1
                try {
                    emit(emptyList())
                    awaitCancellation()
                } finally {
                    counter.stops += 1
                }
            }

        override fun isSupported(): Boolean = true

        override fun getGnssCapabilities(): GnssCapabilitiesInfo? = null
    }

    private class AntennaRecoverySource : GnssPlatformSource {
        val failure = IllegalStateException("Antenna listener unavailable")
        var shouldFail = true
        var starts = 0

        override fun getAcquisitionEvents(): Flow<GnssAcquisitionEvent> = emptyFlow()

        override fun getNmeaSentences(): Flow<NmeaSentence> = emptyFlow()

        override fun getNavigationMessages(): Flow<NavigationMessageFrame> = emptyFlow()

        override fun getAntennaInfos(): Flow<List<AntennaInfo>> =
            flow {
                starts += 1
                if (shouldFail) throw failure
                emit(emptyList())
                awaitCancellation()
            }

        override fun isSupported(): Boolean = true

        override fun getGnssCapabilities(): GnssCapabilitiesInfo? = null
    }
}
