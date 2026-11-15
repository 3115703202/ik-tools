package com.roomarchive.server;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.Locale;

/** 网络测速：TCP 握手延迟 + CDN 下载速度 */
public class SpeedTestActivity extends BaseActivity {

    private static final String TEST_URL = "https://aka.doubaocdn.com/s/E5HCJtUO3q";
    private static final String HOST = "223.5.5.5";

    private TextView pingText, downText, peakText, stateText, btn;
    private ProgressBar bar;
    private boolean running = false;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Tools.bgWithGlow(this));
        root.setPadding(dp(18), dp(24), dp(18), dp(24));
        sv.addView(root);

        TextView title = Tools.pageTitle(this, "网络测速");
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("延迟 + 下载速度 · 用的是 CDN 实测，不是估算");
        sub.setTextSize(12);
        sub.setTextColor(Tools.subTextColor(this));
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(6), 0, dp(16));
        root.addView(sub);

        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        cards.setGravity(Gravity.CENTER);
        root.addView(cards);
        pingText = card(cards, "延迟", "-- ms");
        downText = card(cards, "下载", "-- Mbps");
        peakText = card(cards, "峰值", "-- Mbps");

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(10));
        bp.topMargin = dp(18);
        root.addView(bar, bp);

        stateText = new TextView(this);
        stateText.setTextSize(12);
        stateText.setTextColor(Tools.subTextColor(this));
        stateText.setGravity(Gravity.CENTER);
        stateText.setPadding(0, dp(10), 0, dp(14));
        root.addView(stateText);

        btn = new TextView(this);
        btn.setText("开始测速");
        btn.setTextSize(15);
        btn.setTextColor(0xFFFFFFFF);
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(Tools.pillButton(this, 50, true));
        btn.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50)));
        btn.setOnClickListener(v -> start());
        root.addView(btn);

        TextView tip = new TextView(this);
        tip.setText("测试会消耗约 10–20MB 流量，建议 WiFi 下测。延迟取 3 次 TCP 握手平均。");
        tip.setTextSize(12);
        tip.setTextColor(Tools.subTextColor(this));
        tip.setPadding(dp(4), dp(16), dp(4), 0);
        root.addView(tip);

        setContentView(sv);
    }

    private TextView card(LinearLayout parent, String name, String init) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(10), dp(16), dp(10), dp(16));
        c.setBackground(Tools.liquidGlassCard(this, 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        c.setLayoutParams(lp);

        TextView t = new TextView(this);
        t.setText(init);
        t.setTextSize(19);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setTextColor(Tools.textColor(this));
        t.setGravity(Gravity.CENTER);
        c.addView(t);

        TextView n = new TextView(this);
        n.setText(name);
        n.setTextSize(11);
        n.setTextColor(Tools.subTextColor(this));
        n.setGravity(Gravity.CENTER);
        n.setPadding(0, dp(4), 0, 0);
        c.addView(n);
        parent.addView(c);
        return t;
    }

    private void start() {
        if (running) return;
        running = true;
        btn.setText("测速中…");
        pingText.setText("-- ms");
        downText.setText("-- Mbps");
        peakText.setText("-- Mbps");
        bar.setProgress(0);
        stateText.setText("正在测延迟…");
        new Thread(() -> {
            long ping = ping();
            ui.post(() -> {
                pingText.setText(ping + " ms");
                stateText.setText("正在测下载…");
            });
            download(ping);
        }).start();
    }

    private long ping() {
        long sum = 0, best = Long.MAX_VALUE;
        int ok = 0;
        for (int i = 0; i < 3; i++) {
            long t0 = System.nanoTime();
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(HOST, 443), 3000);
                long ms = (System.nanoTime() - t0) / 1_000_000;
                sum += ms;
                best = Math.min(best, ms);
                ok++;
            } catch (Throwable ignored) {}
            try { Thread.sleep(200); } catch (Throwable ignored) {}
        }
        return ok == 0 ? -1 : sum / ok;
    }

    private void download(long ping) {
        long total = 0;
        long t0 = System.currentTimeMillis();
        double peak = 0;
        long lastT = t0, lastB = 0;
        try {
            URL u = new URL(TEST_URL);
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(10000);
            c.setRequestProperty("Range", "bytes=0-");
            InputStream in = c.getInputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                long now = System.currentTimeMillis();
                if (now - t0 > 10000) break;
                if (now - lastT >= 300) {
                    double mbps = (total - lastB) * 8.0 / ((now - lastT) / 1000.0) / 1_000_000;
                    peak = Math.max(peak, mbps);
                    double avg = total * 8.0 / ((now - t0) / 1000.0) / 1_000_000;
                    final int pct = (int) Math.min(100, (now - t0) / 100.0);
                    final double fAvg = avg, fPeak = peak;
                    final long fTotal = total;
                    ui.post(() -> {
                        bar.setProgress(pct);
                        downText.setText(String.format(Locale.US, "%.1f", fAvg));
                        peakText.setText(String.format(Locale.US, "%.1f", fPeak));
                        stateText.setText("已下载 " + (fTotal / 1024 / 1024) + "MB …");
                    });
                    lastT = now;
                    lastB = total;
                }
            }
            in.close();
        } catch (Throwable t) {
            ui.post(() -> stateText.setText("下载测试中断：" + t.getMessage()));
        }
        final long dt = Math.max(1, System.currentTimeMillis() - t0);
        final double avg = total * 8.0 / (dt / 1000.0) / 1_000_000;
        final long fTotal = total;
        final long fPing = ping;
        final double fPeak = Math.max(avg, peak);
        ui.post(() -> {
            running = false;
            btn.setText("再测一次");
            bar.setProgress(100);
            downText.setText(String.format(Locale.US, "%.1f", avg));
            peakText.setText(String.format(Locale.US, "%.1f", fPeak));
            stateText.setText(String.format(Locale.US,
                    "完成 · 下载 %.1f MB / %.1f 秒 · 延迟 %d ms", fTotal / 1024.0 / 1024.0,
                    dt / 1000.0, fPing));
        });
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
