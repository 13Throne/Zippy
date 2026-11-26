package com.zippy.ear;

import android.util.Base64;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import kotlin.Metadata;
import kotlin.Unit;
import kotlin.collections.ArraysKt;
import kotlin.io.CloseableKt;
import kotlin.io.TextStreamsKt;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.Charsets;
import kotlin.text.StringsKt;
import org.json.JSONObject;

/* JADX INFO: compiled from: Webhook.kt */
/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000:\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0012\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0002\bÆ\u0002\u0018\u00002\u00020\u0001B\t\b\u0002¢\u0006\u0004\b\u0002\u0010\u0003J\u0016\u0010\u0004\u001a\u00020\u00052\u0006\u0010\u0006\u001a\u00020\u00072\u0006\u0010\b\u001a\u00020\tJ \u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\r2\u0006\u0010\u000e\u001a\u00020\t2\u0006\u0010\u000f\u001a\u00020\u0010H\u0002J\u0010\u0010\u0011\u001a\u00020\u00122\u0006\u0010\u0013\u001a\u00020\tH\u0002¨\u0006\u0014"}, d2 = {"Lcom/zippy/ear/Webhook;", "", "<init>", "()V", "post", "Lcom/zippy/ear/SendResult;", "settings", "Lcom/zippy/ear/Settings;", "text", "", "sign", "", "conn", "Ljava/net/HttpURLConnection;", "key", "body", "", "blocked", "", "hostname", "app_release"}, k = 1, mv = {2, 0, 0}, xi = 48)
public final class Webhook {
    public static final Webhook INSTANCE = new Webhook();

    private Webhook() {
    }

    public final SendResult post(Settings settings, String text) {
        Intrinsics.checkNotNullParameter(settings, "settings");
        Intrinsics.checkNotNullParameter(text, "text");
        String string = StringsKt.trim((CharSequence) text).toString();
        if (string.length() == 0) {
            return new SendResult(false, "The task was empty.");
        }
        String string2 = StringsKt.trim((CharSequence) settings.getWebhookUrl()).toString();
        if (string2.length() == 0) {
            return new SendResult(false, "Add Zippy's address first.");
        }
        try {
            URI uri = new URI(string2);
            if (!StringsKt.equals(uri.getScheme(), "https", true)) {
                return new SendResult(false, "The address must start with https.");
            }
            String userInfo = uri.getUserInfo();
            if (userInfo != null && !StringsKt.isBlank(userInfo)) {
                return new SendResult(false, "Put the secret in the secret field, not in the address.");
            }
            String host = uri.getHost();
            if (host == null) {
                return new SendResult(false, "That address is not valid.");
            }
            if (blocked(host)) {
                return new SendResult(false, "That address is not allowed.");
            }
            String string3 = new JSONObject().put("text", string).put("source", "android").toString();
            Intrinsics.checkNotNullExpressionValue(string3, "toString(...)");
            byte[] bytes = string3.getBytes(Charsets.UTF_8);
            Intrinsics.checkNotNullExpressionValue(bytes, "getBytes(...)");
            try {
                URLConnection uRLConnectionOpenConnection = new URL(uri.toString()).openConnection();
                Intrinsics.checkNotNull(uRLConnectionOpenConnection, "null cannot be cast to non-null type java.net.HttpURLConnection");
                HttpURLConnection httpURLConnection = (HttpURLConnection) uRLConnectionOpenConnection;
                httpURLConnection.setRequestMethod("POST");
                httpURLConnection.setConnectTimeout(12000);
                httpURLConnection.setReadTimeout(12000);
                httpURLConnection.setInstanceFollowRedirects(false);
                httpURLConnection.setDoOutput(true);
                httpURLConnection.setRequestProperty("Content-Type", "application/json");
                INSTANCE.sign(httpURLConnection, StringsKt.trim((CharSequence) settings.getWebhookKey()).toString(), bytes);
                OutputStream outputStream = httpURLConnection.getOutputStream();
                try {
                    outputStream.write(bytes);
                    Unit unit = Unit.INSTANCE;
                    String strTake = null;
                    CloseableKt.closeFinally(outputStream, null);
                    int responseCode = httpURLConnection.getResponseCode();
                    if (300 <= responseCode && responseCode < 400) {
                        httpURLConnection.disconnect();
                        return new SendResult(false, "That address redirected. Paste the final https address.");
                    }
                    InputStream errorStream = (200 > responseCode || responseCode >= 300) ? httpURLConnection.getErrorStream() : httpURLConnection.getInputStream();
                    if (errorStream != null) {
                        Reader inputStreamReader = new InputStreamReader(errorStream, Charsets.UTF_8);
                        BufferedReader bufferedReader = inputStreamReader instanceof BufferedReader ? (BufferedReader) inputStreamReader : new BufferedReader(inputStreamReader, 8192);
                        try {
                            String text2 = TextStreamsKt.readText(bufferedReader);
                            CloseableKt.closeFinally(bufferedReader, null);
                            if (text2 != null) {
                                strTake = StringsKt.take(text2, 240);
                            }
                        } catch (Throwable th) {
                            try {
                                throw th;
                            } catch (Throwable th2) {
                                CloseableKt.closeFinally(bufferedReader, th);
                                throw th2;
                            }
                        }
                    }
                    if (strTake == null) {
                        strTake = "";
                    }
                    httpURLConnection.disconnect();
                    if (200 > responseCode || responseCode >= 300) {
                        String str = strTake;
                        if (StringsKt.isBlank(str)) {
                            str = "Zippy returned " + responseCode + ".";
                        }
                        return new SendResult(false, str);
                    }
                    return new SendResult(true, "Accepted.");
                } catch (Throwable th3) {
                    try {
                        throw th3;
                    } catch (Throwable th4) {
                        CloseableKt.closeFinally(outputStream, th3);
                        throw th4;
                    }
                }
            } catch (Exception e) {
                String message = e.getMessage();
                if (message == null) {
                    message = "Zippy did not answer.";
                }
                return new SendResult(false, message);
            }
        } catch (Exception unused) {
            return new SendResult(false, "That address is not valid.");
        }
    }

    private final void sign(HttpURLConnection conn, String key, byte[] body) throws NoSuchAlgorithmException, InvalidKeyException {
        if (key.length() == 0) {
            return;
        }
        if (StringsKt.startsWith$default(key, "whsec_", false, 2, (Object) null)) {
            String str = "msg_" + UUID.randomUUID();
            String strValueOf = String.valueOf(System.currentTimeMillis() / ((long) 1000));
            byte[] bArrDecode = Base64.decode(StringsKt.removePrefix(key, (CharSequence) "whsec_"), 0);
            byte[] bytes = (str + "." + strValueOf + ".").getBytes(Charsets.UTF_8);
            Intrinsics.checkNotNullExpressionValue(bytes, "getBytes(...)");
            byte[] bArrPlus = ArraysKt.plus(bytes, body);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(bArrDecode, "HmacSHA256"));
            String strEncodeToString = Base64.encodeToString(mac.doFinal(bArrPlus), 2);
            conn.setRequestProperty("webhook-id", str);
            conn.setRequestProperty("webhook-timestamp", strValueOf);
            conn.setRequestProperty("webhook-signature", "v1," + strEncodeToString);
            return;
        }
        if (!StringsKt.startsWith(key, "bearer ", true)) {
            key = "Bearer " + key;
        }
        conn.setRequestProperty("Authorization", key);
    }

    private final boolean blocked(String hostname) {
        String lowerCase = hostname.toLowerCase(Locale.ROOT);
        Intrinsics.checkNotNullExpressionValue(lowerCase, "toLowerCase(...)");
        String strRemoveSuffix = StringsKt.removeSuffix(StringsKt.removePrefix(lowerCase, (CharSequence) "["), (CharSequence) "]");
        if (Intrinsics.areEqual(strRemoveSuffix, "localhost") || StringsKt.endsWith$default(strRemoveSuffix, ".localhost", false, 2, (Object) null) || StringsKt.endsWith$default(strRemoveSuffix, ".local", false, 2, (Object) null) || Intrinsics.areEqual(strRemoveSuffix, "metadata.google.internal") || Intrinsics.areEqual(strRemoveSuffix, "metadata.internal")) {
            return true;
        }
        try {
            InetAddress byName = InetAddress.getByName(strRemoveSuffix);
            return byName.isAnyLocalAddress() || byName.isLoopbackAddress() || byName.isLinkLocalAddress() || byName.isSiteLocalAddress();
        } catch (Exception unused) {
            return true;
        }
    }
}
