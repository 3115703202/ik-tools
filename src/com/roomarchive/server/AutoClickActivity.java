package com.roomarchive.server;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 自动点击器（悬浮窗版）：加点击点 → 拖到目标位置 → 设间隔 → 循环点击。
 * 点击靠无障碍 dispatchGesture 完成，需要 canPerformGestures。
 */
public class AutoClickActivity extends Activity {

    private static final String PREF = "ik_autoclick";
    private TextView status;
    private WindowManager wm;
    private boolean panelShown = false;

    private LinearLayout panel;
    private TextView intervalText, loopText, clickCountText;
    private Button startBtn;
    private View addLayer;

    private final List<View> markers = new ArrayList<>();
    private final List<float[]> pts = new ArrayList<>();
    private int interval = 120;   // 每点间隔 ms
    private int loop = 0;         // 0=无限
    private boolean running = false;
    private boolean addMode = false;
    private int clicked = 0;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Runnable task;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(20));
        root.setBackground(Tools.bgWithGlow(this));

        TextView title = new TextView(this);
        title.setText("自动点击器");
        title.setTextSize(22);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setTextColor(Tools.textColor(this));
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(13);
        status.setTextColor(Tools.subTextColor(this));
        status.setPadding(0, dp(14), 0, dp(14));
        status.setLineSpacing(dp(4), 1f);
        root.addView(status);

        Button a11y = new Button(this);
        a11y.setText("① 开启无障碍服务");
        a11y.setAllCaps(false);
        a11y.setTextColor(Color.WHITE);
        a11y.setBackground(Tools.pillButton(this, 48, true));
        a11y.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(a11y, lpMatch(48));

        Button overlay = new Button(this);
        overlay.setText("② 允许悬浮窗");
        overlay.setAllCaps(false);
        overlay.setTextColor(Color.WHITE);
        overlay.setBackground(Tools.pillButton(this, 48, false));
        LinearLayout.LayoutParams op = lpMatch(48);
        op.topMargin = dp(10);
        overlay.setLayoutParams(op);
        overlay.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } else {
                Toast.makeText(this, "悬浮窗权限已允许", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(overlay);

        Button show = new Button(this);
        show.setText("③ 打开悬浮控制面板");
        show.setAllCaps(false);
        show.setTextColor(Color.WHITE);
        show.setBackground(Tools.pillButton(this, 48, true));
        LinearLayout.LayoutParams sp = lpMatch(48);
        sp.topMargin = dp(10);
        show.setLayoutParams(sp);
        show.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                overlay.performClick();
                Toast.makeText(this, "先允许悬浮窗", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!panelShown) showPanel();
            else Toast.makeText(this, "面板已经开着了", Toast.LENGTH_SHORT).show();
        });
        root.addView(show);

        TextView tip = new TextView(this);
        tip.setText("用法：悬浮面板上点「＋」→ 屏幕上点一下落一个点击点\n"
                + "点住圆点拖到要点的位置，长按圆点删除\n"
                + "「▶」开始循环点，「■」停止；间隔点一下可加减 20ms");
        tip.setTextSize(12);
        tip.setTextColor(Tools.subTextColor(this));
        tip.setPadding(0, dp(18), 0, 0);
        root.addView(tip);

        setContentView(root);
        loadConfig();
    }

    private LinearLayout.LayoutParams lpMatch(int h) {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(h));
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean a = isAccessEnabled();
        boolean o = Settings.canDrawOverlays(this);
        status.setText((a ? "✅ 无障碍已开启" : "❌ 无障碍未开启") + "    " + (o ? "✅ 悬浮窗已允许" : "❌ 悬浮窗未允许")
                + "\n无障碍：设置 → 无障碍 → 已下载的应用 → IK工具箱 → 开启\n"
                + "已存点击点：" + pts.size() + " 个，间隔 " + interval + "ms");
        status.setTextColor(a && o ? Tools.textColor(this) : 0xFFB44A4A);
    }

    private boolean isAccessEnabled() {
        try {
            String e = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return e != null && e.contains(getPackageName() + "/" + AutoClickService.class.getName());
        } catch (Throwable t) {
            return false;
        }
    }

    // ---------------- 悬浮面板 ----------------
    private void showPanel() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xEE101820);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), 0x5534D399);
        panel.setBackground(bg);
        panel.setPadding(dp(10), dp(8), dp(10), dp(8));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);

        TextView drag = new TextView(this);
        drag.setText("⠿");
        drag.setTextColor(0xFF9AD9C6);
        drag.setTextSize(18);
        drag.setPadding(dp(6), 0, dp(10), 0);
        row1.addView(drag);

        Button addBtn = new Button(this);
        addBtn.setText("＋");
        addBtn.setAllCaps(false);
        addBtn.setTextColor(Color.WHITE);
        addBtn.setBackground(roundBg(0xFF1F7A6A));
        addBtn.setOnClickListener(v -> toggleAddMode());
        row1.addView(addBtn, new LinearLayout.LayoutParams(dp(46), dp(40)));

        intervalText = new TextView(this);
        intervalText.setTextColor(Color.WHITE);
        intervalText.setTextSize(12);
        intervalText.setGravity(Gravity.CENTER);
        intervalText.setOnClickListener(v -> {
            interval = interval >= 400 ? 60 : interval + 20;
            refreshPanelText();
            saveConfig();
        });
        row1.addView(intervalText, new LinearLayout.LayoutParams(dp(58), dp(40)));

        startBtn = new Button(this);
        startBtn.setText("▶");
        startBtn.setAllCaps(false);
        startBtn.setTextColor(Color.WHITE);
        startBtn.setBackground(roundBg(0xFF2E9E5B));
        startBtn.setOnClickListener(v -> {
            if (running) stopRun(); else startRun();
        });
        row1.addView(startBtn, new LinearLayout.LayoutParams(dp(46), dp(40)));

        Button close = new Button(this);
        close.setText("✕");
        close.setAllCaps(false);
        close.setTextColor(Color.WHITE);
        close.setBackground(roundBg(0xFF444C55));
        close.setOnClickListener(v -> hidePanel());
        row1.addView(close, new LinearLayout.LayoutParams(dp(40), dp(40)));
        panel.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER_VERTICAL);
        loopText = new TextView(this);
        loopText.setTextColor(0xFF9AD9C6);
        loopText.setTextSize(11);
        loopText.setPadding(0, dp(2), dp(10), 0);
        loopText.setOnClickListener(v -> {
            loop = loop >= 10 ? 0 : loop + 5;
            refreshPanelText();
        });
        row2.addView(loopText);
        clickCountText = new TextView(this);
        clickCountText.setTextColor(0xFFAFB8C1);
        clickCountText.setTextSize(11);
        row2.addView(clickCountText);
        panel.addView(row2);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.RIGHT;
        lp.x = dp(8);
        lp.y = dp(120);
        wm.addView(panel, lp);

        // 长按表头可拖动整个面板
        final WindowManager.LayoutParams flp = lp;
        drag.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int ox, oy;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    downX = e.getRawX();
                    downY = e.getRawY();
                    ox = flp.x;
                    oy = flp.y;
                    return true;
                } else if (e.getAction() == MotionEvent.ACTION_MOVE) {
                    flp.x = ox - (int) (e.getRawX() - downX);
                    flp.y = oy + (int) (e.getRawY() - downY);
                    try { wm.updateViewLayout(panel, flp); } catch (Throwable ignored) {}
                    return true;
                }
                return false;
            }
        });

        panelShown = true;
        refreshPanelText();
        redrawMarkers();
    }

    private void hidePanel() {
        stopRun();
        exitAddMode();
        removeMarkers();
        if (wm != null && panel != null) {
            try { wm.removeView(panel); } catch (Throwable ignored) {}
        }
        panel = null;
        panelShown = false;
    }

    private void refreshPanelText() {
        if (intervalText != null) intervalText.setText(interval + "ms");
        if (loopText != null) loopText.setText(loop == 0 ? "循环:无限" : "循环:" + loop + "轮");
        if (clickCountText != null) clickCountText.setText("已点 " + clicked + " 次");
        if (startBtn != null) startBtn.setText(running ? "■" : "▶");
    }

    private GradientDrawable roundBg(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(10));
        return g;
    }

    // ---------------- 点击点 ----------------
    private void toggleAddMode() {
        if (addMode) exitAddMode(); else enterAddMode();
    }

    private void enterAddMode() {
        addMode = true;
        addLayer = new View(this);
        addLayer.setBackgroundColor(0x22000000);
        addLayer.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_UP) {
                    pts.add(new float[]{e.getRawX(), e.getRawY()});
                    redrawMarkers();
                    saveConfig();
                    Toast.makeText(AutoClickActivity.this,
                            "已添加第 " + pts.size() + " 个点击点，再点屏幕继续加", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        try { wm.addView(addLayer, lp); } catch (Throwable t) {
            Toast.makeText(this, "加不了点：" + t.getMessage(), Toast.LENGTH_LONG).show();
            addMode = false;
        }
        Toast.makeText(this, "点屏幕任意位置落下点击点，点「＋」退出", Toast.LENGTH_LONG).show();
    }

    private void exitAddMode() {
        addMode = false;
        if (addLayer != null && wm != null) {
            try { wm.removeView(addLayer); } catch (Throwable ignored) {}
        }
        addLayer = null;
        if (panel != null) refreshPanelText();
    }

    private void redrawMarkers() {
        removeMarkers();
        if (wm == null) return;
        for (int i = 0; i < pts.size(); i++) {
            final int idx = i;
            final float[] p = pts.get(i);
            TextView dot = new TextView(this);
            dot.setText(String.valueOf(i + 1));
            dot.setTextColor(Color.WHITE);
            dot.setTextSize(12);
            dot.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(0xD9E04A4A);
            g.setStroke(dp(2), 0xFFFFFFFF);
            dot.setBackground(g);

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    dp(34), dp(34),
                    Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.LEFT;
            lp.x = (int) (p[0] - dp(17));
            lp.y = (int) (p[1] - dp(17));

            final WindowManager.LayoutParams dlp = lp;
            dot.setOnTouchListener(new View.OnTouchListener() {
                float dx, dy;
                int ox, oy;

                @Override
                public boolean onTouch(View v, MotionEvent e) {
                    if (e.getAction() == MotionEvent.ACTION_DOWN) {
                        dx = e.getRawX();
                        dy = e.getRawY();
                        ox = dlp.x;
                        oy = dlp.y;
                        return true;
                    } else if (e.getAction() == MotionEvent.ACTION_MOVE) {
                        dlp.x = ox + (int) (e.getRawX() - dx);
                        dlp.y = oy + (int) (e.getRawY() - dy);
                        try { wm.updateViewLayout(v, dlp); } catch (Throwable ignored) {}
                        p[0] = dlp.x + dp(17);
                        p[1] = dlp.y + dp(17);
                        return true;
                    }
                    return false;
                }
            });
            dot.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle("删除点击点")
                        .setMessage("删除第 " + (idx + 1) + " 个点击点？")
                        .setPositiveButton("删除", (d, w) -> {
                            if (idx < pts.size()) pts.remove(idx);
                            redrawMarkers();
                            saveConfig();
                        })
                        .setNegativeButton("取消", null).show();
                return true;
            });
            try {
                wm.addView(dot, lp);
                markers.add(dot);
            } catch (Throwable ignored) {}
        }
    }

    private void removeMarkers() {
        if (wm == null) return;
        for (View v : markers) {
            try { wm.removeView(v); } catch (Throwable ignored) {}
        }
        markers.clear();
    }

    // ---------------- 运行 ----------------
    private void startRun() {
        if (pts.isEmpty()) {
            Toast.makeText(this, "先点「＋」添加至少一个点击点", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!AutoClickService.ready()) {
            Toast.makeText(this, "无障碍没开，去设置里开 IK工具箱 的自动点击服务", Toast.LENGTH_LONG).show();
            return;
        }
        running = true;
        clicked = 0;
        refreshPanelText();
        task = new Runnable() {
            int index = 0;
            int round = 0;

            @Override
            public void run() {
                if (!running || pts.isEmpty()) return;
                float[] p = pts.get(index % pts.size());
                AutoClickService.tap(p[0], p[1], 40);
                clicked++;
                index++;
                if (index % pts.size() == 0) {
                    round++;
                    if (loop > 0 && round >= loop) {
                        stopRun();
                        Toast.makeText(AutoClickActivity.this, "已跑完 " + loop + " 轮", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                refreshPanelText();
                ui.postDelayed(this, interval);
            }
        };
        ui.postDelayed(task, interval);
    }

    private void stopRun() {
        running = false;
        if (task != null) ui.removeCallbacks(task);
        refreshPanelText();
    }

    // ---------------- 持久化 ----------------
    private void saveConfig() {
        try {
            JSONArray arr = new JSONArray();
            for (float[] p : pts) {
                JSONObject o = new JSONObject();
                o.put("x", (int) p[0]);
                o.put("y", (int) p[1]);
                arr.put(o);
            }
            SharedPreferences.Editor ed = getSharedPreferences(PREF, MODE_PRIVATE).edit();
            ed.putString("points", arr.toString()).putInt("interval", interval).putInt("loop", loop).apply();
        } catch (Throwable ignored) {}
    }

    private void loadConfig() {
        try {
            SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
            interval = sp.getInt("interval", 120);
            loop = sp.getInt("loop", 0);
            String s = sp.getString("points", "[]");
            JSONArray arr = new JSONArray(s);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                pts.add(new float[]{(float) o.getDouble("x"), (float) o.getDouble("y")});
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onBackPressed() {
        // 退到后台而不是销毁，悬浮面板继续留在屏幕上
        moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        hidePanel();
        super.onDestroy();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 无障碍服务：真正的手势点击 */
    public static class AutoClickService extends AccessibilityService {
        private static AutoClickService instance;

        public static boolean ready() {
            return instance != null;
        }

        @Override
        public void onServiceConnected() {
            super.onServiceConnected();
            instance = this;
        }

        @Override
        public void onAccessibilityEvent(AccessibilityEvent e) {}

        @Override
        public void onInterrupt() {}

        @Override
        public void onDestroy() {
            instance = null;
            super.onDestroy();
        }

        static void tap(float x, float y, long ms) {
            if (instance == null) return;
            try {
                Path p = new Path();
                p.moveTo(x, y);
                GestureDescription.Builder b = new GestureDescription.Builder();
                b.addStroke(new GestureDescription.StrokeDescription(p, 0, Math.max(30, ms)));
                instance.dispatchGesture(b.build(), null, null);
            } catch (Throwable ignored) {}
        }
    }
}
