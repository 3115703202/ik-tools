package com.roomarchive.server;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

/**
 * 网盘应用内下载页：不跳出浏览器。
 * 分享页在本应用内打开，用户登录 / 填提取码后点页面里的「下载」，
 * 下载请求会被本 Activity 拦截并交给内置下载器，直接存进「IK工具箱」目录。
 */
public class NetDiskWebActivity extends Activity {

    public static final String EXTRA_URL = "url";

    private WebView web;
    private TextView statusText;
    private ProgressDialog progress;
    private String pageUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String url = getIntent().getStringExtra(EXTRA_URL);
        if (url == null || url.length() == 0) {
            Toast.makeText(this, "链接为空", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        pageUrl = url;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Tools.bgWithGlow(this));

        // 顶栏
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(10), dp(12), dp(10));

        TextView back = new TextView(this);
        back.setText("‹ 返回");
        back.setTextSize(15);
        back.setTextColor(Tools.accentColor(this));
        back.setClickable(true);
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        TextView title = new TextView(this);
        title.setText(shortDomain(url));
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Tools.textColor(this));
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        bar.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView copy = new TextView(this);
        copy.setText("复制链接");
        copy.setTextSize(12);
        copy.setTextColor(Tools.subTextColor(this));
        copy.setClickable(true);
        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("url", pageUrl));
            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
        });
        bar.addView(copy);

        root.addView(bar);

        // 提示条
        statusText = new TextView(this);
        statusText.setTextSize(11);
        statusText.setTextColor(Tools.subTextColor(this));
        statusText.setPadding(dp(14), dp(6), dp(14), dp(8));
        statusText.setText("登录 / 输入提取码后点页面里的下载按钮，文件会直接存进「IK工具箱」，不会跳浏览器。");
        root.addView(statusText);

        web = new WebView(this);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setSupportZoom(true);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        ws.setSupportMultipleWindows(true);
        ws.setJavaScriptCanOpenWindowsAutomatically(true);
        try {
            CookieManager.getInstance().setAcceptCookie(true);
            CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        } catch (Throwable ignored) {
        }

        web.setWebChromeClient(new WebChromeClient() {
            // 网盘常用 window.open 触发下载，不接管就会被丢掉（点了没反应）
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                                          android.os.Message resultMsg) {
                final WebView tmp = new WebView(NetDiskWebActivity.this);
                tmp.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, String u) {
                        if (u != null && u.startsWith("http")) web.loadUrl(u);
                        tmp.destroy();
                        return true;
                    }
                });
                WebView.WebViewTransport transport =
                        (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(tmp);
                resultMsg.sendToTarget();
                return true;
            }

            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress >= 100 && statusText != null) {
                    statusText.setText("登录 / 输入提取码后点页面里的下载按钮，文件会直接存进「IK工具箱」，不会跳浏览器。");
                }
            }
        });

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String u) {
                if (u.startsWith("magnet:") || u.startsWith("thunder://")) {
                    Toast.makeText(NetDiskWebActivity.this, "磁力/迅雷链接暂不支持内置下载", Toast.LENGTH_SHORT).show();
                    return true;
                }
                if (u.startsWith("intent://") || u.startsWith("weixin://") || u.startsWith("alipays://")) {
                    return true; // 不跳出应用
                }
                // 网盘客户端专属 scheme：不拦死，交给系统，至少别让用户点了没反应
                if (u.startsWith("baiduboxapp://") || u.startsWith("quark://")
                        || u.startsWith("pdsbrowser://") || u.startsWith("alipan://")
                        || u.startsWith("ucloud://") || u.startsWith("thunder://")) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (Throwable t) {
                        Toast.makeText(NetDiskWebActivity.this,
                                "需要对应的网盘 App 才能继续，请先在页面里完成登录再点下载",
                                Toast.LENGTH_LONG).show();
                    }
                    return true;
                }
                pageUrl = u;
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String u) {
                pageUrl = u;
            }
        });

        web.setDownloadListener((dlUrl, userAgent, contentDisposition, mimetype, contentLength) -> {
            if (dlUrl.startsWith("blob:") || dlUrl.startsWith("data:")) {
                Toast.makeText(this, "该链接暂不支持内置下载，试试页面里的其他下载按钮", Toast.LENGTH_LONG).show();
                return;
            }
            startDownload(dlUrl, pageUrl);
        });

        root.addView(web, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        web.loadUrl(url);
    }

    private void startDownload(final String dlUrl, final String referer) {
        progress = new ProgressDialog(this);
        progress.setTitle("正在下载到 IK工具箱");
        progress.setMessage("连接中…");
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setMax(100);
        progress.setCancelable(false);
        progress.show();

        PanDownloader.start(this, dlUrl, referer, null, new PanDownloader.Listener() {
            @Override public void onStart(String name, long total) {
                if (progress != null && progress.isShowing()) {
                    progress.setMessage(name + (total > 0 ? " · " + (total / 1024 / 1024) + "MB" : ""));
                }
            }
            @Override public void onProgress(String name, int percent, long done, long total) {
                if (progress != null && progress.isShowing()) {
                    if (percent >= 0) {
                        progress.setProgress(percent);
                        progress.setMessage(name + " · " + (done / 1024 / 1024) + "MB / "
                                + (total / 1024 / 1024) + "MB");
                    } else {
                        progress.setMessage(name + " · " + (done / 1024 / 1024) + "MB");
                    }
                }
            }
            @Override public void onDone(File file) {
                dismiss();
                showDone(file);
            }
            @Override public void onError(String msg) {
                dismiss();
                Toast.makeText(NetDiskWebActivity.this,
                        "下载失败：" + msg + "\n（大文件或需要登录的网盘，请先在本页登录再点下载）",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void dismiss() {
        try {
            if (progress != null && progress.isShowing()) progress.dismiss();
        } catch (Throwable ignored) {
        }
        progress = null;
    }

    private void showDone(File f) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(4), dp(4), dp(4), dp(4));

        TextView t = new TextView(this);
        t.setText("下载完成\n" + f.getName() + "\n" + (f.length() / 1024 / 1024) + "MB\n"
                + f.getParent());
        t.setTextSize(13);
        t.setTextColor(Tools.textColor(this));
        box.addView(t);

        final android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
        b.setTitle("已保存到 IK工具箱");
        b.setMessage(f.getName() + "\n" + (f.length() / 1024 / 1024) + "MB\n" + f.getParent());
        b.setPositiveButton("打开", (d, w) -> {
            try {
                Intent it = new Intent(Intent.ACTION_VIEW);
                it.setDataAndType(IkFileProvider.uriFor(this, f), guessMime(f.getName()));
                it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(it);
            } catch (Throwable e) {
                Toast.makeText(this, "没有能打开该文件的应用", Toast.LENGTH_SHORT).show();
            }
        });
        b.setNegativeButton("继续", null);
        b.show();
    }

    private String guessMime(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".apk")) return "application/vnd.android.package-archive";
        if (n.endsWith(".mp4")) return "video/mp4";
        if (n.endsWith(".mp3")) return "audio/mpeg";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".zip")) return "application/zip";
        return "*/*";
    }

    private String shortDomain(String u) {
        try {
            String host = Uri.parse(u).getHost();
            return host == null ? "网盘下载" : host;
        } catch (Throwable t) {
            return "网盘下载";
        }
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        dismiss();
        try {
            if (web != null) {
                web.stopLoading();
                web.destroy();
            }
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
