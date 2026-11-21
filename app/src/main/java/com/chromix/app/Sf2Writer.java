package com.chromix.app;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Gera um arquivo SoundFont 2 (.sf2) completo, sem dependências. */
public final class Sf2Writer {

    public static final class Sample {
        public String name = "Sample";
        public short[] pcm;
        public int rate = 44100;
        public int rootKey = 60;
        public int cents = 0;          // correção de afinação (cents)
        public boolean loop = false;
        public int loopStart, loopEnd; // relativos ao início da amostra
        public int lo = 0, hi = 127;   // faixa de teclas da zona
    }

    /** Uma zona do instrumento: uma faixa de teclas que toca uma das amostras. */
    public static final class Zone {
        public final int sample, lo, hi;
        public Zone(int sample, int lo, int hi) { this.sample = sample; this.lo = lo; this.hi = hi; }
    }

    private Sf2Writer() {}

    private static final class Buf {
        final ByteArrayOutputStream o = new ByteArrayOutputStream();
        void u8(int v) { o.write(v & 255); }
        void u16(int v) { u8(v); u8(v >> 8); }
        void u32(long v) { u16((int) (v & 0xFFFF)); u16((int) ((v >> 16) & 0xFFFF)); }
        void tag(String s) { byte[] b = s.getBytes(StandardCharsets.ISO_8859_1); o.write(b, 0, 4); }
        void str(String s, int n) {
            byte[] b = clean(s).getBytes(StandardCharsets.ISO_8859_1);
            for (int i = 0; i < n; i++) u8(i < b.length && i < n - 1 ? b[i] : 0);
        }
        void raw(byte[] b) { o.write(b, 0, b.length); }
        int size() { return o.size(); }
        byte[] bytes() { return o.toByteArray(); }
    }

    private static String clean(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(c >= 32 && c < 127 ? c : '_');
        }
        return sb.toString();
    }

    private static byte[] chunk(String id, byte[] data) {
        Buf b = new Buf();
        b.tag(id);
        b.u32(data.length);
        b.raw(data);
        if ((data.length & 1) == 1) b.u8(0);
        return b.bytes();
    }

    private static byte[] list(String type, byte[] content) {
        Buf b = new Buf();
        b.tag("LIST");
        b.u32(4 + content.length);
        b.tag(type);
        b.raw(content);
        return b.bytes();
    }

    private static byte[] zstr(String s) {
        byte[] t = clean(s).getBytes(StandardCharsets.ISO_8859_1);
        byte[] r = new byte[t.length + 1];
        System.arraycopy(t, 0, r, 0, t.length);
        return r;
    }

    private static int timecents(int ms) {
        double sec = Math.max(ms, 1) / 1000.0;
        int tc = (int) Math.round(1200.0 * Math.log(sec) / Math.log(2));
        return Math.max(-12000, Math.min(8000, tc));
    }

    /** Uma zona por amostra, usando lo/hi de cada amostra. */
    public static byte[] build(String bankName, String presetName, String author,
                               List<Sample> ss, int attackMs, int releaseMs, int volPercent) {
        List<Zone> zs = new ArrayList<>();
        for (int i = 0; i < ss.size(); i++) zs.add(new Zone(i, ss.get(i).lo, ss.get(i).hi));
        return build(bankName, presetName, author, ss, zs, attackMs, releaseMs, volPercent);
    }

    public static byte[] build(String bankName, String presetName, String author,
                               List<Sample> ss, List<Zone> zones, int attackMs, int releaseMs, int volPercent) {
        int n = ss.size();
        int nz = zones.size();
        if (n == 0 || nz == 0) throw new IllegalArgumentException("Nenhuma amostra");

        // ---- sdta / smpl ----
        Buf smpl = new Buf();
        long[] start = new long[n];
        for (int i = 0; i < n; i++) {
            Sample s = ss.get(i);
            start[i] = smpl.size() / 2;
            for (short v : s.pcm) smpl.u16(v & 0xFFFF);
            for (int z = 0; z < 46; z++) smpl.u16(0);
        }

        // ---- shdr ----
        Buf shdr = new Buf();
        for (int i = 0; i < n; i++) {
            Sample s = ss.get(i);
            long len = s.pcm.length;
            shdr.str(s.name, 20);
            shdr.u32(start[i]);
            shdr.u32(start[i] + len);
            if (s.loop) {
                shdr.u32(start[i] + s.loopStart);
                shdr.u32(start[i] + s.loopEnd);
            } else {
                shdr.u32(start[i]);
                shdr.u32(start[i] + len);
            }
            shdr.u32(s.rate);
            shdr.u8(Math.max(0, Math.min(127, s.rootKey)));
            shdr.u8(s.cents & 255);
            shdr.u16(0);
            shdr.u16(1); // mono
        }
        shdr.str("EOS", 20);
        for (int k = 0; k < 5; k++) shdr.u32(0);
        shdr.u8(0); shdr.u8(0); shdr.u16(0); shdr.u16(0);

        // ---- ibag / igen ----
        Buf ibag = new Buf();
        Buf igen = new Buf();
        int genCount = 0;
        int cB = 0;
        if (volPercent < 100) {
            double v = Math.max(volPercent, 1) / 100.0;
            cB = (int) Math.min(1440, Math.round(-200.0 * Math.log10(v)));
        }
        for (int i = 0; i < nz; i++) {
            Zone z = zones.get(i);
            Sample s = ss.get(z.sample);
            ibag.u16(genCount);
            ibag.u16(0);
            igen.u16(43); igen.u8(z.lo); igen.u8(z.hi); genCount++;           // keyRange
            if (attackMs > 1) { igen.u16(34); igen.u16(timecents(attackMs) & 0xFFFF); genCount++; }
            igen.u16(38); igen.u16(timecents(releaseMs) & 0xFFFF); genCount++; // release
            if (cB > 0) { igen.u16(48); igen.u16(cB); genCount++; }            // atenuação
            if (s.loop) { igen.u16(54); igen.u16(1); genCount++; }             // sampleModes
            igen.u16(58); igen.u16(s.rootKey & 0xFFFF); genCount++;            // overridingRootKey
            igen.u16(53); igen.u16(z.sample); genCount++;                      // sampleID (último)
        }
        ibag.u16(genCount); ibag.u16(0);
        igen.u16(0); igen.u16(0);

        // ---- inst ----
        Buf inst = new Buf();
        inst.str(presetName, 20); inst.u16(0);
        inst.str("EOI", 20); inst.u16(nz);

        Buf imod = new Buf();
        for (int k = 0; k < 10; k++) imod.u8(0);

        // ---- preset ----
        Buf phdr = new Buf();
        phdr.str(presetName, 20); phdr.u16(0); phdr.u16(0); phdr.u16(0);
        phdr.u32(0); phdr.u32(0); phdr.u32(0);
        phdr.str("EOP", 20); phdr.u16(0); phdr.u16(0); phdr.u16(1);
        phdr.u32(0); phdr.u32(0); phdr.u32(0);

        Buf pbag = new Buf();
        pbag.u16(0); pbag.u16(0);
        pbag.u16(1); pbag.u16(0);

        Buf pmod = new Buf();
        for (int k = 0; k < 10; k++) pmod.u8(0);

        Buf pgen = new Buf();
        pgen.u16(41); pgen.u16(0);
        pgen.u16(0); pgen.u16(0);

        // ---- INFO ----
        Buf info = new Buf();
        Buf ifil = new Buf(); ifil.u16(2); ifil.u16(1);
        info.raw(chunk("ifil", ifil.bytes()));
        info.raw(chunk("isng", zstr("EMU8000")));
        info.raw(chunk("INAM", zstr(bankName)));
        if (author != null && author.trim().length() > 0) info.raw(chunk("IENG", zstr(author)));
        info.raw(chunk("ICMT", zstr("Criado com Chromix")));
        info.raw(chunk("ISFT", zstr("Chromix 1.0")));

        Buf sdta = new Buf();
        sdta.raw(chunk("smpl", smpl.bytes()));

        Buf pdta = new Buf();
        pdta.raw(chunk("phdr", phdr.bytes()));
        pdta.raw(chunk("pbag", pbag.bytes()));
        pdta.raw(chunk("pmod", pmod.bytes()));
        pdta.raw(chunk("pgen", pgen.bytes()));
        pdta.raw(chunk("inst", inst.bytes()));
        pdta.raw(chunk("ibag", ibag.bytes()));
        pdta.raw(chunk("imod", imod.bytes()));
        pdta.raw(chunk("igen", igen.bytes()));
        pdta.raw(chunk("shdr", shdr.bytes()));

        byte[] l1 = list("INFO", info.bytes());
        byte[] l2 = list("sdta", sdta.bytes());
        byte[] l3 = list("pdta", pdta.bytes());

        Buf riff = new Buf();
        riff.tag("RIFF");
        riff.u32(4L + l1.length + l2.length + l3.length);
        riff.tag("sfbk");
        riff.raw(l1); riff.raw(l2); riff.raw(l3);
        return riff.bytes();
    }
}
