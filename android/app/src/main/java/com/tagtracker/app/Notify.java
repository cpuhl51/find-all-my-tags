package com.tagtracker.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** A single, low-priority notification that reports the latest background check / Drive sync. */
final class Notify {
    private static final String CHANNEL = "sync";
    private static final String CHANNEL_ALERT = "theft";
    private static final String CHANNEL_BATTERY = "battery";
    private static final int ID = 1;

    private Notify() {}

    private static NotificationManager mgr(Context ctx) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
            if (nm.getNotificationChannel(CHANNEL) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL, I18n.t("Sync status"),
                        NotificationManager.IMPORTANCE_LOW); // low = no sound, minimal battery
                ch.setDescription(I18n.t("Shows when the app checks Find Hub and syncs to Google Drive"));
                nm.createNotificationChannel(ch);
            }
            if (nm.getNotificationChannel(CHANNEL_ALERT) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_ALERT, I18n.t("Theft / movement alerts"),
                        NotificationManager.IMPORTANCE_HIGH); // loud: this is the important one
                ch.setDescription(I18n.t("Alerts you when a guarded tag moves"));
                nm.createNotificationChannel(ch);
            }
            if (nm.getNotificationChannel(CHANNEL_BATTERY) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_BATTERY, I18n.t("Tag battery"),
                        NotificationManager.IMPORTANCE_DEFAULT);
                ch.setDescription(I18n.t("Tells you when a tag's battery gets low"));
                nm.createNotificationChannel(ch);
            }
        }
        return nm;
    }

    /** Loud alert when a guarded tag moves. Actions: open the map, open Maps, or ring the tag. */
    static void theftAlert(Context ctx, String deviceId, String name, int movedM, double lat, double lon) {
        NotificationManager nm = mgr(ctx);
        if (nm == null) return;
        int rc = Math.abs(name.hashCode());
        PendingIntent open = PendingIntent.getActivity(ctx, rc, new Intent(ctx, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);
        Intent maps = new Intent(Intent.ACTION_VIEW,
                android.net.Uri.parse("https://www.google.com/maps?q=" + lat + "," + lon));
        PendingIntent mapsPi = PendingIntent.getActivity(ctx, rc + 1, maps, PendingIntent.FLAG_IMMUTABLE);
        Intent ring = new Intent(ctx, RingReceiver.class).putExtra(RingReceiver.EXTRA_DEVICE, deviceId);
        PendingIntent ringPi = PendingIntent.getBroadcast(ctx, rc + 2, ring,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(ctx, CHANNEL_ALERT)
                : new Notification.Builder(ctx).setPriority(Notification.PRIORITY_HIGH);
        Notification n = b.setContentTitle("⚠ " + name + I18n.t(" is moving!"))
                .setContentText(I18n.t("Moved ") + movedM + I18n.t(" m. Tap to see the map."))
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(R.drawable.ic_launcher, I18n.t("Ring tag"), ringPi).build())
                .addAction(new Notification.Action.Builder(R.drawable.ic_launcher, I18n.t("Open in Maps"), mapsPi).build())
                .setAutoCancel(true)
                .build();
        try {
            nm.notify(1000 + rc % 1000, n);
        } catch (SecurityException ignored) {
        }
    }

    static void syncStatus(Context ctx, String text, boolean ongoing) {
        NotificationManager nm = mgr(ctx);
        if (nm == null) return;
        PendingIntent tap = PendingIntent.getActivity(ctx, 0, new Intent(ctx, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(ctx, CHANNEL)
                : new Notification.Builder(ctx);
        Notification n = b.setContentTitle(I18n.t("Tag Tracker is running"))
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(tap)
                .setOnlyAlertOnce(true)
                .setOngoing(ongoing)   // ongoing = a persistent I18n.t("active in background") indicator
                .build();
        try {
            nm.notify(ID, n);
        } catch (SecurityException ignored) {
            // POST_NOTIFICATIONS not granted (Android 13+); silently skip.
        }
    }

    /** Meldung, wenn der Akku eines Tags auf niedrig (2) oder kritisch (3) fällt. */
    static void batteryAlert(Context ctx, String deviceId, String name, int level) {
        NotificationManager nm = mgr(ctx);
        if (nm == null) return;
        int rc = Math.abs(deviceId.hashCode());
        PendingIntent open = PendingIntent.getActivity(ctx, rc, new Intent(ctx, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(ctx, CHANNEL_BATTERY)
                : new Notification.Builder(ctx);
        boolean crit = level >= 3;
        String title = (crit ? "🔴 " : "🟡 ") + name + ": "
                + I18n.t(ctx, crit ? I18n.t("battery critically low") : I18n.t("battery low"));
        String text = I18n.t(ctx, crit ? I18n.t("Replace the battery soon, or the tag will stop sending its location.")
                : I18n.t("The battery is getting weak. Have a spare ready."));
        Notification n = b.setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build();
        try {
            nm.notify(2000 + rc % 1000, n);
        } catch (SecurityException ignored) {
        }
    }
}
