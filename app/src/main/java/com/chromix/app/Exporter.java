package com.chromix.app;

import java.util.ArrayList;
import java.util.List;

/**
 * Converte um Project + áudios em um arquivo .sf2.
 * No modo vogais, a tecla MIDI k toca a vogal ativa número (k mod n), onde n é quantas vogais têm áudio.
 * (A mesma regra é usada pelo piano do app, em Synth.)
 */
public final class Exporter {
    private Exporter() {}

    public static final long MAX_BYTES = 150L * 1024 * 1024;

    private static final String[] NOTES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

    private static String noteName(int midi) {
        return NOTES[midi % 12] + (midi / 12 - 1);
    }

    public static int outRate(Project p, Slot s) {
        return p.exportRate > 0 ? p.exportRate : s.sampleRate;
    }

    /** Estimativa do tamanho do .sf2 em bytes. */
    public static long estimateBytes(Project p) {
        int[] act = p.activeSlots(null);
        if (act.length == 0) return 0;
        p.sanitize();
        int n = act.length;
        double total = 0;
        long zones = 0;
        if (p.exportMode == 0) {
            for (int a : act) {
                Slot s = p.slots[a];
                double rr = (double) outRate(p, s) / Math.max(1, s.sampleRate);
                total += (s.trimEnd - s.trimStart) * rr + 46;
            }
            zones = n == 1 ? 1 : (p.rangeHi - p.rangeLo + 1);
        } else {
            for (int k = p.rangeLo; k <= p.rangeHi; k++) {
                Slot s = p.slots[act[k % n]];
                double rr = (double) outRate(p, s) / Math.max(1, s.sampleRate);
                double semis = k - (s.rootNote + s.fineCents / 100.0);
                double step = Math.pow(2, semis / 12.0);
                total += (s.trimEnd - s.trimStart) / step * rr + 46;
            }
            zones = p.rangeHi - p.rangeLo + 1;
        }
        return (long) (total * 2) + 2048 + zones * 120L;
    }

    public static byte[] build(Project p, float[][] audios, String author) {
        p.sanitize();
        int[] act = p.activeSlots(audios);
        if (act.length == 0) throw new IllegalStateException("Esse projeto ainda não tem áudio");
        if (estimateBytes(p) > MAX_BYTES) {
            throw new IllegalStateException("Arquivo grande demais. Corte o áudio ou diminua a faixa de notas.");
        }
        int n = act.length;

        // volume de cada vogal normalizado separadamente (todas ficam com volume parecido)
        float[] gain = new float[5];
        for (int a : act) {
            Slot s = p.slots[a];
            float peak = Dsp.peak(audios[a], s.trimStart, s.trimEnd);
            gain[a] = (p.normalize && peak > 0.0001f) ? 0.95f / peak : 1f;
        }

        List<Sf2Writer.Sample> samples = new ArrayList<>();
        List<Sf2Writer.Zone> zones = new ArrayList<>();

        if (p.exportMode == 0) {
            int[] sampleOf = new int[5];
            for (int a : act) {
                Slot s = p.slots[a];
                int outRate = outRate(p, s);
                double rateRatio = (double) s.sampleRate / outRate;
                float[] seg = (rateRatio == 1.0) ? slice(audios[a], s.trimStart, s.trimEnd)
                        : Dsp.resample(audios[a], s.trimStart, s.trimEnd, rateRatio);
                Sf2Writer.Sample sm = makeSample(seg, gain[a], s.loop,
                        (int) Math.round((s.loopStart - s.trimStart) / rateRatio),
                        (int) Math.round((s.loopEnd - s.trimStart) / rateRatio));
                sm.name = p.vowelMode ? Project.VOWELS[a] + "_" + shortName(p.name, 16) : shortName(p.name, 19);
                sm.rate = outRate;
                sm.rootKey = s.rootNote;
                sm.cents = clamp(-s.fineCents, -99, 99);
                sm.lo = p.rangeLo;
                sm.hi = p.rangeHi;
                sampleOf[a] = samples.size();
                samples.add(sm);
            }
            if (n == 1) {
                zones.add(new Sf2Writer.Zone(0, p.rangeLo, p.rangeHi));
            } else {
                for (int k = p.rangeLo; k <= p.rangeHi; k++) {
                    zones.add(new Sf2Writer.Zone(sampleOf[act[k % n]], k, k));
                }
            }
        } else {
            for (int k = p.rangeLo; k <= p.rangeHi; k++) {
                int a = act[k % n];
                Slot s = p.slots[a];
                int outRate = outRate(p, s);
                double rateRatio = (double) s.sampleRate / outRate;
                double semis = k - (s.rootNote + s.fineCents / 100.0);
                double step = Math.pow(2, semis / 12.0) * rateRatio;
                float[] seg = Dsp.resample(audios[a], s.trimStart, s.trimEnd, step);
                if (seg.length < 48) continue;
                Sf2Writer.Sample sm = makeSample(seg, gain[a], s.loop,
                        (int) Math.round((s.loopStart - s.trimStart) / step),
                        (int) Math.round((s.loopEnd - s.trimStart) / step));
                sm.name = (p.vowelMode ? Project.VOWELS[a] + "_" : "") + noteName(k) + "_" + k;
                sm.rate = outRate;
                sm.rootKey = k;
                sm.cents = 0;
                sm.lo = k;
                sm.hi = k;
                zones.add(new Sf2Writer.Zone(samples.size(), k, k));
                samples.add(sm);
            }
            if (samples.isEmpty()) throw new IllegalStateException("O áudio ficou curto demais para essas notas");
        }
        return Sf2Writer.build(p.name, shortName(p.name, 19), author, samples, zones,
                p.attackMs, p.releaseMs, p.volume);
    }

    private static Sf2Writer.Sample makeSample(float[] seg, float gain, boolean loop, int ls, int le) {
        int n = seg.length;
        int fi = Math.min(16, n / 4);
        for (int i = 0; i < fi; i++) seg[i] *= i / (float) fi;
        le = Math.min(le, n);
        boolean useLoop = loop && le - ls >= 32 && ls >= 0;
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

    private static String shortName(String s, int max) {
        if (s == null || s.trim().isEmpty()) return "Chromatic";
        s = s.trim();
        return s.length() > max ? s.substring(0, max) : s;
    }
}
