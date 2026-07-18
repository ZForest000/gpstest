package com.example.gpstest.data.source

import com.example.gpstest.data.validator.XtraDataValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import timber.log.Timber

interface AGpsDownloader {
    suspend fun download(url: String): Result<ByteArray>

    fun getDefaultUrls(): List<String>
}

class AGpsDownloaderImpl(
    private val validator: XtraDataValidator = XtraDataValidator(),
) : AGpsDownloader {
    companion object {
        private const val TAG = "AGpsDownloader"
    }

    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

    internal data class UrlValidationResult(
        val isValid: Boolean,
        val error: String = "",
    )

    // internal 以便单测直接覆盖 URL 校验逻辑（含中文错误信息），避免必须走 MockWebServer
    internal fun validateUrl(url: String): UrlValidationResult {
        if (url.isBlank()) {
            return UrlValidationResult(false, "URL为空")
        }

        try {
            val urlObj = url.toHttpUrl()

            if (urlObj.scheme != "https" && urlObj.scheme != "http") {
                return UrlValidationResult(false, "不支持的协议: ${urlObj.scheme}")
            }

            if (urlObj.host.isNullOrBlank()) {
                return UrlValidationResult(false, "缺少主机名")
            }

            if (!urlObj.encodedPath.endsWith(".bin", ignoreCase = true)) {
                Timber.tag(TAG).w("validateUrl: 警告: URL路径不以.bin结尾: ${urlObj.encodedPath}")
            }

            return UrlValidationResult(true)
        } catch (e: Exception) {
            return UrlValidationResult(false, "URL格式无效: ${e.message}")
        }
    }

    override suspend fun download(url: String): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            Timber.tag(TAG).d("download: Starting download from $url")

            val urlValidation = validateUrl(url)
            if (!urlValidation.isValid) {
                val error = "URL验证失败: ${urlValidation.error}"
                Timber.tag(TAG).e("download: $error")
                return@withContext Result.failure(IOException(error))
            }

            try {
                val request =
                    Request
                        .Builder()
                        .url(url)
                        .build()

                client.newCall(request).execute().use { response ->
                    Timber.tag(TAG).d("download: Response code = ${response.code}, message = ${response.message}")

                    if (!response.isSuccessful) {
                        val error = "HTTP ${response.code}: ${response.message}"
                        Timber.tag(TAG).e("download: $error")
                        return@withContext Result.failure(IOException(error))
                    }

                    val body =
                        response.body ?: run {
                            Timber.tag(TAG).e("download: Empty response body")
                            return@withContext Result.failure(IOException("Empty response body"))
                        }

                    val data = body.bytes()
                    if (data.isEmpty()) {
                        Timber.tag(TAG).e("download: Downloaded empty data")
                        return@withContext Result.failure(IOException("Downloaded empty data"))
                    }

                    val mimeType = response.header("content-type")
                    Timber.tag(TAG).d("download: Downloaded ${data.size} bytes, MIME type: $mimeType")

                    val validationResult = validator.validate(data, mimeType, url)
                    if (!validationResult.isValid) {
                        val error = "数据验证失败: ${validationResult.details} (错误类型: ${validationResult.errorType})"
                        Timber.tag(TAG).e("download: $error")
                        Timber.tag(TAG).e("download: 数据统计: ${validator.getSizeStatistics(data)}")
                        return@withContext Result.failure(IOException(error))
                    }

                    Timber.tag(TAG).i("download: 数据验证通过 | ${validator.getSizeStatistics(data)}")
                    Result.success(data)
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "download: Exception: ${e.message}")
                Result.failure(e)
            }
        }

    override fun getDefaultUrls(): List<String> =
        listOf(
            "https://xtrapath1.izatcloud.net/xtra3grc.bin",
            "https://xtrapath2.izatcloud.net/xtra3grc.bin",
            "https://xtrapath3.izatcloud.net/xtra3grc.bin",
        )
}
