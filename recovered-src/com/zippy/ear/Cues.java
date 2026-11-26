package com.zippy.ear;

import android.content.Context;
import android.media.ToneGenerator;
import android.os.VibrationEffect;
import android.os.Vibrator;
import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;

/* JADX INFO: compiled from: Cues.kt */
/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\u0004\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0003\bÆ\u0002\u0018\u00002\u00020\u0001B\t\b\u0002¢\u0006\u0004\b\u0002\u0010\u0003J\u001e\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u00052\u0006\u0010\u000e\u001a\u00020\u000fJ\u0018\u0010\u0010\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\u000e\u001a\u00020\u000fH\u0002J\u0010\u0010\u0011\u001a\u00020\n2\u0006\u0010\u000e\u001a\u00020\u000fH\u0002R\u000e\u0010\u0004\u001a\u00020\u0005X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0005X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0005X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0005X\u0086T¢\u0006\u0002\n\u0000¨\u0006\u0012"}, d2 = {"Lcom/zippy/ear/Cues;", "", "<init>", "()V", "OFF", "", "VIBRATE", "SOUND", "BOTH", "fire", "", "context", "Landroid/content/Context;", "mode", "ready", "", "vibrate", "tone", "app_release"}, k = 1, mv = {2, 0, 0}, xi = 48)
public final class Cues {
    public static final int BOTH = 3;
    public static final Cues INSTANCE = new Cues();
    public static final int OFF = 0;
    public static final int SOUND = 2;
    public static final int VIBRATE = 1;

    private Cues() {
    }

    public final void fire(Context context, int mode, boolean ready) {
        Intrinsics.checkNotNullParameter(context, "context");
        if (mode == 0) {
            return;
        }
        if (mode == 1 || mode == 3) {
            vibrate(context, ready);
        }
        if (mode == 2 || mode == 3) {
            tone(ready);
        }
    }

    private final void vibrate(Context context, boolean ready) {
        VibrationEffect vibrationEffectCreateWaveform;
        Vibrator vibrator = (Vibrator) context.getSystemService(Vibrator.class);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (ready) {
                vibrationEffectCreateWaveform = VibrationEffect.createOneShot(35L, -1);
            } else {
                vibrationEffectCreateWaveform = VibrationEffect.createWaveform(new long[]{0, 24, 36, 24}, -1);
            }
            vibrator.vibrate(vibrationEffectCreateWaveform);
        }
    }

    private final void tone(boolean ready) {
        final ToneGenerator toneGenerator = new ToneGenerator(3, 70);
        toneGenerator.startTone(ready ? 24 : 25, 140);
        new Thread(new Runnable() { // from class: com.zippy.ear.Cues$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() throws InterruptedException {
                Cues.tone$lambda$0(toneGenerator);
            }
        }).start();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void tone$lambda$0(ToneGenerator toneGenerator) throws InterruptedException {
        Thread.sleep(220L);
        toneGenerator.release();
    }
}
