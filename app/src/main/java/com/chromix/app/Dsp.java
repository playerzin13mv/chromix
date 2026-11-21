package com.chromix.app;

/** Funções de processamento de sinal (sem dependência de Android). */
public final class Dsp {
    private Dsp() {}

    public static float peak(float[] d, int from, int to) {
        float m = 0;
        from = Math.max(0, from);
        to = Math.min(d.length, to);
        for (int i = from; i < to; i++) {
            float a = Math.abs(d[i]);
            if (a > m) m = a;
        }
        return m;
    }

    private static float[] boxFilter(float[] x, int w) {
        int n = x.length;
        float[] o = new float[n];
        int half = w / 2;
        double sum = 0;
        int lo = 0, hi = -1;
        for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - half), b = Math.min(n - 1, i + half);
            while (hi < b) { hi++; sum += x[hi]; }
            while (lo < a) { sum -= x[lo]; lo++; }
            o[i] = (float) (sum / (b - a + 1));
        }
        return o;
    }

    private static float g(float[] s, int n, int i) {
        if (i < 0) i = 0;
        if (i > n - 1) i = n - 1;
        return s[i];
    }

    /** Reamostra s[from,to) lendo 'step' amostras de origem por amostra de saída (interpolação Hermite). */
    public static float[] resample(float[] src, int from, int to, double step) {
        from = Math.max(0, from);
        to = Math.min(src.length, to);
        int n = to - from;
        if (n < 4 || step <= 0) return new float[0];
        float[] s = new float[n];
        System.arraycopy(src, from, s, 0, n);
        if (step >= 1.5) {
            int w = (int) Math.round(step);
            s = boxFilter(s, w);
        }
        int outN = (int) Math.floor((n - 1) / step) + 1;
        float[] o = new float[outN];
        for (int i = 0; i < outN; i++) {
            double t = i * step;
            int i1 = (int) t;
            double f = t - i1;
            double y0 = g(s, n, i1 - 1), y1 = g(s, n, i1), y2 = g(s, n, i1 + 1), y3 = g(s, n, i1 + 2);
            double c1 = 0.5 * (y2 - y0);
            double c2 = y0 - 2.5 * y1 + 2.0 * y2 - 0.5 * y3;
            double c3 = 0.5 * (y3 - y0) + 1.5 * (y1 - y2);
            o[i] = (float) (((c3 * f + c2) * f + c1) * f + y1);
        }
        return o;
    }

    public static short[] toPcm16(float[] d, float gain) {
        short[] o = new short[d.length];
        for (int i = 0; i < d.length; i++) {
            float v = d[i] * gain;
            if (v > 1f) v = 1f;
            if (v < -1f) v = -1f;
            o[i] = (short) Math.round(v * 32767f);
        }
        return o;
    }

    /** Detecta a frequência fundamental (Hz) ou -1. Usa autocorrelação normalizada. */
    public static double detectPitch(float[] d, int from, int to, int rate) {
        from = Math.max(0, from);
        to = Math.min(d.length, to);
        int n = to - from;
        int minLag = Math.max(2, rate / 1200);
        int maxLag = rate / 60;
        int W = 2048;
        if (n < W + maxLag) W = n - maxLag;
        if (W < 512) return -1;

        int best = from;
        double bestE = -1;
        int stride = Math.max(256, W / 2);
        for (int s = from; s + W + maxLag <= to; s += stride) {
            double e = 0;
            for (int i = 0; i < W; i += 2) e += (double) d[s + i] * d[s + i];
            if (e > bestE) { bestE = e; best = s; }
        }
        if (bestE <= 1e-6) return -1;

        double[] nsdf = new double[maxLag + 2];
        double gmax = 0;
        for (int lag = minLag; lag <= maxLag; lag++) {
            double r = 0, m = 0;
            for (int i = 0; i < W; i++) {
                double a = d[best + i], b = d[best + i + lag];
                r += a * b;
                m += a * a + b * b;
            }
            nsdf[lag] = m > 0 ? 2 * r / m : 0;
            if (nsdf[lag] > gmax) gmax = nsdf[lag];
        }
        if (gmax < 0.5) return -1;
        for (int lag = minLag + 1; lag < maxLag; lag++) {
            if (nsdf[lag] > nsdf[lag - 1] && nsdf[lag] >= nsdf[lag + 1] && nsdf[lag] >= 0.9 * gmax) {
                double a = nsdf[lag - 1], b = nsdf[lag], c = nsdf[lag + 1];
                double den = a - 2 * b + c;
                double shift = den != 0 ? 0.5 * (a - c) / den : 0;
                return rate / (lag + shift);
            }
        }
        return -1;
    }

    public static double freqToMidi(double hz) {
        return 69.0 + 12.0 * Math.log(hz / 440.0) / Math.log(2.0);
    }

    /** Procura o cruzamento por zero ascendente mais próximo de idx. */
    public static int snapZero(float[] d, int idx, int radius, int min, int max) {
        int best = idx;
        int bestDist = Integer.MAX_VALUE;
        for (int k = 0; k <= radius; k++) {
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                int i = idx + sgn * k;
                if (i < min || i + 1 >= max) continue;
                if (d[i] <= 0 && d[i + 1] > 0 && k < bestDist) {
                    best = i + 1;
                    bestDist = k;
                }
            }
            if (bestDist != Integer.MAX_VALUE) break;
        }
        return best;
    }

    /** Ajusta os pontos de loop para emendarem sem estalo. Retorna {inicio, fim}. */
    public static int[] refineLoop(float[] d, int ls, int le, int min, int max) {
        int s = snapZero(d, ls, 400, min, max);
        int cmp = 64;
        int bestEnd = le;
        double bestCost = Double.MAX_VALUE;
        int radius = 800;
        for (int e = Math.max(s + 64, le - radius); e <= Math.min(max - cmp - 1, le + radius); e++) {
            double cost = 0;
            for (int j = 0; j < cmp; j++) {
                double diff = d[s + j] - d[e + j];
                cost += diff * diff;
            }
            if (cost < bestCost) { bestCost = cost; bestEnd = e; }
        }
        return new int[]{s, bestEnd};
    }

    /** Voz sintética "ah" periódica (período inteiro = rate/200 amostras) para testar o app. */
    public static float[] demoVoice(int rate) {
        int period = 200;
        double f0 = rate / (double) period;
        int n = (rate * 12 / 10 / period) * period;
        float[] o = new float[n];
        for (int h = 1; h < 40; h++) {
            double hf = h * f0;
            if (hf > rate / 2.2) break;
            double amp = Math.pow(h, -0.8)
                    * (1.0 + 2.5 * Math.exp(-sq((hf - 700) / 220)) + 1.5 * Math.exp(-sq((hf - 1200) / 260))
                    + 0.6 * Math.exp(-sq((hf - 2600) / 400)));
            for (int i = 0; i < n; i++) {
                o[i] += (float) (amp * Math.sin(2 * Math.PI * h * i / period));
            }
        }
        int a = rate / 40, r = rate / 8;
        for (int i = 0; i < n; i++) {
            float env = 1f;
            if (i < a) env = i / (float) a;
            if (i > n - r) env = Math.min(env, (n - i) / (float) r);
            o[i] *= env;
        }
        float pk = peak(o, 0, n);
        if (pk > 0) for (int i = 0; i < n; i++) o[i] *= 0.8f / pk;
        return o;
    }

    private static double sq(double v) { return v * v; }
}
