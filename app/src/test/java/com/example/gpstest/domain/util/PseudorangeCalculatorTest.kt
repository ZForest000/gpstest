package com.example.gpstest.domain.util

import com.example.gpstest.domain.model.Constellation
import com.example.gpstest.domain.model.GnssClockData
import com.example.gpstest.domain.model.PseudorangeMeasurement
import com.example.gpstest.domain.model.PseudorangeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.sqrt

class PseudorangeCalculatorTest {
    private val clock =
        GnssClockData(
            timeNanos = 1_000_000_000_000_000L,
            biasNanos = 50.0,
            fullBiasNanos = -1_414_400_000_000_000L,
            driftNanosPerSecond = null,
            biasUncertaintyNanos = 3.0,
            driftUncertaintyNanosPerSecond = null,
            hardwareClockDiscontinuityCount = 0,
        )

    private fun measurement(
        constellation: Constellation = Constellation.GPS,
        receivedSvTimeNanos: Long? = 599_999_920_000_000L,
        timeOffsetNanos: Double = 0.0,
        receivedSvTimeUncertaintyNanos: Double? = 4.0,
        hasCodeLock: Boolean = true,
        hasTowDecoded: Boolean = true,
    ) = PseudorangeMeasurement(
        constellation = constellation,
        timeOffsetNanos = timeOffsetNanos,
        receivedSvTimeNanos = receivedSvTimeNanos,
        receivedSvTimeUncertaintyNanos = receivedSvTimeUncertaintyNanos,
        hasCodeLock = hasCodeLock,
        hasTowDecoded = hasTowDecoded,
    )

    @Test
    fun `calculates GPS pseudorange from receiver and satellite time`() {
        val result = PseudorangeCalculator.calculate(clock, measurement())

        assertEquals(PseudorangeStatus.AVAILABLE, result.status)
        assertEquals(23_983_381.65, result.meters!!, 0.01)
        assertEquals(
            sqrt(3.0 * 3.0 + 4.0 * 4.0) * 1e-9 * 299_792_458.0,
            result.uncertaintyMeters!!,
            0.0001,
        )
    }

    @Test
    fun `adds measurement time offset to receiver time`() {
        val result = PseudorangeCalculator.calculate(clock, measurement(timeOffsetNanos = 10_000_000.0))

        assertEquals(PseudorangeStatus.AVAILABLE, result.status)
        assertEquals(26_981_306.23, result.meters!!, 0.01)
    }

    @Test
    fun `calculates Galileo pseudorange using GPS compatible time of week`() {
        val result = PseudorangeCalculator.calculate(clock, measurement(constellation = Constellation.GALILEO))

        assertEquals(PseudorangeStatus.AVAILABLE, result.status)
        assertEquals(23_983_381.65, result.meters!!, 0.01)
    }

    @Test
    fun `converts GPS receiver time to BDS time`() {
        val result =
            PseudorangeCalculator.calculate(
                clock,
                measurement(
                    constellation = Constellation.BEIDOU,
                    receivedSvTimeNanos = 599_985_920_000_000L,
                ),
            )

        assertEquals(PseudorangeStatus.AVAILABLE, result.status)
        assertEquals(23_983_381.65, result.meters!!, 0.01)
    }

    @Test
    fun `uses zero when bias is absent but full bias is present`() {
        val result =
            PseudorangeCalculator.calculate(
                clock.copy(biasNanos = null),
                measurement(receivedSvTimeNanos = 599_999_920_000_000L),
            )

        assertEquals(PseudorangeStatus.AVAILABLE, result.status)
        assertEquals(23_983_396.64, result.meters!!, 0.01)
    }

    @Test
    fun `wraps a receiver time at the GPS week boundary`() {
        val boundaryClock =
            clock.copy(
                timeNanos = 2_000_000_000_000L,
                fullBiasNanos = -602_800_000_000_000L,
                biasNanos = 0.0,
            )

        val result =
            PseudorangeCalculator.calculate(
                boundaryClock,
                measurement(receivedSvTimeNanos = 604_799_920_000_000L),
            )

        assertEquals(PseudorangeStatus.AVAILABLE, result.status)
        assertEquals(23_983_396.64, result.meters!!, 0.01)
    }

    @Test
    fun `reports missing full bias`() {
        val result = PseudorangeCalculator.calculate(clock.copy(fullBiasNanos = null), measurement())

        assertEquals(PseudorangeStatus.MISSING_FULL_BIAS, result.status)
        assertNull(result.meters)
    }

    @Test
    fun `reports missing satellite time`() {
        val result = PseudorangeCalculator.calculate(clock, measurement(receivedSvTimeNanos = null))

        assertEquals(PseudorangeStatus.MISSING_RECEIVED_SV_TIME, result.status)
    }

    @Test
    fun `reports missing code lock`() {
        val result = PseudorangeCalculator.calculate(clock, measurement(hasCodeLock = false))

        assertEquals(PseudorangeStatus.MISSING_CODE_LOCK, result.status)
    }

    @Test
    fun `reports missing decoded TOW`() {
        val result = PseudorangeCalculator.calculate(clock, measurement(hasTowDecoded = false))

        assertEquals(PseudorangeStatus.MISSING_TOW_DECODED, result.status)
    }

    @Test
    fun `reports unsupported constellations`() {
        val result = PseudorangeCalculator.calculate(clock, measurement(constellation = Constellation.GLONASS))

        assertEquals(PseudorangeStatus.UNSUPPORTED_CONSTELLATION, result.status)
    }

    @Test
    fun `reports invalid non finite measurement input`() {
        val result = PseudorangeCalculator.calculate(clock, measurement(timeOffsetNanos = Double.NaN))

        assertEquals(PseudorangeStatus.INVALID_INPUT, result.status)
    }

    @Test
    fun `reports an out of range pseudorange`() {
        val result =
            PseudorangeCalculator.calculate(
                clock,
                measurement(receivedSvTimeNanos = 599_999_800_000_000L),
            )

        assertEquals(PseudorangeStatus.OUT_OF_RANGE, result.status)
    }
}
