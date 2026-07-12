# Sky Chart Interaction Phase 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add pinch zoom/pan with double-tap reset, satellite position animation (lerp + fade), and optional north-up compass rotation to the existing Sky Chart screen.

**Architecture:** Keep all new state in Compose UI. Split pure helpers into three files under `ui/screens/skychart/`: transform math, compass sensor source, animated positions. `SkyChartView` stays a draw + hit-test component that consumes transform, heading, and animated positions. No ViewModel, domain, repository, permission, or dependency changes.

**Tech Stack:** Kotlin 2.1.0, Jetpack Compose (BOM 2024.10.01) + Material 3, Canvas + `detectTransformGestures` / `detectTapGestures`, Android `Sensor.TYPE_ROTATION_VECTOR`, JUnit 4 unit tests (no mock).

## Global Constraints

- Scope: zoom/pan + double-tap reset + position animation + north-up toggle only. **No screenshot share.**
- No ViewModel / Repository / data layer / AndroidManifest changes.
- No new permissions or third-party dependencies.
- North-up = true north fixed at screen top (not heading-up). Default **OFF**.
- Double-tap resets **scale + offset only**; never `northUp`.
- At `scale == 1f`, pan forced to `Offset.Zero`.
- Sensor registers **only** while `northUp == true` and sky chart is foreground/resumed; unregister on off / leave screen / background.
- Sensor unavailable → keep `northUp` on, `heading = 0f`, no toast/error UI.
- Hit-test only satellites with `alpha > 0.5f` after inverse transform.
- Linear animation only (no spring, no trails).
- Unit tests: JUnit 4, no mock, pure functions — same style as `DopCalculatorTest`.
- Verification: `./gradlew ktlintCheck`, `./gradlew test`, `./gradlew assembleDebug`.
- JDK: set `JAVA_HOME` to `C:\Program Files\Java\jdk-21` before Gradle on this machine.
- Commit style: conventional commits with emoji per AGENTS.md (`feat: ✨ ...`).

**Spec:** `docs/superpowers/specs/2026-07-12-sky-chart-interaction-phase2-design.md`

---

## File Map

| Path                                                                                      | Action | Responsibility                                                                               |
| ----------------------------------------------------------------------------------------- | ------ | -------------------------------------------------------------------------------------------- |
| `app/src/main/java/com/example/gpstest/ui/screens/skychart/SkyChartMath.kt`               | Create | Pure math: satellite key, shortest-arc azimuth, canvas↔screen transform + inverse, pan clamp |
| `app/src/main/java/com/example/gpstest/ui/screens/skychart/SkyChartTransformState.kt`     | Create | Mutable Compose state: scale, offset, northUp; clamp/reset                                   |
| `app/src/main/java/com/example/gpstest/ui/screens/skychart/AnimatedSatellitePositions.kt` | Create | Per-key az/el lerp + alpha fade; produces draw list                                          |
| `app/src/main/java/com/example/gpstest/ui/screens/skychart/CompassHeadingSource.kt`       | Create | Lifecycle-aware `TYPE_ROTATION_VECTOR` → smoothed heading                                    |
| `app/src/main/java/com/example/gpstest/ui/screens/skychart/SkyChartView.kt`               | Modify | Apply transform; draw animated positions; inverse hit-test; transform gestures               |
| `app/src/main/java/com/example/gpstest/ui/screens/skychart/SkyChartScreen.kt`             | Modify | Own transform/animation/compass; north-up IconButton overlay                                 |
| `app/src/test/java/com/example/gpstest/ui/screens/skychart/SkyChartMathTest.kt`           | Create | Unit tests for math helpers                                                                  |
| `app/src/test/java/com/example/gpstest/ui/screens/skychart/SkyChartTransformStateTest.kt` | Create | Unit tests for transform clamp/reset                                                         |

**Unchanged:** `SkyChartLegend.kt`, ViewModel, domain, data, Manifest.

---

### Task 1: Pure Math Helpers + Unit Tests

**Files:**

- Create: `app/src/main/java/com/example/gpstest/ui/screens/skychart/SkyChartMath.kt`
- Test: `app/src/test/java/com/example/gpstest/ui/screens/skychart/SkyChartMathTest.kt`

**Interfaces:**

- Consumes: `Constellation`, `GnssSatellite` field names only (no Android UI).
- Produces:
    - `fun satelliteKey(constellation: Constellation, svid: Int): String`
    - `fun satelliteKey(satellite: GnssSatellite): String`
    - `fun shortestArcAzimuthLerp(fromDeg: Float, toDeg: Float, t: Float): Float`
    - `fun elevationLerp(fromDeg: Float, toDeg: Float, t: Float): Float`
    - `fun chartToScreen(point: Offset, center: Offset, scale: Float, offset: Offset, headingDeg: Float, northUp: Boolean): Offset`
    - `fun screenToChart(point: Offset, center: Offset, scale: Float, offset: Offset, headingDeg: Float, northUp: Boolean): Offset`
    - `fun clampPanOffset(offset: Offset, scale: Float, maxRadius: Float): Offset`

- [ ] **Step 1: Write failing unit tests**

Create `app/src/test/java/com/example/gpstest/ui/screens/skychart/SkyChartMathTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21"
./gradlew test --tests "com.example.gpstest.ui.screens.skychart.SkyChartMathTest"
```

Expected: FAIL — unresolved references (`satelliteKey`, `shortestArcAzimuthLerp`, etc.).

- [ ] **Step 3: Implement `SkyChartMath.kt`**

Create `app/src/main/java/com/example/gpstest/ui/screens/skychart/SkyChartMath.kt`:

```kotlin
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
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21"
./gradlew test --tests "com.example.gpstest.ui.screens.skychart.SkyChartMathTest"
```

Expected: `BUILD SUCCESSFUL`, all tests PASS.

If round-trip fails due to rotation sign mismatch with Compose `Canvas.rotate`, flip the sign in `rotateOffset` usage inside `chartToScreen` / `screenToChart` (keep inverse consistent) and re-run until green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/gpstest/ui/screens/skychart/SkyChartMath.kt \
  app/src/test/java/com/example/gpstest/ui/screens/skychart/SkyChartMathTest.kt
git commit -m "feat: ✨ add sky chart pure math helpers and unit tests"
```

---

### Task 2: Transform State + Unit Tests

**Files:**

- Create: `app/src/main/java/com/example/gpstest/ui/screens/skychart/SkyChartTransformState.kt`
- Test: `app/src/test/java/com/example/gpstest/ui/screens/skychart/SkyChartTransformStateTest.kt`

**Interfaces:**

- Consumes: `clampPanOffset` from Task 1.
- Produces:
    - `class SkyChartTransformState` with `scale: Float`, `offset: Offset`, `northUp: Boolean`
    - `fun applyZoom(centroid: Offset, zoomChange: Float, center: Offset, maxRadius: Float)`
    - `fun applyPan(pan: Offset, maxRadius: Float)`
    - `fun resetScaleAndOffset()`
    - `fun setNorthUp(enabled: Boolean)`
    - `fun rememberSkyChartTransformState(): SkyChartTransformState`

- [ ] **Step 1: Write failing unit tests**

Create `app/src/test/java/com/example/gpstest/ui/screens/skychart/SkyChartTransformStateTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21"
./gradlew test --tests "com.example.gpstest.ui.screens.skychart.SkyChartTransformStateTest"
```

Expected: FAIL — `SkyChartTransformState` not found.

- [ ] **Step 3: Implement `SkyChartTransformState.kt`**

Create `app/src/main/java/com/example/gpstest/ui/screens/skychart/SkyChartTransformState.kt`:

```kotlin
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

    var northUp by mutableStateOf(initialNorthUp)
        private set

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
        val after =
            chartToScreen(
                point = before,
                center = center,
                scale = newScale,
                offset = Offset.Zero,
                headingDeg = 0f,
                northUp = false,
            )
        // Adjust offset so [before] maps back to [centroid]
        val desired =
            Offset(
                offset.x + (centroid.x - after.x),
                offset.y + (centroid.y - after.y),
            )
        // Recompute properly with current offset:
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
        northUp = enabled
    }

    companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 4f
    }
}

@Composable
fun rememberSkyChartTransformState(): SkyChartTransformState =
    remember { SkyChartTransformState() }
```

**Note for implementer:** If `applyZoom` anchor math is flaky in manual testing, simplify to:

```kotlin
fun applyZoom(centroid: Offset, zoomChange: Float, center: Offset, maxRadius: Float) {
    val newScale = (scale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
    scale = newScale
    offset = if (newScale <= 1f) Offset.Zero else clampPanOffset(offset, newScale, maxRadius)
}
```

Pinch-center anchoring is preferred when it works; unit tests only require clamp/reset behavior.

- [ ] **Step 4: Run tests to verify they pass**

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21"
./gradlew test --tests "com.example.gpstest.ui.screens.skychart.SkyChartTransformStateTest"
```

Expected: PASS. If `applyZoom` with large `zoomChange` overshoots intermediate steps, that is fine — final clamp to 4f is what matters.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/gpstest/ui/screens/skychart/SkyChartTransformState.kt \
  app/src/test/java/com/example/gpstest/ui/screens/skychart/SkyChartTransformStateTest.kt
git commit -m "feat: ✨ add sky chart transform state with clamp and reset"
```

---

### Task 3: Animated Satellite Positions

**Files:**

- Create: `app/src/main/java/com/example/gpstest/ui/screens/skychart/AnimatedSatellitePositions.kt`

**Interfaces:**

- Consumes: `List<GnssSatellite>`, `satelliteKey`, `shortestArcAzimuthLerp`, `elevationLerp` from Task 1.
- Produces:
    - `data class AnimatedSatellite(val satellite: GnssSatellite, val azimuthDegrees: Float, val elevationDegrees: Float, val alpha: Float)`
    - `@Composable fun rememberAnimatedSatellites(satellites: List<GnssSatellite>): List<AnimatedSatellite>`

- [ ] **Step 1: Implement animator (no unit test for frame loop)**

Create `app/src/main/java/com/example/gpstest/ui/screens/skychart/AnimatedSatellitePositions.kt`:

```kotlin
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

private fun progress(now: Long, start: Long, duration: Long): Float {
    if (duration <= 0L) return 1f
    return ((now - start).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
}

private fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t.coerceIn(0f, 1f)
```

**Implementer notes:**

- `LaunchedEffect(satellites)` may not re-fire if the list is a new instance every GNSS tick (it usually is). That is desired — each update restarts move from current interpolated position.
- First frame `frame == 0` is OK; `withFrameMillis` quickly advances.
- Do **not** unit-test the frame loop (spec).

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/example/gpstest/ui/screens/skychart/AnimatedSatellitePositions.kt
git commit -m "feat: ✨ add animated satellite positions for sky chart"
```

---

### Task 4: Compass Heading Source

**Files:**

- Create: `app/src/main/java/com/example/gpstest/ui/screens/skychart/CompassHeadingSource.kt`

**Interfaces:**

- Consumes: Android `SensorManager`, `Sensor.TYPE_ROTATION_VECTOR`, Compose lifecycle.
- Produces: `@Composable fun rememberCompassHeading(enabled: Boolean): Float` — smoothed heading 0..360, or `0f` if unavailable.

- [ ] **Step 1: Implement compass source**

Create `app/src/main/java/com/example/gpstest/ui/screens/skychart/CompassHeadingSource.kt`:

```kotlin
package com.example.gpstest.ui.screens.skychart

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Exposes device azimuth relative to true north (0..360) only while [enabled]
 * and the host lifecycle is at least STARTED. Unregisters immediately otherwise.
 * If TYPE_ROTATION_VECTOR is missing, returns 0f with no error UI.
 */
@Composable
fun rememberCompassHeading(enabled: Boolean): Float {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var heading by remember { mutableFloatStateOf(0f) }

    DisposableEffect(enabled, lifecycleOwner) {
        if (!enabled) {
            heading = 0f
            onDispose { }
        } else {
            val sensorManager =
                context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            if (sensorManager == null || rotationSensor == null) {
                heading = 0f
                onDispose { }
            } else {
                var smoothed = heading
                val listener =
                    object : SensorEventListener {
                        private val rotationMatrix = FloatArray(9)
                        private val orientation = FloatArray(3)
                        private val remapped = FloatArray(9)

                        override fun onSensorChanged(event: SensorEvent?) {
                            if (event?.sensor?.type != Sensor.TYPE_ROTATION_VECTOR) return
                            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                            val display =
                                (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
                                    ?.defaultDisplay
                            val rotation = display?.rotation ?: Surface.ROTATION_0
                            val (axisX, axisY) =
                                when (rotation) {
                                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                                }
                            SensorManager.remapCoordinateSystem(
                                rotationMatrix,
                                axisX,
                                axisY,
                                remapped,
                            )
                            SensorManager.getOrientation(remapped, orientation)
                            // azimuth radians → degrees [0, 360)
                            val raw =
                                normalizeDegrees(
                                    Math.toDegrees(orientation[0].toDouble()).toFloat(),
                                )
                            // Shortest-arc low-pass ~120 ms at SENSOR_DELAY_UI (~60 Hz)
                            // alpha ≈ dt / (tau + dt); use fixed blend 0.15 ≈ 100–150 ms feel
                            smoothed = shortestArcBlend(smoothed, raw, 0.15f)
                            heading = smoothed
                        }

                        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                    }

                var registered = false
                fun register() {
                    if (!registered) {
                        sensorManager.registerListener(
                            listener,
                            rotationSensor,
                            SensorManager.SENSOR_DELAY_UI,
                        )
                        registered = true
                    }
                }

                fun unregister() {
                    if (registered) {
                        sensorManager.unregisterListener(listener)
                        registered = false
                    }
                }

                val observer =
                    LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> register()
                            Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> unregister()
                            else -> Unit
                        }
                    }
                lifecycleOwner.lifecycle.addObserver(observer)
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    register()
                }

                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    unregister()
                }
            }
        }
    }

    return if (enabled) heading else 0f
}

/** Blend toward [target] along shortest arc by [alpha] (0..1). */
internal fun shortestArcBlend(current: Float, target: Float, alpha: Float): Float {
    val from = normalizeDegrees(current)
    val to = normalizeDegrees(target)
    var delta = to - from
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    return normalizeDegrees(from + delta * alpha.coerceIn(0f, 1f))
}
```

- [ ] **Step 2: Optional tiny unit test for blend (same file as math or new)**

Append to `SkyChartMathTest.kt` (or create a one-test file) only if you prefer pure coverage of `shortestArcBlend`. Spec requires shortest-arc for animation; blend is analogous. Minimum: already covered by Task 1 arc tests. Skip extra test if time-boxed.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/gpstest/ui/screens/skychart/CompassHeadingSource.kt
git commit -m "feat: ✨ add lifecycle-aware compass heading source for north-up"
```

---

### Task 5: Wire Transform, Animation, Gestures into `SkyChartView`

**Files:**

- Modify: `app/src/main/java/com/example/gpstest/ui/screens/skychart/SkyChartView.kt`

**Interfaces:**

- Consumes: `List<AnimatedSatellite>`, `SkyChartTransformState`, `heading: Float`.
- Produces: Updated `SkyChartView` signature:

```kotlin
@Composable
fun SkyChartView(
    satellites: List<AnimatedSatellite>,
    transformState: SkyChartTransformState,
    headingDegrees: Float,
    onSatelliteClick: (GnssSatellite) -> Unit,
    modifier: Modifier = Modifier,
)
```

- [ ] **Step 1: Update imports**

Add:

```kotlin
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
// keep detectTapGestures
import kotlin.math.hypot
```

- [ ] **Step 2: Change function signature**

Replace `satellites: List<GnssSatellite>` with:

```kotlin
fun SkyChartView(
    satellites: List<AnimatedSatellite>,
    transformState: SkyChartTransformState,
    headingDegrees: Float,
    onSatelliteClick: (GnssSatellite) -> Unit,
    modifier: Modifier = Modifier,
)
```

- [ ] **Step 3: Build plots from animated az/el/alpha**

Replace plottable filter + plot map with:

```kotlin
val plottableSatellites =
    satellites.filter {
        it.azimuthDegrees > 0f || it.elevationDegrees > 0f
    }

// inside BoxWithConstraints, after center/maxRadius:
val plots =
    plottableSatellites.map { anim ->
        val elRad = anim.elevationDegrees.coerceIn(0f, 90f)
        val azRad = Math.toRadians((anim.azimuthDegrees - 90.0))
        val r = (1f - elRad / 90f) * maxRadius
        // Positions in chart space relative to center (before canvas transform)
        val x = center.x + r * cos(azRad).toFloat()
        val y = center.y + r * sin(azRad).toFloat()
        val visualRadius =
            with(density) {
                (5f + (anim.satellite.cn0DbHz.coerceIn(0f, 50f) / 50f) * 5f).dp.toPx()
            }
        SatellitePlot(anim.satellite, x, y, visualRadius, anim.alpha)
    }
```

Update `SatellitePlot`:

```kotlin
private data class SatellitePlot(
    val satellite: GnssSatellite,
    val x: Float,
    val y: Float,
    val visualRadius: Float,
    val animAlpha: Float,
)
```

- [ ] **Step 4: Apply canvas transform when drawing**

Wrap the existing draw body (background through satellites) in:

```kotlin
val scale = transformState.scale
val pan = transformState.offset
val northUp = transformState.northUp
val heading = if (northUp) headingDegrees else 0f

withTransform({
    // Order matches spec: translate(center) → rotate(-heading) → translate(offset) → scale
    translate(left = center.x, top = center.y)
    if (northUp) {
        rotate(degrees = -heading, pivot = Offset.Zero)
    }
    translate(left = pan.x, top = pan.y)
    scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
    // Shift so subsequent draw calls that use absolute center still work:
    // after these transforms, local (0,0) is chart center.
    // Easiest path: draw everything in center-relative coords OR
    // translate back by -center so absolute coords still match.
    translate(left = -center.x, top = -center.y)
}) {
    // existing drawCircle/drawLine/drawText/satellite loop
}
```

**Critical:** Existing code draws with absolute positions using `center`. The final `translate(-center)` makes absolute chart coords still correct under the transform stack. Verify visually that rings stay circular and N is top in chart space.

When drawing satellite fill color, multiply alphas:

```kotlin
val alpha = (if (sat.usedInFix) 1f else nonFixAlpha) * plot.animAlpha
```

Skip drawing labels/dots when `plot.animAlpha <= 0.01f`.

- [ ] **Step 5: Gestures — transform + double-tap + tap hit-test**

Replace the single `pointerInput(plots)` block with two (or one combined) `pointerInput`s:

```kotlin
.pointerInput(transformState, center, maxRadius) {
    detectTransformGestures { centroid, pan, zoom, _ ->
        if (zoom != 1f) {
            transformState.applyZoom(
                centroid = centroid,
                zoomChange = zoom,
                center = center,
                maxRadius = maxRadius,
            )
        }
        if (pan != Offset.Zero) {
            // Pan is in screen space; when northUp rotates canvas, pan is already
            // in post-rotation screen coords (gesture system). Offset is stored in
            // rotated chart space — if northUp, inverse-rotate pan before apply.
            val adjustedPan =
                if (transformState.northUp) {
                    rotateOffset(pan, headingDegrees)
                } else {
                    pan
                }
            transformState.applyPan(adjustedPan, maxRadius)
        }
    }
}
.pointerInput(
    plots,
    transformState.scale,
    transformState.offset,
    transformState.northUp,
    headingDegrees,
    center,
    maxRadius,
) {
    detectTapGestures(
        onDoubleTap = {
            transformState.resetScaleAndOffset()
        },
        onTap = { screenOffset ->
            val chartPoint =
                screenToChart(
                    point = screenOffset,
                    center = center,
                    scale = transformState.scale,
                    offset = transformState.offset,
                    headingDeg = if (transformState.northUp) headingDegrees else 0f,
                    northUp = transformState.northUp,
                )
            val hit =
                plots
                    .filter { it.animAlpha > 0.5f }
                    .minByOrNull { plot ->
                        val dx = chartPoint.x - plot.x
                        val dy = chartPoint.y - plot.y
                        dx * dx + dy * dy
                    }
            if (hit != null) {
                val dx = chartPoint.x - hit.x
                val dy = chartPoint.y - hit.y
                if (dx * dx + dy * dy <= touchRadius * touchRadius) {
                    onSatelliteClick(hit.satellite)
                }
            }
        },
    )
}
```

**Conflict note:** `detectTransformGestures` and `detectTapGestures` on the same node can fight. Prefer one `pointerInput` that uses `awaitEachGesture` / `detectTapGestures` only for double-tap+tap, and transform on another modifier — Compose usually allows both if double-tap uses the tap detector. If double-tap never fires, use `detectTransformGestures` for pinch/pan only and a separate `detectTapGestures` sibling `pointerInput` as shown (known pattern).

- [ ] **Step 6: Empty state**

Empty text should still draw **inside** the transform (or outside — either OK). Prefer **outside** transform so it stays readable when zoomed:

Draw empty state **after** `withTransform` block if `plottableSatellites.isEmpty()`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/gpstest/ui/screens/skychart/SkyChartView.kt
git commit -m "feat: ✨ wire zoom pan animation and inverse hit-test into sky chart view"
```

---

### Task 6: Screen Wiring + North-Up Toggle UI

**Files:**

- Modify: `app/src/main/java/com/example/gpstest/ui/screens/skychart/SkyChartScreen.kt`

**Interfaces:**

- Consumes: `rememberSkyChartTransformState`, `rememberAnimatedSatellites`, `rememberCompassHeading`, updated `SkyChartView`.
- Produces: North-up `IconButton` top-left over chart; passes filtered satellites through animator.

- [ ] **Step 1: Add imports**

```kotlin
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
```

(`Icons.Filled.Explore` / `Icons.Outlined.Explore` need `material-icons-extended` — already in `app/build.gradle.kts`.)

- [ ] **Step 2: Update `SkyChartContent`**

```kotlin
@Composable
private fun SkyChartContent(
    satellites: List<GnssSatellite>,
    visibleConstellations: Set<Constellation>,
    onConstellationToggle: (Constellation) -> Unit,
    onSatelliteClick: (GnssSatellite) -> Unit,
    modifier: Modifier = Modifier,
) {
    val transformState = rememberSkyChartTransformState()
    val animated = rememberAnimatedSatellites(satellites)
    val heading = rememberCompassHeading(enabled = transformState.northUp)

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
        ) {
            SkyChartView(
                satellites = animated,
                transformState = transformState,
                headingDegrees = heading,
                onSatelliteClick = onSatelliteClick,
                modifier = Modifier.fillMaxSize(),
            )

            IconButton(
                onClick = { transformState.setNorthUp(!transformState.northUp) },
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Icon(
                    imageVector =
                        if (transformState.northUp) {
                            Icons.Filled.Explore
                        } else {
                            Icons.Outlined.Explore
                        },
                    contentDescription =
                        if (transformState.northUp) {
                            "关闭北向上"
                        } else {
                            "开启北向上"
                        },
                    tint =
                        if (transformState.northUp) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        SkyChartLegend(
            visibleConstellations = visibleConstellations,
            onConstellationToggle = onConstellationToggle,
        )
    }
}
```

- [ ] **Step 3: Confirm Success branch still filters before content**

Keep existing:

```kotlin
val filteredSatellites = allSatellites.filter { it.constellation in visibleConstellations }
SkyChartContent(satellites = filteredSatellites, ...)
```

Filtered list is the animator input — hidden constellations never enter tracks (spec).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/gpstest/ui/screens/skychart/SkyChartScreen.kt
git commit -m "feat: ✨ wire sky chart phase2 state and north-up toggle"
```

---

### Task 7: Verification

**Files:**

- No source changes expected (fix only if ktlint/tests fail).

**Interfaces:**

- Consumes: Tasks 1–6 complete.
- Produces: green ktlint, unit tests, debug APK.

- [ ] **Step 1: ktlint**

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21"
./gradlew ktlintCheck
```

Expected: `BUILD SUCCESSFUL`. Fix any ktlint issues (imports order, wrapping) inline.

- [ ] **Step 2: Unit tests**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL`. New tests in `SkyChartMathTest` and `SkyChartTransformStateTest` pass; existing tests unchanged.

- [ ] **Step 3: Debug APK**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Manual checklist (device)**

1. Pinch zoom 1×–4×; double-tap → 1×; at 1× cannot pan.
2. Satellites move smoothly; appear/disappear fade.
3. North-up ON: rotate device, true north (N label) stays top; OFF: chart fixed.
4. Combined: zoom/pan + select satellite + north-up.
5. Leave page / background: no continuous rotation-vector registration (profiler or log if needed).
6. Double-tap does **not** turn off north-up.
7. No sensor device: toggle still works, heading stays 0, no crash/toast.

- [ ] **Step 5: Final commit if fixes needed**

```bash
git add -u
git commit -m "fix: 🐛 sky chart phase2 verification fixes"
```

Only if Step 1–3 required code changes.

---

## Self-Review

### Spec coverage

| Spec requirement                                           | Task                   |
| ---------------------------------------------------------- | ---------------------- |
| Pinch zoom 1–4×, pan, double-tap reset                     | Task 2 + 5             |
| Position animation 400 ms az/el, 300 ms fade, shortest-arc | Task 1 + 3             |
| North-up toggle default off, top-left                      | Task 4 + 6             |
| Canvas order translate→rotate→offset→scale                 | Task 5                 |
| Inverse hit-test, alpha > 0.5                              | Task 1 + 5             |
| Sensor only when northUp + foreground                      | Task 4                 |
| Sensor unavailable → heading 0, no error UI                | Task 4                 |
| No screenshot, no VM/data/permission changes               | File map + constraints |
| Unit: clamp/reset, shortest-arc, inverse round-trip        | Task 1 + 2             |
| Manual + ktlint/test/assemble                              | Task 7                 |

### Placeholder scan

No TBD/TODO remaining. Zoom-anchor note has a documented fallback if pinch-center math fails manual check.

### Type consistency

- `AnimatedSatellite` produced by Task 3, consumed by Task 5 `SkyChartView`.
- `SkyChartTransformState` produced by Task 2, consumed by Tasks 5–6.
- `rememberCompassHeading(enabled)` produced by Task 4, consumed by Task 6.
- Math helpers from Task 1 used by Tasks 2, 3, 4, 5.

### Known implementer risks

1. **Gesture detector conflict** between transform and double-tap — dual `pointerInput` pattern documented.
2. **Rotation sign** (math vs Compose Y-down) — Task 1 round-trip tests + Task 5 visual check; flip consistently if inverted.
3. **`applyZoom` anchor** may simplify if flaky — clamp/reset tests still pass.
4. **`WindowManager.defaultDisplay` deprecation** on API 30+ — acceptable for minSdk 24 project; if compile warns, use `context.display` on API 30+ with `@Suppress` or version branch.
