package com.example.gpstest.ui.screens.skychart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import com.example.gpstest.domain.model.GnssSatellite

data class AnimatedSatellite(
    val satellite: GnssSatellite,
    val azimuthDegrees: Float,
    val elevationDegrees: Float,
    val alpha: Float,
)

private data class TrackedSat(
    var satellite: GnssSatellite,
    var fromAz: Float,
    var fromEl: Float,
    var toAz: Float,
    var toEl: Float,
    var moveStartMs: Long,
    var alphaFrom: Float,
    var alphaTo: Float,
    var alphaStartMs: Long,
    var removing: Boolean,
)

private const val MOVE_DURATION_MS = 400L
private const val FADE_DURATION_MS = 300L

@Composable
fun rememberAnimatedSatellites(satellites: List<GnssSatellite>): List<AnimatedSatellite> {
    val tracks = remember { mutableMapOf<String, TrackedSat>() }
    var frame by remember { mutableStateOf(0L) }

    // Sync tracks when satellite list identity/content changes
    LaunchedEffect(satellites) {
        val now = frame
        val incoming = satellites.associateBy { satelliteKey(it) }
        // Appear / update
        for ((key, sat) in incoming) {
            val existing = tracks[key]
            if (existing == null) {
                tracks[key] =
                    TrackedSat(
                        satellite = sat,
                        fromAz = sat.azimuthDegrees,
                        fromEl = sat.elevationDegrees,
                        toAz = sat.azimuthDegrees,
                        toEl = sat.elevationDegrees,
                        moveStartMs = now,
                        alphaFrom = 0f,
                        alphaTo = 1f,
                        alphaStartMs = now,
                        removing = false,
                    )
            } else {
                val currentAz =
                    shortestArcAzimuthLerp(
                        existing.fromAz,
                        existing.toAz,
                        progress(now, existing.moveStartMs, MOVE_DURATION_MS),
                    )
                val currentEl =
                    elevationLerp(
                        existing.fromEl,
                        existing.toEl,
                        progress(now, existing.moveStartMs, MOVE_DURATION_MS),
                    )
                existing.satellite = sat
                existing.removing = false
                if (sat.azimuthDegrees != existing.toAz || sat.elevationDegrees != existing.toEl) {
                    existing.fromAz = currentAz
                    existing.fromEl = currentEl
                    existing.toAz = sat.azimuthDegrees
                    existing.toEl = sat.elevationDegrees
                    existing.moveStartMs = now
                }
                // If was fading out, reverse to fade in
                if (existing.alphaTo < 1f) {
                    val currentAlpha =
                        lerp(
                            existing.alphaFrom,
                            existing.alphaTo,
                            progress(now, existing.alphaStartMs, FADE_DURATION_MS),
                        )
                    existing.alphaFrom = currentAlpha
                    existing.alphaTo = 1f
                    existing.alphaStartMs = now
                }
            }
        }
        // Disappear — mark removing
        val toRemoveKeys = tracks.keys.filter { it !in incoming }
        for (key in toRemoveKeys) {
            val existing = tracks[key] ?: continue
            if (!existing.removing) {
                val currentAlpha =
                    lerp(
                        existing.alphaFrom,
                        existing.alphaTo,
                        progress(now, existing.alphaStartMs, FADE_DURATION_MS),
                    )
                existing.alphaFrom = currentAlpha
                existing.alphaTo = 0f
                existing.alphaStartMs = now
                existing.removing = true
            }
        }
    }

    // Frame ticker while any animation is active, or always while tracks non-empty
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { ms ->
                frame = ms
                // Purge fully faded
                val keys = tracks.keys.toList()
                for (key in keys) {
                    val t = tracks[key] ?: continue
                    if (t.removing) {
                        val p = progress(ms, t.alphaStartMs, FADE_DURATION_MS)
                        if (p >= 1f && t.alphaTo == 0f) {
                            tracks.remove(key)
                        }
                    }
                }
            }
        }
    }

    val now = frame
    return tracks.values.map { t ->
        val moveP = progress(now, t.moveStartMs, MOVE_DURATION_MS)
        val az = shortestArcAzimuthLerp(t.fromAz, t.toAz, moveP)
        val el = elevationLerp(t.fromEl, t.toEl, moveP)
        val alphaP = progress(now, t.alphaStartMs, FADE_DURATION_MS)
        val alpha = lerp(t.alphaFrom, t.alphaTo, alphaP)
        AnimatedSatellite(
            satellite = t.satellite,
            azimuthDegrees = az,
            elevationDegrees = el,
            alpha = alpha.coerceIn(0f, 1f),
        )
    }
}

private fun progress(
    now: Long,
    start: Long,
    duration: Long,
): Float {
    if (duration <= 0L) return 1f
    return ((now - start).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
}

private fun lerp(
    from: Float,
    to: Float,
    t: Float,
): Float = from + (to - from) * t.coerceIn(0f, 1f)
