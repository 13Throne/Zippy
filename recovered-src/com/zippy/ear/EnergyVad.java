package com.zippy.ear;

/**
 * Zippy 1.8: simple energy voice-activity detector, used only when Sherpa-ONNX (Silero VAD) can't run
 * on the phone. Tracks the noise floor and calls a 32 ms frame speech when it is clearly above it,
 * with the same kind of start delay / hangover as Silero (so Capture's timings stay the same).
 */
public final class EnergyVad {
    private float floor = 0.003f;
    private int above, hang;
    private boolean speech;
    static final int START_FRAMES = 4, HANG_FRAMES = 8; // ~130 ms to start, ~250 ms hangover
    static final float RATIO = 3.2f, MIN_RMS = 0.006f;

    public static float rms(float[] f, int n) {
        double s = 0;
        for (int i = 0; i < n; i++) s += f[i] * f[i];
        return n == 0 ? 0f : (float) Math.sqrt(s / n);
    }

    public boolean frame(float rms) {
        boolean loud = rms > Math.max(floor * RATIO, MIN_RMS);
        if (loud) { above++; hang = HANG_FRAMES; } else { above = 0; if (hang > 0) hang--; }
        if (!speech && above >= START_FRAMES) speech = true;
        else if (speech && !loud && hang == 0) speech = false;
        // floor: follows quiet frames quickly, loud ones very slowly
        if (!loud) floor = floor * 0.9f + rms * 0.1f; else floor = floor * 0.999f + rms * 0.001f;
        floor = Math.max(0.0005f, Math.min(0.05f, floor));
        return speech;
    }

    public void reset() { above = 0; hang = 0; speech = false; }
}
