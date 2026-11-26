package com.zippy.ear;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioRecord;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import kotlin.Metadata;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;
import kotlin.ranges.RangesKt;
import kotlin.text.StringsKt;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

/* JADX INFO: compiled from: ListenService.kt */
/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000|\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0014\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u000b\n\u0002\u0018\u0002\n\u0002\b\u0005\u0018\u0000 92\u00020\u0001:\u00019B\u0007¢\u0006\u0004\b\u0002\u0010\u0003J\u0014\u0010\u0017\u001a\u0004\u0018\u00010\u00182\b\u0010\u0019\u001a\u0004\u0018\u00010\u001aH\u0016J\"\u0010\u001b\u001a\u00020\u001c2\b\u0010\u0019\u001a\u0004\u0018\u00010\u001a2\u0006\u0010\u001d\u001a\u00020\u001c2\u0006\u0010\u001e\u001a\u00020\u001cH\u0016J\b\u0010\u001f\u001a\u00020 H\u0016J\b\u0010!\u001a\u00020 H\u0002J(\u0010\"\u001a\u00020#2\u0006\u0010$\u001a\u00020%2\u0006\u0010&\u001a\u00020\u001c2\u0006\u0010'\u001a\u00020\u001c2\u0006\u0010(\u001a\u00020)H\u0002J(\u0010*\u001a\u00020%2\u0006\u0010$\u001a\u00020%2\u0006\u0010&\u001a\u00020\u001c2\u0006\u0010'\u001a\u00020\u001c2\u0006\u0010+\u001a\u00020\u001cH\u0002J\u0018\u0010,\u001a\u00020 2\u0006\u0010-\u001a\u00020\u00142\u0006\u0010(\u001a\u00020)H\u0002J\u0010\u0010.\u001a\u00020 2\u0006\u0010/\u001a\u00020\u0014H\u0002J\u0018\u00100\u001a\u00020 2\u0006\u00101\u001a\u00020\u00142\u0006\u00102\u001a\u00020\u0014H\u0002J\b\u00103\u001a\u00020 H\u0002J\u0010\u00104\u001a\u0002052\u0006\u0010-\u001a\u00020\u0014H\u0002J\b\u00106\u001a\u00020 H\u0002J\u0010\u00107\u001a\u00020\u00142\u0006\u00108\u001a\u00020\u0014H\u0002R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0007X\u0082\u0004¢\u0006\u0002\n\u0000R\u0018\u0010\b\u001a\n \n*\u0004\u0018\u00010\t0\tX\u0082\u0004¢\u0006\u0004\n\u0002\u0010\u000bR\u0010\u0010\f\u001a\u0004\u0018\u00010\rX\u0082\u000e¢\u0006\u0002\n\u0000R\u0010\u0010\u000e\u001a\u0004\u0018\u00010\u000fX\u0082\u000e¢\u0006\u0002\n\u0000R\u0014\u0010\u0010\u001a\b\u0018\u00010\u0011R\u00020\u0012X\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\u0013\u001a\u00020\u0014X\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\u0015\u001a\u00020\u0016X\u0082\u000e¢\u0006\u0002\n\u0000¨\u0006:"}, d2 = {"Lcom/zippy/ear/ListenService;", "Landroid/app/Service;", "<init>", "()V", "running", "Ljava/util/concurrent/atomic/AtomicBoolean;", "main", "Landroid/os/Handler;", "sends", "Ljava/util/concurrent/ExecutorService;", "kotlin.jvm.PlatformType", "Ljava/util/concurrent/ExecutorService;", "thread", "Ljava/lang/Thread;", "record", "Landroid/media/AudioRecord;", "wakeLock", "Landroid/os/PowerManager$WakeLock;", "Landroid/os/PowerManager;", "lastSent", "", "lastSentAt", "", "onBind", "Landroid/os/IBinder;", "intent", "Landroid/content/Intent;", "onStartCommand", "", "flags", "startId", "onDestroy", "", "loop", "matchesVoice", "", "ring", "", "write", "filled", "settings", "Lcom/zippy/ear/Settings;", "snapshot", "samples", "deliver", "text", "fail", "message", "update", "note", "heard", "holdCpu", "notification", "Landroid/app/Notification;", "ensureChannel", "speechText", "json", "Companion", "app_release"}, k = 1, mv = {2, 0, 0}, xi = 48)
public final class ListenService extends Service {
    public static final String ACTION_STOP = "com.zippy.ear.STOP";
    private static final String CHANNEL = "zippy-listen";

    /* JADX INFO: renamed from: Companion, reason: from kotlin metadata */
    public static final Companion INSTANCE = new Companion(null);
    private static final int NOTIF_ID = 41;
    private static final int SAMPLE_RATE = 16000;
    private long lastSentAt;
    private AudioRecord record;
    private Thread thread;
    private PowerManager.WakeLock wakeLock;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService sends = Executors.newSingleThreadExecutor();
    private String lastSent = "";

    @Override // android.app.Service
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override // android.app.Service
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Intrinsics.areEqual(intent != null ? intent.getAction() : null, ACTION_STOP)) {
            ListenService listenService = this;
            Store.INSTANCE.setListening(listenService, false);
            Store.INSTANCE.setStatus(listenService, "Off.", Store.INSTANCE.read(listenService).getHeard());
            stopSelf();
            return 2;
        }
        Notification notification = notification("Starting the microphone.");
        if (Build.VERSION.SDK_INT >= 30) {
            startForeground(41, notification, 128);
        } else {
            startForeground(41, notification);
        }
        holdCpu();
        if (this.thread == null) {
            this.running.set(true);
            Thread thread = new Thread(new Runnable() { // from class: com.zippy.ear.ListenService$$ExternalSyntheticLambda1
                @Override // java.lang.Runnable
                public final void run() throws Throwable {
                    this.f$0.loop();
                }
            }, CHANNEL);
            thread.start();
            this.thread = thread;
        }
        return 1;
    }

    @Override // android.app.Service
    public void onDestroy() throws InterruptedException {
        this.running.set(false);
        try {
            AudioRecord audioRecord = this.record;
            if (audioRecord != null) {
                audioRecord.stop();
            }
        } catch (Exception unused) {
        }
        Thread thread = this.thread;
        if (thread != null) {
            thread.join(2000L);
        }
        this.thread = null;
        PowerManager.WakeLock wakeLock = this.wakeLock;
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        this.sends.shutdownNow();
        super.onDestroy();
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX WARN: Code duplicated, block: B:101:0x0209  */
    /* JADX WARN: Code duplicated, block: B:103:0x020e  */
    public final void loop() throws Throwable {
        Recognizer recognizer;
        Model model;
        String str;
        boolean z;
        String str2 = "Listening for Hey Zippy.";
        String str3 = "";
        System.setProperty("jna.nosys", "true");
        try {
            update("Loading speech on this phone.", "");
            model = new Model(ModelInstaller.INSTANCE.ensure(this).getAbsolutePath());
            try {
                Recognizer recognizer2 = new Recognizer(model, 16000.0f);
                try {
                    AudioRecord audioRecord = new AudioRecord(1, SAMPLE_RATE, 16, 2, Math.max(AudioRecord.getMinBufferSize(SAMPLE_RATE, 16, 2), SAMPLE_RATE));
                    if (audioRecord.getState() != 1) {
                        audioRecord.release();
                        fail("The microphone did not start.");
                        recognizer2.close();
                        model.close();
                        return;
                    }
                    this.record = audioRecord;
                    audioRecord.startRecording();
                    update("Listening for Hey Zippy.", "");
                    int i = 48000;
                    float[] fArr = new float[48000];
                    int i2 = 2048;
                    short[] sArr = new short[2048];
                    int i3 = 0;
                    long jCurrentTimeMillis = 0;
                    boolean zMatchesVoice = true;
                    int i4 = 0;
                    int i5 = 0;
                    boolean z2 = false;
                    boolean z3 = false;
                    while (this.running.get()) {
                        int i6 = audioRecord.read(sArr, i3, i2);
                        if (i6 > 0) {
                            i5 = i5;
                            while (i3 < i6) {
                                fArr[i4] = sArr[i3] / 32768.0f;
                                i4 = (i4 + 1) % i;
                                if (i5 < i) {
                                    i5++;
                                }
                                i3++;
                            }
                            Settings settings = Store.INSTANCE.read(this);
                            if (z2) {
                                audioRecord = audioRecord;
                                if (System.currentTimeMillis() - jCurrentTimeMillis > settings.getWaitMs()) {
                                    update("No task after Hey Zippy.", str3);
                                    z2 = false;
                                    z3 = false;
                                }
                            } else {
                                audioRecord = audioRecord;
                            }
                            boolean zAcceptWaveForm = recognizer2.acceptWaveForm(sArr, i6);
                            String result = zAcceptWaveForm ? recognizer2.getResult() : recognizer2.getPartialResult();
                            Intrinsics.checkNotNull(result);
                            String strSpeechText = speechText(result);
                            if (!StringsKt.isBlank(strSpeechText)) {
                                int sensitivity = settings.getSensitivity();
                                if (!z2) {
                                    str = str3;
                                    String wake = Phrase.INSTANCE.parseWake(strSpeechText, sensitivity);
                                    if (wake == null) {
                                        update(str2, strSpeechText);
                                        if (zAcceptWaveForm) {
                                            z3 = false;
                                        }
                                        str3 = str;
                                    } else {
                                        if (z3) {
                                            str2 = str2;
                                            fArr = fArr;
                                            i5 = i5;
                                            z = true;
                                        } else {
                                            zMatchesVoice = matchesVoice(fArr, i4, i5, settings);
                                            if (zMatchesVoice) {
                                                z = true;
                                                Cues.INSTANCE.fire(this, settings.getReadyCue(), true);
                                            } else {
                                                z = true;
                                            }
                                            z3 = z;
                                        }
                                        if (zMatchesVoice) {
                                            update(StringsKt.isBlank(wake) ? "Say the task." : str2, strSpeechText);
                                            if (zAcceptWaveForm) {
                                                if (StringsKt.isBlank(wake)) {
                                                    jCurrentTimeMillis = System.currentTimeMillis();
                                                    update("Say the task.", strSpeechText);
                                                    z2 = z;
                                                } else {
                                                    deliver(wake, settings);
                                                }
                                                str3 = str;
                                                str2 = str2;
                                                fArr = fArr;
                                                i = 48000;
                                                i2 = 2048;
                                                i3 = 0;
                                                z3 = false;
                                            }
                                        } else if (zAcceptWaveForm) {
                                            update("Heard Hey Zippy, but it didn't match your voice.", strSpeechText);
                                            z3 = false;
                                        }
                                        str3 = str;
                                        str2 = str2;
                                        fArr = fArr;
                                        i = 48000;
                                        i2 = 2048;
                                        i3 = 0;
                                    }
                                } else {
                                    str2 = str2;
                                    str = str3;
                                    fArr = fArr;
                                    i5 = i5;
                                    z = true;
                                    jCurrentTimeMillis = System.currentTimeMillis();
                                    update("Say the task.", strSpeechText);
                                    if (zAcceptWaveForm) {
                                        String strStripLeadingWake = Phrase.INSTANCE.stripLeadingWake(strSpeechText, sensitivity);
                                        if (!StringsKt.isBlank(strStripLeadingWake)) {
                                            deliver(strStripLeadingWake, settings);
                                            str3 = str;
                                            str2 = str2;
                                            fArr = fArr;
                                            i = 48000;
                                            i2 = 2048;
                                            i3 = 0;
                                            z2 = false;
                                            z3 = false;
                                        }
                                    }
                                    str3 = str;
                                    str2 = str2;
                                    fArr = fArr;
                                    i = 48000;
                                    i2 = 2048;
                                    i3 = 0;
                                }
                            }
                            i = 48000;
                            i2 = 2048;
                            i3 = 0;
                        }
                    }
                    AudioRecord audioRecord2 = audioRecord;
                    audioRecord2.stop();
                    audioRecord2.release();
                    this.record = null;
                    recognizer2.close();
                    model.close();
                } catch (Exception e) {
                    e = e;
                    recognizer = recognizer2;
                    try {
                        String message = e.getMessage();
                        if (message == null) {
                            message = "Listening stopped.";
                        }
                        fail(message);
                        if (recognizer != null) {
                            recognizer.close();
                        }
                        if (model == null) {
                        }
                    } catch (Throwable th) {
                        th = th;
                        if (recognizer != null) {
                            recognizer.close();
                        }
                        if (model != null) {
                            model.close();
                        }
                        throw th;
                    }
                } catch (Throwable th2) {
                    th = th2;
                    recognizer = recognizer2;
                    if (recognizer != null) {
                        recognizer.close();
                    }
                    if (model != null) {
                        model.close();
                    }
                    throw th;
                }
            } catch (Exception e2) {
                e = e2;
                recognizer = null;
            } catch (Throwable th3) {
                th = th3;
                recognizer = null;
            }
        } catch (Exception e3) {
            e = e3;
            recognizer = null;
            model = null;
        } catch (Throwable th4) {
            th = th4;
            recognizer = null;
            model = null;
        }
    }

    private final boolean matchesVoice(float[] ring, int write, int filled, Settings settings) {
        Prints prints = Store.INSTANCE.prints(this);
        if (prints.getWakes().size() < 3) {
            return true;
        }
        try {
            return Voice.INSTANCE.matches(Voice.INSTANCE.fingerprint(snapshot(ring, write, filled, 25600), SAMPLE_RATE), prints.getWakes(), prints.getRejects(), settings.getSensitivity());
        } catch (Exception unused) {
            return false;
        }
    }

    private final float[] snapshot(float[] ring, int write, int filled, int samples) {
        int iCoerceAtLeast = RangesKt.coerceAtLeast(Math.min(filled, samples), 1);
        float[] fArr = new float[iCoerceAtLeast];
        int length = write - iCoerceAtLeast;
        if (length < 0) {
            length += ring.length;
        }
        for (int i = 0; i < iCoerceAtLeast; i++) {
            fArr[i] = ring[(length + i) % ring.length];
        }
        return fArr;
    }

    private final void deliver(String text, final Settings settings) {
        final String string = StringsKt.trim((CharSequence) text).toString();
        long jCurrentTimeMillis = System.currentTimeMillis();
        if (!Intrinsics.areEqual(string, this.lastSent) || jCurrentTimeMillis - this.lastSentAt >= 8000) {
            this.lastSent = string;
            this.lastSentAt = jCurrentTimeMillis;
            update("Sending to Zippy.", string);
            this.sends.execute(new Runnable() { // from class: com.zippy.ear.ListenService$$ExternalSyntheticLambda2
                @Override // java.lang.Runnable
                public final void run() {
                    ListenService.deliver$lambda$2(settings, string, this);
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void deliver$lambda$2(Settings settings, String str, ListenService listenService) {
        SendResult sendResultPost = Webhook.INSTANCE.post(settings, str);
        ListenService listenService2 = listenService;
        Store.INSTANCE.addOrder(listenService2, str, sendResultPost.getOk(), sendResultPost.getDetail());
        if (sendResultPost.getOk()) {
            Cues.INSTANCE.fire(listenService2, Store.INSTANCE.read(listenService2).getSentCue(), false);
        }
        listenService.update(sendResultPost.getOk() ? "Sent to Zippy." : sendResultPost.getDetail(), str);
    }

    private final void fail(String message) {
        Store.INSTANCE.setListening(this, false);
        update(message, "");
        this.main.post(new Runnable() { // from class: com.zippy.ear.ListenService$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.stopSelf();
            }
        });
    }

    private final void update(String note, String heard) {
        Store.INSTANCE.setStatus(this, note, heard);
        ((NotificationManager) getSystemService(NotificationManager.class)).notify(41, notification(note));
    }

    private final void holdCpu() {
        PowerManager.WakeLock wakeLock = this.wakeLock;
        if (wakeLock == null || !wakeLock.isHeld()) {
            PowerManager.WakeLock wakeLockNewWakeLock = ((PowerManager) getSystemService(PowerManager.class)).newWakeLock(1, "zippy:listen");
            wakeLockNewWakeLock.setReferenceCounted(false);
            wakeLockNewWakeLock.acquire(28800000L);
            this.wakeLock = wakeLockNewWakeLock;
        }
    }

    private final Notification notification(String text) {
        ensureChannel();
        ListenService listenService = this;
        PendingIntent activity = PendingIntent.getActivity(listenService, 0, new Intent(listenService, (Class<?>) MainActivity.class), 201326592);
        Notification notificationBuild = new NotificationCompat.Builder(listenService, CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("Zippy").setContentText(text).setContentIntent(activity).setOngoing(true).setOnlyAlertOnce(true).addAction(android.R.drawable.ic_media_pause, "Stop", PendingIntent.getService(listenService, 1, new Intent(listenService, (Class<?>) ListenService.class).setAction(ACTION_STOP), 201326592)).build();
        Intrinsics.checkNotNullExpressionValue(notificationBuild, "build(...)");
        return notificationBuild;
    }

    private final void ensureChannel() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(NotificationManager.class);
        if (notificationManager.getNotificationChannel(CHANNEL) != null) {
            return;
        }
        NotificationChannel notificationChannel = new NotificationChannel(CHANNEL, "Listening", 2);
        notificationChannel.setDescription("Shows while Zippy is listening");
        notificationManager.createNotificationChannel(notificationChannel);
    }

    private final String speechText(String json) {
        try {
            JSONObject jSONObject = new JSONObject(json);
            String strOptString = jSONObject.optString("text");
            if (StringsKt.isBlank(strOptString)) {
                strOptString = jSONObject.optString("partial");
            }
            Intrinsics.checkNotNullExpressionValue(strOptString, "ifBlank(...)");
            return StringsKt.trim((CharSequence) strOptString).toString();
        } catch (Exception unused) {
            return "";
        }
    }

    /* JADX INFO: compiled from: ListenService.kt */
    @Metadata(d1 = {"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\t\b\u0002¢\u0006\u0004\b\u0002\u0010\u0003J\u000e\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\rR\u000e\u0010\u0004\u001a\u00020\u0005X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0005X\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\bX\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\bX\u0082T¢\u0006\u0002\n\u0000¨\u0006\u000e"}, d2 = {"Lcom/zippy/ear/ListenService$Companion;", "", "<init>", "()V", "ACTION_STOP", "", "CHANNEL", "NOTIF_ID", "", "SAMPLE_RATE", "start", "", "context", "Landroid/content/Context;", "app_release"}, k = 1, mv = {2, 0, 0}, xi = 48)
    public static final class Companion {
        public /* synthetic */ Companion(DefaultConstructorMarker defaultConstructorMarker) {
            this();
        }

        private Companion() {
        }

        public final void start(Context context) {
            Intrinsics.checkNotNullParameter(context, "context");
            ContextCompat.startForegroundService(context, new Intent(context, (Class<?>) ListenService.class));
        }
    }
}
