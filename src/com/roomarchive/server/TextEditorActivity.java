package com.roomarchive.server;

import android.app.Activity;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

/** 文本编辑器：编辑文本并保存成文件（.txt），支持打开已有文本 */
public class TextEditorActivity extends BaseActivity {

    private static final int REQ_OPEN = 4001;
    private static final int REQ_SAVE = 4002;

    private EditText editor;
    private TextView infoView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(Tools.bgWithGlow(this));
        root.setPadding(dp(16), dp(20), dp(16), dp(18));

        TextView title = Tools.pageTitle(this, "文本编辑器");
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        infoView = new TextView(this);
        infoView.setText("输入内容 → 保存为 .txt 文件");
        infoView.setTextSize(12);
        infoView.setTextColor(Tools.subTextColor(this));
        infoView.setGravity(Gravity.CENTER);
        root.addView(infoView);

        editor = new EditText(this);
        editor.setHint("在这里写文本…\n支持中文、换行，可保存成 txt 文件");
        editor.setTextSize(15);
        editor.setTextColor(Tools.textColor(this));
        editor.setGravity(Gravity.TOP | Gravity.START);
        android.graphics.drawable.GradientDrawable edBg = new android.graphics.drawable.GradientDrawable();
        edBg.setColor(Tools.cardColor(this));
        edBg.setCornerRadius(Tools.dp(this, 12));
        edBg.setStroke(Tools.dp(this, 1),
                Tools.isDark(this) ? 0xFF2A3040 : 0xFFE4E8EE);
        editor.setBackground(edBg);
        editor.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        editor.setLayoutParams(elp);
        root.addView(editor);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        Button btnOpen = new Button(this);
        btnOpen.setText("打开");
        btnOpen.setTextColor(Tools.accentColor(this));
        int ac = Tools.accentColor(this);
        android.graphics.drawable.GradientDrawable oBg = new android.graphics.drawable.GradientDrawable();
        oBg.setColor((0x1F << 24) | (ac & 0x00FFFFFF));
        oBg.setCornerRadius(Tools.dp(this, 24));
        btnOpen.setBackground(oBg);
        btnOpen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                it.addCategory(Intent.CATEGORY_OPENABLE);
                it.setType("text/*");
                startActivityForResult(it, REQ_OPEN);
            }
        });
        row.addView(btnOpen, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button btnSave = new Button(this);
        btnSave.setText("保存");
        btnSave.setTextColor(Color.WHITE);
        btnSave.setBackground(Tools.pillButton(this, 48, true));
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveText();
            }
        });
        row.addView(btnSave, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        root.addView(row);

        setContentView(root);
    }

    private void saveText() {
        String text = editor.getText().toString();
        if (text.trim().isEmpty()) {
            Toast.makeText(this, "内容为空，先写点东西", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File d = FileStore.outDir(TextEditorActivity.this);
            File out = new File(d, "文档_" + System.currentTimeMillis() + ".txt");
            OutputStream os = new java.io.FileOutputStream(out);
            os.write(text.getBytes("UTF-8"));
            os.flush();
            os.close();
            infoView.setText("已保存：" + out.getName());
            Toast.makeText(this, "已保存到工具箱目录\n" + out.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == REQ_OPEN) {
            openText(data.getData());
        }
    }

    private void openText(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            br.close();
            editor.setText(sb.toString());
            infoView.setText("已打开：" + uri.getLastPathSegment());
            Toast.makeText(this, "已载入文本", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "打开失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
