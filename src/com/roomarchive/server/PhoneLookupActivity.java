package com.roomarchive.server;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.*;

public class PhoneLookupActivity extends Activity {

    private EditText input;
    private LinearLayout resultBox;
    private Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        Tools.applyStatusBar(this);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Tools.bgWithGlow(this));
        root.setPadding(dp(16), dp(20), dp(16), dp(28));
        root.addView(Tools.pageTitle(this, "手机归属地"));

        TextView sub = new TextView(this);
        sub.setText("离线号段库 · 无需联网，输入即可查");
        sub.setTextSize(13);
        sub.setTextColor(Tools.subTextColor(this));
        sub.setPadding(0, dp(4), 0, dp(14));
        root.addView(sub);

        // 输入卡片
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(Tools.liquidGlassCard(this, 20));

        input = new EditText(this);
        input.setHint("输入手机号，如 13800138000");
        input.setTextSize(17);
        input.setInputType(InputType.TYPE_CLASS_PHONE);
        input.setSingleLine(true);
        input.setTextColor(Tools.textColor(this));
        input.setHintTextColor(Tools.subTextColor(this));
        input.setBackground(null);
        input.setPadding(0, dp(6), 0, dp(6));
        card.addView(input);

        Button btn = new Button(this);
        btn.setText("查询");
        btn.setTextSize(16);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setBackground(Tools.pillButton(this, 46, true));
        btn.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        lp.topMargin = dp(12);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(v -> lookup());
        Tools.addPressAnimation(btn);
        card.addView(btn);

        root.addView(card);

        resultBox = new LinearLayout(this);
        resultBox.setOrientation(LinearLayout.VERTICAL);
        resultBox.setPadding(0, dp(16), 0, 0);
        root.addView(resultBox);

        sv.addView(root);
        setContentView(sv);

        // 预热号段库（后台线程，避免首次点击卡顿）
        new Thread(() -> PhoneLocator.ensureLoaded(this)).start();
    }

    private void lookup() {
        String raw = input.getText().toString().trim();
        String phone = raw.replaceAll("[^0-9]", "");
        if (phone.length() == 13 && phone.startsWith("86")) phone = phone.substring(2);
        if (phone.length() == 0) {
            tip("请输入手机号");
            return;
        }
        if (phone.length() != 11) {
            tip("手机号应为 11 位，当前 " + phone.length() + " 位");
            return;
        }
        if (!phone.startsWith("1")) {
            tip("手机号应以 1 开头，请检查是否输错");
            return;
        }

        resultBox.removeAllViews();
        final String p = phone;
        showLoading();

        new Thread(() -> {
            PhoneLocator.Result r;
            try {
                r = PhoneLocator.find(this, p);
            } catch (Throwable t) {
                r = null;
            }
            final PhoneLocator.Result res = r;
            final boolean loaded = PhoneLocator.recordCount() > 0;
            ui.post(() -> {
                resultBox.removeAllViews();
                if (res != null) showResult(res);
                else showNotFound(p, loaded);
            });
        }).start();
    }

    private void showLoading() {
        resultBox.removeAllViews();
        TextView t = new TextView(this);
        t.setText("查询中…");
        t.setTextSize(14);
        t.setTextColor(Tools.subTextColor(this));
        resultBox.addView(t);
    }

    private void showNotFound(String phone, boolean dbLoaded) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(Tools.liquidGlassCard(this, 18));

        TextView title = new TextView(this);
        title.setText(dbLoaded ? "未查到该号段" : "号段库读取失败");
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Tools.textColor(this));
        card.addView(title);

        TextView d = new TextView(this);
        d.setTextSize(13);
        d.setTextColor(Tools.subTextColor(this));
        d.setPadding(0, dp(8), 0, 0);
        d.setText(dbLoaded
                ? "号码 " + phone + " 的号段可能较新，内置库未收录。可换其它号码再试。"
                : "assets/phone.dat 未能读取，请重新安装完整版。");
        card.addView(d);

        resultBox.addView(card);
    }

    private void showResult(PhoneLocator.Result r) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(18), dp(20), dp(18));
        card.setBackground(Tools.liquidGlassCard(this, 20));

        TextView loc = new TextView(this);
        loc.setText(r.province + " · " + r.city);
        loc.setTextSize(22);
        loc.setTypeface(Typeface.DEFAULT_BOLD);
        loc.setTextColor(Tools.textColor(this));
        card.addView(loc);

        TextView carrier = new TextView(this);
        carrier.setText(r.carrier);
        carrier.setTextSize(14);
        carrier.setTextColor(Tools.accentColor(this));
        carrier.setPadding(0, dp(6), 0, dp(14));
        card.addView(carrier);

        addRow(card, "号码", r.phone);
        addRow(card, "号段", r.prefix + "xxxx");
        addRow(card, "省份", r.province);
        addRow(card, "城市", r.city);
        addRow(card, "运营商", r.carrier);
        addRow(card, "区号", r.areaCode);
        addRow(card, "邮编", r.zipCode);

        resultBox.addView(card);
    }

    private void addRow(LinearLayout parent, String k, String v) {
        if (v == null || v.length() == 0) return;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(5), 0, dp(5));

        TextView key = new TextView(this);
        key.setText(k);
        key.setTextSize(13);
        key.setTextColor(Tools.subTextColor(this));
        key.setWidth(dp(64));
        row.addView(key);

        TextView val = new TextView(this);
        val.setText(v);
        val.setTextSize(14);
        val.setTextColor(Tools.textColor(this));
        row.addView(val);

        parent.addView(row);
    }

    private void tip(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
