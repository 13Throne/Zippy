# Zippy

On-device wake-word listener for Android (`com.zippy.ear`, version **1.81**, `versionCode` 10). The default phrase is **Hey Zippy**; in 1.81 you can train a custom phrase.

Zippy runs a foreground microphone service, waits for the wake phrase **Hey Zippy** (on-device [Sherpa-ONNX](https://github.com/k2-fsa/sherpa-onnx) keyword spotting since 1.7), hears the task that follows (Android's speech recognizer by default since 1.5, with on-device [Vosk](https://alphacephei.com/vosk/) as the fallback), and POSTs it to a [Grok Bot](https://grok.com) webhook routine:

```json
{"text":"…","source":"android"}
```

The wake word (Sherpa-ONNX) and voice check always run on the phone; Vosk stays on the phone as the offline command recognizer. With **Use Android speech recognition for commands** on (default), the task after a pause goes to Android's `SpeechRecognizer` (on most phones Google, which may use the network). Turn it off to keep everything on the phone. Only the finished task text is posted to the webhook you configure.

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

## What's new in 1.81

**Your own wake word or phrase, trained to your voice.** Settings → Wake word → Change wake word: type any phrase (1–5 words), record 5–8 takes and 3 ordinary sentences, then Tune. Zippy tokenizes the phrase for Sherpa-ONNX keyword spotting, learns pronunciations from how the model hears your takes, picks a threshold where your takes wake it and ordinary speech doesn't, and reuses v1.4 voice prints to lock it to you. "Test it" counts live wakes for 30 seconds. You can re-record, save, or switch back to Hey Zippy. One missed take can still be saved (it's flagged as an outlier); two or more need a re-record. A custom phrase is stripped from the start of the spoken task.

**Sensitivity defaults are unchanged** (default 55). Settings warns above 80, where false wakes about triple on conversational audio. 50–60 is recommended. See [`v1.81/kws-test/RESULTS.md`](v1.81/kws-test/RESULTS.md).

**Optional volume-down double-press** (off by default) pauses or resumes listening. A single press always changes the volume with no delay; a second press within 400 ms is the shortcut. Needs the Accessibility permission, explained in Settings, with a button that opens Accessibility settings. It does nothing during calls, may not work with the screen off on some phones, and on Android 14+ may post a "Tap to start listening" notification if the listener was fully stopped.

Everything from 1.8 is kept. The APK is **69.3 MiB**, signed with the same key as 1.3–1.8.

## What's new in 1.8

Fixes the clipped tasks (e.g. "have Jana, the vacation bot, draft an email … to Brenda" arriving as only "the relevant links and various choices"). In 1.7 the microphone was handed from Zippy to Android's recognizer (or to a fresh Vosk session) after the wake word, a fixed 13 s watchdog threw away a live recognition, and Vosk sent at its first 0.5–1 s pause.

- **The mic never closes between "Hey Zippy" and the send.** Zippy keeps recording into one buffer, starting **2 s before the wake word** (pre-roll), so nothing said right after (or even over) "Hey Zippy" is lost. The 650 ms probe and the mic hand-off are gone.
- **End of speech by Sherpa-ONNX Silero VAD** (`assets/vad/silero_vad.onnx`, 0.6 MB, on the same onnxruntime as the wake word). The task ends after about **2 s of silence** (from the *Wait for the task* slider: half the wait, 1.5–3.5 s), keeps listening while you talk, for up to **45 s**. Short pauses (tested 0.3–1.5 s) never cut you off. Energy-based fallback if Silero can't load.
- **Android speech hears Zippy's recording** (Android 13+, `EXTRA_AUDIO_SOURCE` in segmented mode, on-device recognizer first, `checkRecognitionSupport`): all `onSegmentResults` are joined. Vosk transcribes the **whole buffer** alongside (words with timestamps, all results joined). Zippy sends Android's text, and splices in Vosk's words when Android skipped the beginning or stopped early; Vosk alone when Android isn't available, said nothing, or heard much less. If a phone's recognizer won't read the pipe (or opens its own mic), Zippy notices, remembers it, and uses Vosk.
- **No timeouts that throw text away.** The Android wait resets on every partial result; whatever was heard is kept; the fixed 4 s Vosk deadline is gone; a minimum task length stops "uh" from being sent.
- **Greeting rules.** If you're already talking 0.4 s after the wake word, Liora is skipped (a short vibration instead). If you talk over her, she stops (barge-in: your mic level is compared with her clip's loudness envelope times this phone's measured echo gain, saved per output route and volume after sessions where you let her finish). Acoustic echo cancellation is turned on where the phone has it, and EchoGuard removes her words from the text by timing and by fuzzy match. Confirmation plays only after a successful send.
- **Open Grok Bot after sending** (was "on wake"), and the greeting takes **transient audio focus** (music ducks/pauses, then resumes).
- **Last command** card on Home: a timeline of the last task (wake word time, greeting played/skipped/stopped, recognizer ready, you started/stopped talking, end reason, which recognizer's text was sent and why, webhook result, `EXTRA_AUDIO_SOURCE` support).
- Everything from 1.7 stays: Sherpa-ONNX wake word, VoiceMatch, the 19 Liora clips with dropdowns/Random/Preview, 3 tabs, Reset voice confirmation, `X-Zippy-Signature`, semver updater. The APK is **69.3 MiB** (smaller than 1.7: big entries are re-deflated with Zopfli at build time).

Tests: [`v1.8/capture-test/RESULTS.md`](v1.8/capture-test/RESULTS.md).

## What's new in 1.7

- **New wake word engine: Sherpa-ONNX keyword spotting.** "Hey Zippy" is now spotted by the official [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) 2.0.3 Android libraries (`libsherpa-onnx-jni.so` + `libonnxruntime.so`, `arm64-v8a` and `armeabi-v7a`, 16 KB-aligned) with the `sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01` model (int8, 5.2 MB, in `assets/kws/`). Vosk read "Hey Zippy" as text and often missed the "z"; on the same 384 test clips Sherpa-ONNX wakes on **81%** versus Vosk's 30%, with **0 false alarms in 5.4 h of real speech** (see [`v1.7/kws-test/RESULTS.md`](v1.7/kws-test/RESULTS.md)).
- **Keywords from the model's own tokenizer.** Every pronunciation is the exact BPE sequence from the model's `bpe.model` (checked against `sentencepiece` and `sherpa_onnx.text2token` by [`v1.7/make_keywords.py`](v1.7/make_keywords.py)): `HEY ZIPPY` (`▁HE Y ▁ Z I PP Y`), plus sound-alikes the model actually hears (`HEY ZIPPI`, `HAZIPPI`, …), a soft z (`HEY SIPPY`) from Sensitivity 50, and bare `ZIPPY` from 85.
- **Sensitivity drives Sherpa-ONNX.** 0 → 100 adds pronunciations (4 → 13), raises the boosting score (1.0 → 2.0) and lowers the trigger threshold (0.45 → 0.05); the voice check (VoiceMatch) loosens with it and is off at 100, as before. Each step only makes waking easier (tested).
- **No greeting bleed.** After Liora's greeting finishes, Zippy waits 180 ms of silence before Android's recognizer (or Vosk) listens, and drops a leading echo of the greeting from the task ("what do you what time is it" → "what time is it").
- **Vosk is now only the offline command recognizer** (loaded in the background): a task said in one breath ("Hey Zippy, add milk"), or the task when Android speech is off, missing or fails. The APK stays under 70 MB (69.9 MB) with it.
- **Safety net.** If the native Sherpa-ONNX libraries can't start on a phone (a crash while loading can't be caught in Java), Zippy notices on the next start and uses the 1.6 Vosk wake word for this version instead of crash-looping; Home says so.

## What's new in 1.6

- **Liora's voice.** 19 short clips recorded by Liora are bundled in `assets/voice/` (AAC m4a, mono 48 kHz, loudness-normalized, stored uncompressed so `AssetManager.openFd()` streams them): 9 greetings (`g1` "Yeah?", `g2` "Hey, what's up?", `g3` "Go ahead.", `g4` "I'm listening.", `g5` "What do you need?", `g6` "Talk to me.", `g7` "Ready.", `g8` "Shoot.", `g9` "Right here.") and 10 confirmations (`c1` "On it.", `c2` "Got it.", `c3` "Sent.", `c4` "Handling it now.", `c5` "Consider it done.", `c6` "You got it.", `c7` "All over it.", `c8` "Working on it.", `c9` "Heard you.", `c10` "Got it, I'll take care of it.").
- **Wake cue: Voice greeting (Liora) / Beep / Vibrate / Silent.** The Voice tab has a "Liora says" dropdown: **Random** (default, never the same clip twice in a row), any of the 9 greetings, or **Custom (text-to-speech)** with an editable text box, plus **Preview**. On wake Zippy pauses its mic, plays the clip with `MediaPlayer` (`USAGE_ASSISTANT`) and resumes listening on `OnCompletion` (4 s safety timeout). If a clip can't play, Zippy speaks the same words with TTS, then falls back to a beep. A custom 1.5 greeting is kept as Custom on upgrade.
- **Confirmation (replaces the order-accepted cue): Voice confirmation (default) / Beep / Vibrate / Silent.** Dropdown with Random, the 10 confirmations, or Custom (text-to-speech, default "On it."), plus Preview. It plays once Grok Bot accepts the task, while Zippy's mic is paused so Liora isn't heard as a new command.
- Everything from 1.5 (tabs, Android speech for commands, Open Grok Bot on wake, signing) is unchanged.

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
| `Kws.java` / `KwsTuning.java` / `KwsGuard.java` | 1.7 Sherpa-ONNX wake word: spotter from `assets/kws/`, Sensitivity → BPE keyword lines, crash-safe fallback to the 1.6 Vosk wake word |
| `EchoGuard.java` / `TaskText.java` | 1.7 greeting-echo stripping (1.8: by timing and fuzzy leading match) and one-breath task text |
| `Capture.java` | 1.8 pure capture logic: greeting decision, barge-in, end of speech (silence / no speech / 45 s) |
| `SileroGate.java` / `EnergyVad.java` | 1.8 Sherpa-ONNX Silero VAD per 32 ms frame / energy fallback |
| `AndroidStream.java` | 1.8 Android speech on Zippy's recording (`EXTRA_AUDIO_SOURCE`, segmented, on-device first, support probe) |
| `VoskJob.java` / `VoskWords.java` | 1.8 Vosk over the whole buffer with word timestamps |
| `TaskClean.java` | 1.8 task text from both recognizers: wake-word cut, echo strip, fillers, choosing/splicing |
| `Greeting.java` / `VoiceEnv.java` | 1.8 stoppable greeting with transient audio focus / clip loudness + saved echo gain for barge-in |
| `Timeline.java` | 1.8 "Last command" diagnostics |
| `Liora.java` | 1.6 Liora clip list, Random/named/Custom picking, `MediaPlayer` playback from `assets/voice/` |
| `Cues.java`, `Ui.java`, `Order.java`, `SendResult.java` | Feedback, layout helpers, recent-order log |

## Building v1.81

Same layering as 1.8: decode the v1.2 base APK, apply patches v1.3…v1.8, then `v1.81/patch_v181.py`. `v1.81/build.sh` compiles the newest Java (v1.81 first), merges dex, Zopfli-redeflates, zipaligns `-P 16`, and signs v2+v3. Output must be named `Zippy.apk`, `versionName` 1.81, `versionCode` 10. Tests: `PYTHON=… v1.81/test/run_tests.sh Zippy.apk`. Local builds only until a release is approved; do not git push, tag, or create a GitHub release from this tree without that approval. The next version after 1.81 must be **1.82 or higher** (a dotted `1.9` would sort below 1.81 in the in-app updater).

## Building v1.8

[`v1.8/build.sh`](v1.8/build.sh) is the 1.7 chain plus [`v1.8/fetch_sherpa.sh`](v1.8/fetch_sherpa.sh) (adds the checksum-pinned `silero_vad.onnx`) and [`v1.8/patch_v18.py`](v1.8/patch_v18.py) (Silero model, `assets/barge/env.txt` from [`v1.8/make_voice_env.py`](v1.8/make_voice_env.py), version 1.8/9), then a Zopfli re-deflate ([`v1.8/zopfli_apk.py`](v1.8/zopfli_apk.py), `pip install zopfli`; skipped if missing), zipalign and signing.

```bash
ZIPPY_KS_PASS=… v1.8/build.sh Zippy-1.2.apk zippy-release.jks Zippy.apk
PYTHON=/path/to/python-with-vosk-sherpa-onnx-sentencepiece v1.8/test/run_tests.sh Zippy.apk   # packaging, 1.8 logic, layout, whole 1.7 suite
CAPSIM_CP=… v1.8/capture-test/capture_eval.py work out && v1.8/capture-test/barge_eval.py work out   # long requests, pauses, gaps, greeting
v1.8/test/device/run_device_test.sh out wav greetStart greetLen …   # Silero VAD + Capture on a device via app_process
```

## Building v1.7

[`v1.7/build.sh`](v1.7/build.sh) runs the v1.3–v1.6 patches, downloads and checksum-verifies the official `sherpa-onnx-2.0.3.aar` and the KWS model ([`v1.7/fetch_sherpa.sh`](v1.7/fetch_sherpa.sh); neither is committed), and then runs [`v1.7/patch_v17.py`](v1.7/patch_v17.py). That adds the two native libraries per ABI and `assets/kws/` (int8 encoder/decoder/joiner, `tokens.txt`, the sentinel `keywords.txt`) and bumps the version to 1.7/8. The AAR's Kotlin API (`KeywordSpotter`, `OnlineStream`, configs) becomes `classes3.dex`; the Kotlin stdlib it needs is already in the APK. Sherpa-ONNX's `createStream(keywords)` *adds* to the file's keywords, so the shipped `keywords.txt` holds only a never-said sentinel and every real line comes from `KwsTuning.keywords(sensitivity)`.

```sh
ZIPPY_KS_PASS=… v1.7/build.sh Zippy-1.2.apk zippy-release.jks Zippy.apk
PYTHON=/path/to/python-with-vosk-sherpa-onnx-sentencepiece v1.7/test/run_tests.sh Zippy.apk   # packaging, keywords, 1.7 logic, layout, whole 1.6 suite
v1.7/kws-test/gen_testset.py <piper-voices> testset && v1.7/kws-test/kws_eval.py <model> testset 0,30,55,85,100 --real <librispeech-wavs>
v1.7/test/device/run_device_test.sh 55 testset/pos/*.wav   # same KWS path on a device via app_process
```

## Building v1.6

[`v1.6/build.sh`](v1.6/build.sh) applies the v1.3, v1.4 and v1.5 patches, then [`v1.6/patch_v16.py`](v1.6/patch_v16.py), which copies the clips from [`v1.6/assets/voice/`](v1.6/assets/voice/) into `assets/voice/`, adds `m4a` to apktool's `doNotCompress` list and bumps the version to 1.6/7. The 1.6 Java (`Liora`, `ZippyPrefs`, `WakeCue`, `Tabs`, `ListenService`, `MainActivity`) overrides the 1.5 copies. The build checks that all 19 clips are stored uncompressed, then signs with the release key:

```sh
ZIPPY_KS_PASS=… v1.6/build.sh Zippy-1.2.apk zippy-release.jks Zippy.apk
PYTHON=/path/to/python-with-vosk v1.6/test/run_tests.sh Zippy.apk   # clip words/durations/silence, packaging, 1.6 + 1.5 + 1.4 + 1.3 tests
```

The clips were cut from Liora's recording with `ffmpeg silencedetect` (-50 dB, 0.2 s), padded 60 ms before / 120 ms after each phrase, given +5 dB with a limiter at -3 dBFS and 5/20 ms fades, and encoded as AAC 64 kbps mono 48 kHz with `+faststart`. [`v1.6/test/clip_check.py`](v1.6/test/clip_check.py) transcribes every clip with Vosk and checks the words, duration, padding, internal gaps and peaks.

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

Application code recovered here is Joshua Parten’s Zippy project. The wake word uses [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) 2.0.3 (Apache-2.0; `libsherpa-onnx-jni.so`, ONNX Runtime `libonnxruntime.so` under MIT) and the `sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01` model (Apache-2.0). End of speech uses the [Silero VAD](https://github.com/snakers4/silero-vad) model (MIT). The bundled command model and native libraries are from [Vosk](https://alphacephei.com/vosk/) (`vosk-model-small-en-us-0.15`, plus `libvosk.so` / JNA). AndroidX and Kotlin libraries are used under their upstream licenses (the APK also carries `META-INF/AL2.0` and `META-INF/LGPL2.1`).
