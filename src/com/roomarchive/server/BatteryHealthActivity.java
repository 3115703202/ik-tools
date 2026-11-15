package com.roomarchive.server;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Locale;

/** 电池信息：容量、健康度、电流功率、温度、电压、循环次数 */
public class BatteryHealthActivity extends BaseActivity {

    private LinearLayout cards;
    private TextView summary;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Runnable tick;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Tools.bgWithGlow(this));
        root.setPadding(dp(18), dp(24), dp(18), dp(24));
        sv.addView(root);

        TextView title = Tools.pageTitle(this, "电池健康");
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        summary = new TextView(this);
        summary.setTextSize(12);
        summary.setTextColor(Tools.subTextColor(this));
        summary.setGravity(Gravity.CENTER);
        summary.setPadding(0, dp(6), 0, dp(14));
        root.addView(summary);

        cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.VERTICAL);
        root.addView(cards);

        setContentView(sv);
        tick = () -> {
            refresh();
            ui.postDelayed(tick, 2000);
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        ui.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(tick);
    }

    private void refresh() {
        IntentFilter f = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent it = registerReceiver(null, f);
        if (it == null) { summary.setText("拿不到电池状态"); return; }
        int level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int pct = scale > 0 ? (int) (level * 100.0 / scale) : -1;
        int status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int health = it.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
        int temp = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
        int volt = it.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
        String tech = it.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        long cur = 0;
        try { cur = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW); } catch (Throwable ignored) {}

        String statusName = status == BatteryManager.BATTERY_STATUS_CHARGING ? "充电中"
                : status == BatteryManager.BATTERY_STATUS_FULL ? "已充满"
                : status == BatteryManager.BATTERY_STATUS_DISCHARGING ? "放电中" : "未知";
        String healthName;
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD: healthName = "良好"; break;
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: healthName = "过热"; break;
            case BatteryManager.BATTERY_HEALTH_DEAD: healthName = "损坏"; break;
            case BatteryManager.BATTERY_HEALTH_COLD: healthName = "过冷"; break;
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: healthName = "过压"; break;
            default: healthName = "未知";
        }

        long design = readLong("/sys/class/power_supply/battery/charge_full_design");
        long full = readLong("/sys/class/power_supply/battery/charge_full");
        long cycles = readLong("/sys/class/power_supply/battery/cycle_count");
        String healthPct = "--";
        if (design > 0 && full > 0) {
            healthPct = String.format(Locale.US, "%.1f%%", full * 100.0 / design);
        }
        double watt = Math.abs(cur) / 1_000_000.0 * (volt / 1000.0);

        summary.setText(pct + "% · " + statusName + " · " + healthName);
        cards.removeAllViews();
        add("电量", pct + " %");
        add("状态", statusName);
        add("系统健康", healthName);
        add("健康度（满电/设计）", healthPct);
        add("设计容量", design > 0 ? (design / 1000) + " mAh" : "机型未开放");
        add("当前满电容量", full > 0 ? (full / 1000) + " mAh" : "机型未开放");
        add("循环次数", cycles >= 0 ? String.valueOf(cycles) : "机型未开放");
        add("电流", String.format(Locale.US, "%.0f mA", cur / 1000.0));
        add("功率", String.format(Locale.US, "%.2f W", watt));
        add("电压", String.format(Locale.US, "%.2f V", volt / 1000.0));
        add("温度", String.format(Locale.US, "%.1f ℃", temp / 10.0));
        add("电池技术", tech == null ? "--" : tech);
    }

    private void add(String name, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.setBackground(Tools.liquidGlassCard(this, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        row.setLayoutParams(lp);
        TextView n = new TextView(this);
        n.setText(name);
        n.setTextSize(13);
        n.setTextColor(Tools.subTextColor(this));
        n.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(n);
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(14);
        v.setTextColor(Tools.textColor(this));
        row.addView(v);
        cards.addView(row);
    }

    private long readLong(String path) {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            String s = r.readLine();
            return s == null ? -1 : Long.parseLong(s.trim());
        } catch (Throwable t) {
            return -1;
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
