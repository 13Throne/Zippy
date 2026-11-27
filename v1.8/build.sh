#!/usr/bin/env bash
# Rebuild Zippy v1.8 from the original v1.2 APK: v1.3..v1.7 patches, Silero VAD model, the Java
# classes of every version (a later version's file replaces an earlier one), then sign.
# Usage: ZIPPY_KS_PASS=... ./build.sh <Zippy-v1.2.apk> <keystore.jks> <out.apk>   (SHERPA_CACHE=dir optional)
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
R="$HERE/.."
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
python3 "$R/v1.3/patch_smali.py" "$W/dec"
python3 "$R/v1.4/patch_smali_v14.py" "$W/dec"
python3 "$R/v1.5/patch_smali_v15.py" "$W/dec"
python3 "$R/v1.6/patch_v16.py" "$W/dec" "$R/v1.6/assets/voice"
python3 "$R/v1.7/patch_v17.py" "$W/dec" "$SHERPA_CACHE" "$R/v1.7/assets/kws/keywords.txt"
python3 "$HERE/patch_v18.py" "$W/dec" "$SHERPA_CACHE"

# Java sources: newest version of each file wins (1.8 > 1.7 > 1.6 > 1.5 > 1.4 > 1.3)
SRC=()
newest() { for v in v1.8 v1.7 v1.6 v1.5 v1.4 v1.3; do [ -e "$R/$v/java/com/zippy/ear/$1" ] && { echo "$R/$v/java/com/zippy/ear/$1"; return; }; done; }
for name in $(for v in v1.3 v1.4 v1.5 v1.6 v1.7 v1.8; do ls "$R/$v/java/com/zippy/ear/" 2>/dev/null; done | sort -u); do
  case "$name" in *.java) ;; *) continue;; esac
  # v1.4 only shipped these as Java (its other files were smali-patch helpers)
  f="$(newest "$name")"
  case "$f" in "$R/v1.4/"*) case "$name" in VoiceMath.java|VoiceMatch.java|Sensitivity.java|WakePhrase.java|ListenState.java) ;; *) continue;; esac;; esac
  SRC+=("$f")
done
mkdir -p "$W/cls" "$W/stubcls" "$W/dex" "$W/sherpadex"
javac --release 11 -nowarn -cp "$ANDROID_JAR" -d "$W/stubcls" $(find "$HERE/stubs" -name "*.java")
javac --release 11 -Xlint:-options -encoding UTF-8 -cp "$ANDROID_JAR:$W/stubcls:$SHERPA_JAR" -d "$W/cls" "${SRC[@]}"
"$BT/d8" --release --min-api 26 --lib "$ANDROID_JAR" --classpath "$W/stubcls" --classpath "$SHERPA_JAR" --output "$W/dex" $(find "$W/cls" -name '*.class')
# the official Sherpa-ONNX Kotlin API (KeywordSpotter, Vad, configs); Kotlin stdlib is already in classes.dex
"$BT/d8" --release --min-api 26 --lib "$ANDROID_JAR" --classpath "$W/stubcls" --output "$W/sherpadex" "$SHERPA_JAR" 2>&1 | grep -v "Missing class\|^$" || true

java -jar "$APKTOOL" b "$W/dec" -o "$W/unsigned.apk"
cp "$W/dex/classes.dex" "$W/classes2.dex"
cp "$W/sherpadex/classes.dex" "$W/classes3.dex"
(cd "$W" && zip -q -9 unsigned.apk classes2.dex classes3.dex)
mkdir -p "$W/merge" && unzip -q -o "$W/unsigned.apk" classes.dex -d "$W/merge"
"$BT/d8" --min-api 26 --lib "$ANDROID_JAR" --output "$W/merge" "$W/merge/classes.dex" "$W/classes2.dex" "$W/classes3.dex" 2>&1 | grep -v "Missing class\|^$" || true
# Zopfli re-deflate of the big entries (~1 MB smaller, identical contents); skipped if zopfli isn't installed
ZPY="${ZIPPY_PY:-/workspace/grpcenv/bin/python}"
if "$ZPY" -c "import zopfli.zlib" 2>/dev/null; then
  "$ZPY" "$R/v1.8/zopfli_apk.py" "$W/unsigned.apk" "$W/zopfli.apk" && mv "$W/zopfli.apk" "$W/unsigned.apk"
else echo "zopfli not installed: skipping the Zopfli re-deflate"; fi
"$BT/zipalign" -f -P 16 4 "$W/unsigned.apk" "$W/aligned.apk"
"$BT/apksigner" sign --ks "$KS" --ks-key-alias zippy --ks-pass env:ZIPPY_KS_PASS --key-pass env:ZIPPY_KS_PASS \
  --min-sdk-version 23 --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out "$OUT" "$W/aligned.apk"
"$BT/apksigner" verify --verbose --print-certs "$OUT"
"$BT/zipalign" -c -P 16 4 "$OUT" && echo "zipalign -P 16: OK"
unzip -v "$OUT" 'assets/voice/*' | awk '/assets\/voice\// {n++; if ($2 != "Stored") bad++} END {print n " voice clips, " (bad+0) " compressed"; exit (n != 19 || bad > 0)}'
unzip -l "$OUT" "assets/kws/*" "assets/vad/*" "lib/*/libsherpa-onnx-jni.so" "lib/*/libonnxruntime.so" | awk -v want=$(( 6 + 2 * (2 + $(echo "${TEST_ABIS:-}" | tr "," "\n" | grep -c .)) )) '/assets\/(kws|vad)\/|lib\// {n++} END {print n " Sherpa-ONNX files (expect " want ")"; exit (n != want)}'
ls -l "$OUT" | awk '{printf "APK size: %.1f MiB (%d bytes)\n", $5/1048576, $5}'
