package com.zippy.ear;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 1.4 wake-phrase matcher. Same idea as 1.2's Phrase, but Sensitivity now means
 * "higher = easier", matching VoiceMatch. Every tier accepted at a sensitivity is also
 * accepted at any higher sensitivity (tested in SensitivityDirectionTest).
 */
public final class WakePhrase {
    private static final Set<String> EXACT = new HashSet<>(Arrays.asList(
            "hey zippy", "hey zippi", "hey zippee", "hey zippie", "hey sippy"));

    private WakePhrase() {}

    public static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z\\s]", " ").replaceAll("\\s+", " ").trim();
    }

    /** @return the words after the wake phrase ("" if none), or null if there's no wake phrase. */
    public static String parse(String transcript, int sensitivity) {
        int s = Sensitivity.clamp(sensitivity);
        String[] w = normalize(transcript).split(" ");
        List<String> words = new ArrayList<>();
        for (String x : w) if (!x.isEmpty()) words.add(x);
        if (words.isEmpty()) return null;
        if (words.size() >= 2 && isWake(words.get(0) + " " + words.get(1), s)) {
            return String.join(" ", words.subList(2, words.size()));
        }
        if (isBare(words.get(0), s)) {
            return String.join(" ", words.subList(1, words.size()));
        }
        return null;
    }

    public static String stripLeadingWake(String transcript, int sensitivity) {
        String r = parse(transcript, sensitivity);
        return r == null ? normalize(transcript) : r;
    }

    static boolean isWake(String phrase, int s) {
        if (EXACT.contains(phrase)) return true;
        int d = levenshtein(phrase, "hey zippy");
        if (s >= 30 && (d <= 1 || phrase.equals("a zippy") || phrase.equals("hey zipy"))) return true;
        if (s >= 60 && (d <= 2 || phrase.equals("hey zip"))) return true;
        return s >= 85 && d <= 3 && phrase.endsWith("ippy");
    }

    static boolean isBare(String word, int s) {
        if (s < 85) return false;
        return word.equals("zippy") || word.equals("zippi") || levenshtein(word, "zippy") <= 1;
    }

    static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int c = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + c);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }
}
