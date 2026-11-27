#!/usr/bin/env bash
# Rebuild Zippy v1.7 from the original v1.2 APK: v1.3..v1.6 patches, Sherpa-ONNX KWS (AAR libs, Kotlin
# API classes, model), Java classes, then sign.
# Usage: ZIPPY_KS_PASS=... ./build.sh <Zippy-v1.2.apk> <keystore.jks> <out.apk>   (SHERPA_CACHE=dir optional)
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
V13="$HERE/../v1.3"; V14="$HERE/../v1.4"; V15="$HERE/../v1.5"; V16="$HERE/../v1.6"
IN="$1"; KS="$2"; OUT="$3"
APKTOOL="${APKTOOL:-/tmp/tools/apktool.jar}"
BT="${BUILD_TOOLS:-/workspace/android-tools/android-15}"
ANDROID_JAR="${ANDROID_JAR:-/workspace/android-tools/android-35/android.jar}"
SHERPA_CACHE="${SHERPA_CACHE:-$HOME/.cache/zippy-sherpa}"
W="$(mktemp -d)"
trap 'rm -rf "$W"' EXIT

"$HERE/fetch_sherpa.sh" "$SHERPA_CACHE"
SHERPA_JAR="$SHERPA_CACHE/aar/classes.jar"

java -jar "$APKTOOL" d -f "$IN" -o "$W/dec"
python3 "$V13/patch_smali.py" "$W/dec"
python3 "$V14/patch_smali_v14.py" "$W/dec"
python3 "$V15/patch_smali_v15.py" "$W/dec"
python3 "$V16/patch_v16.py" "$W/dec" "$V16/assets/voice"
python3 "$HERE/patch_v17.py" "$W/dec" "$SHERPA_CACHE" "$HERE/assets/kws/keywords.txt"

override() { [ -e "$HERE/java/com/zippy/ear/$(basename "$1")" ]; }
V14_SRC=()
for f in VoiceMath VoiceMatch Sensitivity WakePhrase ListenState; do override "$f.java" || V14_SRC+=("$V14/java/com/zippy/ear/$f.java"); done
V15_SRC=(); for f in "$V15"/java/com/zippy/ear/*.java; do override "$f" || [ -e "$V16/java/com/zippy/ear/$(basename "$f")" ] || V15_SRC+=("$f"); done
V16_SRC=(); for f in "$V16"/java/com/zippy/ear/*.java; do override "$f" || V16_SRC+=("$f"); done

mkdir -p "$W/cls" "$W/stubcls" "$W/dex" "$W/sherpadex"
javac --release 11 -nowarn -cp "$ANDROID_JAR" -d "$W/stubcls" $(find "$HERE/stubs" -name "*.java")
javac --release 11 -Xlint:-options -encoding UTF-8 -cp "$ANDROID_JAR:$W/stubcls:$SHERPA_JAR" -d "$W/cls" \
  "$V13"/java/com/zippy/ear/*.java "${V14_SRC[@]}" "${V15_SRC[@]}" "${V16_SRC[@]}" "$HERE"/java/com/zippy/ear/*.java
"$BT/d8" --release --min-api 26 --lib "$ANDROID_JAR" --classpath "$W/stubcls" --classpath "$SHERPA_JAR" --output "$W/dex" $(find "$W/cls" -name '*.class')
# the official Sherpa-ONNX Kotlin API (KeywordSpotter, OnlineStream, configs); Kotlin stdlib is already in classes.dex
"$BT/d8" --release --min-api 26 --lib "$ANDROID_JAR" --classpath "$W/stubcls" --output "$W/sherpadex" "$SHERPA_JAR" 2>&1 | grep -v "Missing class\|^$" || true

java -jar "$APKTOOL" b "$W/dec" -o "$W/unsigned.apk"
cp "$W/dex/classes.dex" "$W/classes2.dex"
cp "$W/sherpadex/classes.dex" "$W/classes3.dex"
(cd "$W" && zip -q -0 unsigned.apk classes2.dex classes3.dex)
# cross-dex sanity: every class referenced must resolve and nothing may be defined twice
mkdir -p "$W/merge" && unzip -q -o "$W/unsigned.apk" classes.dex -d "$W/merge"
"$BT/d8" --min-api 26 --lib "$ANDROID_JAR" --output "$W/merge" "$W/merge/classes.dex" "$W/classes2.dex" "$W/classes3.dex" 2>&1 | grep -v "Missing class\|^$" || true
"$BT/zipalign" -f -P 16 4 "$W/unsigned.apk" "$W/aligned.apk"
"$BT/apksigner" sign --ks "$KS" --ks-key-alias zippy --ks-pass env:ZIPPY_KS_PASS --key-pass env:ZIPPY_KS_PASS \
  --min-sdk-version 23 --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out "$OUT" "$W/aligned.apk"
"$BT/apksigner" verify --verbose --print-certs "$OUT"
"$BT/zipalign" -c -P 16 4 "$OUT" && echo "zipalign -P 16: OK"
# Liora clips must be STORED so AssetManager.openFd() works
unzip -v "$OUT" 'assets/voice/*' | awk '/assets\/voice\// {n++; if ($2 != "Stored") bad++} END {print n " voice clips, " (bad+0) " compressed"; exit (n != 19 || bad > 0)}'
unzip -l "$OUT" "assets/kws/*" "lib/*/libsherpa-onnx-jni.so" "lib/*/libonnxruntime.so" | awk -v want=$(( 5 + 2 * (2 + $(echo "${TEST_ABIS:-}" | tr "," "\n" | grep -c .)) )) '/assets\/kws\/|lib\// {n++} END {print n " Sherpa-ONNX files (expect " want ")"; exit (n != want)}'
ls -l "$OUT" | awk '{printf "APK size: %.1f MB (%d bytes)\n", $5/1048576, $5}'
