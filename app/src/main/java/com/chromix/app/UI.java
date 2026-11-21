package com.chromix.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/** Ajudantes para montar a interface em código. */
public final class UI {
    private UI() {}

    public interface Fmt { String f(int v); }
    public interface OnVal { void on(int v, boolean fromUser); }
    public interface OnStep { void on(int dir); }

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable rr(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    public static void setBg(View v, int color, float radiusDp) {
        Context c = v.getContext();
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF),
                rr(c, color, radiusDp), rr(c, 0xFFFFFFFF, radiusDp)));
    }

    public static TextView btn(Context c, String s, int bg, int fg, View.OnClickListener l) {
        TextView t = text(c, s, 13, fg, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8));
        t.setClickable(true);
        t.setSingleLine(true);
        setBg(t, bg, 10);
        if (l != null) t.setOnClickListener(l);
        return t;
    }

    public static void restyle(TextView t, int bg, int fg) {
        t.setTextColor(fg);
        setBg(t, bg, 10);
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams lpw(float weight) {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight);
    }

    public static LinearLayout.LayoutParams lpm(int w, int h, int l, int t, int r, int b, Context c) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    /** Caixa com título para agrupar controles. */
    public static LinearLayout card(Context c, String title) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        l.setBackground(rr(c, Pal.card, 14));
        if (title != null) {
            TextView t = text(c, title.toUpperCase(), 11, Pal.accent, true);
            t.setLetterSpacing(0.08f);
            l.addView(t, lpm(-2, -2, 0, 0, 0, 4, c));
        }
        return l;
    }

    public static Switch sw(Context c, String label, boolean v, CompoundButton.OnCheckedChangeListener l) {
        Switch s = new Switch(c);
        s.setText(label);
        s.setTextColor(Pal.text);
        s.setTextSize(13);
        s.setChecked(v);
        int[][] st = new int[][]{{android.R.attr.state_checked}, {}};
        s.setThumbTintList(new ColorStateList(st, new int[]{Pal.accent, Pal.sub}));
        s.setTrackTintList(new ColorStateList(st, new int[]{Pal.blend(Pal.accent, Pal.card, 0.5f), Pal.line}));
        s.setOnCheckedChangeListener(l);
        s.setPadding(0, dp(c, 4), 0, dp(c, 4));
        return s;
    }

    /** Título + valor + barra deslizante. */
    public static class SeekRow extends LinearLayout {
        public final SeekBar bar;
        private final TextView val;
        private final Fmt fmt;
        private boolean silent;

        public SeekRow(Context c, String title, int min, int max, int v, final Fmt fmt, final OnVal cb) {
            super(c);
            this.fmt = fmt;
            setOrientation(VERTICAL);
            LinearLayout h = new LinearLayout(c);
            h.setOrientation(HORIZONTAL);
            TextView t = text(c, title, 12, Pal.sub, false);
            h.addView(t, new LayoutParams(0, -2, 1f));
            val = text(c, fmt.f(v), 12, Pal.text, true);
            h.addView(val);
            addView(h);
            bar = new SeekBar(c);
            bar.setMin(min);
            bar.setMax(max);
            bar.setProgress(v);
            bar.setProgressTintList(ColorStateList.valueOf(Pal.accent));
            bar.setThumbTintList(ColorStateList.valueOf(Pal.accent));
            bar.setProgressBackgroundTintList(ColorStateList.valueOf(Pal.line));
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar sb, int pr, boolean user) {
                    val.setText(fmt.f(pr));
                    if (!silent && cb != null) cb.on(pr, user);
                }
                @Override public void onStartTrackingTouch(SeekBar sb) { }
                @Override public void onStopTrackingTouch(SeekBar sb) { }
            });
            addView(bar, new LayoutParams(-1, -2));
        }

        public void set(int v) {
            silent = true;
            bar.setProgress(v);
            val.setText(fmt.f(v));
            silent = false;
        }
    }

    /** Título + botões − e + com o valor no meio. */
    public static class Stepper extends LinearLayout {
        private final TextView val;

        public Stepper(Context c, String title, final OnStep cb) {
            super(c);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            addView(text(c, title, 12, Pal.sub, false), new LayoutParams(0, -2, 1f));
            TextView minus = btn(c, "−", Pal.card2, Pal.text, new OnClickListener() {
                @Override public void onClick(View v) { cb.on(-1); }
            });
            minus.setPadding(dp(c, 12), dp(c, 4), dp(c, 12), dp(c, 4));
            minus.setTextSize(16);
            addView(minus, new LayoutParams(-2, dp(c, 34)));
            val = text(c, "", 13, Pal.text, true);
            val.setGravity(Gravity.CENTER);
            addView(val, new LayoutParams(dp(c, 92), -2));
            TextView plus = btn(c, "+", Pal.card2, Pal.text, new OnClickListener() {
                @Override public void onClick(View v) { cb.on(1); }
            });
            plus.setPadding(dp(c, 12), dp(c, 4), dp(c, 12), dp(c, 4));
            plus.setTextSize(16);
            addView(plus, new LayoutParams(-2, dp(c, 34)));
        }

        public void set(String s) { val.setText(s); }
    }
}
