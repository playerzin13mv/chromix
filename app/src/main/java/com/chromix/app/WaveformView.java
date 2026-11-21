package com.chromix.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Mostra a onda e deixa arrastar os marcadores de corte (início/fim) e de loop. */
public class WaveformView extends View {

    public interface Listener {
        /** Chamado enquanto arrasta. trimChanged = true se foi o corte que mexeu. */
        void onMarkers(boolean trimChanged);
    }

    public static final int MODE_TRIM = 0, MODE_LOOP = 1;

    private float[] d;
    private int ts, te, ls, le;
    private boolean loopOn;
    private int mode = MODE_TRIM;
    private int active = -1;
    private Listener listener;

    private float[] mins, maxs;
    private int cols = 0;
    private float[] builtFor;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float dp;

    public WaveformView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
    }

    public void setListener(Listener l) { listener = l; }

    public void setData(float[] data) {
        d = data;
        builtFor = null;
        invalidate();
    }

    public void setTrim(int a, int b) { ts = a; te = b; invalidate(); }
    public void setLoop(int a, int b, boolean on) { ls = a; le = b; loopOn = on; invalidate(); }
    public void setMode(int m) { mode = m; invalidate(); }
    public int getTrimStart() { return ts; }
    public int getTrimEnd() { return te; }
    public int getLoopStart() { return ls; }
    public int getLoopEnd() { return le; }

    private void buildPeaks() {
        int w = getWidth();
        if (d == null || w <= 0) return;
        if (builtFor == d && cols == w) return;
        cols = w;
        mins = new float[cols];
        maxs = new float[cols];
        int n = d.length;
        for (int c = 0; c < cols; c++) {
            int a = (int) ((long) c * n / cols);
            int b = Math.max(a + 1, (int) ((long) (c + 1) * n / cols));
            int step = Math.max(1, (b - a) / 64);
            float mn = 0, mx = 0;
            for (int i = a; i < b && i < n; i += step) {
                float v = d[i];
                if (v < mn) mn = v;
                if (v > mx) mx = v;
            }
            mins[c] = mn;
            maxs[c] = mx;
        }
        builtFor = d;
    }

    private float xOf(int frame) {
        if (d == null || d.length == 0) return 0;
        return frame / (float) d.length * getWidth();
    }

    @Override
    protected void onDraw(Canvas cv) {
        int w = getWidth(), h = getHeight();
        p.setStyle(Paint.Style.FILL);
        p.setColor(Pal.card);
        r.set(0, 0, w, h);
        cv.drawRoundRect(r, 14 * dp, 14 * dp, p);

        if (d == null || d.length == 0) {
            p.setColor(Pal.sub);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(13 * dp);
            cv.drawText("Importe um áudio, grave sua voz ou use a voz de exemplo", w / 2f, h / 2f, p);
            return;
        }
        buildPeaks();

        float mid = h / 2f;
        float amp = h * 0.46f;

        // área de loop
        if (loopOn && le > ls) {
            p.setColor(Pal.accent2);
            p.setAlpha(45);
            cv.drawRect(xOf(ls), 0, xOf(le), h, p);
            p.setAlpha(255);
        }

        // linha central
        p.setColor(Pal.line);
        p.setStrokeWidth(1f);
        cv.drawLine(0, mid, w, mid, p);

        // onda
        p.setStrokeWidth(1f);
        for (int c = 0; c < cols && c < w; c++) {
            int frame = (int) ((long) c * d.length / cols);
            boolean inside = frame >= ts && frame <= te;
            p.setColor(inside ? Pal.accent : Pal.blend(Pal.accent, Pal.card, 0.75f));
            float y1 = mid - maxs[c] * amp;
            float y2 = mid - mins[c] * amp;
            if (y2 - y1 < 1f) { y1 -= 0.5f; y2 += 0.5f; }
            cv.drawLine(c + 0.5f, y1, c + 0.5f, y2, p);
        }

        // áreas cortadas
        p.setColor(Pal.bg);
        p.setAlpha(150);
        cv.drawRect(0, 0, xOf(ts), h, p);
        cv.drawRect(xOf(te), 0, w, h, p);
        p.setAlpha(255);

        // marcadores
        drawHandle(cv, xOf(ts), Pal.warn, mode == MODE_TRIM, active == 0 && mode == MODE_TRIM, true);
        drawHandle(cv, xOf(te), Pal.warn, mode == MODE_TRIM, active == 1 && mode == MODE_TRIM, false);
        if (loopOn) {
            drawHandle(cv, xOf(ls), Pal.accent2, mode == MODE_LOOP, active == 0 && mode == MODE_LOOP, true);
            drawHandle(cv, xOf(le), Pal.accent2, mode == MODE_LOOP, active == 1 && mode == MODE_LOOP, false);
        }
    }

    private void drawHandle(Canvas cv, float x, int color, boolean enabled, boolean hot, boolean left) {
        int h = getHeight();
        p.setColor(color);
        p.setAlpha(enabled ? 255 : 110);
        p.setStrokeWidth((hot ? 3.5f : 2f) * dp);
        cv.drawLine(x, 0, x, h, p);
        float tab = 11 * dp;
        r.set(left ? x : x - tab, 0, left ? x + tab : x, 18 * dp);
        cv.drawRoundRect(r, 4 * dp, 4 * dp, p);
        p.setAlpha(255);
    }

    private int frameAt(float x) {
        if (d == null) return 0;
        int f = (int) (x / Math.max(1, getWidth()) * d.length);
        return Math.max(0, Math.min(d.length, f));
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (d == null || d.length == 0) return true;
        float x = e.getX();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                float a = mode == MODE_TRIM ? xOf(ts) : xOf(ls);
                float b = mode == MODE_TRIM ? xOf(te) : xOf(le);
                if (mode == MODE_LOOP && !loopOn) { active = -1; return true; }
                active = Math.abs(x - a) <= Math.abs(x - b) ? 0 : 1;
                move(x);
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                move(x);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                active = -1;
                invalidate();
                return true;
            default:
                return true;
        }
    }

    private void move(float x) {
        if (active < 0) return;
        int f = frameAt(x);
        int minLen = Math.min(256, Math.max(8, d.length / 4));
        boolean trim = mode == MODE_TRIM;
        if (trim) {
            if (active == 0) ts = Math.max(0, Math.min(f, te - minLen));
            else te = Math.min(d.length, Math.max(f, ts + minLen));
            if (ls < ts) ls = ts;
            if (le > te) le = te;
            if (le <= ls) { ls = ts; le = te; }
        } else {
            int minLoop = Math.min(32, Math.max(2, (te - ts) / 4));
            if (active == 0) ls = Math.max(ts, Math.min(f, le - minLoop));
            else le = Math.min(te, Math.max(f, ls + minLoop));
        }
        invalidate();
        if (listener != null) listener.onMarkers(trim);
    }
}
