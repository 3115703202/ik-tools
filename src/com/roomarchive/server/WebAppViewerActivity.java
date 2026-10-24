/** 网页转应用：全屏打开（WebView 壳，顶部迷你工具条） */
package com.roomarchive.server;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

public class WebAppViewerActivity extends Activity {

    private WebView wv;
    private String name;
    private String type;
    private String content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Tools.applyStatusBar(this);
        name = getIntent().getStringExtra("name");
        type = getIntent().getStringExtra("type");
        content = getIntent().getStringExtra("content");
        if (name == null) name = "";
        if (type == null) type = "url";
        if (content == null) content = "";

        boolean dark = Tools.isDark(this);
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(Tools.bgColor(this));

        // 顶部工具条：返回 | 标题 | 刷新
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(dark ? 0xFF1C212E : 0xFFFFFFFF);
        bar.setPadding(dp(8), dp(10), dp(8), dp(10));

        TextView back = new TextView(this);
        back.setText("返回");
        back.setTextSize(15);
        back.setTextColor(Color.rgb(37, 99, 235));
        back.setPadding(dp(6), dp(4), dp(10), dp(4));
        back.setClickable(true);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        bar.addView(back);

        TextView title = new TextView(this);
        title.setText(name);
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Tools.textColor(this));
        title.setSingleLine(true);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView refresh = new TextView(this);
        refresh.setText("刷新");
        refresh.setTextSize(14);
        refresh.setTextColor(Color.rgb(37, 99, 235));
        Icons.attachToText(refresh, "refresh", Color.rgb(37, 99, 235), 14, 4);
        refresh.setPadding(dp(10), dp(4), dp(6), dp(4));
        refresh.setClickable(true);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (wv != null) wv.reload();
            }
        });
        bar.addView(refresh);

        outer.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // WebView
        wv = new WebView(this);
        wv.setBackgroundColor(Tools.bgColor(this));
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setAllowFileAccess(true);
        wv.setWebViewClient(new WebViewClient());
        wv.setWebChromeClient(new WebChromeClient());
        outer.addView(wv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(outer);
        load();
    }

    private void load() {
        if ("html".equals(type)) {
            wv.loadDataWithBaseURL("file:///android_asset/",
                    content, "text/html", "UTF-8", null);
        } else {
            String u = content;
            if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u;
            wv.loadUrl(u);
        }
    }

    @Override
    public void onBackPressed() {
        if (wv != null && wv.canGoBack()) {
            wv.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (wv != null) {
            wv.stopLoading();
            wv.destroy();
            wv = null;
        }
        super.onDestroy();
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
