#!/usr/bin/env bash
# Zippy 1.6 tests: Liora clips (count, words, duration, edges), APK packaging, 1.6 + 1.5 logic, layout, then all 1.4/1.3 tests.
# Usage: run_tests.sh [Zippy.apk]
# Needs: a Python with `vosk` + `numpy` (PYTHON=...), and the Vosk model (VOSK_MODEL=..., default: unpacked from the APK).
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
V15="$HERE/../v1.5"; V14="$HERE/../v1.4"
APK="${1:-}"
ANDROID_JAR="${ANDROID_JAR:-/workspace/android-tools/android-35/android.jar}"
BT="${BUILD_TOOLS:-/workspace/android-tools/android-15}"
PY="${PYTHON:-python3}"
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT

if [ -z "${VOSK_MODEL:-}" ] && [ -n "$APK" ]; then
  unzip -q -o "$APK" assets/model.zip -d "$T" && unzip -q "$T/assets/model.zip" -d "$T/model"
  VOSK_MODEL="$(dirname "$(find "$T/model" -maxdepth 2 -type d -name am | head -1)")"
fi
echo "== Liora clips (source)"
"$PY" "$HERE/test/clip_check.py" "$HERE/assets/voice" "$VOSK_MODEL"

if [ -n "$APK" ]; then
  echo "== APK packaging"
  unzip -q -o "$APK" 'assets/voice/*' -d "$T/apk"
  for f in "$HERE"/assets/voice/*.m4a; do cmp -s "$f" "$T/apk/assets/voice/$(basename "$f")" || { echo "FAIL $(basename "$f") differs in APK"; exit 1; }; done
  echo "PASS 19 clips in assets/voice/ are byte-identical to the verified clips"
  unzip -v "$APK" 'assets/voice/*' | awk '/assets\/voice\// {n++; if ($2 != "Stored") bad++} END {if (n != 19 || bad > 0) {print "FAIL " n " clips, " bad " compressed"; exit 1} print "PASS all 19 stored uncompressed (AssetManager.openFd works)"}'
  "$BT/zipalign" -c -P 16 4 "$APK" && echo "PASS zipalign -c (4-byte aligned, 16 KB pages for .so)"
  "$BT/aapt2" dump badging "$APK" | grep -q "versionCode='7' versionName='1.6'" && echo "PASS versionName 1.6 / versionCode 7"
  echo "== Liora clips (as packaged)"
  "$PY" "$HERE/test/clip_check.py" "$T/apk/assets/voice" "$VOSK_MODEL" | tail -1
fi

javac --release 11 -nowarn -cp "$ANDROID_JAR" -d "$T/stub" $(find "$HERE/stubs" -name "*.java")
javac --release 11 -nowarn -cp "$ANDROID_JAR:$T/stub" -d "$T/cls" \
  "$HERE"/java/com/zippy/ear/{ZippyPrefs,Tabs,WakeCue,Liora}.java \
  "$V15"/java/com/zippy/ear/{CommandPlan,GrokBot}.java \
  "$V14"/java/com/zippy/ear/{WakePhrase,Sensitivity}.java \
  "$HERE"/test/V16Test.java "$V15"/test/V15Test.java
CP="$T/cls:$T/stub:$ANDROID_JAR"
echo "== V16Test (Liora picking, dropdowns, settings, confirmation)"; java -cp "$CP" V16Test
echo "== V15Test against 1.6 classes";  java -cp "$CP" V15Test
echo "== Layout check";                 python3 "$HERE/test/check_layout.py"
echo "== v1.4 suite (+ v1.3)";          "$V14/test/run_tests.sh"
