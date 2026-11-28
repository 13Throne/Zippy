package com.zippy.ear;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Zippy 1.81: change the wake word to any phrase and lock it to Joshua's voice.
 *
 *  1. Type a phrase. It is spelled with the KWS model's own tokens on the phone (BpeTokenizer).
 *  2. Record 5-8 takes of it (2.5 s each) and, recommended, 3 ordinary sentences (4 s each).
 *  3. Tune (WakeTuner + KwsLab): learned pronunciations from what the model hears in the takes, the
 *     strictest threshold at which every take wakes it, checked against the ordinary sentences;
 *     voice prints of the takes (VoiceMatch, as in Train Hey Zippy) become the voice check.
 *  4. Test it live (counts wakes and shows the voice check), re-record if needed, then Save.
 * The listener's microphone is held while this screen is open (like TrainActivity).
 */
public class PhraseActivity extends Activity {
    static final int RATE = 16000;
    static final float TAKE_S = 2.5f, OTHER_S = 4.0f, TEST_S = 30f;
    static final String[] SENTENCES = {
            "Can you turn the lights off in the kitchen?",
            "Hey, how are you doing today? I think it might rain later.",
            "We should leave in about ten minutes, did anyone feed the dog?"};

    private final Handler main = new Handler(Looper.getMainLooper());
    private BpeTokenizer tok;
    private EditText phraseField;
    private TextView phraseInfo, takesInfo, othersInfo, tuneInfo, testInfo;
    private Button recordTake, recordOther, tuneButton, testButton, saveButton, builtinButton, clearButton;
    private final List<float[]> takes = new ArrayList<>(), others = new ArrayList<>();
    private String[] tokens;
    private String tunedFor;
    private WakeTuner.Result result;
    private List<float[]> prints;
    private volatile boolean busy, testing;
    private KwsLab lab;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().getDecorView().setBackgroundColor(Look.BG);
        setContentView(build());
        try { tok = BpeTokenizer.load(getAssets().open(BpeTokenizer.VOCAB)); } catch (Exception e) { tok = null; }
        WakeWord w = WakeWord.of(this);
        phraseField.setText(w.custom() ? w.phrase() : "");
        checkPhrase();
        renderAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ListenService.holdMic(true);
    }

    @Override
    protected void onPause() {
        testing = false;
        ListenService.holdMic(false);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        testing = false;
        KwsLab l = lab;
        lab = null;
        if (l != null) new Thread(() -> { try { Thread.sleep(400); } catch (InterruptedException ignored) { } l.release(); }).start();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ layout

    private View build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = Look.column(this);
        page.setPadding(dp(20), dp(20), dp(20), dp(32));
        page.addView(Look.title(this, "Wake word"));

        LinearLayout c = Look.card(this);
        c.addView(Look.heading(this, "1. Your wake phrase"));
        c.addView(Look.gap(this, 6));
        c.addView(Look.body(this, "Type any phrase you'll say to wake Zippy, for example \u201cHey Jarvis\u201d or \u201cOkay Computer\u201d. Two or three words with distinct sounds work best. \u201cHey Zippy\u201d stays the default."));
        phraseField = Look.field(this, "Hey Zippy", false);
        phraseField.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        phraseField.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int n) {}
            public void onTextChanged(CharSequence s, int a, int b, int n) {}
            public void afterTextChanged(android.text.Editable e) { checkPhrase(); renderAll(); }
        });
        c.addView(phraseField);
        phraseInfo = Look.body(this, "");
        c.addView(phraseInfo);
        page.addView(c);

        c = Look.card(this);
        c.addView(Look.heading(this, "2. Record it in your voice"));
        c.addView(Look.gap(this, 6));
        c.addView(Look.body(this, "Tap Record, wait for the buzz, then say the phrase once, the way you'd normally call Zippy. Record " + WakeTuner.DEFAULT_TAKES + " takes (" + WakeTuner.MIN_TAKES + " to " + WakeTuner.MAX_TAKES + "); vary it a little (closer, farther, louder, softer)."));
        recordTake = Look.button(this, "Record take 1", true);
        recordTake.setOnClickListener(v -> record(true));
        c.addView(recordTake);
        takesInfo = Look.body(this, "");
        c.addView(takesInfo);
        clearButton = Look.button(this, "Re-record all takes", false);
        clearButton.setOnClickListener(v -> { takes.clear(); others.clear(); result = null; renderAll(); });
        c.addView(clearButton);
        page.addView(c);

        c = Look.card(this);
        c.addView(Look.heading(this, "3. Ordinary speech (recommended)"));
        c.addView(Look.gap(this, 6));
        c.addView(Look.body(this, "Read three everyday sentences so Zippy can check the phrase doesn't wake on normal talk."));
        recordOther = Look.button(this, "Record sentence 1", false);
        recordOther.setOnClickListener(v -> record(false));
        c.addView(recordOther);
        othersInfo = Look.body(this, "");
        c.addView(othersInfo);
        page.addView(c);

        c = Look.card(this);
        c.addView(Look.heading(this, "4. Tune and test"));
        c.addView(Look.gap(this, 6));
        tuneButton = Look.button(this, "Tune to my takes", true);
        tuneButton.setOnClickListener(v -> tune());
        c.addView(tuneButton);
        tuneInfo = Look.body(this, "");
        c.addView(tuneInfo);
        testButton = Look.button(this, "Test it (30 s)", false);
        testButton.setOnClickListener(v -> { if (testing) testing = false; else test(); });
        c.addView(testButton);
        testInfo = Look.text(this, "", 15, Look.FG);
        c.addView(testInfo);
        page.addView(c);

        c = Look.card(this);
        c.addView(Look.heading(this, "5. Use it"));
        c.addView(Look.gap(this, 6));
        saveButton = Look.button(this, "Save and use this wake word", true);
        saveButton.setOnClickListener(v -> save());
        c.addView(saveButton);
        builtinButton = Look.button(this, "Use Hey Zippy instead", false);
        builtinButton.setOnClickListener(v -> {
            WakeWord.of(this).useBuiltin();
            toast("Zippy listens for \u201cHey Zippy\u201d again.");
            finish();
        });
        c.addView(builtinButton);
        page.addView(c);

        scroll.addView(page);
        Look.edgeToEdge(scroll);
        return scroll;
    }

    private int dp(float v) { return Look.dp(this, v); }

    // ------------------------------------------------------------------ steps

    private void checkPhrase() {
        String p = phraseField.getText().toString();
        String problem = WakeWord.problem(p);
        tokens = null;
        if (problem == null && tok != null) {
            tokens = tok.encode(p);
            if (tokens == null) problem = "Zippy can't spell that phrase for the wake-word model. Try plain English words.";
        } else if (tok == null) problem = "The wake-word model's spelling table is missing from this install.";
        if (problem != null) { phraseInfo.setText(problem); return; }
        String key = BpeTokenizer.clean(p);
        if (tunedFor != null && !tunedFor.equals(key)) { result = null; }
        String adv = WakeWord.advice(tokens);
        phraseInfo.setText("Model spelling: " + String.join(" ", tokens).replace("\u2581", "_") + (adv.isEmpty() ? "" : "\n" + adv));
    }

    private void renderAll() {
        boolean okPhrase = tokens != null;
        recordTake.setText(takes.size() >= WakeTuner.MAX_TAKES ? "All " + WakeTuner.MAX_TAKES + " takes recorded" : "Record take " + (takes.size() + 1));
        recordTake.setEnabled(okPhrase && !busy && !testing && takes.size() < WakeTuner.MAX_TAKES);
        StringBuilder t = new StringBuilder();
        t.append(takes.size()).append(" of ").append(WakeTuner.DEFAULT_TAKES).append(" takes");
        if (result != null && result.takeHit.length == takes.size()) {
            t.append(":");
            for (int i = 0; i < takes.size(); i++) t.append(" ").append(i + 1).append(result.takeHit[i] ? "\u2713" : "\u2717");
        }
        takesInfo.setText(t.toString());
        clearButton.setEnabled(!busy && !testing && (!takes.isEmpty() || !others.isEmpty()));
        boolean moreOthers = others.size() < SENTENCES.length;
        recordOther.setText(moreOthers ? "Record sentence " + (others.size() + 1) : "All sentences recorded");
        recordOther.setEnabled(okPhrase && !busy && !testing && moreOthers);
        othersInfo.setText(moreOthers ? "Say: \u201c" + SENTENCES[others.size()] + "\u201d" : others.size() + " sentences recorded.");
        tuneButton.setEnabled(okPhrase && !busy && !testing && takes.size() >= WakeTuner.MIN_TAKES);
        testButton.setEnabled(result != null && !busy);
        testButton.setText(testing ? "Stop test" : "Test it (30 s)");
        saveButton.setEnabled(result != null && result.canSave() && !busy && !testing);
        builtinButton.setVisibility(WakeWord.of(this).custom() ? View.VISIBLE : View.GONE);
        if (result == null && takes.size() >= WakeTuner.MIN_TAKES && !busy) tuneInfo.setText("Ready to tune.");
        else if (result == null && !busy) tuneInfo.setText("Record at least " + WakeTuner.MIN_TAKES + " takes first.");
    }

    private boolean hasMic() {
        return checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void record(final boolean take) {
        if (!hasMic()) { requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 3); return; }
        busy = true;
        result = null;
        final float secs = take ? TAKE_S : OTHER_S;
        (take ? takesInfo : othersInfo).setText(take ? "Recording\u2026 say the phrase now." : "Recording\u2026 read the sentence now.");
        renderAll();
        new Thread(() -> {
            float[] x = null;
            String err = null;
            try { x = capture(secs); } catch (Throwable e) { err = "The microphone didn't start (" + e.getClass().getSimpleName() + ")."; }
            final float[] audio = x;
            final String error = err;
            main.post(() -> {
                busy = false;
                if (error != null) toast(error);
                else if (peak(audio) < 0.02f) toast("That take was too quiet. Hold the phone closer and try again.");
                else (take ? takes : others).add(audio);
                renderAll();
            });
        }, "zippy-phrase-rec").start();
    }

    private float[] capture(float secs) {
        int n = (int) (RATE * secs);
        int min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord ar = new AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, Math.max(min, RATE * 2));
        if (ar.getState() != AudioRecord.STATE_INITIALIZED) { ar.release(); throw new IllegalStateException("mic"); }
        float[] out = new float[n];
        short[] buf = new short[1024];
        try {
            ar.startRecording();
            buzz(30);
            int got = 0;
            while (got < n) {
                int r = ar.read(buf, 0, Math.min(buf.length, n - got));
                if (r < 0) throw new IllegalStateException("read " + r);
                for (int i = 0; i < r; i++) out[got + i] = buf[i] / 32768f;
                got += r;
            }
        } finally {
            try { ar.stop(); } catch (Exception ignored) { }
            ar.release();
        }
        return out;
    }

    static float peak(float[] x) {
        float p = 0;
        if (x != null) for (float v : x) p = Math.max(p, Math.abs(v));
        return p;
    }

    private KwsLab lab() {
        if (lab == null) lab = new KwsLab(this);
        return lab;
    }

    private void tune() {
        if (tokens == null || takes.size() < WakeTuner.MIN_TAKES) return;
        busy = true;
        tuneInfo.setText("Tuning to your takes\u2026 (a few seconds)");
        renderAll();
        final String[] canon = tokens;
        final String key = BpeTokenizer.clean(phraseField.getText().toString());
        final List<float[]> t = new ArrayList<>(takes), o = new ArrayList<>(others);
        final int sens = Sensitivity.clamp(Store.INSTANCE.read(this).getSensitivity());
        new Thread(() -> {
            String msg;
            WakeTuner.Result r = null;
            List<float[]> ps = new ArrayList<>();
            try {
                r = WakeTuner.tune(lab(), canon, t, o);
                for (float[] x : t) ps.add(VoiceMatch.fingerprint(x, RATE));
                msg = describe(r, ps, sens);
            } catch (Throwable e) {
                msg = "Tuning failed: " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " (" + e.getMessage() + ")");
            }
            final WakeTuner.Result res = r;
            final String m = msg;
            main.post(() -> {
                busy = false;
                result = res;
                prints = ps;
                tunedFor = key;
                tuneInfo.setText(m);
                renderAll();
            });
        }, "zippy-phrase-tune").start();
    }

    static String describe(WakeTuner.Result r, List<float[]> prints, int sens) {
        StringBuilder b = new StringBuilder();
        int n = r.takeHit.length;
        if (r.allTakes()) b.append(String.format(Locale.US, "All %d takes wake it (tuned threshold %.2f; at Sensitivity %d Zippy uses %.2f).", n, r.threshold, sens, WakeTuner.runtimeThreshold(r.threshold, sens)));
        else if (r.canSave()) b.append(String.format(Locale.US, "%d of %d takes wake it (tuned threshold %.2f; at Sensitivity %d Zippy uses %.2f). The \u2717 take was treated as an outlier: re-record it to be sure, or save as is.", r.takesHit(), n, r.threshold, sens, WakeTuner.runtimeThreshold(r.threshold, sens)));
        else b.append(String.format(Locale.US, "Only %d of %d takes wake it even at the easiest setting. Re-record the \u2717 takes more clearly, or pick a phrase with more distinct sounds.", r.takesHit(), n));
        b.append(String.format(Locale.US, "\nPronunciations: your spelling + %d learned from your takes", r.learned));
        if (r.dropped > 0) b.append(String.format(Locale.US, " (%d dropped: they woke on ordinary speech)", r.dropped));
        b.append('.');
        if (r.otherHit.length > 0) {
            int oh = r.othersHit();
            b.append(oh == 0 ? String.format(Locale.US, "\nOrdinary speech: none of your %d sentences woke it.", r.otherHit.length)
                    : String.format(Locale.US, "\nOrdinary speech: %d of %d sentences still woke it. A longer or more distinctive phrase will false-wake less.", oh, r.otherHit.length));
        } else b.append("\nRecord the ordinary sentences too so Zippy can check for false wakes.");
        int match = 0;
        for (int i = 0; i < prints.size(); i++) {
            List<float[]> rest = new ArrayList<>(prints);
            rest.remove(i);
            if (VoiceMatch.check(prints.get(i), rest, sens).accepted) match++;
        }
        b.append(String.format(Locale.US, "\nVoice check: %d of %d takes match your other takes%s.", match, prints.size(),
                Sensitivity.clamp(sens) >= 100 ? " (voice check is off at Sensitivity 100)" : ""));
        return b.toString();
    }

    private void test() {
        if (result == null) return;
        if (!hasMic()) { requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 3); return; }
        testing = true;
        renderAll();
        final int sens = Sensitivity.clamp(Store.INSTANCE.read(this).getSensitivity());
        final String kw = WakeTuner.lines(result.variants, WakeTuner.runtimeThreshold(result.threshold, sens));
        final List<float[]> ps = prints;
        final String phrase = WakeWord.tidy(phraseField.getText().toString());
        testInfo.setText("Listening for \u201c" + phrase + "\u201d\u2026 say it a few times, and talk normally in between.");
        new Thread(() -> {
            int wakes = 0, voiceOk = 0;
            String last = "";
            AudioRecord ar = null;
            KwsLab.Live live = null;
            try {
                live = lab().live(kw);
                int min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                ar = new AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min, RATE * 2));
                ar.startRecording();
                short[] buf = new short[2048];
                float[] ring = new float[(int) (RATE * 1.9)];
                int w = 0;
                long end = System.currentTimeMillis() + (long) (TEST_S * 1000);
                while (testing && System.currentTimeMillis() < end) {
                    int n = ar.read(buf, 0, buf.length);
                    if (n <= 0) continue;
                    for (int i = 0; i < n; i++) { ring[w] = buf[i] / 32768f; w = (w + 1) % ring.length; }
                    if (live.accept(buf, n)) {
                        wakes++;
                        float[] win = new float[ring.length];
                        for (int i = 0; i < ring.length; i++) win[i] = ring[(w + i) % ring.length];
                        VoiceMatch.Result vr = VoiceMatch.check(VoiceMatch.fingerprint(win, RATE), ps, sens);
                        if (vr.accepted) voiceOk++;
                        last = vr.checked ? String.format(Locale.US, "voice %s (score %.0f, needs %.0f)", vr.accepted ? "matched" : "didn't match", vr.score, vr.needed) : "voice check off";
                        buzz(60);
                        final String m = String.format(Locale.US, "Heard it! %d wake(s) so far, %d passed the voice check. Last: %s.", wakes, voiceOk, last);
                        main.post(() -> testInfo.setText(m));
                    }
                }
            } catch (Throwable e) {
                last = "error: " + e.getClass().getSimpleName();
            } finally {
                if (ar != null) { try { ar.stop(); } catch (Exception ignored) { } ar.release(); }
                if (live != null) live.close();
            }
            final int fw = wakes, fv = voiceOk;
            final String fl = last;
            main.post(() -> {
                testing = false;
                testInfo.setText(fw == 0 ? "Test over: it never woke. If you said the phrase, re-record your takes (closer to how you say it) and tune again."
                        : String.format(Locale.US, "Test over: %d wake(s), %d passed the voice check (last: %s). Woke when you weren't saying it? Record the ordinary sentences, or choose a more distinctive phrase.", fw, fv, fl));
                renderAll();
            });
        }, "zippy-phrase-test").start();
    }

    private void save() {
        if (result == null || !result.canSave()) return;
        WakeWord.of(this).save(phraseField.getText().toString(), result, prints == null ? new ArrayList<>() : prints);
        toast("Zippy now wakes on \u201c" + WakeWord.tidy(phraseField.getText().toString()) + "\u201d.");
        finish();
    }

    private void buzz(int ms) {
        try {
            Vibrator v = getSystemService(Vibrator.class);
            if (v != null) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (Throwable ignored) { }
    }

    private void toast(String s) { android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_LONG).show(); }
}
