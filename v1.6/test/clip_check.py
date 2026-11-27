#!/usr/bin/env python3
"""Verify the 19 Liora clips: count, order/labels (Vosk transcript), duration, clean edges, no merged phrases, level.
Usage: clip_check.py <voice-dir> <vosk-model-dir>   (needs ffmpeg + `pip install vosk numpy`)"""
import sys, os, json, subprocess, re, wave, io
import numpy as np
from vosk import Model, KaldiRecognizer, SetLogLevel

EXPECT = [("g1", "Yeah?"), ("g2", "Hey, what's up?"), ("g3", "Go ahead."), ("g4", "I'm listening."),
          ("g5", "What do you need?"), ("g6", "Talk to me."), ("g7", "Ready."), ("g8", "Shoot."), ("g9", "Right here."),
          ("c1", "On it."), ("c2", "Got it."), ("c3", "Sent."), ("c4", "Handling it now."), ("c5", "Consider it done."),
          ("c6", "You got it."), ("c7", "All over it."), ("c8", "Working on it."), ("c9", "Heard you."),
          ("c10", "Got it, I'll take care of it.")]

def norm(t): return re.sub(r"[^a-z' ]", "", t.lower()).split()

def pcm(path, sr):
    raw = subprocess.run(["ffmpeg", "-loglevel", "error", "-i", path, "-ac", "1", "-ar", str(sr), "-f", "s16le", "-"],
                         check=True, capture_output=True).stdout
    return np.frombuffer(raw, dtype=np.int16)

def db(x): return 20 * np.log10(np.sqrt(np.mean((x / 32768.0) ** 2)) + 1e-9)

def main():
    d, model = sys.argv[1], sys.argv[2]
    SetLogLevel(-1)
    m = Model(model)
    files = sorted(f for f in os.listdir(d) if f.endswith(".m4a"))
    fails = []
    if len(files) != 19: fails.append(f"expected 19 clips, found {len(files)}")
    for name, text in EXPECT:
        p = os.path.join(d, name + ".m4a")
        if not os.path.exists(p): fails.append(f"{name} missing"); continue
        x16 = pcm(p, 16000)
        r = KaldiRecognizer(m, 16000)
        r.AcceptWaveform(x16.tobytes())
        heard = json.loads(r.FinalResult())["text"]
        x = pcm(p, 48000); sr = 48000
        dur = len(x) / sr
        head, tail = db(x[: int(0.015 * sr)]), db(x[-int(0.015 * sr):])
        peak = 20 * np.log10(np.max(np.abs(x)) / 32768.0 + 1e-9)
        hop = int(0.01 * sr)
        env = np.array([db(x[i:i + hop]) for i in range(0, len(x) - hop, hop)])
        voiced = np.where(env > -45)[0]
        lead = voiced[0] * 0.01 if len(voiced) else 0
        trail = (len(env) - 1 - voiced[-1]) * 0.01 if len(voiced) else 0
        # longest internal gap between voiced frames (two phrases merged would leave a long pause)
        gaps = np.diff(voiced) if len(voiced) > 1 else np.array([1])
        gap = (gaps.max() - 1) * 0.01
        ok_words = norm(heard) == norm(text)
        problems = []
        if not ok_words: problems.append(f"heard '{heard}'")
        if not (0.3 <= dur <= 2.0): problems.append(f"duration {dur:.2f}s")
        if head > -40 or tail > -40: problems.append(f"edge not silent (head {head:.0f} dB, tail {tail:.0f} dB)")
        if lead < 0.02 or trail < 0.04: problems.append(f"speech touches the edge (lead {lead:.2f}s, trail {trail:.2f}s)")
        if gap > 0.25: problems.append(f"internal gap {gap:.2f}s (merged?)")
        if peak > -0.5: problems.append(f"peak {peak:.1f} dBFS")
        status = "PASS" if not problems else "FAIL"
        print(f"{status} {name:4s} {dur:5.2f}s lead {lead:.2f} trail {trail:.2f} peak {peak:5.1f} dBFS  "
              f"expected '{text}' heard '{heard}'" + ("" if not problems else "  <- " + "; ".join(problems)))
        if problems: fails.append(name)
    print("ALL PASS" if not fails else "FAILED: " + ", ".join(fails))
    sys.exit(1 if fails else 0)

main()
