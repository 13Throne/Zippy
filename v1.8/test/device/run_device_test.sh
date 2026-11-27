#!/usr/bin/env bash
# Runs CaptureDeviceTest on a connected device/emulator through app_process, using the INSTALLED Zippy APK's
# classes (Capture, SileroGate, EnergyVad, Sherpa-ONNX Kotlin API) and native libs.
# Usage: run_device_test.sh <out-dir> (<wav> <greetStart> <greetLen>)...   (needs adb, javac, d8)
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; V18="$HERE/../.."
ANDROID_JAR="${ANDROID_JAR:-/workspace/android-tools/android-35/android.jar}"
BT="${BUILD_TOOLS:-/workspace/android-tools/android-15}"
SHERPA_CACHE="${SHERPA_CACHE:-$HOME/.cache/zippy-sherpa}"
OUTD="$1"; shift; mkdir -p "$OUTD"
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
CP="$ANDROID_JAR:$SHERPA_CACHE/aar/classes.jar"
javac --release 11 -nowarn -encoding UTF-8 -cp "$CP" -d "$T/app" "$V18"/java/com/zippy/ear/{Capture,EnergyVad,SileroGate}.java
printf 'package com.zippy.ear;\npublic class ListenService { public static final int VAD_WARM = %s; }\n' "$(grep -o 'VAD_WARM = [^;]*' "$V18/java/com/zippy/ear/ListenService.java" | sed 's/VAD_WARM = //;s/SAMPLE_RATE/16000/')" > "$T/ListenService.java"
javac --release 11 -nowarn -cp "$CP" -d "$T/app" "$T/ListenService.java"
javac --release 11 -nowarn -encoding UTF-8 -cp "$CP:$T/app" -d "$T/c" "$HERE/CaptureDeviceTest.java"
"$BT/d8" --min-api 26 --lib "$ANDROID_JAR" --classpath "$SHERPA_CACHE/aar/classes.jar" --classpath "$T/app" --output "$T" $(find "$T/c" -name '*.class') 2>&1 | grep -v "^$" || true
D=/data/local/tmp/zippy-cap; adb shell "rm -rf $D; mkdir -p $D/w"
adb push -q "$T/classes.dex" $D/test.dex
adb push -q "$SHERPA_CACHE/vad/silero_vad.onnx" $D/silero_vad.onnx
ARGS=(); while [ $# -ge 3 ]; do adb push -q "$1" "$D/w/$(basename "$1")"; ARGS+=("$D/w/$(basename "$1")" "$2" "$3"); shift 3; done
APK=$(adb shell pm path com.zippy.ear | head -1 | sed 's/package://' | tr -d '\r'); LIB="$(dirname "$APK")/lib/$(adb shell getprop ro.product.cpu.abi | tr -d '\r' | sed 's/arm64-v8a/arm64/')"
adb shell "CLASSPATH=$APK:$D/test.dex app_process -Djava.library.path=$LIB /system/bin com.zippy.ear.CaptureDeviceTest $D/silero_vad.onnx ${ARGS[*]}" | tee "$OUTD/device_out.txt"
for ((i=0; i<${#ARGS[@]}; i+=3)); do adb pull -q "${ARGS[$i]}.flags" "$OUTD/" ; done
