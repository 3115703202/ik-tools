package com.roomarchive.server;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 黑客系统：绿色终端模拟器——开机码流、命令交互（help/hack/clear 等）。
 * 纯娱乐模拟，无真实入侵能力。
 */
public class HackerSystemActivity extends BaseActivity {

    private TextView term;
    private EditText cmdInput;
    private Handler handler = new Handler(Looper.getMainLooper());
    private StringBuilder log = new StringBuilder();
    private int bootStep = 0;
    private boolean booting = true;

    private final String[] BOOT_LINES = {
            "> 正在初始化系统内核… OK",
            "> 加载驱动模块… OK",
            "> 检测网络接口… 3 个接口在线",
            "> 解密数据流 0x7F3A… 完成",
            "> 建立加密隧道… 握手成功",
            "> 扫描目标端口 80/443/8080… 开放",
            "> 提取管理员令牌… 验证通过",
            "> 系统就绪。输入 help 查看命令",
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        startBoot();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(2, 8, 4));

        TextView title = new TextView(this);
        title.setText("HACKER SYSTEM v2.0");
        title.setTextSize(16);
        title.setTextColor(Color.rgb(0, 255, 120));
        title.setGravity(Gravity.CENTER);
        title.setTypeface(Typeface.MONOSPACE);
        root.addView(title);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(2, 8, 4));
        term = new TextView(this);
        term.setTextColor(Color.rgb(0, 255, 120));
        term.setTextSize(12);
        term.setTypeface(Typeface.MONOSPACE);
        term.setPadding(dp(12), dp(8), dp(12), dp(8));
        scroll.addView(term);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setPadding(dp(12), dp(6), dp(12), dp(6));
        inputRow.setBackgroundColor(Color.rgb(4, 14, 8));
        TextView prompt = new TextView(this);
        prompt.setText("root@ik:~# ");
        prompt.setTextColor(Color.rgb(0, 255, 120));
        prompt.setTextSize(13);
        prompt.setTypeface(Typeface.MONOSPACE);
        inputRow.addView(prompt);
        cmdInput = new EditText(this);
        cmdInput.setTextColor(Color.rgb(0, 255, 120));
        cmdInput.setTextSize(13);
        cmdInput.setTypeface(Typeface.MONOSPACE);
        cmdInput.setHint("输入命令（help）");
        cmdInput.setHintTextColor(Color.rgb(0, 120, 60));
        cmdInput.setBackgroundColor(Color.TRANSPARENT);
        cmdInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);
        inputRow.addView(cmdInput, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(inputRow);

        Button enter = new Button(this);
        enter.setText("执行");
        enter.setAllCaps(false);
        enter.setTextColor(Color.rgb(0, 255, 120));
        enter.setBackgroundColor(Color.rgb(4, 14, 8));
        root.addView(enter);
        enter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runCmd();
            }
        });

        setContentView(root);
    }

    private void startBoot() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!booting) return;
                append(BOOT_LINES[Math.min(bootStep, BOOT_LINES.length - 1)]);
                bootStep++;
                if (bootStep < BOOT_LINES.length) {
                    handler.postDelayed(this, 260);
                } else {
                    booting = false;
                }
            }
        }, 300);
    }

    private void runCmd() {
        String cmd = cmdInput.getText().toString().trim();
        cmdInput.setText("");
        if (cmd.isEmpty()) return;
        append("root@ik:~# " + cmd);
        handleCmd(cmd.toLowerCase());
    }

    private void handleCmd(String cmd) {
        if (cmd.equals("help")) {
            append("可用命令：\n"
                    + "  help        —— 显示命令帮助\n"
                    + "  hack        —— 模拟入侵动画\n"
                    + "  trace       —— 显示网络路径\n"
                    + "  scan        —— 扫描开放端口\n"
                    + "  decrypt     —— 模拟解密\n"
                    + "  matrix      —— 代码雨\n"
                    + "  whoami      —— 当前身份\n"
                    + "  clear       —— 清空屏幕\n"
                    + "  exit        —— 退出（回到首页）\n"
                    + "提示：本系统为娱乐模拟，不会连接任何真实系统。");
        } else if (cmd.equals("hack")) {
            animateCmd(new String[]{
                    "正在绕过防火墙… [==========] 100%",
                    "注入载荷 payload_0x9F… 成功",
                    "获取目标系统权限… root 已获取",
                    "解密数据库… 完成",
                    "警告：以上为模拟演示，请勿用于非法用途",
            }, 400);
        } else if (cmd.equals("trace")) {
            animateCmd(new String[]{
                    "路由追踪：",
                    "  1  192.168.1.1  1ms",
                    "  2  10.20.30.40  8ms",
                    "  3  203.0.113.10  15ms",
                    "  4  *  *  超时",
                    "  5  目标节点已隐藏",
            }, 300);
        } else if (cmd.equals("scan")) {
            animateCmd(new String[]{
                    "端口扫描：127.0.0.1",
                    "  22/tcp   开放  ssh",
                    "  80/tcp   开放  http",
                    "  443/tcp  开放  https",
                    "  3306/tcp 开放  mysql",
                    "  8080/tcp 开放  http-proxy",
            }, 300);
        } else if (cmd.equals("decrypt")) {
            animateCmd(new String[]{
                    "读取加密块… 4096 bytes",
                    "尝试密钥池… 匹配 0xE71A",
                    "解密进度 [███████░░░] 70%",
                    "解密完成 → 明文已输出（本地）",
            }, 350);
        } else if (cmd.equals("matrix")) {
            animateCmd(new String[]{
                    "01001110 01000101 01001111",
                    "00100000 01101000 01100001",
                    "11011011 10101010 01010101",
                    "⣿⣿⣷⣿⣿⣿⣷⣿⣿⣿ 崩 溃 吧 数 字 世 界",
            }, 280);
        } else if (cmd.equals("whoami")) {
            append("身份：root\n主机：ik-toolbox\n系统：HACKER_OS 2.0 (模拟)\n权限：完整（仅限本机娱乐）");
        } else if (cmd.equals("clear")) {
            log.setLength(0);
            term.setText("");
        } else if (cmd.equals("exit")) {
            finish();
        } else if (cmd.startsWith("ping ")) {
            append("PING " + cmd.substring(5) + " — 64 bytes 响应 8ms（模拟）");
        } else {
            append("command not found: " + cmd + "\n输入 help 查看可用命令");
        }
    }

    private void animateCmd(final String[] lines, final int delay) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                append(lines[0]);
                // 其余逐行延迟
                for (int i = 1; i < lines.length; i++) {
                    final String line = lines[i];
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            append(line);
                        }
                    }, delay * i);
                }
            }
        }, 150);
    }

    private void append(String s) {
        log.append(s).append("\n");
        term.setText(log.toString());
        // 自动滚动到底部
        term.post(new Runnable() {
            @Override
            public void run() {
                ((ScrollView) term.getParent()).fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
