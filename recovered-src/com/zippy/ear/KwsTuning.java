package com.zippy.ear;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Zippy 1.7: Sensitivity (0-100, higher = easier) -> Sherpa-ONNX keyword spotter keywords.
 *
 * Every line is the exact BPE token sequence of the KWS model's bpe.model
 * (sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01), followed by ":boosting score",
 * "#trigger threshold" and "@display name". Lines are joined with '/' for
 * KeywordSpotter.createStream(keywords). Raising the sensitivity adds pronunciations (each tier is a
 * superset of the one below), raises the boosting score and lowers the trigger threshold, so trigger
 * ease only ever increases. Mirrored in v1.7/kws-test/kws_tuning.py, which the accuracy test uses.
 */
public final class KwsTuning {
    private KwsTuning() {}

    /** {min sensitivity, phrase, BPE tokens}. */
    static final String[][] VARIANTS = {
            {"0",  "HEY ZIPPY", "\u2581HE Y \u2581 Z I PP Y"},
            {"0",  "HEY ZIPPI", "\u2581HE Y \u2581 Z I PP I"},
            {"0",  "HAZIPPI",   "\u2581HA Z I PP I"},
            {"0",  "HAZIPPY",   "\u2581HA Z I PP Y"},
            {"30", "HEZIPPI",   "\u2581HE Z I PP I"},
            {"30", "HE ZIPPI",  "\u2581HE \u2581 Z I PP I"},
            {"30", "HAY ZIPPY", "\u2581HA Y \u2581 Z I PP Y"},
            {"30", "HAZIPY",    "\u2581HA Z I P Y"},
            {"30", "HAZIBY",    "\u2581HA Z I B Y"},
            {"50", "HEY SIPPI", "\u2581HE Y \u2581S I PP I"},   // a soft "z" heard as "s"
            {"50", "HEY SIPPY", "\u2581HE Y \u2581S I PP Y"},
            {"85", "ZIPPY",     "\u2581 Z I PP Y"},             // bare "Zippy"
            {"85", "ZIPPI",     "\u2581 Z I PP I"},
    };
    /**
     * createStream(keywords) ADDS its lines to the keywords file the spotter was built with, so the
     * shipped assets/kws/keywords.txt holds only this never-said sentinel (threshold 0.99, ignored if
     * it ever fires) and every real line comes from keywords(sensitivity).
     */
    public static final String SENTINEL = "\u2581 Z Y Z Z Y V A \u2581QU O K K A \u2581 X Y LO PH ONE :1.00 #0.990 @NEVER";
    public static final String SENTINEL_NAME = "NEVER";
    static final double TH_HARD = 0.45, TH_EASY = 0.05;      // trigger threshold at 0 / 100
    static final double BOOST_HARD = 1.0, BOOST_EASY = 2.0;  // boosting score at 0 / 100
    static final double BARE_EXTRA = 0.10;                   // bare "Zippy" needs more confidence
    public static final int BARE_FROM = 85;

    public static int clamp(int s) { return Math.max(0, Math.min(100, s)); }

    public static double threshold(int s) { return round(TH_HARD + (TH_EASY - TH_HARD) * clamp(s) / 100.0, 1000); }

    public static double boost(int s) { return round(BOOST_HARD + (BOOST_EASY - BOOST_HARD) * clamp(s) / 100.0, 100); }

    private static double round(double v, int scale) { return Math.round(v * scale) / (double) scale; }

    /** Keyword lines for createStream(), '/'-separated. */
    public static String keywords(int sensitivity) {
        return String.join("/", lines(sensitivity));
    }

    public static List<String> lines(int sensitivity) {
        int s = clamp(sensitivity);
        double t = threshold(s), b = boost(s);
        List<String> out = new ArrayList<>();
        for (String[] v : VARIANTS) {
            int need = Integer.parseInt(v[0]);
            if (s < need) continue;
            boolean bare = need >= BARE_FROM;
            out.add(String.format(Locale.US, "%s :%.2f #%.3f @%s", v[2], b, bare ? t + BARE_EXTRA : t, bare ? "ZIPPY" : "HEY_ZIPPY"));
        }
        return out;
    }

    /** Short human description for the status line / summary. */
    public static String describe(int sensitivity) {
        int s = clamp(sensitivity);
        int n = lines(s).size();
        return String.format(Locale.US, "%d pronunciations, threshold %.2f", n, threshold(s));
    }
}
