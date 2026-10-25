package com.vortex.client.cosmetics;

/**
 * Weiche Leucht-Texturen fuer die grossen Auren (seit 4.79), einmal berechnet:
 * Lichtfleck, Ring, Zauberkreis, Lichtstrahl, Funke, Schneestern.
 *
 * Alles weiss, die Helligkeit steckt in der Farbe, Deckkraft ist immer voll: die Auren
 * werden additiv gezeichnet (Energy-Swirl-Pipeline, die fast durchsichtige Pixel sonst
 * wegschneiden wuerde), die Farbe kommt aus den Eckpunkten. Ohne Minecraft-Klassen,
 * damit sich jede Textur auch als Bild pruefen laesst. Pixel im NativeImage-Format (ABGR).
 */
public final class AuraTexturen {
    private AuraTexturen() {}

    public enum Art {
        GLUT(128, 128), RING(256, 256), RUNEN(512, 512), STRAHL(32, 128), FUNKE(64, 64), SCHNEE(256, 256), BAND(32, 32);
        public final int w, h;
        Art(int w, int h) { this.w = w; this.h = h; }
    }

    public static int[] bild(Art a) {
        int w = a.w, h = a.h;
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float u = (x + 0.5f) / w * 2f - 1f, v = (y + 0.5f) / h * 2f - 1f;
                float i = switch (a) {
                    case GLUT -> glut(u, v);
                    case RING -> ring(u, v);
                    case RUNEN -> runen(u, v, 2f / w);
                    case STRAHL -> strahl(u, v);
                    case FUNKE -> funke(u, v);
                    case SCHNEE -> schnee(u, v, 2f / w);
                    case BAND -> Math.min(1f, (float) Math.exp(-u * u * 7f) + (float) Math.exp(-u * u * 60f) * 0.6f);
                };
                int k = Math.max(0, Math.min(255, Math.round(i * 255f)));
                px[y * w + x] = 0xFF000000 | k << 16 | k << 8 | k;
            }
        }
        return px;
    }

    private static float glatt(float a, float b, float v) {
        float k = (v - a) / (b - a);
        k = k < 0 ? 0 : k > 1 ? 1 : k;
        return k * k * (3 - 2 * k);
    }

    /** Linie mit weicher Kante: d = Abstand, b = halbe Breite, aa = ein Bildpunkt. */
    private static float linie(float d, float b, float aa) {
        return glatt(b + aa, b - aa, Math.abs(d));
    }

    private static float glut(float u, float v) {
        float r2 = u * u + v * v;
        return (float) Math.exp(-r2 * 4.2f) * glatt(1f, 0.85f, (float) Math.sqrt(r2));
    }

    private static float ring(float u, float v) {
        float r = (float) Math.sqrt(u * u + v * v);
        float d = (r - 0.86f) / 0.035f;
        float kern = (float) Math.exp(-d * d);
        float hof = (float) Math.exp(-((r - 0.86f) / 0.13f) * ((r - 0.86f) / 0.13f)) * 0.35f;
        return Math.min(1f, (kern + hof) * glatt(1f, 0.96f, r));
    }

    /**
     * Zauberkreis: zwei Aussenringe, dazwischen ein Band mit Runen-Zeichen, innen zwei
     * gedrehte Quadrate (Achteck-Stern) mit kleinen Kreisen an den Spitzen und ein innerer Ring.
     */
    private static float runen(float u, float v, float aa) {
        float r = (float) Math.sqrt(u * u + v * v);
        if (r > 0.995f) return 0f;
        float w = (float) Math.atan2(v, u);
        float s = 0f;
        s = Math.max(s, linie(r - 0.95f, 0.008f, aa));
        s = Math.max(s, linie(r - 0.80f, 0.006f, aa));
        s = Math.max(s, linie(r - 0.52f, 0.005f, aa));
        int zeichen = 36;
        float seg = (float) ((w / (Math.PI * 2) + 1) % 1) * zeichen;
        int nr = (int) seg;
        float lu = seg - nr, lv = (r - 0.82f) / 0.11f;
        if (lv >= 0 && lv <= 1 && lu > 0.18f && lu < 0.82f) {
            int bits = (int) (((nr * 2654435761L) >>> 7) & 0xFFFF);
            float bu = (lu - 0.18f) / 0.64f;
            float breite = aa * 0.9f / (0.11f);
            for (int k = 0; k < 6; k++) {
                if (((bits >> k) & 1) == 0) continue;
                float d = switch (k) {
                    case 0 -> lv - 0.15f;
                    case 1 -> lv - 0.85f;
                    case 2 -> (bu - 0.1f) * 0.6f;
                    case 3 -> (bu - 0.9f) * 0.6f;
                    case 4 -> (bu - lv) * 0.45f;
                    default -> (bu - 0.5f) * 0.6f;
                };
                s = Math.max(s, linie(d, 0.045f, breite) * 0.9f);
            }
        }
        for (int k = 0; k < 8; k++) {
            double a1 = Math.PI / 8 + k * Math.PI / 4, a2 = a1 + Math.PI / 2;
            float x1 = 0.78f * (float) Math.cos(a1), y1 = 0.78f * (float) Math.sin(a1);
            float x2 = 0.78f * (float) Math.cos(a2), y2 = 0.78f * (float) Math.sin(a2);
            s = Math.max(s, linie(strecke(u, v, x1, y1, x2, y2), 0.006f, aa) * 0.85f);
            float dk = (float) Math.sqrt((u - x1) * (u - x1) + (v - y1) * (v - y1));
            s = Math.max(s, linie(dk - 0.045f, 0.005f, aa));
        }
        float innen = (float) Math.exp(-r * r * 9f) * 0.18f;
        return Math.min(1f, s + innen);
    }

    private static float strecke(float px, float py, float ax, float ay, float bx, float by) {
        float vx = bx - ax, vy = by - ay, wx = px - ax, wy = py - ay;
        float k = Math.max(0, Math.min(1, (wx * vx + wy * vy) / (vx * vx + vy * vy)));
        float dx = wx - vx * k, dy = wy - vy * k;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /** Lichtstrahl: quer weich (u), laengs an beiden Enden ausgeblendet (v). */
    private static float strahl(float u, float v) {
        float quer = (float) Math.exp(-u * u * 7f);
        float kern = (float) Math.exp(-u * u * 60f) * 0.6f;
        float laengs = glatt(1f, 0.55f, Math.abs(v)) * (0.75f + 0.25f * (1 - Math.abs(v)));
        return Math.min(1f, (quer + kern) * laengs);
    }

    private static float funke(float u, float v) {
        float r2 = u * u + v * v;
        float kern = (float) Math.exp(-r2 * 30f);
        float hof = (float) Math.exp(-r2 * 7f) * 0.45f;
        float strahlen = (float) (Math.exp(-Math.abs(u) * 14f) * Math.exp(-v * v * 120f) + Math.exp(-Math.abs(v) * 14f) * Math.exp(-u * u * 120f)) * 0.8f;
        return Math.min(1f, (kern + hof + strahlen) * glatt(1f, 0.8f, (float) Math.sqrt(r2)));
    }

    /** Schneestern mit sechs Armen und Seitenzweigen, dazu ein feiner Aussenring. */
    private static float schnee(float u, float v, float aa) {
        float r = (float) Math.sqrt(u * u + v * v);
        if (r > 0.995f) return 0f;
        float w = (float) Math.atan2(v, u);
        float seg = (float) (Math.PI / 3);
        float a = (float) (((w % seg) + seg) % seg) - seg / 2;
        float lx = r * (float) Math.cos(a), ly = r * (float) Math.sin(a);
        float s = 0;
        if (lx < 0.82f) s = Math.max(s, linie(ly, 0.012f, aa));
        for (float p : new float[] { 0.3f, 0.5f, 0.68f }) {
            float l = 0.2f * (1 - p * 0.7f);
            float dx = lx - p, dy = Math.abs(ly);
            float d = strecke(dx, dy, 0, 0, l * 0.7f, l);
            s = Math.max(s, linie(d, 0.009f, aa));
        }
        s = Math.max(s, linie(r - 0.9f, 0.006f, aa) * 0.7f);
        float innen = (float) Math.exp(-r * r * 30f) * 0.5f;
        return Math.min(1f, s + innen);
    }

    /** Mip-Stufen (je 2 x 2 gemittelt, nach Deckkraft gewichtet -- keine dunklen Raender), stufen[0] = das Bild selbst. */
    public static int[][] stufen(int[] px, int w, int h, int anzahl) {
        int[][] s = new int[anzahl][];
        s[0] = px;
        for (int k = 1; k < anzahl; k++) {
            int pw = Math.max(1, w >> (k - 1)), ph = Math.max(1, h >> (k - 1));
            int nw = Math.max(1, w >> k), nh = Math.max(1, h >> k);
            int[] a = s[k - 1], b = new int[nw * nh];
            for (int y = 0; y < nh; y++) {
                for (int x = 0; x < nw; x++) {
                    int x0 = Math.min(pw - 1, 2 * x), x1 = Math.min(pw - 1, 2 * x + 1), y0 = Math.min(ph - 1, 2 * y), y1 = Math.min(ph - 1, 2 * y + 1);
                    int p0 = a[y0 * pw + x0], p1 = a[y0 * pw + x1], p2 = a[y1 * pw + x0], p3 = a[y1 * pw + x1];
                    int a0 = p0 >>> 24, a1 = p1 >>> 24, a2 = p2 >>> 24, a3 = p3 >>> 24, as = a0 + a1 + a2 + a3;
                    if (as == 0) { b[y * nw + x] = 0; continue; }
                    int c = ((as + 2) / 4) << 24;
                    for (int sh = 0; sh < 24; sh += 8) {
                        int summe = ((p0 >>> sh) & 255) * a0 + ((p1 >>> sh) & 255) * a1 + ((p2 >>> sh) & 255) * a2 + ((p3 >>> sh) & 255) * a3;
                        c |= Math.min(255, (summe + as / 2) / as) << sh;
                    }
                    b[y * nw + x] = c;
                }
            }
            s[k] = b;
        }
        return s;
    }
}
