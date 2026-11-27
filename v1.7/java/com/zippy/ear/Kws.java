package com.zippy.ear;

import android.content.Context;
import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.KeywordSpotter;
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig;
import com.k2fsa.sherpa.onnx.KeywordSpotterResult;
import com.k2fsa.sherpa.onnx.OnlineModelConfig;
import com.k2fsa.sherpa.onnx.OnlineStream;
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig;

/**
 * Zippy 1.7 wake word: Sherpa-ONNX keyword spotter (zipformer transducer, gigaspeech 3.3M, int8),
 * loaded straight from assets/kws/ with the AssetManager. One stream; its keywords follow the
 * Sensitivity slider (KwsTuning) and are swapped by recreating the stream when it changes.
 */
final class Kws {
    static final String DIR = "kws/";
    static final String ENCODER = DIR + "encoder.int8.onnx";
    static final String DECODER = DIR + "decoder.int8.onnx";
    static final String JOINER = DIR + "joiner.int8.onnx";
    static final String TOKENS = DIR + "tokens.txt";
    static final String KEYWORDS = DIR + "keywords.txt"; // sentinel only, see KwsTuning.SENTINEL
    static final int SAMPLE_RATE = 16000;

    private final KeywordSpotter spotter;
    private OnlineStream stream;
    private String keywords;
    private float[] scratch = new float[0];

    private Kws(KeywordSpotter spotter) { this.spotter = spotter; }

    static KeywordSpotterConfig config() {
        FeatureConfig feat = new FeatureConfig();
        feat.setSampleRate(SAMPLE_RATE);
        feat.setFeatureDim(80);
        OnlineTransducerModelConfig t = new OnlineTransducerModelConfig();
        t.setEncoder(ENCODER);
        t.setDecoder(DECODER);
        t.setJoiner(JOINER);
        OnlineModelConfig m = new OnlineModelConfig();
        m.setTransducer(t);
        m.setTokens(TOKENS);
        m.setNumThreads(1);
        m.setProvider("cpu");
        m.setDebug(false);
        KeywordSpotterConfig c = new KeywordSpotterConfig();
        c.setFeatConfig(feat);
        c.setModelConfig(m);
        c.setMaxActivePaths(4);
        c.setKeywordsFile(KEYWORDS);
        c.setKeywordsScore((float) KwsTuning.boost(Sensitivity.DEFAULT));
        c.setKeywordsThreshold((float) KwsTuning.threshold(Sensitivity.DEFAULT));
        c.setNumTrailingBlanks(1);
        return c;
    }

    static Kws load(Context ctx, int sensitivity) {
        Kws k = new Kws(new KeywordSpotter(ctx.getAssets(), config()));
        k.setSensitivity(sensitivity);
        return k;
    }

    /** Re-creates the stream only when the keyword set actually changes. */
    void setSensitivity(int sensitivity) {
        String kw = KwsTuning.keywords(sensitivity);
        if (kw.equals(keywords) && stream != null) return;
        OnlineStream old = stream;
        stream = spotter.createStream(kw);
        keywords = kw;
        if (old != null) old.release();
    }

    /** Feeds 16 kHz PCM; returns the spotted keyword ("HEY_ZIPPY" / "ZIPPY") or null. */
    String accept(short[] pcm, int n) {
        if (scratch.length != n) scratch = new float[n];
        for (int i = 0; i < n; i++) scratch[i] = pcm[i] / 32768f;
        stream.acceptWaveform(scratch, SAMPLE_RATE);
        String hit = null;
        while (spotter.isReady(stream)) {
            spotter.decode(stream);
            KeywordSpotterResult r = spotter.getResult(stream);
            String k = r == null ? null : r.getKeyword();
            if (k != null && !k.isEmpty()) {
                if (!KwsTuning.SENTINEL_NAME.equals(k)) hit = k;
                spotter.reset(stream); // required after a detection
            }
        }
        return hit;
    }

    /** Forget partial audio (after the mic was paused, a hand-off or Liora talking). */
    void reset() {
        OnlineStream old = stream;
        stream = spotter.createStream(keywords);
        if (old != null) old.release();
    }

    void release() {
        try { if (stream != null) stream.release(); } catch (Throwable ignored) { }
        try { spotter.release(); } catch (Throwable ignored) { }
    }
}
