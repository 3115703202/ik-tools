package com.roomarchive.server;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/** 天气：Open-Meteo 免费API，默认茂名 */
public class WeatherActivity extends Activity {

    private TextView info;
    private final Handler h = new Handler(Looper.getMainLooper());
    // 茂名坐标
    private static final double LAT = 21.66, LON = 110.93;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        sv.setBackground(Tools.bgWithGlow(this));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(20));
        sv.addView(root);

        root.addView(Tools.pageTitle(this, "天气"));

        info = new TextView(this);
        info.setText("加载中…");
        info.setTextSize(14);
        info.setTextColor(Tools.textColor(this));
        info.setPadding(dp(16), dp(16), dp(16), dp(16));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Tools.cardColor(this));
        bg.setCornerRadius(dp(16));
        info.setBackground(bg);
        root.addView(info);

        setContentView(sv);
        loadWeather();
    }

    private void loadWeather() {
        new Thread(() -> {
            String result;
            try {
                String api = String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min,weather_code&timezone=Asia/Shanghai&forecast_days=3",
                        LAT, LON);
                HttpURLConnection c = (HttpURLConnection) new URL(api).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                result = parse(sb.toString());
            } catch (Exception e) {
                result = "加载失败：" + e.getMessage();
            }
            final String r = result;
            h.post(() -> info.setText(r));
        }).start();
    }

    private String parse(String json) throws Exception {
        JSONObject jo = new JSONObject(json);
        JSONObject cur = jo.getJSONObject("current");
        double temp = cur.getDouble("temperature_2m");
        double feels = cur.getDouble("apparent_temperature");
        int hum = cur.getInt("relative_humidity_2m");
        double wind = cur.getDouble("wind_speed_10m");
        int wcode = cur.getInt("weather_code");

        JSONObject daily = jo.getJSONObject("daily");
        JSONArray tmax = daily.getJSONArray("temperature_2m_max");
        JSONArray tmin = daily.getJSONArray("temperature_2m_min");
        JSONArray times = daily.getJSONArray("time");

        StringBuilder sb = new StringBuilder();
        sb.append("📍 茂名\n");
        sb.append(String.format("🌡️ 当前：%.0f°C（体感 %.0f°C）\n", temp, feels));
        sb.append(String.format("💧 湿度：%d%%\n", hum));
        sb.append(String.format("💨 风速：%.0f km/h\n", wind));
        sb.append("☁️ 天气：" + wmo(wcode) + "\n\n");
        sb.append("未来3天：\n");
        for (int i = 0; i < times.length(); i++) {
            sb.append(String.format("  %s  %.0f°~%.0f°  %s\n",
                    times.getString(i), tmin.getDouble(i), tmax.getDouble(i), wmo(daily.getJSONArray("weather_code").getInt(i))));
        }
        return sb.toString();
    }

    private String wmo(int code) {
        if (code == 0) return "晴";
        if (code <= 3) return "多云";
        if (code <= 48) return "雾";
        if (code <= 67) return "雨";
        if (code <= 77) return "雪";
        if (code <= 82) return "阵雨";
        if (code <= 99) return "雷暴";
        return "未知";
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
}
