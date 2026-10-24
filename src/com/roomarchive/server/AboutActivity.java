package com.roomarchive.server;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** 关于：版本 / 下载链接 / 更新日志（时间轴卡片）/ 功能清单 / 隐私。
 *  v3.0 重设计：主题自适应 + 更新日志卡片化时间轴（竖线 + 圆点 + 版本胶囊徽标）。 */
public class AboutActivity extends BaseActivity {

    /** 本软件下载链接（GitHub Pages 官网下载页：他人点开即可下载最新安装包） */
    private static final String DL_URL = "https://3115703202.github.io/ik-tools/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(Tools.bgWithGlow(this));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(24), dp(16), dp(24));
        scroll.addView(root);

        // ===== 页头 =====
        TextView title = new TextView(this);
        title.setText("IK工具箱");
        title.setTextSize(24);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setTextColor(Tools.textColor(this));
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        Tools.gradientText(title, Tools.accentGradient(this));

        TextView ver = new TextView(this);
        String curVer = "?";
        try { curVer = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) {}
        ver.setText("版本 v" + curVer + " · 点击检查更新");
        ver.setTextSize(13);
        ver.setTextColor(Tools.accentColor(this));
        ver.setGravity(Gravity.CENTER);
        ver.setPadding(0, dp(4), 0, 0);
        ver.setClickable(true);
        ver.setOnClickListener(v -> checkUpdate());
        root.addView(ver);

        root.addView(spacer(18));

        // ===== 下载链接区块 =====
        root.addView(Tools.sectionTitle(this, "下载本软件"));
        TextView dlTip = new TextView(this);
        dlTip.setText("复制下面的链接发给朋友，对方粘贴到浏览器即可直接下载安装本软件。");
        dlTip.setTextSize(13);
        dlTip.setTextColor(Tools.subTextColor(this));
        dlTip.setLineSpacing(dp(2), 1.0f);
        dlTip.setPadding(0, dp(8), 0, dp(8));
        root.addView(dlTip);

        TextView linkView = new TextView(this);
        linkView.setText(DL_URL);
        linkView.setTextSize(13);
        linkView.setTextColor(Tools.accentColor(this));
        linkView.setTextIsSelectable(true);
        linkView.setPadding(dp(12), dp(12), dp(12), dp(12));
        linkView.setBackground(Tools.glassCard(this, 12));
        root.addView(linkView);

        root.addView(spacer(8));

        TextView btnCheck = new TextView(this);
        btnCheck.setText("检查更新");
        btnCheck.setTextSize(15);
        btnCheck.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        btnCheck.setTextColor(Color.WHITE);
        btnCheck.setGravity(Gravity.CENTER);
        btnCheck.setPadding(0, dp(14), 0, dp(14));
        btnCheck.setBackground(Tools.pillButton(this, 50, true));
        btnCheck.setClickable(true);
        btnCheck.setOnClickListener(v -> checkUpdate());
        root.addView(btnCheck);

        root.addView(spacer(20));

        // ===== 更新日志（时间轴卡片）=====
        root.addView(Tools.sectionTitle(this, "版本更新记录"));
        root.addView(spacer(4));
        root.addView(buildTimeline());

        root.addView(spacer(20));

        // ===== 功能清单 =====
        root.addView(textSection("功能清单", new String[]{
                "服务器：房型库网页一键分享（局域网）",
                "ZIP 工具：解压、压缩、AES-256 加密加锁、解密",
                "图片工具：旋转、灰度、亮度、缩小、压缩保存",
                "视频工具：提取封面、提取音频、查看视频信息",
                "浏览器：轻量快速、多标签、无图省流",
                "内置小AI：离线问答 + 联网全能",
                "文本编辑器：编辑并保存 .txt 文件",
                "我的文件：查看/分享/删除所有产物",
                "应用管理：提取 APK / 停用 / 卸载 / Shizuku 强制操作",
                "壁纸：默认壁纸 / 自定义 / 3D 视差动态壁纸",
                "设备检测：机型 / 系统 / 内核 / 芯片 / 传感器"
        }));

        root.addView(spacer(14));

        // ===== 隐私声明 =====
        root.addView(textSection("隐私声明", new String[]{
                "本软件永久免费、无广告、无收费",
                "如遇到收费、卡密，是诈骗软件，请勿相信",
                "所有功能完全离线运行，不联网、不收集、不上传任何数据",
                "文件仅在本地处理，加密密码由你保管"
        }));

        setContentView(scroll);
    }

    /** 更新日志时间轴：竖线贯通 + 圆点节点 + 版本胶囊徽标（DesignSpec §5.9） */
    private View buildTimeline() {
        int accent = Tools.accentColor(this);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Tools.liquidGlassCard(this, 18));
        Tools.setElevation(card, 4);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));

        card.addView(groupTitle("最新"));
        card.addView(timelineRow(accent, "v4.0.8", "检查更新真正联网对比版本；APK提取后上传生成分享链接"));
        card.addView(timelineRow(accent, "v4.0.3", "检查更新合并到关于页；无线调试加ADB有线启动指引；底部导航三页统一"));
        card.addView(timelineRow(accent, "v4.0.0", "物体识别阈值提升减少误报；网盘解析接NFD直链；视频解析无水印下载"));
        card.addView(timelineRow(accent, "v3.9.0", "Shizuku主动检测引导；录屏；自动点击器；系统信息/翻译/天气/物流"));
        card.addView(timelineRow(accent, "v3.1.5", "公告改居中弹窗样式；底部导航四格；传感器加固；无线调试页升级"));
        card.addView(timelineRow(accent, "v3.1.4", "新增传感器工具箱（指南针/水平仪/手电筒）；二维码支持扫码识别；新增无线调试专项引导页（三态检测+分步引导+自动轮询）；开屏公告重做液态玻璃设计"));
        card.addView(timelineRow(accent, "v3.1.3", "3D枪战引擎升级：俯仰角视角+后坐力上抬+距离雾化+右半屏滑动瞄准；Shizuku API 升级至 13.1.5；底部导航收窄紧凑化"));
        card.addView(timelineRow(accent, "v3.1.2", "修复 Shizuku 无线调试授权：补充 API_V23 权限声明，激活后即可正常静默停用/卸载、读取深度设备信息"));
        card.addView(timelineRow(accent, "v3.1.1", "新增《失控进化》房型库入口（内置浏览器直达官网网页）；官网下载改国内直连；全文版本信息统一"));
        card.addView(timelineRow(accent, "v3.1.0", "物体识别引擎大升级：EfficientDet-Lite0 检测 + MobileNetV2 分类，误报率大幅下降；检查更新支持官网不可达时自动降级内置信息"));
        card.addView(timelineRow(accent, "v3.0.9", "官网迁移至 GitHub Pages：无水印、下载走 GitHub 发布页"));
        card.addView(timelineRow(accent, "v3.0.8", "玻璃透明感 UI：彩色光斑背景、悬浮玻璃顶栏与底部导航、首页搜索栏"));
        card.addView(timelineRow(accent, "v3.0", "全界面统一设计语言：色板 / 圆角 / 间距 / 字体层级全面归一"));
        card.addView(timelineRow(accent, "v3.0", "全新代码绘制图标系统，全面告别 emoji"));
        card.addView(timelineRow(accent, "v3.0", "更新日志重设计为时间轴卡片"));
        card.addView(timelineRow(accent, "v3.0", "列表滚动与页面切换流畅度优化"));

        card.addView(groupTitle("正式版"));
        card.addView(timelineRow(accent, "v1.9.1", "修复主界面布局挤压问题"));
        card.addView(timelineRow(accent, "v1.9.4", "整蛊版发布"));
        card.addView(timelineRow(accent, "v1.9.8", "新增下载链接一键复制"));
        card.addView(timelineRow(accent, "v2.0", "全新界面：娱乐/工具栏/系统三大板块 + 16 个新功能 + 收藏 + 深浅主题"));
        card.addView(timelineRow(accent, "v2.0.1", "修复 8 处 bug + 界面玻璃立体化"));
        card.addView(timelineRow(accent, "v2.0.2", "浏览器支持自定义协议跳转（bytedance:// 等）"));
        card.addView(timelineRow(accent, "v2.0.3", "立体玻璃卡片 + 阴影修复 + 卡片间距修复 + 版本更新记录"));
        card.addView(timelineRow(accent, "v2.0.4", "卡片对照效果图重做：名称旁「火爆」红橙胶囊标签 + 卡片加高 + 阴影加强"));
        card.addView(timelineRow(accent, "v2.0.5", "引入液态玻璃质感（参照开源 AndroidLiquidGlass 自实现）：半透明磨砂底 + 顶部高光 + 底部内阴影 + 白色边缘亮边"));
        card.addView(timelineRow(accent, "v2.0.6", "真正集成开源项目 Liquid-Glass-Android（MIT）：主界面功能卡改用官方 LiquidGlassView 原生渲染——真实背景模糊 / 折射色散 / 边缘高光 / 触摸弹性"));
        card.addView(timelineRow(accent, "v2.0.7", "修复液态玻璃深色渲染问题（关闭自适应色调）；底部导航图标升级为渐变圆底样式；下载链接改为 IK工具箱官方网站，官网可一键下载最新安装包"));
        card.addView(timelineRow(accent, "v2.0.8", "强制玻璃渲染模式（FORCE_FULL），系统高对比度 / 移除动画 / 省电时不再回退深色卡"));
        card.addView(timelineRow(accent, "v2.0.9", "适配华为 HarmonyOS / 荣耀系：HarmonyOS 改用内置液态玻璃渲染，标准安卓继续用开源库"));
        card.addView(timelineRow(accent, "v2.0.10", "液态玻璃质感升级：卡片更通透 + 中部玻璃反光带 + 顶部高光增强 + 白边加宽加亮"));
        card.addView(timelineRow(accent, "v2.1.0", "官网独立部署（GitHub Pages，无平台水印）"));
        card.addView(timelineRow(accent, "v2.1.1", "底部导航栏升级液态玻璃质感"));
        card.addView(timelineRow(accent, "v2.1.2", "修复：点击底部导航改为无动画瞬间切换（底部导航固定不动、只有内容切换）；导航项图标文字紧凑对齐不再分层"));
        card.addView(timelineRow(accent, "v2.2.0", "新增【物体识别】：内置本地 AI 模型（TensorFlow Lite），实时识别画面中的人物/车辆/动物/日常物品，叠加识别框+类别+置信度+估算距离尺寸，完全离线不联网"));
        card.addView(timelineRow(accent, "v2.2.1", "清理界面表情图标（功能卡/板块标题去 emoji，更简洁专业）；手持弹幕改为横屏显示"));
        card.addView(timelineRow(accent, "v2.2.2", "修复物体识别不工作：相机尺寸时序/模型读取/输入重定位/NV21色序/推理容错全面修复，识别更稳更准"));
        card.addView(timelineRow(accent, "v2.3.0", "物体识别大升级：修复推理报错（输出按模型真实形状分配）；新增 ImageNet 1000 类整图识别——几乎任何东西都能大致识别出是什么（人物/动物/食物/家电/乐器/工具等），面板顶部显示整体识别结果"));
        card.addView(timelineRow(accent, "v2.3.1", "彻底修复识别报错：识别结果统一改一维数组传递，根治 DataType error（此前的报错根因）"));
        card.addView(timelineRow(accent, "v2.3.2", "新增下载中心：内置断点续传下载器，下载 APK 不再卡 0%，完成后一键安装"));
        card.addView(timelineRow(accent, "v2.3.3", "安装包大幅瘦身（30MB→9MB）：穿透内核改为首次使用时下载（约14MB，断点续传+自动安装），下载更新不再卡顿"));
        card.addView(timelineRow(accent, "v2.3.4", "修复穿透内核执行被拒（Permission denied）：兼容模式调整，按需下载的穿透内核可正常运行"));
        card.addView(timelineRow(accent, "v2.3.5", "修复物体识别推理错误：真正根因是多输出调用方式错误（引擎把输出数组误判为单输出），改为多输出 API"));
        card.addView(timelineRow(accent, "v2.3.6", "修复识别输出维度：输出数组改为与引擎 shape 完全一致的多维结构，识别框/标签/距离与 1000 类识别全部正常；穿透内核执行权限进一步加固"));
        card.addView(timelineRow(accent, "v2.3.7", "浏览器新增标识(UA)切换：安卓/电脑Windows/Mac/iPhone/iPad/微信/百度/谷歌蜘蛛，一键模拟任意设备访问"));
        card.addView(timelineRow(accent, "v2.3.8", "图片工具升级为完整图片工具箱：压缩/格式转换/调整大小/旋转翻转/裁剪/滤镜/画面调整/水印/圆角/边框/EXIF清除/图片信息 共12项"));
        card.addView(timelineRow(accent, "v2.3.9", "穿透内核改存外部存储并多路径查找，彻底绕开 /data/user/0 私有目录执行锁定（华为等加固系统也能开启公网）"));
        card.addView(timelineRow(accent, "v2.3.10", "物体识别过滤背景误检框：不再显示 ??? 未知类别，只显示真实物体"));
        card.addView(timelineRow(accent, "v2.3.11", "物体识别全部中文化：检测框 80 类标签与整体识别常见类别均显示中文"));
        card.addView(timelineRow(accent, "v2.3.12", "物体识别防误检：置信度阈值 0.32→0.45，并去重同类重叠框，不再把风扇/柜子识别成笔记本电脑"));
        card.addView(timelineRow(accent, "v2.3.13", "支持安卓系统分屏：可与任意应用分屏同屏使用"));
        card.addView(timelineRow(accent, "v2.3.14", "修复 Shizuku 连接：改为主动直连 Shizuku 服务，不再依赖服务端推送，授权弹窗可正常弹出"));
        card.addView(timelineRow(accent, "v2.3.15", "Shizuku 连接再加强：Provider 对齐官方声明，直连双通道（getService/checkService）"));
        card.addView(timelineRow(accent, "v2.3.16", "Shizuku 自动重连：去 Shizuku 重启服务后，本页自动检测连接并弹授权框"));
        card.addView(timelineRow(accent, "v2.3.17", "穿透 error=13 根治：内核改为内部目录优先执行+实测验证，鸿蒙外部存储不可执行的问题已绕过"));
        card.addView(timelineRow(accent, "v2.3.18", "穿透双通道：系统 shell 方式启动内核，绕开鸿蒙 exec 限制；新增网页转应用（网页/HTML 封装成应用）"));
        card.addView(timelineRow(accent, "v2.3.19", "穿透 linker 通道：改用系统动态链接器加载内核，绕开鸿蒙对下载二进制的 exec 拦截"));
        card.addView(timelineRow(accent, "v2.3.20", "穿透内核 PIE 版：内核重编译为系统 linker 可加载格式，配合 linker 通道在鸿蒙上跑通"));
        card.addView(timelineRow(accent, "v2.3.21", "新增 3D枪战：第一人称射击，单人打AI / 局域网双人对战"));
        card.addView(timelineRow(accent, "v2.3.26", "3D枪战核心升级：第一人称3D真枪(体素建模+真实枪械贴图+后坐摆动)/弹药系统(30发弹夹+换弹音效+换弹按钮)/准星动态扩散/联机音效同步/波次击杀提示"));

        card.addView(groupTitle("内测版"));
        card.addView(timelineRow(accent, "v1.0", "首发六大工具：局域网服务器 / ZIP / 图片 / AI / 浏览器 / 文本"));
        card.addView(timelineRow(accent, "v1.4", "新增视频工具：提取封面、提取音频、视频信息"));
        card.addView(timelineRow(accent, "v1.6", "沉浸式界面：去除顶部黑边"));
        card.addView(timelineRow(accent, "v1.7", "内置小AI：离线问答 + 联网问答"));
        card.addView(timelineRow(accent, "v1.8", "改名「IK工具箱」+ 赛博霓虹新图标"));
        card.addView(timelineRow(accent, "v1.9.0", "主界面重构"));

        return card;
    }

    /** 分组标题（时间轴内） */
    private TextView groupTitle(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setTextColor(Tools.accentColor(this));
        t.setPadding(0, dp(12), 0, dp(4));
        return t;
    }

    /** 单条时间轴记录：左竖线 + 圆点节点 + 版本胶囊 + 内容 */
    private View timelineRow(int accent, String version, String detail) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);

        // 左列：贯通竖线 + 节点圆点
        FrameLayout left = new FrameLayout(this);
        left.setLayoutParams(new LinearLayout.LayoutParams(dp(26),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        View line = new View(this);
        GradientDrawable lineBg = new GradientDrawable();
        lineBg.setColor((0x4D << 24) | (accent & 0x00FFFFFF));   // 强调色 30% 透明
        line.setBackground(lineBg);
        left.addView(line, new FrameLayout.LayoutParams(dp(2),
                FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL));
        View dot = new View(this);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(accent);
        dot.setBackground(dotBg);
        FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(dp(12), dp(12),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        dotLp.topMargin = dp(7);
        left.addView(dot, dotLp);
        row.addView(left);

        // 右列：版本胶囊 + 内容
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        content.setPadding(dp(8), 0, 0, dp(10));

        TextView pill = new TextView(this);
        pill.setText(version);
        pill.setTextSize(12);
        pill.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        pill.setTextColor(accent);
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor((0x1F << 24) | (accent & 0x00FFFFFF));   // 强调色 12% 透明底
        pillBg.setCornerRadius(dp(9));
        pill.setPadding(dp(8), dp(2), dp(8), dp(2));
        content.addView(pill);

        TextView det = new TextView(this);
        det.setText(detail);
        det.setTextSize(13);
        det.setTextColor(Tools.textColor(this));
        det.setLineSpacing(dp(3), 1.0f);
        det.setPadding(0, dp(5), 0, 0);
        content.addView(det);

        row.addView(content);
        return row;
    }

    /** 文本板块：标题 + 条目列表（主题自适应） */
    private View textSection(String title, String[] lines) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Tools.sectionTitle(this, title));
        for (String l : lines) {
            TextView line = new TextView(this);
            line.setText("· " + l);
            line.setTextSize(13);
            line.setTextColor(Tools.subTextColor(this));
            line.setLineSpacing(dp(3), 1.0f);
            line.setPadding(0, dp(5), 0, 0);
            box.addView(line);
        }
        return box;
    }

    /** 一键复制下载链接 */
    private void copyLink() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("IK工具箱下载链接", DL_URL));
            Toast.makeText(this, "下载链接已复制，可粘贴发送给朋友", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "复制失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void checkUpdate() {
        final Toast loading = Toast.makeText(this, "正在检查更新…", Toast.LENGTH_SHORT);
        loading.show();
        new Thread(() -> {
            String latest = null;
            String apkUrl = null;
            try {
                java.net.URL u = new java.net.URL("https://3115703202.github.io/ik-tools/");
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(u.openStream()));
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.contains("VER=v")) {
                        int i = line.indexOf("VER=v");
                        latest = line.substring(i + 5, line.indexOf("|", i));
                    }
                    if (line.contains("URL=https://aka.doubaocdn.com")) {
                        int i = line.indexOf("URL=https://aka.doubaocdn.com");
                        int j = line.indexOf("'", i);
                        if (j < 0) j = line.indexOf("\"", i);
                        apkUrl = line.substring(i + 4, j > 0 ? j : line.length());
                    }
                }
                br.close();
            } catch (Exception ignored) {}
            final String fLatest = latest;
            final String fUrl = apkUrl;
            runOnUiThread(() -> {
                if (fLatest == null) {
                    Toast.makeText(this, "检查失败，请检查网络", Toast.LENGTH_SHORT).show();
                    return;
                }
                String cur = "v4.0.8";
                try {
                    cur = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                    if (!cur.startsWith("v")) cur = "v" + cur;
                } catch (Exception ignored) {}
                if (fLatest.equals(cur)) {
                    Toast.makeText(this, "当前已是最新版 " + cur, Toast.LENGTH_SHORT).show();
                } else {
                    new android.app.AlertDialog.Builder(this)
                            .setTitle("发现新版本 " + fLatest)
                            .setMessage("当前版本：" + cur + "\n\n点击下载安装新版本。")
                            .setPositiveButton("下载", (d, w) -> {
                                if (fUrl != null) {
                                    try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(fUrl))); }
                                    catch (Exception ignored) {}
                                }
                            })
                            .setNegativeButton("取消", null)
                            .show();
                }
            });
        }).start();
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
