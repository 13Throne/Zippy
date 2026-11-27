#!/usr/bin/env bash
# Zippy 1.8 tests: packaging (version, Sherpa libs per ABI + 16 KB alignment, KWS model, Silero VAD model),
# 1.8 logic (Capture: end of speech / greeting / barge-in; EnergyVad; VoskWords; TaskClean; EchoGuard; Timeline),
# layout, then the whole 1.7 suite (which runs the 1.6/1.5/1.4/1.3 suites) against this APK.
# Usage: run_tests.sh <Zippy.apk>     (PYTHON=python-with-vosk+sentencepiece+sherpa-onnx, SHERPA_CACHE=dir)
# The long-request / greeting / gap tests over real audio are in ../capture-test (capture_eval.py).
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
V17="$HERE/../v1.7"; V14="$HERE/../v1.4"
APK="$1"
ANDROID_JAR="${ANDROID_JAR:-/workspace/android-tools/android-35/android.jar}"
BT="${BUILD_TOOLS:-/workspace/android-tools/android-15}"
SHERPA_CACHE="${SHERPA_CACHE:-$HOME/.cache/zippy-sherpa}"
T="$(mktemp -d)"; trap 'rm -rf "$T" "$V17/test/.run_tests_18.sh"' EXIT
"$HERE/fetch_sherpa.sh" "$SHERPA_CACHE" >/dev/null

echo "== 1.8 packaging"
"$BT/aapt2" dump badging "$APK" | grep -q "versionCode='9' versionName='1.8'" && echo "PASS versionName 1.8 / versionCode 9"
unzip -q -o "$APK" 'assets/vad/*' 'assets/barge/*' classes3.dex -d "$T/apk"
cmp -s "$T/apk/assets/barge/env.txt" "$HERE/assets/voice/env.txt" && [ "$(wc -l < "$T/apk/assets/barge/env.txt")" = 19 ] && echo "PASS assets/barge/env.txt: loudness envelopes of all 19 Liora clips (barge-in)"
cmp -s "$T/apk/assets/vad/silero_vad.onnx" "$SHERPA_CACHE/vad/silero_vad.onnx" || { echo "FAIL silero_vad.onnx differs"; exit 1; }
echo "$(sha256sum < "$T/apk/assets/vad/silero_vad.onnx" | cut -d' ' -f1)" | grep -q '^9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6$' && echo "PASS assets/vad/silero_vad.onnx is the official Silero VAD (sha256 9e2449e1…)"
"$BT/dexdump" "$T/apk/classes3.dex" > "$T/d.txt" 2>/dev/null
for c in Vad VadModelConfig SileroVadModelConfig KeywordSpotter; do grep -q "Class descriptor  : 'Lcom/k2fsa/sherpa/onnx/$c;'" "$T/d.txt" || { echo "FAIL $c missing"; exit 1; }; done
echo "PASS Sherpa-ONNX Vad / SileroVadModelConfig / KeywordSpotter are in classes3.dex"
unzip -q -o "$APK" classes2.dex -d "$T/apk"; "$BT/dexdump" "$T/apk/classes2.dex" > "$T/d2.txt" 2>/dev/null
for c in Capture VoiceEnv SileroGate AndroidStream VoskJob Greeting TaskClean Timeline VoskWords EnergyVad ListenService MainActivity KwsGuard VoiceMatch Updater; do
  grep -q "Class descriptor  : 'Lcom/zippy/ear/$c;'" "$T/d2.txt" || { echo "FAIL com.zippy.ear.$c missing"; exit 1; }; done
echo "PASS 1.8 classes (Capture, SileroGate, AndroidStream, VoskJob, Greeting, TaskClean, Timeline, …) + 1.7 classes are in classes2.dex"
SZ=$(stat -c %s "$APK"); echo "INFO APK size $SZ bytes ($(awk "BEGIN{printf \"%.1f\", $SZ/1048576}") MiB)"

javac --release 11 -nowarn -cp "$ANDROID_JAR" -d "$T/stub" $(find "$HERE/stubs" -name "*.java")
javac --release 11 -nowarn -encoding UTF-8 -cp "$ANDROID_JAR:$T/stub" -d "$T/cls" \
  "$HERE"/java/com/zippy/ear/{Capture,EnergyVad,TaskClean,EchoGuard,VoskWords,Timeline}.java \
  "$V17"/java/com/zippy/ear/{Sensitivity,KwsTuning,TaskText}.java "$V14"/java/com/zippy/ear/WakePhrase.java \
  "$HERE"/test/V18Test.java "$V17"/test/V17Test.java "$V14"/test/SensitivityDirectionTest.java
echo "== V18Test (capture, end of speech, greeting/barge-in, task cleanup, echo guard, timeline)"; java -cp "$T/cls:$T/stub:$ANDROID_JAR" V18Test
echo "== V17Test against the 1.8 EchoGuard"; java -cp "$T/cls:$T/stub:$ANDROID_JAR" V17Test | tail -1
echo "== Layout check (1.8)"; python3 "$HERE/test/check_layout.py"
echo "== v1.7 suite (Sherpa KWS packaging + keywords, Liora clips, 1.7 + 1.6 + 1.5 + 1.4 + 1.3)"
python3 - "$V17/test/run_tests.sh" "$V17/test/.run_tests_18.sh" <<'PY'
import sys
s = open(sys.argv[1]).read()
for a, b in [("versionCode='8' versionName='1.7'", "versionCode='9' versionName='1.8'"),
             ("versionName 1.7 / versionCode 8", "versionName 1.8 / versionCode 9"),
             ("versionName 1.7 \\/ versionCode 8", "versionName 1.8 \\/ versionCode 9"),
             (".run_tests_17.sh", ".run_tests_17for18.sh")]:
    s = s.replace(a, b)
open(sys.argv[2], "w").write(s)
PY
chmod +x "$V17/test/.run_tests_18.sh"
set +e; "$V17/test/.run_tests_18.sh" "$APK" > "$T/v17.log" 2>&1; rc=$?; set -e
rm -f "$V17/test/.run_tests_18.sh" "$HERE/../v1.6/test/.run_tests_17for18.sh"
grep -E "^==|ALL .*PASS|passed|FAIL|OK" "$T/v17.log"; echo "PASS count in 1.7 suite: $(grep -c '^PASS' "$T/v17.log")"
[ $rc -eq 0 ] && echo "ALL 1.8 TESTS PASSED" || { echo "1.7 suite FAILED (rc $rc)"; tail -20 "$T/v17.log"; exit 1; }
