package com.roomarchive.server;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * 失控进化房型库 · 入口
 * 直接在 App 内置浏览器中打开托管在官网的《失控进化》房型库网页（离线单文件版）。
 */
public class ShikongActivity extends Activity {

    /** 失控进化房型库网页（GitHub Pages 托管，无平台水印，国内可访问） */
    private static final String PORTAL_URL =
            "https://3115703202.github.io/ik-tools/shikong.html";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent it = new Intent(this, BrowserActivity.class);
        it.putExtra("url", PORTAL_URL);
        it.putExtra("title", "失控进化房型库");
        startActivity(it);
        finish();
    }
}
