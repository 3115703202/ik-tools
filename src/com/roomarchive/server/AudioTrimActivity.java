package com.roomarchive.server;

import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

/** 音频剪切：无损抽取，输出 m4a */
public class AudioTrimActivity extends BaseActivity {

    private static final int REQ_PICK = 2002;
    private Uri src;
    private long durationMs;
    private TextView info, action;
    private EditText startEd, endEd;
    private ProgressBar bar;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Tools.bgWithGlow(this));
        root.setPadding(dp(18), dp(24), dp(18), dp(28));

        TextView title = Tools.pageTitle(this, "音频剪切");
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView sub = new TextView(this);
        sub.setText("选音频 → 填起止秒数 → 无损截取");
        sub.setTextSize(12);
        sub.setTextColor(Tools.subTextColor(this));
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(6), 0, dp(14));
        root.addView(sub);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(Tools.liquidGlassCard(this, 22));
        Tools.setElevation(card, 10);
        root.addView(card);

        info = new TextView(this);
        info.setText("还没选音频");
        info.setTextSize(13);
        info.setTextColor(Tools.subTextColor(this));
        card.addView(info);

        TextView pick = new TextView(this);
        pick.setText("选择音频");
        pick.setGravity(Gravity.CENTER);
        pick.setTextSize(14);
        pick.setTextColor(0xFFFFFFFF);
        pick.setBackground(Tools.pillButton(this, 44, true));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        pl.topMargin = dp(12);
        card.addView(pick, pl);
        pick.setOnClickListener(v -> pickAudio());

        LinearLayout times = new LinearLayout(this);
        times.setOrientation(LinearLayout.HORIZONTAL);
        times.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tl.topMargin = dp(12);
        card.addView(times, tl);

        startEd = field("开始秒", times);
        TextView dash = new TextView(this);
        dash.setText("  →  ");
        dash.setTextColor(Tools.subTextColor(this));
        times.addView(dash);
        endEd = field("结束秒", times);

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(8));
        bl.topMargin = dp(12);
        card.addView(bar, bl);

        action = new TextView(this);
        action.setText("开始剪切");
        action.setGravity(Gravity.CENTER);
        action.setTextSize(15);
        action.setTextColor(0xFFFFFFFF);
        action.setBackground(Tools.pillButton(this, 48, true));
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        al.topMargin = dp(12);
        card.addView(action, al);
        action.setOnClickListener(v -> doTrim());

        TextView tip = new TextView(this);
        tip.setText("· 支持 m4a / aac / mp4 里的音轨，输出 m4a，不重编码\n"
                + "· mp3 的编码 mp4 容器装不了，会提示失败，可先用视频工具转 m4a\n"
                + "· 保存到 Download/IK工具箱/音频剪切");
        tip.setTextSize(12);
        tip.setTextColor(Tools.subTextColor(this));
        tip.setLineSpacing(dp(5), 1f);
        tip.setPadding(dp(16), dp(14), dp(16), dp(14));
        tip.setBackground(Tools.liquidGlassCard(this, 18));
        LinearLayout.LayoutParams tpl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tpl.topMargin = dp(16);
        root.addView(tip, tpl);

        setContentView(root);
    }

    private EditText field(String hint, LinearLayout parent) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(14);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setTextColor(Tools.textColor(this));
        e.setHintTextColor(Tools.subTextColor(this));
        parent.addView(e, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return e;
    }

    private void pickAudio() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/*");
        startActivityForResult(i, REQ_PICK);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_PICK || res != RESULT_OK || data == null || data.getData() == null) return;
        src = data.getData();
        try {
            MediaMetadataRetriever r = new MediaMetadataRetriever();
            r.setDataSource(this, src);
            String d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            durationMs = d == null ? 0 : Long.parseLong(d);
            r.release();
            info.setText("已选 · 总长 " + MediaTrimmer.durationText(durationMs));
            startEd.setText("0");
            endEd.setText(String.format(java.util.Locale.US, "%.1f", durationMs / 1000.0));
        } catch (Throwable t) {
            info.setText("读不出音频信息：" + t.getMessage());
        }
    }

    private void doTrim() {
        if (src == null) { toast("先选音频"); return; }
        double s, e;
        try {
            s = Double.parseDouble(startEd.getText().toString().trim());
            e = Double.parseDouble(endEd.getText().toString().trim());
        } catch (Throwable t) { toast("起止秒数填数字"); return; }
        if (e <= s) { toast("结束要大于开始"); return; }
        action.setEnabled(false);
        action.setText("剪切中…");
        File out = new File(FileStore.outDir(this), "音频剪切");
        out.mkdirs();
        File dest = new File(out, "剪切_" + System.currentTimeMillis() + ".m4a");
        new Thread(() -> {
            try {
                MediaTrimmer.trim(this, src, (long) (s * 1000), (long) (e * 1000), dest, false,
                        p -> runOnUiThread(() -> bar.setProgress(p)));
                runOnUiThread(() -> {
                    action.setEnabled(true);
                    action.setText("再剪一个");
                    toast("完成：" + dest.getAbsolutePath());
                });
            } catch (Throwable t) {
                runOnUiThread(() -> {
                    action.setEnabled(true);
                    action.setText("开始剪切");
                    toast("剪切失败：" + t.getMessage());
                });
            }
        }).start();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
