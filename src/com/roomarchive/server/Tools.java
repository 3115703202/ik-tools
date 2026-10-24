package com.roomarchive.server;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 功能注册表：全部功能的名称 / 图标 / 描述 / 板块 / 火爆标签 / 收藏状态。
 * 主界面、收藏页、插件管理共用这一份数据。
 */
public class Tools {

    public static class Tool {
        public String id;
        public String name;
        /** 图标键（= Tool.id），配合 Icons 代码绘制矢量图标使用 */
        public String icon;
        /** @Deprecated 兼容旧引用，恒为 ""；新代码一律用 icon + Icons */
        @Deprecated
        public String emoji;
        public String desc;
        public int color;
        public Class<?> activity;
        public boolean hot;
        public String section;

        Tool(String id, String name, String icon, String desc, int color,
             Class<?> activity, boolean hot, String section) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.emoji = "";
            this.desc = desc;
            this.color = color;
            this.activity = activity;
            this.hot = hot;
            this.section = section;
        }
    }

    public static final String SEC_ENT = "娱乐";
    public static final String SEC_TOOL = "工具栏";
    public static final String SEC_SYS = "系统功能";

    public static final Tool[] ALL = {
            // ===== 娱乐 =====
            new Tool("gobang", "AI五子棋", "gobang", "人机对战 · 五子连珠", 0xFF7C3AED,
                    AIGobangActivity.class, true, SEC_ENT),
            new Tool("shikong", "失控进化房型库", "shikong", "Rust房型检索 · 内置浏览器直达", 0xFF8B5CF6,
                    ShikongActivity.class, true, SEC_ENT),
            new Tool("arcade", "怀旧街机", "arcade", "经典贪吃蛇 · 街机手感", 0xFFDB2777,
                    RetroArcadeActivity.class, true, SEC_ENT),
            new Tool("jump", "像素跳跃", "jump", "横版跳跃 · 躲避障碍", 0xFFEA580C,
                    PixelJumpActivity.class, true, SEC_ENT),
            new Tool("naWa", "奶蛙", "naWa", "养成小蛙 · 喂食玩耍", 0xFF16A34A,
                    NaWaActivity.class, false, SEC_ENT),
            new Tool("hacker", "黑客系统", "hacker", "模拟终端 · 绿码狂飙", 0xFF059669,
                    HackerSystemActivity.class, true, SEC_ENT),
            new Tool("alipay", "到账提示音", "alipay", "自定义金额 · 语音播报", 0xFF2563EB,
                    AlipaySoundActivity.class, true, SEC_ENT),
            new Tool("fps", "3D枪战", "fps", "第一人称射击 · 局域网对战", 0xFFE53935,
                    FpsActivity.class, false, SEC_ENT),
            new Tool("snake", "贪吃蛇", "snake", "触屏滑动 · 经典街机", 0xFF00d4ff,
                    SnakeGameActivity.class, true, SEC_ENT),
            new Tool("g2048", "2048", "g2048", "数字合并 · 益智消磨", 0xFFbbada0,
                    Game2048Activity.class, true, SEC_ENT),
            new Tool("tetris", "俄罗斯方块", "tetris", "经典下落 · 消除行", 0xFF00ff88,
                    TetrisGameActivity.class, true, SEC_ENT),

            // ===== 工具栏 =====
            new Tool("objdetect", "物体识别", "objdetect", "实时识别人物车辆 · AI本地推理", 0xFF0EA5E9,
                    ObjectDetectActivity.class, true, SEC_TOOL),
            new Tool("download", "下载中心", "download", "内置下载 · 断点续传", 0xFF2563EB,
                    DownloadActivity.class, true, SEC_TOOL),
            new Tool("netdisk", "网盘解析", "netdisk", "粘贴链接 · 全平台识别 · 磁力下载", 0xFF0EA5E9,
                    NetDiskActivity.class, true, SEC_TOOL),
            new Tool("history", "历史上的今天", "history", "每天一个历史事件", 0xFF0891B2,
                    HistoryTodayActivity.class, false, SEC_TOOL),
            new Tool("timecalc", "时光计算器", "timecalc", "日期差 · 倒计时 · 年龄", 0xFF7C3AED,
                    TimeCalculatorActivity.class, false, SEC_TOOL),
            new Tool("danmu", "手持弹幕", "danmu", "全屏滚动弹幕 · 应援神器", 0xFFE11D48,
                    HandBulletActivity.class, true, SEC_TOOL),
            new Tool("image", "图片工具箱", "image", "12项处理：压缩/转换/裁剪/滤镜/水印等", 0xFF9333EA,
                    ImageToolActivity.class, true, SEC_TOOL),
            new Tool("zip", "ZIP解压", "zip", "解压 / 压缩 / 加密加锁", 0xFFEA580C,
                    ZipToolActivity.class, true, SEC_TOOL),
            new Tool("duo", "Duo双人弹球", "duo", "双人同屏对战 · 谁先丢分", 0xFF0EA5E9,
                    DuoActivity.class, false, SEC_TOOL),
            new Tool("video", "视频剪辑", "video", "提取封面 / 音频 / 信息", 0xFFDC2626,
                    VideoToolActivity.class, true, SEC_TOOL),
            new Tool("db", "音频分贝", "db", "实时音量检测 · 环境噪音", 0xFFD97706,
                    AudioDecibelActivity.class, true, SEC_TOOL),
            new Tool("qr", "二维码", "qr", "生成二维码 · 扫码识别", 0xFF2563EB,
                    QRCodeActivity.class, true, SEC_TOOL),
            new Tool("daily", "每日一签", "daily", "每日寄语 · 心情记录", 0xFF65A30D,
                    DailyYouActivity.class, false, SEC_TOOL),
            new Tool("bench", "性能跑分", "bench", "CPU/内存/存储跑分", 0xFFF59E0B,
                    PerformanceActivity.class, true, SEC_TOOL),
            new Tool("sensor", "传感器工具箱", "sensor", "指南针 · 水平仪 · 手电筒", 0xFF0D9488,
                    SensorToolsActivity.class, true, SEC_TOOL),

            // ===== 系统功能 =====
            new Tool("server", "服务器", "server", "房型库网页一键分享", 0xFF2563EB,
                    ServerActivity.class, false, SEC_SYS),
            new Tool("browser", "浏览器", "browser", "轻量快速 · 无图省流", 0xFF3B82F6,
                    BrowserActivity.class, false, SEC_SYS),
            new Tool("webapp", "网页转应用", "webapp", "网页/HTML 封装成应用", 0xFF7C3AED,
                    WebAppsActivity.class, false, SEC_SYS),
            new Tool("ai", "内置小AI", "ai", "离线问答 + 联网全能", 0xFF0891B2,
                    AiChatActivity.class, false, SEC_SYS),
            new Tool("text", "文本编辑器", "text", "写文本存成文件", 0xFF16A34A,
                    TextEditorActivity.class, false, SEC_SYS),
            new Tool("files", "我的文件", "files", "所有产物都在这里", 0xFF7C3AED,
                    MyFilesActivity.class, false, SEC_SYS),
            new Tool("apps", "应用管理", "apps", "提取APK/卸载/停用", 0xFF0D9488,
                    AppListActivity.class, false, SEC_SYS),
            new Tool("wall", "壁纸", "wall", "默认/自定义/3D视差", 0xFF1E40AF,
                    WallpaperActivity.class, false, SEC_SYS),
            new Tool("device", "设备检测", "device", "机型/系统/传感器", 0xFFD97706,
                    DeviceInfoActivity.class, false, SEC_SYS),
            new Tool("sysinfo", "系统信息", "sysinfo", "电池/WiFi/网速/Ping", 0xFF0EA5E9,
                    SystemInfoActivity.class, true, SEC_SYS),
            new Tool("translate", "翻译", "translate", "中↔英 互译", 0xFF7C3AED,
                    TranslateActivity.class, true, SEC_TOOL),
            new Tool("weather", "天气", "weather", "实时天气+3天预报", 0xFF3B82F6,
                    WeatherActivity.class, true, SEC_TOOL),
            new Tool("express", "物流查询", "express", "快递单号查询", 0xFFF59E0B,
                    ExpressActivity.class, true, SEC_TOOL),
            new Tool("screenrec", "录屏", "screenrec", "屏幕录制 MP4", 0xFFE11D48,
                    ScreenRecordActivity.class, true, SEC_TOOL),
            new Tool("autoclick", "自动点击", "autoclick", "无障碍模拟点击", 0xFF059669,
                    AutoClickActivity.class, true, SEC_TOOL),
            new Tool("wldbg", "无线调试", "wldbg", "Shizuku 免root · 分步引导", 0xFF0EA5E9,
                    WirelessDebugActivity.class, true, SEC_SYS),
            new Tool("about", "关于", "about", "版本/下载/检查更新/隐私", 0xFF64748B,
                    AboutActivity.class, false, SEC_SYS),
    };

    private static final String SP = "ik_tools";
    private static final String KEY_FAV = "fav_set";

    /** 取某板块的全部功能 */
    public static List<Tool> ofSection(String section) {
        List<Tool> list = new ArrayList<>();
        for (Tool t : ALL) {
            if (t.section.equals(section)) list.add(t);
        }
        return list;
    }

    public static Tool byId(String id) {
        for (Tool t : ALL) {
            if (t.id.equals(id)) return t;
        }
        return null;
    }

    public static Set<String> favSet(Context c) {
        return c.getSharedPreferences(SP, Context.MODE_PRIVATE)
                .getStringSet(KEY_FAV, new HashSet<String>());
    }

    public static void setFav(Context c, String id, boolean fav) {
        SharedPreferences.Editor e = c.getSharedPreferences(SP, Context.MODE_PRIVATE).edit();
        Set<String> s = new HashSet<>(favSet(c));
        if (fav) s.add(id); else s.remove(id);
        e.putStringSet(KEY_FAV, s).apply();
    }

    public static boolean isFav(Context c, String id) {
        return favSet(c).contains(id);
    }

    /** 主题：0 浅色 / 1 深色 */
    public static void setDark(Context c, boolean dark) {
        c.getSharedPreferences(SP, Context.MODE_PRIVATE)
                .edit().putInt("theme", dark ? 1 : 0).apply();
    }

    public static boolean isDark(Context c) {
        return c.getSharedPreferences(SP, Context.MODE_PRIVATE).getInt("theme", 0) == 1;
    }

    public static int bgColor(Context c) {
        return isDark(c) ? 0xFF10131A : 0xFFF2F4F8;
    }

    public static int cardColor(Context c) {
        return isDark(c) ? 0xFF1C212E : 0xFFFFFFFF;
    }

    public static int textColor(Context c) {
        return isDark(c) ? 0xFFE6E9F2 : 0xFF1E2634;
    }

    public static int subTextColor(Context c) {
        return isDark(c) ? 0xFF8A92A6 : 0xFF787E8C;
    }

    // ================= 玻璃立体风格 UI 助手 =================

    /** 渐变背景（毛玻璃质感基底）——浅色：雾蓝紫渐变 + 4 个彩色光斑（玻璃透出色彩，毛玻璃感知关键）；
     *  深色：深蓝紫渐变 + 幽蓝光斑 */
    public static GradientDrawable bgDrawable(Context c) {
        GradientDrawable g;
        if (isDark(c)) {
            g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0xFF0C1220, 0xFF182A44, 0xFF0E1A30});
        } else {
            g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0xFFE9F2FF, 0xFFDCEBFF, 0xFFEFE9FF});
        }
        return g;
    }

    /** 全屏背景：渐变底 + 彩色光斑层（玻璃卡片/导航透出光斑 → 真实毛玻璃观感） */
    public static android.graphics.drawable.Drawable bgWithGlow(Context c) {
        boolean dark = isDark(c);
        float dens = c.getResources().getDisplayMetrics().density;
        Glow[] glows = dark
                ? new Glow[]{
                    new Glow(0x593C7BFF, 0.08f, 0.06f, 0.42f),
                    new Glow(0x3D9D4EFF, 0.92f, 0.12f, 0.38f),
                    new Glow(0x408B5CF6, 0.86f, 0.60f, 0.45f),
                    new Glow(0x4027D9E0, 0.12f, 0.85f, 0.40f)}
                : new Glow[]{
                    new Glow(0x6638BDF8, 0.06f, 0.04f, 0.46f),
                    new Glow(0x6694A3FF, 0.95f, 0.10f, 0.40f),
                    new Glow(0x55C084FC, 0.90f, 0.66f, 0.44f),
                    new Glow(0x55FF8FD8, 0.10f, 0.88f, 0.42f)};
        android.graphics.drawable.Drawable[] layers = new android.graphics.drawable.Drawable[1 + glows.length];
        layers[0] = bgDrawable(c);
        System.arraycopy(glows, 0, layers, 1, glows.length);
        android.graphics.drawable.LayerDrawable ld =
                new android.graphics.drawable.LayerDrawable(layers);
        ld.setLayerInsetTop(0, 0);
        return ld;
    }

    /** 大半径彩色光斑（背景装饰层，玻璃透出它才有模糊感） */
    static class Glow extends android.graphics.drawable.Drawable {
        final android.graphics.Paint p = new android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG);
        final int color;
        final float cx, cy, r;
        Glow(int color, float cx, float cy, float r) {
            this.color = color;
            this.cx = cx;
            this.cy = cy;
            this.r = r;
        }
        @Override public void draw(android.graphics.Canvas canvas) {
            android.graphics.Rect b = getBounds();
            float x = b.left + cx * b.width();
            float y = b.top + cy * b.height();
            float rr = r * Math.min(b.width(), b.height());
            android.graphics.RadialGradient g = new android.graphics.RadialGradient(
                    x, y, rr,
                    new int[]{color, 0x00000000},
                    new float[]{0f, 1f},
                    android.graphics.Shader.TileMode.CLAMP);
            p.setShader(g);
            canvas.drawCircle(x, y, rr, p);
        }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter cf) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    /** 玻璃卡片：半透明 + 高光描边 + 圆角 */
    public static GradientDrawable glassCard(Context c, float radiusDp) {
        boolean dark = isDark(c);
        GradientDrawable g = new GradientDrawable();
        g.setColor(dark ? 0xFF242C3C : 0xFFFFFFFF);
        g.setCornerRadius(radiusDp * c.getResources().getDisplayMetrics().density + 0.5f);
        g.setStroke((int) (1f * c.getResources().getDisplayMetrics().density + 0.5f),
                dark ? 0x38FFFFFF : 0x00FFFFFF);
        return g;
    }

    /** 立体阴影（玻璃悬浮感） */
    public static void setElevation(View v, float dp) {
        v.setElevation(dp * v.getResources().getDisplayMetrics().density);
    }

    /** 文字渐变着色 */
    public static void gradientText(TextView t, int[] colors) {
        float w = t.getPaint().measureText(t.getText().toString());
        if (w <= 0) w = t.getTextSize() * 4;
        t.getPaint().setShader(new LinearGradient(0, 0, w, 0,
                colors, null, Shader.TileMode.CLAMP));
        t.invalidate();
    }

    /** 液态玻璃卡片（参照开源项目 AndroidLiquidGlass 视觉自实现，v2.0.10 质感升级）：
     *  半透明底（更透，能透出背景渐变）+ 中部玻璃反光带 + 顶部高光 + 底部内阴影 + 白色边缘亮边
     *  零依赖、全版本兼容。 */
    public static android.graphics.drawable.Drawable liquidGlassCard(Context c, float radiusDp) {
        boolean dark = isDark(c);
        float dens = c.getResources().getDisplayMetrics().density;
        float r = radiusDp * dens + 0.5f;
        int strokePx = (int) (2f * dens + 0.5f);

        // 1. 半透明底（更通透，透出背景光斑 → 玻璃感更强）
        GradientDrawable base = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                dark ? new int[]{0x9E2A3246, 0x6E242B3B}
                     : new int[]{0xD2FFFFFF, 0x99FFFFFF});
        base.setCornerRadius(r);

        // 2. 中部玻璃反光带（iOS 液态玻璃标志性：中间竖向柔和亮带）
        GradientDrawable midGlow = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                dark ? new int[]{0x00FFFFFF, 0x3AFFFFFF, 0x00FFFFFF}
                     : new int[]{0x00FFFFFF, 0x52FFFFFF, 0x00FFFFFF});
        midGlow.setCornerRadius(r);

        // 3. 顶部高光（增强：上缘亮光往下渐隐）
        GradientDrawable topGlow = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                dark ? new int[]{0x9EFFFFFF, 0x00FFFFFF}
                     : new int[]{0xE6FFFFFF, 0x00FFFFFF});
        topGlow.setCornerRadius(r);

        // 4. 底部内阴影（立体感：下缘暗部）
        GradientDrawable bottomShade = new GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                dark ? new int[]{0x33FFFFFF, 0x00FFFFFF}
                     : new int[]{0x28FFFFFF, 0x00FFFFFF});
        bottomShade.setCornerRadius(r);

        // 5. 边缘亮边（玻璃边缘反光：加宽加亮）
        GradientDrawable stroke = new GradientDrawable();
        stroke.setColor(0x00000000);
        stroke.setCornerRadius(r);
        stroke.setStroke(strokePx, dark ? 0x8CFFFFFF : 0xFFFFFFFF);

        return new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{base, midGlow, topGlow, bottomShade, stroke});
    }

    /** 玻璃搜索栏：半透明白底 + 大圆角 + 细白描边（顶部搜索框） */
    public static GradientDrawable searchBar(Context c) {
        boolean dark = isDark(c);
        float dens = c.getResources().getDisplayMetrics().density;
        float r = 20f * dens + 0.5f;
        GradientDrawable g = new GradientDrawable();
        g.setColor(dark ? 0xB02A3246 : 0xBFFFFFFF);
        g.setCornerRadius(r);
        g.setStroke((int) (1f * dens + 0.5f), dark ? 0x66FFFFFF : 0xDFFFFFFF);
        return g;
    }

    /** 玻璃底部导航条（v2.1.1：5 层液态玻璃，与功能卡同款质感——更透 + 顶部高光 + 中部反光带 + 白边） */
    public static android.graphics.drawable.Drawable liquidGlassBar(Context c) {
        boolean dark = isDark(c);
        float dens = c.getResources().getDisplayMetrics().density;
        float r = 28f * dens + 0.5f;
        float[] radii = new float[]{r, r, r, r, r, r, r, r};

        GradientDrawable base = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                dark ? new int[]{0x9C2A3246, 0x6E242B3B}
                     : new int[]{0xE6FFFFFF, 0xB8FFFFFF});
        base.setCornerRadii(radii);

        GradientDrawable midGlow = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                dark ? new int[]{0x00FFFFFF, 0x24FFFFFF, 0x00FFFFFF}
                     : new int[]{0x00FFFFFF, 0x33FFFFFF, 0x00FFFFFF});
        midGlow.setCornerRadii(radii);

        GradientDrawable topGlow = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                dark ? new int[]{0x59FFFFFF, 0x00FFFFFF}
                     : new int[]{0x99FFFFFF, 0x00FFFFFF});
        topGlow.setCornerRadii(radii);

        GradientDrawable stroke = new GradientDrawable();
        stroke.setCornerRadii(radii);
        stroke.setStroke((int) (1.2f * dens + 0.5f), dark ? 0x59FFFFFF : 0xE6FFFFFF);

        return new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{base, midGlow, topGlow, stroke});
    }

    /** 底部导航选中图标：玻璃渐变圆底（渐变 + 顶部高光 + 白描边） */
    public static android.graphics.drawable.Drawable glassNavIconBg(Context c, boolean dark) {
        float dens = c.getResources().getDisplayMetrics().density;
        float r = 17.5f * dens + 0.5f;
        GradientDrawable base = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                dark ? new int[]{0xFF2563EB, 0xFF8B5CF6}
                     : new int[]{0xFF2563EB, 0xFF7C3AED});
        base.setCornerRadius(r);
        GradientDrawable glow = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x59FFFFFF, 0x00FFFFFF});
        glow.setCornerRadius(r);
        GradientDrawable stroke = new GradientDrawable();
        stroke.setCornerRadius(r);
        stroke.setStroke((int) (1f * dens + 0.5f), 0xFFFFFFFF);
        return new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{base, glow, stroke});
    }

    /** 玻璃底部导航条（旧版，保留兼容） */
    public static GradientDrawable glassBar(Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadii(new float[]{
                dp(c, 20), dp(c, 20), dp(c, 20), dp(c, 20), 0, 0, 0, 0});
        if (isDark(c)) {
            g.setColor(0xCC131A29);
            g.setStroke(dp(c, 1), 0x40FFFFFF);
        } else {
            g.setColor(0xF2FFFFFF);
            g.setStroke(dp(c, 1), 0xB3FFFFFF);
        }
        return g;
    }

    // ================= 设计系统辅助（v3.0 UI 升级地基） =================

    /** dp 转 px（公开版，供各页面统一使用） */
    public static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 主题自适应状态栏：深色→深底深色图标；浅色→浅底浅色图标。
     *  供不继承 BaseActivity 的页面（Download/Update/WebApps/WebAppViewer 等）调用。 */
    public static void applyStatusBar(Activity a) {
        boolean dark = isDark(a);
        if (Build.VERSION.SDK_INT >= 21) {
            a.getWindow().setStatusBarColor(dark ? 0xFF10131A : 0xFFF2F4F8);
        }
        if (Build.VERSION.SDK_INT >= 23) {
            int flags = a.getWindow().getDecorView().getSystemUiVisibility();
            if (dark) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            a.getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    /** 强调色：浅蓝 / 深蓝 */
    public static int accentColor(Context c) {
        return isDark(c) ? 0xFF60A5FA : 0xFF2563EB;
    }

    /** 强调渐变：按钮 / 选中态 / 渐变字统一蓝紫 */
    public static int[] accentGradient(Context c) {
        return isDark(c)
                ? new int[]{0xFF3B82F6, 0xFF8B5CF6}
                : new int[]{0xFF2563EB, 0xFF7C3AED};
    }

    /** 胶囊按钮底：filled=蓝紫渐变白字；非 filled=透明描边主题色。圆角=高度/2。 */
    public static GradientDrawable pillButton(Context c, int heightDp, boolean filled) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(c, heightDp) / 2f);
        if (filled) {
            g.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
            g.setColors(accentGradient(c));
        } else {
            g.setColor(0x00000000);
            g.setStroke(dp(c, 1), accentColor(c));
        }
        return g;
    }

    /** 内页统一标题：22sp bold，textColor */
    public static TextView pageTitle(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(22);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(textColor(c));
        return t;
    }

    /** 板块标题：16sp bold + 左侧 4dp 蓝紫渐变竖条装饰 */
    public static TextView sectionTitle(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(textColor(c));
        GradientDrawable bar = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, accentGradient(c));
        bar.setSize(dp(c, 4), dp(c, 18));
        t.setCompoundDrawablesWithIntrinsicBounds(bar, null, null, null);
        t.setCompoundDrawablePadding(dp(c, 8));
        return t;
    }

    /** 图标衬底：color 的 18% 透明度圆角方块，配合 Icons 图标使用 */
    public static GradientDrawable iconTile(Context c, int color, int sizeDp, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor((0x2E << 24) | (color & 0x00FFFFFF));
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }
}
