import com.zippy.ear.*;
import java.util.*;

/** Zippy 1.7: Sherpa-ONNX keyword table vs Sensitivity, greeting-echo stripping, one-breath task text. */
public class V17Test {
    static int pass = 0, fail = 0;
    static void check(boolean ok, String what) { if (ok) pass++; else { fail++; System.out.println("FAIL " + what); } }

    public static void main(String[] a) {
        // --- KwsTuning: monotonic, well-formed, bare "Zippy" only at >= 85
        List<String> prev = null;
        double prevT = 9, prevB = -1;
        for (int s = 0; s <= 100; s++) {
            List<String> l = KwsTuning.lines(s);
            double t = KwsTuning.threshold(s), b = KwsTuning.boost(s);
            check(t <= prevT && b >= prevB, "threshold falls and boost rises at " + s);
            check(t >= 0.05 - 1e-9 && t <= 0.45 + 1e-9 && b >= 1 && b <= 2, "ranges at " + s);
            Set<String> toks = new HashSet<>();
            for (String x : l) toks.add(x.substring(0, x.indexOf(" :")));
            if (prev != null) {
                Set<String> pt = new HashSet<>();
                for (String x : prev) pt.add(x.substring(0, x.indexOf(" :")));
                check(toks.containsAll(pt), "pronunciations only get added as sensitivity rises (" + s + ")");
            }
            boolean bare = false;
            for (String x : l) {
                check(x.matches("\u2581\\S*( \\S+)* :\\d\\.\\d\\d #0\\.\\d{3} @(HEY_ZIPPY|ZIPPY)"), "line format: " + x);
                if (x.endsWith("@ZIPPY")) bare = true;
            }
            check(bare == (s >= 85), "bare ZIPPY only at >= 85 (s=" + s + ")");
            check(l.get(0).startsWith("\u2581HE Y \u2581 Z I PP Y :"), "HEY ZIPPY first at " + s);
            check(KwsTuning.keywords(s).split("/").length == l.size(), "'/'-joined for createStream at " + s);
            prev = l; prevT = t; prevB = b;
        }
        check(KwsTuning.lines(0).size() == 4 && KwsTuning.lines(55).size() == 11 && KwsTuning.lines(100).size() == 13, "tier sizes 4 / 11 / 13");
        check(KwsTuning.keywords(55).contains("\u2581HE Y \u2581S I PP Y"), "a soft z (sippy) accepted at the default 55");
        check(!KwsTuning.keywords(49).contains("\u2581S I PP"), "...but not below 50");
        check(KwsTuning.keywords(-5).equals(KwsTuning.keywords(0)) && KwsTuning.keywords(500).equals(KwsTuning.keywords(100)), "clamped");
        for (String x : KwsTuning.lines(100)) if (x.endsWith("@ZIPPY")) check(x.contains("#0.150"), "bare Zippy needs threshold +0.10");

        // --- Sensitivity labels (1.7 copy) still meet the 1.4 contract
        check(Sensitivity.label(100).contains("voice check off") && Sensitivity.label(0).startsWith("0"), "labels explain the number");
        check(Sensitivity.label(85).contains("Zippy") && Sensitivity.label(55).contains("Sippy"), "labels match the keyword tiers");

        // --- EchoGuard
        check(EchoGuard.strip("what do you what time is it", "What do you need?").equals("what time is it"), "echo of 'What do you need?' stripped");
        check(EchoGuard.strip("I'm listening add eggs", "I'm listening.").equals("add eggs"), "echo of 'I'm listening.' stripped");
        check(EchoGuard.strip("ready add eggs", "Ready.").equals("add eggs"), "one-word greeting echo stripped");
        check(EchoGuard.strip("what time is it", "What do you need?").equals("what time is it"), "one shared word is not an echo");
        check(EchoGuard.strip("what do you need", "What do you need?").equals("what do you need"), "never strips the whole transcript");
        check(EchoGuard.strip("add milk", null).equals("add milk") && EchoGuard.strip(null, "Ready.").isEmpty(), "nulls");

        // --- TaskText (one breath, Vosk starts at the keyword)
        check(TaskText.afterWake("pee add milk to the list", 55).equals("add milk to the list"), "tail of Zippy dropped");
        check(TaskText.afterWake("hey zippy what time is it", 55).equals("what time is it"), "repeated wake phrase dropped");
        check(TaskText.afterWake("the", 55).isEmpty() && TaskText.afterWake("e", 55).isEmpty() && TaskText.afterWake("", 55).isEmpty(), "fillers alone are no task");
        check(TaskText.afterWake("I need eggs", 55).equals("i need eggs"), "'I' is kept");
        check(TaskText.afterWake("go", 55).isEmpty() && TaskText.afterWake("stop the music", 55).equals("stop the music"), "short noise word vs real task");

        System.out.println("V17Test: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
