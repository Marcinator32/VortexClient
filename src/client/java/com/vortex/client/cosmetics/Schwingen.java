package com.vortex.client.cosmetics;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fluegel als echtes 3D-Modell (seit 4.88), statt aus dem Pixelbild geschnitten.
 *
 * Federfluegel: Knochen an der Vorderkante, darunter Schwungfedern (Hand, Unterarm, Oberarm),
 * darueber zwei Reihen Deckfedern -- jede Feder eine eigene, leicht gebogene Platte mit
 * Schaft, Verlauf von der Wurzel zur Spitze und je nach Stil leuchtendem Rand oder Glut an
 * der Spitze. Hautfluegel (Daemon, Drache): Arm, gespreizte Finger mit Krallen und eine
 * Flughaut mit Bogenkante. Schmetterling: Vorder- und Hinterfluegel mit Adern, Rand, Punkten
 * und Auge.
 *
 * Koordinaten je Glied: Ursprung am Gelenk, x nach aussen (rechter Fluegel; der linke wird
 * gespiegelt), y nach oben, z nach hinten (vom Ruecken weg). Einheit = Spielerpixel.
 * Die Glieder zeichnet und bewegt ZubehoerEbene.fluegel wie bisher.
 */
public final class Schwingen {
    private Schwingen() {}

    public enum Art { FEDER, HAUT, FALTER }

    static final int RAND = 1, GLUT = 2, KANTE = 4, METALL = 8, ADERN = 16, STACHELN = 32;

    /**
     * Farben eines Fluegels. Feder: a = Deckfedern, b = Mitte, c = Spitzen, kante = Knochen,
     * schaft = Federschaft, akzent = Rand/Glut. Haut: a = Knochen, b = Haut innen, c = Haut
     * aussen, kante = Krallen, akzent = Adern/Rand. Falter: a = Grundfarbe, b = hell,
     * c = Rand, kante = Adern, schaft = Punkte, akzent = Auge.
     */
    record Stil(Art art, int a, int b, int c, int kante, int schaft, int akzent, int flags, float spitz) {
        boolean hat(int f) { return (flags & f) != 0; }
    }

    private static Stil feder(int a, int b, int c, int kante, int schaft, int akzent, int flags, float spitz) {
        return new Stil(Art.FEDER, 0xFF000000 | a, 0xFF000000 | b, 0xFF000000 | c, 0xFF000000 | kante, 0xFF000000 | schaft, 0xFF000000 | akzent, flags, spitz);
    }

    private static Stil haut(int knochen, int innen, int aussen, int kralle, int akzent, int flags) {
        return new Stil(Art.HAUT, 0xFF000000 | knochen, 0xFF000000 | innen, 0xFF000000 | aussen, 0xFF000000 | kralle, 0xFF000000 | kralle, 0xFF000000 | akzent, flags, 1f);
    }

    private static final Map<String, Stil> STILE = Map.ofEntries(
            Map.entry("angel_wings", feder(0xFFFFFF, 0xF2F6FD, 0xC9D7EF, 0xFFF1CC, 0xFFFFFF, 0xFFE08A, 0, 0.15f)),
            Map.entry("phoenix_wings", feder(0xFFD34A, 0xFF8A1F, 0xD93A16, 0xFFE36B, 0xFFF1A8, 0xFFE36B, GLUT | KANTE, 0.85f)),
            Map.entry("vortex_wings", feder(0x8B4CF2, 0x6D28D9, 0x3B1580, 0x67E8F9, 0xB79CFF, 0x67E8F9, RAND | KANTE, 0.55f)),
            Map.entry("frost_wings", feder(0xFFFFFF, 0xD6F3FF, 0x8FD3F4, 0xEFFBFF, 0x9FD8F2, 0xE8FBFF, GLUT, 1f)),
            Map.entry("shadow_wings", feder(0x433D55, 0x2E2A3C, 0x1A1824, 0x4A4460, 0x5A5070, 0xB26BFF, RAND, 0.8f)),
            Map.entry("golden_wings", feder(0xFFF0B0, 0xFFDC5A, 0xEFB437, 0xFFFBEA, 0xFFF3C4, 0xFFFBEA, METALL, 0.25f)),
            Map.entry("neon_wings", feder(0x2A1248, 0x1E0D36, 0x120826, 0x22D3EE, 0x4A2270, 0xE040F0, RAND | KANTE, 0.4f)),
            Map.entry("demon_wings", haut(0x2A0C27, 0x5E1E5A, 0x3A1238, 0x14050F, 0xFF3B3B, ADERN | STACHELN)),
            Map.entry("dragon_wings", haut(0x1A4A3B, 0x34A07A, 0x1F6B52, 0xE6D9A8, 0xE6D9A8, STACHELN)),
            Map.entry("butterfly_wings", new Stil(Art.FALTER, 0xFF2F6FE8, 0xFF62D2FF, 0xFF0F172A, 0xFF16203A, 0xFFE8F6FF, 0xFFFB923C, 0, 0f)));

    /** Stil fuer ein Design; null = altes Pixelbild-Modell. */
    public static Stil stil(Zubehoer.Design d) {
        return d == null ? null : STILE.get(d.id());
    }

    /** Laenge der Glieder (Oberarm, Unterarm, Hand). */
    static float[] laengen(Stil s) {
        return switch (s.art()) {
            case FEDER -> new float[] { 6.5f, 7f, 7.5f };
            case HAUT -> new float[] { 6f, 7.5f, 1f };
            case FALTER -> new float[] { 0f, 0f, 0f };
        };
    }

    private static final Map<List<Object>, Netz[]> NETZE = new ConcurrentHashMap<>();

    /** Die drei Glieder einer Seite (1 = rechts, -1 = links). */
    static Netz[] netze(Stil s, int seite) {
        return NETZE.computeIfAbsent(List.of(s, seite), k -> {
            Netz[] n = { new Netz(), new Netz(), new Netz() };
            for (Netz x : n) {
                x.push();
                if (seite < 0) x.skaliere(-1, 1, 1);
            }
            switch (s.art()) {
                case FEDER -> federfluegel(n, s);
                case HAUT -> hautfluegel(n, s);
                case FALTER -> falter(n[0], s);
            }
            for (Netz x : n) x.pop();
            return n;
        });
    }

    private static final float GRAD = (float) (Math.PI / 180);

    private static int dunkler(int c, float k) { return Netz.mische(c, 0xFF000000, k); }

    private static int heller(int c, float k) { return Netz.mische(c, 0xFFFFFFFF, k); }

    /** Viereck mit flacher Normale, die zur Seite 'hin' zeigt. */
    private static void flaeche(Netz n, float[] a, float[] b, float[] c, float[] d, float[] hin, int fa, int fb, int fc, int fd) {
        float[] k = Netz.kreuz(Netz.sub(c, a), Netz.sub(d, b));
        if (Netz.dot(k, k) < 1e-10f) k = hin.clone();
        float[] nr = Netz.norm(k);
        if (Netz.dot(nr, hin) < 0) nr = Netz.neg(nr);
        n.viereck(a, b, c, d, nr, nr, nr, nr, fa, fb, fc, fd);
    }

    private static void flaeche(Netz n, float[] a, float[] b, float[] c, float[] d, float[] hin, int f) {
        flaeche(n, a, b, c, d, hin, f, f, f, f);
    }

    private static final float[] VORN = { 0, 0, -1 }, HINTEN = { 0, 0, 1 };

    private static final float[] STUFEN = { 0f, 0.12f, 0.3f, 0.5f, 0.68f, 0.84f, 1f };
    private static final float[] RUND = { 0.42f, 0.8f, 1f, 1f, 0.93f, 0.72f, 0.2f };
    private static final float[] SPITZ = { 0.42f, 0.8f, 1f, 0.92f, 0.68f, 0.36f, 0.04f };

    /**
     * Eine Feder: Ansatz (x, y, z), Richtung w (Grad, 0 = nach aussen, -90 = nach unten),
     * Laenge, Breite, Kruemmung (Grad bis zur Spitze), Farbe an Wurzel und Spitze.
     * glut = Anteil der Spitze, der leuchtet (0 = nichts).
     */
    private static void feder(Netz n, Stil s, float x, float y, float z, float w, float laenge, float breite,
                              float krumm, int wurzel, int spitze, boolean rand, float glut, float spitz) {
        int m = STUFEN.length;
        float[][] lo = new float[m][], mo = new float[m][], ro = new float[m][];
        float[][] lu = new float[m][], mu = new float[m][], ru = new float[m][];
        float[][] li = new float[m][], ri = new float[m][];
        float[] f = new float[m];
        int[] farbe = new int[m];
        float px = x, py = y;
        for (int i = 0; i < m; i++) {
            float t = STUFEN[i];
            f[i] = t;
            if (i > 0) {
                float wm = (w + krumm * (STUFEN[i - 1] + t) * 0.5f * (STUFEN[i - 1] + t) * 0.5f) * GRAD;
                float d = laenge * (t - STUFEN[i - 1]);
                px += (float) Math.cos(wm) * d;
                py += (float) Math.sin(wm) * d;
            }
            float wi = (w + krumm * t * t) * GRAD;
            float qx = -(float) Math.sin(wi), qy = (float) Math.cos(wi);
            float form = RUND[i] + (SPITZ[i] - RUND[i]) * spitz;
            float hw = breite * 0.5f * form;
            float aussen = hw * 0.62f, innen = hw;
            float zc = z - 0.45f * t * t;
            float dick = 0.42f * (1 - t) + 0.16f * t;
            float rippe = 0.14f * (1 - t);
            lo[i] = new float[] { px - qx * innen, py - qy * innen, zc + dick * 0.5f };
            mo[i] = new float[] { px, py, zc + dick * 0.5f + rippe };
            ro[i] = new float[] { px + qx * aussen, py + qy * aussen, zc + dick * 0.5f };
            lu[i] = new float[] { lo[i][0], lo[i][1], zc - dick * 0.5f };
            mu[i] = new float[] { px, py, zc - dick * 0.5f - rippe * 0.5f };
            ru[i] = new float[] { ro[i][0], ro[i][1], zc - dick * 0.5f };
            float rb = Math.min(0.2f, hw * 0.25f);
            li[i] = new float[] { lo[i][0] + qx * rb, lo[i][1] + qy * rb, lo[i][2] + 0.03f };
            ri[i] = new float[] { ro[i][0] - qx * rb, ro[i][1] - qy * rb, ro[i][2] + 0.03f };
            farbe[i] = Netz.mische(wurzel, spitze, (float) Math.pow(t, 1.15));
        }
        if (s.hat(METALL)) n.metall(true);
        for (int i = 0; i < m - 1; i++) {
            boolean gluehen = glut > 0 && f[i] >= 1 - glut - 0.001f;
            int c0 = farbe[i], c1 = farbe[i + 1];
            if (gluehen) {
                float k0 = (f[i] - (1 - glut)) / glut, k1 = (f[i + 1] - (1 - glut)) / glut;
                c0 = Netz.mische(c0, s.akzent(), k0 * 0.8f);
                c1 = Netz.mische(c1, s.akzent(), k1 * 0.8f);
                n.leuchten(0.55f + 0.45f * k1);
            }
            int s0 = Netz.mische(c0, s.schaft(), 0.45f), s1 = Netz.mische(c1, s.schaft(), 0.45f);
            int e0 = dunkler(c0, 0.14f), e1 = dunkler(c1, 0.14f);
            float[] mitte = { mo[i][0], mo[i][1], 0 };
            float[] lr = Netz.sub(lo[i], mitte);
            float[] hinL = { lr[0], lr[1], 0 }, hinR = Netz.neg(hinL);
            flaeche(n, lo[i], mo[i], mo[i + 1], lo[i + 1], HINTEN, e0, s0, s1, e1);
            flaeche(n, mo[i], ro[i], ro[i + 1], mo[i + 1], HINTEN, s0, e0, e1, s1);
            int u0 = dunkler(c0, 0.22f), u1 = dunkler(c1, 0.22f);
            int us0 = dunkler(s0, 0.18f), us1 = dunkler(s1, 0.18f);
            flaeche(n, lu[i], lu[i + 1], mu[i + 1], mu[i], VORN, u0, u1, us1, us0);
            flaeche(n, mu[i], mu[i + 1], ru[i + 1], ru[i], VORN, us0, us1, u1, u0);
            int w0 = dunkler(c0, 0.32f), w1 = dunkler(c1, 0.32f);
            flaeche(n, lo[i], lo[i + 1], lu[i + 1], lu[i], hinL, w0, w1, w1, w0);
            flaeche(n, ro[i], ru[i], ru[i + 1], ro[i + 1], hinR, w0, w0, w1, w1);
            if (gluehen) n.leuchten(0f);
        }
        float[] dir = Netz.norm(Netz.sub(mo[m - 1], mo[m - 2]));
        int cs = farbe[m - 1];
        if (glut > 0) { cs = Netz.mische(cs, s.akzent(), 0.8f); n.leuchten(1f); }
        flaeche(n, lo[m - 1], ro[m - 1], ru[m - 1], lu[m - 1], dir, dunkler(cs, 0.3f));
        n.leuchten(0f);
        float[] zurueck = Netz.norm(Netz.sub(mo[0], mo[1]));
        flaeche(n, lo[0], lu[0], ru[0], ro[0], zurueck, dunkler(farbe[0], 0.35f));
        if (rand) {
            n.leuchten(1f);
            int a = s.akzent();
            for (int i = 0; i < m - 1; i++) {
                flaeche(n, lo[i], li[i], li[i + 1], lo[i + 1], HINTEN, a);
                flaeche(n, ri[i], ro[i], ro[i + 1], ri[i + 1], HINTEN, a);
                float[] lv = { li[i][0], li[i][1], lu[i][2] - 0.03f }, lv1 = { li[i + 1][0], li[i + 1][1], lu[i + 1][2] - 0.03f };
                float[] rv = { ri[i][0], ri[i][1], ru[i][2] - 0.03f }, rv1 = { ri[i + 1][0], ri[i + 1][1], ru[i + 1][2] - 0.03f };
                float[] luv = { lu[i][0], lu[i][1], lu[i][2] - 0.03f }, luv1 = { lu[i + 1][0], lu[i + 1][1], lu[i + 1][2] - 0.03f };
                float[] ruv = { ru[i][0], ru[i][1], ru[i][2] - 0.03f }, ruv1 = { ru[i + 1][0], ru[i + 1][1], ru[i + 1][2] - 0.03f };
                flaeche(n, luv, luv1, lv1, lv, VORN, a);
                flaeche(n, rv, rv1, ruv1, ruv, VORN, a);
            }
            n.leuchten(0f);
        }
        if (s.hat(METALL)) n.metall(false);
    }

    /** Knochen an der Vorderkante eines Glieds, von x0 bis x1. */
    private static void knochen(Netz n, Stil s, float x0, float x1, float r0, float r1, float y, float z, boolean spitze) {
        if (s.hat(KANTE)) n.leuchten(s.art() == Art.FEDER ? 0.6f : 0f);
        int fa = s.art() == Art.FEDER ? s.kante() : s.a(), fe = s.art() == Art.FEDER ? s.kante() : dunkler(s.a(), 0.15f);
        float[][] pfad = { { x0, y, z }, { (x0 + x1) / 2, y + 0.25f, z }, { x1, y, z } };
        n.roehre(pfad, new float[] { r0, (r0 + r1) / 2 + 0.06f, spitze ? 0 : r1 }, 7, fa, fe);
        n.kugel(x0, y, z, r0 * 1.08f, 7, heller(fa, 0.1f), dunkler(fa, 0.1f));
        n.leuchten(0f);
    }

    private record Feder(float x, float w, float laenge, float breite) {}

    /** Federfluegel: Ober-, Unterarm und Hand. */
    private static void federfluegel(Netz[] n, Stil s) {
        float[] len = laengen(s);
        boolean rand = s.hat(RAND);
        float glut = s.hat(GLUT) ? 0.42f : 0f;
        float sp = s.spitz();

        Feder[][] schwung = {
                { new Feder(0.4f, -99, 9.5f, 3.6f), new Feder(2.3f, -96, 10.5f, 3.6f), new Feder(4.2f, -94, 11.2f, 3.6f), new Feder(6.0f, -92, 11.8f, 3.5f) },
                { new Feder(0.4f, -90, 12.2f, 3.5f), new Feder(2.1f, -88, 12.6f, 3.5f), new Feder(3.8f, -85, 13.0f, 3.4f), new Feder(5.4f, -82, 13.5f, 3.4f), new Feder(6.9f, -78, 14.0f, 3.3f) },
                { new Feder(0.5f, -73, 14.6f, 3.3f), new Feder(1.9f, -65, 15.6f, 3.2f), new Feder(3.3f, -56, 16.5f, 3.1f), new Feder(4.6f, -47, 17.0f, 3.0f),
                  new Feder(5.8f, -38, 16.6f, 2.9f), new Feder(6.8f, -29, 15.2f, 2.7f), new Feder(7.5f, -20, 12.6f, 2.4f) } };

        for (int k = 0; k < 3; k++) {
            Netz x = n[k];
            Feder[] fs = schwung[k];
            for (int i = fs.length - 1; i >= 0; i--) {
                Feder f = fs[i];
                float krumm = k == 2 ? -6f - i : 4f;
                feder(x, s, f.x(), -0.2f, -0.35f - 0.09f * i, f.w(), f.laenge(), f.breite(), krumm, s.b(), s.c(), rand, glut, sp);
            }
            float l = len[k];
            int deck = Math.max(3, Math.round(l / 1.55f));
            for (int i = deck - 1; i >= 0; i--) {
                float px = 0.3f + (l - 0.3f) * i / (deck - 1f);
                float w = winkel(fs, px);
                float lang = laenge(fs, px) * 0.45f;
                feder(x, s, px, 0.15f, 0.42f - 0.05f * i, w + 3f, lang, 3.0f, 2f, s.a(), s.b(), false, glut * 0.4f, Math.min(sp, 0.5f));
            }
            int klein = Math.max(3, Math.round(l / 1.3f));
            for (int i = klein - 1; i >= 0; i--) {
                float px = 0.2f + (l - 0.2f) * i / (klein - 1f);
                float w = Math.max(-92f, winkel(fs, px) * 0.6f - 40f);
                feder(x, s, px, 0.55f, 0.95f - 0.04f * i, w, k == 2 ? 3.4f : 4.2f, 2.6f, 0f, heller(s.a(), 0.05f), s.a(), false, 0f, 0f);
            }
            knochen(x, s, 0f, l + (k == 2 ? 1.5f : 0.3f), k == 0 ? 0.9f : k == 1 ? 0.78f : 0.66f, k == 0 ? 0.78f : k == 1 ? 0.66f : 0.3f, 0.45f, 0.75f, k == 2);
        }
        Netz x = n[0];
        for (int i = 2; i >= 0; i--) {
            feder(x, s, 0.2f + 0.9f * i, 0.5f, 1.25f - 0.05f * i, -104f + 3 * i, 7.0f + 0.6f * i, 3.0f, 3f, s.a(), s.b(), false, 0f, Math.min(sp, 0.5f));
        }
    }

    private static float winkel(Feder[] fs, float x) {
        if (x <= fs[0].x()) return fs[0].w();
        for (int i = 1; i < fs.length; i++) {
            if (x <= fs[i].x()) {
                float k = (x - fs[i - 1].x()) / (fs[i].x() - fs[i - 1].x());
                return fs[i - 1].w() + (fs[i].w() - fs[i - 1].w()) * k;
            }
        }
        return fs[fs.length - 1].w();
    }

    private static float laenge(Feder[] fs, float x) {
        if (x <= fs[0].x()) return fs[0].laenge();
        for (int i = 1; i < fs.length; i++) {
            if (x <= fs[i].x()) {
                float k = (x - fs[i - 1].x()) / (fs[i].x() - fs[i - 1].x());
                return fs[i - 1].laenge() + (fs[i].laenge() - fs[i - 1].laenge()) * k;
            }
        }
        return fs[fs.length - 1].laenge();
    }

    /**
     * Flughaut zwischen einer Linie oben (o0 -> o1) und einer Linie unten (u0 -> u1), deren
     * Mitte um 'bogen' nach oben gezogen ist. Farbe von innen (oben) nach aussen (unten).
     */
    private static void haut(Netz n, Stil s, float[] o0, float[] o1, float[] u0, float[] u1, float bogen, boolean rand) {
        int nu = 6, nv = 5;
        float[][][] p = new float[nu + 1][nv + 1][];
        for (int i = 0; i <= nu; i++) {
            float u = i / (float) nu;
            float[] o = lerp(o0, o1, u), un = lerp(u0, u1, u);
            float[] mo = lerp(o0, o1, 0.5f);
            float[] zu = Netz.sub(mo, lerp(u0, u1, 0.5f));
            float b = bogen * (float) Math.sin(Math.PI * u);
            float[] zl = Netz.norm(zu);
            un = new float[] { un[0] + zl[0] * b, un[1] + zl[1] * b, un[2] };
            for (int j = 0; j <= nv; j++) {
                float v = j / (float) nv;
                float[] q = lerp(o, un, v);
                q[2] += 0.35f * (float) Math.sin(Math.PI * v) * (float) Math.sin(Math.PI * u);
                p[i][j] = q;
            }
        }
        float d = 0.1f;
        for (int i = 0; i < nu; i++) {
            for (int j = 0; j < nv; j++) {
                int c0 = Netz.mische(s.b(), s.c(), j / (float) nv), c1 = Netz.mische(s.b(), s.c(), (j + 1) / (float) nv);
                float[] a = p[i][j], b = p[i + 1][j], c = p[i + 1][j + 1], e = p[i][j + 1];
                flaeche(n, hoch(a, d), hoch(b, d), hoch(c, d), hoch(e, d), HINTEN, c0, c0, c1, c1);
                flaeche(n, hoch(a, -d), hoch(e, -d), hoch(c, -d), hoch(b, -d), VORN, dunkler(c0, 0.2f), dunkler(c1, 0.2f), dunkler(c1, 0.2f), dunkler(c0, 0.2f));
            }
        }
        if (rand || s.hat(ADERN)) {
            int a = s.akzent();
            n.leuchten(1f);
            if (rand) {
                for (int i = 0; i < nu; i++) {
                    float[] a0 = p[i][nv], a1 = p[i + 1][nv], b0 = lerp(p[i][nv - 1], a0, 0.85f), b1 = lerp(p[i + 1][nv - 1], a1, 0.85f);
                    flaeche(n, hoch(b0, d + 0.02f), hoch(b1, d + 0.02f), hoch(a1, d + 0.02f), hoch(a0, d + 0.02f), HINTEN, a);
                }
            }
            if (s.hat(ADERN)) {
                int mitte = nu / 2;
                for (int j = 0; j < nv - 1; j++) {
                    for (int sgn = -1; sgn <= 1; sgn += 2) {
                        float[] q0 = p[mitte][j], q1 = p[mitte][j + 1];
                        float[] r0 = lerp(q0, p[mitte + 1][j], 0.12f), r1 = lerp(q1, p[mitte + 1][j + 1], 0.12f);
                        float dd = sgn * (d + 0.02f);
                        flaeche(n, hoch(q0, dd), hoch(r0, dd), hoch(r1, dd), hoch(q1, dd), sgn > 0 ? HINTEN : VORN, dunkler(a, 0.15f * j));
                    }
                }
            }
            n.leuchten(0f);
        }
    }

    private static float[] hoch(float[] p, float dz) { return new float[] { p[0], p[1], p[2] + dz }; }

    private static float[] lerp(float[] a, float[] b, float k) {
        return new float[] { a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k };
    }

    private static void kralle(Netz n, Stil s, float[] wo, float w, float laenge, float r) {
        float[] spitze = { wo[0] + (float) Math.cos(w * GRAD) * laenge, wo[1] + (float) Math.sin(w * GRAD) * laenge, wo[2] };
        if (s.hat(ADERN)) n.leuchten(0f);
        n.kegel(wo, r, spitze, 6, s.kante(), heller(s.kante(), 0.2f));
    }

    /** Daemon- und Drachenfluegel: Oberarm, Unterarm, Hand mit vier Fingern. */
    private static void hautfluegel(Netz[] n, Stil s) {
        float[] len = laengen(s);
        boolean rand = s.hat(RAND);
        Netz o = n[0];
        knochen(o, s, 0f, len[0] + 0.2f, 1.1f, 0.85f, 0, 0.3f, false);
        haut(o, s, new float[] { 0, -0.6f, 0 }, new float[] { len[0], -0.4f, 0 }, new float[] { 0, -11.5f, 0 }, new float[] { len[0] + 0.5f, -12.5f, 0 }, 1.4f, rand);
        Netz u = n[1];
        knochen(u, s, 0f, len[1] + 0.2f, 0.9f, 0.7f, 0, 0.3f, false);
        haut(u, s, new float[] { -0.2f, -0.4f, 0 }, new float[] { len[1], -0.3f, 0 }, new float[] { -0.6f, -12.5f, 0 }, new float[] { len[1] + 1.5f, -13.5f, 0 }, 1.8f, rand);
        if (s.hat(STACHELN)) {
            for (int i = 0; i < 2; i++) {
                Netz x = n[i];
                float l = len[i];
                for (int k = 1; k <= 2; k++) {
                    float px = l * k / 3f;
                    x.kegel(new float[] { px, 0.55f, 0.4f }, 0.42f, new float[] { px - 0.6f, 2.0f, 0.9f }, 5, s.kante(), heller(s.kante(), 0.25f));
                }
            }
        }
        Netz h = n[2];
        float[] finger = { 12f, -14f, -42f, -70f };
        float[] lang = { 13.5f, 15f, 14f, 12.5f };
        float[][] spitzen = new float[finger.length][];
        for (int i = 0; i < finger.length; i++) {
            float w = finger[i] * GRAD;
            float[] tip = { (float) Math.cos(w) * lang[i], (float) Math.sin(w) * lang[i], 0.2f };
            spitzen[i] = tip;
            float[] knick = { tip[0] * 0.5f - (float) Math.sin(w) * 0.5f, tip[1] * 0.5f + (float) Math.cos(w) * 0.5f, 0.35f };
            h.roehre(new float[][] { { 0, 0, 0.3f }, knick, tip }, new float[] { 0.62f, 0.42f, 0.16f }, 6, s.a(), dunkler(s.a(), 0.2f));
            if (i == 0) kralle(h, s, tip, finger[i] - 25f, 1.6f, 0.32f);
        }
        h.kugel(0, 0, 0.3f, 0.95f, 7, heller(s.a(), 0.1f), s.a());
        kralle(h, s, new float[] { 0.2f, 0.6f, 0.4f }, 115f, 2.2f, 0.45f);
        for (int i = 0; i < finger.length - 1; i++) {
            float[] a = { spitzen[i][0] * 0.08f, spitzen[i][1] * 0.08f, 0 };
            float[] b = { spitzen[i + 1][0] * 0.08f, spitzen[i + 1][1] * 0.08f, 0 };
            haut(h, s, a, b, spitzen[i], spitzen[i + 1], 3.2f, rand);
        }
        float[] letzte = spitzen[finger.length - 1];
        haut(h, s, new float[] { 0, -0.5f, 0 }, new float[] { letzte[0] * 0.08f, letzte[1] * 0.08f, 0 }, new float[] { -6.5f, -13.2f, 0 }, letzte, 2.2f, rand);
    }

    /** Schmetterling: Vorder- und Hinterfluegel als ein Glied. */
    private static void falter(Netz n, Stil s) {
        lappen(n, s, 92f, 8f, 15.5f, 42f, true);
        lappen(n, s, -4f, -82f, 11.5f, -38f, false);
    }

    /** Radius des Fluegelumrisses bei Winkel w (Grad) -- rundlich, am weitesten bei 'spitzeW'. */
    private static float umriss(float w, float von, float bis, float r, float spitzeW) {
        float t = (w - von) / (bis - von);
        float rund = (float) Math.sin(Math.PI * Math.max(0, Math.min(1, t)));
        float d = (w - spitzeW) / 55f;
        return r * (0.28f + 0.72f * (float) Math.pow(rund, 0.55)) * (0.82f + 0.18f * (float) Math.exp(-d * d * 4));
    }

    private static void lappen(Netz n, Stil s, float von, float bis, float r, float spitzeW, boolean vorne) {
        int na = 14;
        float[] ringe = { 0f, 0.22f, 0.5f, 0.72f, 0.84f, 0.93f, 1f };
        int[] farben = { s.kante(), dunkler(s.a(), 0.25f), s.a(), s.b(), Netz.mische(s.b(), s.c(), 0.65f), s.c(), s.c() };
        float d = 0.12f;
        float[][][] p = new float[na + 1][ringe.length][];
        for (int i = 0; i <= na; i++) {
            float w = von + (bis - von) * i / na;
            float rr = umriss(w, von, bis, r, spitzeW);
            float c = (float) Math.cos(w * GRAD), sn = (float) Math.sin(w * GRAD);
            for (int j = 0; j < ringe.length; j++) {
                float v = ringe[j];
                p[i][j] = new float[] { c * rr * v + 0.4f, sn * rr * v, 0.5f * v * v };
            }
        }
        for (int i = 0; i < na; i++) {
            for (int j = 0; j < ringe.length - 1; j++) {
                float[] a = p[i][j], b = p[i + 1][j], c = p[i + 1][j + 1], e = p[i][j + 1];
                int c0 = farben[j], c1 = farben[j + 1];
                flaeche(n, hoch(a, d), hoch(b, d), hoch(c, d), hoch(e, d), HINTEN, c0, c0, c1, c1);
                flaeche(n, hoch(a, -d), hoch(e, -d), hoch(c, -d), hoch(b, -d), VORN, dunkler(c0, 0.18f), dunkler(c1, 0.18f), dunkler(c1, 0.18f), dunkler(c0, 0.18f));
            }
            float[] a = p[i][ringe.length - 1], b = p[i + 1][ringe.length - 1];
            float[] aus = Netz.norm(new float[] { a[0] + b[0], a[1] + b[1], 0 });
            flaeche(n, hoch(a, d), hoch(b, d), hoch(b, -d), hoch(a, -d), aus, dunkler(s.c(), 0.2f));
        }
        for (int i = 1; i < na; i += 2) {
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                float dd = sgn * (d + 0.03f);
                float[] q0 = p[i][1], q1 = p[i][ringe.length - 2];
                float[] seit = Netz.norm(Netz.sub(p[i + 1][4], p[i][4]));
                float bw = 0.16f;
                float[] q0b = { q0[0] + seit[0] * bw, q0[1] + seit[1] * bw, q0[2] }, q1b = { q1[0] + seit[0] * bw, q1[1] + seit[1] * bw, q1[2] };
                flaeche(n, hoch(q0, dd), hoch(q0b, dd), hoch(q1b, dd), hoch(q1, dd), sgn > 0 ? HINTEN : VORN, s.kante());
            }
        }
        for (int i = 1; i < na; i++) {
            if ((i & 1) == 0) continue;
            float[] m = lerp(lerp(p[i][5], p[i + 1][5], 0.5f), lerp(p[i][6], p[i + 1][6], 0.5f), 0.4f);
            float rr = vorne ? 0.55f : 0.45f;
            for (int sgn = -1; sgn <= 1; sgn += 2) scheibe(n, m, rr, sgn * (d + 0.04f), s.schaft(), s.schaft());
        }
        float wa = vorne ? (von + bis) * 0.5f + 8f : (von + bis) * 0.5f;
        float ra = umriss(wa, von, bis, r, spitzeW) * (vorne ? 0.62f : 0.56f);
        float[] auge = { (float) Math.cos(wa * GRAD) * ra + 0.4f, (float) Math.sin(wa * GRAD) * ra, 0.5f * 0.4f };
        float gr = vorne ? 1.6f : 2.0f;
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            float dd = sgn * (d + 0.05f);
            scheibe(n, auge, gr, dd, s.akzent(), heller(s.akzent(), 0.15f));
            scheibe(n, auge, gr * 0.62f, dd + sgn * 0.02f, s.c(), s.c());
            scheibe(n, new float[] { auge[0] + 0.25f * gr, auge[1] + 0.2f * gr, auge[2] }, gr * 0.22f, dd + sgn * 0.04f, s.schaft(), s.schaft());
        }
    }

    /** Flache runde Scheibe (z. B. Punkt, Auge) knapp ueber der Flaeche. */
    private static void scheibe(Netz n, float[] m, float r, float dz, int innen, int aussen) {
        int seg = 10;
        float[] hin = dz > 0 ? HINTEN : VORN;
        float[] c = { m[0], m[1], m[2] + dz };
        for (int i = 0; i < seg; i++) {
            double a0 = i * 2 * Math.PI / seg, a1 = (i + 1) * 2 * Math.PI / seg;
            float[] p0 = { c[0] + (float) Math.cos(a0) * r, c[1] + (float) Math.sin(a0) * r, c[2] };
            float[] p1 = { c[0] + (float) Math.cos(a1) * r, c[1] + (float) Math.sin(a1) * r, c[2] };
            flaeche(n, c, p0, p1, c, hin, innen, aussen, aussen, innen);
        }
    }
}
