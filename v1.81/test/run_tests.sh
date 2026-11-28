#!/usr/bin/env bash
# Zippy 1.81 tests: packaging (version 1.81 / 10, bpe.vocab, PhraseActivity + VolumeKeyService in the manifest,
# accessibility config, 1.81 classes), V181Test (tokenizer == sentencepiece, WakeTuner, WakeWord, VolumeKeys),
# the Java/Python tuner mirror, the 1.81 layout check, then the whole 1.8 suite (which runs 1.7..1.3) on this APK.
# Usage: run_tests.sh <Zippy.apk>     (PYTHON=python with sentencepiece+sherpa-onnx)
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
V18="$HERE/../v1.8"
APK="$1"
ANDROID_JAR="${ANDROID_JAR:-/workspace/android-tools/android-35/android.jar}"
BT="${BUILD_TOOLS:-/workspace/android-tools/android-15}"
PY="${PYTHON:-/workspace/grpcenv/bin/python}"
T="$(mktemp -d)"; trap 'rm -rf "$T" "$V18/test/.run_tests_181.sh"' EXIT

echo "== 1.81 packaging"
"$BT/aapt2" dump badging "$APK" > "$T/badging.txt"; grep -q "versionCode='10' versionName='1.81'" "$T/badging.txt" && echo "PASS versionName 1.81 / versionCode 10" || { echo "FAIL version"; exit 1; }
unzip -q -o "$APK" assets/kws/bpe.vocab classes2.dex -d "$T/apk"
cmp -s "$T/apk/assets/kws/bpe.vocab" "$HERE/assets/kws/bpe.vocab" && echo "PASS assets/kws/bpe.vocab (497 unigram pieces for typed wake phrases)" || { echo "FAIL bpe.vocab"; exit 1; }
"$BT/aapt2" dump xmltree --file AndroidManifest.xml "$APK" > "$T/m.txt"
grep -q 'com.zippy.ear.PhraseActivity' "$T/m.txt" && echo "PASS PhraseActivity declared" || { echo "FAIL PhraseActivity"; exit 1; }
python3 - "$T/m.txt" <<'PY'
import sys, re
m = open(sys.argv[1]).read()
i = m.find('"com.zippy.ear.VolumeKeyService"'); assert i > 0, "VolumeKeyService missing"
blk = m[m.rfind("E: service", 0, i):]
blk = blk[:blk.find("E: service", 20)] if blk.find("E: service", 20) > 0 else blk
assert "android.permission.BIND_ACCESSIBILITY_SERVICE" in blk, "not protected by BIND_ACCESSIBILITY_SERVICE"
assert "android.accessibilityservice.AccessibilityService" in blk, "no AccessibilityService intent filter"
assert "android.accessibilityservice" in blk and "@0x7f" in blk.split("android.accessibilityservice\"")[-1][:400] or "resource" in blk, "no accessibility meta-data"
print("PASS VolumeKeyService: AccessibilityService, BIND_ACCESSIBILITY_SERVICE, accessibility meta-data")
PY
"$BT/aapt2" dump xmltree --file res/xml/zippy_accessibility.xml "$APK" > "$T/a.txt"
grep -q 'canRequestFilterKeyEvents.*=true' "$T/a.txt" && grep -q 'accessibilityFlags' "$T/a.txt" && ! grep -q 'canRetrieveWindowContent.*=true' "$T/a.txt" \
  && echo "PASS zippy_accessibility.xml: filter key events, no window content" || { echo "FAIL accessibility xml"; cat "$T/a.txt"; exit 1; }
"$BT/aapt2" dump strings "$APK" > "$T/strings.txt"; grep -q "Zippy volume shortcut" "$T/strings.txt" && echo "PASS service label/description strings" || { echo "FAIL strings"; exit 1; }
"$BT/dexdump" "$T/apk/classes2.dex" > "$T/d2.txt" 2>/dev/null
for c in BpeTokenizer WakeTuner WakeWord KwsLab PhraseActivity VolumeKeys VolumeKeyService Kws ListenService MainActivity Capture VoiceMatch; do
  grep -q "Class descriptor  : 'Lcom/zippy/ear/$c;'" "$T/d2.txt" || { echo "FAIL com.zippy.ear.$c missing"; exit 1; }; done
echo "PASS 1.81 classes (BpeTokenizer, WakeTuner, WakeWord, KwsLab, PhraseActivity, VolumeKeys, VolumeKeyService) + 1.8 classes in classes2.dex"
unzip -q -o "$APK" classes3.dex -d "$T/apk"; "$BT/dexdump" "$T/apk/classes3.dex" > "$T/d3.txt" 2>/dev/null
grep -q "Class descriptor  : 'Lcom/k2fsa/sherpa/onnx/OnlineRecognizer;'" "$T/d3.txt" && echo "PASS Sherpa-ONNX OnlineRecognizer present (learned pronunciations)" || { echo "FAIL OnlineRecognizer"; exit 1; }

echo "== V181Test"
javac --release 11 -nowarn -cp "$ANDROID_JAR" -d "$T/stub" $(find "$V18/stubs" -name "*.java")
javac --release 11 -nowarn -encoding UTF-8 -cp "$ANDROID_JAR:$T/stub" -d "$T/cls" \
  "$HERE"/java/com/zippy/ear/{BpeTokenizer,WakeTuner,WakeWord,VolumeKeys,ZippyPrefs,Sensitivity}.java \
  "$HERE"/../v1.7/java/com/zippy/ear/KwsTuning.java "$HERE"/../v1.6/java/com/zippy/ear/{Liora,Tabs}.java "$HERE/test/V181Test.java"
java -cp "$T/cls:$T/stub:$ANDROID_JAR" V181Test "$HERE/test"

echo "== WakeTuner.java == kws-test/phrase_tune.py constants"
python3 - "$HERE" <<'PY'
import re, sys
j = open(sys.argv[1] + "/java/com/zippy/ear/WakeTuner.java").read()
p = open(sys.argv[1] + "/kws-test/phrase_tune.py").read()
jt = re.search(r"THRESHOLDS = \{([^}]*)\}", j).group(1).replace(" ", "")
pt = re.search(r"THRESHOLDS = \[([^\]]*)\]", p).group(1).replace(" ", "")
assert jt == pt, (jt, pt)
pv = {k: float(v) for k, v in re.findall(r"(?m)^(MAX_DIST|MARGIN|SLOPE)\s*=\s*([0-9.]+)", p)}
m = re.search(r"T_MIN, T_MAX = ([0-9.]+), ([0-9.]+)", p); pv["T_MIN"], pv["T_MAX"] = float(m.group(1)), float(m.group(2))
m = re.search(r"T_CAP = ([0-9.]+)", p); pv["T_CAP"] = float(m.group(1))
for k in ("MAX_DIST", "MARGIN", "SLOPE", "T_MIN", "T_MAX", "T_CAP"):
    jv = float(re.search(k + r" = ([0-9.]+)", j).group(1))
    assert jv == pv[k], (k, jv, pv[k])
assert int(re.search(r"MIN_TAKES = (\d+)", j).group(1)) == int(re.search(r"MIN_TAKES = (\d+)", p).group(1))
assert int(re.search(r"MAX_VARIANTS = (\d+)", j).group(1)) == int(re.search(r"MAX_VARIANTS = (\d+)", p).group(1))
print("PASS Java and Python tuners share thresholds, margin, slope, clamp, max distance and variant count")
PY

echo "== Layout check (1.81)"; python3 "$HERE/test/check_layout.py"

echo "== v1.8 suite (capture, Sherpa KWS packaging + keywords, Liora clips, 1.8 .. 1.3)"
python3 - "$V18/test/run_tests.sh" "$V18/test/.run_tests_181.sh" <<'PY'
import sys
s = open(sys.argv[1]).read()
for a, b in [("versionCode='9' versionName='1.8'", "versionCode='10' versionName='1.81'"),
             ("PASS versionName 1.8 / versionCode 9", "PASS versionName 1.81 / versionCode 10"),
             ('trap \'rm -rf "$T" "$V17/test/.run_tests_18.sh"\' EXIT', 'trap \'rm -rf "$T" "$V17/test/.run_tests_18.sh"\' EXIT'),
             ("(\"versionCode='8' versionName='1.7'\", \"versionCode='9' versionName='1.8'\")", "(\"versionCode='8' versionName='1.7'\", \"versionCode='10' versionName='1.81'\")"),
             ('("versionName 1.7 / versionCode 8", "versionName 1.8 / versionCode 9")', '("versionName 1.7 / versionCode 8", "versionName 1.81 / versionCode 10")'),
             ('("versionName 1.7 \\\\/ versionCode 8", "versionName 1.8 \\\\/ versionCode 9")', '("versionName 1.7 \\\\/ versionCode 8", "versionName 1.81 \\\\/ versionCode 10")'),
             ('HERE="$(cd "$(dirname "$0")/.." && pwd)"', 'HERE="$(cd "$(dirname "$0")/.." && pwd)"')]:
    s = s.replace(a, b)
open(sys.argv[2], "w").write(s)
PY
chmod +x "$V18/test/.run_tests_181.sh"
set +e; PYTHON="$PY" "$V18/test/.run_tests_181.sh" "$APK" > "$T/v18.log" 2>&1; rc=$?; set -e
rm -f "$V18/test/.run_tests_181.sh"
grep -E "^==|ALL .*PASS|passed|FAIL|OK" "$T/v18.log"; echo "PASS count in 1.8 suite: $(grep -c '^PASS' "$T/v18.log")"
[ $rc -eq 0 ] && echo "ALL 1.81 TESTS PASSED" || { echo "1.8 suite FAILED (rc $rc)"; tail -30 "$T/v18.log"; exit 1; }
