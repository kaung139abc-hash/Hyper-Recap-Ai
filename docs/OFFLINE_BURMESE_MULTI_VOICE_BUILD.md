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

The Open-Burmese-Speech repository describes the Burmese Daily Dialogue Corpus (BDDC) as approximately 22.38 hours, 24,560 WAV files, and 13 synthetic speakers, and its repository README declares CC BY 4.0:
https://github.com/thantzinphyo-gg/Open-Burmese-Speech
https://huggingface.co/datasets/thantzinphyo/Burmese-Daily-Dialogue-Corpus

This is a promising starting point for several synthetic voices, but it is NOT approved for shipping yet. Before training or redistribution, verify the Hugging Face dataset card, the actual audio/transcript fields, the provenance and terms of the synthetic voices, and that CC BY 4.0 applies to every audio file and speaker identity. Keep attribution with the resulting model. If any of those checks fail, do not use that corpus.

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
