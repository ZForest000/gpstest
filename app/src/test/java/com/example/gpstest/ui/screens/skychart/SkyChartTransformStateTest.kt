package com.example.gpstest.ui.screens.skychart

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyChartTransformStateTest {
    private val center = Offset(200f, 200f)
    private val maxRadius = 180f

    @Test
    fun `defaults are identity`() {
        val state = SkyChartTransformState()
        assertEquals(1f, state.scale, 0.001f)
        assertEquals(0f, state.offset.x, 0.001f)
        assertEquals(0f, state.offset.y, 0.001f)
        assertFalse(state.northUp)
    }

    @Test
    fun `scale clamps to 1 and 4`() {
        val state = SkyChartTransformState()
        state.applyZoom(centroid = center, zoomChange = 0.1f, center = center, maxRadius = maxRadius)
        assertEquals(1f, state.scale, 0.001f)

        state.applyZoom(centroid = center, zoomChange = 10f, center = center, maxRadius = maxRadius)
        assertEquals(4f, state.scale, 0.001f)
    }

    @Test
    fun `pan forced zero at scale 1`() {
        val state = SkyChartTransformState()
        state.applyPan(Offset(50f, -40f), maxRadius)
        assertEquals(0f, state.offset.x, 0.001f)
        assertEquals(0f, state.offset.y, 0.001f)
    }

    @Test
    fun `pan allowed when scale greater than 1`() {
        val state = SkyChartTransformState()
        // zoom in first
        state.applyZoom(centroid = center, zoomChange = 2f, center = center, maxRadius = maxRadius)
        assertTrue(state.scale > 1f)
        state.applyPan(Offset(20f, -10f), maxRadius)
        assertTrue(state.offset.x != 0f || state.offset.y != 0f)
    }

    @Test
    fun `reset clears scale and offset but not northUp`() {
        val state = SkyChartTransformState()
        state.setNorthUp(true)
        state.applyZoom(centroid = center, zoomChange = 2f, center = center, maxRadius = maxRadius)
        state.applyPan(Offset(15f, 15f), maxRadius)
        state.resetScaleAndOffset()
        assertEquals(1f, state.scale, 0.001f)
        assertEquals(0f, state.offset.x, 0.001f)
        assertEquals(0f, state.offset.y, 0.001f)
        assertTrue(state.northUp)
    }

    @Test
    fun `setNorthUp toggles independently`() {
        val state = SkyChartTransformState()
        state.setNorthUp(true)
        assertTrue(state.northUp)
        state.setNorthUp(false)
        assertFalse(state.northUp)
    }
}
