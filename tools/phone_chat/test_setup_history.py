"""Run with Termux Python; tests use a private temporary directory, never real setup data."""
import importlib.util
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('hrt_setup', sys.argv[1])
setup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(setup)
sys.argv = sys.argv[:1]

class SetupHistoryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.before = setup.BASE
        setup.BASE = Path(self.temp.name) / 'setup'

    def tearDown(self):
        setup.BASE = self.before
        self.temp.cleanup()

    def test_records_chronological_steps_without_overwriting_previous_events(self):
        setup.progress('ubuntu')
        setup.progress('installed')
        setup.progress('ready')
        events = [json.loads(s) for s in (setup.BASE / 'events.jsonl').read_text().splitlines()]
        self.assertEqual(['ubuntu', 'installed', 'ready'], [e['phase'] for e in events])
        self.assertTrue(all(e['at'].endswith('+00:00') for e in events))
        self.assertEqual('ready', json.loads((setup.BASE / 'progress.json').read_text())['phase'])
        self.assertEqual(0o600, (setup.BASE / 'events.jsonl').stat().st_mode & 0o777)

    def test_unknown_error_details_and_credentials_are_not_written(self):
        secret = 'oauth-token-and-health-record-example'
        setup.progress('error', secret)
        setup.progress(secret, 'USAGE_LIMIT')
        stored = (setup.BASE / 'events.jsonl').read_text() + (setup.BASE / 'progress.json').read_text()
        self.assertNotIn(secret, stored)
        events = [json.loads(s) for s in (setup.BASE / 'events.jsonl').read_text().splitlines()]
        self.assertEqual('EXECUTION', events[0]['error'])
        self.assertEqual('error', events[1]['phase'])
        self.assertEqual('USAGE_LIMIT', events[1]['error'])
        self.assertTrue(all(set(e) == {'at', 'phase', 'error'} for e in events))

    def test_shared_runtime_descriptor_is_independent_of_app_prompt_and_records(self):
        path = setup.BASE.parent / 'claude-phone' / 'runtime.json'
        setup.register_runtime('ubuntu', path)
        data = json.loads(path.read_text())
        self.assertEqual('ubuntu', data['container'])
        self.assertEqual('24.04', data['ubuntuVersion'])
        self.assertEqual('2.1.292', data['claudeVersion'])
        self.assertEqual('/home/hrt/.local/bin/claude', data['executable'])
        self.assertNotIn('prompt', data)
        self.assertNotIn('records', data)
        self.assertNotIn('authToken', data)
        self.assertEqual(0o600, path.stat().st_mode & 0o777)
        with self.assertRaises(ValueError):
            setup.register_runtime('ubuntu; arbitrary-command', path)
        self.assertEqual(data, json.loads(path.read_text()))

if __name__ == '__main__':
    os.umask(0o077)
    unittest.main()
