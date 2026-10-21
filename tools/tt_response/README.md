# Exploratory Total T indirect-response candidate

This offline tool implements a production-inhibition indirect-response equation:

`dT/dt = kout * (residual + (baseline - residual) * (1 - I_E2) * (1 - I_CPA) - T)`

E2 uses a Hill inhibition and CPA uses a saturating Emax inhibition. Residual production is kept separate. This is a research assumption, including after orchiectomy, not a validated adrenal model. `kout` is an effective response turnover rate; do not substitute the circulating testosterone half-life. No parameter values are supplied as clinical defaults. The Android app continues using its existing effect-compartment model with numerical and dose-status corrections.

The general indirect-response method is described by [Dayneka, Garg & Jusko (1993)](https://pubmed.ncbi.nlm.nih.gov/8133465/). This paper does not establish a transgender HRT model. [ENIGI (2021)](https://pmc.ncbi.nlm.nih.gov/articles/PMC8571811/), [Collet et al. (2023)](https://pubmed.ncbi.nlm.nih.gov/36201493/) and [Seifert et al. (2025)](https://pubmed.ncbi.nlm.nih.gov/?term=40639470) provide population context; they do not identify individual turnover and sensitivity parameters from one or two blood tests.

Run from the repository root, using Python 3.10+ and the standard library:

```powershell
python -m unittest discover -s tools/tt_response -v
python tools/tt_response/response.py exposure.csv parameters.json
```

CSV columns: `day,e2_pg_ml,cpa_level,measured_tt_ng_dl,split`. Optional `effect_compartment_tt_ng_dl` holds predictions exported from the Kotlin engine; `condition` groups matched route/regimen/assay conditions. Times must strictly increase; day zero is the start of the supplied exposure history. Use a separate CSV and initial condition for each patient/history; never concatenate patients into one trajectory. CPA level is in the same arbitrary exposure units as `cpa_ec50`, not daily dose in mg. Include only exposure reconstructed from actual administrations; missed doses must not generate exposure. Preserve assay, route and source information in the input provenance. Sample exposure finely enough to resolve oral peaks; the tool cannot infer missing exposure from sparse labs.

JSON structure: `{"data_source": "description of study/assay/provenance or synthetic fixture", "parameters": {...}}`. All parameters are required:

| Parameter | Unit / constraint |
| --- | --- |
| `baseline_tt` | ng/dL, total unsuppressed equilibrium |
| `residual_tt` | ng/dL, 0 to baseline, independent of suppressors |
| `initial_tt` | ng/dL at first supplied time, nonnegative |
| `kout_per_day` | positive, independently justified effective turnover assumption |
| `e2_emax`, `cpa_emax` | inhibition fractions in [0, 1] |
| `e2_ec50` | positive pg/mL |
| `e2_hill` | positive exponent |
| `cpa_ec50` | positive arbitrary exposure units matching input CPA |

The default result is local ignored `app/build/tt-response/report.json`. It contains predictions, the supplied parameters and source, and MAE/RMSE/signed bias only for rows explicitly marked `validation` with measured T in ng/dL. `calibration` or unlabelled rows never contribute to validation scores. This tool does not fit parameters; keep any future fitting data separate from validation patients. Labels alone do not establish independence. No real independent patient dataset is bundled, and synthetic unit tests prove the mathematics only.

Every result remains `approved_for_runtime: false`. There is no Bayesian personal learning, biological uncertainty interval, dose recommendation, or automatic Kotlin coefficient export. Runtime adoption requires justified parameters, matched independent longitudinal data, and review of prediction errors and uncertainty.

## Compare models on the same input

`response.py` defaults to <=5-minute substeps; choose `--step-minutes 60`, `15`, `5` or `1` for numerical comparison. The Python API keeps its previous 60-minute default for existing callers. Interpolation assumptions still depend on the supplied exposure samples.

When the Kotlin reference column is supplied, the report separates **model disagreement** (no blood tests required) from **common validation** (both predictions and an explicitly labelled measured T on exactly the same rows). It reports both models' MAE/RMSE/bias, their MAE difference, counts of validation rows lacking a reference, and condition-specific paired scores. No common measured rows means `common_validation: null`, never a fabricated accuracy score. Existing single-model `validation_metrics` may have more rows; use common paired scores to compare models. Condition labels do not establish matched assays or independent provenance; review the source before interpreting scores.

## Kotlin integration resolution and desktop cost

The application uses a 5-minute integration grid. The internal test constructor also accepts 60/15/5/1-minute steps; it is not a user-facing biological parameter. The numerical regression threshold of 0.1 ng/dL in the tested off-grid tablet scenarios is an engineering tolerance, not a clinical accuracy claim. To reproduce the actual Kotlin comparison (including a 1-minute reference on short scenarios and 5-minute reference on a one-year record):

```powershell
$env:HORMONELOG_MODEL_BENCHMARK = '1'
$env:HORMONELOG_MODEL_REPORT_DIR = Join-Path (Get-Location) 'app/build/model-validation'
.\gradlew.bat :core:model-engine:testDebugUnitTest --tests '*TtResolutionBenchmarkTest' --rerun-tasks
Remove-Item Env:HORMONELOG_MODEL_BENCHMARK, Env:HORMONELOG_MODEL_REPORT_DIR
```

`resolution-comparison.csv` contains T maximum/mean/RMS and relative differences at identical output times, E2 trapezoidal AUC differences, desktop JVM elapsed times, and thread allocation counts. Allocations are cumulative bytes, **not peak/retained memory**; times include JIT/GC effects and do not establish Android performance. The one-year test resolves history but uses 97 T output points; it is not an exhaustive timewise maximum. Ordinary unit tests skip this expensive benchmark. `--rerun-tasks` avoids reusing Gradle results after only changing opt-in environment variables.

`shared-exposure.csv` exports actual Kotlin E2, CPA and current effect-compartment T on a 5-minute grid, with no invented measured T. Supply an explicitly justified parameter JSON to compare the indirect-response candidate:

```powershell
python tools/tt_response/response.py app/build/model-validation/shared-exposure.csv parameters.json --output app/build/model-validation/tt-comparison.json
```

This export is synthetic scenario data, not independent clinical validation. Changing `kout_per_day` is a sensitivity experiment, not fitting a person's turnover rate.
