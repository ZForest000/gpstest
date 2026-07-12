package com.example.gpstest.domain.repository

import com.example.gpstest.data.local.AGpsFileHandler
import com.example.gpstest.data.local.AGpsSettingsStore
import com.example.gpstest.data.source.AGpsDataSource
import com.example.gpstest.data.source.AGpsDownloader
import com.example.gpstest.data.validator.XtraDataValidator
import com.example.gpstest.domain.model.AGpsSettings
import com.example.gpstest.domain.model.Constellation
import com.example.gpstest.domain.model.DataStatus
import com.example.gpstest.domain.model.GnssSatellite
import com.example.gpstest.domain.model.InjectionType
import com.example.gpstest.domain.model.MultipathIndicator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * AGpsRepositoryImpl 单元测试。覆盖多 URL 回退、注入验证阈值、历史记录上限、时间衰减状态机。
 *
 * 策略：4 个依赖用 MockK 桩，注入真实 XtraDataValidator（显式参数，避开 BuildConfig.DEBUG 默认）。
 * isReturnDefaultValues=true 让裸 android.util.Log 静默返回默认值。
 */
class AGpsRepositoryImplTest {
    private val dataSource: AGpsDataSource = mockk(relaxed = true)
    private val downloader: AGpsDownloader = mockk(relaxed = true)
    private val fileHandler: AGpsFileHandler = mockk(relaxed = true)
    private val settingsStore: AGpsSettingsStore = mockk(relaxed = true)

    // strictMode=false 以跳过 MIME 校验，让 makeValidData() 通过；显式传参避开 BuildConfig.DEBUG
    private val validator =
        XtraDataValidator(
            minSizeBytes = 1024,
            maxSizeBytes = 2 * 1024 * 1024,
            strictMode = false,
        )

    private val defaultUrls =
        listOf(
            "https://xtrapath1.izatcloud.net/xtra3grc.bin",
            "https://xtrapath2.izatcloud.net/xtra3grc.bin",
            "https://xtrapath3.izatcloud.net/xtra3grc.bin",
        )

    private fun makeRepository(
        settings: AGpsSettings = AGpsSettings(downloadUrl = "https://user.example.com/xtra.bin"),
    ): AGpsRepositoryImpl {
        every { settingsStore.settings } returns flowOf(settings)
        return AGpsRepositoryImpl(dataSource, downloader, fileHandler, settingsStore, validator)
    }

    /** 有效二进制数据：字节数组循环填充 0-255，足够大小通过校验。 */
    private fun makeValidData(size: Int = 2048): ByteArray = ByteArray(size) { (it % 256).toByte() }

    private fun makeSatellite(
        svid: Int = 1,
        hasEphemeris: Boolean = true,
        hasAlmanac: Boolean = true,
        constellation: Constellation = Constellation.GPS,
    ): GnssSatellite =
        GnssSatellite(
            svid = svid,
            constellation = constellation,
            rawConstellationType = constellation.constellationType,
            cn0DbHz = 30f,
            azimuthDegrees = 0f,
            elevationDegrees = 45f,
            hasAlmanac = hasAlmanac,
            hasEphemeris = hasEphemeris,
            usedInFix = true,
            carrierFrequencyHz = null,
            carrierCycles = null,
            dopplerShiftHz = null,
            timeNanos = 0L,
            multipathIndicator = MultipathIndicator.NOT_DETECTED,
        )

    // --- downloadAndInject: 多 URL 回退 ---

    @Test
    fun `downloadAndInject returns success when user URL downloads and injects successfully`() =
        runTest {
            val repo = makeRepository()
            val validData = makeValidData()
            coEvery { downloader.download(any()) } returns Result.success(validData)
            coEvery { dataSource.injectXtraFromUrl(any()) } returns Result.success(Unit)

            val result = repo.downloadAndInject()

            assertTrue(result.isSuccess)
            // 用户 URL 成功则不应尝试默认 URL 的注入
            coVerify(exactly = 1) { downloader.download(any()) }
            coVerify(exactly = 1) { dataSource.injectXtraFromUrl(any()) }
        }

    @Test
    fun `downloadAndInject falls back to default URL when user URL download fails`() =
        runTest {
            val userUrl = "https://user.example.com/xtra.bin"
            val repo = makeRepository(settings = AGpsSettings(downloadUrl = userUrl))
            every { downloader.getDefaultUrls() } returns defaultUrls
            // 用户 URL 失败，第一个默认 URL 成功
            coEvery { downloader.download(userUrl) } returns
                Result.failure(IOException("connection refused"))
            coEvery { downloader.download(defaultUrls[0]) } returns Result.success(makeValidData())
            coEvery { dataSource.injectXtraFromUrl(defaultUrls[0]) } returns Result.success(Unit)

            val result = repo.downloadAndInject()

            assertTrue(result.isSuccess)
            coVerify { downloader.download(userUrl) }
            coVerify { downloader.download(defaultUrls[0]) }
            coVerify(exactly = 0) { downloader.download(defaultUrls[1]) }
        }

    @Test
    fun `downloadAndInject skips blank user URL and starts from default URLs`() =
        runTest {
            val repo = makeRepository(settings = AGpsSettings(downloadUrl = "   "))
            every { downloader.getDefaultUrls() } returns defaultUrls
            coEvery { downloader.download(defaultUrls[0]) } returns Result.success(makeValidData())
            coEvery { dataSource.injectXtraFromUrl(defaultUrls[0]) } returns Result.success(Unit)

            val result = repo.downloadAndInject()

            assertTrue(result.isSuccess)
            // 空白 URL 应被过滤，第一个尝试的是默认 URL
            coVerify { downloader.download(defaultUrls[0]) }
        }

    @Test
    fun `downloadAndInject de-duplicates user URL equal to a default URL`() =
        runTest {
            val repo =
                makeRepository(
                    settings = AGpsSettings(downloadUrl = defaultUrls[0]),
                )
            every { downloader.getDefaultUrls() } returns defaultUrls
            coEvery { downloader.download(defaultUrls[0]) } returns Result.success(makeValidData())
            coEvery { dataSource.injectXtraFromUrl(defaultUrls[0]) } returns Result.success(Unit)

            val result = repo.downloadAndInject()

            assertTrue(result.isSuccess)
            // 去重后 defaultUrls[0] 只尝试一次
            coVerify(exactly = 1) { downloader.download(defaultUrls[0]) }
            coVerify(exactly = 0) { downloader.download(defaultUrls[1]) }
        }

    @Test
    fun `downloadAndInject returns failure when all URLs fail download`() =
        runTest {
            val repo = makeRepository()
            every { downloader.getDefaultUrls() } returns defaultUrls
            coEvery { downloader.download(any()) } returns
                Result.failure(IOException("network error"))

            val result = repo.downloadAndInject()

            assertTrue(result.isFailure)
            val message = result.exceptionOrNull()?.message.orEmpty()
            // 错误信息应包含失败原因
            assertTrue("应包含 network error，实际: $message", message.contains("network error"))
        }

    @Test
    fun `downloadAndInject continues to next URL when download returns empty data`() =
        runTest {
            val userUrl = "https://user.example.com/xtra.bin"
            val repo = makeRepository(settings = AGpsSettings(downloadUrl = userUrl))
            every { downloader.getDefaultUrls() } returns defaultUrls
            // 用户 URL 返回空数据（isFailure=false 但 data 为空）
            coEvery { downloader.download(userUrl) } returns Result.success(ByteArray(0))
            coEvery { downloader.download(defaultUrls[0]) } returns Result.success(makeValidData())
            coEvery { dataSource.injectXtraFromUrl(defaultUrls[0]) } returns Result.success(Unit)

            val result = repo.downloadAndInject()

            assertTrue(result.isSuccess)
            coVerify { downloader.download(defaultUrls[0]) }
        }

    @Test
    fun `downloadAndInject continues to next URL when injection fails`() =
        runTest {
            val userUrl = "https://user.example.com/xtra.bin"
            val repo = makeRepository(settings = AGpsSettings(downloadUrl = userUrl))
            every { downloader.getDefaultUrls() } returns defaultUrls
            coEvery { downloader.download(any()) } returns Result.success(makeValidData())
            // 用户 URL 注入失败，默认 URL[0] 注入成功
            coEvery { dataSource.injectXtraFromUrl(userUrl) } returns
                Result.failure(IOException("HAL rejected"))
            coEvery { dataSource.injectXtraFromUrl(defaultUrls[0]) } returns Result.success(Unit)

            val result = repo.downloadAndInject()

            assertTrue(result.isSuccess)
            coVerify { dataSource.injectXtraFromUrl(userUrl) }
            coVerify { dataSource.injectXtraFromUrl(defaultUrls[0]) }
        }

    @Test
    fun `downloadAndInject records success in injection history on success`() =
        runTest {
            val repo = makeRepository()
            coEvery { downloader.download(any()) } returns Result.success(makeValidData())
            coEvery { dataSource.injectXtraFromUrl(any()) } returns Result.success(Unit)

            repo.downloadAndInject()

            val history = repo.injectionHistory.first()
            assertEquals(1, history.size)
            assertTrue(history[0].success)
            assertEquals(InjectionType.XTRA, history[0].type)
            assertNull(history[0].errorMessage)
        }

    @Test
    fun `downloadAndInject records failure with error message when all methods fail`() =
        runTest {
            val repo = makeRepository()
            every { downloader.getDefaultUrls() } returns defaultUrls
            coEvery { downloader.download(any()) } returns
                Result.failure(IOException("timeout"))

            repo.downloadAndInject()

            val history = repo.injectionHistory.first()
            assertEquals(1, history.size)
            assertFalse(history[0].success)
            assertNotNull(history[0].errorMessage)
        }

    // --- verifyInjection: 阈值与状态分类 ---

    @Test
    fun `verifyInjection returns failure for empty satellite list`() =
        runTest {
            val repo = makeRepository()
            val result = repo.verifyInjection(emptyList())

            assertFalse(result.isSuccess)
            assertEquals(0, result.totalSatellites)
            assertEquals(0, result.satellitesWithEphemeris)
            assertEquals(0f, result.ephemerisRatio, 0.001f)
        }

    @Test
    fun `verifyInjection succeeds when ephemeris ratio at or above 50 percent`() =
        runTest {
            val repo = makeRepository()
            // 5 颗卫星，3 颗有星历（60% >= 50%）
            val satellites =
                listOf(
                    makeSatellite(svid = 1, hasEphemeris = true),
                    makeSatellite(svid = 2, hasEphemeris = true),
                    makeSatellite(svid = 3, hasEphemeris = true),
                    makeSatellite(svid = 4, hasEphemeris = false),
                    makeSatellite(svid = 5, hasEphemeris = false),
                )

            val result = repo.verifyInjection(satellites)

            assertTrue(result.isSuccess)
            assertEquals(3, result.satellitesWithEphemeris)
        }

    @Test
    fun `verifyInjection succeeds when only almanac ratio reaches 50 percent`() =
        runTest {
            val repo = makeRepository()
            // 星历全无，历书 3/5
            val satellites =
                listOf(
                    makeSatellite(svid = 1, hasEphemeris = false, hasAlmanac = true),
                    makeSatellite(svid = 2, hasEphemeris = false, hasAlmanac = true),
                    makeSatellite(svid = 3, hasEphemeris = false, hasAlmanac = true),
                    makeSatellite(svid = 4, hasEphemeris = false, hasAlmanac = false),
                    makeSatellite(svid = 5, hasEphemeris = false, hasAlmanac = false),
                )

            val result = repo.verifyInjection(satellites)

            assertTrue(result.isSuccess)
            assertEquals(3, result.satellitesWithAlmanac)
        }

    @Test
    fun `verifyInjection classifies ephemeris status as VALID when ratio at or above 70 percent`() =
        runTest {
            val repo = makeRepository()
            val satellites = (1..10).map { makeSatellite(svid = it, hasEphemeris = true) }

            repo.verifyInjection(satellites)

            assertEquals(DataStatus.VALID, repo.status.first().ephemerisStatus)
        }

    @Test
    fun `verifyInjection classifies ephemeris status as PARTIAL when ratio between 30 and 70 percent`() =
        runTest {
            val repo = makeRepository()
            // 5/10 有星历（50%）→ PARTIAL
            val satellites =
                (1..5).map { makeSatellite(svid = it, hasEphemeris = true) } +
                    (6..10).map { makeSatellite(svid = it, hasEphemeris = false) }

            repo.verifyInjection(satellites)

            assertEquals(DataStatus.PARTIAL, repo.status.first().ephemerisStatus)
        }

    @Test
    fun `verifyInjection classifies ephemeris status as EXPIRED when ratio below 30 percent`() =
        runTest {
            val repo = makeRepository()
            // 1/10 有星历（10%）→ EXPIRED
            val satellites =
                listOf(makeSatellite(svid = 1, hasEphemeris = true)) +
                    (2..10).map { makeSatellite(svid = it, hasEphemeris = false) }

            repo.verifyInjection(satellites)

            assertEquals(DataStatus.EXPIRED, repo.status.first().ephemerisStatus)
        }

    // --- addRecord: 历史记录上限 50 ---

    @Test
    fun `injection history is capped at 50 records`() =
        runTest {
            val repo = makeRepository()
            coEvery { dataSource.injectTime(any()) } returns Result.success(Unit)

            // 连续注入 60 次，触发 60 条记录
            repeat(60) { repo.injectTime() }

            val history = repo.injectionHistory.first()
            assertTrue("历史应限制在 50 条以内，实际: ${history.size}", history.size <= 50)
            assertEquals(50, history.size)
        }

    @Test
    fun `injection history newest record is at head`() =
        runTest {
            val repo = makeRepository()
            coEvery { dataSource.injectTime(any()) } returns Result.success(Unit)

            repo.injectTime()

            val history = repo.injectionHistory.first()
            assertEquals(1, history.size)
            assertEquals(InjectionType.TIME, history[0].type)
        }

    // --- refreshStatus: 时间衰减 ---

    @Test
    fun `refreshStatus sets all statuses to UNKNOWN when never injected`() =
        runTest {
            val repo = makeRepository()

            repo.refreshStatus()

            val status = repo.status.first()
            assertEquals(DataStatus.UNKNOWN, status.timeStatus)
            assertEquals(DataStatus.UNKNOWN, status.ephemerisStatus)
            assertEquals(DataStatus.UNKNOWN, status.almanacStatus)
        }

    @Test
    fun `refreshStatus classifies ephemeris as VALID within 4 hours of injection`() =
        runTest {
            val repo = makeRepository()
            // 先触发一次成功注入，设置 lastInjectionTime
            coEvery { downloader.download(any()) } returns Result.success(makeValidData())
            coEvery { dataSource.injectXtraFromUrl(any()) } returns Result.success(Unit)
            repo.downloadAndInject()

            repo.refreshStatus()

            // 注入刚发生，星历应在 4h 有效期内
            assertEquals(DataStatus.VALID, repo.status.first().ephemerisStatus)
            assertEquals(DataStatus.VALID, repo.status.first().almanacStatus)
        }

    @Test
    fun `refreshStatus sets time status VALID within 24 hours of injection`() =
        runTest {
            val repo = makeRepository()
            coEvery { downloader.download(any()) } returns Result.success(makeValidData())
            coEvery { dataSource.injectXtraFromUrl(any()) } returns Result.success(Unit)
            repo.downloadAndInject()

            repo.refreshStatus()

            assertEquals(DataStatus.VALID, repo.status.first().timeStatus)
        }

    // --- clearApsData ---

    @Test
    fun `clearApsData resets all statuses to UNKNOWN on success`() =
        runTest {
            val repo = makeRepository()
            coEvery { dataSource.clearApsData() } returns Result.success(Unit)
            // 先注入建立 VALID 状态
            coEvery { downloader.download(any()) } returns Result.success(makeValidData())
            coEvery { dataSource.injectXtraFromUrl(any()) } returns Result.success(Unit)
            repo.downloadAndInject()

            val result = repo.clearApsData()

            assertTrue(result.isSuccess)
            val status = repo.status.first()
            assertEquals(DataStatus.UNKNOWN, status.timeStatus)
            assertEquals(DataStatus.UNKNOWN, status.ephemerisStatus)
            assertEquals(DataStatus.UNKNOWN, status.almanacStatus)
            assertNull(status.lastInjectionTime)
        }

    @Test
    fun `clearApsData leaves status unchanged on failure`() =
        runTest {
            val repo = makeRepository()
            coEvery { dataSource.clearApsData() } returns Result.failure(IOException("denied"))
            val before = repo.status.first()

            val result = repo.clearApsData()

            assertTrue(result.isFailure)
            val after = repo.status.first()
            assertEquals(before, after)
        }

    // --- injectTime ---

    @Test
    fun `injectTime records success and updates time status on success`() =
        runTest {
            val repo = makeRepository()
            coEvery { dataSource.injectTime(any()) } returns Result.success(Unit)

            val result = repo.injectTime()

            assertTrue(result.isSuccess)
            assertEquals(DataStatus.VALID, repo.status.first().timeStatus)
            assertNotNull(repo.status.first().lastUpdateTime)
            val history = repo.injectionHistory.first()
            assertEquals(1, history.size)
            assertTrue(history[0].success)
            assertEquals(InjectionType.TIME, history[0].type)
        }

    @Test
    fun `injectTime records failure and leaves time status unchanged on failure`() =
        runTest {
            val repo = makeRepository()
            val statusBefore = repo.status.first()
            coEvery { dataSource.injectTime(any()) } returns Result.failure(IOException("HAL busy"))

            val result = repo.injectTime()

            assertTrue(result.isFailure)
            assertEquals(statusBefore, repo.status.first())
            val history = repo.injectionHistory.first()
            assertEquals(1, history.size)
            assertFalse(history[0].success)
            assertNotNull(history[0].errorMessage)
        }

    // --- validateCurrentSource ---

    @Test
    fun `validateCurrentSource returns invalid with EMPTY_URL when download URL is blank`() =
        runTest {
            val repo = makeRepository(settings = AGpsSettings(downloadUrl = ""))

            val result = repo.validateCurrentSource()

            assertFalse(result.isValid)
            assertEquals("EMPTY_URL", result.errorType)
        }

    @Test
    fun `validateCurrentSource returns DOWNLOAD_ERROR when download fails`() =
        runTest {
            val repo = makeRepository()
            coEvery { downloader.download(any()) } returns Result.failure(IOException("404"))

            val result = repo.validateCurrentSource()

            assertFalse(result.isValid)
            assertEquals("DOWNLOAD_ERROR", result.errorType)
        }

    @Test
    fun `validateCurrentSource returns EMPTY_DATA when download returns empty bytes`() =
        runTest {
            val repo = makeRepository()
            coEvery { downloader.download(any()) } returns Result.success(ByteArray(0))

            val result = repo.validateCurrentSource()

            assertFalse(result.isValid)
            assertEquals("EMPTY_DATA", result.errorType)
        }

    @Test
    fun `validateCurrentSource returns valid when downloaded data passes validation`() =
        runTest {
            val repo = makeRepository()
            coEvery { downloader.download(any()) } returns Result.success(makeValidData())

            val result = repo.validateCurrentSource()

            assertTrue(result.isValid)
            assertEquals(makeValidData().size, result.fileSize)
        }
}
