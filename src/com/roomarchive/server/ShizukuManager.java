package com.roomarchive.server;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicBoolean;

import moe.shizuku.server.IShizukuService;
import moe.shizuku.server.IRemoteProcess;
import rikka.shizuku.Shizuku;

/** Shizuku 集成：以 shell 身份静默执行 停用/启用/卸载 应用（配合无线调试激活，免 root） */
public class ShizukuManager {

    private static final String TAG = "ShizukuMgr";
    /** Shizuku App 包名 */
    public static final String SHIZUKU_PACKAGE = "moe.shizuku.privileged.api";
    /** Shizuku 官方下载地址（GitHub Releases，真实存在） */
    public static final String SHIZUKU_DOWNLOAD_URL = "https://github.com/RikkaApps/Shizuku/releases";
    public static final int REQ_PERMISSION = 10086;

    /** Shizuku App 是否已安装 */
    public static boolean isShizukuInstalled(Context ctx) {
        try {
            ctx.getPackageManager().getPackageInfo(SHIZUKU_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** 已连接 Shizuku 服务（binder 已收到） */
    public static boolean isConnected() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 主动向 Shizuku App 请求 binder。
     * Shizuku 启动时只发一次广播；App 后启动会错过，导致"Shizuku 在跑但 App 检测不到"。
     * 这里直接 ContentResolver.call Shizuku App 的 provider 拿当前 binder。
     */
    public static void requestBinderFromManager(Context ctx) {
        try {
            if (!isShizukuInstalled(ctx)) return;
            if (isConnected()) return;
            android.net.Uri uri = android.net.Uri.parse(
                    "content://moe.shizuku.privileged.api.shizuku");
            android.os.Bundle result = ctx.getContentResolver()
                    .call(uri, "getBinder", null, null);
            if (result == null) return;
            result.setClassLoader(moe.shizuku.api.BinderContainer.class.getClassLoader());
            moe.shizuku.api.BinderContainer container = result.getParcelable("container");
            if (container != null && container.binder != null) {
                Shizuku.onBinderReceived(container.binder, ctx.getPackageName());
                Log.d(TAG, "binder received from manager actively");
            }
        } catch (Throwable t) {
            Log.w(TAG, "requestBinderFromManager failed", t);
        }
    }

    /** 已获得授权（可在 Shizuku App 里确认授权状态） */
    public static boolean isAuthorized() {
        try {
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 打开 Shizuku App（未安装时跳下载页） */
    public static void openShizuku(Context ctx) {
        if (!isShizukuInstalled(ctx)) {
            Intent it = new Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse(SHIZUKU_DOWNLOAD_URL));
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                ctx.startActivity(it);
            } catch (Exception e) {
                Log.w(TAG, "open download failed", e);
            }
            return;
        }
        try {
            Intent it = ctx.getPackageManager().getLaunchIntentForPackage(SHIZUKU_PACKAGE);
            if (it != null) ctx.startActivity(it);
        } catch (Exception e) {
            Log.w(TAG, "open shizuku failed", e);
        }
    }

    /** 发起授权请求（弹出 Shizuku 授权界面） */
    public static void requestPermission(Context ctx) {
        try {
            if (isConnected() && !isAuthorized()) {
                Shizuku.requestPermission(REQ_PERMISSION);
            }
        } catch (Throwable t) {
            Log.w(TAG, "requestPermission failed", t);
        }
    }

    /** 执行 pm 命令（走 Shizuku shell 身份），返回 stdout 摘要；失败返回 null */
    public static String execPm(Context ctx, String[] args) {
        if (!isConnected()) return null;
        try {
            IBinder binder = Shizuku.getBinder();
            IShizukuService svc = IShizukuService.Stub.asInterface(binder);
            String[] cmd = new String[args.length + 1];
            cmd[0] = "pm";
            System.arraycopy(args, 0, cmd, 1, args.length);
            IRemoteProcess proc = svc.newProcess(cmd, null, null);
            if (proc == null) return "（Shizuku 拒绝执行）";
            String out = readAll(proc.getInputStream());
            String err = readAll(proc.getErrorStream());
            int code = proc.waitFor();
            if (code != 0) {
                return "失败(exit " + code + ") " + (err != null && err.length() > 0 ? err : out);
            }
            return out != null && out.trim().length() > 0 ? out.trim() : "成功";
        } catch (Throwable t) {
            Log.e(TAG, "execPm failed", t);
            return "执行异常：" + t.getMessage();
        }
    }

    /** 通用 shell 命令执行（Shizuku shell 身份），返回 stdout 摘要；失败返回 null */
    public static String execShell(Context ctx, String[] args) {
        if (!isConnected()) return null;
        try {
            IBinder binder = Shizuku.getBinder();
            IShizukuService svc = IShizukuService.Stub.asInterface(binder);
            IRemoteProcess proc = svc.newProcess(args, null, null);
            if (proc == null) return "（Shizuku 拒绝执行）";
            String out = readAll(proc.getInputStream());
            String err = readAll(proc.getErrorStream());
            int code = proc.waitFor();
            if (code != 0) {
                return "失败(exit " + code + ") " + (err != null && err.length() > 0 ? err : out);
            }
            return out != null && out.trim().length() > 0 ? out.trim() : "成功";
        } catch (Throwable t) {
            Log.e(TAG, "execShell failed", t);
            return "执行异常：" + t.getMessage();
        }
    }

    /** 读取系统属性（Shizuku shell 身份）：getprop <key> */
    public static String getProp(Context ctx, String key) {
        return execShell(ctx, new String[]{"getprop", key});
    }

    private static String readAll(android.os.ParcelFileDescriptor pfd) {
        if (pfd == null) return "";
        InputStream is = new android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd);
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[512];
            int n;
            while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
            return sb.toString().trim();
        } catch (Exception e) {
            return "";
        } finally {
            try { is.close(); } catch (Exception ignored) {}
        }
    }

    /** 强制停用（保留数据，可恢复）：pm disable-user --user 0 <pkg> */
    public static String disableApp(Context ctx, String pkg) {
        return execPm(ctx, new String[]{"disable-user", "--user", "0", pkg});
    }

    /** 恢复启用：pm enable <pkg> */
    public static String enableApp(Context ctx, String pkg) {
        return execPm(ctx, new String[]{"enable", pkg});
    }

    /** 强制卸载（含数据）：pm uninstall <pkg> */
    public static String uninstallApp(Context ctx, String pkg) {
        return execPm(ctx, new String[]{"uninstall", pkg});
    }

    /** 静默卸载当前用户（保留系统已缓存数据）：pm uninstall --user 0 <pkg> */
    public static String uninstallUserApp(Context ctx, String pkg) {
        return execPm(ctx, new String[]{"uninstall", "--user", "0", pkg});
    }
}
