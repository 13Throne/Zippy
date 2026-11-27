package com.zippy.ear;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Zippy 1.7: the words the offline recognizer heard right after Sherpa-ONNX spotted the keyword.
 * The recognizer starts at the keyword, so it may still catch the tail of "Zippy" ("pee", "e") or
 * a filler; those are dropped from the front, and so is a repeated wake phrase.
 */
public final class TaskText {
    private TaskText() {}

    static final Set<String> LEADING_JUNK = new HashSet<>(Arrays.asList(
            "zippy", "zippi", "zippie", "sippy", "sippi", "hippie", "hippy", "pee", "pea", "p", "e", "ee", "be", "b",
            "he", "hey", "hay", "the", "uh", "um", "huh", "hm", "hmm", "oh"));

    public static String afterWake(String heard, int sensitivity) {
        String t = WakePhrase.stripLeadingWake(heard == null ? "" : heard, sensitivity);
        String[] w = WakePhrase.normalize(t).split(" ");
        int i = 0;
        while (i < w.length && (w[i].isEmpty() || LEADING_JUNK.contains(w[i]))) i++;
        StringBuilder b = new StringBuilder();
        for (; i < w.length; i++) { if (b.length() > 0) b.append(' '); b.append(w[i]); }
        String out = b.toString();
        // a lone short word is more likely noise than a task
        return out.indexOf(' ') < 0 && out.length() < 3 ? "" : out;
    }
}
