# Initial Evidence Registry

This registry records sources considered for HormoneLog model parameters. Listing a source does not automatically enable an estimate model. A parameter is enabled only after its route, unit, sampled population, assay, timing, extraction method, and uncertainty have been reviewed and recorded in a versioned bundle.

## Sources under review

| ID | Scope | Source | Applicability | Status |
| --- | --- | --- | --- | --- |
| SUBLINGUAL_ORAL_PK_2022 | Oral and sublingual E2 | Doll et al., *Endocrine Practice* 2022, PMID 34781041, DOI 10.1016/j.eprac.2021.11.081 | 10 transgender women; 1 mg single-dose crossover; LC-MS/MS and immunoassay; sampled 0–8 h | Metadata reviewed; no production parameter extracted |
| INJECTABLE_SCOPING_2024 | Injectable EV/EC | Rothman et al., 2024, PMID 38782202 | Scoping review; reports limited rigorous injection PK evidence | Context only; no production parameter extracted |
| INJECTABLE_MULTICENTER_2025 | Injectable EV/EC, IM/SC | PMID 39797602 | 562-person retrospective study; dose and post-injection timing were significant covariates | Context only; no production parameter extracted |
| ASSAY_BIAS_2022 | E2 assay uncertainty | PMID 35015702 | 89 transgender women; compares immunoassays with LC-MS/MS | Measurement-uncertainty review required |
| GEL_LABEL | Estradiol gel | DailyMed estradiol gel label | Postmenopausal population; product-specific PK | Indirect evidence; no production parameter extracted |
| PATCH_LABEL | Estradiol patch | DailyMed estradiol transdermal system label | Postmenopausal population; product-specific PK | Indirect evidence; no production parameter extracted |

## Required parameter-review record

Every enabled parameter must include the following fields:

```text
parameter ID, model version, route, drug/ester, value, unit, distribution,
source ID, population, sample size, dose, sampling window, assay,
extraction method, uncertainty rationale, reviewer, review date
```

## Bundle v1 — adopted parameters (reviewed 2026-08-27)

Defined in `core/evidence/.../EvidenceBundleV1.kt`. These are **literature-derived
population approximations for exploration, not clinically validated**, and the app
never uses them for dosing advice.

| Route / variant | Params | Source id(s) | Notes |
| --- | --- | --- | --- |
| IM & SC injection · EV, EC, EEn, EB, EUn | 3-compartment `[d, k1, k2, k3]` | `estrannaise`, `transfemsci_inj_meta` | EV fit pooled from ~28 studies / 309 injections (Düsterberg 1982 n=2 excluded as an outlier). EV 5 mg single dose ≈ Cmax 295 pg/mL @ 2.1 d, t½ ≈ 3.0 d. SC reuses IM params with wider engine uncertainty. |
| Patch · twice-weekly, once-weekly | 3-compartment `[d, k1, k2, k3]` (dose = µg/day) | `estrannaise` | |
| Oral E2 | 1-compartment `oralScale / oralKa / oralKe` | `doll2022`, `winston_mcpherson2025` | Fit to 1 mg → Cmax 35 pg/mL @ 8 h; terminal phase extrapolated (only 0–8 h measured). Linear dose-response. |
| Sublingual E2 | `slScale / slKa / slKe / slSwallowed` (+ oral params for swallowed fraction) | `doll2022` | Fit to 1 mg → Cmax 144 pg/mL @ 1 h; ~40% swallowed → oral tail. Wide uncertainty. |
| Gel | — none — | | `supports(Route.GEL)` stays false: label-only PK, postmenopausal, high variability. |
| TT PK/PD (non-route) | baseline/floor, E2 effect-compartment τ=7 d + Hill (Emax 0.97 / EC50 45 / h 2.2), CPA 1-comp + saturating suppression (Emax 0.6) | `ucsf_guidelines`, `winston_mcpherson2025`, `seifert2025_inj_mono`, `wiki_cpa` | Calibrated to Seifert 2025: injectable monotherapy at E2 ~232 pg/mL → median T 17 ng/dL (IQR 10–33), 82.6% < 50. ENIGI: CPA + estrogen → ~15–25 ng/dL, dose-independent 10–100 mg. Model output for EV 10 mg/2wk + CPA 25 mg = T ~11–20 ng/dL. **Limitation:** an effect-site-*average* model cannot capture route-dependent suppression (stable transdermal suppresses better than peaky oral/SL at the same mean E2 — RCT evidence); oral/SL-only T predictions run low. |

The E2 3-compartment rate constants and patch parameters are from the MIT-licensed
open-source **estrannaise.js** (© 2025 alix, github.com/WHSAH/estrannaise.js). Its
model form: `C(t) = dose·d·k1·k2·[ Σ eᵏ terms ]`.

## Safety rule

No source in this file authorizes a dosing recommendation. Bundle v1 curves are
population estimates with wide uncertainty bands and carry a persistent
"참고용 · 임상 검증 아님" label in the UI.
