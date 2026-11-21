package com.chromix.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Barras coloridas (o mesmo desenho do ícone) que "dançam" devagar. */
public class LogoView extends View {
    private static final int[] COLORS = {0xFFFF4D6D, 0xFFFF9F43, 0xFFFFD93D, 0xFF4ADE80, 0xFF38BDF8, 0xFF818CF8, 0xFFC084FC};
    private static final float[] BASE = {0.22f, 0.48f, 0.72f, 1.0f, 0.62f, 0.38f, 0.2f};
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float phase = 0f;
    private ValueAnimator anim;

    public LogoView(Context c) {
        super(c);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        anim = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
        anim.setDuration(2600);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                phase = (float) a.getAnimatedValue();
                invalidate();
            }
        });
        anim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas cv) {
        int w = getWidth(), h = getHeight();
        p.setStyle(Paint.Style.FILL);
        p.setColor(Pal.dark ? 0xFF231B4D : Pal.card2);
        float rad = Math.min(w, h) * 0.24f;
        r.set(0, 0, w, h);
        cv.drawRoundRect(r, rad, rad, p);

        int n = COLORS.length;
        float bw = w * 0.075f;
        float gap = (w * 0.74f - bw * n) / (n - 1);
        float x0 = w * 0.13f;
        for (int i = 0; i < n; i++) {
            float wob = 0.82f + 0.18f * (float) Math.sin(phase + i * 0.9f);
            float bh = h * 0.62f * BASE[i] * wob;
            float cx = x0 + bw / 2f + i * (bw + gap);
            p.setColor(COLORS[i]);
            r.set(cx - bw / 2f, h / 2f - bh / 2f, cx + bw / 2f, h / 2f + bh / 2f);
            cv.drawRoundRect(r, bw / 2f, bw / 2f, p);
        }
    }
}
