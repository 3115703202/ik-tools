package com.roomarchive.server;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import java.io.File;

/** IK 3D 壁纸：视差动态壁纸——主屏滑动 + 手机倾斜时画面产生 3D 跟随效果 */
public class IKWallpaperService extends WallpaperService {

    @Override
    public Engine onCreateEngine() {
        return new IKEngine();
    }

    private class IKEngine extends Engine implements SensorEventListener {

        private Bitmap base;
        private Bitmap blurLayer;
        private int vw = 1, vh = 1;
        private float offX = 0.5f, offY = 0.5f;
        private float tiltX = 0f, tiltY = 0f;
        private SensorManager sm;
        private Sensor rot;
        private boolean visible = false;
        private boolean hasSensor = false;
        private final Paint paint = new Paint();

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            paint.setFilterBitmap(true);
            paint.setAntiAlias(true);
            sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            if (sm != null) {
                rot = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
                hasSensor = rot != null;
            }
            loadBitmap();
        }

        private void loadBitmap() {
            try {
                File dir = FileStore.outDir(IKWallpaperService.this);
                File f = new File(new File(dir, "3D壁纸"), "current.jpg");
                if (f.exists()) {
                    BitmapFactory.Options opt = new BitmapFactory.Options();
                    opt.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(f.getAbsolutePath(), opt);
                    int sample = 1;
                    while (opt.outWidth / sample > 2880 || opt.outHeight / sample > 2880) sample *= 2;
                    BitmapFactory.Options o2 = new BitmapFactory.Options();
                    o2.inSampleSize = sample;
                    base = BitmapFactory.decodeFile(f.getAbsolutePath(), o2);
                    // 预生成模糊背景层（缩小再放大实现高斯模糊感），增强景深
                    blurLayer = makeBlur(base, 10);
                }
            } catch (Exception ignored) {
                base = null;
                blurLayer = null;
            }
        }

        private Bitmap makeBlur(Bitmap src, int factor) {
            if (src == null) return null;
            try {
                int w = src.getWidth(), h = src.getHeight();
                Bitmap small = Bitmap.createScaledBitmap(src, Math.max(1, w / factor), Math.max(1, h / factor), true);
                Bitmap big = Bitmap.createScaledBitmap(small, w, h, true);
                if (small != big) small.recycle();
                return big;
            } catch (Exception e) {
                return src;
            }
        }

        @Override
        public void onDestroy() {
            unregister();
            if (base != null && !base.isRecycled()) base.recycle();
            if (blurLayer != null && !blurLayer.isRecycled()) blurLayer.recycle();
            base = null;
            blurLayer = null;
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean isVisible) {
            visible = isVisible;
            if (isVisible) {
                register();
                draw();
            } else {
                unregister();
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            vw = Math.max(1, width);
            vh = Math.max(1, height);
            draw();
        }

        @Override
        public void onOffsetsChanged(float x, float y, float xStep, float yStep, int xPixels, int yPixels) {
            offX = x;
            offY = y;
            draw();
        }

        private void register() {
            if (!hasSensor || sm == null) return;
            try {
                sm.registerListener(this, rot, SensorManager.SENSOR_DELAY_GAME);
            } catch (Exception ignored) {
            }
        }

        private void unregister() {
            if (sm == null) return;
            try {
                sm.unregisterListener(this);
            } catch (Exception ignored) {
            }
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                try {
                    float[] rm = new float[9];
                    SensorManager.getRotationMatrixFromVector(rm, event.values);
                    float[] orient = new float[3];
                    SensorManager.getOrientation(rm, orient);
                    // roll 绕 Z（左右倾）、pitch 绕 X（前后倾），归一化到 -1..1
                    tiltX = clamp(-orient[1] / 1.2f, -1f, 1f);
                    tiltY = clamp(orient[2] / 1.2f, -1f, 1f);
                } catch (Exception ignored) {
                }
                draw();
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

        private float clamp(float v, float lo, float hi) {
            if (v < lo) return lo;
            if (v > hi) return hi;
            return v;
        }

        private void draw() {
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas == null) return;
                // 背景铺满
                canvas.drawColor(Color.rgb(8, 10, 18));

                if (base == null) {
                    Paint p = new Paint();
                    p.setColor(Color.rgb(30, 60, 120));
                    canvas.drawRect(0, 0, vw, vh, p);
                    p.setColor(Color.WHITE);
                    p.setTextSize(vw * 0.06f);
                    p.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText("先在 IK工具箱 → 壁纸 → 3D壁纸 中选择图片", vw / 2f, vh / 2f, p);
                    return;
                }

                int bw = base.getWidth(), bh = base.getHeight();
                float scale = Math.max((float) vw / bw, (float) vh / bh) * 1.18f;
                float sw = bw * scale, sh = bh * scale;
                float maxDx = Math.max(0, (sw - vw) / 2f);
                float maxDy = Math.max(0, (sh - vh) / 2f);

                float dx = (offX - 0.5f) * 2f * maxDx * 1.1f + tiltX * maxDx * 0.55f;
                float dy = (offY - 0.5f) * 2f * maxDy * 1.1f + tiltY * maxDy * 0.55f;
                dx = clamp(dx, -maxDx, maxDx);
                dy = clamp(dy, -maxDy, maxDy);

                // 景深背景层：模糊层反向微移（不动感），增强 3D 立体
                if (blurLayer != null) {
                    float bdx = -dx * 0.35f;
                    float bdy = -dy * 0.35f;
                    Rect srcB = new Rect(0, 0, blurLayer.getWidth(), blurLayer.getHeight());
                    Rect dstB = new Rect((int) (vw * 0.5f - sw * 0.5f + bdx - sw * 0.06f),
                            (int) (vh * 0.5f - sh * 0.5f + bdy - sh * 0.06f),
                            (int) (vw * 0.5f + sw * 0.5f + bdx - sw * 0.06f),
                            (int) (vh * 0.5f + sh * 0.5f + bdy - sh * 0.06f));
                    canvas.drawBitmap(blurLayer, srcB, dstB, paint);
                }

                // 前景清晰层：视差移动
                Rect src = new Rect(0, 0, bw, bh);
                Rect dst = new Rect((int) (vw * 0.5f - sw * 0.5f + dx),
                        (int) (vh * 0.5f - sh * 0.5f + dy),
                        (int) (vw * 0.5f + sw * 0.5f + dx),
                        (int) (vh * 0.5f + sh * 0.5f + dy));
                canvas.drawBitmap(base, src, dst, paint);

                // 顶部柔光渐变，提升质感
                Paint g = new Paint();
                g.setShader(new android.graphics.LinearGradient(0, 0, 0, vh,
                        Color.argb(70, 0, 0, 0), Color.TRANSPARENT, android.graphics.Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, vw, vh, g);
            } catch (Exception ignored) {
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }
}
