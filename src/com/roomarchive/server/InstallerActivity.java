package com.roomarchive.server;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 内置安装器：
 * 1) Shizuku 静默安装（shell 身份 pm install，完全不弹系统界面）
 * 2) Root 静默安装（su -c pm install）
 * 3) 普通安装 + 自动点击安装确认（没 root 没 Shizuku 时的兜底）
 */
public class InstallerActivity extends BaseActivity {

    private LinearLayout listBox;
    private TextView status, log;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        sv.setClipToPadding(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Tools.bgWithGlow(this));
        root.setPadding(dp(16), dp(20), dp(16), dp(24));
        sv.addView(root);

        TextView title = Tools.pageTitle(this, "安装器");
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(12);
        status.setLineSpacing(dp(4), 1f);
        status.setTextColor(Tools.subTextColor(this));
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(8), 0, dp(10));
        root.addView(status);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);

        Button refresh = pill("刷新 APK", true);
        refresh.setOnClickListener(v -> refreshList());
        actions.addView(refresh);

        Button pick = pill("选文件", false);
        pick.setOnClickListener(v -> pickFile());
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(42));
        pp.leftMargin = dp(8);
        pick.setLayoutParams(pp);
        actions.addView(pick);

        Button a11y = pill("自动确认", false);
        a11y.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            Toast.makeText(this, "在无障碍里找到「IK工具箱 安装确认」开启", Toast.LENGTH_LONG).show();
        });
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(42));
        ap.leftMargin = dp(8);
        a11y.setLayoutParams(ap);
        actions.addView(a11y);
        root.addView(actions);

        status.post(this::refreshStatus);

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lbp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lbp.topMargin = dp(12);
        listBox.setLayoutParams(lbp);
        root.addView(listBox);

        log = new TextView(this);
        log.setTextSize(12);
        log.setTextColor(Tools.subTextColor(this));
        log.setPadding(dp(10), dp(10), dp(10), dp(10));
        log.setTextIsSelectable(true);
        GradientDrawable lg = new GradientDrawable();
        lg.setColor(Tools.cardColor(this));
        lg.setCornerRadius(dp(12));
        log.setBackground(lg);
        LinearLayout.LayoutParams lgp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lgp.topMargin = dp(12);
        log.setLayoutParams(lgp);
        log.setText("日志：等待安装");
        root.addView(log);

        setContentView(sv);
        refreshList();
    }

    private Button pill(String text, boolean primary) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(primary ? Color.WHITE : Tools.accentColor(this));
        b.setBackground(primary ? Tools.pillButton(this, 42, true) : Tools.pillButton(this, 42, false));
        return b;
    }

    private void refreshStatus() {
        boolean shizuku = ShizukuManager.isConnected() && ShizukuManager.isAuthorized();
        boolean root = hasRoot();
        boolean auto = isInstallClickOn();
        status.setText("Shizuku：" + (shizuku ? "已连接" : (ShizukuManager.isShizukuInstalled(this) ? "已装未授权" : "未安装"))
                + "　Root：" + (root ? "可用" : "无")
                + "\n自动确认点击：" + (auto ? "已开启" : "未开启")
                + "\n静默安装需要 Shizuku 或 Root 任一可用；都没有时用「普通安装」自动点掉确认框");
        status.setTextColor(shizuku || root ? Tools.textColor(this) : Tools.subTextColor(this));
    }

    private boolean hasRoot() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
            java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream()));
            String line = r.readLine();
            p.waitFor();
            return line != null && line.contains("uid=0");
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isInstallClickOn() {
        try {
            String e = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return e != null && e.contains(getPackageName() + "/" + InstallClickService.class.getName());
        } catch (Throwable t) {
            return false;
        }
    }

    private void refreshList() {
        listBox.removeAllViews();
        List<File> apks = new ArrayList<>();
        File base = FileStore.outDir(this);
        collect(base, apks);
        collect(new File(base, "下载"), apks);
        if (apks.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("还没找到 APK。去「下载中心」下载，或用右上「选文件」挑一个。");
            empty.setTextColor(Tools.subTextColor(this));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(20), 0, 0);
            listBox.addView(empty);
            return;
        }
        Collections.sort(apks, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File f : apks) listBox.addView(row(f));
        refreshStatus();
    }

    private void collect(File dir, List<File> out) {
        if (dir == null || !dir.isDirectory()) return;
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isFile() && f.getName().toLowerCase(Locale.US).endsWith(".apk")) out.add(f);
        }
    }

    private View row(final File apk) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Tools.cardColor(this));
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), Tools.isDark(this) ? 0xFF2A3040 : 0xFFE4E8EE);
        row.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(4));
        row.setLayoutParams(lp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView name = new TextView(this);
        name.setText(apk.getName());
        name.setTextSize(13);
        name.setTextColor(Tools.textColor(this));
        name.setSingleLine(true);
        texts.addView(name);

        TextView meta = new TextView(this);
        meta.setText(describe(apk));
        meta.setTextSize(11);
        meta.setTextColor(Tools.subTextColor(this));
        texts.addView(meta);
        row.addView(texts);

        Button install = new Button(this);
        install.setText("安装");
        install.setTextSize(12);
        install.setOnClickListener(v -> chooseMethod(apk));
        row.addView(install);
        return row;
    }

    private String describe(File f) {
        String base = fmtSize(f.length());
        try {
            PackageManager pm = getPackageManager();
            PackageInfo info = pm.getPackageArchiveInfo(f.getAbsolutePath(), 0);
            if (info != null) {
                info.applicationInfo.sourceDir = f.getAbsolutePath();
                info.applicationInfo.publicSourceDir = f.getAbsolutePath();
                String label = pm.getApplicationLabel(info.applicationInfo).toString();
                return label + " · " + info.packageName + " · " + info.versionName
                        + " (" + info.versionCode + ") · " + base;
            }
        } catch (Throwable ignored) {
        }
        return base;
    }

    private void chooseMethod(final File apk) {
        String[] items = new String[]{
                "静默安装（Shizuku）", "静默安装（Root）", "普通安装 + 自动确认"
        };
        new AlertDialog.Builder(this)
                .setTitle("安装 " + apk.getName())
                .setItems(items, (d, which) -> {
                    if (which == 0) silentByShizuku(apk);
                    else if (which == 1) silentByRoot(apk);
                    else normalInstall(apk);
                })
                .show();
    }

    private void silentByShizuku(final File apk) {
        if (!ShizukuManager.isConnected() || !ShizukuManager.isAuthorized()) {
            append("Shizuku 没连上，去「无线调试」页连一下，或先在 Shizuku App 授权");
            ShizukuManager.requestPermission(this);
            refreshStatus();
            return;
        }
        append("Shizuku 静默安装：" + apk.getName());
        new Thread(() -> {
            String out = ShizukuManager.execPm(this, new String[]{
                    "install", "-r", "-d", apk.getAbsolutePath()
            });
            final String r = out == null ? "Shizuku 未连接" : out;
            ui.post(() -> {
                append(r);
                if (r.contains("Success") || r.contains("成功")) {
                    Toast.makeText(this, "安装完成", Toast.LENGTH_SHORT).show();
                    refreshList();
                } else {
                    Toast.makeText(this, "安装失败，看下面日志", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void silentByRoot(final File apk) {
        if (!hasRoot()) {
            append("这台机子没有 root（su 不可用），用不了 root 静默安装");
            return;
        }
        append("Root 静默安装：" + apk.getName());
        new Thread(() -> {
            String r;
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"su", "-c",
                        "pm install -r -d \"" + apk.getAbsolutePath() + "\""});
                java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(p.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append('\n');
                int code = p.waitFor();
                r = "exit " + code + "\n" + sb.toString().trim();
            } catch (Throwable t) {
                r = "Root 执行失败：" + t.getMessage();
            }
            final String out = r;
            ui.post(() -> {
                append(out);
                if (out.contains("Success") || out.contains("成功")) {
                    Toast.makeText(this, "安装完成", Toast.LENGTH_SHORT).show();
                    refreshList();
                }
            });
        }).start();
    }

    private void normalInstall(final File apk) {
        try {
            Uri uri = IkFileProvider.uriFor(this, apk);
            if (uri == null) {
                append("生成安装地址失败");
                return;
            }
            Intent it = new Intent(Intent.ACTION_VIEW);
            it.setDataAndType(uri, "application/vnd.android.package-archive");
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(it);
            append("已交给系统安装器；若没开「自动确认点击」，需要手动点安装。");
        } catch (Throwable t) {
            append("普通安装失败：" + t.getMessage());
        }
    }

    private void pickFile() {
        try {
            Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            it.addCategory(Intent.CATEGORY_OPENABLE);
            it.setType("application/vnd.android.package-archive");
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(it, 9001);
        } catch (Throwable t) {
            Toast.makeText(this, "打不开文件选择器：" + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 9001 && res == RESULT_OK && data != null && data.getData() != null) {
            try {
                Uri u = data.getData();
                File tmp = new File(FileStore.outDir(this), "外部导入_" + System.currentTimeMillis() + ".apk");
                java.io.InputStream in = getContentResolver().openInputStream(u);
                FileOutputStream out = new FileOutputStream(tmp);
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                in.close();
                out.close();
                chooseMethod(tmp);
                refreshList();
            } catch (Throwable t) {
                append("导入失败：" + t.getMessage());
            }
        }
    }

    private void append(String s) {
        log.setText("日志：\n" + s);
    }

    private String fmtSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
