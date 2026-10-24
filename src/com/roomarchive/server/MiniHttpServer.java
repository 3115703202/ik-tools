package com.roomarchive.server;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPOutputStream;

/**
 * 极简静态文件 HTTP 服务器（纯 Java，零依赖）。
 * 性能优化：index.html 启动即预加载 + 预压缩 gzip 缓存，响应走内存、大发送缓冲，局域网加载显著提速。
 * 内容优先级：应用私有目录 files/webroot/ 下用户放入的文件 > 内置 assets/index.html。
 */
public class MiniHttpServer {

    private final Context context;
    private final int port;
    private ServerSocket serverSocket;
    private ExecutorService pool;
    private volatile boolean running = false;
    private Thread acceptThread;

    // ---- 缓存 ----
    private byte[] cachedRaw;    // index.html 原始字节
    private byte[] cachedGzip;   // index.html gzip 压缩字节（响应 Accept-Encoding 用）
    private long cachedMtime = Long.MIN_VALUE; // webroot/index.html 的 mtime（检测用户换文件）

    public MiniHttpServer(Context context, int port) {
        this.context = context.getApplicationContext();
        this.port = port;
    }

    public int getPort() {
        return port;
    }

    /** 返回 true 表示已在运行 */
    public boolean isRunning() {
        return running;
    }

    public boolean start() throws IOException {
        if (running) return true;
        loadCache();
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(port));
        pool = Executors.newCachedThreadPool();
        running = true;
        acceptThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (running) {
                    try {
                        final Socket sock = serverSocket.accept();
                        pool.execute(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    handle(sock);
                                } catch (Exception ignored) {
                                } finally {
                                    try { sock.close(); } catch (Exception ignored) {}
                                }
                            }
                        });
                    } catch (Exception e) {
                        if (running) {
                            // 继续等待下一个连接
                        }
                    }
                }
            }
        }, "http-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        return true;
    }

    public void stop() {
        running = false;
        if (serverSocket != null) {
            try { serverSocket.close(); } catch (Exception ignored) {}
            serverSocket = null;
        }
        if (pool != null) {
            pool.shutdownNow();
            pool = null;
        }
    }

    // ---------- 缓存加载 ----------

    /** 预加载 index.html（assets 或用户 webroot），并预压缩 gzip */
    private void loadCache() {
        byte[] raw = null;
        File web = new File(context.getFilesDir(), "webroot/index.html");
        if (web.isFile()) {
            raw = readFile(web);
            cachedMtime = web.lastModified();
        }
        if (raw == null) {
            raw = readAsset("index.html");
            cachedMtime = -1;
        }
        cachedRaw = raw;
        cachedGzip = (raw == null) ? null : gzip(raw);
    }

    /** 每次请求前轻量检查：用户更换了 webroot 文件则刷新缓存 */
    private void ensureCache() {
        File web = new File(context.getFilesDir(), "webroot/index.html");
        long mt = web.isFile() ? web.lastModified() : -1;
        if (mt != cachedMtime) loadCache();
    }

    // ---------- 请求处理 ----------

    private void handle(Socket sock) throws IOException {
        sock.setSoTimeout(30000);
        // 大发送缓冲：6.5MB 单文件也只需少量大块写出
        try { sock.setSendBufferSize(1024 * 1024); } catch (Exception ignored) {}
        InputStream in = new BufferedInputStream(sock.getInputStream(), 64 * 1024);
        OutputStream out = new BufferedOutputStream(sock.getOutputStream(), 64 * 1024);

        // 读请求行（GET /path HTTP/1.1）
        String requestLine = readLine(in);
        if (requestLine == null || requestLine.isEmpty()) return;

        String[] parts = requestLine.split(" ");
        if (parts.length < 2) return;
        String method = parts[0].toUpperCase(Locale.US);
        String rawPath = parts[1];
        if (!method.equals("GET") && !method.equals("HEAD")) {
            writeResponse(out, 405, "text/plain; charset=utf-8", "Method Not Allowed".getBytes("UTF-8"), false);
            return;
        }

        // 读取并丢弃剩余请求头；顺带识别 Accept-Encoding
        boolean wantGzip = false;
        String line;
        long headerBytes = 0;
        while ((line = readLine(in)) != null && !line.isEmpty() && headerBytes < 16384) {
            headerBytes += line.length() + 2;
            if (line.length() > 16 && line.substring(0, 15).equalsIgnoreCase("Accept-Encoding:")) {
                wantGzip = line.toLowerCase(Locale.US).contains("gzip");
            }
        }

        // 解析路径
        String path;
        try {
            path = URLDecoder.decode(rawPath, "UTF-8");
        } catch (Exception e) {
            path = rawPath;
        }
        if (path.contains("?")) path = path.substring(0, path.indexOf('?'));
        if (path.endsWith("/") || path.isEmpty()) path = "/index.html";

        // 安全：拒绝路径穿越
        if (path.contains("..") || path.startsWith("/../")) {
            writeResponse(out, 403, "text/plain; charset=utf-8", "Forbidden".getBytes("UTF-8"), false);
            return;
        }

        byte[] data = null;
        boolean gzipped = false;
        String mime = mimeOf(path);

        if (path.equals("/index.html")) {
            // 首页走内存缓存（gzip 优先）
            ensureCache();
            if (cachedRaw != null) {
                if (wantGzip && cachedGzip != null) {
                    data = cachedGzip;
                    gzipped = true;
                } else {
                    data = cachedRaw;
                }
            }
        }

        if (data == null) {
            // 其他文件：webroot 优先，其次 assets（现读）
            File f = webrootFile(path);
            if (f != null && f.isFile()) {
                data = readFile(f);
            }
            if (data == null) {
                data = readAsset(path);
            }
        }

        if (data == null) {
            byte[] msg = "404 Not Found".getBytes("UTF-8");
            writeResponse(out, 404, "text/plain; charset=utf-8", msg, false);
            return;
        }

        writeResponse(out, 200, mime, data, gzipped);
        out.flush();
    }

    private File webrootFile(String path) {
        File root = new File(context.getFilesDir(), "webroot");
        if (!root.isDirectory()) return null;
        String rel = path.startsWith("/") ? path.substring(1) : path;
        File f = new File(root, rel);
        String canonical;
        try {
            canonical = f.getCanonicalPath();
        } catch (IOException e) {
            return null;
        }
        if (!canonical.startsWith(root.getAbsolutePath())) return null;
        return f;
    }

    private byte[] readAsset(String path) {
        String rel = path.startsWith("/") ? path.substring(1) : path;
        // 只允许 assets 下已有文件，防穿越
        if (rel.contains("..")) return null;
        try {
            InputStream is = context.getAssets().open(rel);
            try {
                return readAll(is);
            } finally {
                is.close();
            }
        } catch (IOException e) {
            return null;
        }
    }

    private static byte[] readFile(File f) {
        try {
            InputStream is = new FileInputStream(f);
            try {
                return readAll(is);
            } finally {
                is.close();
            }
        } catch (IOException e) {
            return null;
        }
    }

    private static byte[] readAll(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = is.read(buf)) > 0) {
            bos.write(buf, 0, n);
            if (bos.size() > 64 * 1024 * 1024) {
                throw new IOException("文件过大");
            }
        }
        return bos.toByteArray();
    }

    /** gzip 压缩（压缩失败返回 null，调用方回退原始数据） */
    private static byte[] gzip(byte[] data) {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream(Math.max(64, data.length / 3));
            GZIPOutputStream gz = new GZIPOutputStream(bo);
            gz.write(data);
            gz.close();
            return bo.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\r') {
                    sb.setLength(sb.length() - 1);
                }
                return sb.toString();
            }
            if (sb.length() > 8192) return sb.toString();
            sb.append((char) c);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static void writeResponse(OutputStream out, int code, String mime, byte[] body, boolean gzipped) throws IOException {
        StringBuilder head = new StringBuilder();
        head.append("HTTP/1.1 ").append(code).append(" ").append(reason(code)).append("\r\n");
        head.append("Content-Type: ").append(mime).append("\r\n");
        head.append("Content-Length: ").append(body.length).append("\r\n");
        if (gzipped) head.append("Content-Encoding: gzip\r\n");
        head.append("Connection: close\r\n");
        head.append("Cache-Control: no-cache\r\n");
        head.append("Access-Control-Allow-Origin: *\r\n");
        head.append("\r\n");
        out.write(head.toString().getBytes("ISO-8859-1"));
        // 大块写出（64KB 缓冲 + 分块写）
        int off = 0;
        while (off < body.length) {
            int n = Math.min(65536, body.length - off);
            out.write(body, off, n);
            off += n;
        }
        out.flush();
    }

    private static String reason(int code) {
        switch (code) {
            case 200: return "OK";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            default: return "Error";
        }
    }

    private static String mimeOf(String path) {
        String p = path.toLowerCase(Locale.US);
        if (p.endsWith(".html") || p.endsWith(".htm")) return "text/html; charset=utf-8";
        if (p.endsWith(".css")) return "text/css; charset=utf-8";
        if (p.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (p.endsWith(".json")) return "application/json; charset=utf-8";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".gif")) return "image/gif";
        if (p.endsWith(".webp")) return "image/webp";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".ico")) return "image/x-icon";
        if (p.endsWith(".woff")) return "font/woff";
        if (p.endsWith(".woff2")) return "font/woff2";
        if (p.endsWith(".ttf")) return "font/ttf";
        if (p.endsWith(".txt")) return "text/plain; charset=utf-8";
        if (p.endsWith(".md")) return "text/markdown; charset=utf-8";
        if (p.endsWith(".xml")) return "text/xml; charset=utf-8";
        if (p.endsWith(".pdf")) return "application/pdf";
        if (p.endsWith(".mp4")) return "video/mp4";
        if (p.endsWith(".mp3")) return "audio/mpeg";
        if (p.endsWith(".zip")) return "application/zip";
        if (p.endsWith(".apk")) return "application/vnd.android.package-archive";
        return "application/octet-stream";
    }
}
