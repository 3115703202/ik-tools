package com.roomarchive.server;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;

import java.io.File;
import java.nio.ByteBuffer;

/** 无损剪切：MediaExtractor 抽轨 + MediaMuxer 重新封装，不重编码，秒出 */
public class MediaTrimmer {

    public interface Listener { void onProgress(int percent); }

    public static void trim(Context c, Uri uri, long startMs, long endMs, File out,
                            boolean videoTrack, Listener l) throws Exception {
        MediaExtractor ex = new MediaExtractor();
        MediaMuxer mux = null;
        try {
            ex.setDataSource(c, uri, null);
            int track = -1;
            MediaFormat fmt = null;
            for (int i = 0; i < ex.getTrackCount(); i++) {
                MediaFormat f = ex.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime == null) continue;
                boolean isVideo = mime.startsWith("video/");
                if (videoTrack == isVideo) {
                    track = i; fmt = f; break;
                }
            }
            if (track < 0 || fmt == null) {
                throw new Exception(videoTrack ? "这个文件里没有视频轨" : "这个文件里没有音频轨");
            }
            long startUs = Math.max(0, startMs) * 1000L;
            long endUs = endMs * 1000L;
            long durUs = 0;
            try { durUs = fmt.getLong(MediaFormat.KEY_DURATION); } catch (Throwable ignored) {}
            if (endUs <= startUs) throw new Exception("结束时间要大于开始时间");
            if (durUs > 0 && endUs > durUs) endUs = durUs;

            mux = new MediaMuxer(out.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int outTrack = mux.addTrack(fmt);
            mux.start();
            ex.selectTrack(track);
            ex.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);

            ByteBuffer buf = ByteBuffer.allocate(2 * 1024 * 1024);
            android.media.MediaCodec.BufferInfo bi = new android.media.MediaCodec.BufferInfo();
            int wrote = 0;
            long total = Math.max(1, endUs - startUs);
            while (true) {
                int size = ex.readSampleData(buf, 0);
                if (size < 0) break;
                long ts = ex.getSampleTime();
                if (ts > endUs) break;
                if (ts >= startUs - 500000) {
                    buf.position(0);
                    buf.limit(size);
                    bi.set(0, size, ts, ex.getSampleFlags());
                    mux.writeSampleData(outTrack, buf, bi);
                    wrote++;
                }
                if (!ex.advance()) break;
                if (wrote > 0 && (wrote & 31) == 0 && l != null) {
                    int p = (int) Math.min(99, (ts - startUs) * 100 / total);
                    l.onProgress(Math.max(0, p));
                }
            }
            if (wrote == 0) throw new Exception("这个时间段里没有可写的数据");
            mux.stop();
            if (l != null) l.onProgress(100);
        } finally {
            try { ex.release(); } catch (Throwable ignored) {}
            if (mux != null) {
                try { mux.stop(); } catch (Throwable ignored) {}
                try { mux.release(); } catch (Throwable ignored) {}
            }
        }
    }

    public static String durationText(long ms) {
        long s = ms / 1000;
        return String.format(java.util.Locale.US, "%02d:%02d.%03d", s / 60, s % 60, ms % 1000);
    }
}
