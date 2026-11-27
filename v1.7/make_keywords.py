#!/usr/bin/env python3
"""Check every KwsTuning pronunciation against the model's own tokenizer and write assets/kws/keywords.txt.

For each phrase, the tokens must equal BOTH sentencepiece's encoding with bpe.model AND
sherpa_onnx.text2token(..., tokens_type="bpe"), and every token must exist in tokens.txt.
The Java table (KwsTuning.java) must match the Python mirror (kws-test/kws_tuning.py) exactly.
keywords.txt holds only a never-said sentinel: Sherpa-ONNX's createStream(keywords) ADDS its lines
to the file's, so all real lines are passed per sensitivity at runtime (KwsTuning.keywords(s)).
Usage: make_keywords.py <model-dir> <out keywords.txt>
"""
import os, re, sys
import sentencepiece as spm
import sherpa_onnx
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "kws-test"))
import kws_tuning as K

model, out = sys.argv[1], sys.argv[2]
sp = spm.SentencePieceProcessor(model_file=os.path.join(model, "bpe.model"))
vocab = {l.split()[0] for l in open(os.path.join(model, "tokens.txt"), encoding="utf-8") if l.strip()}
bad = 0
phrases = [p for _, p, _ in K.VARIANTS]
t2t = sherpa_onnx.text2token(phrases, tokens=os.path.join(model, "tokens.txt"), tokens_type="bpe",
                             bpe_model=os.path.join(model, "bpe.model"))
sent_tok = K.SENTINEL.split(" :")[0]
sent_ok = " ".join(sp.encode(K.SENTINEL_PHRASE, out_type=str)) == sent_tok and all(t in vocab for t in sent_tok.split())
print(("OK  " if sent_ok else "BAD ") + f"sentinel {K.SENTINEL_PHRASE} -> {sent_tok}")
bad += not sent_ok
for (need, phrase, tok), viat2t in zip(K.VARIANTS, t2t):
    enc = " ".join(sp.encode(phrase, out_type=str))
    ok = enc == tok == " ".join(viat2t) and all(t in vocab for t in tok.split())
    print(("OK  " if ok else "BAD ") + f"{phrase:10s} -> {tok}   (from sensitivity {need})")
    bad += not ok

java = open(os.path.join(HERE, "java/com/zippy/ear/KwsTuning.java"), encoding="utf-8").read()
jrows = re.findall(r'\{"(\d+)",\s*"([A-Z ]+)",\s*"([^"]+)"\}', java)
jrows = [(int(a), b, c.encode().decode("unicode_escape")) for a, b, c in jrows]
same = jrows == [(n, p, t) for n, p, t in K.VARIANTS]
js = re.search(r'SENTINEL = "([^"]+)"', java).group(1).encode().decode("unicode_escape")
same = same and js == K.SENTINEL
num = lambda name: float(re.search(name + r"\s*=\s*([0-9.]+)", java).group(1))
same = same and (num("TH_HARD"), num("TH_EASY"), num("BOOST_HARD"), num("BOOST_EASY"), num("BARE_EXTRA")) == \
       (K.TH_HARD, K.TH_EASY, K.BOOST_HARD, K.BOOST_EASY, K.BARE_EXTRA)
print(("OK  " if same else "BAD ") + "KwsTuning.java table == kws_tuning.py table")
bad += not same
with open(out, "w", encoding="utf-8") as f:
    f.write(K.SENTINEL + "\n")
print("wrote", out)
sys.exit(1 if bad else 0)
