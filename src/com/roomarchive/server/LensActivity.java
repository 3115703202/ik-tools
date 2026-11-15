package com.roomarchive.server;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

/** 看见 · LENS 网页版：直接加载用户指定的识别网页，保留摄像头权限与页面交互。 */
public class LensActivity extends Activity {
    private static final String URL = "https://youlongtool.com/shang";
    private WebView web;
    private ProgressBar bar;

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        Tools.applyStatusBar(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF070B10);
        web = new WebView(this);
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-1, dp(2), android.view.Gravity.TOP);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        root.addView(bar, blp);
        setContentView(root);

        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setAllowFileAccess(false);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setUserAgentString(ws.getUserAgentString() + " IKToolbox/4.5.35");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, String u) { return false; }
            @Override public void onPageFinished(WebView v, String u) { bar.setVisibility(View.GONE); }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView v, int p) {
                bar.setVisibility(p >= 100 ? View.GONE : View.VISIBLE);
                bar.setProgress(p);
            }
            @Override public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    if (request.getResources() != null) request.grant(request.getResources());
                    else request.deny();
                });
            }
        });
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 7);
        }
        web.loadUrl(URL);
    }

    @Override public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        if (code == 7 && web != null) web.reload();
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (web != null) { web.stopLoading(); web.destroy(); web = null; }
        super.onDestroy();
    }

    int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
