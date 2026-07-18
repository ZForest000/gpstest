# Wave F: Polish (U6, G4, E7)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans.

**Goal:** Visual charts (U6), full navigation-message browser (G4), and Room migration for history (E7). Rolling priority — ship independently.

**Architecture:** U6 reuses Canvas patterns from `SignalChart` / `HistoryTrendChart` (no chart library unless human insists). G4 builds on Wave D NavMessage capture. E7 replaces JSON blob DataStore with Room + one-shot migration.

## Global Constraints

- Master roadmap.
- Prefer Canvas over new deps for U6.
- E7 must migrate existing snapshots without data loss.
- G4 is large — ship read-only hex + basic field decode for GPS LNAV first.

---

## Part F1 — U6 Signal bar + DOP trend

### Task F1.1: dopHistory in ViewModel

- [ ] Ring buffer 60 `DopInfo` samples in `SatelliteViewModel`
- [ ] Unit test buffer behavior if extracted pure
- [ ] Commit `feat: ✨ track DOP history buffer`

### Task F1.2: SignalBarChart + DopTrendChart

- [ ] `SignalBarChart.kt` — CN0 bars by satellite or constellation
- [ ] `DopTrendChart.kt` — PDOP/HDOP/VDOP lines (reuse HistoryTrendChart style)
- [ ] Wire into SatelliteListScreen (collapsible section)
- [ ] Commit `feat: ✨ add signal bar and DOP trend charts`

### Task F1.3: TODO U6 ✅

---

## Part F2 — G4 Navigation message UI

### Task F2.1: NavMessage store + screen

- [ ] Ring buffer of recent frames (hex dump)
- [ ] `NavMessageScreen` + drawer route
- [ ] Filter by constellation/svid/type
- [ ] Commit `feat: ✨ add navigation message monitor screen`

### Task F2.2: Optional LNAV field decode panel

- [ ] Reuse D2 parser for human-readable subframe summary
- [ ] Commit `feat: ✨ decode GPS LNAV fields in nav message UI`

### Task F2.3: TODO G4 ✅

---

## Part F3 — E7 Room history

### Task F3.1: Room schema

- [ ] Entities: snapshot + satellite rows
- [ ] DAO: insert, query by time range, delete, clear
- [ ] Database module + manual DI
- [ ] Commit `feat: ✨ add Room schema for satellite history`

### Task F3.2: Migration from DataStore JSON

- [ ] On first launch, read old key, insert Room, mark migrated
- [ ] Switch `SatelliteHistoryRepositoryImpl` to Room
- [ ] Keep repository interface stable for UI
- [ ] Commit `feat: ✨ migrate history from DataStore JSON to Room`

### Task F3.3: Tests + TODO E7 ✅

- [ ] DAO tests (in-memory Room) if androidTest/Robolectric available; else repository tests with fake DAO
- [ ] Full verify
- [ ] Commit `docs: 📝 mark U6 G4 E7 complete`

## Priority order inside Wave F

1. U6 (user-visible, medium effort)
2. G4 (depends on D1 capture)
3. E7 (only when history scale hurts — after U3 done it is optional)
