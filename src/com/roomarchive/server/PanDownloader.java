package com.roomarchive.server;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 网盘文件下载器：Cookie / Referer / UA 全带；支持 Range 分片多连接 + 阶梯提速
 * （4 连接起步，每隔 2 秒加 2 条，最高 16 条，速度起来就停止加），单连接服务器自动回退。
 */
public class PanDownloader {

    public interface Listener {
        void onStart(String name, long total);
        void onProgress(String name, int percent, long done, long total);
        default void onSpeed(double mbps, int conns) {}
        void onDone(File file);
        void onError(String msg);
    }

    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final String UA =
            "Mozilla/5.0 (Linux; Android 12; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/122.0.0.0 Mobile Safari/537.36";
    private static final int MAX_CONN = 16;
    private static final int START_CONN = 4;
    private static final long CHUNK = 1024L * 1024L;

    public static void start(final Context ctx, final String urlStr, final String referer,
                             final String suggestedName, final Listener l) {
        new Thread(() -> {
            File dest = null;
            try {
                String url = urlStr.replace(" ", "%20");
                long total = 0;
                boolean ranges = false;
                String name = suggestedName;
                String ctype = null;
                HttpURLConnection probe = open(url, referer);
                try {
                    probe.setRequestProperty("Range", "bytes=0-0");
                    probe.connect();
                    int pc = probe.getResponseCode();
                    ctype = probe.getContentType();
                    String cr = probe.getHeaderField("Content-Range");
                    if (pc == 206 && cr != null && cr.contains("/")) {
                        ranges = true;
                        try {
                            total = Long.parseLong(cr.substring(cr.lastIndexOf('/') + 1).trim());
                        } catch (Throwable ignored) {}
                    } else if (pc == 200) {
                        total = probe.getContentLengthLong();
                    } else {
                        throw new Exception("服务器返回 HTTP " + pc);
                    }
                    name = pickName(url, probe.getHeaderField("Content-Disposition"), name);
                } finally {
                    try { probe.disconnect(); } catch (Throwable ignored) {}
                }
                if (name == null || name.trim().length() == 0) {
                    name = "网盘文件_" + System.currentTimeMillis();
                }
                if (ctype != null && ctype.toLowerCase().contains("text/html")
                        && (total <= 0 || total < 300 * 1024)) {
                    throw new Exception("返回的是网页不是文件（可能需要在该页面登录，或链接已失效）");
                }
                dest = uniqueFile(ctx, name);
                final String fname = dest.getName();
                final long ftotal = total;
                UI.post(() -> l.onStart(fname, ftotal));

                if (ranges && total > 4 * 1024 * 1024) {
                    segmented(url, referer, dest, total, fname, l);
                } else {
                    single(url, referer, dest, total, fname, l);
                }
            } catch (Throwable t) {
                if (dest != null && dest.length() == 0) dest.delete();
                String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                UI.post(() -> l.onError(msg));
            }
        }).start();
    }

    private static HttpURLConnection open(String url, String referer) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(60000);
        conn.setRequestMethod("GET");
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", UA);
        conn.setRequestProperty("Accept", "*/*");
        if (referer != null && referer.length() > 0) {
            conn.setRequestProperty("Referer", referer);
            try {
                URL u = new URL(referer);
                conn.setRequestProperty("Origin", u.getProtocol() + "://" + u.getHost());
            } catch (Exception ignored) {}
        }
        try {
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null && cookie.length() > 0) conn.setRequestProperty("Cookie", cookie);
        } catch (Throwable ignored) {}
        return conn;
    }

    /** 单连接兜底 */
    private static void single(String url, String referer, File dest, long total,
                               String fname, Listener l) throws Exception {
        HttpURLConnection conn = open(url, referer);
        try {
            conn.connect();
            int code = conn.getResponseCode();
            if (code != 200 && code != 206) throw new Exception("服务器返回 HTTP " + code);
            if (total <= 0) total = conn.getContentLengthLong();
            InputStream is = conn.getInputStream();
            FileOutputStream fos = new FileOutputStream(dest);
            byte[] buf = new byte[16 * 1024];
            int r;
            long done = 0, last = 0;
            long t0 = System.currentTimeMillis();
            try {
                while ((r = is.read(buf)) > 0) {
                    fos.write(buf, 0, r);
                    done += r;
                    long now = System.currentTimeMillis();
                    if (now - last > 250) {
                        last = now;
                        final int pv = total > 0 ? (int) (done * 100 / total) : -1;
                        final long d = done, t = total;
                        final double mbps = d * 8.0 / Math.max(1, now - t0) / 1000.0;
                        UI.post(() -> { l.onProgress(fname, pv, d, t); l.onSpeed(mbps, 1); });
                    }
                }
                fos.flush();
            } finally {
                try { fos.close(); } catch (Exception ignored) {}
                try { is.close(); } catch (Exception ignored) {}
            }
            if (dest.length() == 0) {
                dest.delete();
                throw new Exception("下载到 0 字节，可能需要登录或链接已失效");
            }
            UI.post(() -> l.onDone(dest));
        } finally {
            try { conn.disconnect(); } catch (Throwable ignored) {}
        }
    }

    /** 分片多连接 + 阶梯提速 */
    private static void segmented(String url, String referer, File dest, long total,
                                  String fname, Listener l) throws Exception {
        RandomAccessFile raf = new RandomAccessFile(dest, "rw");
        raf.setLength(total);
        raf.close();

        final int nChunks = (int) ((total + CHUNK - 1) / CHUNK);
        final AtomicInteger next = new AtomicInteger(0);
        final AtomicLong done = new AtomicLong(0);
        final AtomicInteger conns = new AtomicInteger(0);
        final AtomicBoolean stop = new AtomicBoolean(false);
        final AtomicInteger errors = new AtomicInteger(0);
        final String firstError = null;
        final ThreadPoolExecutor pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(MAX_CONN);

        Runnable worker = () -> {
            while (!stop.get()) {
                int idx = next.getAndIncrement();
                if (idx >= nChunks) break;
                long start = (long) idx * CHUNK;
                long end = Math.min(total - 1, start + CHUNK - 1);
                int tries = 0;
                boolean ok = false;
                while (tries < 3 && !ok && !stop.get()) {
                    tries++;
                    try {
                        HttpURLConnection c = open(url, referer);
                        c.setRequestProperty("Range", "bytes=" + start + "-" + end);
                        c.connect();
                        int code = c.getResponseCode();
                        if (code != 206 && code != 200) throw new Exception("HTTP " + code);
                        if (code == 200 && start > 0) throw new Exception("服务器忽略分段请求");
                        InputStream is = c.getInputStream();
                        RandomAccessFile f = new RandomAccessFile(dest, "rw");
                        FileChannel ch = f.getChannel();
                        byte[] buf = new byte[8192];
                        int r;
                        long pos = start;
                        try {
                            while ((r = is.read(buf)) > 0) {
                                ByteBuffer bb = ByteBuffer.wrap(buf, 0, r);
                                while (bb.hasRemaining()) pos += ch.write(bb, pos);
                                done.addAndGet(r);
                                if (stop.get()) break;
                            }
                        } finally {
                            try { ch.close(); } catch (Throwable ignored) {}
                            try { f.close(); } catch (Throwable ignored) {}
                            try { is.close(); } catch (Throwable ignored) {}
                            try { c.disconnect(); } catch (Throwable ignored) {}
                        }
                        ok = pos > start;
                    } catch (Throwable e) {
                        if (tries >= 3) errors.incrementAndGet();
                    }
                }
                if (!ok && !stop.get()) errors.incrementAndGet();
            }
        };

        // 起步 4 条连接
        for (int i = 0; i < START_CONN; i++) pool.execute(worker);

        final long t0 = System.currentTimeMillis();
        final long[] lastBytes = {0};
        final long[] stall = {0};
        final long[] lastTime = {t0};
        final ScheduledExecutorService tick = Executors.newSingleThreadScheduledExecutor();
        tick.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            long d = done.get();
            double mbps = (d - lastBytes[0]) * 8.0 / Math.max(1, now - lastTime[0]) / 1000.0;
            lastBytes[0] = d;
            lastTime[0] = now;
            int cs = conns.get();
            if (!stop.get() && cs < MAX_CONN && mbps < 12.0 && next.get() < nChunks) {
                // 阶梯提速：每隔 2 秒加 2 条
                for (int i = 0; i < 2 && cs + i < MAX_CONN; i++) {
                    conns.incrementAndGet();
                    pool.execute(worker);
                }
            }
            final int pv = total > 0 ? (int) (d * 100 / total) : -1;
            final double m = mbps;
            final int cc = conns.get();
            UI.post(() -> { l.onProgress(fname, pv, d, total); l.onSpeed(m, Math.max(1, cc)); });

            if (d >= total) {
                stop.set(true);
                tick.shutdown();
                pool.shutdown();
                UI.post(() -> l.onDone(dest));
            } else if (mbps <= 0.01) {
                stall[0]++;
                if (stall[0] >= 5) {
                    stop.set(true);
                    tick.shutdown();
                    pool.shutdownNow();
                    UI.post(() -> l.onError("下载卡住了（服务端限速或分片被拒），已保留已下载部分"));
                }
            } else {
                stall[0] = 0;
            }
        }, 0, 2, TimeUnit.SECONDS);
        conns.set(START_CONN);
    }

    private static String pickName(String url, String cd, String suggested) {
        if (suggested != null && suggested.trim().length() > 0) return suggested;
        try {
            if (cd != null) {
                String[] parts = cd.split(";");
                for (String p : parts) {
                    if (p.trim().toLowerCase().startsWith("filename=")) {
                        String n = p.substring(p.indexOf('=') + 1).trim().replace("\"", "");
                        return java.net.URLDecoder.decode(n, "UTF-8");
                    }
                }
            }
            String path = new URL(url).getPath();
            String n = path.substring(path.lastIndexOf('/') + 1);
            if (n.length() > 0) return java.net.URLDecoder.decode(n, "UTF-8");
        } catch (Throwable ignored) {}
        return "网盘文件_" + System.currentTimeMillis();
    }

    private static File uniqueFile(Context ctx, String name) {
        File dir = FileStore.outDir(ctx);
        File f = new File(dir, name);
        if (!f.exists()) return f;
        String base = name, ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) { base = name.substring(0, dot); ext = name.substring(dot); }
        for (int i = 1; i < 999; i++) {
            File n = new File(dir, base + "_" + i + ext);
            if (!n.exists()) return n;
        }
        return f;
    }
}
