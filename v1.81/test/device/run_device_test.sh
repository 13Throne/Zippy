#!/usr/bin/env bash
# Runs PhraseDeviceTest on a connected emulator/device through app_process with the INSTALLED Zippy APK's classes
# (KwsLab, WakeTuner, BpeTokenizer, Sherpa-ONNX Kotlin API) and native libs: the custom wake phrase pipeline on Android.
# Usage: run_device_test.sh <out-file> <phrase> <takes...> -- <others...>     (needs adb, javac, d8)
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; V="$HERE/../.."
ANDROID_JAR="${ANDROID_JAR:-/workspace/android-tools/android-35/android.jar}"
BT="${BUILD_TOOLS:-/workspace/android-tools/android-15}"
SHERPA_CACHE="${SHERPA_CACHE:-$HOME/.cache/zippy-sherpa}"
OUT="$1"; PHRASE="$2"; shift 2
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
CP="$ANDROID_JAR:$SHERPA_CACHE/aar/classes.jar"
javac --release 11 -nowarn -encoding UTF-8 -cp "$CP:$V/../v1.8/stubs" -d "$T/app" "$V"/java/com/zippy/ear/{BpeTokenizer,WakeTuner,KwsLab,Kws,WakeWord,ZippyPrefs,Sensitivity}.java \
  "$V"/../v1.7/java/com/zippy/ear/KwsTuning.java "$V"/../v1.6/java/com/zippy/ear/{Liora,Tabs}.java 2>/dev/null
javac --release 11 -nowarn -encoding UTF-8 -cp "$CP:$T/app" -d "$T/c" "$HERE/PhraseDeviceTest.java"
"$BT/d8" --min-api 26 --lib "$ANDROID_JAR" --classpath "$SHERPA_CACHE/aar/classes.jar" --classpath "$T/app" --output "$T" $(find "$T/c" -name '*.class') 2>&1 | grep -v "^$" || true
D=/data/local/tmp/zippy-phrase; adb shell "rm -rf $D; mkdir -p $D/kws $D/w"
adb push -q "$T/classes.dex" $D/test.dex
M="$SHERPA_CACHE/model"
adb push -q "$M/encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx" $D/kws/encoder.int8.onnx
adb push -q "$M/decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx" $D/kws/decoder.int8.onnx
adb push -q "$M/joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx" $D/kws/joiner.int8.onnx
adb push -q "$M/tokens.txt" $D/kws/tokens.txt
adb push -q "$V/../v1.7/assets/kws/keywords.txt" $D/kws/keywords.txt
adb push -q "$V/assets/kws/bpe.vocab" $D/kws/bpe.vocab
ARGS=(); for f in "$@"; do if [ "$f" = "--" ]; then ARGS+=("--"); else adb push -q "$f" "$D/w/$(basename "$f")"; ARGS+=("$D/w/$(basename "$f")"); fi; done
APK=$(adb shell pm path com.zippy.ear | head -1 | sed 's/package://' | tr -d '\r'); LIB="$(dirname "$APK")/lib/$(adb shell getprop ro.product.cpu.abi | tr -d '\r' | sed 's/arm64-v8a/arm64/')"
adb shell "CLASSPATH=$APK:$D/test.dex app_process -Djava.library.path=$LIB /system/bin com.zippy.ear.PhraseDeviceTest $D/kws/ '$PHRASE' ${ARGS[*]}" | tee "$OUT"
