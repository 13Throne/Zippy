package com.zippy.ear;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Zippy 1.8 barge-in helpers: the loudness envelope of each Liora clip (assets/barge/env.txt, made by
 * v1.8/make_voice_env.py), and the echo gain (mic loudness per clip loudness) this phone measured for the
 * current output route and volume, saved after sessions where you let her finish.
 */
final class VoiceEnv {
    private static Map<String, float[]> env;

    static synchronized float[] of(Context ctx, String clip) {
        if (clip == null) return null;
        if (env == null) {
            env = new HashMap<>();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(ctx.getAssets().open("barge/env.txt"), "UTF-8"))) {
                for (String l; (l = r.readLine()) != null; ) {
                    String[] t = l.trim().split(" ");
                    if (t.length < 2) continue;
                    float[] v = new float[t.length - 1];
                    for (int i = 1; i < t.length; i++) v[i - 1] = Float.parseFloat(t[i]);
                    env.put(t[0], v);
                }
            } catch (Throwable ignored) { }
        }
        return env.get(clip);
    }

    /** "speaker.9", "bluetooth.12", … : the echo depends on where she plays and how loud. */
    static String route(Context ctx) {
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            String kind = "speaker";
            for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                int t = d.getType();
                if (t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || t == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) { kind = "bluetooth"; break; }
                if (t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || t == AudioDeviceInfo.TYPE_WIRED_HEADSET || t == AudioDeviceInfo.TYPE_USB_HEADSET) kind = "wired";
            }
            return kind + "." + am.getStreamVolume(AudioManager.STREAM_MUSIC);
        } catch (Throwable t) { return "unknown"; }
    }

    private static SharedPreferences prefs(Context ctx) { return ctx.getSharedPreferences("zippy", Context.MODE_PRIVATE); }

    /** Saved echo gain for this route/volume, or -1. */
    static float gain(Context ctx, String route) { return prefs(ctx).getFloat("echoGain." + route, -1f); }

    /** Blend a new measurement in (first one taken as is). */
    static void save(Context ctx, String route, float g) {
        if (!(g > 0) || g > 20) return;
        float old = gain(ctx, route);
        float v = old > 0 ? 0.7f * old + 0.3f * g : g;
        prefs(ctx).edit().putFloat("echoGain." + route, v).apply();
    }

    static String describe(float g) { return g > 0 ? String.format(Locale.US, "%.2f", g) : "not measured yet"; }
}
