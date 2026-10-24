package com.roomarchive.server;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 传感器工具箱：指南针 + 水平仪 + 手电筒（3D 液态玻璃仪表盘） */
public class SensorToolsActivity extends Activity implements SensorEventListener {

    private SensorManager sm;
    private Sensor accel, magnet;
    private float[] gravity = new float[3];
    private float[] geomag = new float[3];
    private float azimuth = 0;          // 指南针方位角（度）
    private float tiltX = 0, tiltY = 0; // 水平仪倾角
    private boolean hasCompass = true, hasLevel = true;

    private CompassView compassView;
    private LevelView levelView;
    private TextView compassText, levelText;
    private Button torchBtn;
    private boolean torchOn = false;
    private CameraManager camMgr;
    private String torchCam = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            sm = (SensorManager) getSystemService(SENSOR_SERVICE);
            accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            magnet = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            if (accel == null || magnet == null) hasCompass = false;
            if (accel == null) hasLevel = false;
        } catch (Throwable t) {
            hasCompass = false;
            hasLevel = false;
        }
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                // 先检查 CAMERA 权限（危险权限，未授权时 getCameraIdList 会抛 SecurityException 闪退）
                boolean hasCamPerm = checkSelfPermission(android.Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED;
                if (hasCamPerm) {
                    camMgr = (CameraManager) getSystemService(CAMERA_SERVICE);
                    for (String id : camMgr.getCameraIdList()) {
                        CameraCharacteristics ch = camMgr.getCameraCharacteristics(id);
                        Boolean flash = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                        if (flash != null && flash) { torchCam = id; break; }
                    }
                }
            } catch (Throwable t) {
                torchCam = null;
            }
        }
        try {
            buildUi();
        } catch (Throwable t) {
            // UI 构建兜底：绝不让页面闪退
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER);
            root.setPadding(dp(20), dp(20), dp(20), dp(20));
            root.setBackground(Tools.bgWithGlow(this));
            TextView msg = new TextView(this);
            msg.setText("传感器页面初始化失败：" + t.getMessage() + "\n部分设备不支持对应传感器，不影响其它功能");
            msg.setTextSize(13);
            msg.setTextColor(Tools.subTextColor(this));
            msg.setGravity(Gravity.CENTER);
            root.addView(msg);
            setContentView(root);
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Tools.bgWithGlow(this));
        root.setPadding(dp(16), dp(20), dp(16), dp(18));

        TextView title = Tools.pageTitle(this, "传感器工具箱");
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        // ===== 指南针卡片 =====
        LinearLayout compassCard = glassCard();
        compassCard.setOrientation(LinearLayout.VERTICAL);
        compassCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams ccP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(250));
        ccP.setMargins(0, dp(16), 0, 0);
        root.addView(compassCard, ccP);

        TextView cHead = Tools.sectionTitle(this, "指南针");
        compassCard.addView(cHead);

        compassView = new CompassView(this);
        compassView.setLayoutParams(new LinearLayout.LayoutParams(0, dp(170), 1f));
        compassCard.addView(compassView);

        compassText = new TextView(this);
        compassText.setText(hasCompass ? "N 0°" : "设备缺少指南针传感器");
        compassText.setTextSize(15);
        compassText.setTypeface(Typeface.DEFAULT_BOLD);
        compassText.setTextColor(Tools.accentColor(this));
        compassText.setGravity(Gravity.CENTER);
        compassCard.addView(compassText);

        // ===== 水平仪卡片 =====
        LinearLayout levelCard = glassCard();
        levelCard.setOrientation(LinearLayout.VERTICAL);
        levelCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams lcP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(230));
        lcP.setMargins(0, dp(16), 0, 0);
        root.addView(levelCard, lcP);

        TextView lHead = Tools.sectionTitle(this, "水平仪");
        levelCard.addView(lHead);

        levelView = new LevelView(this);
        levelView.setLayoutParams(new LinearLayout.LayoutParams(0, dp(150), 1f));
        levelCard.addView(levelView);

        levelText = new TextView(this);
        levelText.setText(hasLevel ? "水平：0.0° / 0.0°" : "设备缺少加速度传感器");
        levelText.setTextSize(13);
        levelText.setTextColor(Tools.subTextColor(this));
        levelText.setGravity(Gravity.CENTER);
        levelCard.addView(levelText);

        // ===== 手电筒卡片 =====
        LinearLayout torchCard = glassCard();
        torchCard.setOrientation(LinearLayout.VERTICAL);
        torchCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams tcP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(150));
        tcP.setMargins(0, dp(16), 0, 0);
        root.addView(torchCard, tcP);

        TextView tHead = Tools.sectionTitle(this, "手电筒");
        torchCard.addView(tHead);

        torchBtn = new Button(this);
        torchBtn.setText(torchCam == null ? "设备不支持闪光灯" : "点击开启");
        torchBtn.setTextSize(16);
        torchBtn.setTypeface(Typeface.DEFAULT_BOLD);
        torchBtn.setTextColor(Color.WHITE);
        torchBtn.setAllCaps(false);
        torchBtn.setBackground(Tools.pillButton(this, 26, true));
        LinearLayout.LayoutParams tbP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        tbP.setMargins(0, dp(14), 0, 0);
        torchCard.addView(torchBtn, tbP);
        if (torchCam != null) {
            torchBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setTorch(!torchOn);
                }
            });
        }

        root.addView(spacer(20));
        setContentView(root);
    }

    private void setTorch(boolean on) {
        if (torchCam == null) return;
        try {
            camMgr.setTorchMode(torchCam, on);
            torchOn = on;
            torchBtn.setText(on ? "点击关闭" : "点击开启");
            if (on) {
                torchBtn.setBackground(Tools.pillButton(this, 26, false));
            } else {
                torchBtn.setBackground(Tools.pillButton(this, 26, true));
            }
        } catch (Exception e) {
            torchOn = false;
            torchBtn.setText("闪光灯被占用");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            if (accel != null) sm.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME);
            if (magnet != null) sm.registerListener(this, magnet, SensorManager.SENSOR_DELAY_GAME);
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            sm.unregisterListener(this);
        } catch (Throwable ignored) {
        }
        if (torchOn) setTorch(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (torchOn) {
            try { camMgr.setTorchMode(torchCam, false); } catch (Exception ignored) {}
        }
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        try {
            if (e.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                gravity[0] = e.values[0]; gravity[1] = e.values[1]; gravity[2] = e.values[2];
            } else if (e.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                geomag[0] = e.values[0]; geomag[1] = e.values[1]; geomag[2] = e.values[2];
            }
            // 方位角
            if (hasCompass) {
                float[] R = new float[9], I = new float[9], out = new float[3];
                if (SensorManager.getRotationMatrix(R, I, gravity, geomag)) {
                    SensorManager.getOrientation(R, out);
                    azimuth = (float) Math.toDegrees(out[0]);
                    if (azimuth < 0) azimuth += 360;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                compassView.setAzimuth(azimuth);
                                String dir;
                                if (azimuth < 22.5f || azimuth >= 337.5f) dir = "N";
                                else if (azimuth < 67.5f) dir = "NE";
                                else if (azimuth < 112.5f) dir = "E";
                                else if (azimuth < 157.5f) dir = "SE";
                                else if (azimuth < 202.5f) dir = "S";
                                else if (azimuth < 247.5f) dir = "SW";
                                else if (azimuth < 292.5f) dir = "W";
                                else dir = "NW";
                                compassText.setText(dir + " " + Math.round(azimuth) + "°");
                            } catch (Throwable ignored) {
                            }
                        }
                    });
                }
            }
            // 水平仪
            if (hasLevel && accel != null) {
                float g = (float) Math.sqrt(gravity[0] * gravity[0] + gravity[1] * gravity[1] + gravity[2] * gravity[2]);
                if (g > 0.5f) {
                    tiltX = (float) Math.toDegrees(Math.asin(gravity[0] / g));
                    tiltY = (float) Math.toDegrees(Math.asin(gravity[1] / g));
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            levelView.setTilt(tiltX, tiltY);
                            levelText.setText(String.format("水平：X %.1f° / Y %.1f°", tiltX, tiltY));
                        } catch (Throwable ignored) {
                        }
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    /** 指南针表盘：刻度 + 方位 + 指向指针（玻璃渐变盘面） */
    class CompassView extends View {
        private float az = 0;
        private final Paint tickP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint majP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dirP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint needP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint capP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillP = new Paint(Paint.ANTI_ALIAS_FLAG);

        CompassView(Context c) {
            super(c);
            tickP.setColor(0x66000000);
            tickP.setStrokeWidth(dp(1));
            majP.setColor(0x88000000);
            majP.setStrokeWidth(dp(2));
            dirP.setColor(0xAA000000);
            dirP.setTextSize(dp(12));
            dirP.setTypeface(Typeface.DEFAULT_BOLD);
            dirP.setTextAlign(Paint.Align.CENTER);
            needP.setColor(0xFFE53935);
            needP.setStrokeWidth(dp(4));
            needP.setStrokeCap(Paint.Cap.ROUND);
            capP.setColor(0xFF1565C0);
            fillP.setStyle(Paint.Style.FILL);
        }

        void setAzimuth(float a) { az = a; invalidate(); }

        @Override
        protected void onDraw(Canvas cv) {
            try {
                int w = getWidth(), h = getHeight();
                float cx = w / 2f, cy = h / 2f;
                float r = Math.max(1, Math.min(w, h) / 2f - dp(8));
                boolean dark = Tools.isDark(SensorToolsActivity.this);

            // 盘面玻璃渐变
            fillP.setShader(new RadialGradient(cx, cy, r,
                    new int[]{0xFFE8EDF7, 0xFFC9D4E8, 0xFF9FB3D6},
                    null, Shader.TileMode.CLAMP));
            cv.drawCircle(cx, cy, r, fillP);
            // 外圈高光
            Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(dp(3));
            rim.setShader(new LinearGradient(cx - r, cy - r, cx + r, cy + r,
                    (int) 0xFF66FFFFFFL, 0x33FFFFFF, Shader.TileMode.CLAMP));
            cv.drawCircle(cx, cy, r, rim);

            // 刻度：转盘随方位旋转
            cv.save();
            cv.rotate(-az, cx, cy);
            for (int i = 0; i < 72; i++) {
                float ang = i * 5f;
                if (ang % 45 == 0) {
                    cv.drawLine(cx, cy - r + dp(10), cx, cy - r + dp(4), majP);
                } else {
                    cv.drawLine(cx, cy - r + dp(12), cx, cy - r + dp(8), tickP);
                }
                cv.rotate(5f, cx, cy);
            }
            // 方向字母
            drawDir(cv, cx, cy, r, 0, "N", 0xFFE53935);
            drawDir(cv, cx, cy, r, 90, "E", 0xAA000000);
            drawDir(cv, cx, cy, r, 180, "S", 0xAA000000);
            drawDir(cv, cx, cy, r, 270, "W", 0xAA000000);
            cv.restore();

            // 固定指针（顶部红针，代表手机朝向）
            cv.save();
            cv.rotate(0, cx, cy);
            cv.drawLine(cx, cy - r + dp(26), cx, cy + dp(6), needP);
            cv.restore();
            // 中心轴
            capP.setShader(new RadialGradient(cx, cy, dp(10),
                    0xFF4FC3F7, 0xFF1565C0, Shader.TileMode.CLAMP));
            cv.drawCircle(cx, cy, dp(9), capP);
            } catch (Throwable ignored) {
            }
        }

        private void drawDir(Canvas cv, float cx, float cy, float r, float ang, String t, int color) {
            dirP.setColor(color);
            float rad = (float) Math.toRadians(ang);
            float tx = cx + (float) Math.sin(rad) * (r - dp(20));
            float ty = cy - (float) Math.cos(rad) * (r - dp(20));
            cv.drawText(t, tx, ty + dp(4), dirP);
        }
    }

    /** 水平仪：十字基准 + 气泡 */
    class LevelView extends View {
        private float tx = 0, ty = 0;
        private final Paint gridP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ringP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bubbleP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillP = new Paint(Paint.ANTI_ALIAS_FLAG);

        LevelView(Context c) {
            super(c);
            gridP.setColor(0x55000000);
            gridP.setStrokeWidth(dp(1));
            ringP.setColor(0x77000000);
            ringP.setStyle(Paint.Style.STROKE);
            ringP.setStrokeWidth(dp(2));
            bubbleP.setShader(new RadialGradient(dp(9), dp(9), dp(16),
                    0xCC4FC3F7, 0x8821ABE3, Shader.TileMode.CLAMP));
        }

        void setTilt(float x, float y) {
            float lim = 25f;
            if (x > lim) x = lim; if (x < -lim) x = -lim;
            if (y > lim) y = lim; if (y < -lim) y = -lim;
            tx = x; ty = y;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            try {
            int w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h / 2f;
            float r = Math.max(1, Math.min(w, h) / 2f - dp(6));
            fillP.setShader(new RadialGradient(cx, cy, r,
                    0xFFE8EDF7, 0xFFA9BAD8, Shader.TileMode.CLAMP));
            cv.drawCircle(cx, cy, r, fillP);
            cv.drawCircle(cx, cy, r, ringP);
            // 十字基准
            cv.drawLine(cx - r + dp(14), cy, cx + r - dp(14), cy, gridP);
            cv.drawLine(cx, cy - r + dp(14), cx, cy + r - dp(14), gridP);
            // 圆环（水平参考）
            cv.drawCircle(cx, cy, r * 0.4f, ringP);
            // 气泡：随倾角移动（反向物理：气泡浮向高处）
            float bx = cx - tx / lim() * r * 0.62f;
            float by = cy + ty / lim() * r * 0.62f;
            cv.drawCircle(bx, by, dp(14), bubbleP);
            // 中心点
            Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
            dot.setColor(0x99000000);
            cv.drawCircle(cx, cy, dp(3), dot);
            } catch (Throwable ignored) {
            }
        }

        private float lim() { return 25f; }
    }

    private LinearLayout glassCard() {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(dp(18), dp(18), dp(18), dp(18));
        ll.setBackground(Tools.liquidGlassCard(this, 24));
        Tools.setElevation(ll, 10);
        return ll;
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
