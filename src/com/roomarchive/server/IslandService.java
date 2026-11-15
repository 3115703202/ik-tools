package com.roomarchive.server;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 灵动岛 · 悬浮胶囊。
 * 只依赖「悬浮窗」一个权限，不碰无障碍、不碰通知使用权；
 * 顶部胶囊显示时间/电量，点击展开为卡片，横屏可自动隐藏。
 */
public class IslandService extends Service {

    public static final String PREFS = "ik_island";
    public static final String KEY_ON = "on";
    public static final String KEY_ALWAYS = "always";
    public static final String KEY_HIDE_LAND = "hide_land";

    private static boolean running = false;
    private WindowManager wm;
    private View island;
    private TextView timeView, batteryView, dateView, detailView, notifyView;
    private LinearLayout detailBox;
    private boolean expanded = false, landscape = false;
    private int battery = -1, chargeState = 0;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private final Runnable clock = new Runnable() {
        @Override public void run() {
            refreshTime();
            ui.postDelayed(this, 30000);
        }
    };

    private final BroadcastReceiver eventRx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (i == null) return;
            String a = i.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(a)) { showTemp("已锁定 · 通知仍会上岛", 0); return; }
            if (Intent.ACTION_SCREEN_ON.equals(a)) { showTemp("已解锁", 2500); return; }
            if ("android.media.VOLUME_CHANGED_ACTION".equals(a)) {
                try {
                    android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
                    int v = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
                    int m = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
                    showTemp("音量 " + (m > 0 ? Math.round(v * 100f / m) : 0) + "%", 2500);
                } catch (Throwable ignored) {}
                return;
            }
            if ("android.media.RINGER_MODE_CHANGED".equals(a)) {
                try {
                    android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
                    int mode = am.getRingerMode();
                    showTemp(mode == android.media.AudioManager.RINGER_MODE_SILENT ? "静音模式" :
                            mode == android.media.AudioManager.RINGER_MODE_VIBRATE ? "震动模式" : "响铃模式", 2500);
                } catch (Throwable ignored) {}
            }
        }
    };

    private void showTemp(String text, long restoreMs) {
        if (timeView == null) return;
        timeView.setText(text);
        ui.removeCallbacks(restoreClock);
        if (restoreMs <= 0) return;
        ui.postDelayed(restoreClock, restoreMs);
    }

    private final BroadcastReceiver islandRx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (i == null) return;
            if ("com.roomarchive.server.ISLAND_NOTIFY".equals(i.getAction())) {
                String title = i.getStringExtra("title");
                String text = i.getStringExtra("text");
                String line = (title == null ? "" : title) + (text == null || text.isEmpty() ? "" : "：" + text);
                if (line.length() > 22) line = line.substring(0, 22) + "…";
                timeView.setText(line.isEmpty() ? "新通知" : line);
                if (notifyView != null) notifyView.setText(line);
                ui.removeCallbacks(restoreClock);
                ui.postDelayed(restoreClock, 6000);
            } else if ("com.roomarchive.server.ISLAND_FOREGROUND".equals(i.getAction())) {
                String pkg = i.getStringExtra("pkg");
                if (pkg != null && !pkg.isEmpty() && detailView != null) {
                    detailView.setText("前台：" + pkg + " · 点击胶囊收起");
                }
            }
        }
    };

    private final Runnable restoreClock = new Runnable() {
        @Override public void run() { refreshTime(); }
    };

    private final BroadcastReceiver batteryRx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            battery = i.getIntExtra(BatteryManager.EXTRA_LEVEL, battery);
            chargeState = i.getIntExtra(BatteryManager.EXTRA_STATUS, chargeState);
            refreshBattery();
        }
    };

    public static boolean isRunning() { return running; }

    public static void start(Context c) {
        try { c.startService(new Intent(c, IslandService.class)); } catch (Throwable ignored) {}
    }

    public static void stop(Context c) {
        try { c.stopService(new Intent(c, IslandService.class)); } catch (Throwable ignored) {}
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        buildIsland();
        registerReceiver(batteryRx, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        IntentFilter f = new IntentFilter();
        f.addAction("com.roomarchive.server.ISLAND_NOTIFY");
        f.addAction("com.roomarchive.server.ISLAND_FOREGROUND");
        registerReceiver(islandRx, f);
        IntentFilter sf = new IntentFilter();
        sf.addAction(Intent.ACTION_SCREEN_ON);
        sf.addAction(Intent.ACTION_SCREEN_OFF);
        sf.addAction("android.media.VOLUME_CHANGED_ACTION");
        sf.addAction("android.media.RINGER_MODE_CHANGED");
        registerReceiver(eventRx, sf);
        ui.post(clock);
        running = true;
    }

    private void buildIsland() {
        island = buildView();
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                dp(156), WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = dp(6);
        try {
            wm.addView(island, lp);
        } catch (Throwable t) {
            running = false;
            stopSelf();
        }
    }

    private View buildView() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xF0121620, 0xF0070A10});
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), 0x33FFFFFF);
        card.setBackground(bg);
        card.setElevation(dp(8));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(this);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(0xFF22D3EE);
        dot.setBackground(dotBg);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(7), dp(7));
        dlp.rightMargin = dp(8);
        row.addView(dot, dlp);

        timeView = new TextView(this);
        timeView.setTextColor(Color.WHITE);
        timeView.setTextSize(13);
        timeView.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(timeView);

        View gap = new View(this);
        row.addView(gap, new LinearLayout.LayoutParams(0, 1, 1));

        batteryView = new TextView(this);
        batteryView.setTextColor(0xFF9FE8FF);
        batteryView.setTextSize(11);
        row.addView(batteryView);

        row.setOnClickListener(v -> toggle());
        card.addView(row);

        detailBox = new LinearLayout(this);
        detailBox.setOrientation(LinearLayout.VERTICAL);
        detailBox.setVisibility(View.GONE);
        detailBox.setPadding(0, dp(8), 0, dp(2));

        dateView = new TextView(this);
        dateView.setTextColor(0xFFBFD4E8);
        dateView.setTextSize(11);
        detailBox.addView(dateView);

        notifyView = new TextView(this);
        notifyView.setTextColor(0xFFFFD166);
        notifyView.setTextSize(10);
        notifyView.setPadding(0, dp(2), 0, dp(2));
        detailBox.addView(notifyView);

        detailView = new TextView(this);
        detailView.setTextColor(0xFF7E93AA);
        detailView.setTextSize(10);
        detailView.setPadding(0, dp(2), 0, dp(8));
        detailBox.addView(detailView);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        TextView open = pill("打开工具箱", 0xFF2563EB);
        open.setOnClickListener(v -> {
            Intent it = new Intent(this, MainActivity.class);
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { startActivity(it); } catch (Throwable ignored) {}
            toggle();
        });
        TextView close = pill("关闭灵动岛", 0x33FFFFFF);
        close.setOnClickListener(v -> stopSelf());
        btns.addView(open);
        btns.addView(close);
        detailBox.addView(btns);

        card.addView(detailBox);
        refreshTime();
        refreshBattery();
        return card;
    }

    private TextView pill(String text, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(10);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(10), dp(5), dp(10), dp(5));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(10));
        g.setColor(color);
        tv.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.rightMargin = dp(6);
        tv.setLayoutParams(lp);
        return tv;
    }

    private void toggle() {
        expanded = !expanded;
        detailBox.setVisibility(expanded ? View.VISIBLE : View.GONE);
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) island.getLayoutParams();
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        lp.width = dp(expanded ? sp.getInt("exp_w", 272) : sp.getInt("cap_w", 156));
        try { wm.updateViewLayout(island, lp); } catch (Throwable ignored) {}
        applyVisibility();
    }

    private void refreshTime() {
        if (timeView == null) return;
        timeView.setText(new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date()));
        if (dateView != null) {
            dateView.setText(new SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(new Date()));
        }
    }

    private void refreshBattery() {
        if (batteryView == null) return;
        String s = (battery >= 0 ? battery + "%" : "--");
        if (chargeState == BatteryManager.BATTERY_STATUS_CHARGING
                || chargeState == BatteryManager.BATTERY_STATUS_FULL) s += " ⚡";
        batteryView.setText(s);
        if (detailView != null) {
            detailView.setText((chargeState == BatteryManager.BATTERY_STATUS_CHARGING
                    ? "正在充电 · " : "电池 ") + (battery >= 0 ? battery + "%" : "未知")
                    + " · 点击胶囊收起");
        }
    }

    private void applyVisibility() {
        if (island == null) return;
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean hide = sp.getBoolean(KEY_HIDE_LAND, true);
        boolean land = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        island.setVisibility(hide && land && !expanded ? View.GONE : View.VISIBLE);
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        landscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE;
        applyVisibility();
    }

    @Override public void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        try { unregisterReceiver(batteryRx); } catch (Throwable ignored) {}
        try { unregisterReceiver(islandRx); } catch (Throwable ignored) {}
        try { unregisterReceiver(eventRx); } catch (Throwable ignored) {}
        try { if (island != null && wm != null) wm.removeView(island); } catch (Throwable ignored) {}
        island = null;
        running = false;
        super.onDestroy();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
