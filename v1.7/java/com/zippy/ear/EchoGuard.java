package com.zippy.ear;

/**
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
}
