package com.zippy.ear;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * One-shot Android SpeechRecognizer run for the command after "Hey Zippy".
 * SpeechRecognizer must be created and driven on the main thread; the caller (the listener's worker
 * thread) blocks on a latch with a watchdog, so a recognizer that never answers can't wedge Zippy.
 */
public final class AndroidStt {
    public static final class Result {
        public final String text;
        public final int error;
        public final boolean timedOut;
        Result(String text, int error, boolean timedOut) { this.text = text; this.error = error; this.timedOut = timedOut; }

        public String describe() {
            if (timedOut) return "didn't answer";
            return "error " + error + " (" + errorName(error) + ")";
        }
    }

    private AndroidStt() {}

    public static boolean available(Context c) {
        try { return SpeechRecognizer.isRecognitionAvailable(c); } catch (Throwable t) { return false; }
    }

    public static String errorName(int e) {
        switch (e) {
            case 1: return "network timeout";
            case 2: return "network";
            case 3: return "audio";
            case 4: return "server";
            case 5: return "client";
            case 6: return "no speech";
            case 7: return "no match";
            case 8: return "recognizer busy";
            case 9: return "permission";
            case 10: return "too many requests";
            case 11: return "server disconnected";
            case 12: return "language not supported";
            case 13: return "language unavailable";
            case -1: return "could not start";
            default: return "unknown";
        }
    }

    static Intent intent(Context c, int silenceMs) {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, c.getPackageName());
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, (long) silenceMs);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, (long) silenceMs);
        return i;
    }

    /** Blocks the calling (non-main) thread until a result, an error, or the watchdog. */
    public static Result listen(final Context c, Handler main, long timeoutMs, int silenceMs, java.util.function.BooleanSupplier live) {
        final CountDownLatch done = new CountDownLatch(1);
        final String[] text = {null};
        final int[] err = {0};
        final SpeechRecognizer[] sr = {null};
        main.post(() -> {
            try {
                SpeechRecognizer r = SpeechRecognizer.createSpeechRecognizer(c);
                sr[0] = r;
                r.setRecognitionListener(new RecognitionListener() {
                    public void onReadyForSpeech(Bundle b) {}
                    public void onBeginningOfSpeech() {}
                    public void onRmsChanged(float v) {}
                    public void onBufferReceived(byte[] b) {}
                    public void onEndOfSpeech() {}
                    public void onPartialResults(Bundle b) {}
                    public void onEvent(int t, Bundle b) {}
                    public void onError(int e) {
                        if (done.getCount() > 0) { err[0] = e; done.countDown(); }
                    }
                    public void onResults(Bundle b) {
                        ArrayList<String> list = b == null ? null : b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        text[0] = list == null || list.isEmpty() ? "" : list.get(0);
                        if (text[0].trim().isEmpty()) err[0] = 7;
                        done.countDown();
                    }
                });
                r.startListening(intent(c, silenceMs));
            } catch (Throwable t) {
                err[0] = -1;
                done.countDown();
            }
        });
        boolean finished;
        long end = System.currentTimeMillis() + timeoutMs;
        try {
            do { // short slices so "Stop" cancels the recognizer right away
                finished = done.await(250, TimeUnit.MILLISECONDS);
            } while (!finished && System.currentTimeMillis() < end && live.getAsBoolean());
        } catch (InterruptedException e) {
            finished = false;
        }
        main.post(() -> {
            SpeechRecognizer r = sr[0];
            if (r != null) {
                try { r.cancel(); } catch (Throwable ignored) { }
                try { r.destroy(); } catch (Throwable ignored) { }
            }
        });
        return finished ? new Result(text[0], err[0], false) : new Result(null, 0, true);
    }
}
