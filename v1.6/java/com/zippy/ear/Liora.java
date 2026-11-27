package com.zippy.ear;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Liora's recorded greetings and confirmations, bundled uncompressed as assets/voice/<id>.m4a
 * so AssetManager.openFd() can hand MediaPlayer a file descriptor.
 */
public final class Liora {
    public static final String RANDOM = "random", CUSTOM = "custom";
    public static final String[] GREETING_IDS = {"g1", "g2", "g3", "g4", "g5", "g6", "g7", "g8", "g9"};
    public static final String[] GREETING_TEXT = {"Yeah?", "Hey, what's up?", "Go ahead.", "I'm listening.",
            "What do you need?", "Talk to me.", "Ready.", "Shoot.", "Right here."};
    public static final String[] CONFIRM_IDS = {"c1", "c2", "c3", "c4", "c5", "c6", "c7", "c8", "c9", "c10"};
    public static final String[] CONFIRM_TEXT = {"On it.", "Got it.", "Sent.", "Handling it now.", "Consider it done.",
            "You got it.", "All over it.", "Working on it.", "Heard you.", "Got it, I'll take care of it."};

    private static final Random RNG = new Random();
    private static volatile String lastGreeting, lastConfirm;

    private Liora() {}

    /** Spinner rows: Random, the named clips, Custom (TTS). Keys line up with labels. */
    public static String[] choiceKeys(boolean greeting) {
        String[] ids = greeting ? GREETING_IDS : CONFIRM_IDS;
        String[] k = new String[ids.length + 2];
        k[0] = RANDOM;
        System.arraycopy(ids, 0, k, 1, ids.length);
        k[k.length - 1] = CUSTOM;
        return k;
    }

    public static String[] choiceLabels(boolean greeting) {
        String[] t = greeting ? GREETING_TEXT : CONFIRM_TEXT;
        String[] l = new String[t.length + 2];
        l[0] = "Random";
        for (int i = 0; i < t.length; i++) l[i + 1] = "\u201c" + t[i] + "\u201d";
        l[l.length - 1] = "Custom (text-to-speech)";
        return l;
    }

    public static int indexOf(String[] keys, String key) {
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(key)) return i;
        return 0;
    }

    public static boolean isClip(String id, boolean greeting) {
        for (String s : greeting ? GREETING_IDS : CONFIRM_IDS) if (s.equals(id)) return true;
        return false;
    }

    /** Valid stored choice, or Random. */
    public static String validChoice(String c, boolean greeting) {
        if (RANDOM.equals(c) || CUSTOM.equals(c) || isClip(c, greeting)) return c;
        return RANDOM;
    }

    public static String text(String id) {
        for (int i = 0; i < GREETING_IDS.length; i++) if (GREETING_IDS[i].equals(id)) return GREETING_TEXT[i];
        for (int i = 0; i < CONFIRM_IDS.length; i++) if (CONFIRM_IDS[i].equals(id)) return CONFIRM_TEXT[i];
        return null;
    }

    /**
     * Clip to play for a choice: a named clip as is; Random = any clip but the previous one;
     * Custom = null (caller speaks its text with TTS).
     */
    public static String pick(String choice, String[] ids, String previous, Random rng) {
        if (CUSTOM.equals(choice)) return null;
        for (String s : ids) if (s.equals(choice)) return s;
        if (ids.length == 1) return ids[0];
        String p;
        do { p = ids[rng.nextInt(ids.length)]; } while (p.equals(previous));
        return p;
    }

    public static String pickGreeting(String choice) {
        String p = pick(choice, GREETING_IDS, lastGreeting, RNG);
        if (p != null) lastGreeting = p;
        return p;
    }

    public static String pickConfirm(String choice) {
        String p = pick(choice, CONFIRM_IDS, lastConfirm, RNG);
        if (p != null) lastConfirm = p;
        return p;
    }

    public static String asset(String id) { return "voice/" + id + ".m4a"; }

    private static MediaPlayer prepare(Context c, String id) throws Exception {
        MediaPlayer mp = new MediaPlayer();
        try (AssetFileDescriptor fd = c.getAssets().openFd(asset(id))) {
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            mp.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            mp.prepare();
            return mp;
        } catch (Exception e) {
            mp.release();
            throw e;
        }
    }

    /**
     * Play a clip and block (never call on the main thread) until OnCompletion, an error, or the
     * timeout. @return true when it finished normally.
     */
    public static boolean playAndWait(Context c, String id, long timeoutMs) {
        MediaPlayer mp = null;
        try {
            mp = prepare(c, id);
            final CountDownLatch done = new CountDownLatch(1);
            final boolean[] ok = {false};
            mp.setOnCompletionListener(m -> { ok[0] = true; done.countDown(); });
            mp.setOnErrorListener((m, what, extra) -> { done.countDown(); return true; });
            mp.start();
            boolean finished = done.await(timeoutMs, TimeUnit.MILLISECONDS);
            if (finished && ok[0]) Thread.sleep(80); // let the speaker tail die out before the mic opens
            return finished && ok[0];
        } catch (Throwable t) {
            return false;
        } finally {
            if (mp != null) {
                try { mp.stop(); } catch (Throwable ignored) { }
                try { mp.release(); } catch (Throwable ignored) { }
            }
        }
    }

    private static MediaPlayer previewPlayer;

    /** Preview button: fire and forget on the main thread. @return false if the clip can't play. */
    public static boolean preview(Context c, String id) {
        try {
            if (previewPlayer != null) { try { previewPlayer.release(); } catch (Throwable ignored) { } }
            MediaPlayer mp = prepare(c, id);
            previewPlayer = mp;
            mp.setOnCompletionListener(m -> { m.release(); if (previewPlayer == m) previewPlayer = null; });
            mp.start();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
