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

CSV columns: `day,e2_pg_ml,cpa_level,measured_tt_ng_dl,split`. Times must strictly increase; day zero is the start of the supplied exposure history. CPA level is in the same arbitrary exposure units as `cpa_ec50`, not daily dose in mg. Include only exposure reconstructed from actual administrations; missed doses must not generate exposure. Preserve assay, route and source information in the input provenance. Sample exposure finely enough to resolve oral peaks; the tool cannot infer missing exposure from sparse labs.

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
