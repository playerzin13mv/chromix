package com.chromix.app;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Guarda os projetos no armazenamento interno do app. */
public final class ProjectStore {
    private ProjectStore() {}

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "projects");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    private static File jsonFile(Context c, String id) { return new File(dir(c), id + ".json"); }
    private static File wavFile(Context c, String id) { return new File(dir(c), id + ".wav"); }

    private static byte[] readAll(File f) throws IOException {
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] b = new byte[16384];
            int n;
            while ((n = in.read(b)) > 0) o.write(b, 0, n);
            return o.toByteArray();
        }
    }

    public static List<Project> list(Context c) {
        List<Project> out = new ArrayList<>();
        File[] fs = dir(c).listFiles();
        if (fs != null) {
            for (File f : fs) {
                if (!f.getName().endsWith(".json")) continue;
                try {
                    Project p = Project.fromJson(new JSONObject(new String(readAll(f), "UTF-8")));
                    if (p != null) out.add(p);
                } catch (Exception ignored) { }
            }
        }
        Collections.sort(out, new Comparator<Project>() {
            @Override public int compare(Project a, Project b) { return Long.compare(b.modified, a.modified); }
        });
        return out;
    }

    public static Project load(Context c, String id) {
        try {
            return Project.fromJson(new JSONObject(new String(readAll(jsonFile(c, id)), "UTF-8")));
        } catch (Exception e) {
            return null;
        }
    }

    public static Project create(Context c, String name) {
        Project p = new Project();
        p.id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        p.name = name;
        p.created = System.currentTimeMillis();
        p.modified = p.created;
        p.rangeLo = Prefs.defLo(c);
        p.rangeHi = Prefs.defHi(c);
        p.exportMode = Prefs.defMode(c);
        p.exportRate = Prefs.defRate(c);
        try { save(c, p); } catch (Exception ignored) { }
        return p;
    }

    public static void save(Context c, Project p) throws IOException {
        p.sanitize();
        try (FileOutputStream o = new FileOutputStream(jsonFile(c, p.id))) {
            o.write(p.toJson().toString().getBytes("UTF-8"));
        }
    }

    public static float[] loadAudio(Context c, Project p) {
        File f = wavFile(c, p.id);
        if (!f.exists()) return null;
        try {
            AudioIO.Pcm pcm = AudioIO.parseWav(readAll(f));
            return pcm.data;
        } catch (Exception e) {
            return null;
        }
    }

    public static void saveAudio(Context c, Project p, float[] data) throws IOException {
        AudioIO.writeWav(wavFile(c, p.id), data, p.sampleRate);
    }

    public static void delete(Context c, Project p) {
        //noinspection ResultOfMethodCallIgnored
        jsonFile(c, p.id).delete();
        //noinspection ResultOfMethodCallIgnored
        wavFile(c, p.id).delete();
    }

    public static void deleteAll(Context c) {
        for (Project p : list(c)) delete(c, p);
    }

    public static Project duplicate(Context c, Project p) {
        Project n = Project.fromJson(p.toJson());
        if (n == null) return null;
        n.id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        n.name = p.name + " (cópia)";
        n.created = System.currentTimeMillis();
        n.modified = n.created;
        try {
            File src = wavFile(c, p.id);
            if (src.exists()) {
                try (FileOutputStream o = new FileOutputStream(wavFile(c, n.id))) {
                    o.write(readAll(src));
                }
            }
            save(c, n);
        } catch (Exception e) {
            return null;
        }
        return n;
    }
}
