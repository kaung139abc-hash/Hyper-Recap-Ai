# Myanmar Neural TTS: model and licensing audit

Last checked: 2026-11-12

## Product requirement

Hyper Recap AI needs a Burmese neural TTS engine that:
- runs fully on-device after model download (no runtime API key or network request);
- supports commercially distributed app builds;
- produces natural Burmese narration;
- can offer more than one voice only where separately licensed voice models exist;
- can be packaged for Android/ARM64 and tested on a 4 GB RAM device.

A project calling itself "TTS" or "neural" is not sufficient. The actual inference path, model weights, training data rights, voice identity rights, and redistribution terms must all be checked independently.

## Candidates checked

| Candidate | What the source establishes | Decision |
|---|---|---|
| [hpbyte/myanmar-tts](https://github.com/hpbyte/myanmar-tts) | Tacotron-based Myanmar TTS thesis project. README declares CC BY-NC-SA 4.0 and explicitly excludes commercial use. | Reject for commercial app distribution. |
| [willwade/mms-tts-multilingual-models-onnx](https://huggingface.co/willwade/mms-tts-multilingual-models-onnx) (mya) | Existing ONNX VITS/MMS model path used by the app's experimental integration. The model is marked CC BY-NC 4.0. | Reject for commercial distribution unless the rightsholder grants a separate written commercial license. |
| [burmeseitman/myanmar-tts-pipeline](https://github.com/burmeseitman/myanmar-tts-pipeline) | The inspected engine imports `edge_tts` and streams audio from Microsoft's online Edge TTS service. It selects `my-MM-ThihaNeural` and `my-MM-NilarNeural`. | Reject: network-dependent, not on-device offline inference; also the named voices are not the requested alternative voices. |
| [htoomyintnaung/alistin-myanmar-tts](https://github.com/htoomyintnaung/alistin-myanmar-tts) | Legacy recorded-unit/J2ME speech project; repository has no declared license. | Reject: not a verified neural model and no verified commercial redistribution rights. |
| [Open-Burmese-Speech](https://github.com/thantzinphyo-gg/Open-Burmese-Speech) | A speech corpus, not a ready-to-run TTS model. Its corpus license is not proof that pretrained model weights or every speaker recording can be redistributed commercially. | Possible research/training input only after checking the exact dataset card, source recording permissions, speaker/voice rights, and attribution requirements. Not an app-ready voice. |

## Current conclusion

As of the check date, no candidate above satisfies all requirements. Do not enable or label the non-commercial MMS model as a commercial feature. Do not silently fall back to online Edge TTS and call it offline. Do not claim that a trained model exists until model weights and their license are present and verified.

## Required path to a compliant voice

1. Obtain written commercial redistribution permission for a suitable Burmese speech corpus and the recorded speakers' voice rights, or commission original recordings with explicit model-training and commercial redistribution consent.
2. Prepare a clean, single-speaker corpus for each desired voice (or a properly licensed multi-speaker corpus), with normalized Burmese transcripts and verified audio.
3. Train a Burmese neural TTS model on GPU; evaluate pronunciation, prosody, intelligibility, and naturalness with native Burmese listeners. Training cannot realistically be completed on the target Android phone.
4. Export and validate an Android-compatible inference model (for example, ONNX suitable for sherpa-onnx), including phonemizer/tokenizer, lexicon, sample-rate metadata, and all required model files.
5. Record the exact license and attribution for code, model architecture, weights, dataset, and voices. Ensure redistribution and commercial app use are explicitly permitted.
6. Integrate a downloadable, checksum-verified model package; synthesize locally; test airplane-mode operation, long narration, memory use, cancellation, and MP4 export on the target phone.
7. Only then enable the Neural Voice controls in the production UI and publish a release APK.

## Acceptance tests

- No network access during synthesis after model installation.
- No API key or cloud service dependency.
- At least one natural Burmese voice with intelligible consonants, tones, and sentence prosody; a second voice is enabled only when its own rights and weights are verified.
- Long narration completes without truncation, leaks, or app crash.
- WAV output is valid PCM and can be muxed with source video into MP4.
- Commercial redistribution and attribution obligations are documented alongside the shipped model.
