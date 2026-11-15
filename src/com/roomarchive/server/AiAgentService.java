package com.roomarchive.server;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

/** 本地 AI 的手：读屏、点击、滑动、返回、开应用 */
public class AiAgentService extends AccessibilityService {

    private static volatile AiAgentService INSTANCE;

    public static boolean ready() { return INSTANCE != null; }

    public static AiAgentService get() { return INSTANCE; }

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        INSTANCE = this;
    }

    @Override public void onDestroy() {
        INSTANCE = null;
        super.onDestroy();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {}
    @Override public void onInterrupt() {}

    /** 把当前屏幕读成给模型看的文字清单 */
    public String screenText() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "读不到屏幕内容（当前页面可能禁止读取）";
        List<String> out = new ArrayList<>();
        walk(root, out, 0);
        if (out.isEmpty()) return "当前屏幕没有可读文字";
        StringBuilder sb = new StringBuilder("当前屏幕（包名 " + root.getPackageName() + "）：\n");
        for (String s : out) sb.append(s).append('\n');
        return sb.toString();
    }

    private void walk(AccessibilityNodeInfo n, List<String> out, int depth) {
        if (n == null || depth > 40 || out.size() > 160) return;
        String t = n.getText() == null ? "" : n.getText().toString().trim();
        if (t.isEmpty() && n.getContentDescription() != null) {
            t = n.getContentDescription().toString().trim();
        }
        if (!t.isEmpty() && t.length() < 60) {
            Rect r = new Rect();
            n.getBoundsInScreen(r);
            String tag = n.isClickable() ? "可点" : "";
            out.add("· " + t + "  (" + r.centerX() + "," + r.centerY() + ")" + tag);
        }
        for (int i = 0; i < n.getChildCount(); i++) walk(n.getChild(i), out, depth + 1);
    }

    public boolean clickText(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        List<AccessibilityNodeInfo> all = new ArrayList<>();
        collect(root, all, 0);
        AccessibilityNodeInfo exact = null, loose = null;
        for (AccessibilityNodeInfo n : all) {
            String t = n.getText() == null ? "" : n.getText().toString();
            String d = n.getContentDescription() == null ? "" : n.getContentDescription().toString();
            if (t.equals(text) || d.equals(text)) { exact = n; break; }
            if (loose == null && (t.contains(text) || d.contains(text))) loose = n;
        }
        AccessibilityNodeInfo target = exact != null ? exact : loose;
        if (target == null) return false;
        AccessibilityNodeInfo cur = target;
        while (cur != null) {
            if (cur.isClickable()) {
                if (cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
            }
            cur = cur.getParent();
        }
        Rect r = new Rect();
        target.getBoundsInScreen(r);
        if (r.width() > 0) return tap(r.centerX(), r.centerY());
        return false;
    }

    private void collect(AccessibilityNodeInfo n, List<AccessibilityNodeInfo> out, int depth) {
        if (n == null || depth > 40 || out.size() > 400) return;
        out.add(n);
        for (int i = 0; i < n.getChildCount(); i++) collect(n.getChild(i), out, depth + 1);
    }

    public boolean tap(int x, int y) {
        Path p = new Path();
        p.moveTo(x, y);
        GestureDescription.Builder b = new GestureDescription.Builder();
        b.addStroke(new GestureDescription.StrokeDescription(p, 0, 60));
        return dispatchGesture(b.build(), null, null);
    }

    public boolean swipe(String dir) {
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;
        Path p = new Path();
        switch (dir) {
            case "下":
                p.moveTo(w / 2, h / 4); p.lineTo(w / 2, h * 3 / 4); break;
            case "左":
                p.moveTo(w * 3 / 4, h / 2); p.lineTo(w / 4, h / 2); break;
            case "右":
                p.moveTo(w / 4, h / 2); p.lineTo(w * 3 / 4, h / 2); break;
            default:
                p.moveTo(w / 2, h * 3 / 4); p.lineTo(w / 2, h / 4);
        }
        GestureDescription.Builder b = new GestureDescription.Builder();
        b.addStroke(new GestureDescription.StrokeDescription(p, 0, 320));
        return dispatchGesture(b.build(), null, null);
    }

    public boolean back() { return performGlobalAction(GLOBAL_ACTION_BACK); }
    public boolean home() { return performGlobalAction(GLOBAL_ACTION_HOME); }
    public boolean recents() {
        if (Build.VERSION.SDK_INT >= 21) return performGlobalAction(GLOBAL_ACTION_RECENTS);
        return false;
    }

    public boolean openApp(String pkg) {
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i == null) return false;
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
