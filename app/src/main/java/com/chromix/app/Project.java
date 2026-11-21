package com.chromix.app;

import org.json.JSONObject;

/** Um projeto de Chromatic. O áudio fica num .wav separado com o mesmo id. */
public class Project {
    public String id = "";
    public String name = "Chromatic";
    public long created, modified;

    public int sampleRate = 44100;
    public int frames = 0;
    public int rootNote = 60;
    public int fineCents = 0;        // quanto a amostra está "sustenida" (cents)
    public int trimStart = 0;
    public int trimEnd = 0;
    public boolean loop = false;
    public int loopStart = 0;
    public int loopEnd = 0;
    public int attackMs = 5;
    public int releaseMs = 150;
    public int volume = 100;
    public boolean normalize = true;
    public int rangeLo = 36;
    public int rangeHi = 96;
    public int exportMode = 0;       // 0 = nota única, 1 = uma amostra por nota
    public int exportRate = 0;       // 0 = original

    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("name", name);
            o.put("created", created);
            o.put("modified", modified);
            o.put("sampleRate", sampleRate);
            o.put("frames", frames);
            o.put("rootNote", rootNote);
            o.put("fineCents", fineCents);
            o.put("trimStart", trimStart);
            o.put("trimEnd", trimEnd);
            o.put("loop", loop);
            o.put("loopStart", loopStart);
            o.put("loopEnd", loopEnd);
            o.put("attackMs", attackMs);
            o.put("releaseMs", releaseMs);
            o.put("volume", volume);
            o.put("normalize", normalize);
            o.put("rangeLo", rangeLo);
            o.put("rangeHi", rangeHi);
            o.put("exportMode", exportMode);
            o.put("exportRate", exportRate);
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static Project fromJson(JSONObject o) {
        Project p = new Project();
        p.id = o.optString("id", "");
        if (p.id.isEmpty()) return null;
        p.name = o.optString("name", "Chromatic");
        p.created = o.optLong("created", 0);
        p.modified = o.optLong("modified", 0);
        p.sampleRate = o.optInt("sampleRate", 44100);
        p.frames = o.optInt("frames", 0);
        p.rootNote = o.optInt("rootNote", 60);
        p.fineCents = o.optInt("fineCents", 0);
        p.trimStart = o.optInt("trimStart", 0);
        p.trimEnd = o.optInt("trimEnd", 0);
        p.loop = o.optBoolean("loop", false);
        p.loopStart = o.optInt("loopStart", 0);
        p.loopEnd = o.optInt("loopEnd", 0);
        p.attackMs = o.optInt("attackMs", 5);
        p.releaseMs = o.optInt("releaseMs", 150);
        p.volume = o.optInt("volume", 100);
        p.normalize = o.optBoolean("normalize", true);
        p.rangeLo = o.optInt("rangeLo", 36);
        p.rangeHi = o.optInt("rangeHi", 96);
        p.exportMode = o.optInt("exportMode", 0);
        p.exportRate = o.optInt("exportRate", 0);
        return p;
    }

    /** Garante que trim e loop sejam coerentes. */
    public void sanitize() {
        if (frames <= 0) { trimStart = 0; trimEnd = 0; loopStart = 0; loopEnd = 0; return; }
        trimStart = Math.max(0, Math.min(trimStart, frames - 1));
        if (trimEnd <= trimStart || trimEnd > frames) trimEnd = frames;
        loopStart = Math.max(trimStart, Math.min(loopStart, trimEnd - 1));
        if (loopEnd <= loopStart || loopEnd > trimEnd) loopEnd = trimEnd;
        rootNote = Math.max(0, Math.min(127, rootNote));
        if (rangeLo > rangeHi) { int t = rangeLo; rangeLo = rangeHi; rangeHi = t; }
        rangeLo = Math.max(0, rangeLo);
        rangeHi = Math.min(127, rangeHi);
    }
}
