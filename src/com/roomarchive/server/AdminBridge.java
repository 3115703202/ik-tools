package com.roomarchive.server;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.List;

/** 管理后台的数据桥：只暴露本机、本 App、本机文件与本地服务记录 */
public class AdminBridge {

    static final String PWD = "3115703202";
    private final Context ctx;
    private boolean unlocked = false;

    public AdminBridge(Context c) { ctx = c.getApplicationContext(); }

    @JavascriptInterface
    public boolean auth(String pwd) {
        pwd = pwd == null ? "" : pwd.replaceAll("[^0-9]", "");
        boolean ok = PWD.equals(pwd);
        if (ok) unlocked = true;
        return ok;
    }

    @JavascriptInterface
    public boolean saveNotice(String json) {
        if (!unlocked) return false;
        try {
            new JSONObject(json);
            NoticeCenter.save(ctx, json);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @JavascriptInterface
    public String data(String pwd) {
        String clean = pwd == null ? "" : pwd.replaceAll("[^0-9]", "");
        if (!unlocked && !PWD.equals(clean)) return "{\"error\":\"unauthorized\"}";
        try {
            JSONObject o = new JSONObject();
            o.put("device", device());
            o.put("app", app());
            o.put("storage", storage());
            o.put("files", files());
            o.put("records", records());
            o.put("tools", tools());
            JSONObject n = NoticeCenter.latest(ctx);
            o.put("notice", n == null ? new JSONObject() : n);
            return o.toString();
        } catch (Throwable t) {
            return "{\"error\":\"" + esc(t.toString()) + "\"}";
        }
    }

    private JSONObject device() throws Exception {
        JSONObject d = new JSONObject();
        d.put("brand", Build.BRAND);
        d.put("model", Build.MODEL);
        d.put("manufacturer", Build.MANUFACTURER);
        d.put("android", Build.VERSION.RELEASE);
        d.put("sdk", Build.VERSION.SDK_INT);
        d.put("abi", Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "?");
        android.util.DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        d.put("screen", dm.widthPixels + " × " + dm.heightPixels);
        d.put("density", dm.densityDpi);
        d.put("cores", Runtime.getRuntime().availableProcessors());
        d.put("heapMb", Runtime.getRuntime().maxMemory() / 1048576);
        return d;
    }

    private JSONObject app() throws Exception {
        JSONObject a = new JSONObject();
        android.content.pm.PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
        a.put("package", ctx.getPackageName());
        a.put("version", pi.versionName);
        a.put("code", pi.versionCode);
        a.put("installTime", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",
                java.util.Locale.US).format(new java.util.Date(pi.firstInstallTime)));
        try {
            int total = ctx.getPackageManager().getInstalledApplications(
                    PackageManager.GET_META_DATA).size();
            a.put("installedApps", total);
        } catch (Throwable t) { a.put("installedApps", -1); }
        return a;
    }

    private JSONObject storage() throws Exception {
        JSONObject s = new JSONObject();
        StatFs in = new StatFs(Environment.getDataDirectory().getAbsolutePath());
        long total = in.getTotalBytes(), free = in.getAvailableBytes();
        s.put("internalTotal", mb(total));
        s.put("internalFree", mb(free));
        s.put("internalUsedPct", total > 0 ? (int) ((total - free) * 100 / total) : 0);
        File out = FileStore.outDir(ctx);
        s.put("appData", mb(dirSize(out, 2)));
        s.put("outDir", out.getAbsolutePath());
        return s;
    }

    private JSONArray files() throws Exception {
        JSONArray arr = new JSONArray();
        File out = FileStore.outDir(ctx);
        File[] kids = out.listFiles();
        if (kids == null) return arr;
        for (File f : kids) {
            JSONObject j = new JSONObject();
            j.put("name", f.getName());
            j.put("dir", f.isDirectory());
            j.put("count", f.isDirectory() ? countFiles(f) : 1);
            j.put("size", mb(f.isDirectory() ? dirSize(f, 4) : f.length()));
            arr.put(j);
        }
        return arr;
    }

    private JSONObject records() throws Exception {
        JSONObject r = new JSONObject();
        SharedPreferences cfg = ctx.getSharedPreferences("server_cfg", Context.MODE_PRIVATE);
        for (String k : cfg.getAll().keySet()) {
            Object v = cfg.getAll().get(k);
            r.put("server_" + k, String.valueOf(v));
        }
        SharedPreferences hist = ctx.getSharedPreferences("browser_history", Context.MODE_PRIVATE);
        r.put("browserHistory", hist.getAll().size());
        r.put("outDirExists", FileStore.outDir(ctx).exists());
        r.put("adminEntry", "抖音 → B站 → 密码 → 二次密码");
        return r;
    }

    private JSONArray tools() throws Exception {
        JSONArray arr = new JSONArray();
        String[] secs = {Tools.SEC_TOOL, Tools.SEC_SYS, Tools.SEC_ENT};
        for (String sec : secs) {
            List<Tools.Tool> list = Tools.ofSection(sec);
            JSONObject g = new JSONObject();
            g.put("section", sec);
            JSONArray names = new JSONArray();
            for (Tools.Tool t : list) names.put(t.name);
            g.put("names", names);
            arr.put(g);
        }
        return arr;
    }

    private int countFiles(File dir) {
        File[] fs = dir.listFiles();
        if (fs == null) return 0;
        int n = 0;
        for (File f : fs) n += f.isDirectory() ? countFiles(f) : 1;
        return n;
    }

    private long dirSize(File dir, int depth) {
        if (dir == null || depth < 0) return 0;
        if (!dir.isDirectory()) return dir.length();
        File[] fs = dir.listFiles();
        if (fs == null) return 0;
        long s = 0;
        for (File f : fs) s += f.isDirectory() ? dirSize(f, depth - 1) : f.length();
        return s;
    }

    private String mb(long bytes) {
        return String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0);
    }

    private String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
