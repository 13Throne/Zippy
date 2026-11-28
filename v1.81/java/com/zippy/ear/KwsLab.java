package com.zippy.ear;

import android.content.Context;
import com.k2fsa.sherpa.onnx.EndpointConfig;
import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.KeywordSpotter;
import com.k2fsa.sherpa.onnx.KeywordSpotterResult;
import com.k2fsa.sherpa.onnx.OnlineModelConfig;
import com.k2fsa.sherpa.onnx.OnlineRecognizer;
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OnlineRecognizerResult;
import com.k2fsa.sherpa.onnx.OnlineStream;
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig;

/**
 * Zippy 1.81: the Sherpa-ONNX pieces the wake-phrase trainer needs, on its own (the listener's mic is
 * held while PhraseActivity is open). A keyword spotter built exactly like the listener's (Kws.config)
 * counts hits for any keyword lines, and the SAME KWS model run as a plain greedy transducer recognizer
 * reports what it hears in a take (WakeTuner.learn). Audio is fed in 2048-sample chunks like the listener.
 */
public final class KwsLab implements WakeTuner.Detector {
    private final KeywordSpotter spotter;
    private OnlineRecognizer rec;
    private final android.content.res.AssetManager am;
    private final String dir;

    KwsLab(Context ctx) { this(ctx.getApplicationContext().getAssets(), Kws.DIR); }

    /** am = null and dir = a folder on disk: the emulator test (app_process) runs the same code. */
    public KwsLab(android.content.res.AssetManager am, String dir) {
        this.am = am;
        this.dir = dir;
        spotter = new KeywordSpotter(am, Kws.config(dir));
    }

    private OnlineRecognizer recognizer() {
        if (rec != null) return rec;
        FeatureConfig feat = new FeatureConfig();
        feat.setSampleRate(Kws.SAMPLE_RATE);
        feat.setFeatureDim(80);
        OnlineTransducerModelConfig t = new OnlineTransducerModelConfig();
        t.setEncoder(dir + "encoder.int8.onnx");
        t.setDecoder(dir + "decoder.int8.onnx");
        t.setJoiner(dir + "joiner.int8.onnx");
        OnlineModelConfig m = new OnlineModelConfig();
        m.setTransducer(t);
        m.setTokens(dir + "tokens.txt");
        m.setNumThreads(1);
        m.setProvider("cpu");
        m.setDebug(false);
        OnlineRecognizerConfig c = new OnlineRecognizerConfig();
        c.setFeatConfig(feat);
        c.setModelConfig(m);
        c.setEndpointConfig(new EndpointConfig());
        c.setEnableEndpoint(false);
        c.setDecodingMethod("greedy_search");
        rec = new OnlineRecognizer(am, c);
        return rec;
    }

    @Override
    public int hits(String keywords, float[] audio) {
        OnlineStream s = spotter.createStream(keywords);
        int n = 0;
        try {
            for (int i = 0; i < audio.length; i += 2048) {
                int len = Math.min(2048, audio.length - i);
                float[] chunk = new float[len];
                System.arraycopy(audio, i, chunk, 0, len);
                s.acceptWaveform(chunk, Kws.SAMPLE_RATE);
                n += drain(s);
            }
            s.acceptWaveform(new float[4800], Kws.SAMPLE_RATE); // 0.3 s of silence lets a final hit land
            n += drain(s);
        } finally {
            s.release();
        }
        return n;
    }

    private int drain(OnlineStream s) {
        int n = 0;
        while (spotter.isReady(s)) {
            spotter.decode(s);
            KeywordSpotterResult r = spotter.getResult(s);
            String k = r == null ? null : r.getKeyword();
            if (k != null && !k.isEmpty()) {
                if (!KwsTuning.SENTINEL_NAME.equals(k)) n++;
                spotter.reset(s);
            }
        }
        return n;
    }

    @Override
    public String[] heard(float[] audio) {
        OnlineRecognizer r = recognizer();
        OnlineStream s = r.createStream("");
        try {
            s.acceptWaveform(audio, Kws.SAMPLE_RATE);
            s.acceptWaveform(new float[9600], Kws.SAMPLE_RATE);
            s.inputFinished();
            while (r.isReady(s)) r.decode(s);
            OnlineRecognizerResult res = r.getResult(s);
            String[] t = res == null || res.getTokens() == null ? new String[0] : res.getTokens();
            String[] out = new String[t.length];
            for (int i = 0; i < t.length; i++) out[i] = t[i].startsWith(" ") ? "\u2581" + t[i].substring(1) : t[i];
            return out;
        } finally {
            s.release();
        }
    }

    /** Live test: feed mic chunks, get true on a hit. */
    final class Live {
        private OnlineStream s;
        private final String kw;
        Live(String kw) { this.kw = kw; s = spotter.createStream(kw); }
        boolean accept(short[] pcm, int n) {
            float[] f = new float[n];
            for (int i = 0; i < n; i++) f[i] = pcm[i] / 32768f;
            s.acceptWaveform(f, Kws.SAMPLE_RATE);
            return drain(s) > 0;
        }
        void close() { try { s.release(); } catch (Throwable ignored) { } }
    }

    Live live(String keywords) { return new Live(keywords); }

    public void release() {
        try { spotter.release(); } catch (Throwable ignored) { }
        try { if (rec != null) rec.release(); } catch (Throwable ignored) { }
    }
}
