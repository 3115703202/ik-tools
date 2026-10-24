package com.roomarchive.server;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 收藏页：显示收藏的功能卡片，可取消收藏 */
public class FavoritesActivity extends BaseActivity {

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
        TextView title = Tools.pageTitle(this, "我的收藏");
        title.setGravity(Gravity.CENTER);
        head.addView(title);
        outer.addView(head);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setClipChildren(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(8), dp(12), dp(18));
        scroll.addView(root);

        Set<String> favs = Tools.favSet(this);
        List<Tools.Tool> list = new ArrayList<>();
        for (String id : favs) {
            Tools.Tool t = Tools.byId(id);
            if (t != null) list.add(t);
        }

        if (list.isEmpty()) {
            LinearLayout emptyBox = new LinearLayout(this);
            emptyBox.setOrientation(LinearLayout.VERTICAL);
            emptyBox.setGravity(Gravity.CENTER);
            emptyBox.setPadding(dp(16), dp(60), dp(16), dp(60));
            android.widget.ImageView emptyIcon = new android.widget.ImageView(this);
            emptyIcon.setImageDrawable(Icons.of(this, "fav", Tools.subTextColor(this), 48));
            emptyIcon.setScaleType(android.widget.ImageView.ScaleType.CENTER);
            emptyBox.addView(emptyIcon, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            emptyBox.addView(spacer(12));
            TextView empty = new TextView(this);
            empty.setText("还没有收藏\n\n在主界面点功能卡片右下角的星标即可收藏\n常用的功能一眼就能找到");
            empty.setTextSize(14);
            empty.setTextColor(Tools.subTextColor(this));
            empty.setGravity(Gravity.CENTER);
            emptyBox.addView(empty);
            root.addView(emptyBox);
        } else {
            LinearLayout row = null;
            for (int i = 0; i < list.size(); i++) {
                if (i % 2 == 0) {
                    row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    root.addView(row, new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT));
                }
                final Tools.Tool t = list.get(i);
                View card = favCard(t);
                row.addView(card, new LinearLayout.LayoutParams(0, dp(132), 1f));
                if (i % 2 == 1) root.addView(spacer(8));
            }
            if (list.size() % 2 == 1) {
                row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
                root.addView(spacer(8));
            }
        }

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

    private View favCard(final Tools.Tool t) {
        boolean dark = Tools.isDark(this);
        FrameLayout card = new FrameLayout(this);
        card.setPadding(dp(10), dp(10), dp(10), dp(8));
        card.setBackground(Tools.liquidGlassCard(this, 20));
        Tools.setElevation(card, 8);
        card.setClickable(true);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(FavoritesActivity.this, t.activity));
                overridePendingTransition(0, 0);
            }
        });

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setGravity(Gravity.CENTER);
        FrameLayout tile = new FrameLayout(this);
        tile.setBackground(Tools.iconTile(this, t.color, 40, 12));
        android.widget.ImageView iconIv = new android.widget.ImageView(this);
        iconIv.setImageDrawable(Icons.tool(this, t, 24));
        iconIv.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        tile.addView(iconIv, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        inner.addView(tile);
        inner.addView(spacer(4));
        TextView name = new TextView(this);
        name.setText(t.name);
        name.setTextSize(14);
        name.setTextColor(Tools.textColor(this));
        name.setGravity(Gravity.CENTER);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        inner.addView(name);
        TextView desc = new TextView(this);
        desc.setText(t.desc);
        desc.setTextSize(10);
        desc.setTextColor(Tools.subTextColor(this));
        desc.setGravity(Gravity.CENTER);
        inner.addView(desc);
        card.addView(inner);

        final TextView star = new TextView(this);
        star.setGravity(Gravity.CENTER);
        star.setCompoundDrawablesWithIntrinsicBounds(
                Icons.of(this, "fav", 0xFFF59E0B, 15), null, null, null);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.BOTTOM | Gravity.END);
        star.setLayoutParams(slp);
        star.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Tools.setFav(FavoritesActivity.this, t.id, false);
                Toast.makeText(FavoritesActivity.this, "已取消收藏", Toast.LENGTH_SHORT).show();
                recreate();
            }
        });
        card.addView(star);
        return card;
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

        bar.addView(navItem("home", "首页", 0, 1));
        bar.addView(navItem("fav", "收藏", 1, 1));
        bar.addView(navItem("game", "游戏", 2, 1));
        bar.addView(navItem("user", "我的", 3, 1));
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
        item.setClickable(true);
        item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent it;
                if (tab == 0) {
                    it = new Intent(FavoritesActivity.this, MainActivity.class);
                } else if (tab == 2) {
                    it = new Intent(FavoritesActivity.this, MainActivity.class);
                    it.putExtra("scroll_ent", true);
                } else if (tab == 3) {
                    it = new Intent(FavoritesActivity.this, UserCenterActivity.class);
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

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private View spacer(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        return v;
    }
}
