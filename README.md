# Hyper Recap AI

Android-first movie recap app targeting ARM64 Android devices. The current implementation is an experimental, offline-first pipeline; it is **not yet validated as production-ready on a physical Redmi 13R (4 GB RAM)**.

## Current implementation

### Local video-to-recap pipeline

1. Select a local video using Android's document picker, or share an authorized local video file into the app.
2. Decode the selected video's audio incrementally with Android MediaCodec into PCM WAV. The app does not intentionally load the complete movie into RAM.
3. Transcribe audio locally with sherpa-onnx Whisper Base int8. The first use downloads the model; after it is present, transcription can run offline. Audio is decoded in short windows to bound temporary memory.
4. Sample up to 24 low-resolution frames across the video and classify visible objects/scenes with the bundled on-device ML Kit image-labeling model. No frames are uploaded by this local analyzer.
5. Generate a Burmese story recap locally with Qwen3-0.6B Q4_K_M GGUF through llama.cpp/Llamatik. The first use downloads the model; subsequent inference can run offline.
6. Save recap text, generate SRT subtitles, and export subtitle-burned or narrated MP4 files where the device's codecs and installed Burmese TTS voice support the requested export.

The visual classifier supplies timestamped object/scene labels only. It is **not** a video-language model and does not understand character identity, dialogue, motives, relationships, or plot. When it cannot label frames, transcript-based recap continues and the result is not a full multimodal understanding.

### Audio, subtitles and export

- Whisper transcription retains approximate timestamped segments. Timing quality depends on the model/runtime output; it is not guaranteed word-level alignment.
- Audio extraction writes PCM WAV to app cache. Long films can require hundreds of megabytes of temporary storage; for example, two hours of 48 kHz mono 16-bit PCM is about 659 MiB before other temporary files.
- SRT export is supported. Subtitle burn-in re-encodes the video and can be slow, heat the phone, and consume substantial battery/storage.
- Narrated MP4 export depends on an offline Burmese voice being installed in the Android TTS engine, or a separately configured online voice provider. The app does not guarantee that every Android device includes a Burmese offline voice.
- Voice chunking is used for long narration, but export still needs device-level testing with real files and codecs.

### Burmese voice status

- The current built-in Android voice picker only offers Burmese (my) voices reported by the installed TTS engine and excludes network-required voices.
- The app does **not** currently ship a verified, commercial-use, multi-speaker Burmese neural TTS model.
- The experimental Meta MMS Burmese model is CC BY-NC 4.0 and is not enabled for commercial use.
- A release voice pack must pass the repository's metadata/path/checksum validator and an explicit license allow-list. That validator does not itself prove copyright ownership, speaker consent, dataset provenance, voice quality, or Android compatibility.
- No independent male/female/narrator Burmese neural voices have been trained and device-tested yet.

## Device and performance limitations

The target test device is Redmi 13R, ARM64, 4 GB RAM. The app uses CPU inference; the phone's Mali GPU is not an NVIDIA CUDA device. Qwen and Whisper run sequentially to reduce peak memory, but available RAM varies by Android version, background apps, thermal state and model mapping. Long-video support in code is not proof that every two-hour video will finish successfully on this device.

For long processing:
- Keep sufficient free internal storage.
- Close memory-heavy background apps.
- Keep the phone cool and preferably connected to power.
- Do not force-stop the app while processing.
- Test with a short local video first, then increase duration gradually.

## Online features and privacy

Optional Cloudflare Worker features may be present in the app for cloud recap, translation or commercial voice synthesis. Those paths require network access and server configuration and may be subject to account quotas or billing. They are separate from the local Whisper/Qwen pipeline. Do not use cloud actions for private media unless you explicitly accept sending the relevant transcript/text to the configured service. The local visual analyzer does not upload frames.

Social-media page links are not direct video files. The app opens the relevant official app/browser; users must use a platform-authorized save/download method and have rights to process the resulting file. The app does not bypass login, DRM, or platform restrictions.

## Build and automated checks

GitHub Actions workflow: [Android APK](https://github.com/kaung139abc-hash/Hyper-Recap-Ai/actions).

The workflow builds debug and release variants, runs JVM tests, validates the offline voice-pack metadata tool, and verifies the signed release APK when signing secrets are configured. A green CI run verifies the build and automated tests only. It does **not** verify app behavior on a physical phone, Burmese recap quality, 2-hour processing, or MP4 playback.

The active development pull request is [PR #1](https://github.com/kaung139abc-hash/Hyper-Recap-Ai/pull/1). Its workflow artifacts are downloadable from the relevant Actions run; they are not the same as a merged public release.

## License and model notices

- Qwen3-0.6B GGUF weights: Apache-2.0 as identified by the upstream model repository; verify the exact upstream revision before redistribution.
- sherpa-onnx and the other Android dependencies retain their respective upstream licenses.
- Meta MMS Burmese TTS: CC BY-NC 4.0; not for commercial redistribution/use without separate permission.
- Dataset and speaker rights must be checked independently before training or distributing any Burmese voice model.

## Honest release checklist

The following remain required before calling this production-ready:

- Install and launch the signed APK on Redmi 13R / Android 14.
- Run short, medium and 2+ hour video tests and record peak RAM, duration, heat and storage use.
- Verify Burmese and English speech recognition on real movie audio.
- Evaluate Qwen Burmese story accuracy, chronology, character consistency and ending fidelity against human-written references.
- Verify MP4 exports on the target phone and other common Android codecs/rotations.
- Provide a rights-cleared Burmese offline TTS pack, then test male/female/narrator voices and long-form WAV/MP4 exports.
- Add lifecycle-safe cancellation/recovery and verify that interrupted processing cleans temporary files.
