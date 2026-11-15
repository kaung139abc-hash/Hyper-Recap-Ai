# Voice Studio AI

Android-first, offline-first Text-to-Speech and voice studio.

> Status: migration in progress. The app screen has been replaced with a first local TTS workspace and the Android package identity has been changed. A successful APK build and on-device test have not yet been confirmed.

## Product goals

- Local text-to-speech with no API key, subscription, or per-character charge.
- Long-form narration using bounded text/audio chunks and resumable export.
- Voice library with user-created voice profiles.
- Voice cloning from recordings the user owns or has permission to use.
- Natural narration controls: pace, pitch, pauses, emotion/style where the selected model genuinely supports them.
- WAV export first; MP3 export only after a properly licensed local encoder is included.
- Myanmar/Burmese support is a priority, but quality must be verified with actual Burmese samples before it is advertised as natural or human-like.
- Android phone support, with memory-aware model loading and clear storage/RAM requirements.

## Non-negotiable constraints

- Prefer free and open-source components.
- No paid APIs, cloud quotas, or mandatory accounts.
- No hidden network inference. Downloading a model is a one-time optional setup; synthesis should run locally afterwards.
- “Unlimited” means no provider quota or per-use fee. Actual output remains limited by device RAM, compute speed, battery, heat, and storage.
- Only integrate models whose code, weights, and voice-cloning terms permit the intended use. A permissive code license does not automatically make pretrained weights commercially usable.

## Implemented in the current migration branch\n\n- Replaced the Movie Recap main screen with a Voice Studio screen.\n- Text editor, installed Android TTS voice picker, speed and pitch controls.\n- Chunked text playback to avoid the single-call TTS input limit.\n- Chunked WAV export using the installed Android TTS engine.\n- Import and private-storage saving of a user-provided reference audio sample. This is only reference-file storage, NOT voice cloning.\n- Explicit UI disclosure that a real voice-cloning engine and dedicated Burmese neural voice are not integrated yet.\n\n## Planned modules

1. **Studio** — text editor, character count, chunked synthesis queue, progress, pause/resume, playback.
2. **Voice Library** — installed local voices, saved voice profiles, sample playback, import/delete.
3. **Voice Clone** — recording/import wizard, consent confirmation, reference audio validation, local embedding/model workflow.
4. **Voice Controls** — speed, pitch, pauses, and supported style/emotion controls.
5. **Exports** — WAV output, file naming, share/save, batch export.
6. **Model Manager** — model download, license/source details, size, device compatibility, offline status.
7. **Diagnostics** — model/runtime checks and clear errors rather than fake success states.

## Engineering gates

- Do not label a voice “voice cloned” until a real speaker-conditioning/voice-conversion pipeline is integrated and tested.
- Do not claim Burmese naturalness until native-speaker listening tests pass.
- Do not claim unlimited output without explaining device resource limits.
- Keep model downloads separate from the APK; show size and license before download.
- Never bundle non-commercial-only weights in a commercial build without permission.

## Current implementation baseline

This branch was created from the previous Hyper Recap AI Android project to reuse the Android build pipeline and preserve the previous project history. The previous Movie Recap code is retained on the `movie-recap-archive` branch. The Voice Studio app migration and feature implementation are in progress; existing recap screens must not be represented as completed Voice Studio features.
