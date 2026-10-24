/** 穿透内核路径工具：内部私有目录与外部存储双份，
 *  执行时优先内部私有目录（targetSdk 29 下可执行），
 *  外部存储部分设备为 noexec 挂载（鸿蒙/华为尤其如此），仅作冗余备份与迁移源 */
package com.roomarchive.server;

import android.content.Context;
import android.system.Os;

import java.io.File;

public final class KernelPaths {

    /** 外部存储内核目录（/storage/emulated/0/Android/data/<包名>/files/native）：
     *  用于存放内核副本，但部分设备该目录 noexec 不可执行，不能作为唯一路径 */
    public static File nativeDir(Context c) {
        try {
            File ext = c.getExternalFilesDir(null);
            if (ext != null) {
                File d = new File(ext, "native");
                if (d.exists() || d.mkdirs()) return d;
            }
        } catch (Exception ignored) {
        }
        return new File(c.getFilesDir(), "native");
    }

    /** 内部私有目录（/data/user/0/<包名>/files/native）：
     *  targetSdk 29 豁免 W^X 限制，各版本 Android 均可执行，作为首选执行路径 */
    public static File internalDir(Context c) {
        return new File(c.getFilesDir(), "native");
    }

    /** 双向同步内核副本：确保内部与外部目录都有内核文件（内部优先执行，外部兜底） */
    public static File ensureBoth(Context c) {
        File intDir = internalDir(c);
        File extDir = nativeDir(c);
        if (!intDir.exists()) intDir.mkdirs();
        if (!extDir.exists()) extDir.mkdirs();
        String[] names = {"libcloudflared.so", "libfrpc.so"};
        for (String n : names) {
            File a = new File(intDir, n);
            File b = new File(extDir, n);
            if (b.exists() && b.length() > 1000000 && (!a.exists() || a.length() < 1000000)) {
                copy(b, a);   // 外部 → 内部
            }
            if (a.exists() && a.length() > 1000000 && (!b.exists() || b.length() < 1000000)) {
                copy(a, b);   // 内部 → 外部
            }
        }
        return intDir;
    }

    private static void copy(File src, File dst) {
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(src);
            java.io.FileOutputStream out = new java.io.FileOutputStream(dst);
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.close();
            in.close();
        } catch (Exception ignored) {
        }
    }

    /** 执行权限兜底：二进制 0755 + 目录 0711 */
    public static void chmodKernel(File dir, File bin) {
        try {
            bin.setExecutable(true, true);
            Os.chmod(bin.getAbsolutePath(), 0755);
        } catch (Exception ignored) {
        }
        if (dir != null) {
            try {
                dir.setExecutable(true, true);
                Os.chmod(dir.getAbsolutePath(), 0711);
            } catch (Exception ignored) {
            }
        }
    }
}
