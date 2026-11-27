# Zippy 1.8 capture tests

All numbers from `capture_eval.py` + `reclean.py` + `barge_eval.py` (Nov 27, 2026). The app's own Java classes do the deciding (`Capture`, `TaskClean`, `EchoGuard`, `VoskWords` via `CaptureSim.java`). They are fed by the same engines and models the APK ships: Sherpa-ONNX KWS (sensitivity 55), Sherpa-ONNX Silero VAD (the app's config, 0.5 s warm-up, 512-sample frames), and Vosk small-en-us 0.15 over the whole recording from 2 s before the wake word.

**Setup:** 234 recordings, made with 3 Piper voices. There are 4 requests:
- Joshua's Schlitterbahn/Brenda request, 3 clauses
- an errands request, 3 clauses
- a flights request, 3 clauses
- one short request

Each multi-clause request is recorded with 0.3 / 0.6 / 1.0 / 1.5 s pauses between clauses, and every request with gaps of 0 / 0.15 / 0.3 / 0.6 / 1.0 / 1.8 s after "Hey Zippy". Room noise is added. When Liora greets, her real clip is mixed in as speaker echo, with two reflections, at 0.15 / 0.35 / 0.7 of full level.

**Android speech stand-in:** Android speech can't run on a desktop, so faster-whisper `small.en` stands in for it. It hears exactly what Zippy pipes to it: from 0.35 s before the wake moment to the end Capture chose.

## Headline (sent text)

| | 1.8 |
| --- | --- |
| Ended by silence **after the last word** (no early cut) | **100%** (all pauses up to 1.5 s) |
| Start of the request kept (one of the first 3 words in the first 5 sent) | **99.1%** |
| End of the request kept (one of the last 3 words in the last 5 sent) | **99.6%** |
| Content-word recall / WER | 97.5% / 4.5% |
| Liora's words at the start of the task | 0.4% (1 of 234: Whisper heard "Yeah." over a different greeting) |
| Send delay after your last word | median 2.3 s (2.2–2.5) at the default Wait 4 s |
| 0 ms gap after "Hey Zippy" | start kept 100%, end kept 100%, recall 97.4% |
| Wake word spotted | 98.7% (missed ones were still tested with a forced wake moment) |

The 1.7 one-breath path, run on the same multi-clause audio (gap ≤ 0.3 s), kept the end of the request in only **34%** of cases (recall 57.5%): it sent at the first pause. That is the bug Joshua hit.

Per-gap and per-pause tables are in [`results/capture_summary.txt`](results/capture_summary.txt), and every recording is listed in [`results/scenarios.csv`](results/scenarios.csv).

## Greeting / talking over Liora ([`results/barge_summary.txt`](results/barge_summary.txt))

- **Already talking 0.4 s after the wake word** (gaps 0–0.6 s): the greeting is skipped (vibration instead) in 100% of cases.
- **Talking over her:** she stops when your voice is clearly louder than her expected echo. That expected echo is her clip's loudness envelope (`assets/barge/env.txt`) times the phone's echo gain.
  - After the phone has measured its echo gain (any session where you let her finish), she stops in **62%** of the talk-over cases, a median of **290 ms** after you start. By echo level that is 14/14 at 0.15, 9/14 at 0.35, and 1/11 at 0.7, where her echo is as loud as you.
  - Her own echo never stopped her: **0 false stops in 36** echo-only recordings (9 clips × 4 levels), and **0 of 36** were taken as a task.
  - In the very first session, with no gain measured yet, Zippy is deliberately conservative and she doesn't stop (0 of 39).
- **Whether or not she stops, nothing you say is lost.** The mic never closes, and EchoGuard removes her words from the text.

## Text choice

Of the 234 sent texts:
- 176 used Android speech as is
- 38 used Android speech plus the offline (Vosk) ending, where the recognizer stopped early
- 3 used the offline beginning, where it skipped the first clause
- 17 used offline only

## Caveats

- **Synthetic speech.** The voices are Piper TTS, not real people, and the pauses are exact.
- **Whisper is not Google.** Real Android speech on a phone, and whether a given phone's recognizer accepts `EXTRA_AUDIO_SOURCE`, can only be verified on a real Android 13+ device. Zippy records the answer in the Last command card and in Settings.
- **No real echo canceller.** No AEC was simulated: the worst case. On phones with AEC, the echo is lower and barge-in is easier.
- **Spoken greeting can't be stopped.** A custom text-to-speech greeting can't be stopped mid-word (only Liora's clips can).
