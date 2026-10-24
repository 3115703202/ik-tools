package com.roomarchive.server;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 音频分贝测试：实时检测麦克风音量（分贝 dB），环境噪音检测。
 */
public class AudioDecibelActivity extends BaseActivity {

    private static final int SAMPLE_RATE = 16000;
    private AudioRecord recorder;
    private boolean measuring = false;
    private Thread thread;
    private Handler handler = new Handler(Looper.getMainLooper());

    private TextView dbValue, maxValue, levelText;
    private View barFill;
    private float maxDb = 0;
    private Runnable ticker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Tools.bgWithGlow(this));
        root.setPadding(dp(16), dp(20), dp(16), dp(18));

        TextView title = Tools.pageTitle(this, "音频分贝测试");
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        root.addView(spacer(16));

        // 分贝数值
        dbValue = new TextView(this);
        dbValue.setText("--");
        dbValue.setTextSize(64);
        dbValue.setTextColor(Tools.accentColor(this));
        dbValue.setGravity(Gravity.CENTER);
        dbValue.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(dbValue);

        TextView dbUnit = new TextView(this);
        dbUnit.setText("分贝 dB");
        dbUnit.setTextSize(13);
        dbUnit.setTextColor(Tools.subTextColor(this));
        dbUnit.setGravity(Gravity.CENTER);
        root.addView(dbUnit);

        // 级别
        levelText = new TextView(this);
        levelText.setText("安静");
        levelText.setTextSize(16);
        levelText.setTextColor(Color.rgb(22, 163, 74));
        levelText.setGravity(Gravity.CENTER);
        root.addView(levelText);

        root.addView(spacer(16));

        // 分贝条
        LinearLayout barWrap = new LinearLayout(this);
        barWrap.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable track = new GradientDrawable();
        track.setCornerRadius(dp(8));
        track.setColor(Tools.isDark(this) ? 0xFF2A3040 : 0xFFE4E8EE);
        barWrap.setBackground(track);
        barWrap.setPadding(dp(4), dp(4), dp(4), dp(4));
        barWrap.setGravity(Gravity.BOTTOM);

        barFill = new View(this);
        GradientDrawable fill = new GradientDrawable();
        fill.setCornerRadius(dp(6));
        fill.setColor(Tools.accentColor(this));
        barFill.setBackground(fill);
        barWrap.addView(barFill, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(4)));
        root.addView(barWrap, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(28)));

        root.addView(spacer(12));

        // 最大分贝
        maxValue = new TextView(this);
        maxValue.setText("本次最大分贝：-- dB");
        maxValue.setTextSize(13);
        maxValue.setTextColor(Tools.subTextColor(this));
        maxValue.setGravity(Gravity.CENTER);
        root.addView(maxValue);

        root.addView(spacer(18));

        final Button btn = new Button(this);
        btn.setText("开始检测");
        btn.setAllCaps(false);
        btn.setTextColor(Color.WHITE);
        btn.setBackground(Tools.pillButton(this, 50, true));
        root.addView(btn);

        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (measuring) {
                    stopMeasure();
                    btn.setText("开始检测");
                } else {
                    startMeasure();
                    btn.setText("停止检测");
                }
            }
        });

        root.addView(spacer(10));

        TextView tip = new TextView(this);
        tip.setText("提示：首次使用需授权麦克风权限。\n检测数据仅在本地计算，不上传。");
        tip.setTextSize(11);
        tip.setTextColor(Tools.subTextColor(this));
        tip.setGravity(Gravity.CENTER);
        root.addView(tip);

        setContentView(root);
    }

    private void startMeasure() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 100);
            Toast.makeText(this, "请允许麦克风权限后重试", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            int bufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(bufSize, SAMPLE_RATE * 2));
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                Toast.makeText(this, "麦克风初始化失败", Toast.LENGTH_LONG).show();
                return;
            }
            recorder.startRecording();
            measuring = true;
            maxDb = 0;
            maxValue.setText("本次最大分贝：-- dB");

            thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    short[] buf = new short[SAMPLE_RATE / 4];
                    while (measuring) {
                        int read = recorder.read(buf, 0, buf.length);
                        if (read > 0) {
                            double sum = 0;
                            for (int i = 0; i < read; i++) {
                                sum += (double) buf[i] * buf[i];
                            }
                            double rms = Math.sqrt(sum / read);
                            final double db = 20 * Math.log10(rms + 1e-6);
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    updateDb((float) db);
                                }
                            });
                        }
                    }
                }
            });
            thread.start();
        } catch (Exception e) {
            Toast.makeText(this, "启动失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void updateDb(float db) {
        float show = Math.max(0, db);
        dbValue.setText(String.format(java.util.Locale.US, "%.0f", show));
        if (show > maxDb) {
            maxDb = show;
            maxValue.setText(String.format(java.util.Locale.US, "本次最大分贝：%.0f dB", maxDb));
        }
        float ratio = Math.min(1f, show / 100f);
        int h = Math.max(dp(4), (int) (dp(20) * ratio));
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) barFill.getLayoutParams();
        lp.height = h;
        barFill.setLayoutParams(lp);

        if (show < 30) {
            levelText.setText("安静");
            levelText.setTextColor(Color.rgb(22, 163, 74));
        } else if (show < 50) {
            levelText.setText("正常交谈");
            levelText.setTextColor(Color.rgb(37, 99, 235));
        } else if (show < 70) {
            levelText.setText("较吵");
            levelText.setTextColor(Color.rgb(217, 119, 6));
        } else if (show < 90) {
            levelText.setText("很吵");
            levelText.setTextColor(Color.rgb(220, 38, 38));
        } else {
            levelText.setText("极度嘈杂!");
            levelText.setTextColor(Color.rgb(190, 18, 60));
        }
    }

    private void stopMeasure() {
        measuring = false;
        if (thread != null) {
            try {
                thread.join(300);
            } catch (Exception ignored) {
            }
            thread = null;
        }
        if (recorder != null) {
            try {
                recorder.stop();
                recorder.release();
            } catch (Exception ignored) {
            }
            recorder = null;
        }
    }

    @Override
    protected void onDestroy() {
        stopMeasure();
        super.onDestroy();
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
