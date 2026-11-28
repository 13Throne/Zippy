package com.zippy.ear;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Zippy 1.81: fits a typed wake phrase to the user's own recordings. Pure logic (the detector is an
 * interface), mirrored by v1.81/tools/phrase_tune.py, which the desktop accuracy test uses.
 *
 *  1. The phrase's own tokens (BpeTokenizer) are always a pronunciation.
 *  2. learn(): what the KWS model itself hears in each take (greedy transducer decode with the same
 *     model) becomes an extra pronunciation when it is close to the phrase (letters edit distance
 *     <= 40%), up to 4, most frequent first. This is how 1.7's hand-made "HAZIPPI"-style variants
 *     were found; now it happens per user.
 *  3. tune(): the strictest trigger threshold (boost tied to it, as in KwsTuning) at which EVERY take
 *     wakes, and still does one step easier. Learned pronunciations that wake on the user's ordinary-speech takes are dropped, and
 *     the threshold is raised while an ordinary take still wakes it (never losing a take).
 *  4. runtimeThreshold(): Sensitivity adds a margin around the tuned value (55 = tuned - 0.05).
 */
public final class WakeTuner {
    private WakeTuner() {}

    public interface Detector {
        /** Number of keyword hits in the audio (16 kHz mono) with these keyword lines. */
        int hits(String keywords, float[] audio);
        /** What the model hears in the audio (tokens), or an empty array. */
        String[] heard(float[] audio);
    }

    public static final double[] THRESHOLDS = {0.60, 0.55, 0.50, 0.45, 0.40, 0.35, 0.30, 0.25, 0.20, 0.15, 0.10, 0.05};
    public static final int MAX_VARIANTS = 4;
    public static final double MAX_DIST = 0.40, MARGIN = 0.05, SLOPE = 0.003, T_MIN = 0.03, T_MAX = 0.70;
    /** Never start stricter than this (the strictest all-takes value overfits clean takes and drops held-out recall). */
    public static final double T_CAP = 0.30;
    public static final int MIN_TAKES = 5, MAX_TAKES = 8, DEFAULT_TAKES = 6;
    public static final String LABEL = "CUSTOM";

    public static double boostFor(double t) {
        return Math.round(Math.min(2.0, Math.max(1.0, 1.0 + (0.45 - t) * 2.5)) * 100) / 100.0;
    }

    public static double runtimeThreshold(double tuned, int sens) {
        int s = Math.max(0, Math.min(100, sens));
        double t = Math.min(T_MAX, Math.max(T_MIN, tuned - MARGIN - (s - 55) * SLOPE));
        return Math.round(t * 1000) / 1000.0;
    }

    public static String lines(List<String[]> variants, double t) {
        StringBuilder b = new StringBuilder();
        double boost = boostFor(t);
        for (String[] v : variants) {
            if (b.length() > 0) b.append('/');
            b.append(String.join(" ", v)).append(String.format(Locale.US, " :%.2f #%.3f @%s", boost, t, LABEL));
        }
        return b.toString();
    }

    static String letters(String[] toks) {
        StringBuilder b = new StringBuilder();
        for (String t : toks) b.append(t);
        return b.toString().replace("\u2581", "").replace("'", "");
    }

    static int edit(String a, String b) {
        int[] d = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) d[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int p = d[0];
            d[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int tmp = d[j];
                d[j] = Math.min(Math.min(d[j] + 1, d[j - 1] + 1), p + (a.charAt(i - 1) != b.charAt(j - 1) ? 1 : 0));
                p = tmp;
            }
        }
        return d[b.length()];
    }

    /** The window of whole words in what was heard that is closest to the phrase, or null. */
    public static String[] closest(String[] heard, String[] canon) {
        if (heard == null || heard.length == 0) return null;
        String cl = letters(canon);
        double bestD = Double.MAX_VALUE;
        String[] best = null;
        for (int i = 0; i < heard.length; i++) {
            if (!heard[i].startsWith("\u2581")) continue;
            for (int j = i + 1; j <= heard.length; j++) {
                if (j < heard.length && !heard[j].startsWith("\u2581")) continue; // whole words only
                String[] w = Arrays.copyOfRange(heard, i, j);
                double d = edit(letters(w), cl) / (double) Math.max(1, cl.length());
                if (d < bestD) { bestD = d; best = w; }
            }
        }
        if (best == null || bestD > MAX_DIST || best.length < 3) return null;
        return best;
    }

    public static List<String[]> learn(String[] canon, List<String[]> heards) {
        Map<String, Integer> seen = new LinkedHashMap<>();
        String c = String.join(" ", canon);
        for (String[] h : heards) {
            String[] w = closest(h, canon);
            if (w == null) continue;
            String k = String.join(" ", w);
            if (!k.equals(c)) seen.put(k, seen.getOrDefault(k, 0) + 1);
        }
        List<Map.Entry<String, Integer>> es = new ArrayList<>(seen.entrySet());
        es.sort((a, b) -> b.getValue() - a.getValue()); // stable: first heard wins ties
        List<String[]> out = new ArrayList<>();
        for (int i = 0; i < es.size() && i < MAX_VARIANTS; i++) out.add(es.get(i).getKey().split(" "));
        return out;
    }

    public static final class Result {
        public final String[] tokens;
        public final List<String[]> variants;   // tokens first, then learned pronunciations
        public final double threshold;          // tuned (strictest that wakes on every take)
        public final boolean[] takeHit;         // at the default-Sensitivity runtime threshold
        public final boolean[] otherHit;        // ordinary-speech takes, at the default runtime threshold
        public final int learned, dropped;
        Result(String[] tokens, List<String[]> variants, double threshold, boolean[] takeHit, boolean[] otherHit, int learned, int dropped) {
            this.tokens = tokens; this.variants = variants; this.threshold = threshold;
            this.takeHit = takeHit; this.otherHit = otherHit; this.learned = learned; this.dropped = dropped;
        }
        public int takesHit() { int n = 0; for (boolean b : takeHit) if (b) n++; return n; }
        public int othersHit() { int n = 0; for (boolean b : otherHit) if (b) n++; return n; }
        public boolean allTakes() { return takesHit() == takeHit.length; }
        /** Saving is allowed when every take, or all but one, wakes it at the default Sensitivity. */
        public boolean canSave() { return takeHit.length >= MIN_TAKES && takesHit() >= takeHit.length - 1; }
    }

    static int count(Detector d, List<String[]> vs, double t, List<float[]> xs) {
        String kw = lines(vs, t);
        int n = 0;
        for (float[] x : xs) if (d.hits(kw, x) > 0) n++;
        return n;
    }

    /**
     * Strictest threshold at which every take wakes, AND still every take one step easier (borderline
     * takes can flip between runs, so a single lucky pass isn't trusted).
     */
    static Double strictest(Detector d, List<String[]> vs, List<float[]> takes, int need) {
        for (int i = 0; i < THRESHOLDS.length; i++) {
            if (count(d, vs, THRESHOLDS[i], takes) < need) continue;
            if (i + 1 < THRESHOLDS.length && count(d, vs, THRESHOLDS[i + 1], takes) < need) continue;
            return THRESHOLDS[i];
        }
        return null;
    }

    /**
     * Every take if possible; otherwise allow ONE outlier take (a cough, a clipped take) instead of falling to the
     * loosest threshold, which made the phrase wake far too easily. Sets need[0] to the number of takes required.
     */
    static Double strictestAllowingOne(Detector d, List<String[]> vs, List<float[]> takes, int[] need) {
        need[0] = takes.size();
        Double b = strictest(d, vs, takes, need[0]);
        if (b == null && takes.size() >= MIN_TAKES) { need[0] = takes.size() - 1; b = strictest(d, vs, takes, need[0]); }
        return b;
    }

    public static Result tune(Detector d, String[] canon, List<float[]> takes, List<float[]> others) {
        List<String[]> heards = new ArrayList<>();
        for (float[] x : takes) heards.add(d.heard(x));
        List<String[]> learned = learn(canon, heards);
        List<String[]> vs = new ArrayList<>();
        vs.add(canon);
        vs.addAll(learned);
        int[] need = new int[1];
        Double best = strictestAllowingOne(d, vs, takes, need);
        double t = best == null ? THRESHOLDS[THRESHOLDS.length - 1] : Math.min(T_CAP, best);
        int dropped = 0;
        if (others != null && !others.isEmpty()) {
            List<String[]> kept = new ArrayList<>();
            kept.add(canon);
            for (String[] v : learned) {
                List<String[]> one = new ArrayList<>();
                one.add(v);
                if (count(d, one, runtimeThreshold(t, 55), others) == 0) kept.add(v); else dropped++;
            }
            vs = kept;
            best = strictestAllowingOne(d, vs, takes, need);
            t = best == null ? THRESHOLDS[THRESHOLDS.length - 1] : Math.min(T_CAP, best);
            while (count(d, vs, runtimeThreshold(t, 55), others) > 0) {
                double next = Math.round((t + 0.05) * 100) / 100.0;
                if (next > THRESHOLDS[0] || count(d, vs, next, takes) < need[0]) break;
                t = next;
            }
        }
        String kwRun = lines(vs, runtimeThreshold(t, 55)); // what the listener uses at the default Sensitivity
        boolean[] th = new boolean[takes.size()];
        for (int i = 0; i < th.length; i++) th[i] = d.hits(kwRun, takes.get(i)) > 0;
        int no = others == null ? 0 : others.size();
        boolean[] oh = new boolean[no];
        for (int i = 0; i < no; i++) oh[i] = d.hits(kwRun, others.get(i)) > 0;
        return new Result(canon, vs, t, th, oh, vs.size() - 1, dropped);
    }
}
