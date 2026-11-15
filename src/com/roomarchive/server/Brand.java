package com.roomarchive.server;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.TextView;

import java.io.File;

/** 用户自定义品牌：名字 + 图片，存在 prefs，图片存在私有目录 */
public class Brand {

    private static final String PREF = "ik_brand";

    public static String name(Context c) {
        String n = c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("name", "");
        return n == null || n.trim().isEmpty() ? "IK工具箱" : n.trim();
    }

    public static File iconFile(Context c) {
        String p = c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("icon", "");
        if (p == null || p.isEmpty()) return null;
        File f = new File(p);
        return f.exists() ? f : null;
    }

    public static Bitmap icon(Context c, int max) {
        File f = iconFile(c);
        if (f == null) return null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int s = 1;
            while (Math.max(o.outWidth, o.outHeight) / s > max) s *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = s;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), o2);
        } catch (Throwable t) {
            return null;
        }
    }

    public static void apply(TextView v) {
        if (v != null) v.setText(name(v.getContext()));
    }

    public static void save(Context c, String name, String iconPath) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString("name", name == null ? "" : name.trim())
                .putString("icon", iconPath == null ? "" : iconPath)
                .apply();
    }
}
