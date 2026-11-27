package com.zippy.ear;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "Open Grok Bot on wake". Grok Bot's Play Store package is ai.x.grok.bot; a few guesses and a
 * launcher-label search are fallbacks. AndroidManifest declares <queries> for the package and for
 * MAIN/LAUNCHER so this works on Android 11+ package visibility.
 *
 * Background launches: Android 10+ blocks activity starts from the background unless the app
 * holds "Display over other apps" (SYSTEM_ALERT_WINDOW) or is on screen. Without it Zippy posts a
 * tap-to-open notification instead and says so; it never crashes.
 */
public final class GrokBot {
    public static final String PLAY_PACKAGE = "ai.x.grok.bot";
    static final String[] CANDIDATES = {PLAY_PACKAGE, "ai.x.grokbot", "com.xai.grokbot", "com.cursor.grokbot", "ai.x.grok.bot.android"};
    private static final String CHANNEL = "zippy-grokbot";
    private static final int NOTIF_ID = 42;

    private GrokBot() {}

    /** Pure choice from installed launcher apps (package, label). */
    public static String pick(List<String[]> launchers) {
        for (String c : CANDIDATES) for (String[] a : launchers) if (c.equals(a[0])) return a[0];
        for (String[] a : launchers) if (a[1] != null && a[1].trim().equalsIgnoreCase("grok bot")) return a[0];
        for (String[] a : launchers) if (a[1] != null && a[1].toLowerCase(Locale.ROOT).contains("grok bot")) return a[0];
        for (String[] a : launchers) if (a[1] != null && a[1].toLowerCase(Locale.ROOT).contains("grok")) return a[0];
        return null;
    }

    public static String findPackage(Context c) {
        PackageManager pm = c.getPackageManager();
        for (String p : CANDIDATES) {
            if (pm.getLaunchIntentForPackage(p) != null) return p;
        }
        try {
            Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<String[]> apps = new ArrayList<>();
            for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
                CharSequence l = ri.loadLabel(pm);
                apps.add(new String[]{ri.activityInfo.packageName, l == null ? "" : l.toString()});
            }
            return pick(apps);
        } catch (Exception e) {
            return null;
        }
    }

    public static String label(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    public static boolean canLaunchFromBackground(Context c) {
        return Build.VERSION.SDK_INT < 29 || Settings.canDrawOverlays(c);
    }

    public static Intent overlaySettings(Context c) {
        return new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + c.getPackageName()));
    }

    /**
     * Open Grok Bot from the listener. @return null if it opened, else a short status note.
     * @param appVisible Zippy's own screen is in front (launch is always allowed then)
     */
    public static String open(Context c, boolean appVisible) {
        String pkg = findPackage(c);
        if (pkg == null) return "Grok Bot isn't installed (looked for " + PLAY_PACKAGE + ").";
        Intent launch = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) return "Grok Bot can't be opened.";
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (appVisible || canLaunchFromBackground(c)) {
            try {
                c.startActivity(launch);
                return null;
            } catch (Exception e) {
                // fall through to the notification
            }
        }
        try {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Open Grok Bot", NotificationManager.IMPORTANCE_HIGH));
            }
            PendingIntent pi = PendingIntent.getActivity(c, 7, launch, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification n = new Notification.Builder(c, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                    .setContentTitle("Hey Zippy")
                    .setContentText("Tap to open Grok Bot")
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setTimeoutAfter(60000)
                    .build();
            nm.notify(NOTIF_ID, n);
        } catch (Exception ignored) { }
        return "Android blocked opening Grok Bot from the background. Tap the notification, or allow \"Display over other apps\" for Zippy in Settings.";
    }
}
