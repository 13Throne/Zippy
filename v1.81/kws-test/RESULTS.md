# Zippy 1.81 wake-phrase eval (local; not a release)

Desktop and packaging numbers for the custom wake-word trainer. Voices are Piper TTS stand-ins, not a real user. The conversation set (`conv/`, 90 two-voice ~60 s streams, 92.3 min) is adversarial: heavy on "hey <name>" and Zippy sound-alikes.

## Sensitivity defaults (unchanged)

Default Sensitivity stays **55**. Settings shows a warning above 80. Built-in "Hey Zippy" (v1.8 engine) on the 1,776-clip v1.7 test set plus the 92.3 min conv set:

| Sensitivity | Test-set recall | Conv false wakes (92 min) |
|---|---|---|
| 45 | 77.1% | 27 |
| 50 | 82.3% | 43 |
| 55 (default) | 82.0% | 49 |
| 70 | 84.9% | 63 |
| 85 | 90.9% | 144 |
| 100 | 92.7% | 150 |

85 and above is about 3× the false wakes of 55, which matches the in-app warning. Earlier v1.7 LibriSpeech (5.4 h) had 0 false alarms at 55 and 1 at 85/100.

## Tuner rules used in this eval

- Tokenize the typed phrase with the packaged unigram BPE (`assets/kws/bpe.vocab`).
- Learn up to 4 extra pronunciations from greedy decoding of the takes (whole-word windows, relative edit distance ≤ 0.40).
- Drop a learned variant that also fires on the user's ordinary sentences.
- Pick the strictest threshold where the required takes still fire (and still fire one step easier, to avoid borderline flips).
- **T_CAP = 0.30:** never *start* stricter than 0.30 (lifted only if ordinary sentences still wake it). Stops clean takes from overfitting and dropping held-out recall.
- **One outlier take:** if not every take fires, allow all-but-one (need ≥ 5 takes) instead of falling to the loosest 0.05. Save is enabled when every take, or all but one, wakes it (`canSave`). Two or more misses still cannot be saved.

## Outlier-fix eval (phrase_eval.log, 6 phrases × 6 voices)

Compared with the earlier cap-only run (`phrase_eval_cap.log`):

| | Cap only | Cap + one-outlier |
|---|---|---|
| Pairs with all 6 takes hitting | 21 | 23 |
| Pairs with 5 of 6 (saveable) | 11 | 11 |
| Pairs not saveable | 4 | 2 (ryan: Hey Zippy, Yo Zippy) |
| Pairs that fell to threshold 0.05 | 14 | 2 |
| Summed conv false wakes (trained) | 216 | 113 |
| Held-out same-voice recall, plain → trained | 78.9% → 89.6% | 77.2% → 88.3% |
| Other-voice recall, plain → trained | 62.7% → 69.1% | 61.9% → 66.6% |

Per phrase after the outlier fix (sums over 6 voices; conv false wakes are totals over 92.3 min × 6):

| Phrase | Held-out same voice, untrained → trained | Other voices, untrained → trained | Conv false wakes, untrained → trained | All 6 takes hit |
|---|---|---|---|---|
| Hey Jarvis | 92% → 93% | 77% → 75% | 0 → 0 | 3/6 |
| Hey Zippy | 64% → 82% | 50% → 56% | 54 → 104 | 3/6 |
| ↳ Hey Zippy v1.8 built-in @55 | 92% (same-voice) | 72% | 296 | — |
| Okay Computer | 97% → 95% | 95% → 95% | 0 → 0 | 5/6 |
| Hey Fred | 93% → 93% | 79% → 83% | 6 → 6 | 5/6 |
| Yo Zippy | 59% → 75% | 35% → 42% | 0 → 3 | 2/6 |
| Zippy wake up | 58% → 92% | 36% → 49% | 0 → 0 | 5/6 |

Training helps most on hard phrases ("Zippy wake up", "Yo Zippy"). Distinct phrases (Okay Computer, Hey Jarvis, Hey Fred) stay clean on this conv set. The Hey Zippy rows are a worst case because the conv set is packed with Zippy sound-alikes; even so, a trained custom "Hey Zippy" had fewer conv false wakes (104) than the built-in table at Sensitivity 55 (296), at the cost of lower recall than the built-in (82% vs 92% same-voice held-out).

## Device / emulator

Android 15 x86_64 AVD `z35`. KVM boot hung (qemu never scheduled vCPUs). Booted with TCG (`ANDROID_I_WANT_MY_TCG=yes`, `-accel off`). `Zippy-1.81-x86test.apk` installed (`versionName` 1.81, abi x86_64).

`v1.81/test/device/run_device_test.sh` on the same six "Hey Jarvis" takes + three ordinary sentences as `device_in/expected.json`:

- On-device `heard()` tokens matched the desktop Python recognizer **exactly** on all 6 takes.
- `WakeTuner.tune` on the device did not finish within the ~25 min wall-time cap (TCG is too slow for the many KWS threshold sweeps; `app_process` was still at 0% output past 23:25 CST after ~33 min CPU). The Java tuner is already covered by V181Test and the desktop eval. The on-device tune comparison is unverified.

`expected.json` was written before T_CAP, so its desktop threshold of 0.45 is stale; with T_CAP the same takes would start at 0.30. The heard-token comparison is unaffected.

## Packaging

`/workspace/zippy-repo/v181-local/Zippy.apk` (local only; not published)

- 72,652,501 bytes (69.3 MiB)
- SHA-256 `86dc0b5fecbedd2b84e77f618da836abad9f0773b2d4a3d8e654acb0001a3813`
- versionCode 10, versionName 1.81
- Same cert as 1.3–1.8: `c8b8d668763adeae0c7c07764e2e5038c551965b86206161b5eff6a5343924e4`
- apksigner v2+v3 verified (v1 off, same as 1.8), zipalign -P 16 OK
- `v1.81/test/run_tests.sh`: ALL 1.81 TESTS PASSED (35 V181 tests + 1.8…1.3 suites, layout checks)
