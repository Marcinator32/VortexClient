package com.vortex.client.styles;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Die eingebauten Farb-Stile: Minecrafts eigene Texturen, beim Anwenden
 * umgefaerbt. Kein Bild im Download -- alles wird aus den Original-Texturen
 * des Spiels berechnet, die Form und Schattierung bleiben dabei erhalten.
 *
 * Umfaerben: Helligkeit jedes Pixels bestimmen (auf das hellste Pixel der
 * Textur bezogen), dunkle Stellen werden zur dunklen Stufe der Farbe, die
 * Mitte zur Farbe selbst und helle Stellen gehen etwas Richtung Weiss.
 * "Vortex" ist ein Verlauf von Violett oben nach Blau unten.
 */
public record Farbe(String id, String name, int oben, int unten) {

    public static final List<Farbe> ALLE = List.of(
            new Farbe("vortex", "Vortex", 0x8B5CF6, 0x3B82F6),
            new Farbe("purple", "Purple", 0x9B5CFF, 0x9B5CFF),
            new Farbe("lavender", "Lavender", 0xB9A6FF, 0xB9A6FF),
            new Farbe("pink", "Pink", 0xFF7AC8, 0xFF7AC8),
            new Farbe("red", "Red", 0xE8333F, 0xE8333F),
            new Farbe("gold", "Gold", 0xFFC43D, 0xFFC43D),
            new Farbe("lime", "Lime", 0x7BEA4A, 0x7BEA4A),
            new Farbe("aqua", "Aqua", 0x35D6C8, 0x35D6C8),
            new Farbe("ice", "Ice", 0xA8E8FF, 0xA8E8FF),
            new Farbe("blue", "Blue", 0x3E7BFF, 0x3E7BFF),
            new Farbe("black", "Black", 0x2B2930, 0x2B2930),
            new Farbe("white", "White", 0xF4F4F8, 0xF4F4F8));

    public static Farbe von(String id) {
        for (Farbe f : ALLE) if (f.id.equals(id)) return f;
        return null;
    }

    /** Farbe an einer Hoehe (0 oben, 1 unten). */
    public int bei(float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (((oben >> 16) & 0xFF) + ((((unten >> 16) & 0xFF) - ((oben >> 16) & 0xFF)) * t));
        int g = (int) (((oben >> 8) & 0xFF) + ((((unten >> 8) & 0xFF) - ((oben >> 8) & 0xFF)) * t));
        int b = (int) ((oben & 0xFF) + (((unten & 0xFF) - (oben & 0xFF)) * t));
        return (r << 16) | (g << 8) | b;
    }

    /** Eine Textur umfaerben (liefert ein neues Bild). */
    public BufferedImage faerben(BufferedImage src, Kategorie.Modus modus) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        if (modus == Kategorie.Modus.FLAECHE) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int c = bei(y / (float) Math.max(1, h - 1));
                    float f = 0.92f + 0.08f * (1f - (x + y) / (float) Math.max(1, w + h - 2));
                    out.setRGB(x, y, 0xFF000000 | skaliert(c, f));
                }
            }
            return out;
        }
        float max = 0.05f;
        java.util.List<Float> helle = new java.util.ArrayList<>();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = src.getRGB(x, y);
                if (dran(p, modus)) { max = Math.max(max, hell(p)); helle.add(hell(p)); }
            }
        }
        boolean symbol = modus == Kategorie.Modus.SYMBOL || modus == Kategorie.Modus.HELL;
        float mitte = 0.05f;
        if (symbol && !helle.isEmpty()) {
            java.util.Collections.sort(helle);
            mitte = Math.max(0.05f, helle.get(helle.size() / 2));
        }
        int bildH = (h > w && h % w == 0) ? w : h;
        for (int y = 0; y < h; y++) {
            int c = bei((y % bildH) / (float) Math.max(1, bildH - 1));
            for (int x = 0; x < w; x++) {
                int p = src.getRGB(x, y);
                if (!dran(p, modus)) { out.setRGB(x, y, p); continue; }
                float l = modus == Kategorie.Modus.GRAU ? Math.min(1f, hell(p) * 1.3f)
                        : symbol ? Math.min(1f, hell(p) / mitte * 0.66f) : Math.min(1f, hell(p) / max);
                out.setRGB(x, y, (p & 0xFF000000) | ton(c, l));
            }
        }
        return out;
    }

    /** Wird dieses Pixel umgefaerbt? */
    private static boolean dran(int p, Kategorie.Modus modus) {
        if (((p >>> 24) & 0xFF) == 0) return false;
        float r = ((p >> 16) & 0xFF) / 255f, g = ((p >> 8) & 0xFF) / 255f, b = (p & 0xFF) / 255f;
        float mx = Math.max(r, Math.max(g, b)), mn = Math.min(r, Math.min(g, b));
        float s = mx <= 0f ? 0f : (mx - mn) / mx;
        return switch (modus) {
            case GRAU -> s < 0.2f;
            case KLINGE -> !braun(r, g, b, mx, mn, s);
            case HELL -> (0.299f * r + 0.587f * g + 0.114f * b) > 0.3f;
            default -> true;
        };
    }

    /** Holzgriff: Farbton etwa orange-braun, kraeftig, nicht hell. */
    private static boolean braun(float r, float g, float b, float mx, float mn, float s) {
        if (s < 0.3f || mx > 0.8f || mx - mn <= 0f) return false;
        float hue;
        if (mx == r) hue = 60f * (((g - b) / (mx - mn)) % 6f);
        else if (mx == g) hue = 60f * (((b - r) / (mx - mn)) + 2f);
        else hue = 60f * (((r - g) / (mx - mn)) + 4f);
        if (hue < 0) hue += 360f;
        return hue >= 12f && hue <= 46f;
    }

    private static float hell(int p) {
        return (0.299f * ((p >> 16) & 0xFF) + 0.587f * ((p >> 8) & 0xFF) + 0.114f * (p & 0xFF)) / 255f;
    }

    /** Helligkeit l (0..1) in der Farbe c ausdruecken. */
    private static int ton(int c, float l) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        float knick = 0.62f;
        if (l < knick) {
            float f = 0.16f + 0.84f * (l / knick);
            return (clamp(r * f) << 16) | (clamp(g * f) << 8) | clamp(b * f);
        }
        float t = (l - knick) / (1f - knick) * 0.55f;
        return (clamp(r + (255 - r) * t) << 16) | (clamp(g + (255 - g) * t) << 8) | clamp(b + (255 - b) * t);
    }

    private static int skaliert(int c, float f) {
        return (clamp(((c >> 16) & 0xFF) * f) << 16) | (clamp(((c >> 8) & 0xFF) * f) << 8) | clamp((c & 0xFF) * f);
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, Math.round(v)));
    }
}
