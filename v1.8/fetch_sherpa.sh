#!/usr/bin/env bash
# Zippy 1.8: everything 1.7 needs (sherpa-onnx 2.0.3 AAR + KWS model) plus the Silero VAD model.
# Usage: fetch_sherpa.sh <cache-dir>   -> <cache-dir>/{aar/, model/, vad/silero_vad.onnx}
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
C="$1"
"$HERE/../v1.7/fetch_sherpa.sh" "$C" >/dev/null
VAD_URL=https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx
VAD_SHA=9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6
mkdir -p "$C/vad"
if [ ! -f "$C/vad/silero_vad.onnx" ] || ! echo "$VAD_SHA  $C/vad/silero_vad.onnx" | sha256sum -c --quiet - 2>/dev/null; then
  curl -fsSL -o "$C/vad/silero_vad.onnx" "$VAD_URL"
fi
echo "$VAD_SHA  $C/vad/silero_vad.onnx" | sha256sum -c --quiet -
echo "sherpa-onnx 2.0.3 AAR + KWS model + Silero VAD ready in $C"
