package com.roomarchive.server;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** 应用备份：把已装应用的 APK 拷到 Download/IK工具箱/应用备份 */
public class AppBackupActivity extends BaseActivity {

    private LinearLayout listBox;
    private TextView status, batchBtn;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<ApplicationInfo> apps = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Tools.bgWithGlow(this));
        root.setPadding(dp(18), dp(24), dp(18), dp(24));
        sv.addView(root);

        TextView title = Tools.pageTitle(this, "应用备份");
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        status = new TextView(this);
        status.setText("正在扫描已安装应用…");
        status.setTextSize(12);
        status.setTextColor(Tools.subTextColor(this));
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(6), 0, dp(12));
        root.addView(status);

        batchBtn = new TextView(this);
        batchBtn.setText("一键备份全部用户应用");
        batchBtn.setTextSize(14);
        batchBtn.setTextColor(0xFFFFFFFF);
        batchBtn.setGravity(Gravity.CENTER);
        batchBtn.setBackground(Tools.pillButton(this, 46, true));
        batchBtn.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46)));
        batchBtn.setOnClickListener(v -> batchBackup());
        root.addView(batchBtn);

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        root.addView(listBox, lp);

        setContentView(sv);
        loadApps();
    }

    private void loadApps() {
        new Thread(() -> {
            try {
                PackageManager pm = getPackageManager();
                List<ApplicationInfo> all = pm.getInstalledApplications(0);
                apps.clear();
                for (ApplicationInfo a : all) {
                    if ((a.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                    if (a.sourceDir == null) continue;
                    apps.add(a);
                }
                Collections.sort(apps, (a, b) -> pm.getApplicationLabel(a).toString()
                        .compareToIgnoreCase(pm.getApplicationLabel(b).toString()));
            } catch (Throwable ignored) {}
            ui.post(() -> {
                status.setText("共 " + apps.size() + " 个用户应用 · 备份到 Download/IK工具箱/应用备份");
                listBox.removeAllViews();
                for (ApplicationInfo a : apps) listBox.addView(row(a));
            });
        }).start();
    }

    private LinearLayout row(final ApplicationInfo a) {
        PackageManager pm = getPackageManager();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        row.setBackground(Tools.liquidGlassCard(this, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        row.setLayoutParams(lp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView name = new TextView(this);
        name.setText(pm.getApplicationLabel(a));
        name.setTextSize(14);
        name.setTextColor(Tools.textColor(this));
        texts.addView(name);
        TextView meta = new TextView(this);
        String size = "?";
        try { size = String.format(Locale.US, "%.1f MB", new File(a.sourceDir).length() / 1024.0 / 1024.0); }
        catch (Throwable ignored) {}
        meta.setText(a.packageName + " · " + size);
        meta.setTextSize(10);
        meta.setTextColor(Tools.subTextColor(this));
        texts.addView(meta);
        row.addView(texts);

        TextView doBackup = new TextView(this);
        doBackup.setText("备份");
        doBackup.setTextSize(12);
        doBackup.setTextColor(Tools.accentColor(this));
        doBackup.setPadding(dp(10), dp(6), dp(10), dp(6));
        doBackup.setOnClickListener(v -> backup(a));
        row.addView(doBackup);
        return row;
    }

    private void backup(ApplicationInfo a) {
        new Thread(() -> {
            String r = doCopy(a);
            ui.post(() -> {
                Toast.makeText(this, r, Toast.LENGTH_SHORT).show();
                status.setText("最近：" + r);
            });
        }).start();
    }

    private void batchBackup() {
        batchBtn.setEnabled(false);
        batchBtn.setText("备份中…");
        new Thread(() -> {
            int ok = 0, fail = 0;
            for (ApplicationInfo a : apps) {
                if (doCopy(a).startsWith("已备份")) ok++; else fail++;
            }
            final int fok = ok, ffail = fail;
            ui.post(() -> {
                batchBtn.setEnabled(true);
                batchBtn.setText("一键备份全部用户应用");
                status.setText("完成：成功 " + fok + " 个，失败 " + ffail + " 个");
            });
        }).start();
    }

    private String doCopy(ApplicationInfo a) {
        try {
            File dir = new File(FileStore.outDir(this), "应用备份");
            dir.mkdirs();
            String label;
            try { label = getPackageManager().getApplicationLabel(a).toString(); }
            catch (Throwable t) { label = a.packageName; }
            label = label.replaceAll("[\\\\/:*?\"<>|]", "_");
            File target = new File(dir, label + "-" + a.packageName + ".apk");
            if (target.exists() && target.length() > 0) return "已备份（已存在）" + target.getName();
            try (FileInputStream in = new FileInputStream(a.sourceDir);
                 FileOutputStream out = new FileOutputStream(target)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            return "已备份 " + target.getName();
        } catch (Throwable t) {
            return "失败：" + t.getMessage();
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
