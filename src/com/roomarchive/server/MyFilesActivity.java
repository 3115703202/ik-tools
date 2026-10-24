package com.roomarchive.server;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

/** 我的文件：查看/分享/删除 App 专属目录里的所有产物 */
public class MyFilesActivity extends BaseActivity {

    private LinearLayout listBox;
    private TextView statusView;
    private File dir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dir = FileStore.outDir(this);
        buildUi();
        refresh();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Tools.bgWithGlow(this));
        root.setPadding(dp(16), dp(20), dp(16), dp(18));
        scroll.addView(root);

        TextView title = Tools.pageTitle(this, "我的文件");
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        statusView = new TextView(this);
        statusView.setTextSize(12);
        statusView.setTextColor(Tools.subTextColor(this));
        statusView.setGravity(Gravity.CENTER);
        root.addView(statusView);
        root.addView(spacer(8));

        Button btnRefresh = new Button(this);
        btnRefresh.setText("刷新列表");
        btnRefresh.setTextColor(Tools.accentColor(this));
        int ac = Tools.accentColor(this);
        android.graphics.drawable.GradientDrawable rBg = new android.graphics.drawable.GradientDrawable();
        rBg.setColor((0x1F << 24) | (ac & 0x00FFFFFF));
        rBg.setCornerRadius(Tools.dp(this, 22));
        btnRefresh.setBackground(rBg);
        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refresh();
            }
        });
        root.addView(btnRefresh);
        root.addView(spacer(10));

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(listBox);

        setContentView(scroll);
    }

    private void refresh() {
        listBox.removeAllViews();
        // 合并：主目录 + 旧下载目录（兼容v3.3之前的下载文件）
        java.util.List<File> all = new java.util.ArrayList<>();
        File[] f1 = dir.listFiles();
        if (f1 != null) java.util.Collections.addAll(all, f1);
        File oldDir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
        if (oldDir != null && !oldDir.equals(dir)) {
            File[] f2 = oldDir.listFiles();
            if (f2 != null) java.util.Collections.addAll(all, f2);
        }
        if (all.isEmpty()) {
            statusView.setText("目录：\n" + dir.getAbsolutePath() + "\n\n（还没有文件，去各工具里保存/提取产物吧）");
            statusView.setTextColor(Tools.subTextColor(this));
            return;
        }
        File[] files = all.toArray(new File[0]);
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        statusView.setText("共 " + files.length + " 个文件 · 点文件分享 · 长按删除");
        statusView.setTextColor(Tools.textColor(this));
        for (final File f : files) {
            listBox.addView(fileRow(f));
        }
    }

    private View fileRow(final File f) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setGravity(Gravity.CENTER_VERTICAL);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Tools.cardColor(this));
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), Tools.isDark(this) ? 0xFF2A3040 : 0xFFE4E8EE);
        row.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(4));
        row.setLayoutParams(lp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView name = new TextView(this);
        name.setText(f.getName());
        name.setTextSize(14);
        name.setTextColor(Tools.textColor(this));
        name.setSingleLine(true);
        texts.addView(name);
        TextView meta = new TextView(this);
        meta.setText(fmtSize(f.length()) + " · " + fmtTime(f.lastModified()));
        meta.setTextSize(11);
        meta.setTextColor(Tools.subTextColor(this));
        texts.addView(meta);
        row.addView(texts);

        Button share = new Button(this);
        share.setText("分享");
        share.setTextSize(12);
        share.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareFile(f);
            }
        });
        row.addView(share);

        Button del = new Button(this);
        del.setText("删除");
        del.setTextSize(12);
        del.setTextColor(Color.rgb(180, 60, 60));
        del.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDelete(f);
            }
        });
        row.addView(del);

        row.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                confirmDelete(f);
                return true;
            }
        });
        return row;
    }

    private void confirmDelete(final File f) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("删除文件");
        b.setMessage("确定删除「" + f.getName() + "」吗？删除后无法恢复。");
        b.setPositiveButton("删除", new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface d, int which) {
                if (f.delete()) {
                    Toast.makeText(MyFilesActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                    refresh();
                } else {
                    Toast.makeText(MyFilesActivity.this, "删除失败", Toast.LENGTH_SHORT).show();
                }
            }
        });
        b.setNegativeButton("取消", null);
        b.show();
    }

    /** 分享文件：Android 10+ 通过 MediaStore 生成 uri；旧版本提示用文件管理器 */
    private void shareFile(final File f) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, f.getName());
                cv.put(MediaStore.MediaColumns.MIME_TYPE, mimeOf(f.getName()));
                Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                Uri item = getContentResolver().insert(collection, cv);
                if (item == null) throw new Exception("无法创建分享入口");
                OutputStream os = getContentResolver().openOutputStream(item);
                FileInputStream is = new FileInputStream(f);
                byte[] buf = new byte[65536];
                int n;
                while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
                os.flush();
                os.close();
                is.close();

                Intent it = new Intent(Intent.ACTION_SEND);
                it.setType(mimeOf(f.getName()));
                it.putExtra(Intent.EXTRA_STREAM, item);
                it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(it, "分享「" + f.getName() + "」"));
            } else {
                Toast.makeText(this, "Android 9 及以下：请到文件管理器 Android/data/com.roomarchive.server/files/IK工具箱 查看该文件",
                        Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "分享失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String mimeOf(String name) {
        String n = name.toLowerCase(Locale.US);
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".m4a") || n.endsWith(".aac")) return "audio/mp4";
        if (n.endsWith(".mp3")) return "audio/mpeg";
        if (n.endsWith(".zip")) return "application/zip";
        if (n.endsWith(".room")) return "application/octet-stream";
        if (n.endsWith(".txt")) return "text/plain";
        return "application/octet-stream";
    }

    private String fmtSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0);
    }

    private String fmtTime(long t) {
        return new SimpleDateFormat("MM-dd HH:mm", Locale.US).format(new Date(t));
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
