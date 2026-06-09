package com.example.gpstest.data.validator

import android.util.Log
import com.example.gpstest.BuildConfig
import java.security.MessageDigest

data class ValidationResult(
    val isValid: Boolean,
    val errorType: ValidationErrorType? = null,
    val details: String = "",
)

/** 验证失败类型：EMPTY_DATA/TOO_SMALL/TOO_LARGE（大小异常）、ERROR_PAGE_DETECTED（HTML/JSON 错误响应）、INVALID_FORMAT（非二进制内容）、INVALID_MIME_TYPE。 */
enum class ValidationErrorType {
    EMPTY_DATA,
    TOO_SMALL,
    TOO_LARGE,
    INVALID_FORMAT,
    ERROR_PAGE_DETECTED,
    INVALID_MIME_TYPE,
    VALIDATION_FAILED,
}

/**
 * XTRA 数据文件验证器。
 *
 * 下载的 XTRA 数据在传递给 GPS HAL 前进行防御性检查，检测常见故障模式：
 * - HTML 错误页面：CDN URL 过期后返回 404 页面而非 XTRA 二进制数据
 * - JSON 错误响应：API 网关返回的错误信息
 * - 非二进制内容：下载了错误的文件格式
 *
 * Android GPS HAL 对无效数据会静默忽略，因此注入前验证是必要的防线。
 *
 * @property minSizeBytes 最小有效大小，默认 1KB
 * @property maxSizeBytes 最大有效大小，默认 2MB
 * @property strictMode debug 模式下启用 MIME 类型检查
 */
class XtraDataValidator(
    private val minSizeBytes: Int = 1024,
    private val maxSizeBytes: Int = 2 * 1024 * 1024,
    private val strictMode: Boolean = BuildConfig.DEBUG,
) {
    companion object {
        private const val TAG = "XtraDataValidator"
        private val VALID_MIME_TYPES =
            setOf(
                "application/octet-stream",
                "application/x-gps-data",
                "application/vnd.qualcomm.xtra",
                "application/gzip",
                "application/x-gzip",
            )

        private val HTML_SIGNATURES =
            listOf(
                "<html".toByteArray(),
                "<HTML".toByteArray(),
                "<!DOCTYPE".toByteArray(),
            )

        private val JSON_SIGNATURES =
            listOf(
                "{\"error\"".toByteArray(),
                "{\"message\"".toByteArray(),
                "{\"code\"".toByteArray(),
            )
    }

    private fun logInfo(message: String) {
        try {
            Log.i(TAG, message)
        } catch (_: Exception) {
        }
    }

    private fun logWarn(message: String) {
        try {
            Log.w(TAG, message)
        } catch (_: Exception) {
        }
    }

    fun validate(
        data: ByteArray,
        mimeType: String? = null,
        sourceUrl: String? = null,
    ): ValidationResult {
        val dataSize = data.size

        if (data.isEmpty()) {
            return ValidationResult(
                isValid = false,
                errorType = ValidationErrorType.EMPTY_DATA,
                details = "下载数据为空",
            )
        }

        if (dataSize < minSizeBytes) {
            return ValidationResult(
                isValid = false,
                errorType = ValidationErrorType.TOO_SMALL,
                details = "数据过小: ${dataSize}字节 < ${minSizeBytes}字节",
            )
        }

        if (dataSize > maxSizeBytes) {
            return ValidationResult(
                isValid = false,
                errorType = ValidationErrorType.TOO_LARGE,
                details = "数据过大: ${dataSize}字节 > ${maxSizeBytes}字节",
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
        logInfo(
            String.format(
                "数据验证通过 | 来源: %s | 大小: %d字节 | SHA-256: %s",
                sourceUrl ?: "unknown",
                dataSize,
                hash,
            ),
        )

        return ValidationResult(isValid = true)
    }

    // 三种检测策略：
    // 1. HTML 签名检查 — CDN 过期 URL 返回 HTML 错误页而非 404
    // 2. JSON 签名检查 — API 网关返回 {"error":"..."}
    // 3. 可打印字符比例 — 真实 XTRA 二进制 ~30-50%；>95% 且 >2KB 疑为文本
    private fun detectInvalidFormat(data: ByteArray): ValidationResult? {
        val header = data.take(100).toByteArray()

        for (signature in HTML_SIGNATURES) {
            if (header.startsWith(signature)) {
                return ValidationResult(
                    isValid = false,
                    errorType = ValidationErrorType.ERROR_PAGE_DETECTED,
                    details = "检测到HTML错误页面",
                )
            }
        }

        val headerString = String(header, Charsets.UTF_8)
        for (signature in JSON_SIGNATURES) {
            if (headerString.startsWith(String(signature, Charsets.UTF_8))) {
                return ValidationResult(
                    isValid = false,
                    errorType = ValidationErrorType.ERROR_PAGE_DETECTED,
                    details = "检测到JSON错误响应",
                )
            }
        }

        val printableRatio = data.count { it >= 32 && it <= 126 }.toFloat() / data.size
        if (printableRatio > 0.95 && data.size > 2048) {
            return ValidationResult(
                isValid = false,
                errorType = ValidationErrorType.INVALID_FORMAT,
                details = "疑似文本内容(可打印字符比例: ${(printableRatio * 100).toInt()}%)",
            )
        }

        return null
    }

    // debug 模式下拒绝 text/*、application/json 等可疑 MIME 类型
    // release 模式下未知 MIME 仅记录日志不拦截（生产服务器可能使用非标准头）
    private fun validateMimeType(mimeType: String): ValidationResult {
        val normalizedMime = mimeType.lowercase().trim()

        if (normalizedMime in VALID_MIME_TYPES) {
            return ValidationResult(isValid = true)
        }

        if (normalizedMime.startsWith("text/") ||
            normalizedMime.startsWith("application/json") ||
            normalizedMime.startsWith("application/html")
        ) {
            return ValidationResult(
                isValid = false,
                errorType = ValidationErrorType.INVALID_MIME_TYPE,
                details = "可疑的MIME类型: $mimeType",
            )
        }

        logWarn("未知的MIME类型: $mimeType (非严格模式下允许)")
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
                val magic =
                    data.take(4).joinToString(" ") {
                        "%02X".format(it.toInt() and 0xFF)
                    }
                append(" | Magic: $magic")
            }
        }
    }
}
