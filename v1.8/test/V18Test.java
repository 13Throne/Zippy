import com.zippy.ear.*;
import java.util.*;

/** Zippy 1.8 logic: Capture (greeting, barge-in, end of task), EnergyVad, TaskClean, EchoGuard, VoskWords, Timeline. */
public class V18Test {
    static int pass, fail;
    static void check(boolean ok, String what) { if (ok) pass++; else { fail++; System.out.println("FAIL " + what); } }
    static void eq(Object a, Object b, String what) { check(Objects.equals(a, b), what + " (got " + a + ", want " + b + ")"); }

    /** Runs a frame script: chars '1' speech, '0' silence; 'G' greeting starts, 'g' greeting ends (no frame). */
    static Capture run(Capture.Params p, String script) {
        Capture c = new Capture(p);
        for (char ch : script.toCharArray()) {
            if (ch == 'G') c.greetingStarted();
            else if (ch == 'g') c.greetingEnded();
            else if (ch == '0' || ch == '1') c.frame(ch == '1', ch == '1' ? 0.05f : 0.002f);
            if (c.done()) break;
        }
        return c;
    }
    static String rep(char c, int ms) { char[] a = new char[Math.max(0, ms / 32)]; Arrays.fill(a, c); return new String(a); }

    public static void main(String[] a) {
        Capture.Params p = new Capture.Params();
        // --- greeting decision
        Capture c = run(p, rep('0', 500));
        eq(c.greet(), Capture.Greet.GREET, "silent after wake -> greet");
        c = run(p, rep('1', 500));
        eq(c.greet(), Capture.Greet.SKIP, "talking right after wake (0 ms gap) -> skip greeting");
        c = run(p, rep('0', 200) + rep('1', 300));
        eq(c.greet(), Capture.Greet.SKIP, "talking 200 ms after wake -> skip");
        c = run(p, rep('0', 448) + rep('1', 300));
        eq(c.greet(), Capture.Greet.GREET, "talking only after 400 ms -> greet");
        c = run(p, rep('1', 128) + rep('0', 500));
        eq(c.greet(), Capture.Greet.GREET, "only the tail of 'Hey Zippy' at the start -> still greet");
        check(!c.talking() && !c.wantsStopGreeting(), "wake-word tail doesn't count as the task or stop the greeting");
        c = run(p, rep('1', 128) + rep('0', 500) + "G" + rep('0', 1500) + "g" + rep('0', 3000) + rep('1', 1000) + rep('0', 2500));
        eq(c.end(), Capture.End.SILENCE, "wake-word tail, greeting, then the task -> sent after silence");
        Capture.Params np = new Capture.Params(); np.voiceGreeting = false;
        eq(run(np, rep('0', 500)).greet(), Capture.Greet.NONE, "no voice cue -> no greeting");
        // --- end of task: pauses shorter than endSilence never end it
        for (int pause : new int[]{300, 600, 1000, 1500}) {
            String s = rep('1', 2000) + rep('0', pause) + rep('1', 2000) + rep('0', pause) + rep('1', 1500) + rep('0', 3000);
            c = run(np, s);
            eq(c.end(), Capture.End.SILENCE, "pause " + pause + " ms: ends by silence");
            int speechEnd = (2000 + pause + 2000 + pause + 1500) / 32 * 32;
            check(c.endMs() >= speechEnd + 1000, "pause " + pause + " ms: not cut before the last clause (end " + c.endMs() + " vs speech end " + speechEnd + ")");
            check(c.endMs() <= speechEnd + 2050, "pause " + pause + " ms: ends ~2 s after the VAD stops (" + (c.endMs() - speechEnd) + " ms)");
        }
        c = run(np, rep('1', 2000) + rep('0', 1500) + rep('1', 1000) + rep('0', 3000));
        check(c.endMs() > 4000, "a 1.5 s VAD pause with the default 2 s end-silence keeps listening");
        c = run(np, rep('1', 2000) + rep('0', 1900) + rep('1', 1000) + rep('0', 3000));
        check(c.endMs() > 4500, "even a 1.9 s VAD pause (~1.8 s real) keeps listening at the default");
        Capture.Params p25 = new Capture.Params(); p25.voiceGreeting = false; p25.endSilenceMs = Capture.endSilenceMs(8000);
        c = run(p25, rep('1', 1000) + rep('0', 2500) + rep('1', 1000) + rep('0', 4000));
        check(c.endMs() > 4500, "Wait 8 s -> 3.5 s end silence, a 2.5 s pause keeps listening");
        // --- no speech
        c = run(np, rep('0', 5000));
        eq(c.end(), Capture.End.NO_SPEECH, "never talks -> no speech");
        check(c.endMs() >= 4000 && c.endMs() <= 4100, "no speech after the 4 s wait (" + c.endMs() + ")");
        // blip (end of "Zippy") then a long pause then the task
        c = run(np, rep('1', 128) + rep('0', 2500) + rep('1', 2000) + rep('0', 2500));
        eq(c.end(), Capture.End.SILENCE, "short blip + 2.5 s pause + task -> task kept");
        check(c.endMs() > 4600, "blip doesn't end the task early (" + c.endMs() + ")");
        // max length
        c = run(np, rep('1', 47000));
        eq(c.end(), Capture.End.MAX, "45 s limit");
        check(c.endMs() >= 45000 && c.endMs() < 45100, "limit at 45 s");
        // --- greeting: no-speech clock waits for the greeting; talking during it is kept
        c = run(p, rep('0', 448) + "G" + rep('0', 3000) + "g" + rep('0', 3000));
        check(!c.done(), "the 4 s wait starts after the greeting (" + c.endMs() + ")");
        c = run(p, rep('0', 448) + "G" + rep('0', 800) + "g" + rep('1', 2000) + rep('0', 2500));
        eq(c.end(), Capture.End.SILENCE, "task after greeting");
        check(c.speechStartMs() >= 1248 && c.speechStartMs() < 1500, "speech start after greeting (" + c.speechStartMs() + ")");
        // barge-in: user much louder than echo
        Capture bc = new Capture(p);
        for (int i = 0; i < 14; i++) bc.frame(false, 0.002f);
        bc.greetingStarted();
        for (int i = 0; i < 8; i++) bc.frame(true, 0.01f);   // echo
        for (int i = 0; i < 12 && !bc.wantsStopGreeting(); i++) bc.frame(true, 0.08f); // you
        check(bc.wantsStopGreeting(), "barge-in when you talk over Liora");
        check(bc.bargeInMs() > 0 && bc.talking(), "barge-in counts as the start of the task");
        bc.greetingEnded();
        for (int i = 0; i < 60; i++) bc.frame(true, 0.08f);
        for (int i = 0; i < 80; i++) bc.frame(false, 0.002f);
        eq(bc.end(), Capture.End.SILENCE, "task after barge-in ends by silence");
        // echo alone never barges in
        Capture ec = new Capture(p);
        for (int i = 0; i < 14; i++) ec.frame(false, 0.002f);
        ec.greetingStarted();
        float[] env = {0.01f, 0.02f, 0.03f, 0.025f, 0.04f, 0.035f, 0.02f, 0.03f, 0.045f, 0.03f, 0.02f, 0.03f, 0.04f, 0.03f, 0.02f, 0.035f, 0.03f, 0.025f, 0.03f, 0.04f};
        for (float e : env) ec.frame(true, e);
        check(!ec.wantsStopGreeting(), "echo alone doesn't stop Liora");
        ec.greetingEnded();
        for (int i = 0; i < 4; i++) ec.frame(true, 0.03f); // speaker tail
        check(!ec.talking(), "speaker tail after the greeting isn't you");
        Capture.Params nb = new Capture.Params(); nb.bargeIn = false;
        Capture nc = new Capture(nb);
        for (int i = 0; i < 14; i++) nc.frame(false, 0.002f);
        nc.greetingStarted();
        for (int i = 0; i < 20; i++) nc.frame(true, 0.2f);
        check(!nc.wantsStopGreeting(), "no barge-in without Silero (energy VAD can't tell echo apart)");
        nc.greetingEnded();
        for (int i = 0; i < 10; i++) nc.frame(true, 0.2f);
        check(nc.talking(), "...but your words after her are heard");
        // talking between the decision and the greeting starting cancels it
        Capture cc = new Capture(p);
        for (int i = 0; i < 13; i++) cc.frame(false, 0.002f);
        eq(cc.greet(), Capture.Greet.GREET, "decided to greet");
        for (int i = 0; i < 3; i++) cc.frame(true, 0.05f);
        check(cc.wantsStopGreeting(), "you start before Liora does -> cancel her");
        cc.greetingCancelled();
        for (int i = 0; i < 40; i++) cc.frame(true, 0.05f);
        for (int i = 0; i < 70; i++) cc.frame(false, 0.002f);
        eq(cc.end(), Capture.End.SILENCE, "task after cancelled greeting");
        // greeting that never starts can't hang the capture
        Capture hc = new Capture(p);
        for (int i = 0; i < 300 && !hc.done(); i++) hc.frame(false, 0.002f);
        eq(hc.end(), Capture.End.NO_SPEECH, "greeting never started -> still times out");
        // mapping from the Wait slider
        eq(Capture.endSilenceMs(4000), 2000, "default 4 s wait -> 2 s silence");
        eq(Capture.endSilenceMs(1000), 1500, "1 s -> 1.5 s (never shorter)");
        eq(Capture.endSilenceMs(8000), 3500, "8 s -> 3.5 s");
        eq(Capture.noSpeechMs(4000), 4000, "start wait 4 s");

        // --- barge-in against the clip's loudness (echo gain saved from earlier sessions)
        float[] cenv = new float[40]; Arrays.fill(cenv, 2, 36, 0.2f);
        for (int mode = 0; mode < 3; mode++) {
            Capture.Params bp = new Capture.Params();
            Capture xb = new Capture(bp);
            for (int i = 0; i < 14; i++) xb.frame(false, 0.002f);            // silent -> greet
            xb.echo(cenv, mode == 2 ? -1f : 0.5f);
            xb.greetingStarted();
            for (int i = 0; i < 38 && !xb.wantsStopGreeting(); i++) {
                int k = i - 2;                                               // 2 frames of playback lag
                float echo = k >= 0 && k < cenv.length ? cenv[k] * 0.5f : 0f;
                boolean you = mode >= 1 && i >= 12;
                xb.frame(echo > 0.01f || you, 0.002f + echo + (you ? 0.35f : 0f));
            }
            if (mode == 0) { check(!xb.wantsStopGreeting(), "her own echo (gain known) never stops her"); xb.greetingEnded();
                for (int i = 0; i < 10; i++) xb.frame(false, 0.002f);
                check(Math.abs(xb.echoGain() - 0.5f) < 0.06f, "echo gain measured when you let her finish (" + xb.echoGain() + ")"); }
            if (mode == 1) check(xb.wantsStopGreeting() && xb.bargeInMs() > 0, "you talking louder than her echo stops her");
            if (mode == 2) check(xb.wantsStopGreeting(), "first session: gain learned from her first 0.3 s, then you stop her");
        }

        // --- splicing Android speech's missing ending from the offline text
        eq(TaskClean.missingTail("and include the relevant links and", "and include the relevant links and various choices in a well formatted draft a brendan"),
           "various choices in a well formatted draft a brendan", "Android stopped early -> offline ending appended");
        eq(TaskClean.missingTail("and send me the 3 best.", "and send me the three best options"), "options", "one missing content word appended");
        eq(TaskClean.missingTail("in a well-formatted draft to brand it.", "in a well formatted draft a brenda"), "", "Android went past the last agreed word -> no splice");
        eq(TaskClean.missingTail("add milk to the list", "add milk to the list huh"), "", "offline trailing filler ignored");
        eq(TaskClean.missingTail("add milk to the list", "add milk to the list the"), "", "short trailing word ignored");
        eq(TaskClean.missingTail("call Brenda", "totally different words here"), "", "no agreement -> no splice");
        TaskClean.Pick pk = TaskClean.choose("Remind me to call the dentist and add milk to the", "remind me to call the dentist and add milk to the grocery list then text brenda", false);
        eq(pk.text, "Remind me to call the dentist and add milk to the grocery list then text brenda", "choose splices the ending");
        check(pk.source.startsWith(TaskClean.ANDROID) && !pk.why.isEmpty(), "spliced pick says why");
        eq(TaskClean.choose("What's the weather this weekend?", "what's the weather this weekend", false).text, "What's the weather this weekend?", "complete Android text kept as is");

        String[] hd = TaskClean.missingHead("and add milk, eggs and coffee to the grocery list.", "remind me to call the dentist tomorrow at nine and add milk eggs and coffee to the grocery list");
        check(hd != null && hd[0].equals("remind me to call the dentist tomorrow at nine"), "Android skipped the first clause -> offline beginning");
        eq(TaskClean.choose("and add milk, eggs and coffee to the grocery list.", "remind me to call the dentist tomorrow at nine and add milk eggs and coffee to the grocery list", false).text,
           "remind me to call the dentist tomorrow at nine and add milk, eggs and coffee to the grocery list.", "choose splices the beginning");
        check(TaskClean.missingHead("Remind me to call the dentist", "remind me to call the dentist") == null, "same start -> no splice");

        // --- EnergyVad
        EnergyVad ev = new EnergyVad();
        boolean any = false;
        for (int i = 0; i < 50; i++) any |= ev.frame(0.002f);
        check(!any, "energy VAD quiet on room noise");
        boolean on = false;
        for (int i = 0; i < 10; i++) on = ev.frame(0.05f);
        check(on, "energy VAD hears speech");
        boolean still = true;
        for (int i = 0; i < 4; i++) still = ev.frame(0.002f);
        check(still, "energy VAD hangover");
        for (int i = 0; i < 10; i++) still = ev.frame(0.002f);
        check(!still, "energy VAD ends after hangover");

        // --- VoskWords
        String j = "{\n  \"result\" : [{\n      \"conf\" : 1.000000,\n      \"end\" : 0.660000,\n      \"start\" : 0.480000,\n      \"word\" : \"have\"\n    }, {\n      \"conf\" : 0.71,\n      \"end\" : 0.99,\n      \"start\" : 0.66,\n      \"word\" : \"jana\"\n    }],\n  \"text\" : \"have jana\"\n}";
        List<VoskWords.Word> ws = VoskWords.parse(j);
        eq(ws.size(), 2, "vosk words parsed");
        eq(ws.get(1).text, "jana", "word text");
        check(Math.abs(ws.get(0).start - 0.48) < 1e-9 && Math.abs(ws.get(1).end - 0.99) < 1e-9, "word times");
        eq(VoskWords.text(j), "have jana", "vosk text");
        eq(VoskWords.parse("{\"text\": \"\"}").size(), 0, "empty final result");
        eq(VoskWords.text("{\n  \"partial\" : \"hey zip\"\n}"), "hey zip", "partial text");

        // --- TaskClean.fromWords (offline, timed)
        List<VoskWords.Word> w = words("okay 0.2 0.4 hey 1.2 1.4 zippy 1.4 1.8 have 2.05 2.3 jana 2.3 2.6 draft 2.6 2.9 an 2.9 3.0 email 3.0 3.4");
        eq(TaskClean.fromWords(w, 2.0, null, -1, -1, 55), "have jana draft an email", "wake and earlier speech dropped by time and 'zippy'");
        w = words("hey 1.2 1.4 sippy 1.4 1.8 have 1.85 2.1 jana 2.1 2.4");
        eq(TaskClean.fromWords(w, 2.0, null, -1, -1, 55), "have jana", "one breath: first word started before the wake moment is kept");
        w = words("hazy 1.2 1.5 p 1.5 1.9 add 2.4 2.6 milk 2.6 2.9");
        eq(TaskClean.fromWords(w, 2.0, null, -1, -1, 55), "add milk", "misheard wake word dropped by time");
        w = words("hey 1.2 1.4 zippy 1.4 1.8 what 2.5 2.6 do 2.6 2.7 you 2.7 2.8 need 2.8 3.0 have 3.6 3.8 jana 3.8 4.1 call 4.1 4.4");
        eq(TaskClean.fromWords(w, 2.0, "What do you need?", 2.45, 3.1, 55), "have jana call", "Liora's echo dropped");
        w = words("hey 1.2 1.4 zippy 1.4 1.8 what 2.5 2.6 do 2.6 2.7 add 2.7 2.9 eggs 2.9 3.2 you 3.2 3.3");
        eq(TaskClean.fromWords(w, 2.0, "What do you need?", 2.45, 3.1, 55), "add eggs", "your words over her echo are kept");
        w = words("hey 1.2 1.4 zippy 1.4 1.8 what 6.5 6.6 time 6.6 6.9 is 6.9 7.0 it 7.0 7.1");
        eq(TaskClean.fromWords(w, 2.0, "What do you need?", 2.45, 3.1, 55), "what time is it", "greeting-like words long after her aren't dropped");
        w = words("hey 1.2 1.4 zippy 1.4 1.8 uh 2.4 2.6 um 2.7 2.9");
        eq(TaskClean.fromWords(w, 2.0, null, -1, -1, 55), "", "only fillers -> no task");
        w = words("hey 1.2 1.4 zippy 1.4 1.8 on 2.4 2.6");
        eq(TaskClean.fromWords(w, 2.0, null, -1, -1, 55), "", "a lone short word is no task");
        w = words("hey 1.2 1.4 zippy 1.4 1.8 lights 2.4 2.8");
        eq(TaskClean.fromWords(w, 2.0, null, -1, -1, 55), "lights", "one real word is a task");

        // --- TaskClean.fromText (Android speech)
        eq(TaskClean.fromText("have Jana draft an email to Brenda.", null, 55), "have Jana draft an email to Brenda.", "plain text kept with capitals/punctuation");
        eq(TaskClean.fromText("pee have Jana draft", null, 55), "have Jana draft", "zippy tail dropped");
        eq(TaskClean.fromText("Hey Zippy, have Jana draft", null, 55), "have Jana draft", "repeated wake phrase dropped");
        eq(TaskClean.fromText("What do you need? Have Jana draft an email", "What do you need?", 55), "Have Jana draft an email", "greeting echo dropped");
        eq(TaskClean.fromText("what to you need have Jana call", "What do you need?", 55), "have Jana call", "fuzzy greeting echo dropped");
        eq(TaskClean.fromText("I'm listing. Order more coffee", "I'm listening.", 55), "Order more coffee", "fuzzy two-word greeting echo");
        eq(TaskClean.fromText("Yeah? Add milk", "Yeah?", 55), "Add milk", "one-word greeting echo");
        eq(TaskClean.fromText("what do I have tomorrow", "What do you need?", 55), "what do I have tomorrow", "a task that starts like the greeting is kept");
        eq(TaskClean.fromText("zip e call mom", null, 55), "call mom", "zip e fragment");
        eq(TaskClean.fromText("zip the file and send it", null, 55), "zip the file and send it", "'zip' as a real word kept");
        eq(TaskClean.fromText("", null, 55), "", "empty");
        // --- choose
        TaskClean.Pick k = TaskClean.choose("Have Jana draft an email to Brenda.", "have jana draft an email to brenda", false);
        eq(k.source, TaskClean.ANDROID, "Android speech preferred");
        k = TaskClean.choose("the relevant links", "have jana draft an email regarding the research and include the relevant links", false);
        eq(k.source, TaskClean.OFFLINE, "Android result much shorter than offline -> offline");
        k = TaskClean.choose("have Jana draft an email", "have jana draft an email and include the links", true);
        eq(k.source, TaskClean.OFFLINE, "Android stopped early -> offline");
        k = TaskClean.choose("", "add milk", false);
        eq(k.text, "add milk", "no Android text -> offline");
        k = TaskClean.choose("", "", false);
        eq(k.text, "", "nothing");

        // --- EchoGuard additions + 1.7 behaviour unchanged
        eq(EchoGuard.strip("what do you what time is it", "What do you need?"), "what time is it", "1.7 strip unchanged");
        eq(EchoGuard.stripLeading("go ahead call mom".split(" "), 0, "Go ahead."), 2, "stripLeading");
        eq(EchoGuard.stripLeading("call mom".split(" "), 0, "Go ahead."), 0, "nothing to strip");

        // --- Timeline
        Timeline tl = new Timeline(1_000_000L);
        tl.at(1500, "B"); tl.at(0, "A"); tl.note("n");
        String t = tl.text();
        check(t.indexOf("A") < t.indexOf("B") && t.endsWith("\nn") && t.contains("+ 1.50 s  B"), "timeline ordered: " + t.replace('\n', '|'));

        System.out.println("V18Test: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }

    static List<VoskWords.Word> words(String s) {
        String[] p = s.split(" ");
        List<VoskWords.Word> out = new ArrayList<>();
        for (int i = 0; i + 2 < p.length; i += 3) out.add(new VoskWords.Word(p[i], Double.parseDouble(p[i + 1]), Double.parseDouble(p[i + 2])));
        return out;
    }
}
