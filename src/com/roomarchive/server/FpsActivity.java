/** 3D 枪战 v3：OpenGL ES 真 3D 体素世界 + 真实素材（枪械 PNG/士兵人像/火花）+ 子弹弹道 + 性能优化 */
package com.roomarchive.server;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.net.wifi.WifiManager;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class FpsActivity extends Activity {

    private FrameLayout root;
    private LinearLayout menu;
    private GLSurfaceView glv;
    private HudView hud;
    private FpsEngine engine = new FpsEngine();
    private GLRenderer renderer;
    private Handler ui = new Handler(Looper.getMainLooper());
    private Runnable frameReq;

    private boolean singleMode = true;
    private boolean gameOn = false;

    private float joyCX = -1, joyCY = -1, joyDX = 0, joyDY = 0;
    private float lookLastX = -1, lookLastY = -1;   // 视角滑动上一次位置
    private boolean joyActive = false;
    private long lastT = 0;
    private volatile boolean dead = false;
    private long deadUntil = 0;

    private NetServer server;
    private NetClient client;
    private String myName = "玩家" + (int) (Math.random() * 900 + 100);
    private int myId = -1;
    private Map<Integer, FpsEngine.Sprite> remote = new HashMap<>();

    private SoundFx sfx;
    private float redFlash = 0;      // 受击红屏
    private float crossFlash = 0;    // 命中红叉
    private float bobPhase = 0;      // 持枪摆动相位
    private boolean moving = false;  // 是否在移动（准星扩散用）
    private int lastHp = 100;
    private float stepTimer = 0;
    private int wave = 1;
    private float waveDelay = 0;
    private float waveMsg = 0;   // 中央"第 N 波"提示
    private float killMsg = 0;   // 中央"击杀"提示

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 沉浸式全屏（FPS 标准体验）
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF10131A);
        setContentView(root);
        sfx = new SoundFx();
        showMenu();
    }

    private void showMenu() {
        menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setGravity(Gravity.CENTER);

        TextView title = new TextView(this);
        title.setText("3D 枪战");
        title.setTextSize(30);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        menu.addView(title, lp());

        TextView sub = new TextView(this);
        sub.setText("真 3D 体素竞技场 · 真实枪械 · 局域网对战");
        sub.setTextSize(13);
        sub.setTextColor(0xCCFFFFFF);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(6), 0, dp(30));
        menu.addView(sub, lp());

        menu.addView(menuBtn("单人模式", 0xFF2563EB, new Runnable() {
            @Override
            public void run() {
                singleMode = true;
                startGame();
            }
        }));
        menu.addView(menuBtn("创建房间（局域网对战）", 0xFF059669, new Runnable() {
            @Override
            public void run() {
                singleMode = false;
                startServer();
            }
        }));
        menu.addView(menuBtn("加入房间", 0xFF7C3AED, new Runnable() {
            @Override
            public void run() {
                singleMode = false;
                promptJoin();
            }
        }));

        TextView tip = new TextView(this);
        tip.setText("操作：左下摇杆移动/转向 · 右下射击\n局域网对战需两台手机连接同一 WiFi");
        tip.setTextSize(12);
        tip.setTextColor(0x99FFFFFF);
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(0, dp(30), 0, 0);
        menu.addView(tip, lp());

        root.removeAllViews();
        root.addView(menu);
    }

    private LinearLayout.LayoutParams lp() {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        l.topMargin = dp(8);
        l.leftMargin = dp(40);
        l.rightMargin = dp(40);
        return l;
    }

    private TextView menuBtn(String text, int color, final Runnable r) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextSize(17);
        b.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{color, Color.rgb((int) (Color.red(color) * 0.7f), (int) (Color.green(color) * 0.7f), (int) (Color.blue(color) * 0.7f))});
        g.setCornerRadius(dp(22));
        b.setPadding(0, dp(15), 0, dp(15));
        b.setClickable(true);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                r.run();
            }
        });
        return b;
    }

    // ================= 服务器 / 加入 =================

    private void startServer() {
        server = new NetServer();
        server.start();
        String ip = localIp();
        new AlertDialog.Builder(this)
                .setTitle("房间已创建")
                .setMessage("让好友在「加入房间」里输入你的局域网 IP：\n\n" + ip + "\n\n端口：25001\n\n（两台手机需连接同一 WiFi，对方加入后点开始）")
                .setPositiveButton("开始游戏", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        myId = 0;
                        startGame();
                    }
                })
                .setNegativeButton("返回", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        if (server != null) server.stop();
                        server = null;
                    }
                })
                .show();
    }

    private String localIp() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            int ip = wm.getConnectionInfo().getIpAddress();
            return (ip & 0xFF) + "." + ((ip >> 8) & 0xFF) + "." + ((ip >> 16) & 0xFF) + "." + ((ip >> 24) & 0xFF);
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    private void promptJoin() {
        final EditText et = new EditText(this);
        et.setHint("输入房主的局域网 IP（如 192.168.1.5）");
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        et.setTextSize(15);
        new AlertDialog.Builder(this)
                .setTitle("加入房间")
                .setView(et)
                .setPositiveButton("加入", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        String ip = et.getText().toString().trim();
                        if (ip.isEmpty()) return;
                        client = new NetClient(ip);
                        if (!client.connect()) {
                            toast("连接失败，请检查 IP 与网络");
                            return;
                        }
                        myId = client.myId;
                        myName = client.name;
                        startGame();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ================= 游戏视图 =================

    private void startGame() {
        engine.genWorld();
        engine.resetLocal();
        engine.bullets.clear();
        if (singleMode) {
            engine.resetSprites(true);
        } else {
            engine.resetSprites(false);
            if (server != null) server.clear();
            if (client != null) client.clear();
        }
        remote.clear();
        myId = singleMode ? 0 : (server != null ? 0 : (client != null ? client.myId : 0));
        dead = false;
        deadUntil = 0;
        gameOn = true;
        wave = 1;
        waveDelay = 0;

        glv = new GLSurfaceView(this);
        glv.setEGLContextClientVersion(2);
        renderer = new GLRenderer();
        glv.setRenderer(renderer);
        glv.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        // 40fps 定时渲染（省电）
        frameReq = new Runnable() {
            @Override
            public void run() {
                if (gameOn && glv != null) {
                    glv.requestRender();
                    ui.postDelayed(this, 25);
                }
            }
        };
        ui.postDelayed(frameReq, 25);

        hud = new HudView(this);
        hud.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.removeAllViews();
        root.addView(glv);
        root.addView(hud);
        lastT = SystemClock.uptimeMillis();
    }

    private void doFire() {
        if (engine.hp <= 0) return;
        FpsEngine.Sprite hit = engine.fire();
        if (engine.ammo <= 0) {
            if (sfx != null) sfx.playDry();   // 空仓咔哒
            if (engine.reloadTime <= 0) {
                engine.startReload();
                if (sfx != null) sfx.playReload();
            }
        }
        crossFlash = 0.3f;
        if (hit != null || engine.reloadTime > 0) {
            // 有命中或换弹中：不额外播墙音
        }
        if (sfx != null && engine.reloadTime <= 0) {
            if (hit != null && hit.dead) {
                sfx.playKill();
                killMsg = 0.9f;
            } else if (hit != null) sfx.playHit();
            else sfx.playWall();
            sfx.playShoot();
        }
        if (!singleMode) {
            if (hit != null && hit.isPlayer && hit.dead) {
                if (server != null) server.broadcast("D:" + hit.id);
                if (client != null) client.send("D:" + hit.id);
            } else {
                if (server != null) server.broadcast("S:" + myId);
                if (client != null) client.send("S:" + myId);
            }
        }
    }

    private void onRemoteDead(int id) {
        if (id == myId) {
            engine.hp = 0;
            return;
        }
        if (sfx != null) sfx.playHit();
        killMsg = 0.9f;
        FpsEngine.Sprite s = remote.get(id);
        if (s != null) {
            s.hp = 0;
            s.dead = true;
            if (id != myId) engine.kills++;
            ui.postDelayed(new Runnable() {
                @Override
                public void run() {
                    FpsEngine.Sprite ss = remote.get(id);
                    if (ss != null) {
                        ss.hp = 100;
                        ss.dead = false;
                        ss.x = 2.5f;
                        ss.z = 12.5f;
                    }
                }
            }, 3000);
        }
    }

    private void onShot(int id) {
        // 联机：收到对方开枪（远处枪声）
        if (sfx != null && id != myId) sfx.playWall();
    }

    private void tick(float dt) {
        if (engine.hp <= 0) {
            dead = true;
            if (deadUntil == 0) deadUntil = SystemClock.uptimeMillis() + 3000;
            if (SystemClock.uptimeMillis() >= deadUntil) {
                if (singleMode) {
                    dead = false;
                    deadUntil = 0;
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            onSingleDeath();
                        }
                    });
                } else {
                    engine.resetLocal();
                    dead = false;
                    deadUntil = 0;
                }
            }
            return;
        }
        float fwd = -joyDY / dp(46);
        float turn = joyDX / dp(46);
        engine.move(fwd, turn, dt);
        // 受击反馈
        if (engine.hp < lastHp && engine.hp > 0) {
            redFlash = 0.55f;
            if (sfx != null) sfx.playHurt();
        }
        lastHp = engine.hp;
        // 脚步 + 持枪摆动
        moving = Math.abs(fwd) > 0.02f;
        if (Math.abs(fwd) > 0.02f) {
            bobPhase += dt * 9.0f;
            stepTimer -= dt;
            if (stepTimer <= 0) {
                stepTimer = 0.4f;
                if (sfx != null) sfx.playStep();
            }
        }
        if (redFlash > 0) redFlash -= dt;
        if (crossFlash > 0) crossFlash -= dt;
        if (singleMode) {
            engine.updateAi(dt);
            // 波次推进：清完一波刷下一波（更强）
            if (waveDelay > 0) waveDelay -= dt;
            if (waveDelay <= 0 && engine.allEnemiesDead() && !dead && engine.hp > 0) {
                wave++;
                int n = Math.min(7, 3 + wave);
                engine.spawnWave(n);
                waveDelay = 1.2f;
                waveMsg = 1.6f;
            }
        }
        engine.updateBullets(dt);
        engine.recoil *= 0.85f;
        engine.updatePitch(dt);
        if (waveMsg > 0) waveMsg -= dt;
        if (killMsg > 0) killMsg -= dt;
        if (!singleMode) {
            long now = SystemClock.uptimeMillis();
            if (now % 100 < 20) {
                String msg = "P:" + engine.px + ":" + engine.pz + ":" + engine.pa + ":" + engine.hp;
                if (server != null) server.broadcast("P" + myId + ":" + msg);
                if (client != null) client.send("P" + myId + ":" + msg);
            }
        }
    }

    private void onSingleDeath() {
        new AlertDialog.Builder(this)
                .setTitle("你被击败了")
                .setMessage("击杀数：" + engine.kills + "\n\n要再来一局吗？")
                .setPositiveButton("再来一局", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        engine.resetLocal();
                        engine.resetSprites(true);
                        engine.bullets.clear();
                        dead = false;
                        deadUntil = 0;
                        wave = 1;
                        waveDelay = 0;
                    }
                })
                .setNegativeButton("返回菜单", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        stopGame();
                        showMenu();
                    }
                })
                .show();
    }

    private void stopGame() {
        gameOn = false;
        if (frameReq != null) ui.removeCallbacks(frameReq);
        if (glv != null) {
            glv.onPause();
            glv = null;
        }
        if (server != null) {
            server.stop();
            server = null;
        }
        if (client != null) {
            client.stop();
            client = null;
        }
        remote.clear();
        hud = null;
    }

    @Override
    protected void onPause() {
        super.onPause();
        gameOn = false;
        if (frameReq != null) ui.removeCallbacks(frameReq);
        if (glv != null) glv.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        if (gameOn && glv != null) {
            glv.onResume();
            if (frameReq != null) ui.postDelayed(frameReq, 25);
        }
    }

    @Override
    protected void onDestroy() {
        stopGame();
        if (sfx != null) sfx.release();
        super.onDestroy();
    }

    /** 程序合成音效（零素材依赖）：枪声/命中/击杀/受伤/脚步/打墙 */
    class SoundFx {
        static final int SR = 44100;
        AudioTrack[] shoot = new AudioTrack[4];
        AudioTrack[] hit = new AudioTrack[2];
        AudioTrack[] kill = new AudioTrack[2];
        AudioTrack hurt, step, wall, reload, dry;
        int[] si = new int[4], hi = new int[2], ki = new int[2];
        Random rr = new Random();

        SoundFx() {
            shoot[0] = mk(genShoot(), 4); shoot[1] = mk(genShoot(), 4); shoot[2] = mk(genShoot(), 4); shoot[3] = mk(genShoot(), 4);
            hit[0] = mk(genHit(), 2); hit[1] = mk(genHit(), 2);
            kill[0] = mk(genKill(), 2); kill[1] = mk(genKill(), 2);
            hurt = mk(genHurt(), 1);
            step = mk(genStep(), 1);
            wall = mk(genWall(), 1);
            reload = mk(genReload(), 1);
            dry = mk(genDry(), 1);
        }

        AudioTrack mk(short[] data, int mode) {
            AudioTrack t = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(SR)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(data.length * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
            t.write(data, 0, data.length);
            return t;
        }

        short[] genShoot() {
            int n = SR / 20;   // 50ms 枪声
            short[] d = new short[n];
            for (int i = 0; i < n; i++) {
                double env = Math.exp(-i / (double) (n / 3.2));
                d[i] = (short) ((rr.nextInt(20001) - 10000) * 0.9 * env);
            }
            return d;
        }

        short[] genHit() {
            int n = SR / 14;   // 70ms 命中音（高频 ping）
            short[] d = new short[n];
            for (int i = 0; i < n; i++) {
                double env = Math.exp(-i / (double) (n / 2.5));
                d[i] = (short) (Math.sin(2 * Math.PI * 1500 * i / SR) * 12000 * env);
            }
            return d;
        }

        short[] genKill() {
            int n = SR / 5;    // 200ms 击杀音（低频下滑 + 噪声）
            short[] d = new short[n];
            for (int i = 0; i < n; i++) {
                double env = Math.exp(-i / (double) (n / 2.2));
                double f = 300 - 120.0 * i / n;
                d[i] = (short) ((Math.sin(2 * Math.PI * f * i / SR) * 9000 + (rr.nextInt(4001) - 2000) * 0.4) * env);
            }
            return d;
        }

        short[] genHurt() {
            int n = SR / 6;    // 160ms 受伤闷响
            short[] d = new short[n];
            for (int i = 0; i < n; i++) {
                double env = Math.exp(-i / (double) (n / 2.0));
                d[i] = (short) (Math.sin(2 * Math.PI * 120 * i / SR) * 11000 * env);
            }
            return d;
        }

        short[] genStep() {
            int n = SR / 25;   // 40ms 脚步
            short[] d = new short[n];
            for (int i = 0; i < n; i++) {
                double env = Math.exp(-i / (double) (n / 2.8));
                d[i] = (short) (Math.sin(2 * Math.PI * 70 * i / SR) * 8000 * env);
            }
            return d;
        }

        short[] genWall() {
            int n = SR / 18;   // 55ms 打墙闷响
            short[] d = new short[n];
            for (int i = 0; i < n; i++) {
                double env = Math.exp(-i / (double) (n / 2.4));
                d[i] = (short) ((rr.nextInt(9001) - 4500) * 0.8 * env);
            }
            return d;
        }

        short[] genReload() {
            int n = SR / 4;    // 250ms 换弹机械声（两段咔嗒）
            short[] d = new short[n];
            int click1 = SR / 12, click2 = SR / 6;
            for (int i = 0; i < n; i++) {
                double env = Math.exp(-i / (double) (n / 4));
                double v = 0;
                if (i >= click1 && i < click1 + 600) v = (rr.nextInt(12001) - 6000) * 0.9;
                if (i >= click2 && i < click2 + 500) v = (rr.nextInt(10001) - 5000) * 0.8;
                d[i] = (short) (v * env);
            }
            return d;
        }

        short[] genDry() {
            int n = SR / 16;   // 60ms 空仓咔哒
            short[] d = new short[n];
            for (int i = 0; i < n; i++) {
                double env = Math.exp(-i / (double) (n / 2.2));
                d[i] = (short) ((rr.nextInt(7001) - 3500) * 0.7 * env);
            }
            return d;
        }

        void fire(AudioTrack t) {
            try {
                if (t.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) t.pause();
                t.reloadStaticData();
                t.setPlaybackHeadPosition(0);
                t.play();
            } catch (Exception ignored) {
            }
        }

        void playShoot() {
            int i = si[0]++ % 4;
            fire(shoot[i]);
        }

        void playHit() {
            int i = hi[0]++ % 2;
            fire(hit[i]);
        }

        void playKill() {
            int i = ki[0]++ % 2;
            fire(kill[i]);
        }

        void playHurt() {
            fire(hurt);
        }

        void playStep() {
            fire(step);
        }

        void playWall() {
            fire(wall);
        }

        void playReload() {
            fire(reload);
        }

        void playDry() {
            fire(dry);
        }

        void release() {
            for (AudioTrack t : shoot) if (t != null) t.release();
            for (AudioTrack t : hit) if (t != null) t.release();
            for (AudioTrack t : kill) if (t != null) t.release();
            if (hurt != null) hurt.release();
            if (step != null) step.release();
            if (wall != null) wall.release();
            if (reload != null) reload.release();
            if (dry != null) dry.release();
        }
    }

    private void toast(final String s) {
        ui.post(new Runnable() {
            @Override
            public void run() {
                android.widget.Toast.makeText(FpsActivity.this, s, android.widget.Toast.LENGTH_LONG).show();
            }
        });
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    // ================= HUD 覆盖层（真实枪械 PNG + 准星 + 状态） =================

    class HudView extends View {
        Paint p = new Paint();
        TextView hpText, killText, netText, waveText;

        public HudView(Context c) {
            super(c);
            setWillNotDraw(false);

            hpText = new TextView(c);
            hpText.setTextColor(Color.WHITE);
            hpText.setTextSize(14);
            hpText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            hpText.setPadding(dp(12), dp(10), dp(12), dp(6));
            FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hlp.gravity = Gravity.TOP | Gravity.LEFT;
            FpsActivity.this.root.addView(hpText, hlp);

            killText = new TextView(c);
            killText.setTextColor(Color.rgb(255, 210, 80));
            killText.setTextSize(14);
            killText.setPadding(dp(12), dp(30), dp(12), dp(6));
            FrameLayout.LayoutParams klp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            klp.gravity = Gravity.TOP | Gravity.LEFT;
            FpsActivity.this.root.addView(killText, klp);

            netText = new TextView(c);
            netText.setTextColor(0xBBFFFFFF);
            netText.setTextSize(11);
            netText.setPadding(dp(12), dp(52), dp(12), dp(6));
            FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.gravity = Gravity.TOP | Gravity.LEFT;
            FpsActivity.this.root.addView(netText, nlp);

            waveText = new TextView(c);
            waveText.setTextColor(0xFFFFC107);
            waveText.setTextSize(13);
            waveText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            waveText.setPadding(dp(12), dp(74), dp(12), dp(6));
            FrameLayout.LayoutParams wlp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            wlp.gravity = Gravity.TOP | Gravity.LEFT;
            FpsActivity.this.root.addView(waveText, wlp);

            TextView exit = new TextView(c);
            exit.setText("退出");
            exit.setTextColor(Color.WHITE);
            exit.setTextSize(13);
            exit.setPadding(dp(12), dp(10), dp(12), dp(8));
            exit.setBackgroundColor(0x66000000);
            exit.setClickable(true);
            exit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    stopGame();
                    showMenu();
                }
            });
            FrameLayout.LayoutParams elp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            elp.gravity = Gravity.TOP | Gravity.RIGHT;
            FpsActivity.this.root.addView(exit, elp);
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = c.getWidth(), h = c.getHeight();
            p.reset();
            // 准星（命中时变红叉，移动/连射时扩散）
            float moveSpread = moving ? 5f : 0f;
            float spread = 8.5f + engine.recoil * 20f + moveSpread
                    + Math.abs((float) Math.sin(bobPhase)) * 3.5f;
            int crossColor = crossFlash > 0 ? 0xFFE53935 : 0xCCFFFFFF;
            p.setColor(crossColor);
            p.setStrokeWidth(crossFlash > 0 ? 4 : 3);
            p.setStyle(Paint.Style.STROKE);
            c.drawCircle(w / 2f, h / 2f, spread, p);
            c.drawLine(w / 2f - spread - 4, h / 2f, w / 2f - spread + 4, h / 2f, p);
            c.drawLine(w / 2f + spread - 4, h / 2f, w / 2f + spread + 4, h / 2f, p);
            c.drawLine(w / 2f, h / 2f - spread - 4, w / 2f, h / 2f - spread + 4, p);
            c.drawLine(w / 2f, h / 2f + spread - 4, w / 2f, h / 2f + spread + 4, p);
            if (crossFlash > 0) {
                p.setStrokeWidth(2);
                c.drawLine(w / 2f - spread - 8, h / 2f - spread - 8, w / 2f + spread + 8, h / 2f + spread + 8, p);
                c.drawLine(w / 2f - spread - 8, h / 2f + spread + 8, w / 2f + spread + 8, h / 2f - spread - 8, p);
            }

            // 换弹按钮（右下角射击区上方）
            float btnCx = w - dp(70), btnCy = h - dp(230);
            p.setStyle(Paint.Style.STROKE);
            p.setColor(0x66FFFFFF);
            p.setStrokeWidth(2);
            c.drawCircle(btnCx, btnCy, dp(26), p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x33FFFFFF);
            c.drawCircle(btnCx, btnCy, dp(26), p);
            p.setColor(0xEEFFFFFF);
            p.setTextSize(12);
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText("换弹", btnCx, btnCy + 4, p);
            p.setTextAlign(Paint.Align.LEFT);
            // 换弹中状态
            if (engine.reloadTime > 0) {
                float frac = 1f - engine.reloadTime / 1.25f;
                p.setStyle(Paint.Style.STROKE);
                p.setColor(0xFF4CAF50);
                p.setStrokeWidth(3);
                c.drawArc(btnCx - dp(26), btnCy - dp(26), btnCx + dp(26), btnCy + dp(26),
                        -90, frac * 360, false, p);
            }

            // 真实枪械（第一人称 3D 枪已由 GL 渲染，此处不再画 2D 版）

            // 摇杆
            if (joyActive) {
                p.setStyle(Paint.Style.STROKE);
                p.setColor(0x55FFFFFF);
                p.setStrokeWidth(3);
                float r = dp(46);
                c.drawCircle(joyCX, joyCY, r, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0x88FFFFFF);
                c.drawCircle(joyCX + joyDX, joyCY + joyDY, dp(16), p);
            }

            if (hpText != null) hpText.setText("HP " + engine.hp + "%");
            if (killText != null) {
                killText.setText("击杀 " + engine.kills + "  弹药 " + engine.ammo + "/" + engine.clipSize
                        + (engine.reloadTime > 0 ? "  换弹中" : ""));
            }
            if (netText != null && !singleMode) {
                netText.setText(server != null ? "房主 · 端口 25001" : "已加入房间");
            }
            if (waveText != null) {
                if (singleMode) {
                    waveText.setText("第 " + wave + " 波");
                } else {
                    waveText.setText("");
                }
            }

            // 中央提示（波次开始/击杀）
            if (waveMsg > 0) {
                p.setStyle(Paint.Style.FILL);
                p.setColor((int) (255 * Math.min(1f, waveMsg)) << 24 | 0x00FFFFFF);
                p.setTextSize(26);
                p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                p.setTextAlign(Paint.Align.CENTER);
                c.drawText("第 " + wave + " 波", w / 2f, h * 0.35f, p);
                p.setTextAlign(Paint.Align.LEFT);
                p.setTypeface(android.graphics.Typeface.DEFAULT);
            } else if (killMsg > 0) {
                p.setStyle(Paint.Style.FILL);
                p.setColor((int) (255 * Math.min(1f, killMsg)) << 24 | 0x00FFC107);
                p.setTextSize(22);
                p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                p.setTextAlign(Paint.Align.CENTER);
                c.drawText("击杀！", w / 2f, h * 0.35f, p);
                p.setTextAlign(Paint.Align.LEFT);
                p.setTypeface(android.graphics.Typeface.DEFAULT);
            }

            // 受击红屏（低血量持续泛红）
            float red = redFlash > 0 ? redFlash * 0.8f : Math.max(0f, (40 - engine.hp) / 40f) * 0.22f;
            if (red > 0.02f) {
                p.setStyle(Paint.Style.FILL);
                p.setColor((int) (red * 255) << 24 | 0x00FF0000);
                c.drawRect(0, 0, w, h, p);
            }

            if (dead) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(0x66000000);
                c.drawRect(0, 0, w, h, p);
                p.setColor(0xFFFFFFFF);
                p.setTextSize(24);
                p.setTextAlign(Paint.Align.CENTER);
                c.drawText(singleMode ? "你被击败了" : "你被击杀了", w / 2f, h / 2f, p);
                p.setTextAlign(Paint.Align.LEFT);
            }
            invalidate();
        }

        private void drawRealGun(Canvas c, int w, int h) {
            // 3D 枪械已由 GL 渲染（buildViewmodelGun），此 HUD 层不再绘制
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int action = e.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                float sw = getWidth(), sh = getHeight();
                if (e.getX() < dp(180) && e.getY() > sh - dp(260)) {
                    joyActive = true;
                    joyCX = e.getX();
                    joyCY = e.getY();
                    joyDX = 0;
                    joyDY = 0;
                } else if (e.getX() > sw - dp(140) && e.getY() > sh - dp(160)) {
                    doFire();
                } else if (e.getX() > sw - dp(110) && e.getX() < sw - dp(30)
                        && e.getY() > sh - dp(270) && e.getY() < sh - dp(190)) {
                    // 换弹按钮
                    if (engine.reloadTime <= 0 && engine.ammo < engine.clipSize && engine.hp > 0) {
                        engine.startReload();
                        if (sfx != null) sfx.playReload();
                    }
                } else {
                    // 右半屏/中上部：视角滑动起点
                    lookLastX = e.getX();
                    lookLastY = e.getY();
                }
                return true;
            } else if (action == MotionEvent.ACTION_MOVE) {
                if (joyActive) {
                    float dx = e.getX() - joyCX;
                    float dy = e.getY() - joyCY;
                    float max = dp(46);
                    float len = (float) Math.sqrt(dx * dx + dy * dy);
                    if (len > max) {
                        dx = dx / len * max;
                        dy = dy / len * max;
                    }
                    joyDX = dx;
                    joyDY = dy;
                } else if (lookLastX >= 0) {
                    // 视角滑动：左右=转身，上下=俯仰
                    float ddx = e.getX() - lookLastX;
                    float ddy = e.getY() - lookLastY;
                    engine.pa += ddx * 0.004f;
                    engine.pitch -= ddy * 0.004f;
                    if (engine.pitch > 0.55f) engine.pitch = 0.55f;
                    if (engine.pitch < -0.55f) engine.pitch = -0.55f;
                    lookLastX = e.getX();
                    lookLastY = e.getY();
                }
                return true;
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                joyActive = false;
                joyDX = 0;
                joyDY = 0;
                lookLastX = -1;
                lookLastY = -1;
                return true;
            }
            return true;
        }
    }

    // ================= OpenGL 渲染器 v3 =================

    class GLRenderer implements GLSurfaceView.Renderer {
        private int program;
        private int uMvp, uAlpha, aPos, aUv, aColor;
        private FloatBuffer vb = ByteBuffer.allocateDirect(240000 * 8 * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        private int texId;
        private float[] proj = new float[16], view = new float[16], mvp = new float[16];
        private float[] identity = new float[16];
        private FloatBuffer skyVb = ByteBuffer.allocateDirect(8 * 8 * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        private FloatBuffer cloudsVb = ByteBuffer.allocateDirect(24 * 8 * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        private Random r = new Random(7);
        private int texW = 512, texH = 512, cell = 64;
        private FloatBuffer gunVb = ByteBuffer.allocateDirect(400 * 8 * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();

        // 纹理格（4x4）：石(0,0) 草顶(1,0) 泥(2,0) 砖(3,0) 木(0,1) 兵橙(1,1) 兵蓝(2,1) 兵紫(3,1) 兵绿(0,2) 火花(1,2) 白圆(2,2) 纯白(3,2) 铁箱(2,3) 弹药箱(3,3) 树叶(0,3)
        private final int[] T_STONE = {0, 0}, T_GRASS_TOP = {1, 0}, T_DIRT = {2, 0},
                T_BRICK = {3, 0}, T_WOOD = {0, 1}, T_SOLDIER_O = {1, 1}, T_SOLDIER_B = {2, 1},
                T_SOLDIER_P = {3, 1}, T_SOLDIER_G = {0, 2}, T_IMPACT = {1, 2}, T_WHITE = {2, 2},
                T_SOLID = {3, 2}, T_CRATE = {2, 3}, T_AMMO = {3, 3}, T_LEAF = {0, 3};

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            GLES20.glClearColor(0.55f, 0.75f, 0.92f, 1f);
            program = buildProgram();
            uMvp = GLES20.glGetUniformLocation(program, "uMVP");
            uAlpha = GLES20.glGetUniformLocation(program, "uAlpha");
            aPos = GLES20.glGetAttribLocation(program, "aPos");
            aUv = GLES20.glGetAttribLocation(program, "aUV");
            aColor = GLES20.glGetAttribLocation(program, "aColor");
            texId = makeTexture();
            android.opengl.Matrix.setIdentityM(identity, 0);
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
            float aspect = height == 0 ? 1f : (float) width / height;
            Matrix.perspectiveM(proj, 0, 70f, aspect, 0.1f, 60f);
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            long now = SystemClock.uptimeMillis();
            float dt = Math.min(0.05f, (now - lastT) / 1000f);
            lastT = now;
            tick(dt);

            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

            Matrix.setLookAtM(view, 0,
                    engine.px, 1.6f, engine.pz,
                    engine.px + (float) Math.cos(engine.pa) * (float) Math.cos(engine.pitch),
                    1.6f + (float) Math.sin(engine.pitch),
                    engine.pz + (float) Math.sin(engine.pa) * (float) Math.cos(engine.pitch),
                    0, 1, 0);
            Matrix.multiplyMM(mvp, 0, proj, 0, view, 0);

            GLES20.glUseProgram(program);
            GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId);

            // 天空渐变（先画，深度关闭，世界后画覆盖）
            GLES20.glDisable(GLES20.GL_DEPTH_TEST);
            GLES20.glUniformMatrix4fv(uMvp, 1, false, identity, 0);
            buildSky(skyVb);
            if (skyVb.position() > 0) {
                skyVb.flip();
                GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 32, skyVb);
                GLES20.glEnableVertexAttribArray(aPos);
                skyVb.position(3);
                GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, 32, skyVb);
                GLES20.glEnableVertexAttribArray(aUv);
                skyVb.position(5);
                GLES20.glVertexAttribPointer(aColor, 3, GLES20.GL_FLOAT, false, 32, skyVb);
                GLES20.glEnableVertexAttribArray(aColor);
                GLES20.glUniform1f(uAlpha, 1f);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, skyVb.position() / 8);
                GLES20.glDisableVertexAttribArray(aPos);
                GLES20.glDisableVertexAttribArray(aUv);
                GLES20.glDisableVertexAttribArray(aColor);
            }
            skyVb.clear();
            // 白云（屏幕空间 billboard，深度关闭，半透明叠加）
            GLES20.glEnable(GLES20.GL_BLEND);
            cloudsVb.clear();
            buildClouds(cloudsVb);
            if (cloudsVb.position() > 0) {
                cloudsVb.flip();
                GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 32, cloudsVb);
                GLES20.glEnableVertexAttribArray(aPos);
                cloudsVb.position(3);
                GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, 32, cloudsVb);
                GLES20.glEnableVertexAttribArray(aUv);
                cloudsVb.position(5);
                GLES20.glVertexAttribPointer(aColor, 3, GLES20.GL_FLOAT, false, 32, cloudsVb);
                GLES20.glEnableVertexAttribArray(aColor);
                GLES20.glUniform1f(uAlpha, 0.88f);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, cloudsVb.position() / 8);
                GLES20.glDisableVertexAttribArray(aPos);
                GLES20.glDisableVertexAttribArray(aUv);
                GLES20.glDisableVertexAttribArray(aColor);
            }
            cloudsVb.clear();
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0);

            vb.clear();
            buildWorld(vb);
            buildWallHits(vb);
            buildSprites(vb);
            buildEnemyEffects(vb);
            int triCount = vb.position() / 8;
            buildSun(vb);
            buildBullets(vb);
            int lineStart = vb.position() / 8;
            buildBulletTracers(vb);
            int alphaStart = vb.position() / 8;
            buildAlphaPass(vb);
            int total = vb.position() / 8;
            if (total > 0) {
                vb.flip();
                GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 32, vb);
                GLES20.glEnableVertexAttribArray(aPos);
                vb.position(3);
                GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, 32, vb);
                GLES20.glEnableVertexAttribArray(aUv);
                vb.position(5);
                GLES20.glVertexAttribPointer(aColor, 3, GLES20.GL_FLOAT, false, 32, vb);
                GLES20.glEnableVertexAttribArray(aColor);
                GLES20.glUniform1f(uAlpha, 1f);
                if (triCount > 0) GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, triCount);
                if (alphaStart > lineStart) GLES20.glDrawArrays(GLES20.GL_LINES, lineStart, alphaStart - lineStart);
                // 半透明特效通道（烟雾/阴影）
                if (total > alphaStart) {
                    GLES20.glUniform1f(uAlpha, 0.5f);
                    GLES20.glDrawArrays(GLES20.GL_TRIANGLES, alphaStart, total - alphaStart);
                }
                GLES20.glDisableVertexAttribArray(aPos);
                GLES20.glDisableVertexAttribArray(aUv);
                GLES20.glDisableVertexAttribArray(aColor);
            }
            // 第一人称 3D 枪（清除深度，永远在最前）
            GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT);
            gunVb.clear();
            buildViewmodelGun(gunVb);
            if (gunVb.position() > 0) {
                gunVb.flip();
                GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 32, gunVb);
                GLES20.glEnableVertexAttribArray(aPos);
                gunVb.position(3);
                GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, 32, gunVb);
                GLES20.glEnableVertexAttribArray(aUv);
                gunVb.position(5);
                GLES20.glVertexAttribPointer(aColor, 3, GLES20.GL_FLOAT, false, 32, gunVb);
                GLES20.glEnableVertexAttribArray(aColor);
                GLES20.glUniform1f(uAlpha, 1f);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, gunVb.position() / 8);
                GLES20.glDisableVertexAttribArray(aPos);
                GLES20.glDisableVertexAttribArray(aUv);
                GLES20.glDisableVertexAttribArray(aColor);
            }
        }

        // ===== 第一人称 3D 枪（体素建模 + 真实枪械贴图） =====

        /** 相机空间点 → 世界 */
        private void camToWorld(float[] out, float cx, float cy, float cz,
                                float bx, float by, float bz,
                                float rx, float ry, float rz,
                                float ux, float uy, float uz,
                                float fx, float fy, float fz) {
            out[0] = bx + rx * cx + ux * cy + fx * cz;
            out[1] = by + ry * cx + uy * cy + fy * cz;
            out[2] = bz + rz * cx + uz * cy + fz * cz;
        }

        /** 长方体（相机空间），gunTex 时左右面贴真枪纹理 */
        private void boxGun(FloatBuffer b, float cx, float cy, float cz, float sx, float sy, float sz,
                            float bx, float by, float bz,
                            float rx, float ry, float rz, float ux, float uy, float uz,
                            float fx, float fy, float fz,
                            float bright, int[] texCell, boolean gunTex) {
            float[][] c = new float[8][3];
            float[][] w = new float[8][3];
            c[0] = new float[]{cx - sx / 2, cy - sy / 2, cz - sz / 2};
            c[1] = new float[]{cx + sx / 2, cy - sy / 2, cz - sz / 2};
            c[2] = new float[]{cx + sx / 2, cy + sy / 2, cz - sz / 2};
            c[3] = new float[]{cx - sx / 2, cy + sy / 2, cz - sz / 2};
            c[4] = new float[]{cx - sx / 2, cy - sy / 2, cz + sz / 2};
            c[5] = new float[]{cx + sx / 2, cy - sy / 2, cz + sz / 2};
            c[6] = new float[]{cx + sx / 2, cy + sy / 2, cz + sz / 2};
            c[7] = new float[]{cx - sx / 2, cy + sy / 2, cz + sz / 2};
            for (int i = 0; i < 8; i++) camToWorld(w[i], c[i][0], c[i][1], c[i][2], bx, by, bz, rx, ry, rz, ux, uy, uz, fx, fy, fz);
            // 面（角索引）：+x:1,2,6,5  -x:0,4,7,3  +y:3,7,6,2  -y:0,1,5,4  +z:4,5,6,7  -z:0,3,2,1
            float gu0 = 0.5f / texW, gv0 = (3 * cell + 0.5f) / texH;
            float gu1 = (128 - 0.5f) / texW, gv1 = (4 * cell - 0.5f) / texH;
            float u0 = (texCell[0] * cell + 0.5f) / texW, v0 = (texCell[1] * cell + 0.5f) / texH;
            float u1 = (texCell[0] * cell + cell - 0.5f) / texW, v1 = (texCell[1] * cell + cell - 0.5f) / texH;
            int[][] faces = {{1, 2, 6, 5}, {0, 4, 7, 3}, {3, 7, 6, 2}, {0, 1, 5, 4}, {4, 5, 6, 7}, {0, 3, 2, 1}};
            for (int f = 0; f < 6; f++) {
                int[] idx = faces[f];
                float bf = bright * (f == 0 ? 1f : (f == 2 ? 0.92f : (f == 3 ? 0.62f : 0.78f)));
                if (gunTex && (f == 0 || f == 1)) {
                    quadWorld(b, w[idx[0]], w[idx[1]], w[idx[2]], w[idx[3]], gu0, gv0, gu1, gv1, bf);
                } else {
                    quadWorld(b, w[idx[0]], w[idx[1]], w[idx[2]], w[idx[3]], u0, v0, u1, v1, bf);
                }
            }
        }

        /** 世界空间四边形（四角点 + 自定义 UV） */
        private void quadWorld(FloatBuffer b, float[] a, float[] d, float[] c2, float[] e,
                               float u0, float v0, float u1, float v1, float br) {
            vert(b, a[0], a[1], a[2], u0, v0, br);
            vert(b, d[0], d[1], d[2], u1, v0, br);
            vert(b, c2[0], c2[1], c2[2], u0, v1, br);
            vert(b, c2[0], c2[1], c2[2], u0, v1, br);
            vert(b, d[0], d[1], d[2], u1, v1, br);
            vert(b, e[0], e[1], e[2], u1, v0, br);
        }

        private void buildViewmodelGun(FloatBuffer b) {
            float rec = engine.recoil;
            // 视线基准向量
            float rx = view[0], ry = view[1], rz = view[2];
            float ux = view[4], uy = view[5], uz = view[6];
            float fx = -view[8], fy = -view[9], fz = -view[10];
            // 枪定位：相机右下方
            float bx = engine.px + fx * 0.52f + rx * 0.26f - ux * 0.24f;
            float by = 1.6f + fy * 0.52f + ry * 0.26f - uy * 0.24f;
            float bz = engine.pz + fz * 0.52f + rz * 0.26f - uz * 0.24f;
            // 后坐（向后缩）
            float kick = rec * 0.09f;
            bx -= fx * kick; by -= fy * kick; bz -= fz * kick;
            // 走路摆动
            float bobb = (float) Math.sin(bobPhase) * 0.015f;
            by += uy * bobb;
            bx += ux * bobb * 0.6f;
            // 换弹时枪下移
            float rld = engine.reloadTime > 0 ? Math.min(0.5f, engine.reloadTime) : 0;
            by -= uy * rld * 0.22f;

            // 滑套（贴真实枪械纹理）
            boxGun(b, 0f, 0.02f, 0.22f, 0.075f, 0.075f, 0.34f,
                    bx, by, bz, rx, ry, rz, ux, uy, uz, fx, fy, fz, 0.95f, T_CRATE, true);
            // 枪管前端
            boxGun(b, 0f, 0.04f, 0.46f, 0.05f, 0.055f, 0.16f,
                    bx, by, bz, rx, ry, rz, ux, uy, uz, fx, fy, fz, 0.85f, T_CRATE, false);
            // 握把
            boxGun(b, 0f, -0.115f, 0f, 0.07f, 0.16f, 0.07f,
                    bx, by, bz, rx, ry, rz, ux, uy, uz, fx, fy, fz, 0.72f, T_AMMO, false);
            // 弹匣
            boxGun(b, 0f, -0.085f, 0.05f, 0.055f, 0.09f, 0.05f,
                    bx, by, bz, rx, ry, rz, ux, uy, uz, fx, fy, fz, 0.66f, T_WOOD, false);
            // 扳机护弓
            boxGun(b, 0f, -0.055f, 0.09f, 0.045f, 0.03f, 0.10f,
                    bx, by, bz, rx, ry, rz, ux, uy, uz, fx, fy, fz, 0.62f, T_CRATE, false);
        }

        // ===== 世界 =====

        private void buildWorld(FloatBuffer b) {
            float pxc = engine.px, pzc = engine.pz;
            int x0 = Math.max(0, (int) pxc - 7), x1 = Math.min(FpsEngine.W, (int) pxc + 8);
            int z0 = Math.max(0, (int) pzc - 7), z1 = Math.min(FpsEngine.D, (int) pzc + 8);
            for (int gx = x0; gx < x1; gx++)
                for (int gz = z0; gz < z1; gz++)
                    for (int gy = 0; gy < FpsEngine.H; gy++) {
                        byte t = engine.get(gx, gy, gz);
                        if (t == FpsEngine.AIR) continue;
                        float x = gx, y = gy, z = gz;
                        float dist = (float) Math.sqrt((gx + 0.5f - pxc) * (gx + 0.5f - pxc) + (gz + 0.5f - pzc) * (gz + 0.5f - pzc));
                        float fog = Math.min(1f, dist / 14f);
                        if (engine.get(gx + 1, gy, gz) == FpsEngine.AIR) face(b, x + 1, y, z, x + 1, y + 1, z, x + 1, y, z + 1, x + 1, y + 1, z + 1, 0.72f, uv(t, 0), fog);
                        if (engine.get(gx - 1, gy, gz) == FpsEngine.AIR) face(b, x, y, z, x, y + 1, z + 1, x, y, z + 1, x, y + 1, z, 0.72f, uv(t, 0), fog);
                        if (gy + 1 >= FpsEngine.H || engine.get(gx, gy + 1, gz) == FpsEngine.AIR) face(b, x, y + 1, z, x + 1, y + 1, z, x, y + 1, z + 1, x + 1, y + 1, z + 1, 1f, uv(t, 1), fog);
                        if (gy == 0 || engine.get(gx, gy - 1, gz) == FpsEngine.AIR) face(b, x, y, z, x + 1, y, z + 1, x, y, z + 1, x + 1, y, z, 0.55f, uv(t, 0), fog);
                        if (engine.get(gx, gy, gz + 1) == FpsEngine.AIR) face(b, x, y, z + 1, x + 1, y, z + 1, x, y + 1, z + 1, x + 1, y + 1, z + 1, 0.72f, uv(t, 0), fog);
                        if (engine.get(gx, gy, gz - 1) == FpsEngine.AIR) face(b, x, y, z, x, y + 1, z, x + 1, y, z, x + 1, y + 1, z, 0.72f, uv(t, 0), fog);
                    }
        }

        private int[] uv(byte t, int side) {
            switch (t) {
                case FpsEngine.STONE: return T_STONE;
                case FpsEngine.GRASS: return side == 1 ? T_GRASS_TOP : T_DIRT;
                case FpsEngine.DIRT: return T_DIRT;
                case FpsEngine.BRICK: return T_BRICK;
                case FpsEngine.CRATE: return T_CRATE;
                case FpsEngine.AMMO: return T_AMMO;
                case FpsEngine.LEAF: return T_LEAF;
                default: return T_WOOD;
            }
        }

        // ===== 天空渐变（屏幕空间，深度关闭，MVP=单位矩阵） =====

        private void buildSky(FloatBuffer b) {
            // 只采样纯白实心格（T_SOLID 3,2），用顶点色渐变做天空，避免把图集当天空贴图
            float u0 = (3 * cell + 0.5f) / texW;
            float v0 = (2 * cell + 0.5f) / texH;
            float u1 = (3 * cell + cell - 0.5f) / texW;
            float v1 = (2 * cell + cell - 0.5f) / texH;
            float topR = 0.32f, topG = 0.58f, topB = 0.96f;
            float botR = 0.76f, botG = 0.88f, botB = 0.98f;
            vertC(b, -1f, -1f, 1f, u0, v0, botR, botG, botB);
            vertC(b, 1f, -1f, 1f, u1, v0, botR, botG, botB);
            vertC(b, -1f, 1f, 1f, u0, v1, topR, topG, topB);
            vertC(b, -1f, 1f, 1f, u0, v1, topR, topG, topB);
            vertC(b, 1f, -1f, 1f, u1, v0, botR, botG, botB);
            vertC(b, 1f, 1f, 1f, u1, v1, topR, topG, topB);
        }

        /** 白云：屏幕空间椭圆白圆 billboard，随玩家位置轻微视差 */
        private void buildClouds(FloatBuffer b) {
            float[] cxs = {-0.92f, -0.32f, 0.28f, 0.88f};
            float[] cys = {0.80f, 0.60f, 0.72f, 0.52f};
            float[] csc = {0.30f, 0.20f, 0.24f, 0.17f};
            float parallax = (engine.px - 11.5f) * 0.010f;
            float u0 = (2 * cell + 0.5f) / texW;
            float v0 = (2 * cell + 0.5f) / texH;
            float u1 = (2 * cell + cell - 0.5f) / texW;
            float v1 = (2 * cell + cell - 0.5f) / texH;
            for (int i = 0; i < cxs.length; i++) {
                float cx = cxs[i] - parallax;
                float cy = cys[i];
                float s = csc[i];
                float sh = s * 0.34f;
                vertC(b, cx - s, cy - sh, 1f, u0, v0, 1f, 1f, 1f);
                vertC(b, cx + s, cy - sh, 1f, u1, v0, 1f, 1f, 1f);
                vertC(b, cx - s, cy + sh, 1f, u0, v1, 1f, 1f, 1f);
                vertC(b, cx - s, cy + sh, 1f, u0, v1, 1f, 1f, 1f);
                vertC(b, cx + s, cy - sh, 1f, u1, v0, 1f, 1f, 1f);
                vertC(b, cx + s, cy + sh, 1f, u1, v1, 1f, 1f, 1f);
            }
        }

        /** 太阳光晕：固定远方向 billboard */
        private void buildSun(FloatBuffer b) {
            float sx = engine.px + 34f, sy = 20f, sz = engine.pz - 28f;
            float rx = view[0], rz = view[2];
            float ux = view[4], uy = view[5], uz = view[6];
            float size = 3.6f;
            quad(b, sx - rx * size / 2, sy, sz - rz * size / 2,
                    sx + rx * size / 2, sy, sz + rz * size / 2,
                    sx + rx * size / 2 + ux * size, sy + uy * size, sz + rz * size / 2 + uz * size,
                    sx - rx * size / 2 + ux * size, sy + uy * size, sz - rz * size / 2 + uz * size,
                    1.25f, T_WHITE);
        }

        // ===== 弹孔贴花 =====

        private void buildWallHits(FloatBuffer b) {
            for (FpsEngine.WallHit wh : engine.wallHits) {
                float dist = (float) Math.sqrt((wh.x - engine.px) * (wh.x - engine.px) + (wh.z - engine.pz) * (wh.z - engine.pz));
                if (dist > 14f) continue;
                // 墙面切向量：法线 n=(nx,0,nz)，边 e = (nz,0,-nx)
                float ex = wh.nz, ez = -wh.nx;
                float hw = 0.16f, hh = 0.22f;
                float cx = wh.x - wh.nx * 0.035f;
                float cz = wh.z - wh.nz * 0.035f;
                float fade = Math.min(1f, wh.life / 2f);
                float dark = 0.38f * fade;
                quadColor(b,
                        cx - ex * hw, wh.y - hh / 2, cz - ez * hw,
                        cx + ex * hw, wh.y - hh / 2, cz + ez * hw,
                        cx + ex * hw, wh.y + hh / 2, cz + ez * hw,
                        cx - ex * hw, wh.y + hh / 2, cz - ez * hw,
                        dark, dark * 0.92f, dark * 0.8f, T_SOLID);
                // 中心深点
                quadColor(b,
                        cx - ex * hw * 0.45f, wh.y - hh * 0.35f, cz - ez * hw * 0.45f,
                        cx + ex * hw * 0.45f, wh.y - hh * 0.35f, cz + ez * hw * 0.45f,
                        cx + ex * hw * 0.45f, wh.y + hh * 0.35f, cz + ez * hw * 0.45f,
                        cx - ex * hw * 0.45f, wh.y + hh * 0.35f, cz - ez * hw * 0.45f,
                        dark * 0.55f, dark * 0.5f, dark * 0.4f, T_SOLID);
            }
        }

        // ===== 敌人：billboard 人像（真实人脸） =====

        private void buildSprites(FloatBuffer b) {
            for (FpsEngine.Sprite s : engine.sprites) {
                if (s.dead && s.deathTime < 0) continue;
                float dist = (float) Math.sqrt((s.x - engine.px) * (s.x - engine.px) + (s.z - engine.pz) * (s.z - engine.pz));
                if (dist > 15f) continue;
                float fog = Math.min(1f, dist / 14f);
                int[] cell;
                switch (s.id % 4) {
                    case 0: cell = T_SOLDIER_O; break;
                    case 1: cell = T_SOLDIER_B; break;
                    case 2: cell = T_SOLDIER_P; break;
                    default: cell = T_SOLDIER_G; break;
                }
                float rx = view[0], ry = view[1], rz = view[2];
                float ux = view[4], uy = view[5], uz = view[6];
                float cx = s.x, cz = s.z;
                float hh = 1.85f, ww = 0.92f;
                float cy = 0.92f;
                float br = 0.92f * (1f - fog * 0.55f);
                // 死亡倒下：高度压缩到底（0.5s 动画）
                if (s.dead) {
                    float t = Math.min(1f, s.deathTime / 0.45f);
                    hh *= (1f - t);
                    cy *= (1f - t);
                    cy += 0.06f * t;
                    br = 0.55f;
                }
                // 受击闪烁：泛白（仅活体）
                if (s.hitFlash > 0 && !s.dead) {
                    br = Math.min(2.2f, br + s.hitFlash * 4f);
                }
                quad(b, cx - rx * ww / 2, cy, cz - rz * ww / 2,
                        cx + rx * ww / 2, cy, cz + rz * ww / 2,
                        cx + rx * ww / 2 + ux * hh, cy + uy * hh, cz + rz * ww / 2 + uz * hh,
                        cx - rx * ww / 2 + ux * hh, cy + uy * hh, cz - rz * ww / 2 + uz * hh,
                        br, cell);
            }
            // 弹壳（金色小方块 billboard，抛壳细节）
            float sU0 = (3 * this.cell + 0.5f) / texW;
            float sV0 = (2 * this.cell + 0.5f) / texH;
            float sU1 = (3 * this.cell + this.cell - 0.5f) / texW;
            float sV1 = (2 * this.cell + this.cell - 0.5f) / texH;
            float erx = view[0], ery = view[1], erz = view[2];
            float eux = view[4], euy = view[5], euz = view[6];
            for (FpsEngine.Casing k : engine.casings) {
                if (k.life <= 0) continue;
                float dist = (float) Math.sqrt((k.x - engine.px) * (k.x - engine.px) + (k.z - engine.pz) * (k.z - engine.pz));
                if (dist > 12f) continue;
                float sz = 0.045f + 0.015f * (float) Math.sin(k.rot * 2f);
                float fade = Math.min(1f, k.life / 0.6f);
                float cr = 0.96f * fade, cg = 0.73f * fade, cb = 0.18f * fade;
                float ky = k.y;
                vertC(b, k.x - erx * sz, ky - ery * sz, k.z - erz * sz, sU0, sV0, cr, cg, cb);
                vertC(b, k.x + erx * sz, ky + ery * sz, k.z + erz * sz, sU1, sV0, cr, cg, cb);
                vertC(b, k.x - erx * sz + eux * sz, ky - ery * sz + euy * sz, k.z - erz * sz + euz * sz, sU0, sV1, cr, cg, cb);
                vertC(b, k.x - erx * sz + eux * sz, ky - ery * sz + euy * sz, k.z - erz * sz + euz * sz, sU0, sV1, cr, cg, cb);
                vertC(b, k.x + erx * sz, ky + ery * sz, k.z + erz * sz, sU1, sV0, cr, cg, cb);
                vertC(b, k.x + erx * sz + eux * sz, ky + ery * sz + euy * sz, k.z + erz * sz + euz * sz, sU1, sV1, cr, cg, cb);
            }
        }

        /** 敌人头顶血条 + 枪口闪光（不透明 pass） */
        private void buildEnemyEffects(FloatBuffer b) {
            for (FpsEngine.Sprite s : engine.sprites) {
                if (s.dead) continue;
                if (s.hp >= 100) continue;
                float dist = (float) Math.sqrt((s.x - engine.px) * (s.x - engine.px) + (s.z - engine.pz) * (s.z - engine.pz));
                if (dist > 15f) continue;
                float fog = Math.min(1f, dist / 14f);
                float rx = view[0], rz = view[2];
                float ux = view[4], uy = view[5], uz = view[6];
                float cx = s.x, cz = s.z;
                // 血条：黑底 + 绿色比例
                float barY = 2.25f, barW = 0.9f, barH = 0.10f;
                float br = (1f - fog * 0.5f);
                quad(b, cx - rx * barW / 2, barY, cz - rz * barW / 2,
                        cx + rx * barW / 2, barY, cz + rz * barW / 2,
                        cx + rx * barW / 2 + ux * barH, barY + uy * barH, cz + rz * barW / 2 + uz * barH,
                        cx - rx * barW / 2 + ux * barH, barY + uy * barH, cz - rz * barW / 2 + uz * barH,
                        br * 0.35f, T_SOLID);
                float hpFrac = s.hp / 100f;
                if (hpFrac > 0.01f) {
                    float gw = barW * hpFrac;
                    float cg, cr, cb;
                    if (hpFrac > 0.5f) {
                        cg = 0.76f; cr = 0.3f; cb = 0.2f;
                    } else if (hpFrac > 0.25f) {
                        cg = 0.76f; cr = 0.76f; cb = 0.03f;
                    } else {
                        cg = 0.2f; cr = 0.9f; cb = 0.15f;
                    }
                    quadColor(b, cx - rx * gw / 2, barY, cz - rz * gw / 2,
                            cx + rx * gw / 2, barY, cz + rz * gw / 2,
                            cx + rx * gw / 2 + ux * barH, barY + uy * barH, cz + rz * gw / 2 + uz * barH,
                            cx - rx * gw / 2 + ux * barH, barY + uy * barH, cz - rz * gw / 2 + uz * barH,
                            br * cr, br * cg, br * cb, T_SOLID);
                }
                // 敌人枪口闪光
                if (s.muzzleFlash > 0) {
                    float mf = s.muzzleFlash / 0.12f;
                    float mx = s.x + (float) Math.cos(s.angle) * 0.5f;
                    float mz = s.z + (float) Math.sin(s.angle) * 0.5f;
                    float size = 0.18f * mf;
                    quad(b, mx - rx * size / 2, 1.25f, mz - rz * size / 2,
                            mx + rx * size / 2, 1.25f, mz + rz * size / 2,
                            mx + rx * size / 2 + ux * size, 1.25f + uy * size, mz + rz * size / 2 + uz * size,
                            mx - rx * size / 2 + ux * size, 1.25f + uy * size, mz - rz * size / 2 + uz * size,
                            2.0f * mf, T_WHITE);
                }
            }
        }

        /** 半透明通道：烟雾 / 脚底阴影 */
        private void buildAlphaPass(FloatBuffer b) {
            // 敌人脚底阴影
            for (FpsEngine.Sprite s : engine.sprites) {
                if (s.dead) continue;
                float dist = (float) Math.sqrt((s.x - engine.px) * (s.x - engine.px) + (s.z - engine.pz) * (s.z - engine.pz));
                if (dist > 15f) continue;
                float rx = view[0], rz = view[2];
                float ux = view[4], uz = view[6];
                float sh = 0.62f;
                quad(b, s.x - rx * sh / 2, 0.02f, s.z - rz * sh / 2,
                        s.x + rx * sh / 2, 0.02f, s.z + rz * sh / 2,
                        s.x + rx * sh / 2 + ux * sh, 0.02f, s.z + rz * sh / 2 + uz * sh,
                        s.x - rx * sh / 2 + ux * sh, 0.02f, s.z - rz * sh / 2 + uz * sh,
                        0.4f, T_SOLID);
            }
            // 烟雾粒子
            for (FpsEngine.Smoke sm : engine.smokes) {
                float lifeFrac = sm.life / sm.maxLife;
                if (lifeFrac <= 0) continue;
                float rx = view[0], rz = view[2];
                float ux = view[4], uy = view[5], uz = view[6];
                float size = sm.size * (1f + (1f - lifeFrac) * 1.6f);
                float gray = 0.35f + 0.2f * lifeFrac;
                quad(b, sm.x - rx * size / 2, sm.y, sm.z - rz * size / 2,
                        sm.x + rx * size / 2, sm.y, sm.z + rz * size / 2,
                        sm.x + rx * size / 2 + ux * size, sm.y + uy * size, sm.z + rz * size / 2 + uz * size,
                        sm.x - rx * size / 2 + ux * size, sm.y + uy * size, sm.z - rz * size / 2 + uz * size,
                        gray * lifeFrac, T_WHITE);
            }
        }

        // ===== 子弹：光点 + 火花 =====

        private void buildBullets(FloatBuffer b) {
            for (FpsEngine.Bullet bl : engine.bullets) {
                if (bl.impactLife > 0) {
                    float dist = (float) Math.sqrt((bl.tx - engine.px) * (bl.tx - engine.px) + (bl.tz - engine.pz) * (bl.tz - engine.pz));
                    if (dist < 14f) {
                        float size = 0.55f * (bl.impactLife / 0.45f + 0.4f);
                        float rx = view[0], rz = view[2];
                        float ux = view[4], uy = view[5], uz = view[6];
                        float cy = 1.55f;
                        quad(b, bl.tx - rx * size / 2, cy, bl.tz - rz * size / 2,
                                bl.tx + rx * size / 2, cy, bl.tz + rz * size / 2,
                                bl.tx + rx * size / 2 + ux * size, cy + uy * size, bl.tz + rz * size / 2 + uz * size,
                                bl.tx - rx * size / 2 + ux * size, cy + uy * size, bl.tz - rz * size / 2 + uz * size,
                                1.15f, T_IMPACT);
                    }
                } else {
                    float rx = view[0], rz = view[2];
                    float ux = view[4], uy = view[5], uz = view[6];
                    float cy = 1.6f;
                    float size = 0.09f;
                    quad(b, bl.x - rx * size / 2, cy, bl.z - rz * size / 2,
                            bl.x + rx * size / 2, cy, bl.z + rz * size / 2,
                            bl.x + rx * size / 2 + ux * size, cy + uy * size, bl.z + rz * size / 2 + uz * size,
                            bl.x - rx * size / 2 + ux * size, cy + uy * size, bl.z - rz * size / 2 + uz * size,
                            1.5f, T_WHITE);
                }
            }
        }

        /** 曳光线（GL_LINES）：从枪口到子弹当前位置 */
        private void buildBulletTracers(FloatBuffer b) {
            for (FpsEngine.Bullet bl : engine.bullets) {
                if (bl.impactLife > 0) continue;
                float cx = bl.x, cz = bl.z;
                float f = Math.max(0f, bl.progress - 0.06f);
                float px0 = bl.sx + (bl.tx - bl.sx) * f;
                float pz0 = bl.sz + (bl.tz - bl.sz) * f;
                float dist = (float) Math.sqrt((cx - engine.px) * (cx - engine.px) + (cz - engine.pz) * (cz - engine.pz));
                float fog = Math.min(1f, dist / 14f);
                float br = 1.5f * (1f - fog * 0.5f);
                b.put(px0); b.put(1.6f); b.put(pz0); b.put(0.25f); b.put(0.25f); b.put(br); b.put(br * 0.85f); b.put(br * 0.4f);
                b.put(cx); b.put(1.6f); b.put(cz); b.put(0.25f); b.put(0.25f); b.put(br); b.put(br * 0.85f); b.put(br * 0.4f);
            }
        }

        // ===== 基础绘制 =====

        private void quad(FloatBuffer b, float x1, float y1, float z1, float x2, float y2, float z2,
                          float x3, float y3, float z3, float x4, float y4, float z4, float bright, int[] texCell) {
            float u0 = (texCell[0] * cell + 0.5f) / texW;
            float v0 = (texCell[1] * cell + 0.5f) / texH;
            float u1 = (texCell[0] * cell + cell - 0.5f) / texW;
            float v1 = (texCell[1] * cell + cell - 0.5f) / texH;
            vert(b, x1, y1, z1, u0, v0, bright);
            vert(b, x2, y2, z2, u1, v1, bright);
            vert(b, x3, y3, z3, u0, v1, bright);
            vert(b, x3, y3, z3, u0, v1, bright);
            vert(b, x2, y2, z2, u1, v1, bright);
            vert(b, x4, y4, z4, u1, v0, bright);
        }

        private void face(FloatBuffer b,
                          float x1, float y1, float z1, float x2, float y2, float z2,
                          float x3, float y3, float z3, float x4, float y4, float z4,
                          float bright, int[] cell, float fog) {
            float bf = bright * (1f - fog * 0.62f);
            quad(b, x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, bf, cell);
        }

        /** 彩色四边形（三通道顶点色） */
        private void quadColor(FloatBuffer b, float x1, float y1, float z1, float x2, float y2, float z2,
                               float x3, float y3, float z3, float x4, float y4, float z4,
                               float cr, float cg, float cb, int[] texCell) {
            float u0 = (texCell[0] * cell + 0.5f) / texW;
            float v0 = (texCell[1] * cell + 0.5f) / texH;
            float u1 = (texCell[0] * cell + cell - 0.5f) / texW;
            float v1 = (texCell[1] * cell + cell - 0.5f) / texH;
            vertC(b, x1, y1, z1, u0, v0, cr, cg, cb);
            vertC(b, x2, y2, z2, u1, v1, cr, cg, cb);
            vertC(b, x3, y3, z3, u0, v1, cr, cg, cb);
            vertC(b, x3, y3, z3, u0, v1, cr, cg, cb);
            vertC(b, x2, y2, z2, u1, v1, cr, cg, cb);
            vertC(b, x4, y4, z4, u1, v0, cr, cg, cb);
        }

        private void vertC(FloatBuffer b, float x, float y, float z, float u, float v, float cr, float cg, float cb) {
            b.put(x);
            b.put(y);
            b.put(z);
            b.put(u);
            b.put(v);
            b.put(cr);
            b.put(cg);
            b.put(cb);
        }

        private void vert(FloatBuffer b, float x, float y, float z, float u, float v, float br) {
            b.put(x);
            b.put(y);
            b.put(z);
            b.put(u);
            b.put(v);
            b.put(br);
            b.put(br);
            b.put(br);
        }

        // ===== 着色器（支持 alpha 混合） =====

        private int buildProgram() {
            String vs = "attribute vec4 aPos; attribute vec2 aUV; attribute vec3 aColor;" +
                    "uniform mat4 uMVP; varying vec2 vUV; varying vec3 vColor; varying float vFog;" +
                    "void main(){ vUV=aUV; vColor=aColor; vec4 clip=uMVP*aPos; gl_Position=clip;" +
                    "vFog=clamp((clip.z/clip.w-0.25)/0.75,0.0,1.0); }";
            String fs = "precision mediump float; varying vec2 vUV; varying vec3 vColor; varying float vFog;" +
                    "uniform sampler2D uTex; uniform float uAlpha;" +
                    "void main(){ vec4 t = texture2D(uTex, vUV); vec3 col = t.rgb*vColor;" +
                    "vec3 fog = vec3(0.58,0.72,0.88); col = mix(col, fog, vFog*0.55);" +
                    "gl_FragColor = vec4(col, t.a*uAlpha); }";
            int vsId = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
            GLES20.glShaderSource(vsId, vs);
            GLES20.glCompileShader(vsId);
            int fsId = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
            GLES20.glShaderSource(fsId, fs);
            GLES20.glCompileShader(fsId);
            int p = GLES20.glCreateProgram();
            GLES20.glAttachShader(p, vsId);
            GLES20.glAttachShader(p, fsId);
            GLES20.glLinkProgram(p);
            GLES20.glDeleteShader(vsId);
            GLES20.glDeleteShader(fsId);
            return p;
        }

        // ===== 纹理图集 256x256（64px 格）：程序高清纹理 + 真实素材 =====

        private int makeTexture() {
            Bitmap atlas = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(atlas);
            Paint p = new Paint();
            paintStone(c, 0, 0);
            paintGrassTop(c, 1, 0);
            paintDirt(c, 2, 0);
            paintBrick(c, 3, 0);
            paintWood(c, 0, 1);
            paintCrate(c, 2, 3);
            paintAmmo(c, 3, 3);
            paintLeaf(c, 0, 3);
            // 真实枪械贴图（128x64 区域，格 0,3）
            paintGun(c, 0, 3);
            pasteAsset(c, 1, 1, "soldier_orange.png");
            pasteAsset(c, 2, 1, "soldier_blue.png");
            pasteAsset(c, 3, 1, "soldier_purple.png");
            pasteAsset(c, 0, 2, "soldier_green.png");
            pasteAsset(c, 1, 2, "impact.png");
            // 白色发光圆点（子弹光点）
            p.setColor(Color.WHITE);
            c.drawCircle(2 * 64 + 32, 2 * 64 + 32, 24, p);
            p.setColor(0x99FFFFFF);
            c.drawCircle(2 * 64 + 32, 2 * 64 + 32, 18, p);
            // 纯白实心格（血条/阴影）
            p.setColor(Color.WHITE);
            c.drawRect(3 * 64, 2 * 64, 3 * 64 + 64, 2 * 64 + 64, p);

            int[] tex = new int[1];
            GLES20.glGenTextures(1, tex, 0);
            int id = tex[0];
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, atlas, 0);
            atlas.recycle();
            return id;
        }

        private void pasteAsset(Canvas c, int col, int row, String name) {
            try {
                InputStream is = getAssets().open(name);
                Bitmap bmp = BitmapFactory.decodeStream(is);
                is.close();
                if (bmp.getWidth() != 64 || bmp.getHeight() != 64) {
                    bmp = Bitmap.createScaledBitmap(bmp, 64, 64, true);
                }
                c.drawBitmap(bmp, col * 64, row * 64, null);
                if (bmp != null && !bmp.isRecycled()) bmp.recycle();
            } catch (Exception e) {
                Paint p = new Paint();
                p.setColor(0x88888888);
                c.drawRect(col * 64, row * 64, col * 64 + 64, row * 64 + 64, p);
            }
        }

        /** 真实枪械贴图：缩放 128x64 贴入图集 (col,row) 区域 */
        private void paintGun(Canvas c, int col, int row) {
            try {
                InputStream is = getAssets().open("gun.png");
                Bitmap bmp = BitmapFactory.decodeStream(is);
                is.close();
                Bitmap s = Bitmap.createScaledBitmap(bmp, 128, 64, true);
                c.drawBitmap(s, col * 64, row * 64, null);
                if (s != null && !s.isRecycled()) s.recycle();
                if (bmp != null && !bmp.isRecycled()) bmp.recycle();
            } catch (Exception e) {
                Paint p = new Paint();
                p.setColor(0xFF37474F);
                c.drawRect(col * 64, row * 64, col * 64 + 128, row * 64 + 64, p);
            }
        }

        private void paintStone(Canvas c, int col, int row) {
            int x = col * 64, y = row * 64;
            Paint p = new Paint();
            p.setColor(0xFF9E9E9E);
            c.drawRect(x, y, x + 64, y + 64, p);
            for (int i = 0; i < 40; i++) {
                p.setColor(Color.rgb(120 + r.nextInt(60), 120 + r.nextInt(60), 120 + r.nextInt(60)));
                c.drawRect(x + r.nextInt(64), y + r.nextInt(64), x + r.nextInt(64) + 3, y + r.nextInt(64) + 3, p);
            }
            p.setColor(0xFF757575);
            for (int i = 0; i < 4; i++) {
                c.drawLine(x + r.nextInt(64), y + r.nextInt(64), x + r.nextInt(64), y + r.nextInt(64), p);
            }
        }

        private void paintGrassTop(Canvas c, int col, int row) {
            int x = col * 64, y = row * 64;
            Paint p = new Paint();
            p.setColor(0xFF6BBF59);
            c.drawRect(x, y, x + 64, y + 64, p);
            for (int i = 0; i < 60; i++) {
                p.setColor(Color.rgb(70 + r.nextInt(50), 130 + r.nextInt(60), 60 + r.nextInt(40)));
                c.drawRect(x + r.nextInt(64), y + r.nextInt(64), x + r.nextInt(64) + 2, y + r.nextInt(64) + 2, p);
            }
            p.setStrokeWidth(2);
            p.setColor(0xFF3E8E41);
            for (int i = 0; i < 24; i++) {
                c.drawLine(x + r.nextInt(64), y + 40 + r.nextInt(20), x + r.nextInt(64), y + r.nextInt(30), p);
            }
        }

        private void paintDirt(Canvas c, int col, int row) {
            int x = col * 64, y = row * 64;
            Paint p = new Paint();
            p.setColor(0xFF8D6E63);
            c.drawRect(x, y, x + 64, y + 64, p);
            for (int i = 0; i < 50; i++) {
                p.setColor(Color.rgb(80 + r.nextInt(50), 60 + r.nextInt(40), 45 + r.nextInt(30)));
                c.drawRect(x + r.nextInt(63), y + r.nextInt(63), x + r.nextInt(63) + 3, y + r.nextInt(63) + 3, p);
            }
        }

        private void paintBrick(Canvas c, int col, int row) {
            int x = col * 64, y = row * 64;
            Paint p = new Paint();
            p.setColor(0xFFB71C1C);
            c.drawRect(x, y, x + 64, y + 64, p);
            for (int i = 0; i < 40; i++) {
                p.setColor(Color.rgb(130 + r.nextInt(40), 20 + r.nextInt(30), 20 + r.nextInt(30)));
                c.drawRect(x + r.nextInt(64), y + r.nextInt(64), x + r.nextInt(64) + 4, y + r.nextInt(64) + 4, p);
            }
            p.setColor(0xFFEFEBE9);
            p.setStrokeWidth(3);
            for (int i = 0; i < 5; i++) c.drawLine(x, y + i * 12 + 11, x + 64, y + i * 12 + 11, p);
            for (int i = 0; i < 5; i++) {
                int ox = (i % 2 == 0 ? 0 : 8);
                c.drawLine(x + i * 16 + ox, y, x + i * 16 + ox, y + 64, p);
            }
        }

        private void paintWood(Canvas c, int col, int row) {
            int x = col * 64, y = row * 64;
            Paint p = new Paint();
            p.setColor(0xFF8D6E63);
            c.drawRect(x, y, x + 64, y + 64, p);
            p.setStrokeWidth(4);
            p.setColor(0xFF5D4037);
            for (int i = 0; i < 8; i++) {
                c.drawLine(x + i * 8 + 3, y, x + i * 8 + 3, y + 64, p);
            }
            p.setStrokeWidth(2);
            p.setColor(0xFF6D4C41);
            for (int i = 0; i < 5; i++) {
                c.drawLine(x + r.nextInt(64), y + r.nextInt(64), x + r.nextInt(64), y + r.nextInt(64), p);
            }
        }

        private void paintCrate(Canvas c, int col, int row) {
            int x = col * 64, y = row * 64;
            Paint p = new Paint();
            p.setColor(0xFF78909C);
            c.drawRect(x, y, x + 64, y + 64, p);
            // 锈蚀斑点
            for (int i = 0; i < 30; i++) {
                p.setColor(Color.rgb(90 + r.nextInt(60), 100 + r.nextInt(50), 110 + r.nextInt(40)));
                c.drawRect(x + r.nextInt(63), y + r.nextInt(63), x + r.nextInt(63) + 4, y + r.nextInt(63) + 4, p);
            }
            // 加固横条 + 铆钉
            p.setColor(0xFF546E7A);
            p.setStrokeWidth(6);
            c.drawLine(x + 4, y + 30, x + 60, y + 30, p);
            p.setColor(0xFFCFD8DC);
            p.setStrokeWidth(3);
            c.drawCircle(x + 16, y + 30, 5, p);
            c.drawCircle(x + 48, y + 30, 5, p);
            p.setColor(0xFF455A64);
            c.drawLine(x + 4, y + 58, x + 60, y + 58, p);
        }

        private void paintAmmo(Canvas c, int col, int row) {
            int x = col * 64, y = row * 64;
            Paint p = new Paint();
            p.setColor(0xFF6D4C41);
            c.drawRect(x, y, x + 64, y + 64, p);
            p.setStrokeWidth(4);
            p.setColor(0xFF4E342E);
            for (int i = 0; i < 8; i++) {
                c.drawLine(x + i * 8 + 3, y, x + i * 8 + 3, y + 64, p);
            }
            // 军绿标志带
            p.setColor(0xFF37474F);
            c.drawRect(x, y + 20, x + 64, y + 44, p);
            p.setColor(0xFFFFFFFF);
            p.setTextSize(20);
            p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText("AMMO", x + 32, y + 39, p);
            p.setTextAlign(Paint.Align.LEFT);
            // 边角金属
            p.setColor(0xFFB0BEC5);
            c.drawRect(x, y, x + 8, y + 64, p);
            c.drawRect(x + 56, y, x + 64, y + 64, p);
        }

        private void paintLeaf(Canvas c, int col, int row) {
            int x = col * 64, y = row * 64;
            Paint p = new Paint();
            p.setColor(0xFF3E8E41);
            c.drawRect(x, y, x + 64, y + 64, p);
            // 叶簇
            for (int i = 0; i < 14; i++) {
                p.setColor(Color.rgb(40 + r.nextInt(50), 110 + r.nextInt(60), 40 + r.nextInt(40)));
                c.drawOval(x + r.nextInt(40), y + r.nextInt(40), x + 18 + r.nextInt(40), y + 18 + r.nextInt(40), p);
            }
            // 高光
            for (int i = 0; i < 8; i++) {
                p.setColor(Color.rgb(90 + r.nextInt(60), 150 + r.nextInt(60), 60 + r.nextInt(40)));
                c.drawCircle(x + r.nextInt(60), y + r.nextInt(60), 3 + r.nextInt(4), p);
            }
        }
    }

    // ================= 联机：UDP 服务器 =================

    class NetServer {
        DatagramSocket sock;
        Thread th;
        volatile boolean on = false;
        int idCount = 1;
        String[] peerNames = new String[8];
        List<InetSocketAddress> peers = new ArrayList<>();

        void start() {
            try {
                sock = new DatagramSocket(25001);
            } catch (IOException e) {
                toast("房间启动失败（端口被占用）");
                return;
            }
            on = true;
            th = new Thread(new Runnable() {
                @Override
                public void run() {
                    byte[] buf = new byte[1024];
                    while (on) {
                        try {
                            DatagramPacket pk = new DatagramPacket(buf, buf.length);
                            sock.receive(pk);
                            String msg = new String(pk.getData(), 0, pk.getLength(), "UTF-8").trim();
                            handle(pk.getAddress(), pk.getPort(), msg);
                        } catch (IOException e) {
                            break;
                        }
                    }
                }
            });
            th.setDaemon(true);
            th.start();
        }

        void handle(InetAddress addr, int port, String msg) {
            if (msg.startsWith("J:")) {
                int id = idCount++;
                if (id < peerNames.length) peerNames[id] = msg.substring(2);
                peers.add(new InetSocketAddress(addr, port));
                send(addr, port, "ID:" + id);
                broadcast("JN:" + id + ":" + peerNames[id]);
            } else if (msg.startsWith("P")) {
                broadcastToOthers(port, msg);
            } else if (msg.startsWith("S:") || msg.startsWith("D:")) {
                broadcast(msg);
            }
        }

        void send(InetAddress addr, int port, String s) {
            try {
                byte[] d = s.getBytes("UTF-8");
                sock.send(new DatagramPacket(d, d.length, addr, port));
            } catch (Exception ignored) {
            }
        }

        void broadcast(String s) {
            try {
                byte[] d = s.getBytes("UTF-8");
                for (InetSocketAddress ep : peers) {
                    sock.send(new DatagramPacket(d, d.length, ep.getAddress(), ep.getPort()));
                }
            } catch (Exception ignored) {
            }
        }

        void broadcastToOthers(int excludePort, String s) {
            try {
                byte[] d = s.getBytes("UTF-8");
                for (InetSocketAddress ep : peers) {
                    if (ep.getPort() == excludePort) continue;
                    sock.send(new DatagramPacket(d, d.length, ep.getAddress(), ep.getPort()));
                }
            } catch (Exception ignored) {
            }
        }

        void clear() {
            for (int i = 0; i < peerNames.length; i++) peerNames[i] = null;
            peers.clear();
        }

        void stop() {
            on = false;
            if (th != null) th.interrupt();
            if (sock != null) sock.close();
        }
    }

    // ================= 联机：UDP 客户端 =================

    class NetClient {
        DatagramSocket sock;
        Thread th;
        volatile boolean on = false;
        InetAddress srv;
        int srvPort = 25001;
        int myId = -1;
        String name = myName;

        NetClient(String ip) {
            try {
                srv = InetAddress.getByName(ip);
            } catch (Exception e) {
                srv = null;
            }
        }

        boolean connect() {
            if (srv == null) return false;
            try {
                sock = new DatagramSocket();
                sock.setSoTimeout(3000);
                sock.connect(new InetSocketAddress(srv, srvPort));
                send("J:" + name);
                byte[] buf = new byte[512];
                DatagramPacket pk = new DatagramPacket(buf, buf.length);
                sock.receive(pk);
                String rep = new String(pk.getData(), 0, pk.getLength(), "UTF-8").trim();
                if (rep.startsWith("ID:")) {
                    myId = Integer.parseInt(rep.substring(3));
                    on = true;
                    th = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            byte[] b = new byte[1024];
                            while (on) {
                                try {
                                    DatagramPacket p = new DatagramPacket(b, b.length);
                                    sock.receive(p);
                                    handle(new String(p.getData(), 0, p.getLength(), "UTF-8").trim());
                                } catch (Exception e) {
                                    if (on) {
                                        try {
                                            Thread.sleep(50);
                                        } catch (InterruptedException ignored) {
                                        }
                                    }
                                }
                            }
                        }
                    });
                    th.setDaemon(true);
                    th.start();
                    return true;
                }
            } catch (Exception ignored) {
            }
            return false;
        }

        void send(String s) {
            try {
                byte[] d = s.getBytes("UTF-8");
                sock.send(new DatagramPacket(d, d.length, srv, srvPort));
            } catch (Exception ignored) {
            }
        }

        void handle(String msg) {
            if (msg.startsWith("P")) {
                try {
                    String[] t = msg.split(":");
                    int id = Integer.parseInt(t[0].substring(1));
                    if (id == myId) return;
                    float x = Float.parseFloat(t[1]);
                    float z = Float.parseFloat(t[2]);
                    float a = Float.parseFloat(t[3]);
                    float hp = Float.parseFloat(t[4]);
                    FpsEngine.Sprite s = remote.get(id);
                    if (s == null) {
                        s = new FpsEngine.Sprite();
                        s.id = id;
                        s.isPlayer = true;
                        s.color = id % 2 == 0 ? 0xFF1E88E5 : 0xFFFB8C00;
                        s.name = "好友" + id;
                        remote.put(id, s);
                        engine.sprites.add(s);
                    }
                    s.x = x;
                    s.z = z;
                    s.angle = a;
                    s.hp = hp;
                } catch (Exception ignored) {
                }
            } else if (msg.startsWith("JN:")) {
                try {
                    String[] t = msg.substring(3).split(":", 2);
                    int id = Integer.parseInt(t[0]);
                    String nm = t.length > 1 ? t[1] : "好友" + id;
                    FpsEngine.Sprite s = remote.get(id);
                    if (s == null) {
                        s = new FpsEngine.Sprite();
                        s.id = id;
                        s.isPlayer = true;
                        s.color = id % 2 == 0 ? 0xFF1E88E5 : 0xFFFB8C00;
                        s.name = nm;
                        remote.put(id, s);
                        engine.sprites.add(s);
                    }
                } catch (Exception ignored) {
                }
            } else if (msg.startsWith("D:")) {
                try {
                    onRemoteDead(Integer.parseInt(msg.substring(2)));
                } catch (Exception ignored) {
                }
            } else if (msg.startsWith("S:")) {
                try {
                    onShot(Integer.parseInt(msg.substring(2)));
                } catch (Exception ignored) {
                }
            }
        }

        void clear() {
            remote.clear();
        }

        void stop() {
            on = false;
            if (th != null) th.interrupt();
            if (sock != null) sock.close();
        }
    }
}
