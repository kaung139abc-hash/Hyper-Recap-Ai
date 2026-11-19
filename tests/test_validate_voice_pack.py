import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "tools" / "tts" / "validate_voice_pack.py"


class VoicePackValidatorTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        (self.root / "model.onnx").write_bytes(b"test-model-bytes")
        (self.root / "tokens.txt").write_text("token\n", encoding="utf-8")
        self.manifest = {
            "id": "my_voice_01",
            "display_name": "Burmese Voice 01",
            "language": "my",
            "sample_rate": 22050,
            "speaker_id": 0,
            "license": "CC-BY-4.0",
            "attribution": "Test attribution",
            "dataset_source": "https://example.org/dataset",
            "assets": {
                "model": {
                    "path": "model.onnx",
                    "sha256": hashlib.sha256(b"test-model-bytes").hexdigest(),
                },
                "tokens": {
                    "path": "tokens.txt",
                    "sha256": hashlib.sha256(b"token\n").hexdigest(),
                },
            },
        }
        self.manifest_path = self.root / "manifest.json"

    def tearDown(self):
        self.temp.cleanup()

    def run_validator(self, *extra):
        self.manifest_path.write_text(
            json.dumps(self.manifest), encoding="utf-8"
        )
        return subprocess.run(
            [sys.executable, str(SCRIPT), str(self.manifest_path),
             "--root", str(self.root), *extra],
            text=True, capture_output=True, check=False,
        )

    def test_valid_pack_passes_with_explicit_license_allowlist(self):
        result = self.run_validator("--allow-license", "CC-BY-4.0")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("PASS:", result.stdout)

    def test_no_license_allowlist_fails_closed(self):
        result = self.run_validator()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("no license approval supplied", result.stdout)

    def test_unapproved_license_fails(self):
        result = self.run_validator("--allow-license", "MIT")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("not in the explicitly approved allow-list", result.stdout)

    def test_modified_asset_fails_checksum(self):
        (self.root / "model.onnx").write_bytes(b"tampered")
        result = self.run_validator("--allow-license", "CC-BY-4.0")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("SHA-256 mismatch", result.stdout)

    def test_path_traversal_is_rejected(self):
        self.manifest["assets"]["model"]["path"] = "../outside.onnx"
        result = self.run_validator("--allow-license", "CC-BY-4.0")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("escapes asset root", result.stdout)

    def test_non_burmese_language_is_rejected(self):
        self.manifest["language"] = "en"
        result = self.run_validator("--allow-license", "CC-BY-4.0")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("language must be exactly 'my'", result.stdout)


if __name__ == "__main__":
    unittest.main()
