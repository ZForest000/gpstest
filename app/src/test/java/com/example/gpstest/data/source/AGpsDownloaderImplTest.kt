package com.example.gpstest.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AGpsDownloaderImpl 纯逻辑测试：URL 校验 + 默认地址列表。
 * validateUrl 现为 internal 以便直接单测（避免必须走 MockWebServer）。
 * download() 的 HTTP 流程见 AGpsDownloaderDownloadTest（MockWebServer）。
 */
class AGpsDownloaderImplTest {
    private val downloader = AGpsDownloaderImpl()

    // --- validateUrl: 边界与中文错误信息 ---

    @Test
    fun `validateUrl rejects blank url with URL为空`() {
        val result = downloader.validateUrl("")
        assertFalse(result.isValid)
        assertEquals("URL为空", result.error)
    }

    @Test
    fun `validateUrl rejects whitespace-only url with URL为空`() {
        val result = downloader.validateUrl("   \t  ")
        assertFalse(result.isValid)
        assertEquals("URL为空", result.error)
    }

    @Test
    fun `validateUrl rejects unsupported scheme`() {
        // OkHttp toHttpUrl() 仅接受 http/https，ftp 会抛异常进入 URL格式无效 分支
        val result = downloader.validateUrl("ftp://example.com/xtra.bin")
        assertFalse(result.isValid)
        assertTrue("实际: ${result.error}", result.error.contains("URL格式无效"))
    }

    @Test
    fun `validateUrl rejects url without host`() {
        // 缺少 host 的输入（如 "http://"）OkHttp 解析失败，进入 URL格式无效 分支
        val result = downloader.validateUrl("http://")
        assertFalse(result.isValid)
        assertTrue("实际: ${result.error}", result.error.contains("URL格式无效"))
    }

    @Test
    fun `validateUrl accepts well-formed https url ending in bin`() {
        val result = downloader.validateUrl("https://xtrapath1.izatcloud.net/xtra3grc.bin")
        assertTrue(result.isValid)
        assertEquals("", result.error)
    }

    @Test
    fun `validateUrl accepts well-formed http url ending in bin`() {
        val result = downloader.validateUrl("http://example.com/xtra.bin")
        assertTrue(result.isValid)
    }

    @Test
    fun `validateUrl accepts bin path case insensitive`() {
        val result = downloader.validateUrl("https://example.com/XTRA.BIN")
        assertTrue(result.isValid)
    }

    @Test
    fun `validateUrl accepts url even when path does not end in bin`() {
        // 非 .bin 结尾仅警告（Log.w），不判失败
        val result = downloader.validateUrl("https://example.com/data")
        assertTrue(result.isValid)
    }

    @Test
    fun `validateUrl returns URL格式无效 for malformed input`() {
        val result = downloader.validateUrl("not a url at all")
        assertFalse(result.isValid)
        assertTrue("实际: ${result.error}", result.error.contains("URL格式无效"))
    }

    @Test
    fun `validateUrl rejects scheme-only url as malformed`() {
        val result = downloader.validateUrl("https://")
        assertFalse(result.isValid)
    }

    // --- getDefaultUrls ---

    @Test
    fun `getDefaultUrls returns exactly three urls`() {
        val urls = downloader.getDefaultUrls()
        assertEquals(3, urls.size)
    }

    @Test
    fun `getDefaultUrls all use https scheme`() {
        val urls = downloader.getDefaultUrls()
        urls.forEach { url ->
            assertTrue("应使用 https: $url", url.startsWith("https://"))
        }
    }

    @Test
    fun `getDefaultUrls all end with bin`() {
        val urls = downloader.getDefaultUrls()
        urls.forEach { url ->
            assertTrue("应以 .bin 结尾: $url", url.endsWith(".bin"))
        }
    }

    @Test
    fun `getDefaultUrls all point to izatcloud net`() {
        val urls = downloader.getDefaultUrls()
        urls.forEach { url ->
            assertTrue("应指向 izatcloud.net: $url", url.contains("izatcloud.net"))
        }
    }
}
