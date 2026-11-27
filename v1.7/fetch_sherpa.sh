#!/usr/bin/env bash
# Download (once) and verify the official Sherpa-ONNX Android AAR and the KWS model used by Zippy 1.7.
# Usage: fetch_sherpa.sh <cache-dir>   -> <cache-dir>/{aar/, model/}
set -euo pipefail
C="$1"; mkdir -p "$C"
AAR_URL=https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.3/sherpa-onnx-2.0.3.aar
AAR_SHA=4c766e2e74c9d33d595338c21fe3b305c73ba45d47e96a235675c33142a2ec50
MODEL=sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01
MODEL_URL=https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/$MODEL.tar.bz2
MODEL_SHA=f170013b4716e41b62b9bfd809687c207cef798ef9bc6534d524e17af9b6561a
get() { # url sha out
  if [ ! -f "$3" ] || ! echo "$2  $3" | sha256sum -c --quiet - 2>/dev/null; then curl -fsSL -o "$3" "$1"; fi
  echo "$2  $3" | sha256sum -c --quiet -
}
get "$AAR_URL" "$AAR_SHA" "$C/sherpa-onnx-2.0.3.aar"
get "$MODEL_URL" "$MODEL_SHA" "$C/$MODEL.tar.bz2"
[ -d "$C/aar/jni" ] || (mkdir -p "$C/aar" && cd "$C/aar" && unzip -q -o ../sherpa-onnx-2.0.3.aar)
[ -d "$C/model" ] || (cd "$C" && python3 -c "import tarfile,sys; tarfile.open(sys.argv[1]).extractall('.')" "$MODEL.tar.bz2" && mv "$MODEL" model)
echo "sherpa-onnx 2.0.3 AAR + $MODEL ready in $C"
