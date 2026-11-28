#!/usr/bin/env python3
"""Export the KWS model's SentencePiece unigram table (bpe.model) as assets/kws/bpe.vocab ("piece TAB score").
BpeTokenizer.java does the Viterbi encoding on the phone with it.
Usage: make_vocab.py <model-dir> <out bpe.vocab>"""
import os, sys
from sentencepiece import sentencepiece_model_pb2 as pb
m = pb.ModelProto(); m.ParseFromString(open(os.path.join(sys.argv[1], "bpe.model"), "rb").read())
assert m.trainer_spec.model_type == 1, "expects a unigram model"
with open(sys.argv[2], "w", encoding="utf-8") as f:
    for p in m.pieces:
        if p.type == 1: f.write(f"{p.piece}\t{p.score:.6f}\n")
print("wrote", sys.argv[2])
