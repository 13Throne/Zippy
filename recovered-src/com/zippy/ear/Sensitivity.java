package com.zippy.ear;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Locale;

/**
 * One meaning everywhere (1.4): Sensitivity 0-100, HIGHER = EASIER to trigger.
 * Both the wake-phrase matcher (WakePhrase) and the voice check (VoiceMatch) loosen as it rises.
 *
 * 1.2/1.3 stored the opposite meaning (the slider read "Looser N Stricter", Phrase loosened as the
 * number FELL and the voice threshold was 0.5 + 0.35*N/100), so the saved value is flipped once.
 */
public final class Sensitivity {
    public static final int DEFAULT = 55;
    private static final String PREFS = "zippy", KEY = "sensitivity", SCALE = "sensitivityScale";

    private Sensitivity() {}

    /** Flip a 1.2/1.3 value to the 1.4 scale exactly once (0 "looser" -> 100 "easier"). */
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
        if (s >= 100) what = "Easiest: loosest wake phrase, voice check off";
        else if (s >= 85) what = "Very easy: also wakes on just \"Zippy\"";
        else if (s >= 60) what = "Easy: accepts close sound-alikes";
        else if (s >= 30) what = "Balanced";
        else what = "Strict: exact \"Hey Zippy\" and a close voice match";
        return String.format(Locale.US, "%d  (%s). Higher = triggers more easily.", s, what);
    }
}
