package com.roomarchive.server;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 二维码生成器：文字/链接生成二维码，可保存/分享。
 * 基于 ZXing（Apache-2.0 开源库）。
 */
public class QRCodeActivity extends BaseActivity {

    private ImageView qrView;
    private Bitmap qrBitmap = null;

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

        TextView title = Tools.pageTitle(this, "二维码生成器");
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        root.addView(spacer(14));

        final EditText input = new EditText(this);
        input.setHint("输入文字或链接，如 https://…");
        input.setTextSize(15);
        input.setTextColor(Tools.textColor(this));
        GradientDrawable inBg = new GradientDrawable();
        inBg.setColor(Tools.cardColor(this));
        inBg.setCornerRadius(dp(12));
        inBg.setStroke(dp(1), Tools.isDark(this) ? 0xFF2A3040 : 0xFFE4E8EE);
        input.setBackground(inBg);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setMinLines(3);
        root.addView(input);

        root.addView(spacer(10));

        Button gen = new Button(this);
        gen.setText("生成二维码");
        gen.setAllCaps(false);
        gen.setTextColor(Color.WHITE);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.rgb(30, 64, 175));
        g.setCornerRadius(dp(12));
        gen.setBackground(g);
        root.addView(gen);
        gen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String txt = input.getText().toString().trim();
                if (txt.isEmpty()) {
                    Toast.makeText(QRCodeActivity.this, "请输入内容", Toast.LENGTH_SHORT).show();
                    return;
                }
                qrBitmap = makeQr(txt, 800);
                if (qrBitmap != null) {
                    qrView.setImageBitmap(qrBitmap);
                } else {
                    Toast.makeText(QRCodeActivity.this, "内容过长或生成失败", Toast.LENGTH_LONG).show();
                }
            }
        });

        root.addView(spacer(8));

        Button scan = new Button(this);
        scan.setText("扫描二维码");
        scan.setAllCaps(false);
        scan.setTextColor(Color.WHITE);
        GradientDrawable sg = new GradientDrawable();
        sg.setColor(Color.rgb(15, 118, 110));
        sg.setCornerRadius(dp(12));
        scan.setBackground(sg);
        root.addView(scan);
        scan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new android.content.Intent(QRCodeActivity.this, QrScanActivity.class));
            }
        });

        root.addView(spacer(14));

        qrView = new ImageView(this);
        qrView.setBackgroundColor(Color.WHITE);
        GradientDrawable qb = new GradientDrawable();
        qb.setCornerRadius(dp(12));
        qrView.setBackground(qb);
        qrView.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.addView(qrView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(320)));

        root.addView(spacer(12));

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        Button save = new Button(this);
        save.setText("保存到工具箱目录");
        save.setAllCaps(false);
        save.setTextColor(Color.WHITE);
        save.setBackground(Tools.pillButton(this, 48, true));
        btnRow.addView(save, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        btnRow.addView(spacerH(8));
        Button share = new Button(this);
        share.setText("分享");
        share.setAllCaps(false);
        share.setTextColor(Tools.accentColor(this));
        GradientDrawable s2 = new GradientDrawable();
        int ac = Tools.accentColor(this);
        s2.setColor((0x1F << 24) | (ac & 0x00FFFFFF));
        s2.setCornerRadius(Tools.dp(this, 24));
        share.setBackground(s2);
        btnRow.addView(share, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(btnRow);

        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (qrBitmap == null) {
                    Toast.makeText(QRCodeActivity.this, "请先生成二维码", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    File dir = new File(FileStore.outDir(QRCodeActivity.this), "二维码");
                    dir.mkdirs();
                    File f = new File(dir, "qr_" + System.currentTimeMillis() + ".png");
                    FileOutputStream fos = new FileOutputStream(f);
                    qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    fos.close();
                    Toast.makeText(QRCodeActivity.this,
                            "已保存到：" + f.getAbsolutePath() + "\n可在「我的文件」中查看分享", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(QRCodeActivity.this, "保存失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });

        share.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (qrBitmap == null) {
                    Toast.makeText(QRCodeActivity.this, "请先生成二维码", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    File dir = new File(getExternalCacheDir(), "share");
                    dir.mkdirs();
                    File f = new File(dir, "qr_share.png");
                    FileOutputStream fos = new FileOutputStream(f);
                    qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    fos.close();
                    Intent it = new Intent(Intent.ACTION_SEND);
                    it.setType("image/png");
                    Uri u = Uri.parse("content://com.roomarchive.server.files/share/" + f.getName());
                    it.putExtra(Intent.EXTRA_STREAM, u);
                    it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(it, "分享二维码"));
                } catch (Exception e) {
                    Toast.makeText(QRCodeActivity.this, "分享失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });

        root.addView(spacer(10));

        TextView tip = new TextView(this);
        tip.setText("支持文字、链接、名片信息等。\n生成的二维码为本地离线生成，不联网。");
        tip.setTextSize(11);
        tip.setTextColor(Tools.subTextColor(this));
        root.addView(tip);

        root.setBackground(Tools.bgWithGlow(this));
        setContentView(scroll);
    }

    /** 用 ZXing 生成二维码位图 */
    private Bitmap makeQr(String content, int size) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints);
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    bmp.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private View spacer(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        return v;
    }

    private View spacerH(int w) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(w), 1));
        return v;
    }
}
