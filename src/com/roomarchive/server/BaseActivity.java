package com.roomarchive.server;

import android.app.Activity;
import android.os.Bundle;

/** 公共基类：去标题栏，状态栏随主题自适应（深色深底深图标 / 浅色浅底浅图标） */
public class BaseActivity extends Activity {

    /** onCreate 时的主题版本号，onResume 发现不一致就整页重建 */
    private int themeEpoch = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        themeEpoch = Tools.themeEpoch(this);
        Tools.applyStatusBar(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        int now = Tools.themeEpoch(this);
        if (themeEpoch >= 0 && now != themeEpoch) {
            themeEpoch = now;
            Tools.applyStatusBar(this);
            recreate();
        }
    }
}
