package com.roomarchive.server;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 录屏：MediaProjection + MediaRecorder */
public class ScreenRecordActivity extends Activity {

    private static final int REQ_CODE = 1001;
    private MediaProjectionManager mpm;
    private TextView status;
    private Button toggleBtn;
    private boolean recording = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(30), dp(20), dp(20));
        root.setBackground(Tools.bgWithGlow(this));
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("屏幕录制");
        title.setTextSize(22);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setTextColor(Tools.textColor(this));
        root.addView(title);

        status = new TextView(this);
        status.setText("点击下方按钮开始录屏\n录屏文件保存在「我的文件」");
        status.setTextSize(14);
        status.setTextColor(Tools.subTextColor(this));
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(30), 0, dp(30));
        root.addView(status);

        toggleBtn = new Button(this);
        toggleBtn.setText("开始录屏");
        toggleBtn.setAllCaps(false);
        toggleBtn.setTextSize(16);
        toggleBtn.setTextColor(Color.WHITE);
        toggleBtn.setBackground(Tools.pillButton(this, 50, true));
        toggleBtn.setOnClickListener(v -> {
            if (!recording) {
                startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CODE);
            } else {
                stopService(new Intent(this, RecorderService.class));
                recording = false;
                toggleBtn.setText("开始录屏");
                status.setText("已停止，文件在「我的文件」");
            }
        });
        root.addView(toggleBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50)));

        setContentView(root);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_CODE && res == RESULT_OK) {
            Intent svc = new Intent(this, RecorderService.class);
            svc.putExtra("result_code", res);
            svc.putExtra("result_data", data);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
            else startService(svc);
            recording = true;
            toggleBtn.setText("停止录屏");
            status.setText("● 正在录制… 点击停止结束");
        } else {
            Toast.makeText(this, "需要授权录屏", Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    public static class RecorderService extends Service {
        private MediaProjection mp;
        private MediaRecorder recorder;
        private VirtualDisplay vd;
        private int resultCode;
        private Intent resultData;

        @Override public void onCreate() {
            createChannel();
        }

        @Override public int onStartCommand(Intent i, int flags, int id) {
            resultCode = i.getIntExtra("result_code", 0);
            resultData = i.getParcelableExtra("result_data");
            startForeground(1, buildNotification());
            new Thread(this::startRecording).start();
            return START_STICKY;
        }

        private Notification buildNotification() {
            if (Build.VERSION.SDK_INT >= 26) {
                return new Notification.Builder(this, "rec")
                        .setContentTitle("正在录屏")
                        .setSmallIcon(android.R.drawable.presence_video_online)
                        .build();
            }
            return new Notification.Builder(this)
                    .setContentTitle("正在录屏")
                    .setSmallIcon(android.R.drawable.presence_video_online)
                    .build();
        }

        private void startRecording() {
            try {
                MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
                mp = mpm.getMediaProjection(resultCode, resultData);
                if (mp == null) { stopSelf(); return; }

                DisplayMetrics dm = getResources().getDisplayMetrics();
                int w = dm.widthPixels, h = dm.heightPixels, dpi = dm.densityDpi;

                File outDir = FileStore.outDir(this);
                String name = "录屏_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".mp4";
                File out = new File(outDir, name);

                recorder = new MediaRecorder();
                recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                recorder.setOutputFile(out.getAbsolutePath());
                recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
                recorder.setVideoSize(w, h);
                recorder.setVideoFrameRate(30);
                recorder.setVideoEncodingBitRate(5 * 1024 * 1024);
                recorder.prepare();

                vd = mp.createVirtualDisplay("screen", w, h, dpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        recorder.getSurface(), null, null);
                recorder.start();
            } catch (Exception e) {
                stopSelf();
            }
        }

        private void stopRecording() {
            try { if (recorder != null) { recorder.stop(); recorder.release(); } } catch (Exception ignored) {}
            try { if (vd != null) vd.release(); } catch (Exception ignored) {}
            try { if (mp != null) mp.stop(); } catch (Exception ignored) {}
            recorder = null; vd = null; mp = null;
            stopSelf();
        }

        @Override public void onDestroy() { stopRecording(); super.onDestroy(); }
        @Override public IBinder onBind(Intent i) { return null; }

        private void createChannel() {
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel("rec", "录屏", NotificationManager.IMPORTANCE_LOW);
                ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
            }
        }
    }
}
