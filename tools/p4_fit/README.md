# P4 literature fitting experiment

Run from the repository root: `python tools/p4_fit/fit.py` (numpy, scipy, matplotlib).
Outputs go to ignored `app/build/p4-fit/`. No coefficients are automatically copied into the app.

`observations.csv` contains selected **graph-digitized**, not author-supplied individual, points from Apolikhina, Tarnaeva & Kondratyeva (2023), Figure 3, reference series R (Utrogestan 200 mg oral, fasting, HPLC-MS/MS). The reference product is not the test generic. Figure axis is **pg/mL**, converted to ng/mL by dividing by 1000.

Source: https://aig-journal.ru/articles/Rezultaty-issledovanii-bioekvivalentnosti-preparatov-dienogesta-progesterona-levonorgestrela-estradiola-u-zdorovyh-dobrovolcev-posle-odnokratnogo-prie.html

DOI: 10.18565/aig.2022.304. Original figure URL: https://lib.medvestnik.ru/apps/lib/assets/uploads/aig/2023/01/119-1.jpg

Digitization: original image 350x659 pixels, lower semilog panel. X axis approx x=50 at 0h, x=334 at 48h. Y axis approx y=483 at 100 pg/mL, 56 pixels per decade. Values use `10 ** (2 + (483-y)/56)`. Marker centres were identified by purple reference symbols. Study sampling times, rather than rounded x coordinates, are used; crowded early markers have approximately +/-2px x uncertainty (about 0.34h). Use +/-2px y sensitivity (about 8.6%) as extraction uncertainty, **not biological uncertainty**. Unresolved markers are omitted. Values have rounding/extraction error and must not be treated as exact observations.

The graph's predose value is nonzero while late values fall below it. The paper discusses three predose samples but this figure's baseline correction is not established. Therefore the candidates are fitted to **raw plotted group means**, excluding t=0 from fitting, with zero asymptotic background as an explicit exploratory simplification. They are **not established exogenous-only kernels** and are not eligible for runtime replacement.

Training excludes 3.5, 8, 24 and 48 hours. These timepoint holdouts are internal interpolation/extrapolation checks, not independent patients or independent clinical validation. Rates are constrained positive; random restarts use a fixed seed. Compare the legacy ANSM interpolator, a delayed Bateman curve, and a delayed absorption curve with fast/slow elimination terms. The last is a phenomenological candidate, not a proven biological compartment model.

External comparison: Qin et al. (2022), DOI 10.1111/bcpt.13687, Table 2, Utrogestan reference 200mg, LC-MS/MS. Baseline-corrected fasting reference AUC0-t 36.38 and AUC0-infinity 40.21 ng*h/mL; fed values 270.84 and 280.49. These are **external aggregate summaries**, not held-out time series. Population, baseline handling and sampling differ. Published mean individual Cmax must not be scored as the maximum of a mean fitted curve. Ordinary food must not be mapped to the study's 890kcal high-fat condition.

Qin source: https://doi.org/10.1111/bcpt.13687 (author full text also publicly indexed on ResearchGate, publication 356435970). Original images/full article files remain local in ignored build output and are not redistributed here.

Adoption rule: do not deploy fitted coefficients without established baseline handling, appropriately matched external curves, and reviewed validation. A lower training/holdout residual alone cannot pass this rule. Lack of data is recorded as lack of data, rather than substituted parameters or fixed food multipliers.

The report also includes holdout MAE, RMSE, signed bias (predicted minus observed), and sample count in the original units. Four deterministic +/-2px vertical extraction scenarios are refitted: all concentrations up/down, and early (<=8h) vs tail concentrations shifted in opposite directions. These stress tests expose sensitivity of fitted parameters and holdout errors to digitization. They do not cover horizontal extraction error, establish biological variability or confidence intervals, or resolve the baseline/adoption failures. Run `python -m unittest discover -s tools/p4_fit -v` for numerical checks. NumPy 2+, SciPy and Matplotlib are required.
