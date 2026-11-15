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
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
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
 * 360 全景：手机横过来对着要拍的方向，跟着箭头转，转到刻度自动拍一张，
 * 拍满一圈自动拼成一张长图存到 Download/IK工具箱/全景。
 */
public class PanoramaActivity extends Activity implements SensorEventListener {

    private static final int STEPS = 8;          // 一圈 8 张
    private static final float STEP_DEG = 360f / STEPS;
    private static final float TOLERANCE = 8f;   // 到位判定容差

    private Camera camera;
    private SurfaceView preview;
    private TextView guide, angleText, countText;
    private TextView arrow;       // 随手机转的方向箭头
    private ProgressBar progress;
    private ImageView thumb;
    private Button shootBtn, finishBtn;

    private final List<Bitmap> shots = new ArrayList<>();
    private SensorManager sm;
    private float[] rv = new float[3];
    private float baseYaw = Float.NaN;
    private float yaw = 0f;
    private boolean capturing = false;
    private boolean running = false;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        preview = new SurfaceView(this);
        ((SurfaceView) preview).getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder h) {
                openCamera(h);
            }

            @Override
            public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) {}

            @Override
            public void surfaceDestroyed(SurfaceHolder h) {
                releaseCamera();
            }
        });
        root.addView(preview, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // 顶部引导层
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setGravity(Gravity.CENTER_HORIZONTAL);
        top.setPadding(dp(16), dp(54), dp(16), dp(16));
        top.setBackgroundColor(0x66000000);

        arrow = new TextView(this);
        arrow.setText("➜");
        arrow.setTextSize(46);
        arrow.setTextColor(0xFF5FE3B4);
        arrow.setShadowLayer(8, 0, 0, 0xFF0A4A3C);
        top.addView(arrow);

        guide = new TextView(this);
        guide.setText("点「开始全景」，然后按箭头方向慢慢转手机");
        guide.setTextColor(Color.WHITE);
        guide.setTextSize(16);
        guide.setTypeface(Typeface.DEFAULT_BOLD);
        guide.setGravity(Gravity.CENTER);
        guide.setShadowLayer(6, 0, 0, Color.BLACK);
        top.addView(guide);

        angleText = new TextView(this);
        angleText.setText("角度 --");
        angleText.setTextColor(0xFFB9E9D8);
        angleText.setTextSize(12);
        angleText.setGravity(Gravity.CENTER);
        top.addView(angleText);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(STEPS);
        progress.setProgress(0);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(8));
        pp.topMargin = dp(10);
        progress.setLayoutParams(pp);
        top.addView(progress);

        countText = new TextView(this);
        countText.setText("0 / " + STEPS + " 张");
        countText.setTextColor(Color.WHITE);
        countText.setTextSize(12);
        countText.setGravity(Gravity.CENTER);
        countText.setPadding(0, dp(6), 0, 0);
        top.addView(countText);

        root.addView(top, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP));

        // 底部操作条
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(dp(12), dp(12), dp(12), dp(28));
        bottom.setBackgroundColor(0x66000000);

        shootBtn = new Button(this);
        shootBtn.setText("开始全景");
        shootBtn.setAllCaps(false);
        shootBtn.setOnClickListener(v -> startSweep());
        bottom.addView(shootBtn);

        finishBtn = new Button(this);
        finishBtn.setText("完成拼接");
        finishBtn.setAllCaps(false);
        finishBtn.setEnabled(false);
        finishBtn.setOnClickListener(v -> finishAndStitch());
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        fp.leftMargin = dp(10);
        finishBtn.setLayoutParams(fp);
        bottom.addView(finishBtn);

        thumb = new ImageView(this);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(dp(58), dp(58));
        tp.leftMargin = dp(10);
        thumb.setLayoutParams(tp);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bottom.addView(thumb);

        root.addView(bottom, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM));

        setContentView(root);

        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sm == null || sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) == null) {
            guide.setText("这台手机没有方向传感器，用「拍一张」手动拍 8 张不同角度");
        }
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
        for (Camera.Size c : list) {
            if (c.width >= 1280 && c.width <= 1920) return new int[]{c.width, c.height};
        }
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
        if (camera == null) {
            Toast.makeText(this, "相机还没准备好", Toast.LENGTH_SHORT).show();
            return;
        }
        shots.clear();
        progress.setProgress(0);
        countText.setText("0 / " + STEPS + " 张");
        finishBtn.setEnabled(false);
        running = true;
        baseYaw = Float.NaN;     // 第一次读到方向时再定基准
        shootBtn.setText("停止");
        shootBtn.setOnClickListener(v -> stopSweep());
        guide.setText("按箭头方向慢慢转手机，走到刻度会自动拍");
    }

    private void stopSweep() {
        running = false;
        shootBtn.setText("开始全景");
        shootBtn.setOnClickListener(v -> startSweep());
        guide.setText("已停止，可以点「完成拼接」");
        finishBtn.setEnabled(!shots.isEmpty());
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

    private final float[] rm = new float[9];

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void updateGuide() {
        if (!running) {
            angleText.setText(String.format(Locale.US, "当前角度 %.0f°", yaw));
            return;
        }
        int next = shots.size();
        if (next >= STEPS) {
            stopSweep();
            return;
        }
        float target = next * STEP_DEG;          // 方位角顺时针增大 = 向右转
        float diff = target - yaw;
        float need = Math.abs(diff);
        boolean right = diff > 0;
        if (need > 180) {                        // 反向更近
            need = 360 - need;
            right = !right;
        }
        // 箭头指向要转的方向：右=0°，左=180°
        arrow.setRotation(right ? 0 : 180);
        guide.setText((right ? "向右转 " : "向左转 ") + String.format(Locale.US, "%.0f°", need)
                + " 到第 " + (next + 1) + " 个刻度");
        angleText.setText(String.format(Locale.US, "当前 %.0f° · 目标 %.0f°", yaw, target));
        if (Math.abs(diff) <= TOLERANCE || Math.abs(diff) >= 360 - TOLERANCE) {
            capture();
        }
    }

    private void capture() {
        if (capturing || camera == null) return;
        capturing = true;
        try {
            camera.takePicture(null, null, (data, c) -> {
                capturing = false;
                try {
                    Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
                    if (bmp != null) {
                        shots.add(rotate90(bmp));
                        progress.setProgress(shots.size());
                        countText.setText(shots.size() + " / " + STEPS + " 张");
                        thumb.setImageBitmap(shots.get(shots.size() - 1));
                        finishBtn.setEnabled(true);
                        if (shots.size() >= STEPS) {
                            stopSweep();
                            finishAndStitch();
                        }
                    }
                } catch (Throwable t) {
                    guide.setText("处理失败：" + t.getMessage());
                }
            });
        } catch (Throwable t) {
            capturing = false;
            guide.setText("拍摄失败：" + t.getMessage());
        }
    }

    private Bitmap rotate90(Bitmap src) {
        Matrix m = new Matrix();
        m.postRotate(90);
        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
    }

    /** 简易拼接：统一高度 → 取中间 45% → 12% 渐变重叠 */
    private void finishAndStitch() {
        if (shots.size() < 2) {
            Toast.makeText(this, "至少拍 2 张才能拼", Toast.LENGTH_SHORT).show();
            return;
        }
        guide.setText("正在拼接…");
        new Thread(() -> {
            Bitmap out = null;
            String err = null;
            try {
                out = stitch(shots);
            } catch (Throwable t) {
                err = t.getMessage();
            }
            final Bitmap r = out;
            final String e = err;
            ui.post(() -> {
                if (r == null) {
                    guide.setText("拼接失败：" + e);
                    return;
                }
                String path = save(r);
                guide.setText("拼好了：" + path);
                showResult(r, path);
            });
        }).start();
    }

    private Bitmap stitch(List<Bitmap> frames) {
        int h = Integer.MAX_VALUE;
        for (Bitmap b : frames) h = Math.min(h, b.getHeight());
        List<Bitmap> scaled = new ArrayList<>();
        for (Bitmap b : frames) {
            float s = h / (float) b.getHeight();
            int w = Math.max(1, (int) (b.getWidth() * s));
            scaled.add(Bitmap.createScaledBitmap(b, w, h, true));
        }
        int cropW = (int) (scaled.get(0).getWidth() * 0.45f);
        int overlap = (int) (cropW * 0.12f);
        int total = cropW + (scaled.size() - 1) * (cropW - overlap);
        Bitmap out = Bitmap.createBitmap(total, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        cv.drawColor(Color.BLACK);
        float x = 0;
        for (int i = 0; i < scaled.size(); i++) {
            Bitmap f = scaled.get(i);
            int sx = Math.max(0, (f.getWidth() - cropW) / 2);
            Rect src = new Rect(sx, 0, sx + cropW, h);
            if (i == 0) {
                cv.drawBitmap(f, src, new RectF(0, 0, cropW, h), null);
            } else {
                Rect dstFull = new Rect((int) (x - overlap), 0, (int) (x - overlap) + cropW, h);
                // 重叠区做渐变过渡，避免硬缝
                int strips = 12;
                for (int k = 0; k < strips; k++) {
                    int alpha = (int) (255 * (k + 1) / (float) strips);
                    int dx0 = (int) (x - overlap) + (int) (cropW * k / (float) strips);
                    int dx1 = (int) (x - overlap) + (int) (cropW * (k + 1) / (float) strips);
                    int sx0 = sx + (int) (cropW * k / (float) strips);
                    int sx1 = sx + (int) (cropW * (k + 1) / (float) strips);
                    Rect sub = new Rect(sx0, 0, sx1, h);
                    RectF dsub = new RectF(dx0, 0, dx1, h);
                    int save = cv.saveLayerAlpha(dx0, 0, dx1, h, alpha);
                    Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
                    cv.drawBitmap(f, sub, dsub, p);
                    cv.restoreToCount(save);
                }
                x = x - overlap + cropW;
                continue;
            }
            x = cropW;
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
                .show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
