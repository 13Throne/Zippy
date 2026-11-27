package com.zippy.ear;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.speech.RecognitionListener;
import android.speech.RecognitionSupport;
import android.speech.RecognitionSupportCallback;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Zippy 1.8: Android's speech recognizer hears ZIPPY'S OWN recording (Android 13+): the audio goes
 * through a pipe (RecognizerIntent.EXTRA_AUDIO_SOURCE) in segmented mode, so the recognizer never
 * opens the mic, never needs to "start up" while you're talking, and only ends when Zippy closes the
 * pipe. Every onSegmentResults is joined. There is no fixed timer: after the pipe closes Zippy waits
 * while the recognizer is still doing something (an idle timeout that resets on every callback), and
 * the text heard so far is kept even then.
 *
 * Whether a recognizer really reads the pipe is learned per recognizer (on-device / default) and
 * remembered: if it doesn't read (the pipe backs up) or opens its own mic (Zippy's mic is silenced),
 * Zippy stops it, notes why, and uses the offline recognizer, which heard the same audio anyway.
 */
final class AndroidStream {
    static final String ON_DEVICE = "on-device", DEFAULT = "default";
    private static final String PREFS = "zippy", K = "audioSrc.", LANGS = "audioSrc.langs";
    private static final byte[] EOF = new byte[0];
    /** Not reading Zippy's audio: more than this much is stuck in front of the pipe. */
    static final long BACKLOG_LIMIT_MS = 3500;

    private final Context ctx;
    private final Handler main;
    final String kind;
    private SpeechRecognizer sr;
    private ParcelFileDescriptor readFd, writeFd;
    private final LinkedBlockingQueue<byte[]> q = new LinkedBlockingQueue<>();
    private final AtomicLong backlog = new AtomicLong();
    private final CountDownLatch done = new CountDownLatch(1);
    private final List<String> segments = new CopyOnWriteArrayList<>();
    private volatile String partial = "", whole = null;
    private volatile int error;
    volatile long startedAt, readyAt = -1, speechAt = -1, endedAt = -1, lastActivity;
    private volatile boolean closed, cancelled, sawRms;
    private Thread writer;

    private AndroidStream(Context c, Handler main, String kind) { ctx = c; this.main = main; this.kind = kind; }

    // ------------------------------------------------------------------ support (remembered)

    private static SharedPreferences p(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    /** "yes", "no: <why>" or "" (not tried yet) for a recognizer kind. */
    static String support(Context c, String kind) { return p(c).getString(K + kind + "." + KwsGuard.VERSION, ""); }

    static void remember(Context c, String kind, String v) { p(c).edit().putString(K + kind + "." + KwsGuard.VERSION, v).apply(); }

    static boolean sdkOk() { return Build.VERSION.SDK_INT >= 33; }

    /** Which recognizer to try (on-device first), or null when none can take Zippy's audio. */
    static String choose(Context c) {
        if (!sdkOk()) return null;
        try {
            if (SpeechRecognizer.isOnDeviceRecognitionAvailable(c) && !support(c, ON_DEVICE).startsWith("no")) return ON_DEVICE;
            if (SpeechRecognizer.isRecognitionAvailable(c) && !support(c, DEFAULT).startsWith("no")) return DEFAULT;
        } catch (Throwable ignored) { }
        return null;
    }

    /** One line for Home / Settings. */
    static String describe(Context c) {
        if (!sdkOk()) return "not available (needs Android 13; this phone has Android " + Build.VERSION.RELEASE + ")";
        StringBuilder b = new StringBuilder();
        boolean od = false, df = false;
        try { od = SpeechRecognizer.isOnDeviceRecognitionAvailable(c); df = SpeechRecognizer.isRecognitionAvailable(c); } catch (Throwable ignored) { }
        b.append("on-device recognizer: ").append(od ? label(support(c, ON_DEVICE)) : "none");
        b.append("; default recognizer: ").append(df ? label(support(c, DEFAULT)) : "none");
        String langs = p(c).getString(LANGS, "");
        if (!langs.isEmpty()) b.append("; ").append(langs);
        return b.toString();
    }

    private static String label(String s) { return s.isEmpty() ? "not tried yet" : s.equals("yes") ? "supported" : "not supported (" + s.substring(Math.min(s.length(), 4)) + ")"; }

    /** Asks the on-device recognizer which languages it has (for diagnostics), once per start. */
    static void probe(Context c, Handler main) {
        if (!sdkOk()) return;
        main.post(() -> {
            SpeechRecognizer r = null;
            try {
                if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(c)) return;
                final SpeechRecognizer rr = SpeechRecognizer.createOnDeviceSpeechRecognizer(c);
                r = rr;
                rr.checkRecognitionSupport(intent(c, null, true), c.getMainExecutor(), new RecognitionSupportCallback() {
                    public void onSupportResult(RecognitionSupport s) {
                        String tag = Locale.getDefault().toLanguageTag();
                        boolean inst = s.getInstalledOnDeviceLanguages().contains(tag);
                        p(c).edit().putString(LANGS, "on-device " + tag + ": " + (inst ? "installed"
                                : s.getPendingOnDeviceLanguages().contains(tag) ? "downloading"
                                : s.getSupportedOnDeviceLanguages().contains(tag) ? "supported, not downloaded" : "not offered")).apply();
                        try { rr.destroy(); } catch (Throwable ignored) { }
                    }
                    public void onError(int e) {
                        p(c).edit().putString(LANGS, "on-device language check: error " + e).apply();
                        try { rr.destroy(); } catch (Throwable ignored) { }
                    }
                });
            } catch (Throwable t) {
                if (r != null) try { r.destroy(); } catch (Throwable ignored) { }
            }
        });
    }

    static Intent intent(Context c, ParcelFileDescriptor audio, boolean onDevice) {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag());
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, c.getPackageName());
        if (onDevice) i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        if (audio != null) {
            i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, audio);
            i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1);
            i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
            i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16000);
            i.putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE);
        }
        ArrayList<String> bias = new ArrayList<>();
        bias.add("Zippy");
        bias.add("Hey Zippy");
        i.putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, bias);
        return i;
    }

    // ------------------------------------------------------------------ one session

    static AndroidStream start(Context c, Handler main, String kind) {
        AndroidStream s = new AndroidStream(c, main, kind);
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            s.readFd = pipe[0];
            s.writeFd = pipe[1];
        } catch (IOException e) {
            return null;
        }
        s.startedAt = s.lastActivity = System.currentTimeMillis();
        s.writer = new Thread(s::write, "zippy-stt-pipe");
        s.writer.start();
        main.post(s::open);
        return s;
    }

    private void open() {
        if (cancelled) return;
        try {
            sr = ON_DEVICE.equals(kind) ? SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx) : SpeechRecognizer.createSpeechRecognizer(ctx);
            sr.setRecognitionListener(new RecognitionListener() {
                public void onReadyForSpeech(Bundle b) { readyAt = touch(); }
                public void onBeginningOfSpeech() { if (speechAt < 0) speechAt = touch(); }
                public void onRmsChanged(float v) { sawRms = true; touch(); }
                public void onBufferReceived(byte[] b) { touch(); }
                public void onEndOfSpeech() { touch(); }
                public void onEvent(int t, Bundle b) { touch(); }
                public void onPartialResults(Bundle b) { String t = first(b); if (!t.isEmpty()) partial = t; touch(); }
                public void onSegmentResults(Bundle b) { String t = first(b); if (!t.isEmpty()) segments.add(t); partial = ""; touch(); }
                public void onEndOfSegmentedSession() { end(); }
                public void onResults(Bundle b) { whole = first(b); end(); }
                public void onError(int e) { error = e; end(); }
            });
            sr.startListening(intent(ctx, readFd, ON_DEVICE.equals(kind)));
        } catch (Throwable t) {
            error = -1;
            end();
        }
    }

    private long touch() { long n = System.currentTimeMillis(); lastActivity = n; return n; }

    private void end() { if (endedAt < 0) endedAt = touch(); done.countDown(); }

    private static String first(Bundle b) {
        ArrayList<String> l = b == null ? null : b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return l == null || l.isEmpty() || l.get(0) == null ? "" : l.get(0).trim();
    }

    private void write() {
        try (FileOutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(writeFd)) {
            while (true) {
                byte[] b = q.take();
                if (b == EOF) break;
                out.write(b);
                backlog.addAndGet(-b.length);
            }
        } catch (Throwable ignored) {
            // recognizer closed its end
        }
    }

    /** 16 kHz mono PCM from Zippy's recording. */
    void write(short[] pcm, int off, int n) {
        if (closed || n <= 0) return;
        byte[] b = new byte[n * 2];
        for (int i = 0; i < n; i++) { short s = pcm[off + i]; b[2 * i] = (byte) s; b[2 * i + 1] = (byte) (s >> 8); }
        backlog.addAndGet(b.length);
        q.offer(b);
    }

    /** Audio waiting in front of the pipe, in ms (the pipe itself holds ~2 s). */
    long backlogMs() { return backlog.get() / 32; }

    boolean ended() { return endedAt >= 0; }

    int error() { return error; }

    boolean heardSomething() { return sawRms || readyAt >= 0 || !segments.isEmpty() || !partial.isEmpty(); }

    /** Closes Zippy's end: the session finishes once the recognizer has the last of it. */
    void finish() { if (!closed) { closed = true; q.offer(EOF); } }

    /**
     * Waits for the result after finish(): returns when the recognizer is done, or after idleMs with no
     * callback at all, or maxMs. @return false on a timeout (text() still has what it heard).
     */
    boolean await(long idleMs, long maxMs) {
        long start = System.currentTimeMillis();
        lastActivity = Math.max(lastActivity, start);
        try {
            while (done.getCount() > 0) {
                if (done.await(100, TimeUnit.MILLISECONDS)) break;
                long now = System.currentTimeMillis();
                if (now - lastActivity > idleMs || now - start > maxMs) return false;
            }
        } catch (InterruptedException e) {
            return false;
        }
        return true;
    }

    /** All segments, plus the final result or the last partial. */
    String text() {
        StringBuilder b = new StringBuilder();
        for (String s : segments) { if (b.length() > 0) b.append(' '); b.append(s); }
        String tail = whole != null && !whole.isEmpty() ? whole : partial;
        if (tail != null && !tail.isEmpty() && (segments.isEmpty() || whole == null)) { if (b.length() > 0) b.append(' '); b.append(tail); }
        return b.toString().trim();
    }

    int segmentCount() { return segments.size(); }

    void cancel() {
        cancelled = true;
        finish();
        main.post(() -> {
            if (sr != null) {
                try { sr.cancel(); } catch (Throwable ignored) { }
                try { sr.destroy(); } catch (Throwable ignored) { }
            }
        });
        try { if (readFd != null) readFd.close(); } catch (Throwable ignored) { }
    }

    void close() { cancel(); }
}
