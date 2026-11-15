package com.roomarchive.server;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.net.Uri;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * 模拟通话 · 悬浮来电界面。
 * 不注册 PhoneAccount、不申请系统通话权限，纯 Activity + 铃声/震动 + TTS，
 * 从根上绕开 “PhoneAccountHandle is not enabled for this user” 的 SecurityException。
 */
public class SimCallActivity extends Activity implements TextToSpeech.OnInitListener {

    private FrameLayout root;
    private LinearLayout setupLayer;
    private FrameLayout callLayer;
    private EditText etNumber, etName;
    private TextView callName, callNumber, callState, avatar, btnMute, btnSpeaker, accountState;
    private String pendingCallName = "张三", pendingCallNumber = "13800138000";
    private int delaySec = 0;
    private boolean ringing = false, connected = false, muted = false, speaker = false;
    private long connectAt = 0;
    private Ringtone ringtone;
    private Vibrator vibrator;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (connected) {
                long s = (System.currentTimeMillis() - connectAt) / 1000;
                callState.setText(String.format(Locale.CHINA, "通话中  %02d:%02d", s / 60, s % 60));
                ui.postDelayed(this, 1000);
            }
        }
    };
    private final Runnable startRunnable = new Runnable() {
        @Override public void run() { showIncoming(); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Tools.applyStatusBar(this);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        tts = new TextToSpeech(this, this);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        root = new FrameLayout(this);
        root.setBackground(Tools.bgWithGlow(this));
        buildSetup();
        buildCallLayer();
        setContentView(root);
        if (getIntent() != null && getIntent().getBooleanExtra("incoming", false)) {
            String n = getIntent().getStringExtra("name");
            String num = getIntent().getStringExtra("number");
            if (n != null && !n.isEmpty()) pendingCallName = n;
            if (num != null && !num.isEmpty()) pendingCallNumber = num;
            showIncomingUi(pendingCallName, pendingCallNumber);
        }
    }

    private void buildSetup() {
        setupLayer = new LinearLayout(this);
        setupLayer.setOrientation(LinearLayout.VERTICAL);
        setupLayer.setPadding(dp(20), dp(24), dp(20), dp(24));

        TextView title = new TextView(this);
        title.setText("模拟通话");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        setupLayer.addView(title);

        TextView sub = new TextView(this);
        sub.setText("悬浮来电界面 · 铃声 / 接听 / 挂断 · 仅供娱乐");
        sub.setTextColor(0xFF9FB3C8);
        sub.setTextSize(12);
        sub.setPadding(0, dp(4), 0, dp(16));
        setupLayer.addView(sub);

        accountState = new TextView(this);
        accountState.setTextSize(11);
        accountState.setTextColor(0xFF7E93AA);
        accountState.setPadding(0, 0, 0, dp(4));
        setupLayer.addView(accountState);
        refreshAccountState();

        setupLayer.addView(label("来电号码"));
        etNumber = new EditText(this);
        etNumber.setText("13800138000");
        etNumber.setTextColor(Color.WHITE);
        etNumber.setTextSize(16);
        etNumber.setInputType(InputType.TYPE_CLASS_PHONE);
        etNumber.setSingleLine(true);
        etNumber.setBackground(glass(0x22FFFFFF, 16));
        etNumber.setPadding(dp(14), dp(12), dp(14), dp(12));
        setupLayer.addView(etNumber, lp(-1, -2, 0, 8));

        setupLayer.addView(label("来电显示名称"));
        etName = new EditText(this);
        etName.setText("张三");
        etName.setHint("如：张三 / 中国移动（可留空）");
        etName.setHintTextColor(0xFF6E829A);
        etName.setTextColor(Color.WHITE);
        etName.setTextSize(16);
        etName.setSingleLine(true);
        etName.setBackground(glass(0x22FFFFFF, 16));
        etName.setPadding(dp(14), dp(12), dp(14), dp(12));
        setupLayer.addView(etName, lp(-1, -2, 0, 8));

        setupLayer.addView(label("来电时间"));
        LinearLayout delays = new LinearLayout(this);
        delays.setOrientation(LinearLayout.HORIZONTAL);
        String[] ds = {"立即", "5秒", "15秒", "30秒"};
        int[] dv = {0, 5, 15, 30};
        for (int i = 0; i < ds.length; i++) {
            final int idx = i;
            TextView chip = new TextView(this);
            chip.setText(ds[i]);
            chip.setTextSize(13);
            chip.setTextColor(Color.WHITE);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(0, dp(9), 0, dp(9));
            chip.setBackground(glass(i == 0 ? 0xFF2563EB : 0x22FFFFFF, 14));
            chip.setOnClickListener(v -> {
                delaySec = dv[idx];
                for (int k = 0; k < delays.getChildCount(); k++) {
                    delays.getChildAt(k).setBackground(glass(k == idx ? 0xFF2563EB : 0x22FFFFFF, 14));
                }
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1);
            cp.rightMargin = dp(6);
            delays.addView(chip, cp);
        }
        setupLayer.addView(delays);

        TextView note = new TextView(this);
        note.setText("会真实注册 PhoneAccount（self-managed），并通过系统 Telecom 发起来电；\n系统状态栏会进入来电状态，接听/挂断走 Telecom 回调，但不会真的拨号。");
        note.setTextColor(0xFF7E93AA);
        note.setTextSize(11);
        note.setPadding(0, dp(14), 0, dp(18));
        setupLayer.addView(note);

        TextView start = new TextView(this);
        start.setText("开始模拟来电");
        start.setTextColor(Color.WHITE);
        start.setTextSize(17);
        start.setTypeface(Typeface.DEFAULT_BOLD);
        start.setGravity(Gravity.CENTER);
        start.setPadding(0, dp(16), 0, dp(16));
        start.setBackground(glass(0xFF16A34A, 18));
        start.setOnClickListener(v -> startCall());
        setupLayer.addView(start, lp(-1, -2, 0, 8));

        TextView back = new TextView(this);
        back.setText("返回工具箱");
        back.setTextColor(0xFF9FB3C8);
        back.setTextSize(13);
        back.setGravity(Gravity.CENTER);
        back.setPadding(0, dp(12), 0, dp(12));
        back.setOnClickListener(v -> finish());
        setupLayer.addView(back);

        root.addView(setupLayer, new FrameLayout.LayoutParams(-1, -1));
    }

    private void buildCallLayer() {
        callLayer = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF071426, 0xFF0B2038, 0xFF040B14});
        callLayer.setBackground(bg);
        callLayer.setVisibility(View.GONE);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(24), dp(90), dp(24), dp(36));

        avatar = new TextView(this);
        avatar.setText("张");
        avatar.setTextColor(Color.WHITE);
        avatar.setTextSize(40);
        avatar.setTypeface(Typeface.DEFAULT_BOLD);
        avatar.setGravity(Gravity.CENTER);
        GradientDrawable av = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF38BDF8, 0xFF2563EB, 0xFF7C3AED});
        av.setShape(GradientDrawable.OVAL);
        avatar.setBackground(av);
        content.addView(avatar, new LinearLayout.LayoutParams(dp(110), dp(110)));
        ((LinearLayout.LayoutParams) avatar.getLayoutParams()).bottomMargin = dp(18);

        callName = new TextView(this);
        callName.setText("张三");
        callName.setTextColor(Color.WHITE);
        callName.setTextSize(30);
        callName.setTypeface(Typeface.DEFAULT_BOLD);
        callName.setGravity(Gravity.CENTER);
        content.addView(callName);

        callNumber = new TextView(this);
        callNumber.setText("13800138000");
        callNumber.setTextColor(0xFF9FB3C8);
        callNumber.setTextSize(14);
        callNumber.setGravity(Gravity.CENTER);
        callNumber.setPadding(0, dp(6), 0, dp(10));
        content.addView(callNumber);

        callState = new TextView(this);
        callState.setText("来电中…");
        callState.setTextColor(0xFF7DD3FC);
        callState.setTextSize(15);
        callState.setGravity(Gravity.CENTER);
        content.addView(callState);

        TextView hint = new TextView(this);
        hint.setText("滑动或点击接听 · 纯本地模拟，不会产生真实通话");
        hint.setTextColor(0xFF5F748C);
        hint.setTextSize(11);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(14), 0, 0);
        content.addView(hint);

        FrameLayout spacer = new FrameLayout(this);
        content.addView(spacer, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout callBtns = new LinearLayout(this);
        callBtns.setOrientation(LinearLayout.HORIZONTAL);
        callBtns.setGravity(Gravity.CENTER);

        TextView decline = roundBtn("挂断", 0xFFDC2626, "✕");
        decline.setOnClickListener(v -> hangUp());
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(92), dp(92));
        dlp.rightMargin = dp(48);
        callBtns.addView(decline, dlp);

        TextView accept = roundBtn("接听", 0xFF16A34A, "✓");
        accept.setOnClickListener(v -> acceptCall());
        callBtns.addView(accept, new LinearLayout.LayoutParams(dp(92), dp(92)));
        content.addView(callBtns);

        LinearLayout toolsRow = new LinearLayout(this);
        toolsRow.setOrientation(LinearLayout.HORIZONTAL);
        toolsRow.setGravity(Gravity.CENTER);
        toolsRow.setPadding(0, dp(28), 0, 0);
        btnMute = smallBtn("静音");
        btnMute.setOnClickListener(v -> { muted = !muted; btnMute.setText(muted ? "已静音" : "静音"); });
        btnSpeaker = smallBtn("免提");
        btnSpeaker.setOnClickListener(v -> { speaker = !speaker; btnSpeaker.setText(speaker ? "免提开" : "免提"); });
        toolsRow.addView(btnMute);
        toolsRow.addView(btnSpeaker);
        content.addView(toolsRow);

        callLayer.addView(content, new FrameLayout.LayoutParams(-1, -1));
        root.addView(callLayer, new FrameLayout.LayoutParams(-1, -1));
    }

    private void startCall() {
        String num = etNumber.getText().toString().trim();
        String name = etName.getText().toString().trim();
        if (num.isEmpty()) num = "未知号码";
        if (name.isEmpty()) name = num;
        pendingCallName = name;
        pendingCallNumber = num;
        callName.setText(name);
        callNumber.setText(num);
        avatar.setText(name.substring(0, 1));
        final String fn = name, fnum = num;
        if (delaySec > 0) {
            accountState.setText(delaySec + " 秒后通过 Telecom 发起来电…");
            ui.postDelayed(() -> placeRealCall(fn, fnum), delaySec * 1000L);
        } else {
            placeRealCall(fn, fnum);
        }
    }

    /** 注册 PhoneAccount 并用 TelecomManager.addNewIncomingCall 发起真实来电 */
    private void placeRealCall(String name, String number) {
        if (Build.VERSION.SDK_INT < 23) { showIncomingUi(name, number); return; }
        try {
            TelecomManager tm = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
            PhoneAccountHandle h = SimCallConnectionService.handle(this);
            PhoneAccount acc = tm.getPhoneAccount(h);
            if (acc == null) {
                PhoneAccount.Builder b = PhoneAccount.builder(h, "IK模拟通话")
                        .setShortDescription("模拟来电 · 仅供娱乐")
                        .setSupportedUriSchemes(java.util.Collections.singletonList("tel"));
                if (Build.VERSION.SDK_INT >= 26) {
                    b.setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED);
                } else {
                    b.setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER);
                }
                tm.registerPhoneAccount(b.build());
                acc = tm.getPhoneAccount(h);
            }
            SimCallConnectionService.setPending(name, number);
            android.os.Bundle extras = new android.os.Bundle();
            extras.putString("ik_name", name);
            extras.putString("ik_number", number);
            extras.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS,
                    Uri.fromParts("tel", number, null));
            tm.addNewIncomingCall(h, extras);
            accountState.setTextColor(0xFF16A34A);
            accountState.setText("通话账户已注册 · 正在通过 Telecom 发起来电");
            refreshAccountState();
        } catch (Throwable e) {
            accountState.setTextColor(0xFFDC2626);
            accountState.setText("通话账户调用失败：" + e.getClass().getSimpleName()
                    + " · " + e.getMessage() + "（已切到本机悬浮界面）");
            Toast.makeText(this, "Telecom 拒绝，已切到本机悬浮界面", Toast.LENGTH_LONG).show();
            showIncomingUi(name, number);
        }
    }

    private void refreshAccountState() {
        if (accountState == null) return;
        try {
            TelecomManager tm = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
            PhoneAccount acc = tm.getPhoneAccount(SimCallConnectionService.handle(this));
            if (acc == null) {
                accountState.setTextColor(0xFF7E93AA);
                accountState.setText("通话账户：未注册（点开始后自动注册）");
            } else if (acc.isEnabled()) {
                accountState.setTextColor(0xFF16A34A);
                accountState.setText("通话账户：已注册 · 已启用（系统来电通道）");
            } else {
                accountState.setTextColor(0xFFF59E0B);
                accountState.setText("通话账户：已注册 · 未启用，需到系统「通话账户」里打开");
            }
        } catch (Throwable t) {
            accountState.setTextColor(0xFFDC2626);
            accountState.setText("通话账户状态读取失败：" + t.getClass().getSimpleName());
        }
    }

    private void showIncomingUi(String name, String number) {
        if (name == null || name.isEmpty()) name = number != null ? number : "未知号码";
        callName.setText(name);
        callNumber.setText(number == null ? "" : number);
        avatar.setText(name.substring(0, 1));
        showIncoming();
    }

    private void showIncoming() {
        setupLayer.setVisibility(View.GONE);
        callLayer.setVisibility(View.VISIBLE);
        callState.setText("来电中…");
        ringing = true;
        playRing();
        vibrate(true);
        ui.postDelayed(() -> {
            if (ringing) callState.setText("未接来电 · 仍在响铃");
        }, 18000);
    }

    private void acceptCall() {
        ringing = false;
        stopRing();
        vibrate(false);
        connected = true;
        try { SimCallConnectionService.answer(); } catch (Throwable ignored) {}
        connectAt = System.currentTimeMillis();
        callNumber.setVisibility(View.GONE);
        callState.setText("通话中  00:00");
        ui.removeCallbacks(tick);
        ui.post(tick);
        speak("喂，你好，我这边信号有点不太好，你再说一遍。");
        Toast.makeText(this, "已接听（模拟）", Toast.LENGTH_SHORT).show();
    }

    private void hangUp() {
        ringing = false;
        connected = false;
        stopRing();
        vibrate(false);
        ui.removeCallbacks(tick);
        try { SimCallConnectionService.disconnect(); } catch (Throwable ignored) {}
        Toast.makeText(this, "已挂断（模拟）", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void playRing() {
        try {
            if (ringtone == null) {
                ringtone = RingtoneManager.getRingtone(this,
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
                if (Build.VERSION.SDK_INT >= 21) {
                    ringtone.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build());
                }
            }
            if (ringtone != null) ringtone.play();
        } catch (Throwable ignored) {}
    }

    private void stopRing() {
        try { if (ringtone != null) ringtone.stop(); } catch (Throwable ignored) {}
    }

    private void vibrate(boolean on) {
        try {
            if (vibrator == null || !vibrator.hasVibrator()) return;
            if (on) {
                long[] pattern = {0, 800, 600, 800, 600};
                if (Build.VERSION.SDK_INT >= 26) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
                } else {
                    vibrator.vibrate(pattern, 0);
                }
            } else {
                vibrator.cancel();
            }
        } catch (Throwable ignored) {}
    }

    private void speak(String text) {
        if (ttsReady) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "simcall");
        }
    }

    @Override public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            try {
                tts.setLanguage(Locale.CHINESE);
                ttsReady = true;
            } catch (Throwable ignored) {}
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getBooleanExtra("incoming", false)) {
            String n = intent.getStringExtra("name");
            String num = intent.getStringExtra("number");
            showIncomingUi(n, num);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        refreshAccountState();
    }

    @Override protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        stopRing();
        vibrate(false);
        if (tts != null) { try { tts.stop(); tts.shutdown(); } catch (Throwable ignored) {} }
        super.onDestroy();
    }

    private TextView label(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFFB9C7D6);
        tv.setTextSize(12);
        tv.setPadding(0, dp(12), 0, dp(6));
        return tv;
    }

    private TextView roundBtn(String label, int color, String glyph) {
        TextView ret = new TextView(this);
        ret.setText(glyph + "\n" + label);
        ret.setTextColor(Color.WHITE);
        ret.setTextSize(15);
        ret.setGravity(Gravity.CENTER);
        GradientDrawable b2 = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{lighten(color), color});
        b2.setShape(GradientDrawable.OVAL);
        ret.setBackground(b2);
        return ret;
    }

    private TextView smallBtn(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(12);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(16), dp(9), dp(16), dp(9));
        tv.setBackground(glass(0x22FFFFFF, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(18);
        tv.setLayoutParams(lp);
        return tv;
    }

    private GradientDrawable glass(int color, int radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{color, blend(color, 0x22000000)});
        g.setCornerRadius(dp(radius));
        g.setStroke(dp(1), 0x33FFFFFF);
        return g;
    }

    private static int blend(int c, int overlay) {
        int a = (c >>> 24) & 0xFF, r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        int oa = (overlay >>> 24) & 0xFF;
        float f = oa / 255f;
        r = (int) (r * (1 - f)); g = (int) (g * (1 - f)); b = (int) (b * (1 - f));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lighten(int c) {
        int r = Math.min(255, ((c >> 16) & 0xFF) + 45);
        int g = Math.min(255, ((c >> 8) & 0xFF) + 45);
        int b = Math.min(255, (c & 0xFF) + 45);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private LinearLayout.LayoutParams lp(int w, int h, int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = dp(top);
        p.bottomMargin = dp(bottom);
        return p;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
