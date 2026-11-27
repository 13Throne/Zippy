#!/usr/bin/env bash
# Runs KwsDeviceTest on a connected device/emulator through app_process, using the INSTALLED Zippy APK's
# dex (Sherpa-ONNX Kotlin API + Kotlin stdlib) and native libs. Prints one line per wav, then the RTF.
# Usage: run_device_test.sh <sensitivity> <wav>...   (needs adb, javac, d8; SHERPA_CACHE as for build.sh)
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; V17="$HERE/../.."
ANDROID_JAR="${ANDROID_JAR:-/workspace/android-tools/android-35/android.jar}"
BT="${BUILD_TOOLS:-/workspace/android-tools/android-15}"
SHERPA_CACHE="${SHERPA_CACHE:-$HOME/.cache/zippy-sherpa}"
SENS="$1"; shift
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
javac --release 11 -nowarn -encoding UTF-8 -cp "$ANDROID_JAR:$SHERPA_CACHE/aar/classes.jar" -d "$T/c" "$HERE/KwsDeviceTest.java" "$V17/java/com/zippy/ear/KwsTuning.java"
"$BT/d8" --min-api 26 --lib "$ANDROID_JAR" --classpath "$SHERPA_CACHE/aar/classes.jar" --output "$T" $(find "$T/c" -name '*.class') 2>&1 | grep -v "^$" || true
D=/data/local/tmp/zippy-kws; adb shell "rm -rf $D; mkdir -p $D/m $D/w"
adb push -q "$T/classes.dex" $D/test.dex
for f in encoder decoder joiner; do adb push -q "$SHERPA_CACHE/model/$f-epoch-12-avg-2-chunk-16-left-64.int8.onnx" $D/m/$f.int8.onnx; done
adb push -q "$SHERPA_CACHE/model/tokens.txt" $D/m/tokens.txt; adb push -q "$V17/assets/kws/keywords.txt" $D/m/keywords.txt
ARGS=(); for w in "$@"; do n="$(basename "$(dirname "$w")")_$(basename "$w")"; adb push -q "$w" "$D/w/$n"; ARGS+=("$D/w/$n"); done
APK=$(adb shell pm path com.zippy.ear | head -1 | sed 's/package://' | tr -d '\r'); LIB="$(dirname "$APK")/lib/$(adb shell getprop ro.product.cpu.abi | tr -d '\r' | sed 's/arm64-v8a/arm64/;s/x86_64/x86_64/')"
adb shell "CLASSPATH=$APK:$D/test.dex app_process -Djava.library.path=$LIB /system/bin KwsDeviceTest $D/m $SENS ${ARGS[*]}"
