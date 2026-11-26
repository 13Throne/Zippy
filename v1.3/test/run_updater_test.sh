#!/usr/bin/env bash
# JVM check of Updater's tag parsing / semver comparison (android.jar only for class resolution).
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID_JAR="${ANDROID_JAR:-/workspace/android-tools/android-35/android.jar}"
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
javac --release 11 -nowarn -cp "$ANDROID_JAR" -d "$T/stub" "$HERE"/stubs/androidx/core/content/FileProvider.java
javac --release 11 -cp "$ANDROID_JAR:$T/stub" -d "$T" "$HERE/java/com/zippy/ear/Updater.java" "$HERE/test/UpdaterVersionTest.java"
java -cp "$T:$T/stub:$ANDROID_JAR" UpdaterVersionTest
