package com.roomarchive.server;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

/** 灵动岛辅助服务：监听窗口切换，让灵动岛知道当前前台应用；不读取输入内容。 */
public class IslandAccessibilityService extends AccessibilityService {
    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = e.getPackageName();
        Intent i = new Intent("com.roomarchive.server.ISLAND_FOREGROUND");
        i.setPackage(getPackageName());
        i.putExtra("pkg", pkg == null ? "" : pkg.toString());
        sendBroadcast(i);
    }
    @Override public void onInterrupt() {}
}
