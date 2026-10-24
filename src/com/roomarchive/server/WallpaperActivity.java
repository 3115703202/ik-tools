package com.roomarchive.server;

import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;

/** 壁纸工具：系统默认壁纸 / 自定义壁纸（设为主屏/锁屏）/ 3D 视差动态壁纸 */
public class WallpaperActivity extends BaseActivity {

    private static final int REQ_PICK = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Tools.bgWithGlow(this));
        root.setPadding(dp(16), dp(20), dp(16), dp(18));
        scroll.addView(root);

        TextView title = Tools.pageTitle(this, "壁纸");
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView tip = new TextView(this);
        tip.setText("三种方式：\n① 系统默认壁纸：直接打开手机系统壁纸库\n② 自定义壁纸：从相册选图，设为桌面/锁屏壁纸\n③ 3D 壁纸：普通图片变视差动态壁纸——滑桌面、晃手机都有 3D 跟随效果");
        tip.setTextSize(12);
        tip.setTextColor(Tools.subTextColor(this));
        root.addView(tip);
        root.addView(spacer(16));

        LinearLayout btnDefault = bigButton("系统默认壁纸", "打开手机自带的壁纸库选择");
        btnDefault.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSystemWallpaperPicker();
            }
        });
        root.addView(btnDefault);
        root.addView(spacer(12));

        LinearLayout btnCustom = bigButton("自定义壁纸", "从相册选图，设为壁纸（可选手桌/锁屏）");
        btnCustom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickImage(false);
            }
        });
        root.addView(btnCustom);
        root.addView(spacer(12));

        LinearLayout btn3d = bigButton("3D 壁纸", "把普通图片变成 3D 视差动态壁纸");
        btn3d.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickImage(true);
            }
        });
        root.addView(btn3d);
        root.addView(spacer(12));

        TextView note = new TextView(this);
        note.setText("3D 壁纸说明：\n· 选择图片后保存为 3D 壁纸素材，再打开系统动态壁纸列表，选中「IK 3D壁纸」启用\n· 启用后在桌面左右滑动、手机倾斜时，画面会产生景深 3D 跟随效果\n· 想换图：重新在本页选图即可，效果自动更新");
        note.setTextSize(12);
        note.setTextColor(Tools.subTextColor(this));
        root.addView(note);

        setContentView(scroll);
    }

    /** 打开系统默认壁纸库 */
    private void openSystemWallpaperPicker() {
        try {
            Intent it = new Intent("android.intent.action.SET_WALLPAPER");
            startActivity(it);
        } catch (Exception e) {
            try {
                // 备选：跳系统设置里的显示/壁纸相关页
                Intent it = new Intent(android.provider.Settings.ACTION_SETTINGS);
                startActivity(it);
                Toast.makeText(this, "已打开系统设置，请在「显示与壁纸」中选择", Toast.LENGTH_LONG).show();
            } catch (Exception ex) {
                Toast.makeText(this, "无法打开壁纸选择器，可在桌面长按空白处 → 壁纸 手动更换",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void pickImage(final boolean for3d) {
        try {
            Intent it = new Intent(Intent.ACTION_GET_CONTENT);
            it.setType("image/*");
            it.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(it, for3d ? REQ_PICK + 1 : REQ_PICK);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开相册：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == REQ_PICK) {
            setupCustomWallpaper(data.getData());
        } else if (requestCode == REQ_PICK + 1) {
            setup3dWallpaper(data.getData());
        }
    }

    /** 自定义壁纸：解码 → 选择主屏/锁屏/两者 → 设为壁纸 */
    private void setupCustomWallpaper(final Uri uri) {
        try {
            final Bitmap bm = decodeLimited(uri);
            if (bm == null) {
                Toast.makeText(this, "无法解码该图片", Toast.LENGTH_LONG).show();
                return;
            }
            String[] choices = {"设为主屏壁纸", "设为锁屏壁纸", "主屏 + 锁屏都设"};
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            b.setTitle("设为壁纸");
            b.setItems(choices, new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface d, int which) {
                    int flag;
                    switch (which) {
                        case 0: flag = WallpaperManager.FLAG_SYSTEM; break;
                        case 1: flag = WallpaperManager.FLAG_LOCK; break;
                        default: flag = WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK; break;
                    }
                    applyWallpaper(bm, flag);
                }
            });
            b.show();
        } catch (Exception e) {
            Toast.makeText(this, "读取图片失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void applyWallpaper(Bitmap bm, int which) {
        try {
            WallpaperManager wm = WallpaperManager.getInstance(this);
            wm.setBitmap(bm, null, true, which);
            if (!bm.isRecycled()) bm.recycle();
            Toast.makeText(this, "壁纸已设置", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "设置失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** 3D 壁纸：保存素材 → 打开系统动态壁纸选择器启用 */
    private void setup3dWallpaper(final Uri uri) {
        try {
            final Bitmap bm = decodeLimited(uri);
            if (bm == null) {
                Toast.makeText(this, "无法解码该图片", Toast.LENGTH_LONG).show();
                return;
            }
            File dir = new File(FileStore.outDir(this), "3D壁纸");
            dir.mkdirs();
            File f = new File(dir, "current.jpg");
            FileOutputStream os = new FileOutputStream(f);
            bm.compress(Bitmap.CompressFormat.JPEG, 92, os);
            os.flush();
            os.close();
            if (!bm.isRecycled()) bm.recycle();

            AlertDialog.Builder b = new AlertDialog.Builder(this);
            b.setTitle("3D 壁纸素材已就绪");
            b.setMessage("现在打开系统动态壁纸列表，选择「IK 3D壁纸」即可启用。\n启用后滑动桌面、倾斜手机就有 3D 效果。\n\n以后想换图：重新在本页选一张图即可。");
            b.setPositiveButton("去启用", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface d, int w) {
                    enable3dWallpaper();
                }
            });
            b.setNegativeButton("稍后", null);
            b.show();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** 打开系统动态壁纸选择器并指向 IK 3D壁纸 */
    private void enable3dWallpaper() {
        try {
            ComponentName comp = new ComponentName(this, IKWallpaperService.class);
            Intent it = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            it.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, comp);
            startActivity(it);
        } catch (Exception e) {
            Toast.makeText(this, "无法直接打开动态壁纸列表，请到 桌面长按 → 壁纸 → 动态壁纸 里选「IK 3D壁纸」",
                    Toast.LENGTH_LONG).show();
        }
    }

    /** 采样解码，限制最大边 2880，防大图 OOM */
    private Bitmap decodeLimited(Uri uri) throws Exception {
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inJustDecodeBounds = true;
        java.io.InputStream is0 = getContentResolver().openInputStream(uri);
        BitmapFactory.decodeStream(is0, null, opt);
        if (is0 != null) is0.close();
        int sample = 1;
        while (opt.outWidth / sample > 2880 || opt.outHeight / sample > 2880) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        java.io.InputStream is = getContentResolver().openInputStream(uri);
        Bitmap bm = BitmapFactory.decodeStream(is, null, o2);
        if (is != null) is.close();
        return bm;
    }

    private LinearLayout bigButton(String text, String sub) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(16), dp(18), dp(16));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Tools.cardColor(this));
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), Tools.isDark(this) ? 0xFF2A3040 : 0xFFE4E8EE);
        box.setBackground(bg);
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(17);
        t.setTextColor(Tools.textColor(this));
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        box.addView(t);
        TextView s = new TextView(this);
        s.setText(sub);
        s.setTextSize(12);
        s.setTextColor(Tools.subTextColor(this));
        box.addView(s);
        Button btn = new Button(this);
        btn.setVisibility(View.GONE);
        box.addView(btn);
        box.setClickable(true);
        return box;
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
