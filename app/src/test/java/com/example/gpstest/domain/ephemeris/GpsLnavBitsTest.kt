package com.example.gpstest.domain.ephemeris

import org.junit.Assert.assertEquals
import org.junit.Test

class GpsLnavBitsTest {
    @Test
    fun `reads the lower 30 big endian bits from each Android navigation word`() {
        val bytes = ByteArray(40)
        // Android packs a LNAV word in the low 30 bits of a big-endian 32-bit word.
        bytes[0] = 0x3F
        bytes[1] = 0xFF.toByte()
        bytes[2] = 0xFF.toByte()
        bytes[3] = 0xFF.toByte()

        val bits = GpsLnavBits(bytes)

        assertEquals((1 shl 30) - 1, bits.unsigned(1, 30))
        assertEquals(-1, bits.signed(1, 30))
    }
}
