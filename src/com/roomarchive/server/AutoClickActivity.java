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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 自动点击器（悬浮宏版）
 * 修复点：拾取层挡住面板 / 圆点拖完误弹窗 / 状态刷新递归 / 手势超时限 / 长延时点被丢
 */
public class AutoClickActivity extends Activity {

    private static final String PREF = "ik_autoclick";
    private static final int MAX_STROKES = 8;        // 单次手势最多手指/笔画
    private static final long SEG_MAX = 850;         // 单次手势最长 850ms，留余量

    private TextView status;
    private WindowManager wm;
    private boolean panelShown = false;

    private LinearLayout panel, settingsRow;
    private TextView speedText, countText, startBtn, addBtn;
    private WindowManager.LayoutParams panelLp;
    private View addLayer;

    private final List<View> markers = new ArrayList<>();
    private final List<Point> pts = new ArrayList<>();
    private long hold = 60;
    private int loop = 0;
    private boolean running = false;
    private boolean addMode = false;
    private long clicked = 0;
    private long rounds = 0;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Runnable task;
    private int retryCount = 0;

    static class Point {
        float x, y;
        long delay;
        Point(float x, float y, long d) { this.x = x; this.y = y; this.delay = d; }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(26), dp(18), dp(24));
        root.setBackground(Tools.bgWithGlow(this));

        root.addView(Tools.pageTitle(this, "自动点击器"));

        TextView sub = new TextView(this);
        sub.setText("悬浮面板 · 多点宏 · 每点独立延时");
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

        root.addView(btn("① 开启无障碍服务（点击靠它下发）", true, v ->
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
        tip.setText("· 面板「＋」进入拾取，点屏幕落点；再点「✓」退出拾取\n"
                + "· 圆点拖动改位置，点一下圆点改这个点的间隔或删除\n"
                + "· 「▶」开始，面板可折叠/拖动，退后台继续点；「✕」停止收起");
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
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT) {{ topMargin = dp(top); }};
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void refreshStatus() {
        boolean a = isAccessEnabled();
        boolean o = Settings.canDrawOverlays(this);
        status.setText((a ? "✅ 无障碍已开启" : "❌ 无障碍未开启")
                + "    " + (o ? "✅ 悬浮窗已允许" : "❌ 悬浮窗未允许")
                + "\n" + (a && !AutoClickService.ready()
                ? "服务还没连上，关掉再重开一次无障碍开关"
                : "已存 " + pts.size() + " 个点 · 按住 " + hold + "ms")
                + " · " + (loop == 0 ? "无限循环" : "循环 " + loop + " 轮"));
        status.setTextColor(a && o ? Tools.textColor(this) : 0xFFB44A4A);
        // 只重试 3 次，绝不递归自己
        if (a && !AutoClickService.ready() && retryCount < 3) {
            retryCount++;
            ui.postDelayed(this::refreshStatus, 1000);
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
        if (panelShown) return;
        if (wm == null) wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF2101418);
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), 0x5534D399);
        panel.setBackground(bg);
        panel.setPadding(dp(8), dp(8), dp(8), dp(8));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView drag = new TextView(this);
        drag.setText("⠿");
        drag.setTextColor(0xFF9AD9C6);
        drag.setTextSize(17);
        drag.setPadding(dp(8), dp(4), dp(8), dp(4));
        row.addView(drag);

        addBtn = pill("＋", 0xFF1F7A6A);
        addBtn.setOnClickListener(v -> toggleAddMode());
        row.addView(addBtn, box(44, 38));

        startBtn = pill("▶", 0xFF2E9E5B);
        startBtn.setOnClickListener(v -> { if (running) stopRun(true); else startRun(); });
        row.addView(startBtn, box(44, 38));

        speedText = new TextView(this);
        speedText.setTextColor(Color.WHITE);
        speedText.setTextSize(10);
        speedText.setGravity(Gravity.CENTER);
        speedText.setText("—");
        row.addView(speedText, box(64, 38));

        TextView gear = pill("⚙", 0xFF39424E);
        gear.setTextSize(14);
        gear.setOnClickListener(v -> settingsRow.setVisibility(
                settingsRow.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        row.addView(gear, box(40, 38));

        TextView close = pill("✕", 0xFF444C55);
        close.setOnClickListener(v -> hidePanel());
        row.addView(close, box(38, 38));
        panel.addView(row);

        settingsRow = new LinearLayout(this);
        settingsRow.setOrientation(LinearLayout.VERTICAL);
        settingsRow.setVisibility(View.GONE);

        LinearLayout r2 = new LinearLayout(this);
        r2.setOrientation(LinearLayout.HORIZONTAL);
        r2.setGravity(Gravity.CENTER_VERTICAL);
        r2.setPadding(0, dp(6), 0, 0);
        r2.addView(step("按住 " + hold + "ms", v -> {
            hold = hold >= 400 ? 50 : hold + 50;
            rebuildSettings(r2);
        }), box(0, 34, 1));
        r2.addView(step(loop == 0 ? "循环 ∞" : "循环 " + loop, v -> {
            loop = loop >= 20 ? 0 : loop + 5;
            rebuildSettings(r2);
        }), box(0, 34, 1));
        r2.addView(step("清空点", v -> {
            new AlertDialog.Builder(this).setTitle("清空全部点击点")
                    .setPositiveButton("清空", (d, w) -> {
                        pts.clear(); redrawMarkers(); refresh(); saveConfig();
                    }).setNegativeButton("取消", null).show();
        }), box(0, 34, 1));
        settingsRow.addView(r2);

        LinearLayout r3 = new LinearLayout(this);
        r3.setOrientation(LinearLayout.HORIZONTAL);
        r3.setGravity(Gravity.CENTER_VERTICAL);
        TextView profile = pill("存方案", 0xFF2C3A46);
        profile.setTextSize(11);
        profile.setOnClickListener(v -> saveProfileDialog());
        r3.addView(profile, box(0, 34, 1));
        TextView loadProf = pill("载入", 0xFF2C3A46);
        loadProf.setTextSize(11);
        loadProf.setOnClickListener(v -> loadProfileDialog());
        r3.addView(loadProf, box(0, 34, 1));
        settingsRow.addView(r3);

        countText = new TextView(this);
        countText.setTextColor(0xFFAFB8C1);
        countText.setTextSize(10);
        countText.setPadding(dp(6), dp(4), 0, 0);
        settingsRow.addView(countText);
        panel.addView(settingsRow);

        panelLp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        panelLp.gravity = Gravity.TOP | Gravity.RIGHT;
        panelLp.x = dp(8);
        panelLp.y = getSharedPreferences(PREF, MODE_PRIVATE).getInt("panelY", dp(140));

        final WindowManager.LayoutParams flp = panelLp;
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
                } else if (e.getAction() == MotionEvent.ACTION_UP
                        || e.getAction() == MotionEvent.ACTION_CANCEL) {
                    getSharedPreferences(PREF, MODE_PRIVATE).edit().putInt("panelY", flp.y).apply();
                    return true;
                }
                return false;
            }
        });
        try {
            wm.addView(panel, panelLp);
        } catch (Throwable t) {
            toast("面板加不上：" + t.getMessage());
            panel = null;
            return;
        }
        panelShown = true;
        refresh();
        redrawMarkers();
    }

    private void rebuildSettings(LinearLayout r2) {
        r2.removeAllViews();
        r2.addView(step("按住 " + hold + "ms", v -> {
            hold = hold >= 400 ? 50 : hold + 50;
            rebuildSettings(r2);
        }), box(0, 34, 1));
        r2.addView(step(loop == 0 ? "循环 ∞" : "循环 " + loop, v -> {
            loop = loop >= 20 ? 0 : loop + 5;
            rebuildSettings(r2);
        }), box(0, 34, 1));
        r2.addView(step("清空点", v -> {
            new AlertDialog.Builder(this).setTitle("清空全部点击点")
                    .setPositiveButton("清空", (d, w) -> {
                        pts.clear(); redrawMarkers(); refresh(); saveConfig();
                    }).setNegativeButton("取消", null).show();
        }), box(0, 34, 1));
        refresh();
        saveConfig();
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
        t.setSingleLine(true);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF232B33);
        g.setCornerRadius(dp(9));
        t.setBackground(g);
        t.setOnClickListener(l);
        return t;
    }

    private LinearLayout.LayoutParams box(int w, int h) {
        return box(w, h, 0);
    }

    private LinearLayout.LayoutParams box(int w, int h, float weight) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(w), dp(h));
        p.weight = weight;
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
        long cyc = cycle();
        speedText.setText((60000 / Math.max(60, cyc)) + "轮/分");
        if (countText != null) {
            countText.setText("已点 " + clicked + " · " + rounds + " 轮 · 一轮 " + cyc + "ms");
        }
        if (startBtn != null) startBtn.setText(running ? "■" : "▶");
        if (addBtn != null) addBtn.setText(addMode ? "✓" : "＋");
    }

    private long cycle() {
        long c = 0;
        for (Point p : pts) c += Math.max(20, p.delay);
        return Math.max(60, c);
    }

    // ---------------- 落点 ----------------
    private void toggleAddMode() {
        if (addMode) exitAddMode(); else enterAddMode();
    }

    private void enterAddMode() {
        if (running) stopRun(true);
        if (wm == null) wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        addMode = true;
        addLayer = new View(this);
        addLayer.setBackgroundColor(0x33000000);
        addLayer.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_UP) {
                if (pts.size() >= 10) { toast("最多 10 个点，再多手势不稳"); return true; }
                pts.add(new Point(e.getRawX(), e.getRawY(), 120));
                redrawMarkers();
                refresh();
                saveConfig();
                toast("已加第 " + pts.size() + " 个点");
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
        try {
            // 关键修复：先把面板摘掉，按「层 → 面板 → 点」顺序重挂，面板才点得到
            if (panel != null && panelShown) {
                try { wm.removeView(panel); } catch (Throwable ignored) {}
            }
            wm.addView(addLayer, lp);
            if (panel != null && panelShown) wm.addView(panel, panelLp);
            removeMarkers();
            redrawMarkers();
        } catch (Throwable t) {
            toast("加不了点：" + t.getMessage());
            addMode = false;
            if (panel != null && panelShown) {
                try { wm.addView(panel, panelLp); } catch (Throwable ignored) {}
            }
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
        try { if (panel != null) wm.updateViewLayout(panel, panelLp); } catch (Throwable ignored) {}
        refresh();
    }

    private void redrawMarkers() {
        removeMarkers();
        if (wm == null || !panelShown || panel == null) return;
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

            WindowManager.LayoutParams lp = markerParams((int) p.x, (int) p.y);
            final WindowManager.LayoutParams dlp = lp;

            dot.setOnTouchListener(new View.OnTouchListener() {
                float dx, dy;
                int ox, oy;
                boolean moved;
                @Override
                public boolean onTouch(View v, MotionEvent e) {
                    if (e.getAction() == MotionEvent.ACTION_DOWN) {
                        dx = e.getRawX(); dy = e.getRawY(); ox = dlp.x; oy = dlp.y;
                        moved = false;
                        return true;
                    } else if (e.getAction() == MotionEvent.ACTION_MOVE) {
                        int nx = ox + (int) (e.getRawX() - dx);
                        int ny = oy + (int) (e.getRawY() - dy);
                        if (Math.abs(nx - ox) > dp(6) || Math.abs(ny - oy) > dp(6)) moved = true;
                        dlp.x = nx;
                        dlp.y = ny;
                        try { wm.updateViewLayout(v, dlp); } catch (Throwable ignored) {}
                        p.x = dlp.x + dp(17);
                        p.y = dlp.y + dp(17);
                        return true;
                    } else if (e.getAction() == MotionEvent.ACTION_UP
                            || e.getAction() == MotionEvent.ACTION_CANCEL) {
                        saveConfig();
                        if (!moved && e.getAction() == MotionEvent.ACTION_UP) editPoint(idx);
                        return true;   // 吞掉 UP，避免又触发一次 click
                    }
                    return false;
                }
            });
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
        lp.x = Math.max(0, cx - dp(17));
        lp.y = Math.max(dp(24), cy - dp(17));   // 别跑到状态栏里，否则点不到
        return lp;
    }

    private void editPoint(int idx) {
        if (idx < 0 || idx >= pts.size()) return;
        final Point p = pts.get(idx);
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
        if (addMode) exitAddMode();
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
                if (!AutoClickService.ready()) {
                    stopRun(false);
                    toast("无障碍连接断了，已停止");
                    return;
                }
                // 手势分片下发：单次 ≤850ms、≤8 指，长延时也不会丢点
                List<Object[]> segs = AutoClickService.buildSegments(pts, hold);
                boolean any = false;
                for (final Object[] seg : segs) {
                    final long off = (Long) seg[1];
                    final GestureDescription gd = (GestureDescription) seg[0];
                    ui.postDelayed(() -> {
                        if (!running) return;
                        AutoClickService.dispatch(gd);
                    }, off);
                    any = true;
                }
                if (!any) { stopRun(false); toast("点位参数异常，已停止"); return; }
                clicked += pts.size();
                rounds++;
                if (loop > 0 && rounds >= loop) {
                    stopRun(true);
                    toast("跑完 " + loop + " 轮");
                    return;
                }
                refresh();
                // 下一轮至少等本轮手势全部结束
                long next = Math.max(cycle(), lastOffset(segs) + hold + 40);
                ui.postDelayed(this, next);
            }
        };
        ui.post(task);
    }

    private long lastOffset(List<Object[]> segs) {
        long o = 0;
        for (Object[] s : segs) o = Math.max(o, (Long) s[1]);
        return o;
    }

    private void stopRun(boolean notify) {
        boolean was = running;
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

    private void loadProfileDialog() {
        try {
            JSONObject all = new JSONObject(getSharedPreferences(PREF, MODE_PRIVATE)
                    .getString("profiles", "{}"));
            if (all.length() == 0) { toast("还没存过方案"); return; }
            List<String> names = new ArrayList<>();
            for (java.util.Iterator<String> it = all.keys(); it.hasNext(); ) names.add(it.next());
            String[] arr = names.toArray(new String[0]);
            new AlertDialog.Builder(this)
                    .setTitle("载入方案")
                    .setItems(arr, (d, which) -> {
                        try {
                            JSONObject prof = all.getJSONObject(arr[which]);
                            JSONArray a = prof.getJSONArray("points");
                            pts.clear();
                            for (int i = 0; i < a.length(); i++) {
                                JSONObject o = a.getJSONObject(i);
                                pts.add(new Point((float) o.getDouble("x"),
                                        (float) o.getDouble("y"), o.optLong("d", 120)));
                            }
                            hold = prof.optLong("hold", 60);
                            loop = prof.optInt("loop", 0);
                            redrawMarkers(); refresh(); saveConfig();
                            toast("已载入 " + arr[which]);
                        } catch (Throwable ignored) {}
                    })
                    .show();
        } catch (Throwable ignored) {}
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
            getSharedPreferences(PREF, MODE_PRIVATE).edit()
                    .putString("points", ptsJson().toString())
                    .putLong("hold", hold).putInt("loop", loop).apply();
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

    /** 无障碍服务：分片手势，避免超时/超指被系统丢弃 */
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

        static boolean dispatch(GestureDescription gd) {
            if (instance == null) return false;
            try { return instance.dispatchGesture(gd, null, null); }
            catch (Throwable t) { return false; }
        }

        /** 返回 [{GestureDescription, 绝对起始偏移}]，按 850ms/8 指切片 */
        static List<Object[]> buildSegments(List<Point> pts, long holdMs) {
            List<Object[]> out = new ArrayList<>();
            List<GestureDescription.StrokeDescription> cur = new ArrayList<>();
            long curStart = 0;
            long t = 0;
            long h = Math.max(30, Math.min(400, holdMs));
            for (Point p : pts) {
                long rel = t - curStart;
                boolean overflow = (!cur.isEmpty() && (cur.size() >= AutoClickActivity.MAX_STROKES
                        || rel + h > AutoClickActivity.SEG_MAX));
                if (overflow) {
                    out.add(new Object[]{build(cur), curStart});
                    cur = new ArrayList<>();
                    curStart = t;
                    rel = 0;
                }
                Path path = new Path();
                path.moveTo(p.x, p.y);
                cur.add(new GestureDescription.StrokeDescription(path, Math.max(0, rel), h));
                t += Math.max(20, p.delay);
            }
            if (!cur.isEmpty()) out.add(new Object[]{build(cur), curStart});
            return out;
        }

        private static GestureDescription build(List<GestureDescription.StrokeDescription> strokes) {
            GestureDescription.Builder b = new GestureDescription.Builder();
            for (GestureDescription.StrokeDescription s : strokes) b.addStroke(s);
            return b.build();
        }
    }
}
