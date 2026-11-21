package com.chromix.app;

import android.content.Context;
import android.content.res.Configuration;

/** Paleta de cores (tema escuro / claro / sistema). */
public final class Pal {
    private Pal() {}

    public static boolean dark = true;
    public static int bg, card, card2, text, sub, accent, accent2, line, danger, onAccent,
            keyWhite, keyBlack, keyEdge, warn;

    /** Uma cor do arco-íris para cada nota da escala cromática. */
    public static final int[] RAINBOW = {
            0xFFFF4D6D, 0xFFFF7A3D, 0xFFFFB02E, 0xFFFFD93D, 0xFF9BE15D, 0xFF2DD4A3,
            0xFF22C7E8, 0xFF3B9CFF, 0xFF7C83FF, 0xFFA678FF, 0xFFD46BFF, 0xFFFF5FB5
    };

    public static void apply(Context c) {
        int mode = Prefs.theme(c);
        if (mode == 2) {
            int n = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            dark = n == Configuration.UI_MODE_NIGHT_YES;
        } else {
            dark = mode == 0;
        }
        if (dark) {
            bg = 0xFF120E26; card = 0xFF1C1738; card2 = 0xFF2A2352;
            text = 0xFFF2EEFF; sub = 0xFF9C93C9;
            accent = 0xFF8B7BFF; accent2 = 0xFF2DD4A3; line = 0xFF332B5E;
            danger = 0xFFFF5470; warn = 0xFFFFB02E; onAccent = 0xFFFFFFFF;
            keyWhite = 0xFFF4F1FF; keyBlack = 0xFF1A1433; keyEdge = 0xFF8E86B8;
        } else {
            bg = 0xFFF5F2FF; card = 0xFFFFFFFF; card2 = 0xFFE9E4FF;
            text = 0xFF1B1538; sub = 0xFF6B6490;
            accent = 0xFF6C5CE7; accent2 = 0xFF0FA67E; line = 0xFFD8D1F5;
            danger = 0xFFD92D4B; warn = 0xFFC77A00; onAccent = 0xFFFFFFFF;
            keyWhite = 0xFFFFFFFF; keyBlack = 0xFF241C48; keyEdge = 0xFF9A93C4;
        }
    }

    public static int blend(int a, int b, float t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg2 = (b >> 8) & 255, bb = b & 255;
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg2 - ag) * t);
        int bl = Math.round(ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static final String[] N_EN = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    private static final String[] N_PT = {"Dó", "Dó#", "Ré", "Ré#", "Mi", "Fá", "Fá#", "Sol", "Sol#", "Lá", "Lá#", "Si"};

    /** Nome da nota MIDI (60 = C4). */
    public static String noteName(int midi, int style) {
        int o = midi / 12 - 1;
        return (style == 1 ? N_PT : N_EN)[((midi % 12) + 12) % 12] + o;
    }
}
