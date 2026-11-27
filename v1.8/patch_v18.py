#!/usr/bin/env python3
"""Zippy 1.8 resources on top of the 1.7 tree: Silero VAD model, version 1.8 / 9.

Usage: patch_v18.py <apktool-dir> <sherpa-cache>
 - assets/vad/silero_vad.onnx (0.6 MB, deflated): Sherpa-ONNX Silero VAD for the end of the task.
 - assets/barge/env.txt (8 KB): loudness of each Liora clip per 32 ms, for barge-in.
"""
import os, re, shutil, sys
d, cache = sys.argv[1:3]
vd = os.path.join(d, "assets", "vad"); os.makedirs(vd, exist_ok=True)
shutil.copy(os.path.join(cache, "vad", "silero_vad.onnx"), os.path.join(vd, "silero_vad.onnx"))
# Liora clip loudness envelopes for barge-in (made by make_voice_env.py; kept out of assets/voice/, which holds only the 19 stored clips)
bd = os.path.join(d, "assets", "barge"); os.makedirs(bd, exist_ok=True)
shutil.copy(os.path.join(os.path.dirname(os.path.abspath(__file__)), "assets", "voice", "env.txt"), os.path.join(bd, "env.txt"))
y = os.path.join(d, "apktool.yml"); s = open(y).read()
assert "versionCode: 8" in s and "versionName: 1.7" in s, "expects the 1.7 tree"
open(y, "w").write(s.replace("versionCode: 8", "versionCode: 9").replace("versionName: 1.7", "versionName: 1.8"))
bc = None
for root, _, files in os.walk(d):
    for f in files:
        if f == "BuildConfig.smali" and "com/zippy/ear" in root.replace(os.sep, "/"):
            bc = os.path.join(root, f)
t = open(bc).read()
assert "0x8" in t and '"1.7"' in t
t = re.sub(r"(VERSION_CODE:I = )0x8", r"\g<1>0x9", t).replace('"1.7"', '"1.8"')
open(bc, "w").write(t)
print("v1.8 patches applied")
