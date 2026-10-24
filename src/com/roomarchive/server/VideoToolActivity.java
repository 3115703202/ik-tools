package com.roomarchive.server;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

/** 视频工具：选视频 → 提取封面 / 提取音频(m4a) / 视频信息 */
public class VideoToolActivity extends BaseActivity {

    private static final int REQ_PICK_VIDEO = 5001;
    private static final int REQ_SAVE_COVER = 5002;
    private static final int REQ_SAVE_AUDIO = 5003;

    private TextView statusView;
    private TextView infoView;
    private Uri videoUri;

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

        TextView title = Tools.pageTitle(this, "视频工具");
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("提取封面 / 提取音频 / 查看信息，全程本地处理");
        sub.setTextSize(13);
        sub.setTextColor(Tools.subTextColor(this));
        sub.setGravity(Gravity.CENTER);
        root.addView(sub);
        root.addView(spacer(14));

        statusView = new TextView(this);
        statusView.setText("请先选择视频文件");
        statusView.setTextSize(14);
        statusView.setTextColor(Tools.subTextColor(this));
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(dp(8), dp(10), dp(8), dp(10));
        root.addView(statusView);
        root.addView(spacer(8));

        infoView = new TextView(this);
        infoView.setTextSize(13);
        infoView.setTextColor(Tools.textColor(this));
        infoView.setGravity(Gravity.CENTER);
        root.addView(infoView);
        root.addView(spacer(12));

        root.addView(actionButton("选择视频", "folder", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                it.addCategory(Intent.CATEGORY_OPENABLE);
                it.setType("video/*");
                startActivityForResult(it, REQ_PICK_VIDEO);
            }
        }));
        root.addView(actionButton("提取封面（中间帧）", "image", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickVideoThen(REQ_SAVE_COVER);
            }
        }));
        root.addView(actionButton("提取音频（m4a）", "music", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickVideoThen(REQ_SAVE_AUDIO);
            }
        }));

        root.addView(spacer(12));
        TextView tip = new TextView(this);
        tip.setText("说明：\n"
                + "· 封面提取：取视频中间一帧画面，保存为 JPEG 图片\n"
                + "· 音频提取：支持几乎所有音轨编码（AAC 直封无损，MP3/AC3 等自动转码），保存为 .m4a 音频文件\n"
                + "· 产物自动保存到 App 专属目录，到主界面「我的文件」可查看/分享/删除\n\n"
                + "关于「链接去水印」：第三方平台（抖音等）的水印去除依赖平台解析接口，"
                + "自研应用无法稳定提供真实可用的解析，故未内置，不做假功能。");
        tip.setTextSize(12);
        tip.setTextColor(Tools.subTextColor(this));
        root.addView(tip);

        setContentView(scroll);
    }

    /** 没有选视频时先选，选完再执行 saveReq */
    private void pickVideoThen(final int saveReq) {
        if (videoUri != null) {
            if (saveReq == REQ_SAVE_COVER) saveCover();
            else saveAudio();
            return;
        }
        pendingAction = saveReq;
        Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        it.addCategory(Intent.CATEGORY_OPENABLE);
        it.setType("video/*");
        startActivityForResult(it, REQ_PICK_VIDEO);
    }

    private int pendingAction = 0;

    private Button actionButton(String text, String iconKey, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Tools.pillButton(this, 50, true));
        Icons.attachToText(b, iconKey, Color.WHITE, 16, 6);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        lp.setMargins(0, dp(5), 0, dp(5));
        b.setLayoutParams(lp);
        return b;
    }

    // ================= 信息 =================

    private void showInfo() {
        try {
            MediaMetadataRetriever r = new MediaMetadataRetriever();
            r.setDataSource(this, videoUri);
            String dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            String w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            String mime = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
            long ms = 0;
            if (dur != null) ms = Long.parseLong(dur);
            infoView.setText((w == null ? "?" : w) + " × " + (h == null ? "?" : h)
                    + " 像素 · 时长 " + fmtTime(ms) + (mime == null ? "" : " · " + mime));
            r.release();
        } catch (Exception e) {
            infoView.setText("（读取视频信息失败）");
        }
    }

    private String fmtTime(long ms) {
        long s = ms / 1000;
        return String.format(java.util.Locale.US, "%d:%02d", s / 60, s % 60);
    }

    // ================= 封面 =================

    private void saveCover() {
        setBusy("正在提取封面…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    MediaMetadataRetriever r = new MediaMetadataRetriever();
                    r.setDataSource(VideoToolActivity.this, videoUri);
                    long durMs = 0;
                    String dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if (dur != null) durMs = Long.parseLong(dur);
                    Bitmap bm = r.getFrameAtTime(durMs / 2 * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    r.release();
                    if (bm == null) throw new Exception("无法取帧（视频可能无画面轨）");
                    File out = new File(outDir(), "封面_" + System.currentTimeMillis() + ".jpg");
                    FileOutputStream fos = new FileOutputStream(out);
                    bm.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                    fos.flush();
                    fos.close();
                    if (!bm.isRecycled()) bm.recycle();
                    final String path = out.getAbsolutePath();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("成功：封面已保存\n" + path);
                            statusView.setTextColor(0xFF16A34A);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("失败：提取封面失败：" + e.getMessage());
                            statusView.setTextColor(0xFFDC2626);
                        }
                    });
                }
            }
        }).start();
    }

    // ================= 音频 =================

    private void saveAudio() {
        setBusy("正在提取音频…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File out = new File(outDir(), "音频_" + System.currentTimeMillis() + ".m4a");
                    MediaExtractor extractor = new MediaExtractor();
                    extractor.setDataSource(VideoToolActivity.this, videoUri, null);
                    int audioTrack = -1;
                    for (int i = 0; i < extractor.getTrackCount(); i++) {
                        MediaFormat f = extractor.getTrackFormat(i);
                        String mime = f.getString(MediaFormat.KEY_MIME);
                        if (mime != null && mime.startsWith("audio/")) {
                            audioTrack = i;
                            break;
                        }
                    }
                    if (audioTrack < 0) {
                        extractor.release();
                        throw new Exception("该视频没有音轨");
                    }
                    MediaFormat format = extractor.getTrackFormat(audioTrack);
                    String mime = format.getString(MediaFormat.KEY_MIME);
                    if (mime == null) {
                        extractor.release();
                        throw new Exception("无法识别音轨编码");
                    }
                    extractor.selectTrack(audioTrack);

                    if (mime.contains("aac")) {
                        // AAC：直接 remux（无损、快）
                        MediaMuxer muxer = new MediaMuxer(out.getAbsolutePath(),
                                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                        int trackIdx = muxer.addTrack(format);
                        muxer.start();
                        ByteBuffer buf = ByteBuffer.allocate(1024 * 1024);
                        android.media.MediaCodec.BufferInfo info = new android.media.MediaCodec.BufferInfo();
                        while (true) {
                            int size = extractor.readSampleData(buf, 0);
                            if (size < 0) break;
                            long t = extractor.getSampleTime();
                            int flags = extractor.getSampleFlags();
                            buf.rewind();
                            info.set(0, size, t, flags);
                            muxer.writeSampleData(trackIdx, buf, info);
                            extractor.advance();
                        }
                        muxer.stop();
                        muxer.release();
                        extractor.release();
                    } else {
                        // 其他编码（MP3/AC3/AMR/PCM…）：解码成 PCM 再编码成 AAC
                        transcodeAudio(extractor, format, out);
                        extractor.release();
                    }
                    audioTmpFile = out;
                    final String path = out.getAbsolutePath();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("成功：音频已保存（.m4a）\n" + path);
                            statusView.setTextColor(0xFF16A34A);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("失败：提取音频失败：" + e.getMessage());
                            statusView.setTextColor(0xFFDC2626);
                        }
                    });
                }
            }
        }).start();
    }

    /** 非 AAC 音轨：MediaCodec 解码 → AAC 编码 → m4a */
    private void transcodeAudio(MediaExtractor extractor, MediaFormat inFormat, File out) throws Exception {
        String mime = inFormat.getString(MediaFormat.KEY_MIME);
        android.media.MediaCodec decoder = android.media.MediaCodec.createDecoderByType(mime);
        decoder.configure(inFormat, null, null, 0);
        decoder.start();

        int sampleRate = 44100, channels = 2;
        if (inFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        if (inFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        if (channels <= 0) channels = 2;
        if (sampleRate <= 0) sampleRate = 44100;

        MediaFormat outFormat = MediaFormat.createAudioFormat("audio/mp4a-latm", sampleRate, channels);
        outFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
        outFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        android.media.MediaCodec encoder = android.media.MediaCodec.createEncoderByType("audio/mp4a-latm");
        encoder.configure(outFormat, null, null, android.media.MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoder.start();

        MediaMuxer muxer = new MediaMuxer(out.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        int trackIdx = -1;

        android.media.MediaCodec.BufferInfo decInfo = new android.media.MediaCodec.BufferInfo();
        android.media.MediaCodec.BufferInfo encInfo = new android.media.MediaCodec.BufferInfo();
        boolean inputDone = false, decoderDone = false, encoderDone = false, encoderEofSent = false;

        while (!encoderDone) {
            // 1) 喂解码器（extractor → decoder）
            if (!inputDone) {
                int inIdx = decoder.dequeueInputBuffer(10000);
                if (inIdx >= 0) {
                    ByteBuffer inBuf = decoder.getInputBuffer(inIdx);
                    int size = extractor.readSampleData(inBuf, 0);
                    if (size < 0) {
                        decoder.queueInputBuffer(inIdx, 0, 0, 0, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                    } else {
                        decoder.queueInputBuffer(inIdx, 0, size, extractor.getSampleTime(),
                                extractor.getSampleFlags());
                        extractor.advance();
                    }
                }
            }

            // 2) 解码器输出（PCM）→ 编码器输入
            if (!decoderDone) {
                int outIdx = decoder.dequeueOutputBuffer(decInfo, 10000);
                if (outIdx >= 0) {
                    if ((decInfo.flags & android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        decoder.releaseOutputBuffer(outIdx, false);
                        decoderDone = true;
                    } else if (decInfo.size > 0) {
                        ByteBuffer outBuf = decoder.getOutputBuffer(outIdx);
                        if (outBuf == null) {
                            decoder.releaseOutputBuffer(outIdx, false);
                        } else {
                            int encInIdx = encoder.dequeueInputBuffer(10000);
                            if (encInIdx >= 0) {
                                ByteBuffer encIn = encoder.getInputBuffer(encInIdx);
                                encIn.clear();
                                outBuf.position(decInfo.offset);
                                outBuf.limit(decInfo.offset + decInfo.size);
                                encIn.put(outBuf);
                                encoder.queueInputBuffer(encInIdx, 0, decInfo.size,
                                        decInfo.presentationTimeUs, 0);
                                decoder.releaseOutputBuffer(outIdx, false);
                            }
                            // encInIdx < 0：编码器暂忙，下轮再取该 PCM，不释放
                        }
                    } else {
                        decoder.releaseOutputBuffer(outIdx, false);
                    }
                }
            }

            // 3) 解码结束 → 给编码器发 EOF（否则编码器不结束）
            if (decoderDone && !encoderEofSent) {
                int encInIdx = encoder.dequeueInputBuffer(10000);
                if (encInIdx >= 0) {
                    encoder.queueInputBuffer(encInIdx, 0, 0, 0,
                            android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    encoderEofSent = true;
                }
            }

            // 4) 编码器输出 → muxer
            int encOutIdx = encoder.dequeueOutputBuffer(encInfo, 10000);
            if (encOutIdx >= 0) {
                if (trackIdx < 0) {
                    MediaFormat mf = encoder.getOutputFormat();
                    trackIdx = muxer.addTrack(mf);
                    muxer.start();
                }
                if (encInfo.size > 0) {
                    ByteBuffer encOut = encoder.getOutputBuffer(encOutIdx);
                    if (encOut != null) {
                        encOut.position(encInfo.offset);
                        encOut.limit(encInfo.offset + encInfo.size);
                        muxer.writeSampleData(trackIdx, encOut, encInfo);
                    }
                }
                encoder.releaseOutputBuffer(encOutIdx, false);
                if ((encInfo.flags & android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    encoderDone = true;
                }
            }
        }

        muxer.stop();
        muxer.release();
        decoder.stop();
        decoder.release();
        encoder.stop();
        encoder.release();
    }

    private File audioTmpFile;

    /** App 专属产物目录：安装后自动存在，所有保存都默认进这里 */
    private File outDir() {
        return FileStore.outDir(this);
    }

    // ================= 回调 =================

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == REQ_PICK_VIDEO) {
            videoUri = data.getData();
            statusView.setText("已选择视频，可提取封面 / 音频");
            statusView.setTextColor(0xFF16A34A);
            showInfo();
            if (pendingAction == REQ_SAVE_COVER) {
                pendingAction = 0;
                saveCover();
            } else if (pendingAction == REQ_SAVE_AUDIO) {
                pendingAction = 0;
                saveAudio();
            }
        }
    }

    private void setBusy(String msg) {
        statusView.setText(msg);
        statusView.setTextColor(Color.rgb(200, 140, 20));
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
