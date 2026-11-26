#!/usr/bin/env bash
# Rebuild Zippy v1.3 from the v1.2 APK: apktool decode -> smali patches -> new Java helpers -> sign.
# Usage: ZIPPY_KS_PASS=... ./build.sh <Zippy-v1.2.apk> <keystore.jks> <out.apk>
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
IN="$1"; KS="$2"; OUT="$3"
APKTOOL="${APKTOOL:-/tmp/tools/apktool.jar}"
BT="${BUILD_TOOLS:-/workspace/android-tools/android-15}"
ANDROID_JAR="${ANDROID_JAR:-/workspace/android-tools/android-35/android.jar}"
W="$(mktemp -d)"
trap 'rm -rf "$W"' EXIT

java -jar "$APKTOOL" d -f "$IN" -o "$W/dec"
python3 "$HERE/patch_smali.py" "$W/dec"

# Compile new helper classes (stubs only for compile, not packaged), dex them, baksmali into the decode
mkdir -p "$W/cls" "$W/stubcls" "$W/dex"
javac --release 11 -nowarn -cp "$ANDROID_JAR" -d "$W/stubcls" $(find "$HERE/stubs" -name "*.java")
javac --release 11 -cp "$ANDROID_JAR:$W/stubcls" -d "$W/cls" "$HERE"/java/com/zippy/ear/*.java
"$BT/d8" --release --min-api 26 --lib "$ANDROID_JAR" --classpath "$W/stubcls" --output "$W/dex" $(find "$W/cls" -name '*.class')
java -jar "$APKTOOL" b "$W/dec" -o "$W/unsigned.apk"
# New helpers ship as a second dex (multidex is native on API 21+; minSdk is 26)
cp "$W/dex/classes.dex" "$W/classes2.dex"
(cd "$W" && zip -q -0 unsigned.apk classes2.dex)
"$BT/zipalign" -f -P 16 4 "$W/unsigned.apk" "$W/aligned.apk"
"$BT/apksigner" sign --ks "$KS" --ks-key-alias zippy --ks-pass env:ZIPPY_KS_PASS --key-pass env:ZIPPY_KS_PASS \
  --min-sdk-version 23 --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out "$OUT" "$W/aligned.apk"
"$BT/apksigner" verify --verbose --print-certs "$OUT"
"$BT/apksigner" verify --verbose --min-sdk-version 23 "$OUT" | grep "v1 scheme"
