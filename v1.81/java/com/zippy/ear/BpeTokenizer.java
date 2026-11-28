package com.zippy.ear;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Zippy 1.81: turns any typed wake phrase into the Sherpa-ONNX KWS model's own tokens, on the phone.
 *
 * The KWS model (gigaspeech zipformer) uses a 500-piece SentencePiece UNIGRAM model. Unigram encoding is a
 * Viterbi search for the segmentation with the highest total piece log-probability, so with the piece
 * table (assets/kws/bpe.vocab: "piece TAB score", exported from bpe.model by v1.81/tools/make_vocab.py)
 * it is reproduced exactly in plain Java. v1.81/test checks it against sentencepiece on 300+ phrases.
 */
public final class BpeTokenizer {
    public static final String VOCAB = "kws/bpe.vocab";
    static final char SP = '\u2581';
    private final Map<String, Float> score = new HashMap<>();
    private int maxLen = 1;

    public BpeTokenizer(Map<String, Float> pieces) {
        for (Map.Entry<String, Float> e : pieces.entrySet()) add(e.getKey(), e.getValue());
    }

    public static BpeTokenizer load(InputStream in) throws java.io.IOException {
        Map<String, Float> m = new HashMap<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                int t = line.indexOf('\t');
                if (t <= 0) continue;
                m.put(line.substring(0, t), Float.parseFloat(line.substring(t + 1).trim()));
            }
        }
        return new BpeTokenizer(m);
    }

    private void add(String piece, float s) {
        if (piece.startsWith("<")) return; // <blk>, <sos/eos>, <unk>
        score.put(piece, s);
        maxLen = Math.max(maxLen, piece.length());
    }

    /** Upper case letters, apostrophes and single spaces; everything else becomes a space. */
    public static String clean(String phrase) {
        if (phrase == null) return "";
        String up = phrase.toUpperCase(Locale.US);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < up.length(); i++) {
            char c = up.charAt(i);
            b.append((c >= 'A' && c <= 'Z') || c == '\'' ? c : ' ');
        }
        return b.toString().replaceAll("\\s+", " ").trim();
    }

    /** Tokens for the phrase, or null if some part can't be spelled with the model's pieces. */
    public String[] encode(String phrase) {
        String c = clean(phrase);
        if (c.isEmpty()) return null;
        String s = SP + c.replace(' ', SP);
        int n = s.length();
        double[] best = new double[n + 1];
        int[] from = new int[n + 1];
        java.util.Arrays.fill(best, Double.NEGATIVE_INFINITY);
        best[0] = 0;
        for (int i = 0; i < n; i++) {
            if (best[i] == Double.NEGATIVE_INFINITY) continue;
            for (int len = 1; len <= maxLen && i + len <= n; len++) {
                Float sc = score.get(s.substring(i, i + len));
                if (sc == null) continue;
                double v = best[i] + sc;
                if (v > best[i + len]) { best[i + len] = v; from[i + len] = i; }
            }
        }
        if (best[n] == Double.NEGATIVE_INFINITY) return null;
        List<String> out = new ArrayList<>();
        for (int j = n; j > 0; j = from[j]) out.add(0, s.substring(from[j], j));
        return out.toArray(new String[0]);
    }
}
