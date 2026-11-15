package com.roomarchive.server;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** 灵动岛：白底设置页，5 项必需权限逐项授权，全部通过后才能启动。 */
public class IslandActivity extends Activity {
    private LinearLayout permBox;
    private TextView progress, startBtn;
    private LinearLayout settingsBox;
    private boolean settingsBuilt = false;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Tools.applyStatusBar(this);
        getWindow().setStatusBarColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= 23) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFFF7F7F9);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(36));
        sv.addView(root);

        TextView title = new TextView(this);
        title.setText("IK 灵动岛");
        title.setTextSize(30);
        title.setTextColor(0xFF111114);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("配置权限");
        sub.setTextSize(24);
        sub.setTextColor(0xFF1A1A1E);
        sub.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = dp(26);
        root.addView(sub, slp);

        progress = new TextView(this);
        progress.setTextSize(13);
        progress.setTextColor(0xFF9A6A2F);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, -2);
        plp.topMargin = dp(6);
        root.addView(progress, plp);

        permBox = new LinearLayout(this);
        permBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.topMargin = dp(14);
        root.addView(permBox, blp);

        startBtn = new TextView(this);
        startBtn.setGravity(Gravity.CENTER);
        startBtn.setTextSize(15);
        startBtn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        startBtn.setPadding(0, dp(15), 0, dp(15));
        LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(-1, -2);
        stp.topMargin = dp(14);
        root.addView(startBtn, stp);
        startBtn.setOnClickListener(v -> tryStart());

        TextView refresh = new TextView(this);
        refresh.setText("我已授权完成");
        refresh.setGravity(Gravity.CENTER);
        refresh.setTextSize(14);
        refresh.setTextColor(0xFF2F6BFF);
        refresh.setPadding(0, dp(13), 0, dp(13));
        refresh.setBackground(bg(0xFFE8EFFF, 14, 0xFFD5E2FF));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.topMargin = dp(12);
        root.addView(refresh, rlp);
        refresh.setOnClickListener(v -> refresh());

        settingsBox = new LinearLayout(this);
        settingsBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams setlp = new LinearLayout.LayoutParams(-1, -2);
        setlp.topMargin = dp(34);
        root.addView(settingsBox, setlp);

        setContentView(sv);
        refresh();
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private boolean overlay() { return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this); }
    private boolean listener() {
        try {
            if (Build.VERSION.SDK_INT >= 27) return ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                    .isNotificationListenerAccessGranted(new ComponentName(this, IslandNotificationListener.class));
        } catch (Throwable ignored) {}
        String s = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(getPackageName());
    }
    private boolean access() {
        String s = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(getPackageName() + "/" + IslandAccessibilityService.class.getName());
    }
    private boolean battery() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            return Build.VERSION.SDK_INT < 23 || pm.isIgnoringBatteryOptimizations(getPackageName());
        } catch (Throwable t) { return false; }
    }
    private boolean notif() {
        return Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }
    private int granted() {
        int n = 0; if (overlay()) n++; if (listener()) n++; if (notif()) n++; if (access()) n++; if (battery()) n++; return n;
    }

    private void refresh() {
        int n = granted();
        progress.setText("必需权限：" + n + "/5 · 全部授权后才能继续");
        progress.setTextColor(n == 5 ? 0xFF188A4A : 0xFF9A6A2F);
        permBox.removeAllViews();
        addRow("悬浮窗权限", overlay(), () -> {
            try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))); }
            catch (Throwable t) { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)); }
        });
        addRow("通知使用权", listener(), () -> {
            try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
            catch (Throwable t) { Toast.makeText(this, "请到系统设置开启通知使用权", Toast.LENGTH_LONG).show(); }
        });
        addRow("通知权限", notif(), () -> {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 9);
            else Toast.makeText(this, "当前系统无需单独通知权限", Toast.LENGTH_SHORT).show();
        });
        addRow("无障碍权限（必须开启）", access(), () -> {
            try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
            catch (Throwable t) { Toast.makeText(this, "请到系统设置开启无障碍", Toast.LENGTH_LONG).show(); }
        });
        addRow("忽略电池优化（必须开启）", battery(), () -> {
            try {
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
            } catch (Throwable t) {
                try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); } catch (Throwable ignored) {}
            }
        });
        if (n == 5) {
            startBtn.setText(IslandService.isRunning() ? "灵动岛运行中 · 点击进入通用设置" : "启动灵动岛");
            startBtn.setTextColor(Color.WHITE);
            startBtn.setBackground(bg(0xFF16A34A, 14, 0xFF16A34A));
        } else {
            startBtn.setText("请先完成权限");
            startBtn.setTextColor(0xFF9AA6B8);
            startBtn.setBackground(bg(0xFFE4E8EE, 14, 0xFFE4E8EE));
        }
        buildSettings();
    }

    private void addRow(String label, boolean ok, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(11), dp(16), dp(11));
        row.setBackground(bg(0xFFEEEEF2, 18, 0xFFEEEEF2));
        TextView btn = new TextView(this);
        btn.setText(ok ? "已授权" : "去授权");
        btn.setTextSize(13);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(18), dp(9), dp(18), dp(9));
        btn.setTextColor(ok ? 0xFF2E9D5B : 0xFF2F6BFF);
        btn.setBackground(bg(ok ? 0xFFE1F5E7 : 0xFFE9EFFF, 13, ok ? 0xFFCBEAD5 : 0xFFD5E2FF));
        row.addView(btn, new LinearLayout.LayoutParams(-2, -2));
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(16);
        tv.setTextColor(0xFF17171B);
        tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.leftMargin = dp(14);
        row.addView(tv, tlp);
        row.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.topMargin = dp(10);
        permBox.addView(row, rlp);
    }

    private void buildSettings() {
        if (settingsBuilt) return;
        settingsBuilt = true;
        TextView h = new TextView(this);
        h.setText("通用");
        h.setTextSize(22);
        h.setTextColor(0xFF111114);
        h.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        settingsBox.addView(h);
        TextView note = new TextView(this);
        note.setText("常驻、横屏隐藏、通知/充电/音量/音乐/响铃/解锁/锁屏");
        note.setTextSize(12);
        note.setTextColor(0xFF8A8A93);
        note.setPadding(0, dp(4), 0, dp(10));
        settingsBox.addView(note);
        addSwitch("常驻显示", "always", true);
        addSwitch("横屏自动隐藏", "hide_land", true);
        addSwitch("来消息时显示", "notify", true);
        addSwitch("充电时显示", "charge", true);
        addSwitch("调音量时显示", "volume", false);
        addSwitch("播放音乐时显示", "music", false);
        addSwitch("响铃/静音时显示", "ringer", false);
        addSwitch("解锁时显示", "unlock", false);
        addSwitch("锁屏时显示", "lock", false);
        TextView size = new TextView(this);
        size.setText("外观尺寸");
        size.setTextSize(18); size.setTextColor(0xFF111114); size.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        size.setPadding(0, dp(18), 0, dp(4)); settingsBox.addView(size);
        addSeek("紧凑胶囊宽度", "cap_w", 110, 230, 156);
        addSeek("展开卡片宽度", "exp_w", 220, 380, 272);
    }

    private void addSeek(String label, String key, int min, int max, int def) {
        TextView l = new TextView(this); l.setText(label); l.setTextSize(13); l.setTextColor(0xFF6B6B75); l.setPadding(0, dp(10), 0, 0); settingsBox.addView(l);
        android.widget.SeekBar sb = new android.widget.SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(Math.max(0, getSharedPreferences("ik_island", MODE_PRIVATE).getInt(key, def) - min));
        sb.setPadding(0, dp(6), 0, dp(6));
        sb.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(android.widget.SeekBar bar, int p, boolean fromUser) {
                getSharedPreferences("ik_island", MODE_PRIVATE).edit().putInt(key, min + p).apply();
                sendBroadcast(new Intent("com.roomarchive.server.ISLAND_RESIZE").setPackage(getPackageName()));
            }
            public void onStartTrackingTouch(android.widget.SeekBar bar) {}
            public void onStopTrackingTouch(android.widget.SeekBar bar) {}
        });
        settingsBox.addView(sb);
    }

    private void addSwitch(String label, String key, boolean def) {
        Switch sw = new Switch(this);
        sw.setText(label);
        sw.setTextSize(15);
        sw.setTextColor(0xFF17171B);
        sw.setPadding(0, dp(8), 0, dp(8));
        sw.setChecked(getSharedPreferences("ik_island", MODE_PRIVATE).getBoolean(key, def));
        sw.setOnCheckedChangeListener((buttonView, isChecked) ->
                getSharedPreferences("ik_island", MODE_PRIVATE).edit().putBoolean(key, isChecked).apply());
        settingsBox.addView(sw);
    }

    private void tryStart() {
        if (granted() < 5) {
            Toast.makeText(this, "还有权限未授权，全部通过后才能启动", Toast.LENGTH_LONG).show();
            refresh();
            return;
        }
        try {
            startService(new Intent(this, IslandService.class));
            Toast.makeText(this, "灵动岛已启动", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, "启动失败：" + t, Toast.LENGTH_LONG).show();
        }
        refresh();
    }

    private GradientDrawable bg(int fill, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), stroke);
        return d;
    }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
