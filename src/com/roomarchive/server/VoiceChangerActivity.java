package com.roomarchive.server;

import android.app.Activity;
import android.media.*;
import android.os.*;
import android.view.Gravity;
import android.widget.*;
import java.io.*;

public class VoiceChangerActivity extends Activity {
    MediaRecorder recorder;
    boolean recording = false;
    String recPath;
    MediaPlayer player;
    Button recBtn;
    TextView status;

    // 预设效果：名字, 速度, 音调
    String[][] effects = {
        {"原音", "1.0", "1.0"},
        {"萝莉", "1.5", "1.5"},
        {"大叔", "0.7", "0.7"},
        {"机器人", "1.2", "0.8"},
        {"怪物", "0.6", "0.5"},
        {"花栗鼠", "1.8", "1.8"},
        {"回声", "1.0", "1.2"},
        {"慢动作", "0.5", "0.8"},
    };

    Button[] effectBtns;

    protected void onCreate(Bundle s) {
        super.onCreate(s);
        effectBtns = new Button[effects.length];
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Tools.bgWithGlow(this));
        root.setPadding(dp(16), dp(24), dp(16), dp(16));
        root.addView(Tools.pageTitle(this, "变声器"));

        status = new TextView(this);
        status.setText("点按钮开始录音");
        status.setTextSize(15);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(20), 0, dp(20));
        root.addView(status);

        recBtn = new Button(this);
        recBtn.setText("开始录音");
        recBtn.setTextSize(16);
        recBtn.setOnClickListener(v -> {
            if (recording) stopRec(); else startRec();
        });
        root.addView(recBtn);

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(0, dp(20), 0, 0);
        for (int i = 0; i < effects.length; i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setWeightSum(2);
            for (int j = i; j < Math.min(i + 2, effects.length); j++) {
                effectBtns[j] = new Button(this);
                effectBtns[j].setText(effects[j][0]);
                effectBtns[j].setTextSize(14);
                effectBtns[j].setEnabled(false);
                final int idx = j;
                effectBtns[j].setOnClickListener(v -> playEffect(idx));
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(50), 1f);
                p.setMargins(dp(4), dp(4), dp(4), dp(4));
                row.addView(effectBtns[j], p);
            }
            grid.addView(row);
        }
        root.addView(grid);

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
    }

    void startRec() {
        try {
            File dir = new File(FileStore.outDir(this), "变声器");
            dir.mkdirs();
            recPath = new File(dir, "rec_" + System.currentTimeMillis() + ".m4a").getAbsolutePath();
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setOutputFile(recPath);
            recorder.prepare();
            recorder.start();
            recording = true;
            recBtn.setText("停止录音");
            status.setText("正在录音...");
        } catch (Exception e) {
            status.setText("录音失败: " + e.getMessage());
        }
    }

    void stopRec() {
        try { recorder.stop(); recorder.release(); } catch (Exception ignored) {}
        recorder = null;
        recording = false;
        recBtn.setText("重新录音");
        status.setText("录音完成，点下面效果试听");
        for (Button b : effectBtns) b.setEnabled(true);
    }

    void playEffect(int idx) {
        try {
            if (player != null) { player.stop(); player.release(); }
            player = new MediaPlayer();
            player.setDataSource(recPath);
            player.prepare();
            float speed = Float.parseFloat(effects[idx][1]);
            player.setPlaybackParams(player.getPlaybackParams().setSpeed(speed));
            player.start();
            status.setText("播放: " + effects[idx][0] + " (" + speed + "x)");
        } catch (Exception e) {
            status.setText("播放失败: " + e.getMessage());
        }
    }

    protected void onDestroy() {
        if (player != null) { player.release(); player = null; }
        super.onDestroy();
    }

    int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
}
