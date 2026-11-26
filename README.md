# Zippy

On-device “Hey Zippy” listener for Android (`com.zippy.ear`, version **1.2**, `versionCode` 3).

Zippy runs a foreground microphone service, waits for the wake phrase **Hey Zippy**, transcribes the task that follows with [Vosk](https://alphacephei.com/vosk/) speech recognition entirely on the phone, and POSTs it to a [Grok Bot](https://grok.com) webhook routine:

```json
{"text":"…","source":"android"}
```

No speech is sent to a cloud recognizer. Only the finished task text is posted to the webhook you configure.

## Install this APK

`Zippy.apk` is the **original compiled and signed** release APK recovered from a Grok Build session (49,002,139 bytes, SHA-256 `c7ca406d3576fe648592ad898177ad9a499845f6c592541beeba78b4d941d956`). Because it still has the original signature, it **installs over an existing Zippy already on your phone**. A later rebuild from the recovered source would use a new signing key and would *not* install as an update — you would have to uninstall the old copy first.

If a GitHub Release with `Zippy.apk` is attached to this repository, download that. The app itself also checks `https://api.github.com/repos/13Throne/zippy/releases/latest` from the in-app **Updates** section.

### Phone setup

1. Install `Zippy.apk` (enable “Install unknown apps” for your browser or Files, if prompted).
2. Open Zippy and grant **microphone** and **notification** permission when asked. Listening will not start without the microphone.
3. Set battery use to **Unrestricted** so Android does not kill the listener after the screen sleeps:
   - In Zippy, tap **Allow background listening** (this requests an ignore-battery-optimizations exemption), or
   - Settings → Apps → Zippy → Battery → **Unrestricted**.
4. In Zippy, paste the Grok Bot webhook address (must start with `https://`) and the secret, then optionally tap **Send test**.
5. Train your voice (recommended) and turn **Listening** on.

This recovered 1.2 build does **not** register a `VoiceInteractionService` or `ASSIST` intent, so it cannot be set as Android’s default digital assistant. It listens on its own through the foreground `ListenService`.

### Grok Bot webhook

The in-app hint is: open Grok, then Automations, then **zippy-spoken-order**. Copy the address and the secret into Zippy’s fields. The secret goes in the secret field, not in the URL.

The POST body is JSON (`Content-Type: application/json`). If the secret starts with `whsec_`, Zippy signs the body with Standard Webhooks headers (`webhook-id`, `webhook-timestamp`, `webhook-signature`). Otherwise it sends `Authorization: Bearer …`. Loopback, link-local, site-local, `.local`, and metadata hostnames are blocked.

## What the app does

- **Wake word.** `Phrase` treats several near-spellings as a hit: “hey zippy”, “hey zippi”, “hey zippee”, “hey sippy”, “zippy”, “zippi”, “hey zipy”, “hey zip”, “a zippy”. After a hit, Zippy cues you (“Say the task.”), then captures the following utterance until end-of-task silence.
- **On-device recognition.** Vosk runs locally at 16 kHz via `ListenService`. The bundled model is **Vosk `vosk-model-small-en-us-0.15`**, packed as `assets/model.zip` (~41 MB) inside the APK. It is not stored in this git tree; see [`recovered-src/ASSETS.md`](recovered-src/ASSETS.md).
- **Foreground microphone service.** While listening, a persistent “Listening” notification is shown (`foregroundServiceType="microphone"`). A partial wake lock keeps the CPU available with the screen off.
- **Voice training.** **Train Hey Zippy** records **10 positive** takes (normal, quieter, louder, faster, slower, farther, across the room, and so on) and **5 negative** clips (“hey”, “zip”, “what time is it”, “hey zip”, plus two seconds of room silence). `Voice` compares a live fingerprint against those prints using the **Sensitivity** slider (default **55**, range 0–100). If you skip training, any speaker saying the wake word is accepted.
- **Settings.** Webhook URL and secret; **Sensitivity**; **Wait for the task** — the end-of-task silence timeout (default **4.0 s**, range 1–8 s); ready/sent **Cues** (Off, Vibrate, Sound, or Both); background-listening exemption; GitHub update check.

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
| `ListenService.java` | Foreground mic service, wake word, Vosk, send |
| `Webhook.java` | HTTPS POST of `{"text","source":"android"}` |
| `Phrase.java` | Wake-word matching |
| `Voice.java` / `Prints.java` | Speaker fingerprints and sensitivity |
| `Store.java` / `Settings.java` | SharedPreferences (`zippy`) |
| `ModelInstaller.java` | Unpacks `assets/model.zip` |
| `Updates.java` | Checks `13Throne/zippy` GitHub Releases for a newer APK |
| `Cues.java`, `Ui.java`, `Order.java`, `SendResult.java` | Feedback, layout helpers, recent-order log |

## License / third-party

Application code recovered here is Joshua Parten’s Zippy project. The bundled speech model and native libraries are from [Vosk](https://alphacephei.com/vosk/) (`vosk-model-small-en-us-0.15`, plus `libvosk.so` / JNA). AndroidX and Kotlin libraries are used under their upstream licenses (the APK also carries `META-INF/AL2.0` and `META-INF/LGPL2.1`).
