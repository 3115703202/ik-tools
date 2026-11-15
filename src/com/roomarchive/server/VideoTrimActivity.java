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

/** 视频剪辑：选片段无损裁剪，不重编码 */
public class VideoTrimActivity extends BaseActivity {

    private static final int REQ_PICK = 2001;
    private Uri src;
    private long durationMs;
    private TextView info;
    private EditText startEd, endEd;
    private ProgressBar bar;
    private TextView action;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Tools.bgWithGlow(this));
        root.setPadding(dp(18), dp(24), dp(18), dp(28));

        TextView title = Tools.pageTitle(this, "视频剪辑");
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView sub = new TextView(this);
        sub.setText("选视频 → 填起止秒数 → 直接裁，不重新编码");
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
        info.setText("还没选视频");
        info.setTextSize(13);
        info.setTextColor(Tools.subTextColor(this));
        card.addView(info);

        TextView pick = new TextView(this);
        pick.setText("选择视频");
        pick.setGravity(Gravity.CENTER);
        pick.setTextSize(14);
        pick.setTextColor(0xFFFFFFFF);
        pick.setBackground(Tools.pillButton(this, 44, true));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        pl.topMargin = dp(12);
        card.addView(pick, pl);
        pick.setOnClickListener(v -> pickVideo());

        LinearLayout times = new LinearLayout(this);
        times.setOrientation(LinearLayout.HORIZONTAL);
        times.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tl.topMargin = dp(12);
        card.addView(times, tl);

        startEd = timeField("开始秒", times);
        TextView dash = new TextView(this);
        dash.setText("  →  ");
        dash.setTextColor(Tools.subTextColor(this));
        times.addView(dash);
        endEd = timeField("结束秒", times);

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(8));
        bl.topMargin = dp(12);
        card.addView(bar, bl);

        action = new TextView(this);
        action.setText("开始裁剪");
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
        tip.setText("· 输出 MP4，画面/音轨原样拷贝，速度快、画质不掉\n"
                + "· 起点会对齐到最近关键帧，可能比填的秒数早一点点\n"
                + "· 保存到 Download/IK工具箱/视频剪辑");
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

    private EditText timeField(String hint, LinearLayout parent) {
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

    private void pickVideo() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("video/*");
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
            String w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            r.release();
            info.setText("已选 · " + (w == null ? "?" : w) + "×" + (h == null ? "?" : h)
                    + " · 总长 " + MediaTrimmer.durationText(durationMs));
            startEd.setText("0");
            endEd.setText(String.format(java.util.Locale.US, "%.1f", durationMs / 1000.0));
        } catch (Throwable t) {
            info.setText("读不出视频信息：" + t.getMessage());
        }
    }

    private void doTrim() {
        if (src == null) { toast("先选视频"); return; }
        double s, e;
        try {
            s = Double.parseDouble(startEd.getText().toString().trim());
            e = Double.parseDouble(endEd.getText().toString().trim());
        } catch (Throwable t) { toast("起止秒数填数字"); return; }
        if (e <= s) { toast("结束要大于开始"); return; }
        action.setEnabled(false);
        action.setText("裁剪中…");
        File out = new File(FileStore.outDir(this), "视频剪辑");
        out.mkdirs();
        File dest = new File(out, "裁剪_" + System.currentTimeMillis() + ".mp4");
        new Thread(() -> {
            try {
                MediaTrimmer.trim(this, src, (long) (s * 1000), (long) (e * 1000), dest, true,
                        p -> runOnUiThread(() -> bar.setProgress(p)));
                runOnUiThread(() -> {
                    action.setEnabled(true);
                    action.setText("再裁一个");
                    toast("完成：" + dest.getAbsolutePath());
                });
            } catch (Throwable t) {
                runOnUiThread(() -> {
                    action.setEnabled(true);
                    action.setText("开始裁剪");
                    toast("裁剪失败：" + t.getMessage());
                });
            }
        }).start();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
