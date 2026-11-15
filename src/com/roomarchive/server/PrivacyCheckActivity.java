package com.roomarchive.server;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** 隐私检查：哪些应用拿了敏感权限 / 悬浮窗 / 无障碍 / 设备管理器 */
public class PrivacyCheckActivity extends BaseActivity {

    private static final String[][] PERMS = {
            {"CAMERA", "相机"},
            {"RECORD_AUDIO", "麦克风"},
            {"ACCESS_FINE_LOCATION", "定位"},
            {"READ_CONTACTS", "通讯录"},
            {"READ_SMS", "短信"},
            {"READ_CALL_LOG", "通话记录"},
            {"CALL_PHONE", "拨号"},
            {"READ_EXTERNAL_STORAGE", "存储"},
    };

    private LinearLayout root;
    private TextView status;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Tools.bgWithGlow(this));
        root.setPadding(dp(18), dp(24), dp(18), dp(24));
        sv.addView(root);

        TextView title = Tools.pageTitle(this, "隐私检查");
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        status = new TextView(this);
        status.setText("正在扫描…");
        status.setTextSize(12);
        status.setTextColor(Tools.subTextColor(this));
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(6), 0, dp(12));
        root.addView(status);

        setContentView(sv);
        scan();
    }

    private void scan() {
        new Thread(() -> {
            StringBuilder out = new StringBuilder();
            try {
                PackageManager pm = getPackageManager();
                out.append("##敏感权限\n");
                for (String[] p : PERMS) {
                    List<PackageInfo> list;
                    if (Build.VERSION.SDK_INT >= 33) {
                        String[] perms = {"android.permission." + p[0]};
                        list = new ArrayList<>();
                        for (ApplicationInfo a : pm.getInstalledApplications(0)) {
                            try {
                                if (pm.checkPermission(perms[0], a.packageName)
                                        == PackageManager.PERMISSION_GRANTED) {
                                    PackageInfo pi = new PackageInfo();
                                    pi.applicationInfo = a;
                                    pi.packageName = a.packageName;
                                    pi.requestedPermissions = perms;
                                    list.add(pi);
                                }
                            } catch (Throwable ignored) {}
                        }
                    } else {
                        list = pm.getPackagesHoldingPermissions(
                                new String[]{"android.permission." + p[0]}, 0);
                    }
                    out.append("P|").append(p[1]).append("|").append(list.size()).append("\n");
                    int n = 0;
                    for (PackageInfo pi : list) {
                        if (pi.packageName.equals(getPackageName())) continue;
                        if (n++ >= 8) break;
                        String label;
                        try { label = pm.getApplicationLabel(pi.applicationInfo).toString(); }
                        catch (Throwable t) { label = pi.packageName; }
                        out.append("A|").append(label).append("\n");
                    }
                    if (list.size() > 9) out.append("A|…还有 ").append(list.size() - 8).append(" 个\n");
                }

                out.append("##悬浮窗\n");
                List<String> overlays = overlayApps(pm);
                out.append("P|悬浮窗应用|").append(overlays.size()).append("\n");
                int i = 0;
                for (String s : overlays) { if (i++ >= 10) break; out.append("A|").append(s).append("\n"); }

                out.append("##无障碍\n");
                List<String> a11y = enabledA11y();
                out.append("P|已启用的无障碍服务|").append(a11y.size()).append("\n");
                for (String s : a11y) out.append("A|").append(s).append("\n");

                out.append("##设备管理器\n");
                int admins = 0;
                try { admins = pm.getInstalledPackages(0).size(); } catch (Throwable ignored) {}
                out.append("A|设备管理器需在系统设置里单独查看\n");
            } catch (Throwable t) {
                out.append("A|扫描失败：").append(t.getMessage()).append("\n");
            }
            final String data = out.toString();
            runOnUiThread(() -> render(data));
        }).start();
    }

    private List<String> overlayApps(PackageManager pm) {
        List<String> list = new ArrayList<>();
        try {
            AppOpsManager ops = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            for (ApplicationInfo a : pm.getInstalledApplications(0)) {
                if (a.packageName.equals(getPackageName())) continue;
                int mode;
                if (Build.VERSION.SDK_INT >= 29) {
                    mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                            a.uid, a.packageName);
                } else {
                    mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                            a.uid, a.packageName);
                }
                if (mode == AppOpsManager.MODE_ALLOWED) {
                    String label;
                    try { label = pm.getApplicationLabel(a).toString(); }
                    catch (Throwable t) { label = a.packageName; }
                    list.add(label);
                }
            }
        } catch (Throwable ignored) {}
        return list;
    }

    private List<String> enabledA11y() {
        List<String> list = new ArrayList<>();
        try {
            String s = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (s == null) return list;
            for (String part : s.split(":")) {
                if (part.trim().isEmpty()) continue;
                list.add(part.replace(getPackageName() + "/", "IK工具箱 · "));
            }
        } catch (Throwable ignored) {}
        return list;
    }

    private void render(String data) {
        root.removeView(status);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int permCount = 0, appCount = 0;
        for (String line : data.split("\n")) {
            if (line.startsWith("##")) {
                TextView h = Tools.sectionTitle(this, line.substring(2));
                h.setPadding(0, dp(16), 0, dp(6));
                box.addView(h);
            } else if (line.startsWith("P|")) {
                String[] p = line.split("\\|");
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setGravity(Gravity.CENTER_VERTICAL);
                card.setPadding(dp(14), dp(12), dp(14), dp(12));
                card.setBackground(Tools.liquidGlassCard(this, 16));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.topMargin = dp(6);
                card.setLayoutParams(lp);
                TextView t = new TextView(this);
                t.setText(p.length > 1 ? p[1] : "");
                t.setTextSize(14);
                t.setTextColor(Tools.textColor(this));
                t.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                card.addView(t);
                TextView n = new TextView(this);
                n.setText((p.length > 2 ? p[2] : "0") + " 个应用");
                n.setTextSize(12);
                n.setTextColor(Tools.accentColor(this));
                card.addView(n);
                box.addView(card);
                permCount++;
            } else if (line.startsWith("A|")) {
                TextView a = new TextView(this);
                a.setText("· " + line.substring(2));
                a.setTextSize(12);
                a.setTextColor(Tools.subTextColor(this));
                a.setPadding(dp(18), dp(2), dp(6), dp(2));
                box.addView(a);
            }
        }
        root.addView(box);
        TextView note = new TextView(this);
        note.setText("只做统计展示，不改任何权限。敏感权限多的应用自己决定要不要留。");
        note.setTextSize(11);
        note.setTextColor(Tools.subTextColor(this));
        note.setPadding(dp(4), dp(18), dp(4), 0);
        root.addView(note);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
