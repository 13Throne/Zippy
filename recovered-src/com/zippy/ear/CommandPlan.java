package com.zippy.ear;

/**
 * What happens after an accepted "Hey Zippy":
 *  - Said in one breath ("Hey Zippy, what time is it"): Vosk already has the words -> send now.
 *  - "Hey Zippy" ... pause: hand the mic to Android's SpeechRecognizer (if on + available),
 *    otherwise keep listening with Vosk for the task (the 1.4 behaviour).
 *  - SpeechRecognizer error/timeout: fall back to Vosk so listening never wedges.
 */
public final class CommandPlan {
    public enum Next { SEND_NOW, ANDROID_STT, VOSK }
    public enum SttOutcome { SEND, NO_TASK, FALLBACK_VOSK }

    // android.speech.SpeechRecognizer error codes
    public static final int ERROR_SPEECH_TIMEOUT = 6, ERROR_NO_MATCH = 7;

    private CommandPlan() {}

    public static Next afterWake(String remainder, boolean useAndroidStt, boolean sttAvailable) {
        if (remainder != null && !remainder.trim().isEmpty()) return Next.SEND_NOW;
        return useAndroidStt && sttAvailable ? Next.ANDROID_STT : Next.VOSK;
    }

    /**
     * @param transcript best result (null on error/timeout)
     * @param error      SpeechRecognizer error code, or 0
     * @param timedOut   Zippy's own watchdog fired (recognizer never answered)
     */
    public static SttOutcome onStt(String transcript, int error, boolean timedOut) {
        if (!timedOut && transcript != null && !transcript.trim().isEmpty()) return SttOutcome.SEND;
        if (!timedOut && (error == ERROR_NO_MATCH || error == ERROR_SPEECH_TIMEOUT)) return SttOutcome.NO_TASK;
        return SttOutcome.FALLBACK_VOSK;
    }

    /** Watchdog for the recognizer: the task wait plus room for Android to finish. */
    public static long sttTimeoutMs(int waitMs) {
        return Math.max(6000, Math.min(20000, waitMs + 9000L));
    }
}
