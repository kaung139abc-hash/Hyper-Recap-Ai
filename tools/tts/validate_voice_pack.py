#!/usr/bin/env python3
"""Validate a distributable offline Burmese sherpa-onnx voice-pack manifest.

Manifest format:
{
  "id": "my_voice_01",
  "display_name": "Burmese Voice 01",
  "language": "my",
  "sample_rate": 22050,
  "speaker_id": 0,
  "license": "CC-BY-4.0",
  "attribution": "Required attribution text",
  "dataset_source": "https://example.org/source",
  "assets": {
    "model": {"path": "model.onnx", "sha256": "<64 hex chars>"},
    "tokens": {"path": "tokens.txt", "sha256": "<64 hex chars>"}
  }
}

This validates packaging metadata and file integrity, NOT legal rights,
speaker consent, model quality, or Android runtime compatibility.
"""
import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

SHA256_RE = re.compile(r"^[0-9a-fA-F]{64}$")
VALID_SAMPLE_RATES = {16000, 22050, 24000}


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path, help="Voice-pack JSON manifest")
    parser.add_argument("--root", type=Path, default=None,
                        help="Asset root (default: manifest directory)")
    parser.add_argument("--allow-license", action="append", default=[],
                        help="Exact SPDX/license identifier approved by your legal review; repeatable")
    args = parser.parse_args()

    try:
        data = json.loads(args.manifest.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        print(f"FAIL: cannot read JSON manifest: {exc}", file=sys.stderr)
        return 1

    errors = []
    for key in ("id", "display_name", "attribution", "dataset_source", "license"):
        if not isinstance(data.get(key), str) or not data[key].strip():
            errors.append(f"missing/empty required field: {key}")
    if data.get("language") != "my":
        errors.append("language must be exactly 'my'")
    if type(data.get("sample_rate")) is not int or data["sample_rate"] not in VALID_SAMPLE_RATES:
        errors.append(f"sample_rate must be one of {sorted(VALID_SAMPLE_RATES)}")
    if type(data.get("speaker_id")) is not int or data["speaker_id"] < 0:
        errors.append("speaker_id must be a non-negative integer")
    if not args.allow_license:
        errors.append("no license approval supplied; pass --allow-license only after rights review")
    elif data.get("license") not in args.allow_license:
        errors.append(f"license '{data.get('license')}' is not in the explicitly approved allow-list")

    assets = data.get("assets")
    if not isinstance(assets, dict):
        errors.append("assets must be an object containing model and tokens")
        assets = {}
    root = (args.root or args.manifest.parent).resolve()
    for name in ("model", "tokens"):
        item = assets.get(name)
        if not isinstance(item, dict):
            errors.append(f"assets.{name} must contain path and sha256")
            continue
        rel = item.get("path")
        expected = item.get("sha256")
        if not isinstance(rel, str) or not rel.strip():
            errors.append(f"assets.{name}.path is missing")
            continue
        candidate = (root / rel).resolve()
        if candidate != root and root not in candidate.parents:
            errors.append(f"assets.{name}.path escapes asset root")
            continue
        if not candidate.is_file():
            errors.append(f"assets.{name} file not found: {rel}")
            continue
        if not isinstance(expected, str) or not SHA256_RE.fullmatch(expected):
            errors.append(f"assets.{name}.sha256 must be 64 hexadecimal characters")
            continue
        actual = sha256_file(candidate)
        if actual.lower() != expected.lower():
            errors.append(f"assets.{name} SHA-256 mismatch: expected {expected}, got {actual}")

    if errors:
        print(f"FAIL: {len(errors)} issue(s)")
        for error in errors:
            print(" - " + error)
        return 1
    print("PASS: required manifest fields, approved license identifier, assets and SHA-256 checks passed.")
    print("IMPORTANT: this is not proof of legal rights, consent, voice quality, or device compatibility.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
