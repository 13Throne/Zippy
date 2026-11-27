"""Python mirror of com.zippy.ear.KwsTuning: Sensitivity (0-100, higher = easier) -> Sherpa-ONNX keywords.

Tokens are the exact BPE pieces of the model's bpe.model (see make_keywords.py / keywords.txt).
Higher sensitivity = more accepted pronunciations, a higher boosting score and a lower trigger threshold.
v1.7 test (test-results.md) checks the Java and Python tables are identical.
"""
THREADS = 1
# (min sensitivity, phrase, BPE tokens)
VARIANTS = [
    (0,  "HEY ZIPPY", "▁HE Y ▁ Z I PP Y"),
    (0,  "HEY ZIPPI", "▁HE Y ▁ Z I PP I"),
    (0,  "HAZIPPI",   "▁HA Z I PP I"),
    (0,  "HAZIPPY",   "▁HA Z I PP Y"),
    (30, "HEZIPPI",   "▁HE Z I PP I"),
    (30, "HE ZIPPI",  "▁HE ▁ Z I PP I"),
    (30, "HAY ZIPPY", "▁HA Y ▁ Z I PP Y"),
    (30, "HAZIPY",    "▁HA Z I P Y"),
    (30, "HAZIBY",    "▁HA Z I B Y"),
    (50, "HEY SIPPI", "▁HE Y ▁S I PP I"),   # a soft "z" heard as "s" (the Vosk problem)
    (50, "HEY SIPPY", "▁HE Y ▁S I PP Y"),
    (85, "ZIPPY",     "▁ Z I PP Y"),        # bare "Zippy"
    (85, "ZIPPI",     "▁ Z I PP I"),
]
# createStream(keywords) ADDS to the keywords file the spotter was built with, so the shipped
# keywords.txt holds only this never-said sentinel; every real line comes from keywords_for(s).
SENTINEL_PHRASE = "ZYZZYVA QUOKKA XYLOPHONE"
SENTINEL = "▁ Z Y Z Z Y V A ▁QU O K K A ▁ X Y LO PH ONE :1.00 #0.990 @NEVER"
TH_HARD, TH_EASY = 0.45, 0.05      # trigger threshold (#) at sensitivity 0 / 100
BOOST_HARD, BOOST_EASY = 1.0, 2.0  # boosting score (:) at sensitivity 0 / 100
BARE_EXTRA = 0.10                  # bare "Zippy" needs a higher threshold

def clamp(s):
    return max(0, min(100, int(s)))

def threshold(s):
    return round(TH_HARD + (TH_EASY - TH_HARD) * clamp(s) / 100.0, 3)

def boost(s):
    return round(BOOST_HARD + (BOOST_EASY - BOOST_HARD) * clamp(s) / 100.0, 2)

def keywords_for(s):
    s = clamp(s)
    t, b = threshold(s), boost(s)
    out = []
    for need, phrase, tok in VARIANTS:
        if s < need:
            continue
        th = t + BARE_EXTRA if need >= 85 else t
        label = "ZIPPY" if need >= 85 else "HEY_ZIPPY"
        out.append(f"{tok} :{b:.2f} #{th:.3f} @{label}")
    return "/".join(out)

if __name__ == "__main__":
    for s in (0, 30, 55, 85, 100):
        print(s, keywords_for(s))
