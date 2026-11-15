package com.roomarchive.server;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 安装确认自动点击：只在系统安装器界面生效，自动点掉「继续安装 / 安装 / 仍然安装 / 下一步」。
 * 用来配合「普通安装」——系统弹窗照旧，但不用人手点。
 */
public class InstallClickService extends AccessibilityService {

    private static final String[] TEXTS = {
            "继续安装", "安装", "仍然安装", "仍要安装", "继续", "下一步", "确定", "完成"
    };
    private static final String[] PKGS = {
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.miui.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.vivo.packageinstaller",
            "com.huawei.packageinstaller",
            "com.oppo.packageinstaller",
            "com.android.packageinstaller.oplus"
    };

    private final Handler ui = new Handler(Looper.getMainLooper());
    private long last = 0;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.packageNames = PKGS;
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
            setServiceInfo(info);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null) return;
        CharSequence pkg = e.getPackageName();
        if (pkg == null) return;
        boolean target = false;
        for (String p : PKGS) if (p.contentEquals(pkg)) target = true;
        if (!target) return;
        long now = System.currentTimeMillis();
        if (now - last < 350) return;
        last = now;
        ui.postDelayed(() -> clickConfirm(), 220);
    }

    private void clickConfirm() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;
            for (String t : TEXTS) {
                List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByText(t);
                if (list == null) continue;
                for (AccessibilityNodeInfo n : list) {
                    if (n == null) continue;
                    AccessibilityNodeInfo clickable = n;
                    int depth = 0;
                    while (clickable != null && !clickable.isClickable() && depth < 4) {
                        clickable = clickable.getParent();
                        depth++;
                    }
                    if (clickable != null && clickable.isClickable()
                            && clickable.isEnabled() && clickable.isVisibleToUser()) {
                        clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        return;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onInterrupt() {}
}
