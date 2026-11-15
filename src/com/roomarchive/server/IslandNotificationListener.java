package com.roomarchive.server;

import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/** 灵动岛通知监听：只把通知标题/正文发给 IslandService，不存储、不上传。 */
public class IslandNotificationListener extends NotificationListenerService {
    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            String pkg = sbn.getPackageName();
            if ("com.roomarchive.server".equals(pkg)) return;
            CharSequence title = sbn.getNotification().extras.getCharSequence("android.title");
            CharSequence text = sbn.getNotification().extras.getCharSequence("android.text");
            Intent i = new Intent("com.roomarchive.server.ISLAND_NOTIFY");
            i.setPackage(getPackageName());
            i.putExtra("pkg", pkg);
            i.putExtra("title", title == null ? "" : title.toString());
            i.putExtra("text", text == null ? "" : text.toString());
            sendBroadcast(i);
        } catch (Throwable ignored) {}
    }
}
