package com.zippy.ear;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Zippy 1.8: from what the recognizers heard to the task that gets sent. Pure logic.
 *
 * Offline (Vosk) hears the recording from ~2 s before the wake word, with word times: everything up
 * to the wake word is dropped (the last "Zippy"-like word near the wake moment, and any word that had
 * ended before it), then Liora's echo (EchoGuard.stripTimed), then leading fillers.
 * Android speech hears from just before the wake moment (no times): a leading "Zippy" remnant and
 * fillers are dropped, then a leading echo of the greeting (EchoGuard.stripLeading).
 * The original words (and Android's capitals and punctuation) are kept.
 */
public final class TaskClean {
    private TaskClean() {}

    /** Bits of "Zippy" and hesitations that are never a task's first word. */
    static final Set<String> FILLER = new HashSet<>(Arrays.asList(
            "uh", "um", "umm", "uhm", "er", "erm", "ah", "hm", "hmm", "huh", "oh",
            "p", "pee", "pea", "e", "ee", "b", "be"));
    static final Set<String> WAKE_LEAD = new HashSet<>(Arrays.asList("hey", "hay", "a", "hi", "okay", "ok", "the"));
    static final Set<String> ZIPPY = new HashSet<>(Arrays.asList(
            "zippy", "zippi", "zippie", "zippee", "zipi", "zipy", "sippy", "sippi", "sippie",
            "zappy", "zappi", "skippy", "hippie", "hippy"));

    static boolean zippyLike(String word) {
        String w = WakePhrase.normalize(word);
        if (w.isEmpty() || w.contains(" ")) return false;
        if (ZIPPY.contains(w)) return true;
        if (w.length() >= 4 && w.length() <= 7 && WakePhrase.levenshtein(w, "zippy") <= 1) return true;
        return w.length() <= 7 && (w.endsWith("ippy") || w.endsWith("ippi") || w.endsWith("ippie"));
    }

    static boolean filler(String word) { return FILLER.contains(WakePhrase.normalize(word)); }

    /** A lone short word or a filler is noise, not a task. */
    public static boolean longEnough(String task) {
        String n = WakePhrase.normalize(task);
        if (n.isEmpty()) return false;
        if (n.indexOf(' ') > 0) return true;
        return n.length() >= 3 && !FILLER.contains(n) && !zippyLike(n);
    }

    // ------------------------------------------------------------------ offline words with times

    /**
     * @param hitSec     wake-word moment in the recognizer's timeline (seconds)
     * @param greeting   Liora's words if she spoke, else null; greetStart/End in the same timeline
     */
    public static String fromWords(List<VoskWords.Word> ws, double hitSec, String greeting,
                                   double greetStart, double greetEnd, int sens) {
        int cutWake = 0, cutTime = 0;
        for (int i = 0; i < ws.size(); i++) {
            VoskWords.Word w = ws.get(i);
            if (w.start < hitSec + 0.35 && (zippyLike(w.text) || WakePhrase.normalize(w.text).equals("zip"))) cutWake = i + 1;
            if (w.end < hitSec - 0.05) cutTime = i + 1;
        }
        List<VoskWords.Word> rest = new ArrayList<>(ws.subList(Math.max(cutWake, cutTime), ws.size()));
        if (greeting != null) rest = EchoGuard.stripTimed(rest, greeting, greetStart, greetEnd);
        String[] t = new String[rest.size()];
        for (int i = 0; i < t.length; i++) t[i] = rest.get(i).text;
        return finish(t, lead(t, 0, sens));
    }

    // ------------------------------------------------------------------ Android speech text

    public static String fromText(String text, String greeting, int sens) {
        if (text == null) return "";
        String[] t = text.trim().isEmpty() ? new String[0] : text.trim().split("\\s+");
        int i = lead(t, 0, sens);
        if (greeting != null) {
            int j = EchoGuard.stripLeading(t, i, greeting);
            if (j > i) i = lead(t, j, sens);
        }
        return finish(t, i);
    }

    /** Skips fillers, "Zippy" bits and a (repeated) wake phrase at the front. */
    static int lead(String[] t, int i, int sens) {
        int guard = 0;
        while (i < t.length && guard++ < 8) {
            String w = WakePhrase.normalize(t[i]);
            if (w.isEmpty() || filler(w) || zippyLike(w)) { i++; continue; }
            if ((w.equals("zip") || w.equals("zipp")) && i + 1 < t.length && filler(t[i + 1])) { i += 2; continue; }
            if (i + 1 < t.length && WAKE_LEAD.contains(w)) {
                String nx = WakePhrase.normalize(t[i + 1]);
                if (zippyLike(nx) || WakePhrase.isWake(w + " " + nx, sens)) { i += 2; continue; }
            }
            break;
        }
        return i;
    }

    static String finish(String[] t, int from) {
        StringBuilder b = new StringBuilder();
        for (int i = from; i < t.length; i++) { if (b.length() > 0) b.append(' '); b.append(t[i]); }
        String s = b.toString().trim();
        while (!s.isEmpty() && ",.;:!?-\u2014".indexOf(s.charAt(0)) >= 0) s = s.substring(1).trim();
        return longEnough(s) ? s : "";
    }

    static int words(String s) { String n = WakePhrase.normalize(s); return n.isEmpty() ? 0 : n.split(" ").length; }

    // ------------------------------------------------------------------ which one gets sent

    public static final class Pick {
        public final String text, source, why;
        Pick(String text, String source, String why) { this.text = text; this.source = source; this.why = why; }
    }

    public static final String ANDROID = "Android speech", OFFLINE = "offline (Vosk)";

    /**
     * Android speech wins when it has the task, unless it stopped early (it ended more than 1 s before
     * you did) or has clearly fewer words than the offline recognizer heard in the same audio.
     */
    /**
     * Words the offline recognizer heard AFTER the last word both recognizers agree on, when Android
     * speech ran out right there (at most one word after the match). Empty if nothing is missing.
     * Aligns the two by a longest-common-subsequence over fuzzy-equal words, so repeats don't confuse it.
     */
    public static String missingTail(String android, String offline) {
        String[] ao = android.trim().isEmpty() ? new String[0] : android.trim().split("\\s+");
        String[] vo = offline.trim().isEmpty() ? new String[0] : offline.trim().split("\\s+");
        String[] a = new String[ao.length], v = new String[vo.length];
        for (int i = 0; i < ao.length; i++) a[i] = EchoGuard.tok(ao[i]);
        for (int j = 0; j < vo.length; j++) v[j] = EchoGuard.tok(vo[j]);
        int n = a.length, m = v.length;
        if (n == 0 || m == 0) return "";
        int[][] L = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--)
            for (int j = m - 1; j >= 0; j--)
                L[i][j] = same(a[i], v[j]) ? L[i + 1][j + 1] + 1 : Math.max(L[i + 1][j], L[i][j + 1]);
        int i = 0, j = 0, lastA = -1, lastV = -1;
        while (i < n && j < m) {
            if (same(a[i], v[j]) && L[i][j] == L[i + 1][j + 1] + 1) { lastA = i; lastV = j; i++; j++; }
            else if (L[i + 1][j] >= L[i][j + 1]) i++; else j++;
        }
        if (lastA < 0 || L[0][0] < 3) return "";                  // they don't agree on enough to splice
        int afterA = 0;
        for (int k = lastA + 1; k < n; k++) if (!a[k].isEmpty()) afterA++;
        if (afterA > 1) return "";
        List<String> tail = new ArrayList<>();
        for (int k = lastV + 1; k < m; k++) if (!v[k].isEmpty()) tail.add(vo[k]);
        while (!tail.isEmpty() && FILLER.contains(EchoGuard.tok(tail.get(tail.size() - 1)))) tail.remove(tail.size() - 1);
        if (afterA == 1 && !tail.isEmpty() && same(a[n - 1], EchoGuard.tok(tail.get(0)))) tail.remove(0);
        if (tail.isEmpty() || (tail.size() == 1 && EchoGuard.tok(tail.get(0)).length() < 4)) return "";
        return String.join(" ", tail);
    }

    private static boolean same(String x, String y) {
        if (x.isEmpty() || y.isEmpty()) return false;
        return x.equals(y) || EchoGuard.near(x, y);
    }

    private static String lastWords(String s, int k) {
        String[] w = s.trim().split("\\s+");
        return String.join(" ", Arrays.copyOfRange(w, Math.max(0, w.length - k), w.length));
    }

    public static Pick choose(String android, String offline, boolean androidEndedEarly) {
        String a = android == null ? "" : android, v = offline == null ? "" : offline;
        int aw = words(a), vw = words(v);
        if (!a.isEmpty()) {
            if (androidEndedEarly && !v.isEmpty()) return new Pick(v, OFFLINE, "Android speech stopped before you did");
            if (vw >= 5 && aw < 0.6 * vw) return new Pick(v, OFFLINE, "Android speech heard " + aw + " words, offline heard " + vw);
            String tail = missingTail(a, v);
            if (!tail.isEmpty()) return new Pick(a.replaceAll("[\\s.]+$", "") + " " + tail, ANDROID + " + offline ending",
                    "Android speech stopped at \"" + lastWords(a, 3) + "\"; the offline recognizer heard \"" + tail + "\" after that");
            return new Pick(a, ANDROID, "");
        }
        if (!v.isEmpty()) return new Pick(v, OFFLINE, "");
        return new Pick("", "", "nothing heard");
    }
}
