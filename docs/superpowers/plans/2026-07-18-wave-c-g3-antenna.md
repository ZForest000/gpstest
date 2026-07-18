# Wave C: G3 GnssAntennaInfo

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans.

**Goal:** Collect and display GNSS antenna phase center information (API 31+) for diagnostics and future RINEX headers.

**Architecture:** Follow G6 capabilities pattern: domain model → DataSource callback/query → Repository → ViewModel StateFlow → Compose card. API &lt; 31 → null, UI hidden.

**Tech Stack:** Android `LocationManager.registerAntennaInfoCallback` (API 31+), Kotlin Flow, Compose.

## Global Constraints

- Master roadmap constraints.
- API 31+ only for listener; no crash on older devices.
- ProGuard already keeps `GnssAntennaInfo` — verify still present after E1.
- Chinese strings; no new deps.

---

## File Map

| Path                                           | Action                                                           |
| ---------------------------------------------- | ---------------------------------------------------------------- |
| `domain/model/AntennaInfo.kt`                  | Create domain snapshot (PCO x/y/z mm, carrier frequencies, etc.) |
| `data/source/GnssDataSource.kt` / `Impl`       | Add antenna info Flow or one-shot query                          |
| `domain/repository/GnssRepository.kt` / `Impl` | Pass-through                                                     |
| `viewmodel/SatelliteViewModel.kt`              | `antennaInfo: StateFlow<AntennaInfo?>`                           |
| `ui/components/AntennaInfoCard.kt`             | Create                                                           |
| `SatelliteListScreen` or Clock area            | Show card when non-null                                          |
| `strings.xml`                                  | Labels                                                           |
| `domain/model/AntennaInfoTest.kt`              | Mapping/unit tests for pure mappers                              |

---

### Task 1: Domain model + mapper tests

- [ ] Define `AntennaInfo` with fields needed for UI + RINEX later: carrier frequency Hz, phase center offset (x/y/z mm), optional phase center variation summary.
- [ ] Pure mapper from platform types behind interface for testability where possible (or map in data layer with golden values in test of mapper object).
- [ ] Commit `feat: ✨ add AntennaInfo domain model`

### Task 2: DataSource registration

- [ ] API 31+: `registerAntennaInfoCallback(executor, callback)`; emit list/first antenna into Flow via `callbackFlow` or merge into existing GNSS flow as optional field on `GnssData`.
- [ ] Prefer **separate** `Flow<List<AntennaInfo>>` or `StateFlow` updated by callback — avoids bloating 250ms sample path.
- [ ] Unregister on close.
- [ ] Commit `feat: ✨ register GnssAntennaInfo callback`

### Task 3: Repository + ViewModel + UI

- [ ] Expose through repository; ViewModel collects on init.
- [ ] `AntennaInfoCard` Chinese labels; hide if null/empty.
- [ ] Commit `feat: ✨ show antenna info card on satellite screen`

### Task 4: Verify + TODO

- [ ] `./gradlew ktlintCheck test assembleDebug`
- [ ] Mark G3 ✅ in TODO.md
- [ ] Commit `docs: 📝 mark G3 complete`

## Notes for Wave E

RINEX header will read the same `AntennaInfo` model — keep field names stable.
