package com.roomarchive.server;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class DownloadActivity extends Activity {
    LinearLayout listRoot;
    Handler ui = new Handler(Looper.getMainLooper());

    static class Plugin {
        String name;
        String desc;
        String[] urls;
        String fileName;
        Plugin(String n, String d, String f, String... u) {
            name = n; desc = d; urls = u; fileName = f;
        }
    }

    Plugin[] plugins = {
        new Plugin("IK工具箱最新版", "App 本体更新包 · 自动取线上最新版，覆盖安装保留数据", "IK工具箱-latest.apk",
            "https://aka.doubaocdn.com/s/tzDln6K9Fd"),
        new Plugin("穿透内核", "内网穿透必需 · cloudflared + frpc · 约 14MB", "kernel.zip",
            "https://aka.doubaocdn.com/s/xune67xCpU"),
    };

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        FileStore.ensurePermission(this);
        Tools.applyStatusBar(this);
        ScrollView sv = new ScrollView(this);
        listRoot = new LinearLayout(this);
        listRoot.setOrientation(LinearLayout.VERTICAL);
        listRoot.setPadding(dp(16), dp(20), dp(16), dp(16));
        listRoot.setBackground(Tools.bgWithGlow(this));
        listRoot.addView(Tools.pageTitle(this, "下载中心"));

        TextView hint = new TextView(this);
        hint.setText("插件下载 · 自动保存到我的文件");
        hint.setTextSize(13);
        hint.setPadding(0, dp(4), 0, dp(16));
        listRoot.addView(hint);

        for (Plugin p : plugins) addPluginCard(p);
        addAiModelCards();
        refreshLatestApkUrl();

        TextView filesTitle = new TextView(this);
        filesTitle.setText("已下载文件");
        filesTitle.setTextSize(16);
        filesTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        filesTitle.setPadding(0, dp(24), 0, dp(8));
        listRoot.addView(filesTitle);
        refreshFiles();

        sv.addView(listRoot);
        setContentView(sv);
    }

    /** 每次打开下载中心都从线上 version.txt 取最新 APK 地址，避免写死后发新版还下旧包 */
    void refreshLatestApkUrl() {
        new Thread(() -> {
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                        new java.net.URL("https://3115703202.github.io/ik-tools/version.txt").openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(c.getInputStream()));
                String line, url = null;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith("URL=")) url = line.substring(4).trim();
                }
                br.close();
                if (url != null && url.startsWith("https://") && plugins.length > 0) {
                    plugins[0].urls = new String[]{url};
                }
            } catch (Throwable ignored) {}
        }).start();
    }

    static final String AI_MODEL = "qwen2.5-0.5b-instruct-q4_k_m.gguf";

    static class AiModel {
        String file, title, desc, hf, mirror;
        AiModel(String f, String t, String d, String repo) {
            file = f; title = t; desc = d;
            hf = "https://huggingface.co/" + repo + "/resolve/main/" + f;
            mirror = "https://hf-mirror.com/" + repo + "/resolve/main/" + f;
        }
    }

    AiModel[] aiModels = {
        new AiModel(AI_MODEL, "Qwen2.5 0.5B", "491MB · 最快，短答约 1 秒", "Qwen/Qwen2.5-0.5B-Instruct-GGUF"),
        new AiModel("qwen2.5-1.5b-instruct-q4_k_m.gguf", "Qwen2.5 1.5B", "约 1.1GB · 更聪明，速度稍慢", "Qwen/Qwen2.5-1.5B-Instruct-GGUF"),
        new AiModel("tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf", "TinyLlama 1.1B", "约 669MB · 轻量英文/中文", "TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF"),
        new AiModel("Llama-3.2-1B-Instruct-Q4_K_M.gguf", "Llama 3.2 1B", "约 800MB · 通用对话", "bartowski/Llama-3.2-1B-Instruct-GGUF")
    };

    void addAiModelCards() {
        for (AiModel m : aiModels) addAiModelCard(m);
    }

    void addAiModelCard(AiModel m) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackgroundColor(0x22FFFFFF);

        TextView name = new TextView(this);
        name.setText("本地 AI 模型 · " + m.title);
        name.setTextSize(16);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        card.addView(name);

        TextView desc = new TextView(this);
        desc.setText(m.desc + " · 断点续传 / 双镜像");
        desc.setTextSize(13);
        desc.setPadding(0, dp(4), 0, dp(10));
        card.addView(desc);

        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(100);
        pb.setVisibility(View.GONE);
        card.addView(pb);

        TextView pct = new TextView(this);
        pct.setTextSize(12);
        pct.setVisibility(View.GONE);
        card.addView(pct);

        Button dlBtn = new Button(this);
        java.io.File model = new java.io.File(new java.io.File(FileStore.outDir(this), "models"), m.file);
        if (model.exists() && model.length() > 100_000_000L) {
            dlBtn.setText("已下载 · 打开本地 AI");
            dlBtn.setOnClickListener(v -> startActivity(new android.content.Intent(this, LocalAiActivity.class)));
        } else {
            dlBtn.setText("下载模型（" + m.desc.split(" · ")[0] + "）");
            dlBtn.setOnClickListener(v -> {
                dlBtn.setEnabled(false);
                pb.setVisibility(View.VISIBLE);
                pct.setVisibility(View.VISIBLE);
                downloadAiModel(model, pb, pct, dlBtn, new String[]{m.hf, m.mirror});
            });
        }
        card.addView(dlBtn);
        listRoot.addView(card);
    }

    void downloadAiModel(java.io.File dest, ProgressBar pb, TextView pct, Button btn, String[] urls) {
        new Thread(() -> {
            java.io.File dir = dest.getParentFile();
            if (!dir.exists()) dir.mkdirs();
            java.io.File tmp = new java.io.File(dir, dest.getName() + ".part");
            long have = tmp.exists() ? tmp.length() : 0;
            boolean ok = false;
            for (String urlStr : urls) {
                try {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(urlStr).openConnection();
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(20000);
                    c.setRequestProperty("User-Agent", "IK-Tools");
                    if (have > 0) c.setRequestProperty("Range", "bytes=" + have + "-");
                    int code = c.getResponseCode();
                    if (code != 200 && code != 206) throw new java.io.IOException("HTTP " + code);
                    long total = c.getContentLengthLong();
                    if (total > 0) total += have;
                    java.io.InputStream is = new java.io.BufferedInputStream(c.getInputStream(), 8192);
                    java.io.OutputStream os = new java.io.FileOutputStream(tmp, have > 0);
                    byte[] buf = new byte[8192];
                    int r;
                    long got = have, last = 0, lastGot = have, lastTime = System.currentTimeMillis();
                    while ((r = is.read(buf)) > 0) {
                        os.write(buf, 0, r);
                        got += r;
                        long now = System.currentTimeMillis();
                        if (now - last > 250) {
                            last = now;
                            final long g2 = got, t2 = total;
                            final double sp = (got - lastGot) / 1000000.0 / Math.max(0.25, (now - lastTime) / 1000.0);
                            lastGot = got; lastTime = now;
                            runOnUiThread(() -> {
                                if (t2 > 0) {
                                    int p = (int) (g2 * 100 / t2);
                                    pb.setProgress(p);
                                    pct.setText(String.format("%d%% · %.0f/%.0f MB · %.1f MB/s", p,
                                            g2 / 1000000.0, t2 / 1000000.0, sp));
                                } else {
                                    pct.setText(String.format("%.0f MB · %.1f MB/s", g2 / 1000000.0, sp));
                                }
                            });
                        }
                    }
                    os.flush(); os.close(); is.close();
                    if (tmp.length() < 100_000_000L) throw new java.io.IOException("文件不完整");
                    if (dest.exists()) dest.delete();
                    if (!tmp.renameTo(dest)) throw new java.io.IOException("保存失败");
                    ok = true;
                    break;
                } catch (Throwable e) {
                    have = tmp.exists() ? tmp.length() : 0;
                    final String msg = e.toString();
                    runOnUiThread(() -> pct.setText("换源重试：" + msg));
                }
            }
            final boolean fOk = ok;
            runOnUiThread(() -> {
                btn.setEnabled(true);
                pb.setVisibility(fOk ? View.GONE : View.VISIBLE);
                if (fOk) {
                    btn.setText("已下载 · 打开本地 AI");
                    btn.setOnClickListener(v -> startActivity(new android.content.Intent(this, LocalAiActivity.class)));
                    pct.setText("模型就绪，可离线对话");
                    Toast.makeText(this, "模型下载完成", Toast.LENGTH_SHORT).show();
                } else {
                    btn.setText("重试下载");
                    pct.setText("下载失败，检查网络后重试（支持断点续传）");
                }
            });
        }).start();
    }

    void addPluginCard(Plugin p) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackgroundColor(0x22FFFFFF);

        TextView name = new TextView(this);
        name.setText(p.name);
        name.setTextSize(16);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        card.addView(name);

        TextView desc = new TextView(this);
        desc.setText(p.desc);
        desc.setTextSize(13);
        desc.setPadding(0, dp(4), 0, dp(10));
        card.addView(desc);

        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(100);
        pb.setVisibility(View.GONE);
        card.addView(pb);

        TextView pct = new TextView(this);
        pct.setTextSize(12);
        pct.setVisibility(View.GONE);
        card.addView(pct);

        Button dlBtn = new Button(this);
        java.io.File existing = new java.io.File(FileStore.outDir(this), "下载/" + p.fileName);
        if (existing.exists() && existing.length() > 1024) {
            if (p.fileName.endsWith(".zip")) {
                dlBtn.setText("已下载 · 点击安装内核");
                dlBtn.setOnClickListener(v -> installKernel(existing));
            } else {
                dlBtn.setText("已下载 · 点击打开");
                dlBtn.setOnClickListener(v -> openFile(existing));
            }
        } else {
            dlBtn.setText("下载");
            dlBtn.setOnClickListener(v -> {
                dlBtn.setEnabled(false);
                pb.setVisibility(View.VISIBLE);
                pct.setVisibility(View.VISIBLE);
                startDownload(p, pb, pct, dlBtn);
            });
        }
        card.addView(dlBtn);
        listRoot.addView(card);
    }

    void startDownload(Plugin p, ProgressBar pb, TextView pct, Button btn) {
        ui.post(() -> pct.setText("连接中..."));
        new Thread(() -> {
            java.io.File dir = new java.io.File(FileStore.outDir(this), "下载");
            dir.mkdirs();
            java.io.File out = new java.io.File(dir, p.fileName);
            java.io.File tmp = new java.io.File(dir, p.fileName + ".part");

            Exception lastError = null;
            String lastUrl = "";
            boolean ok = false;

            java.util.List<String> candidates = new java.util.ArrayList<>();
            if (p.fileName.endsWith(".apk")) {
                String latest = resolveLatestUrl();
                if (latest != null && !candidates.contains(latest)) candidates.add(latest);
            }
            for (String u : p.urls) if (!candidates.contains(u)) candidates.add(u);

            for (String u : candidates) {
                lastUrl = u;
                try {
                    if (tmp.exists()) tmp.delete();
                    downloadTo(u, tmp, pb, pct);
                    long size = tmp.length();
                    if (size < 1024) throw new Exception("文件内容为空");
                    if (out.exists()) out.delete();
                    if (!tmp.renameTo(out)) throw new Exception("文件写入失败");
                    ok = true;
                    break;
                } catch (Exception e) {
                    lastError = e;
                    if (tmp.exists()) tmp.delete();
                }
            }

            final boolean success = ok;
            final Exception err = lastError;
            final String used = lastUrl;

            ui.post(() -> {
                pb.setVisibility(View.GONE);
                pct.setVisibility(View.GONE);
                btn.setEnabled(true);
                if (success) {
                    Toast.makeText(this, "下载完成：" + p.fileName, Toast.LENGTH_LONG).show();
                    if (p.fileName.endsWith(".zip")) {
                        btn.setText("已下载 · 点击安装内核");
                        btn.setOnClickListener(v -> installKernel(out));
                    } else {
                        btn.setText("已下载 · 点击打开");
                        btn.setOnClickListener(v -> openFile(out));
                    }
                    refreshFiles();
                } else {
                    btn.setText("重试");
                    btn.setOnClickListener(v -> {
                        btn.setEnabled(false);
                        pb.setVisibility(View.VISIBLE);
                        pct.setVisibility(View.VISIBLE);
                        pb.setProgress(0);
                        startDownload(p, pb, pct, btn);
                    });
                    String reason = describeError(err);
                    pct.setText("下载失败：" + reason);
                    pct.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "下载失败：" + reason, Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    /** 从官网 version.txt 取最新版 APK 地址（取不到就用内置兜底） */
    String resolveLatestUrl() {
        try {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                    new java.net.URL("https://3115703202.github.io/ik-tools/version.txt").openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(6000);
            java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(c.getInputStream(), "UTF-8"));
            String line;
            String url = null;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("URL=")) url = line.substring(4).trim();
            }
            br.close();
            return url;
        } catch (Exception e) {
            return null;
        }
    }

    /** 单次下载，带进度；失败抛异常 */
    void downloadTo(String urlStr, java.io.File dest, ProgressBar pb, TextView pct) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(45000);
        conn.setRequestMethod("GET");
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "IKToolbox/1.0");
        conn.connect();

        int code = conn.getResponseCode();
        if (code != 200 && code != 206) {
            conn.disconnect();
            throw new Exception("HTTP " + code);
        }
        int total = conn.getContentLength();

        ui.post(() -> pct.setText(total > 0
                ? "连接成功 · 共 " + (total / 1024 / 1024) + "MB，开始下载…"
                : "连接成功 · 开始下载…"));

        InputStream is = conn.getInputStream();
        FileOutputStream fos = new FileOutputStream(dest);
        byte[] buf = new byte[8192];
        int r;
        long done = 0;
        long lastPost = 0;
        try {
            while ((r = is.read(buf)) > 0) {
                fos.write(buf, 0, r);
                done += r;
                long now = System.currentTimeMillis();
                if (now - lastPost > 200) {
                    lastPost = now;
                    int pctVal = total > 0 ? (int) (done * 100 / total) : 0;
                    final long d = done;
                    final int t = total;
                    final int pv = pctVal;
                    ui.post(() -> {
                        pb.setProgress(pv);
                        pct.setText(t > 0
                                ? pv + "% · " + (d / 1024 / 1024) + "MB / " + (t / 1024 / 1024) + "MB"
                                : (d / 1024 / 1024) + "MB 已下载");
                    });
                }
            }
        } finally {
            try { is.close(); } catch (Exception ignored) {}
            try { fos.close(); } catch (Exception ignored) {}
            conn.disconnect();
        }

        if (total > 0 && done < total * 0.99) {
            throw new Exception("传输中断（" + (done / 1024) + "KB / " + (total / 1024) + "KB）");
        }
    }

    String describeError(Exception e) {
        if (e == null) return "未知错误";
        String m = e.getMessage();
        if (m == null) m = e.getClass().getSimpleName();
        if (m.contains("HTTP 404")) return "下载地址已失效（404），请联系更新";
        if (m.contains("HTTP")) return "服务器返回 " + m.replace("HTTP ", "HTTP ");
        if (m.contains("Unable to resolve host") || m.contains("dns")) return "网络无法解析下载域名";
        if (e instanceof java.net.SocketTimeoutException) return "网络超时，请检查网络后重试";
        if (e instanceof java.net.UnknownHostException) return "网络无法解析下载域名";
        if (m.contains("ENOSPC") || m.contains("No space")) return "存储空间不足";
        return m;
    }

    void installKernel(java.io.File zip) {
        Toast.makeText(this, "正在安装穿透内核…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            boolean ok = KernelInstaller.installFromZip(this, zip, null);
            ui.post(() -> Toast.makeText(this,
                    ok ? "穿透内核安装完成，可返回开启隧道" : "安装失败，请重新下载内核包",
                    Toast.LENGTH_LONG).show());
        }).start();
    }

    void openFile(java.io.File f) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            String type = f.getName().endsWith(".apk") ? "application/vnd.android.package-archive" : "*/*";
            i.setDataAndType(IkFileProvider.uriFor(this, f), type);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    LinearLayout fileBox;

    void refreshFiles() {
        if (fileBox == null) {
            fileBox = new LinearLayout(this);
            fileBox.setOrientation(LinearLayout.VERTICAL);
            listRoot.addView(fileBox);
        }
        fileBox.removeAllViews();

        java.io.File dir = new java.io.File(FileStore.outDir(this), "下载");
        java.io.File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            TextView empty = new TextView(this);
            empty.setText("暂无下载文件");
            empty.setTextSize(13);
            empty.setTextColor(Tools.subTextColor(this));
            empty.setPadding(0, dp(8), 0, 0);
            fileBox.addView(empty);
            return;
        }
        java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (java.io.File f : files) {
            if (f.getName().endsWith(".part")) continue;
            TextView row = new TextView(this);
            long kb = f.length() / 1024;
            String size = kb >= 1024 ? (kb / 1024) + "MB" : kb + "KB";
            row.setText(f.getName() + "   " + size);
            row.setTextSize(13);
            row.setTextColor(Tools.textColor(this));
            row.setPadding(0, dp(8), 0, dp(8));
            row.setOnClickListener(v -> openFile(f));
            fileBox.addView(row);
        }
    }

    int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
}
