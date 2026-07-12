# Sky Chart Interaction Phase 1 Design

## Goal

Complete U2 phase 1 for the Sky Chart screen by adding always-visible SVID labels and constellation visibility toggles in the existing legend.

## Scope

This phase includes:

- Draw each plotted satellite's `svid` beside its point on the sky chart.
- Allow every constellation legend item to independently toggle that constellation visible or hidden.
- Keep all constellations visible when the user first opens the Sky Chart page.
- Keep filter state local to the current Sky Chart page instance so leaving and returning resets all constellations to visible.

This phase excludes:

- Label collision avoidance or selective label rendering.
- Zoom, pan, rotation, animation, orientation, sharing, persistence, and ViewModel state changes.
- Domain, repository, data source, or navigation architecture changes.

## Architecture

Filtering state belongs in `SkyChartScreen` because it is transient UI-only state. `SkyChartScreen` owns a `Set<Constellation>` of visible constellations, filters the combined satellite list before drawing, and passes toggle state to `SkyChartLegend`.

`SkyChartView` remains a pure drawing and hit-testing component. It receives only satellites that should be visible, so hidden constellations produce no dot, no SVID label, and no tap target.

`SkyChartLegend` keeps the existing passive status entries for `定位中` and `可见`. Constellation entries become clickable toggles; hidden entries remain visible in the legend with reduced opacity so users can restore them.

## Component Changes

- `SkyChartScreen.kt`: add `remember { mutableStateOf(Constellation.entries.toSet()) }`, derive `filteredSatellites`, and pass `visibleConstellations` plus `onConstellationToggle` to `SkyChartLegend`.
- `SkyChartView.kt`: draw `satellite.svid.toString()` near each satellite point using the existing `TextMeasurer` and `drawText` APIs. Use constellation color and the same used-in-fix alpha semantics as satellite points.
- `SkyChartLegend.kt`: add parameters for visible constellations and toggle callback. Make only constellation entries clickable. Apply lower alpha to hidden constellation entries.

## User Experience

On entry, the chart looks like the existing chart plus SVID labels. Tapping a constellation label in the legend hides all satellites from that constellation. Tapping it again restores those satellites. The status legend entries remain informational and do not respond to taps.

If all plotted satellites are hidden, the chart shows the existing empty-state text because the filtered plottable list is empty.

## Verification

Automated checks:

- `./gradlew ktlintCheck`
- `./gradlew test`
- `./gradlew assembleDebug`

Manual checks:

- SVID labels appear next to all plotted satellite points.
- Each constellation legend item independently hides and restores only that constellation.
- Hidden satellites are not drawn, not labeled, and cannot be selected by tapping their old position.
- Leaving and re-entering the Sky Chart page resets all constellations to visible.

## Self-Review

- Placeholder scan: no placeholders remain.
- Consistency check: filtering is screen-local and drawing receives already-filtered data throughout the design.
- Scope check: phase 1 remains limited to labels and constellation toggles.
- Ambiguity check: label overlap is explicitly allowed and filter persistence is explicitly excluded.
