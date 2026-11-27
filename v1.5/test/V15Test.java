import com.zippy.ear.*;
import java.util.*;

/** Zippy 1.5 pure-logic tests: settings persistence + migration, wake cue, STT fallback, Grok Bot pick, tabs. */
public class V15Test {
    static int pass, fail;

    static void check(boolean ok, String what) {
        if (ok) pass++; else { fail++; System.out.println("FAIL: " + what); }
    }
    static void eq(Object want, Object got, String what) {
        check(Objects.equals(want, got), what + " (want " + want + ", got " + got + ")");
    }

    /** In-memory SharedPreferences stand-in. */
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
        settings();
        wakeCue();
        commandPlan();
        grokBot();
        tabs();
        System.out.println("V15Test: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }

    static void settings() {
        // fresh install defaults
        MapKV kv = new MapKV();
        ZippyPrefs p = new ZippyPrefs(kv);
        eq("voice", p.wakeCue(), "fresh install cue = voice greeting");
        eq("Yeah?", p.greeting(), "default greeting");
        eq(true, p.useAndroidStt(), "Android STT default ON");
        eq(false, p.openGrokBot(), "Open Grok Bot default OFF");
        eq(Tabs.Tab.HOME, p.tab(), "default tab Home");
        p.migrate();
        kv.putInt("readyCue", ZippyPrefs.legacyReadyCue(p.wakeCue())); // Store.saveSettings later writes 0
        eq("voice", new ZippyPrefs(kv).wakeCue(), "pinned cue survives a legacy readyCue=0 save");

        // persistence round trip (new ZippyPrefs over same store = app restart / update)
        p.setWakeCue("vibrate"); p.setGreeting("  What's up,   boss?  "); p.setUseAndroidStt(false);
        p.setOpenGrokBot(true); p.setTab(Tabs.Tab.SETTINGS);
        ZippyPrefs q = new ZippyPrefs(kv);
        eq("vibrate", q.wakeCue(), "cue persisted");
        eq("What's up, boss?", q.greeting(), "greeting persisted + whitespace cleaned");
        eq(false, q.useAndroidStt(), "STT toggle persisted");
        eq(true, q.openGrokBot(), "Grok toggle persisted");
        eq(Tabs.Tab.SETTINGS, q.tab(), "tab persisted");
        q.setGreeting("   "); eq("Yeah?", q.greeting(), "blank greeting -> default");
        q.setGreeting(null); eq("Yeah?", q.greeting(), "null greeting -> default");
        StringBuilder longG = new StringBuilder(); for (int i = 0; i < 30; i++) longG.append("yo ");
        q.setGreeting(longG.toString()); check(q.greeting().length() <= ZippyPrefs.MAX_GREETING, "greeting capped");
        kv.putString("wakeCue", "garbage"); eq("voice", q.wakeCue(), "bad stored cue -> voice");
        kv.putString("tab", "nope"); eq(Tabs.Tab.HOME, q.tab(), "bad stored tab -> Home");

        // upgrade from 1.4: keep the cue the user had ("When Hey Zippy hits": Off/Vibrate/Sound/Both)
        int[] legacy = {0, 1, 2, 3};
        String[] want = {"silent", "vibrate", "beep", "beep"};
        for (int i = 0; i < 4; i++) {
            MapKV u = new MapKV();
            u.putInt("readyCue", legacy[i]);
            u.putString("webhookKey", "kept"); // unrelated 1.4 data untouched
            ZippyPrefs up = new ZippyPrefs(u);
            eq(want[i], up.wakeCue(), "1.4 readyCue " + legacy[i] + " migrates");
            up.migrate();
            u.putInt("readyCue", ZippyPrefs.legacyReadyCue(up.wakeCue()));
            eq(want[i], new ZippyPrefs(u).wakeCue(), "1.4 readyCue " + legacy[i] + " stable after save");
            eq("kept", u.getString("webhookKey", ""), "secret untouched by migration");
            eq(true, up.useAndroidStt(), "upgrade gets STT on");
            eq(false, up.openGrokBot(), "upgrade gets Grok off");
        }
        eq(2, ZippyPrefs.legacyReadyCue("beep"), "legacy beep");
        eq(1, ZippyPrefs.legacyReadyCue("vibrate"), "legacy vibrate");
        eq(0, ZippyPrefs.legacyReadyCue("voice"), "legacy voice = no old cue");
        eq(0, ZippyPrefs.legacyReadyCue("silent"), "legacy silent");
        eq("Voice greeting", ZippyPrefs.cueLabel("voice"), "label voice");
        eq("Beep", ZippyPrefs.cueLabel("beep"), "label beep");
    }

    static void wakeCue() {
        // beep / vibrate fire at the voice accept, once
        eq(WakeCue.Action.BEEP, WakeCue.atAccept("beep"), "beep at accept");
        eq(WakeCue.Action.NONE, WakeCue.beforeCommand("beep", true), "beep not repeated before command");
        eq(WakeCue.Action.VIBRATE, WakeCue.atAccept("vibrate"), "vibrate at accept");
        eq(WakeCue.Action.NONE, WakeCue.beforeCommand("vibrate", true), "vibrate not repeated");
        // silent: nothing ever
        eq(WakeCue.Action.NONE, WakeCue.atAccept("silent"), "silent accept");
        eq(WakeCue.Action.NONE, WakeCue.beforeCommand("silent", true), "silent before command");
        // voice: nothing at accept (user may still be talking), greeting before listening
        eq(WakeCue.Action.NONE, WakeCue.atAccept("voice"), "voice waits");
        eq(WakeCue.Action.VOICE, WakeCue.beforeCommand("voice", true), "voice greeting when TTS ready");
        eq(WakeCue.Action.BEEP, WakeCue.beforeCommand("voice", false), "TTS not ready -> beep");
        eq(WakeCue.Action.BEEP, WakeCue.afterSpeak(false), "TTS failed/timeout -> beep");
        eq(WakeCue.Action.NONE, WakeCue.afterSpeak(true), "TTS done -> no extra cue");
        eq(2, WakeCue.cuesMode(WakeCue.Action.BEEP), "beep -> Cues SOUND");
        eq(1, WakeCue.cuesMode(WakeCue.Action.VIBRATE), "vibrate -> Cues VIBRATE");
    }

    static void commandPlan() {
        // same breath: Vosk already heard the task -> send now, no STT
        eq(CommandPlan.Next.SEND_NOW, CommandPlan.afterWake("add milk", true, true), "same breath sends now");
        eq(CommandPlan.Next.SEND_NOW, CommandPlan.afterWake("add milk", false, false), "same breath, STT off");
        // pause after the wake
        eq(CommandPlan.Next.ANDROID_STT, CommandPlan.afterWake("", true, true), "pause -> Android STT");
        eq(CommandPlan.Next.ANDROID_STT, CommandPlan.afterWake(null, true, true), "null remainder -> STT");
        eq(CommandPlan.Next.VOSK, CommandPlan.afterWake("", false, true), "STT toggle off -> Vosk");
        eq(CommandPlan.Next.VOSK, CommandPlan.afterWake("  ", true, false), "no recognizer on phone -> Vosk");
        // STT outcomes
        eq(CommandPlan.SttOutcome.SEND, CommandPlan.onStt("add milk to the list", 0, false), "result -> send");
        eq(CommandPlan.SttOutcome.NO_TASK, CommandPlan.onStt(null, CommandPlan.ERROR_NO_MATCH, false), "no match -> no task");
        eq(CommandPlan.SttOutcome.NO_TASK, CommandPlan.onStt(null, CommandPlan.ERROR_SPEECH_TIMEOUT, false), "silence -> no task");
        eq(CommandPlan.SttOutcome.NO_TASK, CommandPlan.onStt("", CommandPlan.ERROR_NO_MATCH, false), "empty result -> no task");
        for (int e : new int[]{1, 2, 3, 4, 5, 8, 9, 10, 11, 12, 13, -1}) {
            eq(CommandPlan.SttOutcome.FALLBACK_VOSK, CommandPlan.onStt(null, e, false), "error " + e + " -> Vosk fallback");
        }
        eq(CommandPlan.SttOutcome.FALLBACK_VOSK, CommandPlan.onStt(null, 0, true), "watchdog timeout -> Vosk fallback");
        eq(CommandPlan.SttOutcome.FALLBACK_VOSK, CommandPlan.onStt("late text", 0, true), "late result after watchdog ignored");
        // watchdog bounds always finite (listening can't wedge)
        check(CommandPlan.sttTimeoutMs(1000) >= 6000 && CommandPlan.sttTimeoutMs(1000) <= 20000, "timeout bounds low");
        check(CommandPlan.sttTimeoutMs(8000) <= 20000, "timeout bounds high");
        check(CommandPlan.sttTimeoutMs(Integer.MAX_VALUE) == 20000, "timeout clamps");
        // Google sometimes returns the wake words too: they get stripped before sending
        eq("add milk", WakePhrase.stripLeadingWake("hey zippy add milk", 55), "strip wake from STT text");
        eq("add milk", WakePhrase.stripLeadingWake("add milk", 55), "plain STT text unchanged");
    }

    static void grokBot() {
        List<String[]> apps = new ArrayList<>();
        apps.add(new String[]{"com.android.chrome", "Chrome"});
        apps.add(new String[]{"ai.x.grok", "Grok"});
        eq("ai.x.grok", GrokBot.pick(apps), "only plain Grok -> label fallback");
        apps.add(new String[]{"com.example.bot", "Grok Bot"});
        eq("com.example.bot", GrokBot.pick(apps), "label 'Grok Bot' beats plain Grok");
        apps.add(new String[]{"ai.x.grok.bot", "Grok Bot"});
        eq("ai.x.grok.bot", GrokBot.pick(apps), "Play package wins");
        List<String[]> none = new ArrayList<>();
        none.add(new String[]{"com.android.chrome", "Chrome"});
        eq(null, GrokBot.pick(none), "not installed -> null");
        eq(null, GrokBot.pick(new ArrayList<>()), "empty -> null");
        eq("ai.x.grok.bot", GrokBot.PLAY_PACKAGE, "package constant");
    }

    static void tabs() {
        eq(Tabs.Tab.VOICE, Tabs.fromKey("voice"), "key voice");
        eq(Tabs.Tab.SETTINGS, Tabs.fromKey("settings"), "key settings");
        eq(Tabs.Tab.HOME, Tabs.fromKey(null), "null -> home");
        for (Tabs.Tab t : Tabs.Tab.values()) eq(t, Tabs.fromKey(t.key), "round trip " + t);
        Tabs.Section[] notHome = {Tabs.Section.WEBHOOK, Tabs.Section.TRAINING, Tabs.Section.UPDATES, Tabs.Section.SENSITIVITY};
        for (Tabs.Section s : notHome) check(!Tabs.visible(s, Tabs.Tab.HOME), s + " not on Home");
        check(Tabs.visible(Tabs.Section.LISTENING, Tabs.Tab.HOME), "Listening on Home");
        check(Tabs.visible(Tabs.Section.TRAINING, Tabs.Tab.VOICE), "Training on Voice");
        check(Tabs.visible(Tabs.Section.WAKE_CUE, Tabs.Tab.VOICE), "Wake cue on Voice");
        check(Tabs.visible(Tabs.Section.WEBHOOK, Tabs.Tab.SETTINGS), "Webhook on Settings");
        check(Tabs.visible(Tabs.Section.COMMANDS, Tabs.Tab.SETTINGS), "STT toggle on Settings");
        check(Tabs.visible(Tabs.Section.GROK_BOT, Tabs.Tab.SETTINGS), "Grok toggle on Settings");
        for (Tabs.Section s : Tabs.Section.values()) {
            int n = 0;
            for (Tabs.Tab t : Tabs.Tab.values()) if (Tabs.visible(s, t)) n++;
            eq(1, n, s + " on exactly one tab");
        }
    }
}
