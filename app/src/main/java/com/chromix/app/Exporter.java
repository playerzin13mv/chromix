package com.chromix.app;

import java.util.ArrayList;
import java.util.List;

/** Converte um Project + áudio em um arquivo .sf2. */
public final class Exporter {
    private Exporter() {}

    public static final long MAX_BYTES = 150L * 1024 * 1024;

    public static int outRate(Project p) {
        return p.exportRate > 0 ? p.exportRate : p.sampleRate;
    }

    /** Estimativa do tamanho do .sf2 em bytes. */
    public static long estimateBytes(Project p) {
        if (p.frames <= 0) return 0;
        p.sanitize();
        int len = Math.max(0, p.trimEnd - p.trimStart);
        double rateRatio = (double) outRate(p) / Math.max(1, p.sampleRate);
        if (p.exportMode == 0) return (long) ((len * rateRatio + 46) * 2) + 2048;
        double total = 0;
        for (int k = p.rangeLo; k <= p.rangeHi; k++) {
            double semis = k - (p.rootNote + p.fineCents / 100.0);
            double step = Math.pow(2, semis / 12.0);
            total += len / step * rateRatio + 46;
        }
        return (long) (total * 2) + 2048 + (p.rangeHi - p.rangeLo + 1) * 120L;
    }

    public static byte[] build(Project p, float[] audio, String author) {
        if (audio == null || p.frames <= 0) throw new IllegalStateException("Esse projeto ainda não tem áudio");
        p.sanitize();
        if (estimateBytes(p) > MAX_BYTES) {
            throw new IllegalStateException("Arquivo grande demais. Corte o áudio ou diminua a faixa de notas.");
        }
        int ts = p.trimStart, te = p.trimEnd;
        float peak = Dsp.peak(audio, ts, te);
        float gain = (p.normalize && peak > 0.0001f) ? 0.95f / peak : 1f;
        int outRate = outRate(p);
        double rateRatio = (double) p.sampleRate / outRate;

        List<Sf2Writer.Sample> list = new ArrayList<>();

        if (p.exportMode == 0) {
            float[] seg = (rateRatio == 1.0) ? slice(audio, ts, te) : Dsp.resample(audio, ts, te, rateRatio);
            Sf2Writer.Sample s = makeSample(seg, gain, p.loop,
                    (int) Math.round((p.loopStart - ts) / rateRatio),
                    (int) Math.round((p.loopEnd - ts) / rateRatio));
            s.name = shortName(p.name);
            s.rate = outRate;
            s.rootKey = p.rootNote;
            s.cents = clamp(-p.fineCents, -99, 99);
            s.lo = p.rangeLo;
            s.hi = p.rangeHi;
            list.add(s);
        } else {
            for (int k = p.rangeLo; k <= p.rangeHi; k++) {
                double semis = k - (p.rootNote + p.fineCents / 100.0);
                double step = Math.pow(2, semis / 12.0) * rateRatio;
                float[] seg = Dsp.resample(audio, ts, te, step);
                if (seg.length < 48) continue;
                Sf2Writer.Sample s = makeSample(seg, gain, p.loop,
                        (int) Math.round((p.loopStart - ts) / step),
                        (int) Math.round((p.loopEnd - ts) / step));
                s.name = Pal.noteName(k, 0) + "_" + k;
                s.rate = outRate;
                s.rootKey = k;
                s.cents = 0;
                s.lo = k;
                s.hi = k;
                list.add(s);
            }
            if (list.isEmpty()) throw new IllegalStateException("O áudio ficou curto demais para essas notas");
        }
        return Sf2Writer.build(p.name, shortName(p.name), author, list, p.attackMs, p.releaseMs, p.volume);
    }

    private static Sf2Writer.Sample makeSample(float[] seg, float gain, boolean loop, int ls, int le) {
        int n = seg.length;
        int fi = Math.min(16, n / 4);
        for (int i = 0; i < fi; i++) seg[i] *= i / (float) fi;
        boolean useLoop = loop && le - ls >= 32 && ls >= 0 && le <= n;
        if (!useLoop) {
            int fo = Math.min(64, n / 4);
            for (int i = 0; i < fo; i++) seg[n - 1 - i] *= i / (float) fo;
        }
        Sf2Writer.Sample s = new Sf2Writer.Sample();
        s.pcm = Dsp.toPcm16(seg, gain);
        s.loop = useLoop;
        s.loopStart = Math.max(0, ls);
        s.loopEnd = Math.min(n, le);
        return s;
    }

    private static float[] slice(float[] a, int from, int to) {
        float[] o = new float[Math.max(0, to - from)];
        System.arraycopy(a, from, o, 0, o.length);
        return o;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private static String shortName(String s) {
        if (s == null || s.trim().isEmpty()) return "Chromatic";
        s = s.trim();
        return s.length() > 19 ? s.substring(0, 19) : s;
    }
}
