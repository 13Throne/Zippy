package com.zippy.ear;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Zippy 1.81: double-press volume down to pause / resume listening (opt-in).
 *
 * Android only lets an app see volume keys while it's in the background through an AccessibilityService
 * that asks to filter key events (res/xml/zippy_accessibility.xml: flagRequestFilterKeyEvents,
 * canRequestFilterKeyEvents). This service reads nothing on screen (no event types, no window content);
 * it looks at volume-down only, and only while "Double-press volume down" is on in Zippy's Settings.
 * The double-press rule and its trade-off are in VolumeKeys. Feedback: two short buzzes + a rising beep
 * when listening resumes, one long buzz + a low beep when it pauses, three quick buzzes if Android
 * wouldn't start the mic (then a "Tap to start listening" notification appears).
 */
public class VolumeKeyService extends AccessibilityService {
    private final VolumeKeys rule = new VolumeKeys();
    private static final int[] STREAMS = {AudioManager.STREAM_MUSIC, AudioManager.STREAM_RING, AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_VOICE_CALL, AudioManager.STREAM_ALARM, AudioManager.STREAM_SYSTEM, AudioManager.STREAM_ACCESSIBILITY};
    private final int[] before = new int[STREAMS.length];

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        try {
            if (e.getKeyCode() != KeyEvent.KEYCODE_VOLUME_DOWN) { rule.otherKey(); return false; }
            if (!ZippyPrefs.of(this).volumeToggle() || inCall()) return false;
            boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
            boolean wasPending = rule.pendingFirst();
            if (down && e.getRepeatCount() == 0 && !wasPending) snapshot(); // volumes before the first press
            VolumeKeys.Verdict v = rule.onVolumeDown(down, e.getRepeatCount(), e.getEventTime());
            if (v == VolumeKeys.Verdict.TOGGLE) { restore(); toggle(); }
            return v != VolumeKeys.Verdict.PASS;
        } catch (Throwable t) {
            return false; // never break the volume key
        }
    }

    private boolean inCall() {
        AudioManager am = getSystemService(AudioManager.class);
        int m = am == null ? AudioManager.MODE_NORMAL : am.getMode();
        return m == AudioManager.MODE_IN_CALL || m == AudioManager.MODE_IN_COMMUNICATION || m == AudioManager.MODE_RINGTONE;
    }

    private void snapshot() {
        AudioManager am = getSystemService(AudioManager.class);
        for (int i = 0; i < STREAMS.length; i++) {
            try { before[i] = am.getStreamVolume(STREAMS[i]); } catch (Throwable t) { before[i] = -1; }
        }
    }

    /** Puts back the step the first press took (only streams that went down; never raises anything else). */
    private void restore() {
        AudioManager am = getSystemService(AudioManager.class);
        for (int i = 0; i < STREAMS.length; i++) {
            try {
                if (before[i] >= 0 && am.getStreamVolume(STREAMS[i]) < before[i]) am.setStreamVolume(STREAMS[i], before[i], 0);
            } catch (Throwable ignored) { } // e.g. Do Not Disturb blocks ring volume changes
        }
    }

    private void toggle() {
        int r = ListenService.toggleFromKey(this);
        feedback(r);
    }

    private void feedback(int r) {
        long[] pattern;
        int tone;
        if (r == ListenService.TOGGLE_ON) { pattern = new long[]{0, 40, 90, 40}; tone = ToneGenerator.TONE_PROP_BEEP2; }
        else if (r == ListenService.TOGGLE_OFF) { pattern = new long[]{0, 220}; tone = ToneGenerator.TONE_PROP_NACK; }
        else { pattern = new long[]{0, 30, 60, 30, 60, 30}; tone = -1; }
        try {
            Vibrator v = getSystemService(Vibrator.class);
            if (v != null) v.vibrate(VibrationEffect.createWaveform(pattern, -1));
        } catch (Throwable ignored) { }
        if (tone >= 0) {
            try {
                final ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_SYSTEM, 40);
                tg.startTone(tone, 160);
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(tg::release, 400);
            } catch (Throwable ignored) { }
        }
    }

    /** Is Zippy's volume shortcut switched on in Android's Accessibility settings? */
    public static boolean enabled(Context c) {
        try {
            if (Settings.Secure.getInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) return false;
            String list = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (list == null) return false;
            TextUtils.SimpleStringSplitter s = new TextUtils.SimpleStringSplitter(':');
            s.setString(list);
            String me = c.getPackageName() + "/" + VolumeKeyService.class.getName();
            String shortMe = c.getPackageName() + "/.VolumeKeyService";
            while (s.hasNext()) { String n = s.next(); if (n.equalsIgnoreCase(me) || n.equalsIgnoreCase(shortMe)) return true; }
        } catch (Throwable ignored) { }
        return false;
    }

    public static Intent settingsIntent() {
        return new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
}
