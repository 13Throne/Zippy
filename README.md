# Zippy

On-device “Hey Zippy” listener for Android (`com.zippy.ear`, version **1.5**, `versionCode` 6).

Zippy runs a foreground microphone service, waits for the wake phrase **Hey Zippy**, hears the task that follows (Android's speech recognizer by default since 1.5, with on-device [Vosk](https://alphacephei.com/vosk/) as the fallback), and POSTs it to a [Grok Bot](https://grok.com) webhook routine:

```json
{"text":"…","source":"android"}
```

The wake word and voice check always run on the phone with Vosk. With **Use Android speech recognition for commands** on (default), the task after a pause goes to Android's `SpeechRecognizer` (on most phones Google, which may use the network). Turn it off to keep everything on the phone. Only the finished task text is posted to the webhook you configure.

## Install this APK

Download `Zippy.apk` from the latest [GitHub Release](https://github.com/13Throne/Zippy/releases/latest).

**Upgrading from 1.2:** v1.3 is signed with a new, permanent release key (the original 1.2 key was lost), so Android will not install it over 1.2. **Uninstall Zippy 1.2 first**, then install v1.3. From v1.3 on, every release is signed with the same key, so later versions install right over it — including from the in-app **Check for updates** button.

### Phone setup

1. Install `Zippy.apk` (enable “Install unknown apps” for your browser or Files, if prompted).
2. Open Zippy and grant **microphone** and **notification** permission when asked. Listening will not start without the microphone.
3. Set battery use to **Unrestricted** so Android does not kill the listener after the screen sleeps:
   - In Zippy, tap **Allow background listening** (this requests an ignore-battery-optimizations exemption), or
   - Settings → Apps → Zippy → Battery → **Unrestricted**.
4. In Zippy's **Settings** tab, paste the Grok Bot webhook address (must start with `https://`) and the secret, then optionally tap **Send test**.
5. Train your voice on the **Voice** tab (recommended), then turn **Listening** on from **Home**.

Zippy (1.2 and 1.3) does **not** register a `VoiceInteractionService` or `ASSIST` intent, so it cannot be set as Android’s default digital assistant. It listens on its own through the foreground `ListenService`.

### Grok Bot webhook

Zippy posts to the Grok Bot routine **Hey Zippy voice tasks**. The address is prefilled when empty (`https://api2.cursor.sh/automations/webhook/eedaf91e-bf38-5ed3-a66f-a949716538a6`); type the secret you registered with Grok Bot into the secret field (never into the URL).

The POST body is JSON (`Content-Type: application/json`). Signing (v1.3):

- Secret starts with `whsec_` → Standard Webhooks headers (`webhook-id`, `webhook-timestamp`, `webhook-signature`), unchanged from 1.2.
- Any other non-empty secret → Stripe-style `X-Zippy-Signature: t=<unix seconds>,v1=<lowercase hex HMAC-SHA256 of "<t>.<raw UTF-8 body>">`, keyed with the secret's UTF-8 bytes. No `Authorization` header is sent.

Loopback, link-local, site-local, `.local`, and metadata hostnames are blocked.

## What's new in 1.5

- **Tabs.** **Home**: the Listening switch, live status, last heard, an at-a-glance summary (sensitivity, wake cue, command recognizer, Grok Bot), and recent orders. **Voice**: Train / Reset voice (with confirmation), Sensitivity, Wake cue + greeting, Wait for the task, and the order-accepted cue. **Settings**: webhook address, secret and Send test; the Android speech toggle; the Grok Bot toggle; background listening; Updates. Edge-to-edge status/navigation bar insets are handled.
- **Android speech for commands** (toggle, default on). After "Hey Zippy" and a pause, Zippy releases its own `AudioRecord`, runs `SpeechRecognizer` on the main thread (`ACTION_RECOGNIZE_SPEECH`, `LANGUAGE_MODEL_FREE_FORM`, `EXTRA_PREFER_OFFLINE=false`), sends the result (the wake words are stripped if repeated), and reopens its mic for Vosk. Said in one breath ("Hey Zippy, add milk"), the task is sent straight from what Vosk already heard. If no recognizer is installed, or it errors or doesn't answer (watchdog 6–20 s), Zippy falls back to Vosk for that task. "No match" or silence ends with "No task after Hey Zippy." Listening always resumes.
- **Wake cue: Voice greeting / Beep / Vibrate / Silent.** The voice greeting (default "Yeah?", editable, with **Preview**) is spoken with Android TextToSpeech (`USAGE_ASSISTANT`) while Zippy's mic is paused, and listening starts only after `onDone`. If TTS isn't ready, fails or takes more than 4 s, Zippy beeps instead. Beep and Vibrate play the moment your voice is accepted. Upgrades keep their 1.4 cue: Off→Silent, Vibrate→Vibrate, Sound/Both→Beep. New installs get Voice greeting.
- **Open Grok Bot on wake** (toggle, default off). Zippy looks for `ai.x.grok.bot`, falling back to an app labelled "Grok Bot"; it is visible on Android 11+ through `<queries>`. On wake it launches Grok Bot with `FLAG_ACTIVITY_NEW_TASK`. Android 10+ blocks activity starts from the background, so this works directly only while Zippy is on screen or when **Display over other apps** is allowed (there's a button for it in Settings). Otherwise Zippy posts a "Tap to open Grok Bot" notification and shows why in Settings.

## What the app does

- **Wake word.** `Phrase` treats several near-spellings as a hit: “hey zippy”, “hey zippi”, “hey zippee”, “hey sippy”, “zippy”, “zippi”, “hey zipy”, “hey zip”, “a zippy”. After a hit, Zippy cues you (“Say the task.”), then captures the following utterance until end-of-task silence.
- **On-device recognition.** Vosk runs locally at 16 kHz via `ListenService`. The bundled model is **Vosk `vosk-model-small-en-us-0.15`**, packed as `assets/model.zip` (~41 MB) inside the APK. It is not stored in this git tree; see [`recovered-src/ASSETS.md`](recovered-src/ASSETS.md).
- **Foreground microphone service.** While listening, a persistent “Listening” notification is shown (`foregroundServiceType="microphone"`). A partial wake lock keeps the CPU available with the screen off.
- **Voice training.** **Train Hey Zippy** records **10** wake takes (normal, quieter, louder, faster, slower, farther, across the room, …) plus 5 other clips. Since 1.4 the voice check (`VoiceMatch`) compares a noise-compensated cepstral voice print of the live wake against the wake takes, with a threshold calibrated from how much your own takes vary. Prints from 1.2/1.3 are detected and the app asks you to retrain (until then any voice is accepted). If you skip training, any speaker saying the wake word is accepted.
- **Sensitivity (0–100, higher = triggers more easily).** One direction for both the wake-phrase matcher and the voice check: 0 = exact “Hey Zippy” + close voice match; ~55 (default) balanced; 60+ also accepts close sound-alikes such as “hey zip”; 85+ also wakes on a bare “Zippy”; **100 turns the voice check off**. A rejected wake shows its voice score and the score needed. 1.2/1.3 stored the opposite meaning, so the saved value is flipped once on upgrade.
- **Settings.** Webhook URL and secret; **Sensitivity**; **Wait for the task** — the end-of-task silence timeout (default **4.0 s**, range 1–8 s); ready/sent **Cues** (Off, Vibrate, Sound, or Both); background-listening exemption; **Reset voice** (asks for confirmation before deleting your saved voice takes); in-app updater.

- **In-app updates (v1.3).** **Check for updates** reads `https://api.github.com/repos/13Throne/Zippy/releases/latest`, compares the tag (`v1.4`, `1.4`, `v1.4.1` …) numerically with the installed `versionName`, downloads the `Zippy.apk` asset to `cache/updates/` with progress, checks it is a newer `com.zippy.ear`, asks for **Install unknown apps** if needed (and resumes automatically when you come back), then opens the system installer through `androidx.core.content.FileProvider` (`com.zippy.ear.fileprovider`).

Requires Android 8.0 (API 26) or later. Target SDK 35.

## Recovered source

Grok Build wiped the original workspace and keystore. What is in [`recovered-src/`](recovered-src/) was **decompiled from the signed APK**:

- `AndroidManifest.xml` decoded with apktool 2.12.1
- App-specific resources (`res/drawable/ic_launcher.xml`, `res/values`, `res/xml/file_paths.xml`)
- Java sources for the `com.zippy.ear` package, recovered with JADX 1.5.7

The original project was **Kotlin** (Kotlin 2.0.21, Android Gradle Plugin 8.7.3, Gradle 8.11.1) — JADX comments such as `compiled from: Store.kt` reflect that. The recovered Java is a faithful-enough reference of behavior, not a drop-in Gradle project: JADX left typical decompiler artifacts, library code (`androidx`, Kotlin stdlib, JNA, Vosk) was intentionally omitted, and the original signing key is gone.

Package layout:

| File | Role |
| --- | --- |
| `MainActivity.java` | Home screen: webhook fields, listening switch, training, settings, updates |
| `TrainActivity.java` | 10 positive / 5 negative voice-print capture |
| `ListenService.java` | 1.4 Java rewrite: foreground mic service, wake word, Vosk, voice check, send; publishes its real state |
| `ListenState.java` / `ZippyUi.java` | 1.4 switch / status / service sync and live re-render |
| `Webhook.java` | HTTPS POST of `{"text","source":"android"}` |
| `WakePhrase.java` / `Sensitivity.java` | 1.4 wake-word matching and the single Sensitivity scale (`Phrase.java` is the unused 1.2 version) |
| `VoiceMatch.java` / `VoiceMath.java` | 1.4 voice prints + calibrated matching (`Voice.java` now delegates here) |
| `Prints.java` | Stored training prints |
| `Store.java` / `Settings.java` | SharedPreferences (`zippy`) |
| `ModelInstaller.java` | Unpacks `assets/model.zip` |
| `Updater.java` | v1.3 updater: semver tag check, download progress, APK check, install prompt |
| `Updates.java` | 1.2 updater (integer tags only; no longer called) |
| `Sig.java` | v1.3 Stripe-style `X-Zippy-Signature` HMAC |
| `ResetConfirm.java` | v1.3 "Reset voice data?" confirmation dialog |
| `Cues.java`, `Ui.java`, `Order.java`, `SendResult.java` | Feedback, layout helpers, recent-order log |

## Building v1.5

[`v1.5/build.sh`](v1.5/build.sh) applies the v1.3 and v1.4 patches, then [`v1.5/patch_smali_v15.py`](v1.5/patch_smali_v15.py). That script removes the Kotlin `MainActivity` (replaced by the tabbed Java screen), adds `SYSTEM_ALERT_WINDOW` and `<queries>` (`ai.x.grok.bot`, launcher apps, `RecognitionService`, `TTS_SERVICE`), adds TrainActivity insets and bumps the version to 1.5/6. The build then compiles `v1.3/java`, the 1.4 voice classes and `v1.5/java` into `classes2.dex` and signs with the release key:

```sh
ZIPPY_KS_PASS=… v1.5/build.sh Zippy-1.2.apk zippy-release.jks Zippy.apk
v1.5/test/run_tests.sh   # 1.5 settings/cue/STT-fallback/Grok/tab tests + layout check + all 1.4 and 1.3 tests
```

## Building v1.4

[`v1.4/build.sh`](v1.4/build.sh) applies the v1.3 patches, then [`v1.4/patch_smali_v14.py`](v1.4/patch_smali_v14.py), compiles `v1.3/java` + `v1.4/java` into `classes2.dex`, and signs with the same release key:

```sh
ZIPPY_KS_PASS=… v1.4/build.sh Zippy-1.2.apk zippy-release.jks Zippy.apk
v1.4/test/run_tests.sh   # voice matching, sensitivity direction, listening state, signature, updater
```

`run_tests.sh` synthesises a small 6-speaker test set with `espeak-ng` + `sox` (`make_voices.sh`).

What 1.4 replaces: `ListenService` (now Java), `Voice.fingerprint/matches` (delegate to `VoiceMatch`), the Listening status/switch sync (`ZippyUi`, `ListenState`), and the wake-phrase matcher (`WakePhrase`, replacing `Phrase`).

## Building v1.3

[`v1.3/`](v1.3/) rebuilds the release from the original 1.2 APK (the Kotlin project is gone, so the app is patched rather than recompiled):

1. `apktool d` the 1.2 APK, then `patch_smali.py` applies the smali changes (signing hook, Reset-voice dialog, updater wiring, default URL, text, version 1.3 / code 4).
2. The new classes in `v1.3/java/` (`Sig`, `ResetConfirm`, `Updater`) are compiled with `javac` against `android.jar` (+ compile-only stubs) and `d8`, and shipped as `classes2.dex`.
3. `apktool b`, `zipalign -P 16 4`, then `apksigner` (v1+v2+v3) with the release keystore.

```sh
ZIPPY_KS_PASS=… v1.3/build.sh Zippy-1.2.apk zippy-release.jks Zippy.apk
v1.3/test/run_sig_test.sh       # Sig.java vs Python hmac/hashlib
v1.3/test/run_updater_test.sh   # tag parsing / version comparison
```

The keystore is kept outside git (`*.jks`, `*.keystore` are ignored) — keep it safe; every future release must be signed with it.

## License / third-party

Application code recovered here is Joshua Parten’s Zippy project. The bundled speech model and native libraries are from [Vosk](https://alphacephei.com/vosk/) (`vosk-model-small-en-us-0.15`, plus `libvosk.so` / JNA). AndroidX and Kotlin libraries are used under their upstream licenses (the APK also carries `META-INF/AL2.0` and `META-INF/LGPL2.1`).
