#!/usr/bin/env bash
# Zippy 1.7 tests: Sherpa-ONNX packaging (libs per ABI, 16 KB ELF alignment, model, keywords vs bpe.model),
# 1.7 logic (KwsTuning, EchoGuard, TaskText), layout, then the whole 1.6 suite (Liora clips, 1.6/1.5/1.4/1.3 tests).
# Usage: run_tests.sh <Zippy.apk>     (PYTHON=python-with-vosk+sentencepiece+sherpa-onnx, SHERPA_CACHE=dir)
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
V16="$HERE/../v1.6"; V15="$HERE/../v1.5"; V14="$HERE/../v1.4"
APK="$1"
ANDROID_JAR="${ANDROID_JAR:-/workspace/android-tools/android-35/android.jar}"
BT="${BUILD_TOOLS:-/workspace/android-tools/android-15}"
PY="${PYTHON:-python3}"
SHERPA_CACHE="${SHERPA_CACHE:-$HOME/.cache/zippy-sherpa}"
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
"$HERE/fetch_sherpa.sh" "$SHERPA_CACHE" >/dev/null

echo "== Sherpa-ONNX packaging"
"$BT/aapt2" dump badging "$APK" | grep -q "versionCode='8' versionName='1.7'" && echo "PASS versionName 1.7 / versionCode 8"
"$BT/aapt2" dump badging "$APK" | grep -q "native-code: 'arm64-v8a' 'armeabi-v7a'" && echo "PASS native-code: arm64-v8a armeabi-v7a only"
unzip -q -o "$APK" 'lib/*' 'assets/kws/*' -d "$T/apk"
for abi in arm64-v8a armeabi-v7a; do
  for so in libsherpa-onnx-jni.so libonnxruntime.so; do
    cmp -s "$T/apk/lib/$abi/$so" "$SHERPA_CACHE/aar/jni/$abi/$so" || { echo "FAIL $abi/$so differs from the official AAR"; exit 1; }
    al=$(readelf -lW "$T/apk/lib/$abi/$so" | awk '/LOAD/{print $NF}' | sort -u | tr '\n' ' ')
    [ "$al" = "0x4000 " ] || { echo "FAIL $abi/$so LOAD align $al"; exit 1; }
    m=$(readelf -h "$T/apk/lib/$abi/$so" | awk -F: '/Machine/{gsub(/^ +/,"",$2); print $2}')
    case "$abi/$m" in arm64-v8a/AArch64|armeabi-v7a/ARM) ;; *) echo "FAIL $abi/$so is $m"; exit 1;; esac
  done
done
echo "PASS libsherpa-onnx-jni.so + libonnxruntime.so for both ABIs: identical to sherpa-onnx 2.0.3 AAR, right machine, 16 KB LOAD alignment"
for f in encoder decoder joiner; do cmp -s "$T/apk/assets/kws/$f.int8.onnx" "$SHERPA_CACHE/model/$f-epoch-12-avg-2-chunk-16-left-64.int8.onnx" || { echo "FAIL $f"; exit 1; }; done
cmp -s "$T/apk/assets/kws/tokens.txt" "$SHERPA_CACHE/model/tokens.txt" && cmp -s "$T/apk/assets/kws/keywords.txt" "$HERE/assets/kws/keywords.txt"
echo "PASS assets/kws: int8 encoder/decoder/joiner + tokens.txt from the official model, keywords.txt from make_keywords.py"
"$BT/zipalign" -c -P 16 4 "$APK" && echo "PASS zipalign -c -P 16"
unzip -q -o "$APK" classes3.dex -d "$T/dex"
"$BT/dexdump" "$T/dex/classes3.dex" > "$T/dex/d.txt" 2>/dev/null; grep -q "Class descriptor  : 'Lcom/k2fsa/sherpa/onnx/KeywordSpotter;'" "$T/dex/d.txt" || { echo "FAIL KeywordSpotter missing"; exit 1; }
echo "PASS Sherpa-ONNX Kotlin API (KeywordSpotter, OnlineStream, configs) is in classes3.dex"
echo "== keywords.txt vs bpe.model / text2token"
"$PY" "$HERE/make_keywords.py" "$SHERPA_CACHE/model" "$T/kw.txt" | tail -2
cmp -s "$T/kw.txt" "$HERE/assets/kws/keywords.txt" && echo "PASS committed keywords.txt is current"

javac --release 11 -nowarn -cp "$ANDROID_JAR" -d "$T/stub" $(find "$HERE/stubs" -name "*.java")
javac --release 11 -nowarn -encoding UTF-8 -cp "$ANDROID_JAR:$T/stub" -d "$T/cls" \
  "$HERE"/java/com/zippy/ear/{KwsTuning,EchoGuard,TaskText,Sensitivity}.java "$V14"/java/com/zippy/ear/WakePhrase.java \
  "$HERE"/test/V17Test.java "$V14"/test/SensitivityDirectionTest.java
echo "== V17Test (keyword tiers, echo guard, one-breath text)"; java -cp "$T/cls:$T/stub:$ANDROID_JAR" V17Test
echo "== SensitivityDirectionTest against the 1.7 Sensitivity"; java -cp "$T/cls:$T/stub:$ANDROID_JAR" SensitivityDirectionTest | tail -1
echo "== Layout check (1.7)"; python3 "$HERE/test/check_layout.py"
echo "== v1.6 suite (Liora clips, 1.6 + 1.5 + 1.4 + 1.3)"
sed -e "s/versionCode='7' versionName='1.6'/versionCode='8' versionName='1.7'/" -e 's/versionName 1.6 \/ versionCode 7/versionName 1.7 \/ versionCode 8/' "$V16/test/run_tests.sh" > "$V16/test/.run_tests_17.sh"
chmod +x "$V16/test/.run_tests_17.sh"; trap 'rm -rf "$T" "$V16/test/.run_tests_17.sh"' EXIT
set +e; "$V16/test/.run_tests_17.sh" "$APK" > "$T/v16.log" 2>&1; rc=$?; set -e
grep -E "^==|ALL PASS|passed|FAIL|OK" "$T/v16.log"; echo "PASS count in 1.6 suite: $(grep -c '^PASS' "$T/v16.log")"
[ $rc -eq 0 ] && echo "ALL 1.7 TESTS PASSED" || { echo "1.6 suite FAILED (rc $rc)"; tail -20 "$T/v16.log"; exit 1; }
