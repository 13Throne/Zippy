#!/usr/bin/env python3
"""Zippy 1.8 end-to-end capture test on desktop: long multi-clause requests with 0.3-1.5 s pauses,
0 ms to 1.8 s gaps after "Hey Zippy", talking during / over Liora's greeting (her real clips mixed in
as speaker echo), run through the SAME pieces as the app:
  Sherpa-ONNX KWS (app keywords, sensitivity 55) -> wake moment
  Sherpa-ONNX Silero VAD (app config, 512-sample frames)  -> Capture (the app's Java class, via CaptureSim)
  Vosk small-en-us 0.15 (the app's model, setWords, 2048-sample chunks, from 2 s before the wake word)
  TaskClean / EchoGuard (the app's Java classes, via CaptureSim)
Android speech can't run on a desktop; faster-whisper small.en stands in for it (same audio Zippy would
pipe to it: from 0.35 s before the wake moment to the end Capture chose).

Usage: capture_eval.py <work-dir> <out-dir>   (env: PIPER, VOSK_MODEL, SHERPA_CACHE, CAPSIM_CP, JOBS)
"""
import os, sys, json, re, subprocess, random, wave, math, zlib
import numpy as np
from concurrent.futures import ProcessPoolExecutor

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "..", "v1.7", "kws-test"))
from kws_tuning import keywords_for

W, OUT = sys.argv[1], sys.argv[2]
os.makedirs(W, exist_ok=True); os.makedirs(OUT, exist_ok=True)
SR = 16000
PIPER = os.environ.get("PIPER", "/workspace/emu-grpc/piper")
VOSK_MODEL = os.environ.get("VOSK_MODEL", "/workspace/emu-grpc/model/vosk-model-small-en-us-0.15")
CACHE = os.path.expanduser(os.environ.get("SHERPA_CACHE", "~/.cache/zippy-sherpa"))
CP = os.environ["CAPSIM_CP"]
JOBS = int(os.environ.get("JOBS", "6"))
VOICE_DIR = os.path.join(HERE, "..", "..", "v1.6", "assets", "voice")
ENVF = os.path.join(HERE, "..", "assets", "voice", "env.txt")   # Liora clip loudness (barge-in)
KEYWORDS_FILE = os.path.join(HERE, "..", "..", "v1.7", "assets", "kws", "keywords.txt")
GREET_TEXT = ["Yeah?", "Hey, what's up?", "Go ahead.", "I'm listening.", "What do you need?", "Talk to me.", "Ready.", "Shoot.", "Right here."]
VOICES = ["en_US-hfc_male-medium", "en_US-amy-medium", "en_GB-alan-medium"]
REQUESTS = {
    "brenda": ["have Jana, the vacation bot, draft an email regarding the Schlitterbahn research she's been working on",
               "and include the relevant links and various choices", "in a well formatted draft to Brenda"],
    "errands": ["remind me to call the dentist tomorrow at nine", "and add milk, eggs and coffee to the grocery list",
                "then text Brenda that I'll be running late"],
    "flights": ["look up flights from Austin to Denver for the second week of January",
                "only nonstop ones under four hundred dollars", "and send me the three best options"],
    "short": ["what's the weather this weekend"],
}
PAUSES = [0.3, 0.6, 1.0, 1.5]
GAPS = [0.0, 0.15, 0.3, 0.6, 1.0, 1.8]
END_SILENCE, NO_SPEECH = 2000, 4000          # app defaults (Wait 4 s): Capture.endSilenceMs(4000), noSpeechMs(4000)
PRE_ROLL, ANDROID_LEAD, SETTLE = 2.0, 0.35, 0.15
LAT = int(0.06 * SR)                          # speaker output latency
NOISE = 0.002

def trim(a, th=0.01, pad=0.02):
    idx = np.where(np.abs(a) > th)[0]
    if len(idx) == 0: return a
    s = max(0, idx[0] - int(pad * SR)); e = min(len(a), idx[-1] + int(pad * SR))
    return a[s:e]

_voices = {}
def tts(voice, text):
    from piper import PiperVoice, SynthesisConfig
    from scipy.signal import resample_poly
    from math import gcd
    if voice not in _voices: _voices[voice] = PiperVoice.load(os.path.join(PIPER, voice + ".onnx"))
    v = _voices[voice]
    a = np.concatenate([c.audio_float_array for c in v.synthesize(text, syn_config=SynthesisConfig(length_scale=1.0, noise_scale=0.6))])
    sr = v.config.sample_rate; g = gcd(sr, SR)
    return trim(resample_poly(a, SR // g, sr // g).astype(np.float32))

def synth_voice(voice):
    texts = ["Hey Zippy"] + sorted({c for r in REQUESTS.values() for c in r})
    out = {t: tts(voice, t) for t in texts}
    np.savez(os.path.join(W, f"seg_{voice}.npz"), **{str(i): out[t] for i, t in enumerate(texts)}, texts=np.array(texts))
    return voice

def load_segs(voice):
    z = np.load(os.path.join(W, f"seg_{voice}.npz"))
    texts = list(z["texts"]); return {t: z[str(i)] for i, t in enumerate(texts)}

def clip16(i):
    p = os.path.join(W, f"g{i}.wav")
    if not os.path.exists(p):
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", os.path.join(VOICE_DIR, f"g{i}.m4a"), "-ac", "1", "-ar", "16000", p], check=True)
    with wave.open(p) as w: return np.frombuffer(w.readframes(w.getnframes()), dtype="<i2").astype(np.float32) / 32768

_kws = _vadcfg = None
def kws():
    global _kws
    import sherpa_onnx
    if _kws is None:
        m = os.path.join(CACHE, "model")
        _kws = sherpa_onnx.KeywordSpotter(tokens=f"{m}/tokens.txt", encoder=f"{m}/encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
            decoder=f"{m}/decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx", joiner=f"{m}/joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
            num_threads=1, keywords_file=KEYWORDS_FILE, provider="cpu")
    return _kws

def vad_flags(x, warm=None):
    """Same as SileroGate: threshold .5, min_silence .25, min_speech .15, window 512, max 60; one flag per frame."""
    import sherpa_onnx
    c = sherpa_onnx.VadModelConfig()
    c.silero_vad.model = os.path.join(CACHE, "vad", "silero_vad.onnx")
    c.silero_vad.threshold = 0.5; c.silero_vad.min_silence_duration = 0.25; c.silero_vad.min_speech_duration = 0.15
    c.silero_vad.window_size = 512; c.silero_vad.max_speech_duration = 60; c.sample_rate = SR; c.num_threads = 1
    v = sherpa_onnx.VoiceActivityDetector(c, buffer_size_in_seconds=120)
    out = []
    if warm is not None:                      # ListenService.VAD_WARM: 0.5 s before the wake moment, not counted
        for i in range(0, len(warm) - 511, 512):
            v.accept_waveform(warm[i:i + 512])
            while not v.empty(): v.pop()
    for i in range(0, len(x) - 511, 512):
        f = x[i:i + 512]
        v.accept_waveform(f)
        out.append((int(v.is_speech_detected()), float(np.sqrt(np.mean(f.astype(np.float64) ** 2)))))
        while not v.empty(): v.pop()
    return out

def write_flags(path, fl):
    with open(path, "w") as f:
        for s, r in fl: f.write(f"{s} {r:.6f}\n")

def scenarios():
    rnd = random.Random(18)
    out = []
    for req, clauses in REQUESTS.items():
        for voice in VOICES:
            for gap in GAPS:
                for pause in (PAUSES if len(clauses) > 1 else [0.0]):
                    out.append(dict(id=f"{req}_{voice.split('-')[1]}_g{gap}_p{pause}", req=req, voice=voice, gap=gap, pause=pause,
                                    greet=rnd.randint(1, 9), echo=rnd.choice([0.15, 0.35, 0.7])))
    if os.environ.get("SMOKE"): out = [s for s in out if s["voice"] == VOICES[0] and s["gap"] in (0.0, 0.6) and s["pause"] in (0.0, 1.5)]
    return out

def stage_a(sc):
    segs = load_segs(sc["voice"])
    rng = np.random.default_rng(zlib.crc32(sc["id"].encode()))
    parts = [np.zeros(int(2.5 * SR), np.float32), segs["Hey Zippy"]]
    wake_end = (len(parts[0]) + len(parts[1])) / SR
    parts.append(np.zeros(int(sc["gap"] * SR), np.float32))
    marks = []
    t = sum(len(p) for p in parts)
    cl = REQUESTS[sc["req"]]
    for i, c in enumerate(cl):
        a = segs[c]; marks.append((t / SR, (t + len(a)) / SR)); parts.append(a); t += len(a)
        if i < len(cl) - 1: parts.append(np.zeros(int(sc["pause"] * SR), np.float32)); t += int(sc["pause"] * SR)
    parts.append(np.zeros(int(6.0 * SR), np.float32))
    x = np.concatenate(parts)
    x = (x + rng.normal(0, NOISE, len(x))).astype(np.float32)
    k = kws(); st = k.create_stream(keywords_for(55)); hit = None
    for i in range(0, len(x), 2048):
        st.accept_waveform(SR, x[i:i + 2048])
        while k.is_ready(st):
            k.decode_stream(st); r = k.get_result(st)
            if r == "NEVER": k.reset_stream(st)
            elif r and hit is None: hit = i + 2048; k.reset_stream(st)
        if hit is not None and i > hit: break
    sc = dict(sc, wake_end=wake_end, marks=marks, kws_hit=hit is not None)
    if hit is None: hit = int((wake_end + 0.2) * SR) // 2048 * 2048 + 2048   # missed wake: still test the capture
    sc["hit"] = hit
    np.save(os.path.join(W, sc["id"] + "_user.npy"), x)
    write_flags(os.path.join(W, sc["id"] + "_f1.txt"), vad_flags(x[hit:], x[hit - 8000:hit]))
    return sc

def mix(sc, start_frame, stop_frame):
    x = np.load(os.path.join(W, sc["id"] + "_user.npy")).copy()
    if start_frame is None or start_frame < 0: return x
    g = clip16(sc["greet"]); g = g / (np.max(np.abs(g)) + 1e-9) * 0.8 * sc["echo"]
    s = sc["hit"] + (start_frame - 1) * 512 + LAT
    if stop_frame is not None and stop_frame > 0:
        n = max(0, sc["hit"] + (stop_frame + 2) * 512 + LAT - s)
        g = g[:n].copy()
        if len(g) > 320: g[-320:] *= np.linspace(1, 0, 320)
    # a little room: echo plus two weak reflections
    e = np.zeros(len(g) + 1600, np.float32); e[:len(g)] += g; e[480:480 + len(g)] += 0.3 * g; e[1440:1440 + len(g)] += 0.15 * g
    x[s:s + len(e)] += e[:max(0, min(len(e), len(x) - s))]
    return x

def stage_b(args):
    sc, start = args
    x = mix(sc, start, None)
    write_flags(os.path.join(W, sc["id"] + "_f2.txt"), vad_flags(x[sc["hit"]:], x[sc["hit"] - 8000:sc["hit"]]))
    return sc["id"]

def stage_c(args):
    sc, start, stop = args
    x = mix(sc, start, stop)
    np.save(os.path.join(W, sc["id"] + "_mix.npy"), x)
    write_flags(os.path.join(W, sc["id"] + "_f3.txt"), vad_flags(x[sc["hit"]:], x[sc["hit"] - 8000:sc["hit"]]))
    return sc["id"]

_vm = None
def stage_d(sc):
    """Vosk over the app's recording window + the 1.7 one-breath baseline on the user-only audio."""
    global _vm
    import vosk
    vosk.SetLogLevel(-1)
    if _vm is None: _vm = vosk.Model(VOSK_MODEL)
    x = np.load(os.path.join(W, sc["id"] + "_mix.npy"))
    s0 = max(0, sc["hit"] - int(PRE_ROLL * SR)); e = sc["hit"] + int(sc["end_ms"] * SR / 1000)
    pcm = lambda a: (np.clip(a, -1, 1) * 32767).astype("<i2").tobytes()
    r = vosk.KaldiRecognizer(_vm, SR); r.SetWords(True)
    res = []
    for i in range(s0, e, 2048):
        if r.AcceptWaveform(pcm(x[i:min(e, i + 2048)])): res.append(r.Result())
    res.append(r.FinalResult())
    with open(os.path.join(W, sc["id"] + "_vosk.txt"), "w") as f: f.write("\n%%\n".join(res))
    # 1.7 one-breath path (Vosk from hit+0.15 with a 0.15 s tail; sends at the first final, gives up after 650 ms)
    u = np.load(os.path.join(W, sc["id"] + "_user.npy"))
    st = sc["hit"] + int(SETTLE * SR)
    r = vosk.KaldiRecognizer(_vm, SR); r.AcceptWaveform(pcm(u[st - int(SETTLE * SR):st]))
    junk = set("zippy zippi zippie sippy sippi hippie hippy pee pea p e ee be b he hey hay the uh um huh hm hmm oh".split())
    def tt(t):
        w = t.split()
        while w and w[0] in junk: w = w[1:]
        o = " ".join(w); return "" if (" " not in o and len(o) < 3) else o
    best = ""; v17 = None
    for i in range(st, len(u), 2048):
        fin = r.AcceptWaveform(pcm(u[i:i + 2048]))
        h = json.loads(r.Result())["text"] if fin else json.loads(r.PartialResult())["partial"]
        el = (i + 2048 - st) / SR
        if tt(h): best = tt(h)
        if fin and best: v17 = best; break
        if not best and el > 0.65: v17 = None; break
    return sc["id"], v17

NUMW = {"9": ["nine"], "400": ["four", "hundred"], "3": ["three"], "2nd": ["second"], "nonstop": ["non", "stop"]}
def norm(t):
    t = t.lower().replace("'", "").replace("-", " ")
    out = []
    for w in re.sub(r"[^a-z0-9 ]", " ", t).split(): out += NUMW.get(w, [w])
    return [w for w in out if w != "dollars"]

NUM = {"9": "nine", "400": "four hundred", "$400": "four hundred", "3": "three", "2nd": "second"}
def content(ws): return [w for w in ws if len(w) >= 4]
def lev(a, b):
    p = list(range(len(b) + 1))
    for i in range(1, len(a) + 1):
        c = [i] + [0] * len(b)
        for j in range(1, len(b) + 1): c[j] = min(c[j - 1] + 1, p[j] + 1, p[j - 1] + (a[i - 1] != b[j - 1]))
        p = c
    return p[-1]
def has(hyp, w): return any(h == w or (len(w) >= 4 and lev(h, w) <= 1) or (len(w) >= 8 and lev(h, w) <= 2) for h in hyp)

def score(sc, text):
    ref = norm(" ".join(REQUESTS[sc["req"]]))
    hyp = norm(text)
    cw = content(ref)
    rec = sum(has(hyp, w) for w in cw) / max(1, len(cw))
    first = content(norm(REQUESTS[sc["req"]][0]))[:2]; last = content(norm(REQUESTS[sc["req"]][-1]))[-2:]
    g = norm(GREET_TEXT[sc["greet"] - 1])
    echo = sc.get("greeted") and len(hyp) >= len(g) and hyp[:len(g)] == g
    wer = lev(ref, hyp) / max(1, len(ref))
    # clipping (not recognition) checks: one of your first 3 words among the first 5 sent, one of your last 3 among the last 5
    start = any(has(hyp[:5], w) for w in norm(REQUESTS[sc["req"]][0])[:3])
    end = any(has(hyp[-5:], w) for w in norm(REQUESTS[sc["req"]][-1])[-3:])
    return dict(recall=rec, first=all(has(hyp, w) for w in first), last=all(has(hyp, w) for w in last), echo=bool(echo), wer=wer,
                start=start, end=end)

def java(mode, rows, name):
    jp = os.path.join(W, name + "_jobs.tsv"); op = os.path.join(W, name + "_out.tsv")
    with open(jp, "w") as f: f.write("\n".join("\t".join(map(str, r)) for r in rows) + "\n")
    subprocess.run(["java", "-cp", CP, "CaptureSim", mode, jp, op], check=True)
    return {l.split("\t")[0]: l.rstrip("\n").split("\t") for l in open(op) if l.strip()}

def main():
    with ProcessPoolExecutor(min(JOBS, len(VOICES))) as ex: list(ex.map(synth_voice, VOICES))
    S = scenarios()
    with ProcessPoolExecutor(JOBS) as ex: S = list(ex.map(stage_a, S))
    by = {s["id"]: s for s in S}
    glen = {i: int(math.ceil((len(clip16(i)) + LAT) / 512)) for i in range(1, 10)}
    # pass 1: greeting decision on your voice alone
    p1 = java("sim", [[s["id"], os.path.join(W, s["id"] + "_f1.txt"), 1, 1, "3.0", END_SILENCE, NO_SPEECH, -2, glen[s["greet"]], ENVF, f"g{s['greet']}"] for s in S], "p1")
    starts = {i: (int(r[2]) + 3 if r[1] == "GREET" else -1) for i, r in p1.items()}
    with ProcessPoolExecutor(JOBS) as ex: list(ex.map(stage_b, [(by[i], starts[i]) for i in by if starts[i] > 0]))
    # pass 2: with Liora's echo for the whole clip -> did you barge in?
    rows = [[i, os.path.join(W, i + ("_f2.txt" if starts[i] > 0 else "_f1.txt")), 1, 1, "3.0", END_SILENCE, NO_SPEECH, starts[i], glen[by[i]["greet"]], ENVF, f"g{by[i]['greet']}"] for i in by]
    p2 = java("sim", rows, "p2")
    stops = {i: int(r[5]) for i, r in p2.items()}
    with ProcessPoolExecutor(JOBS) as ex: list(ex.map(stage_c, [(by[i], starts[i], stops[i]) for i in by]))
    # pass 3: final audio (echo cut where Liora stopped)
    rows = [[i, os.path.join(W, i + "_f3.txt"), 1, 1, "3.0", END_SILENCE, NO_SPEECH, starts[i],
             (stops[i] + 2 - starts[i]) if stops[i] > 0 else glen[by[i]["greet"]], ENVF, f"g{by[i]['greet']}"] for i in by]
    p3 = java("sim", rows, "p3")
    for i, r in p3.items():
        s = by[i]
        s.update(decision=r[1], greet_start=int(r[3]), greet_end=int(r[4]), stop=int(r[5]), end=r[7], end_ms=int(r[8]),
                 speech_start_ms=int(r[9]), speech_end_ms=int(r[10]), greeted=int(r[3]) > 0)
        if s["end"] == "NO_SPEECH" or s["end_ms"] <= 0: s["end_ms"] = max(s["end_ms"], 1000)
    with ProcessPoolExecutor(JOBS) as ex: v17 = dict(ex.map(stage_d, list(by.values())))
    # Android speech stand-in: faster-whisper on what Zippy would pipe
    from faster_whisper import WhisperModel
    wm = WhisperModel("small.en", device="cpu", compute_type="int8", cpu_threads=8)
    for s in by.values():
        x = np.load(os.path.join(W, s["id"] + "_mix.npy"))
        a = x[s["hit"] - int(ANDROID_LEAD * SR): s["hit"] + int(s["end_ms"] * SR / 1000)]
        segs, _ = wm.transcribe(a, language="en", beam_size=5, condition_on_previous_text=False, vad_filter=False)
        with open(os.path.join(W, s["id"] + "_android.txt"), "w") as f: f.write(" ".join(x.text.strip() for x in segs))
    rows = []
    for s in by.values():
        s0 = max(0, s["hit"] - int(PRE_ROLL * SR))
        gs = (s["hit"] + (s["greet_start"] - 1) * 512 + LAT - s0) / SR if s["greeted"] else -1
        ge = (s["hit"] + s["greet_end"] * 512 - s0) / SR + 0.16 if s["greeted"] else -1
        rows.append([s["id"], os.path.join(W, s["id"] + "_vosk.txt"), f"{(s['hit'] - s0) / SR:.4f}",
                     GREET_TEXT[s["greet"] - 1] if s["greeted"] else "-", f"{gs:.4f}", f"{ge:.4f}", os.path.join(W, s["id"] + "_android.txt"), 55])
    cl = java("clean", rows, "clean")
    report = []
    for s in by.values():
        r = cl[s["id"]]
        s.update(offline=r[1], android=r[2], picked_source=r[3], picked=r[4], v17_one_breath=v17[s["id"]])
        last_end = s["marks"][-1][1]
        s["end_after_last_word"] = s["end"] == "SILENCE" and (s["hit"] / SR + s["end_ms"] / 1000) >= last_end
        s["score_offline"] = score(s, s["offline"]); s["score_android"] = score(s, s["android"]); s["score_sent"] = score(s, s["picked"])
        s["v17_score"] = score(s, v17[s["id"]] or "")
        report.append({k: v for k, v in s.items() if k not in ()})
    json.dump(report, open(os.path.join(OUT, "capture_report.json"), "w"), indent=1)
    summarize(report)

def pct(a): return f"{100 * sum(a) / max(1, len(a)):.1f}%"
def summarize(R):
    L = []
    P = lambda *a: L.append(" ".join(str(x) for x in a))
    P(f"scenarios: {len(R)} ({len(VOICES)} voices x 4 requests x gaps {GAPS} x pauses {PAUSES})")
    P(f"wake word spotted (Sherpa-ONNX, sens 55): {pct([r['kws_hit'] for r in R])}")
    P(f"ended by silence after the LAST word (no early cut): {pct([r['end_after_last_word'] for r in R])}")
    for key, name in (("score_sent", "sent (Android stand-in, offline backup)"), ("score_offline", "offline (Vosk) alone"), ("score_android", "Android stand-in (Whisper) alone")):
        P(f"{name}: content-word recall {100 * np.mean([r[key]['recall'] for r in R]):.1f}%, first words kept {pct([r[key]['first'] for r in R])}, "
          f"last words kept {pct([r[key]['last'] for r in R])}, greeting echo at start {pct([r[key]['echo'] for r in R])}, WER {100 * np.mean([r[key]['wer'] for r in R]):.1f}%, "
          f"start not clipped {pct([r[key]['start'] for r in R])}, end not clipped {pct([r[key]['end'] for r in R])}")
    from collections import Counter
    P("sent text came from: " + ", ".join(f"{k} {v}" for k, v in Counter(r["picked_source"] for r in R).most_common()))
    P("")
    P("by gap after Hey Zippy (sent text): gap | greeted | Liora stopped by barge-in | start not clipped | end not clipped | recall")
    for g in GAPS:
        rs = [r for r in R if r["gap"] == g]
        P(f"  {g:>4} s | {pct([r['greeted'] for r in rs])} | {pct([r['stop'] > 0 for r in rs])} | {pct([r['score_sent']['start'] for r in rs])} | {pct([r['score_sent']['end'] for r in rs])} | {100 * np.mean([r['score_sent']['recall'] for r in rs]):.1f}%")
    P("by pause between clauses (sent text): pause | ended after last word | end not clipped | recall")
    for p in PAUSES:
        rs = [r for r in R if r["pause"] == p]
        P(f"  {p:>4} s | {pct([r['end_after_last_word'] for r in rs])} | {pct([r['score_sent']['end'] for r in rs])} | {100 * np.mean([r['score_sent']['recall'] for r in rs]):.1f}%")
    rs = [r for r in R if r["gap"] <= 0.3 and r["req"] != "short"]
    P(f"1.7 one-breath path on the same multi-clause audio (gap <= 0.3 s): sent anything {pct([r['v17_one_breath'] is not None for r in rs])}, "
      f"end not clipped {pct([r['v17_score']['end'] for r in rs])}, recall {100 * np.mean([r['v17_score']['recall'] for r in rs]):.1f}%")
    b = next((r for r in R if r["req"] == "brenda" and r["gap"] == 0.6 and r["pause"] == 1.0), None) or next(r for r in R if r["req"] == "brenda")
    P("")
    P(f"example ({b['id']}): sent [{b['picked_source']}] \"{b['picked']}\"")
    P(f"  offline: \"{b['offline']}\"")
    open(os.path.join(OUT, "capture_summary.txt"), "w").write("\n".join(L) + "\n")
    print("\n".join(L))

if __name__ == "__main__":
    main()
