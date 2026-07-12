package com.example.gpstest.viewmodel

import android.app.Application
import com.example.gpstest.domain.model.Constellation
import com.example.gpstest.domain.model.GnssData
import com.example.gpstest.domain.model.GnssSatellite
import com.example.gpstest.domain.model.LocationInfo
import com.example.gpstest.domain.model.MultipathIndicator
import com.example.gpstest.domain.model.SatelliteHistorySnapshot
import com.example.gpstest.domain.repository.GnssRepository
import com.example.gpstest.domain.repository.SatelliteHistoryRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * SatelliteViewModel 测试。覆盖分组、TTFF、信号历史环形缓冲、快照节流、权限与错误态。
 *
 * 策略：
 * - Dispatchers.setMain(StandardTestDispatcher) 驯化 viewModelScope（init 即调 loadHistory）
 * - GnssRepository 用 MockK，返回可推送的 SharedFlow 模拟数据流
 * - historyRepository 可空；测快照时用 mock
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SatelliteViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val application: Application = mockk(relaxed = true)

    private fun makeSatellite(
        svid: Int = 1,
        constellation: Constellation = Constellation.GPS,
        cn0DbHz: Float = 30f,
        usedInFix: Boolean = true,
    ): GnssSatellite =
        GnssSatellite(
            svid = svid,
            constellation = constellation,
            rawConstellationType = constellation.constellationType,
            cn0DbHz = cn0DbHz,
            azimuthDegrees = 0f,
            elevationDegrees = 45f,
            hasAlmanac = true,
            hasEphemeris = true,
            usedInFix = usedInFix,
            carrierFrequencyHz = null,
            carrierCycles = null,
            dopplerShiftHz = null,
            timeNanos = 0L,
            multipathIndicator = MultipathIndicator.NOT_DETECTED,
        )

    private fun makeLocation(): LocationInfo =
        LocationInfo(
            latitude = 39.9,
            longitude = 116.4,
            altitude = 50.0,
            accuracy = 5f,
            speed = 0f,
            bearing = 0f,
            timestamp = System.currentTimeMillis(),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- startListening + 分组 ---

    @Test
    fun `startListening groups satellites into usedInFix visibleOnly searching`() =
        runTest(testDispatcher) {
            val sats =
                listOf(
                    makeSatellite(svid = 1, usedInFix = true, cn0DbHz = 35f),
                    makeSatellite(svid = 2, usedInFix = false, cn0DbHz = 30f), // visibleOnly
                    makeSatellite(svid = 3, usedInFix = false, cn0DbHz = 0f), // searching
                )
            val repository: GnssRepository = mockk()
            every { repository.getGnssData() } returns flowOf(GnssData(sats))
            val historyRepository: SatelliteHistoryRepository = mockk(relaxed = true)
            every { historyRepository.historySnapshots } returns flowOf(emptyList())

            val viewModel = SatelliteViewModel(application, repository, historyRepository)
            viewModel.startListening()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue("应为 Success，实际: $state", state is SatelliteUiState.Success)
            val success = state as SatelliteUiState.Success
            assertEquals(3, success.totalCount)
            assertEquals(1, success.usedInFix.size)
            assertEquals(1, success.visibleOnly.size)
            assertEquals(1, success.searching.size)
            assertEquals(1, success.usedInFix[0].svid)
        }

    @Test
    fun `startListening sets Error state when repository flow throws`() =
        runTest(testDispatcher) {
            val repository: GnssRepository = mockk()
            every { repository.getGnssData() } returns
                kotlinx.coroutines.flow.flow {
                    throw RuntimeException("sensor unavailable")
                }
            val historyRepository: SatelliteHistoryRepository = mockk(relaxed = true)
            every { historyRepository.historySnapshots } returns flowOf(emptyList())

            val viewModel = SatelliteViewModel(application, repository, historyRepository)
            viewModel.startListening()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue("应为 Error，实际: $state", state is SatelliteUiState.Error)
            assertTrue((state as SatelliteUiState.Error).message.contains("sensor unavailable"))
        }

    // --- TTFF ---

    @Test
    fun `ttffState transitions to Completed on first non-null location`() =
        runTest(testDispatcher) {
            val dataFlow = MutableSharedFlow<GnssData>(extraBufferCapacity = 10)
            val repository: GnssRepository = mockk()
            every { repository.getGnssData() } returns dataFlow
            val historyRepository: SatelliteHistoryRepository = mockk(relaxed = true)
            every { historyRepository.historySnapshots } returns flowOf(emptyList())

            val viewModel = SatelliteViewModel(application, repository, historyRepository)
            // 初始为 Measuring
            assertTrue(viewModel.ttffState.value is TtffState.Measuring)
            viewModel.startListening()
            advanceUntilIdle()

            dataFlow.emit(GnssData(satellites = listOf(makeSatellite()), location = makeLocation()))
            advanceUntilIdle()

            val ttff = viewModel.ttffState.value
            assertTrue("应为 Completed，实际: $ttff", ttff is TtffState.Completed)
            assertTrue((ttff as TtffState.Completed).ttffMs >= 0)
        }

    @Test
    fun `ttffState stays Completed on subsequent locations (idempotent)`() =
        runTest(testDispatcher) {
            val dataFlow = MutableSharedFlow<GnssData>(extraBufferCapacity = 10)
            val repository: GnssRepository = mockk()
            every { repository.getGnssData() } returns dataFlow
            val historyRepository: SatelliteHistoryRepository = mockk(relaxed = true)
            every { historyRepository.historySnapshots } returns flowOf(emptyList())

            val viewModel = SatelliteViewModel(application, repository, historyRepository)
            viewModel.startListening()
            advanceUntilIdle()

            dataFlow.emit(GnssData(listOf(makeSatellite()), location = makeLocation()))
            advanceUntilIdle()
            val firstTtff = (viewModel.ttffState.value as TtffState.Completed).ttffMs

            dataFlow.emit(GnssData(listOf(makeSatellite()), location = makeLocation()))
            advanceUntilIdle()

            // 不应重新测量
            assertEquals(firstTtff, (viewModel.ttffState.value as TtffState.Completed).ttffMs)
        }

    @Test
    fun `resetTtff returns ttffState to Measuring`() =
        runTest(testDispatcher) {
            val repository: GnssRepository = mockk()
            every { repository.getGnssData() } returns flowOf(GnssData(emptyList()))
            val historyRepository: SatelliteHistoryRepository = mockk(relaxed = true)
            every { historyRepository.historySnapshots } returns flowOf(emptyList())

            val viewModel = SatelliteViewModel(application, repository, historyRepository)

            viewModel.resetTtff()

            assertTrue(viewModel.ttffState.value is TtffState.Measuring)
        }

    // --- 信号历史环形缓冲 ---

    @Test
    fun `signal history caps at 60 readings per satellite`() =
        runTest(testDispatcher) {
            val dataFlow = MutableSharedFlow<GnssData>(extraBufferCapacity = 100)
            val repository: GnssRepository = mockk()
            every { repository.getGnssData() } returns dataFlow
            val historyRepository: SatelliteHistoryRepository = mockk(relaxed = true)
            every { historyRepository.historySnapshots } returns flowOf(emptyList())

            val viewModel = SatelliteViewModel(application, repository, historyRepository)
            viewModel.startListening()
            advanceUntilIdle()

            // 同一颗卫星推送 61 帧
            val sat = makeSatellite(svid = 7, constellation = Constellation.GPS, cn0DbHz = 40f)
            repeat(61) {
                dataFlow.emit(GnssData(listOf(sat)))
            }
            advanceUntilIdle()

            val history = viewModel.getSignalHistoryForSatellite(sat)
            assertEquals("应恰好 60 条，实际: ${history.size}", 60, history.size)
        }

    @Test
    fun `signal history keyed by constellation name underscore svid`() =
        runTest(testDispatcher) {
            val dataFlow = MutableSharedFlow<GnssData>(extraBufferCapacity = 10)
            val repository: GnssRepository = mockk()
            every { repository.getGnssData() } returns dataFlow
            val historyRepository: SatelliteHistoryRepository = mockk(relaxed = true)
            every { historyRepository.historySnapshots } returns flowOf(emptyList())

            val viewModel = SatelliteViewModel(application, repository, historyRepository)
            viewModel.startListening()
            advanceUntilIdle()

            val sat = makeSatellite(svid = 5, constellation = Constellation.BEIDOU, cn0DbHz = 35f)
            dataFlow.emit(GnssData(listOf(sat)))
            advanceUntilIdle()

            // 键格式 "BEIDOU_5"，不同卫星不串扰
            assertTrue(viewModel.signalHistory.value.containsKey("BEIDOU_5"))
            assertEquals(1, viewModel.getSignalHistoryForSatellite(sat).size)
        }

    @Test
    fun `getSignalHistoryForSatellite returns empty list when no history`() =
        runTest(testDispatcher) {
            val repository: GnssRepository = mockk()
            every { repository.getGnssData() } returns flowOf(GnssData(emptyList()))
            val historyRepository: SatelliteHistoryRepository = mockk(relaxed = true)
            every { historyRepository.historySnapshots } returns flowOf(emptyList())

            val viewModel = SatelliteViewModel(application, repository, historyRepository)
            val history = viewModel.getSignalHistoryForSatellite(makeSatellite(svid = 99))

            assertTrue(history.isEmpty())
        }

    // --- setPermissionDenied ---

    @Test
    fun `setPermissionDenied sets uiState to PermissionRequired`() =
        runTest(testDispatcher) {
            val repository: GnssRepository = mockk()
            every { repository.getGnssData() } returns flowOf(GnssData(emptyList()))
            val historyRepository: SatelliteHistoryRepository = mockk(relaxed = true)
            every { historyRepository.historySnapshots } returns flowOf(emptyList())

            val viewModel = SatelliteViewModel(application, repository, historyRepository)

            viewModel.setPermissionDenied()

            assertEquals(SatelliteUiState.PermissionRequired, viewModel.uiState.value)
        }

    // --- historyRepository 为 null 时构造与运行不崩 ---

    @Test
    fun `ViewModel works with null historyRepository`() =
        runTest(testDispatcher) {
            val repository: GnssRepository = mockk()
            every { repository.getGnssData() } returns flowOf(GnssData(emptyList()))

            val viewModel = SatelliteViewModel(application, repository, historyRepository = null)
            viewModel.startListening()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is SatelliteUiState.Success)
            // 无 historyRepository 时历史应为空
            assertTrue(viewModel.historySnapshots.value.isEmpty())
        }

    // --- historySnapshots 从 repository 流入 ---

    @Test
    fun `historySnapshots reflects historyRepository flow`() =
        runTest(testDispatcher) {
            val repository: GnssRepository = mockk()
            every { repository.getGnssData() } returns flowOf(GnssData(emptyList()))
            val historyRepository: SatelliteHistoryRepository = mockk(relaxed = true)
            val snapshot = SatelliteHistorySnapshot.fromSatellites(emptyList(), 1000L)
            every { historyRepository.historySnapshots } returns flowOf(listOf(snapshot))

            val viewModel = SatelliteViewModel(application, repository, historyRepository)
            advanceUntilIdle()

            assertEquals(1, viewModel.historySnapshots.value.size)
            assertEquals(snapshot, viewModel.historySnapshots.value[0])
        }

    // --- clearHistory ---

    @Test
    fun `clearHistory delegates to repository and empties state`() =
        runTest(testDispatcher) {
            val repository: GnssRepository = mockk()
            every { repository.getGnssData() } returns flowOf(GnssData(emptyList()))
            val historyRepository: SatelliteHistoryRepository = mockk(relaxed = true)
            val snapshot = SatelliteHistorySnapshot.fromSatellites(emptyList(), 1000L)
            every { historyRepository.historySnapshots } returns flowOf(listOf(snapshot))

            val viewModel = SatelliteViewModel(application, repository, historyRepository)
            advanceUntilIdle()

            viewModel.clearHistory()
            advanceUntilIdle()

            coVerify { historyRepository.clearHistory() }
            assertTrue(viewModel.historySnapshots.value.isEmpty())
        }

    // --- saveSnapshotNow ---

    @Test
    fun `saveSnapshotNow saves all satellites when in Success state`() =
        runTest(testDispatcher) {
            val sats = listOf(makeSatellite(svid = 1), makeSatellite(svid = 2))
            val dataFlow = MutableSharedFlow<GnssData>(extraBufferCapacity = 10)
            val repository: GnssRepository = mockk()
            every { repository.getGnssData() } returns dataFlow
            val historyRepository: SatelliteHistoryRepository = mockk(relaxed = true)
            every { historyRepository.historySnapshots } returns flowOf(emptyList())

            val viewModel = SatelliteViewModel(application, repository, historyRepository)
            viewModel.startListening()
            advanceUntilIdle()
            dataFlow.emit(GnssData(sats))
            advanceUntilIdle()

            viewModel.saveSnapshotNow()
            advanceUntilIdle()

            coVerify { historyRepository.saveSnapshot(any()) }
        }

    @Test
    fun `saveSnapshotNow does nothing when not in Success state`() =
        runTest(testDispatcher) {
            val repository: GnssRepository = mockk()
            every { repository.getGnssData() } returns flowOf(GnssData(emptyList()))
            val historyRepository: SatelliteHistoryRepository = mockk(relaxed = true)
            every { historyRepository.historySnapshots } returns flowOf(emptyList())

            val viewModel = SatelliteViewModel(application, repository, historyRepository)
            // 初始为 Loading，无 Success 状态
            viewModel.saveSnapshotNow()
            advanceUntilIdle()

            coVerify(exactly = 0) { historyRepository.saveSnapshot(any()) }
        }
}
