package com.roomarchive.server;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 应用外观：切换桌面显示的图标和名称。
 * 15 套 activity-alias，切换即生效，免 root；页面采用液态玻璃卡片 + 桌面预览。
 */
public class SkinActivity extends BaseActivity {

    static final Object[][] PRESETS = new Object[][]{
            {"Default", "IK工具箱", "ic_pick_toolbox", 0xFF0F766E},
            {"Calc", "计算器", "ic_pick_calc", 0xFF334155},
            {"Photo", "相册", "ic_pick_photo", 0xFF7C3AED},
            {"Files", "文件管理", "ic_pick_files", 0xFF1E40AF},
            {"Weather", "天气", "ic_pick_weather", 0xFF0EA5E9},
            {"Calendar", "日历", "ic_pick_calendar", 0xFFDC2626},
            {"Recorder", "录音机", "ic_pick_rec", 0xFFDB2777},
            {"Notes", "备忘录", "ic_pick_notes", 0xFFB45309},
            {"Browser", "浏览器", "ic_pick_browser", 0xFF2563EB},
            {"Camera", "相机", "ic_pick_camera", 0xFF0891B2},
            {"Settings", "设置", "ic_pick_settings", 0xFF475569},
            {"Clock", "时钟", "ic_pick_clock", 0xFFEA580C},
            {"Music", "音乐", "ic_pick_music", 0xFF9333EA},
            {"Video", "视频", "ic_pick_video", 0xFFBE123C},
            {"Store", "应用商店", "ic_pick_store", 0xFF0369A1},
    };
    private static final String PREF = "ik_skin";

    private LinearLayout grid;
    private FrameLayout previewIcon;
    private TextView previewName, previewMeta, countText;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        sv.setClipToPadding(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Tools.bgWithGlow(this));
        root.setPadding(dp(16), dp(20), dp(16), dp(28));
        sv.addView(root);

        TextView title = Tools.pageTitle(this, "应用外观");
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("换掉桌面上的名字和图标 · 免 root · 立即生效");
        sub.setTextSize(12);
        sub.setTextColor(Tools.subTextColor(this));
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(6), 0, dp(14));
        root.addView(sub);

        root.addView(previewCard());
        root.addView(spacer(18));

        TextView sec = Tools.sectionTitle(this, "选择样式");
        root.addView(sec);
        countText = new TextView(this);
        countText.setTextSize(11);
        countText.setTextColor(Tools.subTextColor(this));
        countText.setPadding(dp(2), dp(2), 0, dp(8));
        root.addView(countText);

        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        root.addView(grid);

        root.addView(spacer(16));
        root.addView(tipCard());

        setContentView(sv);
        buildGrid();
        refreshCurrent();
    }

    /** 顶部桌面预览：一眼看到换完是什么样 */
    private View previewCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(Tools.liquidGlassCard(this, 22));
        Tools.setElevation(card, 10);

        LinearLayout home = new LinearLayout(this);
        home.setOrientation(LinearLayout.VERTICAL);
        home.setGravity(Gravity.CENTER);
        // 桌面图标尺寸，带一点底部阴影模拟放在桌面上的样子
        FrameLayout tile = new FrameLayout(this);
        tile.setLayoutParams(new LinearLayout.LayoutParams(dp(72), dp(72)));
        previewIcon = new FrameLayout(this);
        previewIcon.setLayoutParams(new FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER));
        Tools.setElevation(previewIcon, 6);
        tile.addView(previewIcon);
        home.addView(tile);
        card.addView(home);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = dp(16);
        texts.setLayoutParams(tp);

        TextView caption = new TextView(this);
        caption.setText("桌面显示效果");
        caption.setTextSize(11);
        caption.setTextColor(Tools.subTextColor(this));
        texts.addView(caption);

        previewName = new TextView(this);
        previewName.setTextSize(19);
        previewName.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        previewName.setTextColor(Tools.textColor(this));
        previewName.setPadding(0, dp(4), 0, dp(4));
        texts.addView(previewName);

        previewMeta = new TextView(this);
        previewMeta.setTextSize(11);
        previewMeta.setTextColor(Tools.subTextColor(this));
        previewMeta.setText("点下面的样式即可换成这个");
        texts.addView(previewMeta);
        card.addView(texts);
        return card;
    }

    private View tipCard() {
        TextView tip = new TextView(this);
        tip.setText("· 名字只改桌面入口，App 内部和系统设置里仍是 IK工具箱\n"
                + "· 换完回桌面即可看到；桌面有缓存就下拉刷新一下\n"
                + "· 想加别的名字或图标，跟我说一声就行");
        tip.setTextSize(12);
        tip.setTextColor(Tools.subTextColor(this));
        tip.setLineSpacing(dp(4), 1f);
        tip.setPadding(dp(16), dp(14), dp(16), dp(14));
        tip.setBackground(Tools.liquidGlassCard(this, 18));
        return tip;
    }

    private void buildGrid() {
        grid.removeAllViews();
        String cur = currentKey();
        LinearLayout row = null;
        for (int i = 0; i < PRESETS.length; i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rp.topMargin = dp(8);
                row.setLayoutParams(rp);
                grid.addView(row);
            }
            row.addView(cell(PRESETS[i], cur.equals(PRESETS[i][0])));
        }
    }

    private View cell(final Object[] preset, boolean selected) {
        int color = (int) preset[3];

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(8), dp(14), dp(8), dp(12));
        android.graphics.drawable.Drawable glass = Tools.liquidGlassCard(this, 18);
        if (selected) {
            GradientDrawable ring = new GradientDrawable();
            ring.setCornerRadius(dp(18));
            ring.setColor(0x00000000);
            ring.setStroke(dp(2), Tools.accentColor(this));
            box.setBackground(new android.graphics.drawable.LayerDrawable(
                    new android.graphics.drawable.Drawable[]{glass, ring}));
        } else {
            box.setBackground(glass);
        }
        if (selected) Tools.setElevation(box, 6);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        box.setLayoutParams(lp);

        // 图标 + 光晕 + 选中角标
        FrameLayout stack = new FrameLayout(this);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(64), dp(64));
        stack.setLayoutParams(sp);

        View glow = new View(this);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColors(new int[]{withAlpha(color, 96), withAlpha(color, 0)});
        gd.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        gd.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        gd.setGradientRadius(dp(32));
        glow.setBackground(gd);
        glow.setLayoutParams(new FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER));
        stack.addView(glow);

        ImageView icon = new ImageView(this);
        icon.setImageResource(getResources().getIdentifier((String) preset[2], "drawable", getPackageName()));
        icon.setLayoutParams(new FrameLayout.LayoutParams(dp(50), dp(50), Gravity.CENTER));
        stack.addView(icon);

        if (selected) {
            TextView badge = new TextView(this);
            badge.setText("✓");
            badge.setTextSize(10);
            badge.setTextColor(Color.WHITE);
            badge.setGravity(Gravity.CENTER);
            GradientDrawable bd = new GradientDrawable();
            bd.setShape(GradientDrawable.OVAL);
            bd.setColors(Tools.accentGradient(this));
            badge.setBackground(bd);
            badge.setLayoutParams(new FrameLayout.LayoutParams(dp(18), dp(18),
                    Gravity.TOP | Gravity.RIGHT));
            stack.addView(badge);
        }
        box.addView(stack);

        TextView name = new TextView(this);
        name.setText((String) preset[1]);
        name.setTextSize(13);
        name.setTextColor(selected ? Tools.accentColor(this) : Tools.textColor(this));
        if (selected) name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, dp(6), 0, 0);
        name.setSingleLine(true);
        box.addView(name);

        TextView state = new TextView(this);
        state.setText(selected ? "使用中" : "点一下换上");
        state.setTextSize(10);
        state.setTextColor(Tools.subTextColor(this));
        state.setGravity(Gravity.CENTER);
        state.setPadding(0, dp(3), 0, 0);
        box.addView(state);

        Tools.addPressAnimation(box);
        box.setOnClickListener(v -> apply((String) preset[0], (String) preset[1]));
        return box;
    }

    private int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private void apply(String key, String label) {
        String pkg = getPackageName();
        try {
            PackageManager pm = getPackageManager();
            pm.setComponentEnabledSetting(new ComponentName(pkg, pkg + ".alias." + key),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            for (Object[] p : PRESETS) {
                if (((String) p[0]).equals(key)) continue;
                pm.setComponentEnabledSetting(new ComponentName(pkg, pkg + ".alias." + p[0]),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            }
            getSharedPreferences(PREF, MODE_PRIVATE).edit().putString("alias", key).apply();
            buildGrid();
            refreshCurrent();
            Toast.makeText(this, "已换成「" + label + "」，回桌面看", Toast.LENGTH_SHORT).show();
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
            for (Object[] p : PRESETS) {
                int st = pm.getComponentEnabledSetting(new ComponentName(pkg, pkg + ".alias." + p[0]));
                if (st == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return (String) p[0];
            }
        } catch (Throwable ignored) {
        }
        return "Default";
    }

    private void refreshCurrent() {
        String key = currentKey();
        Object[] cur = PRESETS[0];
        for (Object[] p : PRESETS) if (((String) p[0]).equals(key)) cur = p;
        previewIcon.removeAllViews();
        ImageView icon = new ImageView(this);
        icon.setImageResource(getResources().getIdentifier((String) cur[2], "drawable", getPackageName()));
        icon.setLayoutParams(new FrameLayout.LayoutParams(dp(64), dp(64)));
        previewIcon.addView(icon);
        previewName.setText((String) cur[1]);
        previewMeta.setText("当前使用 · 第 " + (indexOf(key) + 1) + " / " + PRESETS.length + " 套");
        if (countText != null) countText.setText("共 " + PRESETS.length + " 套，点亮哪个就用哪个");
    }

    private int indexOf(String key) {
        for (int i = 0; i < PRESETS.length; i++) if (((String) PRESETS[i][0]).equals(key)) return i;
        return 0;
    }

    private View spacer(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        return v;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
