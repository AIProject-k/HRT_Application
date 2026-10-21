"""Score frozen P4 candidates on explicitly matched, separate-study data; never fit."""
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path

import numpy as np

from fit import legacy, predict, score


def validate_rows(rows, fit_report):
    grouped = {}
    excluded = []
    for number, row in enumerate(rows, 2):
        reason = None
        try:
            hour, value, dose = (float(row[k]) for k in ('hour', 'value_ng_ml', 'dose_mg'))
            if not all(math.isfinite(v) and v >= 0 for v in (hour, value, dose)):
                reason = 'invalid_time_concentration_or_dose'
        except (KeyError, ValueError, TypeError):
            reason = 'missing_or_invalid_numeric_input'
        if reason is None:
            if row.get('split') != 'validation':
                reason = 'not_validation'
            elif not row.get('study') or row['study'] == fit_report['study_id']:
                reason = 'missing_or_nonindependent_study'
            elif (row.get('product'), row.get('meal'), dose, row.get('assay'), row.get('concentration_unit')) != (
                    'Utrogestan', 'fasted', 200.0, 'HPLC-MS/MS', 'ng/mL'):
                reason = 'unmatched_product_meal_dose_assay_or_unit'
            elif row.get('baseline_kind') not in ('raw_serum', 'drug_contribution'):
                reason = 'baseline_handling_not_established'
            elif row.get('data_kind') not in ('group_mean', 'individual'):
                reason = 'data_kind_not_established'
        if reason:
            excluded.append(dict(csv_line=number, reason=reason))
            continue
        keys = ('study', 'series', 'product', 'meal', 'dose_mg', 'assay', 'baseline_kind', 'data_kind')
        identity = tuple(row.get(k, '') for k in keys)
        group = grouped.setdefault(identity, dict(conditions=dict(zip(keys, identity)), t=[], y=[]))
        group['t'].append(hour)
        group['y'].append(value)

    results = []
    for group in grouped.values():
        t, y = np.array(group['t']), np.array(group['y'])
        models = {}
        if group['conditions']['baseline_kind'] == 'drug_contribution':
            models['legacy_ANSM'] = score(y, legacy(t))
            unavailable = 'Raw-serum candidate fits are not established drug-contribution kernels.'
        else:
            for name, dual in [('lagged_one_compartment', False), ('lagged_fast_slow_elimination', True)]:
                x = np.array(fit_report['models'][name]['parameters_transformed'])
                predicted = predict(x, t, dual)
                if not np.all(np.isfinite(predicted) & (predicted >= 0)):
                    raise ValueError('Frozen candidate produced invalid concentrations')
                models[name] = score(y, predicted)
            unavailable = 'Legacy ANSM is drug contribution; raw total serum is not a matched target.'
        results.append(dict(conditions=group['conditions'], models=models,
                            other_family_unavailable=unavailable))
    return dict(approved_for_runtime=False, groups=results, excluded=excluded, limitations=[
        'Separate study identifiers and declared conditions do not prove data independence or provenance.',
        'No refitting on validation data. Group means and individual data are never pooled.',
        'Raw serum and baseline-subtracted drug contribution cannot be scored as the same target.',
        'No validated biological interval, food multiplier, repeated-dose kernel or personal calibration.',
        'A lower validation error alone does not authorize runtime adoption.',
    ])


def main():
    root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('validation_csv', type=Path)
    parser.add_argument('--fit-report', type=Path, default=root/'app/build/p4-fit/fit-report.json')
    parser.add_argument('--output', type=Path, default=root/'app/build/p4-fit/validation-report.json')
    args = parser.parse_args()
    frozen = args.fit_report.read_bytes()
    with args.validation_csv.open(encoding='utf-8-sig', newline='') as f:
        report = validate_rows(list(csv.DictReader(f)), json.loads(frozen))
    report['frozen_fit_sha256'] = hashlib.sha256(frozen).hexdigest()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, allow_nan=False)+'\n', encoding='utf-8')
    print(args.output)


if __name__ == '__main__':
    main()
