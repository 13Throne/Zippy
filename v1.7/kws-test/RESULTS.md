# Zippy 1.7 wake word: Sherpa-ONNX KWS accuracy

Model: `sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01`, int8 encoder/decoder/joiner (5.2 MB), 1 thread.
Runtime: sherpa-onnx 2.0.3. Keywords: `KwsTuning` / `kws_tuning.py` on top of the shipped sentinel `keywords.txt`.
Feeding: 2048-sample (128 ms) chunks, stream reset after each hit, exactly like `ListenService`.

## Test set (`gen_testset.py`, 1,776 synthetic utterances)

- **Voices:** 48 Piper voices. That is 8 single-speaker US/GB voices plus 40 random LibriTTS-R speakers.
- **Speed:** random 0.8–1.25×.
- **Level:** peak -26 to -1 dBFS.
- **Noise:** half the clips have noise at 10–25 dB SNR.

| Class | Count | What |
|---|---|---|
| pos | 384 | "Hey Zippy." / "Hey, Zippy!" / "hey zippy" / "Hey Zippy, what time is it?" / "Okay. Hey Zippy." … |
| mis_z | 192 | "Hey Zippie", "Hay Zippy", "Hey Zeppy", "Hey Zippee" |
| mis_s | 96 | "Hey Sippy" (the soft-z problem Vosk had) |
| near | 720 | "Hey Skippy", "Hey Siri", "zipper", "Mississippi", "hippie", "Hey Jimmy", "Is it zippable?" … (should NOT wake) |
| neg | 384 | everyday sentences and commands, including Liora's own lines "What do you need?" / "I'm listening." |

- **Real speech:** LibriSpeech test-clean, all 2,620 files (5.4 h). Any hit counts as a false alarm.
- **Echo:** Liora's 19 clips, as the speaker echo would sound.
- **"Whisper-verified":** the positives where faster-whisper small.en also hears "(hey) zippy/zippie/sippy". This drops 52 clips that the TTS itself garbled (e.g. "HE'S IT BE").

## Results by Sensitivity (the slider; default 55)

| Sensitivity | Lines | "Hey Zippy" recall (384) | Recall, Whisper-verified (332) | Mispronounced z (192) | "Hey Sippy" (96) | Near-miss (720) | Other speech (384) | Liora clips (19) | LibriSpeech 5.4 h |
|---|---|---|---|---|---|---|---|---|---|
| 0 | 4 | 54.4% | 58.4% | 42.7% | 3.1% | 0.0% | 0.0% | 0 | 0 false alarms |
| 15 | 4 | 62.8% | 66.3% | 50.0% | 13.5% | 0.0% | 0.0% | 0 | 0 |
| 30 | 9 | 75.3% | 79.5% | 60.4% | 18.8% | 0.0% | 0.0% | 0 | 0 |
| 45 | 9 | 77.9% | 82.5% | 67.2% | 25.0% | 0.0% | 0.0% | 0 | 0 |
| **55** | 11 | **81.0%** | **85.5%** | 70.8% | 76.0% | 0.3% (2× "Hey Skippy") | 0.0% | 0 | **0** |
| 70 | 11 | 82.0% | 86.4% | 75.5% | 78.1% | 0.3% | 0.0% | 0 | 0 |
| 85 | 13 | 88.3% | 92.5% | 80.7% | 80.2% | 8.7% (bare "Zippy" sound-alikes) | 0.0% | 0 | 1 |
| 100 | 13 | 88.5% | 92.8% | 81.8% | 83.3% | 9.7% | 0.0% | 0 | 1 |

- **Monotonic:** every column rises (or holds) with Sensitivity.
- **Bare "Zippy":** from 85 Zippy also wakes on bare "Zippy", so "zipper", "zippable" and "Mississippi" start to trigger. That is the documented trade-off.

## Baseline: Zippy 1.6 (Vosk transcripts + WakePhrase), same audio, same chunks, Sensitivity 55

| Detector | "Hey Zippy" recall | "Hey Sippy" | Near-miss | Other speech | LibriSpeech 5.4 h |
|---|---|---|---|---|---|
| Vosk (1.6) | 29.9% | 32.3% | 0.0% | 0.0% | 0 false alarms |
| Sherpa-ONNX KWS (1.7) | **81.0%** | **76.0%** | 0.3% | 0.0% | 0 false alarms |

## On-device JNI check (`test/device/run_device_test.sh`)

- **Setup:** the official AAR classes and `libsherpa-onnx-jni.so` / `libonnxruntime.so` (x86_64 build of the same 2.0.3 AAR) ran on the Android 15 emulator through `app_process`. The test used `KwsTuning.keywords(55)` and the shipped `keywords.txt`, on 319 files from the test set.
- **Match with desktop:** results were identical to desktop Python for 316/319 files (keyword and time). The 3 differences are borderline positives, from tiny float differences between the onnxruntime builds.
- **Counts:** pos 95/120, near 1/120, neg 0/60, Liora 0/19.
- **Speed:** model load 0.7 s, real-time factor 0.019 (about 50× faster than real time on one core).
