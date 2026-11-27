#!/usr/bin/env python3
"""Barge-in check for Zippy 1.8 (run after capture_eval.py): does Liora stop when you talk over her,
and does her own echo never stop her? Uses capture_eval's work dir (VAD flags with the echo mixed in)
plus echo-only recordings (every greeting clip at 3 loudness levels, nobody talking).
Usage: barge_eval.py <work-dir> <out-dir>   (env CAPSIM_CP as for capture_eval.py)"""
import os, sys, json, math
import numpy as np
sys.argv = [sys.argv[0], sys.argv[1], sys.argv[2]]
import capture_eval as ce

def main():
    W, OUT = sys.argv[1], sys.argv[2]
    R = json.load(open(os.path.join(OUT, "capture_report.json")))
    glen = {i: int(math.ceil((len(ce.clip16(i)) + ce.LAT) / 512)) for i in range(1, 10)}
    talk = [r for r in R if r["greet_start"] > 0]
    by_id = {r["id"]: r for r in R}
    rows = [[r["id"], os.path.join(W, r["id"] + "_f2.txt"), 1, 1, "3.0", ce.END_SILENCE, ce.NO_SPEECH, r["greet_start"], glen[r["greet"]], ce.ENVF, f"g{r['greet']}"] for r in talk]
    # echo only: 1 s of room noise, then the greeting at frame 16, nobody talking
    rng = np.random.default_rng(7)
    for i in range(1, 10):
        for gain in (0.15, 0.35, 0.7, 1.0):
            x = rng.normal(0, ce.NOISE, int(8 * ce.SR)).astype(np.float32)
            g = ce.clip16(i); g = g / (np.max(np.abs(g)) + 1e-9) * 0.8 * gain
            s = 2 * ce.SR + 15 * 512 + ce.LAT
            e = np.zeros(len(g) + 1600, np.float32); e[:len(g)] += g; e[480:480 + len(g)] += 0.3 * g; e[1440:1440 + len(g)] += 0.15 * g
            x[s:s + len(e)] += e
            p = os.path.join(W, f"echoonly_g{i}_{gain}_f.txt")
            ce.write_flags(p, ce.vad_flags(x[2 * ce.SR:], x[2 * ce.SR - 8000:2 * ce.SR]))
            rows.append([f"echoonly_g{i}_{gain}", p, 1, 1, "3.0", ce.END_SILENCE, ce.NO_SPEECH, 16, glen[i], ce.ENVF, f"g{i}"])
    echo_rows = [r for r in rows if r[0].startswith("echoonly")]
    talk_rows = [r for r in rows if not r[0].startswith("echoonly")]
    L = []
    # 1) first session on a phone: nothing measured yet (prior 2.0, ratio 3.0 as in ListenService without AEC)
    first = ce.java("sim", rows, "barge1")
    # 2) the echo gain each echo-only session measured -> what the phone would have saved per loudness
    meas = {}
    for k, o in first.items():
        if k.startswith("echoonly") and float(o[11]) > 0: meas.setdefault(float(k.split("_")[2]), []).append(float(o[11]))
    saved = {g: float(np.median(v)) for g, v in meas.items()}
    L.append("echo gain measured on echo-only sessions (saved per phone/route): " + ", ".join(f"level {g}: {v:.2f}" for g, v in sorted(saved.items())))
    # 3) later sessions: the saved gain is known (talk-over uses its loudness level; echo-only uses the
    #    median of the OTHER clips at that level, so no clip grades itself)
    rows2 = []
    fix = lambda r: r[:4] + [os.environ.get("RATIO", "1.6")] + r[5:]     # Capture.KNOWN_GAIN_RATIO
    for r in talk_rows: rows2.append(fix(r) + [f"{saved[by_id[r[0]]['echo']]:.4f}", 1])
    for r in echo_rows:
        lvl = float(r[0].split("_")[2]); clip = r[0].split("_")[1]
        others = [float(first[k][11]) for k in first if k.startswith("echoonly") and float(k.split("_")[2]) == lvl and k.split("_")[1] != clip and float(first[k][11]) > 0]
        rows2.append(fix(r) + [f"{float(np.median(others)):.4f}", 1])
    later = ce.java("sim", rows2, "barge2")
    for name, out in (("first session (echo gain not measured yet)", first), ("later sessions (echo gain saved)", later)):
        L.append(f"-- {name}")
        L += report(talk, out)
    open(os.path.join(OUT, "barge_summary.txt"), "w").write("\n".join(L) + "\n")
    print("\n".join(L))

def report(talk, out):
    L = []
    glen = {i: int(math.ceil((len(ce.clip16(i)) + ce.LAT) / 512)) for i in range(1, 10)}
    over = []
    for r in talk:
        o = out[r["id"]]
        onset = (r["marks"][0][0] - r["hit"] / ce.SR) / 0.032            # your first word, in frames
        gend = r["greet_start"] + glen[r["greet"]]
        if onset < gend - 6:                                             # you talk over at least ~0.2 s of her
            over.append((r, int(o[5]), onset))
    stopped = [(r, s, on) for r, s, on in over if s > 0]
    d = [(s - max(on, r["greet_start"])) * 32 for r, s, on in stopped]
    L.append(f"you talk over Liora (>= 0.2 s overlap): {len(over)} cases; she stops in {len(stopped)} ({100 * len(stopped) / max(1, len(over)):.0f}%)"
             + (f", median {int(np.median(d))} ms after you start" if d else ""))
    for gain in (0.15, 0.35, 0.7):
        g = [x for x in over if x[0]["echo"] == gain]
        L.append(f"  echo level {gain}: {sum(1 for x in g if x[1] > 0)} of {len(g)}")
    eo = [k for k in out if k.startswith("echoonly")]
    false = [k for k in eo if int(out[k][5]) > 0]
    L.append(f"echo only (9 clips x 4 levels, nobody talking): {len(false)} false stops of {len(eo)}" + (f" ({', '.join(false)})" if false else ""))
    nos = sum(1 for k in eo if out[k][7] == "NO_SPEECH")
    L.append(f"echo only: ended as 'no task' (her echo never taken for you): {nos} of {len(eo)}")
    return L

if __name__ == "__main__":
    main()
