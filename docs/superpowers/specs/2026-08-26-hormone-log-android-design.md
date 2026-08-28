# HormoneLog Android Implementation Design

**Status:** Approved design for implementation planning
**Date:** 2026-08-26
**Target:** Android native application, device/AVD-tested
**Product boundary:** Local-first HRT record, E2/TT estimate, laboratory context, evidence provenance, and staged personal calibration. It is not a prescribing, dose-recommendation, or physical-change prediction product.

## 1. Goal and non-goals

Build an offline-capable Android application that records actual hormone-administration events and laboratory results, reconstructs historical E2/TT estimates, forecasts from active regimens, and transitions from a literature-backed population model to a personal model as eligible laboratory data accumulates.

The application must always distinguish measured laboratory values from estimated model values. It must preserve an audit trail that lets a user identify the data, model version, parameter set, and evidence sources that produced any displayed curve or exported report.

Out of scope:

- Dose escalation, dose reduction, medication selection, or scheduling recommendations.
- “Optimal,” “ideal,” or feminization-target ranges.
- Predicted physical, psychological, or sexual changes.
- Automatic cloud synchronization or background server transmission.

## 2. Technology and module boundaries

Use Kotlin and Jetpack Compose. Keep model code independent of Android framework APIs and database libraries so it is deterministic and unit-testable.

```text
app (Compose navigation, ViewModels, Android integration)
  -> feature modules (dashboard, records, labs, regimen, model, evidence, export)
  -> domain (entities, use cases, model contracts)
  -> model-engine (E2 PK, TT PK/PD, calibration, uncertainty, validation)
  -> data (Room repositories, SQLCipher database, Keystore key management, export writers)
```

Rules:

- UI calls use cases only; it does not calculate hormone values.
- The model engine takes immutable domain input and produces immutable estimates.
- Repositories own persistence and map database entities to domain types.
- Android Keystore protects the SQLCipher passphrase; no server endpoint exists.
- Model parameters are packaged versioned data, not hard-coded inside UI or view-model logic.

## 3. Domain data model

### 3.1 Profile and treatment configuration

`Profile` stores a generated local identifier, display settings, locale/time-zone preference, and `GonadalStatus` (`Intact`, `PostOrchiectomy`, `Unknown`). It does not require identifying information.

`Medication` describes the actual product/formulation: drug, ester where applicable, route, concentration, dose unit, and optional product label. `Regimen` is a plan with dose, interval, start/end dates, and active status. Regimens are used only for future prediction; historical reconstruction uses actual events only.

### 3.2 Actual administration

`DoseEvent` is immutable after its first save except by an explicit edit revision. It contains:

- Event instant in UTC and the original IANA time-zone ID.
- Medication, route, dose amount, entered unit, and normalized dose unit.
- Event status: administered, skipped, delayed, or corrected.
- Route metadata: IM/SC site where available; patch apply/remove times and release rate; gel site/area; oral/sublingual administration details.
- Optional note and source (`manual`, `import`).

Any edit creates a new revision and invalidates calculation snapshots that used the prior revision. Events retain both source unit and normalized value to prevent silent conversion loss.

### 3.3 Laboratory measurements

`LabResult` stores one collection event, not a model output. It includes UTC collection instant, original time zone, laboratory, assay (`LC_MS_MS`, `IMMUNOASSAY`, `UNKNOWN`), analyzer/reference range when available, and whether the user entered it manually or from a report.

`LabAnalyteValue` stores E2 and TT as first-class analytes and allows optional E1, progesterone, LH, FSH, SHBG, free T, albumin, and DHT. Every analyte preserves reported value/unit and normalized canonical unit. Missing collection time is allowed for record keeping but makes the result ineligible for time-sensitive calibration.

`LabContext` is computed and stored with a model snapshot: previous actual dose, elapsed post-dose time, cycle position, recent missed-dose signal, regimen age, equilibrium estimate, predicted E2/TT at collection, residuals, and data-quality classification.

### 3.4 Evidence and parameter provenance

`EvidenceSource` holds citation metadata: title, authors, year, journal, DOI/PMID, population, sample size, drug, route, dose, sampling design, assay, study findings, and evidence level.

`ModelParameter` holds name, unit, distribution/uncertainty, scope (drug/ester/route), model version, and linked evidence sources. A parameter cannot be active without provenance. A packaged `EvidenceBundle` has a semantic version and integrity identifier.

### 3.5 Reproducibility and calibration

`CalculationSnapshot` stores request range, all included event/lab revision IDs, parameter-bundle version, engine version, calibration snapshot ID, generated estimates, and created instant. Curves and exports render from an explicit snapshot.

`CalibrationSnapshot` holds level 0–4, eligible lab IDs, excluded lab IDs with reasons, route-specific parameter adjustments, shared adjustments, uncertainty state, and effective period. It never overwrites raw labs or population parameters.

## 4. Modeling contracts

### 4.1 Common estimate contract

Every prediction returns a median and interval, never a bare numeric value.

```kotlin
data class HormoneEstimate(
    val hormone: Hormone,
    val timestamp: Instant,
    val median: Double,
    val lowerBound: Double,
    val upperBound: Double,
    val source: EstimateSource,
    val calibrationLevel: CalibrationLevel,
    val modelVersion: String,
    val uncertaintyReasons: Set<UncertaintyReason>
)
```

`EstimateSource` is `Population`, `Calibrating`, or `Personalized`. `Measured` is not an `EstimateSource`; lab data remains a separate type throughout the app.

### 4.2 Route-specific E2 population models

Routes use separate model implementations and parameter sets:

- Injection: fast depot, slow depot, ester hydrolysis, systemic E2, and elimination; EV, EC, EEn, and EB parameter sets are separate.
- Oral: absorption, first-pass effect, systemic exposure, and elimination.
- Sublingual: mucosal and swallowed fractions with independent uncertainty.
- Patch: skin reservoir, applied/replaced/removed state, and residual release.
- Gel: skin reservoir, systemic absorption, and wide route-specific variability.

The model engine sums the contribution of individual actual dose events for historical periods. Future periods derive only from the active regimen schedule. The graph visually separates these regions.

All routes are recordable. A route is estimate-enabled only when an active evidence-backed parameter set exists. Otherwise the app stores its events but shows `Model unavailable` with the evidence limitation, instead of displaying a fabricated curve.

### 4.3 TT PK/PD model

TT estimation consumes time-varying E2 exposure, gonadal status, optional baseline TT/LH/FSH, and medication effects. It models a delayed hormonal-effect state rather than an instantaneous proportional response.

- CPA and GnRH analogue effects use evidence-backed, versioned parameter sets.
- Spironolactone has weak/high-uncertainty effect unless personal labs support calibration.
- Bicalutamide records receptor-antagonist exposure but does not force a strong serum-T reduction.
- Finasteride/dutasteride records 5-alpha-reductase inhibition without a strong direct total-T reduction.

If no valid model evidence exists for the current combination, the TT curve is disabled rather than extrapolated beyond its supported domain.

### 4.4 Calibration and uncertainty

Lab eligibility is evaluated before fitting. Required factors include known collection time, adequate dose history, model-supported route, and no unresolved unit conflict. Quality considers assay, timing accuracy, regimen stability, completeness of prior dose events, and model applicability.

- Level 0: no eligible lab; population model and wide interval.
- Level 1: one eligible lab; conservative exposure-scale adjustment.
- Level 2: two eligible labs at different cycle timing on the same stable regimen; limited curve-shape adjustment.
- Level 3: three or more high-quality labs; constrained personal PK/PD fitting.
- Level 4: five or more high-quality labs, stable regimen, known timing, and compatible assay history; personal model is default while population priors remain regularizers.

Route change clears route-specific personalization for that route. Regimen change retains only applicable shared tendencies and widens uncertainty. No raw lab or historical calculated snapshot is mutated by re-calibration.

Uncertainty increases for missing/inconsistent dose history, assay unknown, route/model mismatch, recent regimen/route change, sublingual/gel variance, and sparse labs. A missing or contradictory critical input blocks estimation; it does not merely widen the displayed interval.

### 4.5 Validation

For eligible labs, compute absolute error, relative error, median error, peak-timing error, and trough error where applicable. Compare `LegacyEmpirical`, `LiteraturePopulation`, and `Personalized` model outputs only when each is applicable. UI summarizes fit as `Good`, `Moderate`, or `Limited`; detailed metrics remain in the model/evidence screen and exported report.

## 5. User experience

The app uses a graph-first home screen selected during design review.

### 5.1 Dashboard

The dashboard displays:

- E2 and TT cards with `Estimated` labels, median, likely range, model status, and last measured value/date.
- An interactive curve with uncertainty band, measured lab points, dose markers, guideline-reference band, and an explicit historical/forecast divider.
- Range selectors: 24 hours, 3/7/14/30/90 days, and custom range.
- Quick actions for dose and lab entry.

Guideline values are labelled `Guideline Reference`, never optimal/ideal/target. The UI never offers dose or medication advice.

### 5.2 Recording and timelines

Dose entry is route-adaptive, validates units and required route metadata, and supports administered/skipped/delayed/corrected events. The dose timeline is the auditable input to historical reconstruction.

Lab entry captures collection time before result entry, then analyzer/assay and reference range. After save, the app shows generated Lab Context including elapsed post-dose time, current regimen context, predicted value at collection, and a clear measured-versus-estimated comparison.

### 5.3 Model, evidence, and privacy

The model screen shows Population, Calibrating, Personalized, or Limited state; calibration level; included/excluded labs; uncertainty reasons; and model-fit summary. Evidence Explorer exposes the parameters and sources used by the selected curve snapshot.

Privacy settings explain local encryption and provide opt-in JSON, CSV, and PDF export. Export requires explicit user action; generated files include model/evidence versions and an estimate disclaimer.

### 5.4 Accessibility and localization

Support Korean and English, system dark mode, large font scaling, sufficient contrast, and charts with textual summaries so critical values are not color-only information.

## 6. Safety and error behavior

- Do not calculate or recommend a dose, regimen, medication switch, or treatment target.
- Do not label an estimated value as measured or merge the two data types.
- Explain `Limited` and `Model unavailable` in plain language with the missing input/evidence reason.
- Preserve invalid-but-recordable user data for correction; exclude it from calculation only with a visible reason.
- Treat encryption/key initialization failure as a blocking error; do not silently fall back to plaintext.
- Keep source values and units intact when a normalization or conversion fails.

## 7. Verification strategy

### 7.1 Model-engine unit tests

Test unit conversion, time-zone/DST preservation, interval math, dose-event superposition, each route contract, historical/forecast boundary, TT effect-state delay, calibration eligibility/levels, uncertainty reasons, blocked-estimation rules, and deterministic snapshots.

Test fixtures must use synthetic values and parameter fixtures; they must not claim clinical validity.

### 7.2 Data integration tests

Verify SQLCipher database open/close behavior, Keystore passphrase lifecycle, repository mappings, revision invalidation, snapshot persistence, evidence provenance requirement, and JSON/CSV/PDF export content.

### 7.3 Android UI/device tests

On an API-supported AVD and at least one physical Android device when available, verify this critical path:

1. Create local profile and encrypted database.
2. Record route-specific dose event.
3. Confirm graph shows historical contribution or explicit model-unavailable state.
4. Add a timed E2/TT laboratory result.
5. Confirm Lab Context and measured point are displayed separately from estimates.
6. Add eligible labs and confirm calibration state transition.
7. Change regimen/route and confirm uncertainty reset/parameter isolation.
8. Export a user-requested report and verify its source/model version disclosure.

Build, unit-test, and instrumentation success are distinct from AVD/device interaction evidence; each must be recorded separately.

## 8. Implementation sequence

The implementation plan will create the empty Android project and then proceed in vertical, testable increments:

1. Project foundation, encrypted local storage, domain contracts, and local profile.
2. Medication, regimen, and multi-route dose recording/timeline.
3. Lab entry, unit normalization, Lab Context, and measurement timeline.
4. Evidence bundle/provenance and population E2 route contracts.
5. Graph-first dashboard and historical/forecast presentation.
6. TT PK/PD contracts and supported medication metadata.
7. Calibration, uncertainty, reproducible snapshots, and validation metrics.
8. Evidence explorer, export, accessibility/localization, and Android device verification.

Clinical parameters are not implementation placeholders: each estimate-enabled model task must add a reviewed evidence bundle and provenance tests before exposing its curve to the user.
