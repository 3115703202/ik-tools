package com.roomarchive.server;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

/**
 * IK工具箱 · 检查更新
 * 联网读取版本清单（ik-update.json），发现新版本后下载并安装 APK。
 * 检查地址可在界面修改（默认指向本机房型库服务器 8080 端口的 ik-update.json）。
 */
public class UpdateActivity extends Activity {

    private EditText urlEdit;
    private TextView curVerText, tipText;
    private Button checkBtn;
    private ProgressDialog prog;
    private String myVersion;
    private int myCode;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Tools.applyStatusBar(this);
        prefs = getSharedPreferences("ik_prefs", Context.MODE_PRIVATE);
        try {
            android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            myVersion = pi.versionName;
            myCode = pi.versionCode;
        } catch (Exception e) {
            myVersion = "?";
            myCode = 0;
        }

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackground(Tools.bgWithGlow(this));
        outer.setPadding(dp(16), dp(20), dp(16), dp(18));
        setContentView(outer);

        TextView title = Tools.pageTitle(this, "检查更新");
        outer.addView(title);

        curVerText = new TextView(this);
        curVerText.setTextColor(Tools.subTextColor(this));
        curVerText.setTextSize(13);
        curVerText.setText("当前版本：v" + myVersion);
        curVerText.setPadding(0, dp(6), 0, dp(4));
        outer.addView(curVerText);

        tipText = new TextView(this);
        tipText.setTextColor(Tools.subTextColor(this));
        tipText.setTextSize(12);
        tipText.setText("从官网或版本清单读取最新版本，发现新版本即可一键下载安装。\n" +
                "默认指向官网（国内直连，随发版自动更新）；局域网自建时填 ik-update.json 地址即可。\n" +
                "App 每次启动也会自动检查一次。");
        tipText.setPadding(0, dp(6), 0, dp(14));
        outer.addView(tipText);

        TextView urlLabel = new TextView(this);
        urlLabel.setText("版本清单地址");
        urlLabel.setTextColor(Tools.textColor(this));
        urlLabel.setTextSize(14);
        urlLabel.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        outer.addView(urlLabel);

        urlEdit = new EditText(this);
        urlEdit.setText(UpdateChecker.defaultUrl(this));
        urlEdit.setTextColor(Tools.textColor(this));
        urlEdit.setTextSize(13);
        urlEdit.setHintTextColor(Tools.subTextColor(this));
        urlEdit.setSingleLine(true);
        GradientDrawable urlBg = new GradientDrawable();
        urlBg.setColor(Tools.cardColor(this));
        urlBg.setCornerRadius(Tools.dp(this, 12));
        urlBg.setStroke(Tools.dp(this, 1),
                Tools.isDark(this) ? 0xFF2A3040 : 0xFFE4E8EE);
        urlEdit.setBackground(urlBg);
        urlEdit.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        elp.setMargins(0, dp(6), 0, dp(14));
        outer.addView(urlEdit, elp);

        checkBtn = new Button(this);
        checkBtn.setText("检查更新");
        checkBtn.setTextColor(Color.WHITE);
        checkBtn.setTextSize(15);
        checkBtn.setBackground(Tools.pillButton(this, 50, true));
        checkBtn.setAllCaps(false);
        checkBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doCheck(false);
            }
        });
        outer.addView(checkBtn);

        TextView foot = new TextView(this);
        foot.setTextColor(Tools.subTextColor(this));
        foot.setTextSize(11);
        foot.setText("官网模式无需配置；JSON 清单格式：{\"version\":\"2.3.29\",\"versionCode\":84,\"apkUrl\":\"ik-toolbox.apk\",\"note\":\"更新说明\"}");
        foot.setPadding(0, dp(12), 0, 0);
        outer.addView(foot);
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** silent=true 时不打扰（用于启动自检的复用），页面内固定 false */
    void doCheck(boolean silent) {
        String url = urlEdit.getText().toString().trim();
        // 兜底：残留的本机地址强制切官网，避免手机连不上自己
        String low = url.toLowerCase();
        if (low.startsWith("http://127.") || low.startsWith("http://localhost")
                || low.startsWith("https://127.") || low.startsWith("https://localhost")) {
            url = UpdateChecker.OFFICIAL_URL;
            urlEdit.setText(url);
            Toast.makeText(this, "已自动切换到官网地址（原 127.0.0.1 是手机自己，连不上）",
                    Toast.LENGTH_LONG).show();
        }
        if (url.length() == 0) {
            Toast.makeText(this, "请填写版本清单地址", Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.edit().putString("ota_url", url).apply();
        final String targetUrl = url;
        checkBtn.setEnabled(false);
        checkBtn.setText("检查中…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final UpdateChecker.Result r = UpdateChecker.check(targetUrl, myVersion);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        checkBtn.setEnabled(true);
                        checkBtn.setText("检查更新");
                        if (r.error != null) {
                            Toast.makeText(UpdateActivity.this, "检查失败：" + r.error, Toast.LENGTH_LONG).show();
                            return;
                        }
                        if (r.code > myCode) {
                            showUpdateDialog(r);
                        } else {
                            Toast.makeText(UpdateActivity.this,
                                    "当前已是最新版本（v" + myVersion + "）", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }).start();
    }

    private void showUpdateDialog(UpdateChecker.Result r) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("发现新版本 v" + r.version);
        String msg = r.note == null || r.note.length() == 0 ? "点击下载并安装" : r.note;
        if (r.fallback) msg = "（官网暂不可达，已自动切换内置更新信息）\n\n" + msg;
        b.setMessage(msg);
        b.setPositiveButton("立即下载", new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface d, int w) {
                doDownload(r.apkUrl);
            }
        });
        b.setNegativeButton("取消", null);
        b.show();
    }

    private void doDownload(final String apkUrl) {
        prog = new ProgressDialog(this);
        prog.setTitle("下载更新");
        prog.setMessage("正在下载安装包…");
        prog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        prog.setMax(100);
        prog.setCancelable(false);
        prog.show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final File apk = UpdateChecker.download(UpdateActivity.this, apkUrl, myVersion,
                        new UpdateChecker.Progress() {
                            @Override
                            public void onProgress(int percent) {
                                if (percent >= 0) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (prog != null) prog.setProgress(percent);
                                        }
                                    });
                                }
                            }
                        });
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (prog != null) {
                            prog.dismiss();
                            prog = null;
                        }
                        if (apk != null) {
                            try {
                                UpdateChecker.install(UpdateActivity.this, apk);
                            } catch (Exception e) {
                                Toast.makeText(UpdateActivity.this,
                                        "无法打开安装器：请在系统设置中允许安装未知应用", Toast.LENGTH_LONG).show();
                            }
                        } else {
                            Toast.makeText(UpdateActivity.this, "下载失败，请检查网络后重试", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
    }
}
