package com.roomarchive.server;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.util.Log;

import java.util.concurrent.CopyOnWriteArrayList;

import moe.shizuku.api.BinderContainer;
import rikka.shizuku.Shizuku;

/**
 * 全局 Application：Shizuku 主动授权。
 * 关键：注册 sticky broadcast receiver，即使 App 在 Shizuku 启动后才打开，
 * 系统也会立即回传最后一次 BINDER_RECEIVED 广播（sticky 机制），拿到 binder。
 */
public class App extends Application {

    private static final String TAG = "IKApp";
    private static App instance;
    private final Handler main = new Handler(Looper.getMainLooper());

    private static final String ACTION_BINDER_RECEIVED = "moe.shizuku.api.action.BINDER_RECEIVED";
    private static final String EXTRA_BINDER = "moe.shizuku.privileged.api.intent.extra.BINDER";

    public interface ShizukuStateListener {
        void onShizukuBinderChanged(boolean connected);
    }
    private static final CopyOnWriteArrayList<ShizukuStateListener> listeners = new CopyOnWriteArrayList<>();

    public static void addShizukuListener(ShizukuStateListener l) { listeners.addIfAbsent(l); }
    public static void removeShizukuListener(ShizukuStateListener l) { listeners.remove(l); }
    public static void notifyShizukuChanged() {
        boolean conn = ShizukuManager.isConnected();
        for (ShizukuStateListener l : listeners) l.onShizukuBinderChanged(conn);
    }

    private BroadcastReceiver stickyReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        ensureLauncherAlias();
        registerBackButtons();
        try {
            Shizuku.addBinderReceivedListener(new Shizuku.OnBinderReceivedListener() {
                @Override
                public void onBinderReceived() {
                    Log.d(TAG, "Shizuku binder received (listener)");
                    notifyShizukuChanged();
                    autoRequestPermission();
                }
            });
            Shizuku.addBinderDeadListener(new Shizuku.OnBinderDeadListener() {
                @Override
                public void onBinderDead() {
                    Log.d(TAG, "Shizuku binder dead");
                    notifyShizukuChanged();
                }
            });
            // 注册授权结果监听：请求授权后接收结果
            Shizuku.addRequestPermissionResultListener(new Shizuku.OnRequestPermissionResultListener() {
                @Override
                public void onRequestPermissionResult(int requestCode, int grantResult) {
                    Log.d(TAG, "Shizuku permission result: " + grantResult);
                    notifyShizukuChanged();
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "Shizuku listener register failed", t);
        }

        // 注册 sticky receiver：App 后启动时，系统立即回传最后一次广播
        try {
            stickyReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    try {
                        if (!ACTION_BINDER_RECEIVED.equals(intent.getAction())) return;
                        Parcelable p = intent.getParcelableExtra(EXTRA_BINDER);
                        if (p instanceof BinderContainer) {
                            BinderContainer c = (BinderContainer) p;
                            if (c.binder != null) {
                                Log.d(TAG, "sticky broadcast: binder received");
                                Shizuku.onBinderReceived(c.binder, getPackageName());
                            }
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "sticky receiver error", t);
                    }
                }
            };
            IntentFilter f = new IntentFilter(ACTION_BINDER_RECEIVED);
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                registerReceiver(stickyReceiver, f, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(stickyReceiver, f);
            }
            Log.d(TAG, "sticky receiver registered");
        } catch (Throwable t) {
            Log.w(TAG, "register sticky receiver failed", t);
        }
    }

    /** 升级后如果所有 alias 都被关掉，桌面就没有图标了，这里兜底启用默认 */
    private void ensureLauncherAlias() {
        try {
            String pkg = getPackageName();
            android.content.pm.PackageManager pm = getPackageManager();
            boolean any = false;
            for (String[] p : candyAliases) {
                int st = pm.getComponentEnabledSetting(
                        new android.content.ComponentName(pkg, pkg + ".alias." + p[0]));
                if (st == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                pm.setComponentEnabledSetting(
                        new android.content.ComponentName(pkg, pkg + ".alias.Default"),
                        android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        android.content.pm.PackageManager.DONT_KILL_APP);
            }
        } catch (Throwable t) {
            Log.w(TAG, "ensureLauncherAlias failed", t);
        }
    }

    private static final String[][] candyAliases = new String[][]{
            {"Default", "Calc", "Photo", "Files", "Weather", "Calendar", "Recorder", "Notes",
                    "Browser", "Camera", "Settings", "Clock", "Music", "Video", "Store"}
    };

    /** 所有二级页面自动挂一个左上角返回箭头 */
    private void registerBackButtons() {
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(android.app.Activity a, android.os.Bundle b) {
                if (a instanceof MainActivity) return;
                if (a instanceof SplashActivity) return;
                attachBack(a);
            }
            @Override public void onActivityStarted(android.app.Activity a) {}
            @Override public void onActivityResumed(android.app.Activity a) {}
            @Override public void onActivityPaused(android.app.Activity a) {}
            @Override public void onActivityStopped(android.app.Activity a) {}
            @Override public void onActivitySaveInstanceState(android.app.Activity a, android.os.Bundle b) {}
            @Override public void onActivityDestroyed(android.app.Activity a) {}
        });
    }

    private void attachBack(final android.app.Activity a) {
        try {
            final android.view.View decor = a.getWindow().getDecorView();
            decor.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        android.view.ViewGroup vg = (android.view.ViewGroup) decor;
                        if (vg.findViewWithTag("ik_back") != null) return;
                        float d = a.getResources().getDisplayMetrics().density;
                        int size = (int) (38 * d + 0.5f);
                        int margin = (int) (8 * d + 0.5f);
                        int status = statusBarHeight(a);

                        android.widget.TextView btn = new android.widget.TextView(a);
                        btn.setTag("ik_back");
                        btn.setText("←");
                        btn.setTextSize(17);
                        btn.setGravity(android.view.Gravity.CENTER);
                        btn.setTextColor(Tools.isDark(a) ? 0xFFFFFFFF : 0xFF111827);
                        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                        bg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                        bg.setColor(Tools.isDark(a) ? 0x99202A37 : 0xCCFFFFFF);
                        bg.setStroke((int) (1 * d), Tools.isDark(a) ? 0x55FFFFFF : 0x22000000);
                        btn.setBackground(bg);
                        btn.setElevation(6 * d);
                        btn.setOnClickListener(new android.view.View.OnClickListener() {
                            @Override
                            public void onClick(android.view.View v) {
                                a.finish();
                            }
                        });

                        android.widget.FrameLayout.LayoutParams lp =
                                new android.widget.FrameLayout.LayoutParams(size, size);
                        lp.gravity = android.view.Gravity.TOP | android.view.Gravity.LEFT;
                        lp.leftMargin = margin;
                        lp.topMargin = status + margin;
                        vg.addView(btn, lp);
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private int statusBarHeight(android.content.Context c) {
        int id = c.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (id > 0) return c.getResources().getDimensionPixelSize(id);
        return (int) (24 * c.getResources().getDisplayMetrics().density);
    }

    public static void autoRequestPermission() {
        if (instance == null) return;
        instance.main.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (ShizukuManager.isConnected() && !ShizukuManager.isAuthorized()) {
                        Log.d(TAG, "auto requesting Shizuku permission");
                        Shizuku.requestPermission(ShizukuManager.REQ_PERMISSION);
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "auto request failed", t);
                }
            }
        });
    }

    public static void resumeCheck() {
        autoRequestPermission();
    }
}
