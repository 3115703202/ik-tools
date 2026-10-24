package com.roomarchive.server;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** 设备检测：机型 / Android 版本 / 内核 / 芯片 / 传感器支持；深度信息用 Shizuku 补充获取 */
public class DeviceInfoActivity extends BaseActivity {

    private TextView deepBox;
    private LinearLayout sensorBox;

    // 常见传感器检测清单：类型、中文名
    private static final int[][] SENSOR_LIST = {
            {Sensor.TYPE_ACCELEROMETER, 0},
            {Sensor.TYPE_GYROSCOPE, 1},
            {Sensor.TYPE_MAGNETIC_FIELD, 2},
            {Sensor.TYPE_LIGHT, 3},
            {Sensor.TYPE_PROXIMITY, 4},
            {Sensor.TYPE_PRESSURE, 5},
            {Sensor.TYPE_AMBIENT_TEMPERATURE, 6},
            {Sensor.TYPE_STEP_COUNTER, 7},
            {Sensor.TYPE_GRAVITY, 8},
            {Sensor.TYPE_LINEAR_ACCELERATION, 9},
            {Sensor.TYPE_ROTATION_VECTOR, 10},
            {Sensor.TYPE_HEART_RATE, 11},
            {Sensor.TYPE_RELATIVE_HUMIDITY, 12},
            {Sensor.TYPE_SIGNIFICANT_MOTION, 13},
    };
    private static final String[] SENSOR_NAMES = {
            "加速度计", "陀螺仪", "指南针（磁场）", "光线感应", "距离感应",
            "气压计", "温度感应", "计步器", "重力感应", "线性加速度",
            "旋转矢量", "心率监测", "湿度感应", "剧烈运动感应",
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Tools.bgWithGlow(this));
        root.setPadding(dp(16), dp(20), dp(16), dp(18));
        scroll.addView(root);

        TextView title = Tools.pageTitle(this, "设备检测");
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        root.addView(spacer(12));

        // ===== 设备基本信息（普通模式，无需权限）=====
        root.addView(sectionTitle("设备信息"));
        String[] basic = buildBasicInfo();
        for (String line : basic) {
            root.addView(infoRow(line));
        }

        root.addView(spacer(12));

        // ===== 传感器检测 =====
        root.addView(sectionTitle("传感器支持"));
        sensorBox = new LinearLayout(this);
        sensorBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(sensorBox);
        fillSensors();

        root.addView(spacer(12));

        // ===== Shizuku 深度信息 =====
        root.addView(sectionTitle("深度信息（Shizuku 补充）"));
        TextView deepTip = new TextView(this);
        deepTip.setText("普通模式拿不到的芯片平台、完整内核、CPU 信息，可通过 Shizuku（无线调试激活）以 shell 身份读取。");
        deepTip.setTextSize(12);
        deepTip.setTextColor(Tools.subTextColor(this));
        root.addView(deepTip);
        root.addView(spacer(8));

        Button btnDeep = new Button(this);
        btnDeep.setText("用 Shizuku 深度获取");
        btnDeep.setAllCaps(false);
        btnDeep.setTextColor(Color.WHITE);
        btnDeep.setBackground(Tools.pillButton(this, 50, true));
        root.addView(btnDeep);
        btnDeep.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fetchDeep();
            }
        });

        root.addView(spacer(8));

        deepBox = new TextView(this);
        deepBox.setText("（尚未获取）");
        deepBox.setTextSize(12);
        deepBox.setTextColor(Tools.textColor(this));
        deepBox.setTextIsSelectable(true);
        deepBox.setPadding(dp(12), dp(12), dp(12), dp(12));
        android.graphics.drawable.GradientDrawable boxBg = new android.graphics.drawable.GradientDrawable();
        boxBg.setColor(Tools.cardColor(this));
        boxBg.setCornerRadius(dp(12));
        boxBg.setStroke(dp(1), Tools.isDark(this) ? 0xFF2A3040 : 0xFFE4E8EE);
        deepBox.setBackground(boxBg);
        root.addView(deepBox);

        setContentView(scroll);
    }

    /** 设备基本信息（普通 API + 反射系统属性） */
    private String[] buildBasicInfo() {
        List<String> list = new ArrayList<>();
        String brand = Build.BRAND;
        String model = Build.MODEL;
        list.add("机型：" + brand + " " + model);
        list.add("Android 版本：Android " + Build.VERSION.RELEASE
                + "（API " + Build.VERSION.SDK_INT + "）");
        String secPatch = Build.VERSION.SECURITY_PATCH;
        if (secPatch != null && secPatch.length() > 0) {
            list.add("安全补丁：" + secPatch);
        }
        String kernel = System.getProperty("os.version");
        list.add("内核版本：" + (kernel != null ? kernel : "未知"));
        String soc = getSocInfo();
        list.add("处理器/芯片：" + (soc != null ? soc : "未知"));
        String abi = Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0
                ? Build.SUPPORTED_ABIS[0] : "未知";
        list.add("CPU 架构：" + abi);
        list.add("硬件平台：" + Build.HARDWARE);
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        list.add("屏幕：" + dm.widthPixels + "×" + dm.heightPixels + " px，密度 "
                + dm.densityDpi + "dpi");
        list.add("内存：" + fmtBytes(getTotalRam()));
        list.add("可用存储：" + fmtBytes(getTotalStorage()));
        list.add("当前用户：" + (Build.USER != null ? Build.USER : "未知"));
        return list.toArray(new String[0]);
    }

    /** 芯片信息：优先公开 API，缺失则反射系统属性 */
    private String getSocInfo() {
        String soc = "";
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                soc = Build.SOC_MANUFACTURER + " " + Build.SOC_MODEL;
            }
        } catch (Throwable ignored) {
        }
        if (soc == null || soc.trim().isEmpty() || soc.trim().equals("null null")) {
            soc = "";
        }
        if (soc.isEmpty()) {
            String m = sysProp("ro.soc.model");
            if (m == null || m.isEmpty()) m = sysProp("ro.board.platform");
            soc = m != null ? m : "";
        }
        if (soc.isEmpty()) {
            soc = sysProp("ro.hardware");
        }
        return soc;
    }

    /** 反射读取系统属性（普通 App 可读大部分公开属性） */
    private String sysProp(String key) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method m = c.getMethod("get", String.class);
            Object v = m.invoke(null, key);
            return v == null ? null : v.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private long getTotalRam() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            return mi.totalMem;
        } catch (Throwable t) {
            return 0;
        }
    }

    private long getTotalStorage() {
        try {
            File dir = Environment.getDataDirectory();
            StatFs sf = new StatFs(dir.getAbsolutePath());
            return sf.getTotalBytes();
        } catch (Throwable t) {
            return 0;
        }
    }

    /** 传感器支持检测 */
    private void fillSensors() {
        SensorManager sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sm == null) {
            TextView t = new TextView(this);
            t.setText("（无传感器服务）");
            sensorBox.addView(t);
            return;
        }
        List<Sensor> all = sm.getSensorList(Sensor.TYPE_ALL);
        for (int i = 0; i < SENSOR_LIST.length; i++) {
            Sensor s = sm.getDefaultSensor(SENSOR_LIST[i][0]);
            String status = (s != null) ? "支持" : "不支持";
            String extra = "";
            if (s != null && s.getVendor() != null) {
                extra = " ｜ " + s.getVendor();
            }
            sensorBox.addView(sensorRow(SENSOR_NAMES[i], status + extra, s != null));
        }
        TextView extra = new TextView(this);
        extra.setText("本机共检测到 " + all.size() + " 个传感器");
        extra.setTextSize(11);
        extra.setTextColor(Color.rgb(150, 156, 166));
        extra.setPadding(dp(4), dp(6), dp(4), dp(2));
        sensorBox.addView(extra);
    }

    /** Shizuku 深度获取：芯片平台 / 完整内核 / CPU */
    private void fetchDeep() {
        if (!ShizukuManager.isShizukuInstalled(this)) {
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            b.setTitle("需要 Shizuku");
            b.setMessage("深度信息需要 Shizuku（免 root，无线调试激活）。\n点击「去下载」安装并激活后再试。");
            b.setPositiveButton("去下载", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface d, int w) {
                    ShizukuManager.openShizuku(DeviceInfoActivity.this);
                }
            });
            b.setNegativeButton("取消", null);
            b.show();
            return;
        }
        if (!ShizukuManager.isConnected()) {
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            b.setTitle("Shizuku 未运行");
            b.setMessage("请在 Shizuku App 内激活（有 root 直接启动；无 root 用「无线调试启动」）。\n激活后回来重新点击。");
            b.setPositiveButton("打开 Shizuku", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface d, int w) {
                    ShizukuManager.openShizuku(DeviceInfoActivity.this);
                }
            });
            b.setNegativeButton("取消", null);
            b.show();
            return;
        }
        if (!ShizukuManager.isAuthorized()) {
            Toast.makeText(this, "请在 Shizuku 授权弹窗中允许", Toast.LENGTH_LONG).show();
            ShizukuManager.requestPermission(this);
            return;
        }
        deepBox.setText("正在通过 Shizuku 获取…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final StringBuilder sb = new StringBuilder();
                sb.append("【芯片平台】\n");
                sb.append("ro.board.platform: ").append(ShizukuManager.getProp(DeviceInfoActivity.this, "ro.board.platform")).append("\n");
                sb.append("ro.soc.model: ").append(ShizukuManager.getProp(DeviceInfoActivity.this, "ro.soc.model")).append("\n");
                sb.append("ro.soc.manufacturer: ").append(ShizukuManager.getProp(DeviceInfoActivity.this, "ro.soc.manufacturer")).append("\n");
                sb.append("ro.hardware: ").append(ShizukuManager.getProp(DeviceInfoActivity.this, "ro.hardware")).append("\n\n");
                sb.append("【完整内核】\n");
                sb.append(ShizukuManager.execShell(DeviceInfoActivity.this, new String[]{"uname", "-a"})).append("\n\n");
                sb.append("【CPU 信息】\n");
                String cpu = ShizukuManager.execShell(DeviceInfoActivity.this,
                        new String[]{"sh", "-c", "head -20 /proc/cpuinfo"});
                sb.append(cpu != null ? cpu : "（无）");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        deepBox.setText(sb.toString());
                        if (sb.toString().contains("失败") || sb.toString().contains("异常")) {
                            Toast.makeText(DeviceInfoActivity.this,
                                    "部分信息获取失败，请检查 Shizuku 授权", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
    }

    private TextView sectionTitle(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(15);
        t.setTextColor(Color.rgb(30, 64, 175));
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setPadding(dp(2), dp(8), dp(2), dp(6));
        return t;
    }

    private TextView infoRow(String line) {
        TextView t = new TextView(this);
        t.setText(line);
        t.setTextSize(13);
        t.setTextColor(Tools.textColor(this));
        t.setPadding(dp(8), dp(5), dp(8), dp(5));
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private TextView sensorRow(String name, String status, boolean ok) {
        TextView t = new TextView(this);
        t.setText(name + "　" + status);
        t.setTextSize(13);
        t.setTextColor(ok ? (Tools.isDark(this) ? 0xFF22C55E : 0xFF16A34A)
                : Tools.subTextColor(this));
        t.setPadding(dp(8), dp(6), dp(8), dp(6));
        return t;
    }

    private static String fmtBytes(long b) {
        if (b <= 0) return "未知";
        double v = b;
        String[] u = {"B", "KB", "MB", "GB", "TB"};
        int i = 0;
        while (v >= 1024 && i < u.length - 1) {
            v /= 1024;
            i++;
        }
        return String.format(java.util.Locale.US, "%.1f %s", v, u[i]);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private View spacer(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        return v;
    }
}
