package com.roomarchive.server;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/** 金属探测：磁力计测场强，声音频率 + 震动随距离变化 */
public class MetalDetectorActivity extends BaseActivity implements SensorEventListener {

    private SensorManager sm;
    private Sensor mag;
    private TextView valueText, deltaText, stateText, tipText;
    private ProgressBar bar;
    private ToneGenerator tone;
    private Vibrator vibrator;
    private Handler ui = new Handler(Looper.getMainLooper());

    private float baseline = 0f;
    private float smooth = 0f;
    private boolean calibrated = false;
    private int stableCount = 0;
    private long lastBeep = 0;
    private boolean highPrev = false;
    private boolean running = true;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Tools.bgWithGlow(this));
        root.setPadding(dp(18), dp(24), dp(18), dp(28));

        TextView title = Tools.pageTitle(this, "金属探测");
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("把手机背面靠近墙面/地面，缓慢平移");
        sub.setTextSize(12);
        sub.setTextColor(Tools.subTextColor(this));
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(6), 0, dp(16));
        root.addView(sub);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(20), dp(18), dp(18));
        card.setBackground(Tools.liquidGlassCard(this, 24));
        Tools.setElevation(card, 12);
        root.addView(card);

        valueText = new TextView(this);
        valueText.setText("—");
        valueText.setTextSize(44);
        valueText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        valueText.setTextColor(Tools.textColor(this));
        valueText.setGravity(Gravity.CENTER);
        card.addView(valueText);

        TextView unit = new TextView(this);
        unit.setText("μT 磁感应强度");
        unit.setTextSize(12);
        unit.setTextColor(Tools.subTextColor(this));
        unit.setGravity(Gravity.CENTER);
        card.addView(unit);

        deltaText = new TextView(this);
        deltaText.setText("Δ 0.0 μT");
        deltaText.setTextSize(13);
        deltaText.setTextColor(Tools.subTextColor(this));
        deltaText.setGravity(Gravity.CENTER);
        deltaText.setPadding(0, dp(10), 0, dp(6));
        card.addView(deltaText);

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setProgress(0);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(10));
        pl.topMargin = dp(6);
        card.addView(bar, pl);

        stateText = new TextView(this);
        stateText.setText("等待传感器…");
        stateText.setTextSize(15);
        stateText.setGravity(Gravity.CENTER);
        stateText.setTextColor(Tools.accentColor(this));
        stateText.setPadding(0, dp(14), 0, 0);
        card.addView(stateText);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rl.topMargin = dp(14);
        root.addView(row, rl);

        TextView cal = new TextView(this);
        cal.setText("重新校准");
        cal.setGravity(Gravity.CENTER);
        cal.setTextSize(14);
        cal.setTextColor(0xFFFFFFFF);
        cal.setBackground(Tools.pillButton(this, 44, true));
        row.addView(cal, new LinearLayout.LayoutParams(dp(150), dp(44)));
        cal.setOnClickListener(v -> {
            baseline = smooth;
            calibrated = true;
            toast("已把当前场强设为基准");
        });

        tipText = new TextView(this);
        tipText.setText("提示：校准后数值长期漂移属于正常现象；靠近钢筋、扬声器、磁吸支架都会触发。");
        tipText.setTextSize(11);
        tipText.setTextColor(Tools.subTextColor(this));
        tipText.setPadding(0, dp(16), 0, 0);
        root.addView(tipText);

        setContentView(root);

        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sm != null) mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        try {
            tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 60);
        } catch (Throwable ignored) {}
        if (mag == null) {
            stateText.setText("这台设备没有磁力计，无法探测");
            bar.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        running = true;
        if (sm != null && mag != null) sm.registerListener(this, mag, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    protected void onPause() {
        super.onPause();
        running = false;
        if (sm != null) sm.unregisterListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tone != null) tone.release();
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        if (!running) return;
        float x = e.values[0], y = e.values[1], z = e.values[2];
        float magValue = (float) Math.sqrt(x * x + y * y + z * z);
        smooth = smooth == 0f ? magValue : smooth * 0.85f + magValue * 0.15f;

        if (!calibrated) {
            stableCount++;
            baseline = smooth;
            if (stableCount > 40) calibrated = true;
        }

        float delta = smooth - baseline;
        float level = Math.min(1f, Math.max(0f, delta / 30f));

        final float v = smooth, d = delta;
        final int p = (int) (level * 1000);
        final String st;
        if (delta < 2f) st = "数值平稳 · 没有明显金属反应";
        else if (delta < 8f) st = "轻微波动 · 附近可能有金属";
        else if (delta < 20f) st = "有反应 · 疑似金属靠近";
        else st = "强反应 · 很近或大块金属";

        ui.post(() -> {
            valueText.setText(String.format(Locale.US, "%.1f", v));
            deltaText.setText(String.format(Locale.US, "Δ %+.1f μT · 基准 %.1f", d, baseline));
            bar.setProgress(p);
            stateText.setText(st);
            stateText.setTextColor(d < 8f ? Tools.accentColor(MetalDetectorActivity.this)
                    : (d < 20f ? 0xFFF59E0B : 0xFFEF4444));
        });

        maybeBeep(level, delta);
    }

    void maybeBeep(float level, float delta) {
        long now = System.currentTimeMillis();
        boolean high = delta > 8f;
        if (high && !highPrev && vibrator != null) {
            try { vibrator.vibrate(60); } catch (Throwable ignored) {}
        }
        highPrev = high;
        if (!high) return;
        long gap = (long) (700 - level * 600);
        if (now - lastBeep < gap) return;
        lastBeep = now;
        try {
            if (tone != null) tone.startTone(ToneGenerator.TONE_PROP_BEEP, (int) Math.max(40, 140 - level * 100));
        } catch (Throwable ignored) {}
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
