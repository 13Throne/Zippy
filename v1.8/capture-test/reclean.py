#!/usr/bin/env python3
"""Re-run only the text step (TaskClean/EchoGuard/choose) of capture_eval.py on its saved audio results.
Usage: reclean.py <work-dir> <out-dir>   (reads and rewrites out-dir/capture_report.json + summary)"""
import os, sys, json
import capture_eval as ce
W, OUT = sys.argv[1], sys.argv[2]
R = json.load(open(os.path.join(OUT, "capture_report.json")))
rows = []
for s in R:
    s0 = max(0, s["hit"] - int(ce.PRE_ROLL * ce.SR))
    gs = (s["hit"] + (s["greet_start"] - 1) * 512 + ce.LAT - s0) / ce.SR if s["greeted"] else -1
    ge = (s["hit"] + s["greet_end"] * 512 - s0) / ce.SR + 0.16 if s["greeted"] else -1
    rows.append([s["id"], os.path.join(W, s["id"] + "_vosk.txt"), f"{(s['hit'] - s0) / ce.SR:.4f}",
                 ce.GREET_TEXT[s["greet"] - 1] if s["greeted"] else "-", f"{gs:.4f}", f"{ge:.4f}", os.path.join(W, s["id"] + "_android.txt"), 55])
cl = ce.java("clean", rows, "reclean")
for s in R:
    r = cl[s["id"]]
    s.update(offline=r[1], android=r[2], picked_source=r[3], picked=r[4])
    s["score_offline"] = ce.score(s, s["offline"]); s["score_android"] = ce.score(s, s["android"]); s["score_sent"] = ce.score(s, s["picked"])
json.dump(R, open(os.path.join(OUT, "capture_report.json"), "w"), indent=1)
ce.summarize(R)
