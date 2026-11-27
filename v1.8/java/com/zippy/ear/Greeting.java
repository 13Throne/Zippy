package com.zippy.ear;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Zippy 1.8: Liora's wake greeting, played on its own thread while Zippy keeps recording, so nothing
 * you say during it is lost. Takes transient audio focus (other audio ducks/pauses for a moment) and
 * can be stopped at once when you talk over her. A Custom greeting uses text-to-speech (not stoppable).
 */
final class Greeting {
    interface Events { void started(); void ended(boolean started, boolean ok); }

    private final Context ctx;
    private final String clip, text;
    private final Greeter tts;
    private volatile MediaPlayer mp;
    private volatile boolean stop;
    private final CountDownLatch finished = new CountDownLatch(1);
    private volatile boolean focusGranted;

    Greeting(Context ctx, ZippyPrefs zp, Greeter tts) {
        this.ctx = ctx;
        this.tts = tts;
        String id = Liora.pickGreeting(zp.greetingChoice());
        clip = id;
        text = id != null ? Liora.text(id) : zp.greeting();
    }

    String text() { return text; }

    String clip() { return clip; }

    String label() { return clip != null ? "Liora \u201c" + text + "\u201d" : "\u201c" + text + "\u201d (text-to-speech)"; }

    boolean focus() { return focusGranted; }

    void start(Events ev) {
        Thread t = new Thread(() -> run(ev), "zippy-greeting");
        t.start();
    }

    private void run(Events ev) {
        AudioManager am = ctx.getSystemService(AudioManager.class);
        AudioFocusRequest req = null;
        boolean ok = false, started = false;
        try {
            AudioAttributes aa = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
            try {
                req = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(aa).build();
                focusGranted = am != null && am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            } catch (Throwable ignored) { }
            if (stop) return;
            if (clip != null) {
                MediaPlayer p = new MediaPlayer();
                try (AssetFileDescriptor fd = ctx.getAssets().openFd(Liora.asset(clip))) {
                    p.setAudioAttributes(aa);
                    p.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
                    p.prepare();
                }
                final CountDownLatch done = new CountDownLatch(1);
                final boolean[] good = {false};
                p.setOnCompletionListener(m -> { good[0] = true; done.countDown(); });
                p.setOnErrorListener((m, w, x) -> { done.countDown(); return true; });
                mp = p;
                if (stop) { p.release(); mp = null; return; }
                p.start();
                started = true;
                ev.started();
                long until = System.currentTimeMillis() + 4000;
                while (!stop && System.currentTimeMillis() < until && !done.await(20, TimeUnit.MILLISECONDS)) { }
                ok = good[0] || stop;
                try { p.stop(); } catch (Throwable ignored) { }
                p.release();
                mp = null;
            } else if (tts != null && tts.ready()) {
                started = true;
                ev.started();
                ok = tts.speakAndWait(text, 4000);
            }
        } catch (Throwable t) {
            ok = false;
        } finally {
            if (req != null && am != null) try { am.abandonAudioFocusRequest(req); } catch (Throwable ignored) { }
            ev.ended(started, ok);
            finished.countDown();
        }
    }

    /** Stop now (you started talking). */
    void stop() {
        stop = true;
        MediaPlayer p = mp;
        if (p != null) try { p.pause(); } catch (Throwable ignored) { }
    }

    boolean stopped() { return stop; }

    void await(long ms) { try { finished.await(ms, TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) { } }
}
