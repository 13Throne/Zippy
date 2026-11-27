import com.k2fsa.sherpa.onnx.*;
import com.zippy.ear.KwsTuning;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

/**
 * On-device check of the exact Sherpa-ONNX path Zippy 1.7 uses (official AAR Kotlin classes + JNI +
 * libonnxruntime, KwsTuning keywords on top of the shipped sentinel keywords.txt, 2048-sample chunks,
 * reset after a hit). Run with app_process against the installed APK (see run_device_test.sh).
 * Args: <model dir with encoder/decoder/joiner/tokens/keywords> <sensitivity> <wav>...  (16 kHz mono s16)
 */
public class KwsDeviceTest {
    public static void main(String[] a) throws Exception {
        String dir = a[0];
        int sens = Integer.parseInt(a[1]);
        FeatureConfig feat = new FeatureConfig(); feat.setSampleRate(16000); feat.setFeatureDim(80);
        OnlineTransducerModelConfig t = new OnlineTransducerModelConfig();
        t.setEncoder(dir + "/encoder.int8.onnx"); t.setDecoder(dir + "/decoder.int8.onnx"); t.setJoiner(dir + "/joiner.int8.onnx");
        OnlineModelConfig m = new OnlineModelConfig(); m.setTransducer(t); m.setTokens(dir + "/tokens.txt"); m.setNumThreads(1); m.setProvider("cpu");
        KeywordSpotterConfig c = new KeywordSpotterConfig(); c.setFeatConfig(feat); c.setModelConfig(m);
        c.setMaxActivePaths(4); c.setKeywordsFile(dir + "/keywords.txt"); c.setKeywordsScore(1.55f); c.setKeywordsThreshold(0.23f); c.setNumTrailingBlanks(1);
        long t0 = System.nanoTime();
        KeywordSpotter k = new KeywordSpotter(null, c);
        System.out.printf(Locale.US, "loaded in %.0f ms; keywords: %d lines%n", (System.nanoTime() - t0) / 1e6, KwsTuning.lines(sens).size());
        String kw = KwsTuning.keywords(sens);
        double audio = 0; long dec = 0;
        for (int i = 2; i < a.length; i++) {
            byte[] b = Files.readAllBytes(Paths.get(a[i]));
            ShortBuffer sb = ByteBuffer.wrap(b, 44, b.length - 44).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
            short[] pcm = new short[sb.remaining()]; sb.get(pcm);
            audio += pcm.length / 16000.0;
            OnlineStream s = k.createStream(kw);
            List<String> hits = new ArrayList<>();
            long d0 = System.nanoTime();
            for (int off = 0; off < pcm.length; off += 2048) {
                int n = Math.min(2048, pcm.length - off);
                float[] f = new float[n];
                for (int j = 0; j < n; j++) f[j] = pcm[off + j] / 32768f;
                s.acceptWaveform(f, 16000);
                while (k.isReady(s)) {
                    k.decode(s);
                    String r = k.getResult(s).getKeyword();
                    if (r != null && !r.isEmpty()) { if (!KwsTuning.SENTINEL_NAME.equals(r)) hits.add(r + "@" + String.format(Locale.US, "%.2f", off / 16000.0)); k.reset(s); }
                }
            }
            dec += System.nanoTime() - d0;
            s.release();
            System.out.println(new File(a[i]).getName() + "\t" + (hits.isEmpty() ? "-" : String.join(",", hits)));
        }
        System.out.printf(Locale.US, "real-time factor %.3f (%.1f s audio)%n", dec / 1e9 / audio, audio);
        k.release();
    }
}
