package com.example.gpstest.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NmeaSentenceTest {
    @Test
    fun `type returns GGA for standard GPS sentence`() {
        val sentence = NmeaSentence(0L, "\$GPGGA,092750.000,5321.6802,N,00606.7072,W,1,8,1.03,61.7,M,55.2,M,,")
        assertEquals("GGA", sentence.type)
    }

    @Test
    fun `type returns RMC for GLONASS sentence`() {
        val sentence = NmeaSentence(0L, "\$GLRMC,092750.0,A,5321.68,N,00606.71,W,0.1,0.0,010120,,,N")
        assertEquals("RMC", sentence.type)
    }

    @Test
    fun `type returns GSA for Galileo sentence`() {
        val sentence = NmeaSentence(0L, "\$GAGSA,A,3,01,02,03,,,,,,,,6.0,1.0,5.0")
        assertEquals("GSA", sentence.type)
    }

    @Test
    fun `type returns GSV for BeiDou sentence`() {
        val sentence = NmeaSentence(0L, "\$GBGSV,1,1,01,01,,,30*4B")
        assertEquals("GSV", sentence.type)
    }

    @Test
    fun `type is UNK when message does not start with dollar`() {
        val sentence = NmeaSentence(0L, "GPGGA,092750.000")
        assertEquals(NmeaSentence.TYPE_UNKNOWN, sentence.type)
    }

    @Test
    fun `type is UNK when message is too short`() {
        val sentence = NmeaSentence(0L, "\$GP")
        assertEquals(NmeaSentence.TYPE_UNKNOWN, sentence.type)
    }

    @Test
    fun `type is UNK when message has only talker and type missing`() {
        val sentence = NmeaSentence(0L, "\$GP")
        assertEquals(NmeaSentence.TYPE_UNKNOWN, sentence.type)
    }

    @Test
    fun `parseType static helper matches property`() {
        val message = "\$GPGGA,092750.000"
        assertEquals(NmeaSentence.parseType(message), NmeaSentence(0L, message).type)
    }

    @Test
    fun `parseType returns UNK for empty string`() {
        assertEquals(NmeaSentence.TYPE_UNKNOWN, NmeaSentence.parseType(""))
    }
}
