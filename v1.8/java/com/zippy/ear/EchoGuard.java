package com.zippy.ear;

import java.util.ArrayList;
import java.util.List;

/**
 * Zippy 1.8: the mic now keeps recording while Liora greets you (nothing you say is lost), so her
 * echo can be in the task audio. Two ways to drop it, both fuzzy (one wrong letter per word is fine):
 *  - stripTimed: offline (Vosk) words with times; only words inside the greeting window that sound like
 *    the greeting's words are dropped, so your own words over her are kept.
 *  - stripLeading: Android speech text (no times): a leading run of the greeting's words, in order.
 * Zippy 1.7: if the phone's speaker echo of Liora's greeting leaked into the command
 * ("what do you what time is it" after "What do you need?"), drop the leading words that repeat the
 * greeting. Only a run of at least two greeting words at the very start is removed, and never the
 * whole transcript.
 */
public final class EchoGuard {
    private EchoGuard() {}

    public static String strip(String heard, String greeting) {
        if (heard == null) return "";
        String h = WakePhrase.normalize(heard);
        String g = WakePhrase.normalize(greeting);
        if (h.isEmpty() || g.isEmpty()) return h.isEmpty() ? heard.trim() : h;
        String[] hw = h.split(" "), gw = g.split(" ");
        int n = 0;
        while (n < hw.length && n < gw.length && hw[n].equals(gw[n])) n++;
        boolean oneWordGreeting = gw.length == 1 && n == 1;
        if ((n >= 2 || oneWordGreeting) && n < hw.length) {
            StringBuilder b = new StringBuilder();
            for (int i = n; i < hw.length; i++) { if (b.length() > 0) b.append(' '); b.append(hw[i]); }
            return b.toString();
        }
        return h;
    }

    /** Greeting words, normalized ("What do you need?" -> what, do, you, need). */
    static String[] words(String greeting) {
        List<String> out = new ArrayList<>();
        if (greeting != null) for (String w : greeting.trim().split("\\s+")) { String t = tok(w); if (!t.isEmpty()) out.add(t); }
        return out.toArray(new String[0]);
    }

    /** One word, lower case, letters only ("I'm" -> "im"). */
    static String tok(String w) { return w == null ? "" : w.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z]", ""); }

    static boolean like(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.equals(b)) return true;
        int n = Math.min(a.length(), b.length());
        int d = WakePhrase.levenshtein(a, b);
        return (n >= 3 && d <= 1) || (n >= 6 && d <= 2);
    }

    /** Looser, for a word in the greeting's position: "mean" for "need". */
    static boolean near(String a, String b) {
        return like(a, b) || (a.length() == b.length() && a.length() >= 4 && WakePhrase.levenshtein(a, b) <= 2);
    }

    static boolean inGreeting(String word, String[] g) {
        String w = tok(word);
        for (String x : g) if (like(w, x)) return true;
        return false;
    }

    /** Drops greeting-sounding words that lie inside [startSec - 0.1, endSec + 0.5]. */
    public static List<VoskWords.Word> stripTimed(List<VoskWords.Word> ws, String greeting, double startSec, double endSec) {
        String[] g = words(greeting);
        if (g.length == 0 || startSec < 0) return ws;
        List<VoskWords.Word> out = new ArrayList<>();
        for (VoskWords.Word w : ws) {
            boolean inside = w.end > startSec - 0.1 && w.start < endSec + 0.5;
            if (inside && inGreeting(w.text, g)) continue;
            out.add(w);
        }
        return out;
    }

    /**
     * Index of the first token after a leading echo of the greeting (tokens[from..]), or `from` if
     * there's none. Words must come in the greeting's order (one may be missing); at least two must
     * match (three of a four-word greeting), or the only word of a one-word greeting.
     */
    public static int stripLeading(String[] tokens, int from, String greeting) {
        String[] g = words(greeting);
        if (g.length == 0) return from;
        int j = 0, k = from, matched = 0, swaps = 0;
        while (k < tokens.length && j < g.length) {
            String t = tok(tokens[k]);
            if (t.isEmpty()) { k++; continue; }
            int found = -1;
            for (int x = j; x < Math.min(g.length, j + 2); x++) if (matched > 0 ? near(t, g[x]) : like(t, g[x])) { found = x; break; }
            if (found < 0 && matched > 0 && swaps == 0 && j + 1 < g.length && k + 1 < tokens.length
                    && near(tok(tokens[k + 1]), g[j + 1])) { // one misheard word ("what to you need")
                swaps++; k++; j++;
                continue;
            }
            if (found < 0) break;
            matched++; j = found + 1; k++;
        }
        return matched >= Math.min(g.length, Math.max(2, g.length - 1)) ? k : from;
    }
}
