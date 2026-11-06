package com.roomarchive.server;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/** 翻译：Google 免费接口 + MyMemory 兜底 */
public class TranslateActivity extends Activity {

    private EditText input;
    private TextView output;
    private final Handler h = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        sv.setBackground(Tools.bgWithGlow(this));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(20));
        sv.addView(root);

        root.addView(Tools.pageTitle(this, "翻译"));

        input = new EditText(this);
        input.setHint("输入要翻译的文本…");
        input.setMinLines(3);
        input.setTextSize(15);
        input.setTextColor(Tools.textColor(this));
        input.setHintTextColor(Tools.subTextColor(this));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Tools.cardColor(this));
        bg.setCornerRadius(dp(12));
        input.setBackground(bg);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.addView(input);
        root.addView(spacer(12));

        android.widget.Button btn = new android.widget.Button(this);
        btn.setText("翻译 中→英");
        btn.setAllCaps(false);
        btn.setTextColor(Color.WHITE);
        btn.setBackground(Tools.pillButton(this, 44, true));
        btn.setOnClickListener(v -> doTranslate("zh-CN", "en"));
        root.addView(btn);

        android.widget.Button btn2 = new android.widget.Button(this);
        btn2.setText("翻译 英→中");
        btn2.setAllCaps(false);
        btn2.setTextColor(Color.WHITE);
        btn2.setBackground(Tools.pillButton(this, 44, true));
        btn2.setOnClickListener(v -> doTranslate("en", "zh-CN"));
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        lp2.setMargins(0, dp(8), 0, 0);
        btn2.setLayoutParams(lp2);
        root.addView(btn2);

        root.addView(spacer(16));
        output = new TextView(this);
        output.setText("结果");
        output.setTextSize(15);
        output.setTextColor(Tools.textColor(this));
        output.setPadding(dp(12), dp(12), dp(12), dp(12));
        android.graphics.drawable.GradientDrawable obg = new android.graphics.drawable.GradientDrawable();
        obg.setColor(Tools.cardColor(this));
        obg.setCornerRadius(dp(12));
        output.setBackground(obg);
        root.addView(output);

        setContentView(sv);
    }

    private void doTranslate(String from, String to) {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) { Toast.makeText(this, "输入文本", Toast.LENGTH_SHORT).show(); return; }
        output.setText("翻译中…");
        new Thread(() -> {
            String result = null;
            String lastErr = null;
            for (int i = 0; i < 2 && result == null; i++) {
                try {
                    if (i == 0) {
                        String api = "https://translate.googleapis.com/translate_a/single?client=gtx&sl="
                                + from + "&tl=" + to + "&dt=t&q=" + URLEncoder.encode(text, "UTF-8");
                        String body = get(api);
                        result = parseGoogleTranslate(body);
                    } else {
                        String api = "https://api.mymemory.translated.net/get?q="
                                + URLEncoder.encode(text, "UTF-8")
                                + "&langpair=" + from + "|" + to;
                        String body = get(api);
                        org.json.JSONObject jo = new org.json.JSONObject(body);
                        String t = jo.getJSONObject("responseData").optString("translatedText", "");
                        if (t.length() > 0 && !t.contains("QUERY LENGTH LIMIT")) result = t;
                    }
                } catch (Throwable e) {
                    lastErr = e.getClass().getSimpleName();
                }
            }
            if (result == null) {
                result = "翻译服务暂不可用，请检查网络后重试";
            }
            final String r = result;
            h.post(() -> output.setText(r));
        }).start();
    }

    private String get(String api) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(api).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(12000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0");
        BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }

    private String parseGoogleTranslate(String json) {
        try {
            // 返回: [[["译文","原文",...],...],null,"zh-CN",...]
            // 提取所有段的译文拼接
            StringBuilder sb = new StringBuilder();
            int idx = 0;
            while (true) {
                int p1 = json.indexOf("[[[\"", idx);
                if (p1 < 0) p1 = json.indexOf("[[\"", idx);
                if (p1 < 0) break;
                int start = p1 + 3;
                // 跳过开始引号
                if (start < json.length() && json.charAt(start) == '"') start++;
                int end = json.indexOf("\"", start);
                if (end < 0) break;
                sb.append(json, start, end);
                idx = end + 1;
                // 每段后面应该是 ,"原文", 找下一段
                int next = json.indexOf("[[[\"", idx);
                if (next < 0) break;
            }
            if (sb.length() > 0) return sb.toString();
        } catch (Exception ignored) {}
        return json.length() > 200 ? json.substring(0, 200) : json;
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private View spacer(int h) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h))); return v; }
}
