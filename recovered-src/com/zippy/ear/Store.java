package com.zippy.ear;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import kotlin.Metadata;
import kotlin.collections.CollectionsKt;
import kotlin.jvm.internal.Intrinsics;
import kotlin.ranges.RangesKt;
import kotlin.text.StringsKt;
import kotlinx.coroutines.DebugKt;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/* JADX INFO: compiled from: Store.kt */
/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000f\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\u0005\n\u0002\u0010\u000b\n\u0002\b\u0004\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\t\n\u0000\n\u0002\u0010\u0014\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\bÆ\u0002\u0018\u00002\u00020\u0001B\t\b\u0002¢\u0006\u0004\b\u0002\u0010\u0003J\u000e\u0010\u0006\u001a\u00020\u00072\u0006\u0010\b\u001a\u00020\tJ>\u0010\n\u001a\u00020\u000b2\u0006\u0010\b\u001a\u00020\t2\u0006\u0010\f\u001a\u00020\u00052\u0006\u0010\r\u001a\u00020\u00052\u0006\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u0010\u001a\u00020\u000f2\u0006\u0010\u0011\u001a\u00020\u000f2\u0006\u0010\u0012\u001a\u00020\u000fJ\u0016\u0010\u0013\u001a\u00020\u000b2\u0006\u0010\b\u001a\u00020\t2\u0006\u0010\u0014\u001a\u00020\u0015J\u001e\u0010\u0016\u001a\u00020\u000b2\u0006\u0010\b\u001a\u00020\t2\u0006\u0010\u0017\u001a\u00020\u00052\u0006\u0010\u0018\u001a\u00020\u0005J\u0014\u0010\u0019\u001a\b\u0012\u0004\u0012\u00020\u001b0\u001a2\u0006\u0010\b\u001a\u00020\tJ&\u0010\u001c\u001a\u00020\u000b2\u0006\u0010\b\u001a\u00020\t2\u0006\u0010\u001d\u001a\u00020\u00052\u0006\u0010\u001e\u001a\u00020\u00152\u0006\u0010\u001f\u001a\u00020\u0005J\u000e\u0010 \u001a\u00020!2\u0006\u0010\b\u001a\u00020\tJ\u0016\u0010\"\u001a\u00020\u000b2\u0006\u0010\b\u001a\u00020\t2\u0006\u0010 \u001a\u00020!J\u000e\u0010#\u001a\u00020\u000b2\u0006\u0010\b\u001a\u00020\tJ\u000e\u0010$\u001a\u00020\u00052\u0006\u0010%\u001a\u00020&J\u0018\u0010'\u001a\b\u0012\u0004\u0012\u00020(0\u001a2\b\u0010)\u001a\u0004\u0018\u00010*H\u0002J\u0016\u0010+\u001a\u00020*2\f\u0010,\u001a\b\u0012\u0004\u0012\u00020(0\u001aH\u0002J\u001d\u0010-\u001a\n /*\u0004\u0018\u00010.0.2\u0006\u0010\b\u001a\u00020\tH\u0002¢\u0006\u0002\u00100R\u000e\u0010\u0004\u001a\u00020\u0005X\u0082T¢\u0006\u0002\n\u0000¨\u00061"}, d2 = {"Lcom/zippy/ear/Store;", "", "<init>", "()V", "NAME", "", "read", "Lcom/zippy/ear/Settings;", "context", "Landroid/content/Context;", "saveSettings", "", "url", "key", "sensitivity", "", "waitMs", "readyCue", "sentCue", "setListening", DebugKt.DEBUG_PROPERTY_VALUE_ON, "", "setStatus", "note", "heard", "orders", "", "Lcom/zippy/ear/Order;", "addOrder", "text", "ok", "detail", "prints", "Lcom/zippy/ear/Prints;", "savePrints", "clearPrints", "whenLabel", "at", "", "readVectors", "", "array", "Lorg/json/JSONArray;", "writeVectors", "rows", "prefs", "Landroid/content/SharedPreferences;", "kotlin.jvm.PlatformType", "(Landroid/content/Context;)Landroid/content/SharedPreferences;", "app_release"}, k = 1, mv = {2, 0, 0}, xi = 48)
public final class Store {
    public static final Store INSTANCE = new Store();
    private static final String NAME = "zippy";

    private Store() {
    }

    public final Settings read(Context context) {
        Intrinsics.checkNotNullParameter(context, "context");
        SharedPreferences sharedPreferencesPrefs = prefs(context);
        String string = sharedPreferencesPrefs.getString("webhookUrl", "");
        String str = string == null ? "" : string;
        String string2 = sharedPreferencesPrefs.getString("webhookKey", "");
        String str2 = string2 == null ? "" : string2;
        int i = sharedPreferencesPrefs.getInt("sensitivity", 55);
        int i2 = sharedPreferencesPrefs.getInt("waitMs", 4000);
        int i3 = sharedPreferencesPrefs.getInt("readyCue", 1);
        int i4 = sharedPreferencesPrefs.getInt("sentCue", 1);
        boolean z = sharedPreferencesPrefs.getBoolean("listening", false);
        String string3 = sharedPreferencesPrefs.getString("note", "Off.");
        String str3 = string3 != null ? string3 : "Off.";
        String string4 = sharedPreferencesPrefs.getString("heard", "");
        if (string4 == null) {
            string4 = "";
        }
        return new Settings(str, str2, i, i2, i3, i4, z, str3, string4);
    }

    public final void saveSettings(Context context, String url, String key, int sensitivity, int waitMs, int readyCue, int sentCue) {
        Intrinsics.checkNotNullParameter(context, "context");
        Intrinsics.checkNotNullParameter(url, "url");
        Intrinsics.checkNotNullParameter(key, "key");
        prefs(context).edit().putString("webhookUrl", StringsKt.trim((CharSequence) url).toString()).putString("webhookKey", StringsKt.trim((CharSequence) key).toString()).putInt("sensitivity", RangesKt.coerceIn(sensitivity, 0, 100)).putInt("waitMs", RangesKt.coerceIn(waitMs, 1000, 8000)).putInt("readyCue", readyCue).putInt("sentCue", sentCue).apply();
    }

    public final void setListening(Context context, boolean on) {
        Intrinsics.checkNotNullParameter(context, "context");
        prefs(context).edit().putBoolean("listening", on).apply();
    }

    public final void setStatus(Context context, String note, String heard) {
        Intrinsics.checkNotNullParameter(context, "context");
        Intrinsics.checkNotNullParameter(note, "note");
        Intrinsics.checkNotNullParameter(heard, "heard");
        prefs(context).edit().putString("note", note).putString("heard", heard).apply();
    }

    public final List<Order> orders(Context context) {
        Intrinsics.checkNotNullParameter(context, "context");
        String string = prefs(context).getString("orders", "[]");
        try {
            JSONArray jSONArray = new JSONArray(string != null ? string : "[]");
            List listCreateListBuilder = CollectionsKt.createListBuilder();
            int length = jSONArray.length();
            for (int i = 0; i < length; i++) {
                JSONObject jSONObject = jSONArray.getJSONObject(i);
                String strOptString = jSONObject.optString("text");
                Intrinsics.checkNotNullExpressionValue(strOptString, "optString(...)");
                long jOptLong = jSONObject.optLong("at");
                boolean zOptBoolean = jSONObject.optBoolean("ok");
                String strOptString2 = jSONObject.optString("detail");
                Intrinsics.checkNotNullExpressionValue(strOptString2, "optString(...)");
                listCreateListBuilder.add(new Order(strOptString, jOptLong, zOptBoolean, strOptString2));
            }
            return CollectionsKt.build(listCreateListBuilder);
        } catch (Exception unused) {
            return CollectionsKt.emptyList();
        }
    }

    public final void addOrder(Context context, String text, boolean ok, String detail) {
        Intrinsics.checkNotNullParameter(context, "context");
        Intrinsics.checkNotNullParameter(text, "text");
        Intrinsics.checkNotNullParameter(detail, "detail");
        JSONArray jSONArray = new JSONArray();
        jSONArray.put(new JSONObject().put("text", text).put("at", System.currentTimeMillis()).put("ok", ok).put("detail", detail));
        for (Order order : CollectionsKt.take(orders(context), 11)) {
            jSONArray.put(new JSONObject().put("text", order.getText()).put("at", order.getAt()).put("ok", order.getOk()).put("detail", order.getDetail()));
        }
        prefs(context).edit().putString("orders", jSONArray.toString()).apply();
    }

    public final Prints prints(Context context) {
        Intrinsics.checkNotNullParameter(context, "context");
        String string = prefs(context).getString("prints", "");
        String str = string != null ? string : "";
        if (StringsKt.isBlank(str)) {
            return new Prints(CollectionsKt.emptyList(), CollectionsKt.emptyList());
        }
        try {
            JSONObject jSONObject = new JSONObject(str);
            return new Prints(readVectors(jSONObject.optJSONArray("wakes")), readVectors(jSONObject.optJSONArray("rejects")));
        } catch (Exception unused) {
            return new Prints(CollectionsKt.emptyList(), CollectionsKt.emptyList());
        }
    }

    public final void savePrints(Context context, Prints prints) throws JSONException {
        Intrinsics.checkNotNullParameter(context, "context");
        Intrinsics.checkNotNullParameter(prints, "prints");
        JSONObject jSONObject = new JSONObject();
        jSONObject.put("wakes", writeVectors(prints.getWakes()));
        jSONObject.put("rejects", writeVectors(prints.getRejects()));
        prefs(context).edit().putString("prints", jSONObject.toString()).apply();
    }

    public final void clearPrints(Context context) {
        Intrinsics.checkNotNullParameter(context, "context");
        prefs(context).edit().remove("prints").apply();
    }

    public final String whenLabel(long at) {
        String str = DateFormat.getDateTimeInstance(2, 3).format(new Date(at));
        Intrinsics.checkNotNullExpressionValue(str, "format(...)");
        return str;
    }

    private final List<float[]> readVectors(JSONArray array) throws JSONException {
        if (array == null) {
            return CollectionsKt.emptyList();
        }
        List listCreateListBuilder = CollectionsKt.createListBuilder();
        int length = array.length();
        for (int i = 0; i < length; i++) {
            JSONArray jSONArray = array.getJSONArray(i);
            int length2 = jSONArray.length();
            float[] fArr = new float[length2];
            for (int i2 = 0; i2 < length2; i2++) {
                fArr[i2] = (float) jSONArray.getDouble(i2);
            }
            listCreateListBuilder.add(fArr);
        }
        return CollectionsKt.build(listCreateListBuilder);
    }

    private final JSONArray writeVectors(List<float[]> rows) throws JSONException {
        JSONArray jSONArray = new JSONArray();
        for (float[] fArr : rows) {
            JSONArray jSONArray2 = new JSONArray();
            for (float f : fArr) {
                jSONArray2.put(f);
            }
            jSONArray.put(jSONArray2);
        }
        return jSONArray;
    }

    private final SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences("zippy", 0);
    }
}
