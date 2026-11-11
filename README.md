# Hyper Recap AI

Android-first, offline-focused movie recap project for the Redmi 13R 5G (4 GB RAM).

## Current prototype

- Native Android app (Kotlin).
- Selects a video using Android's document picker.
- Reads and displays the selected video's duration, including videos longer than two hours.
- GitHub Actions workflow builds a debug APK and uploads it as an artifact.

## Build

The GitHub Actions workflow runs on every push to `main`, or manually from **Actions → Android APK → Run workflow**.

When the workflow succeeds, open its run, scroll to **Artifacts**, and download `Hyper-Recap-AI-debug`. Extract the ZIP to get `app-debug.apk`.

## Important status

This is the initial app foundation, not a completed AI recap engine yet. Local scene analysis, speech recognition, summarization, Burmese translation/TTS, subtitle generation and MP4 export still need to be implemented and tested separately. A 4 GB RAM phone requires quantized, memory-bounded models and chunked processing; processing speed depends on video resolution, codec and device thermals.
