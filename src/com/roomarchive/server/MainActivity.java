package com.roomarchive.server;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/**
 * IK工具箱 · 主界面（分类板块 + 底部导航）
 * 板块：娱乐 / 工具栏 / 系统功能；底部：首页 / 收藏 / 我的
 */
public class MainActivity extends BaseActivity {

    private android.widget.ScrollView mScroll;
    private View entTitleView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        autoCheckUpdate();
        // 公告只在首次安装后第一次打开时弹（记录 SharedPreferences，杀后台重进不再弹）
        boolean noticed = getSharedPreferences("ik", MODE_PRIVATE).getBoolean("noticed_v316", false);
        if (!noticed) {
            showNoticeDialog();
            getSharedPreferences("ik", MODE_PRIVATE).edit().putBoolean("noticed_v316", true).apply();
        }
        // 从 Shizuku 切回来时主动补授权
        App.resumeCheck();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkClipboardForLink();
        checkShizukuStatus();
    }

    /** Shizuku 主动检测：先拉binder，再判断状态，没运行就弹窗引导 */
    private void checkShizukuStatus() {
        try {
            // 1. 主动从 Shizuku App 拿 binder（App后启动时sticky可能丢）
            ShizukuManager.requestBinderFromManager(this);
            // 2. 检测是否连接
            boolean connected = ShizukuManager.isConnected();
            if (connected) {
                // 3. 已连接但没授权 → 自动请求授权
                if (!ShizukuManager.isAuthorized()) {
                    App.autoRequestPermission();
                }
            } else {
                // 4. 没运行 → 弹窗引导（只弹一次）
                boolean asked = getSharedPreferences("ik", MODE_PRIVATE).getBoolean("shizuku_asked", false);
                if (!asked && ShizukuManager.isShizukuInstalled(this)) {
                    getSharedPreferences("ik", MODE_PRIVATE).edit().putBoolean("shizuku_asked", true).apply();
                    new android.app.AlertDialog.Builder(this)
                            .setTitle("Shizuku 未运行")
                            .setMessage("强制卸载/停用等高级功能需要 Shizuku 授权。\n\n请打开 Shizuku App 并启动服务（无线调试或ADB）。")
                            .setPositiveButton("去开启", (d, w) -> ShizukuManager.openShizuku(this))
                            .setNegativeButton("以后再说", null)
                            .show();
                }
            }
        } catch (Throwable ignored) {}
    }

    /** 剪贴板自动监听：复制了分享链接就弹窗问要不要解析 */
    private void checkClipboardForLink() {
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) return;
            android.content.ClipData cd = cm.getPrimaryClip();
            if (cd == null || cd.getItemCount() == 0) return;
            CharSequence t = cd.getItemAt(0).getText();
            if (t == null) return;
            String text = t.toString().trim();
            if (text.isEmpty()) return;
            // 判断是不是链接
            boolean isLink = text.startsWith("http://") || text.startsWith("https://")
                    || text.startsWith("magnet:") || text.startsWith("thunder://");
            if (!isLink) return;
            // 避免重复弹：记录上次处理过的剪贴板内容
            String last = getSharedPreferences("ik", MODE_PRIVATE).getString("last_clip", "");
            if (last.equals(text)) return;
            getSharedPreferences("ik", MODE_PRIVATE).edit().putString("last_clip", text).apply();
            // 弹窗
            new android.app.AlertDialog.Builder(this)
                    .setTitle("检测到分享链接")
                    .setMessage("要打开网盘解析吗？\n\n" + (text.length() > 60 ? text.substring(0, 60) + "…" : text))
                    .setPositiveButton("去解析", (d, w) -> startActivity(new android.content.Intent(this, NetDiskActivity.class)))
                    .setNegativeButton("忽略", null)
                    .show();
        } catch (Throwable ignored) {}
    }

    /** 启动后静默检查更新：发现新版本弹窗，不打扰其它操作 */
    private void autoCheckUpdate() {
        final Handler h = new Handler(Looper.getMainLooper());
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final UpdateChecker.Result r = UpdateChecker.check(
                                UpdateChecker.defaultUrl(MainActivity.this), myVersion());
                        if (r.error != null || r.code <= 0) return;   // 失败/无清单：静默
                        try {
                            int cur = getPackageManager()
                                    .getPackageInfo(getPackageName(), 0).versionCode;
                            if (r.code <= cur) return;                // 已是最新：静默
                        } catch (Exception e) {
                            return;
                        }
                        h.post(new Runnable() {
                            @Override
                            public void run() {
                                showUpdateDialog(r);
                            }
                        });
                    }
                }).start();
            }
        }, 3000);
    }

    private String myVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    /** 公告弹窗：参考 Shizuku 样式——白色圆角大卡片 + 左侧图标 + 名称/版本 + GitHub 链接 */
    private void showNoticeDialog() {
        final android.app.AlertDialog d = new android.app.AlertDialog.Builder(this).create();
        d.setCancelable(false);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        // 白色卡片背景（Shizuku 风格：极浅蓝灰底，大圆角）
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.rgb(240, 245, 250));
        cardBg.setCornerRadius(dp(24));
        box.setBackground(cardBg);
        box.setPadding(dp(22), dp(22), dp(22), dp(18));

        // ===== 头部：图标 + 名称 + 版本（照 Shizuku 卡片）=====
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        // 左侧方形图标（蓝紫渐变底 + "IK"）
        TextView icon = new TextView(this);
        icon.setText("IK");
        icon.setTextSize(22);
        icon.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        icon.setTextColor(Color.WHITE);
        icon.setGravity(Gravity.CENTER);
        GradientDrawable ib = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(99, 102, 241), Color.rgb(139, 92, 246)});
        ib.setCornerRadius(dp(14));
        icon.setBackground(ib);
        head.addView(icon, new LinearLayout.LayoutParams(dp(56), dp(56)));

        LinearLayout nameCol = new LinearLayout(this);
        nameCol.setOrientation(LinearLayout.VERTICAL);
        nameCol.setPadding(dp(14), 0, 0, 0);
        TextView name = new TextView(this);
        name.setText("IK工具箱");
        name.setTextSize(20);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        name.setTextColor(Color.rgb(28, 32, 48));
        nameCol.addView(name);
        TextView ver = new TextView(this);
        ver.setText("3.1.6");
        ver.setTextSize(13);
        ver.setTextColor(Color.rgb(120, 130, 145));
        nameCol.addView(ver);
        head.addView(nameCol);
        box.addView(head);

        box.addView(spacer(14));

        // 公告正文
        TextView b1 = new TextView(this);
        b1.setText("本软件永久免费 · 无广告 · 无收费");
        b1.setTextSize(14);
        b1.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        b1.setTextColor(Color.rgb(28, 32, 48));
        box.addView(b1);
        box.addView(spacer(6));

        TextView b2 = new TextView(this);
        b2.setText("如遇到收费、卡密，是诈骗软件，请勿相信！");
        b2.setTextSize(12);
        b2.setTextColor(Color.rgb(220, 80, 70));
        box.addView(b2);
        box.addView(spacer(10));

        TextView b3 = new TextView(this);
        b3.setText("v4.0.9：检查更新联网对比/APK上传分享/物体识别优化");
        b3.setTextSize(12);
        b3.setTextColor(Color.rgb(100, 110, 125));
        b3.setLineSpacing(0, 1.3f);
        box.addView(b3);

        box.addView(spacer(12));

        // 蓝色下划线链接（Shizuku 风格）
        TextView link = new TextView(this);
        link.setText("在 GitHub 查看项目与最新版本");
        link.setTextSize(13);
        link.setTextColor(Color.rgb(37, 99, 235));
        link.getPaint().setUnderlineText(true);
        link.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/3115703202/ik-tools")));
                } catch (Exception ignored) {
                }
            }
        });
        box.addView(link);

        box.addView(spacer(18));

        // 底部按钮：照 Shizuku 深色圆角胶囊
        Button ok = new Button(this);
        ok.setText("知道了");
        ok.setTextSize(15);
        ok.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        ok.setTextColor(Color.WHITE);
        ok.setAllCaps(false);
        GradientDrawable okBg = new GradientDrawable();
        okBg.setColor(Color.rgb(15, 76, 92));
        okBg.setCornerRadius(dp(26));
        ok.setBackground(okBg);
        box.addView(ok, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });

        d.setView(box, dp(20), 0, dp(20), 0);
        d.show();
        d.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
    }

    private void showUpdateDialog(final UpdateChecker.Result r) {
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
        b.setTitle("发现新版本 v" + r.version);
        b.setMessage(r.note == null || r.note.length() == 0 ? "点击下载并安装" : r.note);
        b.setPositiveButton("立即更新", new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface d, int w) {
                startDownload(r.apkUrl);
            }
        });
        b.setNegativeButton("稍后", null);
        b.show();
    }

    private void startDownload(final String apkUrl) {
        final android.app.ProgressDialog prog = new android.app.ProgressDialog(this);
        prog.setTitle("下载更新");
        prog.setMessage("正在下载安装包…");
        prog.setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL);
        prog.setMax(100);
        prog.setCancelable(false);
        prog.show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final File apk = UpdateChecker.download(MainActivity.this, apkUrl,
                        myVersion(), new UpdateChecker.Progress() {
                            @Override
                            public void onProgress(int percent) {
                                if (percent >= 0) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            prog.setProgress(percent);
                                        }
                                    });
                                }
                            }
                        });
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        prog.dismiss();
                        if (apk != null) {
                            try {
                                UpdateChecker.install(MainActivity.this, apk);
                            } catch (Exception e) {
                                android.widget.Toast.makeText(MainActivity.this,
                                        "无法打开安装器：请在系统设置中允许安装未知应用",
                                        android.widget.Toast.LENGTH_LONG).show();
                            }
                        } else {
                            android.widget.Toast.makeText(MainActivity.this,
                                    "下载失败，请检查网络后重试", android.widget.Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
    }

    private void buildUi() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackground(Tools.bgWithGlow(this));

        // ===== 头部 =====
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(16), dp(16), dp(16), dp(12));
        TextView title = new TextView(this);
        title.setText("IK工具箱");
        title.setTextSize(28);
        title.setTextColor(Tools.textColor(this));
        title.setGravity(Gravity.CENTER);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        head.addView(title);
        Tools.gradientText(title, Tools.isDark(this)
                ? new int[]{0xFFFFFFFF, 0xFF9FC9FF}
                : new int[]{0xFF1D4ED8, 0xFF7C3AED});
        TextView subtitle = new TextView(this);
        subtitle.setText("26 合 1 实用工具 · 永久免费无广告");
        subtitle.setTextSize(12);
        subtitle.setTextColor(Tools.isDark(this) ? 0xFF9FB0CC : 0xFF5B6B84);
        subtitle.setGravity(Gravity.CENTER);
        head.addView(subtitle);
        head.addView(spacer(12));

        // 搜索栏（半透明玻璃：圆角 + 细白边 + 放大镜）
        android.widget.EditText search = new android.widget.EditText(this);
        search.setHint("搜索服务名称...");
        search.setTextSize(14);
        search.setTextColor(Tools.textColor(this));
        search.setHintTextColor(Tools.subTextColor(this));
        search.setSingleLine(true);
        search.setBackground(Tools.searchBar(this));
        search.setPadding(dp(14), dp(0), dp(14), dp(0));
        search.setCompoundDrawablesWithIntrinsicBounds(
                Icons.of(this, "objdetect", Tools.subTextColor(this), 18), null, null, null);
        search.setCompoundDrawablePadding(dp(8));
        search.setSelectAllOnFocus(false);
        head.addView(search, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46)));
        outer.addView(head);

        // ===== 内容区（滚动）=====
        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setClipChildren(false);
        mScroll = scroll;
        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(6), dp(12), dp(18));
        root.setClipChildren(false);
        scroll.addView(root);

        renderAll(root, "");
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                renderAll(root, s.toString().trim());
            }
            @Override public void afterTextChanged(android.text.Editable s) { }
        });

        outer.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // ===== 底部导航（白色圆角卡片：左右留边 + 四角圆角） =====
        LinearLayout bar = bottomNav(0);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.setMargins(dp(16), 0, dp(16), dp(12));
        outer.addView(bar, blp);

        setContentView(outer);
    }

    /** 渲染工具板块（支持搜索过滤）：query 为空显示全部分类，否则只显示匹配项 */
    private void renderAll(LinearLayout root, String query) {
        root.removeAllViews();
        if (query == null || query.length() == 0) {
            entTitleView = addSection(root, "娱乐", Tools.ofSection(Tools.SEC_ENT));
            root.addView(spacer(4));
            addSection(root, "工具栏", Tools.ofSection(Tools.SEC_TOOL));
            root.addView(spacer(4));
            addSection(root, "系统功能", Tools.ofSection(Tools.SEC_SYS));
        } else {
            entTitleView = null;
            List<Tools.Tool> hit = new java.util.ArrayList<Tools.Tool>();
            String q = query.toLowerCase();
            for (Tools.Tool t : Tools.ALL) {
                String name = t.name == null ? "" : t.name.toLowerCase();
                String desc = t.desc == null ? "" : t.desc.toLowerCase();
                if (name.contains(q) || desc.contains(q)) hit.add(t);
            }
            if (hit.isEmpty()) {
                TextView empty = new TextView(this);
                empty.setText("没有找到「" + query + "」相关工具");
                empty.setTextSize(13);
                empty.setTextColor(Tools.subTextColor(this));
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(0, dp(40), 0, dp(40));
                root.addView(empty);
            } else {
                addSection(root, "搜索结果", hit);
            }
        }
        TextView foot = new TextView(this);
        foot.setText("IK工具箱 v4.0.9\n全部功能离线运行，不收集任何数据");
        foot.setTextSize(10);
        foot.setTextColor(Tools.subTextColor(this));
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, dp(8), 0, dp(8));
        root.addView(foot);
    }

    /** 游戏库 tab：滚动到娱乐板块 */
    private void scrollToSection() {
        if (mScroll == null || entTitleView == null) return;
        final android.widget.ScrollView sv = mScroll;
        final View target = entTitleView;
        sv.post(new Runnable() {
            @Override
            public void run() {
                int top = target.getTop();
                if (top > 0) sv.smoothScrollTo(0, top);
            }
        });
    }

    /** 板块：标题 + 两列卡片；返回标题 View（供滚动定位） */
    private View addSection(LinearLayout root, String sectionName, List<Tools.Tool> list) {
        TextView st = new TextView(this);
        st.setText(sectionName);
        st.setTextSize(16);
        st.setTextColor(Tools.isDark(this) ? 0xFFD7E3FF : 0xFF1E2634);
        st.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        st.setPadding(dp(4), dp(14), dp(4), dp(10));
        root.addView(st);

        LinearLayout row = null;
        for (int i = 0; i < list.size(); i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                root.addView(row, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
            }
            final Tools.Tool t = list.get(i);
            View card = toolCard(t);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, dp(152), 1f);
            if (i % 2 == 0) clp.setMarginEnd(dp(8));
            row.addView(card, clp);
        }
        if (list.size() % 2 == 1) {
            row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        }
        return st;
    }

    /** 开源液态玻璃库是否可用：依赖标准安卓渲染管线（RenderEffect / GL）。
     *  华为 HarmonyOS、荣耀系、以及 API 31 以下设备渲染不兼容（会出黑卡），走内置液态玻璃。 */
    private static boolean liquidGlassLibOk() {
        String sig = (Build.MANUFACTURER + "|" + Build.BRAND + "|" + Build.DISPLAY + "|"
                + Build.VERSION.INCREMENTAL + "|" + Build.FINGERPRINT).toUpperCase();
        boolean huaweiLike = sig.contains("HUAWEI") || sig.contains("HONOR") || sig.contains("HARMONY");
        return !huaweiLike && Build.VERSION.SDK_INT >= 31;
    }

    /** 单个功能卡：emoji 图标 + 名称行(含火爆标签) + 描述 + 收藏星
     *  卡片容器 = 开源液态玻璃（标准安卓用 Liquid-Glass-Android，HarmonyOS/老系统用内置渲染） */
    private View toolCard(final Tools.Tool t) {
        boolean dark = Tools.isDark(this);

        FrameLayout card;
        if (liquidGlassLibOk()) {
            // 标准安卓（API 31+，非华为系）：用开源项目 Liquid-Glass-Android 原生渲染
            com.example.liquidglass.LiquidGlassView lv = new com.example.liquidglass.LiquidGlassView(this);
            lv.setCornerRadius(dp(22));
            lv.setMaterial(com.example.liquidglass.GlassMaterial.REGULAR);
            lv.setAccessibilityMode(com.example.liquidglass.GlassAccessibilityMode.FORCE_FULL);
            lv.setEnableAdaptiveTint(false);
            lv.setOverLight(true);
            lv.setEnableBackdropBlur(true);
            lv.setUseHardwareBlurWhenPossible(true);
            lv.setBlurAmount(22f);
            lv.setSaturation(1.4f);
            lv.setEnableEdgeHighlight(true);
            lv.setEnableShadow(true);
            lv.setEnableChromaticAberration(true);
            lv.setEnableChromaticDispersion(true);
            lv.setHighQualityBlur(true);
            lv.setPadding(dp(10), dp(12), dp(10), dp(8));
            Tools.setElevation(lv, 12);
            lv.setClickable(true);
            lv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(MainActivity.this, t.activity));
                }
            });
            card = lv;
        } else {
            // HarmonyOS / 华为荣耀 / 低版本安卓：开源库渲染管线不兼容会出黑卡，
            // 自动使用内置液态玻璃渲染（半透明磨砂 + 高光 + 内阴影 + 边缘亮边，零依赖全兼容）
            FrameLayout fl = new FrameLayout(this);
            fl.setBackground(Tools.liquidGlassCard(this, 22));
            Tools.setElevation(fl, 12);
            fl.setClickable(true);
            fl.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(MainActivity.this, t.activity));
                }
            });
            card = fl;
        }

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setGravity(Gravity.CENTER);

        // 图标衬底（40dp 圆角方块 + 24dp 矢量图标）
        FrameLayout tile = new FrameLayout(this);
        tile.setBackground(Tools.iconTile(this, t.color, 40, 12));
        android.widget.ImageView iconIv = new android.widget.ImageView(this);
        iconIv.setImageDrawable(Icons.tool(this, t, 24));
        iconIv.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        tile.addView(iconIv, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        inner.addView(tile);
        inner.addView(spacer(6));

        // 名称 + 火爆标签同一行
        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER);
        TextView name = new TextView(this);
        name.setText(t.name);
        name.setTextSize(14);
        name.setTextColor(Tools.textColor(this));
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        nameRow.addView(name);
        if (t.hot) {
            TextView hot = new TextView(this);
            hot.setText("火爆");
            hot.setTextSize(10);
            hot.setTextColor(Color.WHITE);
            hot.setGravity(Gravity.CENTER);
            hot.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            GradientDrawable hb = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{Color.rgb(255, 60, 48), Color.rgb(255, 140, 20)});
            hb.setCornerRadius(dp(9));
            hot.setPadding(dp(7), dp(2), dp(7), dp(2));
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(19));
            hlp.setMarginStart(dp(6));
            hot.setLayoutParams(hlp);
            nameRow.addView(hot);
        }
        inner.addView(nameRow);

        inner.addView(spacer(3));

        TextView desc = new TextView(this);
        desc.setText(t.desc);
        desc.setTextSize(10);
        desc.setTextColor(Tools.subTextColor(this));
        desc.setGravity(Gravity.CENTER);
        desc.setMaxLines(2);
        inner.addView(desc);

        card.addView(inner);

        // 收藏星（右下角，矢量图标 fav）
        final TextView star = new TextView(this);
        star.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.BOTTOM | Gravity.END);
        star.setLayoutParams(slp);
        updateFavIcon(star, Tools.isFav(this, t.id));
        star.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean now = !Tools.isFav(MainActivity.this, t.id);
                Tools.setFav(MainActivity.this, t.id, now);
                updateFavIcon(star, now);
                Toast.makeText(MainActivity.this,
                        now ? "已加入收藏" : "已取消收藏", Toast.LENGTH_SHORT).show();
            }
        });
        card.addView(star);

        return card;
    }

    /** 刷新收藏星图标（fav key，选中 0xFFF59E0B / 未选 subTextColor，15dp） */
    private void updateFavIcon(TextView star, boolean fav) {
        int c = fav ? 0xFFF59E0B : Tools.subTextColor(MainActivity.this);
        star.setCompoundDrawablesWithIntrinsicBounds(
                Icons.of(MainActivity.this, "fav", c, 15), null, null, null);
    }

    /** 底部导航：白圆角卡片 + 4 等项（首页/收藏/游戏库/我的），参考 Shizuku 卡片风格 */
    private LinearLayout bottomNav(int active) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        // 白色圆角卡片背景
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(255, 255, 255));
        bg.setCornerRadius(dp(28));
        bg.setStroke(dp(1), Color.rgb(230, 235, 242));
        bar.setBackground(bg);
        bar.setElevation(dp(6));
        bar.setPadding(dp(8), dp(8), dp(8), dp(8));

        bar.addView(navItem("home", "首页", active == 0, 0));
        bar.addView(navItem("fav", "收藏", active == 1, 1));
        bar.addView(navItem("game", "游戏", active == 2, 2));
        bar.addView(navItem("user", "我的", active == 3, 3));
        return bar;
    }

    private LinearLayout navItem(String iconKey, String label, boolean active, final int tab) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(0, dp(8), 0, dp(6));

        // 图标容器：固定大小圆胶囊，选中变色，尺寸不变
        android.widget.FrameLayout iconWrap = new android.widget.FrameLayout(this);
        GradientDrawable ebg = new GradientDrawable();
        ebg.setShape(GradientDrawable.OVAL);
        ebg.setColor(active ? Color.rgb(15, 76, 92) : 0x00000000);
        iconWrap.setBackground(ebg);
        FrameLayout.LayoutParams iwlp = new FrameLayout.LayoutParams(dp(38), dp(30));
        iconWrap.setLayoutParams(iwlp);

        android.widget.ImageView e = new android.widget.ImageView(this);
        int iconColor = active ? Color.WHITE : Color.rgb(140, 150, 165);
        e.setImageDrawable(Icons.nav(this, iconKey, iconColor, 22));
        e.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams elp = new FrameLayout.LayoutParams(dp(22), dp(22));
        elp.gravity = Gravity.CENTER;
        iconWrap.addView(e, elp);
        item.addView(iconWrap);

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(11);
        l.setTypeface(active ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
        l.setIncludeFontPadding(false);
        l.setGravity(Gravity.CENTER);
        l.setPadding(0, dp(4), 0, 0);
        l.setTextColor(active ? Color.rgb(15, 76, 92) : Color.rgb(140, 150, 165));
        item.addView(l, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        item.setLayoutParams(new LinearLayout.LayoutParams(0,
                dp(68), 1f));
        item.setClickable(true);
        item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (tab == 1) {
                    startActivity(new Intent(MainActivity.this, FavoritesActivity.class));
                } else if (tab == 2) {
                    scrollToSection();
                } else if (tab == 3) {
                    startActivity(new Intent(MainActivity.this, UserCenterActivity.class));
                }
                overridePendingTransition(0, 0);
            }
        });
        return item;
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
