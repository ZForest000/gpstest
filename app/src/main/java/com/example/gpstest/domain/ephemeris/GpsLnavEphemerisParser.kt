package com.example.gpstest.domain.ephemeris

import kotlin.math.PI

/** GPS L1 C/A LNAV 子帧 1–3 解码器。所有 bit 位置遵循 IS-GPS-200 表 20-III。 */
object GpsLnavEphemerisParser {
    fun parse(
        svid: Int,
        subframe1: ByteArray,
        subframe2: ByteArray,
        subframe3: ByteArray,
    ): GpsBroadcastEphemeris? {
        val sf1 = GpsLnavBits(subframe1)
        val sf2 = GpsLnavBits(subframe2)
        val sf3 = GpsLnavBits(subframe3)
        val iodc = (sf1.unsigned(83, 2) shl 8) or sf1.unsigned(211, 8)
        val iode2 = sf2.unsigned(61, 8)
        val iode3 = sf3.unsigned(271, 8)
        if ((iodc and 0xFF) != iode2 || iode2 != iode3) return null

        return GpsBroadcastEphemeris(
            svid = svid,
            weekNumber = sf1.unsigned(61, 10),
            toeSeconds = sf2.unsigned(271, 16) * 16.0,
            tocSeconds = sf1.unsigned(219, 16) * 16.0,
            sqrtA = unsignedComposite(sf2, 227, 8, 241, 24) * twoPower(-19),
            eccentricity = unsignedComposite(sf2, 167, 8, 181, 24) * twoPower(-33),
            inclinationRadians = signedComposite(sf3, 137, 8, 151, 24) * twoPower(-31) * PI,
            longitudeOfAscendingNodeRadians = signedComposite(sf3, 77, 8, 91, 24) * twoPower(-31) * PI,
            argumentOfPerigeeRadians = signedComposite(sf3, 197, 8, 211, 24) * twoPower(-31) * PI,
            meanAnomalyRadians = signedComposite(sf2, 107, 8, 121, 24) * twoPower(-31) * PI,
            deltaN = sf2.signed(91, 16) * twoPower(-43) * PI,
            inclinationRate = sf3.signed(279, 14) * twoPower(-43) * PI,
            longitudeRate = sf3.signed(241, 24) * twoPower(-43) * PI,
            cuc = sf2.signed(151, 16) * twoPower(-29),
            cus = sf2.signed(211, 16) * twoPower(-29),
            cic = sf3.signed(61, 16) * twoPower(-29),
            cis = sf3.signed(121, 16) * twoPower(-29),
            crc = sf3.signed(181, 16) * twoPower(-5),
            crs = sf2.signed(69, 16) * twoPower(-5),
            af0Seconds = sf1.signed(271, 22) * twoPower(-31),
            af1SecondsPerSecond = sf1.signed(249, 16) * twoPower(-43),
            af2SecondsPerSecondSquared = sf1.signed(241, 8) * twoPower(-55),
            groupDelaySeconds = sf1.signed(197, 8) * twoPower(-31),
        )
    }

    private fun unsignedComposite(
        bits: GpsLnavBits,
        firstStart: Int,
        firstLength: Int,
        secondStart: Int,
        secondLength: Int,
    ): Double = ((bits.unsigned(firstStart, firstLength).toLong() shl secondLength) or bits.unsigned(secondStart, secondLength).toLong()).toDouble()

    private fun signedComposite(
        bits: GpsLnavBits,
        firstStart: Int,
        firstLength: Int,
        secondStart: Int,
        secondLength: Int,
    ): Double {
        val length = firstLength + secondLength
        val value = (bits.unsigned(firstStart, firstLength).toLong() shl secondLength) or bits.unsigned(secondStart, secondLength).toLong()
        val sign = 1L shl (length - 1)
        return if (value and sign == 0L) value.toDouble() else (value - (1L shl length)).toDouble()
    }

    private fun twoPower(exponent: Int): Double = Math.scalb(1.0, exponent)
}
