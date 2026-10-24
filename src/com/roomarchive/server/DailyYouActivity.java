package com.roomarchive.server;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 每日一签：每天一句原创寄语 + 心情记录（本地保存）。
 */
public class DailyYouActivity extends BaseActivity {

    private static final String[] QUOTES = {
            "今天也是元气满满的一天！",
            "慢慢来，比较快。",
            "你比你想象中更坚强。",
            "每一个今天，都是新的开始。",
            "保持热爱，奔赴山海。",
            "生活明朗，万物可爱。",
            "所有的美好都值得等待。",
            "今天的你，辛苦了。",
            "别灰心，好事正酿。",
            "把日子过成诗。",
            "你想要的，都在路上。",
            "平凡日子里也有小确幸。",
            "向前走，别回头。",
            "世界那么大，先照顾好自己。",
            "好运藏在努力里。",
            "做自己的光。",
            "温柔且坚定。",
            "今天也要好好吃饭。",
            "保持好奇心，世界就有趣。",
            "给自己一点掌声。",
            "万事胜意，未来可期。",
            "内心丰盈者，独行也如众。",
            "按自己的节奏生长。",
            "每一次坚持都算数。",
            "心之所向，素履以往。",
            "今天值得被记录。",
            "小小的进步也是进步。",
            "会好的，会更好的。",
            "别忘了夸夸自己。",
            "认真生活，就能找到生活藏起来的糖果。",
    };

    private TextView quoteView, dateView, historyBox;

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
        root.setPadding(dp(16), dp(20), dp(16), dp(18));
        scroll.addView(root);

        TextView title = Tools.pageTitle(this, "每日一签");
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        root.addView(spacer(10));

        dateView = new TextView(this);
        dateView.setTextSize(14);
        dateView.setTextColor(Tools.subTextColor(this));
        dateView.setGravity(Gravity.CENTER);
        root.addView(dateView);

        root.addView(spacer(10));

        quoteView = new TextView(this);
        quoteView.setTextSize(18);
        quoteView.setTextColor(Tools.textColor(this));
        quoteView.setGravity(Gravity.CENTER);
        quoteView.setLineSpacing(dp(6), 1.0f);
        quoteView.setPadding(dp(18), dp(22), dp(18), dp(22));
        GradientDrawable qb = new GradientDrawable();
        qb.setColor(Tools.cardColor(this));
        qb.setCornerRadius(dp(16));
        qb.setStroke(dp(1), Tools.isDark(this) ? 0xFF2A3040 : 0xFFE4E8EE);
        quoteView.setBackground(qb);
        root.addView(quoteView);

        root.addView(spacer(12));

        final EditText mood = new EditText(this);
        mood.setHint("写下今天的心情（本地保存）…");
        mood.setTextSize(14);
        mood.setTextColor(Tools.textColor(this));
        GradientDrawable inBg = new GradientDrawable();
        inBg.setColor(Tools.cardColor(this));
        inBg.setCornerRadius(dp(12));
        inBg.setStroke(dp(1), Tools.isDark(this) ? 0xFF2A3040 : 0xFFE4E8EE);
        mood.setBackground(inBg);
        mood.setPadding(dp(12), dp(10), dp(12), dp(10));
        mood.setMinLines(3);
        root.addView(mood);

        root.addView(spacer(8));

        Button save = new Button(this);
        save.setText("保存今天的心情");
        save.setAllCaps(false);
        save.setTextColor(Color.WHITE);
        save.setBackground(Tools.pillButton(this, 48, true));
        root.addView(save);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String txt = mood.getText().toString().trim();
                if (txt.isEmpty()) {
                    Toast.makeText(DailyYouActivity.this, "先写点什么吧", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    File dir = new File(FileStore.outDir(DailyYouActivity.this), "每日一签");
                    dir.mkdirs();
                    String today = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
                    File f = new File(dir, today + ".txt");
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
                    fos.write(txt.getBytes("UTF-8"));
                    fos.close();
                    Toast.makeText(DailyYouActivity.this,
                            "已保存：" + f.getAbsolutePath(), Toast.LENGTH_LONG).show();
                    refreshHistory();
                } catch (Exception e) {
                    Toast.makeText(DailyYouActivity.this, "保存失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });

        root.addView(spacer(14));

        historyBox = new TextView(this);
        historyBox.setTextSize(12);
        historyBox.setTextColor(Tools.subTextColor(this));
        historyBox.setLineSpacing(dp(3), 1.0f);
        historyBox.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable hb = new GradientDrawable();
        hb.setColor(Tools.cardColor(this));
        hb.setCornerRadius(dp(12));
        hb.setStroke(dp(1), Tools.isDark(this) ? 0xFF2A3040 : 0xFFE4E8EE);
        historyBox.setBackground(hb);
        root.addView(historyBox);
        refreshHistory();

        Calendar c = Calendar.getInstance();
        String today = (c.get(Calendar.MONTH) + 1) + "月" + c.get(Calendar.DAY_OF_MONTH) + "日 "
                + new SimpleDateFormat("yyyy", Locale.CHINA).format(new Date());
        dateView.setText("「" + today + "」");
        int dayOfYear = c.get(Calendar.DAY_OF_YEAR);
        quoteView.setText("「" + QUOTES[dayOfYear % QUOTES.length] + "」");

        root.setBackground(Tools.bgWithGlow(this));
        setContentView(scroll);
    }

    private void refreshHistory() {
        try {
            File dir = new File(FileStore.outDir(this), "每日一签");
            File[] fs = dir.listFiles();
            if (fs == null || fs.length == 0) {
                historyBox.setText("暂无心情记录");
                return;
            }
            StringBuilder sb = new StringBuilder("历史心情记录：\n");
            java.util.Arrays.sort(fs, new java.util.Comparator<File>() {
                @Override
                public int compare(File a, File b) {
                    return b.getName().compareTo(a.getName());
                }
            });
            for (File f : fs) {
                if (f.getName().endsWith(".txt")) {
                    java.io.FileInputStream fis = new java.io.FileInputStream(f);
                    byte[] bts = new byte[(int) f.length()];
                    fis.read(bts);
                    fis.close();
                    String content = new String(bts, "UTF-8");
                    sb.append("· ").append(f.getName().replace(".txt", "")).append("：")
                            .append(content.length() > 30 ? content.substring(0, 30) + "…" : content).append("\n");
                }
            }
            historyBox.setText(sb.toString());
        } catch (Exception e) {
            historyBox.setText("暂无心情记录");
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
