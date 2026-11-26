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
