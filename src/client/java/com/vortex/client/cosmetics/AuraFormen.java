package com.vortex.client.cosmetics;

import com.vortex.client.cosmetics.AuraTexturen.Art;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometrie der grossen Auren (GrossAura): je Bild eine Liste weicher Leuchtflaechen.
 * Ohne Minecraft-Klassen -- so laesst sich jede Aura auch ausserhalb des Spiels rendern.
 * Koordinaten in Pixeln, y nach oben, Fuesse bei y = -24.
 */
public final class AuraFormen {
    private AuraFormen() {}

    /** Eine Flaeche: Textur, vier Ecken (x, y, z), Farbe (ARGB, additiv). u/v: 0,0 -> 1,0 -> 1,1 -> 0,1. */
    public record Flaeche(Art art, float[] p, int farbe) {}

    static final float BODEN = -23.6f;
    private static final ThreadLocal<List<Flaeche>> LISTE = ThreadLocal.withInitial(ArrayList::new);

    public static List<Flaeche> bauen(String art, float t, int seed, int dichte) {
        List<Flaeche> l = LISTE.get();
        l.clear();
        float menge = dichte == 1 ? 0.6f : dichte == 3 ? 1.5f : 1f;
        switch (art) {
            case "big_arcane" -> arkan(t, menge);
            case "big_solar" -> sonne(t, menge);
            case "big_vortex" -> wirbel(t, menge);
            case "big_frost" -> frost(t, menge, seed);
            case "big_inferno" -> inferno(t, menge, seed);
            case "big_aurora" -> polarlicht(t);
            default -> { }
        }
        return l;
    }


    /** Farbe mit Staerke (0..1) -- additiv, also Helligkeit = Farbe x Staerke. */
    private static int farbe(int rgb, float k) {
        k = Math.max(0f, Math.min(1f, k));
        int r = Math.round(((rgb >> 16) & 255) * k), g = Math.round(((rgb >> 8) & 255) * k), b = Math.round((rgb & 255) * k);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static void quad(Art a, float[] p, int col) {
        if ((col & 0xFFFFFF) == 0) return;
        LISTE.get().add(new Flaeche(a, p, col));
    }

    /** Waagrechte Flaeche (Boden), Mitte auf der Achse, Radius r, um y gedreht. */
    private static void boden(Art a, float y, float r, float grad, int col) {
        double w = Math.toRadians(grad);
        float cs = (float) Math.cos(w) * r, sn = (float) Math.sin(w) * r;
        quad(a, new float[] {
                -cs + sn, y, -sn - cs,
                cs + sn, y, sn - cs,
                cs - sn, y, sn + cs,
                -cs - sn, y, -sn + cs }, col);
    }

    /** Senkrechte Flaeche: Mitte unten (x, y, z), Breite b, Hoehe h, Blickrichtung grad um y. */
    private static void wand(Art a, float x, float y, float z, float b, float h, float grad, int col) {
        double w = Math.toRadians(grad);
        float dx = (float) Math.cos(w) * b / 2, dz = (float) Math.sin(w) * b / 2;
        quad(a, new float[] { x - dx, y + h, z - dz, x + dx, y + h, z + dz, x + dx, y, z + dz, x - dx, y, z - dz }, col);
    }

    /** Leuchtpunkt: zwei gekreuzte senkrechte und eine waagrechte Flaeche -- wirkt von allen Seiten rund. */
    private static void punkt(Art a, float x, float y, float z, float g, int col) {
        wand(a, x, y - g / 2, z, g, g, 0, col);
        wand(a, x, y - g / 2, z, g, g, 90, col);
        quad(a, new float[] { x - g / 2, y, z - g / 2, x + g / 2, y, z - g / 2, x + g / 2, y, z + g / 2, x - g / 2, y, z + g / 2 }, col);
    }

    /** Strahl von a nach b (Breite br), senkrecht zur Verbindung und so gut wie moeglich sichtbar. */
    private static void strahl(float ax, float ay, float az, float bx, float by, float bz, float br, int col) {
        strahl(Art.STRAHL, ax, ay, az, bx, by, bz, br, col);
    }

    private static void strahl(Art art, float ax, float ay, float az, float bx, float by, float bz, float br, int col) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float qx = -dz, qz = dx, ql = (float) Math.sqrt(qx * qx + qz * qz);
        if (ql < 1e-4f) { qx = br / 2; qz = 0; } else { qx = qx / ql * br / 2; qz = qz / ql * br / 2; }
        quad(art, new float[] { ax - qx, ay, az - qz, ax + qx, ay, az + qz, bx + qx, by, bz + qz, bx - qx, by, bz - qz }, col);
    }

    private static float zufall(int n) {
        int h = n * 374761393 + 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFFFF) / 16777216f;
    }

    private static float rampe(float ph) {
        return Math.min(1f, Math.min(ph / 0.15f, (1f - ph) / 0.25f));
    }

    private static int mische(int a, int b, float k) {
        k = Math.max(0, Math.min(1, k));
        int r = (int) (((a >> 16) & 255) + ((((b >> 16) & 255) - ((a >> 16) & 255)) * k));
        int g = (int) (((a >> 8) & 255) + ((((b >> 8) & 255) - ((a >> 8) & 255)) * k));
        int bl = (int) ((a & 255) + (((b & 255) - (a & 255)) * k));
        return r << 16 | g << 8 | bl;
    }


    /** Zauberkreis: zwei gegenlaeufige Runenkreise am Boden, ein aufsteigender Ring, Funken. */
    private static void arkan(float t, float menge) {
        float s = t / 20f;
        float puls = 0.85f + 0.15f * (float) Math.sin(s * 2.1f);
        boden(Art.GLUT, BODEN + 0.05f, 52, 0, farbe(0x6D28D9, 0.35f * puls));
        boden(Art.RUNEN, BODEN + 0.1f, 44, s * 14f, farbe(0x8B5CF6, 0.95f * puls));
        boden(Art.RUNEN, BODEN + 0.15f, 30, -s * 22f, farbe(0x67E8F9, 0.6f));
        boden(Art.RING, BODEN + 0.2f, 47, 0, farbe(0xC4B5FD, 0.55f));
        for (int k = 0; k < 2; k++) {
            float ph = (s / 3.2f + k * 0.5f) % 1f;
            float y = BODEN + ph * 40f;
            float r = 34f - ph * 10f;
            boden(Art.RING, y, r, 0, farbe(0xA78BFA, 0.5f * rampe(ph)));
        }
        int n = Math.round(18 * menge);
        for (int i = 0; i < n; i++) {
            float h = zufall(i * 7 + 1), dauer = 2.4f + 1.8f * zufall(i * 3 + 5);
            float ph = ((s / dauer) + h) % 1f;
            float w = (float) (h * Math.PI * 2 + s * 0.9f + ph * 2.5f);
            float r = 40f - ph * 14f;
            float x = (float) Math.cos(w) * r, z = (float) Math.sin(w) * r;
            punkt(Art.FUNKE, x, BODEN + ph * 36f, z, 3.2f, farbe(i % 3 == 0 ? 0x67E8F9 : 0xC4B5FD, rampe(ph)));
        }
    }

    /** Sonnenkrone: Lichtstrahlen am Boden, ein grosser Strahlenkranz hinter dir, kreisende Funken. */
    private static void sonne(float t, float menge) {
        float s = t / 20f;
        boden(Art.GLUT, BODEN + 0.05f, 50, 0, farbe(0xF59E0B, 0.4f));
        int strahlen = 16;
        for (int i = 0; i < strahlen; i++) {
            double w = s * 0.25f + i * Math.PI * 2 / strahlen;
            float laenge = 26f + 14f * (0.5f + 0.5f * (float) Math.sin(s * 1.6f + i * 1.7f));
            float x0 = (float) Math.cos(w) * 12, z0 = (float) Math.sin(w) * 12;
            float x1 = (float) Math.cos(w) * (12 + laenge), z1 = (float) Math.sin(w) * (12 + laenge);
            strahl(x0, BODEN + 0.1f, z0, x1, BODEN + 0.1f, z1, 7f, farbe(i % 2 == 0 ? 0xFDE68A : 0xF59E0B, 0.75f));
        }
        float y = -2f;
        for (int k = 0; k < 2; k++) {
            float r = k == 0 ? 20f : 14f;
            float dreh = (k == 0 ? 1 : -1) * s * 18f;
            double w = Math.toRadians(dreh);
            float cs = (float) Math.cos(w) * r, sn = (float) Math.sin(w) * r;
            float z = 5.5f + k * 0.2f;
            quad(k == 0 ? Art.RUNEN : Art.RING, new float[] { -cs + sn, y - sn - cs, z, cs + sn, y + sn - cs, z, cs - sn, y + sn + cs, z, -cs - sn, y - sn + cs, z },
                    farbe(k == 0 ? 0xFBBF24 : 0xFFF1C9, k == 0 ? 0.7f : 0.55f));
        }
        int n = Math.round(10 * menge);
        for (int i = 0; i < n; i++) {
            double w = s * 1.1f + i * Math.PI * 2 / n;
            float r = 30f + 4f * (float) Math.sin(s * 2f + i);
            float yy = -10f + 6f * (float) Math.sin(s * 1.3f + i * 2.1f);
            punkt(Art.FUNKE, (float) Math.cos(w) * r, yy, (float) Math.sin(w) * r, 4f, farbe(0xFFF1C9, 0.85f));
            punkt(Art.GLUT, (float) Math.cos(w) * r, yy, (float) Math.sin(w) * r, 9f, farbe(0xF59E0B, 0.35f));
        }
    }

    /** Wirbel: drei Lichtbaender schrauben sich um dich nach oben, Teilchen werden hineingezogen. */
    private static void wirbel(float t, float menge) {
        float s = t / 20f;
        boden(Art.RING, BODEN + 0.1f, 40, 0, farbe(0xA855F7, 0.55f + 0.2f * (float) Math.sin(s * 3f)));
        boden(Art.GLUT, BODEN + 0.05f, 44, 0, farbe(0x581C87, 0.5f));
        int baender = 3, stuecke = 28;
        for (int b = 0; b < baender; b++) {
            float[] vor = null;
            for (int k = 0; k <= stuecke; k++) {
                float q = k / (float) stuecke;
                double w = s * 2.2f + b * Math.PI * 2 / baender + q * Math.PI * 3.2;
                float r = 36f - q * 24f;
                float y = BODEN + q * 42f;
                float[] p = { (float) Math.cos(w) * r, y, (float) Math.sin(w) * r };
                if (vor != null) {
                    float hell = (float) Math.sin(q * Math.PI);
                    int col = mische(0xE879F9, 0x67E8F9, q);
                    strahl(Art.BAND, vor[0], vor[1], vor[2], p[0], p[1], p[2], 13f, farbe(col, hell * 0.6f));
                    strahl(Art.BAND, vor[0], vor[1], vor[2], p[0], p[1], p[2], 4f, farbe(mische(col, 0xFFFFFF, 0.5f), hell * 0.9f));
                }
                vor = p;
            }
        }
        int n = Math.round(16 * menge);
        for (int i = 0; i < n; i++) {
            float h = zufall(i * 11 + 3);
            float ph = ((s / 1.8f) + h) % 1f;
            double w = h * Math.PI * 2 + s * 1.8f + ph * 4f;
            float r = 46f * (1f - ph) + 4f;
            punkt(Art.FUNKE, (float) Math.cos(w) * r, BODEN + 4f + ph * 22f, (float) Math.sin(w) * r, 3f, farbe(0xF0ABFC, rampe(ph)));
        }
    }

    /** Frostwelle: Ringe laufen am Boden nach aussen, ein Schneestern dreht sich, Schnee faellt. */
    private static void frost(float t, float menge, int seed) {
        float s = t / 20f;
        boden(Art.GLUT, BODEN + 0.05f, 46, 0, farbe(0x0EA5E9, 0.3f));
        boden(Art.SCHNEE, BODEN + 0.1f, 34, s * 6f, farbe(0xBAE6FD, 1f));
        boden(Art.RING, BODEN + 0.12f, 36, 0, farbe(0x7DD3FC, 0.6f));
        int splitter = 10;
        for (int i = 0; i < splitter; i++) {
            double w = i * Math.PI * 2 / splitter + s * 0.2f;
            float r = 28f + 2f * (float) Math.sin(i * 2.7f);
            float h = 9f + 6f * zufall(i * 41 + 3) + 1.5f * (float) Math.sin(s * 1.5f + i);
            float glanz = 0.55f + 0.45f * (float) Math.max(0, Math.sin(s * 2.2f + i * 1.9f));
            float x = (float) Math.cos(w) * r, z = (float) Math.sin(w) * r;
            float grad = (float) Math.toDegrees(w) + 90f;
            wand(Art.STRAHL, x, BODEN - 0.5f, z, 5f, h, grad, farbe(0xBAE6FD, 0.75f * glanz));
            wand(Art.STRAHL, x, BODEN - 0.5f, z, 5f, h, grad + 90f, farbe(0xE0F2FE, 0.5f * glanz));
        }
        for (int k = 0; k < 3; k++) {
            float ph = (s / 2.6f + k / 3f) % 1f;
            float r = 10f + ph * 44f;
            boden(Art.RING, BODEN + 0.15f + k * 0.02f, r, 0, farbe(0xE0F2FE, 0.7f * (1f - ph) * Math.min(1f, ph * 6f)));
        }
        int n = Math.round(24 * menge);
        for (int i = 0; i < n; i++) {
            float h = zufall(seed * 13 + i * 17), h2 = zufall(i * 29 + 7);
            float dauer = 3.5f + 2.5f * h2;
            float ph = ((s / dauer) + h) % 1f;
            double w = h2 * Math.PI * 2 + Math.sin(s * 0.7f + i) * 0.3f;
            float r = 12f + 32f * zufall(i * 5 + 1);
            float x = (float) Math.cos(w) * r + (float) Math.sin(s * 1.3f + i) * 2f;
            float z = (float) Math.sin(w) * r;
            punkt(Art.FUNKE, x, 18f - ph * 42f, z, 2.6f, farbe(0xF0F9FF, rampe(ph) * 0.9f));
        }
    }

    /** Flammenring: ein Kreis aus flackernden Flammen um dich, Glut am Boden, aufsteigende Funken. */
    private static void inferno(float t, float menge, int seed) {
        float s = t / 20f;
        boden(Art.GLUT, BODEN + 0.05f, 46, 0, farbe(0xEA580C, 0.5f));
        boden(Art.RING, BODEN + 0.1f, 36, 0, farbe(0xF97316, 0.7f));
        int flammen = 26;
        for (int i = 0; i < flammen; i++) {
            double w = i * Math.PI * 2 / flammen + s * 0.15f;
            float r = 34f;
            float x = (float) Math.cos(w) * r, z = (float) Math.sin(w) * r;
            float flacker = 0.6f + 0.4f * (float) Math.sin(s * 9f + i * 2.3f) * (float) Math.sin(s * 5.3f + i * 1.1f);
            float h = 10f + 9f * flacker + 4f * (float) Math.sin(s * 2f + i);
            float grad = (float) Math.toDegrees(w) + 90f;
            wand(Art.STRAHL, x, BODEN - 1f, z, 7f, h, grad, farbe(0xF97316, 0.8f));
            wand(Art.STRAHL, x, BODEN - 1f, z, 4f, h * 0.65f, grad, farbe(0xFDE047, 0.7f));
        }
        int n = Math.round(20 * menge);
        for (int i = 0; i < n; i++) {
            float h = zufall(seed * 7 + i * 19), dauer = 1.8f + 1.4f * zufall(i * 23 + 9);
            float ph = ((s / dauer) + h) % 1f;
            double w = h * Math.PI * 2 + Math.sin(s + i) * 0.2f;
            float r = 30f + 8f * zufall(i * 31);
            float x = (float) Math.cos(w) * r + (float) Math.sin(s * 3f + i) * 1.5f;
            float z = (float) Math.sin(w) * r;
            punkt(Art.FUNKE, x, BODEN + 4f + ph * 34f, z, 2.4f, farbe(ph < 0.4f ? 0xFDE047 : 0xF97316, rampe(ph)));
        }
    }

    /** Polarlicht: ein Vorhang aus Lichtbahnen rund um dich, wellig, die Farben wandern. */
    private static void polarlicht(float t) {
        float s = t / 20f;
        boden(Art.GLUT, BODEN + 0.05f, 50, 0, farbe(0x10B981, 0.25f));
        int bahnen = 44;
        float r = 40f;
        for (int i = 0; i < bahnen; i++) {
            double w = i * Math.PI * 2 / bahnen;
            float welle = (float) Math.sin(w * 3 + s * 0.9f) * 0.5f + (float) Math.sin(w * 5 - s * 1.3f) * 0.5f;
            float rr = r + 3f * (float) Math.sin(w * 4 + s * 0.7f);
            float x = (float) Math.cos(w) * rr, z = (float) Math.sin(w) * rr;
            float h = 26f + 12f * welle;
            float unten = BODEN + 8f + 4f * (float) Math.sin(w * 2 + s * 0.5f);
            float k = 0.5f + 0.5f * (float) Math.sin(w * 2 + s * 0.6f);
            int col = mische(mische(0x34D399, 0x22D3EE, k), 0xA78BFA, 0.5f + 0.5f * (float) Math.sin(w + s * 0.4f));
            float hell = 0.35f + 0.4f * (0.5f + 0.5f * welle);
            float grad = (float) Math.toDegrees(w) + 90f;
            wand(Art.STRAHL, x, unten, z, 12f, h, grad, farbe(col, hell));
        }
    }
}
