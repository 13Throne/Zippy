"""Python mirror of the Zippy 1.81 custom wake phrase pipeline (WakeTuner.java / BpeTokenizer.java).

1. tokenize(phrase): the KWS model's own unigram SentencePiece tokens (bpe.model).
2. learn(samples): what the model actually hears in each of the user's recordings (greedy transducer
   decode with the same KWS model), kept as extra pronunciations when close to the phrase.
3. tune(samples, others): strictest trigger threshold (with boost tied to it) at which ALL samples
   wake; variants that fire on the user's "ordinary speech" takes are dropped, then the threshold is
   raised until no ordinary take wakes it (as long as every sample still does).
4. runtime(t_tuned, sens): the Sensitivity slider gives a margin around the tuned threshold.
"""
import os, re
import numpy as np
import sherpa_onnx, sentencepiece as spm

M = os.environ.get("KWS_MODEL", "/home/box/.cache/zippy-sherpa/model")
ENC = "encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
SENTINEL_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "v1.7", "assets", "kws", "keywords.txt")
THRESHOLDS = [0.60, 0.55, 0.50, 0.45, 0.40, 0.35, 0.30, 0.25, 0.20, 0.15, 0.10, 0.05]
MAX_VARIANTS = 4
MAX_DIST = 0.40          # letters edit distance / phrase letters
MARGIN = 0.05            # default runtime threshold = tuned - MARGIN
SLOPE = 0.003            # per Sensitivity point away from 55
T_MIN, T_MAX = 0.03, 0.70
T_CAP = 0.30  # never start stricter than this
MIN_TAKES = 5

def boost_for(t):        # same line as KwsTuning: threshold 0.45 <-> boost 1.0, 0.05 <-> 2.0
    return round(min(2.0, max(1.0, 1.0 + (0.45 - t) * 2.5)), 2)

def runtime_threshold(t_tuned, sens):
    s = max(0, min(100, int(sens)))
    return round(min(T_MAX, max(T_MIN, t_tuned - MARGIN - (s - 55) * SLOPE)), 3)

_sp = None
def tokenize(phrase):
    global _sp
    _sp = _sp or spm.SentencePieceProcessor(model_file=os.path.join(M, "bpe.model"))
    return _sp.encode(clean(phrase), out_type=str)

def clean(p):
    p = re.sub(r"[^A-Za-z' ]+", " ", p).upper()
    return re.sub(r"\s+", " ", p).strip()

def letters(toks):
    return "".join(toks).replace("▁", "").replace("'", "")

def edit(a, b):
    d = list(range(len(b) + 1))
    for i in range(1, len(a) + 1):
        p, d[0] = d[0], i
        for j in range(1, len(b) + 1):
            p, d[j] = d[j], min(d[j] + 1, d[j - 1] + 1, p + (a[i - 1] != b[j - 1]))
    return d[len(b)]

_kws = _rec = None
def spotter():
    global _kws
    if _kws is None:
        p = lambda n: os.path.join(M, n)
        _kws = sherpa_onnx.KeywordSpotter(tokens=p("tokens.txt"), encoder=p(ENC), decoder=p(ENC.replace("encoder", "decoder")),
                                          joiner=p(ENC.replace("encoder", "joiner")), num_threads=1,
                                          keywords_file=SENTINEL_FILE, max_active_paths=4, num_trailing_blanks=1)
    return _kws

def recognizer():
    global _rec
    if _rec is None:
        p = lambda n: os.path.join(M, n)
        _rec = sherpa_onnx.OnlineRecognizer.from_transducer(tokens=p("tokens.txt"), encoder=p(ENC),
                    decoder=p(ENC.replace("encoder", "decoder")), joiner=p(ENC.replace("encoder", "joiner")),
                    num_threads=1, decoding_method="greedy_search")
    return _rec

def heard(x):
    r = recognizer(); s = r.create_stream()
    s.accept_waveform(16000, x); s.accept_waveform(16000, np.zeros(9600, np.float32)); s.input_finished()
    while r.is_ready(s): r.decode_stream(s)
    return [t.replace(" ", "▁") for t in r.get_result_all(s).tokens]

def learn(phrase, samples):
    """Extra pronunciations: the model's own reading of each sample, if close to the phrase."""
    canon = tokenize(phrase); cl = letters(canon)
    seen = {}
    for x in samples:
        toks = heard(x)
        if not toks: continue
        # best contiguous window of whole tokens that matches the phrase letters
        best = None
        for i in range(len(toks)):
            if not toks[i].startswith("▁"): continue
            for j in range(i + 1, len(toks) + 1):
                if j < len(toks) and not toks[j].startswith("▁"): continue
                w = toks[i:j]; d = edit(letters(w), cl) / max(1, len(cl))
                if best is None or d < best[0]: best = (d, w)
        if best and best[0] <= MAX_DIST and len(best[1]) >= 3:
            k = " ".join(best[1])
            if k != " ".join(canon): seen[k] = seen.get(k, 0) + 1
    return [k.split() for k, _ in sorted(seen.items(), key=lambda kv: -kv[1])[:MAX_VARIANTS]]

def lines(variants, t, label="CUSTOM"):
    b = boost_for(t)
    return "/".join(f"{' '.join(v)} :{b:.2f} #{t:.3f} @{label}" for v in variants)

def detect(kw, x, chunk=2048):
    k = spotter(); s = k.create_stream(kw); n = 0
    for i in range(0, len(x), chunk):
        s.accept_waveform(16000, x[i:i + chunk])
        while k.is_ready(s):
            k.decode_stream(s); r = k.get_result(s)
            if r:
                if r != "NEVER": n += 1
                k.reset_stream(s)
    s.accept_waveform(16000, np.zeros(4800, np.float32))
    while k.is_ready(s):
        k.decode_stream(s); r = k.get_result(s)
        if r:
            if r != "NEVER": n += 1
            k.reset_stream(s)
    return n

def tune(phrase, samples, others=(), learn_variants=True):
    canon = tokenize(phrase)
    variants = [canon] + (learn(phrase, samples) if learn_variants else [])
    # drop learned variants that wake on the user's ordinary speech even at the strictest threshold that
    # the canonical form needs (they are too generic)
    def hits(vs, t, xs): return sum(1 for x in xs if detect(lines(vs, t), x) > 0)
    need = [len(samples)]
    def strictest1(vs, k):
        for i, t in enumerate(THRESHOLDS):
            if hits(vs, t, samples) < k: continue
            if i + 1 < len(THRESHOLDS) and hits(vs, THRESHOLDS[i + 1], samples) < k: continue
            return t
        return None
    def strictest(vs):
        # every take if possible, otherwise allow one outlier take (mirrors WakeTuner.strictestAllowingOne)
        need[0] = len(samples); b = strictest1(vs, need[0])
        if b is None and len(samples) >= MIN_TAKES: need[0] = len(samples) - 1; b = strictest1(vs, need[0])
        return b
    best = strictest(variants)
    if best is None:
        best = THRESHOLDS[-1]
    best = min(T_CAP, best)
    if others:
        kept = [canon]
        for v in variants[1:]:
            if hits([v], runtime_threshold(best, 55), others) == 0: kept.append(v)
        variants = kept
        # re-tune with the kept set
        best2 = strictest(variants)
        best = min(T_CAP, best2) if best2 is not None else THRESHOLDS[-1]
        # raise until no ordinary take wakes it at the default margin (keep all samples)
        while hits(variants, runtime_threshold(best, 55), others) > 0:
            nxt = round(best + 0.05, 2)
            if nxt > THRESHOLDS[0] or hits(variants, nxt, samples) < need[0]: break
            best = nxt
    got = hits(variants, runtime_threshold(best, 55), samples)
    return {"tokens": canon, "variants": variants, "threshold": best, "samples_hit": got, "n": len(samples), "can_save": len(samples) >= MIN_TAKES and got >= len(samples) - 1}
