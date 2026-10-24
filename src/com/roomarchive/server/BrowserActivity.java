package com.roomarchive.server;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 内置浏览器：Via 风格轻量浏览器（多标签 / 无图模式 / 起始页） */
public class BrowserActivity extends BaseActivity {

    private LinearLayout tabBar;
    private LinearLayout content;
    private EditText addressBox;
    private Button btnNoImage;
    private List<WebView> tabs = new ArrayList<WebView>();
    // 浏览器标识(UA)切换
    private int uaMode = 0;
    private static final String[] UA_NAMES = {
            "默认(安卓)", "电脑 Windows", "电脑 Mac", "iPhone 手机",
            "iPad 平板", "微信内置浏览器", "百度App", "谷歌搜索蜘蛛"};
    private static final String[] UA_VALUES = {
            "",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1",
            "Mozilla/5.0 (iPad; CPU OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1",
            "Mozilla/5.0 (Linux; Android 10; PEX-AL00 Build/HUAWEIPEX-AL00) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/78.0.3904.108 Mobile Safari/537.36 MicroMessenger/8.0.30.2260 WeChat/arm64 Weixin",
            "Mozilla/5.0 (Linux; Android 10; PEX-AL00 Build/HUAWEIPEX-AL00) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/78.0.3904.108 Mobile Safari/537.36 baiduboxapp/13.31.0.10",
            "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"};
    private int currentTab = 0;
    private boolean noImage = false;

    private static final String HOME_URL = "about:start";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        // 支持外部直达：Intent 带 url 时直接打开该网页（用于功能卡/分享）
        String intentUrl = getIntent().getStringExtra("url");
        if (intentUrl != null && !intentUrl.trim().isEmpty()) {
            String intentTitle = getIntent().getStringExtra("title");
            openTab(intentUrl.trim(), intentTitle != null ? intentTitle : "网页");
        } else {
            openTab(HOME_URL, "起始页");
        }
        setContentView(content);
    }

    private void buildUi() {
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        // ===== 顶部：地址栏 =====
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setPadding(dp(6), dp(8), dp(6), dp(8));
        top.setBackgroundColor(Tools.isDark(this) ? 0xFF1C212E : 0xFFFFFFFF);

        addressBox = new EditText(this);
        addressBox.setHint("输入网址或搜索内容");
        addressBox.setTextSize(14);
        addressBox.setTextColor(Tools.textColor(this));
        addressBox.setSingleLine(true);
        addressBox.setSelectAllOnFocus(true);
        GradientDrawable addrBg = new GradientDrawable();
        addrBg.setColor(Tools.cardColor(this));
        addrBg.setCornerRadius(Tools.dp(this, 10));
        addrBg.setStroke(Tools.dp(this, 1),
                Tools.isDark(this) ? 0xFF2A3040 : 0xFFE4E8EE);
        addressBox.setBackground(addrBg);
        addressBox.setPadding(dp(10), dp(4), dp(10), dp(4));
        addressBox.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                    navigate();
                    return true;
                }
                return false;
            }
        });
        top.addView(addressBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button go = new Button(this);
        go.setText("前往");
        go.setTextSize(13);
        go.setTextColor(Tools.accentColor(this));
        go.setBackgroundColor(0x00000000);
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigate();
            }
        });
        top.addView(go);
        content.addView(top);

        // ===== 标签栏 =====
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setBackgroundColor(Tools.isDark(this) ? 0xFF161B27 : 0xFFF2F4F8);
        tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        hsv.addView(tabBar);
        content.addView(hsv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));

        // ===== 网页区 =====
        LinearLayout pageWrap = new LinearLayout(this);
        pageWrap.setOrientation(LinearLayout.VERTICAL);
        pageWrap.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        content.addView(pageWrap);

        // 把 pageWrap 存为成员用于 addTab
        pageContainer = pageWrap;

        // ===== 底部工具栏 =====
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setPadding(dp(4), dp(4), dp(4), dp(4));
        bottom.setBackgroundColor(Tools.isDark(this) ? 0xFF1C212E : 0xFFFFFFFF);

        btnNoImage = toolButton("无图", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleNoImage();
            }
        });
        bottom.addView(btnNoImage, new LinearLayout.LayoutParams(0, dp(44), 1f));
        bottom.addView(toolButton("←", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                back();
            }
        }), new LinearLayout.LayoutParams(0, dp(44), 1f));
        bottom.addView(toolButton("→", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                forward();
            }
        }), new LinearLayout.LayoutParams(0, dp(44), 1f));
        bottom.addView(toolButton("刷新", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                reload();
            }
        }), new LinearLayout.LayoutParams(0, dp(44), 1f));
        bottom.addView(toolButton("标识", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickUaPicker();
            }
        }), new LinearLayout.LayoutParams(0, dp(44), 1f));
        bottom.addView(toolButton("历史", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHistory();
            }
        }), new LinearLayout.LayoutParams(0, dp(44), 1f));
        bottom.addView(toolButton("＋", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openTab(HOME_URL, "起始页");
            }
        }), new LinearLayout.LayoutParams(0, dp(44), 1f));
        content.addView(bottom);
    }

    private LinearLayout pageContainer;

    private Button toolButton(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(13);
        b.setPadding(0, 0, 0, 0);
        b.setOnClickListener(l);
        return b;
    }

    // ================= 标签管理 =================

    private void openTab(String url, String title) {
        if (tabs.size() >= 5) {
            Toast.makeText(this, "最多同时开 5 个标签", Toast.LENGTH_SHORT).show();
            return;
        }
        final WebView wv = new WebView(this);
        if (uaMode > 0 && uaMode < UA_VALUES.length) {
            wv.getSettings().setUserAgentString(UA_VALUES[uaMode]);
        }
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setBlockNetworkImage(noImage);
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(view, request.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(view, url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (tabs.indexOf(view) == currentTab) {
                    addressBox.setText(url);
                }
                updateTabTitles();
                // 记录历史（跳过 about:start 和空 URL）
                if (url != null && !url.startsWith("about:") && !url.startsWith("data:")) {
                    String title = view.getTitle();
                    if (title == null || title.isEmpty()) title = url;
                    saveHistory(url, title);
                }
            }
        });
        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onReceivedTitle(WebView view, String title) {
                updateTabTitles();
            }
        });

        tabs.add(wv);
        int idx = tabs.size() - 1;
        pageContainer.addView(wv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        addTabChip(idx, title);
        switchTo(idx);
        if (HOME_URL.equals(url)) {
            wv.loadDataWithBaseURL(null, START_PAGE, "text/html", "UTF-8", null);
        } else {
            wv.loadUrl(url);
        }
    }

    private void addTabChip(final int idx, String title) {
        final Button chip = new Button(this);
        chip.setText(title);
        chip.setTextSize(12);
        chip.setPadding(dp(10), 0, dp(10), 0);
        chip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchTo(idx);
            }
        });
        chip.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                closeTab(idx);
                return true;
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(36));
        lp.setMargins(dp(4), dp(2), dp(4), dp(2));
        chip.setLayoutParams(lp);
        tabBar.addView(chip);
        chips.add(chip);
    }

    private List<Button> chips = new ArrayList<Button>();

    private void switchTo(int idx) {
        if (idx < 0 || idx >= tabs.size()) return;
        currentTab = idx;
        for (int i = 0; i < tabs.size(); i++) {
            tabs.get(i).setVisibility(i == idx ? View.VISIBLE : View.GONE);
        }
        updateTabTitles();
        String url = tabs.get(idx).getUrl();
        addressBox.setText(url == null ? "" : url);
    }

    private void closeTab(int idx) {
        if (tabs.size() <= 1) {
            Toast.makeText(this, "至少保留一个标签", Toast.LENGTH_SHORT).show();
            return;
        }
        WebView wv = tabs.get(idx);
        pageContainer.removeView(wv);
        wv.destroy();
        tabs.remove(idx);
        Button c = chips.get(idx);
        tabBar.removeView(c);
        chips.remove(idx);
        if (currentTab >= tabs.size()) currentTab = tabs.size() - 1;
        if (currentTab == idx) {
            switchTo(currentTab);
        } else if (currentTab > idx) {
            currentTab--;
            switchTo(currentTab);
        }
        updateTabTitles();
    }

    private void updateTabTitles() {
        for (int i = 0; i < chips.size(); i++) {
            WebView wv = tabs.get(i);
            String t = wv.getTitle();
            if (t == null || t.isEmpty()) t = "标签" + (i + 1);
            if (t.length() > 6) t = t.substring(0, 6) + "…";
            chips.get(i).setText(t);
            if (i == currentTab) {
                chips.get(i).setTextColor(Color.WHITE);
                chips.get(i).setBackgroundColor(Color.rgb(37, 99, 235));
            } else {
                chips.get(i).setTextColor(Color.rgb(80, 88, 100));
                chips.get(i).setBackgroundColor(Color.rgb(225, 228, 234));
            }
        }
    }

    // ================= 操作 =================

    private void navigate() {
        String input = addressBox.getText().toString().trim();
        if (input.isEmpty()) return;
        // 带协议前缀的地址（http/https/自定义协议）
        if (input.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
            if (input.startsWith("http://") || input.startsWith("https://")) {
                WebView wv = currentWebView();
                if (wv != null) wv.loadUrl(input);
            } else {
                externalOpen(input);
            }
            return;
        }
        String url;
        if (isUrl(input)) {
            url = "https://" + input;
        } else {
            url = "https://www.baidu.com/s?wd=" + java.net.URLEncoder.encode(input);
        }
        WebView wv = currentWebView();
        if (wv != null) wv.loadUrl(url);
    }

    /** 统一处理链接：网页链接在 WebView 内打开，自定义协议跳转外部应用 */
    private boolean handleUrl(WebView view, String url) {
        if (url == null) return false;
        if (url.startsWith("http://") || url.startsWith("https://")
                || url.startsWith("about:") || url.startsWith("data:")
                || url.startsWith("file:") || url.startsWith("javascript:")) {
            view.loadUrl(url);
            return true;
        }
        externalOpen(url);
        return true;
    }

    /** 自定义协议（bytedance://、weixin://、mqq:// 等）→ 唤起系统对应 App */
    private void externalOpen(String url) {
        try {
            Intent it = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(it);
            String scheme = url.contains("://") ? url.substring(0, url.indexOf("://")) : url;
            Toast.makeText(this, "已调用系统打开 " + scheme + " 协议", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "没有应用能打开这个链接：" + url, Toast.LENGTH_LONG).show();
        }
    }

    private boolean isUrl(String s) {
        if (s.contains(" ")) return false;
        return s.contains(".") && !s.endsWith(".");
    }

    private WebView currentWebView() {
        if (tabs.isEmpty()) return null;
        return tabs.get(currentTab);
    }

    private void back() {
        WebView wv = currentWebView();
        if (wv != null && wv.canGoBack()) {
            wv.goBack();
        } else if (wv != null) {
            openTab(HOME_URL, "起始页");
            closeTab(currentTab);
        }
    }

    private void forward() {
        WebView wv = currentWebView();
        if (wv != null && wv.canGoForward()) wv.goForward();
    }

    private void reload() {
        WebView wv = currentWebView();
        if (wv != null) wv.reload();
    }

    // 浏览器标识(UA)切换：电脑/手机/平板/微信/百度/蜘蛛等
    private void clickUaPicker() {
        new AlertDialog.Builder(this)
                .setTitle("切换浏览器标识 (UA)")
                .setSingleChoiceItems(UA_NAMES, uaMode, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        uaMode = which;
                        for (WebView wv : tabs) {
                            if (UA_VALUES[which].isEmpty()) {
                                wv.getSettings().setUserAgentString(null);
                            } else {
                                wv.getSettings().setUserAgentString(UA_VALUES[which]);
                            }
                        }
                        WebView cur = currentWebView();
                        if (cur != null) cur.reload();
                        d.dismiss();
                        Toast.makeText(BrowserActivity.this, "已切换为: " + UA_NAMES[which], Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void toggleNoImage() {
        noImage = !noImage;
        for (WebView wv : tabs) {
            wv.getSettings().setBlockNetworkImage(noImage);
            if (noImage) wv.reload();
        }
        btnNoImage.setText(noImage ? "有图" : "无图");
        Toast.makeText(this, noImage ? "无图模式已开启（省流量）" : "无图模式已关闭", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        WebView wv = currentWebView();
        if (wv != null && wv.canGoBack()) {
            wv.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (WebView wv : tabs) {
            wv.destroy();
        }
        tabs.clear();
    }

    // ================= 起始页 =================

    private static final String START_PAGE = "<!DOCTYPE html><html><head><meta charset='utf-8'>"
            + "<meta name='viewport' content='width=device-width, initial-scale=1'>"
            + "<style>body{background:#f5f6f8;font-family:sans-serif;margin:0;padding:24px 16px;}"
            + "h1{font-size:22px;color:#1a2332;text-align:center;margin:40px 0 6px}"
            + "p{text-align:center;color:#888;font-size:13px;margin-bottom:28px}"
            + ".grid{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;max-width:420px;margin:0 auto}"
            + ".site{background:#fff;border-radius:14px;padding:16px 4px;text-align:center;"
            + "box-shadow:0 1px 4px rgba(0,0,0,.06);text-decoration:none}"
            + ".site b{display:block;font-size:15px;color:#222;margin-top:8px}"
            + ".ico{width:40px;height:40px;border-radius:10px;display:inline-flex;align-items:center;"
            + "justify-content:center;font-size:18px;color:#fff}"
            + "</style></head><body>"
            + "<h1>时空浏览器</h1><p>轻量快速 · 多标签 · 无图省流</p><div class='grid'>"
            + "<a class='site' href='https://www.baidu.com'><span class='ico' style='background:#2932e1'>百</span><b>百度</b></a>"
            + "<a class='site' href='https://www.douyin.com'><span class='ico' style='background:#111'>抖</span><b>抖音</b></a>"
            + "<a class='site' href='https://www.bilibili.com'><span class='ico' style='background:#fb7299'>B</span><b>哔哩哔哩</b></a>"
            + "<a class='site' href='https://www.taobao.com'><span class='ico' style='background:#ff5000'>淘</span><b>淘宝</b></a>"
            + "<a class='site' href='https://www.jd.com'><span class='ico' style='background:#e1251b'>京</span><b>京东</b></a>"
            + "<a class='site' href='https://weibo.com'><span class='ico' style='background:#e6162d'>微</span><b>微博</b></a>"
            + "<a class='site' href='https://www.zhihu.com'><span class='ico' style='background:#056de8'>知</span><b>知乎</b></a>"
            + "<a class='site' href='https://www.qq.com'><span class='ico' style='background:#1d6ff2'>腾</span><b>腾讯网</b></a>"
            + "<a class='site' href='https://www.163.com'><span class='ico' style='background:#dd0000'>网</span><b>网易</b></a>"
            + "</div></body></html>";

    // ================= 历史记录 =================

    private static final String HIST_PREF = "browser_history";

    private void saveHistory(String url, String title) {
        try {
            android.content.SharedPreferences sp = getSharedPreferences(HIST_PREF, MODE_PRIVATE);
            // TreeMap 按时间戳倒序
            TreeMap<String, String> map = new TreeMap<String, String>();
            for (Map.Entry<String, ?> en : sp.getAll().entrySet()) {
                map.put(en.getKey(), String.valueOf(en.getValue()));
            }
            map.put(String.valueOf(System.currentTimeMillis()), title + "\n" + url);
            // 最多保留 100 条
            while (map.size() > 100) map.remove(map.firstKey());
            android.content.SharedPreferences.Editor e = sp.edit().clear();
            for (Map.Entry<String, String> en : map.entrySet()) e.putString(en.getKey(), en.getValue());
            e.apply();
        } catch (Throwable ignored) {
        }
    }

    private void showHistory() {
        android.content.SharedPreferences sp = getSharedPreferences(HIST_PREF, MODE_PRIVATE);
        TreeMap<String, String> map = new TreeMap<String, String>();
        for (Map.Entry<String, ?> en : sp.getAll().entrySet()) {
            map.put(en.getKey(), String.valueOf(en.getValue()));
        }
        if (map.isEmpty()) {
            Toast.makeText(this, "暂无历史记录", Toast.LENGTH_SHORT).show();
            return;
        }
        final List<String[]> items = new ArrayList<String[]>(); // [title, url]
        final List<String> keys = new ArrayList<String>();
        // 倒序（最新在前）
        List<String> keysDesc = new ArrayList<String>(map.keySet());
        java.util.Collections.reverse(keysDesc);
        for (String k : keysDesc) {
            String v = (String) map.get(k);
            int nl = v.indexOf('\n');
            String title = nl > 0 ? v.substring(0, nl) : v;
            String url = nl > 0 ? v.substring(nl + 1) : v;
            items.add(new String[]{title, url});
            keys.add(k);
        }
        String[] labels = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            labels[i] = items.get(i)[0] + "\n" + items.get(i)[1];
        }
        new AlertDialog.Builder(this)
                .setTitle("历史记录（" + items.size() + "条）")
                .setItems(labels, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        String url = items.get(which)[1];
                        WebView wv = currentWebView();
                        if (wv != null) wv.loadUrl(url);
                    }
                })
                .setNeutralButton("清空", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        getSharedPreferences(HIST_PREF, MODE_PRIVATE).edit().clear().apply();
                        Toast.makeText(BrowserActivity.this, "历史已清空", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
