package com.roomarchive.server;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** 灵动岛设置 · 只申请悬浮窗权限，不申请无障碍/通知使用权 */
public class IslandActivity extends Activity {

    private TextView status;
    private Switch swAlways, swHideLand;
    private SharedPreferences sp;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Tools.applyStatusBar(this);
        sp = getSharedPreferences(IslandService.PREFS, MODE_PRIVATE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Tools.bgWithGlow(this));
        root.setPadding(dp(20), dp(26), dp(20), dp(20));

        TextView title = new TextView(this);
        title.setText("灵动岛");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("顶部胶囊 · 时间 / 电量 / 展开卡片 · 只需悬浮窗权限");
        sub.setTextColor(0xFF9FB3C8);
        sub.setTextSize(12);
        sub.setPadding(0, dp(4), 0, dp(16));
        root.addView(sub);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(glass());
        root.addView(card);

        status = new TextView(this);
        status.setTextColor(0xFF7DD3FC);
        status.setTextSize(13);
        status.setPadding(0, 0, 0, dp(12));
        card.addView(status);

        TextView perm = pill("① 授予悬浮窗权限", 0xFF2563EB);
        perm.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Throwable t) {
                Toast.makeText(this, "请到系统设置里开启悬浮窗", Toast.LENGTH_LONG).show();
            }
        });
        card.addView(perm, lp(-1, dp(46), 0, 8));

        TextView start = pill("② 启动灵动岛", 0xFF16A34A);
        start.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "先授予悬浮窗权限", Toast.LENGTH_SHORT).show();
                return;
            }
            sp.edit().putBoolean(IslandService.KEY_ON, true).apply();
            IslandService.start(this);
            status.postDelayed(this::refresh, 300);
        });
        card.addView(start, lp(-1, dp(46), 0, 8));

        TextView stop = pill("停止灵动岛", 0x33FFFFFF);
        stop.setOnClickListener(v -> {
            sp.edit().putBoolean(IslandService.KEY_ON, false).apply();
            IslandService.stop(this);
            status.postDelayed(this::refresh, 300);
        });
        card.addView(stop, lp(-1, dp(44), 0, 8));

        swAlways = new Switch(this);
        swAlways.setText("常驻显示（无通知时也保留胶囊）");
        swAlways.setTextColor(Color.WHITE);
        swAlways.setTextSize(13);
        swAlways.setChecked(sp.getBoolean(IslandService.KEY_ALWAYS, true));
        swAlways.setOnCheckedChangeListener((v, c) -> sp.edit().putBoolean(IslandService.KEY_ALWAYS, c).apply());
        card.addView(swAlways);

        swHideLand = new Switch(this);
        swHideLand.setText("横屏自动隐藏（竖屏恢复）");
        swHideLand.setTextColor(Color.WHITE);
        swHideLand.setTextSize(13);
        swHideLand.setChecked(sp.getBoolean(IslandService.KEY_HIDE_LAND, true));
        swHideLand.setOnCheckedChangeListener((v, c) -> sp.edit().putBoolean(IslandService.KEY_HIDE_LAND, c).apply());
        card.addView(swHideLand);

        TextView note = new TextView(this);
        note.setText("说明：本版灵动岛只做本机展示，不读取他人通知内容；\n不使用无障碍服务，不会出现「5/5 权限全部授权才能用」的死锁。");
        note.setTextColor(0xFF7E93AA);
        note.setTextSize(11);
        note.setPadding(0, dp(16), 0, 0);
        root.addView(note);

        TextView back = new TextView(this);
        back.setText("返回工具箱");
        back.setTextColor(0xFF9FB3C8);
        back.setTextSize(13);
        back.setGravity(Gravity.CENTER);
        back.setPadding(0, dp(20), 0, dp(10));
        back.setOnClickListener(v -> finish());
        root.addView(back);

        setContentView(root);
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean perm = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
        boolean run = IslandService.isRunning();
        status.setText((perm ? "悬浮窗权限：已授予" : "悬浮窗权限：未授予")
                + "   ·   " + (run ? "灵动岛运行中" : "灵动岛未启动"));
        status.setTextColor(perm && run ? 0xFF4ADE80 : (perm ? 0xFF7DD3FC : 0xFFF87171));
    }

    private TextView pill(String text, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(14);
        tv.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{lighten(color), color});
        g.setCornerRadius(dp(16));
        tv.setBackground(g);
        return tv;
    }

    private GradientDrawable glass() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0x33FFFFFF, 0x14FFFFFF});
        g.setCornerRadius(dp(18));
        g.setStroke(dp(1), 0x33FFFFFF);
        return g;
    }

    private static int lighten(int c) {
        int r = Math.min(255, ((c >> 16) & 0xFF) + 35);
        int g = Math.min(255, ((c >> 8) & 0xFF) + 35);
        int b = Math.min(255, (c & 0xFF) + 35);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private LinearLayout.LayoutParams lp(int w, int h, int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = dp(top);
        p.bottomMargin = dp(bottom);
        return p;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
