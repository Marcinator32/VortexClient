package com.vortex.client.cosmetics;

import java.util.ArrayList;
import java.util.List;

/**
 * Einfaches 3D-Netz fuer Kopfschmuck (Huete & Co.): glatte Formen statt
 * Kloetzchen -- Ringe, Kugeln, Kegel, gebogene Roehren. Ohne Minecraft-
 * Klassen, damit sich jedes Teil auch ausserhalb des Spiels als Bild pruefen
 * laesst (Vorschau im Test).
 *
 * Einheiten: Minecraft-Pixel (1/16 Block). Ursprung = Mitte der Kopfoberseite,
 * y nach OBEN, z nach hinten (-z = Gesicht).
 *
 * Jede Flaeche ist ein Viereck (Dreiecke = Viereck mit doppelter Ecke) mit
 * Normale je Ecke (weich schattiert), Farbe je Ecke, Leuchten (0..1) und
 * "Metall" (dann wandert ein Glanzstreifen darueber).
 */
public final class Netz {
    /** x,y,z, nx,ny,nz je Ecke (4 Ecken), dazu Farben je Ecke, Leuchten, Metall. */
    public static final class Flaeche {
        public final float[] p = new float[12], n = new float[12];
        public final int[] farbe = new int[4];
        public float leuchten;
        public boolean metall;
    }

    public final List<Flaeche> flaechen = new ArrayList<>();

    private float[] m = { 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0 };
    private final List<float[]> stapel = new ArrayList<>();

    public void push() { stapel.add(m.clone()); }
    public void pop() { m = stapel.remove(stapel.size() - 1); }

    public void verschiebe(float x, float y, float z) {
        m[3] += m[0] * x + m[1] * y + m[2] * z;
        m[7] += m[4] * x + m[5] * y + m[6] * z;
        m[11] += m[8] * x + m[9] * y + m[10] * z;
    }

    private void mal(float[] r) {
        float[] o = m.clone();
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) {
            m[i * 4 + j] = o[i * 4] * r[j] + o[i * 4 + 1] * r[3 + j] + o[i * 4 + 2] * r[6 + j];
        }
    }

    public void drehX(float a) { float c = (float) Math.cos(a), s = (float) Math.sin(a); mal(new float[]{ 1, 0, 0, 0, c, -s, 0, s, c }); }
    public void drehY(float a) { float c = (float) Math.cos(a), s = (float) Math.sin(a); mal(new float[]{ c, 0, s, 0, 1, 0, -s, 0, c }); }
    public void drehZ(float a) { float c = (float) Math.cos(a), s = (float) Math.sin(a); mal(new float[]{ c, -s, 0, s, c, 0, 0, 0, 1 }); }
    public void skaliere(float f) { mal(new float[]{ f, 0, 0, 0, f, 0, 0, 0, f }); }
    public void skaliere(float x, float y, float z) { mal(new float[]{ x, 0, 0, 0, y, 0, 0, 0, z }); }

    private float leuchten;
    private boolean metall;
    public Netz leuchten(float l) { leuchten = l; return this; }
    public Netz metall(boolean b) { metall = b; return this; }

    /** Ein Viereck; Ecken a..d mit Normalen und Farben (lokal, wird transformiert). */
    public void viereck(float[] a, float[] b, float[] c, float[] d, float[] na, float[] nb, float[] nc, float[] nd,
                        int fa, int fb, int fc, int fd) {
        Flaeche f = new Flaeche();
        float[][] pp = { a, b, c, d }, nn = { na, nb, nc, nd };
        for (int i = 0; i < 4; i++) {
            float x = pp[i][0], y = pp[i][1], z = pp[i][2];
            f.p[i * 3] = m[0] * x + m[1] * y + m[2] * z + m[3];
            f.p[i * 3 + 1] = m[4] * x + m[5] * y + m[6] * z + m[7];
            f.p[i * 3 + 2] = m[8] * x + m[9] * y + m[10] * z + m[11];
            float nx = nn[i][0], ny = nn[i][1], nz = nn[i][2];
            float tx = m[0] * nx + m[1] * ny + m[2] * nz, ty = m[4] * nx + m[5] * ny + m[6] * nz, tz = m[8] * nx + m[9] * ny + m[10] * nz;
            float l = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (l < 1e-6f) l = 1;
            f.n[i * 3] = tx / l; f.n[i * 3 + 1] = ty / l; f.n[i * 3 + 2] = tz / l;
        }
        f.farbe[0] = fa; f.farbe[1] = fb; f.farbe[2] = fc; f.farbe[3] = fd;
        f.leuchten = leuchten;
        f.metall = metall;
        float ux = f.p[3] - f.p[0], uy = f.p[4] - f.p[1], uz = f.p[5] - f.p[2];
        float vx = f.p[6] - f.p[0], vy = f.p[7] - f.p[1], vz = f.p[8] - f.p[2];
        float kx = uy * vz - uz * vy, ky = uz * vx - ux * vz, kz = ux * vy - uy * vx;
        if (kx * f.n[0] + ky * f.n[1] + kz * f.n[2] < 0) tausche(f);
        flaechen.add(f);
    }

    private static void tausche(Flaeche f) {
        for (int k = 0; k < 3; k++) {
            float t = f.p[3 + k]; f.p[3 + k] = f.p[9 + k]; f.p[9 + k] = t;
            t = f.n[3 + k]; f.n[3 + k] = f.n[9 + k]; f.n[9 + k] = t;
        }
        int t = f.farbe[1]; f.farbe[1] = f.farbe[3]; f.farbe[3] = t;
    }

    private static final float[] NX = { 1, 0, 0 }, NXM = { -1, 0, 0 }, NY = { 0, 1, 0 }, NYM = { 0, -1, 0 }, NZ = { 0, 0, 1 }, NZM = { 0, 0, -1 };

    /** Achsenparalleler Quader; 'seiten' = Bitmaske, welche Flaechen (x+,x-,y+,y-,z+,z-; 0x3F = alle). */
    public void quader(float x0, float y0, float z0, float x1, float y1, float z1, int farbe, int seiten) {
        if ((seiten & 1) != 0) viereck(new float[]{ x1, y0, z0 }, new float[]{ x1, y1, z0 }, new float[]{ x1, y1, z1 }, new float[]{ x1, y0, z1 }, NX, NX, NX, NX, farbe, farbe, farbe, farbe);
        if ((seiten & 2) != 0) viereck(new float[]{ x0, y0, z0 }, new float[]{ x0, y0, z1 }, new float[]{ x0, y1, z1 }, new float[]{ x0, y1, z0 }, NXM, NXM, NXM, NXM, farbe, farbe, farbe, farbe);
        if ((seiten & 4) != 0) viereck(new float[]{ x0, y1, z0 }, new float[]{ x0, y1, z1 }, new float[]{ x1, y1, z1 }, new float[]{ x1, y1, z0 }, NY, NY, NY, NY, farbe, farbe, farbe, farbe);
        if ((seiten & 8) != 0) viereck(new float[]{ x0, y0, z0 }, new float[]{ x1, y0, z0 }, new float[]{ x1, y0, z1 }, new float[]{ x0, y0, z1 }, NYM, NYM, NYM, NYM, farbe, farbe, farbe, farbe);
        if ((seiten & 16) != 0) viereck(new float[]{ x0, y0, z1 }, new float[]{ x1, y0, z1 }, new float[]{ x1, y1, z1 }, new float[]{ x0, y1, z1 }, NZ, NZ, NZ, NZ, farbe, farbe, farbe, farbe);
        if ((seiten & 32) != 0) viereck(new float[]{ x0, y0, z0 }, new float[]{ x0, y1, z0 }, new float[]{ x1, y1, z0 }, new float[]{ x1, y0, z0 }, NZM, NZM, NZM, NZM, farbe, farbe, farbe, farbe);
    }

    /**
     * Voxel-Modell (seit 4.32): c[y][z][x] = ARGB (Alpha 0 = leer), y = 0 unten,
     * z = 0 vorne (Gesichtsseite). Mitte des Modells im Ursprung, ein Voxel = 1.
     * Nur sichtbare Seiten; an jeder Ecke Umgebungsverdeckung (Ambient Occlusion)
     * wie Minecrafts weiche Beleuchtung -- Ecken und Kanten werden dunkler, das
     * Modell wirkt plastisch. Leuchtende Voxel (glow) ohne Verdunkelung.
     */
    public void voxel(int[][][] c, boolean[][][] glow) {
        int h = c.length, d = 0, w = 0;
        for (int[][] l : c) { d = Math.max(d, l.length); for (int[] r : l) w = Math.max(w, r.length); }
        float ox = w / 2f, oy = h / 2f, oz = d / 2f;
        float alt = leuchten;
        int[] q = new int[3], n1 = new int[3], n2 = new int[3];
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < c[y].length; z++) {
                for (int x = 0; x < c[y][z].length; x++) {
                    int f = c[y][z][x];
                    if ((f >>> 24) == 0) continue;
                    boolean leucht = glow != null && glow[y][z][x];
                    leuchten = leucht ? 1f : alt;
                    for (int achse = 0; achse < 3; achse++) {
                        for (int s = -1; s <= 1; s += 2) {
                            int nx = x + (achse == 0 ? s : 0), ny = y + (achse == 1 ? s : 0), nz = z + (achse == 2 ? s : 0);
                            if (voll(c, nx, ny, nz)) continue;
                            int b = (achse + 1) % 3, cc = (achse + 2) % 3;
                            float[][] ecken = new float[4][];
                            int[] farben = new int[4];
                            int[][] off = { { 0, 0 }, { 1, 0 }, { 1, 1 }, { 0, 1 } };
                            int aoSumme02 = 0, aoSumme13 = 0;
                            int[] ao = new int[4];
                            for (int e = 0; e < 4; e++) {
                                float[] pkt = new float[3];
                                pkt[achse] = (achse == 0 ? x : achse == 1 ? y : z) + (s > 0 ? 1 : 0);
                                pkt[b] = (b == 0 ? x : b == 1 ? y : z) + off[e][0];
                                pkt[cc] = (cc == 0 ? x : cc == 1 ? y : z) + off[e][1];
                                ecken[e] = new float[]{ pkt[0] - ox, pkt[1] - oy, pkt[2] - oz };
                                q[0] = nx; q[1] = ny; q[2] = nz;
                                int db = off[e][0] == 1 ? 1 : -1, dc = off[e][1] == 1 ? 1 : -1;
                                n1[0] = q[0]; n1[1] = q[1]; n1[2] = q[2]; n1[b] += db;
                                n2[0] = q[0]; n2[1] = q[1]; n2[2] = q[2]; n2[cc] += dc;
                                boolean s1 = voll(c, n1[0], n1[1], n1[2]), s2 = voll(c, n2[0], n2[1], n2[2]);
                                boolean ec = voll(c, n1[0] + (cc == 0 ? dc : 0), n1[1] + (cc == 1 ? dc : 0), n1[2] + (cc == 2 ? dc : 0));
                                int a = s1 && s2 ? 3 : (s1 ? 1 : 0) + (s2 ? 1 : 0) + (ec ? 1 : 0);
                                ao[e] = leucht ? 0 : a;
                                farben[e] = AO[ao[e]] < 1f ? dunkel(f, AO[ao[e]]) : f;
                            }
                            aoSumme02 = ao[0] + ao[2]; aoSumme13 = ao[1] + ao[3];
                            float[] nrm = achse == 0 ? (s > 0 ? NX : NXM) : achse == 1 ? (s > 0 ? NY : NYM) : (s > 0 ? NZ : NZM);
                            int st = aoSumme02 > aoSumme13 ? 1 : 0;
                            viereck(ecken[st], ecken[(st + 1) % 4], ecken[(st + 2) % 4], ecken[(st + 3) % 4], nrm, nrm, nrm, nrm,
                                    farben[st], farben[(st + 1) % 4], farben[(st + 2) % 4], farben[(st + 3) % 4]);
                        }
                    }
                }
            }
        }
        leuchten = alt;
    }

    /** Helligkeit je Verdeckungsstufe (0 = frei ... 3 = Innenecke). */
    private static final float[] AO = { 1f, 0.82f, 0.68f, 0.56f };

    private static boolean voll(int[][][] c, int x, int y, int z) {
        return y >= 0 && y < c.length && z >= 0 && z < c[y].length && x >= 0 && x < c[y][z].length && (c[y][z][x] >>> 24) != 0;
    }

    private static int dunkel(int argb, float f) {
        int r = Math.round(((argb >> 16) & 255) * f), g = Math.round(((argb >> 8) & 255) * f), b = Math.round((argb & 255) * f);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /**
     * Pixelbild als flaches 3D-Teil (wie Minecraft-Items in der Hand):
     * c[y][x] = ARGB (Alpha 0 = leer), Zeile 0 oben. Die Mitte des Bilds liegt
     * im Ursprung, ein Bildpixel ist 1 gross, 'tiefe' in Bildpixeln entlang z.
     * Seitenflaechen nur, wo der Nachbar leer ist; leuchtende Pixel (glow)
     * bekommen Leuchten 1.
     */
    public void pixelbild(int[][] c, boolean[][] glow, float tiefe) {
        int h = c.length, w = 0;
        for (int[] r : c) w = Math.max(w, r.length);
        float ox = w / 2f, oy = h / 2f, z0 = -tiefe / 2f, z1 = tiefe / 2f;
        float alt = leuchten;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < c[y].length; x++) {
                int f = c[y][x];
                if ((f >>> 24) == 0) continue;
                leuchten = glow != null && glow[y][x] ? 1f : alt;
                int seiten = 0;
                if (leer(c, x + 1, y)) seiten |= 1;
                if (leer(c, x - 1, y)) seiten |= 2;
                if (leer(c, x, y - 1)) seiten |= 4;
                if (leer(c, x, y + 1)) seiten |= 8;
                float px0 = x - ox, py1 = oy - y;
                quader(px0, py1 - 1, z0, px0 + 1, py1, z1, f, seiten);
                boolean leucht = glow != null && glow[y][x];
                int lo = kante(c, x, y, -1, 1, leucht, f), ro = kante(c, x, y, 1, 1, leucht, f);
                int lu = kante(c, x, y, -1, -1, leucht, f), ru = kante(c, x, y, 1, -1, leucht, f);
                float[] a = { px0, py1 - 1 }, b = { px0 + 1, py1 - 1 }, cc = { px0 + 1, py1 }, d = { px0, py1 };
                viereck(new float[]{ a[0], a[1], z0 }, new float[]{ d[0], d[1], z0 }, new float[]{ cc[0], cc[1], z0 }, new float[]{ b[0], b[1], z0 },
                        NZM, NZM, NZM, NZM, lu, lo, ro, ru);
                viereck(new float[]{ a[0], a[1], z1 }, new float[]{ b[0], b[1], z1 }, new float[]{ cc[0], cc[1], z1 }, new float[]{ d[0], d[1], z1 },
                        NZ, NZ, NZ, NZ, lu, ru, ro, lo);
            }
        }
        leuchten = alt;
    }

    /**
     * Seitenwaende eines flachen Teils mit glattem Umriss (seit 4.81): dieselbe Kontur wie
     * das geglaettete Bild (SpriteKunst.deckung, Schwelle FLACH_SCHWELLE), abgetastet mit Marching Squares
     * (3 Schritte je Pixel). Normalen aus dem Verlauf der Deckung -- die Wand wirkt rund.
     * Farbe = Mittel der Pixel in der Naehe, etwas dunkler; leuchtende Pixel leuchten auch an der Wand.
     */
    public void waende(int[][] c, boolean[][] glow, float tiefe) {
        waende(c, glow, tiefe, 0);
    }

    /** Wie oben; schnitte = Kanten, die nur Schnitte sind (keine Wand, siehe SpriteKunst.spalte). */
    public void waende(int[][] c, boolean[][] glow, float tiefe, int schnitte) {
        int gh = c.length, gw = 0;
        for (int[] r : c) gw = Math.max(gw, r.length);
        boolean[] voll = SpriteKunst.belegt(c, gw);
        int q = 3, R = SpriteKunst.FLACH_RAND, nx = (gw + 2 * R) * q, ny = (gh + 2 * R) * q;
        float[] fe = new float[(nx + 1) * (ny + 1)];
        for (int j = 0; j <= ny; j++) for (int i = 0; i <= nx; i++) fe[j * (nx + 1) + i] = SpriteKunst.deckung(voll, gw, gh, (float) i / q - R, (float) j / q - R, schnitte);
        float z0 = -tiefe / 2f, z1 = tiefe / 2f, ox = gw / 2f, oy = gh / 2f;
        float alt = leuchten;
        float[][] kp = new float[4][];
        for (int j = 0; j < ny; j++) {
            for (int i = 0; i < nx; i++) {
                float v0 = fe[j * (nx + 1) + i], v1 = fe[j * (nx + 1) + i + 1], v2 = fe[(j + 1) * (nx + 1) + i + 1], v3 = fe[(j + 1) * (nx + 1) + i];
                float sw = SpriteKunst.FLACH_SCHWELLE;
                int fall = (v0 >= sw ? 1 : 0) | (v1 >= sw ? 2 : 0) | (v2 >= sw ? 4 : 0) | (v3 >= sw ? 8 : 0);
                if (fall == 0 || fall == 15) continue;
                float x0 = (float) i / q - R, y0 = (float) j / q - R, d = 1f / q;
                if (((schnitte & 1) != 0 && x0 + d * 0.5f < 0) || ((schnitte & 2) != 0 && x0 + d * 0.5f > gw)) continue;
                kp[0] = new float[] { x0 + d * t(v0, v1), y0 };
                kp[1] = new float[] { x0 + d, y0 + d * t(v1, v2) };
                kp[2] = new float[] { x0 + d * t(v3, v2), y0 + d };
                kp[3] = new float[] { x0, y0 + d * t(v0, v3) };
                boolean mitte = (v0 + v1 + v2 + v3) * 0.25f >= sw;
                switch (fall) {
                    case 1, 14 -> wand(kp[3], kp[0], c, glow, voll, gw, gh, ox, oy, z0, z1, alt, schnitte);
                    case 2, 13 -> wand(kp[0], kp[1], c, glow, voll, gw, gh, ox, oy, z0, z1, alt, schnitte);
                    case 3, 12 -> wand(kp[3], kp[1], c, glow, voll, gw, gh, ox, oy, z0, z1, alt, schnitte);
                    case 4, 11 -> wand(kp[1], kp[2], c, glow, voll, gw, gh, ox, oy, z0, z1, alt, schnitte);
                    case 6, 9 -> wand(kp[0], kp[2], c, glow, voll, gw, gh, ox, oy, z0, z1, alt, schnitte);
                    case 7, 8 -> wand(kp[2], kp[3], c, glow, voll, gw, gh, ox, oy, z0, z1, alt, schnitte);
                    case 5 -> {
                        if (mitte) { wand(kp[0], kp[1], c, glow, voll, gw, gh, ox, oy, z0, z1, alt, schnitte); wand(kp[2], kp[3], c, glow, voll, gw, gh, ox, oy, z0, z1, alt, schnitte); }
                        else { wand(kp[3], kp[0], c, glow, voll, gw, gh, ox, oy, z0, z1, alt, schnitte); wand(kp[1], kp[2], c, glow, voll, gw, gh, ox, oy, z0, z1, alt, schnitte); }
                    }
                    case 10 -> {
                        if (mitte) { wand(kp[3], kp[0], c, glow, voll, gw, gh, ox, oy, z0, z1, alt, schnitte); wand(kp[1], kp[2], c, glow, voll, gw, gh, ox, oy, z0, z1, alt, schnitte); }
                        else { wand(kp[0], kp[1], c, glow, voll, gw, gh, ox, oy, z0, z1, alt, schnitte); wand(kp[2], kp[3], c, glow, voll, gw, gh, ox, oy, z0, z1, alt, schnitte); }
                    }
                    default -> {}
                }
            }
        }
        leuchten = alt;
    }

    private static float t(float a, float b) {
        float d = b - a;
        return Math.abs(d) < 1e-6f ? 0.5f : Math.max(0f, Math.min(1f, (SpriteKunst.FLACH_SCHWELLE - a) / d));
    }

    /** Ein Wandstueck von a nach b (Bildpixel, y nach unten), quer ueber die ganze Tiefe. */
    private void wand(float[] a, float[] b, int[][] c, boolean[][] glow, boolean[] voll, int gw, int gh, float ox, float oy, float z0, float z1, float alt, int schnitte) {
        float mx = (a[0] + b[0]) / 2f, my = (a[1] + b[1]) / 2f;
        int k = SpriteKunst.naechstes(voll, gw, gh, mx, my, schnitte);
        if (k < 0) return;
        int px = k % gw, py = k / gw;
        boolean leucht = glow != null && py < glow.length && px < glow[py].length && glow[py][px];
        leuchten = leucht ? 1f : alt;
        int fa = SpriteKunst.umgebung(c, voll, gw, gh, a[0], a[1], schnitte), fb = SpriteKunst.umgebung(c, voll, gw, gh, b[0], b[1], schnitte);
        if (fa == 0) fa = c[py][px];
        if (fb == 0) fb = c[py][px];
        if (!leucht) { fa = dunkel(fa, 0.86f); fb = dunkel(fb, 0.86f); }
        float[] na = aussen(voll, gw, gh, a, schnitte), nb = aussen(voll, gw, gh, b, schnitte);
        float ax = a[0] - ox, ay = oy - a[1], bx = b[0] - ox, by = oy - b[1];
        viereck(new float[] { ax, ay, z0 }, new float[] { bx, by, z0 }, new float[] { bx, by, z1 }, new float[] { ax, ay, z1 }, na, nb, nb, na, fa, fb, fb, fa);
    }

    /** Normale nach aussen an einer Umrissstelle (Netz-Koordinaten, y nach oben). */
    private static float[] aussen(boolean[] voll, int gw, int gh, float[] p, int schnitte) {
        float e = 0.35f;
        float gx = SpriteKunst.deckung(voll, gw, gh, p[0] + e, p[1], schnitte) - SpriteKunst.deckung(voll, gw, gh, p[0] - e, p[1], schnitte);
        float gy = SpriteKunst.deckung(voll, gw, gh, p[0], p[1] + e, schnitte) - SpriteKunst.deckung(voll, gw, gh, p[0], p[1] - e, schnitte);
        return norm(new float[] { -gx, gy, 0 });
    }

    /** Farbe einer Bildecke: dx/dy = Richtung der Ecke (dy = 1 oben). Am Rand der Form dunkler. */
    private static int kante(int[][] c, int x, int y, int dx, int dy, boolean leucht, int f) {
        if (leucht) return f;
        int yy = y - dy;
        int n = (leer(c, x + dx, y) ? 1 : 0) + (leer(c, x, yy) ? 1 : 0) + (leer(c, x + dx, yy) ? 1 : 0);
        return n == 0 ? f : dunkel(f, n == 1 ? 0.9f : n == 2 ? 0.82f : 0.86f);
    }

    private static boolean leer(int[][] c, int x, int y) {
        return y < 0 || y >= c.length || x < 0 || x >= c[y].length || (c[y][x] >>> 24) == 0;
    }

    /** Ring (Torus) um die y-Achse: grosser Radius R, Dicke r. */
    public void torus(float R, float r, int segU, int segV, int farbeAussen, int farbeInnen) {
        for (int i = 0; i < segU; i++) for (int j = 0; j < segV; j++) {
            float[][] pts = new float[4][], nrm = new float[4][];
            int[] fb = new int[4];
            int[][] ij = { { i, j }, { i + 1, j }, { i + 1, j + 1 }, { i, j + 1 } };
            for (int k = 0; k < 4; k++) {
                double u = ij[k][0] * 2 * Math.PI / segU, v = ij[k][1] * 2 * Math.PI / segV;
                float cu = (float) Math.cos(u), su = (float) Math.sin(u), cv = (float) Math.cos(v), sv = (float) Math.sin(v);
                pts[k] = new float[]{ (R + r * cv) * cu, r * sv, (R + r * cv) * su };
                nrm[k] = new float[]{ cv * cu, sv, cv * su };
                fb[k] = mische(farbeInnen, farbeAussen, 0.5f + 0.5f * cv);
            }
            viereck(pts[0], pts[1], pts[2], pts[3], nrm[0], nrm[1], nrm[2], nrm[3], fb[0], fb[1], fb[2], fb[3]);
        }
    }

    /** Offener Reif (Krone): Innen- und Aussenwand, oben und unten geschlossen. */
    public void reif(float rInnen, float rAussen, float y0, float y1, int seg, int unten, int oben) {
        for (int i = 0; i < seg; i++) {
            double a0 = i * 2 * Math.PI / seg, a1 = (i + 1) * 2 * Math.PI / seg;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float[] n0 = { c0, 0, s0 }, n1 = { c1, 0, s1 }, i0 = { -c0, 0, -s0 }, i1 = { -c1, 0, -s1 }, up = { 0, 1, 0 }, dn = { 0, -1, 0 };
            viereck(new float[]{ c0 * rAussen, y0, s0 * rAussen }, new float[]{ c1 * rAussen, y0, s1 * rAussen },
                    new float[]{ c1 * rAussen, y1, s1 * rAussen }, new float[]{ c0 * rAussen, y1, s0 * rAussen }, n0, n1, n1, n0, unten, unten, oben, oben);
            viereck(new float[]{ c0 * rInnen, y0, s0 * rInnen }, new float[]{ c0 * rInnen, y1, s0 * rInnen },
                    new float[]{ c1 * rInnen, y1, s1 * rInnen }, new float[]{ c1 * rInnen, y0, s1 * rInnen }, i0, i0, i1, i1, unten, oben, oben, unten);
            viereck(new float[]{ c0 * rInnen, y1, s0 * rInnen }, new float[]{ c0 * rAussen, y1, s0 * rAussen },
                    new float[]{ c1 * rAussen, y1, s1 * rAussen }, new float[]{ c1 * rInnen, y1, s1 * rInnen }, up, up, up, up, oben, oben, oben, oben);
            viereck(new float[]{ c0 * rInnen, y0, s0 * rInnen }, new float[]{ c1 * rInnen, y0, s1 * rInnen },
                    new float[]{ c1 * rAussen, y0, s1 * rAussen }, new float[]{ c0 * rAussen, y0, s0 * rAussen }, dn, dn, dn, dn, unten, unten, unten, unten);
        }
    }

    /** Kugel um (cx,cy,cz). */
    public void kugel(float cx, float cy, float cz, float r, int seg, int oben, int unten) {
        int ringe = Math.max(3, seg / 2);
        for (int i = 0; i < seg; i++) for (int j = 0; j < ringe; j++) {
            float[][] pts = new float[4][], nrm = new float[4][];
            int[] fb = new int[4];
            int[][] ij = { { i, j }, { i + 1, j }, { i + 1, j + 1 }, { i, j + 1 } };
            for (int k = 0; k < 4; k++) {
                double u = ij[k][0] * 2 * Math.PI / seg, v = ij[k][1] * Math.PI / ringe - Math.PI / 2;
                float x = (float) (Math.cos(v) * Math.cos(u)), y = (float) Math.sin(v), z = (float) (Math.cos(v) * Math.sin(u));
                pts[k] = new float[]{ cx + x * r, cy + y * r, cz + z * r };
                nrm[k] = new float[]{ x, y, z };
                fb[k] = mische(unten, oben, 0.5f + 0.5f * y);
            }
            viereck(pts[0], pts[1], pts[2], pts[3], nrm[0], nrm[1], nrm[2], nrm[3], fb[0], fb[1], fb[2], fb[3]);
        }
    }

    /**
     * Roehre entlang eines Pfades (Punkte), Radius je Punkt; Farbe von
     * Anfang (fa) bis Ende (fe). Ende mit r=0 = Spitze.
     */
    public void roehre(float[][] pfad, float[] radius, int seg, int fa, int fe) {
        int n = pfad.length;
        float[][][] ring = new float[n][seg + 1][];
        float[][][] norm = new float[n][seg + 1][];
        float[] ref = { 0, 0, 1 };
        for (int i = 0; i < n; i++) {
            float[] t = richtung(pfad, i);
            if (Math.abs(t[2]) > 0.9f) ref = new float[]{ 1, 0, 0 };
            float[] b1 = norm(kreuz(t, ref)), b2 = norm(kreuz(t, b1));
            for (int s = 0; s <= seg; s++) {
                double a = s * 2 * Math.PI / seg;
                float c = (float) Math.cos(a), si = (float) Math.sin(a);
                float[] d = { b1[0] * c + b2[0] * si, b1[1] * c + b2[1] * si, b1[2] * c + b2[2] * si };
                norm[i][s] = d;
                ring[i][s] = new float[]{ pfad[i][0] + d[0] * radius[i], pfad[i][1] + d[1] * radius[i], pfad[i][2] + d[2] * radius[i] };
            }
        }
        for (int i = 0; i < n - 1; i++) {
            int c0 = mische(fa, fe, i / (float) (n - 1)), c1 = mische(fa, fe, (i + 1) / (float) (n - 1));
            for (int s = 0; s < seg; s++) {
                viereck(ring[i][s], ring[i + 1][s], ring[i + 1][s + 1], ring[i][s + 1],
                        norm[i][s], norm[i + 1][s], norm[i + 1][s + 1], norm[i][s + 1], c0, c1, c1, c0);
            }
        }
        if (radius[0] > 0.01f) {
            float[] t = richtung(pfad, 0);
            float[] nn = { -t[0], -t[1], -t[2] };
            for (int s = 0; s < seg; s++) viereck(pfad[0], ring[0][s], ring[0][s + 1], pfad[0], nn, nn, nn, nn, fa, fa, fa, fa);
        }
        if (radius[n - 1] > 0.01f) {
            float[] t = richtung(pfad, n - 1);
            for (int s = 0; s < seg; s++) viereck(pfad[n - 1], ring[n - 1][s + 1], ring[n - 1][s], pfad[n - 1], t, t, t, t, fe, fe, fe, fe);
        }
    }

    /** Kegel von Grundflaeche (Mittelpunkt b, Radius r) zur Spitze s. */
    public void kegel(float[] b, float r, float[] s, int seg, int fb, int fs) {
        roehre(new float[][]{ b, { (b[0] + s[0]) / 2, (b[1] + s[1]) / 2, (b[2] + s[2]) / 2 }, s }, new float[]{ r, r * 0.5f, 0 }, seg, fb, fs);
    }

    /** Zylinder(-mantel) um die y-Achse von y0 bis y1, mit Deckeln. */
    public void zylinder(float r, float y0, float y1, int seg, int f0, int f1) {
        roehre(new float[][]{ { 0, y0, 0 }, { 0, y1, 0 } }, new float[]{ r, r }, seg, f0, f1);
    }

    /** Doppelpyramide (Kristall): Mitte (cx,cy,cz), Hoehe h oben/unten, Radius r, n Seiten. */
    public void kristall(float cx, float cy, float cz, float r, float hOben, float hUnten, int seiten, int f1, int f2) {
        float[] oben = { cx, cy + hOben, cz }, unten = { cx, cy - hUnten, cz };
        for (int i = 0; i < seiten; i++) {
            double a0 = i * 2 * Math.PI / seiten, a1 = (i + 1) * 2 * Math.PI / seiten;
            float[] p0 = { cx + r * (float) Math.cos(a0), cy, cz + r * (float) Math.sin(a0) };
            float[] p1 = { cx + r * (float) Math.cos(a1), cy, cz + r * (float) Math.sin(a1) };
            float[] no = norm(kreuz(sub(p1, oben), sub(p0, oben)));
            float[] nu = norm(kreuz(sub(p0, unten), sub(p1, unten)));
            if (dot(no, sub(p0, new float[]{ cx, cy, cz })) < 0) no = neg(no);
            if (dot(nu, sub(p0, new float[]{ cx, cy, cz })) < 0) nu = neg(nu);
            viereck(p0, p1, oben, oben, no, no, no, no, f1, f1, f2, f2);
            viereck(p1, p0, unten, unten, nu, nu, nu, nu, f1, f1, mische(f1, 0xFF000000, 0.35f), mische(f1, 0xFF000000, 0.35f));
        }
    }

    private static float[] richtung(float[][] p, int i) {
        float[] a = p[Math.max(0, i - 1)], b = p[Math.min(p.length - 1, i + 1)];
        return norm(sub(b, a));
    }
    static float[] sub(float[] a, float[] b) { return new float[]{ a[0] - b[0], a[1] - b[1], a[2] - b[2] }; }
    static float[] neg(float[] a) { return new float[]{ -a[0], -a[1], -a[2] }; }
    static float dot(float[] a, float[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    static float[] kreuz(float[] a, float[] b) { return new float[]{ a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0] }; }
    static float[] norm(float[] a) {
        float l = (float) Math.sqrt(dot(a, a));
        return l < 1e-6f ? new float[]{ 0, 1, 0 } : new float[]{ a[0] / l, a[1] / l, a[2] / l };
    }

    /** ARGB mischen (k 0 = a, 1 = b). */
    public static int mische(int a, int b, float k) {
        k = Math.max(0, Math.min(1, k));
        int r = 0;
        for (int s = 0; s < 32; s += 8) {
            int x = (a >>> s) & 255, y = (b >>> s) & 255;
            r |= (Math.round(x + (y - x) * k) & 255) << s;
        }
        return r;
    }
}
