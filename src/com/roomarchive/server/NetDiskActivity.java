package com.roomarchive.server;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.net.URLEncoder;

/** 网盘解析：粘贴分享链接自动解析直链，历史+收藏+分享面板 */
public class NetDiskActivity extends Activity {

    private EditText input;
    private LinearLayout resultBox;
    private LinearLayout historyBox;
    private LinearLayout favBox;
    private ProgressDialog progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        // 分享面板进来的：从其他App分享文本
        Intent it = getIntent();
        if (it != null && Intent.ACTION_SEND.equals(it.getAction()) && "text/plain".equals(it.getType())) {
            String shared = it.getStringExtra(Intent.EXTRA_TEXT);
            if (shared != null) {
                input.setText(shared.trim());
                parse();
            }
        }
    }

    private void buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setBackground(Tools.bgWithGlow(this));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(20));
        sv.addView(root);

        TextView title = Tools.pageTitle(this, "网盘解析");
        root.addView(title);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(Tools.liquidGlassCard(this, 20));
        Tools.setElevation(card, 8);

        TextView lbl = new TextView(this);
        lbl.setText("粘贴分享链接");
        lbl.setTextSize(14);
        lbl.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        lbl.setTextColor(Tools.textColor(this));
        card.addView(lbl);
        card.addView(spacer(8));

        input = new EditText(this);
        input.setHint("粘贴百度/阿里/夸克/蓝奏/123链接，或磁力 magnet:...");
        input.setTextSize(13);
        input.setSingleLine(false);
        input.setMinLines(2);
        input.setTextColor(Tools.textColor(this));
        input.setHintTextColor(Tools.subTextColor(this));
        GradientDrawable ibg = new GradientDrawable();
        ibg.setColor(Tools.cardColor(this));
        ibg.setCornerRadius(dp(12));
        ibg.setStroke(dp(1), Color.rgb(220, 225, 235));
        input.setBackground(ibg);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.addView(input);
        card.addView(spacer(12));

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        Button pasteBtn = pillButton("读剪贴板", false);
        pasteBtn.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip()) {
                ClipData cd = cm.getPrimaryClip();
                if (cd != null && cd.getItemCount() > 0 && cd.getItemAt(0).getText() != null) {
                    input.setText(cd.getItemAt(0).getText().toString().trim());
                    Toast.makeText(this, "已读取", Toast.LENGTH_SHORT).show();
                }
            }
        });
        btnRow.addView(pasteBtn, new LinearLayout.LayoutParams(0, dp(44), 1f));

        Button parseBtn = pillButton("解析", true);
        parseBtn.setOnClickListener(v -> parse());
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        pp.setMarginStart(dp(10));
        btnRow.addView(parseBtn, pp);

        card.addView(btnRow);
        root.addView(card);
        root.addView(spacer(16));

        resultBox = new LinearLayout(this);
        resultBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(resultBox);

        TextView hLbl = new TextView(this);
        hLbl.setText("历史记录");
        hLbl.setTextSize(14);
        hLbl.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        hLbl.setTextColor(Tools.textColor(this));
        root.addView(hLbl);
        historyBox = new LinearLayout(this);
        historyBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(historyBox);
        root.addView(spacer(12));

        TextView fLbl = new TextView(this);
        fLbl.setText("收藏");
        fLbl.setTextSize(14);
        fLbl.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        fLbl.setTextColor(Tools.textColor(this));
        root.addView(fLbl);
        favBox = new LinearLayout(this);
        favBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(favBox);

        setContentView(sv);
        refreshLists();
    }

    private void parse() {
        String text = input.getText().toString().trim();
        if (TextUtils.isEmpty(text)) {
            Toast.makeText(this, "请先粘贴链接", Toast.LENGTH_SHORT).show();
            return;
        }
        String url = extractUrl(text);
        if (url == null) {
            Toast.makeText(this, "未识别到链接", Toast.LENGTH_SHORT).show();
            return;
        }
        String platform = detectPlatform(url);
        saveHistory(url);
        // 磁力/thunder 直接显示
        if (url.startsWith("magnet:") || url.startsWith("thunder://")) {
            showResult(platform, url, null);
            return;
        }
        // 视频链接（抖音/B站/快手/小红书/TikTok）走视频解析
        if (isVideoLink(url)) {
            doVideoParse(platform, url);
            return;
        }
        // 百度网盘：NFD不支持，直接显示打开按钮
        if (url.contains("pan.baidu.com") || url.contains("yun.baidu.com")) {
            showResult(platform, url, null);
            return;
        }
        // 其他网盘走 NFD 解析
        doApiParse(platform, url);
    }

    private boolean isVideoLink(String url) {
        String u = url.toLowerCase();
        return u.contains("douyin.com") || u.contains("iesdouyin.com") || u.contains("tiktok.com")
                || u.contains("bilibili.com") || u.contains("b23.tv")
                || u.contains("kuaishou.com") || u.contains("chenzhongtech.com")
                || u.contains("xiaohongshu.com") || u.contains("xhslink.com");
    }

    private void doVideoParse(String platform, String url) {
        progress = ProgressDialog.show(this, "视频解析", "正在提取无水印直链...", true, false);
        new Thread(() -> {
            String directLink = null;
            String error = null;
            HttpURLConnection conn = null;
            try {
                String apiUrl = "https://api.douyin.wtf/api?url=" + URLEncoder.encode(url, "UTF-8") + "&minimal=false";
                conn = (HttpURLConnection) new URL(apiUrl).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(25000);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                InputStream is = conn.getInputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                JSONObject jo = new JSONObject(sb.toString());
                // 返回: {"detail":null,"data":{"video_url":"...","image_urls":[...],"...":...}}
                JSONObject data = jo.optJSONObject("data");
                if (data != null) {
                    directLink = data.optString("video_url", "");
                    if (directLink.isEmpty()) directLink = data.optString("nwm_video_url", "");
                    if (directLink.isEmpty()) directLink = data.optString("play_url", "");
                }
                if (directLink == null || directLink.isEmpty()) {
                    error = "解析失败，可能需要登录或该视频不支持";
                }
            } catch (Exception e) {
                error = e.getMessage();
            } finally {
                if (conn != null) conn.disconnect();
            }
            final String fLink = directLink;
            final String fErr = error;
            runOnUiThread(() -> {
                if (progress != null && progress.isShowing()) progress.dismiss();
                if (fLink != null && !fLink.isEmpty()) {
                    showResult(platform, url, fLink);
                } else {
                    Toast.makeText(this, "视频解析失败: " + (fErr != null ? fErr : ""), Toast.LENGTH_LONG).show();
                    showResult(platform, url, null);
                }
            });
        }).start();
    }

    private void doApiParse(String platform, String url) {
        progress = ProgressDialog.show(this, "解析中", "正在请求直链...", true, false);
        new Thread(() -> {
            String directLink = null;
            String fileName = null;
            String error = null;
            HttpURLConnection conn = null;
            try {
                String apiUrl = "https://lz0.qaiu.top/json/parser?url=" + URLEncoder.encode(url, "UTF-8");
                conn = (HttpURLConnection) new URL(apiUrl).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(20000);
                conn.setRequestMethod("GET");
                InputStream is = conn.getInputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                JSONObject jo = new JSONObject(sb.toString());
                // 返回: {"code":200,"success":true,"data":{"directLink":"...","shareKey":"..."}}
                if (jo.optInt("code", -1) == 200 && jo.optBoolean("success", false)) {
                    JSONObject data = jo.optJSONObject("data");
                    if (data != null) {
                        directLink = data.optString("directLink", "");
                        if (directLink.isEmpty()) directLink = data.optString("url", "");
                    }
                } else {
                    error = jo.optString("msg", jo.optString("message", "解析失败"));
                }
            } catch (Exception e) {
                error = e.getMessage();
            } finally {
                if (conn != null) conn.disconnect();
            }
            final String fResult = directLink;
            final String fError = error;
            runOnUiThread(() -> {
                if (progress != null && progress.isShowing()) progress.dismiss();
                if (fResult != null && !fResult.isEmpty()) {
                    showResult(platform, url, fResult);
                } else {
                    Toast.makeText(this, "API解析失败: " + (fError != null ? fError : "未知错误"), Toast.LENGTH_LONG).show();
                    showResult(platform, url, null);
                }
            });
        }).start();
    }

    private String extractUrl(String text) {
        String[] words = text.split("[\\s\\n]+");
        for (String w : words) {
            w = w.trim();
            if (w.startsWith("http://") || w.startsWith("https://") || w.startsWith("magnet:") || w.startsWith("thunder://")) return w;
        }
        if (text.startsWith("http") || text.startsWith("magnet:") || text.startsWith("thunder://")) return text;
        return null;
    }

    private String detectPlatform(String url) {
        String u = url.toLowerCase();
        if (u.contains("magnet:")) return "磁力链接";
        if (u.contains("thunder://")) return "迅雷链接";
        if (u.contains("douyin.com") || u.contains("iesdouyin.com")) return "抖音";
        if (u.contains("tiktok.com")) return "TikTok";
        if (u.contains("bilibili.com") || u.contains("b23.tv")) return "B站";
        if (u.contains("kuaishou.com") || u.contains("chenzhongtech.com")) return "快手";
        if (u.contains("xiaohongshu.com") || u.contains("xhslink.com")) return "小红书";
        if (u.contains("pan.baidu.com") || u.contains("yun.baidu.com")) return "百度网盘";
        if (u.contains("alipan.com") || u.contains("aliyundrive.com")) return "阿里云盘";
        if (u.contains("pan.quark.cn") || u.contains("quark.cn")) return "夸克网盘";
        if (u.contains("115.com")) return "115网盘";
        if (u.contains("lanzou")) return "蓝奏云";
        if (u.contains("xunlei.com") || u.contains("xlpan")) return "迅雷网盘";
        if (u.contains("weiyun.com")) return "微云";
        if (u.contains("123pan.com")) return "123云盘";
        return "未知链接";
    }

    private void showResult(String platform, String url, String directLink) {
        resultBox.removeAllViews();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(Tools.liquidGlassCard(this, 20));
        Tools.setElevation(card, 8);

        TextView p = new TextView(this);
        p.setText("识别：" + platform);
        p.setTextSize(15);
        p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        p.setTextColor(Tools.accentColor(this));
        card.addView(p);
        card.addView(spacer(8));

        TextView u = new TextView(this);
        u.setText(url);
        u.setTextSize(11);
        u.setTextColor(Tools.subTextColor(this));
        u.setMaxLines(3);
        card.addView(u);

        if (directLink != null && !directLink.isEmpty()) {
            card.addView(spacer(10));
            TextView dl = new TextView(this);
            dl.setText("✅ 直链解析成功");
            dl.setTextSize(13);
            dl.setTextColor(Color.rgb(0, 160, 80));
            dl.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            card.addView(dl);
            TextView dlUrl = new TextView(this);
            dlUrl.setText(directLink);
            dlUrl.setTextSize(10);
            dlUrl.setTextColor(Tools.subTextColor(this));
            dlUrl.setMaxLines(3);
            card.addView(dlUrl);
        }

        card.addView(spacer(12));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        Button openBtn = pillButton(directLink != null && !directLink.isEmpty() ? "下载" : "打开", true);
        openBtn.setOnClickListener(v -> {
            try {
                String target = (directLink != null && !directLink.isEmpty()) ? directLink : url;
                if (directLink != null && !directLink.isEmpty()) {
                    // 有直链：丢给内置下载器
                    Intent it = new Intent(this, DownloadActivity.class);
                    it.putExtra("url", target);
                    it.putExtra("name", "网盘文件_" + System.currentTimeMillis());
                    it.putExtra("auto_start", true);
                    startActivity(it);
                    Toast.makeText(this, "已加入下载", Toast.LENGTH_SHORT).show();
                } else {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(target)));
                }
            } catch (Exception e) {
                Toast.makeText(this, "无法打开", Toast.LENGTH_SHORT).show();
            }
        });
        row.addView(openBtn, new LinearLayout.LayoutParams(0, dp(42), 1f));

        Button copyBtn = pillButton("复制", false);
        copyBtn.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            String target = (directLink != null && !directLink.isEmpty()) ? directLink : url;
            cm.setPrimaryClip(ClipData.newPlainText("url", target));
            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(42), 1f);
        cp.setMarginStart(dp(8));
        row.addView(copyBtn, cp);

        Button favBtn = pillButton("收藏", false);
        favBtn.setOnClickListener(v -> {
            saveFav(url);
            refreshLists();
            Toast.makeText(this, "已收藏", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(0, dp(42), 1f);
        fp.setMarginStart(dp(8));
        row.addView(favBtn, fp);

        card.addView(row);
        resultBox.addView(card);
    }

    private void saveHistory(String url) {
        android.content.SharedPreferences sp = getSharedPreferences("netdisk_history", MODE_PRIVATE);
        TreeMap<String, String> map = new TreeMap<>();
        for (Map.Entry<String, ?> en : sp.getAll().entrySet()) map.put(en.getKey(), String.valueOf(en.getValue()));
        map.put(String.valueOf(System.currentTimeMillis()), url);
        while (map.size() > 50) map.remove(map.firstKey());
        android.content.SharedPreferences.Editor e = sp.edit().clear();
        for (Map.Entry<String, String> en : map.entrySet()) e.putString(en.getKey(), en.getValue());
        e.apply();
    }

    private void saveFav(String url) {
        getSharedPreferences("netdisk_fav", MODE_PRIVATE).edit()
                .putString(String.valueOf(System.currentTimeMillis()), url).apply();
    }

    private void refreshLists() {
        historyBox.removeAllViews();
        favBox.removeAllViews();
        renderList(historyBox, "netdisk_history", false);
        renderList(favBox, "netdisk_fav", true);
    }

    private void renderList(LinearLayout box, String spName, boolean isFav) {
        android.content.SharedPreferences sp = getSharedPreferences(spName, MODE_PRIVATE);
        TreeMap<String, String> map = new TreeMap<>();
        for (Map.Entry<String, ?> en : sp.getAll().entrySet()) map.put(en.getKey(), String.valueOf(en.getValue()));
        List<String> keys = new ArrayList<>(map.keySet());
        java.util.Collections.reverse(keys);
        if (keys.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(isFav ? "暂无收藏" : "暂无历史");
            empty.setTextSize(12);
            empty.setTextColor(Tools.subTextColor(this));
            empty.setPadding(0, dp(8), 0, dp(8));
            box.addView(empty);
            return;
        }
        for (String k : keys) {
            final String url = map.get(k);
            final String key = k;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Tools.cardColor(this));
            bg.setCornerRadius(dp(10));
            row.setBackground(bg);
            TextView t = new TextView(this);
            t.setText(detectPlatform(url) + "\n" + (url.length() > 50 ? url.substring(0, 50) + "…" : url));
            t.setTextSize(12);
            t.setTextColor(Tools.textColor(this));
            t.setMaxLines(2);
            row.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.setOnClickListener(v -> { input.setText(url); parse(); });
            row.setOnLongClickListener(v -> {
                getSharedPreferences(spName, MODE_PRIVATE).edit().remove(key).apply();
                refreshLists();
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, dp(4), 0, 0);
            row.setLayoutParams(lp);
            box.addView(row);
        }
    }

    private Button pillButton(String text, boolean filled) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTextColor(filled ? Color.WHITE : Tools.textColor(this));
        b.setBackground(Tools.pillButton(this, 42, filled));
        return b;
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private View spacer(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        return v;
    }
}
