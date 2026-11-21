package com.chromix.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.View;

/** Piano multitoque (com glissando). Cada tecla acende na cor da sua nota cromática. */
public class PianoView extends View {

    public interface Listener {
        void down(int key, int pid);
        void up(int key, int pid);
    }

    private static final int[] WHITE_OFF = {0, 2, 4, 5, 7, 9, 11};
    /** Depois de qual tecla branca (índice 0..6 da oitava) existe tecla preta. */
    private static final boolean[] BLACK_AFTER = {true, true, false, true, true, true, false};

    private Listener listener;
    private int firstKey = 48;
    private int octaves = 2;
    private int root = -1, lo = 0, hi = 127;
    private int noteStyle = 0;
    private final SparseIntArray touch = new SparseIntArray();
    private final SparseBooleanArray ext = new SparseBooleanArray();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    public PianoView(Context c) {
        super(c);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    public void setListener(Listener l) { listener = l; }

    public void setOctaves(int o) { octaves = Math.max(1, Math.min(4, o)); clampFirst(); invalidate(); }
    public int getOctaves() { return octaves; }

    /** Define a primeira tecla visível (será ajustada para um Dó). */
    public void setFirstKey(int k) { firstKey = (k / 12) * 12; clampFirst(); invalidate(); }
    public int getFirstKey() { return firstKey; }

    private void clampFirst() {
        int max = 127 - octaves * 12;
        if (firstKey > max) firstKey = (max / 12) * 12;
        if (firstKey < 0) firstKey = 0;
    }

    public void setRoot(int k) { root = k; invalidate(); }
    public void setRange(int l, int h) { lo = l; hi = h; invalidate(); }
    public void setNoteStyle(int s) { noteStyle = s; invalidate(); }

    /** Acende uma tecla por código (usado pela escala automática). */
    public void setExt(int key, boolean on) {
        if (on) ext.put(key, true); else ext.delete(key);
        invalidate();
    }

    public void clearExt() { ext.clear(); invalidate(); }

    private int whiteCount() { return octaves * 7 + 1; }

    private int whiteNote(int idx) {
        return firstKey + (idx / 7) * 12 + WHITE_OFF[idx % 7];
    }

    private boolean pressed(int key) {
        if (ext.get(key, false)) return true;
        for (int i = 0; i < touch.size(); i++) if (touch.valueAt(i) == key) return true;
        return false;
    }

    @Override
    protected void onDraw(Canvas cv) {
        int w = getWidth(), h = getHeight();
        int wc = whiteCount();
        float ww = w / (float) wc;
        float bw = ww * 0.62f;
        float bh = h * 0.60f;
        float rad = Math.min(ww * 0.16f, 14f);

        // teclas brancas
        for (int i = 0; i < wc; i++) {
            int note = whiteNote(i);
            if (note > 127) continue;
            boolean inRange = note >= lo && note <= hi;
            int fill = Pal.keyWhite;
            if (!inRange) fill = Pal.blend(Pal.keyWhite, Pal.bg, 0.55f);
            if (pressed(note)) fill = Pal.RAINBOW[note % 12];
            r.set(i * ww + 1.5f, 0, (i + 1) * ww - 1.5f, h - 2f);
            p.setStyle(Paint.Style.FILL);
            p.setColor(fill);
            cv.drawRoundRect(r, rad, rad, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.5f);
            p.setColor(Pal.keyEdge);
            cv.drawRoundRect(r, rad, rad, p);

            if (note % 12 == 0 || note == root) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(Pal.blend(Pal.keyBlack, Pal.keyWhite, 0.25f));
                p.setTextSize(Math.max(22f, ww * 0.26f));
                p.setTextAlign(Paint.Align.CENTER);
                cv.drawText(Pal.noteName(note, noteStyle), i * ww + ww / 2f, h - 14f - (note == root ? 30f : 0f), p);
            }
            if (note == root) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(Pal.accent2);
                cv.drawCircle(i * ww + ww / 2f, h - 26f, Math.max(7f, ww * 0.12f), p);
            }
        }

        // teclas pretas
        for (int i = 0; i < wc - 1; i++) {
            if (!BLACK_AFTER[i % 7]) continue;
            int note = whiteNote(i) + 1;
            if (note > 127) continue;
            boolean inRange = note >= lo && note <= hi;
            float cx = (i + 1) * ww;
            int fill = Pal.keyBlack;
            if (!inRange) fill = Pal.blend(Pal.keyBlack, Pal.bg, 0.5f);
            if (pressed(note)) fill = Pal.RAINBOW[note % 12];
            r.set(cx - bw / 2f, 0, cx + bw / 2f, bh);
            p.setStyle(Paint.Style.FILL);
            p.setColor(fill);
            cv.drawRoundRect(r, rad, rad, p);
            if (note == root) {
                p.setColor(Pal.accent2);
                cv.drawCircle(cx, bh - 22f, Math.max(6f, ww * 0.10f), p);
            }
        }
    }

    /** Descobre a tecla sob o ponto (pretas têm prioridade), ou -1. */
    private int keyAt(float x, float y) {
        int w = getWidth(), h = getHeight();
        if (w <= 0) return -1;
        int wc = whiteCount();
        float ww = w / (float) wc;
        float bw = ww * 0.62f;
        float bh = h * 0.60f;
        int wi = (int) (x / ww);
        wi = Math.max(0, Math.min(wc - 1, wi));
        if (y < bh) {
            for (int i = wi - 1; i <= wi; i++) {
                if (i < 0 || i >= wc - 1 || !BLACK_AFTER[i % 7]) continue;
                float cx = (i + 1) * ww;
                if (Math.abs(x - cx) <= bw / 2f) {
                    int n = whiteNote(i) + 1;
                    return n <= 127 ? n : -1;
                }
            }
        }
        int n = whiteNote(wi);
        return n <= 127 ? n : -1;
    }

    private void press(int pid, float x, float y) {
        int k = keyAt(x, y);
        if (k < 0) return;
        touch.put(pid, k);
        if (listener != null) listener.down(k, pid);
        invalidate();
    }

    private void release(int pid) {
        int idx = touch.indexOfKey(pid);
        if (idx < 0) return;
        int k = touch.valueAt(idx);
        touch.removeAt(idx);
        if (listener != null) listener.up(k, pid);
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int act = e.getActionMasked();
        switch (act) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                int i = e.getActionIndex();
                press(e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < e.getPointerCount(); i++) {
                    int pid = e.getPointerId(i);
                    int k = keyAt(e.getX(i), e.getY(i));
                    int old = touch.get(pid, -1);
                    if (k != old) {
                        if (old >= 0) release(pid);
                        if (k >= 0) press(pid, e.getX(i), e.getY(i));
                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int i = e.getActionIndex();
                release(e.getPointerId(i));
                break;
            }
            case MotionEvent.ACTION_CANCEL: {
                for (int i = touch.size() - 1; i >= 0; i--) release(touch.keyAt(i));
                break;
            }
            default:
                break;
        }
        return true;
    }
}
