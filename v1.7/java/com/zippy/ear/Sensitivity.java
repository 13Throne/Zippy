package com.zippy.ear;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Locale;

/**
 * One meaning everywhere (1.4+): Sensitivity 0-100, HIGHER = EASIER to trigger.
 * 1.7: the wake word is Sherpa-ONNX keyword spotting (KwsTuning: more pronunciations, higher boost,
 * lower threshold as it rises) and the voice check (VoiceMatch) loosens with it; 100 = voice check off.
 *
 * 1.2/1.3 stored the opposite meaning, so the saved value is flipped once (migrate).
 */
public final class Sensitivity {
    public static final int DEFAULT = 55;
    private static final String PREFS = "zippy", KEY = "sensitivity", SCALE = "sensitivityScale";

    private Sensitivity() {}

    public static void migrate(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (p.getInt(SCALE, 0) >= 2) return;
        SharedPreferences.Editor e = p.edit().putInt(SCALE, 2);
        if (p.contains(KEY)) {
            e.putInt(KEY, migrateValue(p.getInt(KEY, DEFAULT)));
        }
        e.commit();
    }

    public static int migrateValue(int oldStrictness) {
        return 100 - clamp(oldStrictness);
    }

    public static int clamp(int s) {
        return Math.max(0, Math.min(100, s));
    }

    public static String label(int value) {
        int s = clamp(value);
        String what;
        if (s >= 100) what = "Easiest: every pronunciation, voice check off";
        else if (s >= KwsTuning.BARE_FROM) what = "Very easy: also wakes on just \u201cZippy\u201d";
        else if (s >= 50) what = "Easy: also hears a soft Z (\u201cHey Sippy\u201d)";
        else if (s >= 30) what = "Balanced: common sound-alikes";
        else what = "Strict: clear \u201cHey Zippy\u201d and a close voice match";
        return String.format(Locale.US, "%d  (%s). Higher = triggers more easily.", s, what);
    }
}
