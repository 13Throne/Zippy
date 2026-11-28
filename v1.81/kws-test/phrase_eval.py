#!/usr/bin/env python3
"""Desktop eval of the 1.81 custom wake phrase training (Piper TTS stand-in for the user).

For each phrase and each simulated user voice: 6 training takes + 3 ordinary-speech takes -> tune();
then held-out recall on 20 new takes by the same voice (alone, before a task, noisy), recall on 32 takes by
other voices, and false wakes over the 2-voice conversation streams (conv/). Baseline = the phrase's plain
tokens at the 1.8 default-Sensitivity threshold (0.23 / boost 1.55) without training.
Usage: phrase_eval.py <piper-dir> <conv-dir> <out.json>
"""
import os, sys, json, glob, wave, random
import numpy as np
from concurrent.futures import ProcessPoolExecutor
from scipy.signal import resample_poly
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import phrase_tune as P

PIPER, CONV, OUT = sys.argv[1:4]
PHRASES = ["Hey Zippy", "Hey Jarvis", "Okay Computer", "Hey Fred", "Yo Zippy", "Zippy wake up"]
USERS = [("en_US-lessac-medium", None), ("en_US-ryan-medium", None), ("en_US-amy-medium", None),
         ("en_GB-alan-medium", None), ("en_US-kristin-medium", None), ("en_US-libritts_r-medium", 33)]
OTHERS = ["en_US-hfc_male-medium", "en_GB-southern_english_female-low", "en_GB-northern_english_male-medium"] + \
         [("en_US-libritts_r-medium", s) for s in (101, 250, 480, 777, 860)]
ORDINARY = ["Can you turn the lights off in the kitchen?", "We should leave in about ten minutes.",
            "Hey, how are you doing today?", "Did anyone feed the dog this morning?", "Hey, what's up?"]
TASKS = ["what time is it?", "add milk to the shopping list.", "remind me to call mom."]

def conv_audio():
    out = []
    for p in sorted(glob.glob(os.path.join(CONV, "*.wav"))):
        with wave.open(p) as w: out.append(np.frombuffer(w.readframes(w.getnframes()), "<i2").astype(np.float32) / 32768)
    return out

_vc = {}
def voice(name):
    from piper import PiperVoice
    if name not in _vc: _vc[name] = PiperVoice.load(os.path.join(PIPER, name + ".onnx"))
    return _vc[name]

def synth(rng, vname, spk, text, noisy=None):
    from piper import SynthesisConfig
    v = voice(vname)
    cfg = SynthesisConfig(speaker_id=spk, length_scale=rng.choice([0.85, 0.95, 1.0, 1.1, 1.2]),
                          noise_scale=rng.uniform(0.5, 0.8), noise_w_scale=rng.uniform(0.6, 1.0))
    x = np.concatenate([c.audio_float_array for c in v.synthesize(text, syn_config=cfg)])
    sr = v.config.sample_rate
    if sr != 16000:
        g = np.gcd(sr, 16000); x = resample_poly(x, 16000 // g, sr // g)
    x = x / (np.max(np.abs(x)) + 1e-9) * 10 ** (rng.uniform(-20, -3) / 20)
    x = np.concatenate([np.zeros(int(16000 * rng.uniform(0.3, 0.6))), x, np.zeros(int(16000 * 0.6))])
    if noisy if noisy is not None else rng.random() < 0.5:
        snr = rng.uniform(10, 25); n = np.random.default_rng(rng.randrange(1 << 30)).standard_normal(len(x))
        n = np.convolve(n, np.ones(4) / 4, mode="same"); ps = np.mean(x[np.abs(x) > 1e-4] ** 2)
        x = x + n * np.sqrt(ps / (np.mean(n ** 2) * 10 ** (snr / 10)))
    return np.clip(x, -1, 1).astype(np.float32)

def say(phrase, i):
    return [phrase + ".", phrase + "!", phrase + "?", phrase.lower(), phrase + ", " + TASKS[i % 3]][i % 5]

def job(args):
    phrase, ui = args
    vname, spk = USERS[ui]
    rng = random.Random(hash((phrase, ui)) & 0xffffff)
    train = [synth(rng, vname, spk, say(phrase, i)[:-1] if i < 4 else phrase + ".", noisy=False) for i in range(6)]
    others = [synth(rng, vname, spk, t, noisy=False) for t in ORDINARY[:3]]
    held = [synth(rng, vname, spk, say(phrase, i)) for i in range(20)]
    oth = []
    for i in range(32):
        o = OTHERS[i % len(OTHERS)]; vn, sp = (o, None) if isinstance(o, str) else o
        oth.append(synth(rng, vn, sp, say(phrase, i)))
    conv = conv_audio()
    res = {"phrase": phrase, "user": vname + ("" if spk is None else f"#{spk}")}
    tuned = P.tune(phrase, train, others)
    t_run = P.runtime_threshold(tuned["threshold"], 55)
    variants = tuned["variants"]
    configs = {"baseline_untrained": P.lines([P.tokenize(phrase)], 0.23),
               "trained": P.lines(variants, t_run)}
    if phrase == "Hey Zippy":
        sys.path.insert(0, "/workspace/zippy-repo/to-push/v1.7/kws-test"); import kws_tuning as K
        configs["v1.8_builtin_s55"] = K.keywords_for(55)
    for name, kw in configs.items():
        r = {"held_same_voice": sum(P.detect(kw, x) > 0 for x in held) / len(held),
             "other_voices": sum(P.detect(kw, x) > 0 for x in oth) / len(oth),
             "train_hit": sum(P.detect(kw, x) > 0 for x in train),
             "conv_false_wakes": sum(P.detect(kw, x) for x in conv),
             "conv_minutes": round(sum(len(x) for x in conv) / 16000 / 60, 1)}
        res[name] = r
    res["tuned"] = {"threshold": tuned["threshold"], "runtime_threshold_s55": t_run,
                    "variants": [" ".join(v) for v in variants], "samples_hit": tuned["samples_hit"]}
    print(json.dumps(res), flush=True)
    return res

if __name__ == "__main__":
    jobs = [(p, u) for p in PHRASES for u in range(len(USERS))]
    with ProcessPoolExecutor(int(os.environ.get("WORKERS", "8"))) as ex:
        out = list(ex.map(job, jobs))
    json.dump(out, open(OUT, "w"), indent=1)
