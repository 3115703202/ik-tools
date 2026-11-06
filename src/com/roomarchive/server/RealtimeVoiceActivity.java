package com.roomarchive.server;

import android.app.Activity;
import android.media.*;
import android.os.*;
import android.view.Gravity;
import android.widget.*;

public class RealtimeVoiceActivity extends Activity {
    AudioRecord recorder;
    AudioTrack player;
    Thread thread;
    boolean running = false;
    Button startBtn;
    TextView status;
    SeekBar pitchBar;
    float speed = 1.0f;
    int sampleRate = 44100;
    int bufferSize;

    protected void onCreate(Bundle s) {
        super.onCreate(s);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackground(Tools.bgWithGlow(this));
        root.setPadding(dp(20), dp(30), dp(20), dp(20));
        root.addView(Tools.pageTitle(this, "实时变声器"));

        status = new TextView(this);
        status.setText("点开始，对着手机说话");
        status.setTextSize(16);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(30), 0, dp(20));
        root.addView(status);

        TextView pitchLabel = new TextView(this);
        pitchLabel.setText("变调: " + speed + "x");
        root.addView(pitchLabel);

        pitchBar = new SeekBar(this);
        pitchBar.setMax(200);
        pitchBar.setProgress(100);
        pitchBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar sb, int v, boolean fromUser) {
                speed = v / 100f;
                pitchLabel.setText("变调: " + speed + "x");
            }
            public void onStartTrackingTouch(SeekBar sb) {}
            public void onStopTrackingTouch(SeekBar sb) {}
        });
        root.addView(pitchBar);

        String[] presets = {"萝莉", "大叔", "机器人", "怪物", "花栗鼠"};
        float[] speeds = {1.5f, 0.7f, 1.2f, 0.6f, 1.8f};
        for (int i = 0; i < presets.length; i++) {
            Button b = new Button(this);
            b.setText(presets[i]);
            final float sp = speeds[i];
            b.setOnClickListener(v -> {
                speed = sp;
                pitchBar.setProgress((int)(sp * 100));
                pitchLabel.setText("变调: " + sp + "x");
            });
            root.addView(b);
        }

        startBtn = new Button(this);
        startBtn.setText("开始实时变声");
        startBtn.setTextSize(16);
        startBtn.setOnClickListener(v -> {
            if (running) stop(); else start();
        });
        root.addView(startBtn);

        setContentView(root);
    }

    void start() {
        try {
            bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            recorder = new AudioRecord(android.media.MediaRecorder.AudioSource.MIC,
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize * 2);
            int playBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            player = new AudioTrack(AudioManager.STREAM_MUSIC,
                sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, playBuf * 2, AudioTrack.MODE_STREAM);
            recorder.startRecording();
            player.play();
            running = true;
            startBtn.setText("停止");
            status.setText("实时变声中... 说话试试");
            thread = new Thread(() -> {
                short[] buf = new short[bufferSize];
                while (running) {
                    int read = recorder.read(buf, 0, buf.length);
                    if (read > 0) {
                        // 简单变调：按速度比例采样
                        int outLen = (int)(read / speed);
                        short[] out = new short[outLen];
                        for (int i = 0; i < outLen; i++) {
                            int idx = Math.min((int)(i * speed), read - 1);
                            out[i] = buf[idx];
                        }
                        player.write(out, 0, out.length);
                    }
                }
            });
            thread.start();
        } catch (Exception e) {
            status.setText("启动失败: " + e.getMessage());
        }
    }

    void stop() {
        running = false;
        try {
            if (recorder != null) { recorder.stop(); recorder.release(); recorder = null; }
            if (player != null) { player.stop(); player.release(); player = null; }
        } catch (Exception ignored) {}
        startBtn.setText("开始实时变声");
        status.setText("已停止");
    }

    protected void onDestroy() {
        stop();
        super.onDestroy();
    }

    int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
}
