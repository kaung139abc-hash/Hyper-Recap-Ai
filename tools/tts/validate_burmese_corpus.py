#!/usr/bin/env python3
"""Validate a CSV manifest for a Burmese multi-speaker TTS corpus.

Expected columns: audio_path,text,speaker_id
Uses only Python's standard library. WAV files must be PCM, mono, and 16 kHz
or 22.05/24 kHz. This checks corpus hygiene, not licensing or voice quality.
"""
import argparse
import csv
import wave
from collections import Counter
from pathlib import Path

ALLOWED_RATES = {16000, 22050, 24000}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("manifest", type=Path, help="CSV with audio_path,text,speaker_id")
    parser.add_argument("--root", type=Path, default=None,
                        help="Base directory for relative audio paths (default: manifest folder)")
    parser.add_argument("--min-seconds", type=float, default=0.5)
    parser.add_argument("--max-seconds", type=float, default=15.0)
    args = parser.parse_args()

    root = args.root or args.manifest.parent
    speakers = Counter()
    errors = []
    total_seconds = 0.0
    rows = 0
    seen = set()

    with args.manifest.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        required = {"audio_path", "text", "speaker_id"}
        if not reader.fieldnames or not required.issubset(reader.fieldnames):
            raise SystemExit("CSV must contain columns: audio_path,text,speaker_id")

        for line, row in enumerate(reader, start=2):
            rows += 1
            audio_name = (row.get("audio_path") or "").strip()
            text = (row.get("text") or "").strip()
            speaker = (row.get("speaker_id") or "").strip()
            if not audio_name or not text or not speaker:
                errors.append(f"line {line}: empty audio_path, text, or speaker_id")
                continue
            key = (audio_name, speaker)
            if key in seen:
                errors.append(f"line {line}: duplicate audio_path/speaker_id: {key}")
                continue
            seen.add(key)
            audio = Path(audio_name)
            if not audio.is_absolute():
                audio = root / audio
            if not audio.is_file():
                errors.append(f"line {line}: missing audio: {audio}")
                continue
            try:
                with wave.open(str(audio), "rb") as wav:
                    channels = wav.getnchannels()
                    rate = wav.getframerate()
                    width = wav.getsampwidth()
                    frames = wav.getnframes()
                    seconds = frames / float(rate) if rate else 0.0
                    if channels != 1:
                        errors.append(f"line {line}: expected mono, got {channels} channels")
                    if rate not in ALLOWED_RATES:
                        errors.append(f"line {line}: unsupported sample rate {rate}")
                    if width not in (2, 3):
                        errors.append(f"line {line}: expected 16- or 24-bit PCM, got {width * 8}-bit")
                    if not args.min_seconds <= seconds <= args.max_seconds:
                        errors.append(f"line {line}: duration {seconds:.2f}s outside "
                                      f"{args.min_seconds:.2f}-{args.max_seconds:.2f}s")
                    if frames <= 0:
                        errors.append(f"line {line}: empty WAV")
                    total_seconds += seconds
                    speakers[speaker] += 1
            except (wave.Error, EOFError, OSError) as exc:
                errors.append(f"line {line}: unreadable WAV: {exc}")

    print(f"Rows: {rows}")
    print(f"Valid speaker/audio rows: {sum(speakers.values())}")
    print(f"Distinct speakers: {len(speakers)}")
    print(f"Total inspected duration: {total_seconds / 3600:.2f} hours")
    for speaker, count in sorted(speakers.items()):
        print(f"  {speaker}: {count} clips")
    if errors:
        print(f"\nFAILED: {len(errors)} issue(s)")
        for error in errors[:100]:
            print(" - " + error)
        if len(errors) > 100:
            print(f" ... and {len(errors) - 100} more")
        raise SystemExit(1)
    print("\nPASS: basic corpus structure is valid.")
    print("NOTE: this does not establish dataset/speaker rights or TTS quality.")


if __name__ == "__main__":
    main()
