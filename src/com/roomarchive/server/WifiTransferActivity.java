package com.roomarchive.server;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

/** 局域网传文件：手机开网页，电脑浏览器上传/下载 */
public class WifiTransferActivity extends BaseActivity {

    private TextView urlText, stateText, logText, actionBtn;
    private TransferServer server;
    private LinearLayout logBox;
    private static final int REQ_PICK = 1001;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Tools.bgWithGlow(this));
        root.setPadding(dp(18), dp(24), dp(18), dp(24));
        sv.addView(root);

        TextView title = Tools.pageTitle(this, "局域网传文件");
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("同一个 WiFi · 浏览器上传下载 · 不走流量");
        sub.setTextSize(12);
        sub.setTextColor(Tools.subTextColor(this));
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(6), 0, dp(16));
        root.addView(sub);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(Tools.liquidGlassCard(this, 22));
        Tools.setElevation(card, 10);

        stateText = new TextView(this);
        stateText.setText("未启动");
        stateText.setTextSize(13);
        stateText.setTextColor(Tools.subTextColor(this));
        card.addView(stateText);

        urlText = new TextView(this);
        urlText.setText("http://" + TransferServer.localIp() + ":8088");
        urlText.setTextSize(19);
        urlText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        urlText.setTextColor(Tools.accentColor(this));
        urlText.setPadding(0, dp(8), 0, dp(12));
        card.addView(urlText);

        actionBtn = new TextView(this);
        actionBtn.setText("启动服务");
        actionBtn.setGravity(Gravity.CENTER);
        actionBtn.setTextSize(15);
        actionBtn.setTextColor(0xFFFFFFFF);
        actionBtn.setBackground(Tools.pillButton(this, 48, true));
        actionBtn.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        actionBtn.setOnClickListener(v -> toggle());
        card.addView(actionBtn);

        TextView pick = new TextView(this);
        pick.setText("选文件放进共享");
        pick.setGravity(Gravity.CENTER);
        pick.setTextSize(13);
        pick.setTextColor(Tools.accentColor(this));
        pick.setBackground(Tools.pillButton(this, 42, false));
        LinearLayout.LayoutParams pk = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(42));
        pk.topMargin = dp(10);
        card.addView(pick, pk);
        pick.setOnClickListener(v -> pickFiles());

        TextView copy = new TextView(this);
        copy.setText("复制地址");
        copy.setGravity(Gravity.CENTER);
        copy.setTextSize(12);
        copy.setTextColor(Tools.subTextColor(this));
        copy.setPadding(0, dp(10), 0, 0);
        copy.setOnClickListener(v -> {
            try {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("url", urlText.getText()));
                toast("地址已复制");
            } catch (Throwable ignored) {}
        });
        card.addView(copy);
        root.addView(card);

        TextView tip = new TextView(this);
        tip.setText("· 电脑/平板连同一个 WiFi，浏览器打开上面的地址\n"
                + "· 上传的文件落在 Download/IK工具箱/传文件\n"
                + "· 列表里的文件点一下就能下载回电脑\n"
                + "· 服务只在本机局域网，离开页面自动关闭");
        tip.setTextSize(12);
        tip.setTextColor(Tools.subTextColor(this));
        tip.setLineSpacing(dp(5), 1f);
        tip.setPadding(dp(16), dp(14), dp(16), dp(14));
        tip.setBackground(Tools.liquidGlassCard(this, 18));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dp(16);
        root.addView(tip, tp);

        logBox = new LinearLayout(this);
        logBox.setOrientation(LinearLayout.VERTICAL);
        logBox.setPadding(dp(14), dp(12), dp(14), dp(12));
        logBox.setBackground(Tools.liquidGlassCard(this, 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        root.addView(logBox, lp);

        logText = new TextView(this);
        logText.setTextSize(11);
        logText.setTextColor(Tools.subTextColor(this));
        logBox.addView(logText);

        setContentView(sv);
    }

    private void toggle() {
        if (server != null && server.isRunning()) {
            server.stop();
            server = null;
            stateText.setText("已停止");
            actionBtn.setText("启动服务");
            urlText.setTextColor(Tools.subTextColor(this));
            return;
        }
        File dir = new File(FileStore.outDir(this), "传文件");
        try {
            server = new TransferServer(dir, 8088, line -> runOnUiThread(() -> appendLog(line)));
            server.start();
            String ip = TransferServer.localIp();
            urlText.setText("http://" + ip + ":8088");
            urlText.setTextColor(Tools.accentColor(this));
            stateText.setText("运行中 · 文件目录 " + dir.getAbsolutePath());
            actionBtn.setText("停止服务");
            toast("已启动，浏览器打开 " + urlText.getText());
        } catch (Throwable t) {
            server = null;
            toast("启动失败：" + t.getMessage() + "（端口可能被占用）");
        }
    }

    private void pickFiles() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(i, REQ_PICK);
        } catch (Throwable t) {
            toast("打不开文件选择器：" + t.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_PICK || res != RESULT_OK || data == null) return;
        java.util.List<Uri> uris = new java.util.ArrayList<>();
        if (data.getClipData() != null) {
            ClipData cd = data.getClipData();
            for (int i = 0; i < cd.getItemCount(); i++) uris.add(cd.getItemAt(i).getUri());
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        if (uris.isEmpty()) return;
        final File dir = new File(FileStore.outDir(this), "传文件");
        dir.mkdirs();
        new Thread(() -> {
            int ok = 0;
            for (Uri u : uris) {
                try {
                    String name = null;
                    Cursor cur = getContentResolver().query(u,
                            new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
                    if (cur != null) {
                        if (cur.moveToFirst()) name = cur.getString(0);
                        cur.close();
                    }
                    if (name == null || name.trim().isEmpty()) {
                        name = "文件_" + System.currentTimeMillis();
                    }
                    File out = new File(dir, name);
                    if (out.exists()) {
                        String base = name, ext = "";
                        int dot = name.lastIndexOf('.');
                        if (dot > 0) { base = name.substring(0, dot); ext = name.substring(dot); }
                        out = new File(dir, base + "_" + System.currentTimeMillis() + ext);
                    }
                    java.io.InputStream in = getContentResolver().openInputStream(u);
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
                    byte[] buf = new byte[8192];
                    int r;
                    while (in != null && (r = in.read(buf)) > 0) fos.write(buf, 0, r);
                    if (in != null) in.close();
                    fos.close();
                    ok++;
                } catch (Throwable ignored) {}
            }
            final int okk = ok;
            runOnUiThread(() -> {
                appendLog("已放入共享 " + okk + "/" + uris.size() + " 个文件");
                toast("已放入 " + okk + " 个文件，浏览器刷新就能看到");
            });
        }).start();
    }

    private void appendLog(String s) {
        String cur = logText.getText().toString();
        String all = s + "\n" + cur;
        if (all.length() > 1500) all = all.substring(0, 1500);
        logText.setText(all);
    }

    @Override
    protected void onDestroy() {
        if (server != null) server.stop();
        super.onDestroy();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
