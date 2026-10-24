package com.roomarchive.server;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * IK工具箱 · 更新检查公共模块
 * 供「检查更新」页面与主界面启动自检共用：读清单、比对版本、下载、安装。
 */
public class UpdateChecker {

    /** 检查结果（error 非空表示失败） */
    public static class Result {
        public String version = "";
        public int code = 0;
        public String apkUrl = "";
        public String note = "";
        public String error = null;
        /** true 表示来自内置兜底信息（官网不可达时） */
        public boolean fallback = false;
    }

    /** 下载进度回调（0-100；总长未知时为 -1） */
    public interface Progress {
        void onProgress(int percent);
    }

    /** 官网 OTA 地址（GitHub Pages 官网，无水印；国内可达性兜底在 App 内切换逻辑） */
    public static final String OFFICIAL_URL =
            "https://3115703202.github.io/ik-tools/";

    /**
     * 内置兜底更新信息：官网（github.io）在国内偶尔不稳定，检查失败时自动降级到
     * 本组常量，保证用户始终能看到最新版本与可下载的国内直链。
     * 每次发版必须与官网 OTA 标记同步更新。
     */
    public static final String FALLBACK_VERSION = "v4.0.9";
    public static final int FALLBACK_CODE = 113;
    public static final String FALLBACK_URL = "https://aka.doubaocdn.com/s/D0NV7kAnaf";
    public static final String FALLBACK_NOTE = "官网暂不可达，已自动切换内置更新信息；下载走国内直链";

    /** 判断是否为官网地址（只有官网失败时才允许降级到内置信息） */
    private static boolean isOfficial(String url) {
        return url != null && (url.contains("github.io") || url.contains("ik-tools"));
    }

    /** 构造一个“内置兜底”结果 */
    static Result fallbackResult() {
        Result r = new Result();
        r.version = FALLBACK_VERSION;
        r.code = FALLBACK_CODE;
        r.apkUrl = FALLBACK_URL;
        r.note = FALLBACK_NOTE;
        r.fallback = true;
        return r;
    }

    /** 从 SharedPreferences 取用户配置的清单地址；未配置给官网 OTA（也可填局域网 ik-update.json） */
    public static String defaultUrl(Context c) {
        SharedPreferences p = c.getSharedPreferences("ik_prefs", Context.MODE_PRIVATE);
        String saved = p.getString("ota_url", "");
        if (saved != null) {
            String s = saved.trim().toLowerCase();
            // 旧版误存的本机地址（127.0.0.1/localhost）迁移为官网，避免手机连不上自己
            if (s.startsWith("http://127.") || s.startsWith("http://localhost")
                    || s.startsWith("https://127.") || s.startsWith("https://localhost")) {
                p.edit().remove("ota_url").apply();
                return OFFICIAL_URL;
            }
            if (!saved.trim().isEmpty()) return saved.trim();
        }
        return OFFICIAL_URL;
    }

    /** 官网页面内嵌的 OTA 标记：IK-OTA:VER=v3.0.2|CODE=100|URL=直链 */
    private static final java.util.regex.Pattern OTA_MARK =
            java.util.regex.Pattern.compile(
                    "IK-OTA:VER=(v?[0-9.]+)\\|CODE=(\\d+)\\|URL=(\\S+)");

    /** 阻塞式检查（需在子线程调用）：GET 地址 → 解析 → 拼接相对 apkUrl */
    public static Result check(String url, String myVersion) {
        Result r = new Result();
        try {
            URL u = new URL(url);
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(12000);
            c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent", "IKToolbox/" + myVersion);
            c.connect();
            int code = c.getResponseCode();
            if (code != 200) {
                c.disconnect();
                // 官网不可达 → 自动降级到内置更新信息，保证用户仍能看到新版本
                if (isOfficial(url)) return fallbackResult();
                r.error = "服务器返回 " + code;
                return r;
            }
            InputStream is = c.getInputStream();
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            is.close();
            c.disconnect();
            String body = new String(bos.toByteArray(), "UTF-8");

            // 模式一：官网 HTML 内嵌 OTA 标记（机器可读，随每次发版更新）
            java.util.regex.Matcher m = OTA_MARK.matcher(body);
            if (m.find()) {
                r.version = m.group(1);
                r.code = Integer.parseInt(m.group(2));
                r.apkUrl = m.group(3);
                r.note = "官网发布的新版本";
                return r;
            }

            // 模式二：局域网/自建 JSON 清单（ik-update.json）
            JSONObject jo = new JSONObject(body);
            r.code = jo.optInt("versionCode", 0);
            r.version = jo.optString("version", "");
            r.note = jo.optString("note", "");
            String apk = jo.optString("apkUrl", "");
            if (!apk.startsWith("http") && apk.length() > 0) {
                // 相对路径 → 与清单地址同一主机，部署只需改一个字段
                int slash = url.indexOf("/", 8);
                String base = slash > 0 ? url.substring(0, slash) : url;
                apk = base + "/" + (apk.startsWith("/") ? apk.substring(1) : apk);
            }
            r.apkUrl = apk;
            if (r.code <= 0 || r.apkUrl.length() == 0) {
                r.error = "清单格式不对（缺 versionCode 或 apkUrl）";
            }
            return r;
        } catch (Exception e) {
            // 官网网络异常/被劫持 → 自动降级到内置更新信息；自定义地址失败仍如实报错
            if (isOfficial(url)) return fallbackResult();
            r.error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return r;
        }
    }

    /** 检查后的下载地址选择：官网不可达时优先内置国内直链 */
    public static String effectiveApkUrl(Result r) {
        if (r != null && r.fallback && (r.apkUrl == null || r.apkUrl.length() == 0)) {
            return FALLBACK_URL;
        }
        return r == null ? FALLBACK_URL : r.apkUrl;
    }

    /** 阻塞式下载（需在子线程调用）：存到下载目录，返回 APK 文件；失败返回 null。
     *  官网/GitHub 下载失败时自动改用内置国内直链重试一次。 */
    public static File download(Context ctx, String apkUrl, String myVersion, Progress progress) {
        File f = doDownload(ctx, apkUrl, myVersion, progress);
        if (f == null && apkUrl != null && !apkUrl.equals(FALLBACK_URL)) {
            // 主下载地址失败（GitHub 被墙/超时）→ 自动切国内直链
            f = doDownload(ctx, FALLBACK_URL, myVersion, progress);
        }
        return f;
    }

    private static File doDownload(Context ctx, String apkUrl, String myVersion, Progress progress) {
        File dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) dir = ctx.getFilesDir();
        if (!dir.exists()) dir.mkdirs();
        File out = new File(dir, "ik_toolbox_update.apk");
        try {
            URL u = new URL(apkUrl);
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", "IKToolbox/" + myVersion);
            c.connect();
            int code = c.getResponseCode();
            if (code != 200) {
                c.disconnect();
                return null;
            }
            long total = c.getContentLength();
            InputStream is = c.getInputStream();
            FileOutputStream fos = new FileOutputStream(out);
            byte[] buf = new byte[8192];
            int n;
            long done = 0;
            while ((n = is.read(buf)) > 0) {
                fos.write(buf, 0, n);
                done += n;
                if (progress != null) {
                    if (total > 0) progress.onProgress((int) (done * 100 / total));
                    else progress.onProgress(-1);
                }
            }
            fos.flush();
            fos.close();
            is.close();
            c.disconnect();
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    /** 安装 APK（走 App 内置文件提供者 content://，Android 7+ 安全） */
    public static void install(Context ctx, File apk) {
        try {
            Uri u = Uri.parse("content://com.roomarchive.server.files/dl/" + apk.getName());
            ctx.grantUriPermission(ctx.getPackageName(), u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent it = new Intent(Intent.ACTION_VIEW);
            it.setDataAndType(u, "application/vnd.android.package-archive");
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            ctx.startActivity(it);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
