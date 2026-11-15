package com.roomarchive.server;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** 文件实验室：读图片/视频/音频，一键转格式 */
public class MediaLabActivity extends BaseActivity {

    private static final int REQ_IMG = 5001, REQ_VIDEO = 5002, REQ_AUDIO = 5003;

    private Uri src;
    private int kind; // 1 图片 2 视频 3 音频
    private long durationMs;
    private String mime;
    private TextView info, previewHint;
    private ImageView preview;
    private LinearLayout actions;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Tools.bgWithGlow(this));
        root.setPadding(dp(16), dp(22), dp(16), dp(26));

        TextView title = Tools.pageTitle(this, "文件实验室");
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView sub = new TextView(this);
        sub.setText("读图片 / 视频 / 音频 · 一键转换格式，全本地处理");
        sub.setTextSize(12);
        sub.setTextColor(Tools.subTextColor(this));
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(6), 0, dp(14));
        root.addView(sub);

        LinearLayout pickCard = new LinearLayout(this);
        pickCard.setOrientation(LinearLayout.HORIZONTAL);
        pickCard.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams pcl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        root.addView(pickCard, pcl);

        addPick(pickCard, "选图片", REQ_IMG, "image/*");
        addPick(pickCard, "选视频", REQ_VIDEO, "video/*");
        addPick(pickCard, "选音频", REQ_AUDIO, "audio/*");

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(Tools.liquidGlassCard(this, 22));
        Tools.setElevation(card, 10);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cl.topMargin = dp(14);
        root.addView(card, cl);

        info = new TextView(this);
        info.setText("还没选文件");
        info.setTextSize(13);
        info.setTextColor(Tools.subTextColor(this));
        card.addView(info);

        preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setVisibility(android.view.View.GONE);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(200));
        pl.topMargin = dp(10);
        card.addView(preview, pl);

        previewHint = new TextView(this);
        previewHint.setTextSize(11);
        previewHint.setTextColor(Tools.subTextColor(this));
        previewHint.setPadding(0, dp(8), 0, 0);
        card.addView(previewHint);

        actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        card.addView(actions);

        TextView tip = new TextView(this);
        tip.setText("· 图片：转 JPG / PNG / WebP，保留原分辨率\n"
                + "· 视频：提取音轨为 m4a，或整段重封装为 MP4（不重编码）\n"
                + "· 音频：转 m4a（mp3 编码装不进 mp4，暂不支持）\n"
                + "· 输出目录 Download/IK工具箱/文件转换");
        tip.setTextSize(12);
        tip.setTextColor(Tools.subTextColor(this));
        tip.setLineSpacing(dp(5), 1f);
        tip.setPadding(dp(16), dp(14), dp(16), dp(14));
        tip.setBackground(Tools.liquidGlassCard(this, 18));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tl.topMargin = dp(16);
        root.addView(tip, tl);

        setContentView(root);
    }

    private void addPick(LinearLayout parent, String label, int req, String type) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(13);
        t.setTextColor(Tools.accentColor(this));
        t.setBackground(Tools.pillButton(this, 40, false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1);
        lp.leftMargin = dp(4);
        lp.rightMargin = dp(4);
        parent.addView(t, lp);
        t.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType(type);
            startActivityForResult(i, req);
        });
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        src = data.getData();
        kind = req == REQ_IMG ? 1 : req == REQ_VIDEO ? 2 : 3;
        actions.removeAllViews();
        preview.setVisibility(android.view.View.GONE);
        durationMs = 0;
        try { mime = getContentResolver().getType(src); } catch (Throwable ignored) {}
        try {
            MediaMetadataRetriever r = new MediaMetadataRetriever();
            r.setDataSource(this, src);
            if (kind == 1) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                InputStream in = getContentResolver().openInputStream(src);
                BitmapFactory.decodeStream(in, null, o);
                in.close();
                info.setText("图片 · " + o.outWidth + "×" + o.outHeight + " · " + sizeText());
            } else if (kind == 2) {
                String d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                durationMs = d == null ? 0 : Long.parseLong(d);
                String w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                String h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                info.setText("视频 · " + (w == null ? "?" : w) + "×" + (h == null ? "?" : h)
                        + " · " + MediaTrimmer.durationText(durationMs) + " · " + sizeText());
                previewHint.setText("时长 " + (durationMs / 1000) + " 秒 · MIME " + mime);
            } else {
                String d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                durationMs = d == null ? 0 : Long.parseLong(d);
                info.setText("音频 · " + MediaTrimmer.durationText(durationMs) + " · " + sizeText());
            }
            r.release();
        } catch (Throwable t) {
            info.setText("读不出来：" + t.getMessage());
        }
        if (kind == 1) {
            try {
                InputStream in = getContentResolver().openInputStream(src);
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 4;
                Bitmap bm = BitmapFactory.decodeStream(in, null, o);
                in.close();
                preview.setImageBitmap(bm);
                preview.setVisibility(android.view.View.VISIBLE);
            } catch (Throwable ignored) {}
            addAction("转 JPG", () -> convertImage("jpg"));
            addAction("转 PNG", () -> convertImage("png"));
            addAction("转 WebP", () -> convertImage("webp"));
        } else if (kind == 2) {
            addAction("提取音频（m4a）", () -> convertMedia(false, "m4a"));
            addAction("整段重封装为 MP4", () -> convertMedia(true, "mp4"));
        } else {
            addAction("转 m4a", () -> convertMedia(false, "m4a"));
        }
    }

    private void addAction(String label, Runnable r) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(14);
        t.setTextColor(0xFFFFFFFF);
        t.setBackground(Tools.pillButton(this, 44, true));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        lp.topMargin = dp(10);
        actions.addView(t, lp);
        t.setOnClickListener(v -> r.run());
    }

    private String sizeText() {
        try {
            Cursor c = getContentResolver().query(src,
                    new String[]{OpenableColumns.SIZE}, null, null, null);
            if (c != null) {
                long s = c.moveToFirst() ? c.getLong(0) : 0;
                c.close();
                if (s > 0) return String.format("%.1f MB", s / 1048576.0);
            }
        } catch (Throwable ignored) {}
        return "大小未知";
    }

    private File outFile(String name) {
        File dir = new File(FileStore.outDir(this), "文件转换");
        dir.mkdirs();
        return new File(dir, name);
    }

    private void convertImage(String fmt) {
        if (src == null) return;
        new Thread(() -> {
            try {
                InputStream in = getContentResolver().openInputStream(src);
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(in, null, o);
                in.close();
                int sample = 1;
                int max = Math.max(o.outWidth, o.outHeight);
                while (max / sample > 4096) sample *= 2;
                in = getContentResolver().openInputStream(src);
                BitmapFactory.Options o2 = new BitmapFactory.Options();
                o2.inSampleSize = sample;
                Bitmap bm = BitmapFactory.decodeStream(in, null, o2);
                in.close();
                if (bm == null) throw new Exception("图片解码失败");
                Bitmap.CompressFormat cf = fmt.equals("png") ? Bitmap.CompressFormat.PNG
                        : fmt.equals("webp") ? (android.os.Build.VERSION.SDK_INT >= 30
                        ? Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP)
                        : Bitmap.CompressFormat.JPEG;
                File f = outFile("转换_" + System.currentTimeMillis() + "." + fmt);
                FileOutputStream fos = new FileOutputStream(f);
                bm.compress(cf, 92, fos);
                fos.close();
                uiToast("完成：" + f.getAbsolutePath());
            } catch (Throwable t) {
                uiToast("转换失败：" + t.getMessage());
            }
        }).start();
    }

    private void convertMedia(boolean keepVideo, String ext) {
        if (src == null) return;
        if (durationMs <= 0) { toast("读不到时长，无法转换"); return; }
        new Thread(() -> {
            try {
                File f = outFile("转换_" + System.currentTimeMillis() + "." + ext);
                MediaTrimmer.trim(this, src, 0, durationMs, f, keepVideo ? true : false, null);
                uiToast("完成：" + f.getAbsolutePath());
            } catch (Throwable t) {
                uiToast("转换失败：" + t.getMessage());
            }
        }).start();
    }

    private void uiToast(String s) { runOnUiThread(() -> toast(s)); }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
}
