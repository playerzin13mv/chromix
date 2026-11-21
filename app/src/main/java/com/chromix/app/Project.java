package com.chromix.app;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Um projeto de Chromatic. Tem 5 "slots" de áudio (A, E, I, O, U).
 * Sem o modo vogais só o slot 0 é usado. Os áudios ficam em .wav separados.
 */
public class Project {
    public static final String[] VOWELS = {"A", "E", "I", "O", "U"};

    public String id = "";
    public String name = "Chromatic";
    public long created, modified;

    public final Slot[] slots = new Slot[5];

    public boolean vowelMode = false;
    public int attackMs = 5;
    public int releaseMs = 150;
    public int volume = 100;
    public boolean normalize = true;
    public int rangeLo = 36;
    public int rangeHi = 96;
    public int exportMode = 0;       // 0 = nota única, 1 = uma amostra por nota
    public int exportRate = 0;       // 0 = original

    public Project() {
        for (int i = 0; i < slots.length; i++) slots[i] = new Slot();
    }

    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("name", name);
            o.put("created", created);
            o.put("modified", modified);
            o.put("vowelMode", vowelMode);
            o.put("attackMs", attackMs);
            o.put("releaseMs", releaseMs);
            o.put("volume", volume);
            o.put("normalize", normalize);
            o.put("rangeLo", rangeLo);
            o.put("rangeHi", rangeHi);
            o.put("exportMode", exportMode);
            o.put("exportRate", exportRate);
            JSONArray a = new JSONArray();
            for (Slot s : slots) a.put(s.toJson());
            o.put("slots", a);
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
        p.vowelMode = o.optBoolean("vowelMode", false);
        p.attackMs = o.optInt("attackMs", 5);
        p.releaseMs = o.optInt("releaseMs", 150);
        p.volume = o.optInt("volume", 100);
        p.normalize = o.optBoolean("normalize", true);
        p.rangeLo = o.optInt("rangeLo", 36);
        p.rangeHi = o.optInt("rangeHi", 96);
        p.exportMode = o.optInt("exportMode", 0);
        p.exportRate = o.optInt("exportRate", 0);
        JSONArray a = o.optJSONArray("slots");
        if (a != null) {
            for (int i = 0; i < p.slots.length && i < a.length(); i++) {
                JSONObject so = a.optJSONObject(i);
                if (so != null) p.slots[i] = Slot.fromJson(so);
            }
        } else {
            // projeto da versão 1.0: os campos do áudio ficavam no topo
            p.slots[0] = Slot.fromJson(o);
        }
        return p;
    }

    public void sanitize() {
        for (Slot s : slots) s.sanitize();
        if (rangeLo > rangeHi) { int t = rangeLo; rangeLo = rangeHi; rangeHi = t; }
        rangeLo = Math.max(0, rangeLo);
        rangeHi = Math.min(127, rangeHi);
    }

    /**
     * Slots que realmente tocam, em ordem (A, E, I, O, U). Sem o modo vogais é só o slot 0.
     * Se audios != null, também exige que o áudio esteja carregado.
     */
    public int[] activeSlots(float[][] audios) {
        int[] tmp = new int[5];
        int n = 0;
        int max = vowelMode ? 5 : 1;
        for (int i = 0; i < max; i++) {
            if (slots[i].frames > 0 && (audios == null || audios[i] != null)) tmp[n++] = i;
        }
        return java.util.Arrays.copyOf(tmp, n);
    }

    /** Ex.: "A E I O" (vogais que já têm áudio). */
    public String vowelSummary() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            if (slots[i].frames > 0) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(VOWELS[i]);
            }
        }
        return sb.toString();
    }
}
