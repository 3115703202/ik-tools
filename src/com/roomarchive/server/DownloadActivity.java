package com.roomarchive.server;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 下载中心：内置下载器，支持断点续传、实时进度、完成后一键安装。
 * 解决系统下载器在大文件/弱网下卡 0% 的问题。
 */
public class DownloadActivity extends Activity {

    private final Handler ui = new Handler(Looper.getMainLooper());
    private EditText urlInput;
    private Button btnAction;
    private ProgressBar bar;
    private TextView statusView, sizeView, speedView;
    private LinearLayout doneList;

    private volatile boolean running = false;
    private volatile boolean cancel = false;
    private Thread worker;
    private long total = 0, done = 0;
    private String currentUrl = "";
    private File partFile;
    private String baseName = "";

    private static final String PREFS = "ik_downloads";
    private static final String PREFS_KEYS = "done_files";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Tools.applyStatusBar(this);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Tools.bgWithGlow(this));
        root.setPadding(dp(16), dp(20), dp(16), dp(18));
        scroll.setClipToPadding(false);

        TextView title = Tools.pageTitle(this, "下载中心");
        root.addView(title);

        TextView tip = new TextView(this);
        tip.setText("内置下载器：断点续传、实时进度，下载 IK 工具箱最新版不再卡住。");
        tip.setTextSize(13);
        tip.setTextColor(Tools.subTextColor(this));
        tip.setPadding(0, dp(6), 0, dp(14));
        root.addView(tip);

        urlInput = new EditText(this);
        String preset = getIntent().getStringExtra("url");
        if (preset == null || preset.isEmpty()) preset = "https://aka.doubaocdn.com/s/WSaXEpJvZ6";
        urlInput.setText(preset);
        urlInput.setTextSize(14);
        urlInput.setTextColor(Tools.textColor(this));
        urlInput.setSingleLine(true);
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(Tools.cardColor(this));
        inputBg.setCornerRadius(Tools.dp(this, 12));
        inputBg.setStroke(Tools.dp(this, 1),
                Tools.isDark(this) ? 0xFF2A3040 : 0xFFE4E8EE);
        urlInput.setBackground(inputBg);
        root.addView(urlInput);

        btnAction = new Button(this);
        btnAction.setText("开始下载");
        btnAction.setTextSize(16);
        btnAction.setTextColor(0xFFFFFFFF);
        btnAction.setBackground(Tools.pillButton(this, 50, true));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(12);
        root.addView(btnAction, blp);

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        LinearLayout.LayoutParams blp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(12));
        blp2.topMargin = dp(20);
        root.addView(bar, blp2);

        statusView = new TextView(this);
        statusView.setText("等待下载");
        statusView.setTextSize(14);
        statusView.setTextColor(Tools.textColor(this));
        statusView.setPadding(0, dp(8), 0, 0);
        root.addView(statusView);

        sizeView = new TextView(this);
        sizeView.setTextSize(12);
        sizeView.setTextColor(Tools.subTextColor(this));
        sizeView.setPadding(0, dp(2), 0, 0);
        root.addView(sizeView);

        speedView = new TextView(this);
        speedView.setTextSize(12);
        speedView.setTextColor(Tools.subTextColor(this));
        root.addView(speedView);

        TextView doneTitle = Tools.sectionTitle(this, "已下载");
        doneTitle.setPadding(0, dp(18), 0, dp(6));
        root.addView(doneTitle);

        doneList = new LinearLayout(this);
        doneList.setOrientation(LinearLayout.VERTICAL);
        root.addView(doneList);

        scroll.addView(root);
        setContentView(scroll);

        // 从网盘解析跳转过来：自动开始下载
        if (getIntent().getBooleanExtra("auto_start", false)) {
            scroll.postDelayed(() -> { if (btnAction != null) btnAction.performClick(); }, 300);
        }

        btnAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (running) {
                    cancel = true;
                    running = false;
                    btnAction.setText("继续");
                    statusView.setText("已暂停，进度已保存，点继续可断点续传");
                } else {
                    startDownload();
                }
            }
        });

        renderDoneList();
    }

    void startDownload() {
        String url = urlInput.getText().toString().trim();
        if (url.isEmpty()) {
            toast("请输入下载地址");
            return;
        }
        currentUrl = url;
        baseName = nameFromUrl(url);
        running = true;
        cancel = false;
        btnAction.setText("暂停");
        statusView.setText("连接中…");
        speedView.setText("");
        bar.setProgress(0);
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                doDownload();
            }
        });
        worker.start();
    }

    void doDownload() {
        HttpURLConnection conn = null;
        try {
            URL u = new URL(currentUrl);
            conn = (HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true);
            long from = 0;
            if (partFile != null && partFile.exists()) from = partFile.length();
            if (from > 0) conn.setRequestProperty("Range", "bytes=" + from + "-");
            conn.connect();
            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP
                    || code == 307 || code == 303 || code == 301) {
                String loc = conn.getHeaderField("Location");
                conn.disconnect();
                conn = null;
                if (loc != null) {
                    final String l = loc;
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            urlInput.setText(l);
                        }
                    });
                    currentUrl = loc;
                    baseName = nameFromUrl(loc);
                    doDownload();
                    return;
                }
                fail("服务器未给出跳转地址");
                return;
            }
            if (code != 200 && code != 206) {
                fail("服务器响应异常: HTTP " + code);
                return;
            }
            long contentLen = conn.getContentLengthLong();
            if (code == 200) {
                total = contentLen;
                done = 0;
                partFile = new File(dir(), baseName + ".part");
                if (partFile.exists()) partFile.delete();
            } else if (code == 206) {
                total = contentLen + from;
                done = from;
            }
            ui.post(new Runnable() {
                @Override
                public void run() {
                    statusView.setText("下载中…");
                }
            });
            final long[] last = {System.currentTimeMillis(), done};
            InputStream in = conn.getInputStream();
            RandomAccessFile raf = new RandomAccessFile(partFile, "rw");
            raf.seek(done);
            byte[] buf = new byte[65536];
            int n;
            while (!cancel && (n = in.read(buf)) > 0) {
                raf.write(buf, 0, n);
                done += n;
                long now = System.currentTimeMillis();
                if (now - last[0] > 500) {
                    final long d = done;
                    final long t = total;
                    long spd = (d - last[1]) * 1000 / (now - last[0]);
                    last[0] = now;
                    last[1] = d;
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            updateProgress(d, t, spd);
                        }
                    });
                }
            }
            raf.close();
            in.close();
            if (conn != null) conn.disconnect();
            conn = null;
            if (cancel) {
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        statusView.setText("已暂停，点继续可断点续传");
                    }
                });
                return;
            }
            final File target = new File(dir(), baseName);
            if (target.exists()) target.delete();
            partFile.renameTo(target);
            partFile = null;
            // 穿透内核包：自动解压到内部私有目录（首选执行路径），并同步一份到外部存储兜底
            boolean kernelZip = baseName.contains("穿透内核") || baseName.contains("kernel");
            if (kernelZip) {
                try {
                    File nDir = KernelPaths.ensureBoth(DownloadActivity.this);
                    if (!nDir.exists()) nDir.mkdirs();
                    unzip(target, nDir);
                    File[] ks = nDir.listFiles();
                    if (ks != null) {
                        for (File k : ks) KernelPaths.chmodKernel(nDir, k);
                    }
                    KernelPaths.ensureBoth(DownloadActivity.this);   // 同步外部存储副本
                } catch (Exception e) {
                    fail("内核解压失败: " + e.getMessage());
                    return;
                }
            }
            final boolean kz = kernelZip;
            ui.post(new Runnable() {
                @Override
                public void run() {
                    running = false;
                    btnAction.setText("开始下载");
                    bar.setProgress(1000);
                    statusView.setTextColor(Tools.isDark(DownloadActivity.this) ? 0xFF22C55E : 0xFF16A34A);
                    statusView.setText(kz ? "下载完成，穿透内核已就绪" : "下载完成: " + target.getName());
                    speedView.setText("");
                    sizeView.setText(fmt(target.length()) + (kz ? " · 已自动安装到服务器工具" : " · 保存在应用下载目录"));
                    remember(target.getAbsolutePath());
                    renderDoneList();
                }
            });
        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Throwable ignored) {
                }
            }
            if (!cancel) {
                final String m = e.getMessage() == null ? e.toString() : e.getMessage();
                fail("下载失败: " + m);
            }
        } finally {
            running = false;
        }
    }

    void updateProgress(long d, long t, long spd) {
        int pct = t > 0 ? (int) (d * 1000 / t) : 0;
        bar.setProgress(pct);
        sizeView.setText(fmt(d) + " / " + fmt(t));
        speedView.setText(fmtSpeed(spd));
    }

    void fail(final String msg) {
        ui.post(new Runnable() {
            @Override
            public void run() {
                running = false;
                btnAction.setText("开始下载");
                statusView.setTextColor(Tools.isDark(DownloadActivity.this) ? 0xFFF87171 : 0xFFDC2626);
                statusView.setText(msg);
                speedView.setText("");
                toast(msg);
            }
        });
    }

    void renderDoneList() {
        doneList.removeAllViews();
        List<String> files = remembered();
        if (files.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无下载记录");
            empty.setTextSize(13);
            empty.setTextColor(Tools.subTextColor(this));
            empty.setPadding(0, dp(8), 0, 0);
            doneList.addView(empty);
            return;
        }
        for (final String path : files) {
            final File f = new File(path);
            if (!f.exists()) continue;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(14), dp(10), dp(14), dp(10));
            row.setBackground(Tools.liquidGlassCard(this, 14));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = dp(8);
            row.setLayoutParams(rlp);

            TextView name = new TextView(this);
            name.setText(f.getName());
            name.setTextSize(14);
            name.setTextColor(Tools.textColor(this));
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            row.addView(name);

            TextView meta = new TextView(this);
            meta.setText(fmt(f.length()) + (f.getName().endsWith(".apk") ? " · 安装包" : ""));
            meta.setTextSize(12);
            meta.setTextColor(Tools.subTextColor(this));
            meta.setPadding(0, dp(2), 0, dp(4));
            row.addView(meta);

            Button open = new Button(this);
            open.setText(f.getName().endsWith(".apk") ? "安装" : "打开");
            open.setTextSize(14);
            open.setTextColor(Tools.accentColor(this));
            open.setBackgroundColor(0x00000000);
            open.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openFile(f);
                }
            });
            row.addView(open, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            doneList.addView(row);
        }
    }

    void openFile(File f) {
        try {
            Uri uri = Uri.parse("content://com.roomarchive.server.files/dl/" + Uri.encode(f.getName()));
            Intent it = new Intent(Intent.ACTION_VIEW);
            it.setDataAndType(uri, f.getName().endsWith(".apk")
                    ? "application/vnd.android.package-archive"
                    : guessMime(f.getName()));
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(it);
        } catch (Exception e) {
            toast("无法打开: " + e.getMessage());
        }
    }

    String guessMime(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".zip")) return "application/zip";
        if (n.endsWith(".txt")) return "text/plain";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".pdf")) return "application/pdf";
        return "application/octet-stream";
    }

    void unzip(File zip, File destDir) throws Exception {
        ZipFile zf = new ZipFile(zip);
        Enumeration<? extends ZipEntry> en = zf.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            if (e.isDirectory()) continue;
            File out = new File(destDir, e.getName());
            FileOutputStream fos = new FileOutputStream(out);
            InputStream is = zf.getInputStream(e);
            byte[] b = new byte[65536];
            int n;
            while ((n = is.read(b)) > 0) fos.write(b, 0, n);
            fos.close();
            is.close();
            out.setExecutable(true);
        }
        zf.close();
    }

    String nameFromUrl(String url) {
        if (url.contains("bg20OVZlqv") || url.contains("sxX1tMWiqN") || url.contains("1UNUdJGgLt") || url.contains("nliTro1FJI")) return "穿透内核_kernel.zip";
        if (url.contains("JvqOj3tvP4") || url.contains("9xSATBZFJl")) return "IK工具箱安装包.apk";
        String tail = url.substring(url.lastIndexOf('/') + 1);
        tail = tail.split("[?]")[0];
        if (!tail.isEmpty() && tail.length() < 60 && tail.contains(".")) return tail;
        return "download_" + System.currentTimeMillis() + ".apk";
    }

    File dir() {
        File d = FileStore.outDir(this);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    void remember(String path) {
        List<String> list = remembered();
        if (!list.contains(path)) list.add(0, path);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size() && i < 8; i++) {
            if (i > 0) sb.append('\n');
            sb.append(list.get(i));
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREFS_KEYS, sb.toString()).apply();
    }

    List<String> remembered() {
        String s = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREFS_KEYS, "");
        List<String> out = new ArrayList<>();
        if (s.isEmpty()) return out;
        for (String line : s.split("\n")) {
            if (!line.trim().isEmpty()) out.add(line);
        }
        return out;
    }

    static String fmt(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return String.format(java.util.Locale.US, "%.1f KB", b / 1024.0);
        return String.format(java.util.Locale.US, "%.2f MB", b / 1048576.0);
    }

    static String fmtSpeed(long bps) {
        if (bps < 1024) return bps + " B/s";
        if (bps < 1024 * 1024) return String.format(java.util.Locale.US, "%.1f KB/s", bps / 1024.0);
        return String.format(java.util.Locale.US, "%.1f MB/s", bps / 1048576.0);
    }

    int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
