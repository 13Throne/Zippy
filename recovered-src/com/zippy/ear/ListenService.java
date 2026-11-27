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
import android.media.MediaRecorder;
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
 * Zippy 1.5 listener (replaces the 1.2/1.3 Kotlin ListenService).
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
    private static final int RING = SAMPLE_RATE * 2;            // 2.0 s of audio
    private static final int VOICE_WINDOW = (int) (SAMPLE_RATE * 1.9);
    private static final int VOICE_SETTLE = (int) (SAMPLE_RATE * 0.35); // wait for "...ppy" to land

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

    private void loop(int gen) {
        System.setProperty("jna.nosys", "true");
        Model model = null;
        Recognizer rec = null;
        try {
            if (!live(gen)) return;
            update(gen, "Loading speech on this phone.", "");
            model = new Model(ModelInstaller.INSTANCE.ensure(this).getAbsolutePath());
            if (!live(gen)) return;
            rec = new Recognizer(model, (float) SAMPLE_RATE);
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
            update(gen, "Listening for Hey Zippy.", "");
            run(gen, rec);
        } catch (Throwable t) {
            if (live(gen)) fail(gen, t.getMessage() == null ? "Listening stopped." : t.getMessage());
        } finally {
            releaseMic();
            if (rec != null) try { rec.close(); } catch (Throwable ignored) { }
            if (model != null) try { model.close(); } catch (Throwable ignored) { }
            if (worker == Thread.currentThread()) setEngine(ListenState.OFF);
        }
    }

    private static AudioRecord openMic() {
        int min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord ar = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, Math.max(min, SAMPLE_RATE));
        if (ar.getState() != AudioRecord.STATE_INITIALIZED) {
            ar.release();
            return null;
        }
        return ar;
    }

    private void releaseMic() {
        AudioRecord ar = record;
        record = null;
        if (ar != null) {
            try { ar.stop(); } catch (Exception ignored) { }
            try { ar.release(); } catch (Exception ignored) { }
        }
    }

    /** Re-open Zippy's mic after a hand-off; retries while Android's recognizer lets go of it. */
    private void reopenMic(int gen) {
        for (int attempt = 0; attempt < 8 && live(gen); attempt++) {
            try {
                AudioRecord ar = record;
                if (ar == null) {
                    ar = openMic();
                    if (ar == null) throw new IllegalStateException("mic busy");
                    record = ar;
                }
                ar.startRecording();
                if (ar.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) return;
            } catch (Exception e) {
                releaseMic();
            }
            try { Thread.sleep(250); } catch (InterruptedException e) { return; }
        }
        if (live(gen)) throw new IllegalStateException("The microphone did not restart after the command.");
    }

    private static final int HANDOFF_DONE = 0, HANDOFF_VOSK = 1;

    /**
     * "Hey Zippy" ... pause. Zippy's mic is stopped (released for Android STT) so neither the
     * greeting nor the other recognizer is fighting it, then reopened. Returns HANDOFF_VOSK when
     * the task should still be heard by Vosk (STT off/unavailable/failed).
     */
    private int handOff(int gen, CommandPlan.Next next, ZippyPrefs zp, Settings settings, String heard) {
        boolean stt = next == CommandPlan.Next.ANDROID_STT;
        if (stt) releaseMic();
        else { AudioRecord ar = record; if (ar != null) try { ar.stop(); } catch (Exception ignored) { } }
        try {
            WakeCue.Action cue = WakeCue.beforeCommand(zp.wakeCue(), greeter != null && greeter.ready());
            if (cue == WakeCue.Action.VOICE) {
                boolean spoke = greeter.speakAndWait(zp.greeting(), 4000);
                android.util.Log.i("Zippy", "Greeting " + (spoke ? "spoken" : "failed, beeping"));
                cue = WakeCue.afterSpeak(spoke);
            }
            if (cue != WakeCue.Action.NONE) Cues.INSTANCE.fire(this, WakeCue.cuesMode(cue), true);
            if (!live(gen)) return HANDOFF_DONE;
            if (!stt) {
                update(gen, "Say the task.", heard);
                return HANDOFF_VOSK;
            }
            update(gen, "Say the task (Android speech is listening).", heard);
            int silence = Math.max(1000, Math.min(4000, settings.getWaitMs() / 2));
            android.util.Log.i("Zippy", "Android speech: start");
            AndroidStt.Result r = AndroidStt.listen(this, main, CommandPlan.sttTimeoutMs(settings.getWaitMs()), silence, () -> live(gen));
            android.util.Log.i("Zippy", "Android speech: " + (r.text != null && !r.text.isEmpty() ? "result" : r.describe()));
            if (!live(gen)) return HANDOFF_DONE;
            switch (CommandPlan.onStt(r.text, r.error, r.timedOut)) {
                case SEND: {
                    String task = WakePhrase.stripLeadingWake(r.text.trim(), settings.getSensitivity());
                    if (task.isEmpty()) { update(gen, "No task after Hey Zippy.", r.text); return HANDOFF_DONE; }
                    deliver(gen, task, settings);
                    return HANDOFF_DONE;
                }
                case NO_TASK:
                    update(gen, "No task after Hey Zippy.", "");
                    return HANDOFF_DONE;
                default:
                    update(gen, "Android speech " + r.describe() + ". Say the task again; Zippy is listening.", "");
                    return HANDOFF_VOSK;
            }
        } finally {
            if (live(gen)) reopenMic(gen);
        }
    }

    private void openGrokBot() {
        main.post(() -> {
            String note;
            try { note = GrokBot.open(this, appVisible); } catch (Throwable t) { note = "Grok Bot couldn't be opened."; }
            try { ZippyPrefs.of(this).setGrokNote(note); } catch (Throwable ignored) { }
        });
    }

    private void run(int gen, Recognizer rec) {
        float[] ring = new float[RING];
        short[] buf = new short[2048];
        int write = 0, filled = 0;
        long total = 0;
        boolean awaitingTask = false;   // wake accepted, waiting for the task utterance
        long taskDeadline = 0;
        boolean wakeSeen = false;       // wake phrase seen in this utterance
        long voiceDueAt = -1;           // sample count at which to run the voice check
        Boolean voiceOk = null;         // decision for this utterance
        String pendingRemainder = null; // task words heard in the same breath as the wake
        while (live(gen)) {
            AudioRecord ar = record;
            if (ar == null) throw new IllegalStateException("The microphone stopped.");
            if (holdMic) {
                try { ar.stop(); } catch (Exception ignored) { }
                update(gen, "Paused while you train your voice.", "");
                while (holdMic && live(gen)) {
                    try { Thread.sleep(200); } catch (InterruptedException e) { return; }
                }
                if (!live(gen)) return;
                ar.startRecording();
                filled = 0; awaitingTask = false; wakeSeen = false; voiceOk = null;
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
            Settings settings = Store.INSTANCE.read(this);
            int sens = settings.getSensitivity();
            if (awaitingTask && System.currentTimeMillis() > taskDeadline) {
                awaitingTask = false;
                update(gen, "No task after Hey Zippy.", "");
            }
            boolean isFinal = rec.acceptWaveForm(buf, n);
            String text = speechText(isFinal ? rec.getResult() : rec.getPartialResult());

            if (awaitingTask) {
                if (!text.isEmpty()) update(gen, "Say the task.", text);
                if (isFinal && !text.isEmpty()) {
                    String task = WakePhrase.stripLeadingWake(text, sens);
                    if (!task.isEmpty()) {
                        awaitingTask = false;
                        deliver(gen, task, settings);
                    }
                }
                continue;
            }

            if (!text.isEmpty() && !wakeSeen) {
                String rest = WakePhrase.parse(text, sens);
                if (rest != null) {
                    wakeSeen = true;
                    voiceDueAt = total + VOICE_SETTLE;
                } else {
                    update(gen, "Listening for Hey Zippy.", text);
                }
            }
            if (wakeSeen && voiceOk == null && (total >= voiceDueAt || isFinal)) {
                VoiceMatch.Result r = checkVoice(ring, write, filled, sens);
                voiceOk = r.accepted;
                if (r.accepted) {
                    ZippyPrefs zp = ZippyPrefs.of(this);
                    WakeCue.Action cue = WakeCue.atAccept(zp.wakeCue());
                    if (cue != WakeCue.Action.NONE) Cues.INSTANCE.fire(this, WakeCue.cuesMode(cue), true);
                    if (zp.openGrokBot()) openGrokBot();
                    update(gen, "Say the task.", text);
                } else {
                    update(gen, String.format(Locale.US,
                            "Heard Hey Zippy, but it didn't match your voice (score %.0f, needs %.0f). Raise Sensitivity or retrain.",
                            r.score, r.needed), text);
                }
            }
            if (isFinal) {
                if (wakeSeen && Boolean.TRUE.equals(voiceOk)) {
                    String rest = WakePhrase.parse(text, sens);
                    ZippyPrefs zp = ZippyPrefs.of(this);
                    CommandPlan.Next next = CommandPlan.afterWake(rest,
                            zp.useAndroidStt(), zp.useAndroidStt() && AndroidStt.available(this));
                    if (next == CommandPlan.Next.SEND_NOW) {
                        deliver(gen, rest, settings); // said in one breath: Vosk already has it
                    } else {
                        int h = handOff(gen, next, zp, settings, text);
                        if (!live(gen)) return;
                        try { rec.reset(); } catch (Throwable ignored) { }
                        filled = 0;
                        if (h == HANDOFF_VOSK) {
                            awaitingTask = true;
                            taskDeadline = System.currentTimeMillis() + settings.getWaitMs();
                        } else {
                            awaitingTask = false;
                        }
                    }
                }
                wakeSeen = false;
                voiceOk = null;
                voiceDueAt = -1;
            }
        }
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

    private static String speechText(String json) {
        try {
            JSONObject o = new JSONObject(json == null ? "{}" : json);
            String t = o.optString("text", "");
            if (t.trim().isEmpty()) t = o.optString("partial", "");
            return t.trim();
        } catch (Exception e) {
            return "";
        }
    }

    // ------------------------------------------------------------------ delivery / status

    private void deliver(int gen, String text, Settings settings) {
        final String task = text.trim();
        long now = System.currentTimeMillis();
        if (task.isEmpty() || (task.equals(lastSent) && now - lastSentAt < 8000)) return;
        lastSent = task;
        lastSentAt = now;
        update(gen, "Sending to Zippy.", task);
        sends.execute(() -> {
            SendResult r = Webhook.INSTANCE.post(settings, task);
            Store.INSTANCE.addOrder(this, task, r.getOk(), r.getDetail());
            if (r.getOk()) Cues.INSTANCE.fire(this, Store.INSTANCE.read(this).getSentCue(), false);
            update(gen, r.getOk() ? "Sent to Zippy." : r.getDetail(), task);
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
