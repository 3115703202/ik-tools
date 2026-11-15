package com.roomarchive.server;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * 轻量文件提供者：供分享功能把 App 内文件以 content:// 暴露给系统分享面板
 * （Android 7.0+ 禁止直接分享 file:// Uri，否则抛 FileUriExposedException）。
 */
public class IkFileProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File base;
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (path.startsWith("/pub/")) {
            // 公共下载目录：Download/IK工具箱（v4.5.7 起产物默认落这里）
            base = FileStore.publicDir(getContext());
            if (base == null) throw new FileNotFoundException("public dir missing");
        } else if (path.startsWith("/dl/")) {
            base = getContext() == null ? null : getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (base == null) base = getContext() == null ? null : getContext().getFilesDir();
        } else {
            base = getContext() == null ? null : getContext().getCacheDir();
        }
        if (base == null) throw new FileNotFoundException("dir missing");
        // uri 形如 content://com.roomarchive.server.files/share/<name> 或 /dl/<name>
        String name = uri.getLastPathSegment();
        if (name == null) throw new FileNotFoundException("bad uri");
        File f = new File(base, name);
        if (!f.exists()) throw new FileNotFoundException("file missing: " + name);
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    /** 为任意文件生成可分享的 content:// uri（公共目录直接映射，其它目录先拷到缓存） */
    public static Uri uriFor(android.content.Context c, File f) {
        try {
            File pub = FileStore.publicDir(c);
            if (pub != null && f.getParentFile() != null
                    && f.getParentFile().getAbsolutePath().equals(pub.getAbsolutePath())) {
                return Uri.parse("content://com.roomarchive.server.files/pub/" + Uri.encode(f.getName()));
            }
            File cache = new File(c.getCacheDir(), f.getName());
            if (!cache.exists() || cache.length() != f.length()) {
                java.io.FileInputStream in = new java.io.FileInputStream(f);
                java.io.FileOutputStream out = new java.io.FileOutputStream(cache);
                byte[] b = new byte[65536];
                int n;
                while ((n = in.read(b)) > 0) out.write(b, 0, n);
                in.close();
                out.close();
            }
            return Uri.parse("content://com.roomarchive.server.files/share/" + Uri.encode(f.getName()));
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public String getType(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name != null && name.endsWith(".png")) return "image/png";
        if (name != null && name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name != null && name.endsWith(".apk")) return "application/vnd.android.package-archive";
        return "application/octet-stream";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
