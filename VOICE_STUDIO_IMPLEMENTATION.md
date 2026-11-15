# Voice Studio AI — implementation checklist

## Phase 1 — app shell
- [ ] Replace recap screen and navigation with Studio / Voice Library / Voice Clone / Model Manager.
- [ ] Remove recap, video, subtitle, transcript, and license-server flows from the user-facing app.
- [ ] Rename launcher/app metadata and accessibility labels.
- [ ] Keep model files out of the APK; use app-private storage and resumable downloads.

## Phase 2 — local TTS
- [ ] Select a local TTS runtime and a model whose code AND weights license permit the intended distribution/use.
- [ ] Verify Burmese grapheme/token handling, punctuation, numerals, and long text.
- [ ] Implement bounded chunk synthesis, sentence-aware pauses, WAV writer, playback, and cancellation.
- [ ] Run on-device performance, memory, thermal, and long-output tests.

## Phase 3 — voice cloning
- [ ] Select a genuinely open voice-cloning model/runtime suitable for Android ARM64.
- [ ] Confirm model-weight license and attribution obligations before integrating.
- [ ] Add user-consent flow for self-owned/authorized reference audio.
- [ ] Implement reference-audio capture/import, preprocessing, speaker embedding/conditioning, and saved profiles.
- [ ] Test similarity, intelligibility, Burmese pronunciation, and model limitations.

## Phase 4 — quality and release
- [ ] Add MP3 export only with a compatible licensed encoder.
- [ ] Add batch jobs, resumable queue, storage management, and crash recovery.
- [ ] Build debug APK in GitHub Actions.
- [ ] Install and test on the target Android phone before calling the APK usable.
- [ ] Publish release only after the above checks pass.

## Current truth
No integrated local Burmese voice-cloning engine has been verified in this project yet. Android system TTS can serve as a fallback, but it is not equivalent to natural neural speech or voice cloning. The previous MMS Myanmar model in the old project is CC BY-NC 4.0 and is not suitable for commercial redistribution without separate permission.
