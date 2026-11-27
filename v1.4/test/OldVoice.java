import java.util.*;

/** Faithful port of Zippy 1.2/1.3 Voice.fingerprint + Voice.matches (from the shipped smali), for diagnosis. */
public final class OldVoice {
    static float[] loudest(float[] s, int sr, double sec) {
        int win = Math.max(512, (int) (sec * sr));
        if (s.length <= win) return s;
        int step = Math.max((int) (sr * 0.08), 1);
        int best = 0; double bestE = -1;
        for (int i = 0; i + win <= s.length; i += step) {
            double e = 0; for (int k = i; k < i + win; k += 8) e += s[k] * (double) s[k];
            if (e > bestE) { bestE = e; best = i; }
        }
        return Arrays.copyOfRange(s, best, best + win);
    }
    static double hz2mel(double hz) { return 2595 * Math.log10(1 + hz / 700); }
    static double mel2hz(double m) { return 700 * (Math.pow(10, m / 2595) - 1); }

    public static float[] fingerprint(float[] samples, int sr) {
        float[] x = loudest(samples, sr, 0.9);
        float[] acc = new float[24];
        double lo = hz2mel(80), hi = hz2mel(Math.min(4000, sr / 2.0 - 100));
        double[] edges = new double[26];
        for (int i = 0; i < 26; i++) edges[i] = mel2hz(lo + (hi - lo) * i / 25.0);
        float[] re = new float[512], im = new float[512];
        int frames = 0;
        for (int off = 0; off + 512 <= x.length && frames < 40; off += 256) {
            double e = 0; for (int i = 0; i < 512; i++) e += x[off + i] * (double) x[off + i];
            if (e / 512 < 4.0e-4) continue;
            for (int i = 0; i < 512; i++) { re[i] = (float) (x[off + i] * (1 - Math.cos(i * 2 * Math.PI / 511)) * 0.5); im[i] = 0; }
            com.zippy.ear.VoiceMath.fft(re, im);
            for (int b = 0; b < 24; b++) {
                double l = edges[b], c = edges[b + 1], h = edges[b + 2];
                int k0 = Math.max(1, (int) Math.floor(l * 512 / sr)), k1 = Math.min(255, (int) Math.ceil(h * 512 / sr));
                double s = 0, ws = 0;
                for (int k = k0; k <= k1; k++) {
                    double f = k * (double) sr / 512, w;
                    if (f >= l && f <= c && c > l) w = (f - l) / (c - l);
                    else if (f > c && f <= h && h > c) w = (h - f) / (h - c);
                    else w = 0;
                    if (w > 0) { s += Math.hypot(re[k], im[k]) * w; ws += w; }
                }
                acc[b] += (float) (ws > 0 ? s / ws : 0);
            }
            frames++;
        }
        float[] out = new float[24];
        if (frames == 0) return out;
        double n = 0;
        for (int b = 0; b < 24; b++) { double v = Math.log(acc[b] / frames + 1.0); out[b] = (float) v; n += v * v; }
        n = Math.sqrt(n);
        if (n < 1e-8) return new float[24];
        for (int b = 0; b < 24; b++) out[b] = (float) (out[b] / n);
        return out;
    }

    public static double cos(float[] a, float[] b) {
        double d = 0, x = 0, y = 0; int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) { d += a[i] * (double) b[i]; x += a[i] * (double) a[i]; y += b[i] * (double) b[i]; }
        return (x < 1e-8 || y < 1e-8) ? 0 : d / Math.sqrt(x * y);
    }

    /** returns {wakeScore, bestReject, threshold} */
    public static double[] scores(float[] live, List<float[]> wakes, List<float[]> rejects, int sens) {
        List<Double> c = new ArrayList<>(); for (float[] w : wakes) c.add(cos(live, w));
        c.sort(Collections.reverseOrder());
        double top = 0; int n = Math.min(3, c.size()); for (int i = 0; i < n; i++) top += c.get(i); top /= n;
        double rej = 0; for (float[] r : rejects) rej = Math.max(rej, cos(live, r));
        return new double[]{top, rej, 0.5 + Math.max(0, Math.min(100, sens)) / 100.0 * 0.35};
    }

    public static boolean matches(float[] live, List<float[]> wakes, List<float[]> rejects, int sens) {
        if (wakes.size() < 3) return true;
        double[] s = scores(live, wakes, rejects, sens);
        return s[0] >= s[2] && s[0] >= s[1] + 0.03;
    }
}
