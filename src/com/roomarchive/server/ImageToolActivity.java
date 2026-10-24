/** 图片工具箱：压缩/格式转换/大小/旋转翻转/裁剪/滤镜/画面调整/水印/圆角/边框/EXIF清除/信息 */
package com.roomarchive.server;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/** 图片工具：12 项图片处理 */
public class ImageToolActivity extends BaseActivity {

    private static final int REQ_PICK_IMG = 3001;

    private ImageView preview;
    private TextView infoView;
    private Bitmap baseBitmap;   // 当前编辑结果
    private Bitmap original;     // 原始图
    private int outFormat = 0;   // 0 JPG 1 PNG 2 WebP 3 BMP
    private int quality = 88;

    private static final String[] FMT_NAMES = {"JPG", "PNG", "WebP", "BMP"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(Tools.bgWithGlow(this));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(18));
        scroll.addView(root);

        TextView title = Tools.pageTitle(this, "图片工具箱");
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("选一张图片，12 项处理随时可用");
        sub.setTextSize(13);
        sub.setTextColor(Tools.subTextColor(this));
        sub.setGravity(Gravity.CENTER);
        root.addView(sub);
        root.addView(spacer(12));

        // 选图 + 保存
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        Button btnPick = new Button(this);
        btnPick.setText("选择图片");
        btnPick.setAllCaps(false);
        btnPick.setTextColor(Tools.accentColor(this));
        btnPick.setGravity(Gravity.CENTER);
        btnPick.setBackground(Tools.pillButton(this, 50, false));
        Icons.attachToText(btnPick, "folder", Tools.accentColor(this), 16, 6);
        btnPick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("image/*");
                startActivityForResult(i, REQ_PICK_IMG);
            }
        });
        bar.addView(btnPick, new LinearLayout.LayoutParams(0, dp(50), 1f));
        Button btnSave = new Button(this);
        btnSave.setText("保存图片");
        btnSave.setAllCaps(false);
        btnSave.setTextColor(Color.WHITE);
        btnSave.setGravity(Gravity.CENTER);
        btnSave.setBackground(Tools.pillButton(this, 50, true));
        Icons.attachToText(btnSave, "check", Color.WHITE, 16, 6);
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (baseBitmap == null) {
                    Toast.makeText(ImageToolActivity.this, "请先选择图片", Toast.LENGTH_SHORT).show();
                    return;
                }
                saveImage();
            }
        });
        bar.addView(btnSave, new LinearLayout.LayoutParams(0, dp(50), 1f));
        root.addView(bar);
        root.addView(spacer(10));

        // 预览
        preview = new ImageView(this);
        preview.setBackgroundColor(Tools.isDark(this) ? 0xFF1B2230 : Color.rgb(238, 240, 244));
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(preview);

        infoView = new TextView(this);
        infoView.setText("未载入图片 · 输出 JPG · 质量 88");
        infoView.setTextSize(13);
        infoView.setTextColor(Tools.subTextColor(this));
        infoView.setGravity(Gravity.CENTER);
        root.addView(infoView);
        root.addView(spacer(12));

        // 12 功能网格
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setRowCount(6);
        addCard(grid, "图片压缩", "压缩体积，调整质量", new View.OnClickListener() {
            @Override
            public void onClick(View v) { showCompressDlg(); }
        });
        addCard(grid, "格式转换", "JPG/PNG/WebP/BMP互转", new View.OnClickListener() {
            @Override
            public void onClick(View v) { showConvertDlg(); }
        });
        addCard(grid, "调整大小", "宽高缩放，锁定比例", new View.OnClickListener() {
            @Override
            public void onClick(View v) { showResizeDlg(); }
        });
        addCard(grid, "旋转翻转", "旋转角度，镜像翻转", new View.OnClickListener() {
            @Override
            public void onClick(View v) { showRotateDlg(); }
        });
        addCard(grid, "裁剪", "按比例裁剪图片", new View.OnClickListener() {
            @Override
            public void onClick(View v) { showCropDlg(); }
        });
        addCard(grid, "滤镜", "灰度/复古/模糊等", new View.OnClickListener() {
            @Override
            public void onClick(View v) { showFilterDlg(); }
        });
        addCard(grid, "画面调整", "亮度/对比度/饱和度", new View.OnClickListener() {
            @Override
            public void onClick(View v) { showAdjustDlg(); }
        });
        addCard(grid, "添加水印", "文字水印，平铺", new View.OnClickListener() {
            @Override
            public void onClick(View v) { showWatermarkDlg(); }
        });
        addCard(grid, "圆角处理", "生成圆角图片", new View.OnClickListener() {
            @Override
            public void onClick(View v) { showRoundDlg(); }
        });
        addCard(grid, "图片边框", "添加彩色边框", new View.OnClickListener() {
            @Override
            public void onClick(View v) { showBorderDlg(); }
        });
        addCard(grid, "EXIF清除", "清除隐私信息", new View.OnClickListener() {
            @Override
            public void onClick(View v) { clearExif(); }
        });
        addCard(grid, "图片信息", "查看详细属性", new View.OnClickListener() {
            @Override
            public void onClick(View v) { showInfoDlg(); }
        });
        root.addView(grid);

        setContentView(scroll);
    }

    private void addCard(GridLayout grid, String name, String desc, View.OnClickListener l) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(8), dp(14), dp(8), dp(14));
        card.setBackground(Tools.liquidGlassCard(this, 14));
        card.setOnClickListener(l);

        TextView n = new TextView(this);
        n.setText(name);
        n.setTextSize(15);
        n.setTextColor(Tools.textColor(this));
        n.setTypeface(Typeface.DEFAULT_BOLD);
        n.setGravity(Gravity.CENTER);
        card.addView(n);
        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextSize(11);
        d.setTextColor(Tools.subTextColor(this));
        d.setGravity(Gravity.CENTER);
        card.addView(d);

        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = dp(84);
        lp.setMargins(dp(5), dp(5), dp(5), dp(5));
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        card.setLayoutParams(lp);
        grid.addView(card);
    }

    // ================= 图片压缩 =================
    private void showCompressDlg() {
        if (baseBitmap == null) { needPick(); return; }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(16), dp(20), dp(8));
        final TextView label = new TextView(this);
        label.setText("质量: " + quality + " (值越低体积越小)");
        label.setTextSize(14);
        box.addView(label);
        final SeekBar sb = new SeekBar(this);
        sb.setMax(100);
        sb.setProgress(quality);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                label.setText("质量: " + progress + " (值越低体积越小)");
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        box.addView(sb);
        final TextView est = new TextView(this);
        est.setTextSize(12);
        est.setTextColor(Tools.subTextColor(this));
        box.addView(est);
        new AlertDialog.Builder(this)
                .setTitle("图片压缩")
                .setView(box)
                .setPositiveButton("应用", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        quality = sb.getProgress();
                        // 用当前质量重新编码再解码，实时看到体积
                        byte[] data = encode(baseBitmap, 0);
                        Bitmap nb = BitmapFactory.decodeByteArray(data, 0, data.length);
                        if (nb != null) {
                            baseBitmap.recycle();
                            baseBitmap = nb;
                            preview.setImageBitmap(baseBitmap);
                        }
                        refreshInfo();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
        updateEstimate(est, 0, quality);
    }

    // ================= 格式转换 =================
    private void showConvertDlg() {
        if (baseBitmap == null) { needPick(); return; }
        new AlertDialog.Builder(this)
                .setTitle("输出格式")
                .setSingleChoiceItems(FMT_NAMES, outFormat, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        outFormat = which;
                        d.dismiss();
                        byte[] data = encode(baseBitmap, outFormat);
                        Toast.makeText(ImageToolActivity.this,
                                "已切换为 " + FMT_NAMES[outFormat] + " 输出，体积约 " + (data.length / 1024) + " KB",
                                Toast.LENGTH_LONG).show();
                        refreshInfo();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ================= 调整大小 =================
    private void showResizeDlg() {
        if (baseBitmap == null) { needPick(); return; }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(16), dp(20), dp(8));
        final TextView label = new TextView(this);
        label.setText("目标宽度(px): " + Math.min(baseBitmap.getWidth(), 1200) + " (高度按比例)");
        label.setTextSize(14);
        box.addView(label);
        final SeekBar sb = new SeekBar(this);
        sb.setMax(2400);
        sb.setProgress(Math.min(baseBitmap.getWidth(), 1200));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress < 10) progress = 10;
                label.setText("目标宽度(px): " + progress + " (高度按比例)");
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        box.addView(sb);
        new AlertDialog.Builder(this)
                .setTitle("调整大小")
                .setView(box)
                .setPositiveButton("应用", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        int tw = Math.max(10, sb.getProgress());
                        int th = Math.round((float) tw * baseBitmap.getHeight() / baseBitmap.getWidth());
                        Bitmap nb = Bitmap.createScaledBitmap(baseBitmap, tw, th, true);
                        baseBitmap.recycle();
                        baseBitmap = nb;
                        preview.setImageBitmap(baseBitmap);
                        refreshInfo();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ================= 旋转翻转 =================
    private void showRotateDlg() {
        if (baseBitmap == null) { needPick(); return; }
        final String[] ops = {"顺时针 90°", "逆时针 90°", "旋转 180°", "水平镜像", "垂直镜像"};
        new AlertDialog.Builder(this)
                .setTitle("旋转翻转")
                .setItems(ops, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        Matrix m = new Matrix();
                        switch (which) {
                            case 0: m.postRotate(90); break;
                            case 1: m.postRotate(-90); break;
                            case 2: m.postRotate(180); break;
                            case 3: m.postScale(-1, 1); break;
                            case 4: m.postScale(1, -1); break;
                        }
                        Bitmap nb = Bitmap.createBitmap(baseBitmap, 0, 0, baseBitmap.getWidth(), baseBitmap.getHeight(), m, true);
                        baseBitmap.recycle();
                        baseBitmap = nb;
                        preview.setImageBitmap(baseBitmap);
                        refreshInfo();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ================= 裁剪 =================
    private void showCropDlg() {
        if (baseBitmap == null) { needPick(); return; }
        final String[] ratios = {"自由(去边缘10%)", "1:1 方形", "4:3", "3:4", "16:9", "9:16"};
        new AlertDialog.Builder(this)
                .setTitle("裁剪")
                .setItems(ratios, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        int w = baseBitmap.getWidth(), h = baseBitmap.getHeight();
                        int nw = w, nh = h;
                        switch (which) {
                            case 0:
                                nw = w * 8 / 10; nh = h * 8 / 10;
                                break;
                            case 1:
                                if (w >= h) { nw = h; } else { nh = w; }
                                break;
                            case 2:
                                if (w * 3 >= h * 4) { nh = h; nw = h * 4 / 3; } else { nw = w; nh = w * 3 / 4; }
                                break;
                            case 3:
                                if (w * 4 >= h * 3) { nh = h; nw = h * 3 / 4; } else { nw = w; nh = w * 4 / 3; }
                                break;
                            case 4:
                                if (w * 9 >= h * 16) { nh = h; nw = h * 16 / 9; } else { nw = w; nh = w * 9 / 16; }
                                break;
                            case 5:
                                if (w * 16 >= h * 9) { nh = h; nw = h * 9 / 16; } else { nw = w; nh = w * 16 / 9; }
                                break;
                        }
                        int x = (w - nw) / 2, y = (h - nh) / 2;
                        Bitmap nb = Bitmap.createBitmap(baseBitmap, x, y, nw, nh);
                        baseBitmap.recycle();
                        baseBitmap = nb;
                        preview.setImageBitmap(baseBitmap);
                        refreshInfo();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ================= 滤镜 =================
    private void showFilterDlg() {
        if (baseBitmap == null) { needPick(); return; }
        final String[] filters = {"原图", "灰度", "复古", "负片", "模糊", "浮雕", "锐化", "冷调", "暖调"};
        new AlertDialog.Builder(this)
                .setTitle("滤镜")
                .setItems(filters, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        Bitmap nb = null;
                        switch (which) {
                            case 0: return;
                            case 1: nb = grayscale(baseBitmap); break;
                            case 2: nb = colorMatrix(baseBitmap, new float[]{
                                    0.393f, 0.769f, 0.189f, 0, 0,
                                    0.349f, 0.686f, 0.168f, 0, 0,
                                    0.272f, 0.534f, 0.131f, 0, 0,
                                    0, 0, 0, 1, 0}); break;
                            case 3: nb = colorMatrix(baseBitmap, new float[]{
                                    -1, 0, 0, 0, 255,
                                    0, -1, 0, 0, 255,
                                    0, 0, -1, 0, 255,
                                    0, 0, 0, 1, 0}); break;
                            case 4: nb = blurFast(baseBitmap); break;
                            case 5: nb = emboss(baseBitmap); break;
                            case 6: nb = sharpen(baseBitmap); break;
                            case 7: nb = colorMatrix(baseBitmap, new float[]{
                                    1.1f, 0, 0, 0, -10,
                                    0, 1.0f, 0, 0, 0,
                                    0, 0, 1.3f, 0, 0,
                                    0, 0, 0, 1, 0}); break;
                            case 8: nb = colorMatrix(baseBitmap, new float[]{
                                    1.2f, 0, 0, 0, 10,
                                    0, 1.0f, 0, 0, 5,
                                    0, 0, 0.9f, 0, -10,
                                    0, 0, 0, 1, 0}); break;
                        }
                        if (nb != null) {
                            baseBitmap.recycle();
                            baseBitmap = nb;
                            preview.setImageBitmap(baseBitmap);
                            refreshInfo();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ================= 画面调整 =================
    private void showAdjustDlg() {
        if (baseBitmap == null) { needPick(); return; }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(12), dp(20), dp(8));
        final TextView lb = new TextView(this);
        lb.setText("亮度: 0   对比度: 0   饱和度: 0");
        lb.setTextSize(14);
        box.addView(lb);
        final SeekBar sbB = new SeekBar(this); sbB.setMax(200); sbB.setProgress(100);
        final SeekBar sbC = new SeekBar(this); sbC.setMax(200); sbC.setProgress(100);
        final SeekBar sbS = new SeekBar(this); sbS.setMax(200); sbS.setProgress(100);
        box.addView(sbB); box.addView(sbC); box.addView(sbS);
        SeekBar.OnSeekBarChangeListener l = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                lb.setText("亮度: " + (sbB.getProgress() - 100) + "   对比度: " + (sbC.getProgress() - 100)
                        + "   饱和度: " + (sbS.getProgress() - 100));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        };
        sbB.setOnSeekBarChangeListener(l); sbC.setOnSeekBarChangeListener(l); sbS.setOnSeekBarChangeListener(l);
        new AlertDialog.Builder(this)
                .setTitle("画面调整")
                .setView(box)
                .setPositiveButton("应用", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        float b = (sbB.getProgress() - 100) / 100f;
                        float c = (sbC.getProgress() - 100) / 100f;
                        float s = (sbS.getProgress() - 100) / 100f;
                        Bitmap nb = adjust(baseBitmap, b, c, s);
                        baseBitmap.recycle();
                        baseBitmap = nb;
                        preview.setImageBitmap(baseBitmap);
                        refreshInfo();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ================= 水印 =================
    private void showWatermarkDlg() {
        if (baseBitmap == null) { needPick(); return; }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(12), dp(20), dp(8));
        final EditText et = new EditText(this);
        et.setHint("水印文字");
        box.addView(et);
        final TextView sizeLabel = new TextView(this);
        sizeLabel.setText("字号: 40");
        sizeLabel.setTextSize(13);
        box.addView(sizeLabel);
        final SeekBar sizeSb = new SeekBar(this);
        sizeSb.setMax(200); sizeSb.setProgress(40);
        sizeSb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                sizeLabel.setText("字号: " + Math.max(8, progress));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        box.addView(sizeSb);
        final CheckBox tile = new CheckBox(this);
        tile.setText("平铺整张图片");
        box.addView(tile);
        new AlertDialog.Builder(this)
                .setTitle("文字水印")
                .setView(box)
                .setPositiveButton("添加", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String text = et.getText().toString().trim();
                        if (text.isEmpty()) {
                            Toast.makeText(ImageToolActivity.this, "请输入水印文字", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Bitmap nb = drawWatermark(baseBitmap, text, Math.max(8, sizeSb.getProgress()), tile.isChecked());
                        baseBitmap.recycle();
                        baseBitmap = nb;
                        preview.setImageBitmap(baseBitmap);
                        refreshInfo();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ================= 圆角 =================
    private void showRoundDlg() {
        if (baseBitmap == null) { needPick(); return; }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(12), dp(20), dp(8));
        final TextView label = new TextView(this);
        label.setText("圆角半径: 60");
        label.setTextSize(14);
        box.addView(label);
        final SeekBar sb = new SeekBar(this);
        sb.setMax(400); sb.setProgress(60);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                label.setText("圆角半径: " + progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        box.addView(sb);
        new AlertDialog.Builder(this)
                .setTitle("圆角处理")
                .setView(box)
                .setPositiveButton("应用", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        Bitmap nb = roundCorners(baseBitmap, sb.getProgress());
                        baseBitmap.recycle();
                        baseBitmap = nb;
                        preview.setImageBitmap(baseBitmap);
                        refreshInfo();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ================= 边框 =================
    private void showBorderDlg() {
        if (baseBitmap == null) { needPick(); return; }
        final int[] colors = {0xFF000000, 0xFFFFFFFF, 0xFFE53E3E, 0xFF3B82F6, 0xFF10B981, 0xFFF59E0B, 0xFF8B5CF6, 0xFFEC4899};
        final String[] cnames = {"黑色", "白色", "红色", "蓝色", "绿色", "橙色", "紫色", "粉色"};
        final int[] sel = {0};
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(12), dp(20), dp(8));
        final TextView label = new TextView(this);
        label.setText("边框宽度: 20 px · 颜色: 黑色");
        label.setTextSize(14);
        box.addView(label);
        final SeekBar sb = new SeekBar(this);
        sb.setMax(200); sb.setProgress(20);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                label.setText("边框宽度: " + progress + " px · 颜色: " + cnames[sel[0]]);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        box.addView(sb);
        final Button colorBtn = new Button(this);
        colorBtn.setText("选择颜色: 黑色");
        colorBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(ImageToolActivity.this)
                        .setTitle("边框颜色")
                        .setItems(cnames, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                sel[0] = which;
                                colorBtn.setText("选择颜色: " + cnames[which]);
                                label.setText("边框宽度: " + sb.getProgress() + " px · 颜色: " + cnames[which]);
                            }
                        })
                        .show();
            }
        });
        box.addView(colorBtn);
        new AlertDialog.Builder(this)
                .setTitle("图片边框")
                .setView(box)
                .setPositiveButton("应用", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        Bitmap nb = addBorder(baseBitmap, colors[sel[0]], sb.getProgress());
                        baseBitmap.recycle();
                        baseBitmap = nb;
                        preview.setImageBitmap(baseBitmap);
                        refreshInfo();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ================= EXIF 清除 =================
    private void clearExif() {
        if (baseBitmap == null) { needPick(); return; }
        new AlertDialog.Builder(this)
                .setTitle("EXIF 清除")
                .setMessage("重新编码图片将移除拍摄位置、机型、时间等隐私信息。确认后保存时输出全新编码，不含任何 EXIF。")
                .setPositiveButton("清除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        Toast.makeText(ImageToolActivity.this, "已标记清除：保存时输出全新编码，不含任何 EXIF", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ================= 图片信息 =================
    private void showInfoDlg() {
        if (baseBitmap == null) { needPick(); return; }
        Bitmap b = baseBitmap;
        StringBuilder sb = new StringBuilder();
        sb.append("尺寸: ").append(b.getWidth()).append(" × ").append(b.getHeight()).append(" 像素\n");
        sb.append("面积: ").append((long) b.getWidth() * b.getHeight()).append(" px²\n");
        sb.append("内存占用: ").append(bitmapBytes(b) / 1024 / 1024).append(" MB\n");
        sb.append("宽高比: ").append(String.format("%.2f", (float) b.getWidth() / b.getHeight())).append(" : 1\n");
        sb.append("输出格式: ").append(FMT_NAMES[outFormat]).append(" · 质量 ").append(quality).append("\n");
        byte[] enc = encode(b, outFormat);
        sb.append("预计输出体积: ").append(enc.length / 1024).append(" KB");
        new AlertDialog.Builder(this)
                .setTitle("图片信息")
                .setMessage(sb.toString())
                .setPositiveButton("知道了", null)
                .show();
    }

    // ================= 像素级处理 =================
    private Bitmap grayscale(Bitmap src) {
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint();
        p.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[]{
                0.299f, 0.587f, 0.114f, 0, 0,
                0.299f, 0.587f, 0.114f, 0, 0,
                0.299f, 0.587f, 0.114f, 0, 0,
                0, 0, 0, 1, 0})));
        c.drawBitmap(src, 0, 0, p);
        return out;
    }

    private Bitmap colorMatrix(Bitmap src, float[] matrix) {
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint();
        p.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(matrix)));
        c.drawBitmap(src, 0, 0, p);
        return out;
    }

    private Bitmap adjust(Bitmap src, float bright, float contrast, float sat) {
        ColorMatrix cm = new ColorMatrix();
        cm.reset();
        if (bright != 0) {
            float b = bright * 255f;
            cm.postConcat(new ColorMatrix(new float[]{
                    1, 0, 0, 0, b,
                    0, 1, 0, 0, b,
                    0, 0, 1, 0, b,
                    0, 0, 0, 1, 0}));
        }
        if (contrast != 0) {
            float c = 1f + contrast;
            float t = 128f * (1f - c);
            cm.postConcat(new ColorMatrix(new float[]{
                    c, 0, 0, 0, t,
                    0, c, 0, 0, t,
                    0, 0, c, 0, t,
                    0, 0, 0, 1, 0}));
        }
        if (sat != 0) {
            ColorMatrix cms = new ColorMatrix();
            cms.setSaturation(Math.max(0f, 1f + sat));
            cm.postConcat(cms);
        }
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint();
        p.setColorFilter(new ColorMatrixColorFilter(cm));
        c.drawBitmap(src, 0, 0, p);
        return out;
    }

    private Bitmap blurFast(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        Bitmap small = Bitmap.createScaledBitmap(src, Math.max(1, w / 6), Math.max(1, h / 6), true);
        Bitmap out = Bitmap.createScaledBitmap(small, w, h, true);
        small.recycle();
        return out;
    }

    private Bitmap emboss(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int i = y * w + x;
                int r = ((px[i] >> 16) & 0xFF) - ((px[(y - 1) * w + (x - 1)] >> 16) & 0xFF) + 128;
                int g = ((px[i] >> 8) & 0xFF) - ((px[(y - 1) * w + (x - 1)] >> 8) & 0xFF) + 128;
                int b = (px[i] & 0xFF) - (px[(y - 1) * w + (x - 1)] & 0xFF) + 128;
                out.setPixel(x, y, Color.rgb(clamp(r), clamp(g), clamp(b)));
            }
        }
        return out;
    }

    private Bitmap sharpen(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int i = y * w + x;
                int r = 5 * ((px[i] >> 16) & 0xFF) - ((px[(y - 1) * w + x] >> 16) & 0xFF)
                        - ((px[(y + 1) * w + x] >> 16) & 0xFF) - ((px[i - 1] >> 16) & 0xFF) - ((px[i + 1] >> 16) & 0xFF);
                int g = 5 * ((px[i] >> 8) & 0xFF) - ((px[(y - 1) * w + x] >> 8) & 0xFF)
                        - ((px[(y + 1) * w + x] >> 8) & 0xFF) - ((px[i - 1] >> 8) & 0xFF) - ((px[i + 1] >> 8) & 0xFF);
                int b = 5 * (px[i] & 0xFF) - (px[(y - 1) * w + x] & 0xFF)
                        - (px[(y + 1) * w + x] & 0xFF) - (px[i - 1] & 0xFF) - (px[i + 1] & 0xFF);
                out.setPixel(x, y, Color.rgb(clamp(r), clamp(g), clamp(b)));
            }
        }
        return out;
    }

    private Bitmap roundCorners(Bitmap src, int radius) {
        int w = src.getWidth(), h = src.getHeight();
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint();
        p.setAntiAlias(true);
        android.graphics.BitmapShader shader = new android.graphics.BitmapShader(src,
                android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP);
        p.setShader(shader);
        c.drawRoundRect(new RectF(0, 0, w, h), radius, radius, p);
        return out;
    }

    private Bitmap addBorder(Bitmap src, int color, int bw) {
        int w = src.getWidth() + bw * 2, h = src.getHeight() + bw * 2;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(color);
        c.drawBitmap(src, bw, bw, null);
        return out;
    }

    private Bitmap drawWatermark(Bitmap src, String text, int size, boolean tile) {
        Bitmap out = src.copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(out);
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setTextSize(size);
        p.setColor(Color.argb(140, 255, 255, 255));
        p.setShadowLayer(size / 6f, 0, 0, Color.rgb(0, 0, 0));
        float tw = p.measureText(text);
        if (tile) {
            float th = size * 1.4f;
            for (float y = th; y < out.getHeight(); y += th * 2.2f) {
                for (float x = -tw; x < out.getWidth(); x += tw + th * 1.6f) {
                    c.drawText(text, x, y, p);
                }
            }
        } else {
            c.drawText(text, (out.getWidth() - tw) / 2f, out.getHeight() / 2f + size / 3f, p);
        }
        return out;
    }

    // ================= 编码/保存 =================
    private byte[] encode(Bitmap b, int fmt) {
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            switch (fmt) {
                case 1:
                    b.compress(Bitmap.CompressFormat.PNG, 100, bos);
                    break;
                case 2:
                    b.compress(Bitmap.CompressFormat.WEBP, quality, bos);
                    break;
                case 3:
                    bmpEncode(b, bos);
                    break;
                default:
                    b.compress(Bitmap.CompressFormat.JPEG, quality, bos);
                    break;
            }
            return bos.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    // 简易 BMP 编码（24 位）
    private void bmpEncode(Bitmap b, OutputStream os) throws Exception {
        int w = b.getWidth(), h = b.getHeight();
        int rowSize = (w * 3 + 3) & ~3;
        int pixelSize = rowSize * h;
        int fileSize = 54 + pixelSize;
        byte[] hdr = new byte[54];
        hdr[0] = 'B'; hdr[1] = 'M';
        writeInt(hdr, 2, fileSize);
        writeInt(hdr, 10, 54);
        writeInt(hdr, 14, 40);
        writeInt(hdr, 18, w);
        writeInt(hdr, 22, h);
        hdr[26] = 1;
        hdr[28] = 24;
        writeInt(hdr, 34, pixelSize);
        os.write(hdr);
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        byte[] row = new byte[rowSize];
        for (int y = h - 1; y >= 0; y--) {
            int base = y * w;
            for (int x = 0; x < w; x++) {
                int c = px[base + x];
                row[x * 3] = (byte) (c & 0xFF);
                row[x * 3 + 1] = (byte) ((c >> 8) & 0xFF);
                row[x * 3 + 2] = (byte) ((c >> 16) & 0xFF);
            }
            os.write(row);
        }
    }

    private void writeInt(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xFF);
        b[off + 1] = (byte) ((v >> 8) & 0xFF);
        b[off + 2] = (byte) ((v >> 16) & 0xFF);
        b[off + 3] = (byte) ((v >> 24) & 0xFF);
    }

    private void saveImage() {
        try {
            File d = FileStore.outDir(ImageToolActivity.this);
            String ext = FMT_NAMES[outFormat].toLowerCase();
            File out = new File(d, "图片_" + System.currentTimeMillis() + "." + ext);
            OutputStream os = new FileOutputStream(out);
            os.write(encode(baseBitmap, outFormat));
            os.flush();
            os.close();
            Toast.makeText(this, "已保存: " + out.getName() + " (" + (out.length() / 1024) + " KB)", Toast.LENGTH_LONG).show();
            refreshInfo();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void refreshInfo() {
        if (baseBitmap != null) {
            infoView.setText(baseBitmap.getWidth() + " × " + baseBitmap.getHeight()
                    + " 像素 · " + (bitmapBytes(baseBitmap) / 1024) + " KB · 输出 " + FMT_NAMES[outFormat]);
        }
    }

    private long bitmapBytes(Bitmap b) {
        return (long) b.getRowBytes() * b.getHeight();
    }

    private void updateEstimate(final TextView tv, final int fmt, final int q) {
        tv.post(new Runnable() {
            @Override
            public void run() {
                if (baseBitmap != null) {
                    byte[] d = encode(baseBitmap, fmt);
                    tv.setText("按当前质量编码体积约 " + d.length / 1024 + " KB");
                }
            }
        });
    }

    private void needPick() {
        Toast.makeText(this, "请先选择图片", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == REQ_PICK_IMG) {
            loadImage(data.getData());
        }
    }

    private void loadImage(Uri uri) {
        try {
            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inJustDecodeBounds = true;
            try (java.io.InputStream is = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(is, null, opt);
            }
            int sample = 1;
            while (opt.outWidth / sample > 2400 || opt.outHeight / sample > 2400) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            try (java.io.InputStream is = getContentResolver().openInputStream(uri)) {
                Bitmap bm = BitmapFactory.decodeStream(is, null, o2);
                if (bm == null) throw new Exception("无法解码该图片");
                if (original != null && !original.isRecycled()) original.recycle();
                original = bm;
                if (baseBitmap != null && !baseBitmap.isRecycled()) baseBitmap.recycle();
                baseBitmap = bm.copy(bm.getConfig(), true);
                preview.setImageBitmap(baseBitmap);
                refreshInfo();
                Toast.makeText(this, "图片已载入，可开始编辑", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "读取失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (original != null && !original.isRecycled()) original.recycle();
        if (baseBitmap != null && baseBitmap != original && !baseBitmap.isRecycled()) baseBitmap.recycle();
    }

    private int clamp(int v) {
        return Math.max(0, Math.min(255, v));
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
