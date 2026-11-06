package com.roomarchive.server;

import android.app.Activity;
import android.graphics.*;
import android.hardware.Camera;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class PanoramaActivity extends Activity {
    Camera camera;
    List<Bitmap> shots = new ArrayList<>();
    TextView guide;
    int state = 0; // 0=ready, 1=shooting, 2=done

    protected void onCreate(Bundle s) {
        super.onCreate(s);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        CameraPreview preview = new CameraPreview(this);
        root.addView(preview);

        guide = new TextView(this);
        guide.setText("全景拍照 · 点快门开始");
        guide.setTextColor(Color.WHITE);
        guide.setTextSize(16);
        guide.setPadding(20, 60, 20, 0);
        guide.setShadowLayer(4, 2, 2, Color.BLACK);
        root.addView(guide);

        Button shoot = new Button(this);
        shoot.setText("📷 拍摄");
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(dp(140), dp(60));
        sp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        sp.bottomMargin = dp(40);
        shoot.setOnClickListener(v -> onShoot());
        root.addView(shoot, sp);

        setContentView(root);
    }

    void onShoot() {
        if (state == 0) {
            shots.clear();
            state = 1;
            guide.setText("第1张 · 拍好了慢慢往右转");
        } else if (state == 1) {
            // 拍当前帧
            try {
                camera.takePicture(null, null, (data, c) -> {
                    Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
                    shots.add(bmp);
                    guide.setText("已拍" + shots.size() + "张 · 继续右转");
                });
            } catch (Exception e) {
                guide.setText("拍摄失败: " + e.getMessage());
            }
        }
    }

    class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
        public CameraPreview(Activity a) { super(a); getHolder().addCallback(this); }
        public void surfaceCreated(SurfaceHolder h) {
            try {
                camera = Camera.open();
                camera.setPreviewDisplay(h);
                camera.setDisplayOrientation(90);
                camera.startPreview();
            } catch (Exception e) { guide.setText("相机失败: " + e.getMessage()); }
        }
        public void surfaceChanged(SurfaceHolder h, int f, int w, int ht) {}
        public void surfaceDestroyed(SurfaceHolder h) {
            try { if (camera != null) { camera.release(); camera = null; } } catch (Exception ignored) {}
        }
    }

    protected void onPause() {
        super.onPause();
        try { if (camera != null) { camera.release(); camera = null; } } catch (Exception ignored) {}
    }

    int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
}
