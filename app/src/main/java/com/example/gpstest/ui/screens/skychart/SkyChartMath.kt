package com.example.gpstest.ui.screens.skychart

import androidx.compose.ui.geometry.Offset
import com.example.gpstest.domain.model.Constellation
import com.example.gpstest.domain.model.GnssSatellite
import kotlin.math.cos
import kotlin.math.sin

fun satelliteKey(constellation: Constellation, svid: Int): String =
    "${constellation.name}_$svid"

fun satelliteKey(satellite: GnssSatellite): String =
    satelliteKey(satellite.constellation, satellite.svid)

/**
 * Linear interpolate azimuth along the shortest arc. Result normalized to [0, 360).
 */
fun shortestArcAzimuthLerp(fromDeg: Float, toDeg: Float, t: Float): Float {
    val from = normalizeDegrees(fromDeg)
    val to = normalizeDegrees(toDeg)
    var delta = to - from
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    return normalizeDegrees(from + delta * t.coerceIn(0f, 1f))
}

fun elevationLerp(fromDeg: Float, toDeg: Float, t: Float): Float =
    fromDeg + (toDeg - fromDeg) * t.coerceIn(0f, 1f)

/**
 * Chart-space point → screen-space after the canvas transform chain:
 * translate(center) → rotate(-heading if northUp) → translate(offset) → scale(scale)
 *
 * Chart coordinates are absolute canvas coords (origin top-left, center at [center]).
 * Vector from center is scaled, offset, then rotated.
 */
fun chartToScreen(
    point: Offset,
    center: Offset,
    scale: Float,
    offset: Offset,
    headingDeg: Float,
    northUp: Boolean,
): Offset {
    val v = Offset(point.x - center.x, point.y - center.y)
    val scaled = Offset(v.x * scale + offset.x, v.y * scale + offset.y)
    val rotated =
        if (northUp) {
            rotateOffset(scaled, -headingDeg)
        } else {
            scaled
        }
    return Offset(center.x + rotated.x, center.y + rotated.y)
}

/**
 * Inverse of [chartToScreen]: screen → chart for hit-testing.
 */
fun screenToChart(
    point: Offset,
    center: Offset,
    scale: Float,
    offset: Offset,
    headingDeg: Float,
    northUp: Boolean,
): Offset {
    val v = Offset(point.x - center.x, point.y - center.y)
    val unrotated =
        if (northUp) {
            rotateOffset(v, headingDeg)
        } else {
            v
        }
    val safeScale = scale.coerceAtLeast(0.0001f)
    val unscaled =
        Offset(
            (unrotated.x - offset.x) / safeScale,
            (unrotated.y - offset.y) / safeScale,
        )
    return Offset(center.x + unscaled.x, center.y + unscaled.y)
}

/**
 * At scale==1 force zero. Otherwise clamp each axis to ± maxRadius*(scale-1).
 */
fun clampPanOffset(offset: Offset, scale: Float, maxRadius: Float): Offset {
    if (scale <= 1f) return Offset.Zero
    val maxPan = maxRadius * (scale - 1f)
    return Offset(
        offset.x.coerceIn(-maxPan, maxPan),
        offset.y.coerceIn(-maxPan, maxPan),
    )
}

fun normalizeDegrees(degrees: Float): Float {
    var d = degrees % 360f
    if (d < 0f) d += 360f
    return d
}

/** Rotate vector by [degrees] (positive = clockwise in screen Y-down coords? use standard math CCW). */
fun rotateOffset(v: Offset, degrees: Float): Offset {
    val rad = Math.toRadians(degrees.toDouble())
    val c = cos(rad).toFloat()
    val s = sin(rad).toFloat()
    // Standard 2D rotation (CCW for positive degrees in math coords).
    // Canvas Y grows downward; rotation sense matches Canvas.rotate(degrees).
    return Offset(v.x * c - v.y * s, v.x * s + v.y * c)
}
