package com.roomarchive.server;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** 帧率监测开关。 */
public class FpsActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Tools.applyStatusBar(this);
        getWindow().setStatusBarColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= 23) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(22), dp(34), dp(22), dp(30)); root.setBackgroundColor(0xFFF7F7F9);
        TextView t = new TextView(this); t.setText("帧率监测"); t.setTextSize(30); t.setTextColor(0xFF111114); t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); root.addView(t);
        TextView s = new TextView(this); s.setText("实时悬浮窗：FPS、每帧耗时，低于 30 帧变红。进入游戏后开，退出记得关。"); s.setTextSize(13); s.setTextColor(0xFF777783); s.setPadding(0, dp(6), 0, dp(24)); root.addView(s);
        root.addView(btn("开启悬浮 FPS", 0xFF16A34A, () -> {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
                return;
            }
            startService(new Intent(this, FpsService.class)); Toast.makeText(this, "已开启", Toast.LENGTH_SHORT).show();
        }));
        root.addView(btn("关闭悬浮 FPS", 0xFF44444C, () -> { stopService(new Intent(this, FpsService.class)); Toast.makeText(this, "已关闭", Toast.LENGTH_SHORT).show(); }));
        setContentView(root);
    }
    private TextView btn(String text, int color, Runnable r) {
        TextView v = new TextView(this); v.setText(text); v.setTextSize(16); v.setGravity(Gravity.CENTER); v.setTextColor(Color.WHITE); v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(14)); v.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54)); lp.bottomMargin = dp(12); v.setLayoutParams(lp);
        v.setOnClickListener(x -> r.run()); return v;
    }
    int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }
}
