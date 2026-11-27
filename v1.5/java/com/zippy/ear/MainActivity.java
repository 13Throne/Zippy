package com.zippy.ear;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Zippy 1.5 main screen (Java rewrite of the Kotlin screen) with three tabs:
 *  Home     - Listening switch, live status, last heard, a short summary, recent orders.
 *  Voice    - Train / Reset voice, Sensitivity, wake cue + greeting, task wait, sent cue.
 *  Settings - Webhook + secret + test, Android speech toggle, Grok Bot toggle, background, updates.
 * Live state sync from 1.4 (prefs listener + the service's real engine state) is built in.
 */
public class MainActivity extends Activity {
    private static final int REQ_PERMS = 7;
    private static final String[] CUE_KEYS = {ZippyPrefs.CUE_VOICE, ZippyPrefs.CUE_BEEP, ZippyPrefs.CUE_VIBRATE, ZippyPrefs.CUE_SILENT};
    private static final String[] SENT_LABELS = {"Off", "Vibrate", "Sound", "Both"};

    private final Handler main = new Handler(Looper.getMainLooper());
    private ZippyPrefs zp;
    private Tabs.Tab tab = Tabs.Tab.HOME;
    private final Button[] tabButtons = new Button[3];
    private final LinearLayout[] pages = new LinearLayout[3];
    private ScrollView scroll;
    private boolean rendering, startAfterPermission, renderQueued, resumed;
    private long lastKick;
    private Greeter previewer;

    // Home
    private Switch listenSwitch;
    private TextView status, heard, summary, orders;
    // Voice
    private TextView voice, sensitivityLabel, waitLabel, cueInfo;
    private SeekBar sensitivity, waitBar;
    private final Button[] cueButtons = new Button[4];
    private final Button[] sentButtons = new Button[4];
    private LinearLayout greetingBox;
    private EditText greetingField;
    // Settings
    private EditText urlField, keyField;
    private TextView testStatus, sttInfo, grokInfo, battery, updateStatus;
    private Switch sttSwitch, grokSwitch;
    private Button overlayButton, batteryButton, updateButton;

    private final Runnable renderNow = () -> { renderQueued = false; if (!isFinishing()) render(); };
    private final Runnable engineChanged = this::requestRender;
    // strong reference: SharedPreferences keeps listeners weakly
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener = (p, key) -> {
        if (key == null || key.equals("listening") || key.equals("note") || key.equals("heard")
                || key.equals("prints") || key.equals("orders") || key.equals("grokNote")) requestRender();
    };

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Sensitivity.migrate(this);
        zp = ZippyPrefs.of(this);
        zp.migrate();
        tab = zp.tab();
        getWindow().getDecorView().setBackgroundColor(Look.BG);
        if (Build.VERSION.SDK_INT < 35) {
            getWindow().setStatusBarColor(Look.BG);
            getWindow().setNavigationBarColor(Look.BG);
        }
        setContentView(build());
        Settings s = Store.INSTANCE.read(this);
        urlField.setText(s.getWebhookUrl());
        keyField.setText(s.getWebhookKey());
        greetingField.setText(zp.greeting());
        hookFieldSaves();
        askPermissions(false);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        ListenService.appVisible = true;
        getSharedPreferences("zippy", Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(prefsListener);
        ListenService.watch(engineChanged);
        render();
        try { Updater.onResume(this, updateButton, updateStatus); } catch (Throwable ignored) { }
    }

    @Override
    protected void onPause() {
        resumed = false;
        ListenService.appVisible = false;
        getSharedPreferences("zippy", Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefsListener);
        ListenService.unwatch(engineChanged);
        persist();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (previewer != null) previewer.shutdown();
        super.onDestroy();
    }

    /** Kept for ResetConfirm and older hooks. */
    public static void rerender(MainActivity a) { if (a != null && !a.isFinishing()) a.render(); }

    /** Called by ResetConfirm after the user confirms "Delete". */
    public static void resetVoiceConfirmed(MainActivity a) {
        Store.INSTANCE.clearPrints(a);
        a.render();
    }

    private void requestRender() {
        if (renderQueued) return;
        renderQueued = true;
        main.postDelayed(renderNow, 120);
    }

    // ------------------------------------------------------------------ layout

    private View build() {
        LinearLayout root = Look.column(this);
        root.setBackgroundColor(Look.BG);
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout header = Look.column(this);
        header.setPadding(dp(20), dp(20), dp(20), dp(4));
        header.addView(Look.title(this, "Zippy"));
        LinearLayout bar = Look.row(this);
        bar.setBackground(Look.rounded(this, Look.SURFACE, 14));
        bar.setPadding(dp(4), 0, dp(4), dp(8));
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barLp.topMargin = dp(12);
        bar.setLayoutParams(barLp);
        for (Tabs.Tab t : Tabs.Tab.values()) {
            Button b = Look.segment(this, t.title);
            b.setTextSize(15);
            b.setOnClickListener(v -> selectTab(t));
            tabButtons[t.ordinal()] = b;
            bar.addView(b);
        }
        header.addView(bar);
        root.addView(header);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout holder = Look.column(this);
        holder.setPadding(dp(20), 0, dp(20), dp(32));
        for (Tabs.Tab t : Tabs.Tab.values()) {
            pages[t.ordinal()] = Look.column(this);
            holder.addView(pages[t.ordinal()]);
        }
        scroll.addView(holder);
        root.addView(scroll);

        buildHome(pages[0]);
        buildVoice(pages[1]);
        buildSettings(pages[2]);
        Look.edgeToEdge(root);
        return root;
    }

    private void buildHome(LinearLayout page) {
        LinearLayout c = Look.card(this);
        LinearLayout r = Look.row(this);
        TextView h = Look.heading(this, "Listening");
        h.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(h);
        listenSwitch = new Switch(this);
        listenSwitch.setOnCheckedChangeListener((v, on) -> { if (!rendering) setListening(on); });
        r.addView(listenSwitch);
        c.addView(r);
        status = Look.text(this, "", 15, Look.FG);
        c.addView(Look.gap(this, 6));
        c.addView(status);
        page.addView(c);

        c = Look.card(this);
        c.addView(Look.heading(this, "Last heard"));
        heard = Look.body(this, "");
        c.addView(Look.gap(this, 6));
        c.addView(heard);
        page.addView(c);

        c = Look.card(this);
        c.addView(Look.heading(this, "At a glance"));
        summary = Look.body(this, "");
        c.addView(Look.gap(this, 6));
        c.addView(summary);
        page.addView(c);

        c = Look.card(this);
        c.addView(Look.heading(this, "Recent orders"));
        orders = Look.body(this, "");
        c.addView(Look.gap(this, 6));
        c.addView(orders);
        page.addView(c);
    }

    private void buildVoice(LinearLayout page) {
        LinearLayout c = Look.card(this);
        c.addView(Look.heading(this, "Your voice"));
        voice = Look.body(this, "");
        c.addView(Look.gap(this, 6));
        c.addView(voice);
        Button train = Look.button(this, "Train Hey Zippy", true);
        train.setOnClickListener(v -> startActivity(new Intent(this, TrainActivity.class)));
        c.addView(train);
        Button reset = Look.button(this, "Reset voice", false);
        reset.setOnClickListener(v -> ResetConfirm.show(this));
        c.addView(reset);
        page.addView(c);

        c = Look.card(this);
        c.addView(Look.heading(this, "Sensitivity"));
        sensitivityLabel = Look.body(this, "");
        c.addView(Look.gap(this, 6));
        c.addView(sensitivityLabel);
        sensitivity = new SeekBar(this);
        sensitivity.setMax(100);
        sensitivity.setOnSeekBarChangeListener(new Seek(p -> {
            sensitivityLabel.setText(Sensitivity.label(p));
            persist();
        }));
        c.addView(sensitivity);
        page.addView(c);

        c = Look.card(this);
        c.addView(Look.heading(this, "Wake cue"));
        cueInfo = Look.body(this, "");
        c.addView(Look.gap(this, 6));
        c.addView(cueInfo);
        LinearLayout r1 = Look.row(this), r2 = Look.row(this);
        for (int i = 0; i < 4; i++) {
            final String key = CUE_KEYS[i];
            Button b = Look.segment(this, ZippyPrefs.cueLabel(key));
            b.setOnClickListener(v -> { zp.setWakeCue(key); persist(); render(); });
            cueButtons[i] = b;
            (i < 2 ? r1 : r2).addView(b);
        }
        c.addView(r1);
        c.addView(r2);
        greetingBox = Look.column(this);
        greetingBox.addView(Look.gap(this, 8));
        greetingBox.addView(Look.body(this, "Greeting Zippy says"));
        greetingField = Look.field(this, ZippyPrefs.DEFAULT_GREETING, false);
        greetingField.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        greetingBox.addView(greetingField);
        Button preview = Look.button(this, "Preview greeting", false);
        preview.setOnClickListener(v -> previewGreeting());
        greetingBox.addView(preview);
        c.addView(greetingBox);
        page.addView(c);

        c = Look.card(this);
        c.addView(Look.heading(this, "Wait for the task"));
        waitLabel = Look.body(this, "");
        c.addView(Look.gap(this, 6));
        c.addView(waitLabel);
        waitBar = new SeekBar(this);
        waitBar.setMax(14);
        waitBar.setOnSeekBarChangeListener(new Seek(p -> { waitLabel.setText(waitText(1000 + 500 * p)); persist(); }));
        c.addView(waitBar);
        page.addView(c);

        c = Look.card(this);
        c.addView(Look.heading(this, "When the order is accepted"));
        LinearLayout r = Look.row(this);
        for (int i = 0; i < 4; i++) {
            final int mode = i;
            Button b = Look.segment(this, SENT_LABELS[i]);
            b.setOnClickListener(v -> { sentCue = mode; persist(); render(); });
            sentButtons[i] = b;
            r.addView(b);
        }
        c.addView(r);
        page.addView(c);
    }

    private int sentCue = -1;

    private void buildSettings(LinearLayout page) {
        LinearLayout c = Look.card(this);
        c.addView(Look.heading(this, "Webhook"));
        c.addView(Look.gap(this, 6));
        c.addView(Look.body(this, "Grok Bot routine: Hey Zippy voice tasks. Keep the address below and type the secret you gave Grok Bot. Each task is signed with X-Zippy-Signature."));
        urlField = Look.field(this, "https://", false);
        c.addView(urlField);
        keyField = Look.field(this, "Secret", true);
        c.addView(keyField);
        Button test = Look.button(this, "Send test", true);
        test.setOnClickListener(v -> sendTest());
        c.addView(test);
        testStatus = Look.body(this, "");
        c.addView(testStatus);
        page.addView(c);

        c = Look.card(this);
        c.addView(Look.heading(this, "Commands"));
        sttSwitch = new Switch(this);
        sttSwitch.setText("Use Android speech recognition for commands");
        sttSwitch.setTextColor(Look.FG);
        sttSwitch.setTextSize(15);
        sttSwitch.setOnCheckedChangeListener((v, on) -> { if (!rendering) { zp.setUseAndroidStt(on); render(); } });
        c.addView(Look.gap(this, 8));
        c.addView(sttSwitch);
        sttInfo = Look.body(this, "");
        c.addView(Look.gap(this, 6));
        c.addView(sttInfo);
        page.addView(c);

        c = Look.card(this);
        c.addView(Look.heading(this, "Grok Bot"));
        grokSwitch = new Switch(this);
        grokSwitch.setText("Open Grok Bot on wake");
        grokSwitch.setTextColor(Look.FG);
        grokSwitch.setTextSize(15);
        grokSwitch.setOnCheckedChangeListener((v, on) -> {
            if (rendering) return;
            zp.setOpenGrokBot(on);
            zp.setGrokNote("");
            render();
        });
        c.addView(Look.gap(this, 8));
        c.addView(grokSwitch);
        grokInfo = Look.body(this, "");
        c.addView(Look.gap(this, 6));
        c.addView(grokInfo);
        overlayButton = Look.button(this, "Allow display over other apps", false);
        overlayButton.setOnClickListener(v -> {
            try { startActivity(GrokBot.overlaySettings(this)); } catch (Exception e) { grokInfo.setText("Open Android Settings > Apps > Zippy > Display over other apps."); }
        });
        c.addView(overlayButton);
        page.addView(c);

        c = Look.card(this);
        c.addView(Look.heading(this, "Background"));
        battery = Look.body(this, "");
        c.addView(Look.gap(this, 6));
        c.addView(battery);
        batteryButton = Look.button(this, "Allow background listening", false);
        batteryButton.setOnClickListener(v -> {
            try {
                startActivity(new Intent("android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) { }
        });
        c.addView(batteryButton);
        page.addView(c);

        c = Look.card(this);
        c.addView(Look.heading(this, "Updates"));
        c.addView(Look.gap(this, 6));
        c.addView(Look.body(this, "Checks GitHub (13Throne/Zippy) for a newer release and installs it right over this one."));
        updateStatus = Look.text(this, "Installed v1.5.", 14, Look.FG);
        c.addView(Look.gap(this, 6));
        c.addView(updateStatus);
        updateButton = Look.button(this, "Check for updates", true);
        updateButton.setOnClickListener(v -> Updater.check(this, updateButton, updateStatus));
        c.addView(updateButton);
        page.addView(c);
    }

    private void hookFieldSaves() {
        TextWatcher save = new After(this::persist);
        urlField.addTextChangedListener(save);
        keyField.addTextChangedListener(save);
        greetingField.addTextChangedListener(new After(() -> { zp.setGreeting(greetingField.getText().toString()); renderSummary(); }));
    }

    // ------------------------------------------------------------------ actions

    private void selectTab(Tabs.Tab t) {
        tab = t;
        zp.setTab(t);
        if (getCurrentFocus() != null) getCurrentFocus().clearFocus();
        render();
        scroll.scrollTo(0, 0);
    }

    private boolean hasMic() {
        return checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void askPermissions(boolean forStart) {
        List<String> need = new ArrayList<>();
        if (!hasMic()) need.add(android.Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            need.add("android.permission.POST_NOTIFICATIONS");
        }
        if (!need.isEmpty()) {
            startAfterPermission = forStart;
            requestPermissions(need.toArray(new String[0]), REQ_PERMS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code != REQ_PERMS) return;
        if (startAfterPermission) {
            startAfterPermission = false;
            if (hasMic()) setListening(true);
            else Store.INSTANCE.setStatus(this, "Microphone permission is required.", "");
        }
        render();
    }

    private void setListening(boolean on) {
        if (on) {
            if (!hasMic()) {
                askPermissions(true);
                render();
                return;
            }
            Store.INSTANCE.setListening(this, true);
            try { ListenService.Companion.start(this); } catch (Exception e) {
                Store.INSTANCE.setListening(this, false);
                Store.INSTANCE.setStatus(this, "Android refused to start listening. Try again with Zippy open.", "");
            }
        } else {
            Store.INSTANCE.setListening(this, false);
            try { startService(new Intent(this, ListenService.class).setAction(ListenService.ACTION_STOP)); } catch (Exception ignored) { }
        }
        render();
    }

    private void persist() {
        if (rendering || urlField == null || sensitivity == null) return;
        Settings s = Store.INSTANCE.read(this);
        int sent = sentCue >= 0 ? sentCue : s.getSentCue();
        Store.INSTANCE.saveSettings(this,
                urlField.getText().toString().trim(),
                keyField.getText().toString().trim(),
                sensitivity.getProgress(),
                1000 + 500 * waitBar.getProgress(),
                ZippyPrefs.legacyReadyCue(zp.wakeCue()),
                sent);
    }

    private void sendTest() {
        persist();
        testStatus.setText("Sending a test order…");
        final Settings s = Store.INSTANCE.read(this);
        new Thread(() -> {
            String msg;
            try {
                SendResult r = Webhook.INSTANCE.post(s, "test order");
                msg = r.getOk() ? "Zippy accepted the test." : r.getDetail();
            } catch (Throwable t) {
                msg = "Test failed: " + t.getMessage();
            }
            final String m = msg;
            runOnUiThread(() -> testStatus.setText(m));
        }, "zippy-test").start();
    }

    private void previewGreeting() {
        if (previewer == null) previewer = new Greeter(this);
        final String g = zp.greeting();
        main.postDelayed(() -> {
            if (!previewer.speak(g)) cueInfo.setText("Text-to-speech isn't ready on this phone. Zippy will beep instead of speaking until it is.");
        }, previewer.ready() ? 0 : 900);
    }

    // ------------------------------------------------------------------ render

    private void render() {
        if (zp == null || status == null) return;
        rendering = true;
        try {
            for (Tabs.Tab t : Tabs.Tab.values()) {
                pages[t.ordinal()].setVisibility(t == tab ? View.VISIBLE : View.GONE);
                Look.style(this, tabButtons[t.ordinal()], t == tab);
            }
            Settings s = Store.INSTANCE.read(this);
            renderHome(s);
            renderVoice(s);
            if (tab == Tabs.Tab.SETTINGS) renderSettings(s); // package lookups only when visible
        } finally {
            rendering = false;
        }
    }

    private void renderHome(Settings s) {
        boolean wanted = s.getListening();
        int engine = ListenService.engineState();
        listenSwitch.setChecked(wanted);
        status.setText(ListenState.statusText(wanted, engine, s.getNote()));
        long now = System.currentTimeMillis();
        if (resumed && ListenState.screenShouldStart(wanted, engine, hasMic()) && now - lastKick > 3000) {
            lastKick = now; // switch says on but nothing runs (e.g. killed): bring it back
            try { ListenService.Companion.start(this); } catch (Exception ignored) { }
        }
        String h = s.getHeard();
        heard.setText(h == null || h.trim().isEmpty() ? "Nothing heard yet." : h);
        renderSummary();
        StringBuilder b = new StringBuilder();
        try {
            List<Order> list = Store.INSTANCE.orders(this);
            int n = 0;
            for (Order o : list) {
                if (n++ >= 8) break;
                if (b.length() > 0) b.append("\n\n");
                b.append(o.getText()).append('\n');
                if (o.getOk()) b.append("Sent · ").append(Store.INSTANCE.whenLabel(o.getAt()));
                else {
                    b.append("Not sent · ").append(Store.INSTANCE.whenLabel(o.getAt()));
                    String d = o.getDetail();
                    if (d != null && !d.isEmpty()) b.append(" · ").append(d);
                }
            }
        } catch (Exception ignored) { }
        orders.setText(b.length() == 0 ? "Nothing sent from this phone yet." : b.toString());
    }

    private void renderSummary() {
        if (summary == null) return;
        Settings s = Store.INSTANCE.read(this);
        String cue = zp.wakeCue();
        String cueText = ZippyPrefs.cueLabel(cue) + (ZippyPrefs.CUE_VOICE.equals(cue) ? " (\u201c" + zp.greeting() + "\u201d)" : "");
        summary.setText(String.format(Locale.US, "Sensitivity %d of 100\nWake cue: %s\nCommands: %s\nOpen Grok Bot on wake: %s",
                Sensitivity.clamp(s.getSensitivity()), cueText,
                zp.useAndroidStt() ? "Android speech, Zippy offline as backup" : "Zippy offline recognizer",
                zp.openGrokBot() ? "On" : "Off"));
    }

    private void renderVoice(Settings s) {
        String v;
        try {
            Prints p = Store.INSTANCE.prints(this);
            List<float[]> wakes = p.getWakes();
            List<float[]> rejects = p.getRejects();
            int w = wakes == null ? 0 : wakes.size(), o = rejects == null ? 0 : rejects.size();
            if (w == 0) v = "Not trained. Any speaker saying Hey Zippy will be accepted.";
            else if (w >= 3 && !VoiceMatch.allCurrent(wakes)) v = "Retrain needed: Zippy 1.4 has a new, more reliable voice check. Until you retrain, any voice can wake Zippy.";
            else if (Sensitivity.clamp(s.getSensitivity()) >= 100) v = w + " wake takes saved. Voice check is off at Sensitivity 100.";
            else v = w + " wake takes, " + o + " others.";
        } catch (Exception e) {
            v = "Not trained.";
        }
        voice.setText(v);
        int sens = Sensitivity.clamp(s.getSensitivity());
        sensitivity.setProgress(sens);
        sensitivityLabel.setText(Sensitivity.label(sens));
        int step = Math.max(0, Math.min(14, (s.getWaitMs() - 1000 + 250) / 500));
        waitBar.setProgress(step);
        waitLabel.setText(waitText(1000 + 500 * step));
        String cue = zp.wakeCue();
        for (int i = 0; i < 4; i++) Look.style(this, cueButtons[i], CUE_KEYS[i].equals(cue));
        greetingBox.setVisibility(ZippyPrefs.CUE_VOICE.equals(cue) ? View.VISIBLE : View.GONE);
        cueInfo.setText(ZippyPrefs.CUE_VOICE.equals(cue)
                ? "After you say \u201cHey Zippy\u201d and pause, Zippy speaks this greeting, then listens for the task. Said in one breath, the task is sent right away with no greeting."
                : ZippyPrefs.CUE_SILENT.equals(cue) ? "No cue. Zippy just listens for the task."
                : "Plays the moment your voice is accepted.");
        int sent = sentCue >= 0 ? sentCue : s.getSentCue();
        for (int i = 0; i < 4; i++) Look.style(this, sentButtons[i], i == sent);
    }

    private void renderSettings(Settings s) {
        boolean stt = zp.useAndroidStt();
        sttSwitch.setChecked(stt);
        boolean avail = AndroidStt.available(this);
        sttInfo.setText((stt
                ? "After \u201cHey Zippy\u201d and a pause, Zippy pauses its own mic and lets Android's speech recognizer (usually Google, online for best accuracy) hear the task, then goes back to listening. "
                : "Zippy's offline recognizer hears the task. ")
                + "Saying it in one breath (\u201cHey Zippy, add milk\u201d) always sends what Zippy already heard. "
                + (avail ? "Android speech recognizer: available."
                         : "Android speech recognizer: not found on this phone, so Zippy's offline recognizer is used."));

        grokSwitch.setChecked(zp.openGrokBot());
        String pkg = GrokBot.findPackage(this);
        boolean overlay = GrokBot.canLaunchFromBackground(this);
        StringBuilder g = new StringBuilder();
        g.append(pkg == null ? "Grok Bot isn't installed (looked for " + GrokBot.PLAY_PACKAGE + ")."
                : "Found: " + GrokBot.label(this, pkg) + " (" + pkg + ").");
        g.append(overlay
                ? "\nDisplay over other apps: allowed, so Grok Bot can open while you're in another app."
                : "\nAndroid blocks opening apps from the background. Without \u201cDisplay over other apps\u201d, Zippy shows a tap-to-open notification instead.");
        String note = zp.grokNote();
        if (zp.openGrokBot() && note != null && !note.isEmpty()) g.append("\nLast try: ").append(note);
        grokInfo.setText(g.toString());
        overlayButton.setVisibility(overlay ? View.GONE : View.VISIBLE);

        boolean free = false;
        try { free = getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName()); } catch (Exception ignored) { }
        battery.setText(free ? "Battery limits are off for Zippy. It can keep listening with the screen off."
                : "Android may stop listening after the screen sleeps. Allow background once.");
        batteryButton.setVisibility(free ? View.GONE : View.VISIBLE);
    }

    static String waitText(int ms) {
        return String.format(Locale.US, "%.1fs after Hey Zippy", ms / 1000f);
    }

    private int dp(float v) { return Look.dp(this, v); }

    // ------------------------------------------------------------------ small listeners

    private interface IntCb { void on(int v); }

    private final class Seek implements SeekBar.OnSeekBarChangeListener {
        private final IntCb cb;
        Seek(IntCb cb) { this.cb = cb; }
        public void onProgressChanged(SeekBar b, int p, boolean user) { if (user && !rendering) cb.on(p); }
        public void onStartTrackingTouch(SeekBar b) {}
        public void onStopTrackingTouch(SeekBar b) { if (!rendering) { persist(); renderSummary(); } }
    }

    private final class After implements TextWatcher {
        private final Runnable r;
        After(Runnable r) { this.r = r; }
        public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        public void onTextChanged(CharSequence s, int a, int b, int c) {}
        public void afterTextChanged(Editable e) { if (!rendering) r.run(); }
    }
}
