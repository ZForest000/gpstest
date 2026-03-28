package com.example.gpstest.data.source

import android.util.Log
import com.example.gpstest.data.validator.ValidationErrorType
import com.example.gpstest.data.validator.XtraDataValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.util.concurrent.TimeUnit

interface AGpsDownloader {
    suspend fun download(url: String): Result<ByteArray>
    fun getDefaultUrls(): List<String>
}

class AGpsDownloaderImpl(
    private val validator: XtraDataValidator = XtraDataValidator()
) : AGpsDownloader {
    
    companion object {
        private const val TAG = "AGpsDownloader"
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private data class UrlValidationResult(
        val isValid: Boolean,
        val error: String = ""
    )
    
    private fun validateUrl(url: String): UrlValidationResult {
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
                Log.w(TAG, "validateUrl: 警告: URL路径不以.bin结尾: ${urlObj.encodedPath}")
            }
            
            return UrlValidationResult(true)
        } catch (e: Exception) {
            return UrlValidationResult(false, "URL格式无效: ${e.message}")
        }
    }

    override suspend fun download(url: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        Log.d(TAG, "download: Starting download from $url")
        
        val urlValidation = validateUrl(url)
        if (!urlValidation.isValid) {
            val error = "URL验证失败: ${urlValidation.error}"
            Log.e(TAG, "download: $error")
            return@withContext Result.failure(IOException(error))
        }
        
        try {
            val request = Request.Builder()
                .url(url)
                .build()
            
            client.newCall(request).execute().use { response ->
                Log.d(TAG, "download: Response code = ${response.code}, message = ${response.message}")
                
                if (!response.isSuccessful) {
                    val error = "HTTP ${response.code}: ${response.message}"
                    Log.e(TAG, "download: $error")
                    return@withContext Result.failure(IOException(error))
                }
                
                val body = response.body ?: run {
                    Log.e(TAG, "download: Empty response body")
                    return@withContext Result.failure(IOException("Empty response body"))
                }
                
                val data = body.bytes()
                if (data.isEmpty()) {
                    Log.e(TAG, "download: Downloaded empty data")
                    return@withContext Result.failure(IOException("Downloaded empty data"))
                }
                
                val mimeType = response.header("content-type")
                Log.d(TAG, "download: Downloaded ${data.size} bytes, MIME type: $mimeType")
                
                val validationResult = validator.validate(data, mimeType, url)
                if (!validationResult.isValid) {
                    val error = "数据验证失败: ${validationResult.details} (错误类型: ${validationResult.errorType})"
                    Log.e(TAG, "download: $error")
                    Log.e(TAG, "download: 数据统计: ${validator.getSizeStatistics(data)}")
                    return@withContext Result.failure(IOException(error))
                }
                
                Log.i(TAG, "download: 数据验证通过 | ${validator.getSizeStatistics(data)}")
                Result.success(data)
            }
        } catch (e: Exception) {
            Log.e(TAG, "download: Exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    override fun getDefaultUrls(): List<String> {
        return listOf(
            "https://xtrapath1.izatcloud.net/xtra3grc.bin",
            "https://xtrapath2.izatcloud.net/xtra3grc.bin",
            "https://xtrapath3.izatcloud.net/xtra3grc.bin"
        )
    }
}
