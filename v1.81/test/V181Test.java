import com.zippy.ear.*;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/** Zippy 1.81 logic: BpeTokenizer == sentencepiece, WakeTuner, WakeWord, VolumeKeys, Sensitivity warning. */
public class V181Test {
    static int pass = 0, fail = 0;
    static void ok(boolean c, String what) { if (c) { pass++; System.out.println("PASS " + what); } else { fail++; System.out.println("FAIL " + what); } }

    static final class MapKV implements ZippyPrefs.KV {
        final Map<String, Object> m = new HashMap<>();
        public boolean contains(String k) { return m.containsKey(k); }
        public String getString(String k, String d) { Object v = m.get(k); return v == null ? d : (String) v; }
        public boolean getBoolean(String k, boolean d) { Object v = m.get(k); return v == null ? d : (Boolean) v; }
        public int getInt(String k, int d) { Object v = m.get(k); return v == null ? d : (Integer) v; }
        public void putString(String k, String v) { m.put(k, v); }
        public void putBoolean(String k, boolean v) { m.put(k, v); }
        public void putInt(String k, int v) { m.put(k, v); }
    }

    /** Fake spotter: a take "fires" for a keyword line if its required ease (stored in audio[0]) is met. */
    static final class Fake implements WakeTuner.Detector {
        // audio[0] = threshold at or below which the canonical line fires; audio[1] = id for heard()
        final Map<Integer, String[]> heard = new HashMap<>();
        final Set<String> genericVariant = new HashSet<>();
        int calls;
        public int hits(String kw, float[] a) {
            calls++;
            int n = 0;
            for (String line : kw.split("/")) {
                String toks = line.substring(0, line.indexOf(" :"));
                double t = Double.parseDouble(line.substring(line.indexOf('#') + 1, line.indexOf(" @")));
                boolean other = a[1] < 0;
                if (other) { if (genericVariant.contains(toks) || t <= -a[0]) n++; }
                else if (t <= a[0] + (toks.equals("\u2581HA Z I PP I") ? 0.10 : 0)) n++;
            }
            return n > 0 ? 1 : 0;
        }
        public String[] heard(float[] a) { return heard.getOrDefault((int) a[1], new String[0]); }
    }

    public static void main(String[] args) throws Exception {
        String dir = args[0];
        // ---- BpeTokenizer vs sentencepiece
        BpeTokenizer tok = BpeTokenizer.load(new FileInputStream(dir + "/../assets/kws/bpe.vocab"));
        int same = 0, total = 0; String firstBad = null;
        for (String line : Files.readAllLines(Paths.get(dir, "bpe_expected.tsv"), StandardCharsets.UTF_8)) {
            String[] p = line.split("\t");
            String[] got = tok.encode(p[0]);
            total++;
            if (got != null && String.join(" ", got).equals(p[1])) same++; else if (firstBad == null) firstBad = p[0] + " -> " + (got == null ? null : String.join(" ", got)) + " vs " + p[1];
        }
        ok(same == total && total >= 300, "BpeTokenizer matches sentencepiece on " + same + "/" + total + " phrases" + (firstBad == null ? "" : " (first diff: " + firstBad + ")"));
        ok(String.join(" ", tok.encode("hey, zippy!")).equals("\u2581HE Y \u2581 Z I PP Y"), "punctuation/case ignored: 'hey, zippy!' = HEY ZIPPY tokens (same as KwsTuning's first line)");
        ok(tok.encode("123") == null && tok.encode("") == null, "nothing to spell -> null");

        // ---- WakeTuner pure parts
        ok(WakeTuner.boostFor(0.45) == 1.0 && WakeTuner.boostFor(0.05) == 2.0 && WakeTuner.boostFor(0.25) == 1.5, "boost tied to threshold like KwsTuning (0.45->1.0, 0.25->1.5, 0.05->2.0)");
        boolean mono = true;
        for (int s = 0; s < 100; s++) if (WakeTuner.runtimeThreshold(0.30, s + 1) > WakeTuner.runtimeThreshold(0.30, s)) mono = false;
        ok(mono && WakeTuner.runtimeThreshold(0.30, 55) == 0.25, "runtime threshold only gets easier as Sensitivity rises; 55 = tuned - 0.05");
        ok(WakeTuner.runtimeThreshold(0.05, 100) == WakeTuner.T_MIN && WakeTuner.runtimeThreshold(0.60, 0) == WakeTuner.T_MAX, "runtime threshold clamped to [0.03, 0.70]");
        String[] canon = tok.encode("Hey Zippy");
        String[] heard1 = "\u2581OKAY \u2581HA Z I PP I".split(" ");
        String[] closest = WakeTuner.closest(heard1, canon);
        ok(closest != null && String.join(" ", closest).equals("\u2581HA Z I PP I"), "learn: picks the closest window of whole words (HAZIPPI out of 'OKAY HAZIPPI')");
        ok(WakeTuner.closest("\u2581PA Y S".split(" "), canon) == null, "learn: ignores far readings ('PAYS')");
        List<String[]> hs = Arrays.asList(heard1, "\u2581HE Z I PP I".split(" "), "\u2581HA Z I PP I".split(" "), canon);
        List<String[]> learned = WakeTuner.learn(canon, hs);
        ok(learned.size() == 2 && String.join(" ", learned.get(0)).equals("\u2581HA Z I PP I"), "learn: most frequent first, the plain spelling isn't repeated");

        Fake f = new Fake();
        f.heard.put(1, "\u2581HA Z I PP I".split(" "));
        List<float[]> takes = new ArrayList<>();
        double[] need = {0.45, 0.42, 0.33, 0.47, 0.50, 0.38}; // the canonical line fires at t <= need
        for (int i = 0; i < need.length; i++) takes.add(new float[]{(float) need[i], 1});
        List<float[]> others = new ArrayList<>();
        others.add(new float[]{0.0f, -1}); // never fires on the canonical line above t=0
        WakeTuner.Result r = WakeTuner.tune(f, canon, takes, others);
        ok(r.learned == 1 && r.threshold == WakeTuner.T_CAP && r.allTakes() && r.othersHit() == 0,
                "tune: strictest all-takes threshold (0.40 with the learned HAZIPPI) capped at T_CAP 0.30, ordinary speech clean (got " + r.threshold + ", learned " + r.learned + ")");
        f.genericVariant.add("\u2581HA Z I PP I");
        r = WakeTuner.tune(f, canon, takes, others);
        ok(r.dropped == 1 && r.learned == 0 && r.threshold == 0.30, "tune: a learned pronunciation that wakes on ordinary speech is dropped and the threshold re-tuned (0.30)");
        List<float[]> takes2 = new ArrayList<>();
        for (double n : new double[]{0.45, 0.42, 0.47, 0.50, 0.38, 0.41}) takes2.add(new float[]{(float) n, 1});
        List<float[]> others2 = new ArrayList<>();
        others2.add(new float[]{-0.27f, -1}); // ordinary speech that fires at the capped runtime threshold 0.25
        r = WakeTuner.tune(new Fake(), canon, takes2, others2);
        ok(r.threshold == 0.35 && r.allTakes() && r.othersHit() == 0, "tune: the cap is lifted when ordinary speech still wakes it (0.30 -> 0.35, every take kept; got " + r.threshold + ")");
        List<float[]> takes3 = new ArrayList<>();
        for (double n : new double[]{0.45, 0.42, 0.47, 0.50, 0.38, -1}) takes3.add(new float[]{(float) n, 1}); // last take never wakes it
        r = WakeTuner.tune(new Fake(), canon, takes3, others);
        ok(r.threshold == 0.30 && r.takesHit() == 5 && !r.allTakes() && r.canSave(), "tune: one outlier take is allowed instead of dropping to the loosest threshold (got " + r.threshold + ", " + r.takesHit() + "/6)");
        List<float[]> takes4 = new ArrayList<>();
        for (double n : new double[]{0.45, 0.42, 0.47, 0.50, -1, -1}) takes4.add(new float[]{(float) n, 1});
        r = WakeTuner.tune(new Fake(), canon, takes4, others);
        ok(r.takesHit() == 4 && !r.canSave(), "tune: two missing takes can't be saved (re-record)");
        String kw = WakeTuner.lines(r.variants, 0.25);
        ok(kw.equals("\u2581HE Y \u2581 Z I PP Y :1.50 #0.250 @CUSTOM"), "keyword line format: tokens :boost #threshold @CUSTOM");

        // ---- WakeWord
        MapKV kv = new MapKV();
        WakeWord w = new WakeWord(kv);
        ok(!w.custom() && w.phrase().equals("Hey Zippy") && w.keywords(55).equals(KwsTuning.keywords(55)), "default: Hey Zippy with the unchanged 1.7/1.8 Sensitivity table");
        int g0 = WakeWord.generation;
        w.save("  Hey   Jarvis ", WakeTuner.tune(new Fake(), tok.encode("Hey Jarvis"), takes, others), new ArrayList<>());
        ok(w.custom() && w.phrase().equals("Hey Jarvis") && WakeWord.generation == g0 + 1, "save: custom phrase active, tidied, generation bumped (listener swaps keywords)");
        ok(w.keywords(55).startsWith("\u2581HE Y \u2581JA R VI S :") && w.keywords(55).contains("@CUSTOM") && w.takes() == 6, "custom keywords use the phrase's tokens");
        ok(!w.keywords(90).equals(w.keywords(30)), "custom keywords follow the Sensitivity slider");
        w.useBuiltin();
        ok(!w.custom() && w.keywords(55).equals(KwsTuning.keywords(55)), "Use Hey Zippy: back to the built-in table");
        ok(WakeWord.problem("Hey Jarvis") == null && WakeWord.problem("") != null && WakeWord.problem("R2D2") != null
                && WakeWord.problem("one two three four five six") != null, "phrase rules: letters only, 1-5 words");
        ok(!WakeWord.advice(tok.encode("Hey")).isEmpty() && WakeWord.advice(tok.encode("Hey Jarvis")).isEmpty(), "short-phrase advice");
        ok(WakeWord.stripLead("Hey Jarvis, what time is it", "Hey Jarvis").equals("what time is it"), "task: a repeated phrase is cut");
        ok(WakeWord.stripLead("Jarvis what time is it", "Hey Jarvis").equals("what time is it"), "task: the phrase's tail is cut");
        ok(WakeWord.stripLead("hey jervis add milk", "Hey Jarvis").equals("add milk"), "task: fuzzy match (jervis)");
        ok(WakeWord.stripLead("add milk to the list", "Hey Jarvis").equals("add milk to the list"), "task: untouched without the phrase");
        ok(WakeWord.stripLead("hey can you add milk", "Hey Jarvis").equals("hey can you add milk"), "task: a lone 'hey' is not the phrase");

        // ---- VolumeKeys
        VolumeKeys v = new VolumeKeys();
        ok(v.onVolumeDown(true, 0, 1000) == VolumeKeys.Verdict.PASS && v.onVolumeDown(false, 0, 1080) == VolumeKeys.Verdict.PASS, "single press passes through (volume works)");
        ok(v.onVolumeDown(true, 0, 1300) == VolumeKeys.Verdict.TOGGLE, "second press within 400 ms toggles");
        ok(v.onVolumeDown(true, 1, 1800) == VolumeKeys.Verdict.CONSUME && v.onVolumeDown(false, 0, 1850) == VolumeKeys.Verdict.CONSUME, "the toggling press's repeats and key-up are consumed");
        ok(v.onVolumeDown(true, 0, 2000) == VolumeKeys.Verdict.PASS, "the next press starts over (passes)");
        v = new VolumeKeys();
        v.onVolumeDown(true, 0, 0); v.onVolumeDown(false, 0, 60);
        ok(v.onVolumeDown(true, 0, 450) == VolumeKeys.Verdict.PASS, "presses 450 ms apart are two normal presses");
        v = new VolumeKeys();
        v.onVolumeDown(true, 0, 0);
        ok(v.onVolumeDown(true, 1, 300) == VolumeKeys.Verdict.PASS && v.onVolumeDown(true, 2, 350) == VolumeKeys.Verdict.PASS
                && v.onVolumeDown(false, 0, 900) == VolumeKeys.Verdict.PASS && v.onVolumeDown(true, 0, 1000) == VolumeKeys.Verdict.PASS, "holding volume down ramps normally and never toggles");
        v = new VolumeKeys();
        v.onVolumeDown(true, 0, 0); v.onVolumeDown(false, 0, 50); v.otherKey();
        ok(v.onVolumeDown(true, 0, 200) == VolumeKeys.Verdict.PASS, "another key in between cancels the double-press");

        // ---- Sensitivity
        ok(Sensitivity.DEFAULT == 55 && Sensitivity.WARN_ABOVE == 80 && Sensitivity.WARNING.contains("Above 80"), "default unchanged (55); warning above 80");

        System.out.println(fail == 0 ? "ALL " + pass + " V181 TESTS PASSED" : fail + " FAILED, " + pass + " passed");
        System.exit(fail == 0 ? 0 : 1);
    }
}
