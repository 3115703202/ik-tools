package com.roomarchive.server;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** 物流查询：输入单号跳转快递100 */
public class ExpressActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        sv.setBackground(Tools.bgWithGlow(this));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(20));
        sv.addView(root);

        root.addView(Tools.pageTitle(this, "物流查询"));

        TextView hint = new TextView(this);
        hint.setText("输入快递单号，自动跳转快递100查询");
        hint.setTextSize(13);
        hint.setTextColor(Tools.subTextColor(this));
        root.addView(hint);
        root.addView(spacer(12));

        EditText input = new EditText(this);
        input.setHint("如 SF1234567890");
        input.setSingleLine(true);
        input.setTextSize(15);
        input.setTextColor(Tools.textColor(this));
        input.setHintTextColor(Tools.subTextColor(this));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Tools.cardColor(this));
        bg.setCornerRadius(dp(12));
        input.setBackground(bg);
        input.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.addView(input);
        root.addView(spacer(12));

        android.widget.Button btn = new android.widget.Button(this);
        btn.setText("查询");
        btn.setAllCaps(false);
        btn.setTextColor(Color.WHITE);
        btn.setBackground(Tools.pillButton(this, 44, true));
        btn.setOnClickListener(v -> {
            String no = input.getText().toString().trim();
            if (TextUtils.isEmpty(no)) { Toast.makeText(this, "输入单号", Toast.LENGTH_SHORT).show(); return; }
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.kuaidi100.com/chaxun?nu=" + no)));
            } catch (Exception e) {
                Toast.makeText(this, "无法打开", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(btn);

        setContentView(sv);
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private View spacer(int h) { View v = new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h))); return v; }
}
