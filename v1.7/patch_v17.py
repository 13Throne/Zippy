#!/usr/bin/env python3
"""Zippy 1.7 resources on top of the 1.6 tree: Sherpa-ONNX native libs + KWS model, version 1.7 / 8.

Usage: patch_v17.py <apktool-dir> <sherpa-cache> <keywords.txt>
 - lib/arm64-v8a and lib/armeabi-v7a get libsherpa-onnx-jni.so + libonnxruntime.so from the official
   AAR (the only ABIs the APK already ships for Vosk; 16 KB-aligned ELF LOAD segments).
 - assets/kws/: int8 encoder/decoder/joiner, tokens.txt, keywords.txt (default sensitivity), deflated.
"""
import os, re, shutil, sys
d, cache, kw = sys.argv[1:4]
# TEST_ABIS=x86_64 adds emulator-native Sherpa libs for a test build only (never for a release:
# the APK would then install as x86_64 and miss Vosk's ARM-only libs).
for abi in ("arm64-v8a", "armeabi-v7a") + tuple(a for a in os.environ.get("TEST_ABIS", "").split(",") if a):
    dst = os.path.join(d, "lib", abi)
    if abi in ("arm64-v8a", "armeabi-v7a"): assert os.path.isdir(dst), dst
    os.makedirs(dst, exist_ok=True)
    for so in ("libsherpa-onnx-jni.so", "libonnxruntime.so"):
        shutil.copy(os.path.join(cache, "aar", "jni", abi, so), dst)
m = os.path.join(cache, "model")
kd = os.path.join(d, "assets", "kws"); os.makedirs(kd, exist_ok=True)
for src, name in (("encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx", "encoder.int8.onnx"),
                  ("decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx", "decoder.int8.onnx"),
                  ("joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx", "joiner.int8.onnx"),
                  ("tokens.txt", "tokens.txt")):
    shutil.copy(os.path.join(m, src), os.path.join(kd, name))
shutil.copy(kw, os.path.join(kd, "keywords.txt"))

y = os.path.join(d, "apktool.yml"); s = open(y).read()
assert "versionCode: 7" in s and "versionName: 1.6" in s, "expects the 1.6 tree"
s = s.replace("versionCode: 7", "versionCode: 8").replace("versionName: 1.6", "versionName: 1.7")
# The .onnx models are left compressed (saves ~1.3 MB); Sherpa-ONNX reads assets with AASSET_MODE_BUFFER.
open(y, "w").write(s)
bc = None
for root, _, files in os.walk(d):
    for f in files:
        if f == "BuildConfig.smali" and "com/zippy/ear" in root.replace(os.sep, "/"):
            bc = os.path.join(root, f)
t = open(bc).read()
assert "0x7" in t and '"1.6"' in t
t = re.sub(r"(VERSION_CODE:I = )0x7", r"\g<1>0x8", t).replace('"1.6"', '"1.7"')
open(bc, "w").write(t)
print("v1.7 patches applied")
