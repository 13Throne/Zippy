#!/usr/bin/env bash
# Zippy 1.4 JVM tests: voice matching, sensitivity direction, listening state, + 1.3 signature/updater tests.
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID_JAR="${ANDROID_JAR:-/workspace/android-tools/android-35/android.jar}"
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
VOICES="${VOICES:-$T/voices}"
[ -d "$VOICES" ] || "$HERE/test/make_voices.sh" "$VOICES" >/dev/null 2>&1
javac --release 11 -nowarn -cp "$ANDROID_JAR" -d "$T/stub" $(find "$HERE/stubs" -name "*.java")
javac --release 11 -nowarn -cp "$ANDROID_JAR:$T/stub" -d "$T/cls" \
  "$HERE"/java/com/zippy/ear/{VoiceMath,VoiceMatch,Sensitivity,WakePhrase,ListenState}.java \
  "$HERE"/test/{Wav,OldVoice,VoiceMatchTest,SensitivityDirectionTest,ListenStateTest}.java
CP="$T/cls:$T/stub:$ANDROID_JAR"
echo "== VoiceMatchTest";           java -cp "$CP" VoiceMatchTest "$VOICES"
echo "== SensitivityDirectionTest"; java -cp "$CP" SensitivityDirectionTest
echo "== ListenStateTest";          java -cp "$CP" ListenStateTest
echo "== v1.3 SigTest";             "$HERE/../v1.3/test/run_sig_test.sh"
echo "== v1.3 UpdaterVersionTest";  "$HERE/../v1.3/test/run_updater_test.sh" 2>&1 | grep -v "^Note:"
