package com.vortex.client.cosmetics;

/**
 * Hochaufgeloeste Aura-Bildchen (seit 4.80): aus einem kleinen Pixel-Sprite (3 x 3 bis
 * 16 x 16) wird ein weiches Bild mit runden Kanten, sanften Farbuebergaengen und etwas
 * Glanz (Licht von links oben, Rand leicht dunkler) -- statt eckiger Kloetzchen.
 *
 * So geht's: die Deckung jedes Pixels wird weichgezeichnet und bei 50 % geschnitten
 * (runde Umrisse, Kantenglaettung), die Farben werden ueber Nachbarn gemittelt, und aus
 * der weichgezeichneten Form entsteht eine Woelbung fuer Licht und Schatten.
 * Ohne Minecraft-Klassen. Pixel im NativeImage-Format (ABGR), vormultipliziert nur beim Leuchten.
 */
public final class SpriteKunst {
    private SpriteKunst() {}

    /** w/h = Bildgroesse in Texeln, rasterW/rasterH = Groesse in Sprite-Pixeln (mit Rand). */
    public record Bild(int w, int h, int rasterW, int rasterH, int[] basis, int[] leuchten, int mittelFarbe, boolean leuchtet) {}

    /** Rand (in Sprite-Pixeln) rund um das Motiv, damit Kanten und Schein Platz haben. */
    private static final int RAND = 1;

    public static Bild machen(int[][] farben, boolean[][] glow) {
        int sh = farben.length, sw = 0;
        for (int[] r : farben) sw = Math.max(sw, r.length);
        int gw = sw + 2 * RAND, gh = sh + 2 * RAND;
        int f = Math.max(10, Math.min(28, 200 / Math.max(gw, gh)));
        int w = gw * f, h = gh * f;
        float[] deck = new float[gw * gh], gl = new float[gw * gh];
        float[] r = new float[gw * gh], g = new float[gw * gh], b = new float[gw * gh];
        long sr = 0, sg = 0, sb = 0, sn = 0;
        boolean leuchtet = false;
        for (int y = 0; y < sh; y++) {
            for (int x = 0; x < farben[y].length; x++) {
                int c = farben[y][x];
                if ((c >>> 24) == 0) continue;
                int k = (y + RAND) * gw + x + RAND;
                deck[k] = 1f;
                r[k] = ((c >> 16) & 255) / 255f; g[k] = ((c >> 8) & 255) / 255f; b[k] = (c & 255) / 255f;
                sr += (c >> 16) & 255; sg += (c >> 8) & 255; sb += c & 255; sn++;
                if (glow != null && y < glow.length && x < glow[y].length && glow[y][x]) { gl[k] = 1f; leuchtet = true; }
            }
        }
        int mittel = sn == 0 ? 0xFFFFFF : (int) (sr / sn) << 16 | (int) (sg / sn) << 8 | (int) (sb / sn);
        int[] basis = new int[w * h], leucht = new int[w * h];
        float rad = 1.1f;
        for (int py = 0; py < h; py++) {
            float sy = (py + 0.5f) / f - 0.5f;
            for (int px = 0; px < w; px++) {
                float sx = (px + 0.5f) / f - 0.5f;
                float form = 0, formW = 0, hoch = 0, hochW = 0, leu = 0;
                float cr = 0, cg = 0, cb = 0, cw = 0;
                int x0 = (int) Math.floor(sx - rad), x1 = (int) Math.ceil(sx + rad);
                int y0 = (int) Math.floor(sy - rad), y1 = (int) Math.ceil(sy + rad);
                for (int yy = y0; yy <= y1; yy++) {
                    for (int xx = x0; xx <= x1; xx++) {
                        float dx = xx - sx, dy = yy - sy, d2 = dx * dx + dy * dy;
                        float wf = (float) Math.exp(-d2 / 0.13f);
                        float wh = (float) Math.exp(-d2 / 0.9f);
                        float d = (xx < 0 || yy < 0 || xx >= gw || yy >= gh) ? 0f : deck[yy * gw + xx];
                        form += wf * d; formW += wf;
                        hoch += wh * d; hochW += wh;
                        if (d > 0) {
                            int k = yy * gw + xx;
                            float wc = (float) Math.exp(-d2 / 0.07f);
                            cr += r[k] * wc; cg += g[k] * wc; cb += b[k] * wc; cw += wc;
                            leu += gl[k] * wf;
                        }
                    }
                }
                float a = form / formW;
                float aa = 1.6f / f;
                float deckung = glatt(0.5f - aa * 2.2f, 0.5f + aa * 2.2f, a);
                if (deckung <= 0.002f || cw <= 0) continue;
                float rr = cr / cw, gg = cg / cw, bb = cb / cw;
                float hgt = hoch / hochW;
                float hx = hoehe(deck, gw, gh, sx + 0.35f, sy) - hoehe(deck, gw, gh, sx - 0.35f, sy);
                float hy = hoehe(deck, gw, gh, sx, sy + 0.35f) - hoehe(deck, gw, gh, sx, sy - 0.35f);
                float licht = 0.9f - 0.55f * (hx * 0.7f + hy * 0.7f);
                float rand = glatt(0.55f, 0.85f, a);
                licht *= 0.78f + 0.22f * rand;
                float glanz = (float) Math.pow(Math.max(0f, -(hx + hy) * 0.9f), 3f) * 0.5f * glatt(0.6f, 0.95f, hgt);
                rr = Math.min(1f, rr * licht + glanz);
                gg = Math.min(1f, gg * licht + glanz);
                bb = Math.min(1f, bb * licht + glanz);
                basis[py * w + px] = abgr(rr, gg, bb, deckung);
                float lw = formW > 0 ? Math.min(1f, leu / formW * 1.6f) * deckung : 0f;
                if (lw > 0.004f) {
                    float lr = Math.min(1f, cr / cw * 1.1f + 0.1f), lg = Math.min(1f, cg / cw * 1.1f + 0.1f), lb = Math.min(1f, cb / cw * 1.1f + 0.1f);
                    leucht[py * w + px] = abgr(lr * lw, lg * lw, lb * lw, 1f);
                }
            }
        }
        ausbluten(basis, w, h, 3);
        return new Bild(w, h, gw, gh, basis, leucht, mittel, leuchtet);
    }

    /** Rand um flache Bilder (in Pixeln), damit die weiche Kante nicht abgeschnitten wird. */
    public static final int FLACH_RAND = 1;
    /** Wie weich die Umrisse flacher Teile werden (Varianz der Glaettung, in Pixeln hoch 2). */
    private static final float FLACH_VARIANZ = 0.2f;
    /**
     * Ab welcher Deckung etwas zum Teil gehoert. Etwas unter 0.5, damit einzelne Pixel und
     * schraeg verbundene Pixel-Ketten nicht verschwinden oder abreissen.
     */
    public static final float FLACH_SCHWELLE = 0.4f;

    /**
     * Glatte Deckung eines Pixelbilds an einer Stelle (x nach rechts, y nach unten, in Pixeln):
     * die Pixel weichgezeichnet; der Umriss ist dort, wo der Wert FLACH_SCHWELLE ist. Gerade
     * Kanten bleiben (fast) auf der Pixelgrenze, Treppen werden zu glatten Schraegen, Ecken rund.
     * Bild und Seitenwaende (Netz.waende) nehmen beide diesen Wert -- sie passen zusammen.
     */
    public static float deckung(boolean[] voll, int gw, int gh, float x, float y) {
        return deckung(voll, gw, gh, x, y, 0);
    }

    /**
     * Schnittkanten (seit 4.82): Bit 1 = linker Rand, Bit 2 = rechter Rand ist nur ein Schnitt
     * (Fluegelglied am Gelenk) -- dort geht das Bild gedanklich weiter, wird also weder
     * abgerundet noch abgedunkelt. Liefert die Spalte, aus der gelesen wird, oder -1.
     */
    public static int spalte(int x, int gw, int schnitte) {
        if (x < 0) return (schnitte & 1) != 0 ? 0 : -1;
        if (x >= gw) return (schnitte & 2) != 0 ? gw - 1 : -1;
        return x;
    }

    /** Wie oben, mit Schnittkanten. */
    public static float deckung(boolean[] voll, int gw, int gh, float x, float y, int schnitte) {
        int x0 = (int) Math.floor(x - 0.5f) - 1, y0 = (int) Math.floor(y - 0.5f) - 1;
        float[] wx = new float[5], wy = new float[5];
        float sx = 0, sy = 0;
        for (int i = 0; i < 5; i++) {
            float dx = x0 + i + 0.5f - x, dy = y0 + i + 0.5f - y;
            wx[i] = (float) Math.exp(-dx * dx / (2 * FLACH_VARIANZ));
            wy[i] = (float) Math.exp(-dy * dy / (2 * FLACH_VARIANZ));
            sx += wx[i]; sy += wy[i];
        }
        float s = 0;
        for (int j = 0; j < 5; j++) {
            int yy = y0 + j;
            if (yy < 0 || yy >= gh) continue;
            for (int i = 0; i < 5; i++) {
                int xx = spalte(x0 + i, gw, schnitte);
                if (xx >= 0 && voll[yy * gw + xx]) s += wx[i] * wy[j];
            }
        }
        return s / (sx * sy);
    }

    /** Welche Pixel belegt sind (Zeile fuer Zeile, Breite gw). */
    public static boolean[] belegt(int[][] farben, int gw) {
        boolean[] v = new boolean[gw * farben.length];
        for (int y = 0; y < farben.length; y++) for (int x = 0; x < farben[y].length; x++) v[y * gw + x] = (farben[y][x] >>> 24) != 0;
        return v;
    }

    /**
     * Mittlere Farbe der belegten Pixel um eine Stelle (weich gewichtet), ARGB; 0 wenn keins.
     * Fuer die Seitenwaende -- so wechselt die Farbe dort nicht Stueck fuer Stueck.
     */
    public static int umgebung(int[][] farben, boolean[] voll, int gw, int gh, float x, float y) {
        return umgebung(farben, voll, gw, gh, x, y, 0);
    }

    public static int umgebung(int[][] farben, boolean[] voll, int gw, int gh, float x, float y, int schnitte) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        float r = 0, g = 0, b = 0, s = 0;
        for (int j = -2; j <= 2; j++) for (int i = -2; i <= 2; i++) {
            int xx = spalte(ix + i, gw, schnitte), yy = iy + j;
            if (xx < 0 || yy < 0 || yy >= gh || !voll[yy * gw + xx]) continue;
            float dx = ix + i + 0.5f - x, dy = yy + 0.5f - y;
            float w = (float) Math.exp(-(dx * dx + dy * dy) / 0.9f);
            int c = farben[yy][xx];
            r += ((c >> 16) & 255) * w; g += ((c >> 8) & 255) * w; b += (c & 255) * w; s += w;
        }
        if (s <= 1e-6f) return 0;
        return 0xFF000000 | Math.round(r / s) << 16 | Math.round(g / s) << 8 | Math.round(b / s);
    }

    /** Naechstes belegtes Pixel zu einer Stelle (Index) oder -1. */
    public static int naechstes(boolean[] voll, int gw, int gh, float x, float y) {
        return naechstes(voll, gw, gh, x, y, 0);
    }

    public static int naechstes(boolean[] voll, int gw, int gh, float x, float y, int schnitte) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y), best = -1;
        float bd = 1e9f;
        for (int j = -2; j <= 2; j++) for (int i = -2; i <= 2; i++) {
            int xx = spalte(ix + i, gw, schnitte), yy = iy + j;
            if (xx < 0 || yy < 0 || yy >= gh || !voll[yy * gw + xx]) continue;
            float dx = Math.max(0, Math.abs(ix + i + 0.5f - x) - 0.5f), dy = Math.max(0, Math.abs(yy + 0.5f - y) - 0.5f);
            float d = dx * dx + dy * dy;
            if (d < bd) { bd = d; best = yy * gw + xx; }
        }
        return best;
    }

    /**
     * Flache Teile (Fluegel, Rucksaecke, Schwerter, Brillen, Anhaenger; seit 4.81) in hoher
     * Aufloesung: glatter Umriss (siehe deckung) mit weicher Kante, Farben gehen an
     * Pixelgrenzen sanft ineinander ueber, Licht von links oben gibt eine leichte Woelbung,
     * zum Rand hin etwas dunkler. Leuchtende Pixel kommen zusaetzlich in die Leucht-Ebene.
     * rasterW/rasterH = Bildgroesse in Pixeln mit Rand (FLACH_RAND je Seite).
     */
    public static Bild flach(int[][] farben, boolean[][] glow) {
        return flach(farben, glow, 0);
    }

    /** Wie oben; schnitte = Schnittkanten (siehe spalte). */
    public static Bild flach(int[][] farben, boolean[][] glow, int schnitte) {
        int gh = farben.length, gw = 0;
        for (int[] r : farben) gw = Math.max(gw, r.length);
        int R = FLACH_RAND, rw = gw + 2 * R, rh = gh + 2 * R;
        int f = Math.max(8, Math.min(32, 400 / Math.max(1, Math.max(rw, rh))));
        int w = rw * f, h = rh * f;
        boolean[] voll = belegt(farben, gw);
        float[] deck = new float[gw * gh];
        float[][] rgb = new float[3][gw * gh];
        boolean[] gl = new boolean[gw * gh];
        long sr = 0, sg = 0, sb = 0, sn = 0;
        boolean leuchtet = false;
        for (int y = 0; y < gh; y++) {
            for (int x = 0; x < farben[y].length; x++) {
                int c = farben[y][x];
                if ((c >>> 24) == 0) continue;
                int k = y * gw + x;
                deck[k] = 1f;
                rgb[0][k] = ((c >> 16) & 255) / 255f; rgb[1][k] = ((c >> 8) & 255) / 255f; rgb[2][k] = (c & 255) / 255f;
                sr += (c >> 16) & 255; sg += (c >> 8) & 255; sb += c & 255; sn++;
                if (glow != null && y < glow.length && x < glow[y].length && glow[y][x]) { gl[k] = true; leuchtet = true; }
            }
        }
        int mittel = sn == 0 ? 0xFFFFFF : (int) (sr / sn) << 16 | (int) (sg / sn) << 8 | (int) (sb / sn);
        float[] feld = new float[(w + 1) * (h + 1)];
        for (int py = 0; py <= h; py++) {
            for (int px = 0; px <= w; px++) {
                feld[py * (w + 1) + px] = deckung(voll, gw, gh, (float) px / f - R, (float) py / f - R, schnitte);
            }
        }
        int[] basis = new int[w * h], leucht = new int[w * h];
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                float f00 = feld[py * (w + 1) + px], f10 = feld[py * (w + 1) + px + 1], f01 = feld[(py + 1) * (w + 1) + px], f11 = feld[(py + 1) * (w + 1) + px + 1];
                float mitte = (f00 + f10 + f01 + f11) * 0.25f;
                float gx = (f10 + f11 - f00 - f01) * 0.5f, gy = (f01 + f11 - f00 - f10) * 0.5f;
                float grad = (float) Math.sqrt(gx * gx + gy * gy);
                float abstand = grad > 1e-5f ? (mitte - FLACH_SCHWELLE) / grad : (mitte >= FLACH_SCHWELLE ? 9f : -9f);
                float a = Math.max(0f, Math.min(1f, abstand + 0.5f));
                float sx = (px + 0.5f) / f - R, sy = (py + 0.5f) / f - R;
                if (((schnitte & 1) != 0 && sx < 0) || ((schnitte & 2) != 0 && sx > gw)) a = 0;
                if (a <= 0.003f) continue;
                int k0 = naechstes(voll, gw, gh, sx, sy, schnitte);
                if (k0 < 0) continue;
                int ix = (int) Math.floor(sx), iy = (int) Math.floor(sy);
                float fx = sx - ix - 0.5f, fy = sy - iy - 0.5f;
                float r = 0, g = 0, b = 0, wsum = 0;
                for (int j = -1; j <= 1; j++) {
                    for (int i = -1; i <= 1; i++) {
                        int nx = spalte(ix + i, gw, schnitte), ny = iy + j;
                        if (nx < 0 || da(deck, gw, gh, nx, ny) <= 0) continue;
                        float wx = i == 0 ? 1f - glatt(0.25f, 0.5f, Math.abs(fx)) * 0.5f : (i * fx > 0 ? glatt(0.25f, 0.5f, Math.abs(fx)) * 0.5f : 0f);
                        float wy = j == 0 ? 1f - glatt(0.25f, 0.5f, Math.abs(fy)) * 0.5f : (j * fy > 0 ? glatt(0.25f, 0.5f, Math.abs(fy)) * 0.5f : 0f);
                        float wgt = wx * wy;
                        if (wgt <= 0) continue;
                        int k = ny * gw + nx;
                        r += rgb[0][k] * wgt; g += rgb[1][k] * wgt; b += rgb[2][k] * wgt; wsum += wgt;
                    }
                }
                if (wsum <= 1e-4f) { r = rgb[0][k0]; g = rgb[1][k0]; b = rgb[2][k0]; wsum = 1; }
                else { r /= wsum; g /= wsum; b /= wsum; }
                float hx = hoehe(deck, gw, gh, sx - 0.5f + 0.3f, sy - 0.5f, schnitte) - hoehe(deck, gw, gh, sx - 0.5f - 0.3f, sy - 0.5f, schnitte);
                float hy = hoehe(deck, gw, gh, sx - 0.5f, sy - 0.5f + 0.3f, schnitte) - hoehe(deck, gw, gh, sx - 0.5f, sy - 0.5f - 0.3f, schnitte);
                float innen = hoehe(deck, gw, gh, sx - 0.5f, sy - 0.5f, schnitte);
                float licht = 1f - 0.32f * (hx + hy);
                licht *= 0.8f + 0.2f * glatt(0.35f, 0.8f, innen);
                float glanz = (float) Math.pow(Math.max(0f, -(hx + hy)), 2f) * 0.18f;
                if (!gl[k0]) {
                    r = Math.min(1f, r * licht + glanz); g = Math.min(1f, g * licht + glanz); b = Math.min(1f, b * licht + glanz);
                }
                basis[py * w + px] = abgr(r, g, b, a);
                if (gl[k0]) {
                    float lw = a * 0.4f;
                    leucht[py * w + px] = abgr(r * lw, g * lw, b * lw, 1f);
                }
            }
        }
        ausbluten(basis, w, h, 3);
        return new Bild(w, h, rw, rh, basis, leucht, mittel, leuchtet);
    }

    /**
     * Durchsichtige Bildpunkte am Rand bekommen die Farbe ihres Nachbarn (Deckkraft bleibt 0).
     * Sonst mischt die weiche Texturfilterung Schwarz in die Kante -- ein dunkler Saum.
     */
    public static void ausbluten(int[] px, int w, int h, int runden) {
        int[] alt = new int[px.length];
        for (int r = 0; r < runden; r++) {
            System.arraycopy(px, 0, alt, 0, px.length);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int i = y * w + x;
                    if ((alt[i] >>> 24) != 0 || (alt[i] & 0xFFFFFF) != 0) continue;
                    int rs = 0, gs = 0, bs = 0, n = 0;
                    for (int j = -1; j <= 1; j++) for (int k = -1; k <= 1; k++) {
                        int xx = x + k, yy = y + j;
                        if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue;
                        int q = alt[yy * w + xx];
                        if ((q >>> 24) == 0 && (q & 0xFFFFFF) == 0) continue;
                        rs += q & 255; gs += (q >> 8) & 255; bs += (q >> 16) & 255; n++;
                    }
                    if (n > 0) px[i] = (bs / n) << 16 | (gs / n) << 8 | (rs / n);
                }
            }
        }
    }

    private static float da(float[] deck, int gw, int gh, int x, int y) {
        return x < 0 || y < 0 || x >= gw || y >= gh ? 0f : deck[y * gw + x];
    }

    /** Weichgezeichnete Deckung an einer Stelle (fuer die Woelbung). */
    private static float hoehe(float[] deck, int gw, int gh, float sx, float sy) {
        return hoehe(deck, gw, gh, sx, sy, 0);
    }

    private static float hoehe(float[] deck, int gw, int gh, float sx, float sy, int schnitte) {
        float s = 0, n = 0;
        int x0 = (int) Math.floor(sx - 1.5f), x1 = (int) Math.ceil(sx + 1.5f);
        int y0 = (int) Math.floor(sy - 1.5f), y1 = (int) Math.ceil(sy + 1.5f);
        for (int yy = y0; yy <= y1; yy++) {
            for (int xx = x0; xx <= x1; xx++) {
                float dx = xx - sx, dy = yy - sy;
                float wgt = (float) Math.exp(-(dx * dx + dy * dy) / 0.7f);
                int sp = spalte(xx, gw, schnitte);
                s += wgt * ((sp < 0 || yy < 0 || yy >= gh) ? 0f : deck[yy * gw + sp]);
                n += wgt;
            }
        }
        return s / n;
    }

    private static float glatt(float a, float b, float v) {
        float k = (v - a) / (b - a);
        k = k < 0 ? 0 : k > 1 ? 1 : k;
        return k * k * (3 - 2 * k);
    }

    private static int k255(float v) { return v <= 0 ? 0 : v >= 1 ? 255 : (int) (v * 255f + 0.5f); }

    private static int abgr(float r, float g, float b, float a) {
        return k255(a) << 24 | k255(b) << 16 | k255(g) << 8 | k255(r);
    }
}
