package com.roomarchive.server;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/** 帧率监测：Choreographer 逐帧计数，悬浮窗实时显示 FPS 与掉帧。 */
public class FpsService extends Service implements Choreographer.FrameCallback {
    private WindowManager wm;
    private TextView view;
    private int frames;
    private long last = System.nanoTime();
    private float fps;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        view = new TextView(this);
        view.setText("FPS  --");
        view.setTextSize(14);
        view.setTextColor(Color.WHITE);
        view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        view.setPadding(dp(10), dp(6), dp(10), dp(6));
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(0xCC071018); g.setCornerRadius(dp(10)); g.setStroke(dp(1), 0xFF34D399);
        view.setBackground(g);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-2, -2,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = dp(12); lp.y = dp(120);
        try { wm.addView(view, lp); } catch (Throwable t) { stopSelf(); return; }
        Choreographer.getInstance().postFrameCallback(this);
    }

    private boolean running = true;
    @Override public void doFrame(long ns) {
        if (!running) return;
        frames++;
        if (ns - last >= 500_000_000L) {
            fps = frames * 1e9f / (ns - last);
            view.setText(String.format(java.util.Locale.CHINA, "FPS %.0f  ·  %.1f ms", fps, fps > 0 ? 1000f / fps : 0));
            view.setTextColor(fps >= 55 ? 0xFF34D399 : fps >= 30 ? 0xFFFBBF24 : 0xFFF87171);
            frames = 0; last = ns;
        }
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override public int onStartCommand(Intent i, int f, int id) { return START_STICKY; }
    @Override public void onDestroy() {
        running = false;
        try { if (view != null && wm != null) wm.removeView(view); } catch (Throwable ignored) {}
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent i) { return null; }
    int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }
}
