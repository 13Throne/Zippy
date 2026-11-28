#!/usr/bin/env python3
"""Reference tokens from sentencepiece (bpe.model) for V181Test's BpeTokenizer check.
Usage: make_bpe_expected.py <model-dir> <out.tsv>"""
import os, sys, random
import sentencepiece as spm
sp = spm.SentencePieceProcessor(model_file=os.path.join(sys.argv[1], "bpe.model"))
words = ("hey okay ok yo hi hello zippy jarvis computer fred jana brenda josh joshua wake up listen assistant buddy "
         "friend robot alexa siri google marvin friday karen edith nova echo atlas orion sky max zeus apollo luna "
         "phone android galaxy pixel ziggy zappy sparky whiskey tango foxtrot alpha bravo charlie delta captain "
         "commander mister misses doctor professor genie oracle jeeves alfred hal bender data spock kitt "
         "don't it's we're i'm you're o'clock rock'n'roll mississippi xylophone quokka zyzzyva").split()
rng = random.Random(81)
phrases = set(["Hey Zippy", "Hey Jarvis", "Okay Computer", "Hey Fred", "Yo Zippy", "Zippy wake up", "HEY ZIPPY"])
while len(phrases) < 320:
    phrases.add(" ".join(rng.choice(words) for _ in range(rng.randint(1, 4))))
with open(sys.argv[2], "w", encoding="utf-8") as f:
    for p in sorted(phrases):
        f.write(p + "\t" + " ".join(sp.encode(p.upper(), out_type=str)) + "\n")
print(len(phrases), "phrases")
