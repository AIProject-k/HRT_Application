"""Reproducible exploratory fitting. Never exports a production-ready P4 profile."""
import csv
import json
import platform
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import scipy
from scipy.optimize import least_squares
from scipy.special import expit

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "app/build/p4-fit"
HOLDOUT = {3.5, 8.0, 24.0, 48.0}


def legacy(t):
    """Frozen comparison to p4-v1: 200mg, ANSM baseline-subtracted means."""
    t = np.asarray(t)
    h = np.array([0, 1, 2, 4, 6, 8])
    c = np.array([0, 4.25-.13, 11.75-.13, 8.37-.13, 2-.13, 1.64-.13])
    rate = np.log(c[-2]/c[-1])/2
    return np.where(t > 8, c[-1]*np.exp(-rate*np.maximum(t-8, 0)), np.interp(t, h, c))


def bateman(t, ka, ke):
    t = np.maximum(t, 0)
    return ka/(ka-ke) * (np.exp(-ke*t)-np.exp(-ka*t))


def predict(x, t, dual=False):
    if dual:
        slow = np.exp(x[0])
        fast = slow + np.exp(x[1])
        ka = fast + np.exp(x[2])
        amp, weight, lag = np.exp(x[3]), expit(x[4]), x[5]
        tt = np.asarray(t)-lag
        return amp * (weight*bateman(tt, ka, fast)+(1-weight)*bateman(tt, ka, slow))
    ke, ka_gap, amp = np.exp(x[:3])
    return amp * bateman(np.asarray(t)-x[3], ke+ka_gap, ke)


def fit(t, y, dual=False):
    rng = np.random.default_rng(20261019)
    if dual:
        low = np.array([-6., -5., -4., -6., -7., 0.])
        high = np.array([1., 3., 4., 6., 7., 1.2])
        start = np.array([-3., -.5, 1., 1., 1., .1])
    else:
        low = np.array([-6., -4., -6., 0.])
        high = np.array([2., 4., 6., 1.2])
        start = np.array([-1., 1., 1., .1])
    def residual(x):
        return np.log(predict(x, t, dual)+.025)-np.log(y+.025)
    candidates = []
    for i in range(24):
        initial = start if i == 0 else np.clip(start+rng.normal(0, 1, start.size), low+.001, high-.001)
        candidates.append(least_squares(residual, initial, bounds=(low, high), max_nfev=3000))
    return min(candidates, key=lambda r: np.sum(r.fun**2))


def score(observed, predicted):
    return {
        "rmse_ng_ml": float(np.sqrt(np.mean((observed-predicted)**2))),
        "log_rmse_with_0_025_offset": float(np.sqrt(np.mean((np.log(observed+.025)-np.log(predicted+.025))**2))),
    }


def main():
    rows = list(csv.DictReader((Path(__file__).parent / "observations.csv").open(encoding="utf-8")))
    t = np.array([float(r["hour"]) for r in rows])
    # Recompute from recorded pixel calibration; rounded display values are only a cross-check.
    y = np.array([10**(2+(483-float(r["y_px"]))/56)/1000 for r in rows])
    recorded = np.array([float(r["value_pg_ml"])/1000 for r in rows])
    assert np.all(np.abs(recorded-y)/y < .01), "Check digitized units/calibration"
    assert all(r["product"] == "Utrogestan" and r["meal"] == "fasted" and r["dose_mg"] == "200" for r in rows)
    test = np.array([v in HOLDOUT for v in t])
    train = (t > 0) & ~test
    dense = np.linspace(0, 48, 2401)
    results = {}
    curves = {"legacy_ANSM": legacy(dense)}
    for name, dual in [("lagged_one_compartment", False), ("lagged_fast_slow_elimination", True)]:
        opt = fit(t[train], y[train], dual)
        pred = predict(opt.x, t, dual)
        residual = np.log(pred[train]+.025)-np.log(y[train]+.025)
        rss = float(np.sum(residual**2)); n = int(train.sum()); k = len(opt.x)
        results[name] = {
            "parameters_transformed": opt.x.tolist(),
            "training": score(y[train], pred[train]), "timepoint_holdout": score(y[test], pred[test]),
            "aicc_exploratory_log_residuals": n*np.log(max(rss/n, 1e-20))+2*k+2*k*(k+1)/(n-k-1),
            "parameters_at_bounds": np.flatnonzero(opt.active_mask).tolist(),
        }
        if dual:
            slow = float(np.exp(opt.x[0])); fast = slow+float(np.exp(opt.x[1]))
            results[name]["interpretable_parameters"] = {"slow_rate_per_hour": slow, "fast_rate_per_hour": fast,
                "absorption_rate_per_hour": fast+float(np.exp(opt.x[2])), "amplitude_ng_ml": float(np.exp(opt.x[3])),
                "fast_fraction": float(expit(opt.x[4])), "lag_hours": float(opt.x[5])}
        else:
            ke = float(np.exp(opt.x[0]))
            results[name]["interpretable_parameters"] = {"elimination_rate_per_hour": ke,
                "absorption_rate_per_hour": ke+float(np.exp(opt.x[1])), "amplitude_ng_ml": float(np.exp(opt.x[2])), "lag_hours": float(opt.x[3])}
        curves[name] = predict(opt.x, dense, dual)
    results["legacy_ANSM"] = {"training": score(y[train], legacy(t[train])), "timepoint_holdout": score(y[test], legacy(t[test]))}
    for name, curve in curves.items():
        area24 = float(np.trapz(curve[dense <= 24], dense[dense <= 24]))
        results[name]["curve_AUC_0_24_ng_h_ml"] = area24
        results[name]["external_AUC_ratio_to_Qin_fasted_36_38"] = area24/36.38
        results[name]["maximum_of_mean_curve_ng_ml"] = float(max(curve))
    report = {
        "software": {"python": platform.python_version(), "numpy": np.__version__, "scipy": scipy.__version__},
        "study": "Apolikhina 2023 Figure 3 R; Utrogestan 200mg fasting; HPLC-MS/MS",
        "data_kind": "selected digitized raw group means, not individual clinical data",
        "units": "ng/mL; original figure pg/mL",
        "training_hours": t[train].tolist(), "timepoint_holdout_hours": t[test].tolist(),
        "predose_plotted_ng_ml": float(y[0]),
        "approved_for_runtime": False,
        "adoption_failures": [
            "Baseline correction of plotted data is not established; predose exceeds late concentrations.",
            "No appropriately matched independent external time series available.",
            "Internal timepoint holdouts are not independent patients or clinical validation.",
            "Single-dose raw mean fit is not an established repeated-dose exogenous-only kernel.",
        ],
        "external_check_limitations": "Qin is a different population and baseline-corrected. AUC ratio is a stress test, not a validated error percentage. Mean individual Cmax is not the max of a mean curve.",
        "models": results,
    }
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT/"fit-report.json").write_text(json.dumps(report, indent=2)+"\n", encoding="utf-8")
    fig, axes = plt.subplots(1, 2, figsize=(12, 4.5), constrained_layout=True)
    for ax, log in zip(axes, [False, True]):
        ax.scatter(t[t == 0], y[t == 0], color="gray", marker="x", s=45, label="Predose (not fitted)", zorder=5)
        ax.scatter(t[train], y[train], color="black", s=28, label="Digitized training points", zorder=5)
        ax.scatter(t[test], y[test], facecolors="white", edgecolors="black", s=50, label="Timepoint holdout", zorder=5)
        for name, curve in curves.items():
            ax.plot(dense, curve, lw=1.6, label=name.replace("_", " "))
        ax.set(xlabel="Hours after dose", ylabel="P4 (ng/mL)", xlim=(0, 48))
        if log: ax.set_yscale("log"); ax.set_ylim(.005, 20)
        ax.grid(alpha=.2)
    axes[0].legend(fontsize=7)
    fig.suptitle("Exploratory fit: Utrogestan 200 mg, fasting\nDigitized population means - NOT a validated personal/exogenous model", fontsize=11)
    fig.savefig(OUT/"comparison.png", dpi=180)
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
