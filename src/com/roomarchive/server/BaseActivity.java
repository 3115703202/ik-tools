package com.roomarchive.server;

import android.app.Activity;
import android.os.Bundle;

/** 公共基类：去标题栏，状态栏随主题自适应（深色深底深图标 / 浅色浅底浅图标） */
public class BaseActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Tools.applyStatusBar(this);
    }
}
