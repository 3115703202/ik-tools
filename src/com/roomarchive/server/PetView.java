package com.roomarchive.server;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

/** 桌面宠物：Canvas 绘制的液态小龙，随呼吸浮动。 */
public class PetView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long born = SystemClock.uptimeMillis();

    public PetView(Context c) { super(c); setLayerType(LAYER_TYPE_SOFTWARE, null); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float t = (SystemClock.uptimeMillis() - born) / 1000f;
        float bob = (float) Math.sin(t * 2.2f) * 6f;
        float cx = getWidth() / 2f, cy = getHeight() / 2f + bob;
        float r = Math.min(getWidth(), getHeight()) * 0.32f;

        paint.setColor(0x5534D399); paint.setMaskFilter(new android.graphics.BlurMaskFilter(18, android.graphics.BlurMaskFilter.Blur.NORMAL)); paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy + 8, r * 1.25f, paint); paint.setMaskFilter(null);

        android.graphics.LinearGradient lg = new android.graphics.LinearGradient(cx, cy - r, cx, cy + r,
                new int[]{0xFF5EEAD4, 0xFF34D399, 0xFF059669}, null, android.graphics.Shader.TileMode.CLAMP);
        paint.setShader(lg);
        RectF body = new RectF(cx - r, cy - r * 0.9f, cx + r, cy + r * 0.95f);
        canvas.drawOval(body, paint); paint.setShader(null);

        paint.setColor(0xFF052E22);
        float ex = r * 0.32f, ey = -r * 0.1f;
        canvas.drawCircle(cx - ex, cy + ey, r * 0.14f, paint);
        canvas.drawCircle(cx + ex, cy + ey, r * 0.14f, paint);
        paint.setColor(0xFFFFFFFF);
        canvas.drawCircle(cx - ex + r * 0.05f, cy + ey - r * 0.04f, r * 0.045f, paint);
        canvas.drawCircle(cx + ex + r * 0.05f, cy + ey - r * 0.04f, r * 0.045f, paint);
        paint.setColor(0xFF064E3B); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(r * 0.06f);
        RectF mouth = new RectF(cx - r * 0.2f, cy + r * 0.18f, cx + r * 0.2f, cy + r * 0.42f);
        canvas.drawArc(mouth, 10, 160, false, paint);

        Path tail = new Path();
        tail.moveTo(cx + r * 0.85f, cy + r * 0.2f);
        tail.quadTo(cx + r * 1.25f, cy - r * 0.1f, cx + r * 1.1f, cy - r * 0.55f);
        paint.setStyle(Paint.Style.FILL); paint.setColor(0xFF10B981); canvas.drawPath(tail, paint);

        postInvalidateDelayed(45);
    }
}
