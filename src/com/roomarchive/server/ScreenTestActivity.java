package com.roomarchive.server;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * 屏幕检测：纯色坏点检测 + 触摸轨迹测试。
 * 工具箱类 App 的常见项目，装机验机必备。
 */
public class ScreenTestActivity extends Activity {

    private static final int[] COLORS = {Color.WHITE, Color.BLACK, 0xFFFF0000,
            0xFF00FF00, 0xFF0000FF, 0xFF808080, 0xFFFFFF00, 0xFF00FFFF};
    private static final String[] NAMES = {"白", "黑", "红", "绿", "蓝", "灰", "黄", "青"};

    private int colorIndex = 0;
    private boolean touchMode = false;
    private final ArrayList<PointF> trail = new ArrayList<>();
    private FrameLayout root;
    private TextView hint;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        root = new FrameLayout(this);
        root.setBackgroundColor(COLORS[0]);

        final View touchLayer = new View(this) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onDraw(Canvas c) {
                super.onDraw(c);
                if (!touchMode) return;
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(3));
                paint.setColor(0x66000000);
                for (int i = 1; i < trail.size(); i++) {
                    PointF a = trail.get(i - 1), b2 = trail.get(i);
                    c.drawLine(a.x, a.y, b2.x, b2.y, paint);
                }
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFF2563EB);
                for (PointF p : trail) c.drawCircle(p.x, p.y, dp(5), paint);
            }
        };
        touchLayer.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        touchLayer.setOnTouchListener((v, e) -> {
            if (!touchMode) return false;
            if (e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_MOVE) {
                trail.add(new PointF(e.getX(), e.getY()));
                v.invalidate();
            }
            return true;
        });
        root.addView(touchLayer);

        hint = new TextView(this);
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(12);
        hint.setGravity(Gravity.CENTER);
        hint.setShadowLayer(6, 0, 0, Color.BLACK);
        hint.setPadding(dp(16), dp(10), dp(16), dp(10));
        GradientDrawable hg = new GradientDrawable();
        hg.setColor(0x66000000);
        hg.setCornerRadius(dp(16));
        hint.setBackground(hg);
        hint.setText("点屏幕切换颜色 · 找坏点亮点");
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        hp.topMargin = dp(40);
        root.addView(hint, hp);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(10), dp(10), dp(10), dp(34));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xAA000000);
        bar.setBackground(bg);

        TextView mode = barBtn("触摸测试");
        mode.setOnClickListener(v -> {
            touchMode = !touchMode;
            trail.clear();
            if (touchMode) {
                root.setBackgroundColor(0xFF111827);
                mode.setText("纯色检测");
                hint.setText("在屏幕上划，断线/空白处就是触摸bug");
            } else {
                root.setBackgroundColor(COLORS[colorIndex]);
                mode.setText("触摸测试");
                hint.setText("点屏幕切换颜色 · 找坏点亮点");
            }
            touchLayer.invalidate();
        });
        bar.addView(mode);

        TextView exit = barBtn("退出检测");
        exit.setOnClickListener(v -> finish());
        bar.addView(exit);

        root.addView(bar, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM));

        setContentView(root);

        touchLayer.setOnClickListener(null);
        // 纯色模式切换
        root.setOnClickListener(v -> {
            if (touchMode) return;
            colorIndex = (colorIndex + 1) % COLORS.length;
            root.setBackgroundColor(COLORS[colorIndex]);
            hint.setText("当前：" + NAMES[colorIndex] + " · 换个颜色");
        });
    }

    private TextView barBtn(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(13);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF232B33);
        g.setCornerRadius(dp(18));
        t.setBackground(g);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(104), dp(40));
        p.leftMargin = dp(6);
        p.rightMargin = dp(6);
        t.setLayoutParams(p);
        return t;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
