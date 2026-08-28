# HormoneLog

*[한국어](README.md) · English*

> An offline Android app for logging hormone replacement therapy (HRT) · local-only · no server · no account

**⚠️ Reference tool only.** The projected curves are *population approximations* derived
from public literature, not clinically validated values. The app does not suggest
diagnoses, prescriptions, or dose changes, and every piece of data stays on the
device (no network calls).

---

## Features

- **Dose logging** — drug, route, amount, time (exact date/time picker included), repeating schedules (regimens), and one-tap sample data
- **Lab results** — measured E2 / Total Testosterone values, unit conversion (pg/mL·pmol/L, ng/dL·nmol/L), assay method recorded
- **Projected flow** — E2 and Total-T projection curves from literature PK/PD parameters
  - E2: per-ester 3-compartment model for estradiol esters (estrannaise.js), plus oral / sublingual Bateman models
  - Total-T: E2 delayed effect compartment + Hill suppression + CPA saturable suppression
  - past (solid) / future (dashed) split, uncertainty band, dose ticks, measured-value diamonds
  - **Calibrate the curve with real lab values** — scales exposure by the geometric mean of the measured/predicted ratio (Level 1–2, clamped to [0.5, 2.0])
- **Timeline** — grouped by date, dose/lab filters, per-item delete
- **Delete records** — individual / bulk by type / full reset / regimens, all behind a confirm dialog
- **CSV import & export** — Storage Access Framework, no permissions needed ([sample](docs/sample/hrt_2month_sample.csv))
- **Clinic notes** — the user records their own hormone-prescribing clinics (no bundled data, no network)

## Architecture

Multi-module Gradle:

| Module | Role |
| --- | --- |
| `app` | Jetpack Compose UI, single `DashboardState` + pure `DashboardReducer` + `DashboardViewModel` |
| `core:domain` | Domain types (`DoseEvent`, `LabResult`, `Regimen`, `Clinic` …), unit normalization |
| `core:evidence` | Evidence bundle — a route model activates only when every parameter has provenance |
| `core:model-engine` | E2 curve engine, TT suppression engine, measured-value calibration engine |
| `core:data` | Local JSON file persistence (`org.json`), CSV I/O |

- All state transitions are pure functions (`DashboardReducer`); only transitions that need persistence are written to file by the `ViewModel`
- A curve is drawn only when the evidence bundle has provenance for its parameters — gel evidence is thin, so it stays `Model unavailable`
- Model parameters, sources, and review notes: [docs/evidence/README.md](docs/evidence/README.md)

Design docs: [spec](docs/superpowers/specs/2026-08-26-hormone-log-android-design.md) · [implementation plan](docs/superpowers/plans/2026-08-26-hormone-log-android.md)

## Build & run

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug         # install on a connected device
./gradlew testDebugUnitTest         # unit tests, all modules
```

A local Android SDK is required (`sdk.dir` in `local.properties`).

### Release build

```bash
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

With no keystore, the build signs with the debug key and produces a sideloadable
APK. To sign with your own key, see [docs/RELEASE.md](docs/RELEASE.md). Distributed
builds live in [Releases](../../releases).

## Tech stack

- Kotlin 2.3.21 · Jetpack Compose (BOM 2026.06.00) · AGP 9.1.1 · Gradle 9.3.1 · JDK 17
- minSdk 28 · targetSdk 36 · compileSdk 36
- forced dark theme, navigation is a state enum + `when` (no navigation-compose)
- persistence is plain-text JSON files (Room / encryption are follow-up work)

## How it was made

This project was **built with Anthropic Claude** — design, implementation, literature
research, model parameter calibration, unit tests, on-device verification (Galaxy S23),
and release were all done in conversational sessions with the coding agent **Claude Code**
(model **Claude Sonnet 5**). Commits are tagged `Co-Authored-By: Claude Sonnet 5`.

## Status

Currently **in real-world testing**.
