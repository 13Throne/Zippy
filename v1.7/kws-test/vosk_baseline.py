#!/usr/bin/env python3
"""v1.6 baseline: Vosk small-en-us streaming + the 1.4-1.6 WakePhrase rules (Python port), same chunks.
Usage: vosk_baseline.py <vosk-model> <testset-dir> <sens,sens,...> [extra-negative-wav-dir]"""
import os, sys, csv, glob, json, re, wave
import numpy as np
from vosk import Model, KaldiRecognizer, SetLogLevel
SetLogLevel(-1)
EXACT = {"hey zippy", "hey zippi", "hey zippee", "hey zippie", "hey sippy"}

def lev(a, b):
    prev = list(range(len(b) + 1))
    for i in range(1, len(a) + 1):
        cur = [i] + [0] * len(b)
        for j in range(1, len(b) + 1):
            cur[j] = min(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + (a[i - 1] != b[j - 1]))
        prev = cur
    return prev[-1]

def is_wake(p, s):
    if p in EXACT: return True
    d = lev(p, "hey zippy")
    if s >= 30 and (d <= 1 or p in ("a zippy", "hey zipy")): return True
    if s >= 60 and (d <= 2 or p == "hey zip"): return True
    return s >= 85 and d <= 3 and p.endswith("ippy")

def is_bare(w, s):
    return s >= 85 and (w in ("zippy", "zippi") or lev(w, "zippy") <= 1)

def parse(text, s):
    w = re.sub(r"\s+", " ", re.sub(r"[^a-z\s]", " ", text.lower())).split()
    if not w: return None
    if len(w) >= 2 and is_wake(w[0] + " " + w[1], s): return " ".join(w[2:])
    if is_bare(w[0], s): return " ".join(w[1:])
    return None

def read(p):
    with wave.open(p) as f:
        return f.readframes(f.getnframes())

def wakes(model, pcm, s):
    rec = KaldiRecognizer(model, 16000)
    n = 0
    for i in range(0, len(pcm), 4096):
        fin = rec.AcceptWaveform(pcm[i:i + 4096])
        j = json.loads(rec.Result() if fin else rec.PartialResult())
        t = j.get("text") or j.get("partial") or ""
        if t and parse(t, s) is not None:
            n += 1
            rec.Reset()
    return n

if __name__ == "__main__":
    model = Model(sys.argv[1]); T = sys.argv[2]
    rows = list(csv.DictReader(open(os.path.join(T, "manifest.tsv")), delimiter="\t"))
    extra = sorted(glob.glob(os.path.join(sys.argv[4], "*.wav"))) if len(sys.argv) > 4 else []
    for s in map(int, sys.argv[3].split(",")):
        st = {}
        for r in rows:
            c = r["class"]; c = ("mis-s" if "Sippy" in r["text"] else "mis-z") if c == "mis" else c
            h = wakes(model, read(os.path.join(T, r["file"])), s)
            a = st.setdefault(c, [0, 0]); a[0] += 1; a[1] += 1 if h else 0
        res = {c: round(v[1] / v[0], 3) for c, v in st.items()}
        if extra:
            fa = 0; sec = 0
            for p in extra:
                pcm = read(p); sec += len(pcm) / 32000; fa += wakes(model, pcm, s)
            res["libri_fa"] = fa; res["libri_h"] = round(sec / 3600, 2)
        print("vosk sens", s, json.dumps(res), flush=True)
