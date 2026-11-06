package com.roomarchive.server;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * 离线手机号归属地查询（读取 assets/phone.dat）。
 *
 * 数据格式（与 xluohome/phonedata 一致）：
 *   [0,4)   version        4 字节 ASCII
 *   [4,8)   firstRecord    小端 uint32，首条索引记录的偏移
 *   [8, firstRecord)        字符串池（UTF-8，\0 结尾，"省|市|邮编|区号"）
 *   [firstRecord, EOF)      索引记录，每条 9 字节：
 *                              uint32 号段前7位（小端）
 *                              uint32 字符串偏移（小端）
 *                              uint8  运营商代码
 *                              —— 按号段升序排列，可二分
 */
public class PhoneLocator {

    private static final int HEAD_LEN = 8;
    private static final int REC_LEN = 9;
    private static final String ASSET = "phone.dat";

    private static byte[] data;
    private static int firstRecord;
    private static int recordCount;
    private static volatile boolean loadTried = false;

    /** 归属地查询结果 */
    public static class Result {
        public String phone;
        public String province;
        public String city;
        public String zipCode;
        public String areaCode;
        public String carrier;
        public String prefix;

        public String summary() {
            return province + " " + city;
        }
    }

    public static synchronized boolean ensureLoaded(Context ctx) {
        if (data != null) return true;
        if (loadTried && data == null) {
            // 允许一次重试（AssetManager 偶发失败）
            loadTried = false;
        }
        loadTried = true;
        try {
            InputStream is = ctx.getAssets().open(ASSET);
            ByteArrayOutputStream bos = new ByteArrayOutputStream((int) 5_000_000);
            byte[] buf = new byte[16384];
            int r;
            while ((r = is.read(buf)) > 0) bos.write(buf, 0, r);
            is.close();
            byte[] d = bos.toByteArray();
            if (d.length < HEAD_LEN + REC_LEN) return false;

            int fr = readInt(d, 4);
            if (fr <= HEAD_LEN || fr >= d.length) return false;

            data = d;
            firstRecord = fr;
            recordCount = (d.length - fr) / REC_LEN;
            return true;
        } catch (Exception e) {
            data = null;
            return false;
        }
    }

    public static Result find(Context ctx, String phone) {
        if (phone == null) return null;
        String p = phone.replaceAll("[^0-9]", "");
        if (p.length() == 13 && p.startsWith("86")) p = p.substring(2);
        if (p.length() < 7) return null;
        if (!ensureLoaded(ctx)) return null;

        int key;
        try {
            key = Integer.parseInt(p.substring(0, 7));
        } catch (NumberFormatException e) {
            return null;
        }

        int lo = 0, hi = recordCount - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int off = firstRecord + mid * REC_LEN;
            int cur = readInt(data, off);
            if (cur > key) {
                hi = mid - 1;
            } else if (cur < key) {
                lo = mid + 1;
            } else {
                int strOff = readInt(data, off + 4);
                int cardType = data[off + 8] & 0xFF;
                String s = readCString(data, strOff);
                String[] parts = s.split("\\|");
                Result res = new Result();
                res.phone = p;
                res.prefix = p.substring(0, 7);
                res.province = parts.length > 0 ? parts[0] : "";
                res.city = parts.length > 1 ? parts[1] : "";
                res.zipCode = parts.length > 2 ? parts[2] : "";
                res.areaCode = parts.length > 3 ? parts[3] : "";
                res.carrier = carrierName(cardType);
                return res;
            }
        }
        return null;
    }

    private static String carrierName(int type) {
        switch (type) {
            case 1: return "中国移动";
            case 2: return "中国联通";
            case 3: return "中国电信";
            case 4: return "中国电信（虚拟运营商）";
            case 5: return "中国联通（虚拟运营商）";
            case 6: return "中国移动（虚拟运营商）";
            case 7: return "中国广电";
            case 8: return "中国广电（虚拟运营商）";
            default: return "未知运营商";
        }
    }

    private static int readInt(byte[] d, int off) {
        if (off + 4 > d.length) return 0;
        return (d[off] & 0xFF)
                | ((d[off + 1] & 0xFF) << 8)
                | ((d[off + 2] & 0xFF) << 16)
                | ((d[off + 3] & 0xFF) << 24);
    }

    private static String readCString(byte[] d, int off) {
        if (off <= 0 || off >= d.length) return "";
        int end = off;
        while (end < d.length && d[end] != 0) end++;
        try {
            return new String(d, off, end - off, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    /** 数据版本（前 4 字节 ASCII） */
    public static String version() {
        if (data == null || data.length < 4) return "";
        try {
            return new String(data, 0, 4, "US-ASCII").trim();
        } catch (Exception e) {
            return "";
        }
    }

    public static int recordCount() {
        return recordCount;
    }
}
