# Wave D: G1 Full Local Position Solution (GPS MVP)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans.

**Goal:** Produce a **real** local least-squares position from derived pseudoranges + satellite ECEF (from ephemeris), and show residual vs Android fused location. GPS L1 C/A MVP first.

**Architecture:** Extend existing pure domain cores (`PseudorangeCalculator`, `PositionSolver`) with:

1. Navigation message capture (`GnssNavigationMessage`)
2. Ephemeris parse/store (GPS LNAV subframe 1–3 minimum)
3. Satellite ECEF + clock at transmit time
4. Observation corrections (sat clock, simple troposphere/ionosphere or documented stubs with flags)
5. Wire into ViewModel → residual UI card

**Hard rule:** Never call non-existent `getPseudorangeMeters()`. Use `PseudorangeCalculator` output only when status is valid.

**Tech Stack:** Existing domain solvers + Android NavMessage API 24+, optional external ephemeris file later.

## Global Constraints

- Master roadmap.
- GPS-only MVP for first shippable vertical slice; multi-GNSS is follow-up (out of MVP).
- All math pure Kotlin in `domain/`; golden tests with published sample values.
- If device cannot provide NavMessage, UI must show clear "设备不支持/无电文" — never fake a fix.
- Corrections that are approximated must be labeled in UI (`改正: 简化`).

---

## Stages (tasks groups)

### Task Group D1 — NavMessage capture (feeds G4 lite)

**Files:** `GnssDataSource`/`Impl`, domain `NavMessageFrame`, optional ring buffer store

- [ ] Register `registerGnssNavigationMessageCallback`
- [ ] Map to domain model: svid, type, status, messageId, submessageId, data `ByteArray`
- [ ] Flow of frames; unit test mapper with synthetic bytes
- [ ] Commit `feat: ✨ capture GNSS navigation messages`

### Task Group D2 — GPS LNAV ephemeris parser

**Files:** `domain/ephemeris/GpsLnavParser.kt`, `GpsEphemeris.kt`, tests with known ICD-GPS-200 sample

- [ ] Parse subframes 1–3 into orbital elements + clock params
- [ ] Ephemeris store: latest valid set per SVID
- [ ] Golden tests from public sample (document source in test comments)
- [ ] Commit `feat: ✨ parse GPS LNAV ephemeris`

### Task Group D3 — Satellite ECEF propagator

**Files:** `domain/ephemeris/GpsSatellitePosition.kt`

- [ ] Compute ECEF + sat clock bias at GPS time of transmission
- [ ] Unit tests against published ECEF reference points
- [ ] Commit `feat: ✨ compute GPS satellite ECEF from ephemeris`

### Task Group D4 — Corrections + observation builder

**Files:** `domain/util/ObservationBuilder.kt` (name flexible)

- [ ] Input: `GnssSatellite` with valid pseudorange + ephemeris position
- [ ] Apply sat clock; optional simple Klobuchar/iono stub or zero with flag; Saastamoinen or simple zenith troposphere
- [ ] Output: `List<PseudorangeObservation>` for `PositionSolver`
- [ ] Commit `feat: ✨ build corrected pseudorange observations`

### Task Group D5 — Runtime wiring

**Files:** Repository/ViewModel orchestration

- [ ] When ≥4 valid GPS observations + ephemeris: call `PositionSolver.solve`
- [ ] Expose `PositionSolution` + residual vs `LocationInfo` (ENU or horizontal meters)
- [ ] Commit `feat: ✨ wire local position solution into ViewModel`

### Task Group D6 — Residual UI

**Files:** `LocalPositionCard.kt`, strings, SatelliteListScreen

- [ ] Show ECEF/LLA, clock bias, RMS residual, status enum, Δ vs system fix
- [ ] Hide when insufficient data
- [ ] Commit `feat: ✨ show local vs system position residual card`

### Task Group D7 — Verify + TODO

- [ ] Full test suite + assembleDebug
- [ ] Mark G1 stage 3+ ✅ with honest scope notes (GPS MVP)
- [ ] Commit `docs: 📝 mark G1 full MVP complete`

## Explicit Non-Goals (this wave)

- Full multi-constellation ISB
- RTK / PPP
- Complete G4 hex UI browser (Wave F)
- Claiming cm-level accuracy

## Risk Notes

- Many consumer devices deliver sparse/empty NavMessage — plan UI for empty state.
- Fallback path (optional later task): download external ephemeris (NASA/CDDIS) — only if human prioritizes after MVP.
