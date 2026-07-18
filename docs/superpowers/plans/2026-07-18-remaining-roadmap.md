# Remaining Work Master Roadmap

> **For agentic workers:** Execute waves in order via `superpowers:subagent-driven-development`. Each wave has its own plan under `docs/superpowers/plans/`. Do not start Wave N+1 until Wave N is complete (or explicitly skipped by the human).

**Goal:** Deliver all remaining TODO.md items (U4–U6, G1–G4, E1–E3, E5–E8) as independently shippable waves without vibe-coding.

**Architecture:** Clean MVVM, unidirectional data flow, manual DI (no Hilt). Domain pure logic first with JUnit tests; Android framework only in `data/` and `ui/`. Interface + `Impl` pairing throughout.

**Tech Stack:** Kotlin 2.1.0 · Jetpack Compose BOM 2024.10.01 · Material 3 · Coroutines/Flow · DataStore · WorkManager · OkHttp · JUnit 4 (+ mockk/turbine where already present)

## Source of Truth

- Feature backlog: `TODO.md` (verified 2026-07-15 baseline + later NMEA/history commits)
- Project conventions: `AGENTS.md`
- This roadmap locks **order, dependencies, and out-of-scope** decisions

## Global Constraints (all waves)

1. **Clean MVVM + manual DI** — dependencies built in `MainActivity` / Worker; pass via `ViewModelProvider.Factory`. No Hilt/Dagger/Koin.
2. **Interface + Impl** — DataSource / Repository / Downloader / FileHandler stay paired; never skip the interface.
3. **Chinese user-facing strings** via `res/values/strings.xml` (no new hard-coded Chinese/English in Compose except temporary during E5 extraction).
4. **Conventional commits with emoji** per AGENTS.md: `feat: ✨` / `fix: 🐛` / `test: ✅` / `chore: 🔧` / `docs: 📝` / `refactor: ♻️` / `perf: ⚡`.
5. **No fake GNSS APIs** — Android has **no** `GnssMeasurement.getPseudorangeMeters()`. Pseudorange is derived (`PseudorangeCalculator`). Never invent platform getters.
6. **Unit tests** — JUnit 4, domain pure functions preferred; existing mockk/turbine allowed for ViewModel/Repository tests. Method names: backtick descriptive.
7. **Verify before done** — each task: focused tests → full `./gradlew test` before commit when practical; wave end: `ktlintCheck` + `test` + `assembleDebug`.
8. **JDK** — local: `JAVA_HOME=C:\Program Files\Java\jdk-21` (or use `gradle.properties` java.home). CI uses setup-java.
9. **Do not commit secrets** or machine-only paths into shared config.
10. **YAGNI** — implement only what the wave plan specifies; no Crashlytics unless human opts in (Wave B E3 = Timber only by default).

## Wave Overview

| Wave  | Plan file                          | Scope                                                                                           | Est.     | Depends on                                             |
| ----- | ---------------------------------- | ----------------------------------------------------------------------------------------------- | -------- | ------------------------------------------------------ |
| **A** | `2026-07-18-wave-a-u4-u5.md`       | **U4** list filter/sort/freeze · **U5** A-GPS import/URL/interval/history persist               | ~1 week  | —                                                      |
| **B** | `2026-07-18-wave-b-engineering.md` | **E2** CI · **E8** docs · **E6** Version Catalog · **E1** minify · **E3** Timber · **E5** i18n  | ~2 weeks | A recommended (stable UI)                              |
| **C** | `2026-07-18-wave-c-g3-antenna.md`  | **G3** GnssAntennaInfo                                                                          | ~3 days  | — (can parallel B)                                     |
| **D** | `2026-07-18-wave-d-g1-position.md` | **G1** full GPS MVP: NavMessage → ephemeris → ECEF → corrections → PositionSolver → residual UI | ~3 weeks | C optional; NavMessage subset of G4                    |
| **E** | `2026-07-18-wave-e-g2-rinex.md`    | **G2** RINEX 3.x export                                                                         | ~1 week  | C (antenna header) + D stage observations preferred    |
| **F** | `2026-07-18-wave-f-polish.md`      | **U6** charts · **G4** full nav-msg UI · **E7** Room                                            | rolling  | U6 after A; E7 after U3 (done); G4 after D nav capture |

```
Wave A (UX) ──► Wave B (eng) ──┐
                               ├──► Wave F (polish)
Wave C (antenna) ──► Wave E (RINEX)
         └──► Wave D (G1 full) ──┘
```

## Execution Rules

1. **One wave at a time** unless human says otherwise. Default start: **Wave A**.
2. **Subagent-Driven** — fresh implementer per task, task review after each, final branch review per wave.
3. **Progress ledger** — `.superpowers/sdd/progress.md` rewritten per wave (do not mix with sky-chart phase2 ledger).
4. **Branch naming** — `feat/wave-a-u4-u5`, `feat/wave-b-engineering`, etc. Prefer worktree isolation when available.
5. **TODO.md update** — mark items ✅ only after wave final review, not mid-task.

## Explicitly Out of Scope (until a wave claims them)

- Firebase Crashlytics / Sentry (optional add-on after E3 Timber)
- Sky chart screenshot share (U2 leftover, P3)
- Fake `getPseudorangeMeters` platform API
- Hilt migration
- Product flavors / multi-module split

## Definition of Done (per wave)

- [ ] All wave tasks complete and reviewed
- [ ] `./gradlew ktlintCheck test assembleDebug` green
- [ ] Commits follow AGENTS.md
- [ ] TODO.md chapter for wave items marked complete
- [ ] Final whole-branch review clean or only accepted Minors

## Next Action

Open `docs/superpowers/plans/2026-07-18-wave-a-u4-u5.md` and execute with Subagent-Driven Development.
