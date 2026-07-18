package com.example.gpstest.domain.ephemeris

/** Android 40-byte GPS L1 C/A 数据的逻辑 300-bit 视图。 */
class GpsLnavBits(
    data: ByteArray,
) {
    private val words: IntArray

    init {
        require(data.size == DATA_SIZE_BYTES) { "GPS LNAV subframe must contain 40 bytes" }
        words =
            IntArray(WORD_COUNT) { index ->
                val offset = index * BYTES_PER_WORD
                (
                    (data[offset].toInt() and 0xFF) shl 24 or
                        ((data[offset + 1].toInt() and 0xFF) shl 16) or
                        ((data[offset + 2].toInt() and 0xFF) shl 8) or
                        (data[offset + 3].toInt() and 0xFF)
                ) and WORD_MASK
            }
    }

    /** 从 1 开始的逻辑位编号，MSB first。 */
    fun unsigned(
        startBit: Int,
        length: Int,
    ): Int {
        require(startBit >= 1 && length in 1..30 && startBit + length - 1 <= TOTAL_BITS)
        var result = 0
        repeat(length) { index ->
            val bitIndex = startBit + index - 1
            val wordIndex = bitIndex / BITS_PER_WORD
            val bitInWord = bitIndex % BITS_PER_WORD
            result = (result shl 1) or ((words[wordIndex] ushr (BITS_PER_WORD - 1 - bitInWord)) and 1)
        }
        return result
    }

    fun signed(
        startBit: Int,
        length: Int,
    ): Int {
        val value = unsigned(startBit, length)
        val sign = 1 shl (length - 1)
        return if (value and sign == 0) value else value - (1 shl length)
    }

    private companion object {
        const val WORD_COUNT = 10
        const val BITS_PER_WORD = 30
        const val BYTES_PER_WORD = 4
        const val DATA_SIZE_BYTES = WORD_COUNT * BYTES_PER_WORD
        const val TOTAL_BITS = WORD_COUNT * BITS_PER_WORD
        const val WORD_MASK = 0x3FFFFFFF
    }
}
