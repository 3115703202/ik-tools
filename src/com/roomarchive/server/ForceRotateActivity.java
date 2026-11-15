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

/** 强制横屏：写系统旋转设置，真实改变全局方向。 */
public class ForceRotateActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Tools.applyStatusBar(this);
        getWindow().setStatusBarColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= 23) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(34), dp(22), dp(30));
        root.setBackgroundColor(0xFFF7F7F9);
        TextView t = new TextView(this); t.setText("强制横屏"); t.setTextSize(30); t.setTextColor(0xFF111114); t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); root.addView(t);
        TextView s = new TextView(this); s.setText("直接改系统旋转锁，所有应用生效；需要“修改系统设置”权限。"); s.setTextSize(13); s.setTextColor(0xFF777783); s.setPadding(0, dp(6), 0, dp(24)); root.addView(s);
        root.addView(btn("强制横屏", 0xFF16A34A, () -> setRot(1)));
        root.addView(btn("强制竖屏", 0xFF2F6BFF, () -> setRot(0)));
        root.addView(btn("恢复自动旋转", 0xFF44444C, this::autoRot));
        TextView tip = new TextView(this); tip.setText("部分厂商系统会锁死方向，写入失败会提示。"); tip.setTextSize(12); tip.setTextColor(0xFF9A9AA2); tip.setPadding(0, dp(18), 0, 0); root.addView(tip);
        setContentView(root);
    }
    private TextView btn(String text, int color, Runnable r) {
        TextView v = new TextView(this); v.setText(text); v.setTextSize(16); v.setGravity(Gravity.CENTER); v.setTextColor(Color.WHITE); v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(14)); v.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54)); lp.bottomMargin = dp(12); v.setLayoutParams(lp);
        v.setOnClickListener(x -> r.run()); return v;
    }
    private boolean canWrite() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.System.canWrite(this)) {
            Toast.makeText(this, "请先允许修改系统设置", Toast.LENGTH_LONG).show();
            try { startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName()))); }
            catch (Throwable t) { startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)); }
            return false;
        }
        return true;
    }
    private void setRot(int rot) {
        if (!canWrite()) return;
        try {
            Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
            Settings.System.putInt(getContentResolver(), Settings.System.USER_ROTATION, rot);
            setRequestedOrientation(rot == 1 ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            Toast.makeText(this, rot == 1 ? "已强制横屏" : "已强制竖屏", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) { Toast.makeText(this, "写入失败：" + t.getMessage(), Toast.LENGTH_LONG).show(); }
    }
    private void autoRot() {
        if (!canWrite()) return;
        try { Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 1); Toast.makeText(this, "已恢复自动旋转", Toast.LENGTH_SHORT).show(); }
        catch (Throwable t) { Toast.makeText(this, "写入失败", Toast.LENGTH_LONG).show(); }
    }
    int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }
}
