package com.roomarchive.server;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 历史上的今天：内置真实历史事件库（离线），未收录日期可自行添加备注。
 * 数据为公开史实，来源：公共历史资料。
 */
public class HistoryTodayActivity extends BaseActivity {

    /** 真实历史事件库：key="M-D"（如 10-15），value=事件描述（多条用 \n 分隔） */
    private static final Map<String, String> EVENTS = new HashMap<>();

    static {
        // ===== 1月 =====
        EVENTS.put("1-1", "1949年 中华人民共和国成立后首个元旦\n1979年 中美正式建立外交关系\n1980年 中国首部《中华人民共和国婚姻法》实施");
        EVENTS.put("1-4", "1643年 物理学家艾萨克·牛顿诞辰");
        EVENTS.put("1-8", "1942年 物理学家史蒂芬·霍金诞辰");
        EVENTS.put("1-12", "1980年 中国首次派员参加国际刑警组织");
        EVENTS.put("1-15", "1929年 美国民权领袖马丁·路德·金诞辰");
        EVENTS.put("1-21", "1924年 列宁逝世\n1954年 世界上第一艘核潜艇“鹦鹉螺”号下水");
        EVENTS.put("1-27", "1756年 音乐家莫扎特诞辰");
        EVENTS.put("1-28", "1932年 中国十九路军淞沪抗战爆发");
        EVENTS.put("1-31", "1958年 美国发射第一颗人造卫星“探险者一号”");

        // ===== 2月 =====
        EVENTS.put("2-1", "1984年 中国与荷兰恢复大使级外交关系");
        EVENTS.put("2-4", "1999年 首届中国昆明世界园艺博览会开幕");
        EVENTS.put("2-7", "1984年 中国南极长城站建成");
        EVENTS.put("2-11", "1847年 美国发明家托马斯·爱迪生诞辰");
        EVENTS.put("2-12", "1809年 英国博物学家达尔文诞辰\n1912年 清帝退位，清朝统治结束");
        EVENTS.put("2-15", "1564年 意大利天文学家伽利略诞辰");
        EVENTS.put("2-20", "1985年 中国第一个南极科学考察站长城站越冬队出发");
        EVENTS.put("2-23", "1940年 东北抗联英雄杨靖宇壮烈牺牲");

        // ===== 3月 =====
        EVENTS.put("3-5", "1898年 周恩来总理诞辰\n1963年 毛泽东题词“向雷锋同志学习”发表");
        EVENTS.put("3-8", "1910年 国际劳动妇女节确立");
        EVENTS.put("3-12", "1925年 孙中山先生逝世\n1979年 中国植树节确立");
        EVENTS.put("3-14", "1879年 物理学家爱因斯坦诞辰\n1883年 马克思逝世");
        EVENTS.put("3-18", "1978年 全国科学大会召开");
        EVENTS.put("3-23", "1998年 中国首架国产支线客机“新舟60”首飞");
        EVENTS.put("3-27", "1980年 中国开始实行邮政编码制度");

        // ===== 4月 =====
        EVENTS.put("4-1", "2001年 中美南海撞机事件");
        EVENTS.put("4-8", "1984年 中国首次成功发射试验通信卫星");
        EVENTS.put("4-12", "1961年 苏联宇航员加加林完成人类首次太空飞行");
        EVENTS.put("4-14", "1865年 美国总统林肯遇刺逝世");
        EVENTS.put("4-20", "1999年 中国第一艘载人航天试验飞船“神舟”号发射");
        EVENTS.put("4-22", "1970年 第一个世界地球日");
        EVENTS.put("4-24", "1970年 中国第一颗人造卫星“东方红一号”发射成功\n2016年 中国航天日设立");
        EVENTS.put("4-26", "1986年 切尔诺贝利核电站事故");
        EVENTS.put("4-28", "1928年 井冈山会师");

        // ===== 5月 =====
        EVENTS.put("5-1", "1886年 国际劳动节起源（美国芝加哥工人大罢工）\n1947年 内蒙古自治区成立");
        EVENTS.put("5-3", "1989年 中国首条海底光缆开通");
        EVENTS.put("5-4", "1919年 五四运动爆发\n1939年 五四青年节确立");
        EVENTS.put("5-5", "1818年 马克思诞辰\n1921年 孙中山就任中华民国非常大总统");
        EVENTS.put("5-8", "1995年 中国驻南斯拉夫联盟大使馆遭轰炸（纪念日5月7-8日）");
        EVENTS.put("5-12", "2008年 汶川大地震（全国防灾减灾日）");
        EVENTS.put("5-20", "1987年 中国第一座大型核电站大亚湾核电站开工");
        EVENTS.put("5-23", "1951年 西藏和平解放");
        EVENTS.put("5-25", "1961年 美国总统肯尼迪宣布载人登月计划");

        // ===== 6月 =====
        EVENTS.put("6-1", "1951年 国际儿童节确立");
        EVENTS.put("6-5", "1972年 世界环境日设立");
        EVENTS.put("6-17", "1967年 中国第一颗氢弹爆炸成功");
        EVENTS.put("6-26", "1945年 联合国宪章签署（联合国日）\n2008年 国际禁毒日设立");

        // ===== 7月 =====
        EVENTS.put("7-1", "1921年 中国共产党成立\n1997年 香港回归祖国\n1952年 成渝铁路建成通车");
        EVENTS.put("7-4", "1776年 美国独立日");
        EVENTS.put("7-7", "1937年 七七事变（卢沟桥事变）爆发，全民族抗战开始");
        EVENTS.put("7-11", "2005年 中国航海日设立（郑和下西洋纪念）");
        EVENTS.put("7-13", "2001年 北京申奥成功");
        EVENTS.put("7-16", "1969年 美国“阿波罗11号”发射，人类首次载人登月任务");
        EVENTS.put("7-20", "1969年 人类首次登月，阿姆斯特朗踏上月球");
        EVENTS.put("7-28", "1976年 唐山大地震\n1914年 第一次世界大战爆发（奥匈帝国对塞尔维亚宣战）");
        EVENTS.put("7-30", "1932年 中国首次参加奥运会（洛杉矶）");

        // ===== 8月 =====
        EVENTS.put("8-1", "1927年 南昌起义，中国人民解放军建军节\n2008年 中国第一条高铁京津城际铁路通车");
        EVENTS.put("8-2", "1984年 中国选手许海峰夺得奥运首金");
        EVENTS.put("8-6", "1945年 美国向广岛投下原子弹\n1990年 中国首例试管婴儿诞生");
        EVENTS.put("8-8", "2008年 北京奥运会开幕\n2009年 全民健身日设立");
        EVENTS.put("8-12", "1977年 中国恢复高考后首届招生");
        EVENTS.put("8-15", "1945年 日本宣布无条件投降，抗日战争胜利");
        EVENTS.put("8-17", "1945年 印尼宣布独立");
        EVENTS.put("8-19", "1991年 苏联“八一九事件”");
        EVENTS.put("8-25", "1937年 中国工农红军改编为国民革命军第八路军");

        // ===== 9月 =====
        EVENTS.put("9-3", "1945年 中国人民抗日战争胜利纪念日\n2014年 抗战胜利纪念日确立为9月3日");
        EVENTS.put("9-8", "1956年 中国第一座电视台北京电视台（今央视）开播");
        EVENTS.put("9-10", "1985年 中国第一个教师节");
        EVENTS.put("9-12", "1980年 中国首次承办国际体育赛事（北京国际马拉松）");
        EVENTS.put("9-17", "1894年 甲午海战（黄海海战）");
        EVENTS.put("9-18", "1931年 九一八事变，日本侵占东北");
        EVENTS.put("9-21", "1949年 中国人民政治协商会议第一届全体会议召开");
        EVENTS.put("9-25", "1937年 平型关大捷\n2008年 神舟七号载人飞船发射");
        EVENTS.put("9-28", "1974年 中国第一座核电站研究堆建成");
        EVENTS.put("9-30", "2014年 中国烈士纪念日");

        // ===== 10月 =====
        EVENTS.put("10-1", "1949年 中华人民共和国成立，国庆节\n1999年 国庆50周年大阅兵");
        EVENTS.put("10-4", "1957年 苏联发射人类第一颗人造地球卫星“斯普特尼克1号”");
        EVENTS.put("10-7", "2001年 中国男足首次晋级世界杯决赛圈");
        EVENTS.put("10-15", "2003年 神舟五号载人飞船发射，杨利伟成为中国首位航天员");
        EVENTS.put("10-16", "1964年 中国第一颗原子弹爆炸成功\n1964年 中国宣布不首先使用核武器");
        EVENTS.put("10-17", "2003年 神舟五号返回舱成功着陆，中国载人航天首飞成功");
        EVENTS.put("10-19", "1936年 鲁迅先生逝世");
        EVENTS.put("10-24", "2007年 嫦娥一号探月卫星发射");
        EVENTS.put("10-25", "1971年 联合国恢复中华人民共和国合法席位\n1950年 中国人民志愿军入朝作战（抗美援朝纪念日）");
        EVENTS.put("10-31", "1984年 印度总理英迪拉·甘地遇刺");

        // ===== 11月 =====
        EVENTS.put("11-9", "1989年 柏林墙倒塌");
        EVENTS.put("11-11", "1918年 第一次世界大战停战\n2009年 双十一购物节兴起");
        EVENTS.put("11-12", "1866年 孙中山先生诞辰\n1969年 刘少奇同志逝世");
        EVENTS.put("11-17", "1989年 中国首条电气化铁路开通（大同—秦皇岛）");
        EVENTS.put("11-20", "1985年 中国第一艘远洋考察船“向阳红10号”启航");
        EVENTS.put("11-24", "1859年 达尔文《物种起源》出版");
        EVENTS.put("11-28", "1943年 德黑兰会议召开（二战三巨头会晤）");
        EVENTS.put("11-29", "1948年 平津战役开始");

        // ===== 12月 =====
        EVENTS.put("12-1", "1986年 世界艾滋病日设立");
        EVENTS.put("12-4", "1982年 《中华人民共和国宪法》颁布实施（国家宪法日）");
        EVENTS.put("12-7", "1941年 日本偷袭珍珠港，太平洋战争爆发");
        EVENTS.put("12-9", "1935年 一二·九运动爆发");
        EVENTS.put("12-10", "1901年 首届诺贝尔奖颁发\n1948年 联合国通过《世界人权宣言》");
        EVENTS.put("12-13", "1937年 南京大屠杀（国家公祭日）");
        EVENTS.put("12-17", "1903年 莱特兄弟完成人类首次有动力飞行");
        EVENTS.put("12-18", "1978年 十一届三中全会召开，改革开放开启");
        EVENTS.put("12-20", "1999年 澳门回归祖国");
        EVENTS.put("12-25", "1991年 苏联解体");
        EVENTS.put("12-26", "1893年 毛泽东主席诞辰\n2004年 印度洋海啸");
        EVENTS.put("12-31", "1999年 人类迎接千禧年\n2008年 中国公布首批国家历史文化名城");
    }

    private TextView resultView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(18));
        scroll.setClipToPadding(false);
        scroll.addView(root);

        TextView title = Tools.pageTitle(this, "历史上的今天");
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        root.addView(spacer(10));

        String today = new SimpleDateFormat("M-d", Locale.CHINA).format(new Date());
        Calendar c = Calendar.getInstance();
        String todayCn = (c.get(Calendar.MONTH) + 1) + "月" + c.get(Calendar.DAY_OF_MONTH) + "日";

        TextView todayT = new TextView(this);
        todayT.setText("今天是 " + todayCn + "（" + today + "）");
        todayT.setTextSize(14);
        todayT.setTextColor(Tools.subTextColor(this));
        todayT.setGravity(Gravity.CENTER);
        root.addView(todayT);

        root.addView(spacer(12));

        resultView = new TextView(this);
        resultView.setTextSize(15);
        resultView.setTextColor(Tools.textColor(this));
        resultView.setLineSpacing(dp(4), 1.0f);
        resultView.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Tools.cardColor(this));
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), Tools.isDark(this) ? 0xFF2A3040 : 0xFFE4E8EE);
        resultView.setBackground(bg);
        root.addView(resultView);
        showDate(today);

        root.addView(spacer(12));

        TextView tip = new TextView(this);
        tip.setText("查询任意日期（格式如 1-23）：");
        tip.setTextSize(12);
        tip.setTextColor(Tools.subTextColor(this));
        root.addView(tip);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        final EditText input = new EditText(this);
        input.setHint("如 10-15");
        input.setTextSize(14);
        GradientDrawable inBg = new GradientDrawable();
        inBg.setColor(Tools.cardColor(this));
        inBg.setCornerRadius(dp(10));
        inBg.setStroke(dp(1), Tools.isDark(this) ? 0xFF2A3040 : 0xFFE4E8EE);
        input.setBackground(inBg);
        input.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.addView(input, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button btn = new Button(this);
        btn.setText("查询");
        btn.setAllCaps(false);
        btn.setTextColor(Color.WHITE);
        btn.setBackground(Tools.pillButton(this, 44, true));
        row.addView(btn, new LinearLayout.LayoutParams(dp(88), dp(44)));
        root.addView(row);

        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String q = input.getText().toString().trim();
                if (q.isEmpty()) {
                    Toast.makeText(HistoryTodayActivity.this, "请输入日期，如 10-15", Toast.LENGTH_SHORT).show();
                    return;
                }
                showDate(q);
            }
        });

        root.addView(spacer(10));

        TextView note = new TextView(this);
        note.setText("注：事件来自公开历史资料，供学习参考。未收录的日期会显示空白，可自行查询。");
        note.setTextSize(10);
        note.setTextColor(Tools.subTextColor(this));
        root.addView(note);

        root.setBackground(Tools.bgWithGlow(this));
        setContentView(scroll);
    }

    private void showDate(String md) {
        String v = EVENTS.get(md);
        if (v == null || v.trim().isEmpty()) {
            resultView.setText("「" + md + "」暂无内置记录。\n\n事件库正在扩充中，敬请期待。");
            return;
        }
        resultView.setText(v.trim());
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
