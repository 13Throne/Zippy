import com.zippy.ear.*;
import java.util.*;

/** Zippy 1.6: Liora clip picking, dropdown choices, settings persistence + migration, confirmation cue. */
public class V16Test {
    static int pass, fail;
    static void check(boolean ok, String what) { if (ok) pass++; else { fail++; System.out.println("FAIL: " + what); } }
    static void eq(Object want, Object got, String what) { check(Objects.equals(want, got), what + " (want " + want + ", got " + got + ")"); }

    static final class MapKV implements ZippyPrefs.KV {
        final Map<String, Object> m = new HashMap<>();
        public boolean contains(String k) { return m.containsKey(k); }
        public String getString(String k, String d) { Object v = m.get(k); return v instanceof String ? (String) v : d; }
        public boolean getBoolean(String k, boolean d) { Object v = m.get(k); return v instanceof Boolean ? (Boolean) v : d; }
        public int getInt(String k, int d) { Object v = m.get(k); return v instanceof Integer ? (Integer) v : d; }
        public void putString(String k, String v) { m.put(k, v); }
        public void putBoolean(String k, boolean v) { m.put(k, v); }
        public void putInt(String k, int v) { m.put(k, v); }
    }

    public static void main(String[] a) {
        clips(); picking(); prefs(); cues();
        System.out.println("V16Test: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }

    static void clips() {
        eq(9, Liora.GREETING_IDS.length, "9 greetings");
        eq(9, Liora.GREETING_TEXT.length, "9 greeting texts");
        eq(10, Liora.CONFIRM_IDS.length, "10 confirmations");
        eq(10, Liora.CONFIRM_TEXT.length, "10 confirmation texts");
        eq("Yeah?", Liora.text("g1"), "g1 text");
        eq("Right here.", Liora.text("g9"), "g9 text");
        eq("On it.", Liora.text("c1"), "c1 text");
        eq("Got it, I'll take care of it.", Liora.text("c10"), "c10 text");
        eq("voice/c10.m4a", Liora.asset("c10"), "asset path");
        for (boolean g : new boolean[]{true, false}) {
            String[] k = Liora.choiceKeys(g), l = Liora.choiceLabels(g);
            eq(k.length, l.length, "keys/labels aligned " + g);
            eq("random", k[0], "Random first " + g);
            eq("Random", l[0], "Random label " + g);
            eq("custom", k[k.length - 1], "Custom last " + g);
            check(l[l.length - 1].startsWith("Custom"), "Custom label " + g);
            for (int i = 1; i < k.length - 1; i++) check(l[i].contains(Liora.text(k[i])), "label " + k[i] + " shows its words");
            eq(0, Liora.indexOf(k, "nope"), "unknown -> Random index");
        }
        check(Liora.isClip("g5", true) && !Liora.isClip("g5", false), "g5 is a greeting only");
        check(Liora.isClip("c7", false) && !Liora.isClip("c7", true), "c7 is a confirmation only");
    }

    static void picking() {
        Random rng = new Random(42);
        eq("g4", Liora.pick("g4", Liora.GREETING_IDS, "g4", rng), "named clip plays even if it was last");
        eq(null, Liora.pick("custom", Liora.GREETING_IDS, null, rng), "custom -> TTS");
        Set<String> seen = new HashSet<>();
        String prev = null;
        for (int i = 0; i < 2000; i++) {
            String p = Liora.pick("random", Liora.GREETING_IDS, prev, rng);
            check(Liora.isClip(p, true), "random greeting is a greeting");
            if (prev != null && p.equals(prev)) { check(false, "random repeated " + p); break; }
            seen.add(p); prev = p;
        }
        eq(9, seen.size(), "random covers all 9 greetings");
        seen.clear(); prev = null;
        for (int i = 0; i < 2000; i++) {
            String p = Liora.pick("random", Liora.CONFIRM_IDS, prev, rng);
            if (prev != null && p.equals(prev)) { check(false, "random confirmation repeated"); break; }
            seen.add(p); prev = p;
        }
        eq(10, seen.size(), "random covers all 10 confirmations");
        // shared pickers avoid immediate repeats too
        String last = Liora.pickGreeting("random");
        for (int i = 0; i < 200; i++) { String n = Liora.pickGreeting("random"); check(!n.equals(last), "pickGreeting no repeat"); last = n; }
    }

    static void prefs() {
        MapKV kv = new MapKV(); ZippyPrefs p = new ZippyPrefs(kv);
        eq("random", p.greetingChoice(), "fresh: greeting Random Liora");
        eq("voice", p.confirmCue(), "fresh: voice confirmation ON");
        eq("random", p.confirmChoice(), "fresh: confirmation Random");
        eq("On it.", p.confirmText(), "custom confirmation default");
        p.migrate();
        eq("voice", p.wakeCue(), "fresh wake cue voice");
        p.setGreetingChoice("g6"); p.setConfirmCue("beep"); p.setConfirmChoice("c10"); p.setConfirmText("  Done,   boss ");
        ZippyPrefs q = new ZippyPrefs(kv);
        eq("g6", q.greetingChoice(), "greeting choice persisted");
        eq("beep", q.confirmCue(), "confirm mode persisted");
        eq("c10", q.confirmChoice(), "confirm choice persisted");
        eq("Done, boss", q.confirmText(), "confirm text persisted + cleaned");
        q.setConfirmText(" "); eq("On it.", q.confirmText(), "blank confirm text -> default");
        q.setGreetingChoice("c3"); eq("random", q.greetingChoice(), "confirmation id can't be a greeting");
        q.setConfirmChoice("g1"); eq("random", q.confirmChoice(), "greeting id can't be a confirmation");
        q.setGreetingChoice("custom"); eq("custom", q.greetingChoice(), "custom greeting");
        kv.putString("confirmCue", "junk"); eq("voice", q.confirmCue(), "bad confirm mode -> voice");

        // upgrade from 1.5 with the default greeting -> Liora random; with typed text -> keeps Custom TTS
        MapKV u = new MapKV(); u.putString("wakeCue", "voice"); u.putString("greeting", "Yeah?"); u.putInt("sentCue", 1);
        u.putString("webhookKey", "kept");
        ZippyPrefs up = new ZippyPrefs(u); up.migrate();
        eq("random", up.greetingChoice(), "1.5 default greeting -> Liora random");
        eq("voice", up.confirmCue(), "upgrade gets voice confirmation");
        eq("kept", u.getString("webhookKey", ""), "secret untouched");
        MapKV t = new MapKV(); t.putString("wakeCue", "voice"); t.putString("greeting", "Sup?");
        ZippyPrefs typed = new ZippyPrefs(t); typed.migrate();
        eq("custom", typed.greetingChoice(), "1.5 typed greeting kept as Custom TTS");
        typed.setGreeting("Yeah?");
        eq("custom", new ZippyPrefs(t).greetingChoice(), "choice pinned after migrate");
        MapKV b = new MapKV(); b.putInt("readyCue", 2);
        ZippyPrefs beep = new ZippyPrefs(b); beep.migrate();
        eq("beep", beep.wakeCue(), "1.4 beep user keeps beep wake cue");
        eq(2, ZippyPrefs.legacySentCue("beep"), "legacy sent beep");
        eq(1, ZippyPrefs.legacySentCue("vibrate"), "legacy sent vibrate");
        eq(0, ZippyPrefs.legacySentCue("voice"), "legacy sent voice");
    }

    static void cues() {
        eq(WakeCue.Action.VOICE, WakeCue.confirmAction("voice"), "voice confirmation");
        eq(WakeCue.Action.BEEP, WakeCue.confirmAction("beep"), "beep confirmation");
        eq(WakeCue.Action.VIBRATE, WakeCue.confirmAction("vibrate"), "vibrate confirmation");
        eq(WakeCue.Action.NONE, WakeCue.confirmAction("silent"), "silent confirmation");
        eq(WakeCue.Action.VOICE, WakeCue.beforeCommand("voice", true), "voice greeting with clip");
        eq(WakeCue.Action.BEEP, WakeCue.afterSpeak(false), "clip+TTS failed -> beep");
        check(Tabs.visible(Tabs.Section.CONFIRMATION, Tabs.Tab.VOICE), "Confirmation on Voice tab");
        check(!Tabs.visible(Tabs.Section.CONFIRMATION, Tabs.Tab.HOME), "Confirmation not on Home");
    }
}
