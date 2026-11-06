package com.roomarchive.server;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.graphics.Color;

public class UnitConvertActivity extends Activity {
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Tools.bgWithGlow(this));
        root.setPadding(dp(16), dp(20), dp(16), dp(16));
        root.addView(Tools.pageTitle(this, "单位换算"));

        String[] cats = {"长度", "重量", "温度", "面积", "存储", "速度"};
        for (String cat : cats) {
            TextView tv = new TextView(this);
            tv.setText(cat);
            tv.setTextSize(16);
            tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            tv.setPadding(0, dp(16), 0, dp(8));
            root.addView(tv);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            final EditText in = new EditText(this);
            in.setHint("输入值");
            in.setBackgroundColor(0x33FFFFFF);
            in.setPadding(dp(12), dp(10), dp(12), dp(10));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, dp(44), 1f);
            row.addView(in, ip);
            TextView out = new TextView(this);
            out.setGravity(Gravity.CENTER);
            out.setPadding(dp(12), 0, 0, 0);
            out.setTextSize(14);
            out.setTextColor(0xFF3B82F6);
            row.addView(out);
            root.addView(row);
            in.addTextChangedListener(new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence c, int a, int b, int d) {}
                public void onTextChanged(CharSequence c, int a, int b, int d) {}
                public void afterTextChanged(android.text.Editable e) {
                    try {
                        double v = Double.parseDouble(e.toString());
                        String r = "";
                        if (cat.equals("长度")) r = v + " m = " + (v*3.28084) + " ft = " + (v*100) + " cm = " + (v*1000) + " mm";
                        else if (cat.equals("重量")) r = v + " kg = " + (v*2.20462) + " lb = " + (v*1000) + " g";
                        else if (cat.equals("温度")) r = v + "°C = " + (v*9/5+32) + "°F = " + (v+273.15) + "K";
                        else if (cat.equals("面积")) r = v + " m² = " + (v*10.7639) + " ft² = " + (v*10000) + " cm²";
                        else if (cat.equals("存储")) r = v + " MB = " + (v/1024) + " GB = " + (v*1024) + " KB";
                        else if (cat.equals("速度")) r = v + " km/h = " + (v/3.6) + " m/s = " + (v*0.621371) + " mph";
                        out.setText(r);
                    } catch (Exception ignored) {}
                }
            });
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
    }
    int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
}
