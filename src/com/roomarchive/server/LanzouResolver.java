package com.roomarchive.server;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 蓝奏云直链解析（免登录）。
 * 走分享页 → iframe(/fn?) → ajaxm.php 三步，拿到文件真实地址；
 * 任何一步对不上（页面改版/需要密码）就返回 null，由上层回退到「应用内页面下载」。
 */
class LanzouResolver {

    private static final String UA =
            "Mozilla/5.0 (Linux; Android 12; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/122.0.0.0 Mobile Safari/537.36";

    /** 返回直链；失败返回 null，原因写入 err[0] */
    static String resolve(String shareUrl, String pwd, String[] err) {
        try {
            String html = get(shareUrl, shareUrl, null);
            if (html == null) return fail(err, "分享页打不开");

            // 需要密码：提交一次
            if (html.contains("pwd") && html.contains("passwd") && pwd != null && pwd.length() > 0) {
                String body = "pwd=" + URLEncoder.encode(pwd, "UTF-8");
                html = post(shareUrl, shareUrl, body, null);
            }
            if (html == null) return fail(err, "密码提交失败");

            // iframe 页面（/fn?xxxx）
            String iframe = match(html, "src=\"(/fn\\?[^\"]+)\"");
            if (iframe == null) {
                iframe = match(html, "src=\"(https?://[^\"]+/fn\\?[^\"]+)\"");
            }
            String iframeHtml = html;
            if (iframe != null) {
                String base = origin(shareUrl);
                String u = iframe.startsWith("http") ? iframe : base + iframe;
                iframeHtml = get(u, shareUrl, null);
                if (iframeHtml == null) return fail(err, "下载页打不开");
            }

            String ajax = match(iframeHtml, "ajaxdata\\s*[=:]\\s*['\"]([^'\"]+)['\"]");
            if (ajax == null) ajax = match(iframeHtml, "(/ajaxm.php\\?[^'\"\\s]+)");
            String sign = match(iframeHtml, "'sign'\\s*:\\s*'([^']+)'");
            if (sign == null) sign = match(iframeHtml, "sign\\s*:\\s*'([^']+)'");
            if (ajax == null || sign == null) return fail(err, "页面结构已变化，未找到下载参数");

            String base = origin(shareUrl);
            String ajaxUrl = ajax.startsWith("http") ? ajax : base + ajax;
            String body = "action=downprocess&sign=" + URLEncoder.encode(sign, "UTF-8")
                    + "&websign=&websignkey=&ves=1&kd=1";
            String json = post(ajaxUrl, shareUrl, body, null);
            if (json == null) return fail(err, "下载接口无响应");

            String dom = match(json, "\"dom\"\\s*:\\s*\"([^\"]+)\"");
            String path = match(json, "\"url\"\\s*:\\s*\"([^\"]+)\"");
            if (dom == null || path == null) {
                String msg = match(json, "\"inf\"\\s*:\\s*\"([^\"]+)\"");
                return fail(err, msg != null ? msg : "未返回下载地址（可能需要密码或已被删除）");
            }
            String finalUrl = dom + path;
            // 跟随一次跳转拿真实文件地址
            String real = follow(finalUrl, shareUrl);
            return real != null ? real : finalUrl;
        } catch (Throwable t) {
            return fail(err, t.getMessage());
        }
    }

    private static String fail(String[] err, String msg) {
        if (err != null && err.length > 0) err[0] = msg;
        return null;
    }

    private static String origin(String u) {
        try {
            URL url = new URL(u);
            return url.getProtocol() + "://" + url.getHost();
        } catch (Exception e) {
            return u;
        }
    }

    private static String match(String src, String regex) {
        if (src == null) return null;
        Matcher m = Pattern.compile(regex).matcher(src);
        return m.find() ? m.group(1) : null;
    }

    private static String get(String u, String referer, String cookie) throws Exception {
        HttpURLConnection c = open(u, referer, cookie);
        int code = c.getResponseCode();
        if (code != 200) {
            c.disconnect();
            return null;
        }
        String s = read(c.getInputStream());
        c.disconnect();
        return s;
    }

    private static String post(String u, String referer, String body, String cookie) throws Exception {
        HttpURLConnection c = open(u, referer, cookie);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        byte[] bs = body.getBytes("UTF-8");
        c.setRequestProperty("Content-Length", String.valueOf(bs.length));
        c.setFixedLengthStreamingMode(bs.length);
        OutputStream os = c.getOutputStream();
        os.write(bs);
        os.flush();
        os.close();
        int code = c.getResponseCode();
        if (code != 200) {
            c.disconnect();
            return null;
        }
        String s = read(c.getInputStream());
        c.disconnect();
        return s;
    }

    /** 跟随 302 拿最终地址（不发正文） */
    private static String follow(String u, String referer) {
        try {
            HttpURLConnection c = open(u, referer, null);
            c.setInstanceFollowRedirects(true);
            int code = c.getResponseCode();
            String loc = c.getHeaderField("Location");
            c.disconnect();
            if (loc != null && loc.startsWith("http")) return loc;
            return code == 200 ? u : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static HttpURLConnection open(String u, String referer, String cookie) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", UA);
        if (referer != null) c.setRequestProperty("Referer", referer);
        if (cookie != null) c.setRequestProperty("Cookie", cookie);
        return c;
    }

    private static String read(InputStream is) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = is.read(buf)) > 0) bos.write(buf, 0, r);
        is.close();
        return new String(bos.toByteArray(), "UTF-8");
    }
}
