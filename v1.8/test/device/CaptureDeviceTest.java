package com.zippy.ear;

import com.k2fsa.sherpa.onnx.Vad;
import java.io.*;
import java.lang.reflect.Constructor;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

/**
 * On-device check of Zippy 1.8's end-of-speech path: the app's own SileroGate (Sherpa-ONNX Silero VAD over
 * JNI + onnxruntime, from the INSTALLED APK) feeding the app's own Capture, exactly as ListenService does
 * (0.5 s warm-up before the wake moment, then 512-sample frames). The greeting start/length per file come
 * from the desktop run so both sides see the same audio. Writes one flags file per wav next to it and
 * prints the decisions. Run with app_process (see run_device_test.sh).
 * Args: <silero_vad.onnx> (<wav> <greetStartFrame> <greetLenFrames>)...   wav = 16 kHz mono s16, starts 0.5 s before the wake moment
 */
public class CaptureDeviceTest {
    public static void main(String[] a) throws Exception {
        Constructor<SileroGate> k = SileroGate.class.getDeclaredConstructor(Vad.class);
        k.setAccessible(true);
        long t0 = System.nanoTime();
        SileroGate gate = k.newInstance(new Vad(null, SileroGate.config(a[0])));
        System.out.printf(Locale.US, "Silero VAD loaded in %.0f ms%n", (System.nanoTime() - t0) / 1e6);
        double audio = 0; long busy = 0;
        for (int i = 1; i + 2 < a.length; i += 3) {
            byte[] b = Files.readAllBytes(Paths.get(a[i]));
            ShortBuffer sb = ByteBuffer.wrap(b, 44, b.length - 44).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
            short[] pcm = new short[sb.remaining()]; sb.get(pcm);
            audio += pcm.length / 16000.0;
            int gStart = Integer.parseInt(a[i + 1]), gLen = Integer.parseInt(a[i + 2]);
            Capture.Params p = new Capture.Params();
            p.voiceGreeting = true; p.bargeIn = true; p.bargeRatio = 3f;
            p.endSilenceMs = 1800; p.noSpeechMs = 4000;
            Capture c = new Capture(p);
            gate.reset();
            float[] f = new float[Capture.FRAME];
            int warm = ListenService.VAD_WARM, frame = 0, decision = -1, started = -1, gEnd = -1, stopAt = -1;
            boolean cancelled = false;
            StringBuilder flags = new StringBuilder();
            long d0 = System.nanoTime();
            for (int off = 0; off + Capture.FRAME <= pcm.length && !c.done(); off += Capture.FRAME) {
                for (int j = 0; j < Capture.FRAME; j++) f[j] = pcm[off + j] / 32768f;
                boolean s = gate.frame(f);
                if (off < warm) continue;                       // warm-up, not counted (ListenService.warming)
                frame++;
                if (gStart > 0 && frame == gStart && !cancelled) { started = frame; gEnd = frame + gLen; c.greetingStarted(); }
                if (started > 0 && frame == gEnd) c.greetingEnded();
                Capture.Greet before = c.greet();
                float rms = EnergyVad.rms(f, Capture.FRAME);
                c.frame(s, rms);
                flags.append(s ? '1' : '0');
                if (before == Capture.Greet.PENDING && c.greet() != before) decision = frame;
                if (c.wantsStopGreeting()) {
                    if (started > 0 && frame < gEnd && stopAt < 0) { stopAt = frame; gEnd = frame + 2; }
                    else if (started < 0 && !cancelled) { cancelled = true; c.greetingCancelled(); }
                }
            }
            busy += System.nanoTime() - d0;
            Files.write(Paths.get(a[i] + ".flags"), flags.toString().getBytes("UTF-8"));
            System.out.println(String.join("\t", new File(a[i]).getName(), c.greet().name(), "" + decision, "" + stopAt, c.end().name(), "" + c.endMs()));
        }
        System.out.printf(Locale.US, "VAD+Capture real-time factor %.4f (%.1f s audio)%n", busy / 1e9 / audio, audio);
        gate.release();
    }
}
