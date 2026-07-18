package com.example.gpstest.domain.model

import com.example.gpstest.data.source.AntennaInfoMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AntennaInfoTest {
    @Test
    fun `fromPrimitives preserves PCO and carrier`() {
        val info =
            AntennaInfoMapper.fromPrimitives(
                carrierFrequencyMHz = 1575.42,
                pcoXMm = 1.0,
                pcoYMm = 2.0,
                pcoZMm = 3.0,
                pcoXUncertaintyMm = 0.1,
                pcoYUncertaintyMm = 0.2,
                pcoZUncertaintyMm = 0.3,
                pcvSummary = null,
            )
        assertEquals(1575.42, info.carrierFrequencyMHz, 0.0)
        assertEquals(1.0, info.pcoXMm, 0.0)
        assertEquals(2.0, info.pcoYMm, 0.0)
        assertEquals(3.0, info.pcoZMm, 0.0)
        assertEquals(0.1, info.pcoXUncertaintyMm, 0.0)
        assertEquals(0.2, info.pcoYUncertaintyMm, 0.0)
        assertEquals(0.3, info.pcoZUncertaintyMm, 0.0)
        assertNull(info.pcvSummary)
    }

    @Test
    fun `summarizePcv returns null when corrections null`() {
        assertNull(AntennaInfoMapper.summarizePcv(null, 30.0, 5.0))
    }

    @Test
    fun `summarizePcv returns null when corrections empty`() {
        assertNull(AntennaInfoMapper.summarizePcv(emptyArray(), 30.0, 5.0))
    }

    @Test
    fun `summarizePcv computes min max count and deltas`() {
        val grid =
            arrayOf(
                doubleArrayOf(-1.5, 0.0, 2.5),
                doubleArrayOf(1.0, -0.5, 0.25),
            )
        val summary = AntennaInfoMapper.summarizePcv(grid, 30.0, 5.0)!!
        assertEquals(30.0, summary.deltaPhiDeg, 0.0)
        assertEquals(5.0, summary.deltaThetaDeg, 0.0)
        assertEquals(6, summary.sampleCount)
        assertEquals(-1.5, summary.minCorrectionMm, 0.0)
        assertEquals(2.5, summary.maxCorrectionMm, 0.0)
    }

    @Test
    fun `data class equality holds`() {
        val a =
            AntennaInfo(
                carrierFrequencyMHz = 1176.45,
                pcoXMm = 0.0,
                pcoYMm = 0.0,
                pcoZMm = 10.0,
                pcoXUncertaintyMm = 0.0,
                pcoYUncertaintyMm = 0.0,
                pcoZUncertaintyMm = 1.0,
                pcvSummary = null,
            )
        val b = a.copy()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
