package com.vortex.client.cosmetics;

import java.util.ArrayList;
import java.util.List;

/**
 * Knochen fuer Fledermaus- und Drachenfluegel (seit 4.82): Oberarm entlang der Vorderkante,
 * vier Finger vom Handgelenk zu den Spitzen der Flughaut, runde Gelenke und eine Daumenkralle.
 * Als echte, rund schattierte Roehren ueber der geglaetteten Flughaut (FlachBilder).
 *
 * Die Knochen liegen im Bildraster der Fluegel-Designs (40 x 32, wie Demon und Dragon Wings);
 * Farben kommen aus dem Design: Knochen = Vorderkante, Kralle = ihre Spitze (leuchtet, wenn
 * sie im Design leuchtet). Ohne Minecraft-Klassen.
 *
 * Koordinaten wie die Fluegelglieder: Bildpixel, Ursprung am Gelenk des Glieds.
 */
public final class FledermausKunst {
    private FledermausKunst() {}

    /** Knochen als Punktfolge im Bild (x, y nach unten) mit Radius je Punkt. */
    private record Knochen(float[][] punkte, float[] radius) {}

    private static final float RASTER_W = 40f, RASTER_H = 32f;

    private static List<Knochen> gerippe() {
        List<Knochen> l = new ArrayList<>();
        l.add(new Knochen(new float[][] { { -0.4f, 6.4f }, { 6f, 5.6f }, { 12f, 5.0f }, { 17f, 4.8f }, { 24f, 4.5f }, { 32f, 4.2f }, { 39.6f, 4.1f } },
                new float[] { 1.35f, 1.3f, 1.2f, 1.25f, 1.0f, 0.82f, 0.55f }));
        float[] wurzel = { 17f, 5.2f };
        l.add(finger(wurzel, new float[] { 16.2f, 15f }, new float[] { 14.5f, 30.2f }));
        l.add(finger(wurzel, new float[] { 21.5f, 13f }, new float[] { 26.4f, 31.2f }));
        l.add(finger(wurzel, new float[] { 28.5f, 11.5f }, new float[] { 36.3f, 19.8f }));
        l.add(finger(new float[] { 15.6f, 5.6f }, new float[] { 8.5f, 16f }, new float[] { 1.6f, 28.4f }));
        return l;
    }

    private static Knochen finger(float[] a, float[] m, float[] e) {
        float[][] p = new float[7][];
        float[] r = new float[7];
        for (int i = 0; i < 7; i++) {
            float t = i / 6f;
            float u = 1 - t;
            p[i] = new float[] { u * u * a[0] + 2 * u * t * m[0] + t * t * e[0], u * u * a[1] + 2 * u * t * m[1] + t * t * e[1] };
            r[i] = 0.86f - 0.5f * t;
        }
        return new Knochen(p, r);
    }

    /**
     * Saubere Flughaut fuer FlachBilder: statt der gemalten Pixel-Knochen und Sprenkel eine
     * glatte Haut in der Hautfarbe des Designs -- oben etwas heller, unten dunkler, entlang
     * der Knochen eine leichte Mulde. Rand, Leucht-Pixel und alles ueber dem Arm (Krallen,
     * Stacheln) bleiben wie im Design.
     */
    public static int[][] haut(int[][] farben, boolean[][] glow) {
        int h = farben.length, w = 0;
        for (int[] r : farben) w = Math.max(w, r.length);
        float sx = w / RASTER_W, sy = h / RASTER_H;
        int hautFarbe = mittel(farben, glow);
        int rand = Netz.mische(hautFarbe, 0xFF000000, 0.55f);
        List<Knochen> g = gerippe();
        int[][] o = new int[h][];
        for (int y = 0; y < h; y++) {
            o[y] = new int[farben[y].length];
            for (int x = 0; x < farben[y].length; x++) {
                int c = farben[y][x];
                if ((c >>> 24) == 0) continue;
                boolean leucht = glow != null && y < glow.length && x < glow[y].length && glow[y][x];
                if (leucht || (y + 0.5f) / sy < 3.4f) { o[y][x] = c; continue; }
                if (randPixel(farben, x, y)) { o[y][x] = rand; continue; }
                float px = (x + 0.5f) / sx, py = (y + 0.5f) / sy;
                float d = 9f;
                for (Knochen k : g) for (int i = 0; i + 1 < k.punkte().length; i++) d = Math.min(d, strecke(px, py, k.punkte()[i], k.punkte()[i + 1]));
                float mulde = (float) Math.exp(-d * d / 3f) * 0.22f;
                float hoehe = py / RASTER_H;
                int f = Netz.mische(hautFarbe, 0xFFFFFFFF, Math.max(0f, 0.1f - hoehe * 0.2f));
                f = Netz.mische(f, 0xFF000000, hoehe * 0.22f + mulde);
                o[y][x] = f;
            }
        }
        return o;
    }

    /** Mittlere Farbe der Flughaut: die haeufigste mittelhelle Farbe im unteren Teil. */
    private static int mittel(int[][] f, boolean[][] glow) {
        java.util.Map<Integer, Integer> n = new java.util.HashMap<>();
        for (int y = f.length / 3; y < f.length; y++) {
            for (int x = 0; x < f[y].length; x++) {
                int c = f[y][x];
                if ((c >>> 24) == 0 || randPixel(f, x, y)) continue;
                if (glow != null && y < glow.length && x < glow[y].length && glow[y][x]) continue;
                n.merge(c, 1, Integer::sum);
            }
        }
        int best = 0xFF3A3A3A, bn = -1;
        long r = 0, g = 0, b = 0, s = 0;
        for (var e : n.entrySet()) {
            int c = e.getKey();
            int lum = ((c >> 16) & 255) + ((c >> 8) & 255) + (c & 255);
            if (lum < 70) continue;
            r += (long) ((c >> 16) & 255) * e.getValue(); g += (long) ((c >> 8) & 255) * e.getValue(); b += (long) (c & 255) * e.getValue(); s += e.getValue();
            if (e.getValue() > bn) { bn = e.getValue(); best = c; }
        }
        if (s == 0) return best;
        int m = 0xFF000000 | (int) (r / s) << 16 | (int) (g / s) << 8 | (int) (b / s);
        return Netz.mische(m, best, 0.5f);
    }

    /** Pixel am Rand der Form (ein Nachbar leer). */
    private static boolean randPixel(int[][] f, int x, int y) {
        int[][] d = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        for (int[] q : d) {
            int xx = x + q[0], yy = y + q[1];
            if (yy < 0 || yy >= f.length || xx < 0 || xx >= f[yy].length || (f[yy][xx] >>> 24) == 0) return true;
        }
        return false;
    }

    private static float strecke(float px, float py, float[] a, float[] b) {
        float vx = b[0] - a[0], vy = b[1] - a[1], wx = px - a[0], wy = py - a[1];
        float k = Math.max(0, Math.min(1, (wx * vx + wy * vy) / (vx * vx + vy * vy)));
        float dx = wx - vx * k, dy = wy - vy * k;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Netze je Glied (0..2) fuer eine Seite (1 rechts, -1 links). grenzen/gelenk wie beim
     * Fluegel; Bildgroesse w x h, das Gerippe wird darauf gestreckt.
     */
    public static Netz[] bauen(int[][] farben, boolean[][] glow, int[] grenzen, float gelenk, int seite) {
        int h = farben.length, w = 0;
        for (int[] r : farben) w = Math.max(w, r.length);
        float sx = w / RASTER_W, sy = h / RASTER_H;
        int knochen = farbe(farben, 20, 4, 0xFF3A2A2A);
        int haut = farbe(farben, 30, 12, knochen);
        int hell = Netz.mische(Netz.mische(knochen, haut, 0.45f), 0xFFFFFFFF, 0.12f);
        int dunkel = Netz.mische(knochen, 0xFF000000, 0.25f);
        int kralle = farbe(farben, 19, 0, hell);
        boolean kralleLeuchtet = glow != null && glow.length > 0 && glow[0].length > 19 && glow[0][19];
        Netz[] netze = { new Netz(), new Netz(), new Netz() };
        for (Knochen k : gerippe()) {
            int n = k.punkte().length;
            for (int i = 0; i + 1 < n; i++) {
                float[] a = k.punkte()[i], b = k.punkte()[i + 1];
                float ax = a[0] * sx, ay = a[1] * sy, bx = b[0] * sx, by = b[1] * sy;
                stueck(netze, grenzen, gelenk, seite, ax, ay, bx, by, k.radius()[i], k.radius()[i + 1], hell, dunkel, i, n);
            }
            for (int i = 1; i < n - 1; i += 3) {
                float[] a = k.punkte()[i];
                int g = glied(grenzen, a[0] * sx);
                float lx = seite * (a[0] * sx - grenzen[g]), ly = gelenk - a[1] * sy;
                netze[g].kugel(lx, ly, 0, k.radius()[i] * 1.28f, 10, hell, dunkel);
            }
        }
        float kx = 18.8f * sx, ky0 = 3.6f * sy, ky1 = -0.2f * sy;
        int g = glied(grenzen, kx);
        Netz kn = netze[g];
        kn.leuchten(kralleLeuchtet ? 1f : 0f);
        kn.kegel(new float[] { seite * (kx - grenzen[g]), gelenk - ky0, 0 }, 0.55f, new float[] { seite * (kx + 0.6f * sx - grenzen[g]), gelenk - ky1, 0 }, 10, kralle, Netz.mische(kralle, 0xFFFFFFFF, 0.35f));
        kn.leuchten(0f);
        return netze;
    }

    /** Ein Knochenstueck; an den Gliedgrenzen geteilt, damit jedes Glied seine Teile mitdreht. */
    private static void stueck(Netz[] netze, int[] grenzen, float gelenk, int seite, float ax, float ay, float bx, float by,
                               float ra, float rb, int hell, int dunkel, int nr, int n) {
        for (int gr = 1; gr <= 2; gr++) {
            float x = grenzen[gr];
            if ((ax - x) * (bx - x) < 0) {
                float t = (x - ax) / (bx - ax);
                float my = ay + (by - ay) * t, mr = ra + (rb - ra) * t;
                stueck(netze, grenzen, gelenk, seite, ax, ay, x - 0.001f * Math.signum(bx - ax), my, ra, mr, hell, dunkel, nr, n);
                stueck(netze, grenzen, gelenk, seite, x + 0.001f * Math.signum(bx - ax), my, bx, by, mr, rb, hell, dunkel, nr, n);
                return;
            }
        }
        int g = glied(grenzen, (ax + bx) / 2f);
        float g0 = grenzen[g];
        float[][] pfad = { { seite * (ax - g0), gelenk - ay, 0 }, { seite * (bx - g0), gelenk - by, 0 } };
        int fa = Netz.mische(hell, dunkel, 0.15f), fb = Netz.mische(hell, dunkel, 0.15f + 0.4f * (nr + 1f) / n);
        netze[g].roehre(pfad, new float[] { ra, rb }, 10, fa, fb);
        netze[g].kugel(pfad[1][0], pfad[1][1], 0, rb, 8, hell, dunkel);
    }

    private static int glied(int[] grenzen, float x) {
        return x < grenzen[1] ? 0 : x < grenzen[2] ? 1 : 2;
    }

    private static int farbe(int[][] f, int x, int y, int sonst) {
        if (y < 0 || y >= f.length || x < 0 || x >= f[y].length) return sonst;
        int c = f[y][x];
        return (c >>> 24) == 0 ? sonst : c;
    }
}
