package com.roomarchive.server;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;

/**
 * 性能跑分：CPU 单核/多核、内存读写、存储读写综合评分。
 */
public class PerformanceActivity extends BaseActivity {

    private TextView resultBox;
    private Button btnRun;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(18));
        scroll.addView(root);

        TextView title = Tools.pageTitle(this, "性能跑分");
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        root.addView(spacer(8));

        TextView sub = new TextView(this);
        sub.setText("CPU / 内存 / 存储 综合测试（本地计算，不联网）");
        sub.setTextSize(12);
        sub.setTextColor(Tools.subTextColor(this));
        sub.setGravity(Gravity.CENTER);
        root.addView(sub);

        root.addView(spacer(14));

        resultBox = new TextView(this);
        resultBox.setText("点击下方按钮开始跑分\n\n跑分过程约 10~30 秒，请勿锁屏");
        resultBox.setTextSize(14);
        resultBox.setTextColor(Tools.textColor(this));
        resultBox.setLineSpacing(dp(4), 1.0f);
        resultBox.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientDrawable rb = new GradientDrawable();
        rb.setColor(Tools.cardColor(this));
        rb.setCornerRadius(dp(14));
        rb.setStroke(dp(1), Tools.isDark(this) ? 0xFF2A3040 : 0xFFE4E8EE);
        resultBox.setBackground(rb);
        root.addView(resultBox);

        root.addView(spacer(14));

        btnRun = new Button(this);
        btnRun.setText("开始跑分");
        btnRun.setAllCaps(false);
        btnRun.setTextColor(Color.WHITE);
        btnRun.setBackground(Tools.pillButton(this, 52, true));
        root.addView(btnRun);
        btnRun.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (running) return;
                running = true;
                btnRun.setEnabled(false);
                btnRun.setText("跑分中…");
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        runBenchmark();
                    }
                }).start();
            }
        });

        root.addView(spacer(10));

        TextView note = new TextView(this);
        note.setText("评分说明：\n· CPU 整数运算 + 浮点运算，单核与四核并行各测\n· 内存 64MB 读写速度\n· 存储 32MB 顺序写读\n· 总分 0~9999，越高越强，同机型可多次对比");
        note.setTextSize(11);
        note.setTextColor(Tools.subTextColor(this));
        root.addView(note);

        root.setBackground(Tools.bgWithGlow(this));
        setContentView(scroll);
    }

    private void runBenchmark() {
        try {
            // ===== CPU 单核 =====
            log("① CPU 单核测试…");
            long t0 = System.nanoTime();
            cpuWork();
            long t1 = System.nanoTime();
            double cpuSingle = 1e9 * 6.0 / (t1 - t0);   // 越大越快

            // ===== CPU 四核并行 =====
            log("② CPU 四核并行测试…");
            final long[] times = new long[4];
            Thread[] ts = new Thread[4];
            for (int i = 0; i < 4; i++) {
                final int idx = i;
                ts[i] = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        long a = System.nanoTime();
                        cpuWork();
                        long b = System.nanoTime();
                        times[idx] = b - a;
                    }
                });
            }
            for (Thread t : ts) t.start();
            for (Thread t : ts) t.join();
            long minT = Long.MAX_VALUE;
            for (long t : times) minT = Math.min(minT, t);
            double cpuMulti = 1e9 * 6.0 * 4 / minT;   // 并行吞吐

            // ===== 内存读写 =====
            log("③ 内存读写测试…");
            int[] mem = new int[16 * 1024 * 1024];   // 64MB
            long mt0 = System.nanoTime();
            for (int i = 0; i < mem.length; i++) mem[i] = i;
            long sum = 0;
            for (int i = 0; i < mem.length; i++) sum += mem[i];
            long mt1 = System.nanoTime();
            double memSpeed = mem.length * 2L * 4 * 1e9 / (mt1 - mt0) / 1024 / 1024; // MB/s

            // ===== 存储读写 =====
            log("④ 存储读写测试…");
            File f = new File(getCacheDir(), "bench_tmp.bin");
            byte[] data = new byte[32 * 1024 * 1024];
            for (int i = 0; i < data.length; i++) data[i] = (byte) (i & 0xff);
            long st0 = System.nanoTime();
            FileOutputStream fos = new FileOutputStream(f);
            fos.write(data);
            fos.getFD().sync();
            fos.close();
            long st1 = System.nanoTime();
            RandomAccessFile raf = new RandomAccessFile(f, "r");
            byte[] rd = new byte[32 * 1024 * 1024];
            raf.readFully(rd);
            raf.close();
            long st2 = System.nanoTime();
            f.delete();
            double writeMb = 32.0 * 1e9 / (st1 - st0);
            double readMb = 32.0 * 1e9 / (st2 - st1);

            // ===== 综合评分 =====
            double score = cpuSingle * 400 + cpuMulti * 120 + Math.min(memSpeed, 20000) * 0.35
                    + Math.min(writeMb, 3000) * 0.6 + Math.min(readMb, 6000) * 0.25;
            int total = (int) Math.min(9999, score);
            String grade;
            if (total >= 6000) grade = "旗舰级";
            else if (total >= 4000) grade = "高性能";
            else if (total >= 2500) grade = "中高端";
            else if (total >= 1500) grade = "中端";
            else grade = "入门级";

            final String out = String.format(java.util.Locale.US,
                    "跑分完成！\n\n总分：%d（%s）\n\nCPU 单核：%.0f 分\nCPU 四核：%.0f 分\n内存读写：%.0f MB/s\n存储写入：%.0f MB/s\n存储读取：%.0f MB/s\n\n跑分结果仅反映本机当前状态，\n可多次测试对比（建议关闭后台应用）。",
                    total, grade, cpuSingle * 400, cpuMulti * 120, memSpeed, writeMb, readMb);

            handler.post(new Runnable() {
                @Override
                public void run() {
                    resultBox.setText(out);
                    btnRun.setEnabled(true);
                    btnRun.setText("重新跑分");
                    running = false;
                }
            });
        } catch (Throwable e) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    resultBox.setText("跑分失败：" + e.getMessage());
                    btnRun.setEnabled(true);
                    btnRun.setText("开始跑分");
                    running = false;
                    Toast.makeText(PerformanceActivity.this, "跑分异常", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /** CPU 计算负载：整数 + 浮点 */
    private void cpuWork() {
        double d = 0;
        long l = 0;
        for (int i = 0; i < 2000000; i++) {
            l += (long) i * (i & 1023);
            d += Math.sin(i * 0.0001) * 1.0000001;
        }
        if (l == 0 || d == 0) {
            // 防止优化
        }
    }

    private void log(final String msg) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                resultBox.setText(msg + "\n（正在计算，请稍候…）");
            }
        });
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
