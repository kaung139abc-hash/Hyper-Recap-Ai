# Commercial Release Audit

Status date: 2026-11-19  
Project: Hyper Recap AI  
Target: Android ARM64, including Redmi 13R / Android 14 / 4 GB RAM

This checklist is a release gate, not legal advice or a claim that the app is commercially cleared. Do not publish, sell, or bundle an asset until its exact license, provenance, attribution and redistribution rights have been reviewed.

## Current blockers

| Area | Current evidence | Commercial release decision |
|---|---|---|
| Burmese neural voices | Meta MMS Burmese weights are CC BY-NC 4.0. | BLOCKED for commercial use unless separate written permission is obtained. Do not bundle. |
| Multi-speaker Burmese voice pack | No independently trained, rights-cleared male/female/narrator model has been trained, converted and tested. | BLOCKED. Android system TTS may be offered only subject to the TTS provider's terms and installed voice availability. |
| BDDC speech corpus | Upstream project describes CC BY 4.0 and synthetic speakers, but exact revision, per-file provenance and speaker/voice rights are not verified. | RESEARCH ONLY. Do not train or ship commercially until the full chain of rights is documented. |
| Qwen3-0.6B GGUF | Upstream repository identifies Apache-2.0. Exact model revision and required notices must be pinned and retained. | CONDITIONAL. Re-check upstream license and model card at the exact revision used; include notices. |
| sherpa-onnx AAR | Local binary at app/libs/sherpa-onnx.aar. | BLOCKED pending identification of exact version, source commit, license and bundled native notices. |
| Llamatik | Gradle dependency com.llamatik:library:1.10.1; project currently records MIT. | CONDITIONAL. Verify exact artifact license and all transitive/native components; retain notices. |
| ML Kit Image Labeling | Google ML Kit dependency. | CONDITIONAL. Review current Google SDK/service terms and required privacy disclosures for the exact bundled/offline model behavior. |
| Media3 / AndroidX / Commons Compress / Commons IO / Kotlin / Android Gradle Plugin | Declared Gradle dependencies. | CONDITIONAL. Generate a complete dependency and license inventory for the release variant, including transitive dependencies. |
| Application source | No root LICENSE file was found at audit time. | Decide whether the app is proprietary or open source; add appropriate copyright/license notices before distribution. |
| Movie/video content | Users may process locally selected files. | Require users to have rights to process content; do not market the app as a way to bypass DRM, platform restrictions or copyright. Review applicable copyright and platform rules for recap exports. |
| Cloud features | Optional cloud recap/translation/voice paths may send transcript/text to a configured provider and may incur quotas or billing. | Off by default for a free/offline commercial promise unless provider terms, pricing, privacy, retention and user consent are addressed. |

## Required release work

### 1. Software and model supply chain
- Pin every model to an immutable upstream revision and record SHA-256.
- Produce a release-variant SBOM and third-party notices, including native libraries inside AARs.
- Keep copies of license texts, model cards, attribution and source URLs for every distributed asset.
- Check whether each license permits commercial use, modification, redistribution, and app-store bundling.
- Do not assume that a public download, a permissive repository license, or a dataset-level label clears every recording, speaker identity, or model weight.
- Keep non-commercial assets out of commercial builds.

### 2. Burmese voice rights and quality
- Obtain documented commercial training and redistribution rights for every recording and speaker/voice identity.
- Preserve speaker-level provenance and consent/rights records.
- Train or obtain a commercially permitted model; do not use the CC BY-NC MMS weights for the commercial build.
- Validate Burmese pronunciation, naturalness, speaker distinction, long-form stability and offline operation with native listeners.
- Test model memory, speed, thermal behavior, WAV and MP4 export on the target phone.

### 3. Product, privacy and customer commitments
- Publish a privacy policy that accurately distinguishes local processing from optional cloud processing.
- Disclose what transcript/audio/video data leaves the device, why, where it is processed, retention and deletion behavior.
- Make cloud features opt-in and clearly disclose any provider costs or usage limits.
- Add clear user terms covering content rights, acceptable use, output limitations and support/refund policy.
- Review Google Play requirements, data safety declarations, account/payment requirements and regional consumer/privacy laws before launch.

### 4. Technical acceptance
- Install and test the signed release APK on the Redmi 13R (Android 14, 4 GB RAM).
- Run short, medium and 2+ hour real-video tests; record success rate, peak RAM, disk use, time, heat and battery impact.
- Verify English and Burmese transcription, story chronology, character consistency, climax and ending accuracy against human-reviewed references.
- Test cancellation, app process death, low storage, low RAM, corrupted model downloads and recovery.
- Test exported MP4 playback, audio/video sync, subtitle timing, rotations and common codecs.
- Do not advertise 2-hour offline processing, multi-voice narration, or full visual story understanding until measured and verified.

## No-paid-services constraint
The project requirement is to prioritize free/open-source tooling and avoid paid engineers, datasets and APIs. Free GPU notebook sessions are not guaranteed. Commercial rights clearance may require permission or legal review; if a required right cannot be obtained for free, do not silently substitute an unlicensed asset. Keep that feature out of the commercial release until a lawful option is available.

## Decision rule
A green Android CI build is not commercial clearance. Release only when all bundled software/model assets have documented commercial rights, privacy/product requirements are met, and the signed app passes physical-device acceptance testing.
