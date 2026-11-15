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

    private LinearLayout listBox, actionBar;
    private TextView statusView, selectBtn;
    private File dir;
    private boolean selectMode = false;
    private final java.util.Set<String> selected = new java.util.HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FileStore.ensurePermission(this);
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

        selectBtn = new TextView(this);
        selectBtn.setText("多选");
        selectBtn.setTextSize(12);
        selectBtn.setGravity(Gravity.CENTER);
        selectBtn.setTextColor(Tools.accentColor(this));
        selectBtn.setPadding(dp(14), dp(8), dp(14), dp(8));
        android.graphics.drawable.GradientDrawable sb = new android.graphics.drawable.GradientDrawable();
        sb.setColor((0x1F << 24) | (Tools.accentColor(this) & 0x00FFFFFF));
        sb.setCornerRadius(dp(14));
        selectBtn.setBackground(sb);
        selectBtn.setOnClickListener(v -> toggleSelectMode());
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.gravity = Gravity.CENTER_HORIZONTAL;
        slp.bottomMargin = dp(6);
        root.addView(selectBtn, slp);

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

        actionBar = new LinearLayout(this);
        actionBar.setOrientation(LinearLayout.HORIZONTAL);
        actionBar.setGravity(Gravity.CENTER);
        actionBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        alp.topMargin = dp(12);
        root.addView(actionBar, alp);
        actionBar.addView(action("全选", v -> {
            selected.clear();
            for (File f : currentFiles()) selected.add(f.getAbsolutePath());
            refreshRows();
        }));
        actionBar.addView(action("重命名", v -> batchRename()));
        actionBar.addView(action("删除", v -> batchDelete()));
        actionBar.addView(action("取消", v -> toggleSelectMode()));

        setContentView(scroll);
    }

    private void refresh() {
        listBox.removeAllViews();
        java.util.Set<String> alive = new java.util.HashSet<>();
        for (File d : FileStore.allDirs(this)) {
            File[] fs = d.listFiles();
            if (fs == null) continue;
            for (File f : fs) if (f.isFile()) alive.add(f.getAbsolutePath());
        }
        selected.retainAll(alive);
        // 合并：主目录 + 旧下载目录（兼容v3.3之前的下载文件）
        java.util.List<File> all = new java.util.ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (File d : FileStore.allDirs(this)) {
            File[] fs = d.listFiles();
            if (fs == null) continue;
            for (File f : fs) {
                if (f.isDirectory() || f.getName().startsWith(".ik_probe")) continue;
                if (seen.add(f.getAbsolutePath())) all.add(f);
            }
        }
        if (all.isEmpty()) {
            statusView.setText("目录：\n" + FileStore.outDir(this).getAbsolutePath()
                    + "\n\n（还没有文件，去各工具里保存/提取产物吧）");
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
        statusView.setText("共 " + files.length + " 个文件 · 点文件分享 · 长按删除\n"
                + FileStore.outDir(this).getAbsolutePath());
        statusView.setTextColor(Tools.textColor(this));
        for (final File f : files) {
            listBox.addView(fileRow(f));
        }
    }

    private java.util.List<File> currentFiles() {
        java.util.List<File> out = new java.util.ArrayList<>();
        for (File d : FileStore.allDirs(this)) {
            File[] fs = d.listFiles();
            if (fs == null) continue;
            for (File f : fs) {
                if (f.isFile() && !f.getName().startsWith(".ik_probe")) out.add(f);
            }
        }
        return out;
    }

    private void toggleSelectMode() {
        selectMode = !selectMode;
        selected.clear();
        selectBtn.setText(selectMode ? "退出多选" : "多选");
        actionBar.setVisibility(selectMode ? View.VISIBLE : View.GONE);
        refresh();
    }

    private void refreshRows() {
        listBox.removeAllViews();
        java.util.List<File> all = currentFiles();
        if (!selectMode) { refresh(); return; }
        java.util.Collections.sort(all, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File f : all) listBox.addView(fileRow(f));
    }

    private TextView action(String text, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(Tools.accentColor(this));
        t.setPadding(dp(16), dp(10), dp(16), dp(10));
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor((0x1F << 24) | (Tools.accentColor(this) & 0x00FFFFFF));
        g.setCornerRadius(dp(16));
        t.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(4), 0, dp(4), 0);
        t.setLayoutParams(lp);
        t.setOnClickListener(l);
        return t;
    }

    private void batchRename() {
        if (selected.isEmpty()) { Toast.makeText(this, "先选文件", Toast.LENGTH_SHORT).show(); return; }
        android.widget.EditText ed = new android.widget.EditText(this);
        ed.setHint("前缀，比如 我的图");
        new AlertDialog.Builder(this)
                .setTitle("批量重命名（" + selected.size() + " 个）")
                .setMessage("会按 前缀_001、前缀_002… 顺序重命名，原扩展名保留")
                .setView(ed)
                .setPositiveButton("重命名", (d, w) -> {
                    String prefix = ed.getText().toString().trim();
                    if (prefix.isEmpty()) prefix = "文件";
                    java.util.List<File> list = new java.util.ArrayList<>();
                    for (File f : currentFiles()) if (selected.contains(f.getAbsolutePath())) list.add(f);
                    java.util.Collections.sort(list, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                    int ok = 0;
                    for (int i = 0; i < list.size(); i++) {
                        File f = list.get(i);
                        String name = f.getName();
                        int dot = name.lastIndexOf('.');
                        String ext = dot > 0 ? name.substring(dot) : "";
                        String nn = prefix + "_" + String.format(java.util.Locale.US, "%03d", i + 1) + ext;
                        File target = new File(f.getParentFile(), nn);
                        if (target.exists()) continue;
                        if (f.renameTo(target)) ok++;
                    }
                    Toast.makeText(this, "重命名完成 " + ok + "/" + list.size(),
                            Toast.LENGTH_LONG).show();
                    selected.clear();
                    refresh();
                })
                .setNegativeButton("取消", null).show();
    }

    private void batchDelete() {
        if (selected.isEmpty()) { Toast.makeText(this, "先选文件", Toast.LENGTH_SHORT).show(); return; }
        new AlertDialog.Builder(this)
                .setTitle("批量删除")
                .setMessage("删除选中的 " + selected.size() + " 个文件？不可恢复。")
                .setPositiveButton("删除", (d, w) -> {
                    int ok = 0;
                    for (File f : currentFiles()) {
                        if (!selected.contains(f.getAbsolutePath())) continue;
                        try {
                            if (f.delete() || !f.exists()) ok++;
                        } catch (Throwable ignored) {}
                    }
                    Toast.makeText(this, "已删除 " + ok + " 个", Toast.LENGTH_SHORT).show();
                    selected.clear();
                    refresh();
                })
                .setNegativeButton("取消", null).show();
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
        name.setText(selectMode
                ? (selected.contains(f.getAbsolutePath()) ? "● " : "○ ") + f.getName()
                : f.getName());
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

        if (selectMode) {
            if (selected.contains(f.getAbsolutePath())) {
                bg.setColor((0x33 << 24) | (Tools.accentColor(this) & 0x00FFFFFF));
            }
            share.setVisibility(View.GONE);
            del.setVisibility(View.GONE);
            row.setOnClickListener(v -> {
                String p2 = f.getAbsolutePath();
                if (selected.contains(p2)) selected.remove(p2); else selected.add(p2);
                refreshRows();
            });
            row.setOnLongClickListener(null);
            return row;
        }

        row.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                selected.clear();
                selected.add(f.getAbsolutePath());
                if (!selectMode) toggleSelectMode();
                refreshRows();
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
                boolean ok = f.delete();
                if (!ok && !FileStore.hasStoragePermission(MyFilesActivity.this)) {
                    // 没存储权限时公共下载目录删不掉，先要权限再让用户重试
                    FileStore.ensurePermission(MyFilesActivity.this);
                    Toast.makeText(MyFilesActivity.this, "需要存储权限才能删除，已弹出授权，允许后再删一次",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                if (ok || !f.exists()) {
                    Toast.makeText(MyFilesActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                    refresh();
                } else {
                    String why = f.canWrite() ? "文件被其它应用占用" : "该目录只读，需要在系统设置里给存储权限";
                    Toast.makeText(MyFilesActivity.this, "删除失败：" + why, Toast.LENGTH_LONG).show();
                }
            }
        });
        b.setNegativeButton("取消", null);
        b.show();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] granted) {
        super.onRequestPermissionsResult(code, perms, granted);
        if (code == 3001) refresh();
    }

    /** 分享文件：Android 10+ 通过 MediaStore 生成 uri；旧版本提示用文件管理器 */
    private void shareFile(final File f) {
        try {
            android.net.Uri uri = IkFileProvider.uriFor(this, f);
            if (uri == null) throw new Exception("无法生成分享链接");
            Intent it = new Intent(Intent.ACTION_SEND);
            it.setType(mimeOf(f.getName()));
            it.putExtra(Intent.EXTRA_STREAM, uri);
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(it, "分享「" + f.getName() + "」"));
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
