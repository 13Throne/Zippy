package com.zippy.ear;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.vosk.Model;
import org.vosk.Recognizer;

/**
 * Zippy 1.8: the offline recognizer hears the WHOLE recording (from ~2 s before the wake word to
 * the end Zippy decided), on its own thread so the mic never waits. Every utterance Vosk finalizes
 * at a pause is kept (with word times) and joined, so a pause never ends the task. If the model is
 * still loading, the audio waits in the queue until it's ready.
 */
final class VoskJob {
    private static final short[] EOF = new short[0];
    private final LinkedBlockingQueue<short[]> q = new LinkedBlockingQueue<>();
    private final List<String> results = new ArrayList<>();
    private final Supplier<Model> model;
    private final Thread thread;
    private volatile String partial = "";
    private volatile String status = "waiting for the offline model";
    private volatile boolean cancelled;

    VoskJob(Supplier<Model> model) {
        this.model = model;
        thread = new Thread(this::run, "zippy-vosk-task");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        thread.start();
    }

    void feed(short[] pcm, int off, int n) {
        if (n <= 0) return;
        short[] c = new short[n];
        System.arraycopy(pcm, off, c, 0, n);
        q.offer(c);
    }

    String partial() { return partial; }

    String status() { return status; }

    private void run() {
        Recognizer r = null;
        try {
            Model m = null;
            long until = Long.MAX_VALUE;
            while (!cancelled && (m = model.get()) == null) { // model still loading: audio waits in the queue
                if (q.contains(EOF) && until == Long.MAX_VALUE) until = System.currentTimeMillis() + 15000;
                if (System.currentTimeMillis() > until) { status = "offline model not ready"; return; }
                Thread.sleep(100);
            }
            if (cancelled) return;
            r = new Recognizer(m, 16000f);
            r.setWords(true);
            status = "listening";
            int n = 0;
            while (!cancelled) {
                short[] c = q.poll(200, TimeUnit.MILLISECONDS);
                if (c == null) continue;
                if (c == EOF) {
                    String f = r.getFinalResult();
                    synchronized (results) { results.add(f); }
                    status = "done";
                    return;
                }
                if (r.acceptWaveForm(c, c.length)) {
                    String res = r.getResult();
                    synchronized (results) { results.add(res); }
                    String t = VoskWords.text(res);
                    if (!t.isEmpty()) partial = joinedText();
                } else if ((++n & 3) == 0) {
                    String p = VoskWords.text(r.getPartialResult());
                    String done = joinedText();
                    partial = (done + " " + p).trim();
                }
            }
        } catch (Throwable t) {
            status = "offline recognizer failed: " + t.getClass().getSimpleName();
        } finally {
            if (r != null) try { r.close(); } catch (Throwable ignored) { }
        }
    }

    private String joinedText() {
        StringBuilder b = new StringBuilder();
        synchronized (results) { for (String j : results) { String t = VoskWords.text(j); if (!t.isEmpty()) b.append(t).append(' '); } }
        return b.toString().trim();
    }

    /** Ends the audio and waits for the last words. @return every result JSON (empty if none). */
    List<String> finish(long maxWaitMs) {
        q.offer(EOF);
        try { thread.join(maxWaitMs); } catch (InterruptedException ignored) { }
        if (thread.isAlive()) { status = "offline recognizer too slow"; cancelled = true; }
        synchronized (results) { return new ArrayList<>(results); }
    }

    void cancel() { cancelled = true; q.offer(EOF); }
}
