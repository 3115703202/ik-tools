package com.roomarchive.server;
import android.app.Activity;
import android.os.*;
import android.view.Gravity;
import android.widget.*;

public class StopwatchActivity extends Activity {
    Chronometer chrono;
    Button startBtn, lapBtn;
    long base = 0;
    boolean running = false;
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Tools.bgWithGlow(this));
        root.setPadding(dp(16), dp(20), dp(16), dp(16));
        root.addView(Tools.pageTitle(this, "秒表"));
        chrono = new Chronometer(this);
        chrono.setTextSize(48);
        chrono.setGravity(Gravity.CENTER);
        chrono.setPadding(0, dp(40), 0, dp(40));
        root.addView(chrono);
        startBtn = new Button(this);
        startBtn.setText("开始");
        startBtn.setTextSize(16);
        startBtn.setOnClickListener(v -> {
            if (running) { chrono.stop(); startBtn.setText("继续"); running = false; }
            else { chrono.start(); startBtn.setText("暂停"); running = true; }
        });
        root.addView(startBtn);
        Button reset = new Button(this);
        reset.setText("重置");
        reset.setOnClickListener(v -> { chrono.stop(); chrono.setBase(SystemClock.elapsedRealtime()); startBtn.setText("开始"); running = false; });
        root.addView(reset);
        setContentView(root);
    }
    int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
}
