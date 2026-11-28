package com.zippy.ear;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Zippy 1.81 on-device check (app_process on the emulator, the installed APK's classes + native libs):
 * the real KwsLab (Sherpa-ONNX KeywordSpotter + the same model as a greedy OnlineRecognizer, from files)
 * runs WakeTuner.tune on takes and ordinary sentences, exactly as PhraseActivity does.
 * Usage: PhraseDeviceTest <model-dir/> <phrase> <takes...> -- <others...>
 */
public class PhraseDeviceTest {
    static float[] wav(String p) throws Exception {
        byte[] b;
        try (DataInputStream in = new DataInputStream(new FileInputStream(p))) { b = new byte[(int) new File(p).length()]; in.readFully(b); }
        int off = 12;
        while (off + 8 < b.length) {
            String id = new String(b, off, 4, "US-ASCII");
            int len = (b[off + 4] & 255) | (b[off + 5] & 255) << 8 | (b[off + 6] & 255) << 16 | (b[off + 7] & 255) << 24;
            if (id.equals("data")) {
                int n = Math.min(len, b.length - off - 8) / 2;
                float[] x = new float[n];
                for (int i = 0; i < n; i++) x[i] = (short) ((b[off + 8 + 2 * i] & 255) | (b[off + 9 + 2 * i] << 8)) / 32768f;
                return x;
            }
            off += 8 + len;
        }
        throw new IllegalArgumentException("no data chunk: " + p);
    }

    public static void main(String[] a) throws Exception {
        String dir = a[0], phrase = a[1];
        List<float[]> takes = new ArrayList<>(), others = new ArrayList<>();
        boolean o = false;
        for (int i = 2; i < a.length; i++) {
            if (a[i].equals("--")) { o = true; continue; }
            (o ? others : takes).add(wav(a[i]));
        }
        BpeTokenizer tok = BpeTokenizer.load(new FileInputStream(dir + "bpe.vocab"));
        String[] canon = tok.encode(phrase);
        long t0 = System.currentTimeMillis();
        KwsLab lab = new KwsLab(null, dir);
        long t1 = System.currentTimeMillis();
        for (int i = 0; i < takes.size(); i++) System.out.println("heard take " + (i + 1) + ": " + String.join(" ", lab.heard(takes.get(i))));
        WakeTuner.Result r = WakeTuner.tune(lab, canon, takes, others);
        long t2 = System.currentTimeMillis();
        System.out.println("tokens: " + String.join(" ", canon));
        StringBuilder v = new StringBuilder();
        for (String[] x : r.variants) v.append(v.length() > 0 ? " | " : "").append(String.join(" ", x));
        System.out.println("variants: " + v);
        System.out.println("threshold: " + r.threshold + " takes_hit: " + r.takesHit() + "/" + r.takeHit.length + " others_hit: " + r.othersHit() + "/" + r.otherHit.length
                + " learned: " + r.learned + " dropped: " + r.dropped);
        System.out.println("load_ms: " + (t1 - t0) + " tune_ms: " + (t2 - t1));
        lab.release();
    }
}
