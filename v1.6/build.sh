#!/usr/bin/env bash
# Rebuild Zippy v1.6 from the original v1.2 APK: v1.3..v1.6 patches, Java classes and Liora's clips, then sign.
# Usage: ZIPPY_KS_PASS=... ./build.sh <Zippy-v1.2.apk> <keystore.jks> <out.apk>
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
V13="$HERE/../v1.3"; V14="$HERE/../v1.4"; V15="$HERE/../v1.5"
IN="$1"; KS="$2"; OUT="$3"
APKTOOL="${APKTOOL:-/tmp/tools/apktool.jar}"
BT="${BUILD_TOOLS:-/workspace/android-tools/android-15}"
ANDROID_JAR="${ANDROID_JAR:-/workspace/android-tools/android-35/android.jar}"
W="$(mktemp -d)"
trap 'rm -rf "$W"' EXIT

java -jar "$APKTOOL" d -f "$IN" -o "$W/dec"
python3 "$V13/patch_smali.py" "$W/dec"
python3 "$V14/patch_smali_v14.py" "$W/dec"
python3 "$V15/patch_smali_v15.py" "$W/dec"
python3 "$HERE/patch_v16.py" "$W/dec" "$HERE/assets/voice"

# 1.4 classes still used; ListenService is superseded by 1.5's, ZippyUi by the Java MainActivity
V14_SRC=()
for f in VoiceMath VoiceMatch Sensitivity WakePhrase ListenState; do V14_SRC+=("$V14/java/com/zippy/ear/$f.java"); done

# 1.5 classes, minus the ones 1.6 replaces
V15_SRC=()
for f in "$V15"/java/com/zippy/ear/*.java; do [ -e "$HERE/java/com/zippy/ear/$(basename "$f")" ] || V15_SRC+=("$f"); done

mkdir -p "$W/cls" "$W/stubcls" "$W/dex"
javac --release 11 -nowarn -cp "$ANDROID_JAR" -d "$W/stubcls" $(find "$HERE/stubs" -name "*.java")
javac --release 11 -Xlint:-options -cp "$ANDROID_JAR:$W/stubcls" -d "$W/cls" \
  "$V13"/java/com/zippy/ear/*.java "${V14_SRC[@]}" "${V15_SRC[@]}" "$HERE"/java/com/zippy/ear/*.java
"$BT/d8" --release --min-api 26 --lib "$ANDROID_JAR" --classpath "$W/stubcls" --output "$W/dex" $(find "$W/cls" -name '*.class')

java -jar "$APKTOOL" b "$W/dec" -o "$W/unsigned.apk"
cp "$W/dex/classes.dex" "$W/classes2.dex"
(cd "$W" && zip -q -0 unsigned.apk classes2.dex)
# cross-dex sanity: every class referenced must resolve and nothing may be defined twice
mkdir -p "$W/merge" && unzip -q -o "$W/unsigned.apk" classes.dex -d "$W/merge"
"$BT/d8" --min-api 26 --lib "$ANDROID_JAR" --output "$W/merge" "$W/merge/classes.dex" "$W/classes2.dex" 2>&1 | grep -v "Missing class\|^$" || true
"$BT/zipalign" -f -P 16 4 "$W/unsigned.apk" "$W/aligned.apk"
"$BT/apksigner" sign --ks "$KS" --ks-key-alias zippy --ks-pass env:ZIPPY_KS_PASS --key-pass env:ZIPPY_KS_PASS \
  --min-sdk-version 23 --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out "$OUT" "$W/aligned.apk"
"$BT/apksigner" verify --verbose --print-certs "$OUT"
"$BT/apksigner" verify --verbose --min-sdk-version 23 "$OUT" | grep "v1 scheme"
# Liora clips must be STORED so AssetManager.openFd() works
unzip -v "$OUT" 'assets/voice/*' | awk '/assets\/voice\// {n++; if ($2 != "Stored") bad++} END {print n " voice clips, " (bad+0) " compressed"; exit (n != 19 || bad > 0)}'
