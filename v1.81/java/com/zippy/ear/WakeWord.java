package com.zippy.ear;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;

/**
 * Zippy 1.81: which wake word Zippy listens for. "Hey Zippy" (the built-in, Sensitivity-driven table
 * in KwsTuning, unchanged from 1.7/1.8) or a phrase Joshua typed and trained (PhraseActivity +
 * WakeTuner): its tokens, learned pronunciations, tuned threshold and voice prints of his takes.
 * Stored in the same "zippy" SharedPreferences. Pure logic over ZippyPrefs.KV, unit-tested.
 */
public final class WakeWord {
    public static final String BUILTIN = "builtin", CUSTOM = "custom", DEFAULT_PHRASE = "Hey Zippy";
    static final String K_MODE = "wakeMode", K_PHRASE = "wakePhrase", K_VARIANTS = "wakeVariants",
            K_THRESH = "wakeThreshold", K_PRINTS = "wakePrints", K_TAKES = "wakeTakes", K_AT = "wakeTrainedAt";
    public static final int MAX_CHARS = 40, MAX_WORDS = 5;
    /** Bumped on every save so the listener swaps keywords without re-reading prefs per chunk. */
    public static volatile int generation;

    private final ZippyPrefs.KV kv;
    public WakeWord(ZippyPrefs.KV kv) { this.kv = kv; }
    public static WakeWord of(android.content.Context c) { return new WakeWord(ZippyPrefs.kvOf(c)); }

    public boolean custom() { return CUSTOM.equals(kv.getString(K_MODE, BUILTIN)) && !variants().isEmpty(); }
    public String phrase() { return custom() ? kv.getString(K_PHRASE, DEFAULT_PHRASE) : DEFAULT_PHRASE; }
    public double tunedThreshold() {
        try { return Double.parseDouble(kv.getString(K_THRESH, "0.25")); } catch (Exception e) { return 0.25; }
    }
    public int takes() { return kv.getInt(K_TAKES, 0); }

    /** Token lines; the first one is the phrase's own spelling. */
    public List<String[]> variants() {
        List<String[]> out = new ArrayList<>();
        String s = kv.getString(K_VARIANTS, "");
        if (s == null || s.isEmpty()) return out;
        for (String v : s.split("\\|")) if (!v.trim().isEmpty()) out.add(v.trim().split(" "));
        return out;
    }

    /** Keywords for the Sherpa-ONNX stream at this Sensitivity. */
    public String keywords(int sens) {
        if (!custom()) return KwsTuning.keywords(sens);
        return WakeTuner.lines(variants(), WakeTuner.runtimeThreshold(tunedThreshold(), sens));
    }

    public List<float[]> prints() {
        List<float[]> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(kv.getString(K_PRINTS, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONArray p = a.getJSONArray(i);
                float[] f = new float[p.length()];
                for (int j = 0; j < f.length; j++) f[j] = (float) p.getDouble(j);
                out.add(f);
            }
        } catch (Exception ignored) { }
        return out;
    }

    public void save(String phrase, WakeTuner.Result r, List<float[]> prints) {
        StringBuilder v = new StringBuilder();
        for (String[] t : r.variants) { if (v.length() > 0) v.append('|'); v.append(String.join(" ", t)); }
        StringBuilder p = new StringBuilder("[");
        for (int i = 0; i < prints.size(); i++) {
            if (i > 0) p.append(',');
            p.append('[');
            float[] f = prints.get(i);
            for (int j = 0; j < f.length; j++) { if (j > 0) p.append(','); p.append(Float.isFinite(f[j]) ? Float.toString(f[j]) : "0"); }
            p.append(']');
        }
        p.append(']');
        kv.putString(K_PHRASE, tidy(phrase));
        kv.putString(K_VARIANTS, v.toString());
        kv.putString(K_THRESH, String.format(Locale.US, "%.2f", r.threshold));
        kv.putString(K_PRINTS, p.toString());
        kv.putInt(K_TAKES, r.takeHit.length);
        kv.putString(K_AT, Long.toString(System.currentTimeMillis()));
        kv.putString(K_MODE, CUSTOM);
        generation++;
    }

    public void useBuiltin() { kv.putString(K_MODE, BUILTIN); generation++; }

    /** Phrase as shown: collapsed spaces, first letters kept as typed. */
    public static String tidy(String p) { return p == null ? "" : p.replaceAll("\\s+", " ").trim(); }

    /** Why a typed phrase can't be used (null = fine). */
    public static String problem(String phrase) {
        String t = tidy(phrase);
        String c = BpeTokenizer.clean(t);
        if (c.isEmpty()) return "Type a wake phrase (letters only).";
        if (t.length() > MAX_CHARS) return "Keep it under " + MAX_CHARS + " characters.";
        if (c.split(" ").length > MAX_WORDS) return "Use at most " + MAX_WORDS + " words.";
        if (!t.matches("[A-Za-z' ,.!?\\-]+"))
            return "Spell it with letters only (write numbers as words).";
        return null;
    }

    /** Gentle note for short phrases (more false wakes). */
    public static String advice(String[] tokens) {
        if (tokens == null) return "";
        int letters = WakeTuner.letters(tokens).length();
        if (letters < 6 || tokens.length < 4)
            return "Short phrases wake by accident more often. Two or three words with distinct sounds (like \u201cHey Zippy\u201d) work best.";
        return "";
    }

    // ------------------------------------------------------------------ the task text

    /** Drops a (fuzzy) repeat of the wake phrase, or its tail, from the start of the task. */
    public static String stripLead(String task, String phrase) {
        if (task == null) return "";
        String[] t = task.trim().isEmpty() ? new String[0] : task.trim().split("\\s+");
        String[] p = BpeTokenizer.clean(phrase).toLowerCase(Locale.US).split(" ");
        if (t.length == 0 || p.length == 0 || p[0].isEmpty()) return task.trim();
        int bestCut = 0;
        for (int start = 0; start < p.length; start++) { // the whole phrase, or only its last words
            int k = 0;
            while (start + k < p.length && k < t.length && close(norm(t[k]), p[start + k])) k++;
            if (start + k == p.length && k > bestCut) bestCut = k;
        }
        StringBuilder b = new StringBuilder();
        for (int i = bestCut; i < t.length; i++) { if (b.length() > 0) b.append(' '); b.append(t[i]); }
        String s = b.toString().trim();
        while (!s.isEmpty() && ",.;:!?-\u2014".indexOf(s.charAt(0)) >= 0) s = s.substring(1).trim();
        return s;
    }

    static String norm(String w) { return w.toLowerCase(Locale.US).replaceAll("[^a-z']", ""); }

    static boolean close(String a, String b) {
        if (a.isEmpty()) return false;
        if (a.equals(b)) return true;
        int lim = Math.max(a.length(), b.length()) >= 5 ? 2 : 1;
        return WakeTuner.edit(a, b) <= lim;
    }
}
