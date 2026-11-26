package com.zippy.ear;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import kotlin.Metadata;
import kotlin.Unit;
import kotlin.collections.CollectionsKt;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.internal.Intrinsics;
import kotlin.ranges.RangesKt;
import kotlin.text.StringsKt;

/* JADX INFO: compiled from: MainActivity.kt */
/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000z\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\u0010\u0011\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\t\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\b\u0018\u00002\u00020\u0001B\u0007¢\u0006\u0004\b\u0002\u0010\u0003J\u0012\u0010#\u001a\u00020$2\b\u0010%\u001a\u0004\u0018\u00010&H\u0014J\b\u0010'\u001a\u00020$H\u0014J\u0018\u0010(\u001a\u00020$2\u0006\u0010)\u001a\u00020*2\u0006\u0010+\u001a\u00020\u001eH\u0002J\b\u0010,\u001a\u00020$H\u0002J\b\u0010-\u001a\u00020$H\u0002J\b\u0010.\u001a\u00020$H\u0002J\b\u0010/\u001a\u00020$H\u0002J\b\u00100\u001a\u00020$H\u0002J\b\u00101\u001a\u00020$H\u0002J2\u00102\u001a\b\u0012\u0004\u0012\u00020\u00050\u001b2\u0006\u00103\u001a\u0002042\u0006\u00105\u001a\u00020\u00182\u0012\u00106\u001a\u000e\u0012\u0004\u0012\u00020\u0018\u0012\u0004\u0012\u00020$07H\u0002J\u001e\u00108\u001a\u00020$2\f\u00109\u001a\b\u0012\u0004\u0012\u00020\u00050\u001b2\u0006\u00105\u001a\u00020\u0018H\u0002J\b\u0010:\u001a\u00020$H\u0002J\b\u0010;\u001a\u00020\u001eH\u0002J\b\u0010<\u001a\u00020\u001eH\u0002J\u0013\u0010=\u001a\b\u0012\u0004\u0012\u00020\"0!H\u0002¢\u0006\u0002\u0010>R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0005X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0005X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0005X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\u0005X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u0005X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\fX\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\u000eX\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\u000f\u001a\u00020\u0010X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\u0011\u001a\u00020\u0010X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\u0012\u001a\u00020\u0013X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\u0014\u001a\u00020\u0005X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\u0015\u001a\u00020\u0013X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\u0016\u001a\u00020\u0005X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\u0017\u001a\u00020\u0018X\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u0010\u0019\u001a\u00020\u0018X\u0082\u000e¢\u0006\u0002\n\u0000R\u0014\u0010\u001a\u001a\b\u0012\u0004\u0012\u00020\u00050\u001bX\u0082.¢\u0006\u0002\n\u0000R\u0014\u0010\u001c\u001a\b\u0012\u0004\u0012\u00020\u00050\u001bX\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\u001d\u001a\u00020\u001eX\u0082\u000e¢\u0006\u0002\n\u0000R\u001a\u0010\u001f\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\"0!0 X\u0082\u0004¢\u0006\u0002\n\u0000¨\u0006?"}, d2 = {"Lcom/zippy/ear/MainActivity;", "Landroidx/activity/ComponentActivity;", "<init>", "()V", NotificationCompat.CATEGORY_STATUS, "Landroid/widget/TextView;", "heard", "voice", "orders", "battery", "updateStatus", "updateButton", "Landroid/widget/Button;", "listenSwitch", "Landroid/widget/Switch;", "urlField", "Landroid/widget/EditText;", "keyField", "sensitivity", "Landroid/widget/SeekBar;", "sensitivityLabel", "wait", "waitLabel", "readyCue", "", "sentCue", "readyButtons", "", "sentButtons", "rendering", "", "permissions", "Landroidx/activity/result/ActivityResultLauncher;", "", "", "onCreate", "", "savedInstanceState", "Landroid/os/Bundle;", "onResume", "onSwitch", "button", "Landroid/widget/CompoundButton;", "checked", "turnOn", "stopListening", "checkUpdate", "testWebhook", "persist", "render", "cueRow", "parent", "Landroid/widget/LinearLayout;", "selected", "onPick", "Lkotlin/Function1;", "paintCues", "views", "askBattery", "hasMic", "hasNotifications", "neededPermissions", "()[Ljava/lang/String;", "app_release"}, k = 1, mv = {2, 0, 0}, xi = 48)
public final class MainActivity extends ComponentActivity {
    private TextView battery;
    private TextView heard;
    private EditText keyField;
    private Switch listenSwitch;
    private TextView orders;
    private List<? extends TextView> readyButtons;
    private boolean rendering;
    private SeekBar sensitivity;
    private TextView sensitivityLabel;
    private List<? extends TextView> sentButtons;
    private TextView status;
    private Button updateButton;
    private TextView updateStatus;
    private EditText urlField;
    private TextView voice;
    private SeekBar wait;
    private TextView waitLabel;
    private int readyCue = 1;
    private int sentCue = 1;
    private final ActivityResultLauncher<String[]> permissions = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), new ActivityResultCallback() { // from class: com.zippy.ear.MainActivity$$ExternalSyntheticLambda4
        @Override // androidx.activity.result.ActivityResultCallback
        public final void onActivityResult(Object obj) {
            MainActivity.permissions$lambda$0(this.f$0, (Map) obj);
        }
    });

    /* JADX INFO: Access modifiers changed from: private */
    public static final void permissions$lambda$0(MainActivity mainActivity, Map granted) {
        Intrinsics.checkNotNullParameter(granted, "granted");
        if (Intrinsics.areEqual(granted.get("android.permission.RECORD_AUDIO"), (Object) true)) {
            mainActivity.turnOn();
            return;
        }
        MainActivity mainActivity2 = mainActivity;
        Store.INSTANCE.setListening(mainActivity2, false);
        Store.INSTANCE.setStatus(mainActivity2, "Microphone permission is required.", "");
        mainActivity.render();
    }

    @Override // androidx.activity.ComponentActivity, androidx.core.app.ComponentActivity, android.app.Activity
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MainActivity mainActivity = this;
        Settings settings = Store.INSTANCE.read(mainActivity);
        this.readyCue = settings.getReadyCue();
        this.sentCue = settings.getSentCue();
        LinearLayout linearLayoutPage = Ui.INSTANCE.page(mainActivity);
        linearLayoutPage.addView(Ui.INSTANCE.title(mainActivity, "Zippy"));
        linearLayoutPage.addView(Ui.body$default(Ui.INSTANCE, mainActivity, "Installed listener for this phone.", 0, 4, null));
        LinearLayout linearLayoutCard = Ui.INSTANCE.card(mainActivity);
        LinearLayout linearLayout = new LinearLayout(mainActivity);
        linearLayout.setOrientation(0);
        LinearLayout linearLayout2 = new LinearLayout(mainActivity);
        linearLayout2.setOrientation(1);
        linearLayout2.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.0f));
        linearLayout2.addView(Ui.INSTANCE.heading(mainActivity, "Listening"));
        TextView textViewBody$default = Ui.body$default(Ui.INSTANCE, mainActivity, "Off.", 0, 4, null);
        this.status = textViewBody$default;
        TextView textView = null;
        if (textViewBody$default == null) {
            Intrinsics.throwUninitializedPropertyAccessException(NotificationCompat.CATEGORY_STATUS);
            textViewBody$default = null;
        }
        linearLayout2.addView(textViewBody$default);
        Switch r2 = new Switch(mainActivity);
        r2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.zippy.ear.MainActivity$$ExternalSyntheticLambda11
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public final void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                this.f$0.onSwitch(compoundButton, z);
            }
        });
        this.listenSwitch = r2;
        linearLayout.addView(linearLayout2);
        Switch r3 = this.listenSwitch;
        if (r3 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("listenSwitch");
            r3 = null;
        }
        linearLayout.addView(r3);
        linearLayoutCard.addView(linearLayout);
        TextView textViewBody = Ui.INSTANCE.body(mainActivity, "Nothing heard yet.", Ui.FG);
        this.heard = textViewBody;
        if (textViewBody == null) {
            Intrinsics.throwUninitializedPropertyAccessException("heard");
            textViewBody = null;
        }
        linearLayoutCard.addView(textViewBody);
        linearLayoutPage.addView(linearLayoutCard);
        LinearLayout linearLayoutCard2 = Ui.INSTANCE.card(mainActivity);
        linearLayoutCard2.addView(Ui.INSTANCE.heading(mainActivity, "Voice"));
        TextView textViewBody$default2 = Ui.body$default(Ui.INSTANCE, mainActivity, "Not trained.", 0, 4, null);
        this.voice = textViewBody$default2;
        if (textViewBody$default2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("voice");
            textViewBody$default2 = null;
        }
        linearLayoutCard2.addView(textViewBody$default2);
        Button button = Ui.INSTANCE.button(mainActivity, "Train Hey Zippy", true);
        button.setOnClickListener(new View.OnClickListener() { // from class: com.zippy.ear.MainActivity$$ExternalSyntheticLambda12
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.onCreate$lambda$5$lambda$4(this.f$0, view);
            }
        });
        linearLayoutCard2.addView(button);
        Button button2 = Ui.INSTANCE.button(mainActivity, "Reset voice", false);
        button2.setOnClickListener(new View.OnClickListener() { // from class: com.zippy.ear.MainActivity$$ExternalSyntheticLambda13
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.onCreate$lambda$7$lambda$6(this.f$0, view);
            }
        });
        linearLayoutCard2.addView(button2);
        linearLayoutPage.addView(linearLayoutCard2);
        LinearLayout linearLayoutCard3 = Ui.INSTANCE.card(mainActivity);
        linearLayoutCard3.addView(Ui.INSTANCE.heading(mainActivity, "Zippy"));
        linearLayoutCard3.addView(Ui.body$default(Ui.INSTANCE, mainActivity, "Open Grok, then Automations, then zippy-spoken-order. Copy the address and the secret.", 0, 4, null));
        EditText editTextField$default = Ui.field$default(Ui.INSTANCE, mainActivity, "https://", false, 4, null);
        this.urlField = editTextField$default;
        if (editTextField$default == null) {
            Intrinsics.throwUninitializedPropertyAccessException("urlField");
            editTextField$default = null;
        }
        editTextField$default.setText(settings.getWebhookUrl());
        EditText editTextField = Ui.INSTANCE.field(mainActivity, "Secret", true);
        this.keyField = editTextField;
        if (editTextField == null) {
            Intrinsics.throwUninitializedPropertyAccessException("keyField");
            editTextField = null;
        }
        editTextField.setText(settings.getWebhookKey());
        EditText editText = this.urlField;
        if (editText == null) {
            Intrinsics.throwUninitializedPropertyAccessException("urlField");
            editText = null;
        }
        linearLayoutCard3.addView(editText);
        EditText editText2 = this.keyField;
        if (editText2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("keyField");
            editText2 = null;
        }
        linearLayoutCard3.addView(editText2);
        Button button3 = Ui.INSTANCE.button(mainActivity, "Send test", false);
        button3.setOnClickListener(new View.OnClickListener() { // from class: com.zippy.ear.MainActivity$$ExternalSyntheticLambda14
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f$0.testWebhook();
            }
        });
        linearLayoutCard3.addView(button3);
        linearLayoutPage.addView(linearLayoutCard3);
        LinearLayout linearLayoutCard4 = Ui.INSTANCE.card(mainActivity);
        linearLayoutCard4.addView(Ui.INSTANCE.heading(mainActivity, "Sensitivity"));
        TextView textViewBody$default3 = Ui.body$default(Ui.INSTANCE, mainActivity, "55", 0, 4, null);
        this.sensitivityLabel = textViewBody$default3;
        if (textViewBody$default3 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("sensitivityLabel");
            textViewBody$default3 = null;
        }
        linearLayoutCard4.addView(textViewBody$default3);
        SeekBar seekBar = new SeekBar(mainActivity);
        seekBar.setMax(100);
        seekBar.setProgress(settings.getSensitivity());
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { // from class: com.zippy.ear.MainActivity$onCreate$5$1
            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStopTrackingTouch(SeekBar bar) {
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                TextView textView2 = this.this$0.sensitivityLabel;
                if (textView2 == null) {
                    Intrinsics.throwUninitializedPropertyAccessException("sensitivityLabel");
                    textView2 = null;
                }
                textView2.setText("Looser  " + value + "  Stricter");
                if (fromUser) {
                    this.this$0.persist();
                }
            }
        });
        this.sensitivity = seekBar;
        linearLayoutCard4.addView(seekBar);
        linearLayoutCard4.addView(Ui.INSTANCE.heading(mainActivity, "Wait for the task"));
        TextView textViewBody$default4 = Ui.body$default(Ui.INSTANCE, mainActivity, "4.0s", 0, 4, null);
        this.waitLabel = textViewBody$default4;
        if (textViewBody$default4 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("waitLabel");
            textViewBody$default4 = null;
        }
        linearLayoutCard4.addView(textViewBody$default4);
        SeekBar seekBar2 = new SeekBar(mainActivity);
        seekBar2.setMax(14);
        seekBar2.setProgress(RangesKt.coerceIn((settings.getWaitMs() + NotificationManagerCompat.IMPORTANCE_UNSPECIFIED) / 500, 0, 14));
        seekBar2.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { // from class: com.zippy.ear.MainActivity$onCreate$6$1
            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStopTrackingTouch(SeekBar bar) {
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                TextView textView2 = this.this$0.waitLabel;
                if (textView2 == null) {
                    Intrinsics.throwUninitializedPropertyAccessException("waitLabel");
                    textView2 = null;
                }
                textView2.setText((((double) ((value * 500) + 1000)) / 1000.0d) + "s after Hey Zippy");
                if (fromUser) {
                    this.this$0.persist();
                }
            }
        });
        this.wait = seekBar2;
        linearLayoutCard4.addView(seekBar2);
        linearLayoutPage.addView(linearLayoutCard4);
        LinearLayout linearLayoutCard5 = Ui.INSTANCE.card(mainActivity);
        linearLayoutCard5.addView(Ui.INSTANCE.heading(mainActivity, "Cues"));
        linearLayoutCard5.addView(Ui.body$default(Ui.INSTANCE, mainActivity, "When Hey Zippy hits", 0, 4, null));
        this.readyButtons = cueRow(linearLayoutCard5, settings.getReadyCue(), new Function1() { // from class: com.zippy.ear.MainActivity$$ExternalSyntheticLambda15
            @Override // kotlin.jvm.functions.Function1
            public final Object invoke(Object obj) {
                return MainActivity.onCreate$lambda$12(this.f$0, ((Integer) obj).intValue());
            }
        });
        linearLayoutCard5.addView(Ui.body$default(Ui.INSTANCE, mainActivity, "When the order is accepted", 0, 4, null));
        this.sentButtons = cueRow(linearLayoutCard5, settings.getSentCue(), new Function1() { // from class: com.zippy.ear.MainActivity$$ExternalSyntheticLambda16
            @Override // kotlin.jvm.functions.Function1
            public final Object invoke(Object obj) {
                return MainActivity.onCreate$lambda$13(this.f$0, ((Integer) obj).intValue());
            }
        });
        linearLayoutPage.addView(linearLayoutCard5);
        LinearLayout linearLayoutCard6 = Ui.INSTANCE.card(mainActivity);
        linearLayoutCard6.addView(Ui.INSTANCE.heading(mainActivity, "Background"));
        TextView textViewBody$default5 = Ui.body$default(Ui.INSTANCE, mainActivity, "", 0, 4, null);
        this.battery = textViewBody$default5;
        if (textViewBody$default5 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("battery");
            textViewBody$default5 = null;
        }
        linearLayoutCard6.addView(textViewBody$default5);
        Button button4 = Ui.INSTANCE.button(mainActivity, "Allow background listening", false);
        button4.setOnClickListener(new View.OnClickListener() { // from class: com.zippy.ear.MainActivity$$ExternalSyntheticLambda17
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f$0.askBattery();
            }
        });
        linearLayoutCard6.addView(button4);
        linearLayoutPage.addView(linearLayoutCard6);
        LinearLayout linearLayoutCard7 = Ui.INSTANCE.card(mainActivity);
        linearLayoutCard7.addView(Ui.INSTANCE.heading(mainActivity, "Updates"));
        linearLayoutCard7.addView(Ui.body$default(Ui.INSTANCE, mainActivity, "Checks 13Throne/zippy on GitHub.", 0, 4, null));
        TextView textViewBody$default6 = Ui.body$default(Ui.INSTANCE, mainActivity, "Installed 1.2.", 0, 4, null);
        this.updateStatus = textViewBody$default6;
        if (textViewBody$default6 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("updateStatus");
            textViewBody$default6 = null;
        }
        linearLayoutCard7.addView(textViewBody$default6);
        Button button5 = Ui.INSTANCE.button(mainActivity, "Check for update", false);
        button5.setOnClickListener(new View.OnClickListener() { // from class: com.zippy.ear.MainActivity$$ExternalSyntheticLambda18
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                this.f$0.checkUpdate();
            }
        });
        this.updateButton = button5;
        if (button5 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("updateButton");
            button5 = null;
        }
        linearLayoutCard7.addView(button5);
        linearLayoutPage.addView(linearLayoutCard7);
        LinearLayout linearLayoutCard8 = Ui.INSTANCE.card(mainActivity);
        linearLayoutCard8.addView(Ui.INSTANCE.heading(mainActivity, "Recent orders"));
        TextView textViewBody$default7 = Ui.body$default(Ui.INSTANCE, mainActivity, "Nothing sent from this phone yet.", 0, 4, null);
        this.orders = textViewBody$default7;
        if (textViewBody$default7 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("orders");
        } else {
            textView = textViewBody$default7;
        }
        linearLayoutCard8.addView(textView);
        linearLayoutPage.addView(linearLayoutCard8);
        ScrollView scrollView = new ScrollView(mainActivity);
        scrollView.setBackgroundColor(Ui.BG);
        scrollView.addView(linearLayoutPage);
        setContentView(scrollView);
        persist();
        render();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void onCreate$lambda$5$lambda$4(MainActivity mainActivity, View view) {
        mainActivity.stopListening();
        mainActivity.startActivity(new Intent(mainActivity, (Class<?>) TrainActivity.class));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void onCreate$lambda$7$lambda$6(MainActivity mainActivity, View view) {
        Store.INSTANCE.clearPrints(mainActivity);
        mainActivity.render();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit onCreate$lambda$12(MainActivity mainActivity, int i) {
        mainActivity.readyCue = i;
        mainActivity.persist();
        Cues.INSTANCE.fire(mainActivity, i, true);
        return Unit.INSTANCE;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final Unit onCreate$lambda$13(MainActivity mainActivity, int i) {
        mainActivity.sentCue = i;
        mainActivity.persist();
        Cues.INSTANCE.fire(mainActivity, i, false);
        return Unit.INSTANCE;
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        MainActivity mainActivity = this;
        if (Store.INSTANCE.read(mainActivity).getListening() && hasMic()) {
            ListenService.INSTANCE.start(mainActivity);
        }
        render();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void onSwitch(CompoundButton button, boolean checked) {
        if (this.rendering) {
            return;
        }
        if (checked) {
            if (hasMic() && hasNotifications()) {
                turnOn();
                return;
            } else {
                this.permissions.launch(neededPermissions());
                return;
            }
        }
        stopListening();
    }

    private final void turnOn() {
        MainActivity mainActivity = this;
        Store.INSTANCE.setListening(mainActivity, true);
        ListenService.INSTANCE.start(mainActivity);
        render();
    }

    private final void stopListening() {
        MainActivity mainActivity = this;
        Store.INSTANCE.setListening(mainActivity, false);
        startService(new Intent(mainActivity, (Class<?>) ListenService.class).setAction(ListenService.ACTION_STOP));
        render();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void checkUpdate() {
        Button button = this.updateButton;
        TextView textView = null;
        if (button == null) {
            Intrinsics.throwUninitializedPropertyAccessException("updateButton");
            button = null;
        }
        button.setEnabled(false);
        TextView textView2 = this.updateStatus;
        if (textView2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("updateStatus");
        } else {
            textView = textView2;
        }
        textView.setText("Checking GitHub.");
        new Thread(new Runnable() { // from class: com.zippy.ear.MainActivity$$ExternalSyntheticLambda2
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.checkUpdate$lambda$24(this.f$0);
            }
        }).start();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void checkUpdate$lambda$24(final MainActivity mainActivity) {
        try {
            final Updates.Offer offerLatest = Updates.INSTANCE.latest();
            if (offerLatest.getVersionCode() <= 3) {
                mainActivity.runOnUiThread(new Runnable() { // from class: com.zippy.ear.MainActivity$$ExternalSyntheticLambda5
                    @Override // java.lang.Runnable
                    public final void run() {
                        MainActivity.checkUpdate$lambda$24$lambda$19(this.f$0);
                    }
                });
            } else {
                if (!mainActivity.getPackageManager().canRequestPackageInstalls()) {
                    mainActivity.runOnUiThread(new Runnable() { // from class: com.zippy.ear.MainActivity$$ExternalSyntheticLambda6
                        @Override // java.lang.Runnable
                        public final void run() {
                            MainActivity.checkUpdate$lambda$24$lambda$20(this.f$0);
                        }
                    });
                    return;
                }
                mainActivity.runOnUiThread(new Runnable() { // from class: com.zippy.ear.MainActivity$$ExternalSyntheticLambda7
                    @Override // java.lang.Runnable
                    public final void run() {
                        MainActivity.checkUpdate$lambda$24$lambda$21(this.f$0, offerLatest);
                    }
                });
                final File fileDownload = Updates.INSTANCE.download(mainActivity, offerLatest.getApkUrl());
                mainActivity.runOnUiThread(new Runnable() { // from class: com.zippy.ear.MainActivity$$ExternalSyntheticLambda8
                    @Override // java.lang.Runnable
                    public final void run() {
                        MainActivity.checkUpdate$lambda$24$lambda$22(this.f$0, fileDownload);
                    }
                });
            }
        } catch (Exception e) {
            mainActivity.runOnUiThread(new Runnable() { // from class: com.zippy.ear.MainActivity$$ExternalSyntheticLambda9
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.checkUpdate$lambda$24$lambda$23(this.f$0, e);
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void checkUpdate$lambda$24$lambda$19(MainActivity mainActivity) {
        TextView textView = mainActivity.updateStatus;
        Button button = null;
        if (textView == null) {
            Intrinsics.throwUninitializedPropertyAccessException("updateStatus");
            textView = null;
        }
        textView.setText("You're on the latest (1.2).");
        Button button2 = mainActivity.updateButton;
        if (button2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("updateButton");
        } else {
            button = button2;
        }
        button.setEnabled(true);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void checkUpdate$lambda$24$lambda$20(MainActivity mainActivity) {
        TextView textView = mainActivity.updateStatus;
        Button button = null;
        if (textView == null) {
            Intrinsics.throwUninitializedPropertyAccessException("updateStatus");
            textView = null;
        }
        textView.setText("Allow Zippy to install updates, then tap the button again.");
        Button button2 = mainActivity.updateButton;
        if (button2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("updateButton");
        } else {
            button = button2;
        }
        button.setEnabled(true);
        mainActivity.startActivity(new Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES", Uri.parse("package:" + mainActivity.getPackageName())));
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void checkUpdate$lambda$24$lambda$21(MainActivity mainActivity, Updates.Offer offer) {
        TextView textView = mainActivity.updateStatus;
        if (textView == null) {
            Intrinsics.throwUninitializedPropertyAccessException("updateStatus");
            textView = null;
        }
        textView.setText("Downloading " + offer.getTitle() + ".");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void checkUpdate$lambda$24$lambda$22(MainActivity mainActivity, File file) {
        TextView textView = mainActivity.updateStatus;
        Button button = null;
        if (textView == null) {
            Intrinsics.throwUninitializedPropertyAccessException("updateStatus");
            textView = null;
        }
        textView.setText("Ready to install.");
        Button button2 = mainActivity.updateButton;
        if (button2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("updateButton");
        } else {
            button = button2;
        }
        button.setEnabled(true);
        Updates.INSTANCE.install(mainActivity, file);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void checkUpdate$lambda$24$lambda$23(MainActivity mainActivity, Exception exc) {
        TextView textView = mainActivity.updateStatus;
        Button button = null;
        if (textView == null) {
            Intrinsics.throwUninitializedPropertyAccessException("updateStatus");
            textView = null;
        }
        String message = exc.getMessage();
        if (message == null) {
            message = "Couldn't check for an update.";
        }
        textView.setText(message);
        Button button2 = mainActivity.updateButton;
        if (button2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("updateButton");
        } else {
            button = button2;
        }
        button.setEnabled(true);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void testWebhook() {
        persist();
        new Thread(new Runnable() { // from class: com.zippy.ear.MainActivity$$ExternalSyntheticLambda0
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.testWebhook$lambda$26(this.f$0);
            }
        }).start();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void testWebhook$lambda$26(final MainActivity mainActivity) {
        MainActivity mainActivity2 = mainActivity;
        SendResult sendResultPost = Webhook.INSTANCE.post(Store.INSTANCE.read(mainActivity2), "test order");
        Store.INSTANCE.addOrder(mainActivity2, "test order", sendResultPost.getOk(), sendResultPost.getDetail());
        Store.INSTANCE.setStatus(mainActivity2, sendResultPost.getOk() ? "Zippy accepted the test." : sendResultPost.getDetail(), "test order");
        mainActivity.runOnUiThread(new Runnable() { // from class: com.zippy.ear.MainActivity$$ExternalSyntheticLambda1
            @Override // java.lang.Runnable
            public final void run() {
                this.f$0.render();
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void persist() {
        Store store = Store.INSTANCE;
        MainActivity mainActivity = this;
        EditText editText = this.urlField;
        SeekBar seekBar = null;
        if (editText == null) {
            Intrinsics.throwUninitializedPropertyAccessException("urlField");
            editText = null;
        }
        String string = editText.getText().toString();
        EditText editText2 = this.keyField;
        if (editText2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("keyField");
            editText2 = null;
        }
        String string2 = editText2.getText().toString();
        SeekBar seekBar2 = this.sensitivity;
        if (seekBar2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("sensitivity");
            seekBar2 = null;
        }
        int progress = seekBar2.getProgress();
        SeekBar seekBar3 = this.wait;
        if (seekBar3 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("wait");
        } else {
            seekBar = seekBar3;
        }
        store.saveSettings(mainActivity, string, string2, progress, 1000 + (seekBar.getProgress() * 500), this.readyCue, this.sentCue);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void render() {
        String str;
        String str2;
        String strJoinToString$default;
        this.rendering = true;
        MainActivity mainActivity = this;
        Settings settings = Store.INSTANCE.read(mainActivity);
        Switch r2 = this.listenSwitch;
        List<? extends TextView> list = null;
        if (r2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("listenSwitch");
            r2 = null;
        }
        r2.setChecked(settings.getListening());
        TextView textView = this.status;
        if (textView == null) {
            Intrinsics.throwUninitializedPropertyAccessException(NotificationCompat.CATEGORY_STATUS);
            textView = null;
        }
        textView.setText(settings.getNote());
        TextView textView2 = this.heard;
        if (textView2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("heard");
            textView2 = null;
        }
        String heard = settings.getHeard();
        if (StringsKt.isBlank(heard)) {
            heard = "Nothing heard yet.";
        }
        textView2.setText(heard);
        Prints prints = Store.INSTANCE.prints(mainActivity);
        TextView textView3 = this.voice;
        if (textView3 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("voice");
            textView3 = null;
        }
        if (prints.getWakes().size() >= 3) {
            str = prints.getWakes().size() + " wake takes, " + prints.getRejects().size() + " others.";
        }
        textView3.setText(str);
        boolean zIsIgnoringBatteryOptimizations = ((PowerManager) getSystemService(PowerManager.class)).isIgnoringBatteryOptimizations(getPackageName());
        TextView textView4 = this.battery;
        if (textView4 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("battery");
            textView4 = null;
        }
        if (zIsIgnoringBatteryOptimizations) {
        }
        textView4.setText(str2);
        List<Order> listOrders = Store.INSTANCE.orders(mainActivity);
        TextView textView5 = this.orders;
        if (textView5 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("orders");
            textView5 = null;
        }
        if (!listOrders.isEmpty()) {
            strJoinToString$default = CollectionsKt.joinToString$default(listOrders, "\n\n", null, null, 0, null, new Function1() { // from class: com.zippy.ear.MainActivity$$ExternalSyntheticLambda3
                @Override // kotlin.jvm.functions.Function1
                public final Object invoke(Object obj) {
                    return MainActivity.render$lambda$28((Order) obj);
                }
            }, 30, null);
        }
        textView5.setText(strJoinToString$default);
        List<? extends TextView> list2 = this.readyButtons;
        if (list2 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("readyButtons");
            list2 = null;
        }
        paintCues(list2, this.readyCue);
        List<? extends TextView> list3 = this.sentButtons;
        if (list3 == null) {
            Intrinsics.throwUninitializedPropertyAccessException("sentButtons");
        } else {
            list = list3;
        }
        paintCues(list, this.sentCue);
        this.rendering = false;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final CharSequence render$lambda$28(Order order) {
        String str;
        Intrinsics.checkNotNullParameter(order, "order");
        String str2 = order.getOk() ? "Sent" : "Not sent";
        if (order.getOk()) {
            str = "";
        } else {
            str = " · " + order.getDetail();
        }
        return order.getText() + "\n" + str2 + " · " + Store.INSTANCE.whenLabel(order.getAt()) + str;
    }

    private final List<TextView> cueRow(LinearLayout parent, int selected, final Function1<? super Integer, Unit> onPick) {
        MainActivity mainActivity = this;
        LinearLayout linearLayout = new LinearLayout(mainActivity);
        linearLayout.setOrientation(0);
        List listListOf = CollectionsKt.listOf((Object[]) new String[]{"Off", "Vibrate", "Sound", "Both"});
        ArrayList arrayList = new ArrayList(CollectionsKt.collectionSizeOrDefault(listListOf, 10));
        final int i = 0;
        for (Object obj : listListOf) {
            int i2 = i + 1;
            if (i < 0) {
                CollectionsKt.throwIndexOverflow();
            }
            TextView textView = new TextView(mainActivity);
            textView.setText((String) obj);
            textView.setGravity(17);
            textView.setTextColor(i == selected ? Ui.ACCENT_FG : Ui.MUTED);
            textView.setPadding(0, Ui.INSTANCE.dp(mainActivity, 12), 0, Ui.INSTANCE.dp(mainActivity, 12));
            textView.setBackground(Ui.INSTANCE.rounded(i == selected ? Ui.ACCENT : Ui.BG, Ui.INSTANCE.dp(mainActivity, 8)));
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
            layoutParams.setMarginEnd(Ui.INSTANCE.dp(mainActivity, 4));
            textView.setLayoutParams(layoutParams);
            textView.setOnClickListener(new View.OnClickListener() { // from class: com.zippy.ear.MainActivity$$ExternalSyntheticLambda10
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.cueRow$lambda$33$lambda$32$lambda$31(onPick, i, view);
                }
            });
            arrayList.add(textView);
            i = i2;
        }
        ArrayList arrayList2 = arrayList;
        Iterator it = arrayList2.iterator();
        while (it.hasNext()) {
            linearLayout.addView((TextView) it.next());
        }
        parent.addView(linearLayout);
        return arrayList2;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static final void cueRow$lambda$33$lambda$32$lambda$31(Function1 function1, int i, View view) {
        function1.invoke(Integer.valueOf(i));
    }

    private final void paintCues(List<? extends TextView> views, int selected) {
        int i = 0;
        for (Object obj : views) {
            int i2 = i + 1;
            if (i < 0) {
                CollectionsKt.throwIndexOverflow();
            }
            TextView textView = (TextView) obj;
            textView.setTextColor(i == selected ? Ui.ACCENT_FG : Ui.MUTED);
            textView.setBackground(Ui.INSTANCE.rounded(i == selected ? Ui.ACCENT : Ui.BG, Ui.INSTANCE.dp(this, 8)));
            i = i2;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final void askBattery() {
        startActivity(new Intent("android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", Uri.parse("package:" + getPackageName())));
    }

    private final boolean hasMic() {
        return ContextCompat.checkSelfPermission(this, "android.permission.RECORD_AUDIO") == 0;
    }

    private final boolean hasNotifications() {
        return Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS") == 0;
    }

    private final String[] neededPermissions() {
        List listMutableListOf = CollectionsKt.mutableListOf("android.permission.RECORD_AUDIO");
        if (Build.VERSION.SDK_INT >= 33) {
            listMutableListOf.add("android.permission.POST_NOTIFICATIONS");
        }
        return (String[]) listMutableListOf.toArray(new String[0]);
    }
}
