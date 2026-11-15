package com.roomarchive.server;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** 本地小AI：llama.cpp + Qwen2.5-0.5B GGUF，完全离线推理 */
public class LocalAiActivity extends BaseActivity {

    static final String MODEL_NAME = "qwen2.5-0.5b-instruct-q4_k_m.gguf";
    static final long MODEL_SIZE = 491400032L;
    static final String URL_HF =
            "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/" + MODEL_NAME;
    static final String URL_MIRROR =
            "https://hf-mirror.com/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/" + MODEL_NAME;

    private TextView stateText, logText, sendBtn, dlBtn, a11yState;
    private ProgressBar bar;
    private EditText input;
    private LinearLayout chatBox;
    private ScrollView scroll;
    private Handler ui = new Handler(Looper.getMainLooper());
    private Process proc;
    private volatile boolean downloading = false;
    private boolean busy = false;

    private File modelFile() {
        return new File(new File(FileStore.outDir(this), "models"), MODEL_NAME);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        final android.widget.FrameLayout host = new android.widget.FrameLayout(this);
        host.setBackground(Tools.bgWithGlow(this));
        setContentView(host);
        try {
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Tools.bgWithGlow(this));
        root.setPadding(dp(16), dp(22), dp(16), dp(80));
        sv.addView(root);

        TextView title = Tools.pageTitle(this, "本地 AI · 离线大模型");
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("v4.5.27 · llama.cpp 本地推理 · 不联网 · 数据不出手机");
        sub.setTextSize(12);
        sub.setTextColor(Tools.subTextColor(this));
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(6), 0, dp(14));
        root.addView(sub);

        LinearLayout capCard = new LinearLayout(this);
        capCard.setOrientation(LinearLayout.VERTICAL);
        capCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        capCard.setBackground(Tools.liquidGlassCard(this, 20));
        Tools.setElevation(capCard, 8);
        root.addView(capCard);

        a11yState = new TextView(this);
        a11yState.setTextSize(12);
        a11yState.setTextColor(Tools.subTextColor(this));
        capCard.addView(a11yState);

        LinearLayout capRow = new LinearLayout(this);
        capRow.setOrientation(LinearLayout.HORIZONTAL);
        capRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams crl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        crl.topMargin = dp(10);
        capCard.addView(capRow, crl);
        chip(capRow, "权限中心", v -> startActivity(new Intent(this, AiPermissionActivity.class)));
        chip(capRow, "读屏看看", v -> {
            if (!AiAgentService.ready()) { toast("先去权限中心开无障碍"); return; }
            bubble(AiAgentService.get().screenText(), false);
        });
        chip(capRow, "文件实验室", v -> startActivity(new Intent(this, MediaLabActivity.class)));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(Tools.liquidGlassCard(this, 20));
        Tools.setElevation(card, 8);
        root.addView(card);

        stateText = new TextView(this);
        stateText.setTextSize(13);
        stateText.setTextColor(Tools.subTextColor(this));
        card.addView(stateText);

        dlBtn = new TextView(this);
        dlBtn.setText("下载模型");
        dlBtn.setGravity(Gravity.CENTER);
        dlBtn.setTextSize(14);
        dlBtn.setTextColor(0xFFFFFFFF);
        dlBtn.setBackground(Tools.pillButton(this, 44, true));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        lp.topMargin = dp(12);
        card.addView(dlBtn, lp);
        dlBtn.setOnClickListener(v -> startDownload());

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setVisibility(View.GONE);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(6));
        pl.topMargin = dp(10);
        card.addView(bar, pl);

        logText = new TextView(this);
        logText.setTextSize(11);
        logText.setTextColor(Tools.subTextColor(this));
        logText.setPadding(0, dp(8), 0, 0);
        logText.setText("提示：首次需下载 491MB 模型，存到 Download/IK工具箱/models，之后完全离线");
        card.addView(logText);

        chatBox = new LinearLayout(this);
        chatBox.setOrientation(LinearLayout.VERTICAL);
        chatBox.setPadding(0, dp(14), 0, 0);
        root.addView(chatBox);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setBackground(Tools.liquidGlassCard(this, 26));
        inputRow.setPadding(dp(14), dp(6), dp(6), dp(6));
        LinearLayout.LayoutParams irl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        irl.topMargin = dp(14);
        root.addView(inputRow, irl);

        input = new EditText(this);
        input.setHint("问点什么…（完全离线回答）");
        input.setTextSize(14);
        input.setBackground(null);
        input.setTextColor(Tools.textColor(this));
        input.setHintTextColor(Tools.subTextColor(this));
        inputRow.addView(input, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        sendBtn = new TextView(this);
        sendBtn.setText("发送");
        sendBtn.setGravity(Gravity.CENTER);
        sendBtn.setTextSize(13);
        sendBtn.setTextColor(0xFFFFFFFF);
        sendBtn.setBackground(Tools.pillButton(this, 36, true));
        inputRow.addView(sendBtn, new LinearLayout.LayoutParams(dp(64), dp(36)));
        sendBtn.setOnClickListener(v -> send());

        scroll = sv;
        sv.setFillViewport(true);
        host.addView(sv, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        refreshState();
        } catch (Throwable t) {
            TextView err = new TextView(this);
            err.setText("本地 AI 页面加载失败：" + t + "\n\n请截图这段文字发给开发者");
            err.setTextSize(14);
            err.setTextColor(0xFFDC2626);
            err.setPadding(dp(20), dp(40), dp(20), dp(20));
            host.addView(err, new android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));
        }
    }

    void chip(LinearLayout row, String label, android.view.View.OnClickListener c) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(12);
        t.setTextColor(Tools.accentColor(this));
        t.setBackground(Tools.pillButton(this, 34, false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(34), 1);
        lp.leftMargin = dp(4);
        lp.rightMargin = dp(4);
        row.addView(t, lp);
        t.setOnClickListener(c);
    }

    void refreshState() {
        if (a11yState != null) {
            a11yState.setText(AiAgentService.ready()
                    ? "● 操控权限已开：能读屏、点击、滑动、开应用"
                    : "○ 操控权限未开：只能聊天，点「权限中心」给 AI 授权");
            a11yState.setTextColor(AiAgentService.ready() ? 0xFF16A34A : 0xFFEF4444);
        }
        File m = modelFile();
        if (!isArm64()) {
            stateText.setText("● 当前设备架构不支持本地模型（需 arm64）");
            dlBtn.setVisibility(View.GONE);
            return;
        }
        if (m.exists() && m.length() > 100_000_000L) {
            stateText.setText("● 模型就绪 · " + String.format("%.0f MB", m.length() / 1048576.0));
            dlBtn.setText("重新下载模型");
        } else {
            stateText.setText("● 模型未下载（需 491MB）");
            dlBtn.setText("下载模型");
        }
    }

    boolean isArm64() {
        for (String abi : Build.SUPPORTED_ABIS) if (abi.contains("arm64")) return true;
        return false;
    }

    void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    void startDownload() {
        if (downloading) { toast("正在下载，别急"); return; }
        downloading = true;
        bar.setVisibility(View.VISIBLE);
        bar.setProgress(0);
        dlBtn.setEnabled(false);
        new Thread(() -> {
            File dir = modelFile().getParentFile();
            if (!dir.exists()) dir.mkdirs();
            File tmp = new File(dir, MODEL_NAME + ".part");
            long have = tmp.exists() ? tmp.length() : 0;
            boolean ok = false;
            for (String urlStr : new String[]{URL_HF, URL_MIRROR}) {
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(20000);
                    c.setRequestProperty("User-Agent", "IK-Tools");
                    if (have > 0) c.setRequestProperty("Range", "bytes=" + have + "-");
                    int code = c.getResponseCode();
                    if (code != 200 && code != 206) throw new java.io.IOException("HTTP " + code);
                    long total = c.getContentLengthLong();
                    if (total > 0) total += have;
                    InputStream is = new BufferedInputStream(c.getInputStream(), 8192);
                    OutputStream os = new FileOutputStream(tmp, have > 0);
                    byte[] buf = new byte[8192];
                    int r;
                    long got = have;
                    long last = 0;
                    while ((r = is.read(buf)) > 0) {
                        os.write(buf, 0, r);
                        got += r;
                        if (System.currentTimeMillis() - last > 300) {
                            last = System.currentTimeMillis();
                            final long g2 = got, t2 = total;
                            ui.post(() -> {
                                if (t2 > 0) {
                                    bar.setProgress((int) (g2 * 1000 / t2));
                                    logText.setText(String.format("下载中 %.0f / %.0f MB",
                                            g2 / 1048576.0, t2 / 1048576.0));
                                }
                            });
                        }
                    }
                    os.flush(); os.close(); is.close();
                    if (tmp.length() < 100_000_000L) throw new java.io.IOException("文件不完整");
                    File dest = modelFile();
                    if (dest.exists()) dest.delete();
                    if (!tmp.renameTo(dest)) throw new java.io.IOException("保存失败");
                    ok = true;
                    break;
                } catch (Throwable e) {
                    have = tmp.exists() ? tmp.length() : 0;
                    final String msg = e.toString();
                    ui.post(() -> logText.setText("下载出错，换源重试：" + msg));
                }
            }
            final boolean fOk = ok;
            ui.post(() -> {
                downloading = false;
                dlBtn.setEnabled(true);
                bar.setVisibility(View.GONE);
                if (fOk) {
                    toast("模型下载完成");
                    logText.setText("模型已就绪，可以离线对话了");
                } else {
                    logText.setText("下载失败：网络或镜像不可用，可稍后重试（支持断点续传）");
                }
                refreshState();
            });
        }).start();
    }

    void runCommands(String text) {
        if (!AiAgentService.ready() || text == null) return;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "[\\[【](读屏|点击|滑动|返回|主页|打开|页面)[:：]?([^\\]】]*)[\\]】]").matcher(text);
        java.util.List<String[]> cmds = new java.util.ArrayList<>();
        while (m.find()) {
            String cmd = m.group(1);
            String arg = m.group(2) == null ? "" : m.group(2).trim();
            cmds.add(new String[]{cmd, arg});
        }
        if (cmds.isEmpty()) return;
        Handler h = new Handler(Looper.getMainLooper());
        for (int i = 0; i < cmds.size(); i++) {
            final String[] c = cmds.get(i);
            h.postDelayed(() -> exec(c[0], c[1]), i * 900L);
        }
    }

    void exec(String cmd, String arg) {
        AiAgentService svc = AiAgentService.get();
        if (svc == null) return;
        boolean ok = false;
        String label = cmd + (arg.isEmpty() ? "" : " " + arg);
        switch (cmd) {
            case "读屏":
                bubble(svc.screenText(), false);
                return;
            case "点击": ok = svc.clickText(arg); break;
            case "滑动": ok = svc.swipe(arg.isEmpty() ? "上" : arg); break;
            case "返回": ok = svc.back(); break;
            case "主页": ok = svc.home(); break;
            case "打开": ok = svc.openApp(arg); break;
            case "页面": ok = openPage(arg); break;
        }
        bubble((ok ? "✓ 已执行 " : "× 执行失败 ") + label, false);
    }

    boolean openPage(String name) {
        try {
            Class<?> c;
            if (name.contains("文件") || name.contains("图片") || name.contains("视频")) c = MediaLabActivity.class;
            else if (name.contains("剪辑") || name.contains("裁剪")) c = VideoTrimActivity.class;
            else if (name.contains("音频")) c = AudioTrimActivity.class;
            else if (name.contains("长截图") || name.contains("截图")) c = LongShotActivity.class;
            else if (name.contains("网盘")) c = NetDiskActivity.class;
            else if (name.contains("金属")) c = MetalDetectorActivity.class;
            else if (name.contains("识别")) c = ObjectDetectActivity.class;
            else if (name.contains("权限")) c = AiPermissionActivity.class;
            else return false;
            startActivity(new Intent(this, c));
            return true;
        } catch (Throwable t) { return false; }
    }

    void bubble(String text, boolean me) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(14);
        t.setTextIsSelectable(true);
        t.setPadding(dp(14), dp(10), dp(14), dp(10));
        t.setTextColor(me ? 0xFFFFFFFF : Tools.textColor(this));
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setCornerRadius(dp(16));
        g.setColor(me ? Tools.accentColor(this) : 0x33FFFFFF);
        t.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = me ? Gravity.END : Gravity.START;
        lp.topMargin = dp(8);
        chatBox.addView(t, lp);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    void send() {
        String q = input.getText().toString().trim();
        if (q.isEmpty()) return;
        if (busy) { toast("上一条还在生成"); return; }
        if (!modelFile().exists()) { toast("先下载模型"); return; }
        input.setText("");
        bubble(q, true);
        busy = true;
        final TextView wait = new TextView(this);
        wait.setText("思考中…");
        wait.setTextSize(12);
        wait.setTextColor(Tools.subTextColor(this));
        wait.setPadding(dp(6), dp(6), 0, 0);
        chatBox.addView(wait);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        new Thread(() -> runLlama(q, wait)).start();
    }

    void runLlama(String q, TextView wait) {
        String answer;
        try {
            File bin = new File(getApplicationInfo().nativeLibraryDir, "libllama-cli.so");
            if (!bin.exists() || bin.length() < 1_000_000L) {
                bin = new File(getFilesDir(), "llama-cli");
                if (!bin.exists() || bin.length() < 1_000_000L) extractBinary(bin);
            }
            String prompt = "<|im_start|>system\n你是IK工具箱内置的本地AI，用简体中文回答，简洁直接，不啰嗦。你可以操控这台手机，可用命令：[读屏] [点击:文字] [滑动:上] [返回] [主页] [打开:包名] [页面:文件实验室]。需要动手时在回答最后单独一行写命令，不要解释命令。<|im_end|>\n"
                    + "<|im_start|>user\n" + q + "<|im_end|>\n<|im_start|>assistant\n";
            proc = new ProcessBuilder(bin.getAbsolutePath(),
                    "-m", modelFile().getAbsolutePath(),
                    "-p", prompt,
                    "-n", "220", "-c", "2048", "-t", "4",
                    "--temp", "0.7", "--top-p", "0.9",
                    "--no-display-prompt", "--no-warmup", "--no-conversation")
                    .redirectErrorStream(true).start();
            BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            long deadline = System.currentTimeMillis() + 180000;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
                if (System.currentTimeMillis() > deadline) { proc.destroy(); break; }
            }
            int code = proc.waitFor();
            String raw = sb.toString();
            // 去掉可能的回显，只取 assistant 之后的内容
            int idx = raw.indexOf("<|im_start|>assistant");
            if (idx >= 0) raw = raw.substring(idx + 23);
            raw = raw.replace("<|im_end|>", "").replace("<|endoftext|>", "");
            // 去掉 llama.cpp 的统计行
            String[] lines = raw.split("\n");
            StringBuilder out = new StringBuilder();
            for (String l : lines) {
                if (l.startsWith("llama_") || l.contains("llama_perf") || l.startsWith("load time")
                        || l.startsWith("sample time") || l.startsWith(">>")) continue;
                out.append(l).append('\n');
            }
            answer = out.toString().trim();
            if (answer.isEmpty()) answer = "模型没有输出（exit=" + code + "）\n原始：" +
                    raw.substring(0, Math.min(300, raw.length()));
        } catch (Throwable e) {
            answer = "本地推理失败：" + e;
        }
        final String a = answer;
        ui.post(() -> {
            chatBox.removeView(wait);
            bubble(a, false);
            busy = false;
            runCommands(a);
        });
    }

    void extractBinary(File dest) throws Exception {
        InputStream is = getAssets().open("llama-cli");
        OutputStream os = new FileOutputStream(dest);
        byte[] buf = new byte[8192];
        int r;
        while ((r = is.read(buf)) > 0) os.write(buf, 0, r);
        os.close(); is.close();
        dest.setExecutable(true, false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (proc != null) proc.destroy();
    }

    int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
