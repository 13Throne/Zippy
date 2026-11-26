package com.zippy.ear;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;

/* JADX INFO: compiled from: Ui.kt */
/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000f\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\t\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\bÆ\u0002\u0018\u00002\u00020\u0001B\t\b\u0002¢\u0006\u0004\b\u0002\u0010\u0003J\u0016\u0010\r\u001a\u00020\u00052\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u0005J\u000e\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u000e\u001a\u00020\u000fJ\u000e\u0010\u0013\u001a\u00020\u00122\u0006\u0010\u000e\u001a\u00020\u000fJ\u0016\u0010\u0014\u001a\u00020\u00152\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0016\u001a\u00020\u0017J\u0016\u0010\u0018\u001a\u00020\u00152\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0016\u001a\u00020\u0017J \u0010\u0019\u001a\u00020\u00152\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0016\u001a\u00020\u00172\b\b\u0002\u0010\u001a\u001a\u00020\u0005J \u0010\u001b\u001a\u00020\u001c2\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u001d\u001a\u00020\u00172\b\b\u0002\u0010\u001e\u001a\u00020\u001fJ\u001e\u0010 \u001a\u00020!2\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0016\u001a\u00020\u00172\u0006\u0010\"\u001a\u00020\u001fJ\u0016\u0010#\u001a\u00020$2\u0006\u0010\u001a\u001a\u00020\u00052\u0006\u0010%\u001a\u00020\u0005J\u0018\u0010&\u001a\u00020'2\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010(\u001a\u00020\u0005H\u0002J\u0016\u0010)\u001a\u00020*2\u0006\u0010+\u001a\u00020,2\u0006\u0010-\u001a\u00020\u001fR\u000e\u0010\u0004\u001a\u00020\u0005X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0005X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0005X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0005X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\u0005X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u0005X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\u0005X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\u0005X\u0086T¢\u0006\u0002\n\u0000¨\u0006."}, d2 = {"Lcom/zippy/ear/Ui;", "", "<init>", "()V", "BG", "", "SURFACE", "FG", "MUTED", "FAINT", "ACCENT", "ACCENT_FG", "BAD", "dp", "context", "Landroid/content/Context;", "value", "page", "Landroid/widget/LinearLayout;", "card", "title", "Landroid/widget/TextView;", "text", "", "heading", "body", "color", "field", "Landroid/widget/EditText;", "hint", "password", "", "button", "Landroid/widget/Button;", "filled", "rounded", "Landroid/graphics/drawable/GradientDrawable;", "radius", "gap", "Landroid/widget/LinearLayout$LayoutParams;", "top", "hide", "", "view", "Landroid/view/View;", "gone", "app_release"}, k = 1, mv = {2, 0, 0}, xi = 48)
public final class Ui {
    public static final int ACCENT = -1646122;
    public static final int ACCENT_FG = -15329516;
    public static final int BAD = -3896964;
    public static final int BG = -15724273;
    public static final int FAINT = -9146522;
    public static final int FG = -1250588;
    public static final Ui INSTANCE = new Ui();
    public static final int MUTED = -6054253;
    public static final int SURFACE = -15197673;

    private Ui() {
    }

    public final int dp(Context context, int value) {
        Intrinsics.checkNotNullParameter(context, "context");
        return (int) (value * context.getResources().getDisplayMetrics().density);
    }

    public final LinearLayout page(Context context) {
        Intrinsics.checkNotNullParameter(context, "context");
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(1);
        linearLayout.setBackgroundColor(BG);
        Ui ui = INSTANCE;
        int iDp = ui.dp(context, 20);
        linearLayout.setPadding(iDp, ui.dp(context, 28), iDp, ui.dp(context, 32));
        return linearLayout;
    }

    public final LinearLayout card(Context context) {
        Intrinsics.checkNotNullParameter(context, "context");
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(1);
        Ui ui = INSTANCE;
        linearLayout.setBackground(ui.rounded(SURFACE, ui.dp(context, 20)));
        int iDp = ui.dp(context, 16);
        linearLayout.setPadding(iDp, iDp, iDp, iDp);
        linearLayout.setLayoutParams(ui.gap(context, 16));
        return linearLayout;
    }

    public final TextView title(Context context, String text) {
        Intrinsics.checkNotNullParameter(context, "context");
        Intrinsics.checkNotNullParameter(text, "text");
        TextView textView = new TextView(context);
        textView.setText(text);
        textView.setTextColor(FG);
        textView.setTextSize(2, 34.0f);
        textView.setTypeface(Typeface.create("serif", 0));
        return textView;
    }

    public final TextView heading(Context context, String text) {
        Intrinsics.checkNotNullParameter(context, "context");
        Intrinsics.checkNotNullParameter(text, "text");
        TextView textView = new TextView(context);
        textView.setText(text);
        textView.setTextColor(FG);
        textView.setTextSize(2, 18.0f);
        textView.setTypeface(Typeface.create("sans-serif-medium", 0));
        return textView;
    }

    public static /* synthetic */ TextView body$default(Ui ui, Context context, String str, int i, int i2, Object obj) {
        if ((i2 & 4) != 0) {
            i = MUTED;
        }
        return ui.body(context, str, i);
    }

    public final TextView body(Context context, String text, int color) {
        Intrinsics.checkNotNullParameter(context, "context");
        Intrinsics.checkNotNullParameter(text, "text");
        TextView textView = new TextView(context);
        textView.setText(text);
        textView.setTextColor(color);
        textView.setTextSize(2, 14.0f);
        Ui ui = INSTANCE;
        textView.setLineSpacing(ui.dp(context, 2), 1.0f);
        textView.setLayoutParams(ui.gap(context, 6));
        return textView;
    }

    public static /* synthetic */ EditText field$default(Ui ui, Context context, String str, boolean z, int i, Object obj) {
        if ((i & 4) != 0) {
            z = false;
        }
        return ui.field(context, str, z);
    }

    public final EditText field(Context context, String hint, boolean password) {
        Intrinsics.checkNotNullParameter(context, "context");
        Intrinsics.checkNotNullParameter(hint, "hint");
        EditText editText = new EditText(context);
        editText.setHint(hint);
        editText.setHintTextColor(FAINT);
        editText.setTextColor(FG);
        editText.setTextSize(2, 16.0f);
        Ui ui = INSTANCE;
        editText.setBackground(ui.rounded(BG, ui.dp(context, 12)));
        int iDp = ui.dp(context, 12);
        editText.setPadding(iDp, ui.dp(context, 14), iDp, ui.dp(context, 14));
        if (password) {
            editText.setInputType(129);
        }
        editText.setLayoutParams(ui.gap(context, 8));
        return editText;
    }

    public final Button button(Context context, String text, boolean filled) {
        Intrinsics.checkNotNullParameter(context, "context");
        Intrinsics.checkNotNullParameter(text, "text");
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(2, 15.0f);
        Ui ui = INSTANCE;
        button.setBackground(ui.rounded(filled ? ACCENT : SURFACE, ui.dp(context, 12)));
        button.setTextColor(filled ? ACCENT_FG : FG);
        button.setMinimumHeight(ui.dp(context, 48));
        button.setLayoutParams(ui.gap(context, 8));
        return button;
    }

    public final GradientDrawable rounded(int color, int radius) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(color);
        gradientDrawable.setCornerRadius(radius);
        return gradientDrawable;
    }

    private final LinearLayout.LayoutParams gap(Context context, int top) {
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.topMargin = INSTANCE.dp(context, top);
        return layoutParams;
    }

    public final void hide(View view, boolean gone) {
        Intrinsics.checkNotNullParameter(view, "view");
        view.setVisibility(gone ? 8 : 0);
    }
}
