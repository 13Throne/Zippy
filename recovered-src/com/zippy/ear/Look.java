package com.zippy.ear;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Zippy's dark look (same values as the 1.2 Kotlin Ui object) plus edge-to-edge insets. */
public final class Look {
    public static final int BG = -15724273, SURFACE = -15197673, FG = -1250588, MUTED = -6054253,
            FAINT = -9146522, ACCENT = -1646122, ACCENT_FG = -15329516, BAD = -3896964;

    private static final java.util.WeakHashMap<View, Boolean> PADDED = new java.util.WeakHashMap<>();

    private Look() {}

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    public static GradientDrawable rounded(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = column(c);
        l.setBackground(rounded(c, SURFACE, 20));
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 16);
        l.setLayoutParams(lp);
        return l;
    }

    public static TextView title(Context c, String s) {
        TextView t = text(c, s, 34, FG);
        t.setTypeface(Typeface.SERIF);
        return t;
    }

    public static TextView heading(Context c, String s) {
        TextView t = text(c, s, 18, FG);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    public static TextView body(Context c, String s) { return text(c, s, 14, MUTED); }

    public static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    public static View gap(Context c, int dpH) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, dpH)));
        return v;
    }

    public static EditText field(Context c, String hint, boolean secret) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setTextColor(FG);
        e.setHintTextColor(FAINT);
        e.setTextSize(15);
        e.setSingleLine(true);
        e.setBackground(rounded(c, BG, 12));
        int p = dp(c, 12);
        e.setPadding(p, p, p, p);
        e.setInputType(secret ? 129 : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 10);
        e.setLayoutParams(lp);
        return e;
    }

    public static Button button(Context c, String s, boolean primary) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setMinHeight(dp(c, 48));
        b.setStateListAnimator(null);
        style(c, b, primary);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, 10);
        b.setLayoutParams(lp);
        return b;
    }

    public static void style(Context c, Button b, boolean selected) {
        b.setBackground(rounded(c, selected ? ACCENT : BG, 12));
        b.setTextColor(selected ? ACCENT_FG : FG);
    }

    /** Weighted button for a segmented row. */
    public static Button segment(Context c, String s) {
        Button b = button(c, s, false);
        b.setTextSize(13);
        b.setPadding(dp(c, 4), 0, dp(c, 4), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.topMargin = dp(c, 8);
        lp.bottomMargin = dp(c, 2);
        lp.leftMargin = dp(c, 3);
        lp.rightMargin = dp(c, 3);
        b.setLayoutParams(lp);
        return b;
    }

    /**
     * targetSdk 35 is always edge-to-edge: pad the root by the status/nav bars, cutout and keyboard
     * so nothing hides under them. Keeps the view's own padding as the base.
     */
    public static void edgeToEdge(final View root) {
        if (root == null) return;
        final int l = root.getPaddingLeft(), t = root.getPaddingTop(), r = root.getPaddingRight(), b = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            int il, it, ir, ib;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                android.graphics.Insets ime = ins.getInsets(WindowInsets.Type.ime());
                il = bars.left; it = bars.top; ir = bars.right; ib = Math.max(bars.bottom, ime.bottom);
            } else {
                il = ins.getSystemWindowInsetLeft(); it = ins.getSystemWindowInsetTop();
                ir = ins.getSystemWindowInsetRight(); ib = ins.getSystemWindowInsetBottom();
            }
            v.setPadding(l + il, t + it, r + ir, b + ib);
            return ins;
        });
        root.requestApplyInsets();
    }

    /** For activities we don't own the layout of (TrainActivity): pad its content root once. */
    public static void edgeToEdge(Activity a) {
        try {
            ViewGroup content = a.findViewById(android.R.id.content);
            if (content == null || content.getChildCount() == 0) return;
            View root = content.getChildAt(0);
            if (PADDED.containsKey(root)) return;
            PADDED.put(root, Boolean.TRUE);
            a.getWindow().getDecorView().setBackgroundColor(BG);
            edgeToEdge(root);
        } catch (Throwable ignored) { }
    }
}
