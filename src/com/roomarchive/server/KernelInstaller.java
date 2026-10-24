package com.roomarchive.server;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 穿透内核自动安装器：下载内核 zip 并解压 libcloudflared.so / libfrpc.so
 * 到内部私有目录（首选执行路径）+ 外部存储兜底副本。
 * 供 TunnelService 启动隧道前自动检查/补装使用，与下载中心共用同一内核包。
 */
public class KernelInstaller {

    public interface Log {
        void log(String s);
    }

    /** 当前自适应版内核的 md5 前缀（云编译参数自适应版） */
    public static final String ADAPTIVE_MD5 = "8791322fdbea";

    /** 下载穿透内核 zip 并安装；返回 true 表示至少一个内核组件就绪 */
    public static boolean downloadAndInstall(Context ctx, String zipUrl, Log log) {
        File zip = new File(ctx.getCacheDir(), "kernel_dl.zip");
        try {
            if (zip.exists()) zip.delete();
            log.log("自动下载穿透内核…");
            HttpURLConnection c = (HttpURLConnection) new URL(zipUrl).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(90000);
            c.setInstanceFollowRedirects(true);
            int code = c.getResponseCode();
            if (code != 200) {
                log.log("内核下载失败: HTTP " + code);
                return false;
            }
            InputStream in = c.getInputStream();
            FileOutputStream out = new FileOutputStream(zip);
            byte[] buf = new byte[65536];
            int n, total = 0;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                total += n;
            }
            out.close();
            in.close();
            log.log("内核包下载完成: " + String.format("%.1f", total / 1048576f) + "MB，正在安装…");

            File nDir = KernelPaths.ensureBoth(ctx);
            if (!nDir.exists()) nDir.mkdirs();
            ZipFile zf = new ZipFile(zip);
            Enumeration<? extends ZipEntry> es = zf.entries();
            int installed = 0;
            while (es.hasMoreElements()) {
                ZipEntry e = es.nextElement();
                String name = new File(e.getName()).getName();
                if (!name.equals("libcloudflared.so") && !name.equals("libfrpc.so")) continue;
                File target = new File(nDir, name);
                InputStream zi = zf.getInputStream(e);
                FileOutputStream fo = new FileOutputStream(target);
                byte[] b2 = new byte[65536];
                int m;
                while ((m = zi.read(b2)) > 0) fo.write(b2, 0, m);
                fo.close();
                zi.close();
                KernelPaths.chmodKernel(nDir, target);
                installed++;
            }
            zf.close();
            zip.delete();
            KernelPaths.ensureBoth(ctx); // 同步外部存储副本
            log.log("穿透内核已安装（" + installed + " 个组件）");
            return installed > 0;
        } catch (Exception e) {
            log.log("内核自动下载失败: " + e.getMessage());
            try {
                if (zip.exists()) zip.delete();
            } catch (Exception ignore) {
            }
            return false;
        }
    }
}
