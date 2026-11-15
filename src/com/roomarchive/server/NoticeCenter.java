package com.roomarchive.server;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/** 公告中心：后台推一条到官网 notice.json，所有装了 App 的人下次打开就能收到 */
public class NoticeCenter {

    static final String URL = "https://3115703202.github.io/ik-tools/notice.json";
    private static final String PREF = "ik_notice";

    public static JSONObject latest(Context c) {
        String raw = c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("json", "");
        if (raw == null || raw.isEmpty()) return null;
        try { return new JSONObject(raw); } catch (Throwable t) { return null; }
    }

    public static void save(Context c, String json) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("json", json).apply();
    }

    public static void markShown(Context c) {
        JSONObject o = latest(c);
        long id = o == null ? 0 : o.optLong("id", 0);
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putLong("seen", id).apply();
    }

    public static boolean shouldShow(Context c) {
        JSONObject o = latest(c);
        if (o == null) return false;
        long id = o.optLong("id", 0);
        long seen = c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong("seen", 0);
        return id > seen;
    }

    public static String title(Context c) {
        JSONObject o = latest(c);
        return o == null ? "" : o.optString("title", "");
    }

    public static String body(Context c) {
        JSONObject o = latest(c);
        return o == null ? "" : o.optString("content", "");
    }

    public static String time(Context c) {
        JSONObject o = latest(c);
        return o == null ? "" : o.optString("time", "");
    }

    /** 后台拉一次最新公告，缓存到本地 */
    public static void fetch(Context c, Runnable done) {
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(URL + "?t=" + System.currentTimeMillis()).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("User-Agent", "IK-Tools");
                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();
                    String raw = sb.toString().trim();
                    if (raw.startsWith("{")) {
                        JSONObject o = new JSONObject(raw);
                        if (o.optString("title", "").length() > 0) save(c, o.toString());
                    }
                }
                conn.disconnect();
            } catch (Throwable ignored) {
            }
            if (done != null) done.run();
        }).start();
    }
}
