#!/usr/bin/env bash
# Zippy 1.5 tests: new 1.5 logic + layout check, then every 1.4 and 1.3 test.
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID_JAR="${ANDROID_JAR:-/workspace/android-tools/android-35/android.jar}"
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
javac --release 11 -nowarn -cp "$ANDROID_JAR" -d "$T/stub" $(find "$HERE/stubs" -name "*.java")
javac --release 11 -nowarn -cp "$ANDROID_JAR:$T/stub" -d "$T/cls" \
  "$HERE"/java/com/zippy/ear/{ZippyPrefs,Tabs,WakeCue,CommandPlan,GrokBot}.java \
  "$HERE"/../v1.4/java/com/zippy/ear/{WakePhrase,Sensitivity}.java \
  "$HERE"/test/V15Test.java
echo "== V15Test (settings, wake cue, STT fallback, Grok Bot, tabs)"
java -cp "$T/cls:$T/stub:$ANDROID_JAR" V15Test
echo "== Layout check"
python3 "$HERE/test/check_layout.py"
echo "== v1.4 suite (+ v1.3)"
"$HERE/../v1.4/test/run_tests.sh"
