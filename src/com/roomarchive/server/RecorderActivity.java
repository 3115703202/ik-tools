package com.roomarchive.server;
import android.app.Activity;
import android.os.*;
import android.view.Gravity;
import android.widget.*;
import android.media.*;
import java.io.File;

public class RecorderActivity extends Activity {
    MediaRecorder mr;
    boolean recording = false;
    Button recBtn;
    TextView status;
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackground(Tools.bgWithGlow(this));
        root.setPadding(dp(16), dp(20), dp(16), dp(16));
        root.addView(Tools.pageTitle(this, "录音机"));
        status = new TextView(this);
        status.setText("点击按钮开始录音");
        status.setTextSize(16);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(40), 0, dp(40));
        root.addView(status);
        recBtn = new Button(this);
        recBtn.setText("开始录音");
        recBtn.setTextSize(18);
        recBtn.setOnClickListener(v -> {
            if (recording) { stopRec(); } else { startRec(); }
        });
        root.addView(recBtn);
        setContentView(root);
    }
    void startRec() {
        try {
            File dir = new File(FileStore.outDir(this), "录音");
            dir.mkdirs();
            File f = new File(dir, "rec_" + System.currentTimeMillis() + ".m4a");
            mr = new MediaRecorder();
            mr.setAudioSource(MediaRecorder.AudioSource.MIC);
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mr.setOutputFile(f.getAbsolutePath());
            mr.prepare();
            mr.start();
            recording = true;
            recBtn.setText("停止录音");
            status.setText("正在录音...\n保存到: " + f.getAbsolutePath());
        } catch (Exception e) { Toast.makeText(this, "录音失败: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }
    void stopRec() {
        try { mr.stop(); mr.release(); } catch (Exception ignored) {}
        mr = null;
        recording = false;
        recBtn.setText("开始录音");
        status.setText("录音已保存到我的文件");
    }
    int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
}
