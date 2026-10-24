package com.roomarchive.server;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 系统信息：电池/WiFi/网速/网络Ping */
public class SystemInfoActivity extends Activity {

    private TextView batteryView, wifiView, netView, pingView;
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean testing = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        sv.setBackground(Tools.bgWithGlow(this));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(20));
        sv.addView(root);

        root.addView(Tools.pageTitle(this, "系统信息"));

        batteryView = infoCard(root, "电池", "读取中…");
        wifiView = infoCard(root, "WiFi / 网络", "读取中…");
        netView = infoCard(root, "网速测试", "点下方按钮开始");
        pingView = infoCard(root, "Ping", "点下方按钮测试");

        // 按钮
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        android.widget.Button btnNet = smallBtn("测网速");
        btnNet.setOnClickListener(v -> testSpeed());
        row.addView(btnNet, new LinearLayout.LayoutParams(0, dp(44), 1f));

        android.widget.Button btnPing = smallBtn("Ping 百度");
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        pp.setMarginStart(dp(10));
        btnPing.setOnClickListener(v -> testPing());
        row.addView(btnPing, pp);

        root.addView(row);
        setContentView(sv);

        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        updateWifi();
    }

    private TextView infoCard(LinearLayout root, String title, String val) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(Tools.liquidGlassCard(this, 16));
        Tools.setElevation(card, 6);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(13);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setTextColor(Tools.accentColor(this));
        card.addView(t);
        TextView v = new TextView(this);
        v.setText(val);
        v.setTextSize(12);
        v.setTextColor(Tools.textColor(this));
        v.setPadding(0, dp(6), 0, 0);
        card.addView(v);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);
        root.addView(card);
        return v;
    }

    private android.widget.Button smallBtn(String text) {
        android.widget.Button b = new android.widget.Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(Color.WHITE);
        b.setBackground(Tools.pillButton(this, 42, true));
        return b;
    }

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            int pct = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            int health = i.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
            float temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f;
            int volt = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
            String charge = status == BatteryManager.BATTERY_STATUS_CHARGING ? "充电中"
                    : status == BatteryManager.BATTERY_STATUS_DISCHARGING ? "放电中"
                    : status == BatteryManager.BATTERY_STATUS_FULL ? "已充满" : "未知";
            batteryView.setText(String.format(Locale.getDefault(),
                    "电量：%d%%\n温度：%.1f°C\n电压：%dmV\n状态：%s",
                    pct * 100 / scale, temp, volt, charge));
        }
    };

    private void updateWifi() {
        try {
            WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
            WifiInfo wi = wm.getConnectionInfo();
            String ip = Formatter.formatIpAddress(wi.getIpAddress());
            int rssi = wi.getRssi();
            int speed = wi.getLinkSpeed();
            wifiView.setText(String.format(Locale.getDefault(),
                    "IP：%s\n信号：%d dBm\n链路速率：%d Mbps",
                    ip, rssi, speed));
        } catch (Exception e) {
            wifiView.setText("WiFi 不可用");
        }
    }

    private void testSpeed() {
        if (testing) return;
        testing = true;
        netView.setText("测试中…");
        new Thread(() -> {
            try {
                URL u = new URL("https://www.baidu.com");
                HttpURLConnection c = (HttpURLConnection) u.openConnection();
                c.setConnectTimeout(5000);
                long t0 = System.currentTimeMillis();
                c.connect();
                int code = c.getResponseCode();
                long dt = System.currentTimeMillis() - t0;
                c.disconnect();
                String result = code == 200
                        ? String.format(Locale.getDefault(), "连接成功 · %dms\n延迟：%.0fms", dt, (float) dt)
                        : "HTTP " + code;
                h.post(() -> { netView.setText(result); testing = false; });
            } catch (Exception e) {
                h.post(() -> { netView.setText("失败：" + e.getMessage()); testing = false; });
            }
        }).start();
    }

    private void testPing() {
        pingView.setText("Ping 中…");
        new Thread(() -> {
            try {
                long t0 = System.currentTimeMillis();
                InetAddress addr = InetAddress.getByName("www.baidu.com");
                long dt = System.currentTimeMillis() - t0;
                boolean ok = addr.isReachable(3000);
                String result = ok
                        ? String.format(Locale.getDefault(), "可达 · %s · %dms", addr.getHostAddress(), dt)
                        : "超时";
                h.post(() -> pingView.setText(result));
            } catch (Exception e) {
                h.post(() -> pingView.setText("失败：" + e.getMessage()));
            }
        }).start();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(batteryReceiver); } catch (Exception ignored) {}
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
}
