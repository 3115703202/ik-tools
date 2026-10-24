/** 网页转应用：我的应用列表 + 创建应用（网页 / HTML 两种） */
package com.roomarchive.server;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class WebAppsActivity extends Activity {

    private static final String SP = "web_apps";
    private LinearLayout listBox;
    private TextView emptyView;
    private List<JSONObject> apps = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Tools.applyStatusBar(this);
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackground(Tools.bgWithGlow(this));
        outer.setPadding(dp(16), dp(20), dp(16), dp(8));

        // 顶栏：标题 + 说明
        TextView title = new TextView(this);
        title.setText("网页转应用");
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Tools.textColor(this));
        outer.addView(title);

        TextView sub = new TextView(this);
        sub.setText("把任意网页或 HTML 封装成独立应用，一键打开");
        sub.setTextSize(12);
        sub.setTextColor(Tools.subTextColor(this));
        sub.setPadding(0, dp(4), 0, dp(10));
        outer.addView(sub);

        // 列表区
        ScrollView sv = new ScrollView(this);
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        sv.addView(listBox, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        outer.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 空态
        emptyView = new TextView(this);
        emptyView.setText("暂无应用\n点击下方按钮创建您的第一个应用");
        emptyView.setTextSize(15);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setTextColor(Tools.subTextColor(this));
        emptyView.setPadding(0, dp(90), 0, 0);
        outer.addView(emptyView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(210)));

        // 底部创建按钮
        TextView create = new TextView(this);
        create.setText("创建应用");
        create.setTextSize(16);
        create.setTypeface(Typeface.DEFAULT_BOLD);
        create.setTextColor(Color.WHITE);
        create.setGravity(Gravity.CENTER);
        Icons.attachToText(create, "plus", Color.WHITE, 18, 6);
        create.setBackground(Tools.pillButton(this, 50, true));
        create.setPadding(0, dp(14), 0, dp(14));
        create.setClickable(true);
        create.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCreateDialog();
            }
        });
        outer.addView(create, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams mlp = (LinearLayout.LayoutParams) create.getLayoutParams();
        mlp.topMargin = dp(12);
        mlp.bottomMargin = dp(12);

        setContentView(outer);
        reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    // ---------- 数据 ----------

    private void reload() {
        apps.clear();
        try {
            String raw = getSharedPreferences(SP, MODE_PRIVATE).getString("list", "[]");
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) apps.add(arr.getJSONObject(i));
        } catch (Exception ignored) {
        }
        listBox.removeAllViews();
        for (final JSONObject o : apps) {
            listBox.addView(appCard(o));
        }
        boolean empty = apps.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        svVisible(!empty);
    }

    private void svVisible(boolean v) {
        try {
            ((ScrollView) listBox.getParent()).setVisibility(v ? View.VISIBLE : View.GONE);
        } catch (Exception ignored) {
        }
    }

    private void saveApp(String name, String type, String content) {
        try {
            JSONArray arr = new JSONArray(
                    getSharedPreferences(SP, MODE_PRIVATE).getString("list", "[]"));
            JSONObject o = new JSONObject();
            o.put("name", name);
            o.put("type", type);            // url / html
            o.put("content", content);
            o.put("time", System.currentTimeMillis());
            arr.put(o);
            getSharedPreferences(SP, MODE_PRIVATE).edit().putString("list", arr.toString()).apply();
        } catch (Exception ignored) {
        }
        reload();
    }

    private void deleteApp(int index) {
        try {
            JSONArray arr = new JSONArray(
                    getSharedPreferences(SP, MODE_PRIVATE).getString("list", "[]"));
            if (index >= 0 && index < arr.length()) {
                JSONArray n = new JSONArray();
                for (int i = 0; i < arr.length(); i++) {
                    if (i != index) n.put(arr.getJSONObject(i));
                }
                getSharedPreferences(SP, MODE_PRIVATE).edit().putString("list", n.toString()).apply();
            }
        } catch (Exception ignored) {
        }
        reload();
    }

    // ---------- UI ----------

    private View appCard(final JSONObject o) {
        boolean dark = Tools.isDark(this);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Tools.glassCard(this, 18));
        Tools.setElevation(card, 6);
        card.setClickable(true);

        // 类型色块
        boolean isUrl = "url".equals(o.optString("type"));
        TextView tag = new TextView(this);
        tag.setText(isUrl ? "网" : "码");
        tag.setTextSize(13);
        tag.setTypeface(Typeface.DEFAULT_BOLD);
        tag.setTextColor(Color.WHITE);
        tag.setGravity(Gravity.CENTER);
        GradientDrawable tg = new GradientDrawable();
        tg.setColor(isUrl ? Color.rgb(37, 99, 235) : Color.rgb(16, 149, 74));
        tg.setCornerRadius(dp(9));
        tag.setPadding(0, dp(7), 0, dp(7));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(dp(38), dp(38));
        tlp.setMarginEnd(dp(12));
        card.addView(tag, tlp);

        // 名称 + 副信息
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this);
        name.setText(o.optString("name", "未命名"));
        name.setTextSize(15);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(Tools.textColor(this));
        info.addView(name);
        TextView sub = new TextView(this);
        String kind = isUrl ? "网页" : "HTML 代码";
        long t = o.optLong("time", 0);
        String ts = t > 0 ? java.text.SimpleDateFormat.getDateTimeInstance().format(new java.util.Date(t)) : "";
        sub.setText(kind + (ts.isEmpty() ? "" : " · " + ts));
        sub.setTextSize(11);
        sub.setTextColor(Tools.subTextColor(this));
        sub.setPadding(0, dp(3), 0, 0);
        info.addView(sub);
        card.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // 删除
        TextView del = new TextView(this);
        del.setText("删除");
        del.setTextSize(12);
        del.setTextColor(Color.rgb(220, 38, 38));
        del.setPadding(dp(12), dp(8), dp(12), dp(8));
        del.setClickable(true);
        final int idx = apps.indexOf(o);
        del.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(WebAppsActivity.this)
                        .setTitle("删除应用")
                        .setMessage("确定删除「" + o.optString("name", "") + "」？")
                        .setPositiveButton("删除", new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface d, int w) {
                                deleteApp(idx);
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        });
        card.addView(del);

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent it = new Intent(WebAppsActivity.this, WebAppViewerActivity.class);
                it.putExtra("name", o.optString("name", ""));
                it.putExtra("type", o.optString("type", "url"));
                it.putExtra("content", o.optString("content", ""));
                startActivity(it);
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
        lp.setMargins(0, dp(0), 0, dp(10));
        card.setLayoutParams(lp);
        return card;
    }

    /** 创建弹窗：网页 / HTML 两种入口 */
    private void showCreateDialog() {
        final String[] types = {"网页", "HTML"};
        new AlertDialog.Builder(this)
                .setTitle("创建应用")
                .setItems(types, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        if (which == 0) promptUrl();
                        else promptHtml();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void promptUrl() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(8), dp(8), 0);
        EditText name = new EditText(this);
        name.setHint("应用名称（如：我的博客）");
        name.setTextSize(14);
        EditText url = new EditText(this);
        url.setHint("网页地址（如：https://example.com）");
        url.setTextSize(14);
        url.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);
        box.addView(name);
        box.addView(url, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams ulp = (LinearLayout.LayoutParams) url.getLayoutParams();
        ulp.topMargin = dp(8);

        new AlertDialog.Builder(this)
                .setTitle("网页应用")
                .setView(box)
                .setPositiveButton("创建", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        String n = name.getText().toString().trim();
                        String u = url.getText().toString().trim();
                        if (n.isEmpty()) n = "网页应用";
                        if (u.isEmpty()) return;
                        if (!u.startsWith("http://") && !u.startsWith("https://")) {
                            u = "https://" + u;
                        }
                        saveApp(n, "url", u);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void promptHtml() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(8), dp(8), 0);
        EditText name = new EditText(this);
        name.setHint("应用名称（如：我的主页）");
        name.setTextSize(14);
        EditText html = new EditText(this);
        html.setHint("粘贴 HTML 代码（支持完整页面）");
        html.setTextSize(13);
        html.setGravity(Gravity.TOP);
        html.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        html.setMinLines(6);
        box.addView(name);
        box.addView(html, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(220)));
        LinearLayout.LayoutParams hlp = (LinearLayout.LayoutParams) html.getLayoutParams();
        hlp.topMargin = dp(8);

        new AlertDialog.Builder(this)
                .setTitle("HTML 应用")
                .setView(box)
                .setPositiveButton("创建", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        String n = name.getText().toString().trim();
                        String h = html.getText().toString().trim();
                        if (n.isEmpty()) n = "HTML应用";
                        if (h.isEmpty()) return;
                        saveApp(n, "html", h);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
