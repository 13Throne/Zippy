package com.zippy.ear;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Zippy 1.7 safety net for the native Sherpa-ONNX wake word. A native crash while loading
 * (e.g. onnxruntime on an unusual CPU or under ARM translation) can't be caught in Java and would
 * make the sticky service crash-loop. So a "loading" mark is written before the load and cleared
 * after it; if the mark is still there on the next start, that load crashed and this version falls
 * back to the 1.6 Vosk wake word instead of trying again. A Java-level failure falls back too.
 * The mark is per app version, so an update retries Sherpa-ONNX.
 */
public final class KwsGuard {
    static final int VERSION = 9;
    private static final String PREFS = "zippy", LOADING = "kwsLoading", BROKEN = "kwsBroken", WHY = "kwsWhy";

    private KwsGuard() {}

    private static SharedPreferences p(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    public static boolean shouldTry(Context c) {
        SharedPreferences p = p(c);
        if (p.getInt(BROKEN, 0) == VERSION) return false;
        if (p.getInt(LOADING, 0) == VERSION) { // the last load never finished: it crashed the app
            p.edit().putInt(BROKEN, VERSION).putString(WHY, "it crashed while loading").remove(LOADING).commit();
            return false;
        }
        return true;
    }

    public static void begin(Context c) { p(c).edit().putInt(LOADING, VERSION).commit(); }

    public static void ok(Context c) { p(c).edit().remove(LOADING).remove(BROKEN).remove(WHY).commit(); }

    public static void failed(Context c, Throwable t) {
        String why = t == null ? "it failed to load" : t.getClass().getSimpleName();
        p(c).edit().remove(LOADING).putInt(BROKEN, VERSION).putString(WHY, why).commit();
    }

    public static boolean usingFallback(Context c) { return p(c).getInt(BROKEN, 0) == VERSION; }

    /** For the Home summary. */
    public static String engineLabel(Context c) {
        if (!usingFallback(c)) return "Sherpa-ONNX, on this phone";
        return "offline fallback (Sherpa-ONNX couldn't start: " + p(c).getString(WHY, "unknown") + ")";
    }
}
