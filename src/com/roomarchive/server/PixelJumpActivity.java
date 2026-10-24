package com.roomarchive.server;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Random;

/**
 * 像素跳跃（经典横版跳跃玩法）：按住右侧起跳，躲避障碍物，距离越远得分越高。
 * 原创像素风实现。
 */
public class PixelJumpActivity extends BaseActivity {

    private JumpView jv;
    private TextView scoreView;
    private Handler handler = new Handler(Looper.getMainLooper());

    private float playerY = 0;          // 玩家 Y（0=地面）
    private float vy = 0;
    private float groundY;
    private float obstacleX = 0;
    private int score = 0;
    private boolean running = false;
    private boolean jumping = false;
    private Random rand = new Random();
    private long lastScoreTime = 0;

    private Runnable ticker = new Runnable() {
        @Override
        public void run() {
            update();
            if (running) handler.postDelayed(this, 16);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Tools.bgWithGlow(this));
        root.setPadding(dp(14), dp(18), dp(14), dp(14));

        TextView title = new TextView(this);
        title.setText("像素跳跃");
        title.setTextSize(22);
        title.setTextColor(Tools.textColor(this));
        title.setGravity(Gravity.CENTER);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        Icons.attachToText(title, "jump", Tools.textColor(this), 20, 8);
        root.addView(title);

        scoreView = new TextView(this);
        scoreView.setText("距离：0 m");
        scoreView.setTextSize(14);
        scoreView.setTextColor(Tools.subTextColor(this));
        scoreView.setGravity(Gravity.CENTER);
        root.addView(scoreView);

        jv = new JumpView(this);
        root.addView(jv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button start = new Button(this);
        start.setText("开始");
        start.setAllCaps(false);
        start.setTextColor(Color.WHITE);
        Icons.attachToText(start, "play", Color.WHITE, 16, 6);
        android.graphics.drawable.GradientDrawable g1 = new android.graphics.drawable.GradientDrawable();
        g1.setColor(Color.rgb(22, 163, 74));
        g1.setCornerRadius(dp(12));
        start.setBackground(g1);
        row.addView(start, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(spacerH(8));
        Button again = new Button(this);
        again.setText("再来一局");
        again.setAllCaps(false);
        again.setTextColor(Color.WHITE);
        Icons.attachToText(again, "refresh", Color.WHITE, 16, 6);
        android.graphics.drawable.GradientDrawable g2 = new android.graphics.drawable.GradientDrawable();
        g2.setColor(Color.rgb(30, 64, 175));
        g2.setCornerRadius(dp(12));
        again.setBackground(g2);
        row.addView(again, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(row);

        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!running) startGame();
            }
        });
        again.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startGame();
            }
        });

        TextView tip = new TextView(this);
        tip.setText("按住屏幕右侧区域起跳 · 跳过障碍物 · 撞到即结束");
        tip.setTextSize(11);
        tip.setTextColor(Tools.subTextColor(this));
        tip.setGravity(Gravity.CENTER);
        root.addView(tip);

        setContentView(root);
    }

    private void startGame() {
        running = true;
        playerY = 0;
        vy = 0;
        score = 0;
        obstacleX = jv.getWidth() > 0 ? jv.getWidth() : 800;
        lastScoreTime = System.currentTimeMillis();
        scoreView.setText("距离：0 m");
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, 16);
        jv.invalidate();
    }

    private void update() {
        if (!running) return;
        // 跳跃物理
        if (jumping) {
            vy = 34;    // 向上速度
        }
        vy -= 3.2f;     // 重力
        playerY += vy;
        if (playerY <= 0) {
            playerY = 0;
            vy = 0;
        }
        // 障碍移动
        float speed = 10 + Math.min(score / 500f, 6);
        obstacleX -= speed;
        if (obstacleX < -60) {
            obstacleX = jv.getWidth() + rand.nextInt(200) + 100;
            score += 1;
            scoreView.setText("距离：" + score + " m");
        }
        // 碰撞检测（宽约 36，玩家宽 32）
        float px = 70;   // 玩家固定 X
        float pw = dp(32), ph = dp(32);
        float ow = dp(30), oh = dp(46);
        float ox = obstacleX, oy = groundY - oh;
        if (ox < px + pw && ox + ow > px && oy < groundY && oy + oh > groundY - playerY - ph + dp(8)) {
            running = false;
            handler.removeCallbacks(ticker);
            Toast.makeText(this, "撞到障碍物！跑了 " + score + " 米", Toast.LENGTH_LONG).show();
        }
        jv.invalidate();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(ticker);
        super.onDestroy();
    }

    /** 自绘跳跃画面 */
    class JumpView extends View {
        private Paint playerPaint = new Paint();
        private Paint obstPaint = new Paint();
        private Paint groundPaint = new Paint();
        private Paint skyPaint = new Paint();

        JumpView(Context ctx) {
            super(ctx);
            playerPaint.setColor(Color.rgb(245, 158, 11));
            obstPaint.setColor(Color.rgb(220, 38, 38));
            groundPaint.setColor(Color.rgb(94, 68, 46));
            skyPaint.setColor(Color.rgb(135, 206, 250));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            groundY = h * 0.78f;
            canvas.drawRect(0, 0, w, h, skyPaint);
            canvas.drawRect(0, groundY, w, h, groundPaint);
            // 地面装饰
            Paint line = new Paint();
            line.setColor(Color.rgb(120, 90, 60));
            line.setStrokeWidth(dp(2));
            canvas.drawLine(0, groundY, w, groundY, line);

            // 障碍物
            canvas.drawRect(obstacleX, groundY - dp(46), obstacleX + dp(30), groundY, obstPaint);

            // 玩家（随跳跃起伏）
            float py = groundY - playerY - dp(32);
            canvas.drawRect(dp(54), py, dp(54) + dp(32), py + dp(32), playerPaint);
            // 眼睛
            Paint eye = new Paint();
            eye.setColor(Color.BLACK);
            canvas.drawCircle(dp(70), py + dp(8), dp(3), eye);
            canvas.drawCircle(dp(78), py + dp(8), dp(3), eye);

            if (!running) {
                Paint t = new Paint();
                t.setColor(Color.WHITE);
                t.setTextSize(dp(16));
                t.setTextAlign(Paint.Align.CENTER);
                t.setShadowLayer(dp(4), 0, 0, Color.rgb(0, 0, 60));
                canvas.drawText("点击「开始」跑起来！", w / 2f, h / 2f, t);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            // 点屏幕任意位置起跳
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                jumping = true;
            } else if (e.getAction() == MotionEvent.ACTION_UP) {
                jumping = false;
            }
            return true;
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private View spacerH(int w) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(w), 1));
        return v;
    }
}
