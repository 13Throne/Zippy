package com.zippy.ear;

/**
 * Zippy 1.81: the double-press rule for volume down, pure logic (VolumeKeyService feeds it).
 *
 * Trade-off chosen: never delay or swallow a FIRST press. Every first press goes straight to Android,
 * so volume down works exactly as before (no lag, holding it still ramps). A second press that lands
 * within WINDOW_MS of the first press is consumed (with its repeats and its key-up) and toggles
 * listening; the service then puts back the one volume step the first press took. The cost: a quick
 * double-tap of volume down no longer lowers the volume twice, and the first press's volume panel still
 * flashes on screen. The alternative (hold every first press for 400 ms and re-inject it) would make
 * every normal volume press lag and break press-and-hold, and an accessibility service can't re-inject
 * a real key press anyway.
 */
public final class VolumeKeys {
    public static final long WINDOW_MS = 400;
    public enum Verdict { PASS, CONSUME, TOGGLE }

    private long firstDownAt = -1;
    private boolean consuming;

    /** @param down ACTION_DOWN (else ACTION_UP); repeat = getRepeatCount(); t = event time (ms). */
    public Verdict onVolumeDown(boolean down, int repeat, long t) {
        if (consuming) {
            if (!down) consuming = false; // the second press's key-up ends it
            return Verdict.CONSUME;
        }
        if (!down) return Verdict.PASS;
        if (repeat > 0) { firstDownAt = -1; return Verdict.PASS; } // holding = normal volume ramp
        if (firstDownAt >= 0 && t - firstDownAt <= WINDOW_MS) {
            firstDownAt = -1;
            consuming = true;
            return Verdict.TOGGLE;
        }
        firstDownAt = t;
        return Verdict.PASS;
    }

    /** Any other key breaks a pending double-press. */
    public void otherKey() { if (!consuming) firstDownAt = -1; }

    public boolean pendingFirst() { return firstDownAt >= 0; }
}
