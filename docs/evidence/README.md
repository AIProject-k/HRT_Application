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
| MPA_PFIZER_LABEL_TABLE1 | Oral MPA (PROVERA tablets) | Pfizer PROVERA prescribing information, clinical pharmacology, Table 1 (pfizermedical.com/provera/clinical-pharmacology), read 2026-10-28 | Postmenopausal women, fasting, serum GC-MS, mean (SD). Single 2 × 10 mg and 8 × 2.5 mg are each 20 mg in total, not 10 mg single doses. Repeated 1 × 10 mg daily, sampled after the day-7 dose | PK summary only (stage PK_SUMMARY). No time series, so no curve until MODEL_VALIDATED |
| MPA_PFIZER_FOOD | Oral MPA with food | Same page, food-effect text | 10 mg taken before or after a meal: Cmax +50–70%, AUC +18–33%, half-life unchanged. No fed-arm means, n or design | Relative change only; not a curve |
| MPA_PFIZER_HEPATIC | Oral MPA, advanced liver disease | Same page | 14 patients; MPA elimination reduced. No values | Metadata only; carried into the disclaimers |
| MPA_WIKSTROM_1984 | Oral MPA 5 and 10 mg | PMID 6233840 | 5 women; repeated over 8 weeks; RIA. Abstract read 2026-10-27 | Metadata only; full text not read |
| MPA_SVENSSON_1994 | Oral MPA 5 mg with a combined product | PMID 8015506 | 12 postmenopausal women; randomised crossover. Abstract read 2026-10-27 | Metadata only |
| MPA_JOHANSSON_1986 | High-dose oral MPA (500 and 1000 mg) | PMID 2943134 | Two-compartment model; RIA; 96-hour sampling. Abstract read 2026-10-27 | Metadata only; not a 10 mg reference |
| MPA_VICTOR_JOHANSSON_1976 | Oral MPA | PMID 975821 | No abstract on PubMed; ScienceDirect returned 403 | Not verified; no number stored |
| MPA_KIM_2001, MPA_BLUE_2018 | MPA assays | PMID 11675672, PMID 29407362 | LC-MS/MS method limits only (LOQ 0.05 and 0.020 ng/mL) | Method metadata only |

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

## MPA evidence (`MpaEvidence`, bundle `mpa-evidence-2026-10-28.1`)

Defined in `core/evidence/.../MpaEvidence.kt`. The bundle stage is `PK_SUMMARY`, the strongest stage any source reaches. Numbers need `MODEL_VALIDATED` and an enabled model, so the MPA path returns `EVIDENCE_INSUFFICIENT` and draws no curve.

- The Pfizer table gives 20 women for the single-dose study, with no per-arm split and no sampling times.
- Single 20 mg arms and the repeated 10 mg arm are never pooled. The registry holds no single 10 mg value.
- Literature abstracts were read, but no full text was reached, so no number is stored from them.
- WPATH SOC8 (PMC9553112) is clinical guidance with no PK values, so it is not in the PK registry.

## Oral progesterone (P4) re-check, 2026-10-28

- ANSM RCP §5.2 means (2 h 11.75, 4 h 8.37, 6 h 2.0, 8 h 1.64 ng/mL, baseline 0.13) match the parameters in `ProgesteroneEvidence`.
- The label names no assay. A reference curve read from a figure (Apolikhina 2023, Fig. 3, about ±10%) is about 4–8× lower at 2–8 h. Levine & Watson 2000 (PMID 10689005) report that direct RIA reads about 8× higher than LC-MS for oral progesterone. This is a hypothesis; the assay behind the label is unknown.
- In that figure the 24 h value sits below the pre-dose value, so baseline handling is unclear.
- Food: Qin 2022 (PMID 34806331) and Pickar 2015 report large meal effects. The app records meal context but does not model it.
- The 8 h tail is an extrapolation of the 6–8 h slope. The label has no measured points after 8 h.
- Full texts not reached: Simon 1993 (403), Qin 2022 (403), Soni 2025 (403). Apolikhina 2023 tables are images.

## Safety rule

No source in this file authorizes a dosing recommendation. Bundle v1 curves are
population estimates with wide uncertainty bands and carry a persistent
"참고용 · 임상 검증 아님" label in the UI.
