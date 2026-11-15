package com.roomarchive.server;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Locale;

/** 网盘文件下载器：带 Cookie / Referer / UA，直接落到「IK工具箱」目录，供网盘解析页就地下载 */
public class PanDownloader {

    public interface Listener {
        void onStart(String name, long total);
        void onProgress(String name, int percent, long done, long total);
        void onDone(File file);
        void onError(String msg);
    }

    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final String UA =
            "Mozilla/5.0 (Linux; Android 12; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/122.0.0.0 Mobile Safari/537.36";

    /** 启动下载（后台线程），返回目标文件；失败通过 listener 回调 */
    public static void start(final Context ctx, final String urlStr, final String referer,
                             final String suggestedName, final Listener l) {
        new Thread(() -> {
            File dest = null;
            HttpURLConnection conn = null;
            try {
                String url = urlStr.replace(" ", "%20");
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(60000);
                conn.setRequestMethod("GET");
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", UA);
                conn.setRequestProperty("Accept", "*/*");
                if (referer != null && referer.length() > 0) {
                    conn.setRequestProperty("Referer", referer);
                    try {
                        String origin = new URL(referer).getProtocol() + "://"
                                + new URL(referer).getHost();
                        conn.setRequestProperty("Origin", origin);
                    } catch (Exception ignored) {
                    }
                }
                String cookie = null;
                try {
                    cookie = CookieManager.getInstance().getCookie(url);
                } catch (Throwable ignored) {
                }
                if (cookie != null && cookie.length() > 0) {
                    conn.setRequestProperty("Cookie", cookie);
                }
                conn.connect();

                int code = conn.getResponseCode();
                if (code != 200 && code != 206) {
                    throw new Exception("服务器返回 HTTP " + code);
                }
                long total = conn.getContentLengthLong();
                String ctype = conn.getContentType();
                String name = pickName(urlStr, conn.getHeaderField("Content-Disposition"), suggestedName);
                if (name == null || name.trim().length() == 0) {
                    name = "网盘文件_" + System.currentTimeMillis();
                }
                // 网盘经常用 HTML 页当"错误响应"（需要登录 / 链接失效），别把它当文件存下来
                if (ctype != null && ctype.toLowerCase().contains("text/html")
                        && (total <= 0 || total < 300 * 1024)) {
                    throw new Exception("返回的是网页不是文件（可能需要在该页面登录，或链接已失效）");
                }
                dest = uniqueFile(ctx, name);

                final String fname = dest.getName();
                UI.post(() -> l.onStart(fname, total));

                InputStream is = conn.getInputStream();
                FileOutputStream fos = new FileOutputStream(dest);
                byte[] buf = new byte[16 * 1024];
                int r;
                long done = 0;
                long last = 0;
                try {
                    while ((r = is.read(buf)) > 0) {
                        fos.write(buf, 0, r);
                        done += r;
                        long now = System.currentTimeMillis();
                        if (now - last > 250) {
                            last = now;
                            final int pv = total > 0 ? (int) (done * 100 / total) : -1;
                            final long d = done;
                            final long t = total;
                            UI.post(() -> l.onProgress(fname, pv, d, t));
                        }
                    }
                    fos.flush();
                } finally {
                    try { fos.close(); } catch (Exception ignored) {}
                    try { is.close(); } catch (Exception ignored) {}
                }
                // 空文件基本说明被拒绝（网盘常返回 0 字节的错误页）
                if (dest.length() == 0) {
                    dest.delete();
                    throw new Exception("下载到 0 字节，可能需要登录或链接已失效");
                }
                final File f = dest;
                UI.post(() -> l.onDone(f));
            } catch (Throwable t) {
                if (dest != null && dest.length() == 0) dest.delete();
                String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                UI.post(() -> l.onError(msg));
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "pan-dl").start();
    }

    /** 从 Content-Disposition / URL 里取文件名 */
    static String pickName(String urlStr, String cd, String suggested) {
        if (cd != null) {
            try {
                String lower = cd.toLowerCase(Locale.ROOT);
                int star = lower.indexOf("filename*=");
                if (star >= 0) {
                    String v = cd.substring(star + 10).trim();
                    int semi = v.indexOf(';');
                    if (semi > 0) v = v.substring(0, semi);
                    if (v.contains("''")) v = v.substring(v.indexOf("''") + 2);
                    return safe(URLDecoder.decode(v.replace("\"", ""), "UTF-8"));
                }
                int idx = lower.indexOf("filename=");
                if (idx >= 0) {
                    String v = cd.substring(idx + 9).trim().replace("\"", "");
                    int semi = v.indexOf(';');
                    if (semi > 0) v = v.substring(0, semi);
                    return safe(URLDecoder.decode(v, "UTF-8"));
                }
            } catch (Exception ignored) {
            }
        }
        try {
            String path = new URL(urlStr).getPath();
            String last = path.substring(path.lastIndexOf('/') + 1);
            if (last.length() > 0 && last.contains(".")) {
                return safe(URLDecoder.decode(last, "UTF-8"));
            }
        } catch (Exception ignored) {
        }
        if (suggested != null && suggested.length() > 0) return safe(suggested);
        return "网盘文件_" + System.currentTimeMillis();
    }

    private static String safe(String name) {
        if (name == null || name.trim().length() == 0) return null;
        // 去掉 URL 编码残留和首尾空白，避免生成 ".apk" 之类的空名文件
        String n = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (n.length() > 100) {
            int dot = n.lastIndexOf('.');
            n = dot > 0 ? n.substring(0, Math.min(dot, 96)) + n.substring(dot) : n.substring(0, 100);
        }
        if (n.length() == 0) return null;
        return n;
    }

    private static File uniqueFile(Context ctx, String name) {
        File dir = FileStore.outDir(ctx);
        File f = new File(dir, name);
        if (!f.exists()) return f;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; i < 200; i++) {
            File nf = new File(dir, base + "(" + i + ")" + ext);
            if (!nf.exists()) return nf;
        }
        return new File(dir, base + "_" + System.currentTimeMillis() + ext);
    }
}
