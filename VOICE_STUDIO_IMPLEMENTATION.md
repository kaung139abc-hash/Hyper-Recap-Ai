# Voice Studio AI — implementation status

## Product constraints
- Android app, offline-first, no paid API, no cloud TTS, no API key.
- Prioritize free/open-source components and disclose model/license restrictions.
- Never describe an imported reference recording as voice cloning.
- Only enable cloning for a speaker who owns the voice or has explicitly authorized its use.

## Implemented in the current branch
- [x] Voice Studio app shell and Burmese-friendly text editor.
- [x] Android system TTS fallback with installed-voice selection, speed and pitch controls.
- [x] Local workspace persistence for draft text, selected voice, speed, pitch, and reference-sample status.
- [x] Playback errors are reported separately from WAV export errors.
- [x] Long-text chunked speech playback.
- [x] WAV export through Android TTS and PCM chunk merger with format validation.
- [x] Import and private local storage of a reference audio sample (storage only; no cloning inference).
- [x] Custom adaptive launcher icon.
- [x] GitHub Actions debug/release build and JVM test workflow.

## Not implemented / not verified
- [ ] A redistributable, commercially usable Burmese neural TTS model.
- [ ] Local Burmese phonemizer/tokenizer and model package management.
- [ ] Actual voice cloning inference, speaker embedding, and saved clone profiles.
- [ ] MP3 encoding/export and video muxing.
- [ ] On-device installation and long-duration performance testing on the target phone.

## Burmese model and licensing gate
The previous audit found the known Myanmar MMS ONNX model marked CC BY-NC 4.0, which is not suitable for commercial redistribution without separate written permission. Other inspected candidates either have non-commercial restrictions, are online-only, lack a declared license, or are datasets rather than ready-to-run models. Therefore this branch does not bundle or silently download those weights, and does not claim neural Burmese speech or cloning is available.

A compliant model can be integrated only after verifying the exact model weights, inference code, tokenizer/phonemizer, dataset and speaker rights, and redistribution terms. The app must then download a versioned package with checksums, run inference fully offline, and pass Burmese native-speaker quality and ARM64 device tests.

## Release acceptance criteria
- Airplane-mode synthesis after model installation.
- No paid service, API key, or runtime cloud dependency.
- Valid PCM WAV output for long narration, cancellation and recovery.
- Model/license/attribution files included with each model package.
- Voice cloning limited to self-owned or explicitly authorized voices.
- Install and test the APK on the target Android phone before calling it production-ready.
