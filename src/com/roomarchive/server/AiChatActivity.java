package com.roomarchive.server;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Random;

/** 内置小AI：离线关键词问答 + 联网大模型（OpenAI 兼容接口，填 Key 即全能） */
public class AiChatActivity extends BaseActivity {

    private LinearLayout msgList;
    private ScrollView scroll;
    private EditText inputBox;
    private TextView modeView;
    private final Random random = new Random();

    private static final String PREF = "ai_config";
    private static final String KEY_URL = "api_url";
    private static final String KEY_KEY = "api_key";
    private static final String KEY_MODEL = "api_model";

    // ===== 离线知识库（无网络/无 Key 时兜底）=====
    private static final String[][] KB = {
            {"你好|hi|hello|嗨|哈喽", "你好呀！我是时空净化工具箱的内置小AI～（提示：到右上角设置里填一个 AI 接口 Key，我就能联网变全能，什么都能答）"},
            {"你是谁|你叫什么|介绍你", "我是内置小AI，住在时空净化工具箱里。默认离线运行；配置 AI 接口后可以联网问答，知识全面升级。"},
            {"服务器|房型库|网页|共享", "点主界面的【服务器】→ 启动服务器 → 把显示的 192.168.x.x 地址发给同一 WiFi/热点的朋友就能访问房型库网页。"},
            {"公网|外网|frp|樱花|穿透", "外地朋友访问：用【服务器】里的 frp 穿透（国内节点）。注册樱花Frp(natfrp.com)，创建 HTTP 隧道本地端口 8080，复制配置文件到 App 一键识别。"},
            {"zip|解压|压缩|加密|加锁", "点【ZIP 工具】：解压选 zip 文件；压缩可多选文件；加密用 AES-256，产物是 .room 文件，密码要记牢。"},
            {"图片|照片|滤镜|裁剪|旋转", "点【图片工具】选图，可旋转、灰度、调亮度、缩小、压缩保存，全程离线。"},
            {"视频|封面|音频", "点【视频工具】选视频，可提取封面（JPEG）、提取音频（m4a）、查看时长分辨率。"},
            {"文本|记事|写|保存|txt", "点【文本编辑器】输入内容，点保存选位置即可存成 .txt 文件。"},
            {"怎么用|不会用|教程|help", "主界面有 7 个工具：服务器/ZIP/图片/视频/小AI/文本/关于，每个点进去都有说明。"},
            {"网络|联网|api|key|全能|智能", "在聊天页右上角【AI设置】填入接口地址 + Key + 模型名，就能联网问答（支持火山方舟/DeepSeek/智谱等 OpenAI 兼容接口）。"},
            {"谢谢|感谢|辛苦", "不客气～有需要随时叫我！"},
            {"再见|拜拜|88", "再见！记得常来找我玩～"},
            {"能做什么|有什么功能|功能", "工具箱有：服务器分享、ZIP 解压压缩加密、图片编辑、视频提取、文本编辑、和我这个小AI。配置 AI 接口后我更全能。"},
            {"笑话|讲个笑话", "程序员最讨厌的两件事：1. 写注释；2. 别人不写注释。"},
            {"谁做的|谁开发的", "我是时空净化工具箱自带的助手，开发者把工具箱所有功能都做成了本地离线运行。"}
    };

    private static final String[] FALLBACK = {
            "这个问题我离线答不上来～ 配置一个 AI 接口 Key 后我就能联网回答任何问题（点右上角【AI设置】）。",
            "唔，离线小AI知识有限。想要全能问答，去【AI设置】填个接口 Key 就行～",
            "这个超出我的离线知识了。联网模式可以答任何问题：点【AI设置】→ 填 Key → 开启联网。"
    };

    // ===== 常用接口预设 =====
    private static final String[][] PRESETS = {
            {"火山方舟(豆包)", "https://ark.cn-beijing.volces.com/api/v3/chat/completions", "doubao-1-5-pro-32k-250115"},
            {"DeepSeek", "https://api.deepseek.com/chat/completions", "deepseek-chat"},
            {"智谱GLM", "https://open.bigmodel.cn/api/paas/v4/chat/completions", "glm-4-flash"}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        appendBubble("你好！我是内置小AI。默认离线模式，配置 AI 接口后即可联网全能问答（点右上角【AI设置】）。", true);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Tools.bgWithGlow(this));
        root.setPadding(dp(16), dp(20), dp(16), dp(18));

        // 顶栏
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("内置小AI");
        title.setTextSize(20);
        title.setTextColor(Tools.textColor(this));
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button settings = new Button(this);
        settings.setText("AI 设置");
        settings.setTextSize(13);
        settings.setTextColor(Tools.accentColor(this));
        settings.setBackgroundColor(0x00000000);
        settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSettings();
            }
        });
        top.addView(settings);
        root.addView(top);

        modeView = new TextView(this);
        modeView.setTextSize(12);
        modeView.setTextColor(Tools.subTextColor(this));
        refreshMode();
        root.addView(modeView);

        scroll = new ScrollView(this);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        scroll.setLayoutParams(slp);
        msgList = new LinearLayout(this);
        msgList.setOrientation(LinearLayout.VERTICAL);
        msgList.setPadding(dp(4), dp(10), dp(4), dp(10));
        scroll.addView(msgList);
        root.addView(scroll);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputBox = new EditText(this);
        inputBox.setHint("说点什么…");
        inputBox.setTextSize(15);
        inputBox.setTextColor(Tools.textColor(this));
        inputBox.setSingleLine(true);
        android.graphics.drawable.GradientDrawable inputBg = new android.graphics.drawable.GradientDrawable();
        inputBg.setColor(Tools.cardColor(this));
        inputBg.setCornerRadius(Tools.dp(this, 12));
        inputBg.setStroke(Tools.dp(this, 1),
                Tools.isDark(this) ? 0xFF2A3040 : 0xFFE4E8EE);
        inputBox.setBackground(inputBg);
        inputRow.addView(inputBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button send = new Button(this);
        send.setText("发送");
        send.setTextColor(Tools.accentColor(this));
        send.setBackgroundColor(0x00000000);
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage();
            }
        });
        inputRow.addView(send, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(inputRow);

        setContentView(root);
    }

    private void refreshMode() {
        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        String key = sp.getString(KEY_KEY, "");
        if (key != null && !key.isEmpty()) {
            modeView.setText("● 联网模式已开启（全能问答）");
            modeView.setTextColor(Tools.isDark(this) ? 0xFF22C55E : 0xFF16A34A);
        } else {
            modeView.setText("● 离线模式（知识有限）· 点右上角【AI设置】填 Key 开启全能");
            modeView.setTextColor(Tools.isDark(this) ? 0xFFFBBF24 : 0xFFD97706);
        }
    }

    // ================= 设置 =================

    private void showSettings() {
        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        final String savedUrl = sp.getString(KEY_URL, "");
        final String savedKey = sp.getString(KEY_KEY, "");
        final String savedModel = sp.getString(KEY_MODEL, "");

        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("联网 AI 设置（OpenAI 兼容接口）");

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(12), dp(20), dp(4));

        TextView hint = new TextView(this);
        hint.setText("点预设自动填地址/模型，再粘贴你的 Key。Key 只保存在本机。");
        hint.setTextSize(12);
        hint.setTextColor(Tools.subTextColor(this));
        box.addView(hint);

        LinearLayout presetRow = new LinearLayout(this);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        for (final String[] preset : PRESETS) {
            Button p = new Button(this);
            p.setText(preset[0]);
            p.setTextSize(11);
            p.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    urlBox.setText(preset[1]);
                    modelBox.setText(preset[2]);
                }
            });
            presetRow.addView(p, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        box.addView(presetRow);
        box.addView(spacerH(10));

        urlBox = new EditText(this);
        urlBox.setHint("接口地址（含 /chat/completions）");
        urlBox.setText(savedUrl);
        urlBox.setTextSize(13);
        box.addView(urlBox);

        keyBox = new EditText(this);
        keyBox.setHint("API Key（必填才可联网）");
        keyBox.setText(savedKey);
        keyBox.setTextSize(13);
        box.addView(keyBox);

        modelBox = new EditText(this);
        modelBox.setHint("模型名（如 deepseek-chat）");
        modelBox.setText(savedModel);
        modelBox.setTextSize(13);
        box.addView(modelBox);

        b.setView(box);
        b.setPositiveButton("保存", new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface d, int which) {
                SharedPreferences.Editor e = getSharedPreferences(PREF, MODE_PRIVATE).edit();
                e.putString(KEY_URL, urlBox.getText().toString().trim());
                e.putString(KEY_KEY, keyBox.getText().toString().trim());
                e.putString(KEY_MODEL, modelBox.getText().toString().trim());
                e.apply();
                refreshMode();
                Toast.makeText(AiChatActivity.this,
                        keyBox.getText().toString().trim().isEmpty() ? "已保存（离线模式）" : "已保存（联网模式开启）",
                        Toast.LENGTH_SHORT).show();
            }
        });
        b.setNegativeButton("清空Key", new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface d, int which) {
                getSharedPreferences(PREF, MODE_PRIVATE).edit().clear().apply();
                refreshMode();
                Toast.makeText(AiChatActivity.this, "已清空，回到离线模式", Toast.LENGTH_SHORT).show();
            }
        });
        b.setNeutralButton("取消", null);
        b.show();
    }

    private EditText urlBox, keyBox, modelBox;

    private View spacerH(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        return v;
    }

    // ================= 发送 =================

    private void sendMessage() {
        final String text = inputBox.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;
        inputBox.setText("");
        appendBubble(text, false);

        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        String key = sp.getString(KEY_KEY, "");
        String url = sp.getString(KEY_URL, "");
        String model = sp.getString(KEY_MODEL, "");

        if (key != null && !key.isEmpty() && url != null && !url.isEmpty()) {
            appendThinking();
            askOnline(text, key, url, model);
        } else {
            appendBubble(answerOffline(text), true);
        }
    }

    private String answerOffline(String q) {
        String lower = q.toLowerCase();
        for (String[] item : KB) {
            String[] keys = item[0].split("\\|");
            for (String k : keys) {
                if (lower.contains(k)) return item[1];
            }
        }
        return FALLBACK[random.nextInt(FALLBACK.length)];
    }

    // ================= 联网调用（OpenAI 兼容 /chat/completions）=================

    private void appendThinking() {
        appendBubble("（正在联网思考…）", true);
        thinkingIndex = msgList.getChildCount() - 1;
    }

    private int thinkingIndex = -1;

    private void askOnline(final String question, final String key, final String url, final String model) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String reply = null;
                String err = null;
                try {
                    JSONObject body = new JSONObject();
                    body.put("model", model == null || model.isEmpty() ? "deepseek-chat" : model);
                    JSONArray messages = new JSONArray();
                    JSONObject userMsg = new JSONObject();
                    userMsg.put("role", "user");
                    userMsg.put("content", question);
                    messages.put(userMsg);
                    body.put("messages", messages);
                    body.put("stream", false);
                    body.put("max_tokens", 2000);

                    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("Authorization", "Bearer " + key);
                    conn.setConnectTimeout(30000);
                    conn.setReadTimeout(120000);
                    conn.setDoOutput(true);
                    OutputStream os = conn.getOutputStream();
                    os.write(body.toString().getBytes("UTF-8"));
                    os.flush();
                    os.close();

                    int code = conn.getResponseCode();
                    InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
                    BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    if (code >= 200 && code < 300) {
                        JSONObject obj = new JSONObject(sb.toString());
                        JSONArray choices = obj.optJSONArray("choices");
                        if (choices != null && choices.length() > 0) {
                            JSONObject msg = choices.optJSONObject(0);
                            if (msg != null) {
                                JSONObject m = msg.optJSONObject("message");
                                if (m != null) reply = m.optString("content");
                                if (reply == null || reply.isEmpty()) {
                                    reply = msg.optString("text");
                                }
                            }
                        }
                        if (reply == null || reply.isEmpty()) {
                            err = "接口返回为空（请检查模型名是否正确）";
                        }
                    } else {
                        String detail = sb.toString();
                        if (detail.length() > 200) detail = detail.substring(0, 200);
                        err = "HTTP " + code + "：" + detail;
                    }
                } catch (final Exception e) {
                    err = "请求失败：" + e.getMessage();
                }

                final String finalReply = reply;
                final String finalErr = err;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (thinkingIndex >= 0 && thinkingIndex < msgList.getChildCount()) {
                            msgList.removeViewAt(thinkingIndex);
                        }
                        thinkingIndex = -1;
                        if (finalReply != null) {
                            appendBubble(finalReply, true);
                        } else {
                            appendBubble("联网失败：" + finalErr + "\n\n可检查：① Key 是否正确 ② 接口地址/模型名 ③ 网络连接。", true);
                        }
                    }
                });
            }
        }).start();
    }

    // ================= UI 气泡 =================

    private void appendBubble(String text, boolean fromAi) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView label = new TextView(this);
        label.setText(fromAi ? "小AI" : "我");
        label.setTextSize(11);
        label.setTextColor(Tools.subTextColor(this));
        row.addView(label);

        TextView bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextSize(14);
        bubble.setTextColor(Tools.textColor(this));
        bubble.setPadding(dp(12), dp(9), dp(12), dp(9));
        bubble.setLineSpacing(dp(2), 1.0f);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        if (fromAi) {
            bg.setColor(Tools.isDark(this) ? 0xFF223048 : 0xFFEAF0FF);
            row.setGravity(Gravity.START);
        } else {
            bg.setColor(Tools.isDark(this) ? 0xFF1E3A2A : 0xFFE3F8E8);
            row.setGravity(Gravity.END);
            label.setGravity(Gravity.END);
        }
        bubble.setBackground(bg);
        row.addView(bubble);

        msgList.addView(row);
        scroll.post(new Runnable() {
            @Override
            public void run() {
                scroll.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
