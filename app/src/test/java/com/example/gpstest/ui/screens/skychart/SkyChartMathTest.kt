package com.example.gpstest.ui.screens.skychart

import androidx.compose.ui.geometry.Offset
import com.example.gpstest.domain.model.Constellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SkyChartMathTest {
    @Test
    fun `satelliteKey joins constellation name and svid`() {
        assertEquals("GPS_7", satelliteKey(Constellation.GPS, 7))
        assertEquals("BEIDOU_12", satelliteKey(Constellation.BEIDOU, 12))
    }

    @Test
    fun `shortest arc goes forward across zero`() {
        // 350 → 10 should be +20°, at t=0.5 ≈ 0°
        val mid = shortestArcAzimuthLerp(350f, 10f, 0.5f)
        assertTrue("mid=$mid", abs(mid - 0f) < 0.01f || abs(mid - 360f) < 0.01f)
    }

    @Test
    fun `shortest arc goes backward across zero`() {
        // 10 → 350 should be -20°, at t=0.5 ≈ 0°
        val mid = shortestArcAzimuthLerp(10f, 350f, 0.5f)
        assertTrue("mid=$mid", abs(mid - 0f) < 0.01f || abs(mid - 360f) < 0.01f)
    }

    @Test
    fun `shortest arc endpoints preserved`() {
        assertEquals(350f, shortestArcAzimuthLerp(350f, 10f, 0f), 0.01f)
        assertEquals(10f, shortestArcAzimuthLerp(350f, 10f, 1f), 0.01f)
    }

    @Test
    fun `elevation lerp is linear`() {
        assertEquals(30f, elevationLerp(0f, 60f, 0.5f), 0.01f)
        assertEquals(0f, elevationLerp(0f, 60f, 0f), 0.01f)
        assertEquals(60f, elevationLerp(0f, 60f, 1f), 0.01f)
    }

    @Test
    fun `identity transform round-trip`() {
        val center = Offset(200f, 200f)
        val chart = Offset(250f, 180f)
        val screen =
            chartToScreen(
                point = chart,
                center = center,
                scale = 1f,
                offset = Offset.Zero,
                headingDeg = 0f,
                northUp = false,
            )
        val back =
            screenToChart(
                point = screen,
                center = center,
                scale = 1f,
                offset = Offset.Zero,
                headingDeg = 0f,
                northUp = false,
            )
        assertEquals(chart.x, back.x, 0.05f)
        assertEquals(chart.y, back.y, 0.05f)
    }

    @Test
    fun `scale and offset round-trip`() {
        val center = Offset(200f, 200f)
        val chart = Offset(240f, 160f)
        val scale = 2f
        val offset = Offset(30f, -20f)
        val screen =
            chartToScreen(chart, center, scale, offset, headingDeg = 0f, northUp = false)
        val back =
            screenToChart(screen, center, scale, offset, headingDeg = 0f, northUp = false)
        assertEquals(chart.x, back.x, 0.05f)
        assertEquals(chart.y, back.y, 0.05f)
    }

    @Test
    fun `north-up rotation round-trip`() {
        val center = Offset(200f, 200f)
        val chart = Offset(250f, 180f)
        val heading = 45f
        val scale = 1.5f
        val offset = Offset(10f, 15f)
        val screen =
            chartToScreen(chart, center, scale, offset, headingDeg = heading, northUp = true)
        val back =
            screenToChart(screen, center, scale, offset, headingDeg = heading, northUp = true)
        assertEquals(chart.x, back.x, 0.05f)
        assertEquals(chart.y, back.y, 0.05f)
    }

    @Test
    fun `clamp pan forces zero at scale 1`() {
        val clamped = clampPanOffset(Offset(100f, -50f), scale = 1f, maxRadius = 200f)
        assertEquals(0f, clamped.x, 0.001f)
        assertEquals(0f, clamped.y, 0.001f)
    }

    @Test
    fun `clamp pan limits offset when scaled`() {
        val maxRadius = 200f
        val scale = 2f
        // Max pan roughly maxRadius * (scale - 1) so content still reachable
        val maxPan = maxRadius * (scale - 1f)
        val clamped = clampPanOffset(Offset(10_000f, -10_000f), scale, maxRadius)
        assertTrue(abs(clamped.x) <= maxPan + 0.01f)
        assertTrue(abs(clamped.y) <= maxPan + 0.01f)
    }
}
