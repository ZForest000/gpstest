package com.example.gpstest.domain.model

/**
 * 单条原始 NMEA 报文及其解析出的类型代码。
 *
 * 报文格式：`$<talkerID 2 字节><类型 3 字节>,<字段1>,<字段2>,...*<校验和>`。
 * 类型代码取自报文前缀：跳过 `$` 和 2 字节 talker ID 后的 3 个字符。
 * 例如 `$GPGGA,...` → `GGA`；无法识别时返回 [TYPE_UNKNOWN]。
 */
data class NmeaSentence(
    val timestampMs: Long,
    val message: String,
) {
    /** 解析后的 NMEA 报文类型，如 `GGA` / `RMC` / `GSA`；无法识别时为 [TYPE_UNKNOWN]。 */
    val type: String
        get() = parseType(message)

    companion object {
        /** 无法识别报文类型时的占位值。 */
        const val TYPE_UNKNOWN = "UNK"

        /** 首个非 `$` 字符的最小位置。 */
        private const val TALKER_START = 1

        /** talker ID 长度（例如 `GP` / `GL` / `GB`）。 */
        private const val TALKER_LEN = 2

        /** 标准报文类型长度（例如 `GGA`）。 */
        private const val TYPE_LEN = 3

        /**
         * 从原始报文中提取类型代码。
         * 规则：以 `$` 开头且长度足够时，跳过 `$` 和 2 字节 talker ID，取其后 3 个字符；
         * 否则返回 [TYPE_UNKNOWN]。
         */
        fun parseType(message: String): String {
            if (message.length < TALKER_START + TALKER_LEN + TYPE_LEN) return TYPE_UNKNOWN
            if (message[0] != '$') return TYPE_UNKNOWN
            return message.substring(
                TALKER_START + TALKER_LEN,
                TALKER_START + TALKER_LEN + TYPE_LEN,
            )
        }
    }
}
