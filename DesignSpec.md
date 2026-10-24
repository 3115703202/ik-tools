# UI 升级冻结版设计规范（DesignSpec v1.0）

> 适用范围：房型库共享服务器 / IK工具箱（包名 `com.roomarchive.server`）全部页面。
> 本规范是后续 9 个并行批次代理的**唯一执行依据**。所有数值照抄，不得自由发挥。
> 地基文件已就绪：`Icons.java`（矢量图标）、`Tools.java`（设计系统辅助方法）、`BaseActivity.java`（状态栏自适应）。
> 版本：对应当前 v2.3.42/97，升级后统一 v3.0。

---

## 0. 铁律（违反任何一条 = 返工）

1. **版本号纪律**：任何包含 `2.3.42` 的行一律不得改动（后续统一升 v3.0，由专人处理）。
2. **文件白名单**：批次代理只许改自己负责的那一个 Activity；**不许碰** `Tools.java`、`Icons.java`、`BaseActivity.java`、`DesignSpec.md`、`AndroidManifest.xml`、`build.sh`、`res/`、`assets/`、`src_extra/`、`keystore/`。
3. **逻辑零改动**：收藏、主题、网络、文件、游戏/渲染逻辑一律不动；只改视图层（new View / setText / setBackground / setPadding / 间距）。
4. **零 emoji**：交付时 `grep -rnP '[\x{1F300}-\x{1FAFF}\x{2600}-\x{27BF}\x{2B00}-\x{2BFF}]' src/com/roomarchive/server/你负责的文件.java` 必须为空。
5. **无 androidx / 无 XML / 无 RecyclerView / 无动画库**：UI 全部代码构建。
6. **永久免费无广告**：任何地方不得出现"开通会员/去广告/打赏弹窗强制"类引导；赞赏页保持自愿、可关闭。

---

## 1. 色板（浅/深两套完整 hex）

| 角色 | 浅色 | 深色 | 取色来源 |
|---|---|---|---|
| 页面背景渐变（顶层） | `0xFFE9F2FF → 0xFFDCEBFF → 0xFFEFE9FF`（TL_BR 三层） | `0xFF0C1220 → 0xFF182A44 → 0xFF0E1A30` | `Tools.bgDrawable(c)` 原样使用，**禁止自造渐变** |
| 页面近似顶色（状态栏） | `0xFFF2F4F8` | `0xFF10131A` | `Tools.bgColor(c)` |
| 卡片底 | `0xFFFFFFFF` | `0xFF1C212E`（玻璃卡用 `liquidGlassCard`） | `Tools.cardColor(c)` |
| 主文字 | `0xFF1E2634` | `0xFFE6E9F2` | `Tools.textColor(c)` |
| 次要文字 | `0xFF787E8C` | `0xFF8A92A6` | `Tools.subTextColor(c)` |
| 强调蓝 | `0xFF2563EB` | `0xFF60A5FA` | `Tools.accentColor(c)` |
| 强调渐变（蓝→紫） | `0xFF2563EB → 0xFF7C3AED` | `0xFF3B82F6 → 0xFF8B5CF6` | `Tools.accentGradient(c)` |
| 成功绿 | `0xFF16A34A` | `0xFF22C55E` | 成功状态文案/图标 |
| 错误红 | `0xFFDC2626` | `0xFFF87171` | 失败状态文案/图标 |
| 火爆橙红 | `0xFFEA580C` | `0xFFFB923C` | "火爆"胶囊标签 |
| 收藏星黄 | `0xFFF59E0B` | `0xFFFBBF24` | 收藏态 |

**工具图标色**：一律沿用 `Tool.color`（已在 `Tools.ALL` 冻结），不得页面内另行取色。衬底用 `Tools.iconTile(c, t.color, 40, 12)`（自动取 18% 透明）。

## 2. 圆角（dp）

| 元件 | 圆角 |
|---|---|
| 大卡片（功能卡、内容容器） | 22 |
| 列表项（liquidGlassCard） | 18 |
| 小元件（按钮组、输入框、标签底） | 14–16（输入框固定 12） |
| 胶囊按钮 | 高度 / 2（`Tools.pillButton` 自动算） |
| 图标衬底（iconTile） | 12–14 |
| 标签胶囊（"火爆"等） | 9–12 |

## 3. 间距（dp）

| 位置 | 数值 |
|---|---|
| 页面左右 padding | 16 |
| 内容区 ScrollView 内层 padding | 左16 / 上6 / 右16 / 下18，`clipToPadding(false)` |
| 卡片之间纵向间距 | 8–10 |
| 列表项之间纵向间距 | 8 |
| 页头（标题）上下 | 上20 / 下16 |
| 板块标题上下 | 上16 / 下8 |
| 卡片内部左右 | 14 |
| 图标与文字间距（compound） | 8 |

## 4. 字体层级（sp）

| 层级 | 规格 | 用法 |
|---|---|---|
| 主界面大标题 | 28sp bold，居中，蓝紫渐变字（`Tools.gradientText(t, Tools.accentGradient(c))`） | 仅 MainActivity 首页 |
| 内页标题 | 22sp bold，textColor | 一律用 `Tools.pageTitle(c, text)` |
| 板块标题 | 16sp bold + 左 4dp 蓝紫竖条 | 一律用 `Tools.sectionTitle(c, text)` |
| 列表标题 | 15sp bold，textColor | 列表项主行 |
| 正文 | 14sp，textColor | 按钮文字、表单正文 |
| 辅助 | 12–13sp，subTextColor | 副标题、说明 |
| 说明 | 10–11sp，subTextColor | 卡片描述、空状态 |

强调色用法：仅用于主按钮文字（白字配渐变底）、链接/可点文本、选中态图标。**不要**把大段正文染蓝紫。

## 5. 组件配方（代码级照抄）

### 5.1 页面骨架
```
外层 LinearLayout(VERTICAL)
  .setBackground(Tools.bgDrawable(c))          // 唯一一层背景渐变
  ├─ 页头区：Tools.pageTitle(c, "页面名")       // 22sp bold，左右 16dp
  ├─ ScrollView (weight=1, fillViewport)
  │    └─ LinearLayout(VERTICAL) padding(16,6,16,18), clipToPadding(false)
  │         └─ 各板块（sectionTitle + 卡片/列表项）
  └─ 底部导航（仅首页/收藏/我的三页）：Tools.liquidGlassBar(c)
```
非继承 BaseActivity 的页面（Download/Update/WebApps/WebAppViewer 等）在 `onCreate` 第一行调 `Tools.applyStatusBar(this)`。

### 5.2 底部导航（三页统一）
- 容器：`Tools.liquidGlassBar(c)`，内容区每项高 **78dp**，水平三分（每项 width=0, weight=1）；安全区适配：bar 加 `setOnApplyWindowInsetsListener`，底部 padding = 4dp + `insets.getSystemWindowInsetBottom()`（非沉浸式 inset=0 保持原样，沉浸式手势区自动补齐，避免 3 键导航设备双倍留白）。
- 每项：垂直 LinearLayout 居中，padding(0, 10, 0, 6)；上 36dp 图标位 + 下文字。
- 图标位：用 `Icons.nav(c, key, color, 20)` 直接 ImageView；图标容器 36dp，横 padding 12dp、纵 2dp；选中态背景 `Tools.glassNavIconBg(c, dark)`（渐变胶囊底，圆角 17.5dp），未选中透明。
- 图标-文字间距：label 顶部 padding 4dp；文字 11sp（includeFontPadding(false)）。
- key 映射：首页=`home`，收藏=`fav`，我的=`user`。
- 颜色：选中白色图标 + 文字（深色 `0xFF9FC9FF` / 浅色 `0xFF1E40AF`）；未选中 subTextColor。
- 把既有 `navItem("🏠", ...)` 的 emoji TextView 全部换掉；**保留**"图标文字各自 MATCH_PARENT + 居中"的对齐结构。三页（Main/Favorites/UserCenter）数值必须完全一致。

### 5.3 功能卡片（高 152dp）
- 容器：`Tools.liquidGlassCard(c, 22)`（华为/荣耀或 LiquidGlassView 不可用时走内置兜底，维持既有双路径判断，不改）。
- 内容垂直排列、居中：
  1. 图标衬底：40dp × 40dp，背景 `Tools.iconTile(c, t.color, 40, 12)`，居中放 `Icons.tool(c, t, 24)`。
  2. 名称行：14sp bold textColor + 右侧/下方"火爆"胶囊（`t.hot` 时：橙红 12% 底、橙红字、9dp 圆角、9sp）。
  3. 描述：10sp subTextColor，最多两行。
- 收藏星：右上角 15dp，key=`fav`，选中黄 `0xFFF59E0B`，未选 subTextColor；替换既有 `★/☆` 字符。

### 5.4 列表项（高 64dp）
- 容器：`Tools.liquidGlassCard(c, 18)`，左右 16dp 外边距、纵向 8dp 间距。
- 左：40dp 图标衬底（iconTile 12dp 圆角）+ 22dp 图标。
- 中：垂直两行——标题 15sp bold textColor；副标题 11sp subTextColor。
- 右：`Icons.of(c, "arrowRight", subTextColor, 18)` 或 `chevronRight`。

### 5.5 按钮
- 主按钮：`Tools.pillButton(c, 50, true)` 渐变蓝紫 + 白字 15sp bold，高 50dp，左右 16dp 全宽；文字居中。
- 次按钮：`Tools.pillButton(c, 48, false)` 透明 + 主题色描边 1dp + 主题色字 14sp。
- 文字操作按钮（如"清除缓存"）：14sp accentColor，无背景。

### 5.6 状态反馈文案
- 成功：绿色 `0xFF16A34A` / `0xFF22C55E`，前缀"成功："或直接陈述，**禁止 ✅**。
- 失败：错误红，前缀"失败："或"错误："，**禁止 ❌**。
- 例：`"成功：解压完成，共 3 个文件"` / `"失败：密码错误或文件损坏"`。

### 5.7 对话框
- AlertDialog 标题/正文不带 emoji；正文颜色随主题（不硬编码浅色）。
- 自定义内容区（头像选择器等）：色板取色，卡片用 `glassCard(c, 14)`。

### 5.8 空状态页
- 居中：Icons 常用类一个大图标（48dp，subTextColor）+ 说明文字 12sp subTextColor，上下间距 16dp。
- 例：收藏为空 → `Icons.of(c, "fav", subTextColor, 48)` + "还没有收藏，在首页点卡片右上角即可收藏"。

### 5.9 更新日志时间轴（AboutActivity 重设计，A2 批次执行）
- 竖线：宽 2dp、accentColor 30% 透明，沿时间轴左侧贯通。
- 节点：每版本一个 12dp 圆点（accentColor 实心）压在竖线上。
- 版本胶囊徽标：`v3.0` 等，accentColor 12% 底 + accentColor 字，胶囊圆角 9dp，12sp bold。
- 分组：按"最新/历史"用 sectionTitle 分组；每条版本记录一行 13sp textColor + 一行 11sp subTextColor 日期。
- 整体容器：`liquidGlassCard(c, 18)`，内 padding 16/12/16/12。

## 6. 图标用法（尺寸对照，dp）

| 场景 | 衬底 | 图标 |
|---|---|---|
| 功能卡片 | 40 | 24 |
| 列表项 | 40 | 22 |
| 底部导航 | — | 20 |
| 按钮内 compound | — | 16 |
| 页头/板块装饰 | — | 20 |
| 空状态 | — | 48 |
| 对话框/设置行 | 36 | 20 |

`attachToText` 用法：
```java
Icons.attachToText(textView, "settings", Tools.subTextColor(c), 16, 8);
```
**禁止**再出现任何 emoji 字符；`★/☆/▶/⚠/⚙` 等符号字符也一并替换为 Icons key。

## 7. "无 AI 味"规则

1. 每页背景渐变最多一层（`Tools.bgDrawable` 已有）；卡片统一 `liquidGlassCard`，**禁止**再叠多层渐变/高光。
2. 文案自然中文，禁止"震撼来袭 / 全新升级体验 / 炫酷 / 王者归来"等营销腔；写功能怎么用、有什么。
3. 配色克制：蓝紫为主，工具色只出现在图标衬底，不允许整卡五颜六色。
4. 不堆阴影：每页顶部 1–2 个 elevation(dp 8) 即可，列表项靠 glassCard 自带质感。
5. 永久免费、无广告，文案不得诱导付费。

## 8. 性能规则

1. 图标 Drawable 一律走 `Icons` 缓存（Path 全局只建一次）；**禁止**每页 new 一堆 drawable 反复 invalidate。
2. 静态 UI 在 `onCreate` 一次性构建；列表滚动中不得新建 View/Drawable。
3. 不引入 RecyclerView/动画库；保持现有 LinearLayout 列表。
4. Fps/ObjectDetect/游戏/HandBullet 等渲染类的省电帧率控制**不得改动**。
5. 避免滥用 `setElevation`；禁用 `setLayerType` 强行软件渲染。
6. 深色/浅色切换后 `recreate()` 是既有机制，保留。

## 9. 每文件 emoji 清单（grep 结果，逐条消灭）

命令：
```
grep -rnP '[\x{1F300}-\x{1FAFF}\x{2600}-\x{27BF}\x{2B00}-\x{2BFF}]' src/com/roomarchive/server/*.java
```
当前共 125 处（Tools.java 已清零）。完整清单：

```
src/com/roomarchive/server/AboutActivity.java:51:        root.addView(section("📥 下载本软件", new String[]{
src/com/roomarchive/server/AboutActivity.java:113:        sb.append("📋 版本更新记录\n\n");
src/com/roomarchive/server/AboutActivity.java:178:            Toast.makeText(this, "✅ 下载链接已复制，可粘贴发送给朋友", Toast.LENGTH_LONG).show();
src/com/roomarchive/server/AiChatActivity.java:101:        settings.setText("⚙ AI设置");
src/com/roomarchive/server/AIGobangActivity.java:42:        title.setText("⚫ AI 五子棋");
src/com/roomarchive/server/AIGobangActivity.java:61:        btn.setText("🔄 重新开始");
src/com/roomarchive/server/AIGobangActivity.java:94:            status.setText("🎉 你赢了！");
src/com/roomarchive/server/AlipaySoundActivity.java:54:        title.setText("💰 到账提示音");
src/com/roomarchive/server/AppListActivity.java:98:        tip.setText("点击应用可选择：提取安装包 / 查看信息 / 停用 / 卸载\n🔓 Shizuku 强制操作：静默停用/卸载（配合无线调试激活，免 root）\n提取的 APK 自动保存到工具箱目录，可在「我的文件」查看分享");
src/com/roomarchive/server/AppListActivity.java:256:        String[] actions = {"查看信息", "提取安装包 (APK)", "停用（跳系统详情）", "卸载（系统确认）", "🔓 Shizuku 强制操作"};
src/com/roomarchive/server/AppListActivity.java:278:                "🗑 强制卸载（含数据）",
src/com/roomarchive/server/AppListActivity.java:279:                "🗑 静默卸载（保留数据）"};
src/com/roomarchive/server/AppListActivity.java:289:                    case 2: act = ... Toast.makeText(..., "🗑 已强制卸载 " + e.label, ...) ...
src/com/roomarchive/server/AppListActivity.java:290:                    default: ... Toast.makeText(..., "🗑 已静默卸载(保留数据) " + e.label, ...) ...
src/com/roomarchive/server/AppListActivity.java:406:                            Toast.makeText(..., "❌ " + r, ...);
src/com/roomarchive/server/AppListActivity.java:409:                            else Toast.makeText(..., "✅ " + r, ...);
src/com/roomarchive/server/AppListActivity.java:491:                                    "✅ 安装包已提取\n" + dst.getAbsolutePath(), ...
src/com/roomarchive/server/AudioDecibelActivity.java:49:        title.setText("🎤 音频分贝测试");
src/com/roomarchive/server/DailyYouActivity.java:75:        title.setText("📖 每日一签");
src/com/roomarchive/server/DailyYouActivity.java:190:            StringBuilder sb = new StringBuilder("📂 历史心情记录：\n");
src/com/roomarchive/server/DeviceInfoActivity.java:79:        root.addView(sectionTitle("📱 设备信息"));
src/com/roomarchive/server/DeviceInfoActivity.java:88:        root.addView(sectionTitle("📡 传感器支持"));
src/com/roomarchive/server/DeviceInfoActivity.java:97:        root.addView(sectionTitle("🛠 深度信息（Shizuku 补充）"));
src/com/roomarchive/server/DeviceInfoActivity.java:106:        btnDeep.setText("🛠 用 Shizuku 深度获取");
src/com/roomarchive/server/DeviceInfoActivity.java:236:            String status = (s != null) ? "✅ 支持" : "❌ 不支持";
src/com/roomarchive/server/DeviceInfoActivity.java:338:        t.setText((ok ? "🟢 " : "⚪ ") + name + "　" + status);
src/com/roomarchive/server/DuoActivity.java:50:        title.setText("🏓 Duo 双人弹球");
src/com/roomarchive/server/DuoActivity.java:82:        reset.setText("🔄 重新开始");
src/com/roomarchive/server/DuoActivity.java:152:                Toast.makeText(..., "🎉 右边玩家获胜！", ...);
src/com/roomarchive/server/DuoActivity.java:164:                Toast.makeText(..., "🎉 左边玩家获胜！", ...);
src/com/roomarchive/server/FavoritesActivity.java:43:        title.setText("⭐ 我的收藏");
src/com/roomarchive/server/FavoritesActivity.java:68:            empty.setText("还没有收藏\n\n在主界面点功能卡片右下角的 ☆ 即可收藏\n常用的功能一眼就能找到");
src/com/roomarchive/server/FavoritesActivity.java:143:        star.setText("★");
src/com/roomarchive/server/FavoritesActivity.java:169:        bar.addView(navItem("🏠", "首页", 0));
src/com/roomarchive/server/FavoritesActivity.java:170:        bar.addView(navItem("⭐", "收藏", 1));
src/com/roomarchive/server/FavoritesActivity.java:171:        bar.addView(navItem("👤", "我的", 2));
src/com/roomarchive/server/HackerSystemActivity.java:54:        title.setText("💻 HACKER SYSTEM v2.0");
src/com/roomarchive/server/HackerSystemActivity.java:154:                    "⚠ 警告：以上为模拟演示，请勿用于非法用途",
src/com/roomarchive/server/HandBulletActivity.java:46:        title.setText("📱 手持弹幕");
src/com/roomarchive/server/HandBulletActivity.java:210:                btnStart.setText(running ? "🔄 重新开始" : "▶ 开始滚动");
src/com/roomarchive/server/HistoryTodayActivity.java:177:        title.setText("📅 历史上的今天");
src/com/roomarchive/server/MainActivity.java:333:        star.setText(Tools.isFav(this, t.id) ? "★" : "☆");
src/com/roomarchive/server/MainActivity.java:344:                star.setText(now ? "★" : "☆");
src/com/roomarchive/server/MainActivity.java:364:        bar.addView(navItem("🏠", "首页", active == 0, 0));
src/com/roomarchive/server/MainActivity.java:365:        bar.addView(navItem("⭐", "收藏", active == 1, 1));
src/com/roomarchive/server/MainActivity.java:366:        bar.addView(navItem("👤", "我的", active == 2, 2));
src/com/roomarchive/server/NaWaActivity.java:89:            frogFace.setText("😴");
src/com/roomarchive/server/NaWaActivity.java:92:            frogFace.setText("🥺");
src/com/roomarchive/server/NaWaActivity.java:95:            frogFace.setText("😟");
src/com/roomarchive/server/NaWaActivity.java:98:            frogFace.setText("🥱");
src/com/roomarchive/server/NaWaActivity.java:101:            frogFace.setText("🐸");
src/com/roomarchive/server/NaWaActivity.java:104:        statText.setText("🍚 饱食：" + hunger + "　😊 心情：" + mood + "　⚡ 精力：" + energy);
src/com/roomarchive/server/NaWaActivity.java:115:        title.setText("🐸 奶蛙");
src/com/roomarchive/server/NaWaActivity.java:125:        frogFace.setText("🐸");
src/com/roomarchive/server/NaWaActivity.java:151:        row1.addView(actionBtn("🍚 喂食", ...));
src/com/roomarchive/server/NaWaActivity.java:160:        row1.addView(actionBtn("🎮 玩耍", ...));
src/com/roomarchive/server/NaWaActivity.java:176:        sleepBtn = actionBtn(sleeping ? "🔔 叫醒" : "😴 睡觉", ...);
src/com/roomarchive/server/NaWaActivity.java:180:                if (sleepBtn != null) sleepBtn.setText(sleeping ? "🔔 叫醒" : "😴 睡觉");
src/com/roomarchive/server/NaWaActivity.java:186:        row2.addView(actionBtn("💧 洗澡", ...));
src/com/roomarchive/server/PerformanceActivity.java:44:        title.setText("🚀 性能跑分");
src/com/roomarchive/server/PerformanceActivity.java:187:                    "✅ 跑分完成！\n\n🏆 总分：%d（%s）\n\n...",
src/com/roomarchive/server/PerformanceActivity.java:195:                    btnRun.setText("🔄 重新跑分");
src/com/roomarchive/server/PixelJumpActivity.java:57:        title.setText("👾 像素跳跃");
src/com/roomarchive/server/PixelJumpActivity.java:89:        again.setText("🔄 再来一局");
src/com/roomarchive/server/RetroArcadeActivity.java:55:        title.setText("🕹 怀旧街机 · 贪吃蛇");
src/com/roomarchive/server/RetroArcadeActivity.java:87:        restart.setText("🔄 重新开始");
src/com/roomarchive/server/ServerActivity.java:143:        btnOpen.setText("🌐 在本机打开页面");
src/com/roomarchive/server/ServerActivity.java:160:        btnPick.setText("📂 更换要分享的 HTML（可选）");
src/com/roomarchive/server/ServerActivity.java:199:        btnWan.setText("🚀 开启公网访问");
src/com/roomarchive/server/ServerActivity.java:215:        btnCopyWan.setText("📋 复制公网链接");
src/com/roomarchive/server/ServerActivity.java:249:        btnNatFrp.setText("🌸 打开樱花官网（App 内嵌，注册/查配置）");
src/com/roomarchive/server/ServerActivity.java:282:        btnFrpScan.setText("📋 一键识别：粘贴樱花配置自动填");
src/com/roomarchive/server/ServerActivity.java:294:        btnFrp.setText("🌏 frp 一键穿透（国内节点）");
src/com/roomarchive/server/ServerActivity.java:336:        btnCopyLog.setText("📋 复制全部日志");
src/com/roomarchive/server/ServerActivity.java:532:                    btnWan.setText("🚀 开启公网访问");
src/com/roomarchive/server/ServerActivity.java:537:                    btnWan.setText("🚀 开启公网访问");
src/com/roomarchive/server/ServerActivity.java:548:        b.setTitle("🌸 樱花 Frp 官网");
src/com/roomarchive/server/ServerActivity.java:693:            btnWan.setText("🚀 开启公网访问");
src/com/roomarchive/server/SplashActivity.java:94:        warn.setText("⚠ 郑重公告");
src/com/roomarchive/server/SplashActivity.java:126:        dlTip.setText("📥 分享本软件给朋友：");
src/com/roomarchive/server/SplashActivity.java:213:            Toast.makeText(..., "✅ 下载链接已复制，粘贴发送给朋友即可下载", ...);
src/com/roomarchive/server/TextEditorActivity.java:74:        btnOpen.setText("📂 打开");
src/com/roomarchive/server/TextEditorActivity.java:87:        btnSave.setText("💾 保存");
src/com/roomarchive/server/TextEditorActivity.java:115:            Toast.makeText(..., "✅ 已保存到工具箱目录\n" + out.getAbsolutePath(), ...);
src/com/roomarchive/server/TimeCalculatorActivity.java:51:        root.addView(label("📌 日期一（如：恋爱开始 / 出生日期）"));
src/com/roomarchive/server/TimeCalculatorActivity.java:65:        root.addView(label("📌 日期二（如：今天）"));
src/com/roomarchive/server/UserCenterActivity.java:59:        root.addView(listItem("👤", "个人资料", ...));
src/com/roomarchive/server/UserCenterActivity.java:65:        root.addView(listItem("💝", "赞赏我们", ...));
src/com/roomarchive/server/UserCenterActivity.java:71:        root.addView(listItem("✉️", "意见反馈", ...));
src/com/roomarchive/server/UserCenterActivity.java:77:        root.addView(listItem("⚙️", "设置", ...));
src/com/roomarchive/server/UserCenterActivity.java:83:        root.addView(listItem("🧩", "插件管理", ...));
src/com/roomarchive/server/UserCenterActivity.java:89:        root.addView(listItem("🌈", "壁纸随心换", ...));
src/com/roomarchive/server/UserCenterActivity.java:96:        root.addView(listItem("🎨", "切换原生界面", ...));
src/com/roomarchive/server/UserCenterActivity.java:199:        final String[] faces = {"😀", "😎", "🤖", "🐱", "🐶", "🐸", "🌟", "🔥", "🎮", "💪"};
src/com/roomarchive/server/UserCenterActivity.java:244:                .setTitle("💝 赞赏我们")
src/com/roomarchive/server/UserCenterActivity.java:261:                .setTitle("✉️ 意见反馈")
src/com/roomarchive/server/UserCenterActivity.java:305:        clear.setText("🧹 清除缓存（释放存储空间）");
src/com/roomarchive/server/UserCenterActivity.java:327:                .setTitle("⚙️ 设置")
src/com/roomarchive/server/UserCenterActivity.java:356:                .setTitle("🧩 插件管理")
src/com/roomarchive/server/UserCenterActivity.java:370:        bar.addView(navItem("🏠", "首页", 0));
src/com/roomarchive/server/UserCenterActivity.java:371:        bar.addView(navItem("⭐", "收藏", 1));
src/com/roomarchive/server/UserCenterActivity.java:372:        bar.addView(navItem("👤", "我的", 2));
src/com/roomarchive/server/VideoToolActivity.java:81:        root.addView(actionButton("📁 选择视频", ...));
src/com/roomarchive/server/VideoToolActivity.java:90:        root.addView(actionButton("🖼 提取封面（中间帧）", ...));
src/com/roomarchive/server/VideoToolActivity.java:96:        root.addView(actionButton("🎵 提取音频（m4a）", ...));
src/com/roomarchive/server/VideoToolActivity.java:197:                            statusView.setText("✅ 封面已保存\n" + path);
src/com/roomarchive/server/VideoToolActivity.java:205:                            statusView.setText("❌ 提取封面失败：" + e.getMessage());
src/com/roomarchive/server/VideoToolActivity.java:277:                            statusView.setText("✅ 音频已保存（.m4a）\n" + path);
src/com/roomarchive/server/VideoToolActivity.java:285:                            statusView.setText("❌ 提取音频失败：" + e.getMessage());
src/com/roomarchive/server/WallpaperActivity.java:56:        LinearLayout btnDefault = bigButton("🖼 系统默认壁纸", "...");
src/com/roomarchive/server/WallpaperActivity.java:66:        LinearLayout btnCustom = bigButton("📷 自定义壁纸", "...");
src/com/roomarchive/server/WallpaperActivity.java:76:        LinearLayout btn3d = bigButton("✨ 3D 壁纸", "...");
src/com/roomarchive/server/WallpaperActivity.java:169:            Toast.makeText(..., "✅ 壁纸已设置", ...);
src/com/roomarchive/server/ZipToolActivity.java:85:        root.addView(actionButton("📦 解压 ZIP 文件", ...));
src/com/roomarchive/server/ZipToolActivity.java:94:        root.addView(actionButton("🗜 把文件压缩成 ZIP", ...));
src/com/roomarchive/server/ZipToolActivity.java:104:        root.addView(actionButton("🔒 加密加锁（AES）", ...));
src/com/roomarchive/server/ZipToolActivity.java:113:        root.addView(actionButton("🔓 解密（.room 文件）", ...));
src/com/roomarchive/server/ZipToolActivity.java:187:                            statusView.setText("✅ 解压完成：" + c + " 个文件\n" + path);
src/com/roomarchive/server/ZipToolActivity.java:195:                            statusView.setText("❌ 解压失败：" + ex.getMessage());
src/com/roomarchive/server/ZipToolActivity.java:233:                            statusView.setText("✅ 压缩完成：" + uris.size() + " 个文件，共 " + (size / 1024) + " KB\n" + path);
src/com/roomarchive/server/ZipToolActivity.java:241:                            statusView.setText("❌ 压缩失败：" + ex.getMessage());
src/com/roomarchive/server/ZipToolActivity.java:308:                            statusView.setText("✅ 加密完成（AES-256）\n" + path);
src/com/roomarchive/server/ZipToolActivity.java:316:                            statusView.setText("❌ 加密失败：" + ex.getMessage());
src/com/roomarchive/server/ZipToolActivity.java:356:                            statusView.setText("✅ 解密完成\n" + path);
src/com/roomarchive/server/ZipToolActivity.java:364:                            statusView.setText("❌ 解密失败（密码错误或文件损坏）");
```

**特别标注两处 `t.emoji` 引用位点（当前靠兼容字段 `emoji=""` 编译通过，批次代理必须改用 `t.icon + Icons`）：**
- `FavoritesActivity.java:122`：`emoji.setText(t.emoji);` → 改为 40dp iconTile + `Icons.tool(this, t, 24)`。
- `UserCenterActivity.java:352`：`sb.append(t.emoji).append(" ").append(t.name)` → 插件列表改用纯文字 `t.name`（或列表项组件），不要再拼图标字符。

## 10. 已知风格割裂点清单（后续批次必须修复）

| # | 文件 | 问题 | 修复要求 |
|---|---|---|---|
| 1 | UserCenterActivity | 标题硬编码红色 `Color.rgb(220,38,38)` | 改用 `Tools.pageTitle` / textColor，全站蓝紫统一 |
| 2 | UserCenterActivity | 底部栏用旧 `glassBar`，MainActivity 用 `liquidGlassBar` | 换 `Tools.liquidGlassBar` |
| 3 | UserCenterActivity | 导航项无选中态渐变圆底 | 加 `Tools.glassNavIconBg` + Icons.nav |
| 4 | AboutActivity | 全部硬编码浅色（深色不可读） | 全部取色板，深色可读 |
| 5 | AboutActivity | 更新日志纯文本块（用户点名"太丑"） | A2 重设计为 §5.9 时间轴卡片 |
| 6 | BaseActivity | 状态栏此前硬编码浅色 | 已由本批修复（applyStatusBar 自适应） |
| 7 | FavoritesActivity | 卡片用大字 emoji | 换 iconTile + Icons.tool |
| 8 | MainActivity | 星标 ★/☆ 字符、导航 emoji | 换 Icons `fav` / `nav` |

## 11. 版本号纪律

- 任何包含 `2.3.42` 的行**一律不得改动**（后续统一升 v3.0，由专人处理）。
- 批次代理不要在界面里写版本号；需要展示版本号时调用既有 `UpdateChecker` 的既有方法，不要硬编码新字符串。

## 12. 批次交付自检清单（逐条打勾）

- [ ] 页面全部走本规范色板 / 圆角 / 间距 / 字体层级，无硬编码浅色。
- [ ] 交付文件 `grep -rnP '[\x{1F300}-\x{1FAFF}\x{2600}-\x{27BF}\x{2B00}-\x{2BFF}]'` 结果为空（含 ★☆▶⚠⚙ 等符号字符）。
- [ ] 逻辑零改动：收藏 / 主题 / 网络 / 文件 / 游戏渲染行为不变。
- [ ] 深色、浅色两种模式均亲自切换验证可读。
- [ ] 未碰 `Tools.java` / `Icons.java` / `BaseActivity.java` / `DesignSpec.md` / `AndroidManifest.xml` / 任何 `2.3.42` 版本行。
- [ ] 图标全部来自 `Icons` 缓存工厂，无新 emoji、无图片加载。
- [ ] 滚动流畅，无每帧新建 Drawable / Path。
- [ ] 文案自然中文，无营销腔，无"震撼/炫酷/全新升级"。
- [ ] 主按钮 = `Tools.pillButton(true)`，次按钮 = `pillButton(false)`，状态文案绿/红无 emoji。
- [ ] 底部导航内容区高度 ≥76dp、图标文字间距充足（图标 20dp / 容器 36dp / 文字 11sp / 间距 4dp）、安全区适配正确（WindowInsets 条件补边）、三页完全一致（可覆盖安装后实测比对）。
- [ ] `./build.sh` 在自己改完后编译通过。
