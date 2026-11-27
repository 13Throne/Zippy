import com.zippy.ear.VoiceMatch;
import java.io.File;
import java.util.*;

/**
 * Voice check regression test on a synthetic 6-speaker set (make_voices.sh):
 * 10 training takes like TrainActivity, 6 live wakes per speaker at different level / noise /
 * reverb, cut exactly like ListenService's ring window.
 */
public class VoiceMatchTest {
    static int fails = 0;
    static void check(boolean ok, String what) {
        System.out.println((ok ? "PASS " : "FAIL ") + what);
        if (!ok) fails++;
    }

    public static void main(String[] a) throws Exception {
        File[] sps = Objects.requireNonNull(new File(a[0]).listFiles(File::isDirectory));
        Arrays.sort(sps);
        Map<String, List<float[]>> wakes = new LinkedHashMap<>(), lives = new LinkedHashMap<>();
        Map<String, List<float[]>> oldW = new LinkedHashMap<>(), oldR = new LinkedHashMap<>(), oldL = new LinkedHashMap<>();
        for (File sp : sps) {
            List<float[]> w = new ArrayList<>(), l = new ArrayList<>(), ow = new ArrayList<>(), or = new ArrayList<>(), ol = new ArrayList<>();
            for (int i = 0; i < 10; i++) { float[] x = Wav.read(new File(sp, "wake_" + i + ".wav")); w.add(VoiceMatch.fingerprint(x, 16000)); ow.add(OldVoice.fingerprint(x, 16000)); }
            for (int i = 0; i < 5; i++) or.add(OldVoice.fingerprint(Wav.read(new File(sp, "reject_" + i + ".wav")), 16000));
            for (int i = 0; i < 6; i++) { float[] x = Wav.read(new File(sp, "live_" + i + ".wav")); l.add(VoiceMatch.fingerprint(x, 16000)); ol.add(OldVoice.fingerprint(x, 16000)); }
            wakes.put(sp.getName(), w); lives.put(sp.getName(), l);
            oldW.put(sp.getName(), ow); oldR.put(sp.getName(), or); oldL.put(sp.getName(), ol);
        }

        // Root cause, reproduced: 1.3 rejects the trained speaker even at Sensitivity 0
        int oldOwn = 0, oldN = 0;
        for (String sp : oldW.keySet()) for (float[] l : oldL.get(sp)) { oldN++; if (OldVoice.matches(l, oldW.get(sp), oldR.get(sp), 0)) oldOwn++; }
        System.out.printf("1.3 voice check, own voice at Sensitivity 0: %d/%d accepted%n", oldOwn, oldN);
        check(oldOwn * 4 < oldN, "1.3 algorithm reproduces the bug (rejects most of the trained speaker's wakes at Sensitivity 0)");

        int[] sens = {0, 10, 25, 40, 55, 70, 85, 99, 100};
        Map<Integer, int[]> rate = new LinkedHashMap<>();
        boolean monotonic = true;
        for (String sp : wakes.keySet()) for (String lv : lives.keySet()) for (float[] live : lives.get(lv)) {
            boolean prev = false;
            for (int s : sens) {
                boolean ok = VoiceMatch.check(live, wakes.get(sp), s).accepted;
                if (prev && !ok) monotonic = false;
                prev = ok;
                int[] r = rate.computeIfAbsent(s, k -> new int[4]);
                if (sp.equals(lv)) { r[1]++; if (ok) r[0]++; } else { r[3]++; if (ok) r[2]++; }
            }
        }
        for (int s : sens) {
            int[] r = rate.get(s);
            System.out.printf("  sensitivity %3d: own voice %2d/%d accepted, other voices %3d/%d accepted%n", s, r[0], r[1], r[2], r[3]);
        }
        check(monotonic, "raising Sensitivity never turns an accept into a reject (direction: higher = easier)");
        int[] d = rate.get(55);
        check(d[0] >= 0.85 * d[1], "default Sensitivity 55 accepts >= 85% of the trained speaker's live wakes");
        check(d[2] <= 0.15 * d[3], "default Sensitivity 55 accepts <= 15% of other speakers");
        int[] easy = rate.get(85);
        check(easy[0] >= 0.9 * easy[1], "Sensitivity 85 accepts >= 90% of the trained speaker");
        check(rate.get(100)[0] == rate.get(100)[1] && rate.get(100)[2] == rate.get(100)[3], "Sensitivity 100 turns the voice check off");
        int[] strict = rate.get(0);
        check(strict[2] == 0, "Sensitivity 0 accepts no other speaker");

        // quiet / far: old fixed energy gate emptied the print; new one must not
        float[] far = VoiceMatch.fingerprint(scale(Wav.read(new File(sps[0], "live_0.wav")), 0.05f), 16000);
        boolean nonEmpty = false; for (int i = 1; i < far.length; i++) if (far[i] != 0) nonEmpty = true;
        check(nonEmpty, "a wake 26 dB quieter still yields a usable voice print");
        check(VoiceMatch.check(far, wakes.get(sps[0].getName()), 55).accepted, "...and it still matches the same speaker at default Sensitivity");

        // old (1.3) prints -> no lock-out, ask to retrain
        VoiceMatch.Result r = VoiceMatch.check(lives.get(sps[0].getName()).get(0), oldW.get(sps[0].getName()), 0);
        check(r.accepted && r.needsRetrain, "1.3-format prints are detected: accepted + 'retrain needed' instead of rejecting");
        check(VoiceMatch.check(lives.get(sps[0].getName()).get(0), new ArrayList<>(), 0).accepted, "untrained -> any voice accepted");
        System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILED");
        if (fails > 0) System.exit(1);
    }

    static float[] scale(float[] x, float g) { float[] y = x.clone(); for (int i = 0; i < y.length; i++) y[i] *= g; return y; }
}
