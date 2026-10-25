package com.vortex.client.cosmetics;

import java.util.ArrayList;
import java.util.List;

/**
 * Federfluegel als echtes 3D-Modell (seit 4.82): statt eines flachen Pixelbilds besteht
 * jeder Fluegel aus einzelnen, geschichteten Federn -- lange Schwungfedern unten, darueber
 * zwei Reihen Deckfedern und kleine Randfedern an der Vorderkante. Jede Feder ist leicht
 * gewoelbt und zur Spitze hin gebogen und hat eine fein gezeichnete Textur (Kiel, Aeste,
 * weiche Kante, kleine Spalten).
 *
 * Form und Farben kommen aus dem Pixelbild des Designs: Laenge der Federn folgt dem Umriss,
 * jede Feder nimmt die Farben unter sich (Wurzel bis Spitze) und leuchtet, wo das Bild
 * leuchtet. So bekommen alle Federfluegel-Designs automatisch die neue Qualitaet.
 *
 * Ohne Minecraft-Klassen, damit sich alles ausserhalb des Spiels pruefen laesst.
 * Koordinaten wie die Fluegelglieder in ZubehoerEbene: Bildpixel, Ursprung am Gelenk des
 * Glieds (x nach aussen, y nach oben), z nach hinten (Aussenseite des Fluegels).
 */
public final class FederKunst {
    private FederKunst() {}

    /** Atlas: drei Federarten nebeneinander, je TW x TH. */
    public static final int TW = 64, TH = 256, ARTEN = 3;
    public static final int SCHWUNG = 0, ARM = 1, DECK = 2;
    /** Reihen je Feder (entlang) und Spalten (quer: Rand, Kiel, Rand). */
    public static final int REIHEN = 6, SPALTEN = 3;

    /** Eine Feder: (REIHEN+1) x SPALTEN Eckpunkte. */
    public static final class Feder {
        public final int art;
        public final float[] pos, nrm, uv, entlang;
        public final int[] farbe, glut;
        /** Wurzel (fuer das Flattern) und Laenge. */
        public final float laenge, phase;
        public final boolean flattert;
        public boolean leuchtet;

        Feder(int art, float laenge, float phase, boolean flattert) {
            int n = (REIHEN + 1) * SPALTEN;
            this.art = art;
            this.laenge = laenge;
            this.phase = phase;
            this.flattert = flattert;
            pos = new float[n * 3];
            nrm = new float[n * 3];
            uv = new float[n * 2];
            entlang = new float[n];
            farbe = new int[n];
            glut = new int[n];
        }
    }

    /** Die Federn je Glied (0 = Schulter, 1 = Mitte, 2 = Spitze). */
    public record Modell(List<List<Feder>> glieder, boolean leuchtet) {}

    /** Halbe Breite der Feder (0..1) an Stelle v (0 = Wurzel, 1 = Spitze), u &lt; 0 = Aussenfahne. */
    static float breite(int art, float v, boolean aussen) {
        float kiel = art == DECK ? 0.03f : 0.06f;
        float voll = art == DECK ? 0.16f : art == ARM ? 0.24f : 0.28f;
        float rund = art == DECK ? 0.42f : art == ARM ? 0.72f : 0.62f;
        if (v < kiel) return 0f;
        float w = v < voll ? 0.3f + 0.7f * glatt(kiel, voll, v) : 1f;
        if (aussen) w *= art == SCHWUNG ? 0.62f : art == ARM ? 0.8f : 0.92f;
        if (v > rund) {
            float k = (v - rund) / (1f - rund);
            float form = art == SCHWUNG ? (float) Math.pow(Math.max(0, 1 - k * k), 0.62f) : (float) Math.sqrt(Math.max(0, 1 - k * k));
            if (art == SCHWUNG && aussen) form *= 1f - 0.25f * k;
            w *= form;
        }
        return w;
    }

    /** Der Atlas in Graustufen (Farbe kommt aus den Eckpunkten), ABGR wie NativeImage. */
    public static int[] atlas() {
        int w = TW * ARTEN;
        int[] px = new int[w * TH];
        for (int art = 0; art < ARTEN; art++) {
            float spalt1 = 0.42f + 0.21f * art, spalt2 = 0.58f + 0.13f * art;
            for (int y = 0; y < TH; y++) {
                float v = (y + 0.5f) / TH;
                float wa = breite(art, v, true), wi = breite(art, v, false);
                for (int x = 0; x < TW; x++) {
                    float u = (x + 0.5f) / TW * 2f - 1f;
                    boolean aussen = u < 0;
                    float wmax = aussen ? wa : wi;
                    float au = Math.abs(u);
                    float pxRand = (wmax - au) * TW / 2f;
                    float alpha = klemm(pxRand + 0.5f);
                    float kielB = art == DECK ? 0.045f : 0.06f;
                    float kielA = klemm((kielB * (1f - 0.6f * v) - au) * TW / 2f + 0.5f) * (v < 0.97f ? 1f : 0f);
                    if (art != DECK && wmax > 1e-4f) {
                        float e = au / wmax, tief = aussen ? 0.35f : 0.22f;
                        float mitte = aussen ? spalt1 : spalt2;
                        if (e > 1f - tief) {
                            float abw = Math.abs(v - mitte - (e - (1f - tief)) * 0.05f) * TH;
                            alpha *= klemm(abw - 0.6f);
                        }
                    }
                    float a = Math.max(alpha, kielA);
                    if (a <= 0.002f) continue;
                    float e = wmax > 1e-4f ? Math.min(1f, au / wmax) : 1f;
                    float g = 0.97f;
                    g += 0.035f * (float) Math.sin(2 * Math.PI * (v * 30f + au * 4.5f));
                    g += 0.01f * (float) Math.sin(2 * Math.PI * (v * 77f + au * 9f));
                    g *= 1f - 0.13f * glatt(0.78f, 1f, e);
                    g *= 0.8f + 0.2f * glatt(0f, 0.3f, v);
                    g *= 1f - 0.05f * (aussen ? 1f : 0f);
                    float kd = au / Math.max(0.01f, kielB * (1f - 0.6f * v));
                    if (kd < 1f) g = Math.max(g, 1f - 0.1f * kd);
                    else if (kd < 1.7f) g *= 0.86f + 0.14f * (kd - 1f) / 0.7f;
                    int k = Math.round(klemm(g) * 255f);
                    px[y * w + art * TW + x] = Math.round(a * 255f) << 24 | k << 16 | k << 8 | k;
                }
            }
        }
        SpriteKunst.ausbluten(px, w, TH, 4);
        return px;
    }

    /**
     * Das Modell aus dem Pixelbild (rechter Fluegel, Spalte 0 am Ruecken). grenzen = Spalten,
     * an denen die Glieder beginnen ({0, a, b, breite}); gelenk = Bildzeile auf Hoehe des Gelenks.
     */
    public static Modell bauen(int[][] farben, boolean[][] glow, int[] grenzen, float gelenk) {
        int gh = farben.length, gw = 0;
        for (int[] r : farben) gw = Math.max(gw, r.length);
        boolean[] voll = SpriteKunst.belegt(farben, gw);
        float[] oben = new float[gw], unten = new float[gw];
        for (int x = 0; x < gw; x++) {
            oben[x] = -1; unten[x] = -1;
            for (int y = 0; y < gh; y++) if (voll[y * gw + x]) { if (oben[x] < 0) oben[x] = y; unten[x] = y + 1; }
        }
        luecken(oben); luecken(unten);
        oben = glaetten(oben); unten = glaetten(unten);
        List<List<Feder>> glieder = new ArrayList<>();
        for (int i = 0; i < 3; i++) glieder.add(new ArrayList<>());
        Ctx cx = new Ctx(farben, glow, voll, gw, gh, grenzen, gelenk, glieder);

        int nr = 0;
        for (float x = 0.9f; x < gw - 0.6f; x += 2.05f) {
            float o = wert(oben, x), u = wert(unten, x);
            if (o < 0 || u - o < 3f) continue;
            float rel = x / gw;
            float neig = -1.4f + 7.5f * rel * rel + (zufall(nr * 7 + 3) - 0.5f) * 0.6f;
            float tx = Math.max(0.3f, Math.min(gw - 0.4f, x + neig));
            float tu = wert(unten, tx) - zufall(nr * 13 + 5) * 0.5f;
            float wurzel = o + 0.3f * (u - o);
            int art = x >= grenzen[2] ? SCHWUNG : ARM;
            cx.krumm = 0.05f + 0.06f * rel;
            cx.feder(art, x - neig * 0.1f, wurzel, tx, tu + 0.5f, art == SCHWUNG ? 4.3f : 4.6f, nr * 0.032f - 0.55f, 0.07f, 0.16f, true, nr);
            cx.krumm = 0f;
            nr++;
        }
        nr = 0;
        for (float x = 0.6f; x < gw - 1.2f; x += 1.85f) {
            float o = wert(oben, x), u = wert(unten, x);
            if (o < 0 || u - o < 3f) continue;
            float h = u - o, rel = x / gw;
            float wurzel = o + 0.08f * h, lang = Math.max(2.6f, 0.36f * h);
            float tx = x + 0.9f * rel;
            cx.feder(DECK, x, wurzel, tx, wurzel + lang, 4.0f, 0.05f + nr * 0.018f, -0.06f, 0.14f, false, 100 + nr);
            nr++;
        }
        nr = 0;
        for (float x = 0.5f; x < gw - 1.6f; x += 1.6f) {
            float o = wert(oben, x), u = wert(unten, x);
            if (o < 0 || u - o < 3f) continue;
            float h = u - o, rel = x / gw;
            float wurzel = o + 0.02f * h, lang = Math.max(2.2f, 0.2f * h);
            cx.feder(DECK, x, wurzel, x + 0.6f * rel, wurzel + lang, 3.4f, 0.42f + nr * 0.015f, -0.08f, 0.12f, false, 200 + nr);
            nr++;
        }
        nr = 0;
        for (float x = 0.4f; x < gw - 2f; x += 1.35f) {
            float o = wert(oben, x), u = wert(unten, x);
            if (o < 0 || u - o < 3f) continue;
            float h = u - o;
            float lang = Math.max(1.9f, 0.12f * h);
            cx.feder(DECK, x, o - 0.25f, x + 0.3f, o - 0.25f + lang, 2.8f, 0.74f + nr * 0.012f, -0.05f, 0.1f, false, 300 + nr);
            nr++;
        }
        return new Modell(glieder, cx.leuchtet);
    }

    private static final class Ctx {
        final int[][] farben; final boolean[][] glow; final boolean[] voll;
        final int gw, gh; final int[] grenzen; final float gelenk;
        final List<List<Feder>> glieder;
        boolean leuchtet;
        /** Kruemmung der naechsten Feder in der Fluegelebene (Anteil der Laenge, nach aussen). */
        float krumm;

        Ctx(int[][] farben, boolean[][] glow, boolean[] voll, int gw, int gh, int[] grenzen, float gelenk, List<List<Feder>> glieder) {
            this.farben = farben; this.glow = glow; this.voll = voll; this.gw = gw; this.gh = gh;
            this.grenzen = grenzen; this.gelenk = gelenk; this.glieder = glieder;
        }

        /**
         * Eine Feder von (wx, wy) nach (sx, sy) in Bildkoordinaten (y nach unten), Breite b,
         * Hoehe z, Biegung zur Spitze (z), Woelbung quer.
         */
        void feder(int art, float wx, float wy, float sx, float sy, float b, float z, float biegung, float woelbung, boolean flattert, int nr) {
            int glied = wx < grenzen[1] ? 0 : wx < grenzen[2] ? 1 : 2;
            float g0 = grenzen[glied];
            float dx = sx - wx, dy = sy - wy;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 0.5f) return;
            float ax = dx / len, ay = dy / len;
            float qx = -ay, qy = ax;
            Feder f = new Feder(art, len, nr * 1.7f, flattert);
            boolean irgendwo = false;
            for (int r = 0; r <= REIHEN; r++) {
                float v = (float) r / REIHEN;
                float ix = wx + ax * len * v, iy = wy + ay * len * v;
                int c = SpriteKunst.umgebung(farben, voll, gw, gh, ix, iy);
                if (c == 0) c = SpriteKunst.umgebung(farben, voll, gw, gh, wx, wy);
                if (c == 0) c = 0xFFFFFFFF;
                float gl = glutAnteil(ix, iy);
                float schatten = art == DECK ? 1f : 0.95f;
                c = hell(c, schatten);
                for (int s = 0; s < SPALTEN; s++) {
                    float uq = s - 1f;
                    float hb = b / 2f;
                    float bogen = krumm * len * v * v;
                    float px = ix + qx * uq * hb - qx * bogen, py = iy + qy * uq * hb - qy * bogen;
                    float pz = z + biegung * len * v * v + (s == 1 ? woelbung * b : 0f);
                    int k = r * SPALTEN + s;
                    f.pos[k * 3] = px - g0;
                    f.pos[k * 3 + 1] = gelenk - py;
                    f.pos[k * 3 + 2] = pz;
                    f.uv[k * 2] = (s * 0.5f * (TW - 1) + 0.5f) / (TW * ARTEN) + (float) art / ARTEN;
                    f.uv[k * 2 + 1] = v;
                    f.entlang[k] = v;
                    f.farbe[k] = c;
                    f.glut[k] = gl > 0.02f ? (Math.round(gl * 255f) << 24) | (c & 0xFFFFFF) : 0;
                    if (gl > 0.02f) irgendwo = true;
                }
            }
            normalen(f);
            f.leuchtet = irgendwo;
            if (irgendwo) leuchtet = true;
            glieder.get(glied).add(f);
        }

        float glutAnteil(float x, float y) {
            if (glow == null) return 0f;
            int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
            float s = 0, n = 0;
            for (int j = -2; j <= 2; j++) for (int i = -2; i <= 2; i++) {
                int xx = ix + i, yy = iy + j;
                if (xx < 0 || yy < 0 || xx >= gw || yy >= gh || !voll[yy * gw + xx]) continue;
                float ddx = xx + 0.5f - x, ddy = yy + 0.5f - y;
                float w = (float) Math.exp(-(ddx * ddx + ddy * ddy) / 0.9f);
                n += w;
                if (yy < glow.length && xx < glow[yy].length && glow[yy][xx]) s += w;
            }
            return n <= 1e-6f ? 0f : s / n;
        }
    }

    /** Normalen je Eckpunkt aus den Nachbarn (entlang x quer). */
    static void normalen(Feder f) {
        for (int r = 0; r <= REIHEN; r++) {
            for (int s = 0; s < SPALTEN; s++) {
                int k = r * SPALTEN + s;
                int ra = Math.max(0, r - 1), rb = Math.min(REIHEN, r + 1);
                int sa = Math.max(0, s - 1), sb = Math.min(SPALTEN - 1, s + 1);
                float[] t1 = diff(f.pos, rb * SPALTEN + s, ra * SPALTEN + s);
                float[] t2 = diff(f.pos, r * SPALTEN + sb, r * SPALTEN + sa);
                float nx = t1[1] * t2[2] - t1[2] * t2[1], ny = t1[2] * t2[0] - t1[0] * t2[2], nz = t1[0] * t2[1] - t1[1] * t2[0];
                if (nz < 0) { nx = -nx; ny = -ny; nz = -nz; }
                float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (l < 1e-6f) { nx = 0; ny = 0; nz = 1; l = 1; }
                f.nrm[k * 3] = nx / l; f.nrm[k * 3 + 1] = ny / l; f.nrm[k * 3 + 2] = nz / l;
            }
        }
    }

    private static float[] diff(float[] p, int a, int b) {
        return new float[] { p[a * 3] - p[b * 3], p[a * 3 + 1] - p[b * 3 + 1], p[a * 3 + 2] - p[b * 3 + 2] };
    }

    /** Leere Spalten (-1) mit den Nachbarn fuellen, damit der Umriss nicht abreisst. */
    private static void luecken(float[] a) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] >= 0) continue;
            int l = i - 1, r = i + 1;
            while (l >= 0 && a[l] < 0) l--;
            while (r < a.length && a[r] < 0) r++;
            if (l >= 0 && r < a.length) a[i] = (a[l] + a[r]) / 2f;
        }
    }

    private static float[] glaetten(float[] a) {
        float[] o = new float[a.length];
        for (int i = 0; i < a.length; i++) {
            if (a[i] < 0) { o[i] = -1; continue; }
            float s = a[i] * 2, n = 2;
            if (i > 0 && a[i - 1] >= 0) { s += a[i - 1]; n++; }
            if (i + 1 < a.length && a[i + 1] >= 0) { s += a[i + 1]; n++; }
            o[i] = s / n;
        }
        return o;
    }

    /** Wert an einer Zwischenstelle (Spaltenmitte bei x + 0.5). */
    private static float wert(float[] a, float x) {
        float f = x - 0.5f;
        int i = (int) Math.floor(f);
        float k = f - i;
        float l = a[Math.max(0, Math.min(a.length - 1, i))], r = a[Math.max(0, Math.min(a.length - 1, i + 1))];
        if (l < 0) return r;
        if (r < 0) return l;
        return l + (r - l) * k;
    }

    private static int hell(int c, float f) {
        int r = Math.min(255, Math.round(((c >> 16) & 255) * f)), g = Math.min(255, Math.round(((c >> 8) & 255) * f)), b = Math.min(255, Math.round((c & 255) * f));
        return (c & 0xFF000000) | r << 16 | g << 8 | b;
    }

    /** Fester Zufall 0..1 je Zahl (gleich bei jedem Start). */
    private static float zufall(int s) {
        int x = s * 0x45d9f3b;
        x = ((x >>> 16) ^ x) * 0x45d9f3b;
        x = (x >>> 16) ^ x;
        return (x & 0xFFFF) / 65535f;
    }

    private static float klemm(float v) { return v < 0 ? 0 : v > 1 ? 1 : v; }

    private static float glatt(float a, float b, float v) {
        float k = klemm((v - a) / (b - a));
        return k * k * (3 - 2 * k);
    }
}
