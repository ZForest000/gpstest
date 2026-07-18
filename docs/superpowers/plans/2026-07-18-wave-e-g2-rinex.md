# Wave E: G2 RINEX 3.x Observation Export

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans.

**Goal:** Export recorded GNSS observations as RINEX 3.04 `.obs` and share via FileProvider (same pattern as NMEA/History export).

**Architecture:** Pure `RinexWriter` domain module builds text; data layer accumulates epochs; UI trigger on History or dedicated export entry. Antenna header from G3 when available.

**Tech Stack:** Kotlin string/stream writer, FileProvider, existing export helpers pattern (`NmeaExportHelper` / `HistoryExportHelper`).

## Global Constraints

- Master roadmap.
- Depends on **Wave C (G3)** for antenna header fields; approximate position from last `LocationInfo`.
- Observation types MVP: C1C (pseudorange if available), L1C (ADR if available), D1C (doppler if available) — document missing as blanks per RINEX rules.
- Do not invent observations; omit empty fields correctly.
- Unit tests for header + one epoch golden string.

## File Map

| Path                                  | Action                   |
| ------------------------------------- | ------------------------ |
| `domain/export/RinexWriter.kt`        | Create                   |
| `domain/export/RinexEpoch.kt`         | Create models            |
| `data/local/RinexSessionRecorder.kt`  | Create accumulate epochs |
| `data/local/RinexExportHelper.kt`     | FileProvider share       |
| UI entry (HistoryScreen or Satellite) | Export button            |
| `file_paths.xml`                      | cache path if needed     |
| `RinexWriterTest.kt`                  | Golden tests             |

---

### Task 1: RinexWriter pure + tests

- [ ] Header: RINEX VERSION / TYPE, PGM / RUN BY / DATE, MARKER, OBSERVER / AGENCY, REC / TYPE / VERS, ANT / TYPE, APPROX POSITION XYZ, ANTENNA: DELTA H/E/N (from G3), SYS / # / OBS TYPES, TIME OF FIRST OBS, END OF HEADER
- [ ] Epoch lines + observations
- [ ] Golden test fixtures
- [ ] Commit `feat: ✨ add RINEX 3 observation writer`

### Task 2: Session recorder

- [ ] Buffer epochs while listening (configurable max duration/count)
- [ ] Map `GnssSatellite` + clock to RINEX epoch
- [ ] Commit `feat: ✨ record RINEX observation sessions`

### Task 3: Export helper + UI

- [ ] Write cache file `rinex_YYYYMMDD_HHMMSS.obs`
- [ ] Share ACTION_SEND
- [ ] UI button + strings
- [ ] Commit `feat: ✨ share RINEX obs via FileProvider`

### Task 4: Verify + TODO

- [ ] Tests + assembleDebug
- [ ] Mark G2 ✅
- [ ] Commit `docs: 📝 mark G2 RINEX complete`
