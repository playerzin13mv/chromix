package com.chromix.app;

import org.json.JSONObject;

/** Um áudio (uma "vogal") com seus próprios cortes, loop e afinação. */
public class Slot {
    public int sampleRate = 44100;
    public int frames = 0;
    public int rootNote = 60;
    public int fineCents = 0;        // quanto a amostra está "sustenida" (cents)
    public int trimStart = 0;
    public int trimEnd = 0;
    public boolean loop = false;
    public int loopStart = 0;
    public int loopEnd = 0;

    public boolean has() { return frames > 0; }

    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("sampleRate", sampleRate);
            o.put("frames", frames);
            o.put("rootNote", rootNote);
            o.put("fineCents", fineCents);
            o.put("trimStart", trimStart);
            o.put("trimEnd", trimEnd);
            o.put("loop", loop);
            o.put("loopStart", loopStart);
            o.put("loopEnd", loopEnd);
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static Slot fromJson(JSONObject o) {
        Slot s = new Slot();
        s.sampleRate = o.optInt("sampleRate", 44100);
        s.frames = o.optInt("frames", 0);
        s.rootNote = o.optInt("rootNote", 60);
        s.fineCents = o.optInt("fineCents", 0);
        s.trimStart = o.optInt("trimStart", 0);
        s.trimEnd = o.optInt("trimEnd", 0);
        s.loop = o.optBoolean("loop", false);
        s.loopStart = o.optInt("loopStart", 0);
        s.loopEnd = o.optInt("loopEnd", 0);
        return s;
    }

    /** Garante que corte e loop sejam coerentes. */
    public void sanitize() {
        rootNote = Math.max(0, Math.min(127, rootNote));
        if (frames <= 0) { trimStart = 0; trimEnd = 0; loopStart = 0; loopEnd = 0; return; }
        trimStart = Math.max(0, Math.min(trimStart, frames - 1));
        if (trimEnd <= trimStart || trimEnd > frames) trimEnd = frames;
        loopStart = Math.max(trimStart, Math.min(loopStart, trimEnd - 1));
        if (loopEnd <= loopStart || loopEnd > trimEnd) loopEnd = trimEnd;
    }
}
