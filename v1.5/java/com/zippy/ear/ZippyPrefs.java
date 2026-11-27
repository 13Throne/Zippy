package com.zippy.ear;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * v1.5 settings that live next to the Kotlin Store values in the same "zippy" SharedPreferences.
 * Pure logic over a tiny key/value interface so it is unit-tested on the JVM.
 */
public final class ZippyPrefs {
    public interface KV {
        boolean contains(String k);
        String getString(String k, String d);
        boolean getBoolean(String k, boolean d);
        int getInt(String k, int d);
        void putString(String k, String v);
        void putBoolean(String k, boolean v);
        void putInt(String k, int v);
    }

    public static final String CUE_VOICE = "voice", CUE_BEEP = "beep", CUE_VIBRATE = "vibrate", CUE_SILENT = "silent";
    public static final String DEFAULT_GREETING = "Yeah?";
    public static final int MAX_GREETING = 80;
    static final String K_CUE = "wakeCue", K_GREETING = "greeting", K_STT = "useAndroidStt",
            K_GROK = "openGrokBot", K_TAB = "tab", K_READY = "readyCue";

    private final KV kv;

    public ZippyPrefs(KV kv) { this.kv = kv; }

    public static ZippyPrefs of(Context c) {
        final SharedPreferences p = c.getSharedPreferences("zippy", Context.MODE_PRIVATE);
        return new ZippyPrefs(new KV() {
            public boolean contains(String k) { return p.contains(k); }
            public String getString(String k, String d) { return p.getString(k, d); }
            public boolean getBoolean(String k, boolean d) { return p.getBoolean(k, d); }
            public int getInt(String k, int d) { return p.getInt(k, d); }
            public void putString(String k, String v) { p.edit().putString(k, v).apply(); }
            public void putBoolean(String k, boolean v) { p.edit().putBoolean(k, v).apply(); }
            public void putInt(String k, int v) { p.edit().putInt(k, v).apply(); }
        });
    }

    /**
     * Pin the wake cue once, so later saves of the legacy readyCue slot (Store.saveSettings) can't
     * change what the user picked. Upgrades keep their 1.4 cue; fresh installs get the greeting.
     */
    public void migrate() {
        if (!kv.contains(K_CUE)) kv.putString(K_CUE, wakeCue());
    }

    // ---- wake cue: voice greeting / beep / vibrate / silent
    public String wakeCue() {
        if (kv.contains(K_CUE)) return validCue(kv.getString(K_CUE, CUE_VOICE));
        if (!kv.contains(K_READY)) return CUE_VOICE; // fresh install
        return cueFromLegacy(kv.getInt(K_READY, 1)); // keep what 1.2-1.4 users picked
    }

    public void setWakeCue(String cue) { kv.putString(K_CUE, validCue(cue)); }

    static String validCue(String c) {
        if (CUE_BEEP.equals(c) || CUE_VIBRATE.equals(c) || CUE_SILENT.equals(c) || CUE_VOICE.equals(c)) return c;
        return CUE_VOICE;
    }

    /** Cues.OFF=0, VIBRATE=1, SOUND=2, BOTH=3 (old "When Hey Zippy hits" row). */
    public static String cueFromLegacy(int ready) {
        switch (ready) {
            case 0: return CUE_SILENT;
            case 1: return CUE_VIBRATE;
            default: return CUE_BEEP;
        }
    }

    /** Value written to the old readyCue slot so older code paths agree. */
    public static int legacyReadyCue(String cue) {
        if (CUE_BEEP.equals(cue)) return 2;
        if (CUE_VIBRATE.equals(cue)) return 1;
        return 0;
    }

    public static String cueLabel(String cue) {
        switch (validCue(cue)) {
            case CUE_BEEP: return "Beep";
            case CUE_VIBRATE: return "Vibrate";
            case CUE_SILENT: return "Silent";
            default: return "Voice greeting";
        }
    }

    // ---- greeting text
    public String greeting() { return cleanGreeting(kv.getString(K_GREETING, DEFAULT_GREETING)); }

    public void setGreeting(String g) { kv.putString(K_GREETING, cleanGreeting(g)); }

    public static String cleanGreeting(String g) {
        if (g == null) return DEFAULT_GREETING;
        String t = g.replaceAll("\\s+", " ").trim();
        if (t.isEmpty()) return DEFAULT_GREETING;
        return t.length() > MAX_GREETING ? t.substring(0, MAX_GREETING).trim() : t;
    }

    // ---- toggles
    public boolean useAndroidStt() { return kv.getBoolean(K_STT, true); }
    public void setUseAndroidStt(boolean on) { kv.putBoolean(K_STT, on); }
    public boolean openGrokBot() { return kv.getBoolean(K_GROK, false); }
    public void setOpenGrokBot(boolean on) { kv.putBoolean(K_GROK, on); }

    // ---- last "Open Grok Bot" problem, shown on Settings (empty = fine)
    public String grokNote() { return kv.getString("grokNote", ""); }
    public void setGrokNote(String n) { kv.putString("grokNote", n == null ? "" : n); }

    // ---- tab
    public Tabs.Tab tab() { return Tabs.fromKey(kv.getString(K_TAB, Tabs.Tab.HOME.key)); }
    public void setTab(Tabs.Tab t) { kv.putString(K_TAB, t.key); }
}
