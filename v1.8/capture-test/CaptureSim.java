import com.zippy.ear.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Desktop driver for the exact Zippy 1.8 Java logic (Capture, TaskClean, EchoGuard, VoskWords) over
 * VAD frames / recognizer output computed by capture_eval.py with the same Sherpa-ONNX Silero VAD
 * and Vosk model the app uses.
 *   sim   <jobs.tsv> <out.tsv>   id flags greet barge ratio endSil noSpeech greetStart greetLen [envFile clip [echoGainPrior known]]   (greetStart -2 = at decision + 3 frames)
 *   clean <jobs.tsv> <out.tsv>   id voskResults hitSec greetingText|- gs ge androidText|- sens
 */
public class CaptureSim {
    public static void main(String[] a) throws Exception {
        List<String> out = new ArrayList<>();
        for (String line : Files.readAllLines(Paths.get(a[1]))) {
            if (line.trim().isEmpty()) continue;
            String[] f = line.split("\t", -1);
            out.add(a[0].equals("sim") ? sim(f) : clean(f));
        }
        Files.write(Paths.get(a[2]), out);
    }

    static String sim(String[] f) throws IOException {
        List<String> fl = Files.readAllLines(Paths.get(f[1]));
        Capture.Params p = new Capture.Params();
        p.voiceGreeting = f[2].equals("1");
        p.bargeIn = f[3].equals("1");
        p.bargeRatio = Float.parseFloat(f[4]);
        p.endSilenceMs = Integer.parseInt(f[5]);
        p.noSpeechMs = Integer.parseInt(f[6]);
        int gStart = Integer.parseInt(f[7]), gLen = Integer.parseInt(f[8]);
        if (f.length > 10 && !f[9].equals("-")) p.echoEnv = env(f[9], f[10]);
        if (f.length > 12) { p.echoGainPrior = Float.parseFloat(f[11]); p.echoGainKnown = f[12].equals("1"); }
        Capture c = new Capture(p);
        int decision = -1, started = -1, ended = -1, stopAt = -1, gEnd = -1;
        boolean cancelled = false;
        for (int i = 0; i < fl.size() && !c.done(); i++) {
            int frame = i + 1;
            if (gStart == -2 && decision > 0 && c.greet() == Capture.Greet.GREET && started < 0 && !cancelled && frame == decision + 3) { started = frame; gEnd = frame + gLen; c.greetingStarted(); }
            if (gStart >= 0 && frame == gStart && !cancelled) { started = frame; gEnd = frame + gLen; c.greetingStarted(); }
            if (started > 0 && ended < 0 && frame == gEnd) { ended = frame; c.greetingEnded(); }
            String[] s = fl.get(i).trim().split(" ");
            Capture.Greet before = c.greet();
            c.frame(s[0].equals("1"), Float.parseFloat(s[1]));
            if (before == Capture.Greet.PENDING && c.greet() != Capture.Greet.PENDING) decision = frame;
            if (c.wantsStopGreeting()) {
                if (started > 0 && ended < 0 && stopAt < 0) { stopAt = frame; gEnd = frame + 2; }
                else if (started < 0 && !cancelled) { cancelled = true; c.greetingCancelled(); }
            }
        }
        return String.join("\t", f[0], c.greet().name(), "" + decision, "" + started, "" + (ended > 0 ? ended : gEnd), "" + stopAt,
                "" + (c.bargeInMs() / Capture.FRAME_MS), c.end().name(), "" + c.endMs(), "" + c.speechStartMs(), "" + c.speechEndMs(), "" + c.echoGain());
    }

    static final Map<String, float[]> ENV = new HashMap<>();
    static float[] env(String file, String clip) throws IOException {
        if (ENV.isEmpty()) for (String l : Files.readAllLines(Paths.get(file))) {
            String[] t = l.trim().split(" ");
            float[] v = new float[t.length - 1];
            for (int i = 1; i < t.length; i++) v[i - 1] = Float.parseFloat(t[i]);
            ENV.put(t[0], v);
        }
        return ENV.get(clip);
    }

    static String clean(String[] f) throws IOException {
        List<VoskWords.Word> ws = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String l : Files.readAllLines(Paths.get(f[1]))) { // results separated by lines with "%%"
            if (l.equals("%%")) { ws.addAll(VoskWords.parse(cur.toString())); cur.setLength(0); } else cur.append(l).append('\n');
        }
        if (cur.length() > 0) ws.addAll(VoskWords.parse(cur.toString()));
        String g = f[3].equals("-") ? null : f[3];
        int sens = Integer.parseInt(f[7]);
        String v = TaskClean.fromWords(ws, Double.parseDouble(f[2]), g, Double.parseDouble(f[4]), Double.parseDouble(f[5]), sens);
        String at = f[6].equals("-") ? "" : new String(Files.readAllBytes(Paths.get(f[6])), "UTF-8").trim();
        String an = TaskClean.fromText(at, g, sens);
        TaskClean.Pick k = TaskClean.choose(an, v, false);
        return String.join("\t", f[0], v, an, k.source, k.text);
    }
}
