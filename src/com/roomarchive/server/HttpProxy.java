package com.roomarchive.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * 内置 HTTP CONNECT 代理（127.0.0.1:18081）。
 * 解决安卓无系统 resolv.conf 时 Go 静态内核（cloudflared）无法解析域名的问题：
 * 隧道内核的所有域名解析（注册 api.trycloudflare.com、DoH 边缘发现）都经本代理转发，
 * 由 Java InetAddress 走安卓系统 DNS（netd）正常解析。
 */
public class HttpProxy {

    public static final int PORT = 18081;

    private ServerSocket server;
    private ExecutorService pool;
    private Thread acceptThread;
    private volatile boolean running = false;

    public void start() throws IOException {
        if (running) return;
        // 明确绑定 IPv4 回环 127.0.0.1（某些设备 getLoopbackAddress() 会返回 ::1，
        // 而 Go 内核走 IPv4 的 127.0.0.1 连接，会 connection refused）
        server = new ServerSocket();
        server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT), 64);
        pool = Executors.newCachedThreadPool();
        running = true;
        acceptThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (running) {
                    try {
                        final Socket c = server.accept();
                        pool.execute(new Handler(c));
                    } catch (Exception e) {
                        if (running) {
                            try {
                                Thread.sleep(200);
                            } catch (InterruptedException ignored) {
                            }
                        }
                    }
                }
            }
        }, "proxy-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        // 自检：确认端口已实际可连
        Socket probe = new Socket();
        probe.connect(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT), 1000);
        probe.close();
    }

    public void stop() {
        running = false;
        try {
            if (server != null) server.close();
        } catch (Exception ignored) {
        }
        if (pool != null) pool.shutdownNow();
        server = null;
        pool = null;
    }

    public boolean isRunning() {
        return running;
    }

    // ---------- 公共 DNS 直查（绕过运营商 DNS 污染） ----------

    private static final String[] PUBLIC_DNS = {
            "223.5.5.5",   // 阿里
            "119.29.29.29", // 腾讯
            "114.114.114.114"
    };

    /** Cloudflare Anycast 备用 IP 段（任意 Cloudflare IP 均可访问 api.trycloudflare.com，按 Host 路由）；
     *  含 IPv6 候选（部分运营商只劫持 IPv4，IPv6 放行） */
    private static final String[] CLOUDFLARE_FALLBACK_IPS = {
            "104.16.0.1", "104.16.0.2", "104.16.1.1",
            "172.64.0.1", "172.64.0.2", "172.64.0.3", "172.64.0.4", "172.64.0.5",
            "1.1.1.1",
            "2606:4700:4700::1111", "2606:4700::6810:e684", "2606:4700::1111"
    };

    /** 解析目标 IP 候选列表：DoH(HTTPS,防篡改) → 公共DNS(UDP) → 系统DNS；末尾拼接 Cloudflare 备用段 IP */
    private static java.util.List<String> resolveCandidates(String host) {
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        if (host == null || host.isEmpty()) return new java.util.ArrayList<>(set);
        if (host.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
            set.add(host);
            return new java.util.ArrayList<>(set);
        }
        try {
            java.util.List<String> ips = dohQueryAllA(host);
            if (ips != null) set.addAll(ips);
        } catch (Exception ignored) {
        }
        if (set.isEmpty()) {
            for (String server : PUBLIC_DNS) {
                try {
                    java.util.List<String> ips = queryAllA(server, host);
                    if (ips != null && !ips.isEmpty()) {
                        set.addAll(ips);
                        break;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        if (set.isEmpty()) {
            try {
                InetAddress[] addrs = InetAddress.getAllByName(host);
                for (InetAddress a : addrs) set.add(a.getHostAddress());
            } catch (Exception ignored) {
            }
        }
        for (String ip : CLOUDFLARE_FALLBACK_IPS) set.add(ip);
        return new java.util.ArrayList<>(set);
    }

    /** 供诊断：分别打印"DNS 解析结果"与"备用 IP 段" */
    public static String debugResolve(String host) {
        java.util.List<String> parsed = new java.util.ArrayList<>();
        if (host != null && !host.isEmpty() && !host.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
            try {
                java.util.List<String> ips = dohQueryAllA(host);
                if (ips != null && !ips.isEmpty()) parsed.addAll(ips);
            } catch (Exception ignored) {
            }
            if (parsed.isEmpty()) {
                for (String server : PUBLIC_DNS) {
                    try {
                        java.util.List<String> ips = queryAllA(server, host);
                        if (ips != null && !ips.isEmpty()) {
                            parsed.addAll(ips);
                            break;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            if (parsed.isEmpty()) {
                try {
                    InetAddress[] addrs = InetAddress.getAllByName(host);
                    for (InetAddress a : addrs) parsed.add(a.getHostAddress());
                } catch (Exception ignored) {
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("解析=[").append(parsed.isEmpty() ? "全部失败" : parsed.toString()).append("]");
        sb.append(" 备用IP=[").append(CLOUDFLARE_FALLBACK_IPS.length).append("个]");
        return sb.toString();
    }

    /** RFC 8484 wire-format DoH，HTTPS 加密查询 223.5.5.5（运营商无法篡改 HTTPS） */
    private static java.util.List<String> dohQueryAllA(String host) throws Exception {
        byte[] q = buildDnsQuery(host, 1);
        String b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(q);
        URL u = new URL("https://223.5.5.5/dns-query?dns=" + b64);
        HttpsURLConnection conn = (HttpsURLConnection) u.openConnection();
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, new TrustManager[]{new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        }}, new SecureRandom());
        conn.setSSLSocketFactory(sc.getSocketFactory());
        conn.setHostnameVerifier(new javax.net.ssl.HostnameVerifier() {
            public boolean verify(String hostname, javax.net.ssl.SSLSession session) {
                return true;
            }
        });
        conn.setRequestProperty("Accept", "application/dns-message");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        int code = conn.getResponseCode();
        if (code != 200) return null;
        InputStream in = conn.getInputStream();
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return parseAllA(bo.toByteArray(), bo.size());
    }

    private static java.util.List<String> queryAllA(String server, String host) throws IOException {
        DatagramSocket sock = new DatagramSocket();
        sock.setSoTimeout(3000);
        try {
            byte[] q = buildDnsQuery(host, 1);
            InetAddress dnsServer = InetAddress.getByName(server);
            sock.send(new DatagramPacket(q, q.length, dnsServer, 53));
            byte[] buf = new byte[512];
            DatagramPacket resp = new DatagramPacket(buf, buf.length);
            sock.receive(resp);
            return parseAllA(resp.getData(), resp.getLength());
        } finally {
            sock.close();
        }
    }

    private static byte[] buildDnsQuery(String host, int qtype) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int id = (int) (System.currentTimeMillis() & 0xFFFF);
        out.write(id >> 8);
        out.write(id);
        out.write(0x01); // RD
        out.write(0x00);
        out.write(0x00); // QDCOUNT=1
        out.write(0x01);
        out.write(0x00); // ANCOUNT=0
        out.write(0x00);
        out.write(0x00); // NSCOUNT=0
        out.write(0x00);
        out.write(0x00); // ARCOUNT=0
        out.write(0x00);
        for (String part : host.split("\\.")) {
            out.write(part.length());
            out.write(part.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        }
        out.write(0x00);
        out.write(qtype >> 8);
        out.write(qtype);
        out.write(0x00); // QCLASS=IN
        out.write(0x01);
        return out.toByteArray();
    }

    /** 解析 DNS 响应中的全部 A 记录；无则返回空列表 */
    private static java.util.List<String> parseAllA(byte[] d, int len) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (len < 12) return out;
        int rcode = d[3] & 0x0F;
        if (rcode != 0) return out;
        int qdcount = ((d[4] & 0xFF) << 8) | (d[5] & 0xFF);
        int ancount = ((d[6] & 0xFF) << 8) | (d[7] & 0xFF);
        int idx = 12;
        // 跳过 question
        for (int i = 0; i < qdcount; i++) {
            idx = skipName(d, idx);
            if (idx < 0 || idx + 4 > len) return out;
            idx += 4;
        }
        // 遍历 answer 收集全部 A 记录
        for (int i = 0; i < ancount; i++) {
            if (idx < 0 || idx + 10 > len) return out;
            idx = skipName(d, idx);
            if (idx < 0 || idx + 10 > len) return out;
            int rtype = ((d[idx] & 0xFF) << 8) | (d[idx + 1] & 0xFF);
            int rdlen = ((d[idx + 8] & 0xFF) << 8) | (d[idx + 9] & 0xFF);
            idx += 10;
            if (idx + rdlen > len) return out;
            if (rtype == 1 && rdlen == 4) { // A
                out.add((d[idx] & 0xFF) + "." + (d[idx + 1] & 0xFF) + "."
                        + (d[idx + 2] & 0xFF) + "." + (d[idx + 3] & 0xFF));
            }
            idx += rdlen;
        }
        return out;
    }

    /** 跳过（可能带压缩指针的）域名，返回下一字段偏移 */
    private static int skipName(byte[] d, int idx) {
        if (idx >= d.length) return -1;
        while (true) {
            int b = d[idx] & 0xFF;
            if (b == 0) return idx + 1;
            if ((b & 0xC0) == 0xC0) return idx + 2;
            idx += 1 + b;
            if (idx >= d.length) return -1;
        }
    }

    static class Handler implements Runnable {
        private final Socket c;

        Handler(Socket c) {
            this.c = c;
        }

        @Override
        public void run() {
            Socket upstream = null;
            try {
                c.setSoTimeout(30000);
                InputStream in = c.getInputStream();
                OutputStream out = c.getOutputStream();

                String line = readLine(in);
                if (line == null || !line.startsWith("CONNECT ")) {
                    c.close();
                    return;
                }
                String hostPort = line.split(" ")[1];
                String host = hostPort;
                int port = 443;
                int colon = hostPort.lastIndexOf(':');
                if (colon > 0) {
                    host = hostPort.substring(0, colon);
                    try {
                        port = Integer.parseInt(hostPort.substring(colon + 1));
                    } catch (NumberFormatException ignored) {
                    }
                }

                // 解析目标 IP 候选（DoH → UDP公共DNS → 系统DNS → Cloudflare备用段），逐个尝试连接
                java.util.List<String> candidates = resolveCandidates(host);
                if (candidates.isEmpty()) {
                    out.write("HTTP/1.1 502 Bad Gateway\r\n\r\n".getBytes());
                    out.flush();
                    c.close();
                    return;
                }
                Socket picked = null;
                for (String ip : candidates) {
                    Socket s = new Socket();
                    try {
                        s.connect(new InetSocketAddress(InetAddress.getByName(ip), port), 6000);
                        picked = s;
                        break;
                    } catch (Exception e) {
                        try {
                            s.close();
                        } catch (Exception ignored) {
                        }
                    }
                }
                if (picked == null) {
                    out.write("HTTP/1.1 502 Bad Gateway\r\n\r\n".getBytes());
                    out.flush();
                    c.close();
                    return;
                }
                upstream = picked;

                out.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes());
                out.flush();

                // 双向转发
                Thread t1 = new Thread(new Pipe(in, upstream.getOutputStream()), "proxy-t1");
                Thread t2 = new Thread(new Pipe(upstream.getInputStream(), out), "proxy-t2");
                t1.setDaemon(true);
                t2.setDaemon(true);
                t1.start();
                t2.start();
                try {
                    t1.join();
                } catch (InterruptedException ignored) {
                }
            } catch (Exception ignored) {
            } finally {
                try {
                    if (upstream != null) upstream.close();
                } catch (Exception ignored) {
                }
                try {
                    c.close();
                } catch (Exception ignored) {
                }
            }
        }

        private static String readLine(InputStream in) throws IOException {
            StringBuilder sb = new StringBuilder();
            int ch;
            while ((ch = in.read()) != -1) {
                if (ch == '\n') break;
                sb.append((char) ch);
            }
            return sb.toString();
        }

        static class Pipe implements Runnable {
            private final InputStream a;
            private final OutputStream b;

            Pipe(InputStream a, OutputStream b) {
                this.a = a;
                this.b = b;
            }

            @Override
            public void run() {
                byte[] buf = new byte[8192];
                try {
                    int n;
                    while ((n = a.read(buf)) > 0) {
                        b.write(buf, 0, n);
                        b.flush();
                    }
                } catch (Exception ignored) {
                }
                try {
                    b.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
