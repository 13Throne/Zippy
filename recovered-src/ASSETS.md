# Recovered assets and what was left out

The original `Zippy.apk` bundles a **41,205,931-byte** on-device speech model at `assets/model.zip`.

That archive is the official **Vosk `vosk-model-small-en-us-0.15`** English model. At first launch, `ModelInstaller` unpacks it into app-private storage (`vosk-model/`) and checks for `conf/model.conf` before `ListenService` loads it with the Vosk `Model` API.

It is **not** committed here. Checking a 41 MB third-party model into git would bloat the repository, and a rebuild can download the same public model from the [Vosk models](https://alphacephei.com/vosk/models) page (`vosk-model-small-en-us-0.15`). The recovered APK (when attached as a GitHub Release asset on this repository) already contains the original copy.

Also left out of this tree on purpose:

- Decompiled `androidx`, `kotlin`, `kotlinx`, `com.sun.jna`, `org.vosk`, and other library sources
- AndroidX notification layouts, density-specific 9-patches, and `values-*` translation overlays
- Native libraries (`lib/arm64-v8a` and `lib/armeabi-v7a`: `libvosk.so`, `libjnidispatch.so`)
- The original signing keystore (Grok Build wiped it; it is not in the APK)

The UI is built in code (`Ui.java`, `MainActivity.java`, `TrainActivity.java`); there are no app-specific `res/layout` files, only AndroidX notification templates that were omitted.

## Liora voice clips (1.6)

Since 1.6 the APK also bundles `assets/voice/g1..g9.m4a` (greetings) and `assets/voice/c1..c10.m4a` (confirmations), about 172 KB in total, stored uncompressed. The source clips are committed in [`../v1.6/assets/voice/`](../v1.6/assets/voice/).

## Sherpa-ONNX wake word (1.7)

Since 1.7 the APK also bundles:
- `lib/{arm64-v8a,armeabi-v7a}/libsherpa-onnx-jni.so` and `libonnxruntime.so` from the official `sherpa-onnx-2.0.3.aar`.
- `assets/kws/`: the int8 `encoder`/`decoder`/`joiner` and `tokens.txt` of `sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01`.
- `assets/kws/keywords.txt`: the sentinel line from [`../v1.7/assets/kws/keywords.txt`](../v1.7/assets/kws/keywords.txt).

None of these binaries are committed. [`../v1.7/fetch_sherpa.sh`](../v1.7/fetch_sherpa.sh) downloads them with pinned SHA-256s.

Note: Vosk's `libvosk.so` (from the original 1.2 APK) has 4 KB ELF alignment. It only serves offline commands in 1.7.

## Silero voice-activity detector (1.8)

Since 1.8 the APK also bundles `assets/vad/silero_vad.onnx` (644 KB, deflated), the official Silero VAD model as published by sherpa-onnx (`asr-models/silero_vad.onnx`, SHA-256 `9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6`). It runs on the same `libsherpa-onnx-jni.so` / `libonnxruntime.so` as the wake word (Kotlin `Vad` class in `classes3.dex`). It is not committed; [`../v1.8/fetch_sherpa.sh`](../v1.8/fetch_sherpa.sh) downloads it with that pinned checksum.

Since 1.8 the big compressed entries (native libraries, ONNX models, `classes.dex`) are re-deflated with Zopfli at build time (identical contents, about 0.9 MB smaller).
