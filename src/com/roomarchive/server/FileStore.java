package com.roomarchive.server;

import android.content.Context;

import java.io.File;

/** 产物目录统一管理：IK工具箱 专属目录，首次使用时自动迁移旧「时空净化工具箱」目录里的文件 */
public class FileStore {

    public static File outDir(Context c) {
        File nd = c.getExternalFilesDir("IK工具箱");
        if (nd == null) nd = new File(c.getFilesDir(), "IK工具箱");
        nd.mkdirs();
        // 迁移旧目录（如果存在）
        try {
            File od = c.getExternalFilesDir("时空净化工具箱");
            if (od == null) od = new File(c.getFilesDir(), "时空净化工具箱");
            if (od.exists()) {
                File[] fs = od.listFiles();
                if (fs != null) {
                    for (File f : fs) {
                        try {
                            f.renameTo(new File(nd, f.getName()));
                        } catch (Exception ignored) {
                        }
                    }
                }
                od.delete();
            }
        } catch (Exception ignored) {
        }
        return nd;
    }
}
