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
