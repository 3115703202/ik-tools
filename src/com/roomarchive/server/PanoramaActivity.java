package com.roomarchive.server;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 360 全景：跟着箭头转，到刻度自动拍；可随时手动补拍/提前完成。
 * 关键点：Camera 回调在相机线程，所有 UI 更新必须回主线程（旧版这里会直接卡死）。
 */
public class PanoramaActivity extends Activity implements SensorEventListener {

    private static final int STEPS = 8;
    private static final float STEP_DEG = 360f / STEPS;
    private static final float TOLERANCE = 10f;
    private static final long SHOT_COOLDOWN = 900;   // 两次自动拍最短间隔

    private Camera camera;
    private SurfaceView preview;
    private TextView guide, angleText, countText, arrow;
    private ProgressBar progress;
    private ImageView thumb;
    private TextView shootBtn, snapBtn, finishBtn;

    private final List<Bitmap> shots = new ArrayList<>();
    private SensorManager sm;
    private final float[] rv = new float[3];
    private final float[] rm = new float[9];
    private float baseYaw = Float.NaN;
    private float yaw = 0f;
    private boolean capturing = false;
    private boolean running = false;
    private long lastShotAt = 0;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        preview = new SurfaceView(this);
        preview.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override public void surfaceCreated(SurfaceHolder h) { openCamera(h); }
            @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) {}
            @Override public void surfaceDestroyed(SurfaceHolder h) { releaseCamera(); }
        });
        root.addView(preview, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // 顶部引导卡
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setGravity(Gravity.CENTER_HORIZONTAL);
        top.setPadding(dp(16), dp(48), dp(16), dp(12));
        GradientDrawable tg = new GradientDrawable();
        tg.setColor(0x99000000);
        tg.setCornerRadius(dp(22));
        top.setBackground(tg);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.setMargins(dp(12), 0, dp(12), 0);
        root.addView(top, tlp);

        arrow = new TextView(this);
        arrow.setText("➜");
        arrow.setTextSize(50);
        arrow.setTextColor(0xFF5FE3B4);
        arrow.setShadowLayer(8, 0, 0, 0xFF0A4A3C);
        top.addView(arrow);

        guide = new TextView(this);
        guide.setText("横屏拿稳手机，点「开始全景」后跟着箭头慢慢转");
        guide.setTextColor(Color.WHITE);
        guide.setTextSize(15);
        guide.setTypeface(Typeface.DEFAULT_BOLD);
        guide.setGravity(Gravity.CENTER);
        guide.setShadowLayer(6, 0, 0, Color.BLACK);
        top.addView(guide);

        angleText = new TextView(this);
        angleText.setText("角度 --");
        angleText.setTextColor(0xFFB9E9D8);
        angleText.setTextSize(12);
        angleText.setGravity(Gravity.CENTER);
        angleText.setPadding(0, dp(2), 0, 0);
        top.addView(angleText);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(STEPS);
        progress.setProgress(0);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(6));
        pp.topMargin = dp(8);
        progress.setLayoutParams(pp);
        top.addView(progress);

        countText = new TextView(this);
        countText.setText("已拍 0 / " + STEPS + " 张");
        countText.setTextColor(Color.WHITE);
        countText.setTextSize(11);
        countText.setGravity(Gravity.CENTER);
        countText.setPadding(0, dp(4), 0, 0);
        top.addView(countText);

        // 底部操作条
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(dp(12), dp(10), dp(12), dp(26));
        GradientDrawable bgb = new GradientDrawable();
        bgb.setColor(0x99000000);
        bgb.setCornerRadius(dp(22));
        bottom.setBackground(bgb);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.setMargins(dp(12), 0, dp(12), dp(12));
        root.addView(bottom, blp);

        shootBtn = action("开始全景", 0xFF2E9E5B);
        shootBtn.setOnClickListener(v -> { if (running) stopSweep(); else startSweep(); });
        bottom.addView(shootBtn);

        snapBtn = action("拍一张", 0xFF39424E);
        snapBtn.setOnClickListener(v -> capture());
        bottom.addView(snapBtn);

        finishBtn = action("完成", 0xFF1F5F8B);
        finishBtn.setEnabled(false);
        finishBtn.setOnClickListener(v -> finishAndStitch());
        bottom.addView(finishBtn);

        thumb = new ImageView(this);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(dp(46), dp(46));
        tp.leftMargin = dp(8);
        thumb.setLayoutParams(tp);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bottom.addView(thumb);

        setContentView(root);

        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sm == null || sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) == null) {
            guide.setText("没有方向传感器，用「拍一张」手动拍 8 个角度");
        }
    }

    private TextView action(String text, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setTextColor(Color.WHITE);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(18));
        g.setColor(color);
        t.setBackground(g);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(84), dp(40));
        p.leftMargin = dp(6);
        t.setLayoutParams(p);
        return t;
    }

    private void openCamera(SurfaceHolder h) {
        try {
            releaseCamera();
            camera = Camera.open();
            camera.setPreviewDisplay(h);
            camera.setDisplayOrientation(90);
            Camera.Parameters p = camera.getParameters();
            int[] sz = pickSize(p.getSupportedPictureSizes());
            p.setPictureSize(sz[0], sz[1]);
            try {
                p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                p.setAutoExposureLock(false);
            } catch (Throwable ignored) {}
            camera.setParameters(p);
            camera.startPreview();
        } catch (Throwable t) {
            guide.setText("相机打不开：" + t.getMessage());
        }
    }

    private int[] pickSize(List<Camera.Size> list) {
        int[] s = {1280, 720};
        if (list == null) return s;
        for (Camera.Size c : list) if (c.width >= 1280 && c.width <= 1920)
            return new int[]{c.width, c.height};
        Camera.Size best = list.get(0);
        for (Camera.Size c : list) if (c.width > best.width && c.width <= 1920) best = c;
        return new int[]{best.width, best.height};
    }

    private void releaseCamera() {
        try {
            if (camera != null) {
                camera.stopPreview();
                camera.release();
            }
        } catch (Throwable ignored) {}
        camera = null;
    }

    private void startSweep() {
        if (camera == null) { toast("相机还没准备好"); return; }
        shots.clear();
        progress.setProgress(0);
        countText.setText("已拍 0 / " + STEPS + " 张");
        finishBtn.setEnabled(false);
        finishBtn.setAlpha(0.5f);
        thumb.setImageDrawable(null);
        running = true;
        baseYaw = Float.NaN;
        shootBtn.setText("停止");
        guide.setText("跟着箭头慢慢转，到刻度会自动拍");
    }

    private void stopSweep() {
        running = false;
        shootBtn.setText("继续");
        guide.setText(shots.isEmpty() ? "点「拍一张」开始" : "已拍 " + shots.size() + " 张，可继续或完成");
        finishBtn.setEnabled(shots.size() >= 2);
        finishBtn.setAlpha(shots.size() >= 2 ? 1f : 0.5f);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sm != null) {
            Sensor s = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            if (s != null) sm.registerListener(this, s, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sm != null) sm.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        if (e.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) return;
        SensorManager.getRotationMatrixFromVector(rm, e.values);
        SensorManager.getOrientation(rm, rv);
        float az = (float) Math.toDegrees(rv[0]);
        if (Float.isNaN(baseYaw)) baseYaw = az;
        float d = az - baseYaw;
        while (d > 180) d -= 360;
        while (d < -180) d += 360;
        yaw = d;
        updateGuide();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void updateGuide() {
        int next = shots.size();
        if (!running) {
            angleText.setText(String.format(Locale.US, "当前 %.0f° · 已拍 %d 张", yaw, next));
            return;
        }
        float target = next * STEP_DEG;
        float diff = target - yaw;
        if (diff > 180) diff -= 360;
        if (diff < -180) diff += 360;
        boolean right = diff > 0;
        float need = Math.abs(diff);

        arrow.setRotation(right ? 0 : 180);
        float tilt = Math.min(1f, need / 45f);
        arrow.setAlpha(0.45f + 0.55f * tilt);
        guide.setText((right ? "向右转 " : "向左转 ")
                + String.format(Locale.US, "%.0f°", need)
                + " 到第 " + Math.min(STEPS, next + 1) + " 个刻度");
        angleText.setText(String.format(Locale.US, "当前 %.0f° · 目标 %.0f° · 已拍 %d 张", yaw, target, next));

        if (need <= TOLERANCE && !capturing
                && SystemClock.elapsedRealtime() - lastShotAt > SHOT_COOLDOWN) {
            capture();
        }
    }

    private void capture() {
        if (camera == null) { toast("相机未就绪"); return; }
        if (capturing) return;
        if (shots.size() >= STEPS) { toast("已经拍满一圈了"); return; }
        capturing = true;
        preview.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        try {
            camera.takePicture(null, null, (data, c) -> {
                final Bitmap bmp;
                int total;
                try {
                    bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
                    total = shots.size() + 1;
                } catch (Throwable t) {
                    capturing = false;
                    ui.post(() -> guide.setText("这张没存上，再转回刻度拍一次"));
                    return;
                }
                capturing = false;
                lastShotAt = SystemClock.elapsedRealtime();
                if (bmp == null) {
                    ui.post(() -> guide.setText("这张没存上，再试一次"));
                    return;
                }
                final Bitmap rot = rotate90(bmp);
                // 相机线程不能碰 UI，一切回主线程
                ui.post(() -> {
                    shots.add(rot);
                    progress.setProgress(shots.size());
                    countText.setText("已拍 " + shots.size() + " / " + STEPS + " 张");
                    thumb.setImageBitmap(rot);
                    finishBtn.setEnabled(shots.size() >= 2);
                    finishBtn.setAlpha(shots.size() >= 2 ? 1f : 0.5f);
                    if (shots.size() >= STEPS) {
                        running = false;
                        shootBtn.setText("继续");
                        guide.setText("一圈拍完了，正在拼接…");
                        finishAndStitch();
                    }
                });
            });
        } catch (Throwable t) {
            capturing = false;
            toast("拍照失败：" + t.getMessage());
            // 相机可能已失效，重开预览
            try { camera.startPreview(); } catch (Throwable ignored) {}
        }
    }

    private Bitmap rotate90(Bitmap src) {
        try {
            Matrix m = new Matrix();
            m.postRotate(90);
            return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
        } catch (Throwable t) {
            return src;
        }
    }

    private void finishAndStitch() {
        if (shots.size() < 2) { toast("至少拍 2 张才能拼"); return; }
        running = false;
        shootBtn.setText("继续");
        finishBtn.setEnabled(false);
        guide.setText("正在拼接…");
        final List<Bitmap> frames = new ArrayList<>(shots);
        new Thread(() -> {
            Bitmap out = null;
            String err = null;
            try {
                out = stitch(frames);
            } catch (Throwable t) {
                err = t.getMessage();
            }
            final Bitmap r = out;
            final String e = err;
            ui.post(() -> {
                if (r == null) {
                    guide.setText("拼接失败：" + e + "，单张已保留在图库目录");
                    finishBtn.setEnabled(true);
                    return;
                }
                String path = save(r);
                guide.setText("拼好了：" + path);
                showResult(r, path);
            });
        }).start();
    }

    /** 统一高度 → 每张取中间 50% → 12% 重叠做渐变过渡 */
    private Bitmap stitch(List<Bitmap> frames) {
        int h = Integer.MAX_VALUE;
        for (Bitmap b : frames) h = Math.min(h, b.getHeight());
        List<Bitmap> scaled = new ArrayList<>();
        for (Bitmap b : frames) {
            float s = h / (float) b.getHeight();
            int w = Math.max(1, (int) (b.getWidth() * s));
            scaled.add(Bitmap.createScaledBitmap(b, w, h, true));
        }
        int cropW = (int) (scaled.get(0).getWidth() * 0.5f);
        int overlap = Math.max(1, (int) (cropW * 0.12f));
        int total = cropW + (scaled.size() - 1) * (cropW - overlap);
        Bitmap out = Bitmap.createBitmap(Math.max(1, total), h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        cv.drawColor(Color.BLACK);

        int x = 0;
        for (int i = 0; i < scaled.size(); i++) {
            Bitmap f = scaled.get(i);
            int sx = Math.max(0, (f.getWidth() - cropW) / 2);
            Rect src = new Rect(sx, 0, sx + cropW, h);
            if (i == 0) {
                cv.drawBitmap(f, src, new RectF(0, 0, cropW, h), null);
                x = cropW;
            } else {
                int left = x - overlap;
                int strips = 12;
                for (int k = 0; k < strips; k++) {
                    int sx0 = sx + cropW * k / strips;
                    int sx1 = sx + cropW * (k + 1) / strips;
                    float dx0 = left + (cropW - overlap) * k / (float) strips;
                    float dx1 = left + (cropW - overlap) * (k + 1) / (float) strips;
                    if (sx1 <= sx0 || dx1 <= dx0) continue;
                    Rect sub = new Rect(sx0, 0, sx1, h);
                    RectF dsub = new RectF(dx0, 0, dx1, h);
                    int alpha = (int) (255 * (k + 1) / (float) strips);
                    int save = cv.saveLayerAlpha(dx0, 0, dx1, h, alpha);
                    cv.drawBitmap(f, sub, dsub, new Paint(Paint.FILTER_BITMAP_FLAG));
                    cv.restoreToCount(save);
                }
                x = left + cropW;
            }
        }
        return out;
    }

    private String save(Bitmap b) {
        try {
            File dir = new File(FileStore.outDir(this), "全景");
            dir.mkdirs();
            File f = new File(dir, "全景_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream fos = new FileOutputStream(f);
            b.compress(Bitmap.CompressFormat.JPEG, 92, fos);
            fos.close();
            return f.getAbsolutePath();
        } catch (Throwable t) {
            return "保存失败：" + t.getMessage();
        }
    }

    private void showResult(Bitmap b, String path) {
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(b);
        iv.setAdjustViewBounds(true);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        new AlertDialog.Builder(this)
                .setTitle("全景已保存")
                .setMessage(path)
                .setView(iv)
                .setPositiveButton("好", null)
                .setNegativeButton("继续拍", (d, w) -> {
                    running = false;
                    shootBtn.setText("开始全景");
                    guide.setText("点「开始全景」重新拍一圈");
                })
                .show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
