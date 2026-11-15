package com.roomarchive.server;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** 灵动岛设置 · 只申请悬浮窗权限，不申请无障碍/通知使用权 */
public class IslandActivity extends Activity {

    private TextView status, permDetail;
    private Switch swAlways, swHideLand;
    private SharedPreferences sp;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Tools.applyStatusBar(this);
        sp = getSharedPreferences(IslandService.PREFS, MODE_PRIVATE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Tools.bgWithGlow(this));
        root.setPadding(dp(20), dp(26), dp(20), dp(20));

        TextView title = new TextView(this);
        title.setText("灵动岛");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("顶部胶囊 · 时间 / 电量 / 展开卡片 · 只需悬浮窗权限");
        sub.setTextColor(0xFF9FB3C8);
        sub.setTextSize(12);
        sub.setPadding(0, dp(4), 0, dp(16));
        root.addView(sub);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(glass());
        root.addView(card);

        status = new TextView(this);
        status.setTextColor(0xFF7DD3FC);
        status.setTextSize(13);
        status.setPadding(0, 0, 0, dp(12));
        card.addView(status);

        TextView perm = pill("① 授予悬浮窗权限", 0xFF2563EB);
        perm.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Throwable t) {
                Toast.makeText(this, "请到系统设置里开启悬浮窗", Toast.LENGTH_LONG).show();
            }
        });
        card.addView(perm, lp(-1, dp(46), 0, 8));

        permDetail = new TextView(this);
        permDetail.setTextSize(11);
        permDetail.setTextColor(0xFFBFD4E8);
        permDetail.setPadding(dp(4), 0, dp(4), dp(10));
        card.addView(permDetail);

        LinearLayout permRow = new LinearLayout(this);
        permRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView nl = pill("通知监听", 0x33FFFFFF);
        nl.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
            catch (Throwable t) { Toast.makeText(this, "请到系统设置开启通知使用权", Toast.LENGTH_LONG).show(); }
        });
        TextView acc = pill("无障碍", 0x33FFFFFF);
        acc.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
            catch (Throwable t) { Toast.makeText(this, "请到系统设置开启无障碍", Toast.LENGTH_LONG).show(); }
        });
        TextView bat = pill("电池白名单", 0x33FFFFFF);
        bat.setOnClickListener(v -> {
            try {
                Intent it = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName()));
                startActivity(it);
            } catch (Throwable t) {
                try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
                catch (Throwable ignored) {}
            }
        });
        permRow.addView(nl); permRow.addView(acc); permRow.addView(bat);
        card.addView(permRow, lp(-1, dp(38), 0, 6));

        TextView start = pill("② 启动灵动岛", 0xFF16A34A);
        start.setOnClickListener(v -> {
            String lack = missingPermissions();
            if (!lack.isEmpty()) {
                Toast.makeText(this, "还差：" + lack, Toast.LENGTH_LONG).show();
                status.setTextColor(0xFFF87171);
                status.setText("权限未齐 · " + lack);
                return;
            }
            sp.edit().putBoolean(IslandService.KEY_ON, true).apply();
            IslandService.start(this);
            status.postDelayed(this::refresh, 300);
        });
        card.addView(start, lp(-1, dp(46), 0, 8));

        TextView stop = pill("停止灵动岛", 0x33FFFFFF);
        stop.setOnClickListener(v -> {
            sp.edit().putBoolean(IslandService.KEY_ON, false).apply();
            IslandService.stop(this);
            status.postDelayed(this::refresh, 300);
        });
        card.addView(stop, lp(-1, dp(44), 0, 8));

        swAlways = new Switch(this);
        swAlways.setText("常驻显示（无通知时也保留胶囊）");
        swAlways.setTextColor(Color.WHITE);
        swAlways.setTextSize(13);
        swAlways.setChecked(sp.getBoolean(IslandService.KEY_ALWAYS, true));
        swAlways.setOnCheckedChangeListener((v, c) -> sp.edit().putBoolean(IslandService.KEY_ALWAYS, c).apply());
        card.addView(swAlways);

        swHideLand = new Switch(this);
        swHideLand.setText("横屏自动隐藏（竖屏恢复）");
        swHideLand.setTextColor(Color.WHITE);
        swHideLand.setTextSize(13);
        swHideLand.setChecked(sp.getBoolean(IslandService.KEY_HIDE_LAND, true));
        swHideLand.setOnCheckedChangeListener((v, c) -> sp.edit().putBoolean(IslandService.KEY_HIDE_LAND, c).apply());
        card.addView(swHideLand);

        TextView note = new TextView(this);
        note.setText("说明：本版灵动岛只做本机展示，不读取他人通知内容；\n不使用无障碍服务，不会出现「5/5 权限全部授权才能用」的死锁。");
        note.setTextColor(0xFF7E93AA);
        note.setTextSize(11);
        note.setPadding(0, dp(16), 0, 0);
        root.addView(note);

        TextView back = new TextView(this);
        back.setText("返回工具箱");
        back.setTextColor(0xFF9FB3C8);
        back.setTextSize(13);
        back.setGravity(Gravity.CENTER);
        back.setPadding(0, dp(20), 0, dp(10));
        back.setOnClickListener(v -> finish());
        root.addView(back);

        setContentView(root);
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private boolean hasOverlay() { return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this); }
    private boolean hasNotifListener() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 27)
                return nm.isNotificationListenerAccessGranted(new ComponentName(this, IslandNotificationListener.class));
        } catch (Throwable ignored) {}
        String s = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(getPackageName());
    }
    private boolean hasAccessibility() {
        String s = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(getPackageName() + "/" + IslandAccessibilityService.class.getName());
    }
    private boolean hasBattery() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            return Build.VERSION.SDK_INT < 23 || pm.isIgnoringBatteryOptimizations(getPackageName());
        } catch (Throwable t) { return false; }
    }
    private boolean hasNotifPerm() {
        return Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }
    private String missingPermissions() {
        StringBuilder b = new StringBuilder();
        if (!hasOverlay()) b.append("悬浮窗 ");
        if (!hasNotifListener()) b.append("通知监听 ");
        if (!hasAccessibility()) b.append("无障碍 ");
        if (!hasBattery()) b.append("电池白名单 ");
        if (!hasNotifPerm()) b.append("通知权限 ");
        return b.toString().trim();
    }
    private void refresh() {
        boolean run = IslandService.isRunning();
        String lack = missingPermissions();
        boolean all = lack.isEmpty();
        status.setText((all ? "5/5 权限已授权 · 可启动" : "权限未齐：" + lack)
                + "   ·   " + (run ? "灵动岛运行中" : "未启动"));
        status.setTextColor(all && run ? 0xFF4ADE80 : (all ? 0xFF7DD3FC : 0xFFF87171));
        if (permDetail != null) {
            permDetail.setText((hasOverlay() ? "✓ " : "✗ ") + "悬浮窗   "
                    + (hasNotifListener() ? "✓ " : "✗ ") + "通知监听   "
                    + (hasAccessibility() ? "✓ " : "✗ ") + "无障碍   "
                    + (hasBattery() ? "✓ " : "✗ ") + "电池白名单   "
                    + (hasNotifPerm() ? "✓ " : "✗ ") + "通知权限");
        }
    }

    private TextView pill(String text, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(14);
        tv.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{lighten(color), color});
        g.setCornerRadius(dp(16));
        tv.setBackground(g);
        return tv;
    }

    private GradientDrawable glass() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0x33FFFFFF, 0x14FFFFFF});
        g.setCornerRadius(dp(18));
        g.setStroke(dp(1), 0x33FFFFFF);
        return g;
    }

    private static int lighten(int c) {
        int r = Math.min(255, ((c >> 16) & 0xFF) + 35);
        int g = Math.min(255, ((c >> 8) & 0xFF) + 35);
        int b = Math.min(255, (c & 0xFF) + 35);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private LinearLayout.LayoutParams lp(int w, int h, int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = dp(top);
        p.bottomMargin = dp(bottom);
        return p;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
