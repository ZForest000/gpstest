# Sky Chart Interaction Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add always-visible SVID labels and page-local constellation visibility filtering to the existing Sky Chart screen.

**Architecture:** Keep interaction state in `SkyChartScreen` as transient Compose state. Pass filtered satellites into `SkyChartView`; pass visible constellation state and a toggle callback into `SkyChartLegend`. Do not change ViewModel, domain, repository, or persistence layers.

**Tech Stack:** Kotlin 2.1.0, Jetpack Compose Material 3, Canvas drawing with existing `TextMeasurer` and `drawText` APIs.

## Global Constraints

- Scope is limited to U2 phase 1: SVID labels and constellation filtering only.
- All constellations are visible by default.
- Filter state resets after leaving the Sky Chart page.
- Labels always render for every plotted satellite and may overlap.
- No new dependencies or UI test framework.
- Verification commands: `./gradlew ktlintCheck`, `./gradlew test`, `./gradlew assembleDebug`.

---

### Task 1: Screen-Owned Filtering State

**Files:**

- Modify: `app/src/main/java/com/example/gpstest/ui/screens/skychart/SkyChartScreen.kt`

**Interfaces:**

- Consumes: `Constellation.entries`, `GnssSatellite.constellation`.
- Produces: `SkyChartContent(satellites: List<GnssSatellite>, visibleConstellations: Set<Constellation>, onConstellationToggle: (Constellation) -> Unit, onSatelliteClick: (GnssSatellite) -> Unit, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Add constellation state imports**

Add `import com.example.gpstest.domain.model.Constellation`.

- [ ] **Step 2: Add page-local visible constellation state**

Inside `SkyChartScreen`, after `selectedSatellite`, add:

```kotlin
var visibleConstellations by remember {
    mutableStateOf(Constellation.entries.toSet())
}
```

- [ ] **Step 3: Filter the satellites before rendering**

In the `SatelliteUiState.Success` branch, derive:

```kotlin
val allSatellites = state.usedInFix + state.visibleOnly + state.searching
val filteredSatellites = allSatellites.filter { it.constellation in visibleConstellations }
SkyChartContent(
    satellites = filteredSatellites,
    visibleConstellations = visibleConstellations,
    onConstellationToggle = { constellation ->
        visibleConstellations =
            if (constellation in visibleConstellations) {
                visibleConstellations - constellation
            } else {
                visibleConstellations + constellation
            }
    },
    onSatelliteClick = { selectedSatellite = it },
)
```

- [ ] **Step 4: Extend `SkyChartContent` signature and legend call**

Add the `visibleConstellations` and `onConstellationToggle` parameters, then call:

```kotlin
SkyChartLegend(
    visibleConstellations = visibleConstellations,
    onConstellationToggle = onConstellationToggle,
)
```

---

### Task 2: SVID Labels

**Files:**

- Modify: `app/src/main/java/com/example/gpstest/ui/screens/skychart/SkyChartView.kt`

**Interfaces:**

- Consumes: filtered `satellites: List<GnssSatellite>` from `SkyChartScreen`.
- Produces: Canvas text labels based on `GnssSatellite.svid`.

- [ ] **Step 1: Draw labels after each satellite point**

Inside the existing `for (plot in plots)` loop, after drawing the filled and stroked circles, add:

```kotlin
val labelResult =
    textMeasurer.measure(
        text = AnnotatedString(sat.svid.toString()),
        style = TextStyle(fontSize = 10.sp),
    )
drawText(
    textLayoutResult = labelResult,
    color = color.copy(alpha = alpha),
    topLeft =
        Offset(
            plot.x + plot.visualRadius + with(density) { 3.dp.toPx() },
            plot.y - plot.visualRadius - labelResult.size.height / 2f,
        ),
)
```

- [ ] **Step 2: Keep hit testing unchanged**

Confirm `plots` is still built only from `satellites` passed to `SkyChartView`, so hidden satellites are excluded from drawing and nearest-hit selection.

---

### Task 3: Clickable Constellation Legend

**Files:**

- Modify: `app/src/main/java/com/example/gpstest/ui/screens/skychart/SkyChartLegend.kt`

**Interfaces:**

- Consumes: `visibleConstellations: Set<Constellation>` and `onConstellationToggle: (Constellation) -> Unit`.
- Produces: clickable constellation legend entries with reduced opacity when hidden.

- [ ] **Step 1: Add imports**

Add imports for `androidx.compose.foundation.clickable` and `androidx.compose.ui.draw.alpha`.

- [ ] **Step 2: Update `SkyChartLegend` signature**

Use:

```kotlin
fun SkyChartLegend(
    visibleConstellations: Set<Constellation>,
    onConstellationToggle: (Constellation) -> Unit,
    modifier: Modifier = Modifier,
)
```

- [ ] **Step 3: Render constellation entries from enum values**

Replace the `items` mapping and loop with a loop over `Constellation.entries`:

```kotlin
for (constellation in Constellation.entries) {
    val isVisible = constellation in visibleConstellations
    LegendItem(
        dotColor = constellation.color,
        filled = true,
        label = constellation.shortName,
        enabled = isVisible,
        onClick = { onConstellationToggle(constellation) },
    )
}
```

- [ ] **Step 4: Extend `LegendItem`**

Update `LegendItem` to accept `enabled: Boolean = true` and `onClick: (() -> Unit)? = null`. Apply `alpha(if (enabled) 1f else 0.35f)` and add `clickable` only when `onClick` is not null.

---

### Task 4: Verification

**Files:**

- No source file changes expected.

**Interfaces:**

- Consumes: completed Tasks 1-3.
- Produces: verified build and test result.

- [ ] **Step 1: Run ktlint**

Run: `./gradlew ktlintCheck`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run unit tests**

Run: `./gradlew test`

Expected: `BUILD SUCCESSFUL` with all existing JVM tests passing.

- [ ] **Step 3: Build debug APK**

Run: `./gradlew assembleDebug`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Manual UI checks**

On a device or emulator with location data, verify SVID labels, independent constellation hide/restore, hidden satellite non-selectability, and reset after leaving and returning to the Sky Chart page.

## Self-Review

- Spec coverage: Tasks 1-3 cover page-local state, filtering, SVID labels, and clickable legend toggles; Task 4 covers verification.
- Placeholder scan: no placeholders remain.
- Type consistency: `Constellation`, `GnssSatellite`, and Compose function signatures match the existing codebase and this plan.
