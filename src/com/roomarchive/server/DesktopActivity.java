package com.roomarchive.server;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 手机变电脑：横屏桌面矩阵，键鼠/触控都能点，工具窗口直接进入。 */
public class DesktopActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        Tools.applyStatusBar(this);
        getWindow().setStatusBarColor(0xFF0A1018);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0A1018); root.setPadding(dp(22), dp(16), dp(22), dp(16));
        LinearLayout bar = new LinearLayout(this); bar.setOrientation(LinearLayout.HORIZONTAL); bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = new TextView(this); logo.setText("IK 桌面"); logo.setTextSize(18); logo.setTextColor(Color.WHITE); logo.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); bar.addView(logo);
        TextView hint = new TextView(this); hint.setText("横屏 · 键鼠/触控 · 打开即用"); hint.setTextSize(11); hint.setTextColor(0xFF7E93A6);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2); hlp.leftMargin = dp(14); bar.addView(hint, hlp);
        TextView close = new TextView(this); close.setText("退出"); close.setTextSize(12); close.setTextColor(0xFF9FB4C6); close.setPadding(dp(12), dp(6), dp(12), dp(6));
        close.setBackground(bg(0xFF152232, 8)); close.setOnClickListener(v -> finish()); bar.addView(close);
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(48)));

        ScrollView sv = new ScrollView(this);
        GridLayout grid = new GridLayout(this); grid.setColumnCount(4);
        grid.setPadding(0, dp(14), 0, dp(14));
        for (Tools.Tool t : Tools.ALL) {
            if (t.activity == null || t.id.equals("desktop")) continue;
            LinearLayout cell = new LinearLayout(this); cell.setOrientation(LinearLayout.VERTICAL);
            cell.setPadding(dp(12), dp(12), dp(12), dp(10));
            cell.setBackground(bg(0xFF111C28, 10));
            TextView nm = new TextView(this); nm.setText(t.name); nm.setTextSize(13); nm.setTextColor(Color.WHITE); nm.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); cell.addView(nm);
            TextView ds = new TextView(this); ds.setText(t.desc); ds.setTextSize(9); ds.setTextColor(0xFF8FA5B8); ds.setMaxLines(2); ds.setPadding(0, dp(3), 0, 0); cell.addView(ds);
            cell.setOnClickListener(v -> { try { startActivity(new Intent(this, t.activity)); } catch (Throwable ignored) {} });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0; lp.height = dp(76); lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            grid.addView(cell, lp);
        }
        sv.addView(grid); root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }
    GradientDrawable bg(int fill, int r) { GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(r)); return d; }
    int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }
}
