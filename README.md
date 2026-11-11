# Hyper Recap AI

Android-first, offline-focused recap app for modest Android phones (including 4 GB RAM devices).

## Implemented

- Native Kotlin Android application.
- Select a local video with Android's document picker and inspect its duration, including videos longer than two hours.
- Download direct video-file URLs ending in MP4, MKV, WEBM, MOV or M4V.
- Accept shared text links from other Android apps.
- Offline extractive recap for pasted transcript/story text. It runs locally and makes no API/network request.
- Save generated recap as a UTF-8 TXT file.
- Select from the TTS voices already installed in the Android speech engine, with voice locale, online/offline indication and engine quality value.
- Adjust speech rate and pitch, preview a voice, and listen to the generated recap.
- Export the generated speech to a WAV audio file using the selected Android TTS voice.
- The voice picker excludes entries whose names identify them as Thiha or Nilar.
- GitHub Actions builds a debug APK on every push to `main` and uploads it as an artifact.

## Current limitations — important

This is not yet a complete automatic video-to-recap pipeline. Selecting a video currently reads its metadata; it does **not** extract its soundtrack or transcribe speech. The offline recap engine summarizes text supplied by the user. Automatic local speech-to-text (for example, a quantized Whisper.cpp model), translation, subtitle timing and rendered MP4 export remain future implementation and device testing work. Speech output currently uses the Android TTS engine and its installed voices; it does not bundle a new neural voice model. Voice choice and Burmese-language availability depend on the TTS engine and voice data installed on the phone. Some voices require internet access; choose an Offline-labelled voice for offline playback. WAV export uses the same selected system voice and therefore inherits its language, quality and offline/online limitations.

A no-API/no-daily-quota design avoids paid service limits, but local processing is bounded by phone RAM, storage, battery and speed. Long videos must be processed in chunks. A 4 GB RAM device needs small quantized models.

YouTube, Facebook, TikTok and Instagram page URLs are not equivalent to direct video-file URLs. Downloading them is not guaranteed; access, login, platform restrictions and copyright permissions apply. Use video files you own or are authorized to process.

## Build and download APK

1. Open the repository's **Actions** tab.
2. Select **Android APK** and open the latest run for the `main` branch.
3. Wait until the run completes successfully.
4. Under **Artifacts**, download `Hyper-Recap-AI-debug` and extract `app-debug.apk`.
5. Install the APK on Android. If Android asks, allow installation from that file manager/browser.

The workflow can also be started manually with **Actions → Android APK → Run workflow**.