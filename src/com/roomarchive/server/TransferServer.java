package com.roomarchive.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 极简局域网传文件服务：手机开个网页，电脑/其它手机用浏览器上传下载。
 * 只处理 GET /（列表）、GET /f?name=（下载）、POST /upload（multipart 上传）。
 */
public class TransferServer {

    public interface Listener { void onLog(String line); }

    private ServerSocket server;
    private Thread thread;
    private volatile boolean running;
    private final File dir;
    private final int port;
    private final Listener listener;

    public TransferServer(File dir, int port, Listener l) {
        this.dir = dir;
        this.port = port;
        this.listener = l;
    }

    public boolean isRunning() { return running; }

    public void start() throws IOException {
        if (running) return;
        if (!dir.exists()) dir.mkdirs();
        server = new ServerSocket(port);
        running = true;
        thread = new Thread(() -> {
            log("服务已启动，端口 " + port);
            while (running) {
                try {
                    Socket s = server.accept();
                    new Thread(() -> handle(s)).start();
                } catch (Throwable t) {
                    if (running) log("accept 异常：" + t.getMessage());
                }
            }
        }, "ik-transfer");
        thread.start();
    }

    public void stop() {
        running = false;
        try { if (server != null) server.close(); } catch (Throwable ignored) {}
        server = null;
        log("服务已停止");
    }

    private void handle(Socket s) {
        try {
            s.setSoTimeout(20000);
            InputStream in = new BufferedInputStream(s.getInputStream());
            String reqLine = readLine(in);
            if (reqLine == null) { s.close(); return; }
            String[] parts = reqLine.split(" ");
            String method = parts.length > 0 ? parts[0] : "GET";
            String path = parts.length > 1 ? parts[1] : "/";
            int len = 0;
            String contentType = "";
            while (true) {
                String h = readLine(in);
                if (h == null || h.isEmpty()) break;
                int i = h.indexOf(':');
                if (i > 0) {
                    String k = h.substring(0, i).trim().toLowerCase(Locale.US);
                    String v = h.substring(i + 1).trim();
                    if (k.equals("content-length")) {
                        try { len = Integer.parseInt(v); } catch (Throwable ignored) {}
                    } else if (k.equals("content-type")) {
                        contentType = v;
                    }
                }
            }
            OutputStream out = new BufferedOutputStream(s.getOutputStream());
            if (method.equals("GET")) {
                if (path.startsWith("/f?")) {
                    serveFile(out, path.substring(3));
                } else {
                    serveIndex(out);
                }
            } else if (method.equals("POST") && path.startsWith("/upload")) {
                handleUpload(in, out, len, contentType);
            } else {
                respond(out, 404, "text/plain; charset=utf-8", "404");
            }
            out.flush();
        } catch (Throwable t) {
            String m = t.getMessage() == null ? "" : t.getMessage();
            if (!(m.contains("Broken pipe") || m.contains("Connection reset")
                    || m.contains("Socket closed") || m.contains("EPIPE")
                    || m.contains("Connection closed"))) {
                log("请求失败：" + m);
            }
        } finally {
            try { s.close(); } catch (Throwable ignored) {}
        }
    }

    private String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') sb.append((char) c);
        }
        return sb.length() == 0 && c == -1 ? null : sb.toString();
    }

    private void respond(OutputStream out, int code, String type, String body) throws IOException {
        byte[] b = body.getBytes("UTF-8");
        String head = "HTTP/1.1 " + code + " OK\r\nContent-Type: " + type
                + "\r\nContent-Length: " + b.length + "\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes("UTF-8"));
        out.write(b);
    }

    private void serveIndex(OutputStream out) throws IOException {
        List<File> files = new ArrayList<>();
        File[] fs = dir.listFiles();
        if (fs != null) for (File f : fs) if (f.isFile()) files.add(f);
        Collections.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        StringBuilder rows = new StringBuilder();
        for (File f : files) {
            String name = esc(f.getName());
            rows.append("<a class=row href=\"/f?name=").append(enc(f.getName()))
                    .append("\"><b>").append(name).append("</b><span>")
                    .append(size(f.length())).append(" · ")
                    .append(new SimpleDateFormat("MM-dd HH:mm", Locale.US).format(new Date(f.lastModified())))
                    .append("</span></a>");
        }
        if (files.isEmpty()) rows.append("<p class=empty>还没有文件，上传一个试试</p>");
        String html = "<!doctype html><meta name=viewport content='width=device-width,initial-scale=1'>"
                + "<title>IK工具箱 · 局域网传输</title><style>"
                + "body{margin:0;background:#0b1220;color:#e8f0ff;font-family:system-ui,-apple-system,sans-serif}"
                + ".top{padding:22px 18px 10px}.h1{font-size:20px;font-weight:700}"
                + ".sub{color:#8aa0b8;font-size:12px;margin-top:6px}"
                + ".up{margin:14px 14px 6px;padding:18px;border:1px dashed #2f7;border-radius:16px;background:#0f1a2a;text-align:center}"
                + "input[type=file]{color:#cfe3ff;font-size:13px;max-width:100%}"
                + "button{margin-top:10px;padding:10px 22px;border:0;border-radius:12px;background:#2563eb;color:#fff;font-size:14px}"
                + ".list{padding:10px 14px 30px}.row{display:flex;justify-content:space-between;align-items:center;"
                + "background:#141f31;border:1px solid #22324a;border-radius:14px;padding:12px 14px;margin:8px 0;"
                + "text-decoration:none;color:#e8f0ff}.row b{font-size:14px;font-weight:600;word-break:break-all}"
                + ".row span{color:#7f95ad;font-size:11px;margin-left:12px;white-space:nowrap}"
                + ".empty{color:#7f95ad;text-align:center;padding:30px}</style>"
                + "<div class=top><div class=h1>IK工具箱 · 局域网上传</div>"
                + "<div class=sub>同一个 WiFi 下，选文件点上传就进手机 Download/IK工具箱/传文件</div></div>"
                + "<form class=up method=post action=/upload enctype=multipart/form-data>"
                + "<input type=file name=files multiple><br><button type=submit>上传到手机</button></form>"
                + "<div class=list>" + rows + "</div>";
        respond(out, 200, "text/html; charset=utf-8", html);
    }

    private void serveFile(OutputStream out, String query) throws IOException {
        String name = null;
        try {
            name = URLDecoder.decode(query.replace("name=", ""), "UTF-8");
        } catch (Throwable ignored) {}
        File f = name == null ? null : new File(dir, name);
        if (f == null || !f.isFile()) { respond(out, 404, "text/plain", "not found"); return; }
        String head = "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\n"
                + "Content-Length: " + f.length() + "\r\nContent-Disposition: attachment; filename=\""
                + new String(f.getName().getBytes("UTF-8"), "ISO-8859-1") + "\"\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes("UTF-8"));
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = raf.read(buf)) > 0) {
                try {
                    out.write(buf, 0, n);
                } catch (IOException e) {
                    // 浏览器取消下载/提前断开，正常现象，不要当错误
                    break;
                }
            }
        }
    }

    private void handleUpload(InputStream in, OutputStream out, int len, String contentType) throws IOException {
        if (len <= 0 || len > 80 * 1024 * 1024) {
            respond(out, 413, "text/plain; charset=utf-8", "文件太大（限 80MB）");
            return;
        }
        byte[] body = new byte[len];
        int read = 0;
        while (read < len) {
            int n = in.read(body, read, len - read);
            if (n <= 0) break;
            read += n;
        }
        String boundary = null;
        for (String seg : contentType.split(";")) {
            seg = seg.trim();
            if (seg.startsWith("boundary=")) boundary = seg.substring(9).replace("\"", "");
        }
        if (boundary == null) { respond(out, 400, "text/plain; charset=utf-8", "缺少 boundary"); return; }
        int saved = 0;
        String[] chunks = splitMultipart(body, boundary);
        for (String chunk : chunks) {
            int headerEnd = chunk.indexOf("\r\n\r\n");
            if (headerEnd < 0) continue;
            String headers = chunk.substring(0, headerEnd);
            String fileName = null;
            for (String line : headers.split("\r\n")) {
                if (line.toLowerCase(Locale.US).startsWith("content-disposition")) {
                    int i = line.indexOf("filename=");
                    if (i >= 0) {
                        fileName = line.substring(i + 9).replace("\"", "").trim();
                        int semi = fileName.indexOf(';');
                        if (semi > 0) fileName = fileName.substring(0, semi).trim();
                    }
                }
            }
            if (fileName == null || fileName.isEmpty()) continue;
            fileName = sanitize(fileName);
            if (fileName.isEmpty()) fileName = "upload_" + System.currentTimeMillis();
            File target = uniqueTarget(fileName);
            String content = chunk.substring(headerEnd + 4);
            if (content.endsWith("\r\n")) content = content.substring(0, content.length() - 2);
            try (FileOutputStream fos = new FileOutputStream(target)) {
                fos.write(content.getBytes("ISO-8859-1"));
                // 上面按 Latin-1 转回，二进制安全
            }
            saved++;
            log("收到 " + target.getName() + "（" + size(target.length()) + "）");
        }
        String back = "<!doctype html><meta http-equiv=refresh content='1;url=/'>"
                + "<body style='background:#0b1220;color:#e8f0ff;font-family:system-ui;padding:40px;text-align:center'>"
                + "<h3>上传成功 " + saved + " 个文件</h3><p style='color:#8aa0b8'>正在返回列表…</p>";
        respond(out, 200, "text/html; charset=utf-8", back);
    }

    /** 简单按 boundary 切分（上传文件不是超大二进制时够用） */
    private String[] splitMultipart(byte[] body, String boundary) {
        String text = new String(body, java.nio.charset.Charset.forName("ISO-8859-1"));
        String delim = "--" + boundary;
        String[] arr = text.split(java.util.regex.Pattern.quote(delim));
        List<String> out = new ArrayList<>();
        for (String s : arr) {
            if (s.startsWith("--")) continue;
            if (s.startsWith("\r\n")) s = s.substring(2);
            if (!s.isEmpty()) out.add(s);
        }
        return out.toArray(new String[0]);
    }

    private String sanitize(String name) {
        name = name.replace("\\", "/");
        if (name.contains("/")) name = name.substring(name.lastIndexOf('/') + 1);
        return name.replaceAll("[\\r\\n\\t]", "").trim();
    }

    private File uniqueTarget(String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        String base = name, ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) { base = name.substring(0, dot); ext = name.substring(dot); }
        for (int i = 1; i < 999; i++) {
            File n = new File(dir, base + "_" + i + ext);
            if (!n.exists()) return n;
        }
        return new File(dir, base + "_" + System.currentTimeMillis() + ext);
    }

    public static String localIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
                }
            }
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address) return a.getHostAddress();
                }
            }
        } catch (Throwable ignored) {}
        return "0.0.0.0";
    }

    private String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String enc(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); } catch (Throwable t) { return s; }
    }

    private String size(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return String.format(Locale.US, "%.1f KB", b / 1024.0);
        return String.format(Locale.US, "%.1f MB", b / 1024.0 / 1024.0);
    }

    private void log(String s) {
        if (listener != null) listener.onLog(s);
    }
}
