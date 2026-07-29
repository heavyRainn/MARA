# Third-Party Notices

## sherpa-onnx (Piper runtime on Android)

- Repository: https://github.com/k2-fsa/sherpa-onnx
- Release: v1.13.4
- License: Apache License 2.0
- Usage: offline VITS/Piper inference via `libsherpa-onnx-jni.so` and ONNX Runtime

## ONNX Runtime

- Bundled inside sherpa-onnx Android release (`libonnxruntime.so`)
- License: MIT

## Voice model: ru_RU-irina-medium

- Package: `vits-piper-ru_RU-irina-medium`
- Source: https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models
- Voice: Irina (Russian, medium, 22050 Hz)
- Embedded path: `assets/piper/voices/ru_RU-irina-medium/`
- Model files license: MIT (as distributed in Piper voices repository)

### License caveat (important)

The upstream `MODEL_CARD.md` for this voice states **Dataset license: Unknown**.

That means:

- The MIT license applies to the distributed model artifact files in the Piper voices repository.
- The licensing chain for the **original training dataset** is **not fully confirmed** in upstream metadata.
- Internal/personal builds may use this voice as an explicit project-owner decision.
- **Public commercial distribution requires separate legal review** of dataset provenance.

Do not describe the dataset license chain as fully verified.

## Previous voice: ru_RU-dmitri-medium

- Replaced by Irina in Yasna v2 voice pack (`version 2.0.0`)
- Was MIT-licensed with the same dataset-license caveat pattern

## espeak-ng data

- Bundled with the Piper voice package for phonemization
- License: see upstream Piper/espeak-ng notices in the voice bundle
