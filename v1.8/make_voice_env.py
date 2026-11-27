#!/usr/bin/env python3
"""Loudness envelope of each Liora clip for barge-in: RMS per 32 ms frame (512 samples at 16 kHz) of
v1.6/assets/voice/*.m4a, decoded with ffmpeg. Writes assets/voice/env.txt ("g1 0.0012 0.0310 ...").
Capture compares the mic with this (times the measured echo gain) to tell your voice from her echo.
Usage: make_voice_env.py [out]   (needs ffmpeg)"""
import os, subprocess, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "v1.6", "assets", "voice")
out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "assets", "voice", "env.txt")
lines = []
names = sorted((f[:-4] for f in os.listdir(SRC) if f.endswith(".m4a")), key=lambda n: (n[0], int(n[1:])))
for n in names:
    pcm = subprocess.run(["ffmpeg", "-loglevel", "error", "-i", os.path.join(SRC, n + ".m4a"), "-ac", "1", "-ar", "16000", "-f", "s16le", "-"],
                         check=True, capture_output=True).stdout
    x = np.frombuffer(pcm, dtype="<i2").astype(np.float64) / 32768
    fr = [float(np.sqrt(np.mean(x[i:i + 512] ** 2))) for i in range(0, len(x), 512)]
    lines.append(n + " " + " ".join(f"{v:.4f}" for v in fr))
open(out, "w").write("\n".join(lines) + "\n")
print(f"{out}: {len(lines)} clips")
