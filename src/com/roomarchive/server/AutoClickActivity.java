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
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 自动点击器（悬浮宏版）：每个点独立延时，一轮内按各自节奏依次落下，
 * 循环按「轮」执行，可存方案。
 */
public class AutoClickActivity extends Activity {

    private static final String PREF = "ik_autoclick";

    private TextView status;
    private WindowManager wm;
    private boolean panelShown = false;

    private LinearLayout panel, settingsRow;
    private TextView speedText, countText, startBtn, addBtn, gearBtn;
    private View addLayer;

    private final List<View> markers = new ArrayList<>();
    private final List<Point> pts = new ArrayList<>();
    private long hold = 60;      // 每次按下时长
    private int loop = 0;        // 0 = 无限
    private boolean running = false;
    private boolean addMode = false;
    private long clicked = 0;
    private long rounds = 0;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Runnable task;

    static class Point {
        float x, y;
        long delay;   // 相对上一动作的间隔
        Point(float x, float y, long d) { this.x = x; this.y = y; this.delay = d; }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(26), dp(18), dp(24));
        root.setBackground(Tools.bgWithGlow(this));

        TextView title = Tools.pageTitle(this, "自动点击器");
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("悬浮面板 · 多点连点 · 每点独立延时");
        sub.setTextSize(12);
        sub.setTextColor(Tools.subTextColor(this));
        sub.setPadding(0, dp(6), 0, dp(14));
        root.addView(sub);

        status = new TextView(this);
        status.setTextSize(12);
        status.setLineSpacing(dp(4), 1f);
        status.setPadding(dp(14), dp(12), dp(14), dp(12));
        status.setBackground(Tools.liquidGlassCard(this, 16));
        root.addView(status);

        root.addView(btn("① 开启无障碍服务（点按手势靠它）", true, v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))), gap(12));
        root.addView(btn("② 允许悬浮窗", false, v -> {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } else {
                toast("悬浮窗已经允许了");
            }
        }), gap(10));
        root.addView(btn("③ 打开悬浮面板", true, v -> {
            if (!Settings.canDrawOverlays(this)) {
                toast("先允许悬浮窗");
                return;
            }
            if (!panelShown) showPanel();
            else toast("面板已经开着了");
        }), gap(10));

        TextView tip = new TextView(this);
        tip.setText("· 面板上「＋」后点屏幕落点，点可拖动，长按可改间隔/删除\n"
                + "· 「▶」开始，一轮里按每个点自己的延时依次点击\n"
                + "· 面板能拖到边上，退到后台继续点；「✕」停止并收起");
        tip.setTextSize(12);
        tip.setTextColor(Tools.subTextColor(this));
        tip.setLineSpacing(dp(5), 1f);
        tip.setPadding(dp(14), dp(12), dp(14), dp(12));
        tip.setBackground(Tools.liquidGlassCard(this, 16));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dp(16);
        root.addView(tip, tp);

        setContentView(root);
        loadConfig();
    }

    private TextView btn(String text, boolean filled, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(14);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(filled ? Color.WHITE : Tools.accentColor(this));
        t.setBackground(filled ? Tools.pillButton(this, 48, true) : Tools.pillButton(this, 48, false));
        t.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        t.setOnClickListener(l);
        return t;
    }

    private LinearLayout.LayoutParams gap(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(top);
        return p;
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean a = isAccessEnabled();
        boolean o = Settings.canDrawOverlays(this);
        status.setText((a ? "✅ 无障碍已开启" : "❌ 无障碍未开启") + "    " + (o ? "✅ 悬浮窗已允许" : "❌ 悬浮窗未允许")
                + "\n已存 " + pts.size() + " 个点击点 · 长按时长 " + hold + "ms");
        status.setTextColor(a && o ? Tools.textColor(this) : 0xFFB44A4A);
        if (a && !AutoClickService.ready()) {
            // 服务刚被打开时可能还没连上，稍后再刷一次
            ui.postDelayed(this::onResume, 1200);
        }
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
        bg.setColor(0xF2101418);
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), 0x5534D399);
        panel.setBackground(bg);
        panel.setPadding(dp(8), dp(8), dp(8), dp(8));
        Tools.setElevation(panel, 10);

        // 第一行：拖动 + 加 + 启停 + 速度 + 设置 + 关闭
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView drag = new TextView(this);
        drag.setText("⠿");
        drag.setTextColor(0xFF9AD9C6);
        drag.setTextSize(17);
        drag.setPadding(dp(8), 0, dp(8), 0);
        row.addView(drag);

        addBtn = pill("＋", 0xFF1F7A6A);
        addBtn.setOnClickListener(v -> toggleAddMode());
        row.addView(addBtn, box(44, 38));

        startBtn = pill("▶", 0xFF2E9E5B);
        startBtn.setOnClickListener(v -> { if (running) stopRun(true); else startRun(); });
        row.addView(startBtn, box(44, 38));

        speedText = new TextView(this);
        speedText.setTextColor(Color.WHITE);
        speedText.setTextSize(11);
        speedText.setGravity(Gravity.CENTER);
        row.addView(speedText, box(70, 38));

        gearBtn = pill("⚙", 0xFF39424E);
        gearBtn.setOnClickListener(v -> settingsRow.setVisibility(
                settingsRow.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        row.addView(gearBtn, box(40, 38));

        TextView close = pill("✕", 0xFF444C55);
        close.setOnClickListener(v -> hidePanel());
        row.addView(close, box(38, 38));
        panel.addView(row);

        // 第二行：设置（默认折叠）
        settingsRow = new LinearLayout(this);
        settingsRow.setOrientation(LinearLayout.VERTICAL);
        settingsRow.setVisibility(View.GONE);

        LinearLayout r2 = new LinearLayout(this);
        r2.setOrientation(LinearLayout.HORIZONTAL);
        r2.setGravity(Gravity.CENTER_VERTICAL);
        r2.setPadding(0, dp(6), 0, 0);
        r2.addView(step("按住", v -> { hold = hold >= 400 ? 50 : hold + 50; refresh(); saveConfig(); }),
                box(72, 34));
        r2.addView(step("循环", v -> { loop = loop >= 20 ? 0 : loop + 5; refresh(); saveConfig(); }),
                box(72, 34));
        TextView profile = pill("存方案", 0xFF2C3A46);
        profile.setTextSize(11);
        profile.setOnClickListener(v -> saveProfileDialog());
        LinearLayout.LayoutParams pp = box(0, 34);
        pp.weight = 1;
        pp.leftMargin = dp(4);
        r2.addView(profile, pp);
        settingsRow.addView(r2);

        countText = new TextView(this);
        countText.setTextColor(0xFFAFB8C1);
        countText.setTextSize(10);
        countText.setPadding(dp(6), dp(4), 0, 0);
        settingsRow.addView(countText);
        panel.addView(settingsRow);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.RIGHT;
        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        lp.x = dp(8);
        lp.y = sp.getInt("panelY", dp(140));

        final WindowManager.LayoutParams flp = lp;
        drag.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int ox, oy;
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    downX = e.getRawX(); downY = e.getRawY(); ox = flp.x; oy = flp.y;
                    return true;
                } else if (e.getAction() == MotionEvent.ACTION_MOVE) {
                    flp.x = ox - (int) (e.getRawX() - downX);
                    flp.y = oy + (int) (e.getRawY() - downY);
                    try { wm.updateViewLayout(panel, flp); } catch (Throwable ignored) {}
                    return true;
                } else if (e.getAction() == MotionEvent.ACTION_UP) {
                    getSharedPreferences(PREF, MODE_PRIVATE).edit().putInt("panelY", flp.y).apply();
                }
                return false;
            }
        });
        try {
            wm.addView(panel, lp);
        } catch (Throwable t) {
            toast("面板加不上：" + t.getMessage());
            panel = null;
            return;
        }
        panelShown = true;
        refresh();
        redrawMarkers();
    }

    private TextView pill(String text, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(15);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(10));
        t.setBackground(g);
        return t;
    }

    private TextView step(String name, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(name);
        t.setTextColor(0xFF9AD9C6);
        t.setTextSize(11);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF232B33);
        g.setCornerRadius(dp(9));
        t.setBackground(g);
        t.setOnClickListener(l);
        return t;
    }

    private LinearLayout.LayoutParams box(int w, int h) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(w), dp(h));
        p.leftMargin = dp(4);
        return p;
    }

    private void hidePanel() {
        stopRun(false);
        exitAddMode();
        removeMarkers();
        if (wm != null && panel != null) {
            try { wm.removeView(panel); } catch (Throwable ignored) {}
        }
        panel = null;
        panelShown = false;
    }

    private void refresh() {
        if (speedText == null) return;
        long cycle = cycle();
        speedText.setText(cycle > 0 ? (60000 / Math.max(1, cycle)) + "轮/分" : "—");
        if (countText != null) {
            countText.setText("已点 " + clicked + " 次 · " + rounds + " 轮 · 按住 " + hold + "ms · "
                    + (loop == 0 ? "无限循环" : loop + " 轮"));
        }
        if (startBtn != null) startBtn.setText(running ? "■" : "▶");
        if (addBtn != null) addBtn.setText(addMode ? "✓" : "＋");
    }

    private long cycle() {
        long c = 0;
        for (Point p : pts) c += p.delay;
        if (c < 60) c = 60;
        return c;
    }

    // ---------------- 落点 ----------------
    private void toggleAddMode() {
        if (addMode) exitAddMode(); else enterAddMode();
    }

    private void enterAddMode() {
        if (running) stopRun(true);
        addMode = true;
        addLayer = new View(this);
        addLayer.setBackgroundColor(0x33000000);
        addLayer.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_UP) {
                pts.add(new Point(e.getRawX(), e.getRawY(), 120));
                redrawMarkers();
                refresh();
                saveConfig();
                toast("已加第 " + pts.size() + " 个点（默认间隔 120ms，长按可改）");
            }
            return true;
        });
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        try { wm.addView(addLayer, lp); } catch (Throwable t) {
            toast("加不了点：" + t.getMessage());
            addMode = false;
            return;
        }
        refresh();
        toast("点屏幕落点，再点「✓」退出拾取");
    }

    private void exitAddMode() {
        addMode = false;
        if (addLayer != null && wm != null) {
            try { wm.removeView(addLayer); } catch (Throwable ignored) {}
        }
        addLayer = null;
        refresh();
    }

    private void redrawMarkers() {
        removeMarkers();
        if (wm == null || panel == null) return;
        for (int i = 0; i < pts.size(); i++) {
            final int idx = i;
            final Point p = pts.get(i);
            TextView dot = new TextView(this);
            dot.setText(String.valueOf(i + 1));
            dot.setTextColor(Color.WHITE);
            dot.setTextSize(11);
            dot.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(0xD9E04A4A);
            g.setStroke(dp(2), 0xFFFFFFFF);
            dot.setBackground(g);
            dot.setElevation(dp(4));

            WindowManager.LayoutParams lp = markerParams((int) p.x, (int) p.y);

            final WindowManager.LayoutParams dlp = lp;
            dot.setOnTouchListener(new View.OnTouchListener() {
                float dx, dy;
                int ox, oy;
                long downAt;
                @Override
                public boolean onTouch(View v, MotionEvent e) {
                    if (e.getAction() == MotionEvent.ACTION_DOWN) {
                        dx = e.getRawX(); dy = e.getRawY(); ox = dlp.x; oy = dlp.y;
                        downAt = System.currentTimeMillis();
                        return true;
                    } else if (e.getAction() == MotionEvent.ACTION_MOVE) {
                        dlp.x = ox + (int) (e.getRawX() - dx);
                        dlp.y = oy + (int) (e.getRawY() - dy);
                        try { wm.updateViewLayout(v, dlp); } catch (Throwable ignored) {}
                        p.x = dlp.x + dp(17);
                        p.y = dlp.y + dp(17);
                        return true;
                    } else if (e.getAction() == MotionEvent.ACTION_UP) {
                        saveConfig();
                        if (System.currentTimeMillis() - downAt < 400) {
                            v.performClick();
                        }
                    }
                    return false;
                }
            });
            dot.setOnClickListener(v -> editPoint(idx));
            try {
                wm.addView(dot, lp);
                markers.add(dot);
            } catch (Throwable ignored) {}
        }
    }

    private WindowManager.LayoutParams markerParams(int cx, int cy) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                dp(34), dp(34),
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.x = cx - dp(17);
        lp.y = cy - dp(17);
        return lp;
    }

    private void editPoint(int idx) {
        if (idx < 0 || idx >= pts.size()) return;
        Point p = pts.get(idx);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(10), dp(20), 0);
        EditText ed = new EditText(this);
        ed.setInputType(InputType.TYPE_CLASS_NUMBER);
        ed.setText(String.valueOf(p.delay));
        ed.setHint("毫秒，比如 120");
        box.addView(ed);
        new AlertDialog.Builder(this)
                .setTitle("第 " + (idx + 1) + " 个点")
                .setView(box)
                .setPositiveButton("保存间隔", (d, w) -> {
                    try {
                        long v = Long.parseLong(ed.getText().toString().trim());
                        p.delay = Math.max(20, Math.min(5000, v));
                        refresh(); saveConfig();
                    } catch (Throwable ignored) {}
                })
                .setNeutralButton("删除这个点", (d, w) -> {
                    if (idx < pts.size()) pts.remove(idx);
                    redrawMarkers(); refresh(); saveConfig();
                })
                .setNegativeButton("取消", null)
                .show();
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
        if (pts.isEmpty()) { toast("先点「＋」加至少一个点击点"); return; }
        if (!AutoClickService.ready()) { toast("无障碍没连上，去设置开 IK工具箱 的自动点击服务"); return; }
        stopRun(false);
        running = true;
        clicked = 0;
        rounds = 0;
        refresh();
        task = new Runnable() {
            @Override
            public void run() {
                if (!running) return;
                boolean ok = AutoClickService.macro(pts, hold);
                if (ok) {
                    clicked += pts.size();
                    rounds++;
                    if (loop > 0 && rounds >= loop) {
                        stopRun(true);
                        toast("跑完 " + loop + " 轮");
                        return;
                    }
                }
                refresh();
                ui.postDelayed(this, Math.max(60, cycle()));
            }
        };
        ui.post(task);
    }

    private void stopRun(boolean notify) {
        running = false;
        if (task != null) ui.removeCallbacks(task);
        refresh();
        if (notify) toast("已停止，共 " + clicked + " 次");
    }

    // ---------------- 方案 ----------------
    private void saveProfileDialog() {
        EditText ed = new EditText(this);
        ed.setHint("方案名，比如 游戏连点");
        new AlertDialog.Builder(this)
                .setTitle("存方案")
                .setView(ed)
                .setPositiveButton("保存", (d, w) -> {
                    String name = ed.getText().toString().trim();
                    if (name.isEmpty()) name = "方案" + System.currentTimeMillis() % 1000;
                    saveConfig();
                    try {
                        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
                        JSONObject prof = new JSONObject();
                        prof.put("points", ptsJson());
                        prof.put("hold", hold);
                        prof.put("loop", loop);
                        JSONObject all = new JSONObject(sp.getString("profiles", "{}"));
                        all.put(name, prof);
                        sp.edit().putString("profiles", all.toString()).apply();
                        toast("已存：" + name);
                    } catch (Throwable ignored) {}
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private JSONArray ptsJson() {
        JSONArray arr = new JSONArray();
        for (Point p : pts) {
            JSONObject o = new JSONObject();
            try {
                o.put("x", (int) p.x);
                o.put("y", (int) p.y);
                o.put("d", p.delay);
            } catch (Throwable ignored) {}
            arr.put(o);
        }
        return arr;
    }

    private void saveConfig() {
        try {
            SharedPreferences.Editor ed = getSharedPreferences(PREF, MODE_PRIVATE).edit();
            ed.putString("points", ptsJson().toString()).putLong("hold", hold).putInt("loop", loop).apply();
        } catch (Throwable ignored) {}
    }

    private void loadConfig() {
        try {
            SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
            hold = sp.getLong("hold", 60);
            loop = sp.getInt("loop", 0);
            JSONArray arr = new JSONArray(sp.getString("points", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                pts.add(new Point((float) o.getDouble("x"), (float) o.getDouble("y"),
                        o.optLong("d", 120)));
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onBackPressed() {
        if (panelShown) { hidePanel(); return; }
        moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        hidePanel();
        super.onDestroy();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 无障碍服务：一次手势里排好所有点，避免互相打断 */
    public static class AutoClickService extends AccessibilityService {
        private static AutoClickService instance;

        public static boolean ready() { return instance != null; }

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

        static boolean macro(List<Point> pts, long holdMs) {
            if (instance == null || pts.isEmpty()) return false;
            try {
                GestureDescription.Builder b = new GestureDescription.Builder();
                long t = 0;
                final long maxDur = 1000;   // dispatchGesture 单次手势上限
                for (Point p : pts) {
                    Path path = new Path();
                    path.moveTo(p.x, p.y);
                    long start = Math.min(t, maxDur - 1);
                    long dur = Math.min(Math.max(30, holdMs), maxDur - start);
                    if (dur <= 0) break;
                    b.addStroke(new GestureDescription.StrokeDescription(path, start, dur));
                    t += Math.max(20, p.delay);
                }
                return instance.dispatchGesture(b.build(), null, null);
            } catch (Throwable t) {
                return false;
            }
        }
    }
}
