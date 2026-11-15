package com.roomarchive.server;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 产物目录统一管理。
 * 默认落在【系统下载目录】/IK工具箱（Download/IK工具箱），用系统文件管理器、电脑 MTP 都能直接看到；
 * 只有公共目录不可写（没给存储权限 / 系统限制）时才回退到应用专属目录。
 */
public class FileStore {

    public static final String SUB = "IK工具箱";

    /** 系统下载目录：/storage/emulated/0/Download/IK工具箱 */
    public static File publicDir(Context c) {
        try {
            File dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (dl == null) return null;
            return new File(dl, SUB);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 目录是否真的可写（建目录 + 写探针，别只看 mkdirs 返回值） */
    public static boolean writable(File d) {
        if (d == null) return false;
        try {
            if (!d.exists() && !d.mkdirs()) return false;
            File probe = new File(d, ".ik_probe");
            FileOutputStream fos = new FileOutputStream(probe);
            fos.write(1);
            fos.close();
            probe.delete();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static File appDir(Context c) {
        File nd = c.getExternalFilesDir(SUB);
        if (nd == null) nd = new File(c.getFilesDir(), SUB);
        nd.mkdirs();
        return nd;
    }

    /** 统一产物目录（下载、录屏、提取、图片编辑都写这里） */
    public static File outDir(Context c) {
        File pub = publicDir(c);
        File nd = (pub != null && writable(pub)) ? pub : appDir(c);
        migrate(c, nd);
        return nd;
    }

    /** 把旧版本留在应用私有目录里的产物搬去新目录，老用户不会丢文件 */
    private static void migrate(Context c, File dest) {
        try {
            List<File> olds = new ArrayList<>();
            File od = c.getExternalFilesDir("时空净化工具箱");
            if (od != null) olds.add(od);
            File ik = c.getExternalFilesDir(SUB);
            if (ik != null && !ik.getAbsolutePath().equals(dest.getAbsolutePath())) olds.add(ik);
            File dl = c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (dl != null) olds.add(dl);
            for (File src : olds) {
                if (src == null || !src.isDirectory()) continue;
                if (src.getAbsolutePath().equals(dest.getAbsolutePath())) continue;
                File[] fs = src.listFiles();
                if (fs == null) continue;
                for (File f : fs) {
                    if (f.isDirectory()) continue;
                    File to = new File(dest, f.getName());
                    if (to.exists()) continue;
                    try {
                        if (!f.renameTo(to)) copy(f, to);
                    } catch (Throwable ignored) {
                    }
                }
                try {
                    File[] left = src.listFiles();
                    if (left != null && left.length == 0) src.delete();
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void copy(File from, File to) {
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(from);
            FileOutputStream out = new FileOutputStream(to);
            byte[] b = new byte[65536];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            in.close();
            out.close();
        } catch (Throwable ignored) {
        }
    }

    /** 所有可能存放产物的目录（列表页要把新旧目录都扫一遍） */
    public static List<File> allDirs(Context c) {
        List<File> list = new ArrayList<>();
        File pub = publicDir(c);
        if (pub != null && pub.isDirectory()) list.add(pub);
        list.add(appDir(c));
        File od = c.getExternalFilesDir("时空净化工具箱");
        if (od != null && od.isDirectory()) list.add(od);
        File dl = c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dl != null && dl.isDirectory()) list.add(dl);
        return list;
    }

    /** 只读权限判断（写公共下载目录要 WRITE_EXTERNAL_STORAGE，targetSdk 29 + legacy） */
    public static boolean hasStoragePermission(Context c) {
        if (Build.VERSION.SDK_INT >= 33) return true; // 13+ 走 legacy 时不再单独校验，写失败由 writable() 兜底
        try {
            return c.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return true;
        }
    }

    /** 进入需要写文件的页面时调用，缺权限就弹一次 */
    public static void ensurePermission(Activity a) {
        if (hasStoragePermission(a)) return;
        try {
            a.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE}, 3001);
        } catch (Throwable ignored) {
        }
    }
}
