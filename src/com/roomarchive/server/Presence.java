package com.roomarchive.server;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** 在线心跳：前台使用时每 60 秒发一条，后台页实时统计在线人数 */
public class Presence {

    static final String TOPIC = "ik_online_3115703202_9f4c";
    private static final String PREF = "ik_presence";

    public static void ping(Context c) {
        try {
            long last = c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong("last", 0);
            long now = System.currentTimeMillis();
            if (now - last < 55000) return;
            c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putLong("last", now).apply();
        } catch (Throwable ignored) {}
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            try {
                String id = deviceId(app);
                JSONObject o = new JSONObject();
                o.put("id", id);
                o.put("model", Build.BRAND + " " + Build.MODEL);
                o.put("v", app.getPackageManager().getPackageInfo(app.getPackageName(), 0).versionName);
                o.put("t", System.currentTimeMillis());
                HttpURLConnection conn = (HttpURLConnection) new URL("https://ntfy.sh/" + TOPIC).openConnection();
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                OutputStream os = conn.getOutputStream();
                os.write(o.toString().getBytes("UTF-8"));
                os.close();
                conn.getResponseCode();
                conn.disconnect();
            } catch (Throwable ignored) {}
        }).start();
    }

    static String deviceId(Context c) {
        try {
            String aid = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ANDROID_ID);
            if (aid == null) aid = "unknown";
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest((aid + "ik").getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) sb.append(String.format("%02x", h[i]));
            return sb.toString();
        } catch (Throwable t) {
            return "unknown";
        }
    }
}
