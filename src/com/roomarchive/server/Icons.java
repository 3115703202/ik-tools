package com.roomarchive.server;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.widget.TextView;

import java.util.HashMap;

/**
 * 代码绘制矢量图标系统（无 emoji、无图片资源、无 androidx）。
 * 所有 Path 在 100x100 归一化坐标系内定义，按 key 静态缓存，
 * onDraw 时 canvas.scale(宽/100, 高/100) 后单色填充。
 * 列表滚动零卡顿：禁止每帧重建 Path，onDraw 内不 new 对象。
 */
public class Icons {

    private static final HashMap<String, Path> CACHE = new HashMap<>();

    // ================= 内部 Drawable =================

    static class IconDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String key;
        private int color;

        IconDrawable(String key, int color) {
            this.key = key;
            this.color = color;
            paint.setStyle(Paint.Style.FILL);
        }

        @Override
        public void draw(Canvas canvas) {
            Path p = Icons.path(key);
            if (p == null) return;
            float w = getBounds().width();
            float h = getBounds().height();
            if (w <= 0 || h <= 0) return;
            paint.setColor(color);
            canvas.save();
            canvas.scale(w / 100f, h / 100f);
            canvas.drawPath(p, paint);
            canvas.restore();
        }

        @Override
        public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }

        @Override
        public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); invalidateSelf(); }

        @Override
        public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // ================= 公开 API =================

    /** 按 key 取任意图标，sizeDp 为边长（正方形） */
    public static Drawable of(Context c, String key, int color, int sizeDp) {
        IconDrawable d = new IconDrawable(key, color);
        int s = (int) (sizeDp * c.getResources().getDisplayMetrics().density + 0.5f);
        d.setBounds(0, 0, s, s);
        return d;
    }

    /** 按工具取图标（t.icon / t.color） */
    public static Drawable tool(Context c, Tools.Tool t, int sizeDp) {
        return of(c, t.icon, t.color, sizeDp);
    }

    /** 底部导航图标（home/fav/user），语义上与 of 分开 */
    public static Drawable nav(Context c, String key, int color, int sizeDp) {
        return of(c, key, color, sizeDp);
    }

    /** 把图标设为 TextView 的 compound drawable（按钮/列表行加图标） */
    public static void attachToText(TextView tv, String key, int color, int sizeDp, int gapDp) {
        Drawable d = of(tv.getContext(), key, color, sizeDp);
        tv.setCompoundDrawablesWithIntrinsicBounds(d, null, null, null);
        float dens = tv.getResources().getDisplayMetrics().density;
        tv.setCompoundDrawablePadding((int) (gapDp * dens + 0.5f));
    }

    // ================= Path 缓存与构建 =================

    static Path path(String key) {
        Path cached = CACHE.get(key);
        if (cached != null) return cached;
        Path p = build(key);
        if (p != null) CACHE.put(key, p);
        return p;
    }

    // ---------- 几何助手 ----------
    private static Path P() { return new Path(); }
    private static void c(Path p, float cx, float cy, float r) { p.addCircle(cx, cy, r, Path.Direction.CW); }
    private static void r(Path p, float l, float t, float rr_, float b) { p.addRect(l, t, rr_, b, Path.Direction.CW); }
    private static void rr(Path p, float l, float t, float rr_, float b, float rad) {
        p.addRoundRect(new RectF(l, t, rr_, b), rad, rad, Path.Direction.CW);
    }
    /** 用 hole 路径从 p 上挖洞（差集） */
    private static void hole(Path p, Path h) { p.op(h, Path.Op.DIFFERENCE); }
    private static void poly(Path p, float... pts) {
        p.moveTo(pts[0], pts[1]);
        for (int i = 2; i < pts.length; i += 2) p.lineTo(pts[i], pts[i + 1]);
        p.close();
    }
    /** 标准五角星 */
    private static Path starPath(float cx, float cy, float ro, float ri) {
        Path p = new Path();
        double[] ang = {-90, -18, 54, 126, 198};
        for (int i = 0; i < 5; i++) {
            double a1 = Math.toRadians(ang[i]);
            double a2 = Math.toRadians(ang[i] + 36);
            float ox = cx + (float) Math.cos(a1) * ro;
            float oy = cy + (float) Math.sin(a1) * ro;
            float ix = cx + (float) Math.cos(a2) * ri;
            float iy = cy + (float) Math.sin(a2) * ri;
            if (i == 0) p.moveTo(ox, oy); else p.lineTo(ox, oy);
            p.lineTo(ix, iy);
        }
        p.close();
        return p;
    }

    // ---------- 图形库 ----------
    private static Path build(String key) {
        Path p = P();
        switch (key) {
            // ===== 工具类（key = Tool.id）=====
            case "gobang": c(p, 50, 50, 38); break;
            case "arcade":
                rr(p, 14, 34, 86, 78, 20);
                r(p, 28, 48, 38, 66); r(p, 30, 50, 36, 64); // 十字键
                c(p, 66, 48, 5); c(p, 76, 58, 5);
                break;
            case "jump": // 像素小人
                r(p, 40, 14, 60, 34);
                r(p, 42, 38, 58, 64);
                r(p, 42, 66, 49, 88); r(p, 51, 66, 58, 88);
                r(p, 28, 40, 38, 48); r(p, 62, 40, 72, 48);
                break;
            case "naWa":
                c(p, 50, 56, 34); c(p, 30, 24, 12); c(p, 70, 24, 12);
                break;
            case "hacker": // 终端 >_
                rr(p, 12, 14, 88, 72, 6);
                {
                    Path inner = P(); rr(inner, 18, 22, 82, 64, 3); hole(p, inner);
                    poly(p, 28, 30, 44, 42, 28, 54); // >
                    r(p, 52, 46, 72, 52);            // _
                }
                break;
            case "alipay": // 硬币 ¥
                c(p, 50, 50, 38);
                { Path ring = P(); c(ring, 50, 50, 28); hole(p, ring); }
                poly(p, 34, 26, 50, 42, 66, 26, 60, 22, 50, 33, 40, 22); // 简化 Y 上半
                r(p, 47, 40, 53, 60);
                r(p, 38, 58, 62, 64);
                break;
            case "fps": // 准星
                c(p, 50, 50, 13);
                { Path core = P(); c(core, 50, 50, 5); hole(p, core); }
                r(p, 46, 8, 54, 28); r(p, 46, 72, 54, 92);
                r(p, 8, 46, 28, 54); r(p, 72, 46, 92, 54);
                break;
            case "objdetect": // 放大镜
                c(p, 42, 42, 26);
                { Path lens = P(); c(lens, 42, 42, 16); hole(p, lens); }
                rr(p, 56, 56, 90, 68, 6);
                break;
            case "download": // 下箭头入托盘
                r(p, 44, 14, 56, 52);
                poly(p, 30, 44, 70, 44, 50, 70);
                rr(p, 24, 76, 76, 86, 3);
                break;
            case "netdisk": // 云盘+下载箭头
                rr(p, 14, 18, 86, 66, 12);
                r(p, 46, 56, 54, 78);
                poly(p, 38, 72, 62, 72, 50, 88);
                break;
            case "snake": // 贪吃蛇
                r(p, 15, 40, 35, 50); r(p, 35, 40, 55, 30); r(p, 55, 30, 70, 40);
                c(p, 78, 55, 5);
                break;
            case "g2048": // 2048
                rr(p, 15, 15, 85, 85, 8);
                r(p, 48, 15, 52, 85); r(p, 15, 48, 85, 52);
                break;
            case "tetris": // 俄罗斯方块
                r(p, 25, 50, 45, 70); r(p, 45, 30, 65, 50); r(p, 65, 50, 85, 70);
                break;
            case "sysinfo": // 仪表盘
                c(p, 50, 50, 30);
                r(p, 48, 30, 52, 50);
                c(p, 50, 50, 5);
                break;
            case "history": // 日历
                rr(p, 16, 14, 84, 86, 8);
                { Path inner = P(); rr(inner, 23, 21, 77, 79, 4); hole(p, inner); }
                r(p, 28, 6, 36, 22); r(p, 64, 6, 72, 22);
                r(p, 30, 38, 70, 44); r(p, 30, 50, 58, 56); r(p, 30, 62, 66, 68);
                break;
            case "timecalc": // 沙漏
                poly(p, 30, 12, 70, 12, 55, 44, 45, 44);
                poly(p, 45, 56, 55, 56, 70, 88, 30, 88);
                r(p, 46, 44, 54, 56);
                break;
            case "danmu": // 弹幕屏幕
                rr(p, 8, 22, 92, 78, 6);
                { Path scr = P(); rr(scr, 15, 29, 85, 71, 3); hole(p, scr); }
                r(p, 22, 38, 58, 44); r(p, 32, 52, 72, 58);
                break;
            case "image": // 相框 山+太阳
                rr(p, 10, 14, 90, 86, 8);
                { Path inner = P(); rr(inner, 17, 21, 83, 79, 4); hole(p, inner); }
                c(p, 68, 36, 7);
                poly(p, 17, 79, 38, 50, 54, 66, 68, 52, 83, 79);
                break;
            case "zip": // 压缩盒+拉链
                rr(p, 20, 24, 80, 82, 6);
                { Path body = P(); rr(body, 27, 31, 73, 75, 3); hole(p, body); }
                r(p, 46, 30, 54, 72);
                rr(p, 41, 38, 59, 48, 3);
                break;
            case "duo": // 双球拍+球
                c(p, 32, 38, 16); c(p, 68, 62, 16);
                { Path l1 = P(); c(l1, 32, 38, 9); Path l2 = P(); c(l2, 68, 62, 9);
                  hole(p, l1); hole(p, l2); }
                rr(p, 28, 52, 36, 82, 3); rr(p, 64, 72, 72, 92, 3);
                c(p, 50, 24, 6);
                break;
            case "video": // 摄像机
                rr(p, 10, 30, 64, 70, 6);
                poly(p, 64, 38, 92, 24, 92, 76, 64, 62);
                break;
            case "db": // 声波波形
                r(p, 14, 40, 24, 60);
                r(p, 31, 28, 41, 72);
                r(p, 48, 18, 58, 82);
                r(p, 65, 28, 75, 72);
                r(p, 82, 40, 92, 60);
                break;
            case "qr": // 二维码角格
                rr(p, 10, 10, 38, 38, 3); rr(p, 62, 10, 90, 38, 3); rr(p, 10, 62, 38, 90, 3);
                c(p, 50, 50, 5); c(p, 50, 68, 4); c(p, 68, 50, 4); c(p, 68, 68, 4);
                break;
            case "daily": // 翻开的书
                poly(p, 50, 24, 20, 14, 20, 80, 50, 70);
                poly(p, 50, 24, 80, 14, 80, 80, 50, 70);
                r(p, 47, 22, 53, 72);
                break;
            case "bench": // 闪电
                poly(p, 58, 8, 30, 54, 46, 54, 42, 92, 72, 42, 54, 42);
                break;
            case "server": // 服务器机柜
                rr(p, 18, 10, 82, 90, 6);
                {
                    Path slits = P();
                    r(slits, 28, 22, 72, 30); r(slits, 28, 40, 72, 48); r(slits, 28, 58, 60, 66);
                    hole(p, slits);
                }
                break;
            case "browser": // 地球环
                c(p, 50, 50, 36);
                { Path mid = P(); c(mid, 50, 50, 27); hole(p, mid); }
                r(p, 14, 46, 86, 54); r(p, 46, 14, 54, 86);
                break;
            case "webapp": // 窗口+链接
                rr(p, 12, 18, 88, 74, 6);
                { Path scr = P(); rr(scr, 19, 28, 81, 64, 3); hole(p, scr); }
                c(p, 34, 88, 7); c(p, 66, 88, 7);
                { Path h1 = P(); c(h1, 34, 88, 3.5f); Path h2 = P(); c(h2, 66, 88, 3.5f);
                  hole(p, h1); hole(p, h2); }
                rr(p, 40, 84, 60, 92, 3);
                break;
            case "ai": // 芯片
                rr(p, 26, 26, 74, 74, 8);
                r(p, 38, 10, 45, 26); r(p, 55, 10, 62, 26);
                r(p, 38, 74, 45, 90); r(p, 55, 74, 62, 90);
                r(p, 10, 38, 26, 45); r(p, 10, 55, 26, 62);
                r(p, 74, 38, 90, 45); r(p, 74, 55, 90, 62);
                { Path core = P(); c(core, 50, 50, 12); hole(p, core); }
                break;
            case "text": // 文档+笔
                rr(p, 18, 10, 66, 90, 4);
                { Path lines = P();
                    r(lines, 26, 28, 58, 34); r(lines, 26, 42, 58, 48); r(lines, 26, 56, 48, 62);
                    hole(p, lines); }
                poly(p, 62, 60, 88, 34, 95, 41, 69, 67, 62, 60);
                poly(p, 69, 67, 55, 72, 62, 60);
                break;
            case "files": // 文件夹
                poly(p, 12, 36, 40, 36, 48, 26, 88, 26, 88, 86, 12, 86);
                break;
            case "apps": // 九宫格
                for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++)
                    rr(p, 18 + i * 28, 18 + j * 28, 34 + i * 28, 34 + j * 28, 3);
                break;
            case "wall": // 风景画 山+太阳+树
                rr(p, 8, 16, 92, 84, 6);
                { Path inner = P(); rr(inner, 15, 23, 85, 77, 3); hole(p, inner); }
                c(p, 70, 34, 7);
                poly(p, 15, 77, 36, 46, 50, 62, 62, 50, 85, 77);
                break;
            case "device": // 手机
                rr(p, 32, 8, 68, 92, 10);
                { Path scr = P(); rr(scr, 38, 17, 62, 76, 5); hole(p, scr); }
                r(p, 44, 84, 56, 87);
                break;
            case "update": // 环形箭头
                c(p, 50, 50, 34);
                { Path mid = P(); c(mid, 50, 50, 25); hole(p, mid); }
                poly(p, 62, 12, 90, 30, 58, 34);
                break;
            case "about": // 圆环+信息点
                c(p, 50, 50, 38);
                { Path mid = P(); c(mid, 50, 50, 29); hole(p, mid); }
                c(p, 50, 37, 5);
                rr(p, 46, 52, 54, 72, 4);
                break;
            case "sensor": // 指南针表盘
                c(p, 50, 50, 38);
                { Path mid = P(); c(mid, 50, 50, 29); hole(p, mid); }
                poly(p, 50, 10, 60, 42, 50, 38, 40, 42);
                break;
            case "wldbg": // 无线信号 + 闪电
                c(p, 50, 26, 10);
                c(p, 50, 54, 16);
                poly(p, 44, 68, 58, 84, 50, 80, 44, 92);
                break;

            // ===== 导航类 =====
            case "home":
                poly(p, 50, 10, 88, 44, 78, 44, 78, 90, 22, 90, 22, 44, 12, 44);
                break;
            case "fav": case "star":
                return starPath(50, 52, 38, 16);
            case "user": case "person":
                c(p, 50, 30, 17);
                poly(p, 20, 92, 20, 72, 30, 60, 50, 54, 70, 60, 80, 72, 80, 92);
                break;
            case "game": // 游戏手柄
                rr(p, 10, 30, 90, 70, 18);
                { Path mid = P(); rr(mid, 22, 46, 34, 58, 4); hole(p, mid);
                  Path mid2 = P(); rr(mid2, 66, 46, 78, 58, 4); hole(p, mid2); }
                c(p, 34, 36, 3); c(p, 66, 36, 3);
                break;

            // ===== 常用类 =====
            case "settings": { // 齿轮：外圈+齿+中心洞
                c(p, 50, 50, 30);
                r(p, 45, 6, 55, 22); r(p, 45, 78, 55, 94);
                r(p, 6, 45, 22, 55); r(p, 78, 45, 94, 55);
                r(p, 19, 19, 31, 31); r(p, 69, 19, 81, 31);
                r(p, 19, 69, 31, 81); r(p, 69, 69, 81, 81);
                Path mid = P(); c(mid, 50, 50, 14); hole(p, mid);
                break;
            }
            case "heart":
                c(p, 34, 36, 18); c(p, 66, 36, 18);
                poly(p, 16, 40, 50, 88, 84, 40);
                break;
            case "feedback": case "chat":
                rr(p, 14, 14, 86, 66, 14);
                poly(p, 34, 66, 28, 90, 54, 66);
                break;
            case "plugin":
                rr(p, 22, 22, 78, 78, 8);
                c(p, 50, 12, 9); c(p, 50, 88, 9); c(p, 12, 50, 9); c(p, 88, 50, 9);
                break;
            case "theme": // 半亮半暗：圆+偏位挖洞成月牙
                c(p, 50, 50, 38);
                { Path cut = P(); c(cut, 64, 38, 28); hole(p, cut); }
                break;
            case "copy":
                rr(p, 28, 18, 82, 72, 4);
                rr(p, 18, 30, 72, 84, 4);
                break;
            case "link":
                c(p, 30, 50, 15); c(p, 70, 50, 15);
                { Path h1 = P(); c(h1, 30, 50, 8); Path h2 = P(); c(h2, 70, 50, 8);
                  hole(p, h1); hole(p, h2); }
                rr(p, 38, 44, 62, 56, 6);
                break;
            case "share":
                c(p, 50, 20, 9); c(p, 20, 74, 9); c(p, 80, 74, 9);
                rr(p, 28, 48, 40, 55, 3); rr(p, 60, 48, 72, 55, 3);
                rr(p, 46, 30, 54, 52, 3);
                break;
            case "refresh":
                c(p, 50, 50, 32);
                { Path mid = P(); c(mid, 50, 50, 24); hole(p, mid); }
                poly(p, 60, 10, 90, 28, 58, 36);
                break;
            case "lock":
                rr(p, 22, 42, 78, 88, 8);
                c(p, 50, 34, 18);
                { Path cut = P(); c(cut, 50, 34, 11); hole(p, cut); }
                { Path kh = P(); c(kh, 50, 58, 5); r(kh, 47, 62, 53, 74); hole(p, kh); }
                break;
            case "unlock":
                rr(p, 22, 42, 78, 88, 8);
                r(p, 26, 18, 34, 44); r(p, 66, 18, 74, 44); r(p, 38, 18, 62, 26);
                break;
            case "check":
                poly(p, 14, 54, 38, 78, 88, 26, 78, 17, 38, 60, 25, 47);
                break;
            case "close":
                rr(p, 16, 45, 84, 55, 4); rr(p, 45, 16, 55, 84, 4);
                break;
            case "arrowRight": case "chevronRight":
                if (key.equals("chevronRight")) {
                    poly(p, 36, 20, 68, 50, 36, 80, 26, 70, 50, 50, 26, 30);
                } else {
                    rr(p, 12, 45, 70, 55, 4);
                    poly(p, 52, 24, 90, 50, 52, 76, 60, 56, 72, 50, 60, 44);
                }
                break;
            case "folder": return build("files");
            case "file":
                rr(p, 25, 10, 75, 90, 4);
                { Path lines = P();
                    r(lines, 33, 32, 67, 38); r(lines, 33, 46, 67, 52); r(lines, 33, 60, 55, 66);
                    hole(p, lines); }
                break;
            case "music":
                rr(p, 56, 14, 64, 68, 3);
                c(p, 44, 72, 13);
                rr(p, 64, 14, 86, 22, 4);
                break;
            case "film":
                rr(p, 10, 18, 90, 82, 6);
                { Path inner = P(); rr(inner, 19, 28, 81, 72, 3); hole(p, inner); }
                { Path sp = P();
                    c(sp, 19, 37, 4); c(sp, 19, 50, 4); c(sp, 19, 63, 4);
                    c(sp, 81, 37, 4); c(sp, 81, 50, 4); c(sp, 81, 63, 4);
                    hole(p, sp); }
                break;
            case "camera":
                rr(p, 14, 30, 86, 82, 10);
                rr(p, 36, 18, 64, 32, 6);
                c(p, 50, 56, 15);
                { Path lens = P(); c(lens, 50, 56, 7); hole(p, lens); }
                c(p, 74, 40, 4);
                break;
            case "wifi":
                c(p, 50, 82, 26);
                { Path a1 = P(); c(a1, 50, 82, 17); hole(p, a1); }
                c(p, 50, 82, 40);
                { Path a2 = P(); c(a2, 50, 82, 31); hole(p, a2); }
                c(p, 50, 82, 6);
                break;
            case "globe":
                c(p, 50, 50, 38);
                r(p, 12, 45, 88, 55); r(p, 45, 12, 55, 88);
                { Path eq = P(); c(eq, 50, 50, 29); hole(p, eq); }
                break;
            case "code":
                poly(p, 38, 28, 14, 50, 38, 72, 47, 63, 31, 50, 47, 37);
                poly(p, 62, 28, 86, 50, 62, 72, 53, 63, 69, 50, 53, 37);
                break;
            case "send":
                poly(p, 8, 45, 92, 12, 62, 88, 50, 62, 28, 72);
                break;
            case "clock":
                c(p, 50, 50, 38);
                { Path mid = P(); c(mid, 50, 50, 30); hole(p, mid); }
                rr(p, 47, 20, 53, 50, 3); rr(p, 50, 47, 72, 53, 3);
                break;
            case "cal": return build("history");
            case "flame":
                poly(p, 50, 8, 66, 30, 74, 48, 68, 62, 80, 68, 62, 90, 38, 90, 20, 68, 34, 60, 30, 42, 40, 30);
                break;
            case "gamepad": return build("arcade");
            case "robot":
                rr(p, 24, 26, 76, 66, 10);
                r(p, 47, 10, 53, 26); c(p, 50, 9, 5);
                { Path eyes = P(); c(eyes, 38, 46, 5); c(eyes, 62, 46, 5); hole(p, eyes); }
                rr(p, 36, 74, 64, 88, 4);
                break;
            case "cat":
                c(p, 50, 56, 32);
                poly(p, 24, 34, 20, 8, 44, 24);
                poly(p, 76, 34, 80, 8, 56, 24);
                { Path eyes = P(); c(eyes, 38, 52, 4); c(eyes, 62, 52, 4); hole(p, eyes); }
                break;
            case "dog":
                c(p, 50, 56, 30);
                c(p, 24, 44, 13); c(p, 76, 44, 13);
                { Path nose = P(); c(nose, 50, 64, 5); hole(p, nose); }
                break;
            case "frog": return build("naWa");
            case "smile":
                c(p, 50, 50, 36);
                { Path eyes = P(); c(eyes, 38, 40, 4); c(eyes, 62, 40, 4); hole(p, eyes); }
                c(p, 50, 62, 14);
                { Path mouth = P(); c(mouth, 50, 62, 8); hole(p, mouth); }
                break;
            case "cool":
                c(p, 50, 50, 36);
                rr(p, 24, 36, 76, 52, 5);
                r(p, 45, 38, 55, 50);
                rr(p, 36, 68, 64, 74, 3);
                break;
            case "muscle":
                rr(p, 18, 50, 52, 72, 10);
                rr(p, 48, 28, 72, 56, 10);
                c(p, 74, 38, 13);
                break;
            case "moon":
                c(p, 50, 50, 38);
                { Path cut = P(); c(cut, 64, 36, 30); hole(p, cut); }
                break;
            case "sun":
                c(p, 50, 50, 18);
                r(p, 45, 8, 55, 24); r(p, 45, 76, 55, 92);
                r(p, 8, 45, 24, 55); r(p, 76, 45, 92, 55);
                r(p, 20, 20, 32, 32); r(p, 68, 20, 80, 32);
                r(p, 20, 68, 32, 80); r(p, 68, 68, 80, 80);
                break;
            case "warning":
                poly(p, 50, 10, 92, 86, 8, 86);
                { Path ex = P(); rr(ex, 46, 36, 54, 62, 3); c(ex, 50, 74, 4); hole(p, ex); }
                break;
            case "info":
                c(p, 50, 50, 38);
                { Path mid = P(); c(mid, 50, 50, 29); hole(p, mid); }
                c(p, 50, 37, 5);
                rr(p, 46, 52, 54, 72, 4);
                break;
            case "scan":
                rr(p, 8, 8, 28, 16, 2); rr(p, 8, 8, 16, 28, 2);
                rr(p, 72, 8, 92, 16, 2); rr(p, 84, 8, 92, 28, 2);
                rr(p, 8, 84, 28, 92, 2); rr(p, 8, 72, 16, 92, 2);
                rr(p, 72, 84, 92, 92, 2); rr(p, 84, 72, 92, 92, 2);
                break;
            case "swap":
                rr(p, 46, 16, 54, 84, 4);
                poly(p, 28, 28, 50, 8, 72, 28);
                poly(p, 28, 72, 50, 92, 72, 72);
                break;
            case "play":
                poly(p, 34, 20, 34, 80, 82, 50);
                break;
            case "pause":
                rr(p, 33, 20, 47, 80, 4); rr(p, 53, 20, 67, 80, 4);
                break;
            case "stop":
                rr(p, 28, 28, 72, 72, 6);
                break;
            case "record":
                c(p, 50, 50, 28);
                break;
            case "trash":
                rr(p, 24, 32, 76, 88, 6);
                rr(p, 16, 22, 84, 32, 4);
                rr(p, 40, 12, 60, 20, 3);
                { Path slits = P();
                    r(slits, 36, 44, 41, 78); r(slits, 48, 44, 53, 78); r(slits, 60, 44, 65, 78);
                    hole(p, slits); }
                break;
            case "edit": case "pen":
                poly(p, 62, 12, 88, 38, 42, 84, 20, 92, 28, 70);
                break;
            case "plus":
                rr(p, 44, 16, 56, 84, 4); rr(p, 16, 44, 84, 56, 4);
                break;
            case "minus":
                rr(p, 16, 44, 84, 56, 4);
                break;

            // ===== 额外面向批次代理的补充 key =====
            case "power":
                c(p, 50, 52, 34);
                { Path mid = P(); c(mid, 50, 52, 25); hole(p, mid); }
                rr(p, 46, 10, 54, 40, 4);
                break;
            case "volume":
                poly(p, 14, 38, 32, 38, 52, 18, 52, 82, 32, 62, 14, 62);
                c(p, 64, 50, 10);
                { Path mid = P(); c(mid, 64, 50, 4); hole(p, mid); }
                break;
            case "shield":
                poly(p, 50, 8, 84, 20, 84, 52, 50, 92, 16, 52, 16, 20);
                poly(p, 36, 50, 46, 62, 66, 36, 59, 30, 46, 48, 42, 44);
                break;
            case "dots": case "dotsVertical":
                if (key.equals("dots")) {
                    c(p, 24, 50, 6); c(p, 50, 50, 6); c(p, 76, 50, 6);
                } else {
                    c(p, 50, 24, 6); c(p, 50, 50, 6); c(p, 50, 76, 6);
                }
                break;
            case "mail":
                rr(p, 10, 26, 90, 74, 6);
                poly(p, 10, 30, 50, 58, 90, 30);
                break;
            case "logout":
                rr(p, 38, 14, 66, 86, 8);
                { Path cut = P(); rr(cut, 45, 21, 59, 79, 4); hole(p, cut); }
                rr(p, 20, 44, 64, 56, 4);
                poly(p, 52, 30, 80, 50, 52, 70, 60, 60, 70, 50, 60, 40);
                break;
            case "shikong":
                // 房型库：房子轮廓 + 内部隔间
                rr(p, 18, 40, 82, 86, 6);
                poly(p, 12, 44, 50, 16, 88, 44);
                rr(p, 40, 62, 52, 86, 3);
                { Path cut = P(); rr(cut, 60, 62, 74, 76, 3); hole(p, cut); }
                break;
            default:
                return null;
        }
        return p;
    }
}
