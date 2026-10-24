package com.roomarchive.server;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.os.IBinder;
import android.system.Os;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 公网隧道前台服务：在独立服务中托管 cloudflared 进程，
 * 锁屏/切后台/系统回收界面均不影响隧道运行（通知栏常驻）。
 */
public class TunnelService extends Service {

    public static final String ACTION_START = "com.roomarchive.server.START";
    public static final String ACTION_START_FRP = "com.roomarchive.server.START_FRP";
    public static final String ACTION_STOP = "com.roomarchive.server.STOP";
    public static final int STATE_CONNECTING = 0;
    public static final int STATE_CONNECTED = 1;
    public static final int STATE_EXITED = 2;
    public static final int STATE_ERROR = 3;
    public static final int STATE_DOWNLOADING = 4;

    /** frp 参数 extra 键 */
    public static final String EXTRA_FRP_SERVER = "frp_server";   // host:port
    public static final String EXTRA_FRP_TOKEN = "frp_token";
    public static final String EXTRA_FRP_DOMAIN = "frp_domain";   // http 隧道访问域名

    /** 内核自动下载完成后重放 frp 启动参数 */
    private String pendingFrpServer, pendingFrpToken, pendingFrpDomain;

    /** Activity 通过它接收状态/日志回调 */
    public interface Listener {
        void onLog(String line);
        void onUrl(String url);
        void onState(int state, String msg);
    }

    private static Listener sListener;
    private static final int NOTIF_ID = 1001;

    private Process proc;
    private Thread readerThread;
    private volatile boolean running = false;
    private HttpProxy proxy;

    public static void setListener(Listener l) {
        sListener = l;
    }

    public static boolean isRunning() {
        return sProcRef != null;
    }

    private static volatile Process sProcRef;

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundCompat();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_START.equals(action)) {
                startCloudflare();
                return START_STICKY;
            } else if (ACTION_START_FRP.equals(action)) {
                String server = intent.getStringExtra(EXTRA_FRP_SERVER);
                String token = intent.getStringExtra(EXTRA_FRP_TOKEN);
                String domain = intent.getStringExtra(EXTRA_FRP_DOMAIN);
                startFrp(server, token, domain);
                return START_STICKY;
            } else if (ACTION_STOP.equals(action)) {
                stopTunnel();
                stopSelf();
                return START_NOT_STICKY;
            }
        }
        // 默认：已在运行则继续
        return running ? START_STICKY : START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        stopTunnel();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---------- 隧道管理 ----------

    private void startCloudflare() {
        if (running) return;
        try {
            File bin = findBinary("libcloudflared.so");
            if (bin == null || !isAdaptiveKernel(bin)) {
                if (bin == null) {
                    notifyState(STATE_DOWNLOADING, "穿透内核缺失，自动重新下载…");
                    notifyLog("四个路径均未找到可执行内核，自动下载安装…");
                } else {
                    notifyState(STATE_DOWNLOADING, "穿透内核版本过旧，自动重新下载…");
                    notifyLog("内核校验: md5=" + md5Of(bin) + "（旧版，自动下载自适应版…）");
                }
                autoDownloadAndRetry("libcloudflared.so");
                return;
            }
            // 内核校验：md5 前 12 位（d76be7385d4e=自适应版）
            String md5 = md5Of(bin);
            if (md5 != null) {
                notifyLog("内核校验: md5=" + md5.substring(0, Math.min(12, md5.length())) + "（自适应版）");
            }
            notifyLog("内核路径: " + bin.getAbsolutePath());
            // VPN 检测：若系统 VPN 已开启，cloudflared 流量自动走 VPN 出口（可绕开运营商劫持）
            if (isVpnActive()) {
                notifyLog("检测到系统 VPN：隧道流量将走 VPN 出口，绕开运营商劫持");
            } else {
                notifyLog("未检测到 VPN：若连接失败可先开系统 VPN 再试");
            }
            if (!startProxyIfNeeded()) {
                notifyState(STATE_ERROR, "内置 DNS 代理启动失败，无法开启公网");
                return;
            }
            // DNS 自检：确认注册域名能被正确解析（防运营商劫持）
            try {
                final String probeIp = HttpProxy.debugResolve("api.trycloudflare.com");
                notifyLog("DNS 自检: api.trycloudflare.com -> " + (probeIp == null ? "解析失败" : probeIp));
            } catch (Throwable t) {
                notifyLog("DNS 自检异常: " + t.getMessage());
            }
            String[] cmd;
            if (launchMode == 2) {
                String linker = new File("/system/bin/linker64").exists() ? "/system/bin/linker64" : "/system/bin/linker";
                cmd = new String[]{linker, bin.getAbsolutePath(),
                        "tunnel", "--url", "http://127.0.0.1:8080",
                        "--no-autoupdate", "--protocol", "http2",
                        "--logfile", "/dev/null", "--loglevel", "info"};
            } else if (launchMode == 1) {
                cmd = new String[]{"/system/bin/sh", "-c",
                        "exec \"" + bin.getAbsolutePath() + "\" tunnel --url http://127.0.0.1:8080 --no-autoupdate --protocol http2 --logfile /dev/null --loglevel info"};
            } else {
                cmd = new String[]{
                        bin.getAbsolutePath(),
                        "tunnel", "--url", "http://127.0.0.1:8080",
                        "--no-autoupdate",
                        "--protocol", "http2",
                        "--logfile", "/dev/null",
                        "--loglevel", "info"};
            }
            ProcessBuilder pb = new ProcessBuilder(cmd);
            // 出口代理：若用户配置了外部代理端口（如 Clash/代理 App 的本地端口），
            // 则 cloudflared 经该代理出站（可绕开运营商对 Cloudflare 的 TLS 劫持）；
            // 未配置则走 App 内置 DNS 代理（127.0.0.1:18081）。
            String extProxy = getExternalProxy();
            if (extProxy != null) {
                pb.environment().put("HTTPS_PROXY", extProxy);
                pb.environment().put("HTTP_PROXY", extProxy);
                pb.environment().put("ALL_PROXY", extProxy);
                notifyLog("出口代理: " + extProxy + "（用户配置，绕开劫持）");
            } else {
                pb.environment().put("HTTPS_PROXY", "http://127.0.0.1:" + HttpProxy.PORT);
                pb.environment().put("HTTP_PROXY", "http://127.0.0.1:" + HttpProxy.PORT);
            }
            pb.redirectErrorStream(true);
            proc = pb.start();
            sProcRef = proc;
            running = true;
            notifyState(STATE_CONNECTING, "隧道进程已启动");
            notifyLog("进程已启动，等待分配公网地址…");

            final Process p = proc;
            readerThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
                        String line;
                        Pattern urlP = Pattern.compile("https://[a-zA-Z0-9-]+\\.trycloudflare\\.com");
                        while ((line = br.readLine()) != null) {
                            final String l = line;
                            final Matcher m = urlP.matcher(l);
                            notifyLog(l);
                            if (m.find()) {
                                notifyUrl(m.group(0));
                                notifyState(STATE_CONNECTED, "公网已开启（隧道连通）");
                            }
                        }
                    } catch (IOException ignored) {
                    }
                    onProcExit(p);
                }
            }, "tunnel-reader");
            readerThread.setDaemon(true);
            readerThread.start();
        } catch (final Exception e) {
            running = false;
            sProcRef = null;
            notifyLog("启动失败：" + e.getMessage());
            notifyState(STATE_ERROR, "公网开启失败：" + e.getMessage());
        }
    }

    /** frp 穿透：连国内 frp 节点（如樱花 Frp），不走 Cloudflare，规避运营商封锁 */
    private void startFrp(String server, String token, String domain) {
        if (running) return;
        if (server == null || server.trim().isEmpty()
                || token == null || token.trim().isEmpty()
                || domain == null || domain.trim().isEmpty()) {
            notifyState(STATE_ERROR, "frp 参数不完整：请填写节点地址、访问密钥、访问域名");
            return;
        }
        server = server.trim();
        token = token.trim();
        domain = domain.trim();
        pendingFrpServer = server;
        pendingFrpToken = token;
        pendingFrpDomain = domain;
        try {
            File bin = findBinary("libfrpc.so");
            if (bin == null) {
                notifyState(STATE_DOWNLOADING, "穿透内核缺失，自动重新下载…");
                notifyLog("libfrpc.so 未找到，自动下载穿透内核…");
                autoDownloadAndRetry("libfrpc.so");
                return;
            }
            notifyLog("内核路径: " + bin.getAbsolutePath());
            if (!startProxyIfNeeded()) {
                notifyState(STATE_ERROR, "内置 DNS 代理启动失败，无法开启 frp");
                return;
            }
            // DNS 自检：解析 frp 节点（国内 DoH，一般可解）
            String host = server;
            int colon = server.lastIndexOf(':');
            if (colon > 0) host = server.substring(0, colon);
            try {
                final String probeIp = HttpProxy.debugResolve(host);
                notifyLog("DNS 自检: " + host + " -> " + (probeIp == null ? "解析失败" : probeIp));
            } catch (Throwable t) {
                notifyLog("DNS 自检异常: " + t.getMessage());
            }
            // 生成 frpc.toml
            File cfg = new File(getFilesDir(), "frpc.toml");
            java.io.FileWriter fw = new java.io.FileWriter(cfg);
            fw.write("serverAddr = \"" + host + "\"\n");
            int port = 7000;
            if (colon > 0) {
                try {
                    port = Integer.parseInt(server.substring(colon + 1));
                } catch (NumberFormatException ignored) {
                }
            }
            fw.write("serverPort = " + port + "\n");
            fw.write("auth.method = \"token\"\n");
            fw.write("auth.token = \"" + token + "\"\n");
            fw.write("[[proxies]]\n");
            fw.write("name = \"room\"\n");
            fw.write("type = \"http\"\n");
            fw.write("localIP = \"127.0.0.1\"\n");
            fw.write("localPort = 8080\n");
            fw.write("customDomains = [\"" + domain + "\"]\n");
            fw.close();

            String[] cmd;
            if (launchMode == 2) {
                String linker = new File("/system/bin/linker64").exists() ? "/system/bin/linker64" : "/system/bin/linker";
                cmd = new String[]{linker, bin.getAbsolutePath(), "-c", cfg.getAbsolutePath()};
            } else if (launchMode == 1) {
                cmd = new String[]{"/system/bin/sh", "-c",
                        "exec \"" + bin.getAbsolutePath() + "\" -c \"" + cfg.getAbsolutePath() + "\""};
            } else {
                cmd = new String[]{bin.getAbsolutePath(), "-c", cfg.getAbsolutePath()};
            }
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.environment().put("HTTPS_PROXY", "http://127.0.0.1:" + HttpProxy.PORT);
            pb.environment().put("HTTP_PROXY", "http://127.0.0.1:" + HttpProxy.PORT);
            pb.redirectErrorStream(true);
            proc = pb.start();
            sProcRef = proc;
            running = true;
            notifyState(STATE_CONNECTING, "frp 进程已启动");
            notifyLog("frp 进程已启动，等待连接节点…");

            final Process p = proc;
            final String fDomain = domain;
            readerThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
                        String line;
                        while ((line = br.readLine()) != null) {
                            final String l = line;
                            notifyLog(l);
                            if (l.contains("login to server success")) {
                                notifyLog("frp 已登录节点，正在建立隧道…");
                            }
                            if (l.contains("start proxy success")) {
                                notifyUrl("http://" + fDomain);
                                notifyState(STATE_CONNECTED, "frp 公网已开启（隧道连通）");
                            }
                        }
                    } catch (IOException ignored) {
                    }
                    onProcExit(p);
                }
            }, "frp-reader");
            readerThread.setDaemon(true);
            readerThread.start();
        } catch (final Exception e) {
            running = false;
            sProcRef = null;
            notifyLog("frp 启动失败：" + e.getMessage());
            notifyState(STATE_ERROR, "frp 开启失败：" + e.getMessage());
        }
    }

    /** 进程退出统一处理 */
    private void onProcExit(Process p) {
        int code = -1;
        try {
            code = p.waitFor();
        } catch (Exception ignored) {
        }
        running = false;
        sProcRef = null;
        notifyLog("隧道进程已退出（退出码 " + code + "）。");
        if (code == 1) {
            String tail;
            synchronized (sLogBuf) {
                tail = sLogBuf.toString();
            }
            if (tail.contains("gave HTTP response to HTTPS client")) {
                notifyLog("检测到网络劫持：当前网络把到 Cloudflare 的 TLS 请求劫持成明文 HTTP（4 个官方端口均被拦）。");
                notifyLog("建议：① 切换 WiFi/数据网络，或关闭手机 VPN/代理/加速器后重试；② 或用下方 frp 穿透（国内节点，不受 Cloudflare 劫持影响）。");
            }
        }
        notifyState(STATE_EXITED, "隧道进程退出（码 " + code + "），可重新开启");
    }

    /** 启动内置 HTTP CONNECT 代理（幂等）；失败返回 false */
    private boolean startProxyIfNeeded() {
        if (proxy == null) {
            proxy = new HttpProxy();
            try {
                proxy.start();
                notifyLog("内置 DNS 代理已启动 (127.0.0.1:" + HttpProxy.PORT + ")");
            } catch (IOException e) {
                notifyLog("内置 DNS 代理启动失败：" + e.getMessage());
                proxy = null;
                return false;
            }
        }
        return true;
    }

    private void stopTunnel() {
        running = false;
        if (proc != null) {
            try {
                proc.destroy();
            } catch (Exception ignored) {
            }
            proc = null;
        }
        sProcRef = null;
        if (proxy != null) {
            proxy.stop();
            proxy = null;
        }
        notifyLog("已手动关闭隧道。");
        notifyState(STATE_EXITED, "隧道已关闭");
    }

    /** 文件 md5（hex 小写）；失败返回 null */
    private String md5Of(File f) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            in.close();
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** cloudflared 内核是否自适应版（md5 前缀匹配） */
    private boolean isAdaptiveKernel(File f) {
        String md5 = md5Of(f);
        return md5 != null && md5.startsWith(KernelInstaller.ADAPTIVE_MD5);
    }

    /** 后台自动下载内核 zip，完成后回到主线程重试启动当前隧道 */
    private void autoDownloadAndRetry(final String soName) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean ok = KernelInstaller.downloadAndInstall(TunnelService.this,
                        "https://aka.doubaocdn.com/s/nliTro1FJI", new KernelInstaller.Log() {
                            @Override
                            public void log(String s) {
                                notifyLog(s);
                            }
                        });
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        if (ok) {
                            // 重新触发当前隧道启动（下载装好了两个 so）
                            startService(new Intent(TunnelService.this, TunnelService.class)
                                    .setAction(soName.equals("libfrpc.so") ? ACTION_START_FRP
                                            : ACTION_START)
                                    .putExtra(EXTRA_FRP_SERVER, pendingFrpServer)
                                    .putExtra(EXTRA_FRP_TOKEN, pendingFrpToken)
                                    .putExtra(EXTRA_FRP_DOMAIN, pendingFrpDomain));
                        } else {
                            notifyState(STATE_ERROR, "内核自动下载失败，请检查网络后重试");
                        }
                    }
                });
            }
        }).start();
    }

    private File findBinary(String name) {
        // 多路径兜底，且每个候选都实测可执行：        // 内部私有目录（首选）→ 外部存储 → 系统原生库目录 → /data/local/tmp
        // 启动模式按实测结果选择：0=直接 exec，1=sh 包装，2=系统 linker 加载（鸿蒙绕过 exec 限制）
        launchMode = 0;
        java.util.List<File> cand = new java.util.ArrayList<>();
        try {
            cand.add(new File(KernelPaths.internalDir(getApplicationContext()), name));
        } catch (Exception ignored) {
        }
        try {
            cand.add(new File(KernelPaths.nativeDir(getApplicationContext()), name));
        } catch (Exception ignored) {
        }
        try {
            cand.add(new File(getApplicationInfo().nativeLibraryDir, name));
        } catch (Exception ignored) {
        }
        cand.add(new File("/data/local/tmp/" + name));
        for (File bin : cand) {
            try {
                if (bin == null || !bin.exists() || bin.length() < 1000000) continue;
                KernelPaths.chmodKernel(bin.getParentFile(), bin);
                int r = tryExec(bin);
                if (r >= 0) {
                    launchMode = r;
                    notifyLog((launchMode == 2 ? "内核可用（linker 模式）: "
                            : launchMode == 1 ? "内核可用（shell 模式）: "
                            : "内核可用: ") + bin.getAbsolutePath());
                    return bin;
                }
                notifyLog("内核不可执行: " + bin.getAbsolutePath() + "（尝试下一路径）");
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private int launchMode = 0;   // 0=直接 1=shell 2=linker

    /** 实测内核可执行性：
     *  返回 0=直接 exec 成功；1=shell 包装成功；2=linker 加载成功；-1=都失败
     *  严格按退出码判定（126/错误码均视为失败），避免 sh 假成功 */
    private int tryExec(File bin) {
        String path = bin.getAbsolutePath();
        try {
            Process pc = new ProcessBuilder("/system/bin/chmod", "0755", path)
                    .redirectErrorStream(true).start();
            pc.waitFor(3000, java.util.concurrent.TimeUnit.MILLISECONDS);
            pc.destroy();
            notifyLog("  chmod 后 canExecute=" + bin.canExecute() + " 大小=" + bin.length());
        } catch (Exception e) {
            notifyLog("  chmod 命令不可用: " + brief(e));
        }
        if (execOkDirect(path)) return 0;
        if (execOkVia("/system/bin/sh", "-c", "\"" + path + "\" --version", "shell")) return 1;
        // 鸿蒙等加固系统：用系统动态链接器直接加载（绕过对下载文件的 execve 检查）
        String linker = new File("/system/bin/linker64").exists() ? "/system/bin/linker64"
                : new File("/system/bin/linker").exists() ? "/system/bin/linker" : null;
        if (linker != null && execOkVia(linker, path, "--version", "linker")) return 2;
        return -1;
    }

    private boolean execOkDirect(String path) {
        try {
            Process p = new ProcessBuilder(path, "--version").redirectErrorStream(true).start();
            return waitOk(p);
        } catch (Exception e) {
            notifyLog("  直接执行失败: " + brief(e));
            return false;
        }
    }

    private boolean execOkVia(String cmd, String a1, String a2, String tag) {
        try {
            Process p = new ProcessBuilder(cmd, a1, a2).redirectErrorStream(true).start();
            return waitOk(p);
        } catch (Exception e) {
            notifyLog("  " + tag + " 执行失败: " + brief(e));
            return false;
        }
    }

    /** 严格判定：进程能启动且退出码正常（0/1），或超时仍在运行（--version 应秒退），才算可执行 */
    private boolean waitOk(Process p) {
        try {
            boolean exited = p.waitFor(3000, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (exited) {
                int code = p.exitValue();
                if (code == 0 || code == 1) return true;
                notifyLog("  试跑退出码=" + code + "（不可执行）");
                return false;
            }
            p.destroyForcibly();
            return true;
        } catch (Exception e) {
            notifyLog("  试跑异常: " + brief(e));
            return false;
        }
    }

    private String brief(Exception e) {
        String m = e.getMessage();
        return m == null ? e.getClass().getSimpleName() : m;
    }

    // ---------- 回调 ----------

    /** 系统 VPN 是否开启（API 24+，荣耀30/鸿蒙可用） */
    private boolean isVpnActive() {
        try {
            android.net.ConnectivityManager cm =
                    (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            for (android.net.Network n : cm.getAllNetworks()) {
                android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(n);
                if (caps != null
                        && caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** 用户配置的外部出口代理（穿透设置里填的本地代理端口，如 7890） */
    private String getExternalProxy() {
        try {
            android.content.SharedPreferences sp =
                    getSharedPreferences("server_cfg", MODE_PRIVATE);
            String port = sp.getString("wan_proxy_port", "").trim();
            if (!port.isEmpty() && !port.equals("0") && !port.equals("18081")) {
                return "http://127.0.0.1:" + port;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static final StringBuilder sLogBuf = new StringBuilder();

    private void notifyLog(final String line) {
        synchronized (sLogBuf) {
            sLogBuf.append(line).append('\n');
            if (sLogBuf.length() > 60000) sLogBuf.delete(0, sLogBuf.length() - 30000);
        }
        final Listener l = sListener;
        if (l != null) l.onLog(line);
    }

    private void notifyUrl(final String url) {
        final Listener l = sListener;
        if (l != null) l.onUrl(url);
    }

    private void notifyState(final int state, final String msg) {
        final Listener l = sListener;
        if (l != null) l.onState(state, msg);
    }

    // ---------- 前台通知 ----------

    private void startForegroundCompat() {
        Notification.Builder nb;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("tunnel", "公网隧道服务", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(ch);
            nb = new Notification.Builder(this, "tunnel");
        } else {
            nb = new Notification.Builder(this);
        }
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        nb.setContentTitle("房型库共享服务器")
                .setContentText("公网隧道运行中，点按可返回应用")
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .setContentIntent(pi)
                .setOngoing(true);
        startForeground(NOTIF_ID, nb.build());
    }
}
