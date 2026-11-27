package com.zippy.ear;

import android.content.Context;
import com.k2fsa.sherpa.onnx.SileroVadModelConfig;
import com.k2fsa.sherpa.onnx.TenVadModelConfig;
import com.k2fsa.sherpa.onnx.Vad;
import com.k2fsa.sherpa.onnx.VadModelConfig;

/**
 * Zippy 1.8: Sherpa-ONNX Silero voice-activity detector (assets/vad/silero_vad.onnx, 0.6 MB) on the
 * same onnxruntime as the wake word. One 512-sample frame (32 ms) in, "someone is talking" out.
 */
final class SileroGate {
    static final String MODEL = "vad/silero_vad.onnx";
    static final float THRESHOLD = 0.5f, MIN_SILENCE = 0.25f, MIN_SPEECH = 0.15f, MAX_SPEECH = 60f;
    private final Vad vad;

    private SileroGate(Vad v) { vad = v; }

    static VadModelConfig config(String model) {
        SileroVadModelConfig s = new SileroVadModelConfig();
        s.setModel(model);
        s.setThreshold(THRESHOLD);
        s.setMinSilenceDuration(MIN_SILENCE);
        s.setMinSpeechDuration(MIN_SPEECH);
        s.setWindowSize(Capture.FRAME);
        s.setMaxSpeechDuration(MAX_SPEECH);
        VadModelConfig c = new VadModelConfig();
        c.setSileroVadModelConfig(s);
        c.setTenVadModelConfig(new TenVadModelConfig());
        c.setSampleRate(Capture.RATE);
        c.setNumThreads(1);
        c.setProvider("cpu");
        c.setDebug(false);
        return c;
    }

    static SileroGate load(Context ctx) { return new SileroGate(new Vad(ctx.getAssets(), config(MODEL))); }

    boolean frame(float[] f512) {
        vad.acceptWaveform(f512);
        boolean s = vad.isSpeechDetected();
        while (!vad.empty()) vad.pop(); // Zippy keeps its own audio; drop Sherpa's copies
        return s;
    }

    void reset() { vad.reset(); }

    void release() { try { vad.release(); } catch (Throwable ignored) { } }
}
