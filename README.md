# Hyper Recap AI

Android-first, offline-focused recap app for modest Android phones (including 4 GB RAM devices).

## Implemented

- Native Kotlin Android application.
- Select a local video with Android's document picker and inspect its duration, including videos longer than two hours.
- Decode the selected video's audio stream incrementally to 16-bit PCM WAV using Android MediaCodec; it does not load the entire movie into RAM.
- On-device multilingual Whisper Base int8 speech-to-text through sherpa-onnx. The first run downloads the model archive (about 208 MB); after installation it can transcribe offline. Audio is processed in 28-second windows to bound temporary memory, and approximate source-window segment timestamps are retained in the generated SRT. Burmese/Myanmar is among Whisper's supported languages; recognition quality depends on audio clarity and the selected movie.
- Extract/remux the original encoded audio track into an M4A-compatible MP4 container without re-encoding.
- Download direct video-file URLs ending in MP4, MKV, WEBM, MOV or M4V.
- Accept shared text links from other Android apps.
- Offline extractive recap for pasted transcript/story text. It runs locally and makes no API/network request. Long transcripts are processed in 8,000-character windows and then combined into a final extractive recap.
- Save generated recap as a UTF-8 TXT file.
- Generate and export SRT subtitles. Whisper transcript SRT uses estimated sentence timings distributed within source audio windows; imported SRT retains its original cue times. Manual text recap SRT uses estimated narration timing.
- Burn the active SRT cues into video frames and export a re-encoded MP4 using AndroidX Media3 Transformer.
- Select from the TTS voices already installed in the Android speech engine, with voice locale, online/offline indication and engine quality value.
- Adjust speech rate and pitch, preview a voice, and listen to the generated recap.
- Export the generated speech to a WAV audio file using the selected Android TTS voice.
- Burmese neural TTS based on MMS is intentionally disabled in the commercial app UI because its CC BY-NC 4.0 license does not permit commercial use.
- The voice picker excludes entries whose names identify them as Thiha or Nilar.
- GitHub Actions builds a debug APK on every push to `main` and uploads it as an artifact.

## Optional Cloud AI features

The app now includes two optional online features routed through the deployed Cloudflare Worker:

- **Cloud AI generative Burmese recap**: sends the transcript to Cloudflare Workers AI and asks the model to create a chronological Burmese recap grounded in the transcript.
- **English → Myanmar AI translation**: sends transcript chunks to the M2M100 translation model.

Both features require an activated license and an internet connection. They are not offline and are not unlimited: Cloudflare Workers AI has account-level usage limits and may require billing depending on the account and usage. The local Whisper transcription and local extractive recap remain available without AI inference. AI output must be reviewed for translation errors, names, and plot accuracy.

The first deployment adds an AI binding to the Worker. If inference returns an AI binding/model error, enable Workers AI for the Cloudflare account and review its current usage/billing settings in the Cloudflare dashboard.

## Current limitations — important

This is not yet a complete automatic video-to-recap pipeline. The app has a local extractive recap and optional online generative Burmese recap endpoint through Cloudflare Workers AI; the generative feature requires an activated license and internet access, and depends on the Worker AI binding, model availability and account quota/billing. English-to-Myanmar translation is also an optional online Workers AI feature, not offline translation. A selected local movie can be transcribed locally after the Whisper model is downloaded; social-media page links still require the user to open the platform and save an authorized video file first. Burn-in MP4 export is implemented with Media3 but still needs testing across phone codecs, rotations, HDR and long videos. Whisper-generated SRT estimates sentence timings inside 28-second audio windows; it is not word-level alignment. Imported SRT cues preserve their original cue timestamps. TXT/manual text still uses estimated narration timing. PCM WAV conversion is the audio-preparation stage used by the Whisper engine. Speech output supports Android-installed system voices. The MMS Burmese neural voice is disabled in the commercial UI because its upstream license is CC BY-NC 4.0 (non-commercial use only).

A no-API/no-daily-quota design avoids paid service limits, but local processing is bounded by phone RAM, storage, battery and speed. Long videos must be processed in chunks. The current Whisper Base int8 model is about 208 MB on disk and runs with two CPU threads in short audio windows. Burmese neural TTS uses the optional MMS voice model, also on CPU with two threads. A 4 GB RAM device may still need careful thermal/battery testing on long movies.

YouTube, Facebook, TikTok and Instagram page URLs are not equivalent to direct video-file URLs. Downloading them is not guaranteed; access, login, platform restrictions and copyright permissions apply. Use video files you own or are authorized to process.

## Whisper and MP4 export notes

- The first Whisper run downloads the multilingual `base` int8 model (about 208 MB) from the sherpa-onnx model release. After download, transcription runs locally without a network service.
- The upstream Meta MMS Myanmar model is licensed CC BY-NC 4.0 and is not enabled in the commercial app UI. Do not enable or distribute it for paid commercial use without separate commercial permission.
- Whisper processes the extracted PCM audio in non-overlapping 28-second windows with 2 CPU threads to limit peak memory and avoid duplicate subtitle text at chunk boundaries. Sentence cue timing is estimated within each window, not word-level alignment.
- Subtitle MP4 export re-encodes video frames and can be slow, warm the phone, and consume substantial battery on long movies. Keep the phone cool and plugged in for long exports. It preserves the source audio track through Media3's composition pipeline when the source contains a supported audio track.

## Install the latest Android APK

After a successful build on `main`, the workflow publishes the debug APK to the public rolling release:

- [Download latest Hyper Recap AI APK](https://github.com/kaung139abc-hash/Hyper-Recap-Ai/releases/tag/android-latest)

If the release has not appeared yet, wait for the latest [Android APK build](https://github.com/kaung139abc-hash/Hyper-Recap-Ai/actions) to finish successfully.

## Build and download APK manually

1. Open the repository's **Actions** tab.
2. Select **Android APK** and open the latest run for the `main` branch.
3. Wait until the run completes successfully.
4. Under **Artifacts**, download `Hyper-Recap-AI-debug` and extract `app-debug.apk`.
5. Install the APK on Android. If Android asks, allow installation from that file manager/browser.

The workflow can also be started manually with **Actions → Android APK → Run workflow**.

## Source-timed SRT recap

When an SRT subtitle file is imported, the app now parses its cue timestamps and ranks subtitle cues locally. The recap text and exported SRT are built from the same selected cues, preserving their original movie timestamps. This provides source-aligned recap subtitles when a matching SRT transcript is available. TXT transcript imports and manually pasted text still use estimated narration timing for the generated SRT.

Whisper transcription can now create these source-window-timed cues. This still does not translate the transcript. The app can render imported or generated SRT cues into an MP4 using Media3 Transformer.


## Commercial licensing foundation (v0.5.0)

The repository includes a Cloudflare Worker + D1 license service with hashed keys, 3-day trial, 30-day monthly, lifetime, device binding, revoke, extend, stats, and a browser owner dashboard at `/admin`. Android paid actions call the license service before running. The owner dashboard requires the private `ADMIN_SECRET`.

### Release signing
The Android Actions workflow supports a private release keystore. When all four secrets are configured — `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` — it builds and publishes a signed `app-release.apk`. Without them, the release build is only unsigned validation. Follow [RELEASE_SIGNING.md](RELEASE_SIGNING.md). The private keystore must be backed up permanently; future app updates need the same key.

### Remaining blockers before commercial sale
- **Production verification of generative recap and translation:** the Android client and Worker routes are implemented, but must be tested end-to-end with a real activated license and Workers AI enabled. AI inference is online and subject to account quotas/billing; it is not unlimited or offline.
- **Commercial Burmese neural TTS:** not included. MMS-TTS Myanmar is CC BY-NC 4.0 (non-commercial); its app actions are disabled. A commercially licensed Burmese voice model or a written commercial license is required.
- **Social-media page URLs:** not downloaded directly. The app opens the official app/browser. Users must supply an authorized local video file; direct downloadable video-file URLs are supported.
- **Release signing and device QA:** configure the four Android signing secrets, then install and test the signed release on real Android devices, including the target 4 GB RAM phone, long videos, codecs, battery/thermal behavior and license activation.
- **Secret rotation:** rotate the Cloudflare API token and Admin Secret that were exposed in the previous setup conversation before issuing customer licenses. Never paste secrets into chat or commit them.

Do not advertise the app as a complete generative/translation/fully offline commercial product until those features are implemented with compatible models and commercial licenses, and the signed APK has passed real-device testing.


<!-- Signing secrets configured: trigger Android APK workflow to verify signed release output. -->
