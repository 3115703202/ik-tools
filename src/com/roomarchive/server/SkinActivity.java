package com.roomarchive.server;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 应用外观：切换桌面显示的图标和名称。
 * 实现方式是预置一批 activity-alias（各自带 label/icon），切换时只启用其中一个，
 * 不需要改 manifest、不需要 root，桌面立刻能看到新名字新图标。
 */
public class SkinActivity extends BaseActivity {

    static final String[][] PRESETS = new String[][]{
            {"Default", "IK工具箱", "ic_pick_toolbox"},
            {"Calc", "计算器", "ic_pick_calc"},
            {"Photo", "相册", "ic_pick_photo"},
            {"Files", "文件管理", "ic_pick_files"},
            {"Weather", "天气", "ic_pick_weather"},
            {"Calendar", "日历", "ic_pick_calendar"},
            {"Recorder", "录音机", "ic_pick_rec"},
            {"Notes", "备忘录", "ic_pick_notes"},
            {"Browser", "浏览器", "ic_pick_browser"},
            {"Camera", "相机", "ic_pick_camera"},
            {"Settings", "设置", "ic_pick_settings"},
            {"Clock", "时钟", "ic_pick_clock"},
            {"Music", "音乐", "ic_pick_music"},
            {"Video", "视频", "ic_pick_video"},
            {"Store", "应用商店", "ic_pick_store"},
    };
    private static final String PREF = "ik_skin";

    private LinearLayout grid;
    private TextView current;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Tools.bgWithGlow(this));
        root.setPadding(dp(16), dp(20), dp(16), dp(24));
        sv.addView(root);

        TextView title = Tools.pageTitle(this, "应用外观");
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        current = new TextView(this);
        current.setTextSize(12);
        current.setGravity(Gravity.CENTER);
        current.setTextColor(Tools.subTextColor(this));
        current.setPadding(0, dp(6), 0, dp(10));
        root.addView(current);

        TextView tip = new TextView(this);
        tip.setText("点一下换桌面名字和图标，回桌面看立刻生效。名字只影响桌面入口，App 内部不变。");
        tip.setTextSize(12);
        tip.setTextColor(Tools.subTextColor(this));
        tip.setPadding(dp(4), 0, dp(4), dp(12));
        root.addView(tip);

        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        root.addView(grid);

        setContentView(sv);
        buildGrid();
        refreshCurrent();
    }

    private void buildGrid() {
        grid.removeAllViews();
        LinearLayout row = null;
        for (int i = 0; i < PRESETS.length; i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rp.topMargin = dp(6);
                row.setLayoutParams(rp);
                grid.addView(row);
            }
            row.addView(cell(PRESETS[i]));
        }
    }

    private View cell(final String[] preset) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(10), dp(12), dp(10), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Tools.cardColor(this));
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), Tools.isDark(this) ? 0xFF2A3040 : 0xFFE4E8EE);
        box.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        box.setLayoutParams(lp);

        ImageView icon = new ImageView(this);
        icon.setImageResource(getResources().getIdentifier(preset[2], "drawable", getPackageName()));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(52), dp(52));
        icon.setLayoutParams(ip);
        box.addView(icon);

        TextView name = new TextView(this);
        name.setText(preset[1]);
        name.setTextSize(13);
        name.setTextColor(Tools.textColor(this));
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, dp(6), 0, 0);
        box.addView(name);

        box.setOnClickListener(v -> apply(preset[0], preset[1]));
        return box;
    }

    private void apply(String key, String label) {
        String pkg = getPackageName();
        try {
            PackageManager pm = getPackageManager();
            // 先启用目标，再关掉其它，避免桌面上短暂出现两个图标以外的空档
            pm.setComponentEnabledSetting(new ComponentName(pkg, pkg + ".alias." + key),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            for (String[] p : PRESETS) {
                if (p[0].equals(key)) continue;
                pm.setComponentEnabledSetting(new ComponentName(pkg, pkg + ".alias." + p[0]),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            }
            getSharedPreferences(PREF, MODE_PRIVATE).edit().putString("alias", key).apply();
            refreshCurrent();
            Toast.makeText(this, "已换成「" + label + "」，回桌面就能看到", Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Toast.makeText(this, "切换失败：" + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String currentKey() {
        try {
            String saved = getSharedPreferences(PREF, MODE_PRIVATE).getString("alias", null);
            if (saved != null) return saved;
            PackageManager pm = getPackageManager();
            String pkg = getPackageName();
            for (String[] p : PRESETS) {
                int st = pm.getComponentEnabledSetting(new ComponentName(pkg, pkg + ".alias." + p[0]));
                if (st == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return p[0];
            }
        } catch (Throwable ignored) {
        }
        return "Default";
    }

    private void refreshCurrent() {
        String key = currentKey();
        String label = "IK工具箱";
        for (String[] p : PRESETS) if (p[0].equals(key)) label = p[1];
        current.setText("当前桌面显示：" + label);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
