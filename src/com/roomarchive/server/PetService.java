package com.roomarchive.server;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/** 桌面宠物悬浮窗，可拖动。 */
public class PetService extends Service {
    private WindowManager wm; private PetView view;
    @Override public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        view = new PetView(this);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(dp(120), dp(120),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.END; lp.x = dp(8); lp.y = dp(150);
        view.setOnTouchListener(new View.OnTouchListener() {
            float sx, sy; int ox, oy;
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) { sx = e.getRawX(); sy = e.getRawY(); ox = lp.x; oy = lp.y; return true; }
                if (e.getAction() == MotionEvent.ACTION_MOVE) { lp.x = ox - (int)(e.getRawX() - sx); lp.y = oy - (int)(e.getRawY() - sy); wm.updateViewLayout(view, lp); return true; }
                return false;
            }
        });
        try { wm.addView(view, lp); } catch (Throwable t) { stopSelf(); }
    }
    @Override public void onDestroy() { try { if (view != null) wm.removeView(view); } catch (Throwable ignored) {} super.onDestroy(); }
    @Override public int onStartCommand(Intent i, int f, int id) { return START_STICKY; }
    @Override public IBinder onBind(Intent i) { return null; }
    int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }
}
