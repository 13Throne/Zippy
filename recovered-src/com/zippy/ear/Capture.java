package com.zippy.ear;

/**
 * Zippy 1.8: when the task after "Hey Zippy" has been said. Pure logic, no Android.
 *
 * The listener never closes its mic between the wake word and the send. It records from about 2 s
 * before the wake word and feeds this class one voice-activity frame (512 samples, 32 ms at 16 kHz)
 * at a time, starting at the moment the wake word was spotted. This class decides:
 *  - Greeting: after DECIDE_MS, greet only if you haven't started talking yet; otherwise skip it.
 *  - Barge-in: while Liora talks, speech clearly louder than her echo stops her (the audio is kept).
 *  - The end: SILENCE after you talked and then were quiet for endSilenceMs (pauses shorter than that
 *    never end the task), NO_SPEECH if you never started within noSpeechMs after the greeting, or MAX
 *    after maxMs. Nothing is ever cut at the first short pause.
 */
public final class Capture {
    public static final int RATE = 16000, FRAME = 512, FRAME_MS = 32;
    /** How long after the wake word Zippy waits to see if you're already talking before greeting. */
    public static final int DECIDE_MS = 400;
    /** At DECIDE_MS you count as "already talking" only with speech in the last this-many ms. */
    public static final int TALKING_NOW_MS = 160;
    /** Speaker tail after the greeting finished that still counts as Liora (echo), not you. */
    public static final int ECHO_TAIL_MS = 160;
    /** A burst shorter than this, followed by a long pause, is noise (e.g. the end of "Zippy"), not the task. */
    public static final int MIN_VOICED_MS = 350;
    public static final int MAX_MS = 45000;

    public enum Greet { PENDING, GREET, SKIP, NONE }
    public enum End { NONE, SILENCE, NO_SPEECH, MAX }

    public static final class Params {
        public int endSilenceMs = 2000, noSpeechMs = 4000, maxMs = MAX_MS;
        public boolean voiceGreeting = true;   // wake cue is Liora's voice
        public boolean bargeIn = true;         // stop Liora when you talk over her
        public float bargeRatio = 3.0f;        // your voice vs her echo (RMS)
        public int bargeMs = 288;              // that loud, for this long (9 frames)
        public int vadHangMs = 0;              // counted from the VAD's "stopped" (~0.25 s after your last word), so real silence = endSilence + ~0.25 s
    }

    /** "Wait for the task" slider (1-8 s): silence that ends the task. 4 s -> 1.8 s. */
    public static int endSilenceMs(int waitMs) { return clamp(Math.round(waitMs * 0.5f), 1500, 3500); }
    /** How long Zippy waits for you to start talking. */
    public static int noSpeechMs(int waitMs) { return clamp(waitMs, 1000, 8000); }

    static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private final Params p;
    private int frames;                 // frames since the wake word
    private Greet greet = Greet.PENDING;
    private boolean playing, played;
    private int playStart = -1, playEnd = -1, tailUntil = -1;
    private float[] echo = new float[64];
    private int echoN;
    private int bargeRun, bargeAt = -1;
    private int onset = -1, lastSpeech = -1, voiced, waitFrom = 0;
    private int firstSpeech = -1;
    private End end = End.NONE;
    private int endAt = -1;

    public Capture(Params p) {
        this.p = p;
        if (!p.voiceGreeting) greet = Greet.NONE;
    }

    /** One VAD frame. @param speech the VAD says someone is talking; @param rms frame RMS (0..1). */
    public void frame(boolean speech, float rms) {
        if (end != End.NONE) return;
        frames++;
        int ms = frames * FRAME_MS;
        boolean echoTime = playing || frames <= tailUntil;
        if (echoTime && onset < 0) {
            if (playing && p.bargeIn) barge(speech, rms);
        } else {
            if (speech) {
                if (onset < 0) { onset = frames; voiced = 0; if (firstSpeech < 0) firstSpeech = frames; }
                voiced++;
                lastSpeech = frames;
            }
        }
        if (greet == Greet.PENDING && ms >= DECIDE_MS) {
            boolean now = onset >= 0 && (frames - lastSpeech) * FRAME_MS <= TALKING_NOW_MS;
            if (onset >= 0 && !now) { onset = -1; voiced = 0; firstSpeech = -1; } // just the tail of "Hey Zippy"
            greet = now ? Greet.SKIP : Greet.GREET;
            if (greet == Greet.SKIP) waitFrom = frames;
        }
        if (greet == Greet.GREET && !playing && !played && ms > DECIDE_MS + 1500) greetingCancelled(); // never started
        if (ms >= p.maxMs) { finish(End.MAX); return; }
        if (onset >= 0) {
            int silentMs = (frames - lastSpeech) * FRAME_MS;
            if (silentMs >= Math.max(FRAME_MS, p.endSilenceMs - p.vadHangMs)) {
                if (voiced * FRAME_MS >= MIN_VOICED_MS) { finish(End.SILENCE); return; }
                onset = -1; voiced = 0; firstSpeech = -1; // a blip, not the task: keep waiting
                waitFrom = Math.max(waitFrom, Math.min(frames, lastSpeech));
            }
        } else if (waitingCounts(echoTime)) {
            if ((frames - waitFrom) * FRAME_MS >= p.noSpeechMs) finish(End.NO_SPEECH);
        }
    }

    /** The "did you start talking yet" clock runs only once Liora is done (or skipped). */
    private boolean waitingCounts(boolean echoTime) {
        if (echoTime || greet == Greet.PENDING) return false;
        return greet != Greet.GREET || played;
    }

    private void barge(boolean speech, float rms) {
        if (echoN == echo.length) echo = java.util.Arrays.copyOf(echo, echoN * 2);
        float ref = echoN >= 4 ? median() : Float.MAX_VALUE;
        if (speech && rms >= p.bargeRatio * ref && rms >= 0.004f) bargeRun++;
        else { bargeRun = 0; echo[echoN++] = rms; } // only non-candidate frames teach the echo level
        if (bargeRun * FRAME_MS >= p.bargeMs) {
            bargeAt = frames;
            onset = frames - bargeRun + 1;
            if (firstSpeech < 0) firstSpeech = onset;
            voiced = bargeRun;
            lastSpeech = frames;
        }
    }

    private float median() {
        float[] c = java.util.Arrays.copyOf(echo, echoN);
        java.util.Arrays.sort(c);
        return c[c.length / 2];
    }

    private void finish(End e) { end = e; endAt = frames; }

    /** The greeting started playing (call from the mic thread). */
    public void greetingStarted() { if (!playing && !played) { playing = true; playStart = frames; } }

    /** The greeting finished or was stopped. */
    public void greetingEnded() {
        if (!playing) { played = true; return; }
        playing = false; played = true; playEnd = frames;
        tailUntil = frames + ECHO_TAIL_MS / FRAME_MS;
        waitFrom = tailUntil;
    }

    /** The greeting could not play at all (no clip, no TTS), or was cancelled before it started. */
    public void greetingCancelled() { if (playing) { greetingEnded(); return; } played = true; waitFrom = Math.max(waitFrom, frames); }

    /** You started talking while Liora was (about to be) speaking: stop her. */
    public boolean wantsStopGreeting() { return onset >= 0 && (playing || greet == Greet.GREET && !played); }

    public Greet greet() { return greet; }
    public End end() { return end; }
    public boolean done() { return end != End.NONE; }
    public int frames() { return frames; }
    public int endMs() { return endAt * FRAME_MS; }
    public int speechStartMs() { return firstSpeech < 0 ? -1 : (firstSpeech - 1) * FRAME_MS; }
    public int speechEndMs() { return lastSpeech < 0 ? -1 : lastSpeech * FRAME_MS; }
    public int bargeInMs() { return bargeAt < 0 ? -1 : bargeAt * FRAME_MS; }
    public int greetingStartMs() { return playStart < 0 ? -1 : playStart * FRAME_MS; }
    public int greetingEndMs() { return playEnd < 0 ? -1 : playEnd * FRAME_MS; }
    public boolean greetingPlayed() { return played && playStart >= 0; }
    public boolean talking() { return onset >= 0; }
}
