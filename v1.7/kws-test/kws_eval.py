#!/usr/bin/env python3
"""Run Sherpa-ONNX KWS over the test set exactly like Zippy 1.7 does on the phone.

Audio is fed in 2048-sample (128 ms) chunks, the stream is reset after every detection, and the
keywords come from kws_tuning.keywords_for(sensitivity), the same table as KwsTuning.java, passed
per stream (createStream(keywords)) on top of the app's own assets/kws/keywords.txt, just like the app.
Usage: kws_eval.py <model-dir> <testset-dir> <sens,sens,...> [--real <dir>] [--echo <dir>] [--whisper <json>]
"""
import os, sys, csv, glob, wave, json, re, argparse
import numpy as np
import sherpa_onnx
from concurrent.futures import ProcessPoolExecutor
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from kws_tuning import keywords_for, THREADS

ap = argparse.ArgumentParser()
ap.add_argument("model"); ap.add_argument("testset"); ap.add_argument("sens")
ap.add_argument("--keywords-file", default=os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "assets", "kws", "keywords.txt"))
ap.add_argument("--real"); ap.add_argument("--echo"); ap.add_argument("--whisper")
A = ap.parse_args()
ENC = "encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
DEC = "decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
JOI = "joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx"

def spotter():
    p = lambda n: os.path.join(A.model, n)
    return sherpa_onnx.KeywordSpotter(tokens=p("tokens.txt"), encoder=p(ENC), decoder=p(DEC), joiner=p(JOI),
                                      num_threads=THREADS, keywords_file=A.keywords_file, provider="cpu")

def read(path):
    with wave.open(path) as w:
        assert w.getframerate() == 16000 and w.getnchannels() == 1, path
        return np.frombuffer(w.readframes(w.getnframes()), dtype="<i2").astype(np.float32) / 32768

def detect(kws, kw, x):
    s = kws.create_stream(kw)
    hits = []
    for i in range(0, len(x), 2048):
        s.accept_waveform(16000, x[i:i + 2048])
        while kws.is_ready(s):
            kws.decode_stream(s)
            r = kws.get_result(s)
            if r == "NEVER":
                kws.reset_stream(s)
            elif r:
                hits.append((r, round(i / 16000, 2)))
                kws.reset_stream(s)
    return hits

def zippyish(t):
    return re.search(r"\b(hey,?\s*)?[zs]ipp?(y|ie|i|ee)\b", t.lower()) is not None

def evaluate(sens):
    kws, kw = spotter(), keywords_for(sens)
    rows = list(csv.DictReader(open(os.path.join(A.testset, "manifest.tsv")), delimiter="\t"))
    wh = json.load(open(A.whisper)) if A.whisper else {}
    st, misses, alarms = {}, [], []
    def add(c, hit):
        a = st.setdefault(c, [0, 0]); a[0] += 1; a[1] += 1 if hit else 0
    for r in rows:
        h = detect(kws, kw, read(os.path.join(A.testset, r["file"])))
        c = r["class"]
        if c == "mis": c = "mis_s" if "Sippy" in r["text"] else "mis_z"
        add(c, h)
        if r["file"] in wh and r["class"] == "pos" and zippyish(wh[r["file"]]): add("pos_whisper_ok", h)
        if c == "pos" and not h: misses.append(r["file"])
        if c in ("near", "neg") and h: alarms.append((r["text"], h[0][0]))
    res = {c: {"n": v[0], "hit": v[1], "rate": round(v[1] / v[0], 3)} for c, v in sorted(st.items())}
    for name, d in (("echo_liora", A.echo), ("real_speech", A.real)):
        if not d: continue
        n = fa = 0; sec = 0.0; ex = []
        for p in sorted(glob.glob(os.path.join(d, "*.wav"))):
            x = read(p); sec += len(x) / 16000; n += 1
            h = detect(kws, kw, x); fa += len(h)
            if h: ex.append(os.path.basename(p))
        res[name] = {"files": n, "hours": round(sec / 3600, 3), "false_alarms": fa, "examples": ex[:5]}
    return sens, kw, res, sorted(set(a[0] for a in alarms))

if __name__ == "__main__":
    sens = [int(s) for s in A.sens.split(",")]
    out = {}
    with ProcessPoolExecutor(min(8, len(sens))) as ex:
        for s, kw, res, al in ex.map(evaluate, sens):
            out[s] = res
            print(f"== sensitivity {s}  ({kw.count('/') + 1} keyword lines)")
            print(json.dumps(res))
            if al: print("   near/neg alarms:", al)
            sys.stdout.flush()
    json.dump(out, open("/tmp/kws_report.json", "w"), indent=1)
