package com.roomarchive.server;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 应用管理：提取 APK / 查看信息 / 停用 / 卸载 */
public class AppListActivity extends BaseActivity {

    private PackageManager pm;
    private LinearLayout listBox;
    private EditText searchInput;
    private final List<AppEntry> allApps = new ArrayList<>();

    private static class AppEntry {
        ApplicationInfo info;
        String label;
        String pkg;
        String version;
        Drawable icon;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pm = getPackageManager();
        // Shizuku 授权结果监听：授权成功后自动重试等待中的操作
        rikka.shizuku.Shizuku.addRequestPermissionResultListener(
                new rikka.shizuku.Shizuku.OnRequestPermissionResultListener() {
                    @Override
                    public void onRequestPermissionResult(int requestCode, int grantResult) {
                        if (requestCode == ShizukuManager.REQ_PERMISSION
                                && grantResult == PackageManager.PERMISSION_GRANTED) {
                            final Runnable r = pendingAction;
                            pendingAction = null;
                            if (r != null) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        r.run();
                                    }
                                });
                            }
                        }
                    }
                });
        buildUi();
        loadApps("");
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Tools.bgWithGlow(this));
        root.setPadding(dp(16), dp(20), dp(16), dp(18));
        scroll.addView(root);

        TextView title = Tools.pageTitle(this, "应用管理");
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView tip = new TextView(this);
        tip.setText("点击应用可选择：提取安装包 / 查看信息 / 停用 / 卸载\nShizuku 强制操作：静默停用/卸载（配合无线调试激活，免 root）\n提取的 APK 自动保存到工具箱目录，可在「我的文件」查看分享");
        tip.setTextSize(12);
        tip.setTextColor(Tools.subTextColor(this));
        tip.setGravity(Gravity.CENTER);
        root.addView(tip);
        root.addView(spacer(10));

        searchInput = new EditText(this);
        searchInput.setHint("搜索应用…");
        searchInput.setTextColor(Tools.textColor(this));
        searchInput.setSingleLine(true);
        searchInput.setTextSize(14);
        android.graphics.drawable.GradientDrawable sBg = new android.graphics.drawable.GradientDrawable();
        sBg.setColor(Tools.cardColor(this));
        sBg.setCornerRadius(Tools.dp(this, 12));
        sBg.setStroke(Tools.dp(this, 1),
                Tools.isDark(this) ? 0xFF2A3040 : 0xFFE4E8EE);
        searchInput.setBackground(sBg);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                loadApps(s.toString());
            }
        });
        root.addView(searchInput);
        root.addView(spacer(10));

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(listBox);

        setContentView(scroll);
    }

    private void loadApps(String keyword) {
        listBox.removeAllViews();
        allApps.clear();
        try {
            List<ApplicationInfo> apps = pm.getInstalledApplications(0);
            for (ApplicationInfo ai : apps) {
                AppEntry e = new AppEntry();
                e.info = ai;
                e.pkg = ai.packageName;
                try {
                    e.label = pm.getApplicationLabel(ai).toString();
                } catch (Exception ex) {
                    e.label = e.pkg;
                }
                try {
                    PackageInfo pi = pm.getPackageInfo(e.pkg, 0);
                    e.version = pi.versionName == null ? "" : pi.versionName;
                } catch (Exception ex) {
                    e.version = "";
                }
                try {
                    e.icon = pm.getApplicationIcon(ai);
                } catch (Exception ex) {
                    e.icon = null;
                }
                allApps.add(e);
            }
            Collections.sort(allApps, new Comparator<AppEntry>() {
                @Override
                public int compare(AppEntry a, AppEntry b) {
                    return a.label.compareToIgnoreCase(b.label);
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "读取应用列表失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.US);
        int shown = 0;
        for (AppEntry e : allApps) {
            if (!kw.isEmpty()
                    && !e.label.toLowerCase(Locale.US).contains(kw)
                    && !e.pkg.toLowerCase(Locale.US).contains(kw)) {
                continue;
            }
            listBox.addView(appRow(e));
            shown++;
        }
        if (shown == 0) {
            TextView empty = new TextView(this);
            empty.setText("没有匹配的应用");
            empty.setTextColor(Color.rgb(150, 156, 166));
            empty.setGravity(Gravity.CENTER);
            listBox.addView(empty);
        }
    }

    private View appRow(final AppEntry e) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setGravity(Gravity.CENTER_VERTICAL);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), Color.rgb(228, 232, 238));
        row.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(4));
        row.setLayoutParams(lp);

        ImageView icon = new ImageView(this);
        if (e.icon != null) {
            try {
                Bitmap bm = ((BitmapDrawable) e.icon).getBitmap();
                if (bm != null) {
                    icon.setImageBitmap(Bitmap.createScaledBitmap(bm, dp(44), dp(44), true));
                }
            } catch (Exception ignored) {
            }
        }
        icon.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
        row.addView(icon);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView name = new TextView(this);
        name.setText(e.label);
        name.setTextSize(15);
        name.setTextColor(Color.rgb(30, 38, 52));
        name.setSingleLine(true);
        texts.addView(name);
        TextView meta = new TextView(this);
        meta.setText(e.pkg + "  v" + e.version);
        meta.setTextSize(11);
        meta.setTextColor(Color.rgb(140, 148, 158));
        meta.setSingleLine(true);
        texts.addView(meta);
        row.addView(texts);

        Button more = new Button(this);
        more.setText("···");
        more.setTextSize(14);
        more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showActions(e);
            }
        });
        row.addView(more);

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showActions(e);
            }
        });
        return row;
    }

    private void showActions(final AppEntry e) {
        String[] actions = {"查看信息", "提取安装包 (APK)", "停用（跳系统详情）", "卸载（系统确认）", "Shizuku 强制操作"};
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(e.label);
        b.setItems(actions, new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface d, int which) {
                switch (which) {
                    case 0: showInfo(e); break;
                    case 1: extractApk(e); break;
                    case 2: disableApp(e); break;
                    case 3: uninstallApp(e); break;
                    case 4: showShizukuMenu(e); break;
                }
            }
        });
        b.show();
    }

    /** Shizuku 高级操作：静默停用/启用/卸载（需 Shizuku，配合无线调试激活，免 root） */
    private void showShizukuMenu(final AppEntry e) {
        String[] items = {"强制停用（静默，可恢复）",
                "恢复启用",
                "强制卸载（含数据）",
                "静默卸载（保留数据）"};
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Shizuku 强制操作 · " + e.label);
        b.setItems(items, new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface d, int which) {
                final Runnable act;
                switch (which) {
                    case 0: act = new Runnable() { public void run() { runPm(e, "disable", new Runnable() { public void run() { Toast.makeText(AppListActivity.this, "已强制停用 " + e.label, Toast.LENGTH_LONG).show(); } }); } }; break;
                    case 1: act = new Runnable() { public void run() { runPm(e, "enable", new Runnable() { public void run() { Toast.makeText(AppListActivity.this, "已恢复启用 " + e.label, Toast.LENGTH_LONG).show(); } }); } }; break;
                    case 2: act = new Runnable() { public void run() { runPm(e, "uninstall", new Runnable() { public void run() { Toast.makeText(AppListActivity.this, "已强制卸载 " + e.label, Toast.LENGTH_LONG).show(); } }); } }; break;
                    default: act = new Runnable() { public void run() { runPm(e, "uninstallUser", new Runnable() { public void run() { Toast.makeText(AppListActivity.this, "已静默卸载（保留数据）" + e.label, Toast.LENGTH_LONG).show(); } }); } }; break;
                }
                ensureShizuku(act);
            }
        });
        b.show();
    }

    private Runnable pendingAction;  // 等待 Shizuku 授权后自动重试的操作

    private final android.os.Handler retryHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable retryTask;      // 自动重试连接的轮询任务

    private void startShizukuRetry(final Runnable action) {
        stopShizukuRetry();
        retryTask = new Runnable() {
            int tries = 0;
            @Override
            public void run() {
                tries++;
                if (isFinishing() || isDestroyed() || tries > 80) return;  // 最多约 2 分钟
                if (ShizukuManager.isConnected()) {
                    stopShizukuRetry();
                    if (!ShizukuManager.isAuthorized()) {
                        pendingAction = action;
                        Toast.makeText(AppListActivity.this, "已连接 Shizuku，请在授权弹窗中点击「允许」", Toast.LENGTH_LONG).show();
                        ShizukuManager.requestPermission(AppListActivity.this);
                    } else {
                        action.run();
                    }
                    return;
                }
                retryHandler.postDelayed(this, 1500);
            }
        };
        retryHandler.post(retryTask);
    }

    private void stopShizukuRetry() {
        if (retryTask != null) {
            retryHandler.removeCallbacks(retryTask);
            retryTask = null;
        }
    }

    @Override
    protected void onDestroy() {
        stopShizukuRetry();
        super.onDestroy();
    }

    /** 检查 Shizuku 状态并引导，就绪后执行操作 */
    private void ensureShizuku(final Runnable action) {
        if (!ShizukuManager.isShizukuInstalled(this)) {
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            b.setTitle("需要安装 Shizuku");
            b.setMessage("强制停用/卸载需要 Shizuku（免 root 系统工具，用 adb 或无线调试激活一次即可）。\n\n点击「去下载」打开官方下载页，安装后在 Shizuku App 内激活（Android 11+ 可用「无线调试」方式，无需 root），再回来使用本功能。");
            b.setPositiveButton("去下载", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface d, int w) {
                    ShizukuManager.openShizuku(AppListActivity.this);
                }
            });
            b.setNegativeButton("取消", null);
            b.show();
            return;
        }
        if (!ShizukuManager.isConnected()) {
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            b.setTitle("Shizuku 未运行");
            b.setMessage("Shizuku 已安装但未激活/未运行。\n\n打开 Shizuku App：\n· 有 root：直接「通过 root 启动」\n· 无 root（Android 11+）：「通过无线调试启动」，按提示输入配对码即可\n\n注意：如果 Shizuku 界面显示「正在运行」但仍提示未运行，请在 Shizuku App 中「停止」服务后重新启动（或重启手机后重新激活），让服务重新向应用推送连接。\n\n本页会自动检测连接，连上后自动弹出授权框，无需回来再点。");
            b.setPositiveButton("打开 Shizuku", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface d, int w) {
                    ShizukuManager.openShizuku(AppListActivity.this);
                    startShizukuRetry(action);
                }
            });
            b.setNegativeButton("取消", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface d, int w) {
                    // 即使取消也后台继续检测，方便用户去 Shizuku 重启后自动衔接
                    startShizukuRetry(action);
                }
            });
            b.show();
            return;
        }
        if (!ShizukuManager.isAuthorized()) {
            pendingAction = action;
            Toast.makeText(this, "请在 Shizuku 授权弹窗中点击「允许」，授权后会自动执行", Toast.LENGTH_LONG).show();
            ShizukuManager.requestPermission(this);
            return;
        }
        action.run();
    }

    /** 在子线程执行 pm 操作，结果回主线程提示 */
    private void runPm(final AppEntry e, final String op, final Runnable success) {
        if (e == null || e.pkg == null) return;
        Toast.makeText(this, "正在执行…", Toast.LENGTH_SHORT).show();
        final String[] result = {null};
        new Thread(new Runnable() {
            @Override
            public void run() {
                switch (op) {
                    case "disable": result[0] = ShizukuManager.disableApp(AppListActivity.this, e.pkg); break;
                    case "enable": result[0] = ShizukuManager.enableApp(AppListActivity.this, e.pkg); break;
                    case "uninstall": result[0] = ShizukuManager.uninstallApp(AppListActivity.this, e.pkg); break;
                    case "uninstallUser": result[0] = ShizukuManager.uninstallUserApp(AppListActivity.this, e.pkg); break;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String r = result[0];
                        if (r != null && r.startsWith("失败") || r != null && r.contains("异常") || r != null && r.contains("拒绝")) {
                            Toast.makeText(AppListActivity.this, "失败：" + r, Toast.LENGTH_LONG).show();
                        } else if (r != null) {
                            if (success != null) success.run();
                            else Toast.makeText(AppListActivity.this, "成功：" + r, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(AppListActivity.this, "Shizuku 未连接，请先激活", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
    }

    /** 查看信息：包名/版本/大小/时间/SDK/权限数等 */
    private void showInfo(AppEntry e) {
        try {
            PackageInfo pi = pm.getPackageInfo(e.pkg, PackageManager.GET_PERMISSIONS);
            long size = new File(e.info.sourceDir).length();
            int permCount = pi.requestedPermissions == null ? 0 : pi.requestedPermissions.length;
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
            String isSys = (e.info.flags & ApplicationInfo.FLAG_SYSTEM) != 0 ? "是（系统应用）" : "否";
            StringBuilder sb = new StringBuilder();
            sb.append("名称：").append(e.label).append("\n");
            sb.append("包名：").append(e.pkg).append("\n");
            sb.append("版本名：").append(e.version).append("\n");
            sb.append("版本号：").append(pi.versionCode).append("\n");
            sb.append("目标 SDK：").append(pi.applicationInfo.targetSdkVersion).append("\n");
            sb.append("系统应用：").append(isSys).append("\n");
            sb.append("占用大小：").append(fmtSize(size)).append("\n");
            sb.append("安装时间：").append(fmt.format(new Date(pi.firstInstallTime))).append("\n");
            sb.append("更新时间：").append(fmt.format(new Date(pi.lastUpdateTime))).append("\n");
            sb.append("申请权限：").append(permCount).append(" 项\n");
            sb.append("APK 路径：").append(e.info.sourceDir);
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            b.setTitle("应用信息");
            b.setMessage(sb.toString());
            b.setPositiveButton("知道了", null);
            b.show();
        } catch (Exception ex) {
            Toast.makeText(this, "读取信息失败：" + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** 提取 APK 到工具箱目录 */
    private void extractApk(final AppEntry e) {
        final Toast wait = Toast.makeText(this, "正在提取安装包…", Toast.LENGTH_LONG);
        wait.show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File src = new File(e.info.sourceDir);
                    File dir = new File(FileStore.outDir(AppListActivity.this), "提取APK");
                    dir.mkdirs();
                    String safe = e.label.replaceAll("[\\\\/:*?\"<>|]", "_");
                    File dst = new File(dir, safe + "_v" + e.version + ".apk");
                    FileInputStream is = new FileInputStream(src);
                    OutputStream os = new FileOutputStream(dst);
                    byte[] buf = new byte[65536];
                    int n;
                    long total = src.length();
                    long done = 0;
                    int lastPct = -1;
                    while ((n = is.read(buf)) > 0) {
                        os.write(buf, 0, n);
                        done += n;
                        int pct = (int) (done * 100 / total);
                        if (pct != lastPct && pct % 10 == 0) {
                            lastPct = pct;
                            final int fp = pct;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    wait.setText("正在提取安装包… " + fp + "%");
                                }
                            });
                        }
                    }
                    os.flush();
                    os.close();
                    is.close();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            new android.app.AlertDialog.Builder(AppListActivity.this)
                                    .setTitle("提取成功")
                                    .setMessage("APK已保存到我的文件。\n要上传生成分享链接吗？")
                                    .setPositiveButton("上传分享", (d, w) -> uploadApk(dst))
                                    .setNegativeButton("关闭", null)
                                    .show();
                        }
                    });
                } catch (final Exception ex) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(AppListActivity.this, "提取失败：" + ex.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }).start();
    }

    /** 上传APK生成分享链接（多服务备用） */
    private void uploadApk(final File apkFile) {
        final android.app.ProgressDialog pd = android.app.ProgressDialog.show(this, "上传中", "正在上传APK…", true, false);
        new Thread(() -> {
            String url = null;
            String error = null;
            // 先试 catbox.moe
            String[] services = {"catbox", "0x0"};
            for (String svc : services) {
                try {
                    if ("catbox".equals(svc)) {
                        String boundary = "----IK" + System.currentTimeMillis();
                        java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                                new java.net.URL("https://catbox.moe/user/api.php").openConnection();
                        conn.setRequestMethod("POST");
                        conn.setDoOutput(true);
                        conn.setConnectTimeout(15000);
                        conn.setReadTimeout(60000);
                        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                        java.io.OutputStream os = conn.getOutputStream();
                        os.write(("--" + boundary + "\r\n").getBytes());
                        os.write("Content-Disposition: form-data; name=\"reqtype\"\r\n\r\n".getBytes());
                        os.write("fileupload\r\n".getBytes());
                        os.write(("--" + boundary + "\r\n").getBytes());
                        os.write(("Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"" + apkFile.getName() + "\"\r\n").getBytes());
                        os.write("Content-Type: application/vnd.android.package-archive\r\n\r\n".getBytes());
                        java.io.FileInputStream fis = new java.io.FileInputStream(apkFile);
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = fis.read(buf)) > 0) os.write(buf, 0, n);
                        fis.close();
                        os.write(("\r\n--" + boundary + "--\r\n").getBytes());
                        os.flush(); os.close();
                        int code = conn.getResponseCode();
                        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                        br.close();
                        String resp = sb.toString().trim();
                        if (code == 200 && resp.startsWith("http")) { url = resp; break; }
                    } else {
                        // 0x0.st 备用
                        java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                                new java.net.URL("https://0x0.st").openConnection();
                        conn.setRequestMethod("POST");
                        conn.setDoOutput(true);
                        conn.setConnectTimeout(15000);
                        conn.setReadTimeout(60000);
                        java.io.OutputStream os = conn.getOutputStream();
                        java.io.FileInputStream fis = new java.io.FileInputStream(apkFile);
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = fis.read(buf)) > 0) os.write(buf, 0, n);
                        fis.close();
                        os.flush(); os.close();
                        int code = conn.getResponseCode();
                        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                        br.close();
                        String resp = sb.toString().trim();
                        if (code == 200 && resp.startsWith("http")) { url = resp; break; }
                    }
                } catch (Exception e) {
                    error = e.getMessage();
                }
            }
            final String fUrl = url;
            final String fErr = error;
            runOnUiThread(() -> {
                pd.dismiss();
                if (fUrl != null) {
                    // 复制链接
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("apk_url", fUrl));
                    new android.app.AlertDialog.Builder(this)
                            .setTitle("上传成功")
                            .setMessage("分享链接已复制：\n" + fUrl + "\n\n发给朋友，浏览器直接下载安装。")
                            .setPositiveButton("复制", (d, w) -> {
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("apk_url", fUrl));
                                Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
                            })
                            .show();
                } else {
                    Toast.makeText(this, "上传失败: " + fErr, Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    /** 停用：打开系统应用详情页（详情页内有"停用"按钮，由系统完成停用） */
    private void disableApp(AppEntry e) {
        try {
            Intent it = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + e.pkg));
            startActivity(it);
        } catch (Exception ex) {
            Toast.makeText(this, "无法打开应用详情：" + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** 卸载：调用系统卸载流程（需用户确认） */
    private void uninstallApp(AppEntry e) {
        try {
            Intent it = new Intent(Intent.ACTION_UNINSTALL_PACKAGE,
                    Uri.parse("package:" + e.pkg));
            startActivity(it);
        } catch (Exception ex) {
            Toast.makeText(this, "无法发起卸载：" + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String fmtSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0);
        return String.format(Locale.US, "%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0);
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
