package com.roomarchive.server;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** 广告拦截 · 内置浏览器：按域名直接拦掉广告请求，不建 VPN、不改系统。 */
public class AdBlockActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Tools.applyStatusBar(this);
        getWindow().setStatusBarColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= 23) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        ScrollView sv = new ScrollView(this); sv.setBackgroundColor(0xFFF7F7F9);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(30), dp(20), dp(30)); sv.addView(root);
        TextView t = new TextView(this); t.setText("广告拦截"); t.setTextSize(30); t.setTextColor(0xFF111114);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); root.addView(t);
        TextView s = new TextView(this); s.setText("内置浏览器内按域名拦截广告请求，秒开页面，不误伤正文。"); s.setTextSize(13); s.setTextColor(0xFF777783);
        s.setPadding(0, dp(6), 0, dp(22)); root.addView(s);

        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(bg(0xFFFFFFFF, 16, 0xFFE7E7EC));
        Switch sw = new Switch(this); sw.setText("启用广告拦截"); sw.setTextSize(16); sw.setTextColor(0xFF17171B);
        sw.setChecked(AdBlock.on(this));
        sw.setOnCheckedChangeListener((v, ok) -> getSharedPreferences("ik_adblock", MODE_PRIVATE).edit().putBoolean("on", ok).apply());
        card.addView(sw);
        TextView n = new TextView(this); n.setText("已收录 " + AdBlock.HOSTS.length + " 个广告域名 · 规则随版本更新"); n.setTextSize(12); n.setTextColor(0xFF8A8A93);
        n.setPadding(0, dp(8), 0, 0); card.addView(n);
        root.addView(card);
        TextView tip = new TextView(this); tip.setText("说明：这是 IK 内置浏览器的请求级拦截，不是系统级 VPN；其他 App 内的广告不在范围内。"); tip.setTextSize(12); tip.setTextColor(0xFF9A9AA2);
        tip.setPadding(0, dp(18), 0, 0); root.addView(tip);
        setContentView(sv);
    }
    GradientDrawable bg(int fill, int r, int stroke) { GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(r)); d.setStroke(dp(1), stroke); return d; }
    int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }
}
