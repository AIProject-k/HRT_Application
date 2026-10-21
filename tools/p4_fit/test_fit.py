import unittest

import numpy as np

import fit


class DigitizationSensitivityTest(unittest.TestCase):
    def test_two_pixel_scenarios_bound_original_and_perturb_peak_and_tail_separately(self):
        self.assertTrue(callable(getattr(fit, 'digitization_scenarios', None)))
        y = np.array([1.0, 2.0, 0.1])
        scenarios = fit.digitization_scenarios(np.array([1.0, 4.0, 24.0]), y)
        factor = 10 ** (2 / 56)
        np.testing.assert_allclose(scenarios['all_up'], y * factor)
        np.testing.assert_allclose(scenarios['all_down'], y / factor)
        self.assertGreater(scenarios['early_up_tail_down'][0], y[0])
        self.assertLess(scenarios['early_up_tail_down'][-1], y[-1])
        for values in scenarios.values():
            self.assertTrue(np.all(values > 0))

    def test_score_reports_signed_bias_and_absolute_error_in_original_units(self):
        score = fit.score(np.array([2.0, 4.0]), np.array([1.0, 7.0]))
        self.assertIn('mae_ng_ml', score)
        self.assertEqual(2, score['n'])
        self.assertEqual(2.0, score['mae_ng_ml'])
        self.assertEqual(1.0, score['bias_ng_ml'])
        self.assertAlmostEqual(np.sqrt(5.0), score['rmse_ng_ml'])


if __name__ == '__main__':
    unittest.main()
