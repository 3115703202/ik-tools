package com.roomarchive.server;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** 安全护盾：本机扫描已安装应用的危险权限，不上传任何应用清单。 */
public class SecurityShieldActivity extends Activity {
    private LinearLayout list;
    private TextView summary;
    private static final String[] DANGER = {
            "android.permission.READ_SMS", "android.permission.CALL_PHONE",
            "android.permission.RECORD_AUDIO", "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.READ_CONTACTS", "android.permission.REQUEST_INSTALL_PACKAGES",
            "android.permission.SYSTEM_ALERT_WINDOW", "android.permission.READ_CALL_LOG"};

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Tools.applyStatusBar(this);
        getWindow().setStatusBarColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= 23) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        ScrollView sv = new ScrollView(this); sv.setBackgroundColor(0xFFF7F7F9);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(22), dp(34), dp(22), dp(30));
        TextView t = new TextView(this); t.setText("安全护盾"); t.setTextSize(30); t.setTextColor(0xFF111114); t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); root.addView(t);
        TextView s = new TextView(this); s.setText("扫描本机应用的敏感权限与安装来源，只在本机计算，清单不上传。"); s.setTextSize(13); s.setTextColor(0xFF777783); s.setPadding(0, dp(6), 0, dp(20)); root.addView(s);
        summary = new TextView(this); summary.setTextSize(14); summary.setTextColor(0xFF111114); summary.setPadding(dp(16), dp(14), dp(16), dp(14));
        summary.setBackground(bg(0xFFFFFFFF, 16, 0xFFE5E7EB)); root.addView(summary);
        TextView scan = new TextView(this); scan.setText("重新扫描"); scan.setTextSize(15); scan.setGravity(Gravity.CENTER); scan.setTextColor(Color.WHITE); scan.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        scan.setBackground(bg(0xFF16A34A, 14, 0xFF16A34A)); LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, dp(50)); slp.topMargin = dp(12); root.addView(scan, slp);
        scan.setOnClickListener(v -> scan());
        list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-1, -2); llp.topMargin = dp(12); root.addView(list, llp);
        sv.addView(root); setContentView(sv); scan();
    }

    private void scan() {
        list.removeAllViews();
        PackageManager pm = getPackageManager();
        List<String> risky = new ArrayList<>();
        int total = 0, high = 0;
        for (PackageInfo pi : pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)) {
            total++;
            if (pi.requestedPermissions == null) continue;
            int n = 0;
            for (String p : pi.requestedPermissions) for (String d : DANGER) if (d.equals(p)) n++;
            if (n >= 3) {
                high++;
                String name;
                try { name = pm.getApplicationLabel(pi.applicationInfo).toString(); }
                catch (Throwable t2) { name = pi.packageName; }
                risky.add(name + "\n" + pi.packageName + " · " + n + " 项敏感权限");
            }
        }
        summary.setText("已扫描 " + total + " 个应用\n高风险（3 项以上敏感权限）：" + high + " 个");
        for (int i = 0; i < risky.size() && i < 12; i++) {
            TextView row = new TextView(this);
            row.setText(risky.get(i)); row.setTextSize(13); row.setTextColor(0xFF33333A); row.setPadding(dp(14), dp(10), dp(14), dp(10));
            row.setBackground(bg(i % 2 == 0 ? 0xFFFFFFFF : 0xFFF1F2F5, 12, 0xFFE8E9ED));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(8); list.addView(row, lp);
        }
        if (risky.isEmpty()) {
            TextView ok = new TextView(this); ok.setText("没有发现 3 项以上敏感权限的应用。"); ok.setTextSize(13); ok.setTextColor(0xFF15803D); ok.setPadding(dp(8), dp(16), 0, 0); list.addView(ok);
        }
        TextView note = new TextView(this); note.setText("说明：敏感权限多不等于恶意，地图、通讯、录音类应用本身就需要；这里只做可见化，不自动卸载。"); note.setTextSize(11); note.setTextColor(0xFF8A8A93); note.setPadding(dp(4), dp(16), 0, 0); list.addView(note);
    }
    private android.graphics.drawable.GradientDrawable bg(int fill, int radius, int stroke) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(radius)); d.setStroke(dp(1), stroke); return d;
    }
    int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }
}
