package com.example.gpstest.data.validator

import android.util.Log
import com.example.gpstest.BuildConfig
import java.io.ByteArrayInputStream
import java.security.MessageDigest

data class ValidationResult(
    val isValid: Boolean,
    val errorType: ValidationErrorType? = null,
    val details: String = ""
)

enum class ValidationErrorType {
    EMPTY_DATA,
    TOO_SMALL,
    TOO_LARGE,
    INVALID_FORMAT,
    ERROR_PAGE_DETECTED,
    INVALID_MIME_TYPE,
    VALIDATION_FAILED
}

class XtraDataValidator(
    private val minSizeBytes: Int = 1024,
    private val maxSizeBytes: Int = 2 * 1024 * 1024,
    private val allowCompression: Boolean = true,
    private val strictMode: Boolean = BuildConfig.DEBUG
) {
    
    companion object {
        private const val TAG = "XtraDataValidator"
        private val VALID_MIME_TYPES = setOf(
            "application/octet-stream",
            "application/x-gps-data",
            "application/vnd.qualcomm.xtra",
            "application/gzip",
            "application/x-gzip"
        )
        
        private val HTML_SIGNATURES = listOf(
            "<html".toByteArray(),
            "<HTML".toByteArray(),
            "<!DOCTYPE".toByteArray()
        )
        
        private val JSON_SIGNATURES = listOf(
            "{\"error\"".toByteArray(),
            "{\"message\"".toByteArray(),
            "{\"code\"".toByteArray()
        )
    }
    
    fun validate(
        data: ByteArray,
        mimeType: String? = null,
        sourceUrl: String? = null
    ): ValidationResult {
        val dataSize = data.size
        
        if (data.isEmpty()) {
            return ValidationResult(
                isValid = false,
                errorType = ValidationErrorType.EMPTY_DATA,
                details = "下载数据为空"
            )
        }
        
        if (dataSize < minSizeBytes) {
            return ValidationResult(
                isValid = false,
                errorType = ValidationErrorType.TOO_SMALL,
                details = "数据过小: ${dataSize}字节 < ${minSizeBytes}字节"
            )
        }
        
        if (dataSize > maxSizeBytes) {
            return ValidationResult(
                isValid = false,
                errorType = ValidationErrorType.TOO_LARGE,
                details = "数据过大: ${dataSize}字节 > ${maxSizeBytes}字节"
            )
        }
        
        val formatValidation = detectInvalidFormat(data)
        if (formatValidation != null) {
            return formatValidation
        }
        
        if (mimeType != null && strictMode) {
            val mimeValidation = validateMimeType(mimeType)
            if (!mimeValidation.isValid) {
                return mimeValidation
            }
        }
        
        val hash = calculateHash(data)
        Log.i(TAG, String.format("数据验证通过 | 来源: %s | 大小: %d字节 | SHA-256: %s",
            sourceUrl ?: "unknown", dataSize, hash))
        
        return ValidationResult(isValid = true)
    }
    
    private fun detectInvalidFormat(data: ByteArray): ValidationResult? {
        val header = data.take(100).toByteArray()
        
        for (signature in HTML_SIGNATURES) {
            if (header.startsWith(signature)) {
                return ValidationResult(
                    isValid = false,
                    errorType = ValidationErrorType.ERROR_PAGE_DETECTED,
                    details = "检测到HTML错误页面"
                )
            }
        }
        
        val headerString = String(header, Charsets.UTF_8)
        for (signature in JSON_SIGNATURES) {
            if (headerString.startsWith(String(signature, Charsets.UTF_8))) {
                return ValidationResult(
                    isValid = false,
                    errorType = ValidationErrorType.ERROR_PAGE_DETECTED,
                    details = "检测到JSON错误响应"
                )
            }
        }
        
        val printableRatio = data.count { it >= 32 && it <= 126 }.toFloat() / data.size
        if (printableRatio > 0.95 && data.size > 2048) {
            return ValidationResult(
                isValid = false,
                errorType = ValidationErrorType.INVALID_FORMAT,
                details = "疑似文本内容(可打印字符比例: ${(printableRatio * 100).toInt()}%)"
            )
        }
        
        return null
    }
    
    private fun validateMimeType(mimeType: String): ValidationResult {
        val normalizedMime = mimeType.lowercase().trim()
        
        if (normalizedMime in VALID_MIME_TYPES) {
            return ValidationResult(isValid = true)
        }
        
        if (allowCompression && (normalizedMime == "application/gzip" || 
                                 normalizedMime == "application/x-gzip")) {
            return ValidationResult(isValid = true)
        }
        
        if (normalizedMime.startsWith("text/") || 
            normalizedMime.startsWith("application/json") ||
            normalizedMime.startsWith("application/html")) {
            return ValidationResult(
                isValid = false,
                errorType = ValidationErrorType.INVALID_MIME_TYPE,
                details = "可疑的MIME类型: $mimeType"
            )
        }
        
        Log.w(TAG, "未知的MIME类型: $mimeType (非严格模式下允许)")
        return ValidationResult(isValid = true)
    }
    
    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (this.size < prefix.size) return false
        for (i in prefix.indices) {
            if (this[i] != prefix[i]) return false
        }
        return true
    }
    
    private fun calculateHash(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    fun getSizeStatistics(data: ByteArray): String {
        val sizeKB = data.size / 1024.0
        val printableRatio = data.count { it >= 32 && it <= 126 }.toFloat() / data.size
        
        return buildString {
            append("大小: %.2f KB".format(sizeKB))
            append(" | 可打印字符: %.1f%%".format(printableRatio * 100))
            append(" | 首字节: 0x%02X".format(data[0].toInt() and 0xFF))
            
            if (data.size >= 4) {
                val magic = data.take(4).joinToString(" ") { 
                    "%02X".format(it.toInt() and 0xFF) 
                }
                append(" | Magic: $magic")
            }
        }
    }
}
