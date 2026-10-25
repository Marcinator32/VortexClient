package com.vortex.client.gui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Client-Design (seit 4.69): eigene Farben bzw. Farbverlaeufe statt Lila-Blau -- fuer
 * ALLE Vortex-Menues (Mods, Bots, Cosmetics, Startmenue ...), das Logo und auf Wunsch den HUD.
 *
 * WIE: Vortex zeichnet alles mit seiner Lila-Blau-Palette (VortexStyle, Glatt und viele
 * feste Farben in den einzelnen Menues). Statt jede Stelle umzubauen, rechnet
 * FarbthemaMixin beim Zeichnen JEDE Farbe aus diesem Bereich um: Farbton und Saettigung
 * kommen aus dem gewaehlten Design, die Helligkeit bleibt -- dunkle Flaechen bleiben dunkel,
 * helle Akzente hell, Abstufungen und Verlaeufe bleiben erhalten. Wo im Original Lila war,
 * steht der Anfang des Verlaufs, wo Blau war, das Ende. Graue, weisse und alle anderen
 * Farben (Rot fuer Fehler, Gruen fuer "an" ...) bleiben, wie sie sind. Texturen (Items,
 * Skins, Vorschaubilder) werden nicht veraendert, nur Flaechen, Text und Toenungen.
 *
 * Mit dem Standard-Design "Vortex" passiert gar nichts (kein Umrechnen).
 */
public final class Farbthema {
    private Farbthema() {}

    /** Ein Design: 2 oder 3 Farben (RGB), optional fliessend. */
    public record Vorlage(String id, String name, int[] farben, boolean fliessend) {}

    public static final List<Vorlage> VORLAGEN = List.of(
            new Vorlage("blackhole", "Black Hole", new int[] { 0xF59E0B, 0xFFD27A }, false),
            new Vorlage("vortex", "Vortex", new int[] { 0x8B5CF6, 0x3B82F6 }, false),
            new Vorlage("ocean", "Ocean", new int[] { 0x06B6D4, 0x3B82F6 }, false),
            new Vorlage("sunset", "Sunset", new int[] { 0xF97316, 0xEC4899 }, false),
            new Vorlage("crimson", "Crimson", new int[] { 0xEF4444, 0xF97316 }, false),
            new Vorlage("emerald", "Emerald", new int[] { 0x10B981, 0x84CC16 }, false),
            new Vorlage("gold", "Gold", new int[] { 0xF59E0B, 0xFDE047 }, false),
            new Vorlage("sakura", "Sakura", new int[] { 0xF472B6, 0xC084FC }, false),
            new Vorlage("ice", "Ice", new int[] { 0x7DD3FC, 0xE0F2FE }, false),
            new Vorlage("toxic", "Toxic", new int[] { 0x84CC16, 0x22D3EE }, false),
            new Vorlage("aurora", "Aurora", new int[] { 0x22D3EE, 0x8B5CF6, 0xEC4899 }, true),
            new Vorlage("mono", "Mono", new int[] { 0xE5E7EB, 0x9CA3AF }, false),
            new Vorlage("rainbow", "Rainbow", new int[] { 0xEF4444, 0x22C55E, 0x3B82F6 }, true));

    /** Seit 4.74 Standard: Schwarz-Gold wie die Scheibe um ein Schwarzes Loch. */
    private static String vorlage = "blackhole";
    /** Kantige Ecken (seit 4.74, Standard an): Flaechen, Knoepfe und Rahmen fast ohne Rundung. */
    private static int[] eigene = { 0x8B5CF6, 0xEC4899 };
    private static boolean fliessend = false;
    private static boolean mitHud = true;
    private static boolean getoent = true;
    private static boolean geladen;
    private static volatile int version;
    private static volatile boolean aktiv;

    /** Gerade nicht umrechnen (Farbwaehler: dort sollen die echten Farben zu sehen sein). */
    private static int roh;
    /** Zeichnet gerade der HUD? (FarbthemaHudMixin) */
    private static int hud;

    public static void rohAn() { roh++; }
    public static void rohAus() { if (roh > 0) roh--; }
    public static void hudAn() { hud = 1; }
    public static void hudAus() { hud = 0; }

    /** Zaehlt bei jeder Aenderung hoch (z. B. damit das Logo neu eingefaerbt wird). */
    public static int version() { laden(); return version; }

    public static String vorlage() { laden(); return vorlage; }
    public static int[] eigeneFarben() { laden(); return eigene.clone(); }
    public static boolean fliessend() { laden(); return fliessend; }
    public static boolean mitHud() { laden(); return mitHud; }
    public static boolean getoent() { laden(); return getoent; }

    /** Die Farben des aktiven Designs (RGB). */
    public static int[] farben() {
        laden();
        if ("custom".equals(vorlage)) return eigene.clone();
        Vorlage v = finde(vorlage);
        return v == null ? VORLAGEN.get(0).farben().clone() : v.farben().clone();
    }

    public static Vorlage finde(String id) {
        for (Vorlage v : VORLAGEN) if (v.id().equals(id)) return v;
        return null;
    }

    public static void waehle(String id) {
        laden();
        vorlage = id;
        Vorlage v = finde(id);
        if (v != null) fliessend = v.fliessend();
        geaendert();
    }

    public static void setzeEigene(int[] farben) {
        laden();
        if (farben == null || farben.length < 2) return;
        eigene = new int[Math.min(3, farben.length)];
        for (int i = 0; i < eigene.length; i++) eigene[i] = farben[i] & 0xFFFFFF;
        vorlage = "custom";
        geaendert();
    }

    public static void setzeFliessend(boolean an) { laden(); fliessend = an; geaendert(); }
    public static void setzeHud(boolean an) { laden(); mitHud = an; geaendert(); }
    public static void setzeGetoent(boolean an) { laden(); getoent = an; geaendert(); }

    public static void zuruecksetzen() {
        laden();
        vorlage = "blackhole";
        fliessend = false;
        mitHud = true;
        getoent = true;
        geaendert();
    }

    private static void geaendert() {
        version++;
        java.util.Arrays.fill(CACHE_K, 0);
        aktiv = !("vortex".equals(vorlage) && !fliessend && getoent);
        speichern();
    }

    private static Path datei() {
        return com.vortex.client.core.ConfigManager.dataDir().resolve("design.txt");
    }

    private static synchronized void laden() {
        if (geladen) return;
        geladen = true;
        try {
            Path d = datei();
            if (Files.exists(d)) {
                for (String z : Files.readAllLines(d, StandardCharsets.UTF_8)) {
                    int i = z.indexOf('=');
                    if (i <= 0) continue;
                    String k = z.substring(0, i).trim(), v = z.substring(i + 1).trim();
                    switch (k) {
                        case "design" -> vorlage = "custom".equals(v) || finde(v) != null ? v : "blackhole";
                        case "colors" -> {
                            List<Integer> l = new ArrayList<>();
                            for (String f : v.split(",")) {
                                try { l.add(Integer.parseInt(f.trim().replace("#", ""), 16)); } catch (NumberFormatException ignored) { }
                            }
                            if (l.size() >= 2) eigene = l.stream().limit(3).mapToInt(Integer::intValue).toArray();
                        }
                        case "flowing" -> fliessend = Boolean.parseBoolean(v);
                        case "hud" -> mitHud = Boolean.parseBoolean(v);
                        case "tinted" -> getoent = Boolean.parseBoolean(v);
                        default -> { }
                    }
                }
            }
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("Farbthema.laden", t);
        }
        aktiv = !("vortex".equals(vorlage) && !fliessend && getoent);
    }

    private static void speichern() {
        try {
            StringBuilder b = new StringBuilder();
            b.append("design=").append(vorlage).append('\n');
            b.append("colors=");
            for (int i = 0; i < eigene.length; i++) b.append(i == 0 ? "" : ",").append(String.format("#%06X", eigene[i] & 0xFFFFFF));
            b.append('\n').append("flowing=").append(fliessend).append('\n');
            b.append("hud=").append(mitHud).append('\n');
            b.append("tinted=").append(getoent).append('\n');
            Files.createDirectories(datei().getParent());
            Files.writeString(datei(), b.toString(), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("Farbthema.speichern", t);
        }
    }

    private static final int[] CACHE_K = new int[4096], CACHE_V = new int[4096];

    /** Ist gerade ein eigenes Design an (und darf hier umgerechnet werden)? */
    public static boolean wirkt() {
        if (!geladen) laden();
        return aktiv && roh == 0 && (hud == 0 || mitHud);
    }

    /**
     * Eine Farbe (ARGB) ins aktive Design umrechnen. Nur Farben aus Vortex' Lila-Blau-
     * Bereich (Farbton 200-292 Grad, nicht grau) aendern sich.
     */
    public static int um(int argb) {
        if (!wirkt()) return argb;
        return umrechnen(argb, fliessend ? zeit() : 0f, true);
    }

    /** Wie um(), aber ohne Ausnahmen (HUD/Farbwaehler) und zu einem festen Zeitpunkt -- fuers Logo. */
    public static int umStill(int argb) {
        laden();
        if (!aktiv) return argb;
        return umrechnen(argb, 0f, false);
    }

    private static float zeit() { return (System.nanoTime() % 1_000_000_000_000L) / 1e9f; }

    private static int umrechnen(int argb, float zeit, boolean merken) {
        int a = argb >>> 24;
        if (a == 0) return argb;
        boolean bewegt = fliessend || "rainbow".equals(vorlage);
        int fach = 0;
        if (merken && !bewegt) {
            fach = (argb * 0x9E3779B1) >>> 20;
            if (CACHE_K[fach] == argb && argb != 0) return CACHE_V[fach];
        }
        float r = ((argb >> 16) & 255) / 255f, g = ((argb >> 8) & 255) / 255f, b = (argb & 255) / 255f;
        float mx = Math.max(r, Math.max(g, b)), mn = Math.min(r, Math.min(g, b));
        float l = (mx + mn) / 2f, d = mx - mn;
        int ergebnis = argb;
        if (d > 0.0001f) {
            float s = d / (1f - Math.abs(2f * l - 1f));
            float h;
            if (mx == r) h = 60f * (((g - b) / d) % 6f);
            else if (mx == g) h = 60f * ((b - r) / d + 2f);
            else h = 60f * ((r - g) / d + 4f);
            if (h < 0) h += 360f;
            if (s >= 0.16f && l >= 0.025f && l <= 0.97f && h >= 200f && h <= 292f) {
                float t = Math.max(0f, Math.min(1f, (258f - h) / 41f));
                float[] ziel = ziel(t, zeit);
                float lRef = 0.66f + (0.60f - 0.66f) * t;
                float sNeu = Math.max(0f, Math.min(1f, s * ziel[1] / 0.9f));
                if (!getoent && l < 0.3f && s < 0.6f) sNeu = 0f;
                float w = Math.max(0f, Math.min(1f, (l - 0.12f) / 0.35f));
                float lNeu = Math.max(0f, Math.min(1f, l + (ziel[2] - lRef) * w));
                ergebnis = (a << 24) | hslZuRgb(ziel[0], sNeu, lNeu);
            }
        }
        if (merken && !bewegt) { CACHE_K[fach] = argb; CACHE_V[fach] = ergebnis; }
        return ergebnis;
    }

    /** Farbton/Saettigung/Helligkeit des Designs an Stelle t (0 = Anfang, 1 = Ende). */
    private static float[] ziel(float t, float zeit) {
        if ("rainbow".equals(vorlage)) return new float[] { ((t * 120f + zeit * 45f) % 360f + 360f) % 360f, 0.85f, 0.6f };
        if (fliessend) t = 0.5f - 0.5f * (float) Math.cos(Math.PI * 2 * (t * 0.5f + zeit * 0.12f));
        return rgbZuHsl(verlauf(farben(), t));
    }

    /** Farbe (RGB) im Verlauf an Stelle t. */
    public static int verlauf(int[] f, float t) {
        t = Math.max(0f, Math.min(1f, t));
        if (f.length == 1) return f[0];
        float p = t * (f.length - 1);
        int i = Math.min(f.length - 2, (int) p);
        float k = p - i;
        int a = f[i], b = f[i + 1];
        int r = (int) (((a >> 16) & 255) + ((((b >> 16) & 255) - ((a >> 16) & 255)) * k));
        int g = (int) (((a >> 8) & 255) + ((((b >> 8) & 255) - ((a >> 8) & 255)) * k));
        int bl = (int) ((a & 255) + (((b & 255) - (a & 255)) * k));
        return (r << 16) | (g << 8) | bl;
    }

    static float[] rgbZuHsl(int rgb) {
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        float mx = Math.max(r, Math.max(g, b)), mn = Math.min(r, Math.min(g, b));
        float l = (mx + mn) / 2f, d = mx - mn;
        if (d < 0.0001f) return new float[] { 0f, 0f, l };
        float s = d / (1f - Math.abs(2f * l - 1f));
        float h;
        if (mx == r) h = 60f * (((g - b) / d) % 6f);
        else if (mx == g) h = 60f * ((b - r) / d + 2f);
        else h = 60f * ((r - g) / d + 4f);
        if (h < 0) h += 360f;
        return new float[] { h, Math.min(1f, s), l };
    }

    static int hslZuRgb(float h, float s, float l) {
        float c = (1f - Math.abs(2f * l - 1f)) * s;
        float x = c * (1f - Math.abs((h / 60f) % 2f - 1f));
        float m = l - c / 2f;
        float r, g, b;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        int ri = Math.round((r + m) * 255f), gi = Math.round((g + m) * 255f), bi = Math.round((b + m) * 255f);
        return (Math.max(0, Math.min(255, ri)) << 16) | (Math.max(0, Math.min(255, gi)) << 8) | Math.max(0, Math.min(255, bi));
    }
}
