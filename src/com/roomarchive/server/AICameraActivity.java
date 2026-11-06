package com.roomarchive.server;

import android.app.Activity;
import android.graphics.*;
import android.hardware.Camera;
import android.os.*;
import android.speech.tts.TextToSpeech;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.Locale;

public class AICameraActivity extends Activity implements TextToSpeech.OnInitListener {
    Camera camera;
    OverlayView overlay;
    TextView guide;
    TextToSpeech tts;
    boolean ready = false;
    int goodFrames = 0;
    long lastShutter = 0;

    protected void onCreate(Bundle s) {
        super.onCreate(s);
        tts = new TextToSpeech(this, this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        CameraPreview preview = new CameraPreview(this);
        root.addView(preview);

        overlay = new OverlayView(this);
        root.addView(overlay);

        guide = new TextView(this);
        guide.setText("小艺帮你拍照 · 把脸放进画面");
        guide.setTextColor(Color.WHITE);
        guide.setTextSize(16);
        guide.setPadding(20, 60, 20, 0);
        guide.setShadowLayer(4, 2, 2, Color.BLACK);
        root.addView(guide);

        Button manual = new Button(this);
        manual.setText("手动拍");
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(dp(100), dp(50));
        mp.gravity = Gravity.BOTTOM | Gravity.LEFT;
        mp.bottomMargin = dp(40);
        mp.leftMargin = dp(30);
        manual.setOnClickListener(v -> takePhoto());
        root.addView(manual, mp);

        Button auto = new Button(this);
        auto.setText("AI自动拍");
        FrameLayout.LayoutParams ap = new FrameLayout.LayoutParams(dp(120), dp(50));
        ap.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        ap.bottomMargin = dp(40);
        ap.rightMargin = dp(30);
        auto.setOnClickListener(v -> {
            goodFrames = 0;
            guide.setText("AI自动拍摄中...");
        });
        root.addView(auto, ap);

        setContentView(root);
    }

    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.CHINESE);
            ready = true;
        }
    }

    void speak(String t) {
        if (ready) tts.speak(t, TextToSpeech.QUEUE_FLUSH, null, null);
    }

    void onFaceDetected(Camera.Face[] faces) {
        if (faces == null || faces.length == 0) {
            guide.setText("没检测到人脸，把脸放进来");
            goodFrames = 0;
            return;
        }
        Camera.Face f = faces[0];
        Rect r = f.rect;
        // face rect coordinates are -1000 to 1000, x right, y up
        float cx = (r.left + r.right) / 2000f + 0.5f; // 0~1 in preview
        float cy = (r.top + r.bottom) / 2000f + 0.5f;
        int score = f.score;

        overlay.setFaceRect(r);

        // 三分法：理想位置在左1/3或右1/3处
        boolean goodX = (cx > 0.25 && cx < 0.4) || (cx > 0.6 && cx < 0.75);
        boolean goodY = cy > 0.3 && cy < 0.6;

        if (score < 40) {
            guide.setText("再靠近一点，脸大点");
            speak("再靠近一点");
            goodFrames = 0;
        } else if (!goodX) {
            if (cx < 0.3) { guide.setText("往右边挪一点，构图更好看"); speak("往右边挪一点"); }
            else { guide.setText("往左边挪一点，构图更好看"); speak("往左边挪一点"); }
            goodFrames = 0;
        } else if (!goodY) {
            guide.setText("稍微低头/抬头，找到最佳角度");
            goodFrames = 0;
        } else {
            goodFrames++;
            guide.setText("构图完美！保持住... " + goodFrames + "/5");
            if (goodFrames >= 5 && System.currentTimeMillis() - lastShutter > 3000) {
                speak("茄子！拍照啦");
                guide.setText("📸 已自动拍摄");
                takePhoto();
                lastShutter = System.currentTimeMillis();
                goodFrames = 0;
            }
        }
    }

    void takePhoto() {
        try {
            if (camera == null) return;
            camera.takePicture(null, null, (data, c) -> {
                try {
                    File dir = new File(FileStore.outDir(this), "AI拍照");
                    dir.mkdirs();
                    File f = new File(dir, "ai_" + System.currentTimeMillis() + ".jpg");
                    FileOutputStream fos = new FileOutputStream(f);
                    fos.write(data);
                    fos.close();
                    guide.setText("已保存: " + f.getName());
                } catch (Exception e) {
                    guide.setText("保存失败: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            guide.setText("拍照失败: " + e.getMessage());
        }
    }

    class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
        public CameraPreview(Activity a) {
            super(a);
            getHolder().addCallback(this);
        }
        public void surfaceCreated(SurfaceHolder h) {
            try {
                camera = Camera.open();
                camera.setPreviewDisplay(h);
                camera.setDisplayOrientation(90);
                Camera.Parameters p = camera.getParameters();
                if (p.getMaxNumFocusAreas() > 0) p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                camera.setParameters(p);
                camera.startPreview();
                camera.setFaceDetectionListener(new Camera.FaceDetectionListener() {
                    public void onFaceDetection(Camera.Face[] faces, Camera c) {
                        onFaceDetected(faces);
                    }
                });
                try { camera.startFaceDetection(); } catch (Exception ignored) {}
            } catch (Exception e) {
                guide.setText("相机打开失败: " + e.getMessage());
            }
        }
        public void surfaceChanged(SurfaceHolder h, int f, int w, int ht) {}
        public void surfaceDestroyed(SurfaceHolder h) {
            try { if (camera != null) { camera.stopPreview(); camera.release(); camera = null; } } catch (Exception ignored) {}
        }
    }

    class OverlayView extends View {
        Rect faceRect;
        Paint linePaint, boxPaint;
        public OverlayView(Activity a) {
            super(a);
            linePaint = new Paint();
            linePaint.setColor(0x80FFFFFF);
            linePaint.setStrokeWidth(1f);
            boxPaint = new Paint();
            boxPaint.setColor(0x8000FF00);
            boxPaint.setStyle(Paint.Style.STROKE);
            boxPaint.setStrokeWidth(3f);
        }
        void setFaceRect(Rect r) { faceRect = r; postInvalidate(); }
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            // 三分法线
            int w = getWidth(), h = getHeight();
            c.drawLine(w/3f, 0, w/3f, h, linePaint);
            c.drawLine(w*2/3f, 0, w*2/3f, h, linePaint);
            c.drawLine(0, h/3f, w, h/3f, linePaint);
            c.drawLine(0, h*2/3f, w, h*2/3f, linePaint);
            // 人脸框
            if (faceRect != null) {
                float left = (faceRect.left/2000f + 0.5f) * w;
                float right = (faceRect.right/2000f + 0.5f) * w;
                float top = h - (faceRect.top/2000f + 0.5f) * h;
                float bottom = h - (faceRect.bottom/2000f + 0.5f) * h;
                c.drawRect(left, top, right, bottom, boxPaint);
            }
        }
    }

    protected void onPause() {
        super.onPause();
        try { if (camera != null) { camera.release(); camera = null; } } catch (Exception ignored) {}
    }
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
    int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
}
