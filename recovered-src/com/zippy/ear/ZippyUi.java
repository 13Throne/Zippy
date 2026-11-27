package com.zippy.ear;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.List;

/**
 * 1.4 glue for MainActivity (hooked from smali):
 *  - onCreate: one-time sensitivity migration.
 *  - onResume/onPause: watch prefs + the service's real state and re-render live.
 *  - afterRender: derive the status line from (switch, real engine state, note); voice + sensitivity text.
 */
public final class ZippyUi {
    private static WeakReference<MainActivity> current = new WeakReference<>(null);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static boolean queued;
    private static final Runnable RENDER = () -> {
        queued = false;
        MainActivity a = current.get();
        if (a != null && !a.isFinishing()) MainActivity.rerender(a);
    };
    private static final Runnable ENGINE_CHANGED = ZippyUi::requestRender;
    // must be a strong reference: SharedPreferences only keeps listeners weakly
    private static final SharedPreferences.OnSharedPreferenceChangeListener PREFS = (p, key) -> {
        if (key == null || key.equals("listening") || key.equals("note") || key.equals("heard")
                || key.equals("prints") || key.equals("orders")) {
            requestRender();
        }
    };
    private static long lastKick;

    private ZippyUi() {}

    public static void onCreate(MainActivity a) {
        Sensitivity.migrate(a);
    }

    public static void onResume(MainActivity a) {
        current = new WeakReference<>(a);
        a.getSharedPreferences("zippy", Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(PREFS);
        ListenService.watch(ENGINE_CHANGED);
    }

    public static void onPause(MainActivity a) {
        a.getSharedPreferences("zippy", Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(PREFS);
        ListenService.unwatch(ENGINE_CHANGED);
        if (current.get() == a) current = new WeakReference<>(null);
    }

    static void requestRender() {
        if (queued) return;
        queued = true;
        MAIN.postDelayed(RENDER, 120);
    }

    public static void afterRender(MainActivity a, TextView status, Switch sw, TextView voice,
                                   SeekBar sensitivity, TextView sensitivityLabel) {
        Settings s = Store.INSTANCE.read(a);
        boolean wanted = s.getListening();
        int engine = ListenService.engineState();
        if (status != null) status.setText(ListenState.statusText(wanted, engine, s.getNote()));
        boolean mic = a.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        long now = System.currentTimeMillis();
        if (ListenState.screenShouldStart(wanted, engine, mic) && now - lastKick > 3000) {
            lastKick = now; // switch says on but nothing is running (e.g. killed): bring it back
            try { ListenService.Companion.start(a); } catch (Exception ignored) { }
        }
        if (voice != null) {
            try {
                List<float[]> wakes = Store.INSTANCE.prints(a).getWakes();
                if (wakes != null && wakes.size() >= 3 && !VoiceMatch.allCurrent(wakes)) {
                    voice.setText("Retrain needed: Zippy 1.4 has a new, more reliable voice check. Until you retrain, any voice can wake Zippy.");
                } else if (wakes != null && wakes.size() >= 3 && Sensitivity.clamp(s.getSensitivity()) >= 100) {
                    voice.setText(wakes.size() + " wake takes saved. Voice check is off at Sensitivity 100.");
                }
            } catch (Exception ignored) { }
        }
        if (sensitivityLabel != null) sensitivityLabel.setText(Sensitivity.label(s.getSensitivity()));
    }

    /** Replaces the old "Looser N Stricter" label while dragging. */
    public static String sensitivityLabel(int value) {
        return Sensitivity.label(value);
    }
}
