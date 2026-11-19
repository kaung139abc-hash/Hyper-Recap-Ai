# Offline Burmese Multi-Voice TTS Build

## Target

Build a Burmese neural TTS package that is owned and shipped by Hyper Recap AI:
- multiple distinct synthetic Burmese voices (male/female voice styles where the corpus supports them);
- Android ARM64 inference with sherpa-onnx/ONNX;
- no ElevenLabs, Edge TTS, cloud inference, API key, or network call during synthesis;
- model files downloaded once, then usable in airplane mode;
- commercial redistribution only after every dataset, speaker, model-weight, and runtime license is checked.

This document is an implementation plan, not a claim that trained voice weights already exist.

## Dataset candidate under review

The Open-Burmese-Speech repository describes the Burmese Daily Dialogue Corpus (BDDC) as approximately 22.38 hours, 24,560 WAV files, and 13 synthetic speakers. Its repository README states CC BY 4.0, but the project's existing licensing audit correctly notes that this is not sufficient evidence that the exact Hugging Face dataset revision, every audio file, transcript, and synthetic speaker identity can be redistributed or used to train a commercial model:
https://github.com/thantzinphyo-gg/Open-Burmese-Speech
https://huggingface.co/datasets/thantzinphyo/Burmese-Daily-Dialogue-Corpus

BDDC is therefore a research candidate only, NOT an approved training or shipping source. Before use, inspect the exact dataset card and files, verify the license applies to all records and voice identities, check source/provenance and any third-party restrictions, and retain required attribution. If those checks cannot be established, do not use the corpus for the commercial app. The validator added in this repository checks audio/manifest hygiene only; it cannot establish legal rights.

## Training pipeline

1. Download/stream the candidate corpus on a free GPU notebook or other free local GPU. No paid dataset, paid API, or hired engineer is required. Free GPU availability and session duration are not guaranteed.
2. Run `tools/tts/validate_burmese_corpus.py` on a CSV manifest with columns `audio_path,text,speaker_id`. The script reports per-speaker counts and rejects missing, empty, unreadable, or unsuitable WAV records.
3. Split by speaker and utterance into train/validation sets. Normalize Burmese Unicode consistently; do not transliterate or silently rewrite the transcript.
4. Train a multi-speaker VITS-family acoustic model using only a training framework whose license is compatible with the app. Preserve speaker IDs; do not merge voices into one speaker.
5. Evaluate each speaker with native Burmese listeners for consonants, tone, word boundaries, sentence prosody, and long-form narration. A model checkpoint is not a release-quality voice.
6. Export each verified speaker/model to an Android-compatible ONNX package and record model SHA-256, sample rate, tokenizer/phoneme assets, speaker ID, attribution, and license.
7. Add only validated model packs to the app. Run offline synthesis, memory, long narration, WAV, and narrated-MP4 tests on the Redmi 13R (4 GB RAM). Do not ship a placeholder voice pack.

## Model pack contract

Each installed voice pack must have a manifest with:
- `id`: stable unique identifier;
- `display_name`: user-facing voice name;
- `language`: `my`;
- `speaker_id`: model speaker index, if applicable;
- `sample_rate`: actual model output sample rate;
- `model_file`, `tokens_file`, and any required lexicon/phoneme files;
- `sha256`: checksum for every downloaded asset;
- `license`, `attribution`, and `dataset_source`.

The app must not infer commercial rights from a model being downloadable or labelled open source. Missing license metadata means the pack is not installable.

## Current status (2026-11-19)

- Existing MMS Myanmar VITS weights are CC BY-NC 4.0 and are not suitable for commercial app distribution; keep them disabled.
- The optional ElevenLabs/online provider path is not the target architecture and must not be used by the offline voice feature.
- BDDC is a candidate corpus only; exact dataset-card/audio provenance checks remain pending.
- No new multi-speaker Burmese model weights have been trained, converted, or device-tested yet.
- Redmi 13R has 4 GB RAM; benchmark one voice/model at a time before considering simultaneous model loading.

## Voice-pack release gate

Before packaging a trained model, create a JSON manifest using the schema documented
in `tools/tts/validate_voice_pack.py`. Run:

```bash
python tools/tts/validate_voice_pack.py path/to/voice-pack/manifest.json \
  --root path/to/voice-pack --allow-license "EXACT-REVIEWED-LICENSE-ID"
```

The allow-list is deliberately supplied by the release operator after a separate
rights review; the script does not decide whether a license or dataset is legally
suitable. It verifies required metadata, Burmese language tag, supported sample
rate, speaker ID, asset containment, and SHA-256 integrity. It does not verify
speaker consent, dataset provenance, commercial rights, model quality, or runtime
compatibility. Never add a license to the allow-list merely to make validation pass.

