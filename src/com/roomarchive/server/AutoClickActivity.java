package com.roomarchive.server;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** 自动点击器：引导开启无障碍，悬浮窗控制 */
public class AutoClickActivity extends Activity {

    private TextView status;
    private Button toggleBtn;
    private WindowManager wm;
    private View floatView;
    private boolean floating = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(30), dp(20), dp(20));
        root.setBackground(Tools.bgWithGlow(this));

        TextView title = new TextView(this);
        title.setText("自动点击器");
        title.setTextSize(22);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setTextColor(Tools.textColor(this));
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(14);
        status.setTextColor(Tools.subTextColor(this));
        status.setPadding(0, dp(20), 0, dp(20));
        root.addView(status);

        Button openBtn = new Button(this);
        openBtn.setText("开启无障碍服务");
        openBtn.setAllCaps(false);
        openBtn.setTextColor(Color.WHITE);
        openBtn.setBackground(Tools.pillButton(this, 48, true));
        openBtn.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });
        root.addView(openBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

        toggleBtn = new Button(this);
        toggleBtn.setText("显示悬浮窗");
        toggleBtn.setAllCaps(false);
        toggleBtn.setTextColor(Color.WHITE);
        toggleBtn.setBackground(Tools.pillButton(this, 48, true));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        tp.setMargins(0, dp(12), 0, 0);
        toggleBtn.setLayoutParams(tp);
        toggleBtn.setOnClickListener(v -> {
            if (!floating) showFloat();
            else hideFloat();
        });
        root.addView(toggleBtn);

        setContentView(root);
        refresh();
    }

    private void refresh() {
        boolean enabled = isAccessEnabled();
        status.setText(enabled
                ? "✅ 无障碍已开启\n点「显示悬浮窗」后，悬浮窗点一下=在屏幕中心模拟一次点击"
                : "❌ 无障碍未开启\n点上方按钮→找到「IK工具箱」→开启");
    }

    private boolean isAccessEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled != null && enabled.contains(getPackageName());
    }

    private void showFloat() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:" + getPackageName())));
            Toast.makeText(this, "请先允许悬浮窗权限", Toast.LENGTH_SHORT).show();
            return;
        }
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(16), dp(16), dp(16), dp(16));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xCC15403C);
        bg.setCornerRadius(dp(16));
        box.setBackground(bg);
        TextView t = new TextView(this);
        t.setText("👆 点我点击");
        t.setTextColor(Color.WHITE);
        t.setTextSize(13);
        box.addView(t);
        box.setOnClickListener(v -> {
            AutoClickService.clickCenter(this);
        });

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;
        wm.addView(box, lp);
        floatView = box;
        floating = true;
        toggleBtn.setText("隐藏悬浮窗");
    }

    private void hideFloat() {
        if (wm != null && floatView != null) {
            wm.removeView(floatView);
            floatView = null;
        }
        floating = false;
        toggleBtn.setText("显示悬浮窗");
    }

    @Override protected void onResume() { super.onResume(); refresh(); }
    @Override protected void onDestroy() { hideFloat(); super.onDestroy(); }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    /** 无障碍服务：模拟手势点击 */
    public static class AutoClickService extends AccessibilityService {
        private static AutoClickService instance;

        @Override public void onAccessibilityEvent(AccessibilityEvent e) {}
        @Override public void onInterrupt() {}

        @Override public void onServiceConnected() {
            super.onServiceConnected();
            instance = this;
        }

        public static void clickCenter(android.content.Context ctx) {
            if (instance == null) {
                Toast.makeText(ctx, "先开启无障碍", Toast.LENGTH_SHORT).show();
                return;
            }
            android.util.DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
            Path path = new Path();
            path.moveTo(dm.widthPixels / 2f, dm.heightPixels / 2f);
            GestureDescription.Builder b = new GestureDescription.Builder();
            b.addStroke(new GestureDescription.StrokeDescription(path, 0, 50));
            instance.dispatchGesture(b.build(), null, null);
            Toast.makeText(ctx, "已点击屏幕中心", Toast.LENGTH_SHORT).show();
        }
    }
}
