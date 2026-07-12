package com.example.gpstest.data.source

import com.example.gpstest.data.validator.XtraDataValidator
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * AGpsDownloaderImpl.download() 的 HTTP 流程测试，用 MockWebServer 模拟服务器响应。
 * 覆盖 2xx 成功、4xx 失败、空响应体、校验失败等路径。
 *
 * 注意：download() 内部 withContext(Dispatchers.IO)，在 runTest 下会真实执行（IO 池可用）。
 */
class AGpsDownloaderDownloadTest {
    private lateinit var server: MockWebServer

    // strictMode=false：跳过 MIME 校验，让有效二进制数据通过
    private val validator =
        XtraDataValidator(
            minSizeBytes = 1024,
            maxSizeBytes = 2 * 1024 * 1024,
            strictMode = false,
        )

    private lateinit var downloader: AGpsDownloaderImpl

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        downloader = AGpsDownloaderImpl(validator)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /** 有效二进制数据：循环字节，满足 minSizeBytes 且通过格式校验。 */
    private fun validBody(size: Int = 2048): ByteArray = ByteArray(size) { (it % 256).toByte() }

    @Test
    fun `download returns success for 200 with valid binary body`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(okio.Buffer().write(validBody())),
            )

            val result = downloader.download(server.url("/xtra.bin").toString())

            assertTrue(result.isSuccess)
        }

    @Test
    fun `download returns failure with HTTP code for 404`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(404).setBody("Not Found"))

            val result = downloader.download(server.url("/xtra.bin").toString())

            assertTrue(result.isFailure)
            val msg = result.exceptionOrNull()?.message.orEmpty()
            assertTrue("应含 HTTP 404，实际: $msg", msg.contains("HTTP 404"))
        }

    @Test
    fun `download returns failure for 500 server error`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(500))

            val result = downloader.download(server.url("/xtra.bin").toString())

            assertTrue(result.isFailure)
            val msg = result.exceptionOrNull()?.message.orEmpty()
            assertTrue("应含 HTTP 500，实际: $msg", msg.contains("HTTP 500"))
        }

    @Test
    fun `download returns failure when body is empty`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody(""))

            val result = downloader.download(server.url("/xtra.bin").toString())

            assertTrue(result.isFailure)
        }

    @Test
    fun `download returns failure when body smaller than min size`() =
        runTest {
            // 仅 10 字节，小于 validator minSizeBytes=1024
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(okio.Buffer().write(ByteArray(10))),
            )

            val result = downloader.download(server.url("/xtra.bin").toString())

            assertTrue(result.isFailure)
        }

    @Test
    fun `download returns failure when body is HTML error page`() =
        runTest {
            // HTML 错误页 + 填充至 >2048 触发 ERROR_PAGE_DETECTED
            val html = "<html><body>error</body></html>".toByteArray() + ByteArray(3000)
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(okio.Buffer().write(html)),
            )

            val result = downloader.download(server.url("/xtra.bin").toString())

            assertTrue(result.isFailure)
        }

    @Test
    fun `download returns failure when URL is invalid`() =
        runTest {
            val result = downloader.download("")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IOException)
        }
}
