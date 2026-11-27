package com.zippy.ear;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Zippy 1.4 voice check (replaces the 1.2/1.3 Voice.fingerprint/matches logic).
 *
 * Why the old check rejected the trained speaker even at Sensitivity 0:
 *  - Its fingerprint was an L2-normalised vector of log(1 + |FFT|) band averages. Every entry is
 *    positive and the shape barely changes, so ANY two sounds score ~0.99 cosine.
 *  - It then required the wake score to beat the best "other" take (hey / zip / hey zip, recorded
 *    in the SAME voice) by +0.03. With both near 0.99 and a ceiling of 1.0 that is essentially
 *    impossible, and Sensitivity never touched that rule.
 *  - Frames quieter than a fixed mean-square of 4e-4 were dropped, so a phrase said at normal
 *    distance could produce an all-zero fingerprint (cosine 0 -> reject).
 *
 * 1.4 fingerprint: pre-emphasis, 512-pt frames (hop 256), 26 mel bands 100-7000 Hz, log power,
 * DCT -> cepstra c1..c13 (c0 / loudness dropped), frames gated RELATIVE to the loudest frame
 * (so distance / volume don't empty it), per-utterance mean + std of each cepstrum. Vectors carry
 * a version tag in slot 0 so old 1.3 prints are detected and the user is asked to retrain.
 *
 * The room-noise floor (quietest 20% of frames in the window) is subtracted per mel band before
 * the log, so a fan / TV / distance doesn't drag every cepstrum away from the trained takes.
 *
 * Matching: distance to the trained takes (mean of the 3 nearest), compared against a threshold
 * calibrated from how far the user's own takes are from each other (leave-one-out). Sensitivity
 * (0-100, higher = easier to trigger) scales that threshold; 100 turns the voice check off.
 * "Other" takes are not used as negatives: they are the same voice, so they can't separate it.
 */
public final class VoiceMatch {
    public static final float VERSION = 14f;
    public static final int CEPS = 13;
    public static final int DIM = 1 + 2 * CEPS; // [version, mean c1..c13, std c1..c13]
    private static final int FRAME = 512, HOP = 256, MELS = 26;
    private static final double GATE_DB = 20.0;     // speech frames: within 20 dB of the loudest frame
    private static final double NOISE_SUB = 2.0;    // spectral subtraction of the room-noise floor
    private static final double NOISE_MIN_DB = 10;  // only if the quiet frames are clearly below speech
    private static final double FLOOR_MS = 1e-9;  // digital-silence floor (mean square)

    private VoiceMatch() {}

    /** Result of a voice check, shown to the user when a wake is rejected. */
    public static final class Result {
        public final boolean accepted;
        public final boolean checked;      // false = no usable training / check disabled
        public final boolean needsRetrain; // prints are from an older Zippy
        public final double score;         // 0..100, higher = closer to the trained voice
        public final double needed;        // 0..100 score needed at this sensitivity
        Result(boolean accepted, boolean checked, boolean needsRetrain, double score, double needed) {
            this.accepted = accepted; this.checked = checked; this.needsRetrain = needsRetrain;
            this.score = score; this.needed = needed;
        }
        @Override public String toString() {
            return String.format(java.util.Locale.US, "Result(accepted=%s, checked=%s, retrain=%s, score=%.0f, needed=%.0f)",
                    accepted, checked, needsRetrain, score, needed);
        }
    }

    public static boolean isCurrent(float[] print) {
        return print != null && print.length == DIM && print[0] == VERSION;
    }

    public static boolean allCurrent(List<float[]> prints) {
        if (prints == null || prints.isEmpty()) return false;
        for (float[] p : prints) if (!isCurrent(p)) return false;
        return true;
    }

    // ------------------------------------------------------------------ fingerprint

    public static float[] fingerprint(float[] samples, int sampleRate) {
        float[] out = new float[DIM];
        out[0] = VERSION;
        if (samples == null || samples.length < FRAME) return out;
        // Mel power of every frame in the whole window (noise estimate uses the quiet frames)
        double[][] bank = melBank(sampleRate);
        int nAll = 1 + (samples.length - FRAME) / HOP;
        double[][] melAll = new double[nAll][];
        double[] eAll = new double[nAll];
        for (int f = 0; f < nAll; f++) {
            melAll[f] = melPower(samples, f * HOP, bank);
            double e = 0;
            for (int i = 0; i < FRAME; i++) { double v = samples[f * HOP + i]; e += v * v; }
            eAll[f] = e / FRAME;
        }
        double maxE = 0;
        for (double e : eAll) maxE = Math.max(maxE, e);
        if (maxE < FLOOR_MS) return out;
        // noise floor: average of the quietest 20% of frames, only if clearly below speech
        double[] noise = new double[MELS];
        Integer[] order = new Integer[nAll];
        for (int i = 0; i < nAll; i++) order[i] = i;
        Arrays.sort(order, (x, y) -> Double.compare(eAll[x], eAll[y]));
        int q = Math.max(1, nAll / 5);
        double qE = 0;
        for (int i = 0; i < q; i++) qE += eAll[order[i]];
        qE /= q;
        if (qE < maxE * Math.pow(10, -NOISE_MIN_DB / 10)) {
            for (int i = 0; i < q; i++) for (int m = 0; m < MELS; m++) noise[m] += melAll[order[i]][m] / q;
        }
        // speech frames: the loudest second, gated relative to its loudest frame
        int win = Math.min(nAll, Math.max(1, (int) (1.0 * sampleRate / HOP)));
        int bestStart = 0; double bestSum = -1;
        for (int st = 0; st + win <= nAll; st++) {
            double sum = 0;
            for (int f = st; f < st + win; f++) sum += eAll[f];
            if (sum > bestSum) { bestSum = sum; bestStart = st; }
        }
        double gate = Math.max(FLOOR_MS, maxE * Math.pow(10, -GATE_DB / 10));
        double[] sum = new double[CEPS], sq = new double[CEPS];
        double[] logMel = new double[MELS];
        int used = 0;
        for (int f = bestStart; f < bestStart + win; f++) {
            if (eAll[f] < gate) continue;
            for (int m = 0; m < MELS; m++) {
                double p = melAll[f][m];
                logMel[m] = Math.log(Math.max(p - NOISE_SUB * noise[m], p * 0.03) + 1e-12);
            }
            for (int c = 1; c <= CEPS; c++) {
                double v = 0;
                for (int m = 0; m < MELS; m++) v += logMel[m] * Math.cos(Math.PI * c * (m + 0.5) / MELS);
                v *= Math.sqrt(2.0 / MELS);
                sum[c - 1] += v; sq[c - 1] += v * v;
            }
            used++;
        }
        if (used == 0) return out;
        for (int c = 0; c < CEPS; c++) {
            double mean = sum[c] / used;
            double var = Math.max(0, sq[c] / used - mean * mean);
            out[1 + c] = (float) mean;
            out[1 + CEPS + c] = (float) Math.sqrt(var);
        }
        return out;
    }

    private static double[] melPower(float[] x, int off, double[][] bank) {
        float[] re = new float[FRAME], im = new float[FRAME];
        float prev = off > 0 ? x[off - 1] : x[off];
        for (int i = 0; i < FRAME; i++) {
            float cur = x[off + i];
            re[i] = (float) ((cur - 0.97f * prev) * (0.5 - 0.5 * Math.cos(2 * Math.PI * i / (FRAME - 1))));
            prev = cur;
        }
        VoiceMath.fft(re, im);
        double[] out = new double[MELS];
        for (int m = 0; m < MELS; m++) {
            double s = 0;
            double[] w = bank[m];
            for (int k = 0; k < w.length; k++) if (w[k] != 0) s += w[k] * (re[k] * (double) re[k] + im[k] * (double) im[k]);
            out[m] = s;
        }
        return out;
    }

    private static double[][] melBank(int sr) {
        double lo = mel(100), hi = mel(Math.min(7000, sr / 2.0 - 100));
        int bins = FRAME / 2 + 1;
        double[] edge = new double[MELS + 2];
        for (int i = 0; i < edge.length; i++) edge[i] = hz(lo + (hi - lo) * i / (MELS + 1));
        double[][] bank = new double[MELS][bins];
        for (int m = 0; m < MELS; m++) {
            double l = edge[m], c = edge[m + 1], h = edge[m + 2];
            for (int k = 0; k < bins; k++) {
                double f = k * (double) sr / FRAME;
                double w = f >= l && f <= c ? (f - l) / (c - l) : (f > c && f <= h ? (h - f) / (h - c) : 0);
                bank[m][k] = Math.max(0, w);
            }
        }
        return bank;
    }

    private static double mel(double hz) { return 2595 * Math.log10(1 + hz / 700); }
    private static double hz(double mel) { return 700 * (Math.pow(10, mel / 2595) - 1); }

    /** Loudest window of `seconds` (the wake phrase), so leading/trailing room noise is ignored. */
    static float[] loudest(float[] s, int sr, double seconds) {
        int win = Math.max(FRAME, (int) (seconds * sr));
        if (s.length <= win) return s;
        int step = Math.max(1, sr / 50);
        int best = 0; double bestE = -1;
        for (int i = 0; i + win <= s.length; i += step) {
            double e = 0;
            for (int k = i; k < i + win; k += 4) e += s[k] * (double) s[k];
            if (e > bestE) { bestE = e; best = i; }
        }
        return Arrays.copyOfRange(s, best, best + win);
    }

    // ------------------------------------------------------------------ matching

    /** Distance between two current-version prints (smaller = more alike). */
    public static double distance(float[] a, float[] b) {
        double d = 0;
        for (int i = 1; i < DIM; i++) {
            double w = i <= CEPS ? 1.0 : 0.5;  // cepstral means matter more than their spread
            double v = a[i] - b[i];
            d += w * v * v;
        }
        return Math.sqrt(d);
    }

    private static double nearest3(float[] live, List<float[]> takes, int skip) {
        List<Double> ds = new ArrayList<>();
        for (int i = 0; i < takes.size(); i++) if (i != skip) ds.add(distance(live, takes.get(i)));
        ds.sort(null);
        int n = Math.min(3, ds.size());
        double s = 0;
        for (int i = 0; i < n; i++) s += ds.get(i);
        return n == 0 ? Double.MAX_VALUE : s / n;
    }

    /**
     * How far the user's own takes typically are from each other (leave-one-out mean + std).
     * Floors keep a very consistent training set from producing a hair-trigger threshold.
     */
    static double[] spread(List<float[]> wakes) {
        int n = wakes.size();
        double[] d = new double[n];
        double mean = 0;
        for (int i = 0; i < n; i++) { d[i] = nearest3(wakes.get(i), wakes, i); mean += d[i]; }
        mean /= n;
        double var = 0;
        for (double v : d) var += (v - mean) * (v - mean);
        double std = Math.sqrt(var / n);
        return new double[]{Math.max(mean, 1.0), Math.max(std, 0.35)};
    }

    /** Allowed distance at a sensitivity (0 = strictest, 100 = check off). */
    static double allowed(double[] spread, int sensitivity) {
        int s = Math.max(0, Math.min(100, sensitivity));
        // 0: own-take mean + 1 std (strict) ... 99: ~2.4x mean + 8 std (very loose); 100 = off
        double k = 1.4 * s / 100.0;
        return spread[0] * (1.0 + k) + spread[1] * (1.0 + 5.0 * k);
    }

    /** Distance -> friendly 0..100 score where `allowed` maps to the needed score. */
    private static double toScore(double dist, double scale) {
        return 100.0 * Math.exp(-dist / scale);
    }

    public static Result check(float[] live, List<float[]> wakes, int sensitivity) {
        int s = Math.max(0, Math.min(100, sensitivity));
        if (wakes == null || wakes.size() < 3) return new Result(true, false, false, 100, 0);
        if (!allCurrent(wakes)) return new Result(true, false, true, 100, 0);
        if (s >= 100) return new Result(true, false, false, 100, 0);
        if (!isCurrent(live)) return new Result(true, false, false, 100, 0);
        boolean empty = true;
        for (int i = 1; i < DIM; i++) if (live[i] != 0) { empty = false; break; }
        if (empty) return new Result(false, true, false, 0, 0); // nothing audible in the window
        double[] sp = spread(wakes);
        double allowed = allowed(sp, s);
        double dist = nearest3(live, wakes, -1);
        double scale = sp[0] * 3.0;
        return new Result(dist <= allowed, true, false, toScore(dist, scale), toScore(allowed, scale));
    }

    /** Drop-in for the old Voice.matches(live, wakes, rejects, sensitivity). */
    public static boolean matches(float[] live, List<float[]> wakes, List<float[]> rejects, int sensitivity) {
        return check(live, wakes, sensitivity).accepted;
    }
}
