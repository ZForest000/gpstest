# Wave B: Engineering Health (E2, E8, E6, E1, E3, E5)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans.

**Goal:** Raise engineering quality: automated CI gates, contributor docs, Version Catalog, Release minify, Timber logging, and i18n baseline.

**Architecture:** Config/docs-first wave. Minimal runtime behavior change except Timber replacement of `android.util.Log` and string resource extraction.

**Tech Stack:** GitHub Actions, Gradle 8.9, AGP, Timber, Android resource configurations.

## Global Constraints

- Same as master roadmap.
- **E3 default = Timber only** — no Firebase Crashlytics / Sentry unless human explicitly opts in later.
- Restore CI `push`/`pull_request` triggers on `master` (current is `workflow_dispatch` only — E4 notes this as a gap).
- Do not commit machine-specific `org.gradle.java.home` into CI-visible defaults; keep local-only or document JAVA_HOME.
- Minify must not break GNSS reflection / serialization — expand `proguard-rules.pro` first, then enable minify, then verify `assembleRelease`.

**Order (dependencies):** E2 → E8 → E6 → E1 → E3 → E5

---

## File Map

| Path                                                 | Action                                                   |
| ---------------------------------------------------- | -------------------------------------------------------- |
| `.github/workflows/ci.yml`                           | Modify: push/PR + ktlint + lintDebug                     |
| `.github/dependabot.yml`                             | Create                                                   |
| `gradle.properties`                                  | Document / keep local java.home; CI sed stays or use env |
| `CONTRIBUTING.md`                                    | Create                                                   |
| `CHANGELOG.md`                                       | Create                                                   |
| `docs/ARCHITECTURE.md`                               | Create (short)                                           |
| `gradle/libs.versions.toml`                          | Create                                                   |
| `settings.gradle.kts`, root/`app` `build.gradle.kts` | Catalog migration                                        |
| `app/build.gradle.kts`                               | minify + shrinkResources                                 |
| `app/proguard-rules.pro`                             | serialization/OkHttp/WorkManager/Shizuku keeps           |
| `GpsTestApplication.kt`                              | Create; plant Timber                                     |
| `AndroidManifest.xml`                                | android:name Application                                 |
| 4 Log files                                          | Timber                                                   |
| `values-en/strings.xml`                              | Create                                                   |
| Hard-coded string sites                              | Extract to resources                                     |

---

### Task 1: E2 CI gates

**Files:** `.github/workflows/ci.yml`, `.github/dependabot.yml`

- [ ] **Step 1:** Change `on:` to:

```yaml
on:
    push:
        branches: [master]
    pull_request:
        branches: [master]
    workflow_dispatch:
```

- [ ] **Step 2:** After Setup Gradle, add:

```yaml
- name: ktlint
  run: ./gradlew ktlintCheck

- name: Android Lint
  run: ./gradlew lintDebug
```

Keep unit tests + assembleDebug + assembleRelease. Optional: upload lint reports.

- [ ] **Step 3:** Create `.github/dependabot.yml` for gradle + github-actions weekly.

- [ ] **Step 4:** Commit `chore: 🔧 restore CI push PR gates with ktlint and lint`

---

### Task 2: E8 docs

**Files:** `CONTRIBUTING.md`, `CHANGELOG.md`, `docs/ARCHITECTURE.md`

- [ ] **CONTRIBUTING.md:** PR flow, conventional commits + emoji table from AGENTS.md, ktlint, `./gradlew test`, JDK 21 note.
- [ ] **CHANGELOG.md:** Keep a Changelog; seed `[1.0.0]` Unreleased / initial feature list from README.
- [ ] **docs/ARCHITECTURE.md:** Layers diagram from AGENTS.md, DI approach, GNSS/A-GPS pipelines, test scope.
- [ ] Commit `docs: 📝 add CONTRIBUTING CHANGELOG and ARCHITECTURE`

---

### Task 3: E6 Version Catalog

**Files:** `gradle/libs.versions.toml`, `settings.gradle.kts`, root + app `build.gradle.kts`

- [ ] Create catalog with versions currently in `app/build.gradle.kts` (Compose BOM 2024.10.01, lifecycle 2.8.7, okhttp, work, datastore, serialization, shizuku, timber if added later — order: do catalog **before** Timber so E3 uses catalog alias).
- [ ] Migrate plugins and dependencies to `libs.*`.
- [ ] `./gradlew test assembleDebug`
- [ ] Commit `chore: 🔧 migrate dependencies to version catalog`

---

### Task 4: E1 Release minify

**Files:** `app/build.gradle.kts`, `app/proguard-rules.pro`

- [ ] Add keep rules for kotlinx.serialization, OkHttp, WorkManager, Shizuku, GNSS reflection (existing + gaps).
- [ ] `isMinifyEnabled = true`, `isShrinkResources = true` on release.
- [ ] `./gradlew assembleRelease` must succeed.
- [ ] Commit `chore: 🔧 enable release minify with ProGuard rules`

---

### Task 5: E3 Timber

**Files:** Application class, Manifest, 4 Log files, build.gradle (timber via catalog)

- [ ] Dependency `com.jakewharton.timber:timber` (pin current stable via catalog).
- [ ] `GpsTestApplication`: Debug → `Timber.DebugTree()`; Release → no tree or tree that drops VERBOSE/DEBUG.
- [ ] Replace all `android.util.Log` in:
    - `AGpsDataSourceImpl.kt`
    - `AGpsDownloader.kt` (impl)
    - `AGpsRepositoryImpl.kt`
    - `XtraDataValidator.kt`
- [ ] `./gradlew test` (Log stubs still ok; Timber works on JVM if planted in tests or use default).
- [ ] Commit `feat: ✨ replace android.util.Log with Timber`

---

### Task 6: E5 i18n baseline

**Files:** hard-coded sites, `values/strings.xml`, `values-en/strings.xml`, optional `resourceConfigurations`

- [ ] Extract remaining hard-coded Chinese (SkyChart title, any leftovers after Wave A).
- [ ] Strategy: keep `values/` as Chinese (current product default); add `values-en/strings.xml` English translations for all keys (or at least user-visible).
- [ ] Align `app_name` (Chinese display name optional: `GPS 调试工具` in zh, `GPS Debug Tool` in en).
- [ ] Commit `feat: ✨ add English strings and remove hard-coded UI text`

---

### Task 7: Wave B verify + TODO

- [ ] `./gradlew ktlintCheck test assembleDebug assembleRelease`
- [ ] Mark E1,E2,E3,E5,E6,E8 ✅ in TODO.md
- [ ] Commit `docs: 📝 mark Wave B engineering items complete`

## Spec Coverage

| ID  | Task |
| --- | ---- |
| E2  | 1    |
| E8  | 2    |
| E6  | 3    |
| E1  | 4    |
| E3  | 5    |
| E5  | 6    |
