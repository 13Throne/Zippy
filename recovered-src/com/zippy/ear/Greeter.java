package com.zippy.ear;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Spoken wake greeting ("Yeah?") through Android TextToSpeech on the assistant audio usage. */
public final class Greeter {
    private final TextToSpeech tts;
    private volatile boolean ready;
    private final ConcurrentHashMap<String, CountDownLatch> waiting = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> ok = new ConcurrentHashMap<>();
    private int seq;

    public Greeter(Context c) {
        TextToSpeech t = null;
        try {
            t = new TextToSpeech(c.getApplicationContext(), status -> onInit(status));
        } catch (Throwable ignored) { }
        tts = t;
    }

    private void onInit(int status) {
        if (status != TextToSpeech.SUCCESS || tts == null) return;
        try {
            tts.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                public void onStart(String id) {}
                public void onDone(String id) { finish(id, true); }
                public void onError(String id) { finish(id, false); }
                public void onError(String id, int code) { finish(id, false); }
                public void onStop(String id, boolean interrupted) { finish(id, false); }
            });
            ready = true;
        } catch (Throwable ignored) { }
    }

    private void finish(String id, boolean good) {
        ok.put(id, good);
        CountDownLatch l = waiting.remove(id);
        if (l != null) l.countDown();
    }

    public boolean ready() { return ready && tts != null; }

    /** Speak and block until onDone (or error/timeout). Never call on the main thread. */
    public boolean speakAndWait(String text, long timeoutMs) {
        if (!ready()) return false;
        String id;
        synchronized (this) { id = "zippy-" + (++seq); }
        CountDownLatch l = new CountDownLatch(1);
        waiting.put(id, l);
        try {
            if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, new Bundle(), id) != TextToSpeech.SUCCESS) return false;
            boolean finished = l.await(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) { try { tts.stop(); } catch (Throwable ignored) { } return false; }
            Thread.sleep(120); // let the speaker tail die out before the mic opens
            return Boolean.TRUE.equals(ok.remove(id));
        } catch (Throwable t) {
            return false;
        } finally {
            waiting.remove(id);
        }
    }

    /** Fire-and-forget (Preview button). */
    public boolean speak(String text) {
        if (!ready()) return false;
        try { return tts.speak(text, TextToSpeech.QUEUE_FLUSH, new Bundle(), "zippy-preview") == TextToSpeech.SUCCESS; }
        catch (Throwable t) { return false; }
    }

    public void shutdown() {
        ready = false;
        if (tts != null) try { tts.shutdown(); } catch (Throwable ignored) { }
    }
}
