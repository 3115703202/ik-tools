package com.roomarchive.server;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

/** 用户中心：个人资料 / 赞赏 / 反馈 / 设置 / 插件管理 / 壁纸 / 主题 / 联系 */
public class UserCenterActivity extends BaseActivity {

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    private void buildUi() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackground(Tools.bgWithGlow(this));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(16), dp(20), dp(16), dp(10));
        TextView title = Tools.pageTitle(this, "用户中心");
        title.setGravity(Gravity.CENTER);
        head.addView(title);
        outer.addView(head);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setClipChildren(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(8), dp(14), dp(16));
        scroll.addView(root);

        String nick = getSharedPreferences("ik_tools", MODE_PRIVATE).getString("nick", "IK 用户");
        root.addView(listItem("person", "个人资料", "昵称：" + nick, new Runnable() {
            @Override
            public void run() {
                editProfile();
            }
        }));
        root.addView(listItem("heart", "赞赏我们", "喜欢请支持作者", new Runnable() {
            @Override
            public void run() {
                aboutDonate();
            }
        }));
        root.addView(listItem("feedback", "意见反馈", "告诉我们你的建议", new Runnable() {
            @Override
            public void run() {
                feedback();
            }
        }));
        root.addView(listItem("settings", "设置", "主题 / 字号 / 缓存", new Runnable() {
            @Override
            public void run() {
                settings();
            }
        }));
        root.addView(listItem("plugin", "插件管理", "查看已内置功能模块", new Runnable() {
            @Override
            public void run() {
                plugins();
            }
        }));
        root.addView(listItem("wall", "壁纸随心换", "默认 / 自定义 / 3D视差", new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(UserCenterActivity.this, WallpaperActivity.class));
                overridePendingTransition(0, 0);
            }
        }));
        root.addView(listItem("theme", "切换原生界面", "浅色 / 深色主题", new Runnable() {
            @Override
            public void run() {
                switchTheme();
            }
        }));

        root.addView(spacer(18));

        TextView btn = new TextView(this);
        btn.setText("联系客服");
        btn.setTextSize(16);
        btn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        btn.setTextColor(Color.WHITE);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(0), dp(14), dp(0), dp(14));
        btn.setBackground(Tools.pillButton(this, 50, true));
        btn.setClickable(true);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder b = new AlertDialog.Builder(UserCenterActivity.this);
                b.setTitle("联系客服");
                b.setMessage("暂未开通在线客服。\n遇到问题请通过「意见反馈」提交，我们会尽快处理。\n\n本软件永久免费 · 无广告 · 无收费，如遇到收费卡密均为诈骗。");
                b.setPositiveButton("知道了", null);
                b.show();
            }
        });
        root.addView(btn);

        outer.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout barBox = bottomBar();
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.setMargins(dp(12), 0, dp(12), dp(8));
        outer.addView(barBox, blp);

        setContentView(outer);
    }

    /** 列表项：图标衬底 + 标题 + 副标题 + 右箭头 */
    private View listItem(String iconKey, String label, String sub, final Runnable action) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setBackground(Tools.liquidGlassCard(this, 18));
        Tools.setElevation(item, 6);
        item.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(64));
        lp.bottomMargin = dp(8);
        item.setLayoutParams(lp);
        item.setClickable(true);
        item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });

        // 40dp 图标衬底 + 22dp 矢量图标
        FrameLayout tile = new FrameLayout(this);
        tile.setBackground(Tools.iconTile(this, Tools.accentColor(this), 40, 12));
        android.widget.ImageView iconIv = new android.widget.ImageView(this);
        iconIv.setImageDrawable(Icons.of(this, iconKey, Tools.accentColor(this), 22));
        iconIv.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        tile.addView(iconIv, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        item.addView(tile);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(12), 0, 0, 0);
        TextView t1 = new TextView(this);
        t1.setText(label);
        t1.setTextSize(15);
        t1.setTextColor(Tools.textColor(this));
        t1.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        texts.addView(t1);
        TextView t2 = new TextView(this);
        t2.setText(sub);
        t2.setTextSize(11);
        t2.setTextColor(Tools.subTextColor(this));
        texts.addView(t2);
        item.addView(texts);

        android.widget.ImageView arrow = new android.widget.ImageView(this);
        arrow.setImageDrawable(Icons.of(this, "arrowRight", Tools.subTextColor(this), 18));
        arrow.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        item.addView(arrow, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        return item;
    }

    private void editProfile() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(4));
        final EditText input = new EditText(this);
        input.setHint("输入昵称");
        input.setText(getSharedPreferences("ik_tools", MODE_PRIVATE).getString("nick", ""));
        box.addView(input);

        final String[] faces = {"smile", "cool", "robot", "cat", "dog", "frog", "star", "flame", "gamepad", "muscle"};
        LinearLayout faceRow = new LinearLayout(this);
        faceRow.setOrientation(LinearLayout.HORIZONTAL);
        faceRow.setGravity(Gravity.CENTER);
        faceRow.setPadding(0, dp(8), 0, 0);
        final TextView[] faceViews = new TextView[faces.length];
        for (int i = 0; i < faces.length; i++) {
            final int idx = i;
            faceViews[i] = new TextView(this);
            faceViews[i].setGravity(Gravity.CENTER);
            faceViews[i].setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
            faceViews[i].setCompoundDrawablesWithIntrinsicBounds(
                    Icons.of(this, faces[idx], Tools.subTextColor(this), 26), null, null, null);
            faceViews[i].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    for (int j = 0; j < faceViews.length; j++) {
                        int c = j == idx ? Tools.accentColor(UserCenterActivity.this)
                                : Tools.subTextColor(UserCenterActivity.this);
                        faceViews[j].setCompoundDrawablesWithIntrinsicBounds(
                                Icons.of(UserCenterActivity.this, faces[j], c, 26), null, null, null);
                    }
                    getSharedPreferences("ik_tools", MODE_PRIVATE)
                            .edit().putString("face", faces[idx]).apply();
                    Toast.makeText(UserCenterActivity.this, "头像已选择", Toast.LENGTH_SHORT).show();
                }
            });
            faceRow.addView(faceViews[i]);
        }
        box.addView(faceRow);

        new AlertDialog.Builder(this)
                .setTitle("个人资料")
                .setView(box)
                .setPositiveButton("保存", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        String n = input.getText().toString().trim();
                        if (n.isEmpty()) n = "IK 用户";
                        getSharedPreferences("ik_tools", MODE_PRIVATE).edit().putString("nick", n).apply();
                        Toast.makeText(UserCenterActivity.this, "已保存", Toast.LENGTH_SHORT).show();
                        recreate();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void aboutDonate() {
        new AlertDialog.Builder(this)
                .setTitle("赞赏我们")
                .setMessage("感谢你的喜欢！\n\nIK工具箱 坚持永久免费、无广告、无收费。\n\n支持我们的最好方式：\n· 把软件分享给需要的朋友\n· 通过「意见反馈」告诉我们使用体验\n· 在应用商店给个好评\n\n本软件不含任何收费项目，如遇到收费卡密均为诈骗，请勿相信！")
                .setPositiveButton("知道了", null)
                .show();
    }

    private void feedback() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(4));
        final EditText input = new EditText(this);
        input.setHint("写下你的建议或遇到的问题…");
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setMinLines(4);
        box.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("意见反馈")
                .setView(box)
                .setPositiveButton("提交", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        String txt = input.getText().toString().trim();
                        if (txt.isEmpty()) {
                            Toast.makeText(UserCenterActivity.this, "内容不能为空", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        try {
                            File dir = new File(FileStore.outDir(UserCenterActivity.this), "反馈");
                            dir.mkdirs();
                            File f = new File(dir, "feedback_" + System.currentTimeMillis() + ".txt");
                            java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
                            fos.write(txt.getBytes("UTF-8"));
                            fos.close();
                            Toast.makeText(UserCenterActivity.this,
                                    "已保存到 " + f.getAbsolutePath() + "\n可通过「我的文件」查看", Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            Toast.makeText(UserCenterActivity.this, "保存失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void settings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(4));
        final android.widget.RadioGroup themeGroup = new android.widget.RadioGroup(this);
        themeGroup.setOrientation(LinearLayout.VERTICAL);
        final android.widget.RadioButton light = new android.widget.RadioButton(this);
        light.setText("浅色主题（默认）");
        final android.widget.RadioButton dark = new android.widget.RadioButton(this);
        dark.setText("深色主题");
        if (Tools.isDark(this)) dark.setChecked(true); else light.setChecked(true);
        themeGroup.addView(light);
        themeGroup.addView(dark);
        box.addView(themeGroup);

        TextView clear = new TextView(this);
        clear.setText("清除缓存（释放存储空间）");
        clear.setTextSize(14);
        clear.setTextColor(Tools.accentColor(this));
        clear.setPadding(0, dp(14), 0, dp(2));
        clear.setClickable(true);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    File cache = getCacheDir();
                    long before = dirSize(cache);
                    deleteDir(cache);
                    Toast.makeText(UserCenterActivity.this,
                            "已清理 " + fmt(before) + " 缓存", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(UserCenterActivity.this, "清理失败", Toast.LENGTH_SHORT).show();
                }
            }
        });
        box.addView(clear);

        new AlertDialog.Builder(this)
                .setTitle("设置")
                .setView(box)
                .setPositiveButton("应用主题", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        Tools.setDark(UserCenterActivity.this, dark.isChecked());
                        Toast.makeText(UserCenterActivity.this, "主题已切换", Toast.LENGTH_SHORT).show();
                        recreate();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void switchTheme() {
        boolean now = !Tools.isDark(this);
        Tools.setDark(this, now);
        Toast.makeText(this, now ? "已切换到深色界面" : "已切换到浅色界面", Toast.LENGTH_SHORT).show();
        recreate();
    }

    private void plugins() {
        StringBuilder sb = new StringBuilder();
        sb.append("已内置 ").append(Tools.ALL.length).append(" 个功能模块（全部免费、离线可用）：\n\n");
        for (Tools.Tool t : Tools.ALL) {
            sb.append(t.name).append("\n");
        }
        sb.append("\n在「首页」可进入全部功能");
        new AlertDialog.Builder(this)
                .setTitle("插件管理")
                .setMessage(sb.toString())
                .setPositiveButton("知道了", null)
                .show();
    }

    private LinearLayout bottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(28));
        bg.setStroke(dp(1), Color.rgb(230, 235, 242));
        bar.setBackground(bg);
        bar.setElevation(dp(6));
        bar.setPadding(dp(8), dp(8), dp(8), dp(8));
        bar.addView(navItem("home", "首页", 0, 3));
        bar.addView(navItem("fav", "收藏", 1, 3));
        bar.addView(navItem("game", "游戏", 2, 3));
        bar.addView(navItem("user", "我的", 3, 3));
        return bar;
    }

    private LinearLayout navItem(String iconKey, String label, final int tab, final int activeTab) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(0, dp(8), 0, dp(6));
        boolean active = (tab == activeTab);
        int iconColor = active ? Color.WHITE : Color.rgb(140, 150, 165);

        android.widget.FrameLayout iconWrap = new android.widget.FrameLayout(this);
        GradientDrawable ebg = new GradientDrawable();
        ebg.setShape(GradientDrawable.OVAL);
        ebg.setColor(active ? Color.rgb(15, 76, 92) : 0x00000000);
        iconWrap.setBackground(ebg);
        iconWrap.setLayoutParams(new android.widget.FrameLayout.LayoutParams(dp(38), dp(30)));

        android.widget.ImageView e = new android.widget.ImageView(this);
        e.setImageDrawable(Icons.nav(this, iconKey, iconColor, 22));
        e.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        android.widget.FrameLayout.LayoutParams elp = new android.widget.FrameLayout.LayoutParams(dp(22), dp(22));
        elp.gravity = Gravity.CENTER;
        iconWrap.addView(e, elp);
        item.addView(iconWrap);

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(11);
        l.setTypeface(active ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
        l.setIncludeFontPadding(false);
        l.setGravity(Gravity.CENTER);
        l.setPadding(0, dp(4), 0, 0);
        l.setTextColor(active ? Color.rgb(15, 76, 92) : Color.rgb(140, 150, 165));
        item.addView(l, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        item.setLayoutParams(new LinearLayout.LayoutParams(0, dp(68), 1f));
        item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent it;
                if (tab == 0) {
                    it = new Intent(UserCenterActivity.this, MainActivity.class);
                } else if (tab == 1) {
                    it = new Intent(UserCenterActivity.this, FavoritesActivity.class);
                } else if (tab == 2) {
                    it = new Intent(UserCenterActivity.this, MainActivity.class);
                    it.putExtra("scroll_ent", true);
                } else {
                    return;
                }
                startActivity(it);
                overridePendingTransition(0, 0);
                finish();
            }
        });
        return item;
    }

    private static long dirSize(File dir) {
        long size = 0;
        File[] fs = dir.listFiles();
        if (fs == null) return 0;
        for (File f : fs) {
            if (f.isDirectory()) size += dirSize(f);
            else size += f.length();
        }
        return size;
    }

    private static void deleteDir(File dir) {
        File[] fs = dir.listFiles();
        if (fs != null) {
            for (File f : fs) {
                if (f.isDirectory()) deleteDir(f);
                else f.delete();
            }
        }
        dir.delete();
    }

    private static String fmt(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return String.format(java.util.Locale.US, "%.1f KB", b / 1024.0);
        return String.format(java.util.Locale.US, "%.2f MB", b / 1024.0 / 1024.0);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private View spacer(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        return v;
    }
}
