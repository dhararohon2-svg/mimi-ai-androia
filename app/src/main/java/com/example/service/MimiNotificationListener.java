package com.example.service;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MimiNotificationListener extends NotificationListenerService {

    public static class AppNotification {
        public String packageName;
        public String title;
        public String text;
        public long postTime;

        public AppNotification(String packageName, String title, String text, long postTime) {
            this.packageName = packageName;
            this.title = title;
            this.text = text;
            this.postTime = postTime;
        }
    }

    private static MimiNotificationListener sInstance = null;
    private static final List<AppNotification> sRecentNotifications = Collections.synchronizedList(new ArrayList<>());

    public static MimiNotificationListener getInstance() {
        return sInstance;
    }

    public static boolean isRunning() {
        return sInstance != null;
    }

    public static boolean isPermissionGranted(Context context) {
        String enabledListeners = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
        String myComponent = new ComponentName(context, MimiNotificationListener.class).flattenToString();
        return enabledListeners != null && enabledListeners.contains(myComponent);
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        sInstance = this;
        refreshActiveNotifications();
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        if (sInstance == this) {
            sInstance = null;
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;
        Notification n = sbn.getNotification();
        Bundle extras = n.extras;
        if (extras == null) return;

        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);

        if (title != null && text != null && !sbn.getPackageName().equals(getPackageName())) {
            AppNotification item = new AppNotification(
                    sbn.getPackageName(),
                    title.toString(),
                    text.toString(),
                    sbn.getPostTime()
            );
            sRecentNotifications.add(0, item);
            if (sRecentNotifications.size() > 50) {
                sRecentNotifications.remove(sRecentNotifications.size() - 1);
            }
        }
    }

    public void refreshActiveNotifications() {
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active != null) {
                for (StatusBarNotification sbn : active) {
                    onNotificationPosted(sbn);
                }
            }
        } catch (Exception ignored) {}
    }

    public static List<AppNotification> getRecentNotifications(int max) {
        List<AppNotification> copy = new ArrayList<>(sRecentNotifications);
        if (copy.size() > max) {
            return copy.subList(0, max);
        }
        return copy;
    }

    public static String getReadableSummary() {
        if (sRecentNotifications.isEmpty()) {
            return "You have no new notifications right now.";
        }
        StringBuilder sb = new StringBuilder();
        int count = Math.min(sRecentNotifications.size(), 3);
        sb.append("You have ").append(count).append(" recent notifications. ");
        for (int i = 0; i < count; i++) {
            AppNotification item = sRecentNotifications.get(i);
            sb.append("Notification ").append(i + 1).append(" from ").append(item.title).append(": ").append(item.text).append(". ");
        }
        return sb.toString();
    }
}
