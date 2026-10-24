package com.roomarchive.server;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * AI 五子棋：15×15 棋盘，玩家执黑先手，AI 白棋防守+进攻。
 * 纯本地算法，无联网。
 */
public class AIGobangActivity extends BaseActivity {

    private static final int N = 15;
    private int[][] board = new int[N][N]; // 0空 1黑 2白
    private boolean playerTurn = true;
    private boolean gameOver = false;
    private GobangView gv;
    private TextView status;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Tools.bgWithGlow(this));
        root.setPadding(dp(14), dp(18), dp(14), dp(14));

        TextView title = new TextView(this);
        title.setText("AI 五子棋");
        title.setTextSize(22);
        title.setTextColor(Tools.textColor(this));
        title.setGravity(Gravity.CENTER);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        Icons.attachToText(title, "gobang", Tools.textColor(this), 20, 8);
        root.addView(title);

        status = new TextView(this);
        status.setText("你执黑先手 · 点击棋盘落子");
        status.setTextSize(14);
        status.setTextColor(Tools.subTextColor(this));
        status.setGravity(Gravity.CENTER);
        root.addView(status);

        gv = new GobangView(this);
        root.addView(gv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        Button btn = new Button(this);
        btn.setText("重新开始");
        btn.setAllCaps(false);
        btn.setTextColor(Color.WHITE);
        Icons.attachToText(btn, "refresh", Color.WHITE, 16, 6);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.rgb(30, 64, 175));
        bg.setCornerRadius(dp(12));
        btn.setBackground(bg);
        root.addView(btn);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                reset();
            }
        });

        setContentView(root);
    }

    private void reset() {
        handler.removeCallbacksAndMessages(null);
        board = new int[N][N];
        playerTurn = true;
        gameOver = false;
        status.setText("你执黑先手 · 点击棋盘落子");
        gv.invalidate();
    }

    private void playerMove(int r, int c) {
        if (gameOver || !playerTurn) return;
        if (board[r][c] != 0) return;
        board[r][c] = 1;
        if (checkWin(r, c, 1)) {
            gameOver = true;
            status.setText("你赢了！");
            gv.invalidate();
            return;
        }
        if (isFull()) {
            gameOver = true;
            status.setText("平局");
            gv.invalidate();
            return;
        }
        playerTurn = false;
        status.setText("AI 思考中…");
        gv.invalidate();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                aiMove();
            }
        }, 60);
    }

    private void aiMove() {
        if (gameOver) return;
        int[] best = findBestMove();
        board[best[0]][best[1]] = 2;
        if (checkWin(best[0], best[1], 2)) {
            gameOver = true;
            status.setText("AI 赢了，再来一局？");
            gv.invalidate();
            return;
        }
        if (isFull()) {
            gameOver = true;
            status.setText("平局");
            gv.invalidate();
            return;
        }
        playerTurn = true;
        status.setText("轮到你落子");
        gv.invalidate();
    }

    private boolean isFull() {
        for (int i = 0; i < N; i++)
            for (int j = 0; j < N; j++)
                if (board[i][j] == 0) return false;
        return true;
    }

    /** 评估某一位置对某方的连子得分 */
    private int evaluate(int r, int c, int player) {
        int score = 0;
        int[][] dirs = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        for (int[] d : dirs) {
            int count = 1;
            int block = 0;
            // 正方向
            int rr = r + d[0], cc = c + d[1];
            while (rr >= 0 && rr < N && cc >= 0 && cc < N && board[rr][cc] == player) {
                count++;
                rr += d[0];
                cc += d[1];
            }
            if (rr < 0 || rr >= N || cc < 0 || cc >= N) block++;
            else if (board[rr][cc] != 0) block++;
            // 反方向
            rr = r - d[0];
            cc = c - d[1];
            while (rr >= 0 && rr < N && cc >= 0 && cc < N && board[rr][cc] == player) {
                count++;
                rr -= d[0];
                cc -= d[1];
            }
            if (rr < 0 || rr >= N || cc < 0 || cc >= N) block++;
            else if (board[rr][cc] != 0) block++;

            if (count >= 5) score += 100000;
            else if (block == 0) score += count * count * 100;
            else if (block == 1) score += count * count * 40;
        }
        return score;
    }

    /** 找出 AI 最佳落点：进攻 + 防守（防守权重略高） */
    private int[] findBestMove() {
        int bestScore = -1, br = 7, bc = 7;
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                if (board[i][j] != 0) continue;
                // 仅评估附近有棋子的位置，加速
                boolean near = false;
                for (int di = -2; di <= 2 && !near; di++) {
                    for (int dj = -2; dj <= 2; dj++) {
                        int ni = i + di, nj = j + dj;
                        if (ni >= 0 && ni < N && nj >= 0 && nj < N && board[ni][nj] != 0) {
                            near = true;
                            break;
                        }
                    }
                }
                if (!near && !(i == 7 && j == 7)) continue;
                int attack = evaluate(i, j, 2);
                int defend = evaluate(i, j, 1);
                int score = attack + defend * 11 / 10;   // 防守略优先
                if (score > bestScore) {
                    bestScore = score;
                    br = i;
                    bc = j;
                }
            }
        }
        return new int[]{br, bc};
    }

    private boolean checkWin(int r, int c, int player) {
        int[][] dirs = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        for (int[] d : dirs) {
            int count = 1;
            for (int k = 1; k < 5; k++) {
                int rr = r + d[0] * k, cc = c + d[1] * k;
                if (rr >= 0 && rr < N && cc >= 0 && cc < N && board[rr][cc] == player) count++;
                else break;
            }
            for (int k = 1; k < 5; k++) {
                int rr = r - d[0] * k, cc = c - d[1] * k;
                if (rr >= 0 && rr < N && cc >= 0 && cc < N && board[rr][cc] == player) count++;
                else break;
            }
            if (count >= 5) return true;
        }
        return false;
    }

    /** 自绘棋盘 */
    class GobangView extends View {
        private Paint linePaint = new Paint();
        private Paint blackPaint = new Paint();
        private Paint whitePaint = new Paint();
        private Paint lastPaint = new Paint();

        GobangView(Context ctx) {
            super(ctx);
            linePaint.setColor(Color.rgb(90, 74, 55));
            linePaint.setStrokeWidth(dp(1));
            blackPaint.setColor(Color.rgb(20, 20, 24));
            whitePaint.setColor(Color.WHITE);
            lastPaint.setColor(Color.RED);
            lastPaint.setStrokeWidth(dp(3));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            int side = Math.min(w, h);
            int pad = dp(24);
            float cell = (side - pad * 2f) / (N - 1);
            canvas.drawColor(Color.rgb(236, 220, 190));
            for (int i = 0; i < N; i++) {
                float x = pad + i * cell;
                canvas.drawLine(x, pad, x, pad + (N - 1) * cell, linePaint);
                canvas.drawLine(pad, pad + i * cell, pad + (N - 1) * cell, pad + i * cell, linePaint);
            }
            for (int i = 0; i < N; i++) {
                for (int j = 0; j < N; j++) {
                    float cx = pad + j * cell;
                    float cy = pad + i * cell;
                    if (board[i][j] == 1) {
                        canvas.drawCircle(cx, cy, cell * 0.42f, blackPaint);
                    } else if (board[i][j] == 2) {
                        canvas.drawCircle(cx, cy, cell * 0.42f, whitePaint);
                    }
                }
            }
            // 最后一手标记
            int lastR = -1, lastC = -1;
            for (int i = N - 1; i >= 0 && lastR < 0; i--) {
                for (int j = N - 1; j >= 0; j--) {
                    if (board[i][j] != 0) {
                        lastR = i;
                        lastC = j;
                        break;
                    }
                }
            }
            if (lastR >= 0) {
                float cx = pad + lastC * cell;
                float cy = pad + lastR * cell;
                canvas.drawCircle(cx, cy, dp(4), lastPaint);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_UP) {
                int w = getWidth(), h = getHeight();
                int side = Math.min(w, h);
                int pad = dp(24);
                float cell = (side - pad * 2f) / (N - 1);
                int c = Math.round((e.getX() - pad) / cell);
                int r = Math.round((e.getY() - pad) / cell);
                if (r >= 0 && r < N && c >= 0 && c < N) {
                    playerMove(r, c);
                }
            }
            return true;
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
