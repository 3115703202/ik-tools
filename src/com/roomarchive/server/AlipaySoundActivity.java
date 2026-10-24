package com.roomarchive.server;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * 到账提示音：输入金额，一键语音播报（整蛊/演示用）。
 * 使用系统 TTS 本地播报，不内置任何第三方音频。
 */
public class AlipaySoundActivity extends BaseActivity {

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private EditText amountInput;

    private final String[] QUICK = {"100", "1000", "520", "1314", "8888", "9999"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int r = tts.setLanguage(Locale.CHINA);
                    ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
                }
            }
        });
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(24));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("到账提示音");
        title.setTextSize(22);
        title.setTextColor(Tools.textColor(this));
        title.setGravity(Gravity.CENTER);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        Icons.attachToText(title, "alipay", Tools.textColor(this), 20, 8);
        root.addView(title);

        root.addView(spacer(6));

        TextView sub = new TextView(this);
        sub.setText("输入金额，一键播放「到账语音」（整蛊/演示）");
        sub.setTextSize(12);
        sub.setTextColor(Tools.subTextColor(this));
        sub.setGravity(Gravity.CENTER);
        root.addView(sub);

        root.addView(spacer(16));

        amountInput = new EditText(this);
        amountInput.setHint("输入金额，如 100");
        amountInput.setTextSize(40);
        amountInput.setTextColor(Color.rgb(30, 64, 175));
        amountInput.setGravity(Gravity.CENTER);
        amountInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        GradientDrawable inBg = new GradientDrawable();
        inBg.setColor(Tools.cardColor(this));
        inBg.setCornerRadius(dp(14));
        inBg.setStroke(dp(1), Tools.isDark(this) ? 0xFF2A3040 : 0xFFE4E8EE);
        amountInput.setBackground(inBg);
        amountInput.setPadding(dp(12), dp(14), dp(12), dp(14));
        root.addView(amountInput);

        root.addView(spacer(12));

        Button play = new Button(this);
        play.setText("播放到账提示音");
        play.setAllCaps(false);
        play.setTextColor(Color.WHITE);
        Icons.attachToText(play, "play", Color.WHITE, 16, 6);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.rgb(30, 64, 175));
        g.setCornerRadius(dp(14));
        play.setBackground(g);
        root.addView(play);
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playSound();
            }
        });

        root.addView(spacer(14));

        TextView q = new TextView(this);
        q.setText("快捷金额：");
        q.setTextSize(13);
        q.setTextColor(Tools.subTextColor(this));
        root.addView(q);

        LinearLayout quickRow = new LinearLayout(this);
        quickRow.setOrientation(LinearLayout.HORIZONTAL);
        quickRow.setGravity(Gravity.CENTER);
        for (final String s : QUICK) {
            TextView chip = new TextView(this);
            chip.setText("¥" + s);
            chip.setTextSize(14);
            chip.setTextColor(Color.rgb(30, 64, 175));
            chip.setGravity(Gravity.CENTER);
            GradientDrawable cbg = new GradientDrawable();
            cbg.setColor(Tools.cardColor(this));
            cbg.setCornerRadius(dp(16));
            cbg.setStroke(dp(1), Color.rgb(190, 200, 225));
            chip.setPadding(dp(14), dp(8), dp(14), dp(8));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(42));
            clp.setMargins(dp(3), 0, dp(3), 0);
            chip.setLayoutParams(clp);
            chip.setClickable(true);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    amountInput.setText(s);
                }
            });
            quickRow.addView(chip);
        }
        root.addView(quickRow);

        root.addView(spacer(16));

        TextView tip = new TextView(this);
        tip.setText("说明：\n· 播报文案为「支付宝到账，xx元」，使用系统语音本地合成\n· 纯娱乐演示功能，不识别、不采集任何真实收款信息\n· 首次播报需等待语音引擎初始化约 1 秒");
        tip.setTextSize(11);
        tip.setTextColor(Tools.subTextColor(this));
        root.addView(tip);

        root.setBackground(Tools.bgWithGlow(this));
        setContentView(scroll);
    }

    private void playSound() {
        String amt = amountInput.getText().toString().trim();
        if (amt.isEmpty()) {
            Toast.makeText(this, "请先输入金额", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!ttsReady) {
            Toast.makeText(this, "语音引擎未就绪，请稍后再试", Toast.LENGTH_SHORT).show();
            return;
        }
        String speech = "支付宝到账，" + amt + "元";
        tts.speak(speech, TextToSpeech.QUEUE_FLUSH, null, "ik_alipay");
        Toast.makeText(this, "正在播报：支付宝到账 " + amt + " 元", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
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
