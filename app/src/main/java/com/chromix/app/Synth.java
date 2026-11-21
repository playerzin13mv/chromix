package com.chromix.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Process;

/** Sintetizador polifônico que toca a amostra em qualquer nota (teste do piano). */
public final class Synth {

    private static final class Voice {
        boolean on, released;
        int key, pid;
        double pos, step;
        float env;
        long age;
    }

    private final Object lock = new Object();
    private final Voice[] vs = new Voice[32];
    private final int outRate;
    private final int frames;
    private final Context ctx;

    private AudioTrack track;
    private Thread th;
    private volatile boolean running;
    private long ageCounter = 0;

    // amostra e parâmetros
    private float[] data = new float[0];
    private int srcRate = 44100, start = 0, end = 0, ls = 0, le = 0, root = 60;
    private boolean loop = false;
    private double fine = 0;
    private float atk = 0.005f, rel = 0.15f, gain = 0.9f;

    public Synth(Context c) {
        ctx = c.getApplicationContext();
        int r = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
        outRate = r >= 8000 ? r : 44100;
        int lat = Prefs.latency(ctx);
        frames = lat == 0 ? 256 : (lat == 2 ? 1024 : 512);
    }

    /** Define a amostra. 'from'..'to' é o trecho cortado; ls..le o loop (em índices absolutos). */
    public void set(float[] d, int rate, int from, int to, boolean loopOn, int loopS, int loopE,
                    int rootNote, double fineCents, float attackSec, float releaseSec, float g) {
        synchronized (lock) {
            data = d == null ? new float[0] : d;
            srcRate = rate;
            start = Math.max(0, from);
            end = Math.min(data.length, to);
            if (end < start) end = start;
            loop = loopOn;
            ls = loopS;
            le = loopE;
            root = rootNote;
            fine = fineCents;
            atk = attackSec;
            rel = releaseSec;
            gain = g;
        }
    }

    public void start() {
        if (running) return;
        try {
            int minBytes = AudioTrack.getMinBufferSize(outRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int bytes = Math.max(minBytes, frames * 2 * 2);
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(outRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(bytes)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    .build();
        } catch (Exception e) {
            track = null;
            return;
        }
        running = true;
        final AudioTrack t = track;
        th = new Thread(new Runnable() {
            @Override public void run() {
                try { Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO); } catch (Exception ignored) { }
                short[] buf = new short[frames];
                float[] mix = new float[frames];
                try { t.play(); } catch (Exception e) { return; }
                while (running) {
                    render(mix);
                    float m = Prefs.masterVol(ctx) / 100f;
                    for (int i = 0; i < frames; i++) {
                        float v = mix[i] * m;
                        if (v > 1f) v = 1f;
                        if (v < -1f) v = -1f;
                        buf[i] = (short) (v * 32767f);
                    }
                    try { t.write(buf, 0, frames); } catch (Exception e) { break; }
                }
            }
        }, "chromix-synth");
        th.start();
    }

    public void stop() {
        running = false;
        try { if (th != null) th.join(800); } catch (InterruptedException ignored) { }
        th = null;
        try {
            if (track != null) { track.stop(); track.release(); }
        } catch (Exception ignored) { }
        track = null;
        kill();
    }

    private void kill() {
        synchronized (lock) {
            for (Voice v : vs) if (v != null) v.on = false;
        }
    }

    public void noteOn(int key, int pid) {
        synchronized (lock) {
            if (end - start < 4) return;
            Voice v = null;
            for (int i = 0; i < vs.length; i++) {
                if (vs[i] == null) { vs[i] = new Voice(); v = vs[i]; break; }
                if (!vs[i].on) { v = vs[i]; break; }
            }
            if (v == null) {
                v = vs[0];
                for (Voice o : vs) if (o.age < v.age) v = o;
            }
            v.on = true;
            v.released = false;
            v.key = key;
            v.pid = pid;
            v.pos = start;
            v.env = 0f;
            v.age = ++ageCounter;
            double semis = key - root - fine / 100.0;
            v.step = Math.pow(2.0, semis / 12.0) * srcRate / outRate;
        }
    }

    public void noteOff(int pid) {
        synchronized (lock) {
            for (Voice v : vs) if (v != null && v.on && v.pid == pid) v.released = true;
        }
    }

    public void allOff() {
        synchronized (lock) {
            for (Voice v : vs) if (v != null && v.on) v.released = true;
        }
    }

    private void render(float[] mix) {
        java.util.Arrays.fill(mix, 0f);
        synchronized (lock) {
            final float[] d = data;
            final boolean loopActive = loop && le - ls >= 16 && ls >= start && le <= end;
            final int loopLen = le - ls;
            final float atkInc = 1f / (Math.max(atk, 0.001f) * outRate);
            final float relDec = 1f / (Math.max(rel, 0.005f) * outRate);
            for (Voice vo : vs) {
                if (vo == null || !vo.on) continue;
                double pos = vo.pos;
                float env = vo.env;
                for (int i = 0; i < frames; i++) {
                    if (!vo.released) {
                        if (env < 1f) { env += atkInc; if (env > 1f) env = 1f; }
                    } else {
                        env -= relDec;
                        if (env <= 0f) { vo.on = false; break; }
                    }
                    int i0 = (int) pos;
                    if (!loopActive && i0 >= end - 1) { vo.on = false; break; }
                    if (i0 < 0 || i0 >= d.length) { vo.on = false; break; }
                    float f = (float) (pos - i0);
                    float a = d[i0];
                    float b = d[Math.min(i0 + 1, end - 1 < 0 ? 0 : end - 1)];
                    float s = a + (b - a) * f;
                    if (!loopActive) {
                        int rem = end - i0;
                        if (rem < 128) s *= rem / 128f;
                    }
                    mix[i] += s * env * gain;
                    pos += vo.step;
                    if (loopActive) while (pos >= le) pos -= loopLen;
                }
                vo.pos = pos;
                vo.env = env;
            }
        }
    }
}
