package com.roomarchive.server;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.util.Hashtable;

/** 二维码扫码器：相机实时识别，识别结果可复制 / 打开链接 / 分享 */
public class QrScanActivity extends Activity implements SurfaceHolder.Callback, Camera.PreviewCallback {

    private Camera camera;
    private SurfaceView preview;
    private boolean scanning = true;
    private Handler handler = new Handler();

    private TextView resultView;
    private Button btnCopy;
    private Button btnOpen;
    private Button btnShare;
    private TextView tipView;

    private String lastResult = "";

    private static final int REQ_CAMERA = 88;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (checkSelfPermission(android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.CAMERA}, REQ_CAMERA);
        }
        buildUi();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0A0F1E);

        preview = new SurfaceView(this);
        root.addView(preview, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, 0, Gravity.TOP));

        // 底部操作面板（玻璃）
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(16), dp(16), dp(18));
        panel.setBackground(Tools.liquidGlassCard(this, 28));
        Tools.setElevation(panel, 16);

        tipView = new TextView(this);
        tipView.setText("将二维码放入取景框内，自动识别");
        tipView.setTextSize(14);
        tipView.setTextColor(Color.WHITE);
        tipView.setGravity(Gravity.CENTER);
        panel.addView(tipView);

        resultView = new TextView(this);
        resultView.setTextSize(13);
        resultView.setTextColor(Color.rgb(200, 210, 228));
        resultView.setGravity(Gravity.CENTER);
        resultView.setMaxLines(3);
        resultView.setPadding(0, dp(8), 0, dp(4));
        panel.addView(resultView);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        btnCopy = smallBtn("复制");
        btnOpen = smallBtn("打开链接");
        btnShare = smallBtn("分享");
        btnRow.addView(btnCopy, rowLp());
        btnRow.addView(btnOpen, rowLp());
        btnRow.addView(btnShare, rowLp());
        panel.addView(btnRow);

        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(150), Gravity.BOTTOM);
        root.addView(panel, pp);

        // 扫描框提示线（简化为中央指示条）
        View line = new View(this);
        line.setBackgroundColor(0xCC4FC3F7);
        FrameLayout.LayoutParams lp2 = new FrameLayout.LayoutParams(dp(240), dp(2), Gravity.CENTER);
        root.addView(line, lp2);

        setContentView(root);

        btnCopy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (lastResult.isEmpty()) return;
                android.content.ClipboardManager cm = (android.content.ClipboardManager)
                        getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("扫码结果", lastResult));
                Toast.makeText(QrScanActivity.this, "已复制", Toast.LENGTH_SHORT).show();
            }
        });
        btnOpen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (lastResult.isEmpty()) return;
                if (lastResult.startsWith("http://") || lastResult.startsWith("https://")) {
                    startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(lastResult)));
                } else {
                    Toast.makeText(QrScanActivity.this, "不是链接，可长按复制", Toast.LENGTH_SHORT).show();
                }
            }
        });
        btnShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (lastResult.isEmpty()) return;
                android.content.Intent it = new android.content.Intent(android.content.Intent.ACTION_SEND);
                it.setType("text/plain");
                it.putExtra(android.content.Intent.EXTRA_TEXT, lastResult);
                startActivity(android.content.Intent.createChooser(it, "分享扫码结果"));
            }
        });
    }

    private Button smallBtn(String t) {
        Button b = new Button(this);
        b.setText(t);
        b.setTextSize(13);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setBackground(Tools.pillButton(this, 20, true));
        return b;
    }

    private LinearLayout.LayoutParams rowLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1f);
        lp.setMarginStart(dp(6));
        lp.setMarginEnd(dp(6));
        return lp;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA) {
            if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "需要相机权限才能扫码", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            camera = Camera.open();
            Camera.Parameters p = camera.getParameters();
            p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            camera.setParameters(p);
            SurfaceHolder sh = preview.getHolder();
            sh.addCallback(this);
            sh.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
            scanning = true;
        } catch (Exception e) {
            tipView.setText("相机打开失败：" + e.getMessage());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        scanning = false;
        if (camera != null) {
            try {
                camera.setPreviewCallback(null);
                camera.stopPreview();
                camera.release();
            } catch (Exception ignored) {
            }
            camera = null;
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (camera == null) return;
        try {
            camera.setPreviewDisplay(holder);
            camera.startPreview();
            camera.setPreviewCallback(this);
        } catch (Exception e) {
            tipView.setText("预览启动失败：" + e.getMessage());
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    private final MultiFormatReader reader = new MultiFormatReader();

    @Override
    public void onPreviewFrame(final byte[] data, Camera cam) {
        if (!scanning) return;
        try {
            Camera.Size sz = cam.getParameters().getPreviewSize();
            Hashtable<DecodeHintType, Object> hints = new Hashtable<>();
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            reader.setHints(hints);
            int w = sz.width, h = sz.height;
            int min = Math.min(w, h);
            int cropX = (w - min) / 2, cropY = (h - min) / 2;
            PlanarYUVLuminanceSource src = new PlanarYUVLuminanceSource(
                    data, w, h, cropX, cropY, min, min, false);
            BinaryBitmap bmp = new BinaryBitmap(new HybridBinarizer(src));
            Result r = reader.decodeWithState(bmp);
            if (r != null) {
                scanning = false;
                lastResult = r.getText();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        resultView.setText("识别结果：\n" + lastResult);
                        tipView.setText("已识别 · 可复制 / 打开 / 分享");
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                scanning = true;
                                tipView.setText("继续扫描（自动识别下一张）");
                            }
                        }, 2500);
                    }
                });
            }
        } catch (Exception ignored) {
        }
        reader.reset();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
