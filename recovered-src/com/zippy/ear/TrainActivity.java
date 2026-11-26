package com.zippy.ear;

import android.media.AudioRecord;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import kotlin.Metadata;
import kotlin.collections.CollectionsKt;
import kotlin.jvm.internal.Intrinsics;
import org.json.JSONException;

/* JADX INFO: compiled from: TrainActivity.kt */
/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000R\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010!\n\u0002\u0010\u0014\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0005\u0018\u00002\u00020\u0001:\u0001 B\u0007¢\u0006\u0004\b\u0002\u0010\u0003J\u0012\u0010\u0019\u001a\u00020\u001a2\b\u0010\u001b\u001a\u0004\u0018\u00010\u001cH\u0014J\b\u0010\u001d\u001a\u00020\u001aH\u0002J\u0012\u0010\u001e\u001a\u0004\u0018\u00010\u00102\u0006\u0010\u001f\u001a\u00020\nH\u0002R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082\u0004¢\u0006\u0002\n\u0000R\u0014\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\b0\u0007X\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\nX\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\fX\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\fX\u0082\u000e¢\u0006\u0002\n\u0000R\u0014\u0010\u000e\u001a\b\u0012\u0004\u0012\u00020\u00100\u000fX\u0082\u0004¢\u0006\u0002\n\u0000R\u0014\u0010\u0011\u001a\b\u0012\u0004\u0012\u00020\u00100\u000fX\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010\u0012\u001a\u00020\u0013X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\u0014\u001a\u00020\u0013X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\u0015\u001a\u00020\u0013X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\u0016\u001a\u00020\u0013X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\u0017\u001a\u00020\u0018X\u0082.¢\u0006\u0002\n\u0000¨\u0006!"}, d2 = {"Lcom/zippy/ear/TrainActivity;", "Landroidx/activity/ComponentActivity;", "<init>", "()V", "handler", "Landroid/os/Handler;", "steps", "", "Lcom/zippy/ear/TrainActivity$Step;", "index", "", "busy", "", "replaced", "wakes", "", "", "rejects", NotificationCompat.CATEGORY_PROGRESS, "Landroid/widget/TextView;", "title", "hint", "message", "action", "Landroid/widget/Button;", "onCreate", "", "savedInstanceState", "Landroid/os/Bundle;", "take", "capture", "ms", "Step", "app_release"}, k = 1, mv = {2, 0, 0}, xi = 48)
public final class TrainActivity extends ComponentActivity {
    private Button action;
    private boolean busy;
    private TextView hint;
    private int index;
    private TextView message;
    private TextView progress;
    private boolean replaced;
    private TextView title;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Step> steps = CollectionsKt.listOf((Object[]) new Step[]{new Step("wake", "Say Hey Zippy", "Normal voice, close to the phone.", false), new Step("wake", "Say Hey Zippy", "The same way again.", false), new Step("wake", "Say Hey Zippy", "A little quieter.", false), new Step("wake", "Say Hey Zippy", "A little louder.", false), new Step("wake", "Say Hey Zippy", "Slightly faster.", false), new Step("wake", "Say Hey Zippy", "Slightly slower.", false), new Step("wake", "Say Hey Zippy", "Hold the phone a bit farther away.", false), new Step("wake", "Say Hey Zippy", "Normal voice.", false), new Step("wake", "Say Hey Zippy", "As you would say it across the room.", false), new Step("wake", "Say Hey Zippy", "One last time, the way you will actually say it.", false), new Step("reject", "Say hey", "Only that word.", false), new Step("reject", "Say zip", "Only that word.", false), new Step("reject", "Say what time is it", "A normal sentence, not the wake word.", false), new Step("reject", "Say hey zip", "Stop before Zippy.", false), new Step("reject", "Stay quiet", "Two seconds of the room as it usually is.", true)});
    private final List<float[]> wakes = new ArrayList();
    private final List<float[]> rejects = new ArrayList();

    @Override // androidx.activity.ComponentActivity, androidx.core.app.ComponentActivity, android.app.Activity
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TrainActivity trainActivity = this;
        LinearLayout linearLayoutPage = Ui.INSTANCE.page(trainActivity);
        Button button = Ui.INSTANCE.button(trainActivity, "Back", false);
        button.setOnClickListener(new View.OnClickListener() { // from class: com.zippy.ear.TrainActivity$$ExternalSyntheticLambda1
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f$0.finish();
            }
        });
        linearLayoutPage.addView(button);
        TextView textViewBody$default = Ui.body$default(Ui.INSTANCE, trainActivity, "1 of " + this.steps.size(), 0, 4, null);
        this.progress = textViewBody$default;
        TextView textView = null;
        if (textViewBody$default == null) {
            Intrinsics.throwUninitializedPropertyAccessException(NotificationCompat.CATEGORY_PROGRESS);
            textViewBody$default = null;
        }
        linearLayoutPage.addView(textViewBody$default);
        LinearLayout linearLayoutCard = Ui.INSTANCE.card(trainActivity);
        this.title = Ui.INSTANCE.heading(trainActivity, this.steps.get(0).getTitle());
        this.hint = Ui.body$default(Ui.INSTANCE, trainActivity, this.steps.get(0).getHint(), 0, 4, null);
        this.message = Ui.INSTANCE.body(trainActivity, "", Ui.BAD);
        Button button2 = Ui.INSTANCE.button(trainActivity, "Record", true);
        button2.setOnClickListener(new View.OnClickListener() { // from class: com.zippy.ear.TrainActivity$$ExternalSyntheticLambda2
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f$0.take();
            }
        });
        this.action = button2;
        TextView textView2 = this.title;
        if (textView2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("title");
            textView2 = null;
        }
        linearLayoutCard.addView(textView2);
        TextView textView3 = this.hint;
        if (textView3 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("hint");
            textView3 = null;
        }
        linearLayoutCard.addView(textView3);
        Button button3 = this.action;
        if (button3 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("action");
            button3 = null;
        }
        linearLayoutCard.addView(button3);
        TextView textView4 = this.message;
        if (textView4 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("message");
        } else {
            textView = textView4;
        }
        linearLayoutCard.addView(textView);
        linearLayoutPage.addView(linearLayoutCard);
        ScrollView scrollView = new ScrollView(trainActivity);
        scrollView.setBackgroundColor(Ui.BG);
        scrollView.addView(linearLayoutPage);
        setContentView(scrollView);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void take() {
        if (this.busy) {
            return;
        }
        TextView textView = null;
        if (ContextCompat.checkSelfPermission(this, "android.permission.RECORD_AUDIO") != 0) {
            TextView textView2 = this.message;
            if (textView2 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("message");
            } else {
                textView = textView2;
            }
            textView.setText("Allow the microphone for Zippy, then try again.");
            return;
        }
        this.busy = true;
        final Step step = this.steps.get(this.index);
        final int i = step.getQuiet() ? 2000 : 2200;
        Button button = this.action;
        if (button == null) {
            Intrinsics.throwUninitializedPropertyAccessException("action");
            button = null;
        }
        button.setText("Listening");
        TextView textView3 = this.message;
        if (textView3 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("message");
        } else {
            textView = textView3;
        }
        textView.setText("");
        new Thread(new Runnable() { // from class: com.zippy.ear.TrainActivity$$ExternalSyntheticLambda4
            @Override // java.lang.Runnable
            public final void run() {
                TrainActivity.take$lambda$7(this.f$0, i, step);
            }
        }).start();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void take$lambda$7(final TrainActivity trainActivity, int i, final Step step) {
        final float[] fArrCapture = trainActivity.capture(i);
        trainActivity.handler.post(new Runnable() { // from class: com.zippy.ear.TrainActivity$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() throws JSONException {
                TrainActivity.take$lambda$7$lambda$6(this.f$0, fArrCapture, step);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void take$lambda$7$lambda$6(final TrainActivity trainActivity, float[] fArr, Step step) throws JSONException {
        trainActivity.busy = false;
        Button button = trainActivity.action;
        TextView textView = null;
        TextView textView2 = null;
        TextView textView3 = null;
        TextView textView4 = null;
        Button button2 = null;
        if (button == null) {
            Intrinsics.throwUninitializedPropertyAccessException("action");
            button = null;
        }
        button.setText("Record");
        if (fArr == null) {
            TextView textView5 = trainActivity.message;
            if (textView5 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("message");
            } else {
                textView2 = textView5;
            }
            textView2.setText("The microphone did not start.");
            return;
        }
        float fRms = Voice.INSTANCE.rms(fArr);
        if (step.getQuiet() && fRms > 0.05f) {
            TextView textView6 = trainActivity.message;
            if (textView6 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("message");
            } else {
                textView3 = textView6;
            }
            textView3.setText("Still hearing sound. Try again somewhere quieter.");
            return;
        }
        if (!step.getQuiet() && fRms < 0.012f) {
            TextView textView7 = trainActivity.message;
            if (textView7 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("message");
            } else {
                textView4 = textView7;
            }
            textView4.setText("Too quiet. Move closer and say it again.");
            return;
        }
        if (!trainActivity.replaced) {
            trainActivity.wakes.clear();
            trainActivity.rejects.clear();
            trainActivity.replaced = true;
        }
        (Intrinsics.areEqual(step.getKind(), "wake") ? trainActivity.wakes : trainActivity.rejects).add(Voice.INSTANCE.fingerprint(fArr, 16000));
        Store.INSTANCE.savePrints(trainActivity, new Prints(CollectionsKt.toList(trainActivity.wakes), CollectionsKt.toList(trainActivity.rejects)));
        if (trainActivity.index + 1 >= trainActivity.steps.size()) {
            TextView textView8 = trainActivity.title;
            if (textView8 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("title");
                textView8 = null;
            }
            textView8.setText("Voice pattern saved");
            TextView textView9 = trainActivity.hint;
            if (textView9 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("hint");
                textView9 = null;
            }
            textView9.setText("Listening will ignore Hey Zippy unless it sounds like these takes.");
            TextView textView10 = trainActivity.progress;
            if (textView10 == null) {
                Intrinsics.throwUninitializedPropertyAccessException(NotificationCompat.CATEGORY_PROGRESS);
                textView10 = null;
            }
            textView10.setText("Saved on this phone");
            Button button3 = trainActivity.action;
            if (button3 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("action");
                button3 = null;
            }
            button3.setText("Done");
            Button button4 = trainActivity.action;
            if (button4 == null) {
                Intrinsics.throwUninitializedPropertyAccessException("action");
            } else {
                button2 = button4;
            }
            button2.setOnClickListener(new View.OnClickListener() { // from class: com.zippy.ear.TrainActivity$$ExternalSyntheticLambda3
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    this.f$0.finish();
                }
            });
            return;
        }
        int i = trainActivity.index + 1;
        trainActivity.index = i;
        Step step2 = trainActivity.steps.get(i);
        TextView textView11 = trainActivity.progress;
        if (textView11 == null) {
            Intrinsics.throwUninitializedPropertyAccessException(NotificationCompat.CATEGORY_PROGRESS);
            textView11 = null;
        }
        textView11.setText((trainActivity.index + 1) + " of " + trainActivity.steps.size());
        TextView textView12 = trainActivity.title;
        if (textView12 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("title");
            textView12 = null;
        }
        textView12.setText(step2.getTitle());
        TextView textView13 = trainActivity.hint;
        if (textView13 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("hint");
        } else {
            textView = textView13;
        }
        textView.setText(step2.getHint());
    }

    private final float[] capture(int ms) {
        int i = (ms * 16000) / 1000;
        AudioRecord audioRecord = new AudioRecord(1, 16000, 16, 2, Math.max(AudioRecord.getMinBufferSize(16000, 16, 2), 16000));
        if (audioRecord.getState() != 1) {
            audioRecord.release();
            return null;
        }
        audioRecord.startRecording();
        float[] fArr = new float[i];
        short[] sArr = new short[1024];
        int i2 = 0;
        while (i2 < i) {
            int i3 = audioRecord.read(sArr, 0, 1024);
            if (i3 <= 0) {
                break;
            }
            int i4 = 0;
            while (i4 < i3 && i2 < i) {
                fArr[i2] = sArr[i4] / 32768.0f;
                i4++;
                i2++;
            }
        }
        audioRecord.stop();
        audioRecord.release();
        if (i2 == i) {
            return fArr;
        }
        float[] fArrCopyOf = Arrays.copyOf(fArr, i2);
        Intrinsics.checkNotNullExpressionValue(fArrCopyOf, "copyOf(...)");
        return fArrCopyOf;
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX INFO: compiled from: TrainActivity.kt */
    @Metadata(d1 = {"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\b\u0010\n\u0002\u0010\b\n\u0002\b\u0002\b\u0082\b\u0018\u00002\u00020\u0001B'\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003\u0012\u0006\u0010\u0005\u001a\u00020\u0003\u0012\u0006\u0010\u0006\u001a\u00020\u0007¢\u0006\u0004\b\b\u0010\tJ\t\u0010\u0010\u001a\u00020\u0003HÆ\u0003J\t\u0010\u0011\u001a\u00020\u0003HÆ\u0003J\t\u0010\u0012\u001a\u00020\u0003HÆ\u0003J\t\u0010\u0013\u001a\u00020\u0007HÆ\u0003J1\u0010\u0014\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00032\b\b\u0002\u0010\u0005\u001a\u00020\u00032\b\b\u0002\u0010\u0006\u001a\u00020\u0007HÆ\u0001J\u0013\u0010\u0015\u001a\u00020\u00072\b\u0010\u0016\u001a\u0004\u0018\u00010\u0001HÖ\u0003J\t\u0010\u0017\u001a\u00020\u0018HÖ\u0001J\t\u0010\u0019\u001a\u00020\u0003HÖ\u0001R\u0011\u0010\u0002\u001a\u00020\u0003¢\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u0011\u0010\u0004\u001a\u00020\u0003¢\u0006\b\n\u0000\u001a\u0004\b\f\u0010\u000bR\u0011\u0010\u0005\u001a\u00020\u0003¢\u0006\b\n\u0000\u001a\u0004\b\r\u0010\u000bR\u0011\u0010\u0006\u001a\u00020\u0007¢\u0006\b\n\u0000\u001a\u0004\b\u000e\u0010\u000f¨\u0006\u001a"}, d2 = {"Lcom/zippy/ear/TrainActivity$Step;", "", "kind", "", "title", "hint", "quiet", "", "<init>", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)V", "getKind", "()Ljava/lang/String;", "getTitle", "getHint", "getQuiet", "()Z", "component1", "component2", "component3", "component4", "copy", "equals", "other", "hashCode", "", "toString", "app_release"}, k = 1, mv = {2, 0, 0}, xi = 48)
    static final /* data */ class Step {
        private final String hint;
        private final String kind;
        private final boolean quiet;
        private final String title;

        public static /* synthetic */ Step copy$default(Step step, String str, String str2, String str3, boolean z, int i, Object obj) {
            if ((i & 1) != 0) {
                str = step.kind;
            }
            if ((i & 2) != 0) {
                str2 = step.title;
            }
            if ((i & 4) != 0) {
                str3 = step.hint;
            }
            if ((i & 8) != 0) {
                z = step.quiet;
            }
            return step.copy(str, str2, str3, z);
        }

        /* JADX INFO: renamed from: component1, reason: from getter */
        public final String getKind() {
            return this.kind;
        }

        /* JADX INFO: renamed from: component2, reason: from getter */
        public final String getTitle() {
            return this.title;
        }

        /* JADX INFO: renamed from: component3, reason: from getter */
        public final String getHint() {
            return this.hint;
        }

        /* JADX INFO: renamed from: component4, reason: from getter */
        public final boolean getQuiet() {
            return this.quiet;
        }

        public final Step copy(String kind, String title, String hint, boolean quiet) {
            Intrinsics.checkNotNullParameter(kind, "kind");
            Intrinsics.checkNotNullParameter(title, "title");
            Intrinsics.checkNotNullParameter(hint, "hint");
            return new Step(kind, title, hint, quiet);
        }

        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Step)) {
                return false;
            }
            Step step = (Step) other;
            return Intrinsics.areEqual(this.kind, step.kind) && Intrinsics.areEqual(this.title, step.title) && Intrinsics.areEqual(this.hint, step.hint) && this.quiet == step.quiet;
        }

        public int hashCode() {
            return (((((this.kind.hashCode() * 31) + this.title.hashCode()) * 31) + this.hint.hashCode()) * 31) + Boolean.hashCode(this.quiet);
        }

        public String toString() {
            return "Step(kind=" + this.kind + ", title=" + this.title + ", hint=" + this.hint + ", quiet=" + this.quiet + ")";
        }

        public Step(String kind, String title, String hint, boolean z) {
            Intrinsics.checkNotNullParameter(kind, "kind");
            Intrinsics.checkNotNullParameter(title, "title");
            Intrinsics.checkNotNullParameter(hint, "hint");
            this.kind = kind;
            this.title = title;
            this.hint = hint;
            this.quiet = z;
        }

        public final String getHint() {
            return this.hint;
        }

        public final String getKind() {
            return this.kind;
        }

        public final boolean getQuiet() {
            return this.quiet;
        }

        public final String getTitle() {
            return this.title;
        }
    }
}
