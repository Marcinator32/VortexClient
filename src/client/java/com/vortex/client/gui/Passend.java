package com.vortex.client.gui;

import net.minecraft.client.gui.Font;

/**
 * Text, der in jede Fensterbreite passt (seit 4.31).
 *
 * Viele Vortex-Fenster schreiben Hinweise und Namen mit fester Breite -- bei
 * kleinen Fenstern (z. B. 640x480 oder 1280x1024 mit grosser GUI) liefen sie
 * ueber den Rand oder unter Knoepfe. Diese Helfer kuerzen sauber.
 */
public final class Passend {
    private Passend() {}

    /** Kuerzt auf hoechstens max Pixel, mit "..." am Ende. */
    public static String kuerzen(Font f, String s, int max) {
        if (s == null || s.isEmpty()) return "";
        if (max <= 6) return "";
        if (f.width(s) <= max) return s;
        String c = s;
        while (c.length() > 1 && f.width(c + "...") > max) c = c.substring(0, c.length() - 1);
        return c.length() <= 1 ? "" : c.stripTrailing() + "...";
    }

    /**
     * Hinweiszeile aus mehreren Teilen ("S share   W world ..." oder
     * "a  ·  b  ·  c"): von hinten so viele Teile weglassen, bis sie passt --
     * lieber drei ganze Hinweise als sechs abgeschnittene.
     */
    public static String hinweis(Font f, String s, int max) {
        if (s == null || s.isEmpty() || f.width(s) <= max) return s == null ? "" : s;
        String trenner = s.contains("·") ? "·" : "   ";
        String[] teile = s.split(java.util.regex.Pattern.quote(trenner));
        for (int n = teile.length - 1; n >= 1; n--) {
            String t = String.join(trenner, java.util.Arrays.copyOf(teile, n)).stripTrailing();
            if (f.width(t) <= max) return t;
        }
        return kuerzen(f, s, max);
    }
}
