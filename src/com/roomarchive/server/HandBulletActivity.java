package com.roomarchive.server;

import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * 手持弹幕：输入文字全屏滚动展示（应援/表白/提醒神器），支持颜色/字号/速度/方向。
 */
public class HandBulletActivity extends BaseActivity {

    private TextView scrollText;
    private ValueAnimator animator;
    private boolean running = false;
    private int speed = 3000;   // 一次滚动时长 ms
    private int fontSize = 42;
    private int colorIdx = 0;
    private final int[] COLORS = {Color.WHITE, Color.rgb(255, 82, 82), Color.rgb(64, 224, 208),
            Color.rgb(255, 220, 0), Color.rgb(0, 229, 255), Color.rgb(255, 105, 180),
            Color.rgb(170, 255, 0)};
    private final String[] COLOR_NAMES = {"白色", "红色", "青色", "金色", "亮蓝", "粉色", "荧光绿"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Tools.bgWithGlow(this));
        root.setPadding(dp(16), dp(18), dp(16), dp(16));

        TextView title = new TextView(this);
        title.setText("手持弹幕");
        title.setTextSize(22);
        title.setTextColor(Tools.textColor(this));
        title.setGravity(Gravity.CENTER);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        Icons.attachToText(title, "device", Tools.textColor(this), 20, 8);
        root.addView(title);

        root.addView(spacer(10));

        final EditText input = new EditText(this);
        input.setHint("输入弹幕内容，如：我爱你！");
        input.setTextSize(15);
        input.setTextColor(Tools.textColor(this));
        GradientDrawable inBg = new GradientDrawable();
        inBg.setColor(Tools.cardColor(this));
        inBg.setCornerRadius(dp(12));
        inBg.setStroke(dp(1), Tools.isDark(this) ? 0xFF2A3040 : 0xFFE4E8EE);
        input.setBackground(inBg);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.addView(input);

        root.addView(spacer(10));

        // 颜色选择
        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        colorRow.setGravity(Gravity.CENTER);
        for (int i = 0; i < COLORS.length; i++) {
            final int idx = i;
            View dot = new View(this);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(COLORS[i]);
            d.setStroke(dp(2), idx == colorIdx ? Color.WHITE : Color.TRANSPARENT);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(30), dp(30));
            lp.setMargins(dp(5), 0, dp(5), 0);
            dot.setLayoutParams(lp);
            dot.setBackground(d);
            dot.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    colorIdx = idx;
                    refreshColorDots(colorRow, idx);
                    if (scrollText != null) scrollText.setTextColor(COLORS[idx]);
                }
            });
            colorRow.addView(dot);
        }
        root.addView(colorRow);

        root.addView(spacer(8));

        // 字号
        LinearLayout sizeRow = new LinearLayout(this);
        sizeRow.setOrientation(LinearLayout.HORIZONTAL);
        sizeRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView sizeL = new TextView(this);
        sizeL.setText("字号");
        sizeL.setTextSize(13);
        sizeL.setTextColor(Tools.subTextColor(this));
        sizeRow.addView(sizeL);
        final SeekBar sizeBar = new SeekBar(this);
        sizeBar.setMax(80);
        sizeBar.setProgress(42);
        sizeRow.addView(sizeBar, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        final TextView sizeV = new TextView(this);
        sizeV.setText("42");
        sizeV.setTextSize(13);
        sizeV.setTextColor(Tools.subTextColor(this));
        sizeRow.addView(sizeV);
        sizeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                fontSize = progress;
                sizeV.setText(String.valueOf(progress));
                if (scrollText != null) scrollText.setTextSize(fontSize);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        root.addView(sizeRow);

        root.addView(spacer(8));

        // 速度
        LinearLayout speedRow = new LinearLayout(this);
        speedRow.setOrientation(LinearLayout.HORIZONTAL);
        speedRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView speedL = new TextView(this);
        speedL.setText("速度");
        speedL.setTextSize(13);
        speedL.setTextColor(Tools.subTextColor(this));
        speedRow.addView(speedL);
        final SeekBar speedBar = new SeekBar(this);
        speedBar.setMax(100);
        speedBar.setProgress(30);
        speedRow.addView(speedBar, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        final TextView speedV = new TextView(this);
        speedV.setText("中速");
        speedV.setTextSize(13);
        speedV.setTextColor(Tools.subTextColor(this));
        speedRow.addView(speedV);
        speedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                speed = 10000 - progress * 70; // 慢→快
                if (progress < 33) speedV.setText("慢速");
                else if (progress < 66) speedV.setText("中速");
                else speedV.setText("快速");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        root.addView(speedRow);

        root.addView(spacer(12));

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        final Button btnStart = new Button(this);
        btnStart.setText("开始滚动");
        btnStart.setAllCaps(false);
        btnStart.setTextColor(Color.WHITE);
        Icons.attachToText(btnStart, "play", Color.WHITE, 16, 6);
        GradientDrawable g1 = new GradientDrawable();
        g1.setColor(Color.rgb(30, 64, 175));
        g1.setCornerRadius(dp(12));
        btnStart.setBackground(g1);
        btnRow.addView(btnStart, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        btnRow.addView(spacerH(8));
        final Button btnStop = new Button(this);
        btnStop.setText("停止");
        btnStop.setAllCaps(false);
        btnStop.setTextColor(Color.WHITE);
        Icons.attachToText(btnStop, "stop", Color.WHITE, 16, 6);
        GradientDrawable g2 = new GradientDrawable();
        g2.setColor(Color.rgb(220, 38, 38));
        g2.setCornerRadius(dp(12));
        btnStop.setBackground(g2);
        btnRow.addView(btnStop, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(btnRow);

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String txt = input.getText().toString().trim();
                if (txt.isEmpty()) {
                    txt = "手持弹幕";
                }
                startScroll(txt);
                btnStart.setText(running ? "重新开始" : "开始滚动");
            }
        });
        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopScroll();
                btnStart.setText("开始滚动");
            }
        });

        setContentView(root);
    }

    /** 全屏滚动弹幕层 */
    private void startScroll(final String text) {
        stopScroll();
        running = true;

        final RelativeLayout overlay = new RelativeLayout(this);
        overlay.setBackgroundColor(Color.BLACK);

        scrollText = new TextView(this);
        scrollText.setText(text);
        scrollText.setTextSize(fontSize);
        scrollText.setTextColor(COLORS[colorIdx]);
        scrollText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        scrollText.setSingleLine(true);
        scrollText.setGravity(Gravity.CENTER_VERTICAL);
        scrollText.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

        final int textW = scrollText.getMeasuredWidth() + 100;
        final int startX = getResources().getDisplayMetrics().widthPixels;
        final int endX = -textW;

        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.addRule(RelativeLayout.CENTER_VERTICAL);
        lp.leftMargin = startX;
        scrollText.setLayoutParams(lp);
        overlay.addView(scrollText);

        final ViewGroup parent = (ViewGroup) getWindow().getDecorView();
        ViewGroup.LayoutParams full = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        parent.addView(overlay, full);

        animator = ValueAnimator.ofInt(startX, endX);
        animator.setDuration(speed);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.RESTART);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int x = (Integer) animation.getAnimatedValue();
                RelativeLayout.LayoutParams plp = (RelativeLayout.LayoutParams) scrollText.getLayoutParams();
                plp.leftMargin = x;
                scrollText.setLayoutParams(plp);
            }
        });
        animator.start();

        overlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopScroll();
            }
        });
    }

    private void stopScroll() {
        running = false;
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        if (scrollText != null) {
            ViewGroup overlay = (ViewGroup) scrollText.getParent();
            if (overlay != null) {
                overlay.removeAllViews();
                ViewGroup decor = (ViewGroup) overlay.getParent();
                if (decor != null) decor.removeView(overlay);
            }
            scrollText = null;
        }
    }

    private void refreshColorDots(LinearLayout row, int idx) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View dot = row.getChildAt(i);
            GradientDrawable d = (GradientDrawable) dot.getBackground();
            d.setStroke(dp(2), i == idx ? Color.WHITE : Color.TRANSPARENT);
        }
    }

    @Override
    protected void onDestroy() {
        stopScroll();
        super.onDestroy();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private View spacer(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        return v;
    }

    private View spacerH(int w) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(w), 1));
        return v;
    }
}
