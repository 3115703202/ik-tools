package com.roomarchive.server;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** 时光计算器：两个日期之间的天数 / 倒计时 / 年龄 */
public class TimeCalculatorActivity extends BaseActivity {

    private Calendar calA, calB;
    private TextView showA, showB, result;
    private SimpleDateFormat fmt = new SimpleDateFormat("yyyy年M月d日", Locale.CHINA);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        calA = Calendar.getInstance();
        calB = Calendar.getInstance();
        calA.add(Calendar.DAY_OF_YEAR, -100);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(18));
        scroll.addView(root);

        TextView title = Tools.pageTitle(this, "时光计算器");
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        root.addView(spacer(16));

        root.addView(label("日期一（如：恋爱开始 / 出生日期）"));
        showA = dateBox();
        root.addView(showA);
        Button btnA = smallButton("选择日期一", Tools.accentColor(this));
        root.addView(btnA);
        btnA.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickDate(calA, showA, true);
            }
        });

        root.addView(spacer(12));

        root.addView(label("日期二（如：今天）"));
        showB = dateBox();
        root.addView(showB);
        Button btnB = smallButton("选择日期二", Tools.accentColor(this));
        root.addView(btnB);
        btnB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickDate(calB, showB, false);
            }
        });

        root.addView(spacer(14));

        Button calc = new Button(this);
        calc.setText("开始计算");
        calc.setAllCaps(false);
        calc.setTextColor(Color.WHITE);
        calc.setBackground(Tools.pillButton(this, 50, true));
        root.addView(calc);
        calc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                calcResult();
            }
        });

        root.addView(spacer(14));

        result = new TextView(this);
        result.setTextSize(14);
        result.setTextColor(Tools.textColor(this));
        result.setLineSpacing(dp(4), 1.0f);
        result.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientDrawable rb = new GradientDrawable();
        rb.setColor(Tools.cardColor(this));
        rb.setCornerRadius(dp(14));
        rb.setStroke(dp(1), Tools.isDark(this) ? 0xFF2A3040 : 0xFFE4E8EE);
        result.setBackground(rb);
        root.addView(result);

        refreshText();
        root.setBackground(Tools.bgWithGlow(this));
        setContentView(scroll);
    }

    private void pickDate(final Calendar cal, final TextView box, final boolean isA) {
        new DatePickerDialog(this,
                new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(android.widget.DatePicker view, int year, int month, int day) {
                        cal.set(year, month, day);
                        refreshText();
                    }
                },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
                .show();
    }

    private void calcResult() {
        long msA = calA.getTimeInMillis();
        long msB = calB.getTimeInMillis();
        long diff = Math.abs(msB - msA);
        long days = diff / (24L * 3600 * 1000);
        long years = days / 365;
        long remainDays = days % 365;
        long months = days / 30;

        StringBuilder sb = new StringBuilder();
        sb.append("两日期相差：\n");
        sb.append("· ").append(days).append(" 天\n");
        sb.append("· ").append(years).append(" 年 ").append(remainDays).append(" 天\n");
        sb.append("· 约 ").append(months).append(" 个月\n");
        sb.append("· 约 ").append(days * 24L).append(" 小时\n");
        sb.append("· 约 ").append(days * 24L * 60L).append(" 分钟\n");
        if (days > 0) {
            Calendar now = Calendar.getInstance();
            long toNow = now.getTimeInMillis() - msA;
            if (toNow >= 0) {
                sb.append("\n日期一距今已过 ").append(toNow / (24L * 3600 * 1000)).append(" 天");
            }
        }
        result.setText(sb.toString());
    }

    private void refreshText() {
        showA.setText(fmt.format(calA.getTime()));
        showB.setText(fmt.format(calB.getTime()));
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(13);
        t.setTextColor(Tools.subTextColor(this));
        t.setPadding(dp(2), dp(4), dp(2), dp(4));
        return t;
    }

    private TextView dateBox() {
        TextView t = new TextView(this);
        t.setTextSize(16);
        t.setTextColor(Tools.textColor(this));
        t.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Tools.cardColor(this));
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), Tools.isDark(this) ? 0xFF2A3040 : 0xFFE4E8EE);
        t.setBackground(bg);
        return t;
    }

    private Button smallButton(String txt, int color) {
        Button b = new Button(this);
        b.setText(txt);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(10));
        b.setBackground(bg);
        return b;
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
