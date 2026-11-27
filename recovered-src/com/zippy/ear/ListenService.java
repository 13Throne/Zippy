package com.zippy.ear;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioRecordingConfiguration;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

/**
 * Zippy 1.8: nothing you say after "Hey Zippy" is lost. The mic is never closed between the wake
 * word and the send: Zippy records from ~2 s before the wake word, Liora greets you on her own
 * thread while it keeps recording (or not at all if you're already talking, and she stops if you
 * talk over her), Sherpa-ONNX Silero VAD decides when you're done (Capture: a long pause, not the
 * first short one), and the same recording goes to Android's recognizer through a pipe (Android 13+,
 * AndroidStream) and to the offline recognizer (VoskJob). TaskClean picks the result. The "Last
 * command" timeline on Home shows each step. Confirmation, then "Open Grok Bot", come after the send.
 *
 * Zippy 1.7: the wake word is Sherpa-ONNX keyword spotting (Kws/KwsTuning) instead of Vosk
 * transcripts. Vosk now only hears commands offline (a task said in the same breath, or when
 * Android's recognizer is off/unavailable/fails) and loads in the background. After Liora's
 * greeting there is a short silent gap and any greeting echo is stripped from the task (EchoGuard).
 *
 * Zippy 1.6: wake greeting and sent confirmation can be Liora's recorded voice (MediaPlayer, mic
 * paused until OnCompletion or 4 s). Zippy 1.5 listener (replaces the 1.2/1.3 Kotlin ListenService).
 *
 * 1.5: after an accepted "Hey Zippy" with a pause, the mic is released and Android's
 * SpeechRecognizer takes the command (toggle, default on); Vosk keeps the wake word and voice
 * check and is the fallback. Wake cue can be a spoken greeting (TTS, mic paused until onDone),
 * beep, vibrate or silent. Optional "Open Grok Bot on wake".
 *
 * State fixes:
 *  - The service obeys the switch: a sticky restart, or any start while prefs say "off", stops
 *    immediately instead of opening the mic behind an "Off." screen.
 *  - Its real state (OFF / STARTING / LISTENING) is published; the screen listens for changes and
 *    re-renders live, so the switch, the status line and the mic indicator can't drift apart.
 *  - A stopped worker can't write "Listening…" or re-post the notification after it was stopped
 *    (generation check), and a new start waits for the old worker to release the mic.
 *
 * Voice fixes: the voice check runs once the whole wake phrase is in the buffer (not on the first
 * partial), uses VoiceMatch (calibrated, sensitivity-aware) and reports score/needed on a reject.
 */
public final class ListenService extends Service {
    public static final String ACTION_STOP = "com.zippy.ear.STOP";
    private static final String CHANNEL = "zippy-listen";
    private static final int NOTIF_ID = 41;
    private static final int SAMPLE_RATE = 16000;
    private static final int RING = SAMPLE_RATE * 3;            // 3.0 s of audio (2 s pre-roll + settle)
    /** Kept from before the wake word for the offline recognizer (it also catches a fast first word). */
    static final int PRE_ROLL = SAMPLE_RATE * 2;
    /** Android speech gets the recording from this long before the wake moment (end of "Zippy"). */
    static final int ANDROID_LEAD = (int) (SAMPLE_RATE * 0.35);
    /** VAD warm-up before the wake moment (fed to the VAD, not to Capture). */
    static final int VAD_WARM = SAMPLE_RATE / 2;
    private static final int VOICE_WINDOW = (int) (SAMPLE_RATE * 1.9);
    private static final int VOICE_SETTLE = (int) (SAMPLE_RATE * 0.15); // KWS fires at "...ppy"; let it land
    private static final int VOICE_SETTLE_VOSK = (int) (SAMPLE_RATE * 0.35); // fallback: Vosk partials lag

    /** Kept so MainActivity's existing ListenService.Companion.start(ctx) calls still work. */
    public static final Companion Companion = new Companion();

    private static volatile int engine = ListenState.OFF;
    private static final AtomicInteger GEN = new AtomicInteger();
    private static final List<Runnable> WATCHERS = new CopyOnWriteArrayList<>();
    private static volatile Thread worker;
    /** Set while TrainActivity is open, so training and listening never share the mic. */
    private static volatile boolean holdMic;
    /** MainActivity is on screen (background-launch rules don't apply then). */
    public static volatile boolean appVisible;
    /** Set while Zippy itself is talking (confirmation), so the listener never hears itself. */
    private static volatile boolean selfTalk, micPaused;
    private Greeter greeter;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService sends = Executors.newSingleThreadExecutor();
    private volatile AudioRecord record;
    private PowerManager.WakeLock wakeLock;
    private int myGen = -1;
    private String lastNote = null, lastHeard = null, lastSent = "";
    private long lastSentAt;

    public static final class Companion {
        private Companion() {}
        public final void start(Context context) {
            Intent i = new Intent(context, ListenService.class);
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i); else context.startService(i);
        }
    }

    // ------------------------------------------------------------------ published state

    public static int engineState() { return engine; }

    /** TrainActivity onResume/onPause: pause the listener's mic so voice takes record cleanly. */
    public static void holdMic(boolean hold) { holdMic = hold; }

    public static void watch(Runnable r) { if (!WATCHERS.contains(r)) WATCHERS.add(r); }

    public static void unwatch(Runnable r) { WATCHERS.remove(r); }

    private static void setEngine(int s) {
        if (engine == s) return;
        engine = s;
        Handler h = new Handler(Looper.getMainLooper());
        for (Runnable r : WATCHERS) h.post(r);
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        Sensitivity.migrate(this);
        try { ZippyPrefs.of(this).migrate(); } catch (Throwable ignored) { }
        try { greeter = new Greeter(this); } catch (Throwable ignored) { }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean stop = intent != null && ACTION_STOP.equals(intent.getAction());
        if (stop) { // notification "Stop" or the switch: plain startService, no foreground needed
            Store.INSTANCE.setListening(this, false);
            shutdown();
            stopSelf();
            return START_NOT_STICKY;
        }
        boolean wanted = Store.INSTANCE.read(this).getListening();
        // startForegroundService() must be answered with startForeground() even if we stop right away
        boolean foreground = goForeground("Starting the microphone.");
        if (!ListenState.shouldRun(wanted, false)) { // e.g. sticky restart after the user turned it off
            shutdown();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!foreground) { // Android refuses a background mic start (e.g. restart after the app was killed)
            Store.INSTANCE.setListening(this, false);
            setStatus("Listening stopped in the background. Turn it on again here.", "");
            shutdown();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (myGen == GEN.get() && worker != null && worker.isAlive()) {
            return START_STICKY; // already listening
        }
        holdCpu();
        myGen = GEN.incrementAndGet();
        setEngine(ListenState.STARTING);
        setStatus("Starting the microphone.", "");
        final Thread previous = worker;
        final int gen = myGen;
        Thread t = new Thread(() -> {
            if (previous != null && previous.isAlive()) {
                try { previous.join(8000); } catch (InterruptedException ignored) { return; }
            }
            loop(gen);
        }, CHANNEL);
        worker = t;
        t.start();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        shutdown();
        sends.shutdown();
        if (greeter != null) greeter.shutdown();
        super.onDestroy();
    }

    private void shutdown() {
        if (myGen == GEN.get()) GEN.incrementAndGet(); // invalidate our worker
        myGen = -1;
        AudioRecord r = record;
        if (r != null) {
            try { r.stop(); } catch (Exception ignored) { }
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            try { wakeLock.release(); } catch (Exception ignored) { }
        }
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE); else stopForeground(true);
        boolean wanted = Store.INSTANCE.read(this).getListening();
        if (!wanted) setStatus("Off.", Store.INSTANCE.read(this).getHeard());
        Thread w = worker;
        if (w == null || !w.isAlive()) setEngine(ListenState.OFF);
        // else the worker sets OFF itself once it has released the mic
    }

    private boolean live(int gen) { return GEN.get() == gen; }

    // ------------------------------------------------------------------ listening loop

    /** Offline Vosk model: loaded in the background, used for commands (and the fallback wake word). */
    private final Object voskLock = new Object();
    private Model vosk;
    private int voskGen = -1;
    private volatile String voskNote = "";
    private Kws kws;
    private SileroGate silero;
    private String sileroNote = "";
    private AcousticEchoCanceler aec;
    private volatile boolean echoOn;

    private void loop(int gen) {
        Recognizer wakeRec = null;
        try {
            if (!live(gen)) return;
            int sens = Store.INSTANCE.read(this).getSensitivity();
            if (KwsGuard.shouldTry(this)) {
                update(gen, "Loading the wake word (Sherpa-ONNX).", "");
                KwsGuard.begin(this);
                try {
                    kws = Kws.load(this, sens);
                    try {
                        silero = SileroGate.load(this);
                        sileroNote = "";
                    } catch (Throwable t) {
                        silero = null;
                        sileroNote = "Silero VAD failed (" + t.getClass().getSimpleName() + ")";
                    }
                    KwsGuard.ok(this);
                    android.util.Log.i("Zippy", "Sherpa-ONNX KWS ready: " + KwsTuning.describe(sens) + (silero != null ? "; Silero VAD ready" : "; " + sileroNote));
                } catch (Throwable t) {
                    kws = null;
                    KwsGuard.failed(this, t);
                    android.util.Log.i("Zippy", "Sherpa-ONNX KWS failed: " + t);
                }
            }
            if (!live(gen)) return;
            if (kws == null) { // 1.6 wake word: Vosk must be loaded before listening
                update(gen, "Loading speech on this phone.", "");
                System.setProperty("jna.nosys", "true");
                Model m = new Model(ModelInstaller.INSTANCE.ensure(this).getAbsolutePath());
                synchronized (voskLock) { vosk = m; voskGen = gen; }
                wakeRec = new Recognizer(m, (float) SAMPLE_RATE);
                if (silero == null && sileroNote.isEmpty()) sileroNote = "Silero VAD needs Sherpa-ONNX";
            }
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                fail(gen, "Microphone permission is required.");
                return;
            }
            AudioRecord ar = openMic();
            if (ar == null) {
                fail(gen, "The microphone did not start.");
                return;
            }
            record = ar;
            if (!live(gen)) return;
            ar.startRecording();
            setEngine(ListenState.LISTENING);
            try { AndroidStream.probe(this, main); } catch (Throwable ignored) { }
            if (kws != null) {
                loadVosk(gen);
                update(gen, "Listening for Hey Zippy.", "");
                run(gen, new SherpaWake());
            } else {
                update(gen, "Listening for Hey Zippy (offline fallback wake word).", "");
                run(gen, new VoskWake(wakeRec, gen));
            }
        } catch (Throwable t) {
            if (live(gen)) fail(gen, t.getMessage() == null ? "Listening stopped." : t.getMessage());
        } finally {
            releaseMic();
            Kws k = kws;
            kws = null;
            if (k != null) k.release();
            SileroGate g = silero;
            silero = null;
            if (g != null) g.release();
            if (wakeRec != null) try { wakeRec.close(); } catch (Throwable ignored) { }
            closeVosk(gen);
            if (worker == Thread.currentThread()) setEngine(ListenState.OFF);
        }
    }

    /** Loads the offline command recognizer off the worker thread so the wake word starts at once. */
    private void loadVosk(int gen) {
        Thread t = new Thread(() -> {
            try {
                System.setProperty("jna.nosys", "true");
                Model m = new Model(ModelInstaller.INSTANCE.ensure(this).getAbsolutePath());
                synchronized (voskLock) {
                    if (live(gen)) { vosk = m; voskGen = gen; voskNote = ""; android.util.Log.i("Zippy", "Offline command recognizer ready"); return; }
                }
                m.close();
            } catch (Throwable e) {
                voskNote = "Offline command recognizer unavailable.";
                android.util.Log.i("Zippy", voskNote);
            }
        }, "zippy-vosk");
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    private Model vosk(int gen) {
        synchronized (voskLock) { return voskGen == gen ? vosk : null; }
    }

    private void closeVosk(int gen) {
        synchronized (voskLock) {
            if (vosk != null && (voskGen == gen || !live(voskGen))) {
                try { vosk.close(); } catch (Throwable ignored) { }
                vosk = null;
                voskGen = -1;
            }
        }
    }

    private AudioRecord openMic() {
        int min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord ar = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, Math.max(min, SAMPLE_RATE * 2));
        if (ar.getState() != AudioRecord.STATE_INITIALIZED) {
            ar.release();
            return null;
        }
        echoOn = false;
        try { // Liora's voice through the speaker is removed from the recording where the phone supports it
            if (AcousticEchoCanceler.isAvailable()) {
                aec = AcousticEchoCanceler.create(ar.getAudioSessionId());
                if (aec != null) { aec.setEnabled(true); echoOn = aec.getEnabled(); }
            }
        } catch (Throwable ignored) { echoOn = false; }
        return ar;
    }

    private void releaseMic() {
        AudioRecord ar = record;
        record = null;
        AcousticEchoCanceler e = aec;
        aec = null;
        if (e != null) try { e.release(); } catch (Throwable ignored) { }
        if (ar != null) {
            try { ar.stop(); } catch (Exception ignored) { }
            try { ar.release(); } catch (Exception ignored) { }
        }
    }

    private static boolean silenced(AudioRecord ar) {
        if (Build.VERSION.SDK_INT < 29 || ar == null) return false;
        try {
            AudioRecordingConfiguration c = ar.getActiveRecordingConfiguration();
            return c != null && c.isClientSilenced();
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------ wake word sources

    private interface WakeSource {
        /** @return "Hey Zippy" / "Zippy" when the wake word was heard in this chunk, else null. */
        String accept(short[] buf, int n, int sens);
        /** Audio to let in after the hit before the voice check. */
        int settle();
        void reset();
        String engine();
    }

    private final class SherpaWake implements WakeSource {
        private int lastSens = -1;
        public String accept(short[] buf, int n, int sens) {
            if (sens != lastSens) { kws.setSensitivity(sens); lastSens = sens; }
            String hit = kws.accept(buf, n);
            if (hit == null) return null;
            android.util.Log.i("Zippy", "Sherpa-ONNX spotted " + hit);
            return "ZIPPY".equals(hit) ? "Zippy" : "Hey Zippy";
        }
        public int settle() { return VOICE_SETTLE; }
        public void reset() { kws.reset(); }
        public String engine() { return "Sherpa-ONNX"; }
    }

    /** 1.6 wake word (Vosk transcripts + WakePhrase), only when Sherpa-ONNX can't start (KwsGuard). */
    private final class VoskWake implements WakeSource {
        private final Recognizer rec;
        private final int gen;
        private boolean seen;
        VoskWake(Recognizer rec, int gen) { this.rec = rec; this.gen = gen; }
        public String accept(short[] buf, int n, int sens) {
            boolean fin = rec.acceptWaveForm(buf, n);
            String text = VoskWords.text(fin ? rec.getResult() : rec.getPartialResult());
            String hit = null;
            if (!text.isEmpty() && !seen) {
                if (WakePhrase.parse(text, sens) != null) { seen = true; hit = "Hey Zippy"; }
                else update(gen, "Listening for Hey Zippy (offline fallback wake word).", text);
            }
            if (fin) seen = false;
            return hit;
        }
        public int settle() { return VOICE_SETTLE_VOSK; }
        public void reset() { try { rec.reset(); } catch (Throwable ignored) { } seen = false; }
        public String engine() { return "offline fallback wake word"; }
    }

    // ------------------------------------------------------------------ the mic loop

    private void run(int gen, WakeSource wake) {
        float[] ring = new float[RING];
        short[] buf = new short[2048];
        int write = 0, filled = 0;
        long total = 0, hitTotal = 0, voiceDueAt = -1;
        String spotted = null;
        Session s = null;
        try {
            while (live(gen)) {
                AudioRecord ar = record;
                if (ar == null) throw new IllegalStateException("The microphone stopped.");
                if (selfTalk && s == null) { // Liora is confirming: don't listen to ourselves
                    try { ar.stop(); } catch (Exception ignored) { }
                    micPaused = true;
                    long until = System.currentTimeMillis() + 8000;
                    while (selfTalk && live(gen) && System.currentTimeMillis() < until) {
                        try { Thread.sleep(30); } catch (InterruptedException e) { micPaused = false; return; }
                    }
                    micPaused = false;
                    if (!live(gen)) return;
                    ar.startRecording();
                    wake.reset();
                    filled = 0; spotted = null;
                    continue;
                }
                if (holdMic) {
                    if (s != null) { s.cancel("paused for voice training"); s = null; }
                    try { ar.stop(); } catch (Exception ignored) { }
                    update(gen, "Paused while you train your voice.", "");
                    while (holdMic && live(gen)) {
                        try { Thread.sleep(200); } catch (InterruptedException e) { return; }
                    }
                    if (!live(gen)) return;
                    ar.startRecording();
                    wake.reset();
                    filled = 0; spotted = null;
                    update(gen, "Listening for Hey Zippy.", "");
                    continue;
                }
                int n = ar.read(buf, 0, buf.length);
                if (n <= 0) {
                    if (n < 0) throw new IllegalStateException("The microphone stopped (" + n + ").");
                    continue;
                }
                for (int i = 0; i < n; i++) {
                    ring[write] = buf[i] / 32768f;
                    write = (write + 1) % RING;
                    if (filled < RING) filled++;
                }
                total += n;

                if (s != null) { // recording the task: the mic stays open until Capture says you're done
                    s.feed(buf, n, ar);
                    if (s.done()) {
                        Session done = s;
                        s = null;
                        done.finish();
                        if (!live(gen)) return;
                        try { ar.stop(); ar.startRecording(); } catch (Exception ignored) { } // drop audio from while we waited
                        wake.reset();
                        filled = 0; spotted = null;
                        if (!selfTalk) update(gen, "Listening for Hey Zippy.", Store.INSTANCE.read(this).getHeard());
                    }
                    continue;
                }

                Settings settings = Store.INSTANCE.read(this);
                int sens = settings.getSensitivity();
                String hit = wake.accept(buf, n, sens);
                if (hit != null && spotted == null) {
                    spotted = hit;
                    hitTotal = total;
                    voiceDueAt = total + wake.settle();
                }
                if (spotted == null || total < voiceDueAt) continue;

                String label = spotted;
                spotted = null;
                VoiceMatch.Result r = checkVoice(ring, write, filled, sens);
                if (!r.accepted) {
                    update(gen, String.format(Locale.US,
                            "Heard %s, but it didn't match your voice (score %.0f, needs %.0f). Raise Sensitivity or retrain.",
                            label, r.score, r.needed), label);
                    wake.reset();
                    continue;
                }
                s = new Session(gen, wake, label, r, settings, ring, write, filled, (int) (total - hitTotal));
            }
        } finally {
            if (s != null) s.cancel("stopped");
        }
    }

    private static final class Pcm {
        short[] a = new short[SAMPLE_RATE * 8];
        int n;
        void add(short[] b, int off, int len) {
            if (n + len > a.length) a = java.util.Arrays.copyOf(a, Math.max(a.length * 2, n + len));
            System.arraycopy(b, off, a, n, len);
            n += len;
        }
    }

    /** One task: from the accepted wake word to the send. Runs on the mic thread. */
    private final class Session {
        final int gen, sens, hitIdx;
        final Settings settings;
        final ZippyPrefs zp;
        final Timeline tl;
        final Capture cap;
        final Pcm audio = new Pcm();
        final VoskJob offline;
        final EnergyVad energy = new EnergyVad();
        final SileroGate vad;
        final float[] frame = new float[Capture.FRAME];
        int fpos, frameCount, zeroChunks;
        boolean warming;
        AndroidStream android;
        String androidKind = null;
        Greeting greeting;
        String greetingText;
        volatile boolean gStarted, gEnded, gOk;
        boolean gStartSeen, gEndSeen;
        int gStartIdx = -1, gEndIdx = -1;
        long lastStatus;
        boolean wasTalking;

        Session(int gen, WakeSource wake, String label, VoiceMatch.Result voice, Settings settings,
                float[] ring, int write, int filled, int sinceHit) {
            this.gen = gen;
            this.settings = settings;
            this.sens = settings.getSensitivity();
            this.zp = ZippyPrefs.of(ListenService.this);
            int settleMs = sinceHit * 1000 / SAMPLE_RATE;
            tl = new Timeline(System.currentTimeMillis() - settleMs);
            tl.at(0, "Wake word: " + label + " (" + wake.engine() + ")");
            tl.at(settleMs, Sensitivity.clamp(sens) >= 100 ? "Voice check off (Sensitivity 100)"
                    : String.format(Locale.US, "Voice check passed (score %.0f, needs %.0f)", voice.score, voice.needed));

            float[] pre = snapshot(ring, write, filled, Math.min(RING, PRE_ROLL + sinceHit));
            short[] p = new short[pre.length];
            for (int i = 0; i < p.length; i++) p[i] = (short) Math.max(-32768, Math.min(32767, Math.round(pre[i] * 32768f)));
            hitIdx = Math.max(0, p.length - sinceHit);
            audio.add(p, 0, p.length);
            offline = new VoskJob(() -> vosk(gen));
            offline.feed(p, 0, p.length);

            Capture.Params cp = new Capture.Params();
            cp.endSilenceMs = Capture.endSilenceMs(settings.getWaitMs());
            cp.noSpeechMs = Capture.noSpeechMs(settings.getWaitMs());
            cp.voiceGreeting = ZippyPrefs.CUE_VOICE.equals(zp.wakeCue());
            vad = silero;
            cp.bargeIn = vad != null;
            cp.bargeRatio = echoOn ? 2.0f : 3.0f;
            cap = new Capture(cp);
            tl.at(settleMs, String.format(Locale.US, "Recording the task (kept %.1f s from before the wake word; ends after %.1f s of silence)",
                    hitIdx / (float) SAMPLE_RATE, cp.endSilenceMs / 1000f));

            WakeCue.Action cue = WakeCue.atAccept(zp.wakeCue());
            if (cue != WakeCue.Action.NONE) Cues.INSTANCE.fire(ListenService.this, WakeCue.cuesMode(cue), true);

            if (zp.useAndroidStt()) {
                androidKind = AndroidStream.choose(ListenService.this);
                if (androidKind != null) {
                    android = AndroidStream.start(ListenService.this, main, androidKind);
                    if (android != null) {
                        int from = Math.max(0, hitIdx - ANDROID_LEAD);
                        android.write(p, from, p.length - from);
                        tl.at(settleMs, "Android speech (" + androidKind + " recognizer) is hearing Zippy's recording");
                    } else {
                        tl.at(settleMs, "Android speech couldn't open a pipe; offline recognizer only");
                    }
                } else {
                    tl.at(settleMs, AndroidStream.sdkOk()
                            ? "Android speech can't take Zippy's recording on this phone; offline recognizer only"
                            : "Android speech with Zippy's recording needs Android 13; offline recognizer only");
                }
            } else {
                tl.at(settleMs, "Commands: offline recognizer (Android speech is off)");
            }
            if (vad != null) vad.reset(); else energy.reset();
            // Warm the VAD on the half second before the wake moment (not counted): if you ran straight
            // from "Hey Zippy" into the task, it already knows you're mid-sentence.
            int warm = Math.max(0, hitIdx - VAD_WARM);
            warming = true;
            vadFeed(p, warm, hitIdx - warm);
            warming = false;
            fpos = 0;
            vadFeed(p, hitIdx, p.length - hitIdx);
            update(gen, "Listening to the task.", label);
        }

        void feed(short[] b, int n, AudioRecord ar) {
            audio.add(b, 0, n);
            offline.feed(b, 0, n);
            if (android != null) {
                android.write(b, 0, n);
                boolean zero = true;
                for (int i = 0; i < n && zero; i++) zero = b[i] == 0;
                zeroChunks = zero ? zeroChunks + 1 : 0;
            }
            vadFeed(b, 0, n);
            if (android != null && !android.ended()) {
                if (android.backlogMs() > AndroidStream.BACKLOG_LIMIT_MS) dropAndroid("it didn't read Zippy's recording");
                else if (zeroChunks >= 4 || (frameCount % 8 == 0 && silenced(ar))) dropAndroid("it opened its own microphone, which silenced Zippy's");
            }
            long now = System.currentTimeMillis();
            if (now - lastStatus > 700) {
                lastStatus = now;
                String heard = offline.partial();
                update(gen, "Listening to the task.", heard.isEmpty() ? "…" : heard);
            }
        }

        private void dropAndroid(String why) {
            AndroidStream a = android;
            android = null;
            if (a == null) return;
            a.cancel();
            AndroidStream.remember(ListenService.this, a.kind, "no: " + why);
            tl.now("Android speech dropped: " + why + " (the offline recognizer has everything)");
        }

        private void vadFeed(short[] b, int off, int n) {
            for (int i = off; i < off + n; i++) {
                frame[fpos++] = b[i] / 32768f;
                if (fpos == Capture.FRAME) {
                    fpos = 0;
                    float rms = EnergyVad.rms(frame, Capture.FRAME);
                    boolean speech;
                    try { speech = vad != null ? vad.frame(frame) : energy.frame(rms); }
                    catch (Throwable t) { speech = energy.frame(rms); }
                    if (!warming) onFrame(speech, rms);
                }
            }
        }

        private void onFrame(boolean speech, float rms) {
            frameCount++;
            if (gStarted && !gStartSeen) {
                gStartSeen = true;
                gStartIdx = audio.n;
                cap.greetingStarted();
            }
            if (gEnded && !gEndSeen) {
                gEndSeen = true;
                gEndIdx = audio.n;
                if (gStartSeen) cap.greetingEnded(); else cap.greetingCancelled();
                tl.now(gStartSeen ? (greeting != null && greeting.stopped() ? "Greeting stopped" : gOk ? "Greeting finished" : "Greeting failed")
                        : "Greeting didn't play");
            }
            Capture.Greet before = cap.greet();
            cap.frame(speech, rms);
            if (!wasTalking && cap.talking()) tl.at(cap.speechStartMs(), "You started talking");
            wasTalking = cap.talking();
            if (before == Capture.Greet.PENDING && cap.greet() == Capture.Greet.GREET) {
                greeting = new Greeting(ListenService.this, zp, greeter);
                greetingText = greeting.text();
                final Greeting g = greeting;
                g.start(new Greeting.Events() {
                    public void started() { gStarted = true; tl.now("Greeting: " + g.label() + (g.focus() ? " (audio focus)" : "")); }
                    public void ended(boolean st, boolean ok) { gOk = ok; gEnded = true; }
                });
            } else if (before == Capture.Greet.PENDING && cap.greet() == Capture.Greet.SKIP) {
                Cues.INSTANCE.fire(ListenService.this, 1, true); // vibrate: no sound over your words
                tl.at(cap.frames() * Capture.FRAME_MS, "Greeting skipped: you were already talking (vibrated)");
            }
            if (greeting != null && !greeting.stopped() && cap.wantsStopGreeting()) {
                greeting.stop();
                tl.now(cap.bargeInMs() >= 0 ? "You talked over Liora: she stopped (recording kept)" : "You started talking: greeting cancelled");
            }
        }

        boolean done() { return cap.done(); }

        void cancel(String why) {
            if (greeting != null) greeting.stop();
            if (android != null) android.cancel();
            offline.cancel();
            tl.now("Cancelled: " + why);
            saveTimeline(tl);
        }

        /** Ends the recording, waits for the recognizers and sends the task. */
        void finish() {
            Capture.End end = cap.end();
            if (greeting != null) { greeting.stop(); greeting.await(500); }
            if (cap.speechEndMs() >= 0 && end != Capture.End.NO_SPEECH) tl.at(cap.speechEndMs(), "You stopped talking");
            switch (end) {
                case SILENCE: tl.at(cap.endMs(), String.format(Locale.US, "Done: %.1f s of silence", Capture.endSilenceMs(settings.getWaitMs()) / 1000f)); break;
                case MAX: tl.at(cap.endMs(), "Done: " + Capture.MAX_MS / 1000 + " s limit (everything so far is kept)"); break;
                default: tl.at(cap.endMs(), String.format(Locale.US, "No task: nothing said within %.1f s", Capture.noSpeechMs(settings.getWaitMs()) / 1000f));
            }
            String echoLine = "Echo canceller: " + (echoOn ? "on" : "not available on this phone (Liora's words are removed from the text instead)");
            String vadLine = "End-of-speech detector: " + (vad != null ? "Sherpa-ONNX Silero VAD" : "energy-based (" + (sileroNote.isEmpty() ? "Silero unavailable" : sileroNote) + ")");
            if (end == Capture.End.NO_SPEECH) {
                if (android != null) android.cancel();
                offline.cancel();
                tl.note(vadLine);
                tl.note(echoLine);
                saveTimeline(tl);
                update(gen, "No task after Hey Zippy.", "");
                return;
            }
            update(gen, "Got it, working out the words.", offline.partial());
            String aText = "";
            boolean early = false;
            if (android != null) {
                long closedAt = System.currentTimeMillis();
                android.finish();
                boolean ok = android.await(3000, 12000);
                aText = android.text();
                early = android.endedAt > 0 && android.endedAt < closedAt - 1000;
                int err = android.error();
                if (android.readyAt > 0) tl.at((int) (android.readyAt - tl.wakeWall()), "Android speech ready");
                String what;
                if (!ok) what = "Android speech idle for 3 s after the end; kept what it heard (" + TaskClean.words(aText) + " words)";
                else if (err != 0) what = "Android speech error " + err + " (" + AndroidStt.errorName(err) + ")";
                else what = "Android speech: " + android.segmentCount() + " segment(s), " + TaskClean.words(aText) + " words";
                tl.now(what);
                if (!aText.isEmpty() && err == 0) AndroidStream.remember(ListenService.this, android.kind, "yes");
                else if (!android.heardSomething() && err != 0 && err != 6 && err != 7) AndroidStream.remember(ListenService.this, android.kind, "no: error " + err);
                android.close();
            }
            List<String> res = offline.finish(20000);
            List<VoskWords.Word> words = VoskWords.parseAll(res);
            tl.now("Offline recognizer: " + words.size() + " words" + ("done".equals(offline.status()) ? "" : " (" + offline.status() + ")"));
            boolean played = cap.greetingPlayed() && gStartIdx >= 0;
            String g = played ? greetingText : null;
            double gs = played ? gStartIdx / (double) SAMPLE_RATE : -1;
            double ge = played ? (gEndIdx >= 0 ? gEndIdx : audio.n) / (double) SAMPLE_RATE + Capture.ECHO_TAIL_MS / 1000.0 : -1;
            String vClean = TaskClean.fromWords(words, hitIdx / (double) SAMPLE_RATE, g, gs, ge, sens);
            String aClean = TaskClean.fromText(aText, g, sens);
            TaskClean.Pick pick = TaskClean.choose(aClean, vClean, early);
            if (!aText.isEmpty()) tl.note("Android speech heard: \u201c" + clip(aText) + "\u201d");
            if (!words.isEmpty()) tl.note("Offline heard: \u201c" + clip(VoskWords.textOf(words)) + "\u201d");
            tl.note(pick.text.isEmpty() ? "Nothing to send" : "Sending (" + pick.source + (pick.why.isEmpty() ? "" : "; " + pick.why) + "): \u201c" + clip(pick.text) + "\u201d");
            tl.note("Android speech with Zippy's recording (EXTRA_AUDIO_SOURCE): " + AndroidStream.describe(ListenService.this));
            tl.note(vadLine);
            tl.note(echoLine);
            saveTimeline(tl);
            if (pick.text.isEmpty()) { update(gen, "No task after Hey Zippy.", ""); return; }
            deliver(gen, pick.text, settings, tl);
        }
    }

    private static String clip(String s) { return s.length() <= 300 ? s : s.substring(0, 297) + "…"; }

    static final String LAST_COMMAND = "lastCommand";

    private void saveTimeline(Timeline tl) {
        try { getSharedPreferences("zippy", MODE_PRIVATE).edit().putString(LAST_COMMAND, tl.text()).apply(); } catch (Throwable ignored) { }
    }

    /** Runs on the send thread after the webhook accepted the task: Liora's confirmation. */
    private void confirm() {
        ZippyPrefs zp = ZippyPrefs.of(this);
        WakeCue.Action a = WakeCue.confirmAction(zp.confirmCue());
        if (a == WakeCue.Action.NONE) return;
        if (a != WakeCue.Action.VOICE) { Cues.INSTANCE.fire(this, WakeCue.cuesMode(a), false); return; }
        selfTalk = true;
        try {
            long until = System.currentTimeMillis() + 400;
            while (!micPaused && worker != null && worker.isAlive() && System.currentTimeMillis() < until) {
                try { Thread.sleep(20); } catch (InterruptedException e) { break; }
            }
            boolean ok = sayConfirmation(zp);
            android.util.Log.i("Zippy", "Confirmation " + (ok ? "spoken" : "failed, beeping"));
            if (!ok) Cues.INSTANCE.fire(this, 2, false);
            else sleepQuietly(180);
        } finally {
            selfTalk = false;
        }
    }

    private boolean sayConfirmation(ZippyPrefs zp) {
        String id = Liora.pickConfirm(zp.confirmChoice());
        if (id != null && Liora.playAndWait(this, id, 4000)) return true;
        String text = id != null ? Liora.text(id) : zp.confirmText();
        return greeter != null && greeter.ready() && greeter.speakAndWait(text, 4000);
    }

    private static void sleepQuietly(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }

    private void openGrokBot() {
        main.post(() -> {
            String note;
            try { note = GrokBot.open(this, appVisible); } catch (Throwable t) { note = "Grok Bot couldn't be opened."; }
            try { ZippyPrefs.of(this).setGrokNote(note); } catch (Throwable ignored) { }
        });
    }

    private VoiceMatch.Result checkVoice(float[] ring, int write, int filled, int sens) {
        try {
            Prints prints = Store.INSTANCE.prints(this);
            float[] window = snapshot(ring, write, filled, VOICE_WINDOW);
            return VoiceMatch.check(VoiceMatch.fingerprint(window, SAMPLE_RATE), prints.getWakes(), sens);
        } catch (Exception e) {
            return VoiceMatch.check(null, null, sens); // untrained -> accept rather than lock the user out
        }
    }

    /** Last `samples` samples of the ring, oldest first. */
    static float[] snapshot(float[] ring, int write, int filled, int samples) {
        int n = Math.max(1, Math.min(filled, samples));
        float[] out = new float[n];
        int start = write - n;
        if (start < 0) start += ring.length;
        for (int i = 0; i < n; i++) out[i] = ring[(start + i) % ring.length];
        return out;
    }

    // ------------------------------------------------------------------ delivery / status

    private void deliver(int gen, String text, Settings settings, Timeline tl) {
        final String task = text.trim();
        long now = System.currentTimeMillis();
        if (task.isEmpty() || (task.equals(lastSent) && now - lastSentAt < 8000)) return;
        lastSent = task;
        lastSentAt = now;
        update(gen, "Sending to Zippy.", task);
        sends.execute(() -> {
            SendResult r = Webhook.INSTANCE.post(settings, task);
            Store.INSTANCE.addOrder(this, task, r.getOk(), r.getDetail());
            update(gen, r.getOk() ? "Sent to Zippy." : r.getDetail(), task);
            if (tl != null) { tl.now(r.getOk() ? "Sent to the webhook" : "Not sent: " + r.getDetail()); saveTimeline(tl); }
            if (r.getOk() && live(gen)) {
                confirm();                                         // Liora: only after the send
                if (ZippyPrefs.of(this).openGrokBot()) openGrokBot(); // and Grok Bot after that
            }
        });
    }

    private void fail(int gen, String message) {
        if (!live(gen)) return;
        Store.INSTANCE.setListening(this, false);
        setStatus(message, "");
        main.post(this::stopSelf);
    }

    /** Status + notification, only while this worker is the live one. */
    private void update(int gen, String note, String heard) {
        if (!live(gen)) return;
        setStatus(note, heard);
        if (!note.equals(lastNote)) {
            lastNote = note;
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null && live(gen)) nm.notify(NOTIF_ID, notification(note));
        }
    }

    private synchronized void setStatus(String note, String heard) {
        if (note.equals(lastNoteWritten) && heard.equals(lastHeard)) return;
        if (!note.equals(lastNoteWritten)) android.util.Log.i("Zippy", note); // status only, never the secret
        lastNoteWritten = note;
        lastHeard = heard;
        Store.INSTANCE.setStatus(this, note, heard);
    }
    private String lastNoteWritten = null;

    private boolean goForeground(String text) {
        try {
            Notification n = notification(text);
            if (Build.VERSION.SDK_INT >= 30) startForeground(NOTIF_ID, n, 128 /* FOREGROUND_SERVICE_TYPE_MICROPHONE */);
            else startForeground(NOTIF_ID, n);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void holdCpu() {
        if (wakeLock == null || !wakeLock.isHeld()) {
            PowerManager pm = getSystemService(PowerManager.class);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "zippy:listen");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(8 * 60 * 60 * 1000L);
        }
    }

    private Notification notification(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Listening", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Shows while Zippy is listening");
            nm.createNotificationChannel(ch);
        }
        int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), flags);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, ListenService.class).setAction(ACTION_STOP), flags);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Zippy")
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(null, "Stop", stop).build())
                .build();
    }
}
