package com.example.gpstest.viewmodel

import android.app.Application
import android.net.Uri
import com.example.gpstest.domain.model.AGpsSettings
import com.example.gpstest.domain.model.AGpsStatus
import com.example.gpstest.domain.model.DataStatus
import com.example.gpstest.domain.repository.AGpsRepository
import com.example.gpstest.domain.repository.FileValidationResult
import com.example.gpstest.service.AGpsUpdateWorker
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * AGpsViewModel 状态机测试。覆盖下载注入、时间同步、清除、设置更新、验证的状态流转。
 *
 * 策略：
 * - Dispatchers.setMain(StandardTestDispatcher) 驯化 viewModelScope
 * - AGpsRepository 用 MockK relaxed mock
 * - AGpsUpdateWorker（静态 schedule/cancel）用 mockkObject 桩掉，避免 Robolectric
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AGpsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val application: Application = mockk(relaxed = true)
    private val repository: AGpsRepository = mockk(relaxed = true)

    // 默认 Flow 桩：避免 lazy 属性访问时崩
    private val statusFlow = MutableStateFlow(AGpsStatus())
    private val settingsFlow = MutableStateFlow(AGpsSettings())
    private val historyFlow = MutableStateFlow<List<com.example.gpstest.domain.model.AGpsInjectionRecord>>(emptyList())

    private lateinit var viewModel: AGpsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { repository.status } returns statusFlow
        every { repository.settings } returns settingsFlow
        every { repository.injectionHistory } returns historyFlow
        coEvery { repository.hydrateHistory() } just Runs
        // AGpsUpdateWorker 伴生对象静态方法桩（schedule/cancel 需 Context + WorkManager）
        mockkObject(AGpsUpdateWorker)
        every { AGpsUpdateWorker.schedule(any(), any()) } just Runs
        every { AGpsUpdateWorker.cancel(any()) } just Runs
        viewModel = AGpsViewModel(application, repository)
    }

    @After
    fun tearDown() {
        unmockkObject(AGpsUpdateWorker)
        Dispatchers.resetMain()
    }

    // --- downloadAndInject 状态机 ---

    @Test
    fun `downloadAndInject transitions to Success when repository succeeds`() =
        runTest(testDispatcher) {
            coEvery { repository.downloadAndInject() } returns Result.success(Unit)

            viewModel.downloadAndInject()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is AGpsUiState.Success)
        }

    @Test
    fun `downloadAndInject transitions to Error when repository fails`() =
        runTest(testDispatcher) {
            coEvery { repository.downloadAndInject() } returns
                Result.failure(Exception("network down"))

            viewModel.downloadAndInject()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue("应为 Error，实际: $state", state is AGpsUiState.Error)
            assertTrue((state as AGpsUiState.Error).message.contains("network down"))
        }

    @Test
    fun `downloadAndInject falls back to default message when exception has no message`() =
        runTest(testDispatcher) {
            coEvery { repository.downloadAndInject() } returns Result.failure(Exception())

            viewModel.downloadAndInject()
            advanceUntilIdle()

            val state = viewModel.uiState.value as AGpsUiState.Error
            assertEquals("下载失败", state.message)
        }

    // --- injectTime 状态机 ---

    @Test
    fun `injectTime transitions to Success on repository success`() =
        runTest(testDispatcher) {
            coEvery { repository.injectTime() } returns Result.success(Unit)

            viewModel.injectTime()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is AGpsUiState.Success)
        }

    @Test
    fun `injectTime transitions to Error on repository failure`() =
        runTest(testDispatcher) {
            coEvery { repository.injectTime() } returns Result.failure(Exception("HAL busy"))

            viewModel.injectTime()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is AGpsUiState.Error)
        }

    // --- clearApsData 状态机 ---

    @Test
    fun `clearApsData transitions to Success on repository success`() =
        runTest(testDispatcher) {
            coEvery { repository.clearApsData() } returns Result.success(Unit)

            viewModel.clearApsData()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is AGpsUiState.Success)
        }

    @Test
    fun `clearApsData transitions to Error on repository failure`() =
        runTest(testDispatcher) {
            coEvery { repository.clearApsData() } returns Result.failure(Exception("denied"))

            viewModel.clearApsData()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is AGpsUiState.Error)
        }

    // --- updateSettings ---

    @Test
    fun `updateSettings calls repository updateSettings`() =
        runTest(testDispatcher) {
            val settings = AGpsSettings(autoUpdateEnabled = true, updateIntervalHours = 12)

            viewModel.updateSettings(settings)
            advanceUntilIdle()

            coVerify { repository.updateSettings(settings) }
        }

    @Test
    fun `updateSettings schedules worker when autoUpdate enabled`() =
        runTest(testDispatcher) {
            val settings = AGpsSettings(autoUpdateEnabled = true, updateIntervalHours = 6)

            viewModel.updateSettings(settings)
            advanceUntilIdle()

            // 验证调用了 schedule（用 Application + intervalHours）
            io.mockk
                .verify(exactly = 1) { AGpsUpdateWorker.schedule(any(), 6) }
            io.mockk.verify(exactly = 0) { AGpsUpdateWorker.cancel(any()) }
        }

    @Test
    fun `updateSettings cancels worker when autoUpdate disabled`() =
        runTest(testDispatcher) {
            val settings = AGpsSettings(autoUpdateEnabled = false)

            viewModel.updateSettings(settings)
            advanceUntilIdle()

            io.mockk.verify(exactly = 0) { AGpsUpdateWorker.schedule(any(), any()) }
            io.mockk.verify(exactly = 1) { AGpsUpdateWorker.cancel(any()) }
        }

    // --- importAndInject 状态机 ---

    @Test
    fun `importAndInject transitions to Success when repository succeeds`() =
        runTest(testDispatcher) {
            val uri = mockk<Uri>()
            every { uri.toString() } returns "content://downloads/xtra.bin"
            coEvery { repository.importAndInject("content://downloads/xtra.bin") } returns
                Result.success(Unit)

            viewModel.importAndInject(uri)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is AGpsUiState.Success)
            assertEquals(
                "文件导入并注入成功",
                (viewModel.uiState.value as AGpsUiState.Success).message,
            )
            coVerify { repository.importAndInject("content://downloads/xtra.bin") }
        }

    @Test
    fun `importAndInject transitions to Error when repository fails`() =
        runTest(testDispatcher) {
            val uri = mockk<Uri>()
            every { uri.toString() } returns "content://downloads/bad.bin"
            coEvery { repository.importAndInject("content://downloads/bad.bin") } returns
                Result.failure(Exception("invalid xtra"))

            viewModel.importAndInject(uri)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue("应为 Error，实际: $state", state is AGpsUiState.Error)
            assertTrue((state as AGpsUiState.Error).message.contains("invalid xtra"))
        }

    @Test
    fun `importAndInject falls back to default message when exception has no message`() =
        runTest(testDispatcher) {
            val uri = mockk<Uri>()
            every { uri.toString() } returns "content://downloads/xtra.bin"
            coEvery { repository.importAndInject(any()) } returns Result.failure(Exception())

            viewModel.importAndInject(uri)
            advanceUntilIdle()

            val state = viewModel.uiState.value as AGpsUiState.Error
            assertEquals("导入失败", state.message)
        }

    // --- clearInjectionHistory ---

    @Test
    fun `clearInjectionHistory calls repository clearInjectionHistory`() =
        runTest(testDispatcher) {
            coEvery { repository.clearInjectionHistory() } just Runs

            viewModel.clearInjectionHistory()
            advanceUntilIdle()

            coVerify(exactly = 1) { repository.clearInjectionHistory() }
        }

    // --- validateCurrentSource ---

    @Test
    fun `validateCurrentSource exposes valid result and Success state`() =
        runTest(testDispatcher) {
            val valid = FileValidationResult(isValid = true, fileSize = 2048)
            coEvery { repository.validateCurrentSource() } returns valid

            viewModel.validateCurrentSource()
            advanceUntilIdle()

            assertEquals(valid, viewModel.validationResult.value)
            assertTrue(viewModel.uiState.value is AGpsUiState.Success)
        }

    @Test
    fun `validateCurrentSource exposes invalid result and Error state`() =
        runTest(testDispatcher) {
            val invalid =
                FileValidationResult(
                    isValid = false,
                    fileSize = 0,
                    errorMessage = "数据损坏",
                    errorType = "INVALID_FORMAT",
                )
            coEvery { repository.validateCurrentSource() } returns invalid

            viewModel.validateCurrentSource()
            advanceUntilIdle()

            assertEquals(invalid, viewModel.validationResult.value)
            assertTrue(viewModel.uiState.value is AGpsUiState.Error)
        }

    // --- 纯逻辑方法 ---

    @Test
    fun `clearMessage resets uiState to Idle`() {
        viewModel.clearMessage()
        assertEquals(AGpsUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `clearValidationResult resets validationResult to null`() {
        viewModel.clearValidationResult()
        assertNull(viewModel.validationResult.value)
    }

    // --- lazy Flow 传播（访问属性触发 lazy 收集）---

    @Test
    fun `settings StateFlow reflects repository settings changes`() =
        runTest(testDispatcher) {
            // 触发 lazy 初始化，开启 viewModelScope 收集
            val initial = viewModel.settings.value
            assertEquals(AGpsSettings(), initial)

            val newSettings = AGpsSettings(updateIntervalHours = 6)
            settingsFlow.value = newSettings
            advanceUntilIdle()

            assertEquals(newSettings, viewModel.settings.value)
        }

    @Test
    fun `status StateFlow reflects repository status changes`() =
        runTest(testDispatcher) {
            val initial = viewModel.status.value
            assertEquals(AGpsStatus(), initial)

            val newStatus =
                AGpsStatus(
                    timeStatus = DataStatus.VALID,
                    ephemerisStatus = DataStatus.VALID,
                )
            statusFlow.value = newStatus
            advanceUntilIdle()

            assertEquals(newStatus, viewModel.status.value)
        }
}
