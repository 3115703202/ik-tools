package com.roomarchive.server;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** 给本地 AI 集中授权：无障碍、悬浮窗、媒体读取、全文件、安装 */
public class AiPermissionActivity extends BaseActivity {

    private static final int REQ_MEDIA = 4001;
    private TextView a11yState, overlayState, mediaState, allFileState, installState, notifyState;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Tools.bgWithGlow(this));
        root.setPadding(dp(16), dp(22), dp(16), dp(26));

        TextView title = Tools.pageTitle(this, "AI 权限中心");
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView sub = new TextView(this);
        sub.setText("本地 AI 要用你的手机，就得有对应的钥匙。缺哪项点哪项。");
        sub.setTextSize(12);
        sub.setTextColor(Tools.subTextColor(this));
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(6), 0, dp(14));
        root.addView(sub);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(10), dp(16), dp(10));
        card.setBackground(Tools.liquidGlassCard(this, 22));
        Tools.setElevation(card, 10);
        root.addView(card);

        a11yState = row(card, "无障碍服务", "AI 读屏、点击、滑动、返回、开应用", v -> openA11y());
        overlayState = row(card, "悬浮窗", "AI 操作时显示悬浮控制条", v -> openOverlay());
        mediaState = row(card, "图片 / 视频 / 音频", "读取相册与媒体文件做识别和转换", v -> askMedia());
        allFileState = row(card, "所有文件访问", "读写 Download、任意目录里的文件", v -> openAllFiles());
        installState = row(card, "安装未知应用", "AI 下载的 APK 能直接装", v -> openInstall());
        notifyState = row(card, "通知", "长任务进度、下载完成提示", v -> askNotify());

        TextView all = new TextView(this);
        all.setText("一键申请（能弹的弹，弹不了的跳设置）");
        all.setGravity(Gravity.CENTER);
        all.setTextSize(14);
        all.setTextColor(0xFFFFFFFF);
        all.setBackground(Tools.pillButton(this, 46, true));
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        al.topMargin = dp(14);
        root.addView(all, al);
        all.setOnClickListener(v -> {
            askMedia();
            askNotify();
            if (!Settings.canDrawOverlays(this)) openOverlay();
            if (!Environment.isExternalStorageManager()) openAllFiles();
        });

        TextView tip = new TextView(this);
        tip.setText("· 无障碍是 AI 替你操作手机的唯一通道，开启后只在你发指令时动作\n"
                + "· 媒体权限只用于读文件信息、做格式转换，不上传\n"
                + "· 全部权限都能在系统设置里随时收回");
        tip.setTextSize(12);
        tip.setTextColor(Tools.subTextColor(this));
        tip.setLineSpacing(dp(5), 1f);
        tip.setPadding(dp(16), dp(14), dp(16), dp(14));
        tip.setBackground(Tools.liquidGlassCard(this, 18));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tl.topMargin = dp(16);
        root.addView(tip, tl);

        setContentView(root);
    }

    private TextView row(LinearLayout card, String name, String desc, android.view.View.OnClickListener click) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(0, dp(10), 0, dp(10));
        r.setOnClickListener(click);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(this);
        t.setText(name);
        t.setTextSize(14);
        t.setTextColor(Tools.textColor(this));
        head.addView(t, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView st = new TextView(this);
        st.setTextSize(11);
        head.addView(st);
        r.addView(head);

        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextSize(11);
        d.setTextColor(Tools.subTextColor(this));
        d.setPadding(0, dp(3), 0, 0);
        r.addView(d);
        card.addView(r);
        return st;
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        set(a11yState, a11yEnabled());
        set(overlayState, Settings.canDrawOverlays(this));
        set(mediaState, hasMedia());
        set(allFileState, Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager());
        set(installState, getPackageManager().canRequestPackageInstalls());
        set(notifyState, Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED);
    }

    private void set(TextView v, boolean ok) {
        v.setText(ok ? "● 已开启" : "○ 未开启 · 点我");
        v.setTextColor(ok ? 0xFF16A34A : 0xFFEF4444);
    }

    private boolean a11yEnabled() {
        if (AiAgentService.ready()) return true;
        try {
            String s = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return s != null && s.contains(getPackageName() + "/.") && s.contains("AiAgentService");
        } catch (Throwable t) { return false; }
    }

    private boolean hasMedia() {
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void openA11y() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            toast("在列表里找到 IK工具箱 AI 操控，打开开关");
        } catch (Throwable t) { toast("打不开设置：" + t.getMessage()); }
    }

    private void openOverlay() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (Throwable t) { toast("打不开设置：" + t.getMessage()); }
    }

    private void openAllFiles() {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } else {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            }
        } catch (Throwable t) {
            try { startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); }
            catch (Throwable t2) { toast("打不开设置：" + t2.getMessage()); }
        }
    }

    private void openInstall() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())));
        } catch (Throwable t) { toast("打不开设置：" + t.getMessage()); }
    }

    private void askMedia() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO}, REQ_MEDIA);
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_MEDIA);
        }
    }

    private void askNotify() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_MEDIA + 1);
        }
    }

    @Override public void onRequestPermissionsResult(int req, String[] p, int[] g) {
        super.onRequestPermissionsResult(req, p, g);
        refresh();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
}
