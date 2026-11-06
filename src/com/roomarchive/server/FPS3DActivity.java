package com.roomarchive.server;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class FPS3DActivity extends Activity {

    private WebView wv;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);

        // 全屏沉浸 + 常亮
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        wv = new WebView(this);
        wv.setBackgroundColor(0xFF000000);

        WebSettings st = wv.getSettings();
        st.setJavaScriptEnabled(true);
        st.setDomStorageEnabled(true);
        st.setUseWideViewPort(true);
        st.setLoadWithOverviewMode(true);
        st.setMediaPlaybackRequiresUserGesture(false);
        st.setSupportZoom(false);
        st.setBuiltInZoomControls(false);
        st.setDisplayZoomControls(false);
        st.setCacheMode(WebSettings.LOAD_NO_CACHE);
        if (Build.VERSION.SDK_INT >= 21) {
            st.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        wv.setWebViewClient(new WebViewClient());
        wv.setVerticalScrollBarEnabled(false);
        wv.setHorizontalScrollBarEnabled(false);

        wv.loadUrl("file:///android_asset/fps3d.html");
        setContentView(wv);

        hideSystemBars();
    }

    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 19) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (wv != null) {
            wv.onPause();
            wv.pauseTimers();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (wv != null) {
            wv.onResume();
            wv.resumeTimers();
        }
    }

    @Override
    protected void onDestroy() {
        if (wv != null) {
            wv.destroy();
            wv = null;
        }
        super.onDestroy();
    }
}
