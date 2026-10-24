package com.roomarchive.server;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/** 服务器 · 房型库网页一键分享（局域网 + 公网穿透 + frp） */
public class ServerActivity extends BaseActivity implements TunnelService.Listener {

    private static final int PORT = 8080;
    private static final int REQ_PICK_HTML = 1001;

    private MiniHttpServer server;
    private TextView statusView;
    private TextView addrView;
    private Button btnToggle;
    private Button btnOpen;
    private Button btnPick;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView wanStatusView;
    private TextView wanUrlView;
    private Button btnWan;
    private EditText proxyPort;
    private Button btnCopyWan;
    private ScrollView logScroll;
    private TextView logView;
    private boolean wanRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        server = new MiniHttpServer(this, PORT);
        TunnelService.setListener(this);
        if (TunnelService.isRunning()) {
            wanRunning = true;
            wanStatusView.setText("公网隧道运行中");
            wanStatusView.setTextColor(Color.rgb(30, 140, 80));
            btnWan.setText("关闭公网访问");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (server != null) server.stop();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(Tools.bgWithGlow(this));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(18));
        scroll.addView(root);

        TextView title = Tools.pageTitle(this, "服务器 · 房型库共享");
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("一键分享网页给朋友 · 局域网 + 公网双通道（隧道在通知栏常驻，锁屏切后台不断）");
        subtitle.setTextSize(13);
        subtitle.setTextColor(Tools.subTextColor(this));
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle);
        root.addView(spacer(14));

        // ===== 本地服务器区域 =====
        TextView secLocal = new TextView(this);
        secLocal.setText("本地服务器（局域网）");
        secLocal.setTextSize(16);
        secLocal.setTextColor(Tools.textColor(this));
        root.addView(secLocal);
        root.addView(spacer(6));

        statusView = new TextView(this);
        statusView.setText("服务器未启动");
        statusView.setTextSize(15);
        statusView.setTextColor(0xFFDC2626);
        statusView.setGravity(Gravity.CENTER);
        root.addView(statusView);
        root.addView(spacer(6));

        addrView = new TextView(this);
        addrView.setText("——");
        addrView.setTextSize(14);
        addrView.setTextColor(Tools.accentColor(this));
        addrView.setGravity(Gravity.CENTER);
        root.addView(addrView);
        root.addView(spacer(10));

        btnToggle = new Button(this);
        btnToggle.setText("启动服务器");
        btnToggle.setTextSize(16);
        btnToggle.setAllCaps(false);
        btnToggle.setTextColor(Color.WHITE);
        btnToggle.setGravity(Gravity.CENTER);
        btnToggle.setBackground(Tools.pillButton(this, 50, true));
        Icons.attachToText(btnToggle, "play", Color.WHITE, 18, 6);
        btnToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (server != null && server.isRunning()) {
                    server.stop();
                    stopWanTunnel();
                    render();
                } else {
                    try {
                        if (server == null) server = new MiniHttpServer(ServerActivity.this, PORT);
                        server.start();
                        render();
                    } catch (IOException e) {
                        Toast.makeText(ServerActivity.this, "启动失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
            }
        });
        root.addView(btnToggle);
        root.addView(spacer(8));

        btnOpen = new Button(this);
        btnOpen.setText("在本机打开页面");
        btnOpen.setTextSize(14);
        btnOpen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (server != null && server.isRunning()) {
                    Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:" + PORT));
                    startActivity(it);
                } else {
                    Toast.makeText(ServerActivity.this, "请先启动服务器", Toast.LENGTH_SHORT).show();
                }
            }
        });
        root.addView(btnOpen);
        root.addView(spacer(8));

        btnPick = new Button(this);
        btnPick.setText("更换要分享的 HTML（可选）");
        btnPick.setTextSize(14);
        btnPick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                it.addCategory(Intent.CATEGORY_OPENABLE);
                it.setType("text/html");
                startActivityForResult(it, REQ_PICK_HTML);
            }
        });
        root.addView(btnPick);
        root.addView(spacer(18));

        // ===== 公网访问区域 =====
        TextView secWan = new TextView(this);
        secWan.setText("公网访问（内网穿透）");
        secWan.setTextSize(16);
        secWan.setTextColor(Tools.textColor(this));
        root.addView(secWan);
        root.addView(spacer(6));

        wanStatusView = new TextView(this);
        wanStatusView.setText("公网未开启");
        wanStatusView.setTextSize(14);
        wanStatusView.setTextColor(Color.rgb(150, 120, 40));
        wanStatusView.setGravity(Gravity.CENTER);
        root.addView(wanStatusView);
        root.addView(spacer(6));

        wanUrlView = new TextView(this);
        wanUrlView.setText("开启后生成公网链接，任何网络的朋友都能访问（免费临时隧道）");
        wanUrlView.setTextSize(13);
        wanUrlView.setTextColor(Tools.subTextColor(this));
        wanUrlView.setGravity(Gravity.CENTER);
        root.addView(wanUrlView);
        root.addView(spacer(10));

        Button btnDiag = new Button(this);
        btnDiag.setText("一键网络诊断（挂 VPN 前后各跑一次对比）");
        btnDiag.setTextSize(14);
        btnDiag.setAllCaps(false);
        btnDiag.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runNetworkDiag();
            }
        });
        root.addView(btnDiag);
        root.addView(spacer(6));

        btnWan = new Button(this);
        btnWan.setText("开启公网访问");
        btnWan.setTextSize(15);
        btnWan.setAllCaps(false);
        btnWan.setTextColor(Color.WHITE);
        btnWan.setGravity(Gravity.CENTER);
        btnWan.setBackground(Tools.pillButton(this, 50, true));
        Icons.attachToText(btnWan, "globe", Color.WHITE, 16, 6);
        btnWan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (wanRunning) {
                    stopWanTunnel();
                } else {
                    // 保存出口代理端口（cloudflared 启动时读取）
                    try {
                        getSharedPreferences("server_cfg", MODE_PRIVATE).edit()
                                .putString("wan_proxy_port",
                                        proxyPort.getText().toString().trim()).apply();
                    } catch (Throwable ignored) {
                    }
                    startWanTunnel();
                }
            }
        });
        root.addView(btnWan);
        root.addView(spacer(6));

        btnCopyWan = new Button(this);
        btnCopyWan.setText("复制公网链接");
        btnCopyWan.setTextSize(14);
        btnCopyWan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = wanUrlView.getText().toString();
                if (url.startsWith("https://") || url.startsWith("http://")) {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("公网链接", url));
                    Toast.makeText(ServerActivity.this, "已复制，发给朋友即可", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ServerActivity.this, "还没有公网链接", Toast.LENGTH_SHORT).show();
                }
            }
        });
        root.addView(btnCopyWan);
        root.addView(spacer(10));

        // ===== frp 穿透（国内节点）=====
        TextView frpTitle = new TextView(this);
        frpTitle.setText("frp 穿透（国内节点 · 运营商封锁 Cloudflare 时用）");
        frpTitle.setTextSize(16);
        frpTitle.setTextColor(Tools.textColor(this));
        root.addView(frpTitle);
        root.addView(spacer(6));

        TextView frpHint = new TextView(this);
        frpHint.setText("国内免实名首选 OpenFrp(openfrp.net)：注册 → 创建 HTTP 隧道（本地端口 8080）→ 复制配置文件 → 一键识别。\n备选 樱花Frp(natfrp.com)（需实名）：流程相同。");
        frpHint.setTextSize(12);
        frpHint.setTextColor(Tools.subTextColor(this));
        root.addView(frpHint);
        root.addView(spacer(6));

        Button btnOpenFrp = new Button(this);
        btnOpenFrp.setText("打开 OpenFrp 官网（免实名，App 内嵌）");
        btnOpenFrp.setTextSize(14);
        btnOpenFrp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openWebPage("https://www.openfrp.net");
            }
        });
        root.addView(btnOpenFrp);
        root.addView(spacer(4));

        Button btnNatFrp = new Button(this);
        btnNatFrp.setText("打开樱花官网（需实名，App 内嵌）");
        btnNatFrp.setTextSize(14);
        btnNatFrp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openNatFrp();
            }
        });
        root.addView(btnNatFrp);
        root.addView(spacer(8));

        final EditText frpServer = new EditText(this);
        frpServer.setHint("节点地址（面板里的，含端口，如 us-1.sakura.frp:7000）");
        frpServer.setTextSize(14);
        frpServer.setTextColor(Tools.textColor(this));
        root.addView(frpServer);
        root.addView(spacer(6));

        final EditText frpToken = new EditText(this);
        frpToken.setHint("访问密钥（面板里的一长串 token）");
        frpToken.setTextSize(14);
        frpToken.setTextColor(Tools.textColor(this));
        root.addView(frpToken);
        root.addView(spacer(6));

        final EditText frpDomain = new EditText(this);
        frpDomain.setHint("访问域名（创建隧道时填的，如 myroom.sakura.frp）");
        frpDomain.setTextSize(14);
        frpDomain.setTextColor(Tools.textColor(this));
        root.addView(frpDomain);
        root.addView(spacer(8));

        // 出口代理端口（可选）：手机上有代理 App（如 Clash/V2Ray 本地端口）时填写，
        // cloudflared 将借代理出口绕开运营商劫持
        proxyPort = new EditText(this);
        proxyPort.setHint("出口代理端口（可选，如 7890；填了走代理绕开劫持）");
        proxyPort.setTextSize(14);
        proxyPort.setTextColor(Tools.textColor(this));
        proxyPort.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        try {
            String saved = getSharedPreferences("server_cfg", MODE_PRIVATE)
                    .getString("wan_proxy_port", "");
            if (!saved.isEmpty()) proxyPort.setText(saved);
        } catch (Throwable ignored) {
        }
        root.addView(proxyPort);
        root.addView(spacer(8));

        Button btnFrpScan = new Button(this);
        btnFrpScan.setText("一键识别：粘贴樱花配置自动填");
        btnFrpScan.setTextSize(14);
        btnFrpScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFrpConfigDialog(frpServer, frpToken, frpDomain);
            }
        });
        root.addView(btnFrpScan);
        root.addView(spacer(8));

        Button btnFrp = new Button(this);
        btnFrp.setText("frp 一键穿透（国内节点）");
        btnFrp.setTextSize(15);
        btnFrp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (wanRunning) {
                    stopWanTunnel();
                } else {
                    startFrpTunnel(frpServer.getText().toString(),
                            frpToken.getText().toString(),
                            frpDomain.getText().toString());
                }
            }
        });
        root.addView(btnFrp);
        root.addView(spacer(12));

        // 隧道日志面板
        TextView logTitle = new TextView(this);
        logTitle.setText("隧道日志（诊断用）：");
        logTitle.setTextSize(13);
        logTitle.setTextColor(Tools.subTextColor(this));
        root.addView(logTitle);
        root.addView(spacer(4));

        logScroll = new ScrollView(this);
        logScroll.setBackgroundColor(Tools.isDark(this) ? 0xFF1B2230 : Color.rgb(240, 242, 246));
        logScroll.setVerticalScrollBarEnabled(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(180));
        logScroll.setLayoutParams(lp);

        logView = new TextView(this);
        logView.setTextSize(11);
        logView.setTextColor(Tools.subTextColor(this));
        logView.setPadding(dp(10), dp(8), dp(10), dp(8));
        logView.setText("（暂无日志）");
        logScroll.addView(logView);
        root.addView(logScroll);
        root.addView(spacer(6));

        Button btnCopyLog = new Button(this);
        btnCopyLog.setText("复制全部日志");
        btnCopyLog.setTextSize(13);
        btnCopyLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String t = logView.getText().toString();
                if (!t.isEmpty() && !t.equals("（暂无日志）")) {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("隧道日志", t));
                    Toast.makeText(ServerActivity.this, "日志已复制", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ServerActivity.this, "暂无日志", Toast.LENGTH_SHORT).show();
                }
            }
        });
        root.addView(btnCopyLog);
        root.addView(spacer(18));

        TextView tip = new TextView(this);
        tip.setText("使用说明：\n"
                + "1. 点【启动服务器】\n"
                + "2. 局域网朋友：访问显示的 192.168.x.x 地址（同一 WiFi/热点）\n"
                + "3. 外地朋友：优先用【frp 一键穿透】（国内节点）；Cloudflare 按钮备用。\n\n"
                + "frp：1. 注册樱花Frp(natfrp.com) 实名；2. 创建 HTTP 隧道本地端口 8080；3. 复制配置文件 → 一键识别 → 穿透。\n"
                + "Cloudflare：免费临时隧道无需注册；若网络封锁（日志报 http: server gave HTTP response）请改用 frp。\n"
                + "隧道开启后通知栏常驻；点【关闭公网访问】才停止。");
        tip.setTextSize(12);
        tip.setTextColor(Tools.subTextColor(this));
        root.addView(tip);

        setContentView(scroll);
        render();
    }

    // ================= 公网隧道（委托前台服务）=================

    private void startWanTunnel() {
        if (server == null || !server.isRunning()) {
            Toast.makeText(this, "请先启动本地服务器", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 2001);
            }
        }
        if (!hasKernel("libcloudflared.so")) {
            promptKernel();
            return;
        }
        TunnelService.setListener(this);
        Intent it = new Intent(this, TunnelService.class);
        it.setAction(TunnelService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(it);
        } else {
            startService(it);
        }
        wanRunning = true;
        wanStatusView.setText("正在连接公网隧道…");
        wanStatusView.setTextColor(Color.rgb(200, 140, 20));
        wanUrlView.setText("正在建立隧道，首次约需 10~30 秒…");
        btnWan.setText("关闭公网访问");
        clearLog();
    }

    private void startFrpTunnel(String server, String token, String domain) {
        if (server == null || server.trim().isEmpty()
                || token == null || token.trim().isEmpty()
                || domain == null || domain.trim().isEmpty()) {
            Toast.makeText(this, "请先填满 frp 三项参数（节点地址/访问密钥/访问域名）", Toast.LENGTH_LONG).show();
            return;
        }
        if (this.server == null || !this.server.isRunning()) {
            Toast.makeText(this, "请先启动本地服务器", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 2001);
            }
        }
        if (!hasKernel("libfrpc.so")) {
            promptKernel();
            return;
        }
        TunnelService.setListener(this);
        Intent it = new Intent(this, TunnelService.class);
        it.setAction(TunnelService.ACTION_START_FRP);
        it.putExtra(TunnelService.EXTRA_FRP_SERVER, server.trim());
        it.putExtra(TunnelService.EXTRA_FRP_TOKEN, token.trim());
        it.putExtra(TunnelService.EXTRA_FRP_DOMAIN, domain.trim());
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(it);
        } else {
            startService(it);
        }
        wanRunning = true;
        wanStatusView.setText("正在连接 frp 节点…");
        wanStatusView.setTextColor(Color.rgb(200, 140, 20));
        wanUrlView.setText("正在建立隧道，首次约需 5~15 秒…");
        btnWan.setText("关闭公网访问");
        clearLog();
    }

    private void stopWanTunnel() {
        Intent it = new Intent(this, TunnelService.class);
        it.setAction(TunnelService.ACTION_STOP);
        startService(it);
        wanRunning = false;
        render();
    }

    boolean hasKernel(String name) {
        // 多路径：外部存储 → 内部私有目录 → 系统原生库目录（与 TunnelService.findBinary 一致）
        try {
            File ext = new File(KernelPaths.nativeDir(this), name);
            if (ext.exists() && ext.length() > 1000000) return true;
        } catch (Exception ignored) {
        }
        File f1 = new File(getFilesDir(), "native/" + name);
        if (f1.exists() && f1.length() > 1000000) return true;
        try {
            File f2 = new File(getApplicationInfo().nativeLibraryDir, name);
            if (f2.exists() && f2.length() > 1000000) return true;
        } catch (Exception ignored) {
        }
        try {
            File f3 = new File("/data/local/tmp/" + name);
            if (f3.exists() && f3.length() > 1000000) return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    void promptKernel() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("穿透内核未安装")
                .setMessage("为了把安装包从 30MB 瘦身到 9MB，穿透内核改为首次使用时下载（约 14MB，支持断点续传）。是否前往下载中心下载并自动安装？")
                .setPositiveButton("去下载", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        Intent it = new Intent(ServerActivity.this, DownloadActivity.class);
                        it.putExtra("url", "https://aka.doubaocdn.com/s/nliTro1FJI");
                        startActivity(it);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ================= TunnelService.Listener =================

    @Override
    public void onLog(final String line) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                appendLog(line);
            }
        });
    }

    @Override
    public void onUrl(final String url) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                wanStatusView.setText("公网已开启（隧道连通）");
                wanStatusView.setTextColor(Color.rgb(30, 140, 80));
                wanUrlView.setText(url);
                wanRunning = true;
                btnWan.setText("关闭公网访问");
                Toast.makeText(ServerActivity.this, "公网链接已生成", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onState(final int state, final String msg) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                wanRunning = (state == TunnelService.STATE_CONNECTING || state == TunnelService.STATE_CONNECTED);
                if (state == TunnelService.STATE_CONNECTING) {
                    wanStatusView.setText("正在连接公网隧道…");
                    wanStatusView.setTextColor(Color.rgb(200, 140, 20));
                    btnWan.setText("关闭公网访问");
                } else if (state == TunnelService.STATE_CONNECTED) {
                    wanStatusView.setText("公网已开启（隧道连通）");
                    wanStatusView.setTextColor(Color.rgb(30, 140, 80));
                    btnWan.setText("关闭公网访问");
                } else if (state == TunnelService.STATE_EXITED) {
                    wanStatusView.setText("公网隧道已断开");
                    wanStatusView.setTextColor(Color.rgb(150, 65, 65));
                    wanUrlView.setText(msg);
                    btnWan.setText("开启公网访问");
                } else {
                    wanStatusView.setText("公网开启失败");
                    wanStatusView.setTextColor(Color.rgb(150, 65, 65));
                    wanUrlView.setText(msg);
                    btnWan.setText("开启公网访问");
                }
            }
        });
    }

    // ================= frp 一键识别 =================

    /** 内嵌打开樱花 Frp 官网；打不开可用系统浏览器 */
    private void openWebPage(final String url) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(url.contains("openfrp") ? "OpenFrp 官网（免实名）" : "官网");
        final android.webkit.WebView wv = new android.webkit.WebView(this);
        android.webkit.WebSettings ws = wv.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        wv.setWebViewClient(new android.webkit.WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(android.webkit.WebView view, String u) {
                view.loadUrl(u);
                return true;
            }
        });
        int h = (int) (getResources().getDisplayMetrics().heightPixels * 0.82);
        wv.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h));
        wv.loadUrl(url);
        b.setView(wv);
        b.setNeutralButton("用系统浏览器打开", new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface d, int which) {
                try {
                    Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(it);
                } catch (Exception ignored) {
                }
            }
        });
        b.setNegativeButton("关闭", null);
        b.show();
    }

    private void openNatFrp() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("樱花 Frp 官网");
        final android.webkit.WebView wv = new android.webkit.WebView(this);
        android.webkit.WebSettings ws = wv.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        wv.setWebViewClient(new android.webkit.WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(android.webkit.WebView view, String url) {
                view.loadUrl(url);
                return true;
            }
        });
        int h = (int) (getResources().getDisplayMetrics().heightPixels * 0.82);
        wv.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h));
        wv.loadUrl("https://www.natfrp.com");
        b.setView(wv);
        b.setNeutralButton("用系统浏览器打开", new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface d, int which) {
                try {
                    Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.natfrp.com"));
                    startActivity(it);
                } catch (Exception e) {
                    Toast.makeText(ServerActivity.this, "没有可用的浏览器", Toast.LENGTH_SHORT).show();
                }
            }
        });
        b.setNegativeButton("关闭", null);
        b.show();
    }

    /** 粘贴樱花配置自动识别 */
    private void showFrpConfigDialog(final EditText serverBox, final EditText tokenBox, final EditText domainBox) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("粘贴樱花配置文件");
        final EditText input = new EditText(this);
        input.setHint("到樱花面板 → 隧道 → 配置文件 → 全选复制，整段粘贴到这里\n（包含 server_addr / token / custom_domains 那几行）");
        input.setTextSize(13);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(6);
        input.setGravity(Gravity.TOP | Gravity.START);
        b.setView(input);
        b.setPositiveButton("识别并填入", new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface d, int which) {
                String text = input.getText().toString();
                String[] r = parseFrpConfig(text);
                if (r[0] == null || r[1] == null || r[2] == null) {
                    Toast.makeText(ServerActivity.this,
                            "没识别全：请确认粘贴了 server_addr / token / custom_domains 三行", Toast.LENGTH_LONG).show();
                    return;
                }
                serverBox.setText(r[0]);
                tokenBox.setText(r[1]);
                domainBox.setText(r[2]);
                Toast.makeText(ServerActivity.this, "识别成功，三项已自动填入，点【frp 一键穿透】即可", Toast.LENGTH_LONG).show();
            }
        });
        b.setNegativeButton("取消", null);
        b.show();
    }

    private String[] parseFrpConfig(String text) {
        String server = null, token = null, domain = null;
        String port = "7000";
        if (text != null) {
            String t = text;
            java.util.regex.Matcher m;
            m = java.util.regex.Pattern.compile("(?i)server_?addr\\s*=\\s*([^\\s\\r\\n]+)").matcher(t);
            if (m.find()) server = m.group(1).trim();
            m = java.util.regex.Pattern.compile("(?i)server_?port\\s*=\\s*(\\d+)").matcher(t);
            if (m.find()) port = m.group(1);
            m = java.util.regex.Pattern.compile("(?i)(?:auth\\.?token|auth_token|token)\\s*=\\s*([^\\s\\r\\n]+)").matcher(t);
            if (m.find()) token = m.group(1).trim();
            m = java.util.regex.Pattern.compile("(?i)custom_?domains\\s*=\\s*[\\[\"]?\\s*([^\\s\\]\\\"\\r\\n,]+)").matcher(t);
            if (m.find()) domain = m.group(1).trim();
        }
        if (server != null && !server.contains(":")) server = server + ":" + port;
        return new String[]{server, token, domain};
    }

    // ================= 一键网络诊断 =================

    private void runNetworkDiag() {
        clearLog();
        appendLog("开始网络诊断 ...");
        appendLog("当前状态: VPN=" + vpnText() + " 网络=" + netTypeText());
        new Thread(new Runnable() {
            @Override
            public void run() {
                diagTls("Cloudflare API (trycloudflare.com:443)", "https://api.trycloudflare.com/tunnel", "cloudflare");
                diagGet("OpenFrp 官网 (国内免实名)", "https://www.openfrp.net", "openfrp");
                diagGet("樱花Frp 官网", "https://www.natfrp.com", "natfrp");
                appendLog("诊断完成。");
                appendLog("结论：Cloudflare=通 → 直接开穿透即可；不通 → 挂 VPN 后重跑，VPN 下通 → 开穿透走 VPN 出口。");
                appendLog("VPN 和 OpenFrp 都不通 = 当前网络整体受限，需切换网络。");
            }
        }).start();
    }

    private String vpnText() {
        try {
            android.net.ConnectivityManager cm =
                    (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm == null) return "未知";
            for (android.net.Network n : cm.getAllNetworks()) {
                android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(n);
                if (caps != null && caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)) {
                    return "已开启";
                }
            }
        } catch (Throwable ignored) {
        }
        return "未开启";
    }

    private String netTypeText() {
        try {
            android.net.ConnectivityManager cm =
                    (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
            if (ni == null) return "无网络";
            return ni.getTypeName();
        } catch (Throwable t) {
            return "未知";
        }
    }

    private void diagTls(String label, final String url, final String tag) {
        try {
            java.net.URL u = new java.net.URL(url);
            javax.net.ssl.HttpsURLConnection c =
                    (javax.net.ssl.HttpsURLConnection) u.openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestMethod("GET");
            int code = c.getResponseCode();
            c.disconnect();
            if (code == 403 || code == 429) {
                appendLog("[通] " + label + " → HTTP " + code + "（TLS 正常，属风控/限流）");
            } else {
                appendLog("[通] " + label + " → HTTP " + code);
            }
        } catch (javax.net.ssl.SSLException e) {
            appendLog("[劫持] " + label + " → TLS 异常: " + e.getMessage());
        } catch (java.io.IOException e) {
            appendLog("[不通] " + label + " → " + e.getMessage());
        } catch (Throwable e) {
            appendLog("[异常] " + label + " → " + e.getClass().getSimpleName());
        }
    }

    private void diagGet(String label, final String url, final String tag) {
        try {
            java.net.URL u = new java.net.URL(url);
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) u.openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setInstanceFollowRedirects(true);
            int code = c.getResponseCode();
            c.disconnect();
            appendLog("[通] " + label + " → HTTP " + code);
        } catch (java.io.IOException e) {
            appendLog("[不通] " + label + " → " + e.getMessage());
        } catch (Throwable e) {
            appendLog("[异常] " + label + " → " + e.getClass().getSimpleName());
        }
    }

    // ================= 日志 =================

    private void appendLog(final String line) {
        if (logView == null) return;
        String cur = logView.getText().toString();
        if (cur.equals("（暂无日志）")) cur = "";
        String text = cur + line + "\n";
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') count++;
        }
        if (count > 150) {
            int idx = text.indexOf('\n');
            text = text.substring(idx + 1);
        }
        logView.setText(text);
        logScroll.post(new Runnable() {
            @Override
            public void run() {
                logScroll.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    private void clearLog() {
        if (logView != null) logView.setText("（暂无日志）");
    }

    // ================= 渲染 =================

    private void render() {
        boolean running = server != null && server.isRunning();
        if (running) {
            statusView.setText("服务器运行中 (端口 " + PORT + ")");
            statusView.setTextColor(Color.rgb(30, 140, 80));
            btnToggle.setText("停止服务器");
            List<String> addrs = new ArrayList<String>();
            for (String ip : lanIps()) {
                addrs.add("http://" + ip + ":" + PORT);
            }
            StringBuilder sb = new StringBuilder();
            sb.append("本机：http://127.0.0.1:").append(PORT);
            if (!addrs.isEmpty()) {
                sb.append("\n\n局域网（发给朋友）：\n");
                for (int i = 0; i < addrs.size(); i++) {
                    sb.append(addrs.get(i));
                    if (i < addrs.size() - 1) sb.append("\n");
                }
            } else {
                sb.append("\n\n（未检测到 WiFi 地址，请确认已连接 WiFi）");
            }
            addrView.setText(sb.toString());
        } else {
            statusView.setText("服务器未启动");
            statusView.setTextColor(Color.rgb(150, 65, 65));
            btnToggle.setText("启动服务器");
            addrView.setText("——");
        }
        if (!wanRunning) {
            wanStatusView.setText("公网未开启");
            wanStatusView.setTextColor(Color.rgb(150, 120, 40));
            wanUrlView.setText("先启动本地服务器，再开启公网访问");
            btnWan.setText("开启公网访问");
        }
    }

    private List<String> lanIps() {
        List<String> out = new ArrayList<String>();
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            if (nis == null) return out;
            for (NetworkInterface ni : Collections.list(nis)) {
                if (!ni.isUp()) continue;
                Enumeration<java.net.InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress ia = addrs.nextElement();
                    if (!(ia instanceof Inet4Address)) continue;
                    String s = ia.getHostAddress();
                    if (s == null) continue;
                    if (s.startsWith("192.168.") || s.startsWith("10.") || s.startsWith("172.")) {
                        out.add(s);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private View spacer(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        return v;
    }
}
