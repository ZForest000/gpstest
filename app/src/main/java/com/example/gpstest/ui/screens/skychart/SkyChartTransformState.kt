package com.example.gpstest.ui.screens.skychart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/**
 * Sky chart view transform. Scale clamped [1, 4]; pan forced zero at 1×.
 * [northUp] is independent of scale/offset (double-tap does not clear it).
 */
class SkyChartTransformState(
    initialScale: Float = 1f,
    initialOffset: Offset = Offset.Zero,
    initialNorthUp: Boolean = false,
) {
    var scale by mutableFloatStateOf(initialScale.coerceIn(MIN_SCALE, MAX_SCALE))
        private set

    var offset by mutableStateOf(initialOffset)
        private set

    private var _northUp by mutableStateOf(initialNorthUp)
    val northUp: Boolean get() = _northUp

    /**
     * Apply pinch zoom around [centroid] (screen coords).
     * Anchors zoom so the chart point under the pinch center stays put.
     */
    fun applyZoom(
        centroid: Offset,
        zoomChange: Float,
        center: Offset,
        maxRadius: Float,
    ) {
        if (zoomChange == 1f || zoomChange <= 0f) return
        val oldScale = scale
        val newScale = (oldScale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
        if (newScale == oldScale) {
            if (newScale <= 1f) offset = Offset.Zero
            return
        }

        // Chart point under centroid before zoom (heading ignored for pan math;
        // gestures are applied in post-rotation screen space — see SkyChartView).
        // Using identity heading for offset math: offset lives in rotated chart space.
        val before =
            screenToChart(
                point = centroid,
                center = center,
                scale = oldScale,
                offset = offset,
                headingDeg = 0f,
                northUp = false,
            )
        scale = newScale
        if (newScale <= 1f) {
            offset = Offset.Zero
            return
        }
        // Recompute with current offset so the chart point under [centroid] stays put.
        val afterWithOffset =
            chartToScreen(
                point = before,
                center = center,
                scale = newScale,
                offset = offset,
                headingDeg = 0f,
                northUp = false,
            )
        val delta = Offset(centroid.x - afterWithOffset.x, centroid.y - afterWithOffset.y)
        offset = clampPanOffset(offset + delta, newScale, maxRadius)
    }

    fun applyPan(pan: Offset, maxRadius: Float) {
        if (scale <= 1f) {
            offset = Offset.Zero
            return
        }
        offset = clampPanOffset(offset + pan, scale, maxRadius)
    }

    fun resetScaleAndOffset() {
        scale = MIN_SCALE
        offset = Offset.Zero
    }

    fun setNorthUp(enabled: Boolean) {
        _northUp = enabled
    }

    companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 4f
    }
}

@Composable
fun rememberSkyChartTransformState(): SkyChartTransformState =
    remember { SkyChartTransformState() }
