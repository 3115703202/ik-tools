package com.roomarchive.server;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;

/** ZIP 工具：解压 / 压缩 / AES 加密加锁 / 解密 */
public class ZipToolActivity extends BaseActivity {

    private static final int REQ_PICK_ZIP = 2001;
    private static final int REQ_PICK_FILES = 2002;   // 多选文件压缩
    private static final int REQ_PICK_ENC = 2003;     // 选文件加密
    private static final int REQ_PICK_DEC = 2004;     // 选 .room 解密

    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(Tools.bgWithGlow(this));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(20), dp(16), dp(18));
        scroll.addView(root);

        TextView title = Tools.pageTitle(this, "ZIP 工具");
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("解压 / 压缩 / AES 加密加锁，全程离线，无需存储权限");
        sub.setTextSize(13);
        sub.setTextColor(Tools.subTextColor(this));
        sub.setGravity(Gravity.CENTER);
        root.addView(sub);
        root.addView(spacer(14));

        statusView = new TextView(this);
        statusView.setText("选择下方操作开始");
        statusView.setTextSize(14);
        statusView.setTextColor(Tools.subTextColor(this));
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(dp(8), dp(10), dp(8), dp(10));
        root.addView(statusView);
        root.addView(spacer(12));

        root.addView(actionButton("解压 ZIP 文件", "folder", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                it.addCategory(Intent.CATEGORY_OPENABLE);
                it.setType("application/zip");
                startActivityForResult(it, REQ_PICK_ZIP);
            }
        }));
        root.addView(actionButton("把文件压缩成 ZIP", "zip", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                it.addCategory(Intent.CATEGORY_OPENABLE);
                it.setType("*/*");
                it.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                startActivityForResult(it, REQ_PICK_FILES);
            }
        }));
        root.addView(actionButton("加密加锁（AES）", "lock", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                it.addCategory(Intent.CATEGORY_OPENABLE);
                it.setType("*/*");
                startActivityForResult(it, REQ_PICK_ENC);
            }
        }));
        root.addView(actionButton("解密（.room 文件）", "unlock", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                it.addCategory(Intent.CATEGORY_OPENABLE);
                it.setType("*/*");
                startActivityForResult(it, REQ_PICK_DEC);
            }
        }));

        root.addView(spacer(14));
        TextView tip = new TextView(this);
        tip.setText("输出位置：\n" + outDir().getAbsolutePath() + "\n\n"
                + "提示：可到系统「文件管理」→ Android/data/com.roomarchive.server/files/IK工具箱 查看产物。\n"
                + "加密文件扩展名为 .room，忘记密码无法找回。");
        tip.setTextSize(11);
        tip.setTextColor(Tools.subTextColor(this));
        root.addView(tip);

        setContentView(scroll);
    }

    private Button actionButton(String text, String iconKey, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Tools.pillButton(this, 50, true));
        Icons.attachToText(b, iconKey, Color.WHITE, 16, 6);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        lp.setMargins(0, dp(5), 0, dp(5));
        b.setLayoutParams(lp);
        return b;
    }

    private File outDir() {
        return FileStore.outDir(this);
    }

    // ================= 解压 =================

    private void doUnzip(Uri uri) {
        setBusy("正在解压…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File dir = new File(outDir(), "解压_" + System.currentTimeMillis());
                    dir.mkdirs();
                    InputStream in = getContentResolver().openInputStream(uri);
                    ZipInputStream zis = new ZipInputStream(in);
                    ZipEntry e;
                    int count = 0;
                    byte[] buf = new byte[65536];
                    while ((e = zis.getNextEntry()) != null) {
                        File f = new File(dir, e.getName());
                        if (e.isDirectory()) {
                            f.mkdirs();
                            continue;
                        }
                        File parent = f.getParentFile();
                        if (parent != null) parent.mkdirs();
                        OutputStream os = new FileOutputStream(f);
                        int n;
                        while ((n = zis.read(buf)) > 0) os.write(buf, 0, n);
                        os.close();
                        zis.closeEntry();
                        count++;
                    }
                    zis.close();
                    final int c = count;
                    final String path = dir.getAbsolutePath();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("成功：解压完成：" + c + " 个文件\n" + path);
                            statusView.setTextColor(0xFF16A34A);
                        }
                    });
                } catch (final Exception ex) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("失败：解压失败：" + ex.getMessage());
                            statusView.setTextColor(0xFFDC2626);
                        }
                    });
                }
            }
        }).start();
    }

    // ================= 压缩 =================

    private void doZip(java.util.List<Uri> uris) {
        setBusy("正在压缩…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File zip = new File(outDir(), "压缩_" + System.currentTimeMillis() + ".zip");
                    ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip));
                    byte[] buf = new byte[65536];
                    int idx = 0;
                    for (Uri uri : uris) {
                        String name = uri.getLastPathSegment();
                        if (name == null) name = "file_" + (idx++);
                        if (name.contains("/")) name = name.substring(name.lastIndexOf('/') + 1);
                        zos.putNextEntry(new ZipEntry(name));
                        InputStream in = getContentResolver().openInputStream(uri);
                        int n;
                        while ((n = in.read(buf)) > 0) zos.write(buf, 0, n);
                        in.close();
                        zos.closeEntry();
                    }
                    zos.close();
                    final String path = zip.getAbsolutePath();
                    final long size = zip.length();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("成功：压缩完成：" + uris.size() + " 个文件，共 " + (size / 1024) + " KB\n" + path);
                            statusView.setTextColor(0xFF16A34A);
                        }
                    });
                } catch (final Exception ex) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("失败：压缩失败：" + ex.getMessage());
                            statusView.setTextColor(0xFFDC2626);
                        }
                    });
                }
            }
        }).start();
    }

    // ================= AES 加密 / 解密 =================

    private void askPassword(final Uri uri, final boolean encrypt) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(encrypt ? "设置加密密码" : "输入解密密码");
        final EditText pwd = new EditText(this);
        pwd.setHint("密码（务必牢记，忘记无法找回）");
        pwd.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        b.setView(pwd);
        b.setPositiveButton("确定", new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface d, int which) {
                String p = pwd.getText().toString();
                if (p == null || p.length() < 4) {
                    Toast.makeText(ZipToolActivity.this, "密码至少 4 位", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (encrypt) doEncrypt(uri, p);
                else doDecrypt(uri, p);
            }
        });
        b.setNegativeButton("取消", null);
        b.show();
    }

    private void doEncrypt(final Uri uri, final String password) {
        setBusy("正在加密…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String name = uri.getLastPathSegment();
                    if (name == null) name = "file";
                    File out = new File(outDir(), name + ".room");
                    InputStream in = getContentResolver().openInputStream(uri);
                    FileOutputStream fos = new FileOutputStream(out);

                    byte[] salt = new byte[16];
                    new SecureRandom().nextBytes(salt);
                    SecretKey key = deriveKey(password, salt);
                    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                    byte[] iv = new byte[12];
                    new SecureRandom().nextBytes(iv);
                    cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));

                    fos.write(salt);
                    fos.write(iv);
                    CipherOutputStream cos = new CipherOutputStream(fos, cipher);
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) cos.write(buf, 0, n);
                    cos.close();
                    in.close();

                    final String path = out.getAbsolutePath();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("成功：加密完成（AES-256）\n" + path);
                            statusView.setTextColor(0xFF16A34A);
                        }
                    });
                } catch (final Exception ex) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("失败：加密失败：" + ex.getMessage());
                            statusView.setTextColor(0xFFDC2626);
                        }
                    });
                }
            }
        }).start();
    }

    private void doDecrypt(final Uri uri, final String password) {
        setBusy("正在解密…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String name = uri.getLastPathSegment();
                    if (name == null) name = "file";
                    if (name.endsWith(".room")) name = name.substring(0, name.length() - 5);
                    File out = new File(outDir(), "解密_" + name);
                    InputStream in = getContentResolver().openInputStream(uri);
                    byte[] salt = new byte[16];
                    byte[] iv = new byte[12];
                    int r = in.read(salt);
                    if (r != 16) throw new Exception("不是有效的 .room 加密文件");
                    in.read(iv);
                    SecretKey key = deriveKey(password, salt);
                    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                    cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
                    CipherInputStream cis = new CipherInputStream(in, cipher);
                    FileOutputStream fos = new FileOutputStream(out);
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = cis.read(buf)) > 0) fos.write(buf, 0, n);
                    fos.close();
                    cis.close();

                    final String path = out.getAbsolutePath();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("成功：解密完成\n" + path);
                            statusView.setTextColor(0xFF16A34A);
                        }
                    });
                } catch (final Exception ex) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("失败：解密失败（密码错误或文件损坏）");
                            statusView.setTextColor(0xFFDC2626);
                        }
                    });
                }
            }
        }).start();
    }

    private SecretKey deriveKey(String password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 10000, 256);
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] k = f.generateSecret(spec).getEncoded();
        return new SecretKeySpec(k, "AES");
    }

    private void setBusy(String msg) {
        statusView.setText(msg);
        statusView.setTextColor(Color.rgb(200, 140, 20));
    }

    // ================= 选择回调 =================

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == REQ_PICK_ZIP) {
            doUnzip(data.getData());
        } else if (requestCode == REQ_PICK_FILES) {
            java.util.List<Uri> uris = new java.util.ArrayList<>();
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    uris.add(data.getClipData().getItemAt(i).getUri());
                }
            } else {
                uris.add(data.getData());
            }
            doZip(uris);
        } else if (requestCode == REQ_PICK_ENC) {
            askPassword(data.getData(), true);
        } else if (requestCode == REQ_PICK_DEC) {
            askPassword(data.getData(), false);
        }
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
