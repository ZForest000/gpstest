# Sky Chart Interaction Phase 2 Design

## Goal

Complete the remaining U2 interaction scope for the Sky Chart: pinch zoom / pan, satellite position animation, and optional north-up compass rotation. Phase 1 (SVID labels + constellation filter) is already shipped and out of scope here.

## Scope

### In scope

1. **Pinch zoom + pan + double-tap reset** on the sky chart canvas
2. **Satellite position animation** (lerp az/el + fade in/out)
3. **North-up toggle** (default OFF) with device heading from `TYPE_ROTATION_VECTOR`

### Out of scope

- Screenshot / share
- Label collision avoidance
- Fling inertia, two-finger rotate gesture
- ViewModel / Repository / domain / data-layer changes
- New Android permissions or third-party dependencies
- Persisting transform / north-up / filter across process death beyond `remember` / config change
- Heading-up mode (device heading at top) — only true-north-up is supported

## Architecture

State and sensor logic stay in the Compose UI layer. `SkyChartView` remains a pure drawing + hit-test component.

### New files

| File                                          | Responsibility                                                                               |
| --------------------------------------------- | -------------------------------------------------------------------------------------------- |
| `ui/components/SkyChartTransformState.kt`     | scale, offset, northUp; clamp/reset helpers; inverse transform for hit-test                  |
| `ui/components/CompassHeadingSource.kt`       | register/unregister `TYPE_ROTATION_VECTOR` only while northUp is on; expose smoothed heading |
| `ui/components/AnimatedSatellitePositions.kt` | key satellites by constellation+svid; lerp az/el; fade appear/disappear                      |

### Modified files

| File                            | Change                                                                                     |
| ------------------------------- | ------------------------------------------------------------------------------------------ |
| `ui/components/SkyChartView.kt` | apply canvas transform; draw animated positions with alpha; inverse hit-test               |
| `ui/screens/SkyChartScreen.kt`  | own transform state, compass source lifecycle, animated positions; north-up toggle overlay |

### Unchanged

- `SkyChartLegend.kt` — constellation toggles stay as phase 1
- `SatelliteViewModel`, repositories, data sources — no changes
- AndroidManifest — no new permissions (`TYPE_ROTATION_VECTOR` needs none)

### Data flow

```
Gnss satellites (filtered by constellation)
        │
        ▼
AnimatedSatellitePositions  ──►  animated (az, el, alpha) list
        │
        ▼
SkyChartView
  ◄── SkyChartTransformState (scale, offset, northUp)
  ◄── heading (from CompassHeadingSource when northUp)

Gestures ──► SkyChartTransformState
North-up toggle ──► northUp ──► CompassHeadingSource on/off
```

## Transform & Gestures

### State model (`SkyChartTransformState`)

| Field     | Type      | Default       | Notes                              |
| --------- | --------- | ------------- | ---------------------------------- |
| `scale`   | `Float`   | `1f`          | clamped to `[1f, 4f]`              |
| `offset`  | `Offset`  | `Offset.Zero` | pan in canvas units after rotation |
| `northUp` | `Boolean` | `false`       | independent of scale/offset        |

### Gesture rules

- **Pinch zoom**: 1×–4×; zoom anchored at pinch center
- **Single-finger pan**: allowed only when `scale > 1`; pan is clamped so chart content stays usable; at `scale == 1` pan is forced to `Offset.Zero`
- **Double-tap**: resets `scale` and `offset` only; does **not** change `northUp`
- **Not supported**: fling, two-finger rotate, multi-touch rotate of the chart

### Canvas transform order

Applied in this order (center-relative):

1. `translate(center)`
2. if `northUp`: `rotate(-heading)` so true north stays at the top of the screen
3. `translate(offset)`
4. `scale(scale)`
5. draw rings, labels, satellites (existing polar math: azimuth 0° = north at top of chart space)

### Coordinate systems

- **Chart space**: existing polar plot; N at top of chart, E right, S bottom, W left
- **Screen space**: after full transform including north-up rotation
- Gestures are interpreted in **post-rotation screen coordinates**
- Hit-test maps tap from screen → chart space via **inverse** of the same transform chain
- The N label always marks **true north** in chart space (it rotates with the canvas when north-up is on)

## Position Animation

### Keying

Satellite identity key: `constellation` + `svid` (stable across GNSS updates).

### Motion

| Event                        | Behavior                                                          | Duration    |
| ---------------------------- | ----------------------------------------------------------------- | ----------- |
| Position update (same key)   | Linear lerp of azimuth & elevation                                | **400 ms**  |
| Azimuth wrap                 | Shortest-arc interpolation (e.g. 350° → 10° goes +20°, not −340°) | same 400 ms |
| Appear (new key)             | Fade in alpha 0 → 1                                               | **300 ms**  |
| Disappear (key removed)      | Keep last position as ghost; fade alpha 1 → 0; then drop          | **300 ms**  |
| Full list clear then restore | Fade all out, then fade new keys in; **no** cross-key continuity  | as above    |

### Implementation constraints

- Driven in Compose UI only (`withFrameMillis` and/or `Animatable`)
- Linear easing only — no springs
- No motion trails
- Hit-test only satellites with **alpha > 0.5**
- Filtered-out constellations never enter the animator (filtering happens upstream in `SkyChartScreen`)

## North-Up Compass

### Semantics

When `northUp == true`, the chart rotates so **true north stays at the top of the screen**, matching the existing N label. This is **not** heading-up (device forward at top).

### Sensor

- Source: `Sensor.TYPE_ROTATION_VECTOR` only
- Register **only** while `northUp == true` **and** the Sky Chart is in the foreground / resumed
- Unregister immediately when: north-up turns off, user leaves the screen, or app goes to background
- On resume: re-register only if `northUp` is still true
- Heading range: device azimuth vs true north, `0f..360f` degrees
- Smoothing: shortest-arc update + low-pass with ~**100–150 ms** time constant
- Canvas uses `rotate(-heading)`

### Sensor unavailable

- Keep `northUp` toggled on
- Heading fixed at `0f` (chart looks like north-up with device assumed aligned to north)
- No toast, snackbar, or crash

### Toggle UI

- Top-left icon overlaid on the sky chart canvas area
- Default: OFF
- Double-tap reset does not touch this toggle

## Error & Lifecycle Boundaries

| Case                         | Behavior                                                                                                                    |
| ---------------------------- | --------------------------------------------------------------------------------------------------------------------------- |
| No rotation sensor           | heading = 0°, northUp may stay on, no error UI                                                                              |
| Leave Sky Chart / background | unregister sensor immediately                                                                                               |
| Resume with northUp still on | re-register sensor                                                                                                          |
| scale returns to 1×          | force offset = Zero                                                                                                         |
| Config change (rotation)     | transform + northUp retained via `remember` / saved state as implemented; animation may rebuild from current satellite list |
| No new permissions           | confirmed — rotation vector needs none                                                                                      |
| No ViewModel changes         | confirmed                                                                                                                   |

## Testing

### Unit tests (JUnit 4, no mocks, domain-style pure functions)

1. **`SkyChartTransformState`**
    - scale clamps to `[1, 4]`
    - pan forced to zero at scale 1
    - `resetScaleAndOffset()` clears scale/offset but leaves `northUp` unchanged
2. **Azimuth shortest-arc** helper used by animation
3. **Inverse transform hit-test** round-trip (screen point → chart → screen) within float epsilon

### Not unit-tested

- Compose multi-touch gestures
- Hardware `TYPE_ROTATION_VECTOR` readings
- Frame-by-frame `withFrameMillis` animation
- Screenshots / visual regression

### Manual verification

1. Pinch zoom 1×–4×; double-tap returns to 1×; pan disabled at 1×
2. Satellite moves smoothly; appear/disappear fades
3. North-up ON: true north stays top while rotating device; OFF: chart fixed
4. Combined: zoom/pan + select satellite + north-up
5. Leave page / background: sensor stops (no continuous sensor use)

### Build checks

```bash
./gradlew ktlintCheck
./gradlew test
./gradlew assembleDebug
```

## Self-Review

- **Placeholder scan**: no TBD/TODO left.
- **Consistency**: canvas order, gesture coords, inverse hit-test, and N-label semantics agree across sections.
- **Scope**: zoom/pan + animation + north-up only; screenshot and ViewModel changes excluded.
- **Ambiguity**: north-up = true north fixed at top (not heading-up); double-tap does not reset northUp; sensor missing → heading 0, no error UI.

## Relation to prior specs

- Builds on `2026-03-28-sky-chart-design.md` (base polar chart)
- Builds on `2026-07-12-sky-chart-interaction-design.md` (phase 1: SVID labels + constellation filter)
- Does not reopen phase 1 decisions
