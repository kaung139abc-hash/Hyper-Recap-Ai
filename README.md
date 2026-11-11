# Hyper Recap AI

Android-first, offline-focused recap app for modest Android phones (including 4 GB RAM devices).

## Implemented

- Native Kotlin Android application.
- Select a local video with Android's document picker and inspect its duration, including videos longer than two hours.
- Decode the selected video's audio stream incrementally to 16-bit PCM WAV using Android MediaCodec; it does not load the entire movie into RAM.
- Extract/remux the original encoded audio track into an M4A-compatible MP4 container without re-encoding.
- Download direct video-file URLs ending in MP4, MKV, WEBM, MOV or M4V.
- Accept shared text links from other Android apps.
- Offline extractive recap for pasted transcript/story text. It runs locally and makes no API/network request. Long transcripts are processed in 8,000-character windows and then combined into a final extractive recap.
- Save generated recap as a UTF-8 TXT file.
- Generate and export an editable SRT subtitle draft for the recap narration. Subtitle timing is estimated from text length and is not automatically synchronized to the source movie.
- Select from the TTS voices already installed in the Android speech engine, with voice locale, online/offline indication and engine quality value.
- Adjust speech rate and pitch, preview a voice, and listen to the generated recap.
- Export the generated speech to a WAV audio file using the selected Android TTS voice.
- The voice picker excludes entries whose names identify them as Thiha or Nilar.
- GitHub Actions builds a debug APK on every push to `main` and uploads it as an artifact.

## Current limitations — important

This is not yet a complete automatic video-to-recap pipeline. Selecting a video reads its metadata. The PCM WAV button decodes the audio stream to a standard 16-bit PCM WAV, and the M4A button saves the original encoded audio stream. Neither button transcribes speech yet. The offline recap engine summarizes text supplied by the user. Automatic local speech-to-text (for example, a quantized Whisper.cpp model), Myanmar translation, source-aligned subtitle timing, and rendered MP4 export are not implemented yet. The app does not currently turn a selected movie or a social-media page link directly into a finished recap. These require an actual on-device ASR model/runtime, translation capability, media rendering pipeline, and device testing. The current SRT export is a narration-timing draft only. The long-transcript recap improvement is extractive text processing, not AI speech recognition or generative summarization. PCM WAV conversion is only the audio-preparation stage, not speech recognition. Speech output currently uses the Android TTS engine and its installed voices; it does not bundle a new neural voice model. Voice choice and Burmese-language availability depend on the TTS engine and voice data installed on the phone. Some voices require internet access; choose an Offline-labelled voice for offline playback. WAV export uses the same selected system voice and therefore inherits its language, quality and offline/online limitations.

A no-API/no-daily-quota design avoids paid service limits, but local processing is bounded by phone RAM, storage, battery and speed. Long videos must be processed in chunks. A 4 GB RAM device needs small quantized models.

YouTube, Facebook, TikTok and Instagram page URLs are not equivalent to direct video-file URLs. Downloading them is not guaranteed; access, login, platform restrictions and copyright permissions apply. Use video files you own or are authorized to process.

## Build and download APK

1. Open the repository's **Actions** tab.
2. Select **Android APK** and open the latest run for the `main` branch.
3. Wait until the run completes successfully.
4. Under **Artifacts**, download `Hyper-Recap-AI-debug` and extract `app-debug.apk`.
5. Install the APK on Android. If Android asks, allow installation from that file manager/browser.

The workflow can also be started manually with **Actions → Android APK → Run workflow**.

## Source-timed SRT recap

When an SRT subtitle file is imported, the app now parses its cue timestamps and ranks subtitle cues locally. The recap text and exported SRT are built from the same selected cues, preserving their original movie timestamps. This provides source-aligned recap subtitles when a matching SRT transcript is available. TXT transcript imports and manually pasted text still use estimated narration timing for the generated SRT.

This does not perform automatic speech recognition, translation, or render subtitles into an MP4. Those features still require dedicated offline speech/translation models and a media rendering pipeline.
