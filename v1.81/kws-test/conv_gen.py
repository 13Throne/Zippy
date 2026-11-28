#!/usr/bin/env python3
"""Hard conversational negatives for the Zippy wake word (none of these should wake it).

Two-voice "conversations": random everyday sentences, lots of "hey <name>" greetings and
Zippy sound-alikes (hazy, has it been, sippy cup, zipper, Skippy, Ziggy, Mississippi, ...),
stitched into ~60 s streams with 0.15-0.9 s gaps, random level and optional noise.
Usage: conv_gen.py <piper-voice-dir> <out-dir> [n_streams]
"""
import os, sys, random, wave
import numpy as np
from scipy.signal import resample_poly
from piper import PiperVoice, SynthesisConfig

VOICES, OUT = sys.argv[1], sys.argv[2]
N = int(sys.argv[3]) if len(sys.argv) > 3 else 90
rng = random.Random(1081)
NAMES = ["Siri", "Sydney", "Skippy", "Zippo", "Ziggy", "Zeke", "Lizzie", "Missy", "Tippy", "Chippy", "Kippy",
         "Pippa", "Sissy", "Izzy", "Jimmy", "Timmy", "Sammy", "Zach", "Zoe", "Zara", "Ziva", "Philippe", "Gypsy",
         "Hippo", "Sweetie", "buddy", "guys", "babe", "Josh", "Brenda", "Jana", "Lucy", "Google", "Alexa",
         "Steve", "Sophie", "Suzy", "Bippy", "Dizzy", "Bitsy", "Poppy", "Sunny", "Frankie", "Lily"]
OPENERS = ["Hey {n}, how's it going?", "Hey {n}, can you grab that?", "Hey, {n}!", "Hey {n}, come here a sec.",
           "Okay {n}, that's enough.", "Hey {n}, did you see this?", "{n}, hey, wait up!", "Hi {n}, nice to see you.",
           "Hey there {n}.", "Yo {n}, what's up?"]
SOUNDALIKE = [
    "It's kind of hazy outside today.", "Has it been a long day?", "Ha, he's a sippy cup kid.", "Pull your zipper up.",
    "His zipper broke again.", "That car is really zippy.", "Hey, is it P or B?", "Hey, sit by me.", "Hey, it's me again.",
    "Easy peasy lemon squeezy.", "Mississippi is a long river.", "She's a total hippie.", "Hey, see if he's coming.",
    "Hey, sip it slowly.", "Hey, this be the one.", "Has he been here?", "He zipped it up.", "Hey, zip it!",
    "The zip code is wrong.", "Hey, it's a zipline!", "Hey, his sippy cup fell.", "Happy birthday to you!",
    "That was snappy.", "Hey, is he happy?", "Hay is for horses.", "He's easy to talk to.", "Hey, the Zippo lighter's empty.",
    "Hey, the zipper's stuck.", "Hey, Ziploc bags are in the drawer.", "Has easy access.", "Hey, sippin' on coffee.",
    "Hey, he's sleepy.", "Hey, Skippy peanut butter please.", "Hey, it's zippier now.", "A zippy little hatchback.",
    "Hey see, pee, and zee.", "Hazy IPA is my favorite.", "He's a bit tipsy.", "Hey Zip, you there?", "Hey, the city is busy.",
    "Is it zippable?", "The hippies sat by the river.", "Hey, fizzy drinks are bad for you.", "Hey, Lizzie's here.",
    "Is he busy?", "Hey, is she a Zumba teacher?", "Kids love sippy cups.", "Zippers and buttons.", "Hey, did you zip the bag?",
]
EVERYDAY = [
    "We should leave in about ten minutes.", "Did anyone feed the dog?", "I'll pick up groceries on the way home.",
    "Can you turn the TV down a little?", "What time does the game start?", "I think it's going to rain later.",
    "Where are my car keys?", "Let's order pizza tonight.", "The meeting ran long again.", "Did you call your mom back?",
    "I need to send that email before five.", "Hey, how was work?", "Hey, what are you doing this weekend?",
    "Hey, can you help me with this?", "Hey, dinner's ready!", "Hey, look at this.", "Hey, I'm home.",
    "I was thinking we could go hiking.", "That movie was actually pretty good.", "Turn left at the next light.",
    "My phone is almost dead.", "Is there any coffee left?", "The kids have school tomorrow.", "Remind me to pay the bill.",
    "Can you believe the price of gas?", "I'll be there in five minutes.", "We're out of milk again.",
    "Okay, sounds good.", "Honestly I have no idea.", "Wait, say that again?", "Yeah, I know what you mean.",
    "Did you finish the report?", "Let me check my calendar.", "The package should arrive tomorrow.",
    "Hey, hey, hey, calm down.", "Hey, hold on a second.", "Hey, you left the light on.", "Hey, it's your turn.",
    "So anyway, he said he'd think about it.", "I'm going to take a quick shower.", "Hey, are you listening?",
]

def load(name): return PiperVoice.load(os.path.join(VOICES, name + ".onnx"))

def synth(voice, text, spk=None):
    cfg = SynthesisConfig(speaker_id=spk, length_scale=rng.choice([0.8, 0.9, 1.0, 1.1, 1.2]),
                          noise_scale=rng.uniform(0.5, 0.8), noise_w_scale=rng.uniform(0.6, 1.0))
    pcm = np.concatenate([c.audio_float_array for c in voice.synthesize(text, syn_config=cfg)])
    sr = voice.config.sample_rate
    if sr != 16000:
        g = np.gcd(sr, 16000); pcm = resample_poly(pcm, 16000 // g, sr // g)
    return pcm.astype(np.float32)

def sentence():
    r = rng.random()
    if r < 0.30: return rng.choice(OPENERS).format(n=rng.choice(NAMES))
    if r < 0.65: return rng.choice(SOUNDALIKE)
    return rng.choice(EVERYDAY)

def main():
    os.makedirs(OUT, exist_ok=True)
    singles = ["en_US-lessac-medium", "en_US-ryan-medium", "en_US-amy-medium", "en_GB-alan-medium",
               "en_GB-southern_english_female-low", "en_US-hfc_male-medium", "en_US-kristin-medium",
               "en_GB-northern_english_male-medium"]
    lts = load("en_US-libritts_r-medium"); cache = {"en_US-libritts_r-medium": lts}
    spk = [(s, None) for s in singles] + [("en_US-libritts_r-medium", s) for s in rng.sample(range(lts.config.num_speakers), 60)]
    rows = []
    for k in range(N):
        a, b = rng.sample(spk, 2)
        parts, texts = [np.zeros(int(16000 * 0.5), np.float32)], []
        dur = 0.0
        while dur < 60:
            vn, sid = a if len(texts) % 2 == 0 else b
            v = cache.get(vn) or cache.setdefault(vn, load(vn))
            t = sentence(); x = synth(v, t, sid)
            x = x / (np.max(np.abs(x)) + 1e-9) * 10 ** (rng.uniform(-20, -2) / 20)
            parts += [x, np.zeros(int(16000 * rng.uniform(0.15, 0.9)), np.float32)]
            texts.append(t); dur += (len(x) + len(parts[-1])) / 16000
        y = np.concatenate(parts)
        if rng.random() < 0.5:
            snr = rng.uniform(10, 25)
            n = np.random.default_rng(rng.randrange(1 << 30)).standard_normal(len(y))
            n = np.convolve(n, np.ones(4) / 4, mode="same")
            ps = np.mean(y[np.abs(y) > 1e-4] ** 2)
            y = y + n * np.sqrt(ps / (np.mean(n ** 2) * 10 ** (snr / 10)))
        y = np.clip(y, -1, 1)
        name = f"conv_{k:03d}.wav"
        with wave.open(os.path.join(OUT, name), "wb") as w:
            w.setnchannels(1); w.setsampwidth(2); w.setframerate(16000); w.writeframes((y * 32767).astype("<i2").tobytes())
        rows.append((name, " | ".join(texts)))
        print("stream", k, round(dur, 1), flush=True)
    with open(os.path.join(OUT, "texts.tsv"), "w") as f:
        for r in rows: f.write("\t".join(r) + "\n")

main()
