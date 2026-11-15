package com.roomarchive.server;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

/** 滚动长截图：用户自己慢慢往下滑，程序按重叠区域自动拼接 */
public class LongShotActivity extends BaseActivity {

    private static final int REQ_PROJ = 3001;
    private MediaProjectionManager mpm;
    private MediaProjection projection;
    private VirtualDisplay vd;
    private ImageReader reader;
    private Handler ui = new Handler(Looper.getMainLooper());
    private Handler loop;
    private int w, h, dpi;

    private TextView state, action, saveBtn;
    private ProgressBar bar;
    private ImageView preview;

    private Bitmap result;
    private Canvas canvas;
    private int resultH = 0;
    private int[] prevPix;
    private boolean capturing = false;
    private int frames = 0;
    private android.view.WindowManager wm;
    private TextView overlayBtn;

    private static final int MAX_H = 10000;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Tools.bgWithGlow(this));
        root.setPadding(dp(16), dp(22), dp(16), dp(24));

        TextView title = Tools.pageTitle(this, "滚动长截图");
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView sub = new TextView(this);
        sub.setText("点开始后，自己慢慢往下滑页面，程序自动拼成一张");
        sub.setTextSize(12);
        sub.setTextColor(Tools.subTextColor(this));
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(6), 0, dp(12));
        root.addView(sub);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(Tools.liquidGlassCard(this, 22));
        Tools.setElevation(card, 10);
        root.addView(card);

        state = new TextView(this);
        state.setText("未开始");
        state.setTextSize(13);
        state.setTextColor(Tools.subTextColor(this));
        card.addView(state);

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(0);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(6));
        bl.topMargin = dp(8);
        card.addView(bar, bl);

        preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams pvl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(220));
        pvl.topMargin = dp(10);
        card.addView(preview, pvl);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rl.topMargin = dp(12);
        root.addView(row, rl);

        action = new TextView(this);
        action.setText("开始");
        action.setGravity(Gravity.CENTER);
        action.setTextSize(14);
        action.setTextColor(0xFFFFFFFF);
        action.setBackground(Tools.pillButton(this, 44, true));
        row.addView(action, new LinearLayout.LayoutParams(0, dp(44), 1));
        action.setOnClickListener(v -> {
            if (!capturing) requestProjection();
        });

        saveBtn = new TextView(this);
        saveBtn.setText("结束并保存");
        saveBtn.setGravity(Gravity.CENTER);
        saveBtn.setTextSize(14);
        saveBtn.setTextColor(Tools.accentColor(this));
        saveBtn.setBackground(Tools.pillButton(this, 44, false));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, dp(44), 1);
        sl.leftMargin = dp(10);
        row.addView(saveBtn, sl);
        saveBtn.setOnClickListener(v -> stopAndSave());

        TextView tip = new TextView(this);
        tip.setText("· 适合聊天记录、网页、长文、设置列表\n"
                + "· 慢慢滑，每屏至少留一半重叠；一次滑太多会拼不上\n"
                + "· 视频、动画多的页面会跳过那一帧\n"
                + "· 最长 10000 像素，保存到 Download/IK工具箱/长截图");
        tip.setTextSize(12);
        tip.setTextColor(Tools.subTextColor(this));
        tip.setLineSpacing(dp(5), 1f);
        tip.setPadding(dp(16), dp(14), dp(16), dp(14));
        tip.setBackground(Tools.liquidGlassCard(this, 18));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tl.topMargin = dp(14);
        root.addView(tip, tl);

        setContentView(root);

        mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        w = dm.widthPixels; h = dm.heightPixels; dpi = dm.densityDpi;
    }

    private void requestProjection() {
        try {
            startActivityForResult(mpm.createScreenCaptureIntent(), REQ_PROJ);
        } catch (Throwable t) {
            toast("无法发起录屏授权：" + t.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_PROJ || res != RESULT_OK || data == null) return;
        Intent svc = new Intent(this, ShotService.class);
        svc.putExtra("code", res);
        svc.putExtra("data", data);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc); else startService(svc);
        startCapture(res, data);
    }

    private void startCapture(int code, Intent data) {
        try {
            projection = mpm.getMediaProjection(code, data);
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { stopCapture(); }
            }, null);
            reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3);
            vd = projection.createVirtualDisplay("longshot", w, h, dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, null);
            result = Bitmap.createBitmap(w, MAX_H, Bitmap.Config.ARGB_8888);
            canvas = new Canvas(result);
            resultH = 0;
            prevPix = null;
            frames = 0;
            capturing = true;
            action.setText("截取中…（慢慢滑）");
            state.setText("正在拼接 · 0 屏");
            loop = new Handler(Looper.getMainLooper());
            loop.postDelayed(tick, 600);
            showOverlay();
        } catch (Throwable t) {
            toast("启动失败：" + t.getMessage());
        }
    }

    private void showOverlay() {
        try {
            if (!Settings.canDrawOverlays(this)) {
                toast("开始后切到目标页慢慢滑，滑完回到本页点「结束并保存」");
                return;
            }
            wm = (android.view.WindowManager) getSystemService(WINDOW_SERVICE);
            overlayBtn = new TextView(this);
            overlayBtn.setText("结束并保存");
            overlayBtn.setTextSize(12);
            overlayBtn.setTextColor(0xFFFFFFFF);
            overlayBtn.setGravity(Gravity.CENTER);
            overlayBtn.setPadding(dp(14), dp(8), dp(14), dp(8));
            overlayBtn.setBackground(Tools.pillButton(this, 34, true));
            android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Build.VERSION.SDK_INT >= 26
                            ? android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : android.view.WindowManager.LayoutParams.TYPE_PHONE,
                    android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.END;
            lp.y = dp(80);
            lp.x = dp(10);
            wm.addView(overlayBtn, lp);
            overlayBtn.setOnClickListener(v -> stopAndSave());
        } catch (Throwable ignored) {}
    }

    private void hideOverlay() {
        try {
            if (wm != null && overlayBtn != null) wm.removeView(overlayBtn);
        } catch (Throwable ignored) {}
        overlayBtn = null;
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!capturing) return;
            grabFrame();
            if (capturing) loop.postDelayed(this, 700);
        }
    };

    private void grabFrame() {
        try {
            Image img = reader.acquireLatestImage();
            if (img == null) return;
            ByteBuffer buf = img.getPlanes()[0].getBuffer();
            int rowStride = img.getPlanes()[0].getRowStride();
            int pxStride = img.getPlanes()[0].getPixelStride();
            int strideW = rowStride / pxStride;
            Bitmap full = Bitmap.createBitmap(strideW, h, Bitmap.Config.ARGB_8888);
            full.copyPixelsFromBuffer(buf);
            Bitmap frame = full.getWidth() == w ? full : Bitmap.createBitmap(full, 0, 0, w, h);
            img.close();

            int[] pix = new int[w * h];
            frame.getPixels(pix, 0, w, 0, 0, w, h);
            if (prevPix == null) {
                append(frame, 0);
                prevPix = pix;
                frames = 1;
                afterFrame(frame);
                return;
            }
            int d = findScroll(prevPix, pix, w, h);
            if (d < 30) {
                // 没滑动或变化太小，丢弃
                frame.recycle();
                return;
            }
            if (resultH + (h - d) > MAX_H) {
                capturing = false;
                toast("到 10000 像素上限，自动结束");
                stopAndSave();
                return;
            }
            append(frame, d);
            prevPix = pix;
            frames++;
            afterFrame(frame);
        } catch (Throwable ignored) {}
    }

    private void afterFrame(Bitmap frame) {
        ui.post(() -> {
            state.setText("正在拼接 · " + frames + " 屏 · 高度 " + resultH + "px");
            bar.setProgress(Math.min(100, resultH * 100 / MAX_H));
            try { preview.setImageBitmap(frame); } catch (Throwable ignored) {}
        });
    }

    private void append(Bitmap frame, int scroll) {
        int srcTop = scroll;
        int rows = h - scroll;
        if (resultH + rows > MAX_H) rows = MAX_H - resultH;
        Rect src = new Rect(0, srcTop, w, srcTop + rows);
        Rect dst = new Rect(0, resultH, w, resultH + rows);
        canvas.drawBitmap(frame, src, dst, new Paint());
        resultH += rows;
    }

    /** 找新图和旧图的重叠位移：new[y] == old[y+d] */
    private int findScroll(int[] oldPix, int[] newPix, int w, int h) {
        int minD = 30, maxD = Math.min(h / 3, h - 50);
        int bestD = -1;
        double best = Double.MAX_VALUE;
        int[] cols = new int[]{w / 4, w / 2, w * 3 / 4};
        for (int d = minD; d <= maxD; d += 4) {
            double diff = 0;
            int cnt = 0;
            for (int y = 0; y + d < h; y += 8) {
                for (int x : cols) {
                    int a = oldPix[(y + d) * w + x];
                    int b = newPix[y * w + x];
                    int da = Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF));
                    int dg = Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF));
                    int db = Math.abs((a & 0xFF) - (b & 0xFF));
                    diff += da + dg + db;
                    cnt++;
                }
            }
            double avg = diff / Math.max(1, cnt);
            if (avg < best) { best = avg; bestD = d; }
        }
        return best < 18 ? bestD : -1;
    }

    private void stopCapture() {
        capturing = false;
        try { if (loop != null) loop.removeCallbacksAndMessages(null); } catch (Throwable ignored) {}
        try { if (vd != null) vd.release(); } catch (Throwable ignored) {}
        try { if (reader != null) reader.close(); } catch (Throwable ignored) {}
        try { if (projection != null) projection.stop(); } catch (Throwable ignored) {}
        try { stopService(new Intent(this, ShotService.class)); } catch (Throwable ignored) {}
    }

    private void stopAndSave() {
        hideOverlay();
        if (resultH <= 0) { toast("还没有截到内容"); return; }
        stopCapture();
        Bitmap out = Bitmap.createBitmap(result, 0, 0, w, resultH);
        File dir = new File(FileStore.outDir(this), "长截图");
        dir.mkdirs();
        File f = new File(dir, "长截图_" + System.currentTimeMillis() + ".jpg");
        try {
            FileOutputStream fos = new FileOutputStream(f);
            out.compress(Bitmap.CompressFormat.JPEG, 92, fos);
            fos.close();
            preview.setImageBitmap(out);
            state.setText("已保存 · " + resultH + "px");
            toast("已保存：" + f.getAbsolutePath());
        } catch (Throwable t) {
            toast("保存失败：" + t.getMessage());
        }
        action.setText("重新开始");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCapture();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 只负责让 MediaProjection 有前台服务可挂 */
    public static class ShotService extends Service {
        @Override public IBinder onBind(Intent i) { return null; }
        @Override public int onStartCommand(Intent i, int flags, int startId) {
            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    NotificationChannel ch = new NotificationChannel("ikshot", "长截图",
                            NotificationManager.IMPORTANCE_LOW);
                    NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    nm.createNotificationChannel(ch);
                }
                Notification n = new Notification.Builder(this, "ikshot")
                        .setContentTitle("IK工具箱")
                        .setContentText("长截图运行中")
                        .setSmallIcon(android.R.drawable.ic_menu_camera)
                        .build();
                startForeground(78, n);
            } catch (Throwable ignored) {}
            return START_NOT_STICKY;
        }
    }
}
