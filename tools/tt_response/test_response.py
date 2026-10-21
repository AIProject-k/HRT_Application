import math
import unittest

from response import simulate, validation_metrics


class ResponseTest(unittest.TestCase):
    def setUp(self):
        # Synthetic numerical fixtures, never population parameter estimates.
        self.params = dict(baseline_tt=600.0, residual_tt=20.0, initial_tt=600.0,
                           kout_per_day=0.5, e2_emax=1.0, e2_ec50=100.0,
                           e2_hill=2.0, cpa_emax=1.0, cpa_ec50=10.0)

    def rows(self, e2=0.0, cpa=0.0):
        return [dict(day=d, e2_pg_ml=e2, cpa_level=cpa) for d in (0.0, 1.0, 2.0, 10.0)]

    def test_unsuppressed_equilibrium(self):
        self.assertEqual([600.0] * 4, simulate(self.rows(), self.params))

    def test_constant_inhibition_matches_analytical_solution(self):
        expected_target = 20.0 + 580.0 * 0.5
        values = simulate(self.rows(e2=100.0), self.params)
        for d, value in zip((0, 1, 2, 10), values):
            self.assertAlmostEqual(expected_target + (600 - expected_target) * math.exp(-0.5*d), value)

    def test_cpa_is_saturating_and_does_not_suppress_residual_production(self):
        values = simulate(self.rows(e2=1e12, cpa=1e12), self.params)
        self.assertGreater(values[-1], 20.0)
        self.assertAlmostEqual(20.0 + 580.0 * math.exp(-5), values[-1], places=6)

    def test_post_orchiectomy_equilibrium_ignores_suppressors(self):
        p = dict(self.params, baseline_tt=20.0, residual_tt=20.0, initial_tt=20.0)
        self.assertEqual([20.0] * 4, simulate(self.rows(e2=1000.0, cpa=1000.0), p))

    def test_duplicate_times_nonfinite_negative_exposures_and_invalid_parameters_rejected(self):
        for name, value in [('kout_per_day', 0), ('e2_ec50', -1), ('e2_hill', 0),
                            ('e2_emax', 1.1), ('initial_tt', float('nan')),
                            ('residual_tt', 601)]:
            with self.subTest(name=name), self.assertRaises(ValueError):
                simulate(self.rows(), dict(self.params, **{name: value}))
        for rows in ([self.rows()[0]] * 2, self.rows(e2=-1), self.rows(cpa=float('inf'))):
            with self.assertRaises(ValueError):
                simulate(rows, self.params)

    def test_metrics_use_only_labelled_validation_measurements(self):
        rows = [dict(measured_tt_ng_dl=600, split='calibration'),
                dict(measured_tt_ng_dl=12, split='validation'),
                dict(measured_tt_ng_dl='', split='validation'),
                dict(measured_tt_ng_dl=18, split='validation')]
        self.assertEqual(dict(n=2, mae_ng_dl=3.0, rmse_ng_dl=math.sqrt(10), bias_ng_dl=1.0),
                         validation_metrics(rows, [1, 10, 100, 22]))
        self.assertIsNone(validation_metrics([dict(measured_tt_ng_dl=20)], [25]))


if __name__ == '__main__':
    unittest.main()
