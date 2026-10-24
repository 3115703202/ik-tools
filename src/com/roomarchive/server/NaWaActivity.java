package com.roomarchive.server;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 奶蛙：养成小青蛙宠物——喂食 / 玩耍 / 睡觉，状态随时间变化，本地保存。
 */
public class NaWaActivity extends BaseActivity {

    private static final String SP = "naWa_state";

    private ImageView frogFace;
    private TextView statusText, statText;
    private Drawable[] faces;
    private Handler handler = new Handler(Looper.getMainLooper());

    private int hunger = 80;   // 饱食度 0-100
    private int mood = 80;     // 心情 0-100
    private int energy = 80;   // 精力 0-100
    private long lastTime = 0;
    private boolean sleeping = false;
    private Button sleepBtn;

    private Runnable ticker = new Runnable() {
        @Override
        public void run() {
            decay();
            updateUi();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        load();
        buildUi();
        handler.postDelayed(ticker, 1000);
    }

    private void load() {
        android.content.SharedPreferences sp = getSharedPreferences(SP, MODE_PRIVATE);
        hunger = sp.getInt("hunger", 80);
        mood = sp.getInt("mood", 80);
        energy = sp.getInt("energy", 80);
        lastTime = sp.getLong("last", System.currentTimeMillis());
        sleeping = sp.getBoolean("sleep", false);
        // 离线衰减：每 10 分钟 -1
        long mins = (System.currentTimeMillis() - lastTime) / 60000;
        if (mins > 0) {
            hunger = Math.max(0, hunger - (int) (mins / 10));
            mood = Math.max(0, mood - (int) (mins / 12));
            energy = Math.max(0, energy - (int) (mins / 15));
        }
    }

    private void save() {
        getSharedPreferences(SP, MODE_PRIVATE).edit()
                .putInt("hunger", hunger)
                .putInt("mood", mood)
                .putInt("energy", energy)
                .putLong("last", System.currentTimeMillis())
                .putBoolean("sleep", sleeping)
                .apply();
    }

    private void decay() {
        if (!sleeping) {
            hunger = Math.max(0, hunger - 1);
            mood = Math.max(0, mood - 1);
            energy = Math.max(0, energy - 2);
        } else {
            energy = Math.min(100, energy + 3);
        }
        if (hunger <= 0 || mood <= 0) {
            energy = Math.max(0, energy - 2);
        }
    }

    private void updateUi() {
        int state;
        if (sleeping) {
            state = 1;
            statusText.setText("奶蛙睡着啦…（精力恢复中）");
        } else if (hunger <= 15) {
            state = 2;
            statusText.setText("奶蛙饿坏了，快喂它！");
        } else if (mood <= 15) {
            state = 3;
            statusText.setText("奶蛙不开心，陪它玩玩吧");
        } else if (energy <= 15) {
            state = 4;
            statusText.setText("奶蛙困了，让它睡觉吧");
        } else {
            state = 0;
            statusText.setText("奶蛙今天心情不错～");
        }
        if (frogFace != null && faces != null) frogFace.setImageDrawable(faces[state]);
        statText.setText("饱食 " + hunger + " · 心情 " + mood + " · 精力 " + energy);
        save();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Tools.bgWithGlow(this));
        root.setPadding(dp(18), dp(18), dp(18), dp(18));

        TextView title = new TextView(this);
        title.setText("奶蛙");
        title.setTextSize(22);
        title.setTextColor(Tools.textColor(this));
        title.setGravity(Gravity.CENTER);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        Icons.attachToText(title, "frog", Tools.textColor(this), 20, 8);
        root.addView(title);

        root.addView(spacer(14));

        // 5 种表情状态：0 正常 / 1 睡觉 / 2 委屈(饿) / 3 难过 / 4 困倦
        faces = new Drawable[]{
                new FrogFaceDrawable(0), new FrogFaceDrawable(1), new FrogFaceDrawable(2),
                new FrogFaceDrawable(3), new FrogFaceDrawable(4)};
        frogFace = new ImageView(this);
        frogFace.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        frogFace.setLayoutParams(new LinearLayout.LayoutParams(dp(140), dp(140)));
        frogFace.setAdjustViewBounds(true);
        root.addView(frogFace);

        root.addView(spacer(8));

        statusText = new TextView(this);
        statusText.setTextSize(14);
        statusText.setTextColor(Tools.textColor(this));
        statusText.setGravity(Gravity.CENTER);
        root.addView(statusText);

        root.addView(spacer(8));

        statText = new TextView(this);
        statText.setTextSize(13);
        statText.setTextColor(Tools.subTextColor(this));
        statText.setGravity(Gravity.CENTER);
        root.addView(statText);

        root.addView(spacer(18));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER);
        row1.addView(actionBtn("喂食", Color.rgb(234, 88, 12), new Runnable() {
            @Override
            public void run() {
                hunger = Math.min(100, hunger + 25);
                mood = Math.min(100, mood + 5);
                updateUi();
            }
        }));
        row1.addView(spacerH(10));
        row1.addView(actionBtn("玩耍", Color.rgb(30, 64, 175), new Runnable() {
            @Override
            public void run() {
                mood = Math.min(100, mood + 25);
                energy = Math.max(0, energy - 8);
                hunger = Math.max(0, hunger - 5);
                updateUi();
            }
        }));
        root.addView(row1);

        root.addView(spacer(12));

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER);
        sleepBtn = actionBtn(sleeping ? "叫醒" : "睡觉", Color.rgb(8, 145, 178), new Runnable() {
            @Override
            public void run() {
                sleeping = !sleeping;
                if (sleepBtn != null) sleepBtn.setText(sleeping ? "叫醒" : "睡觉");
                updateUi();
            }
        });
        row2.addView(sleepBtn);
        row2.addView(spacerH(10));
        row2.addView(actionBtn("洗澡", Color.rgb(22, 163, 74), new Runnable() {
            @Override
            public void run() {
                mood = Math.min(100, mood + 15);
                energy = Math.max(0, energy - 5);
                updateUi();
            }
        }));
        root.addView(row2);

        root.addView(spacer(18));

        TextView tip = new TextView(this);
        tip.setText("奶蛙会随时间慢慢饿/闷/困\n（每 10 分钟离线也会掉状态）\n记得常来照顾它！");
        tip.setTextSize(11);
        tip.setTextColor(Tools.subTextColor(this));
        tip.setGravity(Gravity.CENTER);
        root.addView(tip);

        updateUi();
        setContentView(root);
    }

    private Button actionBtn(String txt, int color, final Runnable r) {
        Button b = new Button(this);
        b.setText(txt);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(12));
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(150), dp(52));
        b.setLayoutParams(lp);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                r.run();
            }
        });
        return b;
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(ticker);
        save();
        super.onDestroy();
    }

    /**
     * 奶蛙表情 Drawable（代码绘制，100x100 坐标系）：
     * state 0 正常=圆眼+微笑；1 睡觉=闭眼线+小嘴；2 委屈(饿)=圆眼+泪点+撇嘴；
     * 3 难过=圆眼+下垂嘴；4 困倦=半闭眼+哈欠张嘴。
     */
    private static class FrogFaceDrawable extends Drawable {
        private final int state;
        private final Paint face = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dark = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mouth = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint tear = new Paint(Paint.ANTI_ALIAS_FLAG);

        FrogFaceDrawable(int state) {
            this.state = state;
            face.setColor(0xFF43A047);
            dark.setColor(Color.rgb(28, 60, 32));
            mouth.setColor(Color.rgb(28, 60, 32));
            mouth.setStyle(Paint.Style.STROKE);
            mouth.setStrokeWidth(5f);
            mouth.setStrokeCap(Paint.Cap.ROUND);
            tear.setColor(Color.rgb(120, 190, 255));
        }

        @Override
        public void draw(Canvas canvas) {
            float w = getBounds().width(), h = getBounds().height();
            if (w <= 0 || h <= 0) return;
            canvas.save();
            canvas.scale(w / 100f, h / 100f);
            // 脸 + 两个眼包
            canvas.drawCircle(50, 60, 34, face);
            canvas.drawCircle(30, 26, 12, face);
            canvas.drawCircle(70, 26, 12, face);
            if (state == 1) {
                // 睡觉：两条闭合眼线
                canvas.drawLine(24, 26, 36, 26, mouth);
                canvas.drawLine(64, 26, 76, 26, mouth);
                canvas.drawLine(42, 72, 58, 72, mouth);
            } else if (state == 4) {
                // 困倦：半闭眼 + 哈欠张嘴
                canvas.drawLine(24, 24, 36, 24, mouth);
                canvas.drawLine(64, 24, 76, 24, mouth);
                canvas.drawCircle(50, 70, 8, dark);
            } else {
                // 圆眼睛
                canvas.drawCircle(30, 26, 4.5f, dark);
                canvas.drawCircle(70, 26, 4.5f, dark);
                if (state == 2) {
                    // 委屈：泪点 + 撇嘴
                    canvas.drawCircle(28, 38, 2.5f, tear);
                    canvas.drawLine(42, 74, 58, 70, mouth);
                } else if (state == 3) {
                    // 难过：下垂嘴
                    canvas.drawArc(new android.graphics.RectF(38, 62, 62, 84), 200, 140, false, mouth);
                } else {
                    // 正常：微笑
                    canvas.drawArc(new android.graphics.RectF(38, 58, 62, 80), 20, 140, false, mouth);
                }
            }
            canvas.restore();
        }

        @Override
        public void setAlpha(int alpha) {
            face.setAlpha(alpha); dark.setAlpha(alpha); mouth.setAlpha(alpha); tear.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter cf) { }

        @Override
        public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private View spacer(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        return v;
    }

    private View spacerH(int w) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(w), 1));
        return v;
    }
}
