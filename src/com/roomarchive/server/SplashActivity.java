package com.roomarchive.server;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 开屏品牌页：液态玻璃 LOGO + 名称，短暂停留后进入主界面（公告弹窗在 MainActivity 弹出） */
public class SplashActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(0xFF0A0F1E);
            getWindow().getDecorView().setSystemUiVisibility(
                    getWindow().getDecorView().getSystemUiVisibility() & ~android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        buildUi();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
                finish();
            }
        }, 1400);
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(18, 26, 48), Color.rgb(7, 9, 18)});
        root.setBackground(bg);
        GlowView glow = new GlowView(this);
        root.addView(glow, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        col.setLayoutParams(cp);
        root.addView(col);

        TextView icon = new TextView(this);
        icon.setText("IK");
        icon.setTextSize(38);
        icon.setTypeface(Typeface.DEFAULT_BOLD);
        icon.setTextColor(Color.WHITE);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(logoGlass());
        col.addView(icon, new LinearLayout.LayoutParams(dp(104), dp(104)));

        col.addView(spacer(22));

        TextView name = new TextView(this);
        name.setText("IK工具箱");
        name.setTextSize(27);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(Color.WHITE);
        name.setGravity(Gravity.CENTER);
        col.addView(name);

        col.addView(spacer(4));

        TextView ver = new TextView(this);
        ver.setText("26 合 1 实用工具 · 永久免费无广告");
        ver.setTextSize(12);
        ver.setTextColor(Color.rgb(160, 170, 190));
        ver.setGravity(Gravity.CENTER);
        col.addView(ver);

        setContentView(root);
    }

    private android.graphics.drawable.Drawable logoGlass() {
        int r = dp(52);
        GradientDrawable base = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(62, 110, 228), Color.rgb(34, 64, 168)});
        base.setCornerRadius(r);
        GradientDrawable top = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xCCFFFFFF, 0x00FFFFFF});
        top.setCornerRadius(r);
        GradientDrawable rim = new GradientDrawable();
        rim.setColor(0x00000000);
        rim.setCornerRadius(r);
        rim.setStroke(dp(2), 0x99FFFFFF);
        return new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{base, top, rim});
    }

    /** 背景光斑：三个径向渐变光球，营造液态玻璃氛围 */
    class GlowView extends View {
        private final Paint p1 = new Paint();
        private final Paint p2 = new Paint();
        private final Paint p3 = new Paint();

        GlowView(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas cv) {
            int w = getWidth(), h = getHeight();
            if (p1.getShader() == null) {
                p1.setShader(new RadialGradient(w * 0.18f, h * 0.20f, dp(180),
                        Color.argb(120, 96, 165, 250), 0x00000000, Shader.TileMode.CLAMP));
                p2.setShader(new RadialGradient(w * 0.86f, h * 0.34f, dp(200),
                        Color.argb(100, 139, 92, 246), 0x00000000, Shader.TileMode.CLAMP));
                p3.setShader(new RadialGradient(w * 0.55f, h * 0.95f, dp(170),
                        Color.argb(80, 45, 212, 191), 0x00000000, Shader.TileMode.CLAMP));
            }
            cv.drawRect(0, 0, w, h, p1);
            cv.drawRect(0, 0, w, h, p2);
            cv.drawRect(0, 0, w, h, p3);
        }
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
