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

/**
 * Duo 双人弹球（Pong 双人版）：两名玩家同屏对战，左边上滑/下滑，右边上滑/下滑。
 */
public class DuoActivity extends BaseActivity {

    private PongView pv;
    private TextView scoreView;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean running = false;

    private float leftY = 0, rightY = 0;   // 挡板中心（0-1）
    private float ballX = 0.5f, ballY = 0.5f;
    private float ballVx = 0.006f, ballVy = 0.004f;
    private int scoreL = 0, scoreR = 0;

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

        TextView title = Tools.pageTitle(this, "Duo 双人弹球");
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        scoreView = new TextView(this);
        scoreView.setText("左 0 : 0 右");
        scoreView.setTextSize(16);
        scoreView.setTextColor(Tools.textColor(this));
        scoreView.setGravity(Gravity.CENTER);
        root.addView(scoreView);

        pv = new PongView(this);
        root.addView(pv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button start = new Button(this);
        start.setText("开始");
        start.setAllCaps(false);
        start.setTextColor(Color.WHITE);
        start.setBackground(Tools.pillButton(this, 50, true));
        row.addView(start, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(spacerH(8));
        Button reset = new Button(this);
        reset.setText("重新开始");
        reset.setAllCaps(false);
        reset.setTextColor(Tools.accentColor(this));
        android.graphics.drawable.GradientDrawable g2 = new android.graphics.drawable.GradientDrawable();
        int ac = Tools.accentColor(this);
        g2.setColor((0x1F << 24) | (ac & 0x00FFFFFF));
        g2.setCornerRadius(Tools.dp(this, 25));
        reset.setBackground(g2);
        row.addView(reset, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(row);

        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!running) startGame();
            }
        });
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scoreL = 0;
                scoreR = 0;
                startGame();
            }
        });

        TextView tip = new TextView(this);
        tip.setText("左边玩家：触摸左半屏上下移动\n右边玩家：触摸右半屏上下移动\n先到 5 分获胜");
        tip.setTextSize(11);
        tip.setTextColor(Tools.subTextColor(this));
        tip.setGravity(Gravity.CENTER);
        root.addView(tip);

        setContentView(root);
    }

    private void startGame() {
        running = true;
        ballX = 0.5f;
        ballY = 0.5f;
        ballVx = (Math.random() < 0.5 ? -1 : 1) * 0.006f;
        ballVy = (Math.random() < 0.5 ? -1 : 1) * 0.004f;
        scoreView.setText("左 " + scoreL + " : " + scoreR + " 右");
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, 16);
        pv.invalidate();
    }

    private void update() {
        // 球移动
        ballX += ballVx;
        ballY += ballVy;
        // 上下反弹
        if (ballY < 0.04f || ballY > 0.96f) ballVy = -ballVy;
        // 左挡板碰撞（x ≈ 0.06）
        if (ballX < 0.10f && Math.abs(ballY - leftY) < 0.13f && ballVx < 0) {
            ballVx = -ballVx;
            ballVx *= 1.02f;
        }
        // 右挡板碰撞（x ≈ 0.94）
        if (ballX > 0.90f && Math.abs(ballY - rightY) < 0.13f && ballVx > 0) {
            ballVx = -ballVx;
            ballVx *= 1.02f;
        }
        // 得分判定
        if (ballX < -0.02f) {
            scoreR++;
            if (scoreR >= 5) {
                running = false;
                handler.removeCallbacks(ticker);
                Toast.makeText(this, "右边玩家获胜！", Toast.LENGTH_LONG).show();
            } else {
                ballX = 0.5f;
                ballY = 0.5f;
                ballVx = 0.006f;
                ballVy = (Math.random() < 0.5 ? -1 : 1) * 0.004f;
            }
        } else if (ballX > 1.02f) {
            scoreL++;
            if (scoreL >= 5) {
                running = false;
                handler.removeCallbacks(ticker);
                Toast.makeText(this, "左边玩家获胜！", Toast.LENGTH_LONG).show();
            } else {
                ballX = 0.5f;
                ballY = 0.5f;
                ballVx = -0.006f;
                ballVy = (Math.random() < 0.5 ? -1 : 1) * 0.004f;
            }
        }
        scoreView.setText("左 " + scoreL + " : " + scoreR + " 右");
        pv.invalidate();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(ticker);
        super.onDestroy();
    }

    /** 自绘 Pong */
    class PongView extends View {
        private Paint ballPaint = new Paint();
        private Paint paddlePaint = new Paint();
        private Paint midPaint = new Paint();

        PongView(Context ctx) {
            super(ctx);
            ballPaint.setColor(Color.WHITE);
            paddlePaint.setColor(Color.rgb(30, 64, 175));
            midPaint.setColor(Color.rgb(200, 210, 230));
            midPaint.setStrokeWidth(dp(1));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            canvas.drawColor(Tools.isDark(DuoActivity.this) ? 0xFF10141C : 0xFF1E293B);
            // 中线
            for (int y = 0; y < h; y += dp(16)) {
                canvas.drawRect(w / 2f - dp(1), y, w / 2f + dp(1), y + dp(8), midPaint);
            }
            // 左右挡板
            float paddleH = h * 0.22f;
            canvas.drawRect(dp(8), leftY * h - paddleH / 2, dp(16), leftY * h + paddleH / 2, paddlePaint);
            canvas.drawRect(w - dp(16), rightY * h - paddleH / 2, w - dp(8), rightY * h + paddleH / 2, paddlePaint);
            // 球
            canvas.drawCircle(ballX * w, ballY * h, dp(9), ballPaint);
            if (!running) {
                Paint t = new Paint();
                t.setColor(Color.WHITE);
                t.setTextSize(dp(14));
                t.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("点击「开始」对战", w / 2f, h / 2f, t);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float y = e.getY() / (float) getHeight();
            if (e.getX() < getWidth() / 2f) {
                leftY = Math.max(0.11f, Math.min(0.89f, y));
            } else {
                rightY = Math.max(0.11f, Math.min(0.89f, y));
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
