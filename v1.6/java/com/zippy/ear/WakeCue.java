package com.zippy.ear;

/** When and how the wake cue plays. Pure decisions; ListenService does the Android work. */
public final class WakeCue {
    public enum Action { VOICE, BEEP, VIBRATE, NONE }

    private WakeCue() {}

    /** Instant feedback the moment the voice check accepts "Hey Zippy" (user may still be talking). */
    public static Action atAccept(String cue) {
        if (ZippyPrefs.CUE_BEEP.equals(cue)) return Action.BEEP;
        if (ZippyPrefs.CUE_VIBRATE.equals(cue)) return Action.VIBRATE;
        return Action.NONE; // the voice greeting waits until the mic is paused (see beforeCommand)
    }

    /** Right before Zippy listens for the command, with Zippy's own mic stopped. */
    public static Action beforeCommand(String cue, boolean voiceReady) {
        if (!ZippyPrefs.CUE_VOICE.equals(cue)) return Action.NONE;
        return voiceReady ? Action.VOICE : Action.BEEP; // no clip and no TTS: fall back to a beep
    }

    /** After speak(): if it failed or timed out, still give the user a cue. */
    public static Action afterSpeak(boolean spokeOk) {
        return spokeOk ? Action.NONE : Action.BEEP;
    }

    /** 1.6: what plays once a task is accepted by the webhook. */
    public static Action confirmAction(String cue) {
        if (ZippyPrefs.CUE_VOICE.equals(cue)) return Action.VOICE;
        if (ZippyPrefs.CUE_BEEP.equals(cue)) return Action.BEEP;
        if (ZippyPrefs.CUE_VIBRATE.equals(cue)) return Action.VIBRATE;
        return Action.NONE;
    }

    /** Cues.fire mode for an action (Cues.VIBRATE=1, SOUND=2). */
    public static int cuesMode(Action a) {
        return a == Action.BEEP ? 2 : a == Action.VIBRATE ? 1 : 0;
    }
}
