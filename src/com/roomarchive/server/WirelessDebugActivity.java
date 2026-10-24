package com.roomarchive.server;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** 无线调试（Shizuku）专项引导页：四态检测 + 分步引导 + 版本诊断 + 鸿蒙/ADB 备选路径 */
public class WirelessDebugActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView stateTitle, stateDetail, diagnoseText;
    private Button btnPrimary, btnSecondary;
    private LinearLayout stepBox;
    private boolean scanning = false;
    private App.ShizukuStateListener shizukuListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        rikka.shizuku.Shizuku.addRequestPermissionResultListener(
                new rikka.shizuku.Shizuku.OnRequestPermissionResultListener() {
                    @Override
                    public void onRequestPermissionResult(int requestCode, int grantResult) {
                        if (requestCode == ShizukuManager.REQ_PERMISSION) {
                            android.widget.Toast.makeText(WirelessDebugActivity.this,
                                    grantResult == PackageManager.PERMISSION_GRANTED
                                            ? "授权成功，Shizuku 全部功能可用"
                                            : "授权被拒绝，可稍后重试",
                                    android.widget.Toast.LENGTH_SHORT).show();
                            refresh();
                        }
                    }
                });
        // 主动监听 binder 状态变化：binder 一连上就刷新 UI
        shizukuListener = new App.ShizukuStateListener() {
            @Override
            public void onShizukuBinderChanged(boolean connected) {
                runOnUiThread(new Runnable() {
                    @Override public void run() { refresh(); }
                });
            }
        };
        App.addShizukuListener(shizukuListener);
        refresh();
        startPolling();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // sticky receiver 已在 App.onCreate 注册，这里直接尝试弹授权框
        App.resumeCheck();
        refresh();
    }

    @Override
    protected void onDestroy() {
        scanning = false;
        handler.removeCallbacksAndMessages(null);
        if (shizukuListener != null) App.removeShizukuListener(shizukuListener);
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Tools.bgWithGlow(this));
        root.setPadding(dp(16), dp(20), dp(16), dp(18));

        TextView title = Tools.pageTitle(this, "无线调试");
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        // ===== 状态卡 =====
        LinearLayout stateCard = glassCard();
        stateTitle = new TextView(this);
        stateTitle.setTextSize(20);
        stateTitle.setTypeface(Typeface.DEFAULT_BOLD);
        stateTitle.setGravity(Gravity.CENTER);
        stateCard.addView(stateTitle);

        stateDetail = new TextView(this);
        stateDetail.setTextSize(13);
        stateDetail.setGravity(Gravity.CENTER);
        stateDetail.setPadding(0, dp(8), 0, 0);
        stateCard.addView(stateDetail);

        // 诊断行：Shizuku App 版本 / 服务端版本 / UID
        diagnoseText = new TextView(this);
        diagnoseText.setTextSize(11);
        diagnoseText.setTextColor(Tools.subTextColor(this));
        diagnoseText.setGravity(Gravity.CENTER);
        diagnoseText.setPadding(0, dp(8), 0, 0);
        stateCard.addView(diagnoseText);

        LinearLayout.LayoutParams scP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(170));
        scP.setMargins(0, dp(16), 0, 0);
        root.addView(stateCard, scP);

        // 主按钮
        btnPrimary = new Button(this);
        btnPrimary.setTextSize(16);
        btnPrimary.setTypeface(Typeface.DEFAULT_BOLD);
        btnPrimary.setTextColor(Color.WHITE);
        btnPrimary.setAllCaps(false);
        LinearLayout.LayoutParams bP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        bP.setMargins(0, dp(14), 0, 0);
        root.addView(btnPrimary, bP);

        // 副按钮
        btnSecondary = new Button(this);
        btnSecondary.setTextSize(14);
        btnSecondary.setTextColor(Color.WHITE);
        btnSecondary.setAllCaps(false);
        LinearLayout.LayoutParams bP2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        bP2.setMargins(0, dp(8), 0, 0);
        root.addView(btnSecondary, bP2);

        // ===== 分步引导卡 =====
        stepBox = glassCard();
        LinearLayout.LayoutParams sbP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        sbP.setMargins(0, dp(16), 0, 0);
        root.addView(stepBox, sbP);

        // ===== ADB有线启动命令 =====
        LinearLayout adbCard = glassCard();
        TextView adbTitle = new TextView(this);
        adbTitle.setText("📱 有线ADB启动（推荐）");
        adbTitle.setTextSize(15);
        adbTitle.setTypeface(Typeface.DEFAULT_BOLD);
        adbCard.addView(adbTitle);

        TextView adbSteps = new TextView(this);
        adbSteps.setTextSize(13);
        adbSteps.setLineSpacing(dp(3), 1.0f);
        adbSteps.setTextColor(Tools.subTextColor(this));
        adbSteps.setPadding(0, dp(8), 0, dp(8));
        adbSteps.setText("1. 手机设置 → 关于手机 → 连点版本号7次开启开发者选项\n"
                + "2. 开发者选项 → 开启「USB调试」\n"
                + "3. USB连电脑，电脑装了ADB工具\n"
                + "4. 电脑终端执行下面这条命令：");
        adbCard.addView(adbSteps);

        TextView adbCmd = new TextView(this);
        adbCmd.setText("adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh");
        adbCmd.setTextSize(13);
        adbCmd.setTextIsSelectable(true);
        adbCmd.setBackgroundColor(0xFF1E293B);
        adbCmd.setTextColor(0xFF4ADE80);
        adbCmd.setPadding(dp(10), dp(10), dp(10), dp(10));
        adbCard.addView(adbCmd);

        TextView adbCopy = new TextView(this);
        adbCopy.setText("点击复制命令");
        adbCopy.setTextSize(13);
        adbCopy.setTextColor(0xFF3B82F6);
        adbCopy.setGravity(Gravity.CENTER);
        adbCopy.setPadding(0, dp(8), 0, 0);
        adbCard.addView(adbCopy);
        adbCopy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("adb", "adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh"));
            Toast.makeText(this, "已复制，去电脑粘贴执行", Toast.LENGTH_LONG).show();
        });

        LinearLayout.LayoutParams adbP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        adbP.setMargins(0, dp(12), 0, 0);
        root.addView(adbCard, adbP);

        // 立即授权按钮
        TextView btnAuth = new TextView(this);
        btnAuth.setText("立即授权 IK 工具箱");
        btnAuth.setTextSize(15);
        btnAuth.setTypeface(Typeface.DEFAULT_BOLD);
        btnAuth.setTextColor(Color.WHITE);
        btnAuth.setGravity(Gravity.CENTER);
        btnAuth.setPadding(0, dp(14), 0, dp(14));
        btnAuth.setBackground(Tools.pillButton(this, 50, true));
        btnAuth.setClickable(true);
        LinearLayout.LayoutParams authP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        authP.setMargins(0, dp(12), 0, 0);
        btnAuth.setOnClickListener(v -> {
            // 先拉binder连接Shizuku
            ShizukuManager.requestBinderFromManager(this);
            btnAuth.postDelayed(() -> {
                if (ShizukuManager.isConnected()) {
                    ShizukuManager.requestPermission(this);
                    Toast.makeText(this, "已请求授权，请在弹窗中点击允许", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Shizuku未连接，请确认Shizuku服务已启动", Toast.LENGTH_LONG).show();
                }
            }, 500);
        });
        root.addView(btnAuth, authP);

        root.addView(spacer(20));
        setContentView(root);
    }

    /** 诊断信息：Shizuku App 版本 / 服务端版本 / UID（定位"连不上"卡点） */
    private String diagnose() {
        try {
            String appVer = "未安装";
            if (ShizukuManager.isShizukuInstalled(this)) {
                appVer = getPackageManager()
                        .getPackageInfo(ShizukuManager.SHIZUKU_PACKAGE, 0).versionName;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Shizuku App ").append(appVer);
            try {
                if (ShizukuManager.isConnected()) {
                    sb.append("  |  服务端 v").append(rikka.shizuku.Shizuku.getVersion());
                    try {
                        sb.append("  |  UID ").append(rikka.shizuku.Shizuku.getUid());
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable t) {
                sb.append("  |  服务端版本读取失败");
            }
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private void refresh() {
        try {
            diagnoseText.setText(diagnose());
        } catch (Throwable ignored) {
        }
        if (!ShizukuManager.isShizukuInstalled(this)) {
            stateTitle.setText("需要安装 Shizuku");
            stateTitle.setTextColor(Color.rgb(255, 130, 80));
            stateDetail.setText("Shizuku 是免费开源工具，用无线调试激活一次后\n即可静默停用/卸载应用、读取深度设备信息");
            btnPrimary.setText("下载 Shizuku（GitHub 官方）");
            btnPrimary.setBackground(Tools.pillButton(this, 26, true));
            btnPrimary.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ShizukuManager.openShizuku(WirelessDebugActivity.this);
                }
            });
            btnSecondary.setText("查看官方使用教程");
            btnSecondary.setBackground(Tools.pillButton(this, 22, false));
            btnSecondary.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://shizuku.rikka.app/")));
                    } catch (Exception ignored) {
                    }
                }
            });
            buildSteps(new String[]{
                    "打开 Shizuku 官网下载安装（需 v13 以上版本）",
                    "安装完成后回到本页，点击继续",
            });
        } else if (!ShizukuManager.isConnected()) {
            stateTitle.setText("Shizuku 未激活");
            stateTitle.setTextColor(Color.rgb(255, 170, 60));
            stateDetail.setText("在 Shizuku App 内激活一次即可（无需 root）\nAndroid 11+ 推荐用「无线调试」方式");
            btnPrimary.setText("打开 Shizuku 开始激活");
            btnPrimary.setBackground(Tools.pillButton(this, 26, true));
            btnPrimary.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ShizukuManager.openShizuku(WirelessDebugActivity.this);
                }
            });
            btnSecondary.setText("打开系统「开发者选项」");
            btnSecondary.setBackground(Tools.pillButton(this, 22, false));
            btnSecondary.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openDevSettings();
                }
            });
            buildSteps(stepsForActivate());
        } else if (!ShizukuManager.isAuthorized()) {
            stateTitle.setText("已连接 · 待授权");
            stateTitle.setTextColor(Color.rgb(80, 210, 150));
            stateDetail.setText("Shizuku 服务已连接，点击下方按钮授予权限");
            btnPrimary.setText("立即授权");
            btnPrimary.setBackground(Tools.pillButton(this, 26, true));
            btnPrimary.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ShizukuManager.requestPermission(WirelessDebugActivity.this);
                }
            });
            btnSecondary.setText("刷新状态");
            btnSecondary.setBackground(Tools.pillButton(this, 22, false));
            btnSecondary.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    refresh();
                }
            });
            buildSteps(new String[]{
                    "点击「立即授权」，在 Shizuku 授权页选择本应用",
                    "授权成功后状态变为「已就绪」",
            });
        } else {
            stateTitle.setText("已就绪 · 全部可用");
            stateTitle.setTextColor(Color.rgb(80, 210, 150));
            stateDetail.setText("无线调试已激活，Shizuku 权限已授予\n可前往「应用管理」使用静默停用/卸载\n可前往「设备检测」读取深度信息");
            btnPrimary.setText("去应用管理试试");
            btnPrimary.setBackground(Tools.pillButton(this, 26, true));
            btnPrimary.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(WirelessDebugActivity.this, AppListActivity.class));
                }
            });
            btnSecondary.setText("重新检测状态");
            btnSecondary.setBackground(Tools.pillButton(this, 22, false));
            btnSecondary.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    refresh();
                }
            });
            buildSteps(new String[]{
                    "Shizuku 服务运行中，权限已授予",
                    "全部 Shizuku 功能可正常使用",
            });
        }
    }

    /** 无线调试激活分步引导（含品牌特殊路径与鸿蒙 ADB 备选） */
    private String[] stepsForActivate() {
        String brand = (Build.MANUFACTURER + Build.BRAND).toLowerCase();
        if (brand.contains("huawei") || brand.contains("honor") || brand.contains("harmony")) {
            return new String[]{
                    "① 设置 → 系统和更新 → 开发者选项",
                    "② 打开「USB 调试」",
                    "③ 打开「无线调试」，点「使用配对码配对设备」",
                    "④ 回到 Shizuku App → 通过无线调试启动",
                    "⑤ 输入屏幕上的 6 位配对码，等待连接成功",
                    "⑥ 鸿蒙如无配对码选项或连接失败：用电脑 ADB 方式（见下方提示）",
            };
        }
        if (brand.contains("xiaomi") || brand.contains("redmi")) {
            return new String[]{
                    "① 设置 → 更多设置 → 开发者选项",
                    "② 打开「USB 调试」和「无线调试」",
                    "③ 点「无线调试」→「使用配对码配对设备」",
                    "④ Shizuku App 点「通过无线调试启动」，输入 6 位配对码",
                    "⑤ 连接成功会自动弹授权，允许本应用",
            };
        }
        return new String[]{
                "① 设置 → 系统 → 开发者选项",
                "② 打开「无线调试」，点「使用配对码配对设备」",
                "③ 在 Shizuku App 选「通过无线调试启动」",
                "④ 输入屏幕上的 6 位配对码",
                "⑤ 连接成功自动弹授权，点允许即可",
        };
    }

    private void openDevSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception ignored) {
            }
        }
    }

    private void buildSteps(String[] steps) {
        stepBox.removeAllViews();
        TextView h = new TextView(this);
        h.setText("分步引导");
        h.setTextSize(15);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setTextColor(Tools.textColor(this));
        stepBox.addView(h);
        stepBox.addView(spacer(10));
        for (int i = 0; i < steps.length; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);
            row.setPadding(0, dp(6), 0, dp(6));

            TextView num = new TextView(this);
            num.setText(String.valueOf(i + 1));
            num.setTextSize(12);
            num.setTypeface(Typeface.DEFAULT_BOLD);
            num.setTextColor(Color.WHITE);
            num.setGravity(Gravity.CENTER);
            GradientDrawable nb = new GradientDrawable();
            nb.setColor(Tools.accentColor(this));
            nb.setCornerRadius(dp(11));
            num.setBackground(nb);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(dp(22), dp(22));
            row.addView(num, np);

            TextView tx = new TextView(this);
            tx.setText(steps[i]);
            tx.setTextSize(13);
            tx.setTextColor(Tools.textColor(this));
            tx.setLineSpacing(0, 1.15f);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tp.setMarginStart(dp(10));
            row.addView(tx, tp);

            stepBox.addView(row);
        }
        TextView note = new TextView(this);
        note.setText("提示：① Shizuku 必须为 v13 及以上（旧版连不上新版协议）。\n"
                + "② 无线调试连不上（尤其鸿蒙）：电脑装 adb → 手机连 USB 开 USB 调试 →\n"
                + "   打开 Shizuku App 复制「ADB 方式启动」里的命令，在电脑终端执行，\n"
                + "   执行成功即激活，免配对码。\n"
                + "③ 配对码在系统「无线调试 → 使用配对码配对设备」页面显示，不要退出该页。");
        note.setTextSize(12);
        note.setTextColor(Tools.subTextColor(this));
        note.setPadding(0, dp(10), 0, 0);
        stepBox.addView(note);
    }

    private void startPolling() {
        scanning = true;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!scanning) return;
                refresh();
                handler.postDelayed(this, 1500);
            }
        }, 1500);
    }

    private LinearLayout glassCard() {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(dp(18), dp(18), dp(18), dp(18));
        ll.setBackground(Tools.liquidGlassCard(this, 24));
        Tools.setElevation(ll, 10);
        return ll;
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
