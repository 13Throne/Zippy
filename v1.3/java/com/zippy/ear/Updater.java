package com.zippy.ear;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import androidx.core.content.FileProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * v1.3 in-app updater. Replaces the v1.2 flow (which only understood integer tags).
 *
 * 1. GET https://api.github.com/repos/13Throne/Zippy/releases/latest
 * 2. Compare tag_name ("v1.4" / "1.4" / "v1.4.1") to the installed versionName.
 * 3. Download the Zippy.apk asset to cache/updates/ with progress.
 * 4. Check the downloaded file is com.zippy.ear with a higher versionCode.
 * 5. If "Install unknown apps" isn't allowed yet, open that setting; when the user
 *    comes back (onResume) the installer opens automatically.
 * 6. Open the system installer via FileProvider (content://com.zippy.ear.fileprovider/updates/...).
 */
public final class Updater {
    public static final String LATEST_URL = "https://api.github.com/repos/13Throne/Zippy/releases/latest";
    private static final String AUTHORITY_SUFFIX = ".fileprovider";
    private static final AtomicBoolean BUSY = new AtomicBoolean(false);
    private static Handler main;

    /** Downloaded + verified APK waiting for the "Install unknown apps" permission. */
    private static volatile File pending;
    private static volatile String pendingTag;

    private Updater() {}

    // ---------------------------------------------------------------- entry points

    /** "Check for updates" button. */
    public static void check(final Activity activity, final Button button, final TextView status) {
        if (!BUSY.compareAndSet(false, true)) {
            return;
        }
        setButton(button, false);
        setStatus(status, "Checking GitHub for updates…");
        final Context app = activity.getApplicationContext();
        new Thread(() -> {
            try {
                run(activity, app, button, status);
            } catch (Throwable t) {
                ui(activity, () -> setStatus(status, friendly(t)));
            } finally {
                BUSY.set(false);
                ui(activity, () -> setButton(button, true));
            }
        }, "zippy-updater").start();
    }

    /** Called from MainActivity.onResume(): finish an install that was waiting on permission. */
    public static void onResume(Activity activity, Button button, TextView status) {
        if (status != null && !BUSY.get() && pending == null) {
            CharSequence cur = status.getText();
            if (cur == null || cur.length() == 0 || cur.toString().startsWith("Installed ")) {
                status.setText("Installed v" + installedName(activity) + ".");
            }
        }
        File f = pending;
        if (f == null || BUSY.get()) {
            return;
        }
        if (!canInstall(activity)) {
            setStatus(status, "Zippy still can't install apps. Allow \"Install unknown apps\" for Zippy, then tap Check for updates.");
            return;
        }
        if (!f.isFile()) {
            pending = null;
            return;
        }
        pending = null;
        launchInstaller(activity, f, pendingTag, status);
    }

    // ---------------------------------------------------------------- core flow

    private static void run(Activity activity, Context app, Button button, TextView status) throws Exception {
        String installedName = installedName(app);
        long installedCode = installedCode(app);

        // 1. Ask GitHub for the latest release
        HttpURLConnection c = open(LATEST_URL, installedName);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        int code;
        String body;
        try {
            code = c.getResponseCode();
            body = readAll(code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream());
        } finally {
            c.disconnect();
        }
        if (code == 404) {
            throw new UpdateError("No release on GitHub yet.");
        }
        if (code == 403 || code == 429) {
            throw new UpdateError("GitHub is rate-limiting update checks. Try again in a few minutes.");
        }
        if (code < 200 || code >= 300) {
            throw new UpdateError("GitHub returned " + code + ". Try again later.");
        }

        JSONObject rel = new JSONObject(body);
        final String tag = rel.optString("tag_name", "").trim();
        int[] latest = parseVersion(tag);
        if (latest == null) {
            throw new UpdateError("The latest release tag (\"" + tag + "\") isn't a version number.");
        }
        final String latestLabel = "v" + join(latest);
        int[] installed = parseVersion(installedName);
        if (installed != null && compare(latest, installed) <= 0) {
            ui(activity, () -> setStatus(status, "You're up to date (v" + installedName + ")."));
            return;
        }

        // 2. Find the Zippy.apk asset
        JSONArray assets = rel.optJSONArray("assets");
        String url = null;
        long size = -1;
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.optJSONObject(i);
                if (a == null) continue;
                String name = a.optString("name", "");
                if (!name.toLowerCase(Locale.ROOT).endsWith(".apk")) continue;
                if (url == null || name.equalsIgnoreCase("Zippy.apk")) {
                    url = a.optString("browser_download_url", null);
                    size = a.optLong("size", -1);
                }
                if (name.equalsIgnoreCase("Zippy.apk")) break;
            }
        }
        if (url == null || url.trim().isEmpty() || !url.startsWith("https://")) {
            throw new UpdateError(latestLabel + " is out, but its release has no Zippy.apk attached.");
        }

        // 3. Download (reuse an already-verified file from an earlier try)
        File dir = new File(app.getCacheDir(), "updates");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new UpdateError("Couldn't make room for the download.");
        }
        final File apk = new File(dir, "Zippy-" + join(latest) + ".apk");
        cleanOld(dir, apk);
        boolean reuse = apk.isFile() && size > 0 && apk.length() == size && verify(app, apk, installedCode) == null;
        if (!reuse) {
            ui(activity, () -> setStatus(status, "Update " + latestLabel + " found. Downloading…"));
            download(activity, url, size, apk, latestLabel, installedName, status);
            String problem = verify(app, apk, installedCode);
            if (problem != null) {
                apk.delete();
                throw new UpdateError(problem);
            }
        }

        // 4. Permission, then installer
        if (!canInstall(app)) {
            pending = apk;
            pendingTag = latestLabel;
            ui(activity, () -> {
                setStatus(status, latestLabel + " downloaded. Turn on \"Allow from this source\" for Zippy, then come back to install.");
                openUnknownSourcesSettings(activity);
            });
            return;
        }
        ui(activity, () -> launchInstaller(activity, apk, latestLabel, status));
    }

    private static void download(Activity activity, String url, long expected, File dest, String label,
                                 String installedName, TextView status) throws IOException {
        File part = new File(dest.getPath() + ".part");
        part.delete();
        HttpURLConnection c = open(url, installedName);
        c.setRequestProperty("Accept", "application/octet-stream");
        c.setReadTimeout(60000);
        try {
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new UpdateError("Download failed (HTTP " + code + "). Try again.");
            }
            long total = c.getContentLengthLong();
            if (total <= 0) total = expected;
            long done = 0;
            long lastUi = 0;
            byte[] buf = new byte[64 * 1024];
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(part)) {
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    done += n;
                    long now = System.currentTimeMillis();
                    if (now - lastUi > 300) {
                        lastUi = now;
                        final String msg = progress(label, done, total);
                        ui(activity, () -> setStatus(status, msg));
                    }
                }
            }
            if (total > 0 && done != total) {
                part.delete();
                throw new UpdateError("Download was cut off (" + mb(done) + " of " + mb(total) + " MB). Try again.");
            }
            dest.delete();
            if (!part.renameTo(dest)) {
                part.delete();
                throw new UpdateError("Couldn't save the download.");
            }
        } catch (IOException e) {
            part.delete();
            throw e;
        } finally {
            c.disconnect();
        }
    }

    /** @return null when the APK is a newer com.zippy.ear, else a user-facing problem. */
    private static String verify(Context ctx, File apk, long installedCode) {
        PackageInfo info = ctx.getPackageManager().getPackageArchiveInfo(apk.getPath(), 0);
        if (info == null) {
            return "The downloaded file isn't a valid APK. Try again.";
        }
        if (!ctx.getPackageName().equals(info.packageName)) {
            return "The downloaded APK isn't Zippy (" + info.packageName + ").";
        }
        long code = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        if (code <= installedCode) {
            return "The downloaded APK isn't newer than this one (versionCode " + code + ").";
        }
        return null;
    }

    private static void launchInstaller(Activity activity, File apk, String label, TextView status) {
        try {
            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + AUTHORITY_SUFFIX, apk);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(i);
            setStatus(status, "Opening the installer for " + (label == null ? "the update" : label) + "…");
        } catch (ActivityNotFoundException e) {
            setStatus(status, "No app on this phone can install APKs.");
        } catch (Exception e) {
            setStatus(status, "Couldn't open the installer: " + friendly(e));
        }
    }

    private static void openUnknownSourcesSettings(Activity activity) {
        try {
            activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName())));
        } catch (Exception e) {
            try {
                activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + activity.getPackageName())));
            } catch (Exception ignored) {
                // status text already explains what to do
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    static boolean canInstall(Context ctx) {
        return Build.VERSION.SDK_INT < 26 || ctx.getPackageManager().canRequestPackageInstalls();
    }

    static String installedName(Context ctx) {
        try {
            PackageInfo p = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return p.versionName == null ? "?" : p.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    static long installedCode(Context ctx) {
        try {
            PackageInfo p = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    /** "v1.4", "1.4", "V1.4.2", "v1.5-beta" -> {1,4}, {1,4}, {1,4,2}, {1,5}. null if no leading number. */
    public static int[] parseVersion(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        int end = 0;
        while (end < s.length() && (Character.isDigit(s.charAt(end)) || s.charAt(end) == '.')) end++;
        s = s.substring(0, end);
        while (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        if (s.isEmpty() || s.startsWith(".")) return null;
        String[] parts = s.split("\\.");
        int[] out = new int[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].isEmpty()) return null;
                out[i] = Integer.parseInt(parts[i]);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return out;
    }

    /** Numeric compare, missing parts count as 0 (1.4 == 1.4.0). */
    public static int compare(int[] a, int[] b) {
        int n = Math.max(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) return x < y ? -1 : 1;
        }
        return 0;
    }

    static String join(int[] v) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append('.');
            sb.append(v[i]);
        }
        return sb.toString();
    }

    private static String progress(String label, long done, long total) {
        if (total > 0) {
            int pct = (int) Math.min(100, done * 100 / total);
            return "Downloading " + label + "… " + pct + "% (" + mb(done) + " / " + mb(total) + " MB)";
        }
        return "Downloading " + label + "… " + mb(done) + " MB";
    }

    private static String mb(long bytes) {
        return String.format(Locale.US, "%.1f", bytes / 1048576.0);
    }

    private static void cleanOld(File dir, File keep) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (!f.equals(keep)) f.delete();
        }
    }

    private static HttpURLConnection open(String url, String installedName) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(true); // GitHub asset URLs redirect https -> https
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "Zippy/" + installedName + " (Android)");
        return c;
    }

    private static String readAll(InputStream in) throws IOException {
        if (in == null) return "";
        try (InputStream s = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = s.read(buf)) != -1) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    static String friendly(Throwable t) {
        if (t instanceof UpdateError) return t.getMessage();
        if (t instanceof java.net.UnknownHostException || t instanceof java.net.ConnectException
                || t instanceof java.net.SocketTimeoutException || t instanceof javax.net.ssl.SSLException) {
            return "Couldn't reach GitHub. Check your internet connection and try again.";
        }
        if (t instanceof org.json.JSONException) {
            return "GitHub sent an unexpected reply. Try again later.";
        }
        if (t instanceof IOException) {
            return "Network error: " + (t.getMessage() == null ? "try again." : t.getMessage());
        }
        return "Update failed: " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
    }

    private static void ui(Activity a, Runnable r) {
        mainHandler().post(() -> {
            if (!a.isFinishing() && !(Build.VERSION.SDK_INT >= 17 && a.isDestroyed())) r.run();
        });
    }

    private static synchronized Handler mainHandler() {
        if (main == null) main = new Handler(Looper.getMainLooper());
        return main;
    }

    private static void setStatus(TextView status, String text) {
        if (status != null) status.setText(text);
    }

    private static void setButton(Button b, boolean enabled) {
        if (b != null) b.setEnabled(enabled);
    }

    static final class UpdateError extends IOException {
        UpdateError(String msg) { super(msg); }
    }
}
