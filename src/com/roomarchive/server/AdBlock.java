package com.roomarchive.server;

import android.content.Context;

/** 内置浏览器广告请求拦截：按域名命中直接返回空响应，不经过网络。 */
public final class AdBlock {
    static final String[] HOSTS = {
        "doubleclick.net","googlesyndication.com","googleadservices.com","adservice.google.com",
        "ads.yahoo.com","adsrvr.org","amazon-adsystem.com","applovin.com","unityads.unity3d.com",
        "umeng.com","gdt.qq.com","tanx.com","cpro.baidu.com","pos.baidu.com","union.baidu.com",
        "bdadunion.com","adsmind.apdcdn.tc.qq.com","aduktag.com","adcolony.com","chartboost.com",
        "inmobi.com","mopub.com","startapp.com","tapjoy.com","vungle.com","ironsrc.com"
    };
    static boolean on(Context c) { return c.getSharedPreferences("ik_adblock", Context.MODE_PRIVATE).getBoolean("on", true); }
    static boolean blocked(String url) {
        if (url == null) return false;
        try {
            String host = android.net.Uri.parse(url).getHost();
            if (host == null) return false;
            host = host.toLowerCase();
            for (String h : HOSTS) if (host.equals(h) || host.endsWith("." + h)) return true;
        } catch (Throwable ignored) {}
        return false;
    }
}
