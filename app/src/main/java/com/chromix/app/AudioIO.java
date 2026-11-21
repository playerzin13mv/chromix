package com.chromix.app;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.FloatBuffer;

/** Leitura (WAV/MP3/OGG/M4A...), gravação do microfone e escrita de WAV. */
public final class AudioIO {
    private AudioIO() {}

    public static final int MAX_SECONDS = 30;

    public static final class Pcm {
        public float[] data;
        public int rate;
    }

    /** Lista simples de floats que cresce sozinha. */
    public static final class FloatBuf {
        float[] a = new float[1 << 16];
        int n = 0;
        public void add(float v) {
            if (n == a.length) a = java.util.Arrays.copyOf(a, a.length * 2);
            a[n++] = v;
        }
        public int size() { return n; }
        public float[] toArray() { return java.util.Arrays.copyOf(a, n); }
    }

    // ------------------------------------------------------------- WAV

    private static int le16(byte[] b, int i) { return (b[i] & 255) | ((b[i + 1] & 255) << 8); }
    private static int le32(byte[] b, int i) {
        return (b[i] & 255) | ((b[i + 1] & 255) << 8) | ((b[i + 2] & 255) << 16) | ((b[i + 3] & 255) << 24);
    }
    private static boolean tag(byte[] b, int i, String s) {
        for (int k = 0; k < 4; k++) if (b[i + k] != (byte) s.charAt(k)) return false;
        return true;
    }

    public static boolean looksLikeWav(byte[] b) {
        return b.length >= 12 && tag(b, 0, "RIFF") && tag(b, 8, "WAVE");
    }

    public static Pcm parseWav(byte[] b) throws IOException {
        if (!looksLikeWav(b)) throw new IOException("Arquivo não é um WAV válido");
        int pos = 12;
        int fmt = 1, ch = 1, rate = 44100, bits = 16;
        int dataOff = -1, dataLen = 0;
        while (pos + 8 <= b.length) {
            int len = le32(b, pos + 4);
            int body = pos + 8;
            if (tag(b, pos, "fmt ")) {
                fmt = le16(b, body);
                ch = le16(b, body + 2);
                rate = le32(b, body + 4);
                bits = le16(b, body + 14);
                if (fmt == 0xFFFE && len >= 26) fmt = le16(b, body + 24);
            } else if (tag(b, pos, "data")) {
                dataOff = body;
                dataLen = (len < 0) ? (b.length - body) : Math.min(len, b.length - body);
                break;
            }
            if (len < 0) break;
            pos = body + len + (len & 1);
        }
        if (dataOff < 0) throw new IOException("WAV sem dados de áudio");
        if (ch < 1) ch = 1;
        int bytes = bits / 8;
        if (bytes < 1 || bytes > 4) throw new IOException("WAV de " + bits + " bits não suportado");
        int frames = dataLen / (bytes * ch);
        frames = Math.min(frames, rate * MAX_SECONDS);
        float[] o = new float[frames];
        for (int f = 0; f < frames; f++) {
            float sum = 0;
            for (int c = 0; c < ch; c++) {
                int i = dataOff + (f * ch + c) * bytes;
                float v;
                if (bytes == 1) v = ((b[i] & 255) - 128) / 128f;
                else if (bytes == 2) v = (short) le16(b, i) / 32768f;
                else if (bytes == 3) v = ((b[i + 2] << 16) | ((b[i + 1] & 255) << 8) | (b[i] & 255)) / 8388608f;
                else if (fmt == 3) v = Float.intBitsToFloat(le32(b, i));
                else v = le32(b, i) / 2147483648f;
                sum += v;
            }
            o[f] = sum / ch;
        }
        Pcm p = new Pcm();
        p.data = o;
        p.rate = rate;
        return p;
    }

    public static void writeWav(File f, float[] d, int rate) throws IOException {
        int n = d.length;
        byte[] out = new byte[44 + n * 2];
        ByteBuffer bb = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN);
        bb.put(new byte[]{'R', 'I', 'F', 'F'});
        bb.putInt(36 + n * 2);
        bb.put(new byte[]{'W', 'A', 'V', 'E', 'f', 'm', 't', ' '});
        bb.putInt(16);
        bb.putShort((short) 1);
        bb.putShort((short) 1);
        bb.putInt(rate);
        bb.putInt(rate * 2);
        bb.putShort((short) 2);
        bb.putShort((short) 16);
        bb.put(new byte[]{'d', 'a', 't', 'a'});
        bb.putInt(n * 2);
        for (int i = 0; i < n; i++) {
            float v = Math.max(-1f, Math.min(1f, d[i]));
            bb.putShort((short) Math.round(v * 32767f));
        }
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(out);
        }
    }

    // ------------------------------------------------------------- Importar

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[32768];
        int n;
        while ((n = in.read(b)) > 0) o.write(b, 0, n);
        return o.toByteArray();
    }

    /** Lê qualquer áudio que o Android consiga decodificar. */
    public static Pcm decode(Context ctx, Uri uri) throws IOException {
        byte[] head = new byte[12];
        int got = 0;
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Não consegui abrir o arquivo");
            while (got < 12) {
                int r = in.read(head, got, 12 - got);
                if (r <= 0) break;
                got += r;
            }
        }
        if (got == 12 && looksLikeWav(head)) {
            try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IOException("Não consegui abrir o arquivo");
                return parseWav(readAll(in));
            }
        }
        return decodeWithCodec(ctx, uri);
    }

    private static Pcm decodeWithCodec(Context ctx, Uri uri) throws IOException {
        MediaExtractor ex = new MediaExtractor();
        MediaCodec codec = null;
        try {
            ex.setDataSource(ctx, uri, null);
            int track = -1;
            MediaFormat fmt = null;
            String mime = null;
            for (int i = 0; i < ex.getTrackCount(); i++) {
                MediaFormat f = ex.getTrackFormat(i);
                String m = f.getString(MediaFormat.KEY_MIME);
                if (m != null && m.startsWith("audio/")) { track = i; fmt = f; mime = m; break; }
            }
            if (track < 0) throw new IOException("Esse arquivo não tem áudio");
            ex.selectTrack(track);
            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(fmt, null, null, 0);
            codec.start();

            int rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int ch = fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;
            boolean pcmFloat = false;
            FloatBuf out = new FloatBuf();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inDone = false, outDone = false;

            while (!outDone) {
                if (!inDone) {
                    int ii = codec.dequeueInputBuffer(10000);
                    if (ii >= 0) {
                        ByteBuffer ib = codec.getInputBuffer(ii);
                        int n = ib == null ? -1 : ex.readSampleData(ib, 0);
                        if (n < 0) {
                            codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inDone = true;
                        } else {
                            codec.queueInputBuffer(ii, 0, n, ex.getSampleTime(), 0);
                            ex.advance();
                        }
                    }
                }
                int oi = codec.dequeueOutputBuffer(info, 10000);
                if (oi >= 0) {
                    ByteBuffer ob = codec.getOutputBuffer(oi);
                    if (ob != null && info.size > 0) {
                        ob.position(info.offset);
                        ob.limit(info.offset + info.size);
                        ob.order(ByteOrder.nativeOrder());
                        if (pcmFloat) {
                            FloatBuffer fb = ob.asFloatBuffer();
                            while (fb.remaining() >= ch) {
                                float s = 0;
                                for (int c = 0; c < ch; c++) s += fb.get();
                                out.add(s / ch);
                            }
                        } else {
                            ShortBuffer sb = ob.asShortBuffer();
                            while (sb.remaining() >= ch) {
                                float s = 0;
                                for (int c = 0; c < ch; c++) s += sb.get() / 32768f;
                                out.add(s / ch);
                            }
                        }
                    }
                    codec.releaseOutputBuffer(oi, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outDone = true;
                    if (out.size() >= rate * MAX_SECONDS) outDone = true;
                } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat nf = codec.getOutputFormat();
                    if (nf.containsKey(MediaFormat.KEY_SAMPLE_RATE)) rate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    if (nf.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) ch = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if (nf.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        pcmFloat = nf.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT;
                    }
                }
            }
            if (out.size() < 64) throw new IOException("Áudio vazio ou muito curto");
            Pcm p = new Pcm();
            p.data = out.toArray();
            if (p.data.length > rate * MAX_SECONDS) p.data = java.util.Arrays.copyOf(p.data, rate * MAX_SECONDS);
            p.rate = rate;
            return p;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Formato de áudio não suportado");
        } finally {
            try { if (codec != null) { codec.stop(); codec.release(); } } catch (Exception ignored) { }
            ex.release();
        }
    }

    // ------------------------------------------------------------- Gravar

    public static final class Recorder {
        private AudioRecord ar;
        private Thread t;
        private volatile boolean run;
        private final FloatBuf buf = new FloatBuf();
        public final int rate = 44100;

        @SuppressWarnings("MissingPermission")
        public boolean start() {
            try {
                int min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                if (min <= 0) return false;
                ar = new AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, Math.max(min * 2, rate));
                if (ar.getState() != AudioRecord.STATE_INITIALIZED) { ar.release(); ar = null; return false; }
                ar.startRecording();
            } catch (Exception e) {
                return false;
            }
            run = true;
            t = new Thread(new Runnable() {
                @Override public void run() {
                    short[] b = new short[2048];
                    while (run) {
                        int n = ar.read(b, 0, b.length);
                        if (n <= 0) continue;
                        synchronized (buf) {
                            for (int i = 0; i < n; i++) buf.add(b[i] / 32768f);
                            if (buf.size() >= rate * MAX_SECONDS) run = false;
                        }
                    }
                }
            }, "chromix-rec");
            t.start();
            return true;
        }

        public boolean isRunning() { return run; }

        public float[] stop() {
            run = false;
            try { if (t != null) t.join(500); } catch (InterruptedException ignored) { }
            try { if (ar != null) { ar.stop(); ar.release(); } } catch (Exception ignored) { }
            ar = null;
            synchronized (buf) { return buf.toArray(); }
        }
    }
}
