import unittest

import validate


class FrozenValidationTest(unittest.TestCase):
    def setUp(self):
        self.report = dict(study_id='training', models={
            'lagged_one_compartment': dict(parameters_transformed=[-1, 1, 1, 0.1]),
            'lagged_fast_slow_elimination': dict(parameters_transformed=[-3, -0.5, 1, 1, 1, 0.1]),
            'legacy_ANSM': {}})
        self.row = dict(study='external', series='R', product='Utrogestan', meal='fasted',
                        dose_mg='200', assay='HPLC-MS/MS', baseline_kind='raw_serum', data_kind='group_mean',
                        concentration_unit='ng/mL', hour='2', value_ng_ml='2', split='validation')

    def test_frozen_candidates_use_external_matched_rows_without_mixing_baseline_kinds(self):
        result = validate.validate_rows([self.row], self.report)
        self.assertFalse(result['approved_for_runtime'])
        group = result['groups'][0]
        self.assertEqual(2, len(group['models']))
        self.assertNotIn('legacy_ANSM', group['models'])
        self.assertEqual(1, group['models']['lagged_one_compartment']['n'])
        contribution = dict(self.row, baseline_kind='drug_contribution')
        group = validate.validate_rows([contribution], self.report)['groups'][0]
        self.assertEqual(['legacy_ANSM'], list(group['models']))

    def test_mismatched_food_assay_units_dose_or_same_study_are_excluded(self):
        bad = [dict(self.row, **{key: value}) for key, value in [
            ('meal', 'after_meal'), ('assay', 'immunoassay'), ('dose_mg', '100'),
            ('concentration_unit', 'pg/mL'), ('study', 'training'),
            ('baseline_kind', 'unknown'), ('value_ng_ml', '-1'), ('hour', 'nan'), ('split', 'calibration')]]
        result = validate.validate_rows(bad, self.report)
        self.assertEqual([], result['groups'])
        self.assertEqual(9, len(result['excluded']))

    def test_different_studies_and_conditions_remain_separate(self):
        result = validate.validate_rows([self.row, dict(self.row, study='external2'),
                                        dict(self.row, baseline_kind='drug_contribution')], self.report)
        self.assertEqual(3, len(result['groups']))


if __name__ == '__main__':
    unittest.main()
