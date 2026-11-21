package com.chromix.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Configurações do app. */
public final class Prefs {
    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("chromix", Context.MODE_PRIVATE);
    }

    public static int getInt(Context c, String k, int def) { return sp(c).getInt(k, def); }
    public static void putInt(Context c, String k, int v) { sp(c).edit().putInt(k, v).apply(); }
    public static boolean getBool(Context c, String k, boolean def) { return sp(c).getBoolean(k, def); }
    public static void putBool(Context c, String k, boolean v) { sp(c).edit().putBoolean(k, v).apply(); }
    public static String getStr(Context c, String k, String def) { return sp(c).getString(k, def); }
    public static void putStr(Context c, String k, String v) { sp(c).edit().putString(k, v).apply(); }

    /** 0 escuro, 1 claro, 2 sistema */
    public static int theme(Context c) { return getInt(c, "theme", 0); }
    /** 0 = C D E, 1 = Dó Ré Mi */
    public static int noteStyle(Context c) { return getInt(c, "noteStyle", 0); }
    public static int octaves(Context c) { return getInt(c, "octaves", 2); }
    /** 0 baixa, 1 normal, 2 alta */
    public static int latency(Context c) { return getInt(c, "latency", 1); }
    public static int masterVol(Context c) { return getInt(c, "masterVol", 80); }
    public static int defRate(Context c) { return getInt(c, "defRate", 0); }
    public static int defMode(Context c) { return getInt(c, "defMode", 0); }
    public static int defLo(Context c) { return getInt(c, "defLo", 36); }
    public static int defHi(Context c) { return getInt(c, "defHi", 96); }
    public static boolean haptic(Context c) { return getBool(c, "haptic", true); }
    public static boolean keepOn(Context c) { return getBool(c, "keepOn", true); }
    public static String author(Context c) { return getStr(c, "author", ""); }
}
