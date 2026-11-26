package com.zippy.ear;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import kotlin.Metadata;
import kotlin.io.ByteStreamsKt;
import kotlin.io.CloseableKt;
import kotlin.io.TextStreamsKt;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.Charsets;
import kotlin.text.StringsKt;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/* JADX INFO: compiled from: Updates.kt */
/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000@\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\u0002\bÆ\u0002\u0018\u00002\u00020\u0001:\u0001\u0017B\t\b\u0002¢\u0006\u0004\b\u0002\u0010\u0003J\u0006\u0010\u0007\u001a\u00020\bJ\u0016\u0010\t\u001a\u00020\n2\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\r\u001a\u00020\u0005J\u0016\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\u000b\u001a\u00020\f2\u0006\u0010\u0010\u001a\u00020\nJ\u0010\u0010\u0011\u001a\u00020\u00122\u0006\u0010\r\u001a\u00020\u0005H\u0002J\u0018\u0010\u0013\u001a\u00020\u00052\u0006\u0010\u0014\u001a\u00020\u00122\u0006\u0010\u0015\u001a\u00020\u0016H\u0002R\u000e\u0010\u0004\u001a\u00020\u0005X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0005X\u0086T¢\u0006\u0002\n\u0000¨\u0006\u0018"}, d2 = {"Lcom/zippy/ear/Updates;", "", "<init>", "()V", "OWNER", "", "REPO", "latest", "Lcom/zippy/ear/Updates$Offer;", "download", "Ljava/io/File;", "context", "Landroid/content/Context;", "url", "install", "", "file", "connect", "Ljava/net/HttpURLConnection;", "read", "conn", NotificationCompat.CATEGORY_STATUS, "", "Offer", "app_release"}, k = 1, mv = {2, 0, 0}, xi = 48)
public final class Updates {
    public static final Updates INSTANCE = new Updates();
    public static final String OWNER = "13Throne";
    public static final String REPO = "zippy";

    private Updates() {
    }

    /* JADX INFO: compiled from: Updates.kt */
    @Metadata(d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0010\u000e\n\u0002\b\r\n\u0002\u0010\u000b\n\u0002\b\u0004\b\u0086\b\u0018\u00002\u00020\u0001B\u001f\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0005¢\u0006\u0004\b\u0007\u0010\bJ\t\u0010\u000e\u001a\u00020\u0003HÆ\u0003J\t\u0010\u000f\u001a\u00020\u0005HÆ\u0003J\t\u0010\u0010\u001a\u00020\u0005HÆ\u0003J'\u0010\u0011\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u0005HÆ\u0001J\u0013\u0010\u0012\u001a\u00020\u00132\b\u0010\u0014\u001a\u0004\u0018\u00010\u0001HÖ\u0003J\t\u0010\u0015\u001a\u00020\u0003HÖ\u0001J\t\u0010\u0016\u001a\u00020\u0005HÖ\u0001R\u0011\u0010\u0002\u001a\u00020\u0003¢\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u0011\u0010\u0004\u001a\u00020\u0005¢\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u0011\u0010\u0006\u001a\u00020\u0005¢\u0006\b\n\u0000\u001a\u0004\b\r\u0010\f¨\u0006\u0017"}, d2 = {"Lcom/zippy/ear/Updates$Offer;", "", "versionCode", "", "title", "", "apkUrl", "<init>", "(ILjava/lang/String;Ljava/lang/String;)V", "getVersionCode", "()I", "getTitle", "()Ljava/lang/String;", "getApkUrl", "component1", "component2", "component3", "copy", "equals", "", "other", "hashCode", "toString", "app_release"}, k = 1, mv = {2, 0, 0}, xi = 48)
    public static final /* data */ class Offer {
        private final String apkUrl;
        private final String title;
        private final int versionCode;

        public static /* synthetic */ Offer copy$default(Offer offer, int i, String str, String str2, int i2, Object obj) {
            if ((i2 & 1) != 0) {
                i = offer.versionCode;
            }
            if ((i2 & 2) != 0) {
                str = offer.title;
            }
            if ((i2 & 4) != 0) {
                str2 = offer.apkUrl;
            }
            return offer.copy(i, str, str2);
        }

        /* JADX INFO: renamed from: component1, reason: from getter */
        public final int getVersionCode() {
            return this.versionCode;
        }

        /* JADX INFO: renamed from: component2, reason: from getter */
        public final String getTitle() {
            return this.title;
        }

        /* JADX INFO: renamed from: component3, reason: from getter */
        public final String getApkUrl() {
            return this.apkUrl;
        }

        public final Offer copy(int versionCode, String title, String apkUrl) {
            Intrinsics.checkNotNullParameter(title, "title");
            Intrinsics.checkNotNullParameter(apkUrl, "apkUrl");
            return new Offer(versionCode, title, apkUrl);
        }

        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Offer)) {
                return false;
            }
            Offer offer = (Offer) other;
            return this.versionCode == offer.versionCode && Intrinsics.areEqual(this.title, offer.title) && Intrinsics.areEqual(this.apkUrl, offer.apkUrl);
        }

        public int hashCode() {
            return (((Integer.hashCode(this.versionCode) * 31) + this.title.hashCode()) * 31) + this.apkUrl.hashCode();
        }

        public String toString() {
            return "Offer(versionCode=" + this.versionCode + ", title=" + this.title + ", apkUrl=" + this.apkUrl + ")";
        }

        public Offer(int i, String title, String apkUrl) {
            Intrinsics.checkNotNullParameter(title, "title");
            Intrinsics.checkNotNullParameter(apkUrl, "apkUrl");
            this.versionCode = i;
            this.title = title;
            this.apkUrl = apkUrl;
        }

        public final String getApkUrl() {
            return this.apkUrl;
        }

        public final String getTitle() {
            return this.title;
        }

        public final int getVersionCode() {
            return this.versionCode;
        }
    }

    public final Offer latest() throws JSONException, IOException {
        HttpURLConnection httpURLConnectionConnect = connect("https://api.github.com/repos/13Throne/zippy/releases/latest");
        httpURLConnectionConnect.setRequestProperty("Accept", "application/vnd.github+json");
        int responseCode = httpURLConnectionConnect.getResponseCode();
        String str = read(httpURLConnectionConnect, responseCode);
        httpURLConnectionConnect.disconnect();
        if (responseCode == 404) {
            throw new IllegalStateException("No release on GitHub yet.".toString());
        }
        if (200 > responseCode || responseCode >= 300) {
            String str2 = str;
            if (StringsKt.isBlank(str2)) {
                str2 = "GitHub returned " + responseCode + ".";
            }
            throw new IllegalStateException(StringsKt.take(str2, 180).toString());
        }
        JSONObject jSONObject = new JSONObject(str);
        String strOptString = jSONObject.optString("tag_name");
        Intrinsics.checkNotNullExpressionValue(strOptString, "optString(...)");
        Integer intOrNull = StringsKt.toIntOrNull(StringsKt.removePrefix(strOptString, (CharSequence) "v"));
        if (intOrNull == null) {
            throw new IllegalStateException("The latest release tag is not a version number.".toString());
        }
        int iIntValue = intOrNull.intValue();
        JSONArray jSONArrayOptJSONArray = jSONObject.optJSONArray("assets");
        if (jSONArrayOptJSONArray == null) {
            throw new IllegalStateException("That release has no file.".toString());
        }
        int length = jSONArrayOptJSONArray.length();
        String strOptString2 = null;
        for (int i = 0; i < length; i++) {
            JSONObject jSONObject2 = jSONArrayOptJSONArray.getJSONObject(i);
            String strOptString3 = jSONObject2.optString("name");
            Intrinsics.checkNotNull(strOptString3);
            if (StringsKt.endsWith(strOptString3, ".apk", true)) {
                strOptString2 = jSONObject2.optString("browser_download_url");
                if (StringsKt.equals(strOptString3, "Zippy.apk", true)) {
                    break;
                }
            }
        }
        String str3 = strOptString2;
        if (str3 == null || StringsKt.isBlank(str3)) {
            throw new IllegalStateException("That release has no APK.".toString());
        }
        String strOptString4 = jSONObject.optString("name");
        if (StringsKt.isBlank(strOptString4)) {
            strOptString4 = jSONObject.optString("tag_name");
        }
        String str4 = strOptString4;
        Intrinsics.checkNotNull(str4);
        return new Offer(iIntValue, str4, strOptString2);
    }

    public final File download(Context context, String url) throws IOException {
        Intrinsics.checkNotNullParameter(context, "context");
        Intrinsics.checkNotNullParameter(url, "url");
        File file = new File(context.getCacheDir(), "updates");
        file.mkdirs();
        File file2 = new File(file, "Zippy.apk");
        HttpURLConnection httpURLConnectionConnect = connect(url);
        httpURLConnectionConnect.setRequestProperty("Accept", "application/octet-stream");
        int responseCode = httpURLConnectionConnect.getResponseCode();
        if (200 > responseCode || responseCode >= 300) {
            String str = read(httpURLConnectionConnect, responseCode);
            httpURLConnectionConnect.disconnect();
            String str2 = str;
            if (StringsKt.isBlank(str2)) {
                str2 = "Download returned " + responseCode + ".";
            }
            throw new IllegalStateException(StringsKt.take(str2, 180).toString());
        }
        InputStream inputStream = httpURLConnectionConnect.getInputStream();
        try {
            InputStream inputStream2 = inputStream;
            FileOutputStream fileOutputStream = new FileOutputStream(file2);
            try {
                Intrinsics.checkNotNull(inputStream2);
                ByteStreamsKt.copyTo$default(inputStream2, fileOutputStream, 0, 2, null);
                CloseableKt.closeFinally(fileOutputStream, null);
                CloseableKt.closeFinally(inputStream, null);
                httpURLConnectionConnect.disconnect();
                return file2;
            } catch (Throwable th) {
                try {
                    throw th;
                } catch (Throwable th2) {
                    CloseableKt.closeFinally(fileOutputStream, th);
                    throw th2;
                }
            }
        } catch (Throwable th3) {
            try {
                throw th3;
            } catch (Throwable th4) {
                CloseableKt.closeFinally(inputStream, th3);
                throw th4;
            }
        }
    }

    public final void install(Context context, File file) {
        Intrinsics.checkNotNullParameter(context, "context");
        Intrinsics.checkNotNullParameter(file, "file");
        Uri uriForFile = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
        Intrinsics.checkNotNullExpressionValue(uriForFile, "getUriForFile(...)");
        Intent intent = new Intent("android.intent.action.VIEW");
        intent.setDataAndType(uriForFile, "application/vnd.android.package-archive");
        intent.addFlags(268435457);
        context.startActivity(intent);
    }

    private final HttpURLConnection connect(String url) throws IOException {
        URLConnection uRLConnectionOpenConnection = new URL(url).openConnection();
        Intrinsics.checkNotNull(uRLConnectionOpenConnection, "null cannot be cast to non-null type java.net.HttpURLConnection");
        HttpURLConnection httpURLConnection = (HttpURLConnection) uRLConnectionOpenConnection;
        httpURLConnection.setInstanceFollowRedirects(true);
        httpURLConnection.setConnectTimeout(AccessibilityNodeInfoCompat.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_MAX_LENGTH);
        httpURLConnection.setReadTimeout(120000);
        httpURLConnection.setRequestProperty("User-Agent", "Zippy");
        return httpURLConnection;
    }

    private final String read(HttpURLConnection conn, int status) throws IOException {
        InputStream errorStream = (200 > status || status >= 300) ? conn.getErrorStream() : conn.getInputStream();
        String str = null;
        if (errorStream != null) {
            Reader inputStreamReader = new InputStreamReader(errorStream, Charsets.UTF_8);
            BufferedReader bufferedReader = inputStreamReader instanceof BufferedReader ? (BufferedReader) inputStreamReader : new BufferedReader(inputStreamReader, 8192);
            try {
                String text = TextStreamsKt.readText(bufferedReader);
                CloseableKt.closeFinally(bufferedReader, null);
                str = text;
            } catch (Throwable th) {
                try {
                    throw th;
                } catch (Throwable th2) {
                    CloseableKt.closeFinally(bufferedReader, th);
                    throw th2;
                }
            }
        }
        return str == null ? "" : str;
    }
}
