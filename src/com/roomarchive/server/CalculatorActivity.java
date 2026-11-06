package com.roomarchive.server;
import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import android.graphics.Color;

public class CalculatorActivity extends Activity {
    String expr = "";
    TextView display;
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Tools.bgWithGlow(this));
        root.setPadding(dp(16), dp(20), dp(16), dp(16));
        root.addView(Tools.pageTitle(this, "计算器"));
        display = new TextView(this);
        display.setTextSize(32);
        display.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        display.setPadding(0, dp(20), 0, dp(20));
        root.addView(display);
        String[][] btns = {
            {"7","8","9","/"},
            {"4","5","6","*"},
            {"1","2","3","-"},
            {"0",".","=","+"},
            {"C","(","%","^"}
        };
        for (String[] row : btns) {
            LinearLayout lr = new LinearLayout(this);
            lr.setOrientation(LinearLayout.HORIZONTAL);
            lr.setWeightSum(4);
            for (String b : row) {
                Button btn = new Button(this);
                btn.setText(b);
                btn.setTextSize(18);
                btn.setAllCaps(false);
                btn.setOnClickListener(v -> onBtn(b));
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(56), 1f);
                p.setMargins(dp(4), dp(4), dp(4), dp(4));
                lr.addView(btn, p);
            }
            root.addView(lr);
        }
        setContentView(root);
    }
    void onBtn(String b) {
        if (b.equals("C")) { expr = ""; display.setText("0"); return; }
        if (b.equals("=")) {
            try { display.setText(eval(expr) + ""); } catch (Exception e) { display.setText("错误"); }
            return;
        }
        expr += b;
        display.setText(expr);
    }
    double eval(String e) {
        try { return (double) new javax.script.ScriptEngineManager().getEngineByName("js").eval(e.replace("^","**").replace("%","/100")); }
        catch (Exception ex) {
            // simple eval
            return 0;
        }
    }
    int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
}
