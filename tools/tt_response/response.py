"""Offline indirect-response candidate. No fitted or runtime-approved parameters."""
import argparse
import csv
import json
import math
from pathlib import Path


def simulate(rows, parameters, step_minutes=60):
    """dT/dt = kout * (residual + gonadal * (1-Ie2)*(1-Icpa) - T).

    Exposures are linearly interpolated; production is held at the midpoint of
    each substep and the elimination update is solved analytically.
    kout is an effective response turnover assumption, not serum T half-life.
    """
    if not math.isfinite(step_minutes) or step_minutes <= 0 or step_minutes > 60:
        raise ValueError('Integration step must be in (0, 60] minutes')
    p = {key: float(parameters[key]) for key in (
        'baseline_tt', 'residual_tt', 'initial_tt', 'kout_per_day',
        'e2_emax', 'e2_ec50', 'e2_hill', 'cpa_emax', 'cpa_ec50')}
    if not all(math.isfinite(v) for v in p.values()):
        raise ValueError('Parameters must be finite')
    if not (0 <= p['residual_tt'] <= p['baseline_tt'] and p['initial_tt'] >= 0
            and p['kout_per_day'] > 0 and p['e2_ec50'] > 0 and p['e2_hill'] > 0
            and p['cpa_ec50'] > 0 and 0 <= p['e2_emax'] <= 1 and 0 <= p['cpa_emax'] <= 1):
        raise ValueError('Invalid indirect-response parameters')
    if not rows:
        raise ValueError('Exposure history is required')
    exposure = [tuple(float(row[key]) for key in ('day', 'e2_pg_ml', 'cpa_level')) for row in rows]
    if any(not math.isfinite(v) or v < 0 for row in exposure for v in row):
        raise ValueError('Time and exposures must be finite and nonnegative')
    if any(b[0] <= a[0] for a, b in zip(exposure, exposure[1:])):
        raise ValueError('Exposure times must be strictly increasing')

    def target(e2, cpa):
        # Log form avoids overflow at high concentrations or Hill exponents.
        log_hill = p['e2_hill'] * (math.log(e2) - math.log(p['e2_ec50'])) if e2 else -math.inf
        hill_fraction = 1 / (1 + math.exp(-log_hill)) if log_hill >= 0 else math.exp(log_hill) / (1 + math.exp(log_hill))
        ie2 = p['e2_emax'] * hill_fraction
        icpa = p['cpa_emax'] / (1 + p['cpa_ec50'] / cpa) if cpa else 0.0
        return p['residual_tt'] + (p['baseline_tt'] - p['residual_tt']) * (1-ie2) * (1-icpa)

    value = p['initial_tt']
    result = [value]
    for previous, current in zip(exposure, exposure[1:]):
        days = current[0] - previous[0]
        steps = max(1, math.ceil(days * 1440 / step_minutes))
        fraction = -math.expm1(-p['kout_per_day'] * days / steps)
        for i in range(steps):
            weight = (i + 0.5) / steps
            e2 = previous[1] + (current[1] - previous[1]) * weight
            cpa = previous[2] + (current[2] - previous[2]) * weight
            value += fraction * (target(e2, cpa) - value)
        result.append(value)
    return result


def validation_metrics(rows, predictions):
    """Only explicitly designated validation rows; no fitting or split inference."""
    errors = []
    for row, predicted in zip(rows, predictions, strict=True):
        if row.get('split') != 'validation' or row.get('measured_tt_ng_dl') in (None, ''):
            continue
        measured = float(row['measured_tt_ng_dl'])
        if not math.isfinite(measured) or measured < 0:
            raise ValueError('Validation T must be finite, nonnegative ng/dL')
        errors.append(predicted - measured)
    if not errors:
        return None
    return dict(n=len(errors), mae_ng_dl=sum(abs(e) for e in errors)/len(errors),
                rmse_ng_dl=math.sqrt(sum(e*e for e in errors)/len(errors)),
                bias_ng_dl=sum(errors)/len(errors))


def compare_models(rows, predictions):
    """Paired measured errors and model disagreement are intentionally separate."""
    paired = []
    common = []
    missing = 0
    for row, predicted in zip(rows, predictions, strict=True):
        is_validation = row.get('split') == 'validation' and row.get('measured_tt_ng_dl') not in (None, '')
        old = row.get('effect_compartment_tt_ng_dl')
        if old in (None, ''):
            missing += int(is_validation)
            continue
        old = float(old)
        if not math.isfinite(old) or old < 0:
            raise ValueError('Reference T prediction must be finite, nonnegative ng/dL')
        paired.append((old, predicted))
        if is_validation:
            common.append((row, old, predicted))

    def metrics(items):
        if not items:
            return None
        selected = [r for r, _, _ in items]
        old = validation_metrics(selected, [v for _, v, _ in items])
        new = validation_metrics(selected, [v for _, _, v in items])
        return dict(effect_compartment=old, indirect_response=new,
                    mae_change_ng_dl=new['mae_ng_dl']-old['mae_ng_dl'])

    # These pseudo-observations only quantify disagreement, never clinical error.
    disagreement = validation_metrics(
        [dict(split='validation', measured_tt_ng_dl=old) for old, _ in paired],
        [new for _, new in paired])
    conditions = sorted({r.get('condition') or 'unspecified' for r, _, _ in common})
    return dict(model_disagreement=disagreement, common_validation=metrics(common),
                validation_rows_without_reference=missing,
                by_condition={c: metrics([item for item in common if (item[0].get('condition') or 'unspecified') == c]) for c in conditions})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('exposure_csv', type=Path)
    parser.add_argument('parameters_json', type=Path)
    parser.add_argument('--step-minutes', type=int, choices=(60, 15, 5, 1), default=5)
    parser.add_argument('--output', type=Path, default=Path(__file__).resolve().parents[2] / 'app/build/tt-response/report.json')
    args = parser.parse_args()
    with args.exposure_csv.open(encoding='utf-8-sig', newline='') as f:
        rows = list(csv.DictReader(f))
    config = json.loads(args.parameters_json.read_text(encoding='utf-8-sig'))
    predictions = simulate(rows, config['parameters'], args.step_minutes)
    report = {
        'model': 'exploratory_indirect_response',
        'approved_for_runtime': False,
        'data_source': config['data_source'],
        'parameters': config['parameters'],
        'integration_step_minutes': args.step_minutes,
        'validation_metrics': validation_metrics(rows, predictions),
        'comparison': compare_models(rows, predictions),
        'limitations': [
            'Parameters are supplied assumptions, not estimated population or personal PK/PD.',
            'Constant nonsuppressible residual production is a modeling assumption.',
            'No Bayesian learning, biological uncertainty interval, or clinical validation.',
            'Validation labels do not prove independent provenance or matched assay/population.',
            'Exposure sampling must resolve oral peaks; interpolation cannot recover missing exposure.',
            'Model disagreement is not error against measured T. Both models must be scored on identical validation rows.',
        ],
        'points': [dict(day=float(row['day']), tt_ng_dl=value,
                        effect_compartment_tt_ng_dl=float(row['effect_compartment_tt_ng_dl']) if row.get('effect_compartment_tt_ng_dl') not in (None, '') else None)
                   for row, value in zip(rows, predictions)],
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, allow_nan=False) + '\n', encoding='utf-8')
    print(args.output)


if __name__ == '__main__':
    main()
