#!/usr/bin/env python3
"""Synthesize the Zippy 1.7 wake-word test set with Piper voices (16 kHz mono wav + manifest.tsv).

Classes:
  pos   "Hey Zippy" (alone, with punctuation/prosody variants, followed by a task, inside a sentence)
  mis   slight mispronunciations that should still wake ("hey zippie", "hay zippy", "hey zeppy", s-for-z "hey sippy")
  near  near-miss phrases that should NOT wake ("hey skippy", "happy", "hey siri", "zipper", ...)
  neg   ordinary conversation / commands that should NOT wake
Every utterance gets random speed, a random level (-26..0 dBFS peak) and half of them background noise (10-25 dB SNR).
Usage: gen_testset.py <piper-voice-dir> <out-dir> [n_libritts_speakers]
"""
import os, sys, random, wave
import numpy as np
from scipy.signal import resample_poly
from piper import PiperVoice, SynthesisConfig

VOICES, OUT = sys.argv[1], sys.argv[2]
N_LTS = int(sys.argv[3]) if len(sys.argv) > 3 else 40
rng = random.Random(17)

POS = ["Hey Zippy.", "Hey, Zippy!", "Hey Zippy?", "hey zippy", "Hey Zippy, what time is it?",
       "Hey Zippy, add milk to the shopping list.", "Okay. Hey Zippy.", "Hey Zippy... remind me to call mom."]
MIS = ["Hey Zippie.", "Hay Zippy.", "Hey Zeppy.", "Hey Zippee!", "Hey Sippy.", "Hey, Sippy?"]
NEAR = ["Hey Skippy.", "Happy birthday!", "Hey Siri.", "Pull up the zipper.", "Hey sweetie.", "Hey Jimmy.",
        "Hey, did you zip it?", "Mississippi is big.", "Hey Zack.", "She's a hippie.", "That was snappy.",
        "Hey Pippa.", "Hey there, sleepy.", "Is it zippable?", "Hey, Lizzie!"]
NEG = ["What do you need?", "I'm listening.", "Can you turn the lights off in the kitchen?",
       "We should leave in about ten minutes.", "Did anyone feed the dog this morning?",
       "The meeting got moved to Thursday afternoon.", "Hey, how are you doing today?", "Hey, what's up?",
       "Add milk to the shopping list.", "What's the weather like tomorrow?", "Play some music in the living room.",
       "I think the package arrived yesterday.", "Hey guys, dinner is ready.", "Hey Google, set a timer.",
       "Let me know when you're done.", "Okay, that sounds good to me.", "Where did I put my keys?",
       "The quick brown fox jumps over the lazy dog.", "Remind me to call mom at six.", "That's pretty easy."]

def load(name):
    return PiperVoice.load(os.path.join(VOICES, name + ".onnx"))

def synth(voice, text, spk=None, ls=1.0):
    cfg = SynthesisConfig(speaker_id=spk, length_scale=ls, noise_scale=rng.uniform(0.5, 0.8),
                          noise_w_scale=rng.uniform(0.6, 1.0))
    pcm = np.concatenate([c.audio_float_array for c in voice.synthesize(text, syn_config=cfg)])
    sr = voice.config.sample_rate
    if sr != 16000:
        g = np.gcd(sr, 16000)
        pcm = resample_poly(pcm, 16000 // g, sr // g)
    return pcm.astype(np.float32)

def augment(x):
    peak_db = rng.uniform(-26, -1)
    x = x / (np.max(np.abs(x)) + 1e-9) * 10 ** (peak_db / 20)
    x = np.concatenate([np.zeros(int(16000 * rng.uniform(0.3, 0.8))), x, np.zeros(int(16000 * 0.8))])
    noisy = rng.random() < 0.5
    snr = None
    if noisy:
        snr = rng.uniform(10, 25)
        n = np.random.default_rng(rng.randrange(1 << 30)).standard_normal(len(x))
        n = np.convolve(n, np.ones(4) / 4, mode="same")  # slightly pink-ish
        p_sig = np.mean(x[np.abs(x) > 1e-4] ** 2) if np.any(np.abs(x) > 1e-4) else 1e-8
        n *= np.sqrt(p_sig / (np.mean(n ** 2) * 10 ** (snr / 10)))
        x = x + n
    return np.clip(x, -1, 1).astype(np.float32), peak_db, snr

def save(path, x):
    with wave.open(path, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(16000)
        w.writeframes((x * 32767).astype("<i2").tobytes())

def main():
    for c in ("pos", "mis", "near", "neg"):
        os.makedirs(os.path.join(OUT, c), exist_ok=True)
    singles = ["en_US-lessac-medium", "en_US-ryan-medium", "en_US-amy-medium", "en_GB-alan-medium",
               "en_GB-southern_english_female-low", "en_US-hfc_male-medium", "en_US-kristin-medium",
               "en_GB-northern_english_male-medium"]
    speakers = [(s, None) for s in singles]
    lts = load("en_US-libritts_r-medium")
    nspk = lts.config.num_speakers
    speakers += [("en_US-libritts_r-medium", s) for s in rng.sample(range(nspk), N_LTS)]
    cache = {"en_US-libritts_r-medium": lts}
    rows = []
    for vi, (vname, spk) in enumerate(speakers):
        v = cache.get(vname) or cache.setdefault(vname, load(vname))
        sets = [("pos", POS), ("mis", MIS), ("near", NEAR), ("neg", rng.sample(NEG, 8))]
        for cls, texts in sets:
            for ti, t in enumerate(texts):
                ls = rng.choice([0.8, 0.9, 1.0, 1.1, 1.25])
                x, pk, snr = augment(synth(v, t, spk, ls))
                name = f"{cls}/{vi:02d}_{ti:02d}.wav"
                save(os.path.join(OUT, name), x)
                rows.append((name, cls, t, vname + ("" if spk is None else f"#{spk}"), ls, round(pk, 1),
                             "" if snr is None else round(snr, 1)))
        print("voice", vi, vname, spk, flush=True)
    with open(os.path.join(OUT, "manifest.tsv"), "w") as f:
        f.write("file\tclass\ttext\tvoice\tspeed\tpeak_db\tsnr_db\n")
        for r in rows:
            f.write("\t".join(map(str, r)) + "\n")

main()
