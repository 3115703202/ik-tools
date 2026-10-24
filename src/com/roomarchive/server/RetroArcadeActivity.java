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

import java.util.LinkedList;
import java.util.Random;

/**
 * 怀旧街机 · 贪吃蛇：经典玩法，滑动/方向键控制，吃豆得分。
 */
public class RetroArcadeActivity extends BaseActivity {

    private static final int COLS = 16, ROWS = 28;
    private SnakeView sv;
    private TextView scoreView;
    private Handler handler = new Handler(Looper.getMainLooper());
    private LinkedList<int[]> snake = new LinkedList<>();
    private int dirX = 1, dirY = 0;      // 当前方向
    private int nextDirX = 1, nextDirY = 0;
    private int foodX, foodY;
    private int score = 0;
    private boolean running = false;
    private boolean gameOver = false;
    private Random rand = new Random();
    private Runnable ticker = new Runnable() {
        @Override
        public void run() {
            step();
            if (running) handler.postDelayed(this, 150);
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
        title.setText("怀旧街机 · 贪吃蛇");
        title.setTextSize(22);
        title.setTextColor(Tools.textColor(this));
        title.setGravity(Gravity.CENTER);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        Icons.attachToText(title, "arcade", Tools.textColor(this), 20, 8);
        root.addView(title);

        scoreView = new TextView(this);
        scoreView.setText("得分：0");
        scoreView.setTextSize(14);
        scoreView.setTextColor(Tools.subTextColor(this));
        scoreView.setGravity(Gravity.CENTER);
        root.addView(scoreView);

        sv = new SnakeView(this);
        root.addView(sv, new LinearLayout.LayoutParams(
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
        Button restart = new Button(this);
        restart.setText("重新开始");
        restart.setAllCaps(false);
        restart.setTextColor(Color.WHITE);
        Icons.attachToText(restart, "refresh", Color.WHITE, 16, 6);
        android.graphics.drawable.GradientDrawable g2 = new android.graphics.drawable.GradientDrawable();
        g2.setColor(Color.rgb(30, 64, 175));
        g2.setCornerRadius(dp(12));
        restart.setBackground(g2);
        row.addView(restart, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(row);

        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!running) {
                    startGame();
                }
            }
        });
        restart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startGame();
            }
        });

        TextView tip = new TextView(this);
        tip.setText("滑动屏幕控制方向 · 吃到红点加分 · 撞墙/撞自己结束");
        tip.setTextSize(11);
        tip.setTextColor(Tools.subTextColor(this));
        tip.setGravity(Gravity.CENTER);
        root.addView(tip);

        setContentView(root);
    }

    private void startGame() {
        running = true;
        gameOver = false;
        score = 0;
        snake.clear();
        snake.add(new int[]{COLS / 2, ROWS / 2});
        snake.add(new int[]{COLS / 2 - 1, ROWS / 2});
        dirX = 1;
        dirY = 0;
        nextDirX = 1;
        nextDirY = 0;
        spawnFood();
        scoreView.setText("得分：0");
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, 150);
        sv.invalidate();
    }

    private void spawnFood() {
        while (true) {
            foodX = rand.nextInt(COLS);
            foodY = rand.nextInt(ROWS);
            boolean onSnake = false;
            for (int[] p : snake) {
                if (p[0] == foodX && p[1] == foodY) {
                    onSnake = true;
                    break;
                }
            }
            if (!onSnake) break;
        }
    }

    private void step() {
        dirX = nextDirX;
        dirY = nextDirY;
        int headX = snake.get(0)[0] + dirX;
        int headY = snake.get(0)[1] + dirY;
        if (headX < 0 || headX >= COLS || headY < 0 || headY >= ROWS) {
            endGame("撞墙了！得分 " + score);
            return;
        }
        for (int[] p : snake) {
            if (p[0] == headX && p[1] == headY) {
                endGame("撞到自己了！得分 " + score);
                return;
            }
        }
        snake.add(0, new int[]{headX, headY});
        if (headX == foodX && headY == foodY) {
            score += 10;
            scoreView.setText("得分：" + score);
            spawnFood();
        } else {
            snake.removeLast();
        }
        sv.invalidate();
    }

    private void endGame(String msg) {
        running = false;
        gameOver = true;
        handler.removeCallbacks(ticker);
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        sv.invalidate();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(ticker);
        super.onDestroy();
    }

    /** 自绘贪吃蛇 */
    class SnakeView extends View {
        private Paint headPaint = new Paint();
        private Paint bodyPaint = new Paint();
        private Paint foodPaint = new Paint();

        SnakeView(Context ctx) {
            super(ctx);
            headPaint.setColor(Color.rgb(22, 101, 52));
            bodyPaint.setColor(Color.rgb(74, 180, 90));
            foodPaint.setColor(Color.rgb(220, 38, 38));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            float cell = Math.min(w / (float) COLS, h / (float) ROWS);
            float offX = (w - cell * COLS) / 2;
            float offY = (h - cell * ROWS) / 2;
            canvas.drawColor(Tools.isDark(RetroArcadeActivity.this) ? 0xFF161A22 : 0xFFF0F2F6);

            // 食物
            canvas.drawCircle(offX + foodX * cell + cell / 2, offY + foodY * cell + cell / 2,
                    cell * 0.32f, foodPaint);
            // 蛇
            for (int i = 0; i < snake.size(); i++) {
                int[] p = snake.get(i);
                Paint paint = i == 0 ? headPaint : bodyPaint;
                float cx = offX + p[0] * cell + cell / 2;
                float cy = offY + p[1] * cell + cell / 2;
                canvas.drawCircle(cx, cy, cell * 0.44f, paint);
            }
            if (!running && !gameOver) {
                Paint t = new Paint();
                t.setColor(Tools.textColor(RetroArcadeActivity.this));
                t.setTextSize(dp(16));
                t.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("点击「开始」进入游戏", w / 2f, h / 2f, t);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (!running) return true;
            if (e.getAction() == MotionEvent.ACTION_UP) {
                float x = e.getX(), y = e.getY();
                if (Math.abs(x - lastX) > Math.abs(y - lastY)) {
                    if (x > lastX && dirX != -1) {
                        nextDirX = 1;
                        nextDirY = 0;
                    } else if (x < lastX && dirX != 1) {
                        nextDirX = -1;
                        nextDirY = 0;
                    }
                } else {
                    if (y > lastY && dirY != -1) {
                        nextDirY = 1;
                        nextDirX = 0;
                    } else if (y < lastY && dirY != 1) {
                        nextDirY = -1;
                        nextDirX = 0;
                    }
                }
            } else if (e.getAction() == MotionEvent.ACTION_DOWN) {
                lastX = e.getX();
                lastY = e.getY();
            }
            return true;
        }

        private float lastX, lastY;
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
